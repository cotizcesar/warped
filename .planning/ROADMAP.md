# Roadmap: Warped v1.3

**Milestone:** v1.3 Remote Provider Endpoints & UX
**Created:** 2026-05-06
**Phases:** 16–19 (4 phases, continues from v1.2)
**Total requirements:** 31

## Phase Structure

| # | Phase | Goal | Requirements | Success Criteria |
|---|-------|------|--------------|------------------|
| 16 | Provider UI & API Key Auth | Expose all provider types in UI with readable names and encrypted API key management | PROV-01..04, AUTH-01..03 (7) | 4 |
| 17 | OpenAI + Anthropic Endpoints | Complete OpenAI-compatible API and expose Anthropic in UI | OPAI-01..05, ANTH-01..02 (7) | 4 |
| 18 | Ollama Full API | Full Ollama endpoint coverage: generate, chat, embed, ps, show, create, delete, pull | OLLM-01..09 (9) | 4 |
| 19 | LM Studio Validation & MCP | Validate LM Studio end-to-end with MCP ephemeral and mcp.json server support | LMST-01..08 (8) | 5 |

---

## Phase 16: Provider UI & API Key Auth

**Goal:** Expose all provider types in UI with readable names and encrypted API key management — the foundation that every other phase builds on.

**Requirements:** PROV-01, PROV-02, PROV-03, PROV-04, AUTH-01, AUTH-02, AUTH-03

**Depends on:** — (no dependencies)

### Success Criteria

1. Endpoint form dropdown shows all 5 remote provider types: OpenAI, Anthropic, Ollama, LM Studio, Custom — each with human-readable label
2. Provider type labels display as "OpenAI", "Anthropic", "LM Studio", "Ollama", "Custom" across all UI surfaces (dropdown, endpoint cards, chat header)
3. API key field is present in endpoint form, encrypted on save via Android Keystore, and submitted as `Authorization: Bearer` (OpenAI) or `x-api-key` (Anthropic/LM Studio)
4. Provider-specific form fields appear contextually (e.g., custom path fields for Custom provider, auth toggle for Anthropic/LM Studio)

### Key Deliverables

- `ProviderType.displayName` extension property mapping enum → readable label
- `EndpointForm` dropdown expanded from `listOf("LM_STUDIO")` to all 5 remote `ProviderType` values
- `DeployedEndpointCard` chip updated from `apiType.name` to `apiType.displayName`
- `ModelsUiState.formApiType` default changed to support all types
- `ApiKeyStore` integration in endpoint save flow: encrypt key → store ref → use in provider resolution
- `ProviderRouter.resolve()` updated to pass API key from `ApiKeyStore` to provider constructors
- `EndpointForm` dynamic fields based on selected `apiType` (e.g., `chatPath`/`modelsPath` for Custom, auth fields for Anthropic/LM Studio)

---

## Phase 17: OpenAI + Anthropic Endpoints

**Goal:** Complete OpenAI-compatible API surface (chat, models, responses, embeddings, completions) and expose Anthropic Messages API in the UI.

**Requirements:** OPAI-01, OPAI-02, OPAI-03, OPAI-04, OPAI-05, ANTH-01, ANTH-02

**Depends on:** Phase 16 (provider UI and auth must be in place)

### Success Criteria

1. `POST /v1/chat/completions` streams tokens via SSE to the chat UI using standard OpenAI chunk format (`choices[0].delta.content`)
2. `POST /v1/responses` works both streaming (`stream: true` → SSE events) and non-streaming, with `previous_response_id` for stateful follow-up
3. `POST /v1/embeddings` returns vector embeddings for single and batch input text
4. Anthropic `POST /v1/messages` is selectable from provider dropdown, with SSE streaming events parsed and rendered in chat UI

### Key Deliverables

- `OpenAiApi` expanded: `@POST("v1/responses")`, `@POST("v1/embeddings")`, `@POST("v1/completions")`
- `OpenAiResponsesRequest` / `OpenAiResponsesResponse` DTOs (responses API format)
- `OpenAiEmbeddingsRequest` / `OpenAiEmbeddingsResponse` DTOs
- `OpenAiCompletionsRequest` / `OpenAiCompletionsResponse` DTOs (legacy completions)
- `AnthropicProvider` registered for UI visibility (already implemented, verify SSR SSE parsing)
- `AnthropicApi` verified: `@POST("v1/messages")` with `x-api-key` header and SSE streaming
- `ProviderRouter.resolve()` verified for `ProviderType.ANTHROPIC` path with API key from `ApiKeyStore`
- SSE parsing utilities for responses API events and completions API chunks

---

## Phase 18: Ollama Full API

**Goal:** Full Ollama endpoint coverage — generate, chat, embed, running models, model details, create, delete, pull — all accessible through the same UI patterns.

**Requirements:** OLLM-01, OLLM-02, OLLM-03, OLLM-04, OLLM-05, OLLM-06, OLLM-07, OLLM-08, OLLM-09

**Depends on:** Phase 16 (provider UI and auth foundation)

### Success Criteria

1. `POST /api/generate` and `POST /api/chat` work with streaming NDJSON and non-streaming JSON, supporting all parameters (images, format, options, system, keep_alive)
2. `POST /api/embed` returns vector embeddings for single and batch input, with truncate and dimensions support
3. `POST /api/pull` shows streaming progress (NDJSON) for model downloads
4. `GET /api/ps`, `POST /api/show`, `POST /api/create`, `DELETE /api/delete` are callable from the endpoint and return correct responses

### Key Deliverables

- `OllamaApi` expanded: `@POST("api/embed")`, `@GET("api/ps")`, `@POST("api/show")`, `@POST("api/create")`, `@DELETE("api/delete")`, `@POST("api/pull")`
- `OllamaEmbedRequest` / `OllamaEmbedResponse` DTOs
- `OllamaPsResponse` / `OllamaShowRequest/Response` DTOs
- `OllamaCreateRequest` / `OllamaDeleteRequest` DTOs
- `OllamaPullRequest` with streaming NDJSON progress parsing
- `OllamaProvider` extended with new endpoints: `embed()`, `listRunning()`, `showModel()`, `createModel()`, `deleteModel()`, `pullModel()`
- Non-chat endpoints return results via `Result<T>` pattern, surfaced in ModelsScreen or dedicated UI as appropriate

---

## Phase 19: LM Studio Validation & MCP

**Goal:** Validate LM Studio works end-to-end (chat, models, load/unload, download), and add MCP support for ephemeral servers and mcp.json plugin servers.

**Requirements:** LMST-01, LMST-02, LMST-03, LMST-04, LMST-05, LMST-06, LMST-07, LMST-08

**Depends on:** Phase 16 (provider UI and auth)

### Success Criteria

1. `POST /api/v1/chat` streams all event types (reasoning.delta, message.delta, tool_call.*, chat.end) correctly to the chat UI, with stats displayed after generation
2. `POST /api/v1/models/load` and `POST /api/v1/models/unload` manage model lifecycle — chat switches models correctly without errors
3. `POST /api/v1/models/download` initiates downloads and `GET /api/v1/models/download/status/:job_id` reports progress
4. MCP ephemeral servers work: `integrations` with `type: "ephemeral_mcp"` sends tools to chat endpoint, tool calls are parsed from SSE events and displayed
5. MCP mcp.json servers work: `integrations` with `type: "plugin"` and `id: "mcp/<label>"` enables pre-configured server tools

### Key Deliverables

- `LmStudioApi` verified and expanded: `@POST("api/v1/models/download")`, `@GET("api/v1/models/download/status/{jobId}")`
- `LmStudioDownloadRequest/Response` DTOs
- `LmStudioChatRequest` expanded with `integrations` field (ephemeral MCP + plugin)
- `LmStudioIntegration` sealed class: `EphemeralMcp(serverLabel, serverUrl, allowedTools, headers)` + `PluginMcp(id, allowedTools)`
- MCP tool call parsing from SSE events (`tool_call.start`, `tool_call.arguments`, `tool_call.success`, `tool_call.failure`)
- `LmStudioSseEvent` expanded to include tool call event types
- Chat UI renders tool calls (name, arguments, output) inline alongside messages
- `ChatViewModel` LM Studio lifecycle management verified: load before chat, unload on switch, download with progress
- Model management UI in ModelsScreen for LM Studio: load/unload buttons, download progress

---

## Dependency Graph

```
Phase 16 (Provider UI & Auth)
   └── Phase 17 (OpenAI + Anthropic)
   └── Phase 18 (Ollama Full API)
   └── Phase 19 (LM Studio MCP & Validation)
```

Phases 17, 18, and 19 are independent of each other and can be developed in any order after Phase 16.

---

## Progress

| Phase | Status | Requirements | Progress |
|-------|--------|-------------|----------|
| 16    | ○      | 7           | 0%       |
| 17    | ○      | 7           | 0%       |
| 18    | ○      | 9           | 0%       |
| 19    | ○      | 8           | 0%       |

---
*Roadmap created: 2026-05-06*
*Last updated: 2026-05-06 after initial creation*
