package com.warped.ui.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.warped.data.local.inference.ActiveEngine
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.EngineType
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.local.preferences.VoicePreferences
import com.warped.data.remote.provider.ProviderRouter
import com.warped.domain.llm.LlmModelHelper
import com.warped.domain.model.LocalModel
import com.warped.domain.model.LocalSelection
import com.warped.domain.model.ModelCapabilities
import com.warped.domain.model.ParameterStore
import com.warped.domain.model.ProviderType
import com.warped.domain.model.RemoteSelection
import com.warped.domain.model.SyntaxTheme
import com.warped.domain.repository.ChatRepository
import com.warped.domain.repository.EndpointRepository
import com.warped.domain.repository.LocalModelRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import io.mockk.mockk
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
 * Phase 69 Plan 03 (VMSG-03): coachmark flag discipline + gate combination.
 * The preferences store is a MockK fake whose write flips the seen flow
 * (mirroring DataStore persistence); a throwing fake covers persist
 * failure. Fixture follows [VoiceGatingTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceCoachmarkTest {

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @TempDir
    lateinit var tempDir: File

    private val localSelection = MutableStateFlow(LocalSelection())
    private val remoteSelection = MutableStateFlow(RemoteSelection())
    private val modelsFlow = MutableStateFlow<List<LocalModel>>(emptyList())
    private var audioCapablePaths: Set<String> = emptySet()
    private val seenFlow = MutableStateFlow(false)
    private lateinit var voicePrefs: VoicePreferences

    private fun localModel(path: String) = LocalModel(
        id = path.hashCode().toLong(),
        name = path.substringAfterLast("/"),
        filePath = path,
        sizeBytes = 10,
        quantization = "Q4",
        parameterCount = "1B",
        architecture = "test",
        importedAt = Instant.EPOCH,
    )

    private fun buildViewModel(enginePath: String): ChatViewModel {
        val chatRepository = mockk<ChatRepository>()
        val endpointRepository = mockk<EndpointRepository>()
        val localModelRepository = mockk<LocalModelRepository>()
        val activeModelSelection = mockk<com.warped.domain.model.ActiveModelSelection>()
        val providerRouter = mockk<ProviderRouter>()
        val engineManager = mockk<EngineManager>()
        val memoryChecker = mockk<MemoryChecker>()
        val advancedPreferences = mockk<AdvancedPreferences>()
        val context = mockk<Context>()
        every { context.getString(any<Int>()) } returns ""
        every { context.getString(any<Int>(), *anyVararg<Any>()) } returns ""

        every { chatRepository.observeConversations() } returns MutableStateFlow(emptyList())
        coEvery { chatRepository.createConversation(any(), any(), any(), any()) } returns 42L
        coEvery { chatRepository.saveMessage(any(), any()) } returns Unit
        every { endpointRepository.observeEndpoints() } returns MutableStateFlow(emptyList())
        every { localModelRepository.observeModels() } returns modelsFlow
        every { activeModelSelection.activeModel } returns MutableStateFlow(null)
        every { activeModelSelection.localSelection } returns localSelection
        every { engineManager.getActiveEngine() } returns
            ActiveEngine(EngineType.LITE_RT_LM, enginePath)
        every { activeModelSelection.remoteSelection } returns remoteSelection
        every { activeModelSelection.saveLastConversation(any()) } just Runs
        every { advancedPreferences.syntaxTheme } returns flowOf(SyntaxTheme.MONOKAI)
        every { advancedPreferences.codeFontScale } returns flowOf(1.0f)
        every { advancedPreferences.thinkingEnabled } returns flowOf(false)
        every { advancedPreferences.webGroundingEnabled } returns flowOf(false)
        val helper = mockk<LlmModelHelper>()
        every { helper.type } returns ProviderType.LITE_RT_LM
        coEvery { helper.initialize(any()) } returns Unit
        coEvery { helper.stopResponse() } returns Unit
        every { helper.runInference(any(), any()) } returns flow { }
        every { providerRouter.resolveLocalHelper(any(), any()) } returns helper
        val fetcher = mockk<com.warped.data.grounding.WebPageFetcher>()
        every { fetcher.cancel() } just Runs
        val multiUrlFetcher = mockk<com.warped.data.grounding.MultiUrlFetcher>()

        val allowlist = mockk<com.warped.data.repository.ModelAllowlistRepository>()
        every { allowlist.effectiveCapabilities(any()) } answers {
            ModelCapabilities(audio = firstArg<LocalModel>().filePath in audioCapablePaths)
        }

        voicePrefs = mockk()
        every { voicePrefs.voiceCoachmarkSeen } returns seenFlow
        coEvery { voicePrefs.markVoiceCoachmarkSeen() } answers { seenFlow.value = true }

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
            voicePreferences = voicePrefs,
            fetcher = fetcher,
            multiUrlFetcher = multiUrlFetcher,
            ddgSearchRepository = mockk(),
            modelAllowlistRepository = allowlist,
            context = context,
            reviewHelper = mockk(relaxed = true),
        )
    }

    @Test
    fun `unseen plus Allowed shows coachmark`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val audioFile = File(tempDir, "audio.litertlm").apply { writeText("fake") }
        val audioPath = audioFile.absolutePath
        modelsFlow.value = listOf(localModel(audioPath))
        audioCapablePaths = setOf(audioPath)
        seenFlow.value = false
        val vm = buildViewModel(audioPath)

        localSelection.value = LocalSelection(modelId = audioPath, isConnected = true)
        advanceUntilIdle()

        assertThat(vm.showVoiceCoachmark.value).isTrue()
    }

    @Test
    fun `seen never re-shows and dismiss persists`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val audioFile = File(tempDir, "audio.litertlm").apply { writeText("fake") }
        val audioPath = audioFile.absolutePath
        modelsFlow.value = listOf(localModel(audioPath))
        audioCapablePaths = setOf(audioPath)
        seenFlow.value = false
        val vm = buildViewModel(audioPath)

        localSelection.value = LocalSelection(modelId = audioPath, isConnected = true)
        advanceUntilIdle()
        assertThat(vm.showVoiceCoachmark.value).isTrue()

        vm.dismissVoiceCoachmark()
        // The persist runs on Dispatchers.IO (a real thread under runTest)
        // — rendezvous with the mock call in real time, then settle the
        // virtual scheduler for the combine propagation.
        coVerify(timeout = 3_000) { voicePrefs.markVoiceCoachmarkSeen() }
        advanceUntilIdle()

        assertThat(seenFlow.value).isTrue()
        assertThat(vm.showVoiceCoachmark.value).isFalse()

        // Re-collecting (new subscriber) still hides it — never re-shows.
        runCurrent()
        advanceUntilIdle()
        assertThat(vm.showVoiceCoachmark.value).isFalse()
    }

    @Test
    fun `gated configs hide coachmark even when unseen`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val textFile = File(tempDir, "text.litertlm").apply { writeText("fake") }
        val textPath = textFile.absolutePath
        modelsFlow.value = listOf(localModel(textPath))
        audioCapablePaths = emptySet()
        seenFlow.value = false
        val vm = buildViewModel(textPath)

        localSelection.value = LocalSelection(modelId = textPath, isConnected = true)
        advanceUntilIdle()
        assertThat(vm.showVoiceCoachmark.value).isFalse()

        localSelection.value = LocalSelection()
        remoteSelection.value = RemoteSelection(
            modelId = "gpt-x",
            providerType = ProviderType.OPENAI,
        )
        advanceUntilIdle()
        assertThat(vm.showVoiceCoachmark.value).isFalse()
    }

    @Test
    fun `persist failure degrades to re-show, never a crash`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val audioFile = File(tempDir, "audio.litertlm").apply { writeText("fake") }
        val audioPath = audioFile.absolutePath
        modelsFlow.value = listOf(localModel(audioPath))
        audioCapablePaths = setOf(audioPath)
        seenFlow.value = false
        val vm = buildViewModel(audioPath)

        localSelection.value = LocalSelection(modelId = audioPath, isConnected = true)
        advanceUntilIdle()
        assertThat(vm.showVoiceCoachmark.value).isTrue()

        coEvery { voicePrefs.markVoiceCoachmarkSeen() } throws RuntimeException("disk gone")
        vm.dismissVoiceCoachmark()
        coVerify(timeout = 3_000) { voicePrefs.markVoiceCoachmarkSeen() }
        advanceUntilIdle()

        // Failed persist only re-shows once — no crash, still visible.
        assertThat(vm.showVoiceCoachmark.value).isTrue()
    }
}
