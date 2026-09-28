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
    val modelOnlyNotice: ModelOnlyNotice? = null,
)

/** Phase 50 (WEB-06): why a grounded turn fell back to the model-only path. */
enum class ModelOnlyNotice {
    OFFLINE,
    FETCH_FAILED,
}
