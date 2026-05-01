package com.warped.domain.model

import java.time.Instant
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val content: String,
    val tokenCount: Int = 0,
    val createdAt: Instant = Instant.now()
)
