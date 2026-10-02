package com.warped.ui.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.warped.data.local.inference.ActiveEngine
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.EngineType
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.remote.provider.ProviderRouter
import com.warped.domain.llm.LlmModelHelper
import com.warped.domain.model.LocalSelection
import com.warped.domain.model.ParameterStore
import com.warped.domain.model.ProviderType
import com.warped.domain.model.RemoteSelection
import com.warped.domain.model.SyntaxTheme
import com.warped.domain.repository.ChatRepository
import com.warped.domain.repository.EndpointRepository
import com.warped.domain.repository.LocalModelRepository
import com.warped.ui.chat.voice.VoiceDictationManager
import com.warped.domain.review.ReviewHelper
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

/**
 * Continuous-dictation state machine (user decision 2026-10-02: the
 * session stops ONLY on user tap).
 *
 * - Platform partials are cumulative hypotheses, so each partial REPLACES
 *   the previous one: one utterance yields exactly one insertion, and the
 *   final never double-appends.
 * - Finals (including blank/silence finals) commit text and RE-ARM the
 *   session: listening stays true across utterances.
 * - Recoverable errors (NO_MATCH, SPEECH_TIMEOUT) re-arm; fatal errors
 *   clear listening.
 * - The listening flag sets only after a successful start (WR-01).
 * - Sending/stop clears first so late callbacks are dropped and can never
 *   re-arm a dead session or pollute the fresh draft.
 * - Dictation inserts at the last reported cursor, not always at the end
 *   (WR-03).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceDictationTest {

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

    private fun buildViewModel(helper: LlmModelHelper, modelPath: String): ChatViewModel {
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
        every { localModelRepository.observeModels() } returns MutableStateFlow(emptyList())
        every { activeModelSelection.activeModel } returns MutableStateFlow(null)
        every { activeModelSelection.localSelection } returns
            MutableStateFlow(LocalSelection(modelId = modelPath, isConnected = true))
        // Lazy load: the send path mounts only on engine-path mismatch —
        // stub the engine as already serving this model.
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
            modelAllowlistRepository = mockk<com.warped.data.repository.ModelAllowlistRepository>().also(::stubEffectiveCapabilities),
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

    private fun fakeManager(startResult: Boolean = true): VoiceDictationManager {
        val manager = mockk<VoiceDictationManager>()
        every { manager.start() } returns startResult
        every { manager.stop() } just Runs
        every { manager.isAvailable() } returns true
        return manager
    }

    private fun listeningViewModel(): ChatViewModel {
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(idleHelper(), modelFile.absolutePath)
        vm.dictationManagerOverride = fakeManager()
        vm.startDictation()
        return vm
    }

    @Test
    fun `partials replace in place - one utterance yields exactly one insertion`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = listeningViewModel()
        runCurrent()

        assertThat(vm.isListening.value).isTrue()

        vm.onDictationPartial("hel")
        assertThat(vm.inputState.value.inputText).isEqualTo("hel")

        // Cumulative hypotheses must NOT accumulate.
        vm.onDictationPartial("hello")
        assertThat(vm.inputState.value.inputText).isEqualTo("hello")

        vm.onDictationPartial("hello world")
        assertThat(vm.inputState.value.inputText).isEqualTo("hello world")

        // The final replaces the standing hypothesis once — no duplication —
        // and the session auto-stops (user decision 2026-10-02).
        vm.onDictationFinal("hello world")
        assertThat(vm.inputState.value.inputText).isEqualTo("hello world")
        assertThat(vm.isListening.value).isFalse()
    }

    @Test
    fun `final without partials appends once and stops listening`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = listeningViewModel()
        runCurrent()

        vm.onDictationFinal("hello")
        assertThat(vm.inputState.value.inputText).isEqualTo("hello")
        assertThat(vm.isListening.value).isFalse()
    }

    @Test
    fun `blank final stops listening without touching the draft`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = listeningViewModel()
        runCurrent()

        vm.updateInput("keep me")
        vm.onDictationFinal("   ")
        assertThat(vm.inputState.value.inputText).isEqualTo("keep me")
        assertThat(vm.isListening.value).isFalse()
    }

    @Test
    fun `any error stops listening and keeps the partial draft`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = listeningViewModel()
        runCurrent()

        vm.onDictationPartial("hello")
        vm.onDictationError(android.speech.SpeechRecognizer.ERROR_NO_MATCH)
        assertThat(vm.inputState.value.inputText).isEqualTo("hello")
        assertThat(vm.isListening.value).isFalse()
    }

    @Test
    fun `fatal error clears listening and keeps the partial draft`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = listeningViewModel()
        runCurrent()

        vm.onDictationPartial("hello")
        vm.onDictationError(android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
        assertThat(vm.inputState.value.inputText).isEqualTo("hello")
        assertThat(vm.isListening.value).isFalse()
    }

    @Test
    fun `late error after stop stays stopped`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = listeningViewModel()
        runCurrent()

        vm.stopDictation()
        vm.onDictationError(android.speech.SpeechRecognizer.ERROR_NO_MATCH)
        vm.onDictationFinal("late words")
        assertThat(vm.isListening.value).isFalse()
    }

    @Test
    fun `failed start never reports listening`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val vm = buildViewModel(idleHelper(), modelFile.absolutePath)
        vm.dictationManagerOverride = fakeManager(startResult = false)
        runCurrent()

        vm.startDictation()
        assertThat(vm.isListening.value).isFalse()
        assertThat(vm.inputState.value.inputText).isEmpty()
    }

    @Test
    fun `send stops dictation and late callbacks never pollute the fresh draft`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = listeningViewModel()
        runCurrent()

        vm.onDictationPartial("hello")
        assertThat(vm.isListening.value).isTrue()

        vm.sendMessage("hello")
        advanceUntilIdle()

        assertThat(vm.isListening.value).isFalse()
        assertThat(vm.inputState.value.inputText).isEmpty()

        // Orphaned recognizer output after send is dropped.
        vm.onDictationPartial("late partial")
        vm.onDictationFinal("late words")
        assertThat(vm.inputState.value.inputText).isEmpty()
    }

    @Test
    fun `dictation inserts at the reported cursor, and finals revise in place`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = listeningViewModel()
        runCurrent()

        vm.updateInput("hello world")
        vm.updateInputCursor(5)

        vm.onDictationPartial("brave")
        assertThat(vm.inputState.value.inputText).isEqualTo("hello brave world")

        vm.onDictationFinal("brave new")
        assertThat(vm.inputState.value.inputText).isEqualTo("hello brave new world")
        assertThat(vm.isListening.value).isFalse()
    }

    @Test
    fun `stop clears listening and drops the trailing final`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = listeningViewModel()
        runCurrent()

        vm.onDictationPartial("hello")
        vm.stopDictation()
        assertThat(vm.isListening.value).isFalse()

        // The platform flush after stopListening must not resurrect text.
        vm.onDictationFinal("hello")
        assertThat(vm.inputState.value.inputText).isEqualTo("hello")
    }
}
