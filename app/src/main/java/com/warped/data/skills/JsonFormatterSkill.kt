package com.warped.data.skills

import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import com.warped.domain.skills.SkillIds
import com.warped.domain.skills.ToolEventSink
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

/**
 * 47-02 (D-04): JsonFormatter `@Tool` via [ToolSet]. Descriptions import the
 * Plan 01 descriptor constants verbatim (SKILLS-09 no-drift). Same
 * [ToolEventSink] + never-throw contract as [CalculatorToolSet].
 */
class JsonFormatterToolSet(private val events: ToolEventSink) : ToolSet {
    @Tool(description = JSON_TOOL_DESCRIPTION)
    fun jsonFormatter(
        @ToolParam(description = JSON_TEXT_DESCRIPTION) json: String,
    ): String {
        events.onStart(SkillIds.JSON_FORMATTER)
        return try {
            when (val r = formatJson(json)) {
                is ToolResult.Success -> sanitizeToolOutput(r.text)
                is ToolResult.Failure -> "Error: ${r.reason}"
            }
        } catch (e: Exception) {
            "Error: formatting failed"
        } finally {
            events.onFinish(SkillIds.JSON_FORMATTER)
        }
    }
}
