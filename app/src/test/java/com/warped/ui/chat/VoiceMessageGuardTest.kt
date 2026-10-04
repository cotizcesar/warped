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
import com.warped.domain.review.ReviewHelper
import com.warped.ui.chat.voice.RecorderFactory
import com.warped.ui.chat.voice.RecorderHandle
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
 * Phase 67 (VMSG-01/05): minimal voice-send guards. Harness copied from
 * [VoiceDictationTest.buildViewModel], except the model list is populated
 * (so verifiedLocalCapabilities resolves) and the allowlist stub returns
 * a text-only [ModelCapabilities] (audio = false) for the gate test.
 *
 * - Text-only local model + audioBytes hits the error_no_audio backstop.
 * - Blank text with null audio is a no-op (no message, no error).
 * - Cancel after stop deletes the kept clip from disk.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceMessageGuardTest {

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

    private fun textOnlyModel(path: String) = LocalModel(
        id = 1,
        name = path.substringAfterLast("/"),
        filePath = path,
        sizeBytes = 10,
        quantization = "Q4",
        parameterCount = "1B",
        architecture = "test",
        importedAt = Instant.EPOCH,
    )

    private fun buildViewModel(
        helper: LlmModelHelper,
        modelPath: String,
        models: List<LocalModel> = emptyList(),
    ): ChatViewModel {
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
        every { localModelRepository.observeModels() } returns MutableStateFlow(models)
        every { activeModelSelection.activeModel } returns MutableStateFlow(null)
        every { activeModelSelection.localSelection } returns
            MutableStateFlow(LocalSelection(modelId = modelPath, isConnected = true))
        // Engine already serving this model: the send path skips the mount
        // and reaches the capabilities gate directly.
        every { engineManager.getActiveEngine() } returns
            ActiveEngine(EngineType.LITE_RT_LM, modelPath)
        every { activeModelSelection.remoteSelection } returns MutableStateFlow(RemoteSelection())
        every { activeModelSelection.saveLastConversation(any()) } just Runs
        every { advancedPreferences.syntaxTheme } returns flowOf(SyntaxTheme.MONOKAI)
        every { advancedPreferences.codeFontScale } returns flowOf(1.0f)
        every { advancedPreferences.thinkingEnabled } returns flowOf(false)
        every { advancedPreferences.webGroundingEnabled } returns flowOf(false)
        every { providerRouter.resolveLocalHelper(any(), any()) } returns helper
        val fetcher = mockk<com.warped.data.grounding.WebPageFetcher>()
        every { fetcher.cancel() } just Runs
        val multiUrlFetcher = mockk<com.warped.data.grounding.MultiUrlFetcher>()

        // Text-only allowlist: every model resolves audio = false, so the
        // in-path error_no_audio backstop owns the rejection.
        val allowlist = mockk<com.warped.data.repository.ModelAllowlistRepository>()
        every { allowlist.effectiveCapabilities(any()) } returns ModelCapabilities()

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

    private fun idleHelper(): LlmModelHelper {
        val helper = mockk<LlmModelHelper>()
        every { helper.type } returns ProviderType.LITE_RT_LM
        coEvery { helper.initialize(any()) } returns Unit
        coEvery { helper.stopResponse() } returns Unit
        every { helper.runInference(any(), any()) } returns flow { }
        return helper
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
    fun `text-only model plus audioBytes hits the error_no_audio gate`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val path = modelFile.absolutePath
        val vm = buildViewModel(idleHelper(), path, listOf(textOnlyModel(path)))
        runCurrent()
        advanceUntilIdle()

        // Phase 69 Plan 01 (VMSG-04/08): the VM gate blocks gated voice
        // sends with a reason Snackbar BEFORE the turn starts — the legacy
        // model-side error_no_audio backstop no longer fires for
        // known-text-only models (it still guards the fail-open path where
        // capabilities are unknown). The send is a no-op: no message, no
        // error, never generating.
        vm.events.test {
            vm.sendMessage("hello", audioBytes = byteArrayOf(1, 2, 3))
            assertThat(awaitItem()).isInstanceOf(ChatEvent.Snackbar::class.java)
        }
        advanceUntilIdle()

        assertThat(vm.transcriptState.value.messages).isEmpty()
        assertThat(vm.transcriptState.value.error).isNull()
        assertThat(vm.transcriptState.value.isStreaming).isFalse()
        assertThat(vm.inputState.value.isGenerating).isFalse()
    }

    @Test
    fun `blank text with null audio is a no-op`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(idleHelper(), modelFile.absolutePath)
        runCurrent()

        vm.sendMessage("   ")
        advanceUntilIdle()

        assertThat(vm.transcriptState.value.messages).isEmpty()
        assertThat(vm.transcriptState.value.error).isNull()
        assertThat(vm.inputState.value.isGenerating).isFalse()
    }

    @Test
    fun `cancel after stop deletes the kept clip`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(idleHelper(), modelFile.absolutePath)
        runCurrent()
        // Phase 68: stop validates the clip duration off-Main (sub-1 s
        // guard) — inject a long clip so the keep lands, then park on the
        // keep instead of asserting it synchronously.
        vm.voiceDurationReader = { 3_000L }

        val outDir = File(tempDir, "voice")
        val handle = FakeHandle()
        val recorder = VoiceMessageRecorder(
            mockk(relaxed = true),
            outDir,
            object : RecorderFactory {
                override fun create(): RecorderHandle = handle
            },
        )
        vm.voiceRecorderOverride = recorder

        var clip: File? = null
        vm.isVoiceRecording.test {
            assertThat(awaitItem()).isFalse()
            vm.startVoiceRecording()
            // startVoiceRecording hops to Dispatchers.IO (a real thread
            // under runTest) — awaitItem parks until the flip lands.
            assertThat(awaitItem()).isTrue()

            // Simulate the platform having written the clip file.
            clip = File(requireNotNull(handle.outputPath))
            val clipFile = requireNotNull(clip)
            clipFile.parentFile?.mkdirs()
            clipFile.writeText("fake-audio")

            vm.stopVoiceRecording()
            assertThat(awaitItem()).isFalse()
        }
        // Park until the IO-side guard keeps the clip.
        vm.hasVoiceClip.test {
            var kept = awaitItem()
            while (!kept) kept = awaitItem()
        }
        assertThat(vm.hasVoiceClip.value).isTrue()
        assertThat(requireNotNull(clip).exists()).isTrue()

        // Cancel after stop discards the kept clip from disk.
        vm.cancelVoiceRecording()
        assertThat(vm.hasVoiceClip.value).isFalse()
        assertThat(requireNotNull(clip).exists()).isFalse()
    }
}
