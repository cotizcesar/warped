package com.warped.domain.model

/**
 * Quick-task (card-snippet): max stored snippet chars. Search excerpts are
 * short by nature; the cap only binds legacy keyed-provider `content`
 * heads stored as snippets (the card render caps again at 160 for density).
 */
const val GROUNDED_SNIPPET_MAX_CHARS = 500

/**
 * Phase 53 (SRC-01/02): ephemeral per-source detail for the Fuentes list and
 * the SourcePreviewSheet. Hydrated from the `grounded_sources` table on
 * history load; never persisted into [ChatMessage] columns.
 *
 * @property url fetcher-resolved post-redirect URL (http/https only).
 * @property extractedText sanitized page text for ok rows; null for omitida rows.
 * @property status ok rows open the preview sheet; omitida rows render struck/disabled.
 * @property snippet quick-task (card-snippet): sanitized search-excerpt for
 * card description fallback when `ogDescription` is absent. Null for
 * omitida rows, fetch-path rows (they carry full `extractedText`), and
 * pre-snippet history rows.
 */
data class GroundedSource(
    val url: String,
    val extractedText: String? = null,
    val status: GroundedSourceStatus = GroundedSourceStatus.OK,
    /**
     * Phase 58 (OG-01): OpenGraph fields threaded from [com.warped.data.grounding.OpenGraphParser]
     * via GroundingResult.Grounded. Null means no OG captured (pre-58 rows,
     * plain/markdown sources, legacy keyed-provider rows) — renders as a text-only card.
     */
    val ogTitle: String? = null,
    val ogDescription: String? = null,
    val ogImageUrl: String? = null,
    val snippet: String? = null,
)

/** Phase 53: per-source fetch outcome. Unknown stored strings map to OMITIDA (drop-unknown). */
enum class GroundedSourceStatus {
    OK,
    OMITIDA,
}
