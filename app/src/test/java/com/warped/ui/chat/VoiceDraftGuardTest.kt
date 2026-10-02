package com.warped.ui.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.cash.turbine.testIn
import app.cash.turbine.turbineScope
import com.google.common.truth.Truth.assertThat
import com.warped.R
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
import com.warped.ui.chat.voice.PlayerFactory
import com.warped.ui.chat.voice.PlayerHandle
import com.warped.ui.chat.voice.RecorderFactory
import com.warped.ui.chat.voice.RecorderHandle
import com.warped.ui.chat.voice.VoiceMessagePlayer
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
 * Phase 68 (VMSG-02): draft-playback guard tests. Harness mirrors
 * [VoiceMessageGuardTest] (same ViewModel builder + fake recorder), plus a
 * fake player behind [ChatViewModel.voicePlayerOverride] and fixed
 * durations behind [ChatViewModel.voiceDurationReader] (MediaMetadataRetriever
 * is framework-only and unavailable under JVM unit tests).
 *
 * - Sub-1 s stop deletes the file and emits the too-short Snackbar.
 * - The 60 s auto-stop path rejects an identical short clip (same choke).
 * - A long clip is kept (file + hasVoiceClip + duration).
 * - deleteVoiceDraft removes the file and clears hasVoiceClip.
 * - Play-then-pause keeps the position; completion resets it to 0.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceDraftGuardTest {

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
        every { context.getString(R.string.voice_msg_too_short) } returns "TOO_SHORT"

        every { chatRepository.observeConversations() } returns MutableStateFlow(emptyList())
        coEvery { chatRepository.createConversation(any(), any(), any(), any()) } returns 42L
        coEvery { chatRepository.saveMessage(any(), any()) } returns Unit
        every { endpointRepository.observeEndpoints() } returns MutableStateFlow(emptyList())
        every { localModelRepository.observeModels() } returns MutableStateFlow(models)
        every { activeModelSelection.activeModel } returns MutableStateFlow(null)
        every { activeModelSelection.localSelection } returns
            MutableStateFlow(LocalSelection(modelId = modelPath, isConnected = true))
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

    private class FakeRecorderHandle : RecorderHandle {
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

    private class FakePlayerHandle : PlayerHandle {
        var starts = 0
        var pauses = 0
        var stops = 0
        var playing = false
        var position = 1_500
        var completion: (() -> Unit)? = null
        var error: (() -> Boolean)? = null
        override fun setDataSource(path: String) = Unit
        override fun prepare() = Unit
        override fun start() {
            starts++
            playing = true
        }
        override fun pause() {
            pauses++
            playing = false
        }
        override fun stop() {
            stops++
            playing = false
        }
        override fun release() = Unit
        override fun seekTo(ms: Int) { position = ms }
        override fun getCurrentPosition(): Int = position
        override fun getDuration(): Int = 5_000
        override fun isPlaying(): Boolean = playing
        override fun setOnCompletionListener(listener: (() -> Unit)?) { completion = listener }
        override fun setOnErrorListener(listener: (() -> Boolean)?) { error = listener }
        fun fireCompletion() = completion?.invoke()
    }

    private val playerHandles = mutableListOf<FakePlayerHandle>()
    private val playerFactory = object : PlayerFactory {
        override fun create(): PlayerHandle =
            FakePlayerHandle().also { playerHandles.add(it) }
    }

    private fun attachRecorder(vm: ChatViewModel, outDir: File): FakeRecorderHandle {
        val handle = FakeRecorderHandle()
        val recorder = VoiceMessageRecorder(
            mockk(relaxed = true),
            outDir,
            object : RecorderFactory {
                override fun create(): RecorderHandle = handle
            },
        )
        vm.voiceRecorderOverride = recorder
        return handle
    }

    private fun attachPlayer(vm: ChatViewModel) {
        playerHandles.clear()
        vm.voicePlayerOverride = VoiceMessagePlayer(mockk(relaxed = true), playerFactory)
    }

    /** Record a clip through the fake recorder, leaving the file on disk. */
    private suspend fun recordClip(
        vm: ChatViewModel,
        handle: FakeRecorderHandle,
        stop: ChatViewModel.() -> Unit,
    ): File {
        vm.isVoiceRecording.test {
            assertThat(awaitItem()).isFalse()
            vm.startVoiceRecording()
            // startVoiceRecording hops to Dispatchers.IO (a real thread
            // under runTest) — awaitItem parks until the flip lands.
            assertThat(awaitItem()).isTrue()
            val clip = File(requireNotNull(handle.outputPath))
            clip.parentFile?.mkdirs()
            clip.writeText("fake-audio")
            vm.stop()
            assertThat(awaitItem()).isFalse()
        }
        return File(requireNotNull(handle.outputPath))
    }

    @Test
    fun `sub-1s manual stop deletes the file and emits the too-short snackbar`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(idleHelper(), modelFile.absolutePath)
        runCurrent()
        vm.voiceDurationReader = { 500L }
        val handle = attachRecorder(vm, File(tempDir, "voice"))

        // Collect-first: the too-short Snackbar is a buffered SharedFlow
        // emission from a real IO thread — a late collector races the
        // virtual-time Turbine timeout, so both turbines go live before
        // the triggering action (Phase 67 awaitItem precedent).
        turbineScope {
            val recording = vm.isVoiceRecording.testIn(this)
            val events = vm.events.testIn(this)
            assertThat(recording.awaitItem()).isFalse()
            vm.startVoiceRecording()
            assertThat(recording.awaitItem()).isTrue()
            val clip = File(requireNotNull(handle.outputPath))
            clip.parentFile?.mkdirs()
            clip.writeText("fake-audio")
            vm.stopVoiceRecording()
            assertThat(recording.awaitItem()).isFalse()

            // The guard validates off-Main: the Snackbar is the last validator
            // step, so awaiting it proves the delete + state clear already ran.
            val event = events.awaitItem()
            assertThat(event).isInstanceOf(ChatEvent.Snackbar::class.java)
            assertThat((event as ChatEvent.Snackbar).message).isEqualTo("TOO_SHORT")
            recording.cancel()
            events.cancel()

            assertThat(clip.exists()).isFalse()
            assertThat(vm.hasVoiceClip.value).isFalse()
        }
    }

    @Test
    fun `auto-stop path with a short clip rejects identically - same choke`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(idleHelper(), modelFile.absolutePath)
        runCurrent()
        vm.voiceDurationReader = { 200L }
        val handle = attachRecorder(vm, File(tempDir, "voice"))

        turbineScope {
            val recording = vm.isVoiceRecording.testIn(this)
            val events = vm.events.testIn(this)
            val capEvents = vm.voiceCapEvent.testIn(this)
            assertThat(recording.awaitItem()).isFalse()
            vm.startVoiceRecording()
            assertThat(recording.awaitItem()).isTrue()
            val clip = File(requireNotNull(handle.outputPath))
            clip.parentFile?.mkdirs()
            clip.writeText("fake-audio")
            vm.autoStopVoiceRecording(announceCap = true)
            assertThat(recording.awaitItem()).isFalse()

            val event = events.awaitItem()
            assertThat(event).isInstanceOf(ChatEvent.Snackbar::class.java)
            assertThat((event as ChatEvent.Snackbar).message).isEqualTo("TOO_SHORT")
            recording.cancel()
            events.cancel()

            assertThat(clip.exists()).isFalse()
            assertThat(vm.hasVoiceClip.value).isFalse()
            // Rejected clips never fire the 60 s cap toast (the Snackbar was
            // the validator's last step, so the absence is final).
            capEvents.expectNoEvents()
            capEvents.cancel()
        }
    }

    @Test
    fun `long clip is kept with duration for the draft card`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(idleHelper(), modelFile.absolutePath)
        runCurrent()
        vm.voiceDurationReader = { 3_000L }
        val handle = attachRecorder(vm, File(tempDir, "voice"))

        val clip = recordClip(vm, handle) { stopVoiceRecording() }

        // The keep validates off-Main — park until the keep lands.
        vm.hasVoiceClip.test {
            var kept = awaitItem()
            while (!kept) kept = awaitItem()
        }
        assertThat(clip.exists()).isTrue()
        assertThat(vm.hasVoiceClip.value).isTrue()
        assertThat(vm.draftDurationMs.value).isEqualTo(3_000L)
    }

    @Test
    fun `deleteVoiceDraft removes the file and clears the draft`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(idleHelper(), modelFile.absolutePath)
        runCurrent()
        vm.voiceDurationReader = { 3_000L }
        val handle = attachRecorder(vm, File(tempDir, "voice"))

        val clip = recordClip(vm, handle) { stopVoiceRecording() }
        vm.hasVoiceClip.test {
            var kept = awaitItem()
            while (!kept) kept = awaitItem()
        }
        assertThat(clip.exists()).isTrue()

        vm.deleteVoiceDraft()

        assertThat(clip.exists()).isFalse()
        assertThat(vm.hasVoiceClip.value).isFalse()
        assertThat(vm.draftDurationMs.value).isEqualTo(0L)
    }

    @Test
    fun `play-then-pause keeps the position`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(idleHelper(), modelFile.absolutePath)
        runCurrent()
        vm.voiceDurationReader = { 3_000L }
        val handle = attachRecorder(vm, File(tempDir, "voice"))
        attachPlayer(vm)

        recordClip(vm, handle) { stopVoiceRecording() }
        vm.hasVoiceClip.test {
            var kept = awaitItem()
            while (!kept) kept = awaitItem()
        }

        // Park until the Default-thread poll publishes the fake position.
        vm.draftPositionMs.test {
            assertThat(awaitItem()).isEqualTo(0)
            vm.playVoiceDraft()
            assertThat(awaitItem()).isEqualTo(1_500)
        }

        vm.isDraftPlaying.test {
            assertThat(awaitItem()).isTrue()
            vm.pauseVoiceDraft()
            assertThat(awaitItem()).isFalse()
        }
        // Pause keeps the position for one-tap resume (only stop/completion
        // reset to 0).
        assertThat(vm.draftPositionMs.value).isEqualTo(1_500)
        assertThat(playerHandles.single().pauses).isEqualTo(1)
    }

    @Test
    fun `completion resets the position to zero`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(idleHelper(), modelFile.absolutePath)
        runCurrent()
        vm.voiceDurationReader = { 3_000L }
        val handle = attachRecorder(vm, File(tempDir, "voice"))
        attachPlayer(vm)

        recordClip(vm, handle) { stopVoiceRecording() }
        vm.hasVoiceClip.test {
            var kept = awaitItem()
            while (!kept) kept = awaitItem()
        }
        vm.draftPositionMs.test {
            assertThat(awaitItem()).isEqualTo(0)
            vm.playVoiceDraft()
            assertThat(awaitItem()).isEqualTo(1_500)
        }

        playerHandles.single().fireCompletion()

        assertThat(vm.isDraftPlaying.value).isFalse()
        assertThat(vm.draftPositionMs.value).isEqualTo(0)
    }

    @Test
    fun `play without a clip is a no-op`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(idleHelper(), modelFile.absolutePath)
        runCurrent()
        attachPlayer(vm)
        advanceUntilIdle()

        vm.playVoiceDraft()
        advanceUntilIdle()

        assertThat(vm.isDraftPlaying.value).isFalse()
        assertThat(playerHandles).isEmpty()
    }
}
