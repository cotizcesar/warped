# Roadmap: Warped

**Created:** 2026-04-30
**Updated:** 2026-05-02 (v1.1 phases added)
**Granularity:** Coarse (3-5 phases)

## Milestones

- ✅ **v1.0 MVP** — Phases 1-5 (shipped 2026-05-01)
- 🚧 **v1.1 LiteRT-LM Integration** — Phases 6-10 (planning)
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

### 🚧 v1.1 LiteRT-LM Integration (In Progress)

**Milestone Goal:** Add Google's LiteRT-LM as a second high-performance local inference engine alongside llama.cpp — search, download, import, and chat with `.litertlm` models from Hugging Face's litert-community, with auto-detected GPU/NPU acceleration.

#### Phase 6: Engine Foundation
**Goal:** Establish the LiteRT-LM engine with backend auto-detection, thread-safe lifecycle management, mutual exclusion with llama.cpp, and Room schema support — the foundation everything else depends on.
**Depends on:** Phase 5 (v1.0)
**Requirements:** LITE-01, LITE-02, LITE-03, LITE-04, LITE-08, POL-01, POL-02
**Success Criteria** (what must be TRUE):
  1. App compiles with litertlm-android Maven dependency without Gradle errors
  2. BackendDetector correctly probes GPU availability and falls back to CPU on devices without OpenCL
  3. LiteRTLmEngine initializes with a `.litertlm` model path and closes cleanly without native crashes or memory leaks
  4. EngineManager enforces mutual exclusion — only one local model engine (llama.cpp or LiteRT-LM) loaded at a time, unloading the current engine when switching
  5. User receives a clear warning before loading a `.litertlm` model that exceeds 80% of available device RAM
**Plans:** 4/4 plans complete

Plans:
- [x] 06-01-PLAN.md — Build integration: Maven dependency, ProGuard rules, AndroidManifest declarations (LITE-01, LITE-08)
- [x] 06-02-PLAN.md — Room schema v6→v7 migration adding model_format column (LITE-04)
- [x] 06-03-PLAN.md — BackendDetector GPU probing + LiteRTLmEngine wrapper + Hilt wiring (LITE-02, LITE-03)
- [x] 06-04-PLAN.md — EngineManager mutual exclusion + MemoryChecker .litertlm RAM warnings (POL-01, POL-02)

#### Phase 7: Provider Integration & Chat
**Goal:** Wire LiteRT-LM into the existing chat architecture — users can select a `.litertlm` model and chat with streaming responses, with input sanitization, parameter mapping, and graceful error recovery.
**Depends on:** Phase 6
**Requirements:** LITE-05, LITE-06, LITE-07, POL-04
**Success Criteria** (what must be TRUE):
  1. User can select a LiteRT-LM model from the chat model selector and stream tokens in real-time with latency comparable to GGUF models
  2. Input containing LaTeX, Unicode math, or special characters is sanitized before reaching the engine and does not crash the app
  3. Generation parameters (temperature, topK, topP, seed) map correctly to LiteRT-LM's SamplerConfig and visibly affect the output
   4. If the engine enters an "Engine not alive" state mid-chat, the app automatically reinitializes and recovers without requiring a manual restart
**Plans:** 1/2 plans executed

Plans:
- [x] 07-01-PLAN.md — Core provider components: ProviderType.LITE_RT_LM, InputSanitizer, LiteRTLmProvider (LITE-05, LITE-06, LITE-07, POL-04)
- [x] 07-02-PLAN.md — Hilt + ProviderRouter wiring: InferenceModule registration, LITE_RT_LM routing (LITE-05)

#### Phase 8: Model Acquisition
**Goal:** Users can discover, download, and import `.litertlm` models from Hugging Face's litert-community — extending the existing download infrastructure.
**Depends on:** Phase 6 (engine foundation), can partially overlap with Phase 7
**Requirements:** ACQ-06, ACQ-07, ACQ-08, ACQ-09, ACQ-10
**Success Criteria** (what must be TRUE):
  1. User can search for `.litertlm` models from the litert-community and see only text-capable models (filtered from vision/speech)
  2. User can view model details including file size and format info before downloading
  3. User sees a foreground progress notification during `.litertlm` downloads, and downloads continue if the app is backgrounded
  4. User can pause an in-progress `.litertlm` download, close the app, return, and resume without data loss
  5. User can import a local `.litertlm` file from device storage via the system file picker
**Plans:** 3/3 executed, 2 gap closure pending

Plans:
- [x] 08-01-PLAN.md — Domain model: add modelFormat to LocalModel + update mappers (ACQ-06, ACQ-07, ACQ-08, ACQ-09, ACQ-10)
- [x] 08-02-PLAN.md — HF API: searchByFormat in repository layer for litertlm filtering (ACQ-06, ACQ-07)
- [x] 08-03-PLAN.md — Pipeline: download format detection, import .litertlm, ViewModel format awareness (ACQ-06, ACQ-07, ACQ-08, ACQ-09, ACQ-10)
- [x] 08-GAP-01-PLAN.md — Foreground download notifications + persistent pause/resume via WorkManager (ACQ-08, ACQ-09)
- [x] 08-GAP-02-PLAN.md — Text-only model filtering via pipelineTag exclusion (ACQ-06)

#### Phase 9: UI Integration
**Goal:** Make LiteRT-LM fully user-facing with separate ecosystem tabs, format badges, backend status indicators, cached loading, and lifecycle-aware memory management.
**Depends on:** Phase 6, Phase 7, Phase 8
**Requirements:** UI-01, UI-02, UI-03, UI-04, UI-05, POL-03, POL-05
**Success Criteria** (what must be TRUE):
  1. User sees separate "GGUF" and "LiteRT-LM" tabs on the Models screen, each showing only models of that format
  2. Each model card in the list displays a format badge ("GGUF" or "LiteRT-LM") to visually distinguish ecosystems
  3. During chat with a LiteRT-LM model, the active backend ("CPU" or "GPU") is displayed on the chat screen
  4. User can view, manage, and delete downloaded LiteRT-LM models from the Models screen
  5. When the app is backgrounded, LiteRT-LM model memory is released, and models reload efficiently from cache on next use
**Plans**: TBD
**UI hint**: yes

#### Phase 10: Parameters & Polish
**Goal:** Complete the parameter experience for LiteRT-LM with engine-aware UI controls, preset unification, and unsupported parameter handling.
**Depends on:** Phase 9
**Requirements:** PARM-03, PARM-04, PARM-05
**Success Criteria** (what must be TRUE):
  1. User can adjust LiteRT-LM specific parameters (temperature, topK, topP, seed) via sliders in the parameters screen
  2. Parameters unsupported by LiteRT-LM (repeat_penalty, context_size, threads) are visibly greyed out and non-interactive when a LiteRT-LM model is selected
  3. User can save a preset with LiteRT-LM parameters and load it for reuse across chat sessions
  4. Presets created for GGUF models show GGUF-specific params, and presets created for LiteRT-LM show LiteRT-LM-specific params — both stored and restored correctly
**Plans**: TBD
**UI hint**: yes

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Foundation & Remote Chat | v1.0 | 3/3 | Complete | 2026-04-30 |
| 2. Local Inference | v1.0 | 3/3 | Complete | 2026-05-01 |
| 3. Model Acquisition | v1.0 | 2/2 | Complete | 2026-05-01 |
| 4. Parameters & Presets | v1.0 | 2/2 | Complete | 2026-05-01 |
| 5. Security Hardening & Polish | v1.0 | 1/1 | Complete | 2026-05-01 |
| 6. Engine Foundation | v1.1 | 4/4 | Complete   | 2026-05-02 |
| 7. Provider Integration & Chat | v1.1 | 1/2 | In Progress|  |
| 8. Model Acquisition | v1.1 | 3/3 + 2 GAP | In Progress|  |
| 9. UI Integration | v1.1 | 0/0 | Not started | — |
| 10. Parameters & Polish | v1.1 | 0/0 | Not started | — |

## Coverage

| Milestone | Requirements | Mapped | Unmapped |
|-----------|-------------|--------|----------|
| v1.0 | 30 | 30 ✓ | 0 |
| v1.1 | 26 | 26 ✓ | 0 |

All v1.1 requirements are traced to exactly one phase in the [Traceability table](REQUIREMENTS.md#v11-traceability).

---

*Roadmap updated: 2026-05-02*
