package com.warped.ui.chat.components

import com.warped.domain.skills.toolDisplayName
import com.warped.domain.skills.toolDisplayNameCapitalized

/**
 * 47-01 (D-05/D-06): UI-SPEC §8 copy deck — exact strings, no paraphrase.
 *
 * Templates keep the `{display}` / `{Display}` / `{reason}` placeholders
 * literally in code so the copy contract is greppable; [format…] helpers
 * substitute at render time. Single source for chips, status, error,
 * transcript, and notice rows (threat T-47-03: labels fixed, ids never shown).
 */
const val TOOL_STATUS_TEMPLATE = "Using {display}…"
const val TOOL_ERROR_TEMPLATE = "{Display} failed: {reason}"
const val TOOL_TRANSCRIPT_TEMPLATE = "Used {Display}"
const val NO_TOOL_SUPPORT_NOTICE = "This model doesn't support tools — answering directly."

const val SKILL_CHIP_A11Y_TEMPLATE = "{Label} skill, {state}, tap to toggle"
const val TOOL_STATUS_A11Y_TEMPLATE = "Tool running: {display}"
const val TOOL_ERROR_A11Y_TEMPLATE = "Tool error: {reason}"
const val TOOL_TRANSCRIPT_A11Y_TEMPLATE = "Tool result from {display}, {state}, tap to toggle"

fun formatToolStatus(toolId: String): String =
    TOOL_STATUS_TEMPLATE.replace("{display}", toolDisplayName(toolId))

fun formatToolStatusA11y(toolId: String): String =
    TOOL_STATUS_A11Y_TEMPLATE.replace("{display}", toolDisplayName(toolId))

fun formatToolError(toolId: String, reason: String): String =
    TOOL_ERROR_TEMPLATE
        .replace("{Display}", toolDisplayNameCapitalized(toolId))
        .replace("{reason}", reason)

fun formatToolErrorA11y(reason: String): String =
    TOOL_ERROR_A11Y_TEMPLATE.replace("{reason}", reason)

fun formatToolTranscriptHeader(toolId: String): String =
    TOOL_TRANSCRIPT_TEMPLATE.replace("{Display}", toolDisplayNameCapitalized(toolId))

fun formatToolTranscriptA11y(toolId: String, expanded: Boolean): String =
    TOOL_TRANSCRIPT_A11Y_TEMPLATE
        .replace("{display}", toolDisplayName(toolId))
        .replace("{state}", if (expanded) "expanded" else "collapsed")

fun formatSkillChipA11y(label: String, enabled: Boolean): String =
    SKILL_CHIP_A11Y_TEMPLATE
        .replace("{Label}", label)
        .replace("{state}", if (enabled) "enabled" else "disabled")

/**
 * Transcript row persistence contract (SKILLS-11 foundation).
 *
 * `role=tool` rows store only the summarized result plus the tool id,
 * encoded as `"<toolId>\n<summary>"` — the `"Used {Display}"` header is
 * rendered by [ToolResultRow], never stored. Unknown ids degrade via
 * [toolDisplayName] (underscore→space), never crash.
 */
fun toolResultContent(toolId: String, summary: String): String = "$toolId\n$summary"

fun parseToolResultContent(content: String): Pair<String, String> {
    val idx = content.indexOf('\n')
    return if (idx < 0) "calculator" to content else content.substring(0, idx) to content.substring(idx + 1)
}

/** UI-SPEC §5: summarized results truncate to ~200 chars + "…". */
fun summarizeToolResult(text: String, maxChars: Int = 200): String =
    if (text.length <= maxChars) text else text.take(maxChars) + "…"
