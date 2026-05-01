package com.warped.domain.model

data class ChatRequest(
    val messages: List<ChatMessage>,
    val parameters: GenerationParameters = GenerationParameters()
)
