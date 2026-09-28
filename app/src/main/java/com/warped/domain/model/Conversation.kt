package com.warped.domain.model

import java.time.Instant

data class Conversation(
    val id: Long,
    val title: String,
    val providerType: ProviderType,
    val endpointId: Long,
    val modelId: String? = null,
    val systemPrompt: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    // Phase 53 (TOGGLE-01): tri-state per-chat web override. null means inherit
    // the global default-ON DataStore setting; non-null wins over global.
    val webOverride: Boolean? = null,
)
