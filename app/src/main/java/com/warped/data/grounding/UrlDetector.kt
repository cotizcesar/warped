package com.warped.data.grounding

/**
 * Phase 50 (WEB-01): deterministic first-URL heuristic.
 *
 * The first http(s) URL in the message triggers exactly one fetch;
 * second and later URLs are ignored (no multi-fetch agent loop).
 * Pure Kotlin — no Android imports, unit-testable on the JVM.
 */
object UrlDetector {

    private val URL_REGEX = Regex("""https?://[^\s<>"')\]]+""")

    /**
     * Returns the first http(s) URL in [text], with trailing sentence
     * punctuation (.,;:!?) trimmed, or null when there is no match.
     */
    fun firstUrl(text: String): String? {
        val match = URL_REGEX.find(text) ?: return null
        val trimmed = match.value.trimEnd('.', ',', ';', ':', '!', '?')
        return trimmed.ifEmpty { null }
    }

    /**
     * Phase 52 (FETCH-01): deterministic fan-out input — all http(s) URLs
     * in [text] with the same trailing-punctuation trim semantics as
     * [firstUrl], deduped preserving first-seen order, capped at [max].
     *
     * The default cap is the single source of truth in
     * [MultiUrlFetcher.MAX_URLS] (FETCH-01 contract) — never a duplicated
     * literal here. Negative [max] yields an empty list (never throws).
     */
    fun allUrls(text: String, max: Int = MultiUrlFetcher.MAX_URLS): List<String> =
        URL_REGEX.findAll(text)
            .map { it.value.trimEnd('.', ',', ';', ':', '!', '?') }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(max.coerceAtLeast(0))
            .toList()
}
