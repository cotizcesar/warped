package com.warped.ui.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.warped.data.grounding.MultiUrlResult
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.remote.provider.ProviderRouter
import com.warped.domain.llm.LlmModelHelper
import com.warped.domain.model.GroundedSourceStatus
import com.warped.domain.model.LocalSelection
import com.warped.domain.model.ParameterStore
import com.warped.domain.model.ProviderType
import com.warped.domain.model.RemoteSelection
import com.warped.domain.model.Role
import com.warped.domain.model.StreamToken
import com.warped.domain.model.SyntaxTheme
import com.warped.domain.repository.ChatRepository
import com.warped.domain.repository.EndpointRepository
import com.warped.domain.repository.LocalModelRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
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

/**
 * Phase 53 (TOGGLE-01..03/SRC-02): ChatViewModel grounding-hook wiring.
 *
 * The hook evaluates shouldGround(skipOnce, perChat, global) at the top of
 * the fetch block: the Sin web chip skips fetch for one send and resets,
 * a per-chat No skips fetch despite the global ON, Heredar inherits the
 * global ON (fetch runs, ok + omitida details persist via
 * saveMessageWithSources), and a pre-conversation toggle is held pending
 * until the first send creates the row.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatGroundingToggleTest {

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

    private lateinit var chatRepository: ChatRepository
    private lateinit var multiUrlFetcher: com.warped.data.grounding.MultiUrlFetcher

    private fun buildViewModel(modelPath: String): ChatViewModel {
        chatRepository = mockk()
        val endpointRepository = mockk<EndpointRepository>()
        val localModelRepository = mockk<LocalModelRepository>()
        val activeModelSelection = mockk<com.warped.domain.model.ActiveModelSelection>()
        val providerRouter = mockk<ProviderRouter>()
        val engineManager = mockk<EngineManager>()
        val memoryChecker = mockk<MemoryChecker>()
        val advancedPreferences = mockk<AdvancedPreferences>()
        val context = mockk<Context>()

        every { chatRepository.observeConversations() } returns MutableStateFlow(emptyList())
        coEvery { chatRepository.createConversation(any(), any(), any(), any()) } returns 42L
        coEvery { chatRepository.saveMessage(any(), any()) } just Runs
        coEvery { chatRepository.saveMessageWithSources(any(), any(), any()) } returns 99L
        coEvery { chatRepository.getWebOverride(any()) } returns null
        coEvery { chatRepository.setWebOverride(any(), any()) } just Runs
        every { endpointRepository.observeEndpoints() } returns MutableStateFlow(emptyList())
        every { localModelRepository.observeModels() } returns MutableStateFlow(emptyList())
        every { activeModelSelection.activeModel } returns MutableStateFlow(null)
        every { activeModelSelection.localSelection } returns
            MutableStateFlow(LocalSelection(modelId = modelPath, isConnected = true))
        every { activeModelSelection.remoteSelection } returns MutableStateFlow(RemoteSelection())
        every { activeModelSelection.saveLastConversation(any()) } just Runs
        every { advancedPreferences.syntaxTheme } returns flowOf(SyntaxTheme.MONOKAI)
        every { advancedPreferences.codeFontScale } returns flowOf(1.0f)
        every { advancedPreferences.thinkingEnabled } returns flowOf(false)
        every { advancedPreferences.webGroundingEnabled } returns flowOf(true)
        every { providerRouter.resolveLocalHelper(any(), any()) } returns answeringHelper()
        val fetcher = mockk<com.warped.data.grounding.WebPageFetcher>()
        every { fetcher.cancel() } just Runs
        multiUrlFetcher = mockk()

        return ChatViewModel(
            chatRepository = chatRepository,
            endpointRepository = endpointRepository,
            localModelRepository = localModelRepository,
            activeModelSelection = activeModelSelection,
            providerRouter = providerRouter,
            savedStateHandle = SavedStateHandle(),
            parameterStore = ParameterStore(),
            engineManager = engineManager,
            memoryChecker = memoryChecker,
            advancedPreferences = advancedPreferences,
            fetcher = fetcher,
            multiUrlFetcher = multiUrlFetcher,
            context = context,
        )
    }

    private fun answeringHelper(): LlmModelHelper {
        val helper = mockk<LlmModelHelper>()
        every { helper.type } returns ProviderType.LITE_RT_LM
        coEvery { helper.initialize(any()) } just Runs
        coEvery { helper.stopResponse() } just Runs
        every { helper.runInference(any(), any()) } returns flow {
            emit(StreamToken.Delta("hola"))
            emit(StreamToken.Done())
        }
        return helper
    }

    private fun fusedResult() = MultiUrlResult.Fused(
        block = "BLOQUE",
        okUrls = listOf("https://a.example/uno"),
        skippedUrls = listOf("https://dead.example/x"),
        pageTexts = mapOf("https://a.example/uno" to "Texto a."),
    )

    @Test
    fun `skipWebOnce skips fetch and resets after send`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()
        coEvery { multiUrlFetcher.fetchAll(any(), any(), any()) } returns fusedResult()

        vm.toggleSkipWebOnce()
        assertThat(vm.inputState.value.skipWebOnce).isTrue()

        vm.sendMessage("mira https://a.example/uno")
        advanceUntilIdle()

        coVerify(exactly = 0) { multiUrlFetcher.fetchAll(any(), any(), any()) }
        assertThat(vm.inputState.value.skipWebOnce).isFalse()
        assertThat(
            vm.transcriptState.value.messages.any { it.role == Role.ASSISTANT && it.content == "hola" },
        ).isTrue()
    }

    @Test
    fun `per-chat No skips fetch despite global on`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()
        coEvery { multiUrlFetcher.fetchAll(any(), any(), any()) } returns fusedResult()
        coEvery { chatRepository.getWebOverride(any()) } returns false

        vm.sendMessage("mira https://a.example/uno")
        advanceUntilIdle()

        coVerify(exactly = 0) { multiUrlFetcher.fetchAll(any(), any(), any()) }
        assertThat(
            vm.transcriptState.value.messages.any { it.role == Role.ASSISTANT && it.content == "hola" },
        ).isTrue()
    }

    @Test
    fun `inherit with global on fetches and persists ok plus omitida details`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()
        coEvery { multiUrlFetcher.fetchAll(any(), any(), any()) } returns fusedResult()

        vm.sendMessage("mira https://a.example/uno y https://dead.example/x")
        advanceUntilIdle()

        coVerify(exactly = 1) { multiUrlFetcher.fetchAll(any(), any(), any()) }
        val sourcesSlot = slot<List<com.warped.domain.model.GroundedSource>>()
        coVerify(exactly = 1) {
            chatRepository.saveMessageWithSources(42L, any(), capture(sourcesSlot))
        }
        val sources = sourcesSlot.captured
        assertThat(sources.map { it.url }).containsExactly(
            "https://a.example/uno",
            "https://dead.example/x",
        ).inOrder()
        assertThat(sources[0].status).isEqualTo(GroundedSourceStatus.OK)
        assertThat(sources[0].extractedText).isEqualTo("Texto a.")
        assertThat(sources[1].status).isEqualTo(GroundedSourceStatus.OMITIDA)
        assertThat(sources[1].extractedText).isNull()
    }

    @Test
    fun `toggle before first send is held pending and applied at conversation creation`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(modelFile.absolutePath)
        runCurrent()

        vm.setWebOverride(true)
        assertThat(vm.connectionState.value.webOverride).isTrue()

        vm.sendMessage("hola sin urls")
        advanceUntilIdle()

        coVerify { chatRepository.setWebOverride(42L, true) }
    }
}
