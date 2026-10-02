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
    /**
     * Phase 70 (70-02): turn-bound `[DOCUMENT CONTEXT]` block for the
     * `read_text_file` executor branch (local + all three remote drivers).
     * The VM sets the same fused `DocumentPrompt` block string it fused
     * into the turn text, so an explicit model tool call re-feeds identical
     * content (same cap, same loop call budget — never a second full-size
     * copy). Null when no document is attached: the executor feeds the
     * failed-read degradation string, never a previous turn's content
     * (T-70-04). Never persisted — request-scoped like the fused text.
     */
    val documentBlock: String? = null,
)
