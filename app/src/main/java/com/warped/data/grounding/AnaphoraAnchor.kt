package com.warped.data.grounding

import java.text.Normalizer

/**
 * Quick-task (always-presearch-anchored): deterministic anaphora anchor for
 * the always-on VM pre-search query.
 *
 * Follow-up turns like "Quien es su hermanastro?" search the RAW message, so
 * keyword DDG finds nothing topical and the model answers from stale history,
 * recycling old citation numbers. When the message carries an anaphoric token
 * (pronoun/demonstrative, ES+EN) AND prior-turn history exists, the query
 * becomes `"$message $anchor"` where the anchor is the most recent prior USER
 * turn (else the last assistant text), trimmed and capped at 200 chars.
 *
 * Pure function, query-text only — never changes call count, gates, budgets,
 * or caps (downstream `take(MAX_QUERY_CHARS)` still applies). Complements the
 * model-side GroundingPrompt reference rule ("resolve pronouns against
 * history first") — VM-side query anchoring gives DDG topical signal; the
 * model-side rule governs answering. Do not reword that rule.
 *
 * Matching mirrors [NeedsWeb]/[CodeIntent]: NFD-normalize + strip combining
 * marks (diacritic-insensitive, so `él`==`el`, `quién`==`quien`) THEN lowercase
 * THEN split on non-letters into letter-tokens. Whole-token matching only —
 * never substrings.
 *
 * Known accepted over-fire (fail-open by design): `el` collides with the
 * Spanish article after diacritic-strip, and lo/la/le fire on articles — worst
 * case is a harmless topic suffix DDG tolerates. Under-anchoring (a miss) is
 * the failure we refuse.
 */
object AnaphoraAnchor {

    /** Max anchor chars appended to the query (via take). */
    const val ANCHOR_MAX_CHARS = 200

    /**
     * Anaphoric tokens (ES+EN, stored WITHOUT diacritics — normalization makes
     * `él` match `el`, `quién` match `quien`). Matched as whole tokens.
     */
    internal val ANAPHORA = setOf(
        // ES possessives / pronouns / demonstratives
        "su", "sus",
        "el", "ella", "ello", "ellos", "ellas",
        "este", "esta", "estos", "estas",
        "ese", "esa", "esos", "esas",
        "aquel", "aquella", "aquellos", "aquellas",
        "eso", "esto", "aquello",
        "lo", "la", "los", "las", "le", "les",
        // EN pronouns / demonstratives
        "it", "its", "this", "these", "that", "those",
        "he", "she", "they", "him", "her", "them",
        "his", "hers", "theirs",
    )

    /**
     * Build the pre-search query for [message] given prior-turn history.
     *
     * - No anaphora token in [message] → [message] raw (today's behavior).
     * - No history ([priorUser] empty AND [lastAssistant] null/blank) →
     *   [message] raw.
     * - Anchor = most recent non-blank [priorUser] entry, else [lastAssistant]
     *   trimmed and truncated to [ANCHOR_MAX_CHARS].
     * - Dedupe: blank anchor OR [message] contains anchor (case-insensitive
     *   contains on raw strings) → [message] raw.
     * - Else `"$message $anchor"`.
     */
    fun buildQuery(
        message: String,
        priorUser: List<String>,
        lastAssistant: String?,
    ): String {
        if (normalize(message).none { it in ANAPHORA }) return message
        val anchorRaw = priorUser.lastOrNull { it.isNotBlank() }
            ?: lastAssistant?.takeIf { it.isNotBlank() }?.trim()
            ?: return message
        val anchor = anchorRaw.trim().take(ANCHOR_MAX_CHARS)
        if (anchor.isBlank()) return message
        if (message.contains(anchor, ignoreCase = true)) return message
        return "$message $anchor"
    }

    private fun normalize(query: String): List<String> {
        val stripped = Normalizer.normalize(query, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return stripped
            .lowercase()
            .split(Regex("[^\\p{L}]+"))
            .filter { it.isNotEmpty() }
    }
}
