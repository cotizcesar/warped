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
import com.warped.ui.chat.voice.RecorderFactory
import com.warped.ui.chat.voice.RecorderHandle
import com.warped.ui.chat.voice.VoiceDictationManager
import com.warped.ui.chat.voice.VoiceMessageRecorder
import io.mockk.coEvery
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
 * Phase 69 Plan 02 (VMSG-07): parallel transcript STT session discipline,
 * buffer accumulation, and send-time stamping. The platform callbacks are
 * driven directly (internal handlers — same functions the recognizer
 * invokes); session start/stop wiring is verified against a MockK manager.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceTranscriptTest {

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

    private fun FakeTranscript(): VoiceDictationManager {
        val fake = mockk<VoiceDictationManager>()
        every { fake.start() } returns true
        every { fake.stop() } just Runs
        every { fake.destroy() } just Runs
        return fake
    }

    private fun recordingVm(audioPath: String, outName: String): Pair<ChatViewModel, FakeHandle> {
        val vm = buildViewModel(audioPath)
        vm.voiceDurationReader = { 3_000L }
        val handle = FakeHandle()
        vm.voiceRecorderOverride = VoiceMessageRecorder(
            mockk(relaxed = true),
            File(tempDir, outName),
            object : RecorderFactory {
                override fun create(): RecorderHandle = handle
            },
        )
        return vm to handle
    }

    @Test
    fun `partial replaces, final appends, duplicate final skipped`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val audioFile = File(tempDir, "audio.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(audioFile.absolutePath)

        vm.onTranscriptPartial("hel")
        assertThat(vm.voiceTranscriptLive.value).isEqualTo("hel")
        // Latest hypothesis replaces — never appends partials.
        vm.onTranscriptPartial("hello")
        assertThat(vm.voiceTranscriptLive.value).isEqualTo("hello")

        vm.onTranscriptFinal("hello")
        assertThat(vm.voiceTranscriptLive.value).isEqualTo("hello")
        // Duplicate delivery is skipped (already the suffix).
        vm.onTranscriptFinal("hello")
        assertThat(vm.voiceTranscriptLive.value).isEqualTo("hello")

        vm.onTranscriptFinal("world")
        assertThat(vm.voiceTranscriptLive.value).isEqualTo("hello world")

        // Blank finals never pollute the buffer.
        vm.onTranscriptFinal("   ")
        assertThat(vm.voiceTranscriptLive.value).isEqualTo("hello world")
    }

    @Test
    fun `error mid-session degrades holder to null, recording proceeds`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val audioFile = File(tempDir, "audio.litertlm").apply { writeText("fake") }
        val audioPath = audioFile.absolutePath
        modelsFlow.value = listOf(localModel(audioPath))
        audioCapablePaths = setOf(audioPath)
        val (vm, _) = recordingVm(audioPath, "voice-err")
        val transcript = FakeTranscript()
        vm.transcriptManagerOverride = transcript
        localSelection.value = LocalSelection(modelId = audioPath, isConnected = true)
        advanceUntilIdle()

        vm.isVoiceRecording.test {
            assertThat(awaitItem()).isFalse()
            vm.startVoiceRecording()
            assertThat(awaitItem()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
        verify { transcript.start() }

        // Genuine partials arrived, then the recognizer errored.
        vm.onTranscriptPartial("hola")
        assertThat(vm.voiceTranscriptLive.value).isEqualTo("hola")
        vm.onTranscriptError(7)

        // Stopping freezes a null holder (duration-only fallback renders);
        // the recording itself stopped normally.
        vm.stopVoiceRecording()
        assertThat(vm.isVoiceRecording.value).isFalse()
        assertThat(vm.lastSentVoiceTranscript).isNull()
        verify { transcript.stop() }

        vm.cancelVoiceRecording()
        advanceUntilIdle()
    }

    @Test
    fun `stop freezes buffer into holder`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val audioFile = File(tempDir, "audio.litertlm").apply { writeText("fake") }
        val audioPath = audioFile.absolutePath
        modelsFlow.value = listOf(localModel(audioPath))
        audioCapablePaths = setOf(audioPath)
        val (vm, _) = recordingVm(audioPath, "voice-freeze")
        vm.transcriptManagerOverride = FakeTranscript()
        localSelection.value = LocalSelection(modelId = audioPath, isConnected = true)
        advanceUntilIdle()

        vm.isVoiceRecording.test {
            assertThat(awaitItem()).isFalse()
            vm.startVoiceRecording()
            assertThat(awaitItem()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }

        vm.onTranscriptPartial("hello")
        vm.onTranscriptFinal("hello world")
        vm.stopVoiceRecording()
        assertThat(vm.lastSentVoiceTranscript).isEqualTo("hello world")

        vm.cancelVoiceRecording()
        advanceUntilIdle()
    }

    @Test
    fun `cancel clears holder and buffer`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val audioFile = File(tempDir, "audio.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(audioFile.absolutePath)

        vm.lastSentVoiceTranscript = "stale"
        vm.onTranscriptPartial("stale-buffer")
        assertThat(vm.voiceTranscriptLive.value).isEqualTo("stale-buffer")

        vm.cancelVoiceRecording()

        assertThat(vm.lastSentVoiceTranscript).isNull()
        assertThat(vm.voiceTranscriptLive.value).isEmpty()
    }

    @Test
    fun `stamp lands on voice message and null on text messages`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val audioFile = File(tempDir, "audio.litertlm").apply { writeText("fake") }
        val audioPath = audioFile.absolutePath
        modelsFlow.value = listOf(localModel(audioPath))
        audioCapablePaths = setOf(audioPath)
        val vm = buildViewModel(audioPath)
        localSelection.value = LocalSelection(modelId = audioPath, isConnected = true)
        advanceUntilIdle()

        vm.lastSentVoiceTranscript = "hola mundo"
        vm.sendMessage("caption", audioBytes = byteArrayOf(1, 2, 3))
        assertThat(vm.transcriptState.value.messages).hasSize(1)
        assertThat(vm.transcriptState.value.messages.first().transcript).isEqualTo("hola mundo")
        assertThat(vm.lastSentVoiceTranscript).isNull()
        advanceUntilIdle()

        vm.sendMessage("plain text")
        advanceUntilIdle()
        assertThat(vm.transcriptState.value.messages).hasSize(2)
        assertThat(vm.transcriptState.value.messages.last().transcript).isNull()
    }

    @Test
    fun `startDictation ends transcript session`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val audioFile = File(tempDir, "audio.litertlm").apply { writeText("fake") }
        val audioPath = audioFile.absolutePath
        modelsFlow.value = listOf(localModel(audioPath))
        audioCapablePaths = setOf(audioPath)
        val (vm, _) = recordingVm(audioPath, "voice-mutex")
        val transcript = FakeTranscript()
        vm.transcriptManagerOverride = transcript
        val dictation = mockk<VoiceDictationManager>()
        every { dictation.start() } returns true
        every { dictation.stop() } just Runs
        vm.dictationManagerOverride = dictation
        localSelection.value = LocalSelection(modelId = audioPath, isConnected = true)
        advanceUntilIdle()

        vm.isVoiceRecording.test {
            assertThat(awaitItem()).isFalse()
            vm.startVoiceRecording()
            assertThat(awaitItem()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
        verify { transcript.start() }

        // Dictation and transcript STT never overlap: starting dictation
        // stops the transcript session first.
        vm.startDictation()
        verify { transcript.stop() }
        assertThat(vm.isListening.value).isTrue()

        vm.cancelVoiceRecording()
        advanceUntilIdle()
    }
}
