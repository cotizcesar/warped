package com.warped.data.remote.dto

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

/**
 * Device-proven 2026-10-03: kotlinx omits defaulted values, so
 * `OpenAiTool.type` never hit the wire — LM Studio validates the literal
 * (`invalid_literal, expected "function"`) and 400d EVERY armed turn,
 * which the app misread as "endpoint doesn't support tool calling".
 * The literal must serialize, always.
 */
class OpenAiToolWireTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `every default tool carries the function type literal`() {
        val encoded = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(OpenAiTool.serializer()),
            defaultRemoteTools(),
        )
        val tools = json.parseToJsonElement(encoded).jsonArray

        assertThat(tools).hasSize(3)
        for (tool in tools) {
            assertThat(tool.jsonObject["type"]?.jsonPrimitive?.content).isEqualTo("function")
        }
    }

    @Test
    fun `full chat body keeps the literal per tool entry`() {
        val body = OpenAiChatRequest(
            model = "m",
            messages = listOf(OpenAiMessage(role = "user", content = "hi")),
            tools = defaultRemoteTools(),
        )
        val encoded = json.encodeToString(OpenAiChatRequest.serializer(), body)
        val parsed = json.parseToJsonElement(encoded).jsonObject
        val tools = parsed["tools"]!!.jsonArray

        assertThat(tools).hasSize(3)
        for (tool in tools) {
            assertThat(tool.jsonObject["type"]?.jsonPrimitive?.content).isEqualTo("function")
        }
    }
}
