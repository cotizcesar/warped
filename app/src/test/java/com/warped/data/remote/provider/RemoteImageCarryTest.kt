package com.warped.data.remote.provider

import com.google.common.truth.Truth.assertThat
import com.warped.data.local.inference.InputSanitizer
import com.warped.data.remote.dto.OpenAiMessage
import com.warped.domain.model.ChatMessage
import com.warped.domain.model.Role
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

/**
 * Quick-task (remote-image-carry): remote history carries the K newest
 * image turns with the same cap/dedupe/skip rules as the local
 * [com.warped.data.local.inference.LiteRTLmProvider.buildHistoryMessages]
 * canonical implementation. Text-only behavior is preserved when no
 * images are present.
 *
 * Audio decision (documented, planner rule): history audio is carried on
 * NO path — [com.warped.domain.model.ChatRequest.audioBytes] is
 * current-turn-only and `MessageEntity` persists no audio column (columns:
 * id, conversation_id, role, content, token_count, created_at, images,
 * stats, reasoning), so history rebuilt from Room cannot carry audio on
 * local or remote. Audio stays a current-turn attachment everywhere.
 */
class RemoteImageCarryTest {

    private val sanitizer = InputSanitizer()

    private fun user(text: String, images: List<String> = emptyList()) =
        ChatMessage(role = Role.USER, content = text, imageUris = images)

    private fun assistant(text: String) =
        ChatMessage(role = Role.ASSISTANT, content = text)

    private fun img(tag: String) = "data:image/png;base64,$tag-payload"

    // ------------------------------------------------------------------
    // Shared rule
    // ------------------------------------------------------------------

    @Test
    fun `carry caps at K newest image turns`() {
        val messages = listOf(
            user("one", images = listOf(img("A"))),
            user("two", images = listOf(img("B"))),
            user("three", images = listOf(img("C"))),
            user("four", images = listOf(img("D"))),
            user("current"),
        )
        val kept = HistoryImageCarry.selectKeptUrls(messages)
        assertThat(kept.keys).containsExactly(1, 2, 3)
    }

    @Test
    fun `duplicate image is carried only on its newest turn`() {
        val shared = img("SAME")
        val messages = listOf(
            user("old", images = listOf(shared)),
            user("new", images = listOf(shared)),
            user("current"),
        )
        val kept = HistoryImageCarry.selectKeptUrls(messages)
        assertThat(kept[0]).isNull()
        assertThat(kept[1]).containsExactly(shared)
    }

    @Test
    fun `current message images are never carried`() {
        val messages = listOf(
            user("old", images = listOf(img("A"))),
            user("current", images = listOf(img("NOW"))),
        )
        val kept = HistoryImageCarry.selectKeptUrls(messages)
        assertThat(kept.keys).containsExactly(0)
        assertThat(kept.values.flatten()).doesNotContain(img("NOW"))
    }

    @Test
    fun `blank urls are skipped`() {
        val messages = listOf(
            user("old", images = listOf("   ", img("A"))),
            user("current"),
        )
        val kept = HistoryImageCarry.selectKeptUrls(messages)
        assertThat(kept[0]).containsExactly(img("A"))
    }

    @Test
    fun `assistant imageUris never carry`() {
        val messages = listOf(
            ChatMessage(role = Role.ASSISTANT, content = "seen", imageUris = listOf(img("A"))),
            user("current"),
        )
        assertThat(HistoryImageCarry.selectKeptUrls(messages)).isEmpty()
    }

    // ------------------------------------------------------------------
    // OpenAI-compat shape
    // ------------------------------------------------------------------

    @Test
    fun `openai history carries images as image_url parts`() {
        val messages = listOf(
            user("what is this?", images = listOf(img("A"))),
            assistant("a cat"),
            user("follow-up"),
        )
        val mapped = mapOpenAiHistory(messages, includeSystem = false, sanitizeUser = sanitizer::sanitize)
        assertThat(mapped).hasSize(3)
        assertThat(mapped[0].imageUrls).containsExactly(img("A"))
        assertThat(mapped[0].content).isEqualTo("what is this?")
        assertThat(mapped[1].imageUrls).isNull()
        assertThat(mapped[2].imageUrls).isNull()
    }

    @Test
    fun `openai text-only history stays string content on the wire`() {
        val messages = listOf(
            user("hello"),
            assistant("hi"),
            user("again"),
        )
        val mapped = mapOpenAiHistory(messages, includeSystem = false, sanitizeUser = sanitizer::sanitize)
        val json = Json { ignoreUnknownKeys = true }
        val encoded = json.encodeToString(OpenAiMessage.serializer(), mapped[0])
        assertThat(encoded).isEqualTo("""{"role":"user","content":"hello"}""")
    }

    @Test
    fun `openai carried row encodes multipart content array`() {
        val messages = listOf(
            user("look", images = listOf(img("A"))),
            user("current"),
        )
        val mapped = mapOpenAiHistory(messages, includeSystem = false, sanitizeUser = sanitizer::sanitize)
        val json = Json { ignoreUnknownKeys = true }
        val el = json.parseToJsonElement(
            json.encodeToString(OpenAiMessage.serializer(), mapped[0]),
        ).jsonObject
        val parts = el["content"]!!.jsonArray
        assertThat(parts).hasSize(2)
        assertThat(parts[0].jsonObject["type"]!!.jsonPrimitive.content).isEqualTo("text")
        assertThat(parts[1].jsonObject["type"]!!.jsonPrimitive.content).isEqualTo("image_url")
        assertThat(
            parts[1].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content,
        ).isEqualTo(img("A"))
    }

    @Test
    fun `openai assistant echo omits content key like before`() {
        val echo = OpenAiMessage(role = "assistant", toolCalls = emptyList())
        val json = Json { ignoreUnknownKeys = true }
        val el = json.parseToJsonElement(json.encodeToString(OpenAiMessage.serializer(), echo)).jsonObject
        assertThat(el.containsKey("content")).isFalse()
        assertThat(el["role"]!!.jsonPrimitive.content).isEqualTo("assistant")
    }

    // ------------------------------------------------------------------
    // Ollama native shape
    // ------------------------------------------------------------------

    @Test
    fun `ollama native carries images as raw base64 without prefix`() {
        val provider = OllamaProvider(
            baseUrl = "http://localhost:11434",
            modelId = "m",
            inputSanitizer = mockk { every { sanitize(any()) } answers { firstArg() } },
        )
        val messages = listOf(
            user("what is this?", images = listOf(img("A"))),
            user("follow-up"),
        )
        val mapped = provider.mapNativeMessages(messages)
        assertThat(mapped).hasSize(2)
        assertThat(mapped[0].images).containsExactly("A-payload")
        assertThat(mapped[1].images).isNull()
    }

    @Test
    fun `ollama text-only rows omit images key`() {
        val provider = OllamaProvider(
            baseUrl = "http://localhost:11434",
            modelId = "m",
            inputSanitizer = mockk { every { sanitize(any()) } answers { firstArg() } },
        )
        val mapped = provider.mapNativeMessages(listOf(user("hi"), user("there")))
        assertThat(mapped[0].images).isNull()
        val json = Json { ignoreUnknownKeys = true }
        val encoded = json.encodeToString(
            com.warped.data.remote.dto.OllamaMessage.serializer(),
            mapped[0],
        )
        assertThat(encoded).doesNotContain("images")
    }

    // ------------------------------------------------------------------
    // LM Studio native shape
    // ------------------------------------------------------------------

    @Test
    fun `lmstudio native inserts history image items before their text`() {
        val provider = LMStudioProvider(
            baseUrl = "http://localhost:1234",
            modelId = "m",
            inputSanitizer = mockk { every { sanitize(any()) } answers { firstArg() } },
        )
        val messages = listOf(
            user("what is this?", images = listOf(img("A"))),
            assistant("a cat"),
            user("follow-up"),
        )
        val (system, input) = provider.buildNativeInput(messages, currentImages = emptyList())
        assertThat(system).isNull()
        val imageItems = input.filter { it.type == "image" }
        assertThat(imageItems).hasSize(1)
        assertThat(imageItems[0].dataUrl).isEqualTo(img("A"))
        // History image item sits immediately before its turn's text item.
        val imgIdx = input.indexOf(imageItems[0])
        assertThat(input[imgIdx + 1].content).isEqualTo("what is this?")
    }

    @Test
    fun `lmstudio native keeps current images first and text order`() {
        val provider = LMStudioProvider(
            baseUrl = "http://localhost:1234",
            modelId = "m",
            inputSanitizer = mockk { every { sanitize(any()) } answers { firstArg() } },
        )
        val messages = listOf(user("a"), assistant("b"), user("c"))
        val (_, input) = provider.buildNativeInput(messages, currentImages = listOf(img("NOW")))
        assertThat(input[0].type).isEqualTo("image")
        assertThat(input[0].dataUrl).isEqualTo(img("NOW"))
        assertThat(input.filter { it.type == "text" }.map { it.content })
            .containsExactly("a", "b", "c")
            .inOrder()
    }

    // ------------------------------------------------------------------
    // Current-turn images (user report 2026-10-03 — compat paths dropped
    // the live photo and the model answered "no image attached")
    // ------------------------------------------------------------------

    @Test
    fun `openai current-turn images fuse onto the last user row`() {
        val messages = listOf(
            user("old", images = listOf(img("A"))),
            assistant("a cat"),
            user("describe esta imagen."),
        )
        val mapped = mapOpenAiHistory(
            messages,
            includeSystem = false,
            sanitizeUser = sanitizer::sanitize,
            currentImages = listOf(img("NOW")),
        )
        assertThat(mapped).hasSize(3)
        assertThat(mapped[0].imageUrls).containsExactly(img("A"))
        assertThat(mapped[2].imageUrls).containsExactly(img("NOW"))
        assertThat(mapped[2].content).isEqualTo("describe esta imagen.")
    }

    @Test
    fun `openai current image dedupes against history urls`() {
        val messages = listOf(
            user("old", images = listOf(img("A"))),
            user("again"),
        )
        val mapped = mapOpenAiHistory(
            messages,
            includeSystem = false,
            sanitizeUser = sanitizer::sanitize,
            currentImages = listOf(img("A")),
        )
        assertThat(mapped[1].imageUrls).containsExactly(img("A"))
    }

    @Test
    fun `ollama native carries current-turn images as raw base64`() {
        val provider = OllamaProvider(
            baseUrl = "http://localhost:11434",
            modelId = "m",
            inputSanitizer = mockk { every { sanitize(any()) } answers { firstArg() } },
        )
        val messages = listOf(
            user("old", images = listOf(img("A"))),
            user("describe esta imagen."),
        )
        val mapped = provider.mapNativeMessages(messages, currentImages = listOf(img("NOW")))
        assertThat(mapped[0].images).containsExactly("A-payload")
        assertThat(mapped[1].images).containsExactly("NOW-payload")
    }

    @Test
    fun `anthropic current-turn images become native image blocks`() {
        val msg = com.warped.data.remote.dto.AnthropicMessage.userWithImages(
            "describe esta imagen.",
            listOf("data:image/png;base64,QUJD", "  ", "notaurl"),
        )
        assertThat(msg.role).isEqualTo("user")
        val blocks = msg.content.jsonArray
        assertThat(blocks[0].jsonObject["type"]?.jsonPrimitive?.content).isEqualTo("text")
        assertThat(blocks[0].jsonObject["text"]?.jsonPrimitive?.content)
            .isEqualTo("describe esta imagen.")
        val image = blocks[1].jsonObject
        assertThat(image["type"]?.jsonPrimitive?.content).isEqualTo("image")
        val source = image["source"]!!.jsonObject
        assertThat(source["type"]?.jsonPrimitive?.content).isEqualTo("base64")
        assertThat(source["media_type"]?.jsonPrimitive?.content).isEqualTo("image/png")
        assertThat(source["data"]?.jsonPrimitive?.content).isEqualTo("QUJD")
        // Blanks and prefix-less payloads are dropped, never sent.
        assertThat(blocks).hasSize(2)
    }
}
