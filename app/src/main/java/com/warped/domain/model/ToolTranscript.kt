package com.warped.domain.model

/**
 * Phase 49 (DEL-01/DEL-02): read-only legacy transcript helpers.
 *
 * The skills/tool-execution surface is gone — no new tool calls can be
 * produced. Persisted `role=tool` rows (encoded as `"<toolId>\n<summary>"`)
 * keep rendering as collapsed transcript rows, and remote providers replay
 * them as plain user-adjacent text so the raw encoding never hits the wire.
 *
 * Unknown tool ids degrade via [toolDisplayName] (underscore→space), never
 * crash, never blank. Malformed payloads (no `\n`) fall back to the
 * default transcript header.
 */
fun toolDisplayName(toolId: String): String = when (toolId) {
    "calculator" -> "calculator"
    "current_time" -> "current time"
    "json_formatter" -> "JSON formatter"
    else -> toolId.replace('_', ' ').ifBlank { toolId }
}

/** `{Display}` casing rule: first letter capitalized (acronyms keep caps). */
fun toolDisplayNameCapitalized(toolId: String): String =
    toolDisplayName(toolId).replaceFirstChar { it.uppercase() }

/** Split a persisted `role=tool` row into `(toolId, summary)`. */
fun parseToolRow(content: String): Pair<String, String> {
    val idx = content.indexOf('\n')
    return if (idx < 0) "calculator" to content
    else content.substring(0, idx) to content.substring(idx + 1)
}

/** Plain-text replay line for providers without a tool-role concept. */
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
