package com.warped.domain.model

/**
 * Phase 53 (SRC-01/02): ephemeral per-source detail for the Fuentes list and
 * the SourcePreviewSheet. Hydrated from the `grounded_sources` table on
 * history load; never persisted into [ChatMessage] columns.
 *
 * @property url fetcher-resolved post-redirect URL (http/https only).
 * @property extractedText sanitized page text for ok rows; null for omitida rows.
 * @property status ok rows open the preview sheet; omitida rows render struck/disabled.
 */
data class GroundedSource(
    val url: String,
    val extractedText: String? = null,
    val status: GroundedSourceStatus = GroundedSourceStatus.OK,
    /**
     * Phase 58 (OG-01): OpenGraph fields threaded from [com.warped.data.grounding.OpenGraphParser]
     * via GroundingResult.Grounded. Null means no OG captured (pre-58 rows,
     * plain/markdown sources, Tavily rows) — renders as a text-only card.
     */
    val ogTitle: String? = null,
    val ogDescription: String? = null,
    val ogImageUrl: String? = null,
)

/** Phase 53: per-source fetch outcome. Unknown stored strings map to OMITIDA (drop-unknown). */
enum class GroundedSourceStatus {
    OK,
    OMITIDA,
}
