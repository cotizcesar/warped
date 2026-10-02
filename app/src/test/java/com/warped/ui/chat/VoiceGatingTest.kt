package com.warped.ui.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.warped.data.local.inference.ActiveEngine
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.EngineType
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.local.preferences.AdvancedPreferences
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
import com.warped.ui.chat.voice.GateState
import com.warped.ui.chat.voice.RecorderFactory
import com.warped.ui.chat.voice.RecorderHandle
import com.warped.ui.chat.voice.VoiceDictationManager
import com.warped.ui.chat.voice.VoiceMessageRecorder
import io.mockk.coEvery
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
 * Phase 69 Plan 01 (VMSG-04/08 + VMSG-03 exclusion): VM gate flow,
 * draft-kept send block, and mutual-exclusion locks. Fixture follows
 * [VoiceMessageGuardTest] with controllable selection flows (live flip)
 * and a per-model allowlist stub.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceGatingTest {

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

    private val localSelection = MutableStateFlow(LocalSelection())
    private val remoteSelection = MutableStateFlow(RemoteSelection())
    private val modelsFlow = MutableStateFlow<List<LocalModel>>(emptyList())
    private var audioCapablePaths: Set<String> = emptySet()

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
        every { context.getString(com.warped.R.string.voice_msg_gate_audio) } returns "AUDIO-REASON"
        every { context.getString(com.warped.R.string.voice_msg_gate_remote) } returns "REMOTE-REASON"

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
            ddgSearchRepository = mockk(),
            modelAllowlistRepository = allowlist,
            context = context,
            reviewHelper = mockk(relaxed = true),
        )
    }

    private class FakeHandle : RecorderHandle {
        var outputPath: String? = null
        override fun setAudioSource(source: Int) = Unit
        override fun setOutputFormat(format: Int) = Unit
        override fun setAudioEncoder(encoder: Int) = Unit
        override fun setAudioEncodingBitRate(bitRate: Int) = Unit
        override fun setAudioSamplingRate(rate: Int) = Unit
        override fun setOutputFile(path: String) { outputPath = path }
        override fun prepare() = Unit
        override fun start() = Unit
        override fun stop() = Unit
        override fun getMaxAmplitude(): Int = 0
        override fun release() = Unit
    }

    @Test
    fun `gate flips live on model switch`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val audioFile = File(tempDir, "audio.litertlm").apply { writeText("fake") }
        val textFile = File(tempDir, "text.litertlm").apply { writeText("fake") }
        val audioPath = audioFile.absolutePath
        val textPath = textFile.absolutePath
        modelsFlow.value = listOf(localModel(audioPath), localModel(textPath))
        audioCapablePaths = setOf(audioPath)
        val vm = buildViewModel(audioPath)

        localSelection.value = LocalSelection(modelId = audioPath, isConnected = true)
        advanceUntilIdle()
        assertThat(vm.voiceSendGate.value).isEqualTo(GateState.Allowed)

        localSelection.value = LocalSelection(modelId = textPath, isConnected = true)
        advanceUntilIdle()
        assertThat(vm.voiceSendGate.value).isEqualTo(GateState.GatedTextOnly)

        localSelection.value = LocalSelection()
        remoteSelection.value = RemoteSelection(
            modelId = "gpt-x",
            providerType = ProviderType.OPENAI,
        )
        advanceUntilIdle()
        assertThat(vm.voiceSendGate.value).isEqualTo(GateState.GatedRemote)

        remoteSelection.value = RemoteSelection()
        localSelection.value = LocalSelection(modelId = audioPath, isConnected = true)
        advanceUntilIdle()
        assertThat(vm.voiceSendGate.value).isEqualTo(GateState.Allowed)
    }

    @Test
    fun `gated voice send emits reason and preserves holders`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val textFile = File(tempDir, "text.litertlm").apply { writeText("fake") }
        val textPath = textFile.absolutePath
        modelsFlow.value = listOf(localModel(textPath))
        audioCapablePaths = emptySet()
        val vm = buildViewModel(textPath)

        localSelection.value = LocalSelection(modelId = textPath, isConnected = true)
        advanceUntilIdle()
        assertThat(vm.voiceSendGate.value).isEqualTo(GateState.GatedTextOnly)

        vm.lastSentVoicePath = "/tmp/clip.m4a"
        vm.lastSentVoiceDurationMs = 3_000L
        vm.events.test {
            vm.sendMessage("hi", audioBytes = byteArrayOf(1, 2, 3))
            assertThat(awaitItem()).isEqualTo(ChatEvent.Snackbar("AUDIO-REASON"))
        }
        assertThat(vm.transcriptState.value.messages).isEmpty()
        assertThat(vm.lastSentVoicePath).isEqualTo("/tmp/clip.m4a")
        assertThat(vm.lastSentVoiceDurationMs).isEqualTo(3_000L)
    }

    @Test
    fun `gated remote voice send emits remote reason`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = buildViewModel("/tmp/nowhere.litertlm")
        advanceUntilIdle()

        localSelection.value = LocalSelection()
        remoteSelection.value = RemoteSelection(
            modelId = "ollama-model",
            providerType = ProviderType.OLLAMA,
        )
        advanceUntilIdle()
        assertThat(vm.voiceSendGate.value).isEqualTo(GateState.GatedRemote)

        vm.events.test {
            vm.sendMessage("hi", audioBytes = byteArrayOf(1, 2, 3))
            assertThat(awaitItem()).isEqualTo(ChatEvent.Snackbar("REMOTE-REASON"))
        }
        assertThat(vm.transcriptState.value.messages).isEmpty()
    }

    @Test
    fun `text send on gated config proceeds and keeps holders`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val textFile = File(tempDir, "text.litertlm").apply { writeText("fake") }
        val textPath = textFile.absolutePath
        modelsFlow.value = listOf(localModel(textPath))
        audioCapablePaths = emptySet()
        val vm = buildViewModel(textPath)

        localSelection.value = LocalSelection(modelId = textPath, isConnected = true)
        advanceUntilIdle()
        assertThat(vm.voiceSendGate.value).isEqualTo(GateState.GatedTextOnly)

        vm.lastSentVoicePath = "/tmp/clip.m4a"
        vm.lastSentVoiceDurationMs = 3_000L
        vm.sendMessage("hello")
        advanceUntilIdle()

        assertThat(vm.transcriptState.value.messages).hasSize(1)
        assertThat(vm.transcriptState.value.messages.first().audioPath).isNull()
        assertThat(vm.lastSentVoicePath).isEqualTo("/tmp/clip.m4a")
        assertThat(vm.lastSentVoiceDurationMs).isEqualTo(3_000L)
    }

    @Test
    fun `startVoiceRecording with dictation live ends listening`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val audioFile = File(tempDir, "audio.litertlm").apply { writeText("fake") }
        val audioPath = audioFile.absolutePath
        modelsFlow.value = listOf(localModel(audioPath))
        audioCapablePaths = setOf(audioPath)
        val vm = buildViewModel(audioPath)
        localSelection.value = LocalSelection(modelId = audioPath, isConnected = true)
        advanceUntilIdle()

        val dictation = mockk<VoiceDictationManager>()
        every { dictation.start() } returns true
        every { dictation.stop() } just Runs
        vm.dictationManagerOverride = dictation

        vm.voiceDurationReader = { 3_000L }
        val outDir = File(tempDir, "voice")
        val recorder = VoiceMessageRecorder(
            mockk(relaxed = true),
            outDir,
            object : RecorderFactory {
                override fun create(): RecorderHandle = FakeHandle()
            },
        )
        vm.voiceRecorderOverride = recorder

        vm.startDictation()
        assertThat(vm.isListening.value).isTrue()

        vm.isVoiceRecording.test {
            assertThat(awaitItem()).isFalse()
            vm.startVoiceRecording()
            // stopDictation() runs synchronously before the IO launch.
            assertThat(vm.isListening.value).isFalse()
            // Park until the IO side accepts (all disk activity settles
            // before test end so @TempDir cleanup never races mkdirs).
            assertThat(awaitItem()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
        vm.cancelVoiceRecording()
        assertThat(vm.isVoiceRecording.value).isFalse()
    }

    @Test
    fun `startDictation with recording live ends recording`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val audioFile = File(tempDir, "audio.litertlm").apply { writeText("fake") }
        val audioPath = audioFile.absolutePath
        modelsFlow.value = listOf(localModel(audioPath))
        audioCapablePaths = setOf(audioPath)
        val vm = buildViewModel(audioPath)
        localSelection.value = LocalSelection(modelId = audioPath, isConnected = true)
        advanceUntilIdle()

        val dictation = mockk<VoiceDictationManager>()
        every { dictation.start() } returns true
        every { dictation.stop() } just Runs
        vm.dictationManagerOverride = dictation

        vm.voiceDurationReader = { 3_000L }
        val outDir = File(tempDir, "voice2")
        val handle = FakeHandle()
        val recorder = VoiceMessageRecorder(
            mockk(relaxed = true),
            outDir,
            object : RecorderFactory {
                override fun create(): RecorderHandle = handle
            },
        )
        vm.voiceRecorderOverride = recorder

        vm.isVoiceRecording.test {
            assertThat(awaitItem()).isFalse()
            vm.startVoiceRecording()
            assertThat(awaitItem()).isTrue()

            vm.startDictation()
            // stopVoiceRecording() flips the flag synchronously.
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(vm.isListening.value).isTrue()
        vm.cancelVoiceRecording()
    }
}
