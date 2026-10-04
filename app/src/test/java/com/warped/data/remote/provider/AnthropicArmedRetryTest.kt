package com.warped.data.remote.provider

import com.google.common.truth.Truth.assertThat
import com.warped.data.grounding.DuckDuckGoSearchRepository
import com.warped.data.grounding.MultiUrlFetcher
import com.warped.data.grounding.WebPageFetcher
import com.warped.data.local.inference.InputSanitizer
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.domain.model.ChatMessage
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.Role
import com.warped.domain.model.StreamToken
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * CompatToolLoop parity (device evidence 2026-10-03): a lone tools
 * rejection is retried armed once before concluding incapability.
 * Loopback HttpServer (no new dependencies); runTest (no real IO —
 * OkHttp inside runTest uses its own threads, matching the existing
 * loopback suites).
 */
class AnthropicArmedRetryTest {

    private var server: HttpServer? = null
    private var postUrl: String = ""

    @AfterEach
    fun tearDown() {
        server?.stop(0)
    }

    private fun startScripted(script: List<Pair<Int, String>>) {
        val hits = AtomicInteger(0)
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server!!.createContext("/v1/messages") { exchange ->
            exchange.requestBody.readAllBytes()
            val (code, body) = script.getOrElse(hits.getAndIncrement()) { 200 to script.last().second }
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server!!.start()
        postUrl = "http://127.0.0.1:${server!!.address.port}"
    }

    private fun provider(): AnthropicProvider {
        val prefs = mockk<AdvancedPreferences>()
        every { prefs.webGroundingEnabled } returns flowOf(true)
        val net = mockk<WebPageFetcher>()
        every { net.hasValidatedInternet() } returns true
        return AnthropicProvider(
            baseUrl = postUrl,
            modelId = "test-model",
            apiKey = null,
            inputSanitizer = InputSanitizer(),
            ddg = mockk<DuckDuckGoSearchRepository>(),
            multiUrlFetcher = mockk<MultiUrlFetcher>(),
            webPageFetcher = net,
            advancedPreferences = prefs,
        )
    }

    private fun answerEvents(text: String): String = listOf(
        """event: message_start""",
        """data: {"type":"message_start","message":{"id":"m1","role":"assistant"}}""",
        """""",
        """event: content_block_delta""",
        """data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"$text"}}""",
        """""",
        """event: message_delta""",
        """data: {"type":"message_delta","delta":{"stop_reason":"end_turn"}}""",
        """""",
        """event: message_stop""",
        """data: {"type":"message_stop"}""",
        """""",
    ).joinToString("\n")

    private suspend fun chatTokens(): List<StreamToken> {
        val request = ChatRequest(
            messages = listOf(ChatMessage(role = Role.USER, content = "q")),
        )
        return provider().chat(request).toList()
    }

    @Test
    fun `transient tools rejection retries armed without notice`() = runTest {
        val rejection = """{"error":{"message":"model does not support tool use"}}"""
        startScripted(
            listOf(
                400 to rejection,
                200 to answerEvents("recovered"),
            )
        )

        val tokens = chatTokens()

        assertThat(tokens.filterIsInstance<StreamToken.ToolsUnsupported>()).isEmpty()
        assertThat(tokens.filterIsInstance<StreamToken.Delta>().map { it.content })
            .contains("recovered")
        assertThat(tokens.last()).isInstanceOf(StreamToken.Done::class.java)
    }

    @Test
    fun `double tools rejection falls back with exactly one notice`() = runTest {
        val rejection = """{"error":{"message":"model does not support tool use"}}"""
        startScripted(
            listOf(
                400 to rejection,
                400 to rejection,
                200 to answerEvents("model-only"),
            )
        )

        val tokens = chatTokens()

        assertThat(tokens.filterIsInstance<StreamToken.ToolsUnsupported>()).hasSize(1)
        assertThat(tokens.filterIsInstance<StreamToken.Delta>().map { it.content })
            .contains("model-only")
        assertThat(tokens.last()).isInstanceOf(StreamToken.Done::class.java)
    }

    @Test
    fun `reasoning off sends no thinking block`() = runTest {
        var capturedBody = ""
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server!!.createContext("/v1/messages") { exchange ->
            capturedBody = exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
            val body = answerEvents("ok").toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server!!.start()
        postUrl = "http://127.0.0.1:${server!!.address.port}"
        val prefs = mockk<AdvancedPreferences>()
        every { prefs.webGroundingEnabled } returns flowOf(false)
        val provider = AnthropicProvider(
            baseUrl = postUrl,
            modelId = "test-model",
            apiKey = null,
            inputSanitizer = InputSanitizer(),
            advancedPreferences = prefs,
        )

        val request = ChatRequest(
            messages = listOf(ChatMessage(role = Role.USER, content = "q")),
            parameters = com.warped.domain.model.GenerationParameters(reasoningEnabled = false),
        )
        provider.chat(request).toList()

        val parsed = kotlinx.serialization.json.Json.parseToJsonElement(capturedBody).jsonObject
        assertThat(parsed.containsKey("thinking")).isFalse()
    }
}
