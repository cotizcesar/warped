package com.warped.data.agentic

import com.google.common.truth.Truth.assertThat
import com.warped.data.remote.dto.OpenAiChatRequest
import com.warped.data.remote.dto.OpenAiMessage
import com.warped.data.remote.dto.OpenAiStreamChunk
import com.warped.data.remote.dto.defaultRemoteTools
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

/**
 * Phase 57 (57-01): exit gates for the pure SSE `tool_calls` reassembly.
 *
 * Every case is JVM-local (no network, no key): fragments accumulate per
 * index and only the reassembled string is ever parsed — per-chunk parsing
 * would stall on transport-split boundaries (Pitfall 1).
 */
class ToolCallAccumulatorTest {

    private val json = Json { ignoreUnknownKeys = true }

    // Accumulation.

    @Test
    fun `whole object in one feed completes verbatim`() {
        val acc = ToolCallAccumulator()
        acc.feed(0, "call_abc", "web_search", "{\"query\":\"latest news\"}")
        val calls = acc.complete()
        assertThat(calls).hasSize(1)
        assertThat(calls[0].id).isEqualTo("call_abc")
        assertThat(calls[0].name).isEqualTo("web_search")
        assertThat(calls[0].argumentsJson).isEqualTo("{\"query\":\"latest news\"}")
    }

    @Test
    fun `arguments split across three chunks reassemble exactly`() {
        val acc = ToolCallAccumulator()
        acc.feed(0, "call_abc", "web_search", "{\"qu")
        acc.feed(0, null, null, "ery\":\"lates")
        acc.feed(0, null, null, "t news\"}")
        assertThat(acc.complete()[0].argumentsJson).isEqualTo("{\"query\":\"latest news\"}")
    }

    @Test
    fun `mid escape split reassembles and parses`() {
        // Fragment boundary inside a unicode escape — naive per-chunk JSON
        // parsing throws here; the accumulator only parses the whole.
        val acc = ToolCallAccumulator()
        acc.feed(0, "call_1", "web_search", "{\"query\":\"a\\u00")
        acc.feed(0, null, null, "41b\"}")
        val call = acc.complete()[0]
        assertThat(call.argumentsJson).isEqualTo("{\"query\":\"a\\u0041b\"}")
        assertThat(parseToolArgs(call.argumentsJson)).isEqualTo(mapOf("query" to "aAb"))
    }

    @Test
    fun `missing id synthesizes call index`() {
        val acc = ToolCallAccumulator()
        acc.feed(2, null, "web_fetch", "{\"url\":\"https://example.com\"}")
        assertThat(acc.complete()[0].id).isEqualTo("call_2")
    }

    @Test
    fun `missing name on continuation keeps first seen name`() {
        val acc = ToolCallAccumulator()
        acc.feed(0, "call_1", "web_search", "{\"query\":")
        acc.feed(0, null, null, "\"x\"}")
        assertThat(acc.complete()[0].name).isEqualTo("web_search")
    }

    @Test
    fun `later different id and name do not overwrite first seen`() {
        val acc = ToolCallAccumulator()
        acc.feed(0, "call_first", "web_search", "{}")
        acc.feed(0, "call_second", "web_fetch", "")
        val call = acc.complete()[0]
        assertThat(call.id).isEqualTo("call_first")
        assertThat(call.name).isEqualTo("web_search")
    }

    @Test
    fun `interleaved indices complete independently in index order`() {
        val acc = ToolCallAccumulator()
        acc.feed(1, "call_b", "web_fetch", "{\"url\":\"")
        acc.feed(0, "call_a", "web_search", "{\"query\":\"x\"}")
        acc.feed(1, null, null, "https://example.com\"}")
        val calls = acc.complete()
        assertThat(calls).hasSize(2)
        assertThat(calls[0].id).isEqualTo("call_a")
        assertThat(calls[1].id).isEqualTo("call_b")
        assertThat(calls[1].argumentsJson).isEqualTo("{\"url\":\"https://example.com\"}")
    }

    @Test
    fun `null and empty fragments are ignored`() {
        val acc = ToolCallAccumulator()
        assertThat(acc.hasCalls()).isFalse()
        assertThat(acc.complete()).isEmpty()
        acc.feed(0, null, null, null)
        acc.feed(0, "", "", "")
        assertThat(acc.hasCalls()).isTrue()
        val call = acc.complete()[0]
        assertThat(call.id).isEqualTo("call_0")
        assertThat(call.name).isNull()
        assertThat(call.argumentsJson).isEmpty()
    }

    @Test
    fun `reset clears all slots`() {
        val acc = ToolCallAccumulator()
        acc.feed(0, "call_1", "web_search", "{}")
        acc.reset()
        assertThat(acc.hasCalls()).isFalse()
        assertThat(acc.complete()).isEmpty()
    }

    // Args parsing (never throws, fail-closed degradation).

    @Test
    fun `blank args parse to empty map for validateArgs short circuit`() {
        assertThat(parseToolArgs("")).isEqualTo(emptyMap<String, Any?>())
        assertThat(parseToolArgs("   ")).isEqualTo(emptyMap<String, Any?>())
        // ... which the policy maps to the model-only copy, pre-socket.
        assertThat(LocalToolLoop.validateArgs("web_search", emptyMap())).isEqualTo(
            LocalToolLoop.MODEL_ONLY_STRING,
        )
    }

    @Test
    fun `garbage args parse to null for toolFailureMessage degradation`() {
        assertThat(parseToolArgs("{\"query\":" )).isNull()
        assertThat(parseToolArgs("not json at all")).isNull()
    }

    @Test
    fun `non object root parses to null`() {
        assertThat(parseToolArgs("[1,2]")).isNull()
        assertThat(parseToolArgs("\"str\"")).isNull()
        assertThat(parseToolArgs("42")).isNull()
    }

    @Test
    fun `primitives convert, nested structures carried as text`() {
        val args = parseToolArgs("{\"query\":\"x\",\"n\":3,\"ok\":true,\"nil\":null}")
        assertThat(args).isNotNull()
        assertThat(args!!["query"]).isEqualTo("x")
        assertThat(args["n"]).isEqualTo(3L)
        assertThat(args["ok"]).isEqualTo(true)
        assertThat(args["nil"]).isNull()
    }

    @Test
    fun `unparseable call degrades to toolFailureMessage never throwing`() {
        val message = LocalToolLoop.toolFailureMessage("{\"query\":")
        assertThat(message).isNotEmpty()
        assertThat(LocalToolLoop.toolFailureMessage("   ")).isEqualTo("Error: the tool call failed.")
    }

    // DTO wire shape (additive: text turns byte-identical).

    @Test
    fun `plain request omits tools key entirely`() {
        val body = OpenAiChatRequest(
            model = "m",
            messages = listOf(OpenAiMessage(role = "user", content = "hi")),
        )
        val raw = json.encodeToString(OpenAiChatRequest.serializer(), body)
        assertThat(raw).doesNotContain("tools")
        assertThat(raw).doesNotContain("tool_call")
    }

    @Test
    fun `tooled request carries loose schemas without strict flag`() {
        val body = OpenAiChatRequest(
            model = "m",
            messages = listOf(OpenAiMessage(role = "user", content = "hi")),
            tools = defaultRemoteTools(),
        )
        val raw = json.encodeToString(OpenAiChatRequest.serializer(), body)
        assertThat(raw).contains("web_search")
        assertThat(raw).contains("web_fetch")
        assertThat(raw).doesNotContain("strict")
        assertThat(raw).doesNotContain("tool_choice")
    }

    @Test
    fun `stream delta parses tool_calls fragments additively`() {
        val data = "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\"," +
            "\"type\":\"function\",\"function\":{\"name\":\"web_search\",\"arguments\":\"{\\\"qu\"}}]," +
            "\"content\":null},\"finish_reason\":null}]}"
        val chunk = json.decodeFromString(OpenAiStreamChunk.serializer(), data)
        val delta = chunk.choices.firstOrNull()?.delta
        assertThat(delta?.content).isNull()
        val call = delta?.toolCalls?.single()
        assertThat(call?.index).isEqualTo(0)
        assertThat(call?.id).isEqualTo("call_1")
        assertThat(call?.function?.name).isEqualTo("web_search")
        assertThat(call?.function?.arguments).isEqualTo("{\"qu")
    }

    @Test
    fun `text only delta still parses with null tool_calls`() {
        val data = "{\"choices\":[{\"delta\":{\"content\":\"Hello\"},\"finish_reason\":null}]}"
        val chunk = json.decodeFromString(OpenAiStreamChunk.serializer(), data)
        val delta = chunk.choices.firstOrNull()?.delta
        assertThat(delta?.content).isEqualTo("Hello")
        assertThat(delta?.toolCalls).isNull()
    }
}
