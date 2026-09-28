package com.warped.data.skills

/**
 * 47-02 (HARD-02, T-47-07): shared output hygiene for tool results.
 *
 * Tool output is untrusted text (Pitfall 6): before a result re-enters model
 * context or the transcript it is truncated and stripped of control
 * characters. Used by [LocalToolExecutor] and the `@Tool` bodies (the local
 * automatic-mode path bypasses the executor, so both sites sanitize).
 */
internal const val TOOL_TEXT_MAX_CHARS = 2000
internal const val TOOL_SUMMARY_MAX_CHARS = 200

private val CONTROL_CHARS = Regex("[\\p{Cc}&&[^\\n\\t]]")

/** Strip control chars (keep `\n`/`\t`) and cap at [maxChars] with "…". */
internal fun sanitizeToolOutput(text: String, maxChars: Int = TOOL_TEXT_MAX_CHARS): String {
    val clean = CONTROL_CHARS.replace(text, "")
    return if (clean.length <= maxChars) clean else clean.take(maxChars) + "…"
}

/** Transcript form: [sanitizeToolOutput] capped at summary length. */
internal fun summarizeToolOutput(text: String, maxChars: Int = TOOL_SUMMARY_MAX_CHARS): String =
    sanitizeToolOutput(text, maxChars)
