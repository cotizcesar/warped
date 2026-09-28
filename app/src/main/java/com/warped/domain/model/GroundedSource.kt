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
)

/** Phase 53: per-source fetch outcome. Unknown stored strings map to OMITIDA (drop-unknown). */
enum class GroundedSourceStatus {
    OK,
    OMITIDA,
}
