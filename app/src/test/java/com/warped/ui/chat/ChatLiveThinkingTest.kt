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
import com.warped.domain.model.ActiveModelSelection
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
import com.warped.domain.review.ReviewHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
import org.junit.jupiter.api.Tag

/**
 * Quick-task (live-thinking): native thought deltas update
 * `streamingReasoning` DURING the turn (throttled like content), and
 * `Done(reasoning)` stays the final authoritative value.
 *
 * Harness mirrors [ChatSubStateTest]; the fake helper scripts a
 * thought-then-answer turn held open on two gates so the intermediate
 * (pre-Done) panel state is observable. The 50ms throttle reads the wall
 * clock, so the test parks the turn, sleeps past the window on the test
 * thread (the collector is parked on a gate — nothing needs the thread),
 * then releases one more delta to trigger the flush.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Tag("slow")
class ChatLiveThinkingTest {

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
        val activeModelSelection = mockk<ActiveModelSelection>()
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
        every { advancedPreferences.thinkingEnabled } returns flowOf(true)
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

    @Test
    fun `thought deltas update the panel live - throttled - Done stays final`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val gate1 = CompletableDeferred<Unit>()
        val gate2 = CompletableDeferred<Unit>()
        val thoughtDeltas = listOf(
            "Cómo", " te", " puedo", " ayudar", " con", " tu",
            " modelo", " local", " hoy", "?",
        )
        val fullThought = thoughtDeltas.joinToString("")
        val helper = mockk<LlmModelHelper>()
        every { helper.type } returns ProviderType.LITE_RT_LM
        coEvery { helper.initialize(any()) } returns Unit
        coEvery { helper.stopResponse() } returns Unit
        every { helper.runInference(any(), any()) } returns flow {
            for (delta in thoughtDeltas) emit(StreamToken.Thinking(delta))
            gate1.await() // hold: all buffered inside the 50ms window
            emit(StreamToken.Thinking("!"))
            gate2.await() // hold: flushed panel observable before Done
            emit(StreamToken.Delta("Answer"))
            emit(StreamToken.Done(reasoning = fullThought + "!"))
        }

        val vm = buildViewModel(helper, modelFile.absolutePath)
        runCurrent()

        val reasoningSeen = mutableListOf<String>()
        val collectJob = launch { vm.transcriptState.collect { reasoningSeen += it.streamingReasoning } }
        runCurrent()

        vm.sendMessage("hola")
        // Pump until the turn is streaming, then drain the cascade so the
        // scripted turn is parked on gate1 (all ten deltas absorbed inside
        // the throttle window — no timers involved, so an idle scheduler
        // means parked).
        repeat(100) {
            runCurrent()
            if (vm.transcriptState.value.isStreaming) return@repeat
        }
        repeat(50) { runCurrent() }
        // Sleep PAST the 50ms throttle window on the test thread — the
        // collector is parked on gate1, so nothing needs the thread.
        Thread.sleep(250)

        // Rapid-fire deltas landed inside the throttle window: nothing lost
        // (the provider mirrors them into Done) but no UI churn yet.
        gate1.complete(Unit)
        repeat(50) { runCurrent() }

        // Live accumulation: the full streamed thought is on the panel
        // BEFORE Done arrives.
        val expected = fullThought + "!"
        assertThat(vm.transcriptState.value.streamingReasoning).isEqualTo(expected)

        gate2.complete(Unit)
        advanceUntilIdle()

        // Final-equals-streamed: the persisted message carries exactly what
        // the panel showed — no delta lost to throttling.
        val terminal = vm.transcriptState.value
        assertThat(terminal.isStreaming).isFalse()
        val persisted = terminal.messages.first { it.role == Role.ASSISTANT }
        assertThat(persisted.content).isEqualTo("Answer")
        assertThat(persisted.reasoning).isEqualTo(expected)

        // Throttle: rapid successive thought deltas coalesce — fewer panel
        // updates than deltas (11 deltas, 1 live flush + the text-flush
        // re-emit of the same value + the Done clear).
        val liveUpdates = reasoningSeen.filter { it.isNotEmpty() }.distinct()
        assertThat(liveUpdates).containsExactly(expected)
        assertThat(reasoningSeen.count { it == expected })
            .isLessThan(thoughtDeltas.size + 1)

        collectJob.cancel()
    }
}
