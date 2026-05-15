package com.warped.domain.model

data class ChatRequest(
    val messages: List<ChatMessage>,
    val parameters: GenerationParameters = GenerationParameters(),
    val images: List<String> = emptyList(), // base64 data URLs
    val audioBytes: ByteArray? = null,
)
