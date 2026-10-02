package com.warped.ui.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.ToolCall
import com.google.common.truth.Truth.assertThat
import com.warped.data.agentic.LocalToolLoop
import com.warped.data.grounding.DuckDuckGoSearchRepository
import com.warped.data.grounding.GroundingResult
import com.warped.data.grounding.MultiUrlFetcher
import com.warped.data.grounding.MultiUrlResult
import com.warped.data.grounding.SearchOutcome
import com.warped.data.grounding.WebPageFetcher
import com.warped.data.local.inference.AgenticTurn
import com.warped.data.local.inference.AgenticTurnTransport
import com.warped.data.local.inference.ActiveEngine
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.EngineType
import com.warped.data.local.inference.InputSanitizer
import com.warped.data.local.inference.LiteRTLmProvider
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.remote.provider.ProviderRouter
import com.warped.data.repository.AllowlistCapabilities
import com.warped.data.repository.AllowlistedModel
import com.warped.data.repository.ModelAllowlistRepository
import com.warped.domain.llm.LlmModelHelper
import com.warped.domain.model.ActiveModelSelection
import com.warped.domain.model.GroundedSource
import com.warped.domain.model.GroundedSourceStatus
import com.warped.domain.model.LocalSelection
import com.warped.domain.model.ParameterStore
import com.warped.domain.model.ProviderType
import com.warped.domain.model.RemoteSelection
import com.warped.domain.model.StreamToken
import com.warped.domain.model.SyntaxTheme
import com.warped.domain.repository.ChatRepository
import com.warped.domain.repository.EndpointRepository
import com.warped.domain.repository.LocalModelRepository
import com.warped.domain.review.ReviewHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
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
import java.io.File

/**
 * Quick-task (tool-failure-note): a failed tool call surfaces one
 * transient, auto-clearing, non-persisted note via the existing
 * `ChatEvent.Snackbar` precedent. The model-fed `"Error: ..."` text is
 * untouched; success paths emit nothing.
 *
 * Failure = attempted-but-threw (executor catch-all → `failed = true`).
 * Validation short-circuits, unknown names, offline, cap, and
 * key/limit/model-only degradations carry their own surfaces and stay
 * silent here (IN-02 extended).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ToolFailureAffordanceTest {

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
    // Provider level: loop emission.
    // ------------------------------------------------------------------

    private val engineManager = mockk<EngineManager>()
    private val ddg = mockk<DuckDuckGoSearchRepository>()
    private val multiUrlFetcher = mockk<MultiUrlFetcher>()
    private val webPageFetcher = mockk<WebPageFetcher>()
    private val allowlist = mockk<ModelAllowlistRepository>().also(::stubEffectiveCapabilities)
    private val advancedPreferences = mockk<AdvancedPreferences>()

    private fun provider() = LiteRTLmProvider(
        engineManager = engineManager,
        inputSanitizer = mockk<InputSanitizer>(),
        activeModelSelection = mockk<ActiveModelSelection>(),
        ddg = ddg,
        multiUrlFetcher = multiUrlFetcher,
        webPageFetcher = webPageFetcher,
        allowlist = allowlist,
        advancedPreferences = advancedPreferences,
    )

    private data class FakeTurn(
        val terminal: Message,
        val texts: List<String> = emptyList(),
    )

    private class FakeTransport(turns: List<FakeTurn>) : AgenticTurnTransport {
        override var isAlive: Boolean = true
        private val queue = ArrayDeque(turns)
        val replies = mutableListOf<Message?>()
        override suspend fun collectTurn(
            first: Contents?,
            reply: Message?,
            onText: suspend (String) -> Unit,
            onThought: suspend (String) -> Unit,
        ): AgenticTurn {
            replies.add(reply)
            val turn = queue.removeFirst()
            for (text in turn.texts) onText(text)
            return AgenticTurn(turn.terminal)
        }
    }

    private fun toolTerminal(vararg calls: ToolCall) = Message.model(
        Contents.of(Content.Text("")),
        calls.toList(),
        emptyMap(),
    )

    private fun finalTerminal(text: String) = Message.model(
        Contents.of(Content.Text(text)),
        emptyList(),
        emptyMap(),
    )

    @Test
    fun `failed search emits ToolCompleted with transient note and no rows`() = runTest {
        every { webPageFetcher.hasValidatedInternet() } returns true
        coEvery { ddg.search(any(), any(), any(), any()) } throws RuntimeException("socket boom")
        val transport = FakeTransport(
            listOf(
                FakeTurn(toolTerminal(ToolCall("web_search", mapOf("query" to "q")))),
                FakeTurn(finalTerminal("Final"), texts = listOf("Final")),
            )
        )
        val tokens = flow<StreamToken> {
            with(provider()) { runToolLoop(transport, Contents.of("q"), 4096) }
        }.toList()

        val completed = tokens.filterIsInstance<StreamToken.ToolCompleted>()
        assertThat(completed).hasSize(1)
        assertThat(completed.single().sources).isEmpty()
        assertThat(completed.single().errorReason).isNotNull()
        assertThat(completed.single().errorReason).contains("Web search failed")
        // The turn still finishes — the model-fed error text is untouched.
        assertThat(tokens.filterIsInstance<StreamToken.Done>()).hasSize(1)
        val fed = transport.replies[1]?.contents?.contents
            ?.filterIsInstance<Content.ToolResponse>()
            ?.single()
        assertThat(fed?.response.toString()).startsWith("Error:")
    }

    @Test
    fun `successful search emits ToolCompleted with no note`() = runTest {
        every { webPageFetcher.hasValidatedInternet() } returns true
        coEvery { ddg.search(any(), any(), any(), any()) } returns SearchOutcome.Grounded(
            MultiUrlResult.Fused(
                block = "Source [1] search",
                okUrls = listOf("https://s1.example/a"),
                skippedUrls = emptyList(),
                details = listOf(
                    GroundedSource(
                        url = "https://s1.example/a",
                        extractedText = "Search text.",
                        status = GroundedSourceStatus.OK,
                    ),
                ),
            )
        )
        val transport = FakeTransport(
            listOf(
                FakeTurn(toolTerminal(ToolCall("web_search", mapOf("query" to "q")))),
                FakeTurn(finalTerminal("Final"), texts = listOf("Final")),
            )
        )
        val tokens = flow<StreamToken> {
            with(provider()) { runToolLoop(transport, Contents.of("q"), 4096) }
        }.toList()

        val completed = tokens.filterIsInstance<StreamToken.ToolCompleted>()
        assertThat(completed).hasSize(1)
        assertThat(completed.single().errorReason).isNull()
    }

    @Test
    fun `failure note copy is English per tool`() {
        assertThat(LocalToolLoop.failureNote("web_search"))
            .isEqualTo("Web search failed \u2014 answering from model knowledge.")
        assertThat(LocalToolLoop.failureNote("web_fetch"))
            .isEqualTo("Couldn't read the page \u2014 answering from model knowledge.")
        assertThat(LocalToolLoop.failureNote("nope"))
            .contains("model knowledge")
    }

    // ------------------------------------------------------------------
    // ViewModel level: transient Snackbar, no persistence.
    // ------------------------------------------------------------------

    private lateinit var chatRepository: ChatRepository
    private lateinit var helperTokens: List<StreamToken>

    private fun buildViewModel(modelPath: String): ChatViewModel {
        chatRepository = mockk()
        val endpointRepository = mockk<EndpointRepository>()
        val localModelRepository = mockk<LocalModelRepository>()
        val activeModelSelection = mockk<ActiveModelSelection>()
        val providerRouter = mockk<ProviderRouter>()
        val engineManager = mockk<EngineManager>()
        val memoryChecker = mockk<MemoryChecker>()
        val advancedPreferences = mockk<AdvancedPreferences>()
        val allowlist = mockk<ModelAllowlistRepository>().also(::stubEffectiveCapabilities)
        val context = mockk<Context>()
        every { context.getString(any<Int>()) } returns ""
        every { context.getString(any<Int>(), *anyVararg<Any>()) } returns ""

        every { chatRepository.observeConversations() } returns MutableStateFlow(emptyList())
        coEvery { chatRepository.createConversation(any(), any(), any(), any()) } returns 42L
        coEvery { chatRepository.saveMessage(any(), any()) } returns Unit
        coEvery { chatRepository.saveMessageWithSources(any(), any(), any()) } returns 99L
        coEvery { chatRepository.getWebOverride(any()) } returns null
        coEvery { chatRepository.setWebOverride(any(), any()) } returns Unit
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
        every { activeModelSelection.saveLastConversation(any()) } returns Unit
        every { advancedPreferences.syntaxTheme } returns flowOf(SyntaxTheme.MONOKAI)
        every { advancedPreferences.codeFontScale } returns flowOf(1.0f)
        every { advancedPreferences.thinkingEnabled } returns flowOf(false)
        every { advancedPreferences.webGroundingEnabled } returns flowOf(true)
        every { allowlist.findByModelFile("tiny.litertlm") } returns AllowlistedModel(
            name = "tiny",
            displayName = "Tiny",
            modelFile = "tiny.litertlm",
            sizeInBytes = 1L,
            capabilities = AllowlistCapabilities(supportsFunctionCalling = true),
        )
        val helper = mockk<LlmModelHelper>()
        every { helper.type } returns ProviderType.LITE_RT_LM
        coEvery { helper.initialize(any()) } returns Unit
        coEvery { helper.stopResponse() } returns Unit
        every { helper.runInference(any(), any()) } returns flow {
            for (token in helperTokens) emit(token)
        }
        every { providerRouter.resolveLocalHelper(any(), any()) } returns helper
        val fetcher = mockk<WebPageFetcher>()
        every { fetcher.cancel() } returns Unit
        every { fetcher.hasValidatedInternet() } returns true
        val ddgSearchRepository = mockk<DuckDuckGoSearchRepository>()
        coEvery {
            ddgSearchRepository.search(any(), any(), any(), any())
        } returns SearchOutcome.ModelOnly(
            MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
        )

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
            multiUrlFetcher = mockk(),
            ddgSearchRepository = ddgSearchRepository,
            modelAllowlistRepository = allowlist,
            context = context,
            reviewHelper = mockk(relaxed = true),
        )
    }

    private fun tempModel(): String =
        File.createTempFile("tiny", ".litertlm").apply { writeText("fake") }.absolutePath

    @Test
    fun `failed tool call surfaces one transient snackbar and persists no rows`() = runTest {
        val note = "Web search failed \u2014 answering from model knowledge."
        helperTokens = listOf(
            StreamToken.ToolStatus("Searching for \"q\"\u2026"),
            StreamToken.ToolCompleted("local:web_search#1", "Error: socket boom", errorReason = note),
            StreamToken.ToolStatus(null),
            StreamToken.Delta("answer from knowledge"),
            StreamToken.Done(),
        )
        val vm = buildViewModel(tempModel())
        runCurrent()
        val events = mutableListOf<ChatEvent>()
        val job = launch { vm.events.collect { events.add(it) } }

        vm.sendMessage("latest news")
        advanceUntilIdle()
        job.cancel()

        val snackbars = events.filterIsInstance<ChatEvent.Snackbar>()
        assertThat(snackbars).hasSize(1)
        assertThat(snackbars.single().message).isEqualTo(note)
        // Non-persisted: no Fuentes rows written for the failed call.
        coVerify(exactly = 0) {
            chatRepository.saveMessageWithSources(any(), any(), any())
        }
    }

    @Test
    fun `success path emits no snackbar`() = runTest {
        helperTokens = listOf(
            StreamToken.ToolStatus("Searching for \"q\"\u2026"),
            StreamToken.ToolCompleted(
                "local:web_search#1",
                "summary",
                sources = listOf(
                    GroundedSource(
                        url = "https://s1.example/a",
                        extractedText = "Search text.",
                        status = GroundedSourceStatus.OK,
                    ),
                ),
            ),
            StreamToken.ToolStatus(null),
            StreamToken.Delta("answer with [1]"),
            StreamToken.Done(),
        )
        val vm = buildViewModel(tempModel())
        runCurrent()
        val events = mutableListOf<ChatEvent>()
        val job = launch { vm.events.collect { events.add(it) } }

        vm.sendMessage("latest news")
        advanceUntilIdle()
        job.cancel()

        assertThat(events.filterIsInstance<ChatEvent.Snackbar>()).isEmpty()
    }
}
