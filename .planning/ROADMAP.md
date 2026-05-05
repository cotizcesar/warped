# Roadmap: Warped

**Created:** 2026-04-30
**Updated:** 2026-05-05 (v1.2 phases added)
**Granularity:** Coarse (3-5 phases per milestone)

## Milestones

- ✅ **v1.0 MVP** — Phases 1-5 (shipped 2026-05-01)
- ✅ **v1.1 LiteRT-LM Integration** — Phases 6-10 (shipped 2026-05-02) → [archive](milestones/v1.1-ROADMAP.md)
- 📋 **v1.2 GGUF Native Inference** — Phases 11-15 (roadmapped 2026-05-05)

## Phases

- [x] **Phase 1-5: v1.0 MVP** — Remote chat, local GGUF inference, Hugging Face downloads, parameters, security (SHIPPED)
- [x] **Phase 6-10: v1.1 LiteRT-LM** — LiteRT-LM engine, .litertlm model acquisition, UI integration, parameter support (SHIPPED)
- [ ] **Phase 11: Native Foundation** — llama.cpp CMake build, Hugging Face GGUF browsing/download with validation
- [ ] **Phase 12: Model Loading & Memory Foundation** — JNI model loading with progress, metadata display, pre-load RAM checks, memory pressure handling
- [ ] **Phase 13: Inference Core & Thread Safety** — Streaming GGUF chat, stop/cancel, TPS display, generation parameters, thread-safe pipeline
- [ ] **Phase 14: Vulkan GPU Backend** — Vulkan GPU acceleration with automatic CPU fallback, backend display
- [ ] **Phase 15: Cross-Engine UX Parity** — Unified metadata display, TPS, stop button, RAM recommendations, model management across all engines

---

## Phase Details

<details>
<summary>✅ v1.0 MVP (Phases 1-5) — SHIPPED 2026-05-01</summary>

See [milestone archive](milestones/v1.0-ROADMAP.md) for full phase details.

</details>

<details>
<summary>✅ v1.1 LiteRT-LM Integration (Phases 6-10) — SHIPPED 2026-05-02</summary>

See [milestone archive](milestones/v1.1-ROADMAP.md) for full phase details.

</details>

### Phase 11: Native Foundation — CMake Build, Hugging Face GGUF Pipeline & Validation

**Goal:** llama.cpp compiles for Android arm64-v8a/x86_64 and ProGuard rules are in place. Users can browse .gguf files on Hugging Face with per-file quantization metadata and RAM estimates, download .gguf models with foreground progress + pause/resume, and every downloaded file passes automatic integrity validation.

**Depends on:** Nothing within v1.2 (foundation phase)

**Requirements:** NTVL-01, NTVL-05, HFDL-01, HFDL-02, HFDL-03, HFDL-04, MEMS-04

**Success Criteria** (what must be TRUE when this phase completes):

1. User can search Hugging Face for models, tap a model, and see a list of available .gguf files with file name, size, and quantization type (e.g., Q4_K_M, Q5_K_M, Q8_0) parsed from the filename
2. User can see an estimated RAM requirement (file_size × 1.3) displayed next to each .gguf file before initiating a download
3. User can download a .gguf file with a foreground notification showing byte progress percentage, pause the download, close the app, return later, and resume from the same offset without data loss
4. Downloaded .gguf files are automatically validated (magic number + header offset integrity) on completion — corrupted files display a clear error message and are not added to the model list
5. llama.cpp compiles from source via CMake+NDK for arm64-v8a and x86_64, producing libwarped_llama.so with real llama.cpp symbols; ProGuard/R8 keep rules preserve all JNI callback methods for release builds

**Plans:** TBD
**UI hint:** yes

---

### Phase 12: Model Loading & Memory Foundation

**Goal:** Users can load validated GGUF models through the JNI bridge, see real-time loading progress, view rich model metadata after successful load, and receive clear pre-load memory warnings. The app handles Android memory pressure by automatically unloading models to prevent process death.

**Depends on:** Phase 11 (libwarped_llama.so must compile, GGUF files must be downloadable and validated)

**Requirements:** NTVL-02, NTVL-03, NTVL-04, MEMS-01, MEMS-02

**Success Criteria** (what must be TRUE when this phase completes):

1. User can select a downloaded .gguf model and see a loading progress indicator during initialization — large models show visible progress as they mmap into memory
2. After a GGUF model loads successfully, user can view its metadata: architecture (e.g., "llama", "mistral"), parameter count (e.g., "7.2B"), context size, quantization type, and file size — displayed on the model detail screen
3. Before loading, the app checks available RAM against the estimated requirement (file_size × 1.3 for KV cache) and warns the user with a specific message: "This model needs ~5.8 GB, your device has 4.2 GB available. Loading may cause instability."
4. When Android signals critical memory pressure (onTrimMemory), the active GGUF model unloads automatically to prevent the OS from killing the app process
5. JNI nativeLoadModel returns descriptive, user-facing error messages for each failure case: "Out of memory — try a smaller quantization", "Corrupted model file — please re-download", and "Unsupported architecture — this model requires ARM64"

**Plans:** TBD
**UI hint:** yes

---

### Phase 13: Inference Core & Thread Safety

**Goal:** Users can chat with loaded GGUF models with streaming token-by-token responses that appear in the chat UI at the same speed as LiteRT-LM, stop mid-generation with a cancel button, see real-time tokens-per-second, and configure all 8 generation parameters. The entire pipeline — from native token generation through Kotlin callbackFlow to UI — is thread-safe under concurrent use, rapid stop/unload sequences, and backgrounding during generation.

**Depends on:** Phase 12 (model must load and metadata must be available before inference can run)

**Requirements:** INFR-01, INFR-02, INFR-03, INFR-04, MEMS-03, MEMS-05, MEMS-06

**Success Criteria** (what must be TRUE when this phase completes):

1. User can type a message with a loaded GGUF model and see tokens stream into the chat UI within 2 seconds of sending — the same UI widget that works for LiteRT-LM and remote providers
2. User can tap the stop button during GGUF generation; the partial response text is preserved in the conversation, the model remains loaded and ready for the next message, and there is no crash, ANR, or memory leak
3. User sees a real-time tokens-per-second counter during GGUF generation, updating at least once per second, displayed in the chat header
4. User can adjust all 8 generation parameters — temperature, top_p, top_k, repeat_penalty, max_tokens, context_size, seed, threads — via the existing presets panel and see each change affect the next generation output
5. User can rapidly stop-then-unload, send concurrent messages (which are serialized), and background the app mid-generation without any SIGSEGV, data race, or "Engine not alive" error — verified by stress-tests of 50+ rapid stop/unload/reload cycles

**Plans:** TBD
**UI hint:** yes

---

### Phase 14: Vulkan GPU Backend

**Goal:** llama.cpp supports Vulkan GPU acceleration compiled alongside the CPU backend, with automatic runtime detection and silent CPU fallback on unsupported devices. Users see the active backend ("Running on Vulkan GPU" / "Running on CPU") during chat, matching the existing LiteRT-LM backend indicator pattern.

**Depends on:** Phase 13 (CPU inference must be stable before adding GPU complexity; GPU debugging with untested memory management multiplies risk)

**Requirements:** BACK-01, BACK-02, BACK-03

**Success Criteria** (what must be TRUE when this phase completes):

1. llama.cpp compiles with GGML_VULKAN=ON for arm64-v8a, producing a Vulkan-capable .so variant that loads and runs GGUF inference on devices with Vulkan 1.1+ support
2. On devices with Vulkan drivers (e.g., Snapdragon 8 Gen 2+ with Adreno 7xx), GGUF inference automatically uses GPU acceleration and runs 2-4× faster than CPU — user observes faster token generation without any configuration
3. On devices without Vulkan support or where Vulkan initialization fails, the app silently falls back to CPU inference — no error dialogs, no manual intervention required, chat continues normally
4. User sees the active backend label ("Running on Vulkan GPU" or "Running on CPU") during GGUF chat, using the same UI pattern already established for LiteRT-LM's backend display

**Plans:** TBD
**UI hint:** yes

---

### Phase 15: Cross-Engine UX Parity

**Goal:** The GGUF experience is indistinguishable from LiteRT-LM and remote providers in metadata display, tokens-per-second visualization, stop button behavior, RAM recommendations, and model file management. Users experience a seamless, unified app — they choose a model, the engine is transparent.

**Depends on:** Phase 13 (inference must work), Phase 14 (Vulkan backend indicator needed for parity). Can overlap with Phase 14.

**Requirements:** UXMT-01, UXMT-02, UXMT-03, UXMT-04, UXMT-05

**Success Criteria** (what must be TRUE when this phase completes):

1. User viewing model details for any format — GGUF, LiteRT-LM, or remote — sees the same metadata fields where information is available: architecture, parameter count, context size, quantization, license, and tokenizer info, all using the same UI layout
2. Tokens-per-second real-time display works identically for GGUF, LiteRT-LM, and streaming remote providers — same UI widget position, same update frequency (~1 Hz), same visual treatment
3. Stop generation button behaves consistently across all engine types: partial response preserved in conversation, engine returns to ready state, no crashes or stale states — user experience is the same regardless of which engine is behind the chat
4. RAM recommendation badges show device-appropriate guidance for both GGUF (e.g., "Q5_K_M — tight on your 8 GB device") and LiteRT-LM models (e.g., "1.8 GB — fits comfortably"), using the same color-coded badge system
5. Model file management — viewing all downloaded models, seeing per-model details, and deleting models — works uniformly for GGUF and LiteRT-LM formats from the same Models screen, with consistent delete confirmation dialogs and immediate list refresh

**Plans:** TBD
**UI hint:** yes

---

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
| 11. Native Foundation | v1.2 | 0/? | Not started | - |
| 12. Model Loading & Memory | v1.2 | 0/? | Not started | - |
| 13. Inference Core & Thread Safety | v1.2 | 0/? | Not started | - |
| 14. Vulkan GPU Backend | v1.2 | 0/? | Not started | - |
| 15. Cross-Engine UX Parity | v1.2 | 0/? | Not started | - |

## Coverage

| Milestone | Requirements | Mapped | Unmapped |
|-----------|-------------|--------|----------|
| v1.0 | 30 | 30 ✓ | 0 |
| v1.1 | 26 | 26 ✓ | 0 |
| v1.2 | 27 | 27 ✓ | 0 |

All v1.2 requirements are traced to exactly one phase in the [Traceability table](REQUIREMENTS.md#v12-traceability).

---

*Roadmap updated: 2026-05-05*
