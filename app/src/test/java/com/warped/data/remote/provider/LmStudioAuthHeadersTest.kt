package com.warped.data.remote.provider

import com.google.common.truth.Truth.assertThat
import com.warped.data.local.inference.InputSanitizer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import com.sun.net.httpserver.HttpServer

/**
 * Auth docs: LM Studio accepts `x-api-key` and `Authorization: Bearer`
 * (examples use Bearer on the native REST API). The provider sends both
 * when a key is configured so every endpoint authenticates.
 */
class LmStudioAuthHeadersTest {

    private var server: HttpServer? = null
    private val seenApiKey = AtomicReference<String?>(null)
    private val seenBearer = AtomicReference<String?>(null)

    @AfterEach
    fun tearDown() {
        server?.stop(0)
    }

    @Test
    fun `keyed client sends both auth headers`() = runBlocking {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server!!.createContext("/api/v1/models") { exchange ->
            exchange.requestBody.readAllBytes()
            seenApiKey.set(exchange.requestHeaders.getFirst("x-api-key"))
            seenBearer.set(exchange.requestHeaders.getFirst("Authorization"))
            val body = """{"models":[]}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server!!.start()
        val base = "http://127.0.0.1:${server!!.address.port}"

        val provider = LMStudioProvider(
            baseUrl = base,
            modelId = "m",
            apiKey = "secret",
            inputSanitizer = InputSanitizer(),
        )
        provider.listLoadedModelKeys()

        assertThat(seenApiKey.get()).isEqualTo("secret")
        assertThat(seenBearer.get()).isEqualTo("Bearer secret")
    }

    @Test
    fun `keyless client sends neither header`() = runBlocking {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server!!.createContext("/api/v1/models") { exchange ->
            exchange.requestBody.readAllBytes()
            seenApiKey.set(exchange.requestHeaders.getFirst("x-api-key"))
            seenBearer.set(exchange.requestHeaders.getFirst("Authorization"))
            val body = """{"models":[]}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server!!.start()
        val base = "http://127.0.0.1:${server!!.address.port}"

        val provider = LMStudioProvider(
            baseUrl = base,
            modelId = "m",
            apiKey = null,
            inputSanitizer = InputSanitizer(),
        )
        provider.listLoadedModelKeys()

        assertThat(seenApiKey.get()).isNull()
        assertThat(seenBearer.get()).isNull()
    }
}
