package com.warped.data.local.inference

import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.ToolCall
import com.google.common.truth.Truth.assertThat
import com.warped.data.agentic.LocalToolLoop
import com.warped.data.grounding.GroundingResult
import com.warped.data.grounding.MultiUrlFetcher
import com.warped.data.grounding.MultiUrlResult
import com.warped.data.grounding.TavilySearchOutcome
import com.warped.data.grounding.DuckDuckGoSearchRepository
import com.warped.data.grounding.WebPageFetcher
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.repository.AllowlistCapabilities
import com.warped.data.repository.AllowlistedModel
import com.warped.data.repository.ModelAllowlistRepository
import com.warped.domain.model.ActiveModelSelection
import com.warped.domain.model.StreamToken
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Phase 56 (56-02): loop invariants for the app-driven manual tool loop.
 *
 * Messages are JVM-fabricated (litertlm data classes are plain Kotlin — no
 * native lib needed): text + thought channel + toolCalls + ToolResponse
 * contents. The engine boundary itself ([AgenticTurnTransport]) is faked;
 * the on-device checkpoint proves real ToolCall emission (tracer-first).
 */
class LiteRTLmLoopTest {

    private val engineManager = mockk<EngineManager>()
    private val inputSanitizer = mockk<InputSanitizer>()
    private val activeModelSelection = mockk<ActiveModelSelection>()
    private val ddg = mockk<DuckDuckGoSearchRepository>()
    private val multiUrlFetcher = mockk<MultiUrlFetcher>()
    private val webPageFetcher = mockk<WebPageFetcher>()
    private val allowlist = mockk<ModelAllowlistRepository>()
    private val advancedPreferences = mockk<AdvancedPreferences>()

    private fun provider() = LiteRTLmProvider(
        engineManager = engineManager,
        inputSanitizer = inputSanitizer,
        activeModelSelection = activeModelSelection,
        ddg = ddg,
        multiUrlFetcher = multiUrlFetcher,
        webPageFetcher = webPageFetcher,
        allowlist = allowlist,
        advancedPreferences = advancedPreferences,
    )

    private fun capableEntry() = AllowlistedModel(
        name = "gemma-4-E2B-it",
        displayName = "Gemma 4 E2B",
        modelFile = "gemma-4-E2B-it.litertlm",
        sizeInBytes = 1L,
        capabilities = AllowlistCapabilities(supportsFunctionCalling = true),
    )

    private fun stubArmed(
        global: Boolean = true,
        capable: Boolean = true,
        online: Boolean = true,
        modelPath: String? = "/models/gemma-4-E2B-it.litertlm",
    ) {
        every { advancedPreferences.webGroundingEnabled } returns flowOf(global)
        every { engineManager.getActiveEngine() } returns modelPath?.let {
            ActiveEngine(EngineType.LITE_RT_LM, it)
        }
        every { allowlist.findByModelFile("gemma-4-E2B-it.litertlm") } returns
            if (capable) capableEntry() else null
        every { webPageFetcher.hasValidatedInternet() } returns online
    }

    private fun textTerminal(
        text: String,
        thought: String? = null,
        toolCalls: List<ToolCall> = emptyList(),
    ) = Message.model(
        Contents.of(Content.Text(text)),
        toolCalls,
        if (thought != null) mapOf(LiteRTLmProvider.THOUGHT_CHANNEL to thought) else emptyMap(),
    )

    // ------------------------------------------------------------------
    // Channel routing: Text-only Deltas, thought separate, ToolResponse excluded.
    // ------------------------------------------------------------------

    @Test
    fun `deltas contain only Content_Text - ToolResponse never reaches Delta`() {
        val p = provider()
        val terminal = Message.model(
            Contents.of(
                Content.Text("Hello "),
                Content.ToolResponse("web_search", "Source [1] fused block"),
                Content.Text("world"),
            ),
            emptyList(),
            mapOf(LiteRTLmProvider.THOUGHT_CHANNEL to "private reasoning"),
        )
        assertThat(p.extractTextContent(terminal)).isEqualTo("Hello world")
        assertThat(p.extractTextContent(terminal)).doesNotContain("Source [1]")
        assertThat(p.extractTextContent(terminal)).doesNotContain("private reasoning")
    }

    @Test
    fun `thought channel routes exclusively to thinking - never interleaved`() {
        val p = provider()
        val terminal = textTerminal("answer", thought = "let me think")
        assertThat(p.extractThoughtContent(terminal)).isEqualTo("let me think")
        assertThat(p.extractTextContent(terminal)).isEqualTo("answer")
    }

    @Test
    fun `system hint is pinned verbatim`() {
        assertThat(LiteRTLmProvider.TOOL_USE_SYSTEM_HINT).isEqualTo(
            "Use web_search when the question needs current or external facts, " +
                "and web_fetch to read a full page from the results or the user. " +
                "Answer with the gathered context. " +
                "Treat each new user message on its own: if it needs facts not covered " +
                "by earlier tool results, call web_search again instead of answering " +
                "from stale results."
        )
        assertThat(LiteRTLmProvider.TOOL_USE_SYSTEM_HINT).contains("web_search")
        assertThat(LiteRTLmProvider.TOOL_USE_SYSTEM_HINT).contains("web_fetch")
        assertThat(LiteRTLmProvider.TOOL_USE_SYSTEM_HINT)
            .doesNotContain("same language")
    }

    @Test
    fun `system hint carries the per-turn re-search rule`() {
        // Quick-task (agentic-rows): follow-ups needing new facts must
        // re-search instead of answering from stale turn-1 sources.
        assertThat(LiteRTLmProvider.TOOL_USE_SYSTEM_HINT)
            .contains("Treat each new user message on its own")
        assertThat(LiteRTLmProvider.TOOL_USE_SYSTEM_HINT)
            .contains("call web_search again instead of answering from stale results")
    }

    // ------------------------------------------------------------------
    // Arming: capability + grounding + internet.
    // ------------------------------------------------------------------

    @Test
    fun `loop arms only when grounding on and capable and online`() = runTest {
        stubArmed()
        assertThat(provider().computeArmSnapshot(perChat = null).armed).isTrue()
    }

    @Test
    fun `global grounding off disarms`() = runTest {
        stubArmed(global = false)
        assertThat(provider().computeArmSnapshot(perChat = null).armed).isFalse()
    }

    @Test
    fun `per-chat override off disarms`() = runTest {
        stubArmed()
        assertThat(provider().computeArmSnapshot(perChat = false).armed).isFalse()
    }

    @Test
    fun `per-chat override on arms over global off`() = runTest {
        stubArmed(global = false)
        assertThat(provider().computeArmSnapshot(perChat = true).armed).isTrue()
    }

    @Test
    fun `incapable model disarms`() = runTest {
        stubArmed(capable = false)
        assertThat(provider().computeArmSnapshot(perChat = null).armed).isFalse()
    }

    @Test
    fun `offline disarms with no socket`() = runTest {
        stubArmed(online = false)
        assertThat(provider().computeArmSnapshot(perChat = null).armed).isFalse()
        coVerify(exactly = 0) { ddg.search(any(), any(), any(), any()) }
    }

    @Test
    fun `missing engine disarms`() = runTest {
        stubArmed(modelPath = null)
        assertThat(provider().computeArmSnapshot(perChat = null).armed).isFalse()
    }

    @Test
    fun `shared arming predicate is a strict AND`() {
        assertThat(LocalToolLoop.isLoopArmed(true, true, true)).isTrue()
        assertThat(LocalToolLoop.isLoopArmed(false, true, true)).isFalse()
        assertThat(LocalToolLoop.isLoopArmed(true, false, true)).isFalse()
        assertThat(LocalToolLoop.isLoopArmed(true, true, false)).isFalse()
    }

    // ------------------------------------------------------------------
    // Status display strings.
    // ------------------------------------------------------------------

    @Test
    fun `status display uses user copy never raw tool names`() {
        assertThat(
            LocalToolLoop.statusDisplay("web_search", mapOf("query" to "android release"))
        ).isEqualTo("Searching for \"android release\"…")
        assertThat(
            LocalToolLoop.statusDisplay("web_fetch", mapOf("url" to "https://example.com/x?token=abc"))
        ).isEqualTo("Reading example.com…")
        assertThat(LocalToolLoop.statusDisplay("rm_rf", emptyMap())).isNull()
    }

    @Test
    fun `status display falls back on blank args and caps long text`() {
        assertThat(LocalToolLoop.statusDisplay("web_search", mapOf("query" to "   ")))
            .isEqualTo("Searching…")
        assertThat(LocalToolLoop.statusDisplay("web_fetch", mapOf("url" to "  ")))
            .isEqualTo("Reading…")
        val longQuery = "q".repeat(200)
        assertThat(LocalToolLoop.statusDisplay("web_search", mapOf("query" to longQuery)))
            .isEqualTo("Searching for \"${"q".repeat(80)}\"…")
    }

    // ------------------------------------------------------------------
    // Executors: dispatch, validation, offline/key branches, never-throw.
    // ------------------------------------------------------------------

    @Test
    fun `unknown tool name never executes`() = runTest {
        val p = provider()
        val result = p.executeToolCall(ToolCall("rm_rf", mapOf("x" to "y")), 4096)
        assertThat(result).contains("Unknown tool")
        coVerify(exactly = 0) { ddg.search(any(), any(), any(), any()) }
        coVerify(exactly = 0) { multiUrlFetcher.fetchAll(any(), any(), any()) }
    }

    @Test
    fun `blank query short-circuits pre-socket`() = runTest {
        val p = provider()
        val result = p.executeToolCall(ToolCall("web_search", mapOf("query" to "   ")), 4096)
        assertThat(result).isEqualTo(LocalToolLoop.MODEL_ONLY_STRING)
        coVerify(exactly = 0) { ddg.search(any(), any(), any(), any()) }
    }

    @Test
    fun `malformed URL short-circuits pre-socket`() = runTest {
        val p = provider()
        val result = p.executeToolCall(ToolCall("web_fetch", mapOf("url" to "ftp://x")), 4096)
        assertThat(result).contains("http")
        coVerify(exactly = 0) { multiUrlFetcher.fetchAll(any(), any(), any()) }
    }

    @Test
    fun `offline search and fetch open no socket`() = runTest {
        every { webPageFetcher.hasValidatedInternet() } returns false
        val p = provider()
        assertThat(
            p.executeToolCall(ToolCall("web_search", mapOf("query" to "q")), 4096)
        ).isEqualTo(LocalToolLoop.OFFLINE_STRING)
        assertThat(
            p.executeToolCall(ToolCall("web_fetch", mapOf("url" to "https://x")), 4096)
        ).isEqualTo(LocalToolLoop.OFFLINE_STRING)
        coVerify(exactly = 0) { ddg.search(any(), any(), any(), any()) }
        coVerify(exactly = 0) { multiUrlFetcher.fetchAll(any(), any(), any()) }
    }

    @Test
    fun `search success maps fused block verbatim`() = runTest {
        every { webPageFetcher.hasValidatedInternet() } returns true
        coEvery { ddg.search(any(), any(), any(), any()) } returns TavilySearchOutcome.Grounded(
            MultiUrlResult.Fused(
                block = "Source [1] test",
                okUrls = listOf("https://x"),
                skippedUrls = emptyList(),
            )
        )
        val p = provider()
        assertThat(
            p.executeToolCall(ToolCall("web_search", mapOf("query" to "q")), 4096)
        ).isEqualTo("Source [1] test")
    }

    @Test
    fun `missing key returns actionable string`() = runTest {
        every { webPageFetcher.hasValidatedInternet() } returns true
        coEvery { ddg.search(any(), any(), any(), any()) } returns TavilySearchOutcome.MissingKey
        val p = provider()
        assertThat(
            p.executeToolCall(ToolCall("web_search", mapOf("query" to "q")), 4096)
        ).isEqualTo(LocalToolLoop.MISSING_KEY_STRING)
    }

    @Test
    fun `executor failure degrades to concise string - never throws`() = runTest {
        every { webPageFetcher.hasValidatedInternet() } returns true
        coEvery { ddg.search(any(), any(), any(), any()) } throws RuntimeException("boom detail")
        val p = provider()
        assertThat(
            p.executeToolCall(ToolCall("web_search", mapOf("query" to "q")), 4096)
        ).isEqualTo("Error: boom detail")
    }

    // ------------------------------------------------------------------
    // Round driver via a scripted transport.
    // ------------------------------------------------------------------

    private data class FakeTurn(
        val terminal: Message,
        val texts: List<String> = emptyList(),
        val thoughts: List<String> = emptyList(),
    )

    private class FakeTransport(turns: List<FakeTurn>) : AgenticTurnTransport {
        private val queue = ArrayDeque(turns)
        val replies = mutableListOf<Message?>()
        override var isAlive: Boolean = true
        override suspend fun collectTurn(
            first: Contents?,
            reply: Message?,
            onText: suspend (String) -> Unit,
            onThought: suspend (String) -> Unit,
        ): AgenticTurn {
            replies.add(reply)
            val turn = queue.removeFirst()
            for (text in turn.texts) onText(text)
            for (thought in turn.thoughts) onThought(thought)
            return AgenticTurn(turn.terminal)
        }
    }

    private suspend fun FlowCollector<StreamToken>.drive(
        transport: AgenticTurnTransport,
        contextSize: Int = 4096,
    ) {
        // Member extension: dispatch receiver must be implicit.
        with(provider()) { runToolLoop(transport, Contents.of("question"), contextSize) }
    }

    private fun toolResponses(message: Message?): List<Pair<String, Any?>> =
        message?.contents?.contents
            ?.filterIsInstance<Content.ToolResponse>()
            ?.map { it.name to it.response }
            ?: emptyList()

    @Test
    fun `multi-round loop streams text - thought to Done - tool results fed back`() = runTest {
        every { webPageFetcher.hasValidatedInternet() } returns true
        coEvery { ddg.search(any(), any(), any(), any()) } returns TavilySearchOutcome.Grounded(
            MultiUrlResult.Fused(
                block = "Source [1] search",
                okUrls = listOf("https://s"),
                skippedUrls = emptyList(),
            )
        )
        coEvery { multiUrlFetcher.fetchAll(any(), any(), any()) } returns MultiUrlResult.Fused(
            block = "Source [1] fetch",
            okUrls = listOf("https://f"),
            skippedUrls = emptyList(),
        )
        val transport = FakeTransport(
            listOf(
                FakeTurn(
                    terminal = textTerminal("", toolCalls = listOf(ToolCall("web_search", mapOf("query" to "q")))),
                    thoughts = listOf("need fresh info"),
                ),
                FakeTurn(
                    terminal = textTerminal("", toolCalls = listOf(ToolCall("web_fetch", mapOf("url" to "https://f")))),
                ),
                FakeTurn(terminal = textTerminal("Final answer"), texts = listOf("Final answer")),
            )
        )
        val tokens = flow<StreamToken> { drive(transport) }.toList()

        assertThat(tokens).containsExactly(
            StreamToken.ToolStatus("Searching for \"q\"…"),
            StreamToken.ToolStatus(null),
            StreamToken.ToolStatus("Reading f…"),
            StreamToken.ToolStatus(null),
            StreamToken.Delta("Final answer"),
            StreamToken.Done(reasoning = "need fresh info"),
        ).inOrder()
        // Tool results fed back as Message.tool(ToolResponse), never into Deltas.
        assertThat(toolResponses(transport.replies[1])).containsExactly(
            "web_search" to "Source [1] search",
        )
        assertThat(toolResponses(transport.replies[2])).containsExactly(
            "web_fetch" to "Source [1] fetch",
        )
        assertThat(tokens.filterIsInstance<StreamToken.Delta>().map { it.content })
            .doesNotContain("Source [1] search")
    }

    @Test
    fun `cap reached feeds continuation string - one call over the cap`() = runTest {
        every { webPageFetcher.hasValidatedInternet() } returns true
        coEvery { ddg.search(any(), any(), any(), any()) } returns TavilySearchOutcome.Grounded(
            MultiUrlResult.Fused(block = "S", okUrls = listOf("https://s"), skippedUrls = emptyList())
        )
        val sixCalls = (1..6).map { ToolCall("web_search", mapOf("query" to "q$it")) }
        val transport = FakeTransport(
            listOf(
                FakeTurn(terminal = textTerminal("", toolCalls = sixCalls)),
                FakeTurn(terminal = textTerminal("Answered"), texts = listOf("Answered")),
            )
        )
        val tokens = flow<StreamToken> { drive(transport) }.toList()

        coVerify(exactly = 5) { ddg.search(any(), any(), any(), any()) }
        val fed = toolResponses(transport.replies[1])
        assertThat(fed).hasSize(6)
        assertThat(fed.last()).isEqualTo("web_search" to LocalToolLoop.CAP_REACHED_STRING)
        assertThat(tokens.last()).isEqualTo(StreamToken.Done(reasoning = null))
    }

    @Test
    fun `second tool request after cap finishes instead of ping-ponging`() = runTest {
        every { webPageFetcher.hasValidatedInternet() } returns true
        coEvery { ddg.search(any(), any(), any(), any()) } returns TavilySearchOutcome.Grounded(
            MultiUrlResult.Fused(block = "S", okUrls = listOf("https://s"), skippedUrls = emptyList())
        )
        val fiveCalls = (1..5).map { ToolCall("web_search", mapOf("query" to "q$it")) }
        val transport = FakeTransport(
            listOf(
                FakeTurn(terminal = textTerminal("", toolCalls = fiveCalls)),
                FakeTurn(
                    terminal = textTerminal("", toolCalls = listOf(ToolCall("web_search", mapOf("query" to "more")))),
                    texts = listOf("Gathered "),
                ),
                FakeTurn(
                    terminal = textTerminal("", toolCalls = listOf(ToolCall("web_search", mapOf("query" to "again")))),
                ),
            )
        )
        val tokens = flow<StreamToken> { drive(transport) }.toList()

        // 5 real calls + the 6th fed the cap string; the 7th never executes.
        coVerify(exactly = 5) { ddg.search(any(), any(), any(), any()) }
        assertThat(toolResponses(transport.replies[2]).single().second)
            .isEqualTo(LocalToolLoop.CAP_REACHED_STRING)
        assertThat(tokens.last()).isInstanceOf(StreamToken.Done::class.java)
        assertThat(tokens.filterIsInstance<StreamToken.Delta>().map { it.content })
            .contains("Gathered ")
    }

    @Test
    fun `cancellation propagates - never Done or Error`() = runTest {
        val p = provider()
        val transport = object : AgenticTurnTransport {
            override var isAlive: Boolean = true
            override suspend fun collectTurn(
                first: Contents?,
                reply: Message?,
                onText: suspend (String) -> Unit,
                onThought: suspend (String) -> Unit,
            ): AgenticTurn = throw CancellationException("stop")
        }
        val seen = mutableListOf<StreamToken>()
        assertThrows<CancellationException> {
            flow<StreamToken> {
                with(p) { runToolLoop(transport, Contents.of("q"), 4096) }
            }.toCollection(seen)
        }
        assertThat(seen).isEmpty()
    }

    // ------------------------------------------------------------------
    // Quick-task (agentic-rows): ToolCompleted sources plumbing.
    // ------------------------------------------------------------------

    private fun searchDetails() = listOf(
        com.warped.domain.model.GroundedSource(
            url = "https://s1.example/a",
            extractedText = "Search text.",
            status = com.warped.domain.model.GroundedSourceStatus.OK,
            ogTitle = "S1",
            ogDescription = "Desc S1",
            ogImageUrl = "https://s1.example/img.png",
        ),
        com.warped.domain.model.GroundedSource(
            url = "https://dead.example/x",
            extractedText = null,
            status = com.warped.domain.model.GroundedSourceStatus.OMITIDA,
        ),
    )

    @Test
    fun `executed search emits ToolCompleted carrying structured sources`() = runTest {
        every { webPageFetcher.hasValidatedInternet() } returns true
        val details = searchDetails()
        coEvery { ddg.search(any(), any(), any(), any()) } returns TavilySearchOutcome.Grounded(
            MultiUrlResult.Fused(
                block = "Source [1] search",
                okUrls = listOf("https://s1.example/a"),
                skippedUrls = listOf("https://dead.example/x"),
                details = details,
            )
        )
        val transport = FakeTransport(
            listOf(
                FakeTurn(
                    terminal = textTerminal("", toolCalls = listOf(ToolCall("web_search", mapOf("query" to "q")))),
                ),
                FakeTurn(terminal = textTerminal("Final"), texts = listOf("Final")),
            )
        )
        val tokens = flow<StreamToken> { drive(transport) }.toList()

        val completed = tokens.filterIsInstance<StreamToken.ToolCompleted>()
        assertThat(completed).hasSize(1)
        // Same row shape incl. OG columns, same order; stable tool id.
        assertThat(completed.single().sources).isEqualTo(details)
        assertThat(completed.single().toolId).isNotEmpty()
        // Model-facing text mapping untouched.
        assertThat(toolResponses(transport.replies[1]).single().second)
            .isEqualTo("Source [1] search")
    }

    @Test
    fun `search plus fetch union emits one ToolCompleted per call`() = runTest {
        every { webPageFetcher.hasValidatedInternet() } returns true
        val searchRows = searchDetails()
        val fetchRows = listOf(
            com.warped.domain.model.GroundedSource(
                url = "https://f.example/p",
                extractedText = "Fetch text.",
                status = com.warped.domain.model.GroundedSourceStatus.OK,
            ),
        )
        coEvery { ddg.search(any(), any(), any(), any()) } returns TavilySearchOutcome.Grounded(
            MultiUrlResult.Fused(
                block = "Source [1] search",
                okUrls = listOf("https://s1.example/a"),
                skippedUrls = listOf("https://dead.example/x"),
                details = searchRows,
            )
        )
        coEvery { multiUrlFetcher.fetchAll(any(), any(), any()) } returns MultiUrlResult.Fused(
            block = "Source [1] fetch",
            okUrls = listOf("https://f.example/p"),
            skippedUrls = emptyList(),
            details = fetchRows,
        )
        val transport = FakeTransport(
            listOf(
                FakeTurn(
                    terminal = textTerminal("", toolCalls = listOf(ToolCall("web_search", mapOf("query" to "q")))),
                ),
                FakeTurn(
                    terminal = textTerminal("", toolCalls = listOf(ToolCall("web_fetch", mapOf("url" to "https://f.example/p")))),
                ),
                FakeTurn(terminal = textTerminal("Final"), texts = listOf("Final")),
            )
        )
        val tokens = flow<StreamToken> { drive(transport) }.toList()

        val completed = tokens.filterIsInstance<StreamToken.ToolCompleted>()
        assertThat(completed).hasSize(2)
        assertThat(completed[0].sources).isEqualTo(searchRows)
        assertThat(completed[1].sources).isEqualTo(fetchRows)
        // Distinct tool ids per call.
        assertThat(completed[0].toolId).isNotEqualTo(completed[1].toolId)
    }

    @Test
    fun `zero-tool turn and short-circuits emit no ToolCompleted`() = runTest {
        every { webPageFetcher.hasValidatedInternet() } returns true
        // Zero-tool turn: plain answer, no tool rows at all.
        val plain = FakeTransport(
            listOf(FakeTurn(terminal = textTerminal("Hi"), texts = listOf("Hi")))
        )
        val plainTokens = flow<StreamToken> { drive(plain) }.toList()
        assertThat(plainTokens.filterIsInstance<StreamToken.ToolCompleted>()).isEmpty()

        // Short-circuit (blank query): validation feeds back with no
        // status/completed rows and no socket.
        val short = FakeTransport(
            listOf(
                FakeTurn(
                    terminal = textTerminal("", toolCalls = listOf(ToolCall("web_search", mapOf("query" to "  ")))),
                ),
                FakeTurn(terminal = textTerminal("Hi"), texts = listOf("Hi")),
            )
        )
        val shortTokens = flow<StreamToken> { drive(short) }.toList()
        assertThat(shortTokens.filterIsInstance<StreamToken.ToolCompleted>()).isEmpty()
        assertThat(shortTokens.filterIsInstance<StreamToken.ToolStatus>()).isEmpty()
        coVerify(exactly = 0) { ddg.search(any(), any(), any(), any()) }
    }

    @Test
    fun `detailed executor keeps text mapping identical to the string executor`() = runTest {
        every { webPageFetcher.hasValidatedInternet() } returns true
        coEvery { ddg.search(any(), any(), any(), any()) } returns TavilySearchOutcome.Grounded(
            MultiUrlResult.Fused(
                block = "Source [1] search",
                okUrls = listOf("https://s"),
                skippedUrls = emptyList(),
                details = listOf(
                    com.warped.domain.model.GroundedSource(
                        url = "https://s",
                        extractedText = "Search text.",
                        status = com.warped.domain.model.GroundedSourceStatus.OK,
                    ),
                ),
            )
        )
        val p = provider()
        val call = ToolCall("web_search", mapOf("query" to "q"))
        assertThat(p.executeToolCallDetailed(call, 4096).text)
            .isEqualTo(p.executeToolCall(call, 4096))
        assertThat(p.executeToolCallDetailed(call, 4096).sources.map { it.url })
            .containsExactly("https://s")
    }

    // ------------------------------------------------------------------
    // Quick-task (loop-images): includeImages + fused-images threading.
    // ------------------------------------------------------------------

    @Test
    fun `loop web_search requests images from the repository`() = runTest {
        every { webPageFetcher.hasValidatedInternet() } returns true
        coEvery { ddg.search(any(), any(), any(), any()) } returns TavilySearchOutcome.Grounded(
            MultiUrlResult.Fused(
                block = "Source [1] search",
                okUrls = listOf("https://s1.example/a"),
                skippedUrls = emptyList(),
                details = searchDetails().take(1),
                images = listOf("https://s1.example/img1.png"),
            )
        )
        val transport = FakeTransport(
            listOf(
                FakeTurn(
                    terminal = textTerminal("", toolCalls = listOf(ToolCall("web_search", mapOf("query" to "q")))),
                ),
                FakeTurn(terminal = textTerminal("Final"), texts = listOf("Final")),
            )
        )
        flow<StreamToken> { drive(transport) }.toList()

        // includeImages=true ALWAYS: the DDG leg ignores it, only the keyed
        // Tavily fallback leg uses it (credit-capped).
        coVerify(exactly = 1) { ddg.search("q", any(), any(), includeImages = true) }
    }

    @Test
    fun `executed search emits ToolCompleted carrying fused images`() = runTest {
        every { webPageFetcher.hasValidatedInternet() } returns true
        val withImages = MultiUrlResult.Fused(
            block = "Source [1] search",
            okUrls = listOf("https://s1.example/a"),
            skippedUrls = emptyList(),
            details = searchDetails().take(1),
            images = listOf(
                "https://s1.example/img1.png",
                "https://s1.example/img2.png",
            ),
        )
        val withoutImages = MultiUrlResult.Fused(
            block = "Source [1] other",
            okUrls = listOf("https://o.example/b"),
            skippedUrls = emptyList(),
            details = listOf(
                com.warped.domain.model.GroundedSource(
                    url = "https://o.example/b",
                    extractedText = "Other text.",
                    status = com.warped.domain.model.GroundedSourceStatus.OK,
                ),
            ),
        )
        coEvery { ddg.search("q1", any(), any(), any()) } returns TavilySearchOutcome.Grounded(withImages)
        coEvery { ddg.search("q2", any(), any(), any()) } returns TavilySearchOutcome.Grounded(withoutImages)
        val transport = FakeTransport(
            listOf(
                FakeTurn(
                    terminal = textTerminal("", toolCalls = listOf(ToolCall("web_search", mapOf("query" to "q1")))),
                ),
                FakeTurn(
                    terminal = textTerminal("", toolCalls = listOf(ToolCall("web_search", mapOf("query" to "q2")))),
                ),
                FakeTurn(terminal = textTerminal("Final"), texts = listOf("Final")),
            )
        )
        val tokens = flow<StreamToken> { drive(transport) }.toList()

        val completed = tokens.filterIsInstance<StreamToken.ToolCompleted>()
        assertThat(completed).hasSize(2)
        // Fused Tavily images[] carried verbatim on the search turn.
        assertThat(completed[0].images).containsExactly(
            "https://s1.example/img1.png",
            "https://s1.example/img2.png",
        ).inOrder()
        // Sources-only outcomes stay imageless.
        assertThat(completed[1].images).isEmpty()
        assertThat(completed[1].sources.map { it.url }).containsExactly("https://o.example/b")
    }
}
