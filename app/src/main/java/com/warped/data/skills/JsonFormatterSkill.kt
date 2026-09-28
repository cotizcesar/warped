package com.warped.data.skills

import com.warped.domain.skills.ToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * 47-02 (HARD-02): JsonFormatter pure tool body.
 *
 * [formatJson] is PURE SYNC and NEVER throws out. Input is size-capped
 * (~64KB per the shared descriptor) and malformed JSON maps to Failure;
 * pretty-printed output is capped so a valid-but-huge document cannot flood
 * the next model turn (Pitfall 6). The `@Tool` ToolSet wrapper lands in
 * Task 2 and delegates here.
 */
const val JSON_MAX_CHARS = 65536

private const val JSON_PRETTY_MAX_CHARS = 8000

private val prettyJson = Json { prettyPrint = true }

fun formatJson(jsonText: String): ToolResult {
    return try {
        if (jsonText.isBlank()) return ToolResult.Failure("empty JSON")
        if (jsonText.length > JSON_MAX_CHARS) return ToolResult.Failure("JSON too large")
        val element: JsonElement = try {
            Json.parseToJsonElement(jsonText)
        } catch (e: Exception) {
            return ToolResult.Failure("invalid JSON")
        }
        val pretty = prettyJson.encodeToString(JsonElement.serializer(), element)
        val text = sanitizeToolOutput(pretty, JSON_PRETTY_MAX_CHARS)
        ToolResult.Success(text = text, summary = summarizeToolOutput(text))
    } catch (e: Exception) {
        // Belt-and-braces: the trust boundary guarantees no throw-out.
        ToolResult.Failure("invalid JSON")
    }
}
