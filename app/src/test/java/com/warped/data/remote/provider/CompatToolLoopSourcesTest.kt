package com.warped.data.remote.provider

import com.google.common.truth.Truth.assertThat
import com.sun.net.httpserver.HttpServer
import com.warped.data.agentic.LocalToolLoop
import com.warped.data.grounding.DuckDuckGoSearchRepository
import com.warped.data.grounding.MultiUrlFetcher
import com.warped.data.grounding.MultiUrlResult
import com.warped.data.grounding.SearchOutcome
import com.warped.data.grounding.WebPageFetcher
import com.warped.data.remote.dto.OpenAiMessage
import com.warped.domain.model.ChatMessage
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.GroundedSource
import com.warped.domain.model.GroundedSourceStatus
import com.warped.domain.model.Role
import com.warped.domain.model.StreamToken
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import org.junit.jupiter.api.Tag

/**
 * Quick-task (agentic-rows): the shared compat loop surfaces per-call
 * structured sources on `ToolCompleted` so the VM persists Fuentes rows on
 * Done — same row shape (incl. OG columns) as local loop turns.
 *
 * The wire is a JDK-embedded `HttpServer` (no extra test deps): round 1
 * returns non-streaming `tool_calls` JSON (Pitfall-3 path), round 2 the
 * final answer. The `web_search` executor is a mocked DDG repository with
 * known details.
 */
@Tag("slow")
class CompatToolLoopSourcesTest {

    private val ddg = mockk<DuckDuckGoSearchRepository>()
    private val multiUrlFetcher = mockk<MultiUrlFetcher>()
    private val webPageFetcher = mockk<WebPageFetcher>()
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var server: HttpServer
    private var postUrl: String = ""

    @BeforeEach
    fun setUp() {
        every { webPageFetcher.hasValidatedInternet() } returns true
    }

    @AfterEach
    fun tearDown() {
        if (::server.isInitialized) server.stop(0)
    }

    private fun startServer(firstBody: String, secondBody: String) {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var hits = 0
        server.createContext("/chat") { exchange ->
            hits++
            val body = if (hits == 1) firstBody else secondBody
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        postUrl = "http://127.0.0.1:${server.address.port}/chat"
    }

    private val searchDetails = listOf(
        GroundedSource(
            url = "https://r.example/news",
            extractedText = "Remote text.",
            status = GroundedSourceStatus.OK,
            ogTitle = "R",
            ogDescription = "Desc R",
            ogImageUrl = "https://r.example/img.png",
        ),
        GroundedSource(
            url = "https://dead.example/x",
            extractedText = null,
            status = GroundedSourceStatus.OMITIDA,
        ),
    )

    @Test
    fun `compat turn emits ToolCompleted carrying structured sources`() = runTest {
        coEvery { ddg.search(any(), any(), any(), any()) } returns SearchOutcome.Grounded(
            MultiUrlResult.Fused(
                block = "Source [1] remote",
                okUrls = listOf("https://r.example/news"),
                skippedUrls = listOf("https://dead.example/x"),
                details = searchDetails,
                images = listOf("https://r.example/grid1.png"),
            )
        )
        startServer(
            firstBody = """{"choices":[{"message":{"tool_calls":[{"id":"call_1","type":"function","function":{"name":"web_search","arguments":"{\"query\":\"q\"}"}}]}}]}""",
            secondBody = """{"choices":[{"message":{"content":"done answer"}}]}""",
        )
        val request = ChatRequest(
            messages = listOf(ChatMessage(role = Role.USER, content = "q")),
        )

        val tokens = flow<StreamToken> {
            with(CompatToolLoop) {
                runTurn(
                    client = client,
                    json = json,
                    postUrl = postUrl,
                    modelId = "test-model",
                    baseMessages = listOf(OpenAiMessage(role = "user", content = "q")),
                    request = request,
                    ddg = ddg,
                    multiUrlFetcher = multiUrlFetcher,
                    webPageFetcher = webPageFetcher,
                    logTag = "Test",
                    onCallCreated = {},
                    onCallCleared = {},
                )
            }
        }.toList()

        val completed = tokens.filterIsInstance<StreamToken.ToolCompleted>()
        assertThat(completed).hasSize(1)
        assertThat(completed.single().toolId).isEqualTo("call_1")
        // Same row shape incl. OG columns, same order.
        assertThat(completed.single().sources).isEqualTo(searchDetails)
        // Quick-task (loop-images): fused search images[] carried verbatim.
        assertThat(completed.single().images).containsExactly("https://r.example/grid1.png")
        // Turn still answers normally from gathered context.
        assertThat(tokens.filterIsInstance<StreamToken.Delta>().map { it.content })
            .contains("done answer")
        assertThat(tokens.last()).isInstanceOf(StreamToken.Done::class.java)
        // Quick-task (loop-images): includeImages=true ALWAYS (DDG leg
        // ignores it — no image API).
        coVerify(exactly = 1) { ddg.search(any(), any(), any(), includeImages = true) }
    }

    @Test
    fun `answer-only turn emits no ToolCompleted`() = runTest {
        startServer(
            firstBody = """{"choices":[{"message":{"content":"plain answer"}}]}""",
            secondBody = """{"choices":[{"message":{"content":"unreached"}}]}""",
        )
        val request = ChatRequest(
            messages = listOf(ChatMessage(role = Role.USER, content = "q")),
        )

        val tokens = flow<StreamToken> {
            with(CompatToolLoop) {
                runTurn(
                    client = client,
                    json = json,
                    postUrl = postUrl,
                    modelId = "test-model",
                    baseMessages = listOf(OpenAiMessage(role = "user", content = "q")),
                    request = request,
                    ddg = ddg,
                    multiUrlFetcher = multiUrlFetcher,
                    webPageFetcher = webPageFetcher,
                    logTag = "Test",
                    onCallCreated = {},
                    onCallCleared = {},
                )
            }
        }.toList()

        assertThat(tokens.filterIsInstance<StreamToken.ToolCompleted>()).isEmpty()
        assertThat(tokens.last()).isInstanceOf(StreamToken.Done::class.java)
        coVerify(exactly = 0) { ddg.search(any(), any(), any(), any()) }
    }

    @Test
    fun `shared mapping helpers keep text and sources in sync`() {
        // The OpenAI/Anthropic inline loops use these same helpers, so this
        // pins the text/sources contract for all three remote drivers.
        val outcome = SearchOutcome.Grounded(
            MultiUrlResult.Fused(
                block = "BLOQUE",
                okUrls = listOf("https://r.example/news"),
                skippedUrls = emptyList(),
                details = searchDetails.take(1),
                images = listOf("https://r.example/grid1.png"),
            ),
        )
        assertThat(LocalToolLoop.mapSearchOutcome(outcome)).isEqualTo("BLOQUE")
        assertThat(LocalToolLoop.searchSources(outcome)).isEqualTo(searchDetails.take(1))
        // Quick-task (loop-images): same shared-helper coverage for the
        // images leg (all four loop executors populate from this).
        assertThat(LocalToolLoop.searchImages(outcome))
            .containsExactly("https://r.example/grid1.png")
    }

    /**
     * Transient-rejection tolerance (device evidence 2026-10-03): a lone
     * 400 naming tools is retried armed once before concluding
     * incapability — the server accepts the identical body right after.
     */
    private fun startFlakyServer(firstCode: Int, firstBody: String, restBody: String) {
        startFlakyServer(listOf(firstCode to firstBody), restBody)
    }

    /**
     * Scripted rounds: each hit consumes the next (code, body); hits past
     * the script replay the last entry. Lets the double-400 test fail the
     * armed attempt AND the armed retry, then answer the model-only
     * fallback cleanly.
     */
    private fun startFlakyServer(script: List<Pair<Int, String>>, restBody: String) {
        val hits = java.util.concurrent.atomic.AtomicInteger(0)
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/chat") { exchange ->
            val n = hits.incrementAndGet()
            val (code, body) = script.getOrElse(n - 1) { 200 to restBody }
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        postUrl = "http://127.0.0.1:${server.address.port}/chat"
    }

    private suspend fun runTurnTokens(): List<StreamToken> {
        val request = ChatRequest(
            messages = listOf(ChatMessage(role = Role.USER, content = "q")),
        )
        return kotlinx.coroutines.flow.flow<StreamToken> {
            with(CompatToolLoop) {
                runTurn(
                    client = client,
                    json = json,
                    postUrl = postUrl,
                    modelId = "test-model",
                    baseMessages = listOf(OpenAiMessage(role = "user", content = "q")),
                    request = request,
                    ddg = ddg,
                    multiUrlFetcher = multiUrlFetcher,
                    webPageFetcher = webPageFetcher,
                    logTag = "Test",
                    onCallCreated = {},
                    onCallCleared = {},
                )
            }
        }.toList()
    }

    @Test
    fun `transient tools four hundred retries armed without notice`() = runTest {
        startFlakyServer(
            firstCode = 400,
            firstBody = """{"error":{"message":"tool call failed transiently","type":"invalid_request_error"}}""",
            restBody = """{"choices":[{"message":{"content":"recovered answer"}}]}""",
        )

        val tokens = runTurnTokens()

        assertThat(tokens.filterIsInstance<StreamToken.ToolsUnsupported>()).isEmpty()
        assertThat(tokens.filterIsInstance<StreamToken.Delta>().map { it.content })
            .contains("recovered answer")
        assertThat(tokens.last()).isInstanceOf(StreamToken.Done::class.java)
    }

    @Test
    fun `double tools four hundred falls back with exactly one notice`() = runTest {
        val rejection = """{"error":{"message":"model does not support tool use","type":"invalid_request_error"}}"""
        startFlakyServer(
            script = listOf(
                400 to rejection,
                400 to rejection,
            ),
            restBody = """{"choices":[{"message":{"content":"model-only answer"}}]}""",
        )

        val tokens = runTurnTokens()

        // Armed retry + model-only fallback both run to completion.
        assertThat(tokens.filterIsInstance<StreamToken.ToolsUnsupported>()).hasSize(1)
        assertThat(tokens.filterIsInstance<StreamToken.Delta>().map { it.content })
            .contains("model-only answer")
        assertThat(tokens.last()).isInstanceOf(StreamToken.Done::class.java)
    }

    @Test
    fun `compat rounds carry full sampling params`() = runTest {
        var capturedBody = ""
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/chat") { exchange ->
            capturedBody = exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
            val body = """{"choices":[{"message":{"content":"ok"}}]}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        postUrl = "http://127.0.0.1:${server.address.port}/chat"

        runTurnTokens()

        // Docs-supported sampling (top_k, repeat_penalty, seed) must ride
        // the compat rounds like the native path — silent drops here
        // meant inconsistent sampling across paths.
        val parsed = kotlinx.serialization.json.Json.parseToJsonElement(capturedBody).jsonObject
        assertThat(parsed["top_k"]?.jsonPrimitive?.int).isEqualTo(40)
        assertThat(parsed["repeat_penalty"]?.jsonPrimitive?.double ?: -1.0)
            .isWithin(0.001).of(1.1)
        assertThat(parsed["max_tokens"]?.jsonPrimitive?.int).isEqualTo(2048)
        // Seed -1 (random) encodes null, never a literal -1 the server
        // could pin.
        val seed = parsed["seed"]
        assertThat(seed == null || seed is kotlinx.serialization.json.JsonNull).isTrue()
    }

    @Test
    fun `compat rounds carry the current-turn image as image_url`() = runTest {
        var capturedBody = ""
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/chat") { exchange ->
            capturedBody = exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
            val body = """{"choices":[{"message":{"content":"veo una foto"}}]}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        postUrl = "http://127.0.0.1:${server.address.port}/chat"

        // History carry (last message excluded) + live turn images, like
        // the LM Studio armed branch builds them.
        val history = com.warped.domain.model.ChatMessage(
            role = com.warped.domain.model.Role.USER,
            content = "old",
        )
        val live = com.warped.domain.model.ChatMessage(
            role = com.warped.domain.model.Role.USER,
            content = "describe esta imagen.",
        )
        val base = mapOpenAiHistory(
            listOf(history, live),
            includeSystem = false,
            sanitizeUser = { it },
            currentImages = listOf("data:image/png;base64,QUJD"),
        )
        val request = ChatRequest(
            messages = listOf(history, live),
        )
        kotlinx.coroutines.flow.flow<StreamToken> {
            with(CompatToolLoop) {
                runTurn(
                    client = client,
                    json = json,
                    postUrl = postUrl,
                    modelId = "test-model",
                    baseMessages = base,
                    request = request,
                    ddg = ddg,
                    multiUrlFetcher = multiUrlFetcher,
                    webPageFetcher = webPageFetcher,
                    logTag = "Test",
                    onCallCreated = {},
                    onCallCleared = {},
                )
            }
        }.toList()

        val parsed = kotlinx.serialization.json.Json.parseToJsonElement(capturedBody).jsonObject
        val messages = parsed["messages"]!!.jsonArray
        val last = messages.last().jsonObject
        val parts = last["content"]!!.jsonArray
        val images = parts.filter {
            it.jsonObject["type"]?.jsonPrimitive?.content == "image_url"
        }
        assertThat(images).hasSize(1)
        assertThat(
            images.single().jsonObject["image_url"]!!.jsonObject["url"]?.jsonPrimitive?.content
        ).isEqualTo("data:image/png;base64,QUJD")
    }
}
