package com.warped.ui.chat

import kotlinx.coroutines.test.TestScope
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.warped.data.local.inference.ActiveEngine
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.EngineType
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.local.inference.MemoryInfo
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.remote.provider.ProviderRouter
import com.warped.domain.llm.LlmModelHelper
import com.warped.domain.model.ActiveModelSelection
import com.warped.domain.model.Endpoint
import com.warped.domain.model.LocalModel
import com.warped.domain.model.ModelCapabilities
import com.warped.domain.model.ParameterStore
import com.warped.domain.model.ProviderType
import com.warped.domain.model.Role
import com.warped.domain.model.StreamToken
import com.warped.domain.model.SyntaxTheme
import com.warped.domain.repository.ChatRepository
import com.warped.domain.repository.EndpointRepository
import com.warped.domain.repository.LocalModelRepository
import com.warped.domain.review.ReviewHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Instant

/**
 * Quick-task lazy-model-load: selecting a model only marks it pending —
 * the engine is never touched on selection. The first send mounts it
 * (`markLocalLoading` + `preloadLocalModel` on engine-path mismatch),
 * then generates; a failed mount surfaces a transcript error with the
 * draft kept and nothing persisted. Local→remote keeps the explicit
 * unload (nothing else frees the RAM).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelSwitchUnloadTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @TempDir
    lateinit var tempDir: File

    private fun model(path: String, at: Instant) = LocalModel(
        id = 1,
        name = path.substringAfterLast("/"),
        filePath = path,
        sizeBytes = 10,
        quantization = "Q4",
        parameterCount = "1B",
        architecture = "gemma",
        importedAt = at
    )

    private class Fixture(
        val vm: ChatViewModel,
        val selection: ActiveModelSelection,
        val engineManager: EngineManager,
        val chatRepository: ChatRepository,
        val endpointRepository: EndpointRepository,
    )

    private fun buildFixture(
        models: List<LocalModel>,
        canLoad: Boolean = true,
        failSwitchWith: Throwable? = null,
        helper: LlmModelHelper? = null,
    ): Fixture {
        val keystoreManager = mockk<com.warped.data.local.security.KeystoreManager>()
        every { keystoreManager.get(any()) } returns null
        every { keystoreManager.put(any(), any()) } just Runs
        every { keystoreManager.remove(any()) } just Runs
        val selection = ActiveModelSelection(keystoreManager)

        val localModelRepository = mockk<LocalModelRepository>()
        every { localModelRepository.observeModels() } returns MutableStateFlow(models)
        val engineManager = mockk<EngineManager>()
        var loadedPath: String? = null
        every { engineManager.getActiveEngine() } answers {
            loadedPath?.let { ActiveEngine(EngineType.LITE_RT_LM, it) }
        }
        every { engineManager.switchToLiteRT(any()) } answers {
            failSwitchWith?.let { throw it }
            loadedPath = firstArg()
        }
        every { engineManager.scheduleUnload() } answers { loadedPath = null }
        every { engineManager.unloadCurrent() } answers { loadedPath = null }
        val memoryChecker = mockk<MemoryChecker>()
        every { memoryChecker.canLoadModel(any()) } returns canLoad
        every { memoryChecker.shouldWarn(any()) } returns false
        every { memoryChecker.getMemoryInfo() } returns
            MemoryInfo(
                availableBytes = 100L * 1024L * 1024L,
                totalBytes = 4000L * 1024L * 1024L,
                usedPercent = 97
            )

        val chatRepository = mockk<ChatRepository>()
        every { chatRepository.observeConversations() } returns MutableStateFlow(emptyList())
        coEvery { chatRepository.createConversation(any(), any(), any(), any()) } returns 42L
        coEvery { chatRepository.saveMessage(any(), any()) } just Runs
        coEvery { chatRepository.getWebOverride(any()) } returns null
        val endpointRepository = mockk<EndpointRepository>(relaxed = true)
        every { endpointRepository.observeEndpoints() } returns MutableStateFlow(emptyList())
        val providerRouter = mockk<ProviderRouter>(relaxed = true)
        helper?.let { every { providerRouter.resolveLocalHelper(any(), any()) } returns it }
        val advancedPreferences = mockk<AdvancedPreferences>()
        every { advancedPreferences.syntaxTheme } returns flowOf(SyntaxTheme.MONOKAI)
        every { advancedPreferences.codeFontScale } returns flowOf(1.0f)
        every { advancedPreferences.thinkingEnabled } returns flowOf(false)
        every { advancedPreferences.webGroundingEnabled } returns flowOf(false)
        val context = mockk<Context>()
        every { context.getString(any<Int>()) } returns ""
        every { context.getString(any<Int>(), *anyVararg<Any>()) } returns ""
        val fetcher = mockk<com.warped.data.grounding.WebPageFetcher>()
        every { fetcher.cancel() } just Runs
        every { fetcher.hasValidatedInternet() } returns false
        val allowlist = mockk<com.warped.data.repository.ModelAllowlistRepository>()
        every { allowlist.effectiveCapabilities(any()) } returns ModelCapabilities()

        val vm = ChatViewModel(
            chatRepository = chatRepository,
            endpointRepository = endpointRepository,
            localModelRepository = localModelRepository,
            activeModelSelection = selection,
            providerRouter = providerRouter,
            savedStateHandle = SavedStateHandle(),
            parameterStore = ParameterStore(),
            engineManager = engineManager,
            memoryChecker = memoryChecker,
            advancedPreferences = advancedPreferences,
            fetcher = fetcher,
            multiUrlFetcher = mockk<com.warped.data.grounding.MultiUrlFetcher>(),
            ddgSearchRepository = mockk(),
            modelAllowlistRepository = allowlist,
            context = context,
            reviewHelper = mockk(relaxed = true),
        )
        return Fixture(vm, selection, engineManager, chatRepository, endpointRepository)
    }

    /**
     * `preloadLocalModel` hops to Dispatchers.Default (a real thread under
     * runTest) — yield until the mount lands before asserting.
     */
    private suspend fun TestScope.awaitMount(fixture: Fixture) {
        var attempts = 0
        while (fixture.engineManager.getActiveEngine() == null && attempts++ < 100) {
            kotlinx.coroutines.delay(10)
        }
        advanceUntilIdle()
    }

    private fun idleHelper(): LlmModelHelper {
        val helper = mockk<LlmModelHelper>()
        every { helper.type } returns ProviderType.LITE_RT_LM
        coEvery { helper.initialize(any()) } just Runs
        every { helper.runInference(any(), any()) } returns flow {
            emit(StreamToken.Delta("hello"))
            emit(StreamToken.Done())
        }
        return helper
    }

    @Test
    fun `selecting a model never touches the engine`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val pathA = File(tempDir, "a.litertlm").apply { writeText("fake") }.absolutePath
        val pathB = File(tempDir, "b.litertlm").apply { writeText("fake") }.absolutePath
        val models = listOf(
            model(pathA, Instant.EPOCH),
            model(pathB, Instant.EPOCH.plusSeconds(10))
        )
        val fixture = buildFixture(models, helper = idleHelper())
        runCurrent()
        advanceUntilIdle()

        fixture.vm.launchModelSelection(pathA, ProviderType.LITE_RT_LM)
        advanceUntilIdle()
        fixture.vm.launchModelSelection(pathB, ProviderType.LITE_RT_LM)
        advanceUntilIdle()

        // Selection only marks pending — no mount, no unload, no spinner.
        verify(exactly = 0) { fixture.engineManager.switchToLiteRT(any()) }
        verify(exactly = 0) { fixture.engineManager.unloadCurrent() }
        assertThat(fixture.vm.connectionState.value.selectedLocalModelId).isEqualTo(pathB)
        assertThat(fixture.vm.connectionState.value.isLoadingModel).isFalse()
        assertThat(fixture.vm.connectionState.value.isLocalModelLoaded).isFalse()
        assertThat(fixture.selection.localSelection.value.isLoading).isFalse()
    }

    @Test
    fun `first send with pending model loads it then generates`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val path = File(tempDir, "tiny.litertlm").apply { writeText("fake") }.absolutePath
        val fixture = buildFixture(listOf(model(path, Instant.EPOCH)), helper = idleHelper())
        runCurrent()
        advanceUntilIdle()

        fixture.vm.launchModelSelection(path, ProviderType.LITE_RT_LM)
        advanceUntilIdle()
        assertThat(fixture.engineManager.getActiveEngine()).isNull()

        fixture.vm.sendMessage("hello")
        awaitMount(fixture)

        // Mounted on first send, then generated.
        verify(exactly = 1) { fixture.engineManager.switchToLiteRT(path) }
        assertThat(fixture.selection.localSelection.value.isConnected).isTrue()
        val messages = fixture.vm.transcriptState.value.messages
        assertThat(messages.filter { it.role == Role.USER }.map { it.content })
            .containsExactly("hello")
        assertThat(messages.filter { it.role == Role.ASSISTANT }.map { it.content })
            .containsExactly("hello")
        assertThat(fixture.vm.transcriptState.value.error).isNull()
        assertThat(fixture.vm.connectionState.value.isLoadingModel).isFalse()
    }

    @Test
    fun `second send with loaded model skips the mount`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val path = File(tempDir, "tiny.litertlm").apply { writeText("fake") }.absolutePath
        val fixture = buildFixture(listOf(model(path, Instant.EPOCH)), helper = idleHelper())
        runCurrent()
        advanceUntilIdle()

        fixture.vm.launchModelSelection(path, ProviderType.LITE_RT_LM)
        advanceUntilIdle()
        fixture.vm.sendMessage("one")
        awaitMount(fixture)
        fixture.vm.sendMessage("two")
        advanceUntilIdle()

        // Exactly one mount across both turns.
        verify(exactly = 1) { fixture.engineManager.switchToLiteRT(path) }
        assertThat(fixture.vm.transcriptState.value.error).isNull()
    }

    @Test
    fun `memory blocked first send surfaces error and keeps draft`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val path = File(tempDir, "big.litertlm").apply { writeText("fake") }.absolutePath
        val fixture = buildFixture(
            listOf(model(path, Instant.EPOCH)),
            canLoad = false,
            helper = idleHelper(),
        )
        runCurrent()
        advanceUntilIdle()

        fixture.vm.launchModelSelection(path, ProviderType.LITE_RT_LM)
        advanceUntilIdle()
        fixture.vm.sendMessage("hello")
        advanceUntilIdle()

        // Error surfaced, spinner cleared, selection kept for retry, draft
        // kept, nothing persisted.
        assertThat(fixture.vm.connectionState.value.modelLoadError).isNotNull()
        assertThat(fixture.vm.transcriptState.value.error).isNotNull()
        assertThat(fixture.vm.connectionState.value.isLoadingModel).isFalse()
        assertThat(fixture.vm.connectionState.value.selectedLocalModelId).isEqualTo(path)
        assertThat(fixture.vm.inputState.value.inputText).isEqualTo("hello")
        assertThat(fixture.vm.transcriptState.value.messages).isEmpty()
        verify(exactly = 0) { fixture.engineManager.switchToLiteRT(any()) }
        coVerify(exactly = 0) { fixture.chatRepository.createConversation(any(), any(), any(), any()) }
        coVerify(exactly = 0) { fixture.chatRepository.saveMessage(any(), any()) }
    }

    @Test
    fun `failed mount surfaces error and keeps draft`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val path = File(tempDir, "tiny.litertlm").apply { writeText("fake") }.absolutePath
        val fixture = buildFixture(
            listOf(model(path, Instant.EPOCH)),
            failSwitchWith = RuntimeException("boom"),
            helper = idleHelper(),
        )
        runCurrent()
        advanceUntilIdle()

        fixture.vm.launchModelSelection(path, ProviderType.LITE_RT_LM)
        advanceUntilIdle()
        fixture.vm.sendMessage("hello")
        advanceUntilIdle()
        // The throw hops off Dispatchers.Default — yield, then assert.
        var attempts = 0
        while (fixture.vm.transcriptState.value.error == null && attempts++ < 100) {
            kotlinx.coroutines.delay(10)
        }
        advanceUntilIdle()

        assertThat(fixture.vm.transcriptState.value.error)
            .isInstanceOf(ChatError.Unknown::class.java)
        assertThat((fixture.vm.transcriptState.value.error as ChatError.Unknown).message)
            .isEqualTo("boom")
        assertThat(fixture.vm.connectionState.value.isLoadingModel).isFalse()
        // Selection kept (pending) for retry, draft kept, nothing persisted.
        assertThat(fixture.vm.connectionState.value.selectedLocalModelId).isEqualTo(path)
        assertThat(fixture.vm.inputState.value.inputText).isEqualTo("hello")
        assertThat(fixture.vm.transcriptState.value.messages).isEmpty()
        coVerify(exactly = 0) { fixture.chatRepository.createConversation(any(), any(), any(), any()) }
        coVerify(exactly = 0) { fixture.chatRepository.saveMessage(any(), any()) }
    }

    @Test
    fun `missing file send surfaces download-first with draft kept`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val path = File(tempDir, "ghost.litertlm").absolutePath
        val fixture = buildFixture(listOf(model(path, Instant.EPOCH)), helper = idleHelper())
        runCurrent()
        advanceUntilIdle()

        fixture.vm.launchModelSelection(path, ProviderType.LITE_RT_LM)
        advanceUntilIdle()
        fixture.vm.sendMessage("hello")
        advanceUntilIdle()

        assertThat(fixture.vm.transcriptState.value.error)
            .isEqualTo(ChatError.DownloadModelFirst)
        assertThat(fixture.vm.inputState.value.inputText).isEqualTo("hello")
        assertThat(fixture.vm.transcriptState.value.messages).isEmpty()
        verify(exactly = 0) { fixture.engineManager.switchToLiteRT(any()) }
        coVerify(exactly = 0) { fixture.chatRepository.createConversation(any(), any(), any(), any()) }
    }

    @Test
    fun `local to remote switch unloads explicitly`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val pathA = File(tempDir, "a.litertlm").apply { writeText("fake") }.absolutePath
        val models = listOf(model(pathA, Instant.EPOCH))
        val fixture = buildFixture(models, helper = idleHelper())
        runCurrent()
        advanceUntilIdle()

        // Mount A via a first send so there is an engine to unload.
        fixture.vm.launchModelSelection(pathA, ProviderType.LITE_RT_LM)
        advanceUntilIdle()
        fixture.vm.sendMessage("hi")
        awaitMount(fixture)
        assertThat(fixture.engineManager.getActiveEngine()?.modelPath).isEqualTo(pathA)

        val endpoint = Endpoint(
            id = 9,
            name = "remote",
            url = "http://localhost:11434/",
            apiType = ProviderType.OPENAI,
            modelId = "gpt",
        )
        coEvery { fixture.endpointRepository.getActive() } returns endpoint
        fixture.vm.launchModelSelection("gpt", ProviderType.OPENAI, endpointId = null)
        advanceUntilIdle()

        // The sent turn leaves messages behind, so the switch parks behind
        // the mid-conversation confirm dialog — confirm it to proceed.
        fixture.vm.confirmModelSwitch()
        advanceUntilIdle()

        // Local->remote keeps the explicit unload; local selection cleared.
        // The unload launches on Dispatchers.Default (a real thread under
        // runTest), so verify with a timeout instead of bare advanceUntilIdle.
        verify(timeout = 5000, exactly = 1) { fixture.engineManager.unloadCurrent() }
        assertThat(fixture.selection.localSelection.value.modelId).isNull()
    }
}
