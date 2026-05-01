package com.warped.domain.model

import java.time.Instant

data class Endpoint(
    val id: Long = 0,
    val name: String,
    val url: String,
    val apiType: ProviderType,
    val modelId: String? = null,
    val isActive: Boolean = false,
    val createdAt: Instant = Instant.now()
)
