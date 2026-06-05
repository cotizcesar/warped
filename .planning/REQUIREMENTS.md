# Requirements: Warped

**Defined:** 2026-06-05
**Last updated:** 2026-06-05 after milestone v1.8 requirements definition
**Core Value:** Run and chat with any LLM — local or remote — from a single Android app, with a simple LM Studio-grade experience that works offline.

## v1.8 Requirements

Requirements for the LiteRT Update, Bugfix Round & Recommended Models milestone.

### LiteRT Engine

- [ ] **LRT-01**: User gets the latest stable LiteRT-LM library release (currently upgrading from v0.12.0) — version bump in `libs.versions.toml`, Gradle sync succeeds
- [ ] **LRT-02**: Multi-turn conversation with a LiteRT-LM model retains full message history — the engine no longer treats each user message as the first message of a new conversation
- [ ] **LRT-03**: Any breaking API changes introduced by the new LiteRT-LM version are adapted — compilation succeeds, all existing local inference functionality preserved, unit tests pass

### Hugging Face Model Browser

- [ ] **HF-01**: Model detail screen shows all `.litertlm` sibling files regardless of LFS metadata availability — files with `size=0` or missing `lfs` field appear in the list and compatibility check
- [ ] **HF-02**: Model detail file cards match the visual design of the main search list — same Storage icon, chips, border, `primaryContainer` colors
- [ ] **HF-03**: User can cancel an in-progress model download from the detail screen — cancels the in-flight download and removes any partial file
- [ ] **HF-04**: Search query text in the Hugging Face browser survives device rotation and back-navigation from a model detail screen
- [ ] **HF-05**: After a model download completes successfully, the user is auto-navigated to the Models & Endpoints list so the new model is immediately visible
- [ ] **HF-06**: GGUF/LiteRT metadata parser validates string lengths before allocating byte arrays — corrupted or malformed metadata no longer triggers false "out of memory" errors
- [ ] **HF-07**: Downloads continue in the background when the user navigates away from the Hugging Face screen — leaving the screen does not pause or kill the download
- [ ] **HF-08**: Active and incomplete downloads appear in the Models & Endpoints list with progress bars
- [ ] **HF-09**: User can cancel and delete an incomplete/paused download from the Models & Endpoints list

### Chat UI & Model Selector

- [ ] **CHAT-01**: Chat input bar is a rounded pill with a transparent underline — ChatGPT/Claude style with Material `Icons.AutoMirrored.Filled.Send` and `Icons.Filled.Stop`
- [ ] **CHAT-02**: Chat screen has no TopAppBar — model picker is inline above the messages, drawer is reachable via swipe gesture
- [ ] **CHAT-03**: Bottom navigation bar shows icons only, no text labels — more vertical space for the chat content
- [ ] **CHAT-04**: Network endpoints appear alongside local models in the chat model picker — single unified bottom-sheet selector
- [ ] **CHAT-05**: Model selection is an icon button near the chat input that opens the bottom-sheet picker — no longer at the top of the chat
- [ ] **CHAT-06**: Models & Endpoints screen has a compact title with minimal top space — wasted vertical padding removed
- [ ] **CHAT-07**: When a user selects a local model for chat, a "Cargando modelo" loading indicator appears in the chat while the model loads asynchronously into RAM
- [ ] **CHAT-08**: Network endpoints fetch and display their available models (e.g. LM Studio's `/api/v1/models`) — the picker shows models the endpoint can serve

### Endpoint CRUD & Provider

- [ ] **ENDPT-01**: User can edit an existing network endpoint from the Models & Endpoints list — name, URL, and API key are editable in a form
- [ ] **ENDPT-02**: User can delete a network endpoint from the Models & Endpoints list — deletion removes the endpoint and its stored API key
- [ ] **ENDPT-03**: Endpoint deletion provides immediate UI feedback — the card disappears from the list without waiting for the DB Flow to re-emit
- [ ] **ENDPT-04**: Warped uses the LM Studio native v1 REST API (`/api/v1/chat`, `/api/v1/models`) instead of the OpenAI-compatible endpoint — full native provider with native DTOs
- [ ] **ENDPT-05**: Endpoint form offers only LM_STUDIO as the provider type — OpenAI, Anthropic, Ollama, and Custom options are removed in favor of LM Studio focus
- [ ] **ENDPT-06**: Network security config allows HTTP cleartext traffic to LAN IPs (192.168.x.x, 10.x.x.x, 172.16-31.x.x) — local-network LM Studio / Ollama servers work without TLS
- [ ] **ENDPT-07**: User-entered endpoint URLs are normalized — missing `http://` scheme is added, missing trailing `/` is appended, before creating the provider

### Recommended Models

- [ ] **REC-01**: User sees a hand-curated "Recommended" section at the top of the model browser — a fixed list of 5–10 recommended `.litertlm` models shipped with the app (no scraping, no API calls)
- [ ] **REC-02**: Each recommended model displays name, size, a one-line "why recommended" description, and a one-tap entry point that opens the model's Hugging Face detail page for download
- [ ] **REC-03**: Recommended list is shipped as a static asset (e.g. JSON in `assets/` or a Kotlin constant) — the list is part of the app binary, versioned with the app, and never fetched over the network

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

- **REMOTE-01**: Endpoint health check polling with background refresh
- **REMOTE-02**: Memory pressure-based automatic model eviction with configurable thresholds
- **LRT-04**: Samsung Hexagon NPU acceleration for LiteRT-LM (when stable)
- **LRT-05**: Vulkan GPU acceleration for LiteRT-LM (when stable)
- **ANTHROPIC-01**: Re-introduce Anthropic API support if user demand returns — currently removed in v1.8 in favor of LM Studio focus

## Out of Scope

| Feature | Reason |
|---------|--------|
| OpenAI, Anthropic, Ollama, Custom provider types | Removed in v1.8 — milestone focuses on LM Studio as the sole remote provider |
| Voice input/output | Defer, focus on text chat |
| Image/multimodal models | Defer, focus on text LLMs |
| AI agents / autonomous tool use | Defer, MCP tool calling via LM Studio API is in scope |
| Real-time sync across devices | Defer |
| Paid subscriptions to remote providers | User brings own API keys |
| GGUF / llama.cpp local inference | Removed in v1.5. App pivots to LiteRT-LM as sole local engine |
| Certificate pinning for remote endpoints | Defer |
| Play Integrity / root detection | Defer |
| Auto-scraped "trending" models list | REC-01 uses a hand-curated static list — no API scraping, no ranking algorithms |
| User-customizable recommended lists | REC-01 ships a fixed list with the app |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| LRT-01 | Phase 35 | Pending |
| LRT-02 | Phase 35 | Pending |
| LRT-03 | Phase 35 | Pending |
| HF-01 | Phase 36 | Pending |
| HF-02 | Phase 36 | Pending |
| HF-03 | Phase 36 | Pending |
| HF-04 | Phase 36 | Pending |
| HF-05 | Phase 36 | Pending |
| HF-06 | Phase 36 | Pending |
| HF-07 | Phase 36 | Pending |
| HF-08 | Phase 36 | Pending |
| HF-09 | Phase 36 | Pending |
| CHAT-01 | Phase 37 | Pending |
| CHAT-02 | Phase 37 | Pending |
| CHAT-03 | Phase 37 | Pending |
| CHAT-04 | Phase 37 | Pending |
| CHAT-05 | Phase 37 | Pending |
| CHAT-06 | Phase 37 | Pending |
| CHAT-07 | Phase 37 | Pending |
| CHAT-08 | Phase 37 | Pending |
| ENDPT-01 | Phase 38 | Pending |
| ENDPT-02 | Phase 38 | Pending |
| ENDPT-03 | Phase 38 | Pending |
| ENDPT-04 | Phase 38 | Pending |
| ENDPT-05 | Phase 38 | Pending |
| ENDPT-06 | Phase 38 | Pending |
| ENDPT-07 | Phase 38 | Pending |
| REC-01 | Phase 39 | Pending |
| REC-02 | Phase 39 | Pending |
| REC-03 | Phase 39 | Pending |

**Coverage:**
- v1.8 requirements: 30 total
- Mapped to phases: 30
- Unmapped: 0 ✓

---
*Requirements defined: 2026-06-05*
*Last updated: 2026-06-05 after milestone v1.8 requirements definition*
