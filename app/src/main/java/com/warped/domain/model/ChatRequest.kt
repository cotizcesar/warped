package com.warped.domain.model

data class ChatRequest(
    val messages: List<ChatMessage>,
    val parameters: GenerationParameters = GenerationParameters(),
    val images: List<String> = emptyList(), // base64 data URLs
    val audioBytes: ByteArray? = null,
    /**
     * Phase 56 (56-02): per-chat web override tri-state (null = Heredar).
     * Carried so LiteRTLmProvider can compute its own loop-arming via
     * GroundingPrecedence.shouldGround(perChat, global) — the provider has
     * no conversationId to read the row itself. Ignored by remote helpers
     * (Phase 57 owns the remote loop). Defaults null: pre-56 callers behave
     * as inherit.
     */
    val webOverride: Boolean? = null,
)
