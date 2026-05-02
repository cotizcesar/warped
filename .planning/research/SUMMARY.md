# Project Research Summary

**Project:** Warped v1.1 — LiteRT-LM Integration
**Domain:** Android LLM client — second local inference engine addition
**Researched:** 2026-05-02
**Confidence:** HIGH

## Executive Summary

Warped is an Android app equivalent to LM Studio for mobile, already shipping with llama.cpp/GGUF local inference and remote provider support. This v1.1 milestone adds **LiteRT-LM** (Google's on-device LLM runtime) as a second local inference engine, giving users access to Google-optimized `.litertlm` models from the litert-community organization on Hugging Face. The integration is a **parallel provider pattern** — not a replacement or refactor of the existing llama.cpp path — with both engines coexisting under a unified `LlmProvider` interface.

The recommended approach is to integrate LiteRT-LM via a single Maven dependency (`com.google.ai.edge.litertlm:litertlm-android:0.10.2`) — no NDK/CMake build required, unlike llama.cpp. LiteRT-LM ships pre-built `.so` libraries inside the AAR with a pure Kotlin API surface (`Engine`, `Conversation`, `SamplerConfig`). The key architectural adaptation is mapping LiteRT-LM's **stateful** Engine→Conversation lifecycle onto the existing **stateless** `LlmProvider.chat()` contract by creating a fresh `Conversation` per chat call while keeping the `Engine` singleton alive.

**Critical risks** center on device-specific SoC issues: (1) GPU backend silently crashes on Tensor G3/Pixel 8 due to missing OpenCL — must use runtime backend probing with CPU fallback, (2) Conversation reuse causes SIGSEGV on MediaTek Dimensity — architect for fresh-per-message conversations, (3) Unicode/LaTeX input triggers ICU `RegexMatcher` native crash — input sanitization or engine fallback routing required, and (4) dual-engine memory exhaustion if both llama.cpp and LiteRT-LM load simultaneously — enforce mutual exclusion via an `EngineManager`. Every one of these is preventable with upfront architecture decisions, not runtime patches.

## Key Findings

### Recommended Stack Additions

The existing v1.0 stack (Kotlin 2.1.10, Jetpack Compose BOM 2025.04.00, Hilt 2.59.2, Room 2.7.x, OkHttp 4.12.0, Retrofit 2.11.1, llama.cpp via JNI) remains unchanged. LiteRT-LM adds exactly **one dependency**:

**New core technologies:**
- **`com.google.ai.edge.litertlm:litertlm-android:0.10.2`**: Pre-built AAR from Google Maven containing the full LiteRT runtime with native `.so` libraries. Provides `Engine` (model lifecycle), `Conversation` (stateful chat with auto-templating), `SamplerConfig` (temperature/topP/topK/seed), and backends (CPU/GPU/NPU). Zero CMake/NDK setup — `implementation()` dependency only.
- **`<uses-native-library android:name="libOpenCL.so" android:required="false"/>`**: AndroidManifest additions required for GPU backend. Marked `required="false"` so devices without OpenCL (Tensor G3, some Exynos) can still install and fall back to CPU.
- **Hugging Face `litert-community` org**: 93+ pre-converted `.litertlm` models (Gemma, Llama, Phi, Qwen). Same HF Hub REST API as GGUF — no new HTTP client needed. Filter by `author=litert-community` plus client-side `.litertlm` extension filtering.
- **Room schema migration**: Add `model_format TEXT NOT NULL DEFAULT 'GGUF'` and `engine_type` columns. Make `quantization` nullable (meaningless for `.litertlm`). New `:library:litertlm` module for engine wrapper + backend detection.

### Expected Features

**Must have (table stakes for v1.1 launch):**
- **Search `.litertlm` models on Hugging Face** — Extends `HuggingFaceApi` with litert-community search mode. Client-side `.litertlm` extension filtering to exclude vision/speech models.
- **Download `.litertlm` models with progress and pause/resume** — Reuses existing `ModelDownloadManager` with `.litertlm` extension handling. Same OkHttp `Range` header resume pattern.
- **Load and chat with streaming** — Core value proposition. New `LiteRTLmProvider` implementing `LlmProvider`. Wraps `Engine` → `Conversation` → `sendMessageAsync()` which returns `Flow<Message>` natively (no callbackFlow wrapper needed unlike llama.cpp).
- **Auto-detect GPU with CPU fallback** — Runtime backend probing via `BackendDetector`. Try GPU first, catch `LiteRtLmJniException`, fall back to CPU. Surface active backend in UI.
- **Separate GGUF / LiteRT-LM UI tabs in Models screen** — Compose `TabRow` with `ModelFormat` filter. Prevents user confusion between the two ecosystems.

**Should have (v1.1.x polish):**
- Import local `.litertlm` files — Reuses `ActivityResultContracts.OpenDocument` file picker.
- LiteRT-LM generation parameters UI — Separate `LiteRtParameters` model, sliders for temperature/topK/topP.
- Model management (view/delete) — Reuses existing list/view/delete flow with `ModelFormat` discriminator.
- Cached model loading — Set `EngineConfig.cacheDir` for ~10x faster subsequent loads.

**Defer to v2.0+:**
- Multi-modality (vision/audio) — Requires separate backends, new UI, larger scope.
- Tool use / function calling — Different interaction paradigm, requires agent architecture.
- NPU auto-detection — SoC-fragmented, needs per-device testing. Expose as manual toggle in v1.1.
- Converting GGUF → `.litertlm` on-device — Computationally infeasible on mobile.
- Running both engines simultaneously — Memory exhaustion on typical 8GB devices.

### Architecture Approach

LiteRT-LM integrates as a **second `LlmProvider` implementation** under the existing provider polymorphism pattern. The domain layer gets exactly one new enum value (`ProviderType.LITERT_LM`). The UI layer is completely decoupled — `ChatScreen` and `ChatViewModel` never know which engine is running. The key adaptation is mapping LiteRT-LM's stateful `Engine → Conversation` lifecycle onto the existing stateless `chat(ChatRequest): Flow<StreamToken>` contract by creating a **fresh `Conversation` per `chat()` call** with `ConversationConfig.initialMessages` seeded from `ChatRequest.messages`.

**Major components (new or modified):**
1. **`:library:litertlm` module** — `LiteRTLmEngine` (Engine lifecycle wrapper with Mutex-serialized init/close), `BackendDetector` (GPU/NPU capability probing with CPU fallback chain), `LiteRTLmEngineConfig` (engine-scoped params: maxTokens, threads, cacheDir).
2. **`LiteRTLmProvider`** — Implements `LlmProvider`, wraps `LiteRTLmEngine`. Per-call Conversation factory pattern. Maps `GenerationParameters → SamplerConfig`. Runs chat on `Dispatchers.Default`.
3. **`ProviderRouter`** — Add `ProviderType.LITERT_LM → liteRTLmProvider` case. Same routing mechanism as all other providers.
4. **`HuggingFaceApi`** — Add `searchLiteRTLmModels()` method targeting `author=litert-community` with client-side `.litertlm` extension filtering.
5. **`ModelsScreen`** — Add Compose `TabRow`: "GGUF" | "LiteRT-LM", each filtering `LocalModel` by `ModelFormat`.
6. **Room schema** — Migration adding `model_format` and `engine_type` columns, nullable `quantization`.

### Critical Pitfalls

1. **GPU backend silently crashes on Tensor G3/Pixel 8** — `Backend.GPU()` constructs without error but inference crashes with "Can not find OpenCL library" at message-send time. **Prevent by:** Building a `BackendDetector` that probes `System.loadLibrary("OpenCL")` and attempts a minimal GPU init with try/catch + 5-second timeout before ever exposing GPU to the user. Cache result per session.

2. **Conversation SIGSEGV on second `sendMessage()` (MediaTek Dimensity)** — Native conversation state invalidates after first use on Dimensity SoCs. Second `sendMessageAsync` crashes with `SIGSEGV (SEGV_MAPERR)` at `nativeSendMessage`. **Prevent by:** Always creating a fresh `Conversation` per message exchange. Never reuse. This also happens to match the official `.use {}` block pattern from the getting-started guide.

3. **Unicode/LaTeX input crashes via ICU `RegexMatcher`** — Input containing LaTeX (`$x^2$`), Unicode math (`∫`, `∑`), or non-Latin scripts triggers SIGSEGV in `libicui18n.so` at `RegexMatcher::find()`. 100% reproducible on all API surfaces. **Prevent by:** Input sanitization layer (strip/escape regex metacharacters) OR route LaTeX-heavy conversations to llama.cpp which handles arbitrary Unicode.

4. **Dual-engine memory contention** — Loading a 7B GGUF (~4.3GB) + Gemma 4 E4B `.litertlm` (~4GB) simultaneously exceeds ~8GB available on typical 12GB devices. LowMemoryKiller kills the process silently. **Prevent by:** `EngineManager` enforcing mutual exclusion — only one local model loaded at a time. Unload current engine before loading a different one. Pre-load memory check: `availMem < model_size × 1.5` shows warning.

5. **Parameter mapping mismatch** — `GenerationParameters` has fields LiteRT-LM doesn't support (`repeatPenalty`, `contextSize`, `threads` at per-request level). Blindly passing all params silently drops unsupported ones. **Prevent by:** Explicit `GenerationParameters.toLiteRTSamplerConfig()` extension with documented mapping table. Grey out unsupported params in UI when LiteRT-LM model is selected.

## Implications for Roadmap

Based on combined research, the LiteRT-LM integration naturally breaks into **5 sequential phases**, with Phase 1 being the foundation everything else depends on and Phase 5 being polish that can ship post-launch.

### Phase 1: Engine Foundation (deepest dependency, zero UI)

**Rationale:** Everything depends on being able to load a `.litertlm` model and produce tokens. This phase establishes the `:library:litertlm` module, the `LiteRTLmEngine` lifecycle wrapper, backend auto-detection with CPU fallback, and the Gradle dependency. No UI changes — testable with a tiny model (Gemma-3N-1B, ~500MB) via unit tests. Must address pitfalls #1 (GPU probe), #4 (memory mutual exclusion via EngineManager design), #7 (engine lifecycle strategy), and #10 (background dispatch for `initialize()`).

**Delivers:** Unit test proving `LiteRTLmEngine.initialize(path)` → `createConversation().sendMessageAsync()` returns tokens. BackendDetector correctly identifies GPU availability and falls back to CPU. Room schema migration passes on DB with 10+ existing GGUF records.

**Features from FEATURES.md:** Infrastructure for "Load and chat with streaming" and "Auto-detect GPU with CPU fallback" (no user-facing UI yet).

**Avoids:** Pitfall #1 (GPU silent failure — probe built day one), Pitfall #4 (memory contention — EngineManager designed before both engines coexist), Pitfall #9 (Room migration — tested on real data), Pitfall #10 (init blocking — `withContext(Dispatchers.Default)` enforced).

### Phase 2: Provider Integration + Chat Streaming (domain → data wiring)

**Rationale:** Wires the engine into the existing architecture. Maps the stateful Conversation API to the stateless `LlmProvider.chat()` contract via per-call Conversation factory. Adds `ProviderType.LITERT_LM` to domain layer. Implements `LiteRTLmProvider` with parameter mapping from `GenerationParameters → SamplerConfig`. This is where Pitfall #2 (single-use Conversation), #5 (thread safety via Mutex), and #3 (unicode/LaTeX fallback routing) must be addressed.

**Delivers:** Integration test proving end-to-end chat pipeline works (Endpoint with LITERT_LM type → chat → tokens stream back). Parameter mapping table tested for all edge values. Input sanitization layer intercepting LaTeX/Unicode before reaching the engine.

**Features from FEATURES.md:** "Load and chat with streaming" (complete). Foundation for "Generation parameters for LiteRT-LM."

**Uses from STACK.md:** `litertlm-android:0.10.2` via version catalog. `Engine.initialize()` on `Dispatchers.Default`. `Conversation.sendMessageAsync()` → `Flow<Message>` integration.

**Avoids:** Pitfall #2 (Conversation reuse SIGSEGV — architect per-call Conversation), Pitfall #3 (Unicode crash — sanitization before sendMessageAsync), Pitfall #5 (thread safety — Mutex on Engine, sequential Conversation access), Pitfall #6 (parameter mapping — explicit extension function).

### Phase 3: Model Acquisition (download + management)

**Rationale:** Users need models on device to use the engine. Reuses existing download infrastructure (WorkManager + OkHttp + foreground notifications) with different HF endpoint and file extension. This phase requires Pitfall #8 (HF API divergence — litert-community search filter) and the client-side `.litertlm` extension filtering.

**Delivers:** User can search litert-community, see only LLM-capable `.litertlm` models (excludes vision/speech), download with progress and pause/resume, and see downloaded models in their library with format badges.

**Features from FEATURES.md:** "Search .litertlm models on HF" and "Download .litertlm with progress."

**Implements:** `HuggingFaceApi.searchLiteRTLmModels()`, `ModelDownloadManager` `.litertlm` extension handling, `ModelRepository` format-aware queries.

**Avoids:** Pitfall #8 (HF API divergence — separate search endpoint, client-side file extension filtering), Pitfall #9 (Room queries filtering by GGUF-specific fields without format check).

### Phase 4: UI Integration (tabs, settings, user-facing UX)

**Rationale:** UI is the thin layer on top. By this point, the engine works, the provider works, models can be acquired. This phase makes it user-facing with separate GGUF/LiteRT-LM tabs, backend preference settings, and format badges. The `ChatScreen` needs zero changes — it's provider-agnostic by design.

**Delivers:** Full user flow: discover model in LiteRT-LM tab → download → select → chat → with visible backend badge ("GPU" / "CPU"). Settings screen with backend preference (Auto/GPU/CPU). Greyed-out unsupported parameters for LiteRT-LM models.

**Features from FEATURES.md:** "Separate GGUF/LiteRT-LM UI tabs" and "Backend selection with auto-detection" (completed UI). "Generation parameters for LiteRT-LM" (parameter UI adapts to engine).

**Avoids:** All UX pitfalls — mixed model lists without format indicators, showing unsupported parameters as active, no engine type badge in model selector.

### Phase 5: Polish & Edge Cases (ship-quality hardening)

**Rationale:** Ship after core path works end-to-end. Covers backend fallback UX (toasts), error recovery (retry with different backend on init failure), memory management (unload on app background, `onTrimMemory` handling), thermal throttling integration, and import local `.litertlm` files. This phase can ship as v1.1.x after launch.

**Delivers:** Cached model loading (set `cacheDir` in EngineConfig). Import local files via SAF file picker. Defensive error recovery — if `createConversation()` fails with "Engine not alive," close and re-initialize. Thermal monitoring pauses inference on severe status.

**Features from FEATURES.md:** "Import local .litertlm files," "Cached model loading," "Model management (view/delete)."

**Avoids:** Pitfall #7 (conversation lifecycle mismanagement — defensive recreate on "Engine not alive" errors), performance traps (engine creation on every chat message — keep engine alive; large downloads without storage check).

### Phase Ordering Rationale

- **Phase 1 must come first** — the engine library, backend detection, and Room schema are prerequisites for everything else. Testable in isolation.
- **Phase 2 must come before Phase 4** — the provider needs to work before UI can be wired to it. Chat streaming is the value proposition; everything else supports it.
- **Phase 3 can partially overlap with Phase 2** — model acquisition (download/search) is independent of chat once the engine exists, but needs the `ModelFormat` concept established in Phase 2.
- **Phase 4 is intentionally last for significant work** — it's the thinnest layer but depends on all three prior phases being complete.
- **Phase 5 is polish** — can ship after initial v1.1 launch without blocking anything.

### Research Flags

**Phases likely needing `/gsd-research-phase` during planning:**
- **Phase 1 (Engine Foundation):** GPU backend detection across SoCs is hardware-dependent. The `BackendDetector` probe strategy may need device-specific adjustments. The `.litertlm` file format is new — confirm header parsing for metadata extraction.
- **Phase 3 (Model Acquisition):** litert-community HF API filter behavior needs live testing. The `author=litert-community` query may return models without `.litertlm` files — client-side filtering logic needs validation with real API responses.

**Phases with well-documented patterns (skip research-phase):**
- **Phase 2 (Provider Integration):** Standard provider pattern already established for llama.cpp and remote providers. Parameter mapping is a straightforward extension function.
- **Phase 4 (UI Integration):** Standard Compose TabRow + filtered lists. Well-established Material 3 patterns.
- **Phase 5 (Polish):** Error handling, thermal monitoring, memory management are Android platform patterns with official documentation.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | **HIGH** | LiteRT-LM v0.10.2 confirmed via Google Maven metadata XML. API surface verified against official source code (Engine.kt, Conversation.kt, Config.kt). Maven dependency coordinates confirmed. |
| Features | **HIGH** | Feature set validated against existing Warped codebase (LlmProvider, LocalModel, ModelDownloadManager, HuggingFaceApi). litert-community org verified with 93+ models. Competitor analysis confirms dual-engine approach is unique among Android LLM apps. |
| Architecture | **HIGH** | Integration pattern verified against existing Warped architecture and LiteRT-LM source code. Per-call Conversation factory pattern tested conceptually. ProviderRouter extension is trivial. Build order derived from component dependencies. |
| Pitfalls | **HIGH** | All 10 critical pitfalls backed by confirmed GitHub issues with reproduction steps. GPU crash (#1860), Conversation SIGSEGV (#1849), Unicode crash (#1616), and others verified on specific devices/SoCs. Prevention strategies validated against official docs. |
| GPT/Roadmap Fit | **MEDIUM** | Phase structure is well-derived from dependencies, but exact phase boundaries (how many waves per phase, whether acquisition and chat belong together) should be refined during planning based on team velocity and testing hardware availability. |

**Overall confidence:** **HIGH** — All major areas verified against official sources (Google Maven, LiteRT-LM GitHub, Hugging Face API, existing Warped codebase). Remaining MEDIUM-confidence items are execution details that require live device testing (GPU probe on Tensor G3, MediaTek stability, APK size measurement) rather than architecture questions.

### Gaps to Address

- **LiteRT-LM v0.10.2 Unicode/LaTeX bug status:** The ICU crash was confirmed in v0.9.0-alpha06 through v0.10.0. Need to verify whether v0.10.2 (Apr 14, 2026 release) fixes it. If not, input sanitization is mandatory from Phase 2. Test with `$x^2$` input against v0.10.2 before freezing the dependency.
- **`.litertlm` file header metadata extraction:** Can we extract parameter count, quantization, and architecture from the file header before full load? Similar to GGUF header parsing. LiteRT-LM's `Capabilities` class may provide this but API surface needs verification. If not available, metadata must come from Hugging Face model cards.
- **MediaTek Dimensity testing hardware:** Pitfalls #2, #5, #7 are all MediaTek-specific. Without a physical Dimensity device (OnePlus CPH2609, iQOO I2407, OPPO CPH2717), these must be tested via Firebase Test Lab or assumed from GitHub issue reports.
- **NPU driver bundling strategy:** To use `Backend.NPU()`, the app must bundle NPU native libraries or download them. Is the NPU driver available on Google Play Services, or must it be bundled? This decision affects APK size (potentially +50-100MB for multiple NPU drivers) and should be resolved before v1.3 NPU planning.
- **APK size impact measurement:** `litertlm-android` AAR adds ~10-20 MB per ABI. Combined with existing llama.cpp `.so` files, total APK impact needs measurement before release to avoid Play Store size limit issues (200MB APK, 2GB AAB with Play Asset Delivery).
- **Open questions from Architecture Appendix B** (10 items): Context window mapping (`maxNumTokens` = input+output vs our output-only `maxTokens`), repeat penalty availability, multi-turn performance with per-call Conversation, NPU library bundling, offline model auth requirements, and model metadata extraction. These are planning-phase decisions, not research gaps.

## Sources

### Primary (HIGH confidence)
- **LiteRT-LM GitHub (google-ai-edge/LiteRT-LM):** Official source code (`Engine.kt`, `Config.kt`, `Conversation.kt`, `Session.kt`, `Capabilities.kt`) — v0.10.2 API surface verified. 4.6k stars.
- **Google Maven metadata:** `https://dl.google.com/dl/android/maven2/com/google/ai/edge/litertlm/litertlm-android/maven-metadata.xml` — confirmed v0.10.2 as latest stable, v0.11.0-rc1 as pre-release.
- **LiteRT-LM Kotlin Getting Started Guide:** Official Android integration docs — confirmed `Engine`, `Conversation`, `sendMessageAsync()` patterns.
- **Hugging Face litert-community org:** `https://huggingface.co/litert-community` — 93 models, 6,277 followers. Verified Gemma 4 E2B/E4B, Llama 3.2, Phi-4, Qwen 2.5 availability.
- **Existing Warped codebase:** `LlmProvider`, `LocalModel`, `ModelDownloadManager`, `HuggingFaceApi`, `ProviderRouter`, `ModelsScreen` — mapped all integration points from source.
- **Context7 LiteRT-LM library:** `/google-ai-edge/litert-lm` — official docs cross-reference.

### Secondary (MEDIUM confidence)
- **GitHub Issues (google-ai-edge/LiteRT-LM):** 97 open issues as of 2026-05-02. Key issues: #1860 (GPU silent failure), #1849 (Conversation SIGSEGV on MediaTek), #1616 (ICU Unicode crash), #2028 (SIGSEGV on second createConversation), #1859 (FunctionGemma SIGSEGV), #1681 (GPU misidentification), #1864 (Exynos 2600 init failure).
- **Google AI Edge Gallery App:** Production reference for Android LiteRT-LM integration on Google Play. HIGH confidence for UX patterns but app internals not inspectable.

### Tertiary (LOW confidence)
- **`.litertlm` format specifications:** Internal flatbuffer structure not publicly documented in detail. Memory footprint estimates derived from model descriptions and GitHub issue reports, not benchmarked.
- **NPU backend availability:** Per-SoC NPU driver distribution is fragmented. Decision to defer NPU auto-detection based on community difficulty reports, not exhaustive testing.

---

*Research completed: 2026-05-02*
*Ready for roadmap: yes — phase structure derived from dependencies, pitfalls mapped to phases, research flags identified for deeper investigation.*
