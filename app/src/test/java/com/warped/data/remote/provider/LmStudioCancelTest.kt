package com.warped.data.remote.provider

import com.google.common.truth.Truth.assertThat
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.EngineType
import com.warped.data.local.inference.ActiveEngine
import com.warped.data.local.inference.InputSanitizer
import com.warped.data.local.inference.LiteRTLmEngine
import com.warped.data.local.inference.LiteRTLmProvider
import com.warped.data.local.inference.LiteRtLlmHelper
import com.warped.data.local.security.ApiKeyStore
import com.warped.domain.model.ActiveModelSelection
import kotlinx.coroutines.flow.MutableStateFlow
import com.warped.domain.model.ChatMessage
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.Endpoint
import com.warped.domain.model.GenerationParameters
import com.warped.domain.model.ProviderType
import com.warped.domain.model.Role
import com.warped.domain.model.StreamToken
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.OutputStream
import java.net.InetSocketAddress
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 46-01 RUNTIME-14 tracer: true transport cancellation regression tests.
 *
 * (d) Cancelling collection of [LMStudioProvider.chat] terminates promptly with no
 *     Error bubble (defect 6: CancellationException-as-Error + no Call handle).
 *     Runs against a real loopback SSE server over raw OkHttp.
 * (e) `stopResponse()` reaches the real transport handle: `Call.cancel()` (remote)
 *     and `cancelActiveGeneration()` (local, verified in litertlm 0.17.1 AAR).
 * (f) The LiteRT retry path never retries a cancelled turn (ensureActive guard).
 *
 * HTTP tests use runBlocking (real socket IO is incompatible with runTest virtual
 * time); mock-only test (f) uses runTest.
 */

/** Phase 49 (DEL-01): single-turn plain chat — helper takes no skill/tool deps. */
private fun testHelper(apiKeyStore: ApiKeyStore): LmStudioHelper =
    LmStudioHelper(
        InputSanitizer(),
        apiKeyStore,
    )
class LmStudioCancelTest {

    private var server: HttpServer? = null
    private var executor: ExecutorService? = null

    @AfterEach
    fun tearDown() {
        runCatching { server?.stop(0) }
        runCatching { executor?.shutdownNow() }
        server = null
        executor = null
    }

    // ------------------------------------------------------------------
    // Loopback SSE server fixtures (JDK HttpServer, no new dependencies).
    // ------------------------------------------------------------------

    private fun startServer(chatHandler: HttpHandler): String {
        val exec = Executors.newCachedThreadPool { r ->
            Thread(r).also { it.isDaemon = true }
        }
        val srv = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        srv.createContext("/api/v1/models/load", HttpHandler { exchange ->
            exchange.requestBody.readAllBytes()
            val body = """{"type":"loaded","instance_id":"test-inst-1","load_time_seconds":0.1,"status":"ok"}"""
                .toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        })
        srv.createContext("/api/v1/chat", chatHandler)
        srv.executor = exec
        srv.start()
        server = srv
        executor = exec
        return "http://127.0.0.1:${srv.address.port}/"
    }

    private fun sseDelta(out: OutputStream, content: String) {
        val line = "event: message.delta\n" +
            "data: {\"type\":\"message.delta\",\"content\":\"$content\"}\n\n"
        out.write(line.toByteArray())
        out.flush()
    }

    /** Scripted stream: tok1@100ms, tok2@300ms, tok3@1200ms, tok4@1400ms, DONE@1600ms. */
    private val scriptedHandler = HttpHandler { exchange: HttpExchange ->
        exchange.requestBody.readAllBytes()
        exchange.responseHeaders.add("Content-Type", "text/event-stream")
        exchange.sendResponseHeaders(200, 0)
        val out = exchange.responseBody
        try {
            Thread.sleep(100); sseDelta(out, "tok1")
            Thread.sleep(200); sseDelta(out, "tok2")
            Thread.sleep(900); sseDelta(out, "tok3")
            Thread.sleep(200); sseDelta(out, "tok4")
            Thread.sleep(200)
            out.write("data: [DONE]\n\n".toByteArray())
            out.flush()
        } catch (_: Exception) {
            // Client went away (Call.cancel) — expected on Stop.
        } finally {
            runCatching { out.close() }
        }
    }

    /** Two tokens then holds the socket open: proves prompt teardown on cancel. */
    private val holdHandler = HttpHandler { exchange: HttpExchange ->
        exchange.requestBody.readAllBytes()
        exchange.responseHeaders.add("Content-Type", "text/event-stream")
        exchange.sendResponseHeaders(200, 0)
        val out = exchange.responseBody
        try {
            Thread.sleep(100); sseDelta(out, "tok1")
            Thread.sleep(200); sseDelta(out, "tok2")
            Thread.sleep(30_000) // hold open: client must tear down via Call.cancel
        } catch (_: Exception) {
            // Client went away — expected post-fix.
        } finally {
            runCatching { out.close() }
        }
    }

    private fun chatRequest() = ChatRequest(
        messages = listOf(ChatMessage(role = Role.USER, content = "hello")),
        parameters = GenerationParameters(),
    )

    // ------------------------------------------------------------------
    // (d) CancellationException transparency + prompt teardown (defect 6).
    // ------------------------------------------------------------------

    @Test
    fun `cancelling chat collection terminates promptly with no Error token`() = runBlocking {
        val baseUrl = startServer(holdHandler)
        val provider = LMStudioProvider(
            baseUrl = baseUrl,
            modelId = "test-model",
            apiKey = null,
            inputSanitizer = InputSanitizer(),
        )

        val tokens = Collections.synchronizedList(mutableListOf<StreamToken>())
        val job = launch(Dispatchers.IO) {
            provider.chat(chatRequest()).collect { tokens.add(it) }
        }
        val gotTokens = withTimeoutOrNull(10_000) {
            while (tokens.size < 2) delay(50)
            true
        } ?: false
        assertThat(gotTokens).isTrue()

        job.cancel()
        // RUNTIME-14: teardown must be prompt (Call.cancel tears down the socket).
        // Pre-fix the provider stays blocked in readUtf8Line with no Call handle.
        val finished = withTimeoutOrNull(5_000) {
            job.join()
            true
        } ?: false
        assertThat(finished).isTrue()
        assertThat(tokens.filterIsInstance<StreamToken.Error>()).isEmpty()
    }

    // ------------------------------------------------------------------
    // (e) Remote: stopResponse halts the stream via the real Call handle.
    // ------------------------------------------------------------------

    @Test
    fun `helper stopResponse halts remote stream with no trailing deltas`() = runBlocking {
        val baseUrl = startServer(scriptedHandler)
        val apiKeyStore = mockk<ApiKeyStore>()
        every { apiKeyStore.getKey(any()) } returns null
        val helper = testHelper(apiKeyStore)
        helper.setEndpoint(
            Endpoint(
                id = 1L,
                name = "loopback",
                url = baseUrl,
                apiType = ProviderType.LM_STUDIO,
                modelId = "test-model",
            ),
        )
        helper.initialize("test-model")

        val arrivals = Collections.synchronizedList(mutableListOf<Pair<StreamToken, Long>>())
        val job = launch(Dispatchers.IO) {
            helper.runInference(chatRequest(), false).collect {
                arrivals.add(it to System.currentTimeMillis())
            }
        }
        val gotTokens = withTimeoutOrNull(10_000) {
            while (arrivals.size < 2) delay(50)
            true
        } ?: false
        assertThat(gotTokens).isTrue()

        // Stop ~t=400ms; tok3/tok4 are scheduled @1200/@1400ms.
        val tStop = System.currentTimeMillis()
        helper.stopResponse()
        delay(2_000) // past tok3/tok4/DONE schedule
        runCatching { job.cancel(); job.join() }

        // RUNTIME-14: no trailing Deltas after Stop, and no fake Error bubble.
        val lateDeltas = arrivals.filter { (token, at) ->
            token is StreamToken.Delta && at > tStop + 400
        }
        assertThat(lateDeltas).isEmpty()
        assertThat(arrivals.map { it.first }.filterIsInstance<StreamToken.Error>()).isEmpty()
    }

    @Test
    fun `provider retains cancellable Call handle for chat`() {
        // Contract assertion (plain Java reflection: compiles pre-fix, fails pre-fix).
        val names = LMStudioProvider::class.java.methods.map { it.name }
        assertThat(names).contains("cancelChat")
    }

    // ------------------------------------------------------------------
    // (e) Local: stopResponse delegates to cancelActiveGeneration.
    // ------------------------------------------------------------------

    @Test
    fun `provider exposes cancelActiveGeneration accessor`() {
        // Contract assertion (plain Java reflection: compiles pre-fix, fails pre-fix).
        val names = LiteRTLmProvider::class.java.methods.map { it.name }
        assertThat(names).contains("cancelActiveGeneration")
    }

    @Test
    fun `local stopResponse delegates to transport cancelProcess`() {
        val conversation = mockk<Conversation>(relaxed = true)
        val engineManager = mockk<EngineManager>(relaxed = true)
        val activeSelection = mockk<ActiveModelSelection>(relaxed = true)
        val provider = LiteRTLmProvider(
            engineManager,
            InputSanitizer(),
            activeSelection,
        )
        LiteRTLmProvider::class.java.getDeclaredField("activeConversation").apply {
            isAccessible = true
        }.set(provider, conversation)

        val helper = LiteRtLlmHelper(provider, engineManager)
        helper.stopResponse()

        // RUNTIME-14: Stop must reach Conversation.cancelProcess (0.17.1 AAR),
        // never a sentinel no-op Job.
        verify(exactly = 1) { conversation.cancelProcess() }
    }

    // ------------------------------------------------------------------
    // (f) Retry path never retries a cancelled turn (ensureActive guard).
    // ------------------------------------------------------------------

    @Test
    fun `cancelled turn never retries engine error`() = runTest {
        val attempts = AtomicInteger(0)
        val conversation = mockk<Conversation>()
        every { conversation.isAlive } returns true
        lateinit var collectJob: Job
        every { conversation.sendMessageAsync(any<Contents>()) } answers {
            attempts.incrementAndGet()
            val turnJob: Job? = try { collectJob } catch (_: Exception) { null }
            flow {
                // Cancel the collecting turn, then surface an engine-flavored error.
                turnJob?.cancel()
                throw IllegalStateException("Conversation not alive")
            }
        }
        val engine = mockk<LiteRTLmEngine>()
        every { engine.isInitialized() } returns true
        val engineManager = mockk<EngineManager>()
        every { engineManager.getActiveEngine() } returns
            ActiveEngine(EngineType.LITE_RT_LM, "model-x")
        every { engineManager.getLiteRTLmEngine() } returns engine
        every { engineManager.createLiteRTConversation(any()) } returns conversation
        every { engineManager.switchToLiteRT(any()) } just Runs
        val activeSelection = mockk<ActiveModelSelection>(relaxed = true)
        every { activeSelection.activeModel } returns MutableStateFlow(null)

        val provider = LiteRTLmProvider(
            engineManager,
            InputSanitizer(),
            activeSelection,
        )
        collectJob = launch {
            provider.chat(chatRequest()).collect { }
        }
        runCatching { collectJob.join() }

        // RUNTIME-14 / Pitfall 4: a cancelled turn must not resurrect via retry.
        assertThat(attempts.get()).isEqualTo(1)
    }
}
