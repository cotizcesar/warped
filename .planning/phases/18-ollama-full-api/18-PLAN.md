# Phase 18: Ollama Full API - Plan

**Planned:** 2026-05-06
**Status:** Complete

## Tasks

### T1: Ollama DTOs
- OllamaGenerateRequest, OllamaGenerateChunk, OllamaPullRequest, OllamaPullChunk
- OllamaEmbedRequest, OllamaEmbedResponse
- OllamaPsResponse, OllamaRunningModel
- OllamaShowRequest, OllamaShowResponse
- OllamaCreateRequest, OllamaDeleteRequest
- Files: `data/remote/dto/OllamaChatRequest.kt`, `data/remote/dto/StreamChunks.kt`

### T2: OllamaApi endpoints
- POST api/generate, api/embed, api/show, api/create, api/pull
- GET api/ps, DELETE api/delete
- File: `data/remote/api/OllamaApi.kt`

### T3: Ollama streaming parsers
- asOllamaGenerateFlow() — NDJSON generate stream
- asOllamaPullFlow() — NDJSON pull progress stream
- File: `data/remote/network/SseExtensions.kt`

### T4: OllamaProvider methods
- generate(), embed(), listRunning(), showModel(), createModel(), deleteModel(), pullModel()
- File: `data/remote/provider/OllamaProvider.kt`
