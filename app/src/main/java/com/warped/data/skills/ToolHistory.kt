package com.warped.data.skills

import com.warped.domain.model.ChatMessage
import com.warped.domain.model.Role
import com.warped.domain.skills.SkillIds
import com.warped.domain.skills.toolDisplayNameCapitalized

/**
 * 47 review fix (CR-01): shared tool-history replay for providers WITHOUT a
 * tool loop (OpenAI / Anthropic / Ollama / Custom).
 *
 * `role=tool` rows persist as `"<toolId>\n<summary>"` (47-01 ToolCopy
 * contract). Only the LM Studio tool loop (`role:tool` + `tool_call_id`
 * echo), the LM Studio native path (plain-text `Used …:` replay), and
 * LiteRT (`Message.tool`) understand that encoding. Every other provider
 * must replay tool rows as plain user-adjacent text:
 * - Anthropic rejects `role:"tool"` outright (only `user`/`assistant` valid).
 * - OpenAI/Custom strict servers reject unpaired `role:"tool"` messages
 *   (no matching `tool_calls` echo), and the raw encoding would leak.
 * - Ollama has no tool-role concept on this path either.
 *
 * Single shared parser (also the IN-01 convergence point for new code; the
 * three pre-existing sites keep their local mirrors to minimize diff).
 */
fun parseToolRow(content: String): Pair<String, String> {
    val idx = content.indexOf('\n')
    return if (idx < 0) SkillIds.CALCULATOR to content
    else content.substring(0, idx) to content.substring(idx + 1)
}

/** Plain-text replay line, mirroring the LM Studio native path. */
fun toolReplayText(toolId: String, summary: String): String =
    "Used ${toolDisplayNameCapitalized(toolId)}: $summary"

/**
 * Wire role + text for a history message on non-tool-loop providers.
 * TOOL rows become `user` text so the raw encoding never hits the wire
 * and no API-invalid role is ever emitted.
 */
fun ChatMessage.toProviderText(): Pair<String, String> =
    if (role == Role.TOOL) {
        val (toolId, summary) = parseToolRow(content)
        "user" to toolReplayText(toolId, summary)
    } else {
        role.name.lowercase() to content
    }

/**
 * WR-04: nesting-depth pre-check against `StackOverflowError`.
 * `StackOverflowError` is an `Error`, not an `Exception`, so it escapes
 * every `catch (e: Exception)` guard on the tool trust boundary
 * (`formatJson`, `@Tool` wrappers, `LocalToolExecutor`, the remote loop's
 * arg validation). A deeply-nested payload (`[[[[…]]]]`, trivially under
 * the 64KB cap) recurses in `Json.parseToJsonElement` and kills the turn.
 * Counting brackets up front is cheap and string-aware (brackets inside
 * JSON strings don't count), so deeply-nested input is rejected before
 * the parser ever recurses.
 */
const val JSON_MAX_DEPTH = 100

fun isJsonTooDeep(jsonText: String, maxDepth: Int = JSON_MAX_DEPTH): Boolean {
    var depth = 0
    var inString = false
    var escaped = false
    for (c in jsonText) {
        if (inString) {
            when {
                escaped -> escaped = false
                c == '\\' -> escaped = true
                c == '"' -> inString = false
            }
            continue
        }
        when (c) {
            '"' -> inString = true
            '{', '[' -> {
                depth++
                if (depth > maxDepth) return true
            }
            '}', ']' -> depth--
        }
    }
    return false
}
