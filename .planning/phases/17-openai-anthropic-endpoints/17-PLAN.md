# Phase 17: OpenAI + Anthropic Endpoints - Plan

**Planned:** 2026-05-06
**Status:** Complete

## Tasks

### T1: OpenAI DTOs
- OpenAiResponsesRequest, OpenAiResponsesResponse, OpenAiResponseOutputItem, OpenAiResponseContentPart
- OpenAiResponsesStreamEvent, OpenAiResponseDelta
- OpenAiEmbeddingsRequest, OpenAiEmbeddingsResponse, OpenAiEmbeddingData
- OpenAiCompletionsRequest, OpenAiCompletionsStreamChunk, OpenAiCompletionChoice
- File: `data/remote/dto/OpenAiChatRequest.kt`

### T2: OpenAiApi endpoints
- `@POST("v1/responses")`, `@POST("v1/embeddings")`, `@POST("v1/completions")`
- File: `data/remote/api/OpenAiApi.kt`

### T3: SSE parsers
- `asResponsesSseFlow()` — Responses API event parsing (output_text.delta, completed)
- `asCompletionsSseFlow()` — Legacy completions SSE parsing (choices[0].text)
- File: `data/remote/network/SseExtensions.kt`

### T4: OpenAIProvider methods
- `responses()` — streaming via asResponsesSseFlow
- `completions()` — streaming via asCompletionsSseFlow
- `embed()` — non-streaming, returns List<List<Float>>
- File: `data/remote/provider/OpenAIProvider.kt`

### T5: Anthropic thinking blocks
- `AnthropicDelta.thinking` field added
- SSE parser: thinking deltas wrapped in `<think>...</think>` tags
- ChatViewModel `parseThinkBlocks()` handles the rest
- Files: `data/remote/dto/AnthropicDtos.kt`, `data/remote/provider/AnthropicProvider.kt`
