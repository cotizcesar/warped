package com.warped.ui.chat

import timber.log.Timber

private val TAG_STRIP_REGEX =
    Regex("<[/]?think>|<[/]?channel\\|?>", setOf(RegexOption.IGNORE_CASE))
private val CHANNEL_REGEX = Regex(
    "<channel\\|>([\\s\\S]*?)<\\|channel>",
    setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
)
private val THINK_PAIR_REGEX = Regex(
    "<think>([\\s\\S]*?)</think>",
    setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
)
private const val OPEN_TAG = "<think>"
private const val CLOSE_TAG = "</think>"

/**
 * Split raw model output into (answer, reasoning).
 *
 * Covers the R1-distill leak (2026-10-03): DeepSeek-R1 variants often emit
 * the thought WITHOUT the opening `<think>` tag, ending with a lone
 * `</think>`. The pair regex then matches nothing and the whole thought
 * leaks in-band. An orphan close tag therefore splits: everything before
 * the first `</think>` is reasoning, everything after is the answer.
 *
 * - enabled=true (thinking on): reasoning → Thinking panel, answer clean.
 * - enabled=false (thinking off): thought dropped entirely (not inlined),
 *   answer clean. Without any close tag the text is a plain answer and is
 *   kept (tags scrubbed).
 *
 * Live mode ([live]=true, mid-stream flushes): tag-less text cannot be
 * classified yet — the `</think>` that proves thought may still be coming.
 * For verified thinkers ([modelThinks]) route provisionally: thinking-on
 * shows it in the Thinking panel from the first token, thinking-off
 * suppresses it until the close tag (or the end). Non-thinkers stream
 * untouched so plain answers never blank mid-stream. At Done ([live]=false)
 * tag-less text is always the answer (no flicker-back for models that
 * never emit tags).
 *
 * Pure — unit-tested. Extracted from ChatViewModel (same logic + orphan
 * handling); the `modelMayThink` reservation is kept at the call sites.
 */
internal fun parseThinkBlocks(
    raw: String,
    enabled: Boolean = true,
    live: Boolean = false,
    modelThinks: Boolean = false,
): Pair<String, String> {
    val lower = raw.lowercase()
    // Note: "</think>" does NOT contain "<think" ('<' is followed by '/'),
    // so the orphan close needs its own check.
    val hasMarker = "<think" in lower || "</think" in lower || "<channel" in lower
    if (live && modelThinks && !hasMarker) {
        return if (enabled) {
            Pair("", raw.trim())
        } else {
            Pair("", "")
        }
    }
    if (!enabled) {
        val closeIdx = raw.lowercase().indexOf(CLOSE_TAG)
        val dropped = if (closeIdx >= 0) raw.substring(closeIdx + CLOSE_TAG.length) else raw
        return Pair(TAG_STRIP_REGEX.replace(dropped, "").trim(), "")
    }
    val reasoning = StringBuilder()
    var clean = raw

    CHANNEL_REGEX.findAll(clean).forEach { m -> reasoning.append(m.groupValues[1].trim()).append("\n") }
    clean = CHANNEL_REGEX.replace(clean, "")

    THINK_PAIR_REGEX.findAll(clean).forEach { m -> reasoning.append(m.groupValues[1].trim()).append("\n") }
    clean = THINK_PAIR_REGEX.replace(clean, "")

    val openIdx = clean.lowercase().lastIndexOf(OPEN_TAG)
    if (openIdx >= 0) {
        reasoning.append(clean.substring(openIdx + OPEN_TAG.length).trim())
        clean = clean.substring(0, openIdx)
    }

    // Orphan close (no opening tag anywhere): thought precedes it.
    val closeIdx = clean.lowercase().indexOf(CLOSE_TAG)
    if (closeIdx >= 0) {
        reasoning.append(clean.substring(0, closeIdx).trim()).append("\n")
        clean = clean.substring(closeIdx + CLOSE_TAG.length)
    }

    // Untagged output is the answer, not reasoning: without explicit
    // <think>/<channel|> markers there is no evidence the model was thinking,
    // and routing plain replies into the collapsed Thinking panel produces
    // empty assistant bubbles (local-empty-response, 2026-09-28).
    clean = TAG_STRIP_REGEX.replace(clean, "")

    Timber.d("ChatVM: parseThinkBlocks result — clean=%d reasoning=%d", clean.length, reasoning.length)
    return Pair(clean.trim(), reasoning.toString().trim())
}
