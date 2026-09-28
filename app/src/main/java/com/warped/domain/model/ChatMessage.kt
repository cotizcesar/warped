package com.warped.domain.model

import java.time.Instant
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val content: String,
    val tokenCount: Int = 0,
    val createdAt: Instant = Instant.now(),
    val reasoning: String? = null,
    val stats: String? = null,
    val imageUris: List<String> = emptyList(), // base64 data URLs
    // Phase 50 (WEB-06): ephemeral grounding state — never persisted to Room
    // (EntityMappers maps field-by-field, no migration).
    val groundedSources: List<String> = emptyList(),
    // Phase 53 (SRC-01/02): ephemeral per-source details (url + text + status)
    // hydrated from the grounded_sources table on history load. groundedSources
    // stays as the ephemeral render list; no source columns on MessageEntity.
    val groundedSourceDetails: List<GroundedSource> = emptyList(),
    val modelOnlyNotice: ModelOnlyNotice? = null,
    // Phase 52 (FETCH-02): attempted-URL count behind an all-fail banner so
    // the copy pluralizes for M > 1. Ephemeral, same as its siblings.
    val modelOnlySourceCount: Int = 1,
)

/** Phase 50 (WEB-06): why a grounded turn fell back to the model-only path. */
enum class ModelOnlyNotice {
    OFFLINE,
    FETCH_FAILED,
}
