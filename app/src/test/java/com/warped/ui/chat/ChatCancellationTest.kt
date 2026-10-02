package com.warped.ui.chat

import com.google.common.truth.Truth.assertThat
import com.warped.data.local.inference.ActiveEngine
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.EngineType
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.remote.provider.ProviderRouter
import com.warped.domain.llm.LlmModelHelper
import com.warped.domain.model.ActiveModelSelection
import com.warped.domain.model.ChatMessage
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.GenerationParameters
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
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
import java.util.concurrent.atomic.AtomicInteger

/**
 * 46-01 RUNTIME-13/14 tracer: cancellation + single-shared-Flow regression tests.
 *
 * (a)-(c) pin the required shareIn/turn-scope semantics against a fake helper.
 * (d)-(e) wire the REAL ChatViewModel.stopGeneration contract: transport-stop FIRST,
 * then scope-cancel, then streaming-state reset — and stop-then-follow-up streaming.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatCancellationTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ------------------------------------------------------------------
    // Fake helper: numbered deltas with delay, controllable terminal.
    // ------------------------------------------------------------------

    private class FakeHelper(
        private val tokenCount: Int = Int.MAX_VALUE,
        private val terminal: StreamToken = StreamToken.Done(),
    ) : LlmModelHelper {
        override val type: ProviderType = ProviderType.LM_STUDIO
        val stopCalls = AtomicInteger(0)
        val starts = AtomicInteger(0)

        override suspend fun initialize(modelPath: String) = Unit

        override fun runInference(request: ChatRequest, enableThinking: Boolean): Flow<StreamToken> = flow {
            starts.incrementAndGet()
            var i = 1
            while (i <= tokenCount) {
                emit(StreamToken.Delta("tok$i"))
                i++
                delay(100)
            }
            emit(terminal)
        }

        override fun resetConversation() = Unit
        override fun stopResponse() {
            stopCalls.incrementAndGet()
        }

        override fun cleanUp() = Unit
    }

    // ------------------------------------------------------------------
    // (a) Cancel turn scope -> no trailing tokens.
    // ------------------------------------------------------------------

    @Test
    fun `cancel turn scope halts shared upstream with no trailing tokens`() = runTest {
        val fake = FakeHelper()
        val turnScope = CoroutineScope(coroutineContext + SupervisorJob())
        val shared = fake.runInference(
            ChatRequest(messages = emptyList(), parameters = GenerationParameters()),
            false,
        ).shareIn(turnScope, SharingStarted.Eagerly, replay = 1)

        val collected = mutableListOf<StreamToken>()
        val collector = launch { shared.collect { collected.add(it) } }
        advanceTimeBy(250) // emissions at t=0,100,200 -> tok1..tok3
        assertThat(collected).containsExactly(
            StreamToken.Delta("tok1"),
            StreamToken.Delta("tok2"),
            StreamToken.Delta("tok3"),
        )

        // Stop: cancel the per-turn scope (upstream must die here).
        turnScope.cancel()
        advanceTimeBy(1_000)
        collector.cancel()

        assertThat(collected).containsExactly(
            StreamToken.Delta("tok1"),
            StreamToken.Delta("tok2"),
            StreamToken.Delta("tok3"),
        )
        assertThat(fake.starts.get()).isEqualTo(1)
    }

    // ------------------------------------------------------------------
    // (b) replay=1: re-collect gets last token, upstream starts once.
    // ------------------------------------------------------------------

    @Test
    fun `replay 1 re-collect gets last token without restarting upstream`() = runTest {
        val fake = FakeHelper()
        val turnScope = CoroutineScope(coroutineContext + SupervisorJob())
        val shared = fake.runInference(
            ChatRequest(messages = emptyList(), parameters = GenerationParameters()),
            false,
        ).shareIn(turnScope, SharingStarted.Eagerly, replay = 1)

        // First collector takes two tokens then goes away (rotation).
        shared.test {
            assertThat(awaitItem()).isEqualTo(StreamToken.Delta("tok1"))
            assertThat(awaitItem()).isEqualTo(StreamToken.Delta("tok2"))
            cancelAndIgnoreRemainingEvents()
        }

        // Re-collect: replay cache replays the LAST token, upstream not restarted.
        shared.test {
            assertThat(awaitItem()).isEqualTo(StreamToken.Delta("tok2"))
            assertThat(awaitItem()).isEqualTo(StreamToken.Delta("tok3"))
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(fake.starts.get()).isEqualTo(1)
        turnScope.cancel()
    }

    // ------------------------------------------------------------------
    // (c) Stop turn 1 mid-stream, then turn 2 streams fully (no wedge).
    // ------------------------------------------------------------------

    private class TurnHarness(
        private val scope: CoroutineScope,
        private val helper: FakeHelper,
    ) {
        var isStreaming = false
        var content = ""
        private var job: Job? = null

        fun send() {
            val turnJob = scope.launch {
                isStreaming = true
                val turnScope = this
                helper.runInference(
                    ChatRequest(messages = emptyList(), parameters = GenerationParameters()),
                    false,
                ).shareIn(turnScope, SharingStarted.Eagerly, replay = 1).collect { token ->
                    when (token) {
                        is StreamToken.Delta -> content += token.content
                        is StreamToken.Done -> isStreaming = false
                        is StreamToken.Error -> isStreaming = false
                        // 47-02: tool status carries no text content.
                        is StreamToken.ToolStatus -> Unit
                        // 47-03: completion records carry no text either.
                        is StreamToken.ToolCompleted -> Unit
                        // Phase 57 UI-review: typed tools-unsupported
                        // notice carries no text either.
                        is StreamToken.ToolsUnsupported -> Unit
                        // Quick-task (live-thinking): native thought
                        // carries no answer text either.
                        is StreamToken.Thinking -> Unit
                    }
                }
            }
            job = turnJob
        }

        fun stop() {
            // Contract under test: transport-stop FIRST, then scope-cancel, then state reset.
            helper.stopResponse()
            job?.cancel()
            job = null
            isStreaming = false
            content = ""
        }
    }

    @Test
    fun `stop mid-stream then follow-up streams full sequence with clean state`() = runTest {
        val helper = FakeHelper(tokenCount = 5)
        // Explicit worker scope (same pattern as the passing shareIn test above):
        // turn jobs cancelled deterministically, no TestScope tracking surprises.
        val worker = CoroutineScope(coroutineContext + SupervisorJob())
        val harness = TurnHarness(worker, helper)

        harness.send()
        advanceTimeBy(250)
        assertThat(harness.isStreaming).isTrue()
        assertThat(harness.content).isEqualTo("tok1tok2tok3")

        harness.stop()
        advanceUntilIdle()
        assertThat(helper.stopCalls.get()).isEqualTo(1)
        assertThat(harness.isStreaming).isFalse()
        assertThat(harness.content).isEmpty()

        // Follow-up turn: full sequence, terminates on its own.
        harness.send()
        advanceUntilIdle()
        assertThat(harness.content).isEqualTo("tok1tok2tok3tok4tok5")
        assertThat(harness.isStreaming).isFalse()
        worker.cancel()
    }

    // ------------------------------------------------------------------
    // (d) REAL ChatViewModel: stopGeneration must call helper.stopResponse.
    // ------------------------------------------------------------------

    @TempDir
    lateinit var tempDir: File

    private fun buildViewModel(
        helper: LlmModelHelper,
        modelPath: String,
        chatRepository: ChatRepository = mockk(),
        providerRouter: ProviderRouter = mockk(),
    ): ChatViewModel {
        val endpointRepository = mockk<EndpointRepository>()
        val localModelRepository = mockk<LocalModelRepository>()
        val activeModelSelection = mockk<ActiveModelSelection>()
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

    @Test
    fun `stopGeneration calls helper stopResponse and clears streaming state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val helper = mockk<LlmModelHelper>()
        every { helper.type } returns ProviderType.LITE_RT_LM
        coEvery { helper.initialize(any()) } returns Unit
        every { helper.runInference(any(), any()) } returns flow {
            emit(StreamToken.Delta("a"))
            delay(100)
            emit(StreamToken.Delta("b"))
            delay(100)
            emit(StreamToken.Delta("c"))
            emit(StreamToken.Done())
        }

        val vm = buildViewModel(helper, modelFile.absolutePath)
        runCurrent() // flush init collectors (selection -> selectedLocalModelId)

        vm.sendMessage("hi")
        runCurrent()
        assertThat(vm.uiState.value.isStreaming).isTrue()

        advanceTimeBy(120)
        vm.stopGeneration()
        runCurrent()

        // RUNTIME-14: transport-stop must be reached on Stop.
        verify(exactly = 1) { helper.stopResponse() }
        assertThat(vm.uiState.value.isStreaming).isFalse()
        assertThat(vm.uiState.value.streamingContent).isEmpty()
        assertThat(vm.uiState.value.streamingReasoning).isEmpty()
    }

    @Test
    fun `stop then immediate follow-up streams cleanly with no stale spinner`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val helper = mockk<LlmModelHelper>()
        every { helper.type } returns ProviderType.LITE_RT_LM
        coEvery { helper.initialize(any()) } returns Unit
        coEvery { helper.stopResponse() } returns Unit
        every { helper.runInference(any(), any()) } returns flow {
            emit(StreamToken.Delta("x"))
            delay(50)
            emit(StreamToken.Delta("y"))
            emit(StreamToken.Done())
        }

        val vm = buildViewModel(helper, modelFile.absolutePath)
        runCurrent()

        // Turn 1: stop mid-stream.
        vm.sendMessage("first")
        runCurrent()
        assertThat(vm.uiState.value.isStreaming).isTrue()
        vm.stopGeneration()
        runCurrent()
        assertThat(vm.uiState.value.isStreaming).isFalse()

        // Turn 2: full sequence, assistant message persisted, spinner cleared.
        vm.sendMessage("second")
        advanceUntilIdle()
        assertThat(vm.uiState.value.isStreaming).isFalse()
        val assistants = vm.uiState.value.messages.filter { it.role == Role.ASSISTANT }
        assertThat(assistants).hasSize(1)
        assertThat(assistants.single().content).isEqualTo("xy")
        coVerify(atLeast = 1) { helper.stopResponse() }
    }

    @Test
    fun `fake helper flow cancelled mid-stream emits no trailing tokens`() = runTest {
        val fake = FakeHelper()
        fake.runInference(
            ChatRequest(messages = emptyList(), parameters = GenerationParameters()),
            false,
        ).test {
            assertThat(awaitItem()).isEqualTo(StreamToken.Delta("tok1"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ------------------------------------------------------------------
    // 56-02: ToolStatus rows are transient — shown while running, cleared
    // on completion, never persisted to the transcript.
    // ------------------------------------------------------------------

    @Test
    fun `tool status rows are transient - set shown cleared never persisted`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelFile = File(tempDir, "tiny.litertlm").apply { writeText("fake") }
        val helper = mockk<LlmModelHelper>()
        every { helper.type } returns ProviderType.LITE_RT_LM
        coEvery { helper.initialize(any()) } returns Unit
        coEvery { helper.stopResponse() } returns Unit
        every { helper.runInference(any(), any()) } returns flow {
            emit(StreamToken.ToolStatus("Searching for \"android release\"…"))
            delay(100)
            emit(StreamToken.ToolStatus(null))
            emit(StreamToken.Delta("hi"))
            emit(StreamToken.Done())
        }

        val vm = buildViewModel(helper, modelFile.absolutePath)
        runCurrent()

        vm.sendMessage("fresh news?")
        runCurrent()
        // Transient row shows the query while the tool runs.
        assertThat(vm.uiState.value.toolCallActive).isEqualTo("Searching for \"android release\"…")

        advanceUntilIdle()
        // Cleared on completion; never persisted to the transcript.
        assertThat(vm.uiState.value.toolCallActive).isNull()
        assertThat(vm.uiState.value.isStreaming).isFalse()
        assertThat(vm.uiState.value.messages.map { it.role })
            .containsExactly(Role.USER, Role.ASSISTANT)
        assertThat(vm.uiState.value.messages.last().content).isEqualTo("hi")
    }
}
