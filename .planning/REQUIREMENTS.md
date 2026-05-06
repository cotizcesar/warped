# Requirements: Warped

**Defined:** 2026-05-06
**Core Value:** Run and chat with any LLM — local or remote — from a single Android app, with a simple LM Studio-grade experience that works offline.

## v1.3 Requirements

Requirements for milestone v1.3 Remote Provider Endpoints & UX. Each maps to roadmap phases.

### Provider UI & UX

- [ ] **PROV-01**: User can select any provider type (OpenAI, Anthropic, Ollama, LM Studio, Custom) in the endpoint form dropdown
- [ ] **PROV-02**: Provider type names display as human-readable labels ("OpenAI", "Anthropic", "LM Studio", "Ollama", "Custom") — not UPPER_CASE enum names with underscores
- [ ] **PROV-03**: Endpoint cards in ModelsScreen display provider name as human-readable label (not raw apiType.name)
- [ ] **PROV-04**: Each provider type shows its specialized form fields (e.g., auth headers toggle for Anthropic/LM Studio, model load config for LM Studio, custom paths for Custom)

### API Key & Authentication

- [ ] **AUTH-01**: User can configure API key per endpoint, stored encrypted via Android Keystore (EncryptedSharedPreferences)
- [ ] **AUTH-02**: API key is sent as `Authorization: Bearer <token>` header for OpenAI-compatible endpoints
- [ ] **AUTH-03**: API key is sent as `x-api-key` header for Anthropic-compatible and LM Studio endpoints

### OpenAI-Compatible Endpoints

- [ ] **OPAI-01**: `POST /v1/chat/completions` — streaming chat with SSE token parsing, response_format (JSON schema structured output), tools (function calling), and all inference parameters (temperature, top_p, top_k, max_tokens, stream, stop, presence_penalty, frequency_penalty, logit_bias, repeat_penalty, seed)
- [ ] **OPAI-02**: `GET /v1/models` — list available models from the remote endpoint
- [ ] **OPAI-03**: `POST /v1/responses` — OpenAI Responses API (non-streaming + streaming SSE, stateful via previous_response_id, tools with MCP server_label/server_url, reasoning effort)
- [ ] **OPAI-04**: `POST /v1/embeddings` — OpenAI Embeddings API (single + batch input, dimensions, encoding_format)
- [ ] **OPAI-05**: `POST /v1/completions` — OpenAI legacy Completions API (prompt-based, non-chat format, streaming SSE)

### Anthropic-Compatible Endpoints

- [ ] **ANTH-01**: `POST /v1/messages` — Anthropic Messages API with streaming SSE (message_start, content_block_start, content_block_delta, content_block_stop, message_delta, message_stop), tools (function calling with input_schema), tool_choice, system prompt, max_tokens
- [ ] **ANTH-02**: Anthropic provider is selectable from endpoint form UI dropdown (provider implementation already exists in codebase)

### LM Studio Validation & MCP

- [ ] **LMST-01**: `POST /api/v1/chat` — stateful chat with SSE streaming (chat.start, model_load.*, prompt_processing.*, reasoning.*, tool_call.*, message.*, chat.end), multimodal input (text + image data_url), system_prompt, integrations (plugins + ephemeral MCP), context_length, reasoning setting, store/previous_response_id for stateful context
- [ ] **LMST-02**: `GET /api/v1/models` — list LLM and embedding models with quantization, capabilities (vision, trained_for_tool_use, reasoning), loaded_instances, variants
- [ ] **LMST-03**: `POST /api/v1/models/load` — load model into memory with configurable context_length, eval_batch_size, flash_attention, num_experts, offload_kv_cache_to_gpu, echo_load_config
- [ ] **LMST-04**: `POST /api/v1/models/unload` — unload model by instance_id to free memory
- [ ] **LMST-05**: `POST /api/v1/models/download` — download model from LM Studio catalog identifier or Hugging Face URL, with optional quantization
- [ ] **LMST-06**: `GET /api/v1/models/download/status/:job_id` — check download progress (bytes_per_second, estimated_completion, downloaded_bytes, status)
- [ ] **LMST-07**: MCP ephemeral server support — `integrations` field with `type: "ephemeral_mcp"`, `server_label`, `server_url`, `allowed_tools`, and custom `headers` for authenticated MCP servers
- [ ] **LMST-08**: MCP mcp.json server support — `integrations` field with `type: "plugin"`, `id: "mcp/<server_label>"`, and optional `allowed_tools`

### Ollama Endpoints

- [ ] **OLLM-01**: `POST /api/generate` — generate response from prompt (streaming NDJSON, non-streaming JSON, images, format/structured output, system, options, keep_alive, raw mode)
- [ ] **OLLM-02**: `POST /api/chat` — OpenAI-compatible chat format with messages array, streaming NDJSON, tools, format, options
- [ ] **OLLM-03**: `GET /api/tags` — list locally available models with details (format, family, parameter_size, quantization_level, size, digest)
- [ ] **OLLM-04**: `POST /api/embed` — generate vector embeddings (single + batch input, truncate, dimensions)
- [ ] **OLLM-05**: `GET /api/ps` — list currently running models (size, size_vram, context_length, expires_at)
- [ ] **OLLM-06**: `POST /api/show` — show model details (parameters, license, template, capabilities, model_info metadata)
- [ ] **OLLM-07**: `POST /api/create` — create model from modelfile content, path, or from existing model
- [ ] **OLLM-08**: `DELETE /api/delete` — delete a model and its data
- [ ] **OLLM-09**: `POST /api/pull` — pull/download model from registry (streaming progress NDJSON)

## v2 Requirements

Deferred to future milestone. Tracked but not in current roadmap.

### Remote Provider

- **REM-01**: OAuth/OIDC authentication for remote endpoints — defer, API key is sufficient
- **REM-02**: Automatic mDNS/LAN discovery of Ollama and LM Studio instances — defer, manual URL entry works
- **REM-03**: Remote endpoint health monitoring and auto-reconnect — defer

### LM Studio

- **LMST-09**: LM Studio `/api/v1/models/download` via WorkManager for background downloads with Android notifications — defer, use in-app download
- **LMST-10**: Structured Output (JSON schema) for `/api/v1/chat` via grammar-based sampling — defer until grammar support is stable

### Ollama

- **OLLM-10**: `POST /api/push` — push model to registry — defer, pull/download is sufficient
- **OLLM-11**: `POST /api/copy` — copy/rename model — defer, low priority

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Voice input/output | Defer, focus on text chat |
| Image/multimodal models (for chat) | Defer, LM Studio multimodal input is supported but not in Warped's chat UI |
| AI agents / autonomous tool use | Defer, MCP tool calling via LM Studio API is in scope but agentic loops are not |
| Real-time sync across devices | Defer |
| Paid subscriptions to remote providers built into the app | User brings own API keys |
| OAuth/OIDC authentication | API key is sufficient for v1.3 |
| mDNS/LAN auto-discovery | Manual URL entry works |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| PROV-01 | Phase 16 | Pending |
| PROV-02 | Phase 16 | Pending |
| PROV-03 | Phase 16 | Pending |
| PROV-04 | Phase 16 | Pending |
| AUTH-01 | Phase 16 | Pending |
| AUTH-02 | Phase 16 | Pending |
| AUTH-03 | Phase 16 | Pending |
| OPAI-01 | Phase 17 | Pending |
| OPAI-02 | Phase 17 | Pending |
| OPAI-03 | Phase 17 | Pending |
| OPAI-04 | Phase 17 | Pending |
| OPAI-05 | Phase 17 | Pending |
| ANTH-01 | Phase 17 | Pending |
| ANTH-02 | Phase 17 | Pending |
| LMST-01 | Phase 19 | Pending |
| LMST-02 | Phase 19 | Pending |
| LMST-03 | Phase 19 | Pending |
| LMST-04 | Phase 19 | Pending |
| LMST-05 | Phase 19 | Pending |
| LMST-06 | Phase 19 | Pending |
| LMST-07 | Phase 19 | Pending |
| LMST-08 | Phase 19 | Pending |
| OLLM-01 | Phase 18 | Pending |
| OLLM-02 | Phase 18 | Pending |
| OLLM-03 | Phase 18 | Pending |
| OLLM-04 | Phase 18 | Pending |
| OLLM-05 | Phase 18 | Pending |
| OLLM-06 | Phase 18 | Pending |
| OLLM-07 | Phase 18 | Pending |
| OLLM-08 | Phase 18 | Pending |
| OLLM-09 | Phase 18 | Pending |

**Coverage:**
- v1.3 requirements: 31 total
- Mapped to phases: 31
- Unmapped: 0 ✓

---
*Requirements defined: 2026-05-06*
*Last updated: 2026-05-06 after roadmap creation*
