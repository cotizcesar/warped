# Phase 17: OpenAI + Anthropic Endpoints - Context

**Gathered:** 2026-05-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Complete OpenAI-compatible API surface (responses, embeddings, completions) and expose/verify Anthropic Messages API in the UI. Anthropic UI exposure is already done from Phase 16 dropdown. This phase adds the remaining API endpoints and SSE parsing.
</domain>

<decisions>
## Implementation Decisions

### OpenAI API Surface
- Add methods directly to OpenAIProvider (no separate provider classes per endpoint)
- New `asResponsesSseFlow()` parser for Responses API SSE (different event format: `response.created`, `response.output_text.delta`)
- Implement `LlmProvider.embed()` but defer UI — embeddings are programmatic
- Implement legacy `/v1/completions` with streaming for backward compatibility

### Anthropic Integration & Verification
- No additional UI changes needed — Phase 16 already handles dropdown + API key injection
- Anthropic SSE parser already handles: `content_block_delta`, `message_delta`, `message_stop`, `error`. Verify works end-to-end
- Anthropic thinking blocks: render as collapsible "Thinking..." section following existing `<think>` pattern in ChatViewModel

### the agent's Discretion
- All implementation choices at agent's discretion within bounds above
- Follow existing patterns: DTO naming, @Serializable, OpenAiApi Retrofit interface
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `OpenAiApi.kt` — already has `chatCompletions` and `listModels`. Add `responses`, `embeddings`, `completions`
- `OpenAiChatRequest.kt` — existing DTO pattern with @Serializable, SerialName
- `SseExtensions.kt` — `asSseFlow()` for chat completions, `asOllamaFlow()` for Ollama
- `AnthropicProvider.kt` — fully implemented with SSE parsing, x-api-key auth, testConnection
- `ProviderRouter.kt` — already resolves ANTHROPIC with API key
- `StreamChunks.kt` — `OpenAiStreamChunk`, `OllamaStreamChunk` DTOs
- `LlmProvider.kt` — interface with `chat()`, `listModels()`, `testConnection()`

### Integration Points
- `OpenAiApi.kt:15` — add `@POST("v1/responses")`, `@POST("v1/embeddings")`, `@POST("v1/completions")`
- `OpenAIProvider.kt` — add `chatResponses()`, `embed()`, `completions()`
- `SseExtensions.kt` — add `asResponsesSseFlow()` 
- `OpenAiChatRequest.kt` — add response/embedding/completion DTOs
- `LlmProvider.kt` — add `embed(): Flow<FloatArray>` or similar
- `ChatViewModel.kt` — check Anthropic thinking block rendering
</code_context>

<specifics>x``
- OPAI-01 through OPAI-05 requirements map to this phase
- ANTH-01 and ANTH-02 requirements — Anthropic API with streaming SSE
- Responses API: streaming + non-streaming, previous_response_id for stateful follow-up
- Embeddings API: single + batch input, dimensions, encoding_format
- Completions API: legacy prompt-based, streaming SSE
</specifics>

<deferred>
## Deferred Ideas

- Embeddings UI — implement provider API but defer any UI
- Responses API tool calling with MCP server_label — defer, complex
- OpenAI structured output (JSON schema response_format) — defer unless trivial
</deferred>
