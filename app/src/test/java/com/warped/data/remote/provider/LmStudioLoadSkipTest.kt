package com.warped.data.remote.provider

import com.warped.data.local.inference.InputSanitizer
import com.warped.data.local.security.ApiKeyStore
import com.warped.domain.model.Endpoint
import com.warped.domain.model.ProviderType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import com.google.common.truth.Truth.assertThat
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Tag

/**
 * User report 2026-10-03: every question POSTed `/api/v1/models/load`,
 * making LM Studio reload (and OOM-kill) the model on every turn.
 * `initialize()` must skip the reload when the server still holds the
 * same model, and reload when it was evicted.
 *
 * Loopback HttpServer fixtures (no new dependencies); runBlocking
 * (real socket IO is incompatible with runTest virtual time).
 */
@Tag("slow")
class LmStudioLoadSkipTest {

    private var server: HttpServer? = null
    private val loadCount = AtomicInteger(0)
    @Volatile private var loadedKeys: List<String> = listOf("m1")

    @AfterEach
    fun tearDown() {
        runCatching { server?.stop(0) }
        server = null
    }

    private fun startServer(): String {
        val srv = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        srv.createContext("/api/v1/models/load", HttpHandler { exchange ->
            exchange.requestBody.readAllBytes()
            loadCount.incrementAndGet()
            val body = """{"type":"loaded","instance_id":"test-inst-1","status":"ok"}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        })
        srv.createContext("/api/v1/models", HttpHandler { exchange ->
            exchange.requestBody.readAllBytes()
            // Docs-shaped: availability list where ONLY non-empty
            // loaded_instances means loaded. An available-but-unloaded
            // model must NOT suppress the reload.
            val models = loadedKeys.joinToString(",") {
                """{"key":"$it","display_name":"$it","loaded_instances":[{"id":"$it"}]}"""
            }
            val available = """{"key":"other","display_name":"other","loaded_instances":[]}"""
            val body = if (models.isEmpty()) {
                """{"models":[$available]}"""
            } else {
                """{"models":[$models,$available]}"""
            }
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        })
        srv.executor = Executors.newCachedThreadPool { r -> Thread(r).also { it.isDaemon = true } }
        srv.start()
        server = srv
        return "http://127.0.0.1:${srv.address.port}/"
    }

    private fun helper(baseUrl: String): LmStudioHelper {
        val apiKeyStore = mockk<ApiKeyStore>()
        every { apiKeyStore.getKey(any()) } returns null
        return LmStudioHelper(
            InputSanitizer(),
            apiKeyStore,
        ).also {
            it.setEndpoint(
                Endpoint(name = "lm", url = baseUrl, apiType = ProviderType.LM_STUDIO, modelId = "m1")
            )
        }
    }

    @Test
    fun `second initialize with same loaded model skips reload`() = runBlocking {
        loadedKeys = emptyList()
        val h = helper(startServer())

        h.initialize("m1")
        loadedKeys = listOf("m1")
        h.initialize("m1")

        assertThat(loadCount.get()).isEqualTo(1)
    }

    @Test
    fun `initialize reloads after server-side eviction`() = runBlocking {
        loadedKeys = emptyList()
        val h = helper(startServer())

        h.initialize("m1")
        loadedKeys = listOf("m1")
        h.initialize("m1")
        loadedKeys = emptyList()
        h.initialize("m1")

        assertThat(loadCount.get()).isEqualTo(2)
    }

    @Test
    fun `failed load surfaces the server reason not just the code`() = runBlocking {
        val srv = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        srv.createContext("/api/v1/models/load", HttpHandler { exchange ->
            exchange.requestBody.readAllBytes()
            val body = """{"error":{"type":"model_load_failed","message":"error loading model: unable to allocate CUDA0 buffer"}}""".toByteArray()
            exchange.sendResponseHeaders(500, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        })
        srv.executor = Executors.newCachedThreadPool { r -> Thread(r).also { it.isDaemon = true } }
        srv.start()
        try {
            val provider = LMStudioProvider(
                baseUrl = "http://127.0.0.1:${srv.address.port}",
                modelId = "m1",
                apiKey = null,
                inputSanitizer = com.warped.data.local.inference.InputSanitizer(),
            )

            val result = provider.loadModel("m1")

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()?.message).contains("unable to allocate CUDA0 buffer")
        } finally {
            srv.stop(0)
        }
    }

    @Test
    fun `second initialize with short id skips when full slug loaded`() = runBlocking {
        loadedKeys = emptyList()
        val h = helper(startServer())

        h.initialize("m1")
        loadedKeys = listOf("org/m1")
        h.initialize("m1")

        assertThat(loadCount.get()).isEqualTo(1)
    }

    @Test
    fun `short endpoint id matches full server slug`() {
        // User report 2026-10-03: endpoint id "gemma-4-12b-qat" vs server
        // key "google/gemma-4-12b-qat" — exact match missed and every
        // chat reloaded (and OOM-killed) the model.
        assertThat(LMStudioProvider.isLoadedKeyMatch("google/gemma-4-12b-qat", "gemma-4-12b-qat")).isTrue()
        assertThat(LMStudioProvider.isLoadedKeyMatch("google/gemma-4-12b-qat", "google/gemma-4-12b-qat")).isTrue()
        assertThat(
            LMStudioProvider.isLoadedKeyMatch(
                "google/gemma-4-12b-qat@q4_0",
                "gemma-4-12b-qat"
            )
        ).isTrue()
        // Slug-boundary safety: same tail, different org must not match.
        assertThat(
            LMStudioProvider.isLoadedKeyMatch("other-org/bonsai-27b", "prism-ml/bonsai-27b")
        ).isFalse()
        assertThat(LMStudioProvider.isLoadedKeyMatch("google/gemma-4-12b-qat", "qwen3-4b")).isFalse()
    }
}
