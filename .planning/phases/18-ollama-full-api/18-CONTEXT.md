# Phase 18: Ollama Full API - Context

**Gathered:** 2026-05-06
**Status:** Ready for planning
**Mode:** Auto-generated (infrastructure phase)

<domain>
## Phase Boundary

Full Ollama endpoint coverage — generate, chat, embed, running models, model details, create, delete, pull. Currently only /api/chat and /api/tags are implemented. Add the remaining 7 endpoints.
</domain>

<decisions>
## Implementation Decisions

### the agent's Discretion
Pure API expansion phase — all implementation choices at agent's discretion. Follow existing OllamaProvider/API/DTO patterns.

Required endpoints:
- POST /api/generate — generate response (NDJSON streaming)
- POST /api/embed — vector embeddings
- GET /api/ps — running models
- POST /api/show — model details
- POST /api/create — create model from modelfile/path
- DELETE /api/delete — delete model
- POST /api/pull — download model (NDJSON streaming progress)
</decisions>

<code_context>
## Existing Code
- OllamaApi.kt — /api/chat + /api/tags
- OllamaChatRequest.kt — DTOs for chat/messages/models
- OllamaProvider.kt — chat(), listModels(), testConnection()
- StreamChunks.kt — OllamaStreamChunk
- SseExtensions.kt — asOllamaFlow() for NDJSON streaming
</code_context>

<specifics>
No specific requirements beyond ROADMAP phase description and OLLM-01 through OLLM-09 requirements.
</specifics>

<deferred>
None
</deferred>
