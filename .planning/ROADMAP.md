# Roadmap: Warped

**Created:** 2026-04-30
**Updated:** 2026-05-02 (v1.1 phases added)
**Granularity:** Coarse (3-5 phases)

## Milestones

- ✅ **v1.0 MVP** — Phases 1-5 (shipped 2026-05-01)
- ✅ **v1.1 LiteRT-LM Integration** — Phases 6-10 (shipped 2026-05-02) → [archive](milestones/v1.1-ROADMAP.md)
- 📋 **v2.0 Advanced Features** — Phases 11+ (planned)

## Phases

<details>
<summary>✅ v1.0 MVP (Phases 1-5) — SHIPPED 2026-05-01</summary>

### Phase 1: Foundation & Remote Chat
**Goal:** Establish the app foundation and deliver remote chat with streaming token-by-token responses from OpenAI-compatible, Ollama, LM Studio, and custom endpoints — with full persistence and encrypted API keys.
**Requirements:** PROV-01, PROV-02, PROV-03, PROV-04, PROV-05, CHAT-01, CHAT-02, CHAT-03, CHAT-04, CHAT-05, PERS-01, PERS-02, SEC-01
**Plans:** Complete

### Phase 2: Local Inference
**Goal:** Enable on-device GGUF model inference via llama.cpp JNI bridge, with import from storage, streaming chat, cancel/stop, model management, RAM-based warnings, and graceful error handling.
**Requirements:** LOCL-01, LOCL-02, LOCL-03, LOCL-04, LOCL-05, PERS-03, DEV-01, DEV-02
**Plans:** Complete

### Phase 3: Model Acquisition
**Goal:** Let users discover and download GGUF models directly from Hugging Face in-app — with search, GGUF filtering, file details, foreground download notifications, pause/resume, and storage validation.
**Requirements:** ACQ-01, ACQ-02, ACQ-03, ACQ-04, ACQ-05
**Plans:** Complete

### Phase 4: Parameters & Presets
**Goal:** Expose all v1 generation parameters (temperature, top_p, top_k, repeat_penalty, max_tokens, context_size, seed, threads) with sensible defaults, and allow saving/loading named presets.
**Requirements:** PARM-01, PARM-02
**Plans:** Complete

### Phase 5: Security Hardening & Polish
**Goal:** Provide user-facing data deletion controls (chat history, API keys), ensure all sensitive data is purged, and perform final security review against checklist.
**Requirements:** SEC-02, SEC-03
**Plans:** Complete

</details>

<details>
<summary>✅ v1.1 LiteRT-LM Integration (Phases 6-10) — SHIPPED 2026-05-02</summary>

See [milestone archive](milestones/v1.1-ROADMAP.md) for full phase details.

**Milestone Goal:** Add Google's LiteRT-LM as a second high-performance local inference engine alongside llama.cpp — search, download, import, and chat with `.litertlm` models from Hugging Face's litert-community, with auto-detected GPU/NPU acceleration.

**Phases:** 6. Engine Foundation (4 plans) → 7. Provider Integration & Chat (2 plans) → 8. Model Acquisition (5 plans) → 9. UI Integration (3 plans) → 10. Parameters & Polish (1 plan)
**Requirements:** 26/26 satisfied
**Plans:** 14 completed, 2 gap closure
**Tech Debt:** 24 items (hardware-dependent testing, DRY violations, stale docs)

</details>

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Foundation & Remote Chat | v1.0 | 3/3 | Complete | 2026-04-30 |
| 2. Local Inference | v1.0 | 3/3 | Complete | 2026-05-01 |
| 3. Model Acquisition | v1.0 | 2/2 | Complete | 2026-05-01 |
| 4. Parameters & Presets | v1.0 | 2/2 | Complete | 2026-05-01 |
| 5. Security Hardening & Polish | v1.0 | 1/1 | Complete | 2026-05-01 |
| 6. Engine Foundation | v1.1 | 4/4 | Complete | 2026-05-02 |
| 7. Provider Integration & Chat | v1.1 | 2/2 | Complete | 2026-05-02 |
| 8. Model Acquisition | v1.1 | 5/5 | Complete | 2026-05-02 |
| 9. UI Integration | v1.1 | 3/3 | Complete | 2026-05-02 |
| 10. Parameters & Polish | v1.1 | 1/1 | Complete | 2026-05-02 |

## Coverage

| Milestone | Requirements | Mapped | Unmapped |
|-----------|-------------|--------|----------|
| v1.0 | 30 | 30 ✓ | 0 |
| v1.1 | 26 | 26 ✓ | 0 |

All v1.1 requirements are traced to exactly one phase in the [Traceability table](REQUIREMENTS.md#v11-traceability).

---

*Roadmap updated: 2026-05-02*
