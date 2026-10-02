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
import com.warped.domain.model.ChatMessage
import com.warped.domain.model.LocalModel
import com.warped.domain.model.LocalSelection
import com.warped.domain.model.ModelCapabilities
import com.warped.domain.model.ParameterStore
import com.warped.domain.model.ProviderType
import com.warped.domain.model.RemoteSelection
import com.warped.domain.model.Role
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
 * Phase 68 Plan 02 (VMSG-06): history-playback discipline through the shared
 * [VoiceMessagePlayer] (fake factory behind
 * [ChatViewModel.voicePlayerOverride]).
 *
 * - Play sets playingMessageId (+ duration); a second play stops the first
 *   (single-player — draft or history).
 * - A missing file emits the unavailable Snackbar and never touches the player.
 * - Completion clears state; pause keeps the position.
 * - Sends stamp audioPath + duration on the saved message; deleting a voice
 *   message deletes its file.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceHistoryPlaybackTest {

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

    private fun audioModel(path: String) = LocalModel(
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
        // WR-05: source-of-truth lookup for the delete path (null = row
        // gone / non-voice — the VM falls back to the transcript).
        repoAudioPath: String? = null,
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
        every { context.getString(R.string.voice_msg_clip_unavailable) } returns "NO_CLIP"
        // Production clips live in filesDir/voice (recorder outputDir) —
        // the delete path confines itself there.
        every { context.filesDir } returns tempDir

        every { chatRepository.observeConversations() } returns MutableStateFlow(emptyList())
        coEvery { chatRepository.createConversation(any(), any(), any(), any()) } returns 42L
        coEvery { chatRepository.saveMessage(any(), any()) } returns Unit
        coEvery { chatRepository.deleteMessage(any<Long>()) } returns Unit
        coEvery { chatRepository.getMessageAudioPath(any()) } returns repoAudioPath
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

        // Audio-capable allowlist so voice turns pass the capabilities gate.
        val allowlist = mockk<com.warped.data.repository.ModelAllowlistRepository>()
        every { allowlist.effectiveCapabilities(any()) } returns ModelCapabilities(audio = true)

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

    private val playerHandles = mutableListOf<FakePlayerHandle>()
    private val playerFactory = object : PlayerFactory {
        override fun create(): PlayerHandle =
            FakePlayerHandle().also { playerHandles.add(it) }
    }

    private fun attachPlayer(vm: ChatViewModel) {
        playerHandles.clear()
        vm.voicePlayerOverride = VoiceMessagePlayer(mockk(relaxed = true), playerFactory)
    }

    private fun voiceMessage(path: String, durationMs: Long = 5_000L) = ChatMessage(
        role = Role.USER,
        content = "voice note",
        audioPath = path,
        audioDurationMs = durationMs,
    )

    @Test
    fun `play history clip sets playingMessageId and duration`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(idleHelper(), modelFile.absolutePath)
        runCurrent()
        attachPlayer(vm)
        val clip = File(tempDir, "h1.m4a").apply { writeText("fake-audio") }
        val message = voiceMessage(clip.absolutePath)

        vm.playingMessageId.test {
            assertThat(awaitItem()).isNull()
            vm.playHistoryVoice(message)
            assertThat(awaitItem()).isEqualTo(message.id)
        }

        assertThat(vm.isHistoryPlaying.value).isTrue()
        assertThat(vm.historyDurationMs.value).isEqualTo(5_000L)
        assertThat(playerHandles.single().starts).isEqualTo(1)
    }

    @Test
    fun `second history play stops the first - single-player`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(idleHelper(), modelFile.absolutePath)
        runCurrent()
        attachPlayer(vm)
        val clipA = File(tempDir, "hA.m4a").apply { writeText("a") }
        val clipB = File(tempDir, "hB.m4a").apply { writeText("b") }

        vm.playingMessageId.test {
            assertThat(awaitItem()).isNull()
            vm.playHistoryVoice(voiceMessage(clipA.absolutePath))
            assertThat(awaitItem()).isNotNull()
        }
        // Quiescence: the first play's in-flight flag clears before its
        // first poll emission, so awaiting the position guarantees the
        // second play is accepted (not debounced).
        vm.historyPositionMs.test {
            var pos = awaitItem()
            while (pos != 1_500) pos = awaitItem()
        }
        vm.playingMessageId.test {
            assertThat(awaitItem()).isNotNull()
            vm.playHistoryVoice(voiceMessage(clipB.absolutePath))
            // The preempting stop clears the id (null) before the new play
            // sets it — skip to the settled id.
            var second: String? = awaitItem()
            while (second == null) second = awaitItem()
            assertThat(second).isNotNull()
        }

        assertThat(playerHandles[0].stops).isEqualTo(1)
        assertThat(playerHandles[1].starts).isEqualTo(1)
        assertThat(vm.isHistoryPlaying.value).isTrue()
    }

    @Test
    fun `playing history stops a playing draft - shared player`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(idleHelper(), modelFile.absolutePath)
        runCurrent()
        vm.voiceDurationReader = { 3_000L }
        val recHandle = FakeRecorderHandle()
        vm.voiceRecorderOverride = VoiceMessageRecorder(
            mockk(relaxed = true),
            File(tempDir, "voice"),
            object : RecorderFactory {
                override fun create(): RecorderHandle = recHandle
            },
        )
        attachPlayer(vm)

        // Record + keep a draft, then play it.
        vm.isVoiceRecording.test {
            assertThat(awaitItem()).isFalse()
            vm.startVoiceRecording()
            assertThat(awaitItem()).isTrue()
            val clip = File(requireNotNull(recHandle.outputPath))
            clip.parentFile?.mkdirs()
            clip.writeText("fake-audio")
            vm.stopVoiceRecording()
            assertThat(awaitItem()).isFalse()
        }
        vm.hasVoiceClip.test {
            var kept = awaitItem()
            while (!kept) kept = awaitItem()
        }
        vm.isDraftPlaying.test {
            assertThat(awaitItem()).isFalse()
            vm.playVoiceDraft()
            assertThat(awaitItem()).isTrue()
        }

        // Playing a history bubble preempts the draft on the same player.
        val historyClip = File(tempDir, "hist.m4a").apply { writeText("h") }
        vm.playingMessageId.test {
            assertThat(awaitItem()).isNull()
            vm.playHistoryVoice(voiceMessage(historyClip.absolutePath))
            assertThat(awaitItem()).isNotNull()
        }

        assertThat(playerHandles[0].stops).isEqualTo(1)
        assertThat(vm.isDraftPlaying.value).isFalse()
        assertThat(vm.isHistoryPlaying.value).isTrue()
    }

    @Test
    fun `missing file emits unavailable and never touches the player`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(idleHelper(), modelFile.absolutePath)
        runCurrent()
        attachPlayer(vm)

        turbineScope {
            val events = vm.events.testIn(this)
            vm.playHistoryVoice(voiceMessage(File(tempDir, "gone.m4a").absolutePath))
            val event = events.awaitItem()
            assertThat(event).isInstanceOf(ChatEvent.Snackbar::class.java)
            assertThat((event as ChatEvent.Snackbar).message).isEqualTo("NO_CLIP")
            events.cancel()
        }

        assertThat(playerHandles).isEmpty()
        assertThat(vm.playingMessageId.value).isNull()
        assertThat(vm.isHistoryPlaying.value).isFalse()
    }

    @Test
    fun `blank audioPath is a no-op`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(idleHelper(), modelFile.absolutePath)
        runCurrent()
        attachPlayer(vm)
        advanceUntilIdle()

        vm.playHistoryVoice(voiceMessage("   "))
        advanceUntilIdle()

        assertThat(playerHandles).isEmpty()
        assertThat(vm.playingMessageId.value).isNull()
    }

    @Test
    fun `completion clears playingMessageId`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(idleHelper(), modelFile.absolutePath)
        runCurrent()
        attachPlayer(vm)
        val clip = File(tempDir, "h2.m4a").apply { writeText("fake-audio") }

        vm.playingMessageId.test {
            assertThat(awaitItem()).isNull()
            vm.playHistoryVoice(voiceMessage(clip.absolutePath))
            assertThat(awaitItem()).isNotNull()
        }

        playerHandles.single().fireCompletion()

        assertThat(vm.playingMessageId.value).isNull()
        assertThat(vm.isHistoryPlaying.value).isFalse()
        assertThat(vm.historyPositionMs.value).isEqualTo(0)
    }

    @Test
    fun `history pause keeps the position`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(idleHelper(), modelFile.absolutePath)
        runCurrent()
        attachPlayer(vm)
        val clip = File(tempDir, "h3.m4a").apply { writeText("fake-audio") }

        // Park until the Default-thread poll publishes the fake position.
        vm.historyPositionMs.test {
            assertThat(awaitItem()).isEqualTo(0)
            vm.playHistoryVoice(voiceMessage(clip.absolutePath))
            assertThat(awaitItem()).isEqualTo(1_500)
        }

        vm.isHistoryPlaying.test {
            assertThat(awaitItem()).isTrue()
            vm.pauseHistoryVoice()
            assertThat(awaitItem()).isFalse()
        }

        assertThat(vm.historyPositionMs.value).isEqualTo(1_500)
        assertThat(vm.playingMessageId.value).isNotNull()
        assertThat(playerHandles.single().pauses).isEqualTo(1)
    }

    @Test
    fun `sendVoiceMessage stamps holders and keeps the file on transcode failure`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(idleHelper(), modelFile.absolutePath)
        runCurrent()
        vm.voiceDurationReader = { 3_000L }
        val recHandle = FakeRecorderHandle()
        vm.voiceRecorderOverride = VoiceMessageRecorder(
            mockk(relaxed = true),
            File(tempDir, "voice"),
            object : RecorderFactory {
                override fun create(): RecorderHandle = recHandle
            },
        )

        vm.isVoiceRecording.test {
            assertThat(awaitItem()).isFalse()
            vm.startVoiceRecording()
            assertThat(awaitItem()).isTrue()
            val clip = File(requireNotNull(recHandle.outputPath))
            clip.parentFile?.mkdirs()
            clip.writeText("not-real-aac")
            vm.stopVoiceRecording()
            assertThat(awaitItem()).isFalse()
        }
        vm.hasVoiceClip.test {
            var kept = awaitItem()
            while (!kept) kept = awaitItem()
        }
        val clip = File(requireNotNull(recHandle.outputPath))

        // The fake clip is not valid AAC — the transcode fails, the draft
        // (and its file) is kept for retry, and the holders are stamped.
        vm.hasVoiceClip.test {
            assertThat(awaitItem()).isTrue()
            vm.sendVoiceMessage("caption")
            assertThat(awaitItem()).isFalse()
            assertThat(awaitItem()).isTrue()
        }

        assertThat(vm.lastSentVoicePath).isEqualTo(clip.absolutePath)
        assertThat(vm.lastSentVoiceDurationMs).isEqualTo(3_000L)
        assertThat(clip.exists()).isTrue()
    }

    @Test
    fun `sendMessage with audioBytes persists audioPath and duration on the row`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val path = modelFile.absolutePath
        val vm = buildViewModel(idleHelper(), path, listOf(audioModel(path)))
        runCurrent()
        advanceUntilIdle()
        val clip = File(tempDir, "sent.m4a").apply { writeText("fake-audio") }
        vm.lastSentVoicePath = clip.absolutePath
        vm.lastSentVoiceDurationMs = 4_000L

        vm.sendMessage("voice note", audioBytes = byteArrayOf(1, 2, 3))
        advanceUntilIdle()

        val sent = vm.transcriptState.value.messages.firstOrNull { it.role == Role.USER }
        assertThat(sent).isNotNull()
        assertThat(sent!!.audioPath).isEqualTo(clip.absolutePath)
        assertThat(sent.audioDurationMs).isEqualTo(4_000L)
        // Holders are consumed — a later text send never steals them.
        assertThat(vm.lastSentVoicePath).isNull()
        assertThat(vm.lastSentVoiceDurationMs).isEqualTo(0L)
    }

    @Test
    fun `text send leaves voice fields empty`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val path = modelFile.absolutePath
        val vm = buildViewModel(idleHelper(), path, listOf(audioModel(path)))
        runCurrent()
        advanceUntilIdle()

        vm.sendMessage("plain text")
        advanceUntilIdle()

        val sent = vm.transcriptState.value.messages.firstOrNull { it.role == Role.USER }
        assertThat(sent).isNotNull()
        assertThat(sent!!.audioPath).isNull()
        assertThat(sent.audioDurationMs).isEqualTo(0L)
    }

    @Test
    fun `deleting a voice message deletes its file`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val path = modelFile.absolutePath
        val vm = buildViewModel(idleHelper(), path, listOf(audioModel(path)))
        runCurrent()
        advanceUntilIdle()
        val clip = File(tempDir, "voice/doomed.m4a").apply {
            parentFile?.mkdirs()
            writeText("fake-audio")
        }
        vm.lastSentVoicePath = clip.absolutePath
        vm.lastSentVoiceDurationMs = 2_000L

        vm.sendMessage("voice note", audioBytes = byteArrayOf(1, 2, 3))
        advanceUntilIdle()
        val id = vm.transcriptState.value.messages.first { it.role == Role.USER }.id
        assertThat(clip.exists()).isTrue()

        vm.deleteMessage(id)
        advanceUntilIdle()

        assertThat(clip.exists()).isFalse()
        assertThat(vm.transcriptState.value.messages.none { it.id == id }).isTrue()
    }

    @Test
    fun `deleting an unloaded-row voice message deletes its file via repository`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val path = modelFile.absolutePath
        // WR-05: the row is NOT in the transcript (stale id / unloaded row) —
        // the clip path resolves from the Room row instead of leaking.
        val clip = File(tempDir, "voice/orphan.m4a").apply {
            parentFile?.mkdirs()
            writeText("fake-audio")
        }
        val vm = buildViewModel(
            idleHelper(),
            path,
            listOf(audioModel(path)),
            repoAudioPath = clip.absolutePath,
        )
        runCurrent()
        advanceUntilIdle()

        vm.deleteMessage(123L)
        advanceUntilIdle()

        assertThat(clip.exists()).isFalse()
    }
}
