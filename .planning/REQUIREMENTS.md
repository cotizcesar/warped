# Requirements: Warped

**Defined:** 2026-06-05
**Last updated:** 2026-06-05 after milestone v2.0 requirements definition
**Core Value:** Run and chat with any LLM — local or remote — from a single Android app, with a simple LM Studio-grade experience that works offline.

## v2.0 Requirements

Requirements for the Gallery Convergence & Performance Overhaul milestone. Reference: [google-ai-edge/gallery](https://github.com/google-ai-edge/gallery) (v1.0.16, 23.6k stars). Warped is the convergence target; Gallery anti-patterns (kapt, kotlin-reflect, Moshi, Gson, Firebase, Ktor, mlkit-genai, CameraX, TFLite, AppAuth, compose-richtext, Proto DataStore, JS webview skills) are explicitly rejected.

### Runtime & Engine Foundation (keystone)

- [ ] **RUNTIME-01**: Domain layer exposes `LlmModelHelper` interface with `initialize`, `runInference`, `resetConversation`, `cleanUp`, `stopResponse` methods mirroring Gallery's signature
- [ ] **RUNTIME-02**: `LiteRtLlmHelper` implements `LlmModelHelper`, wrapping the existing `LiteRTLmProvider`; `Engine.initialize()` is dispatched to `Dispatchers.IO`
- [ ] **RUNTIME-03**: `LmStudioHelper` implements `LlmModelHelper`, wrapping `LMStudioProvider` with SSE-cancellation-aware `stopResponse` that cancels both the `Flow` and the underlying `Call`
- [ ] **RUNTIME-04**: `ChatViewModel` depends on `LlmModelHelper` via the interface; provider resolution is handled by `ProviderRouter` factory at construction time
- [ ] **RUNTIME-05**: `assets/model_allowlist.json` ships with the app, using the Gallery schema subset: `name`, `displayName`, `modelFile`, `sizeInBytes`, `capabilities`, `llmPromptTemplates`, `taskTypes`
- [ ] **RUNTIME-06**: `ModelAllowlistRepository` reads and parses the asset and exposes capability queries (`supportsThinking(modelId)`, `supportsSpeculativeDecoding(modelId)`)
- [ ] **RUNTIME-07**: Type-safe navigation via `@Serializable` destinations for 7 routes (chat, models, endpoints, settings, huggingface, promptlab, benchmark)
- [ ] **RUNTIME-08**: `AndroidManifest.xml` adds `configChanges="uiMode|orientation|screenSize|smallestScreenSize|screenLayout"`, `extractNativeLibs="false"`, `windowSoftInputMode="adjustResize"`, three `<uses-native-library required="false">` entries (libvndksupport.so, libOpenCL.so, libcdsprpc.so), `largeHeap="true"`, `theme="...SplashScreen"`
- [ ] **RUNTIME-09**: `libs.versions.toml` bumps: compose-bom 2026.04.01→2026.05.01, hilt-navigation-compose 1.2.0→1.3.0, hilt-work 1.2.0→1.3.0, Room 2.7.1→2.8.4, Lifecycle 2.8.7→2.10.0, Navigation 2.8.8→2.9.x, LiteRT-LM 0.13.0→0.13.1; additions: `lifecycle-process:2.10.0`, `core-splashscreen:1.2.0-beta01`, `kotlinx-collections-immutable:0.4.0`
- [ ] **RUNTIME-10**: `gradle.properties` adds `android.nonTransitiveRClass=true`, `-Xmx4g` Gradle JVM args, `kotlin.incremental=true`
- [ ] **RUNTIME-11**: R8 keep rules for `com.google.ai.edge.litertlm.**` JNI, `MessageCallback`, `ToolProvider`, and canonical kotlinx-serialization `$$serializer` companions; R8 full mode enabled in release
- [ ] **RUNTIME-12**: Gradle dependency audit script returns empty for: kapt, firebase, moshi, gson, kotlin-reflect, ktor, mcp, tflite, mlkit-genai, appauth, compose-richtext, cameraX, datastore-proto; CI fails on hit

### Model Cache (mmap-only)

- [ ] **CACHE-01**: `EngineManager` no longer copies the model file before loading; `EngineConfig` receives `cacheDir=context.cacheDir.absolutePath/<version>` (namespaced by `BuildConfig.LITERTLM_VERSION`) for mmap-only caching
- [ ] **CACHE-02**: Cache directory is capped at 500MB with LRU eviction; cap is configurable via `AdvancedPreferences`
- [ ] **CACHE-03**: `EngineManager.handleTrimMemory` evicts the cache when the device enters `TRIM_MEMORY_RUNNING_CRITICAL` or higher

### Thinking Mode

- [ ] **THINK-01**: `LlmModelHelper.runInference` accepts an `enableThinking: Boolean` flag and emits `ResultListener.partialThinkingResult: String?` while the model is reasoning
- [ ] **THINK-02**: `LiteRtLlmHelper` maps `enableThinking` to LiteRT-LM 0.13.1's thinking accessor (exact API verified in phase research)
- [ ] **THINK-03**: `LmStudioHelper` maps `enableThinking` to LM Studio v1's `reasoning_content` (exact JSON path verified in phase research)
- [ ] **THINK-04**: `MessageBubble` renders a collapsible "Thinking" panel above the response when `partialThinkingResult` is present in the stream
- [ ] **THINK-05**: Chat input bar shows a "Thinking ON/OFF" chip; clicking toggles `enableThinking` for the next user message
- [ ] **THINK-06**: Thinking toggle is hidden when the selected model's allowlist entry lacks `llm_thinking` capability
- [ ] **THINK-07**: After streaming completes, the reasoning panel is collapsed by default; the user can tap to expand and read the full thinking trace

### Model Benchmark

- [ ] **BENCH-01**: `ui/benchmark/` package with `BenchmarkScreen`, `BenchmarkViewModel`, `BenchmarkResultsViewer`, `BenchmarkValueSeriesViewer` mirroring Gallery's 4 files
- [ ] **BENCH-02**: `worker/ModelBenchmarkWorker.kt` runs the benchmark via WorkManager with `setForeground()` for long runs and `Constraints(UNMETERED, BATTERY_NOT_LOW)`
- [ ] **BENCH-03**: `BenchmarkRepository` persists results to a Room `BenchmarkResult` table: id, modelId, configHash, initTimeMs, prefillTokPerSec, decodeTokPerSec, peakMemoryBytes, createdAt
- [ ] **BENCH-04**: Room schema migration v1.8 → v2.0 adds the `BenchmarkResult` table via `@AutoMigration(from=v1, to=v2)`; `exportSchema = true`; `MigrationTest` in androidTest/ verifies a 50-message round-trip
- [ ] **BENCH-05**: Benchmark results page shows model name, configuration used, all 4 metrics, timestamp, with sparkline charts where applicable
- [ ] **BENCH-06**: User can pick a model from the downloaded list and a config (temperature, top-k, max tokens), then start a benchmark

### Prompt Lab

- [ ] **PROMPT-01**: `ui/promptlab/` package with `PromptLabScreen`, `PromptLabViewModel`, `PromptTemplateConfigs.kt`
- [ ] **PROMPT-02**: 5-8 curated templates ship with the app (rewrite, summarize, extract-key-points, code-explain, translate, sentiment, table-to-json — final selection at phase planning)
- [ ] **PROMPT-03**: Prompt Lab uses a side-by-side layout: prompt input on left, output on right, single-turn with no conversation state
- [ ] **PROMPT-04**: `PromptLabTaskModule` Hilt module with `@IntoSet` binding per Gallery pattern
- [ ] **PROMPT-05**: Output renders through Warped's existing `MarkdownText` (v1.6) for syntax highlighting parity
- [ ] **PROMPT-06**: Reuses `LlmModelHelper` from Runtime — no new inference plumbing

### Performance Convergence

- [ ] **PERF-01**: `ChatUiState` is split into 3 sub-states: `ChatListState`, `ChatInputState`, `ChatStreamingState`, each annotated `@Immutable`
- [ ] **PERF-02**: `MessageBubble`, `CodeBlock`, `ChatInputBar` adopt `kotlinx-collections-immutable:0.4.0`; all `List<T>` params converted to `ImmutableList<T>` for Compose 1.11 strong-skipping mode
- [ ] **PERF-03**: Subcomposables extracted in `MessageBubble` and `CodeBlock` (565 lines) to limit recomposition scope
- [ ] **PERF-04**: `derivedStateOf` applied to `trafficLightState` and `trafficLightStatusText` derivations
- [ ] **PERF-05**: Hoisted state: `codeTheme`, `codeFontScale`, `attachedImages` lifted to parent where stable
- [ ] **PERF-06**: `LazyColumn` `key()` parameter set to `message.id` for stable identity
- [ ] **PERF-07**: Hilt graph audit: every `@Provides` is either `@Singleton` or `@ViewModelScoped`; no eager `LiteRtLlmEngine` SingletonComponent injection; `ProviderRouter` lazy-resolves
- [ ] **PERF-08**: OkHttp interceptor chain audit: `retryOnConnectionFailure(false)` for SSE, `callTimeout(60s)`, `Cache(50MB)` TTL
- [ ] **PERF-09**: Room composite index on `messages(conversation_id, created_at)`; verified via `EXPLAIN QUERY PLAN`
- [ ] **PERF-10**: `AppLifecycleProvider` interface + impl backed by `ProcessLifecycleOwner`; `DownloadWorker` reads `isAppInForeground` before posting notification
- [ ] **PERF-11**: `installSplashScreen()` + cross-fade mask in `MainActivity` using `androidx.core:core-splashscreen:1.2.0-beta01`
- [ ] **PERF-12**: Macrobenchmark cold-start baseline on Pixel 7 recorded in `BENCHMARKS.md` (target: <1.5s cold, <800ms warm, 60fps streaming, peak memory <1.5× model size)
- [ ] **PERF-13**: SQLCipher microbench (with/without) — measure first, document the decision in `BENCHMARKS.md`

### Agent Skills Lite (optional P2 — defer to v2.1 if v2.0 timeline tight)

- [ ] **SKILLS-01**: 3-5 built-in Kotlin `@Tool`-annotated skills ship with the app (calculator, JSON-formatter, current-time, code-block-extractor, text-summarizer-template)
- [ ] **SKILLS-02**: `LlmModelHelper` interface extended with `tools: List<ToolProvider>` plumbed to LiteRT-LM 0.13.1
- [ ] **SKILLS-03**: `LmStudioHelper` maps `tools` to LM Studio `/api/v1/chat` `tools` field
- [ ] **SKILLS-04**: Skill chips under chat input; tap to insert the skill's prompt prefix
- [ ] **SKILLS-05**: `domain/repository/SkillRepository.kt` discovers and lists enabled skills
- [ ] **SKILLS-06**: `skills/` package with one file per skill (per-skill file convention)

## v2 Requirements (deferred)

Tracked but not in current v2.0 roadmap.

- **LMSTUDIO-MCP-01**: LM Studio MCP Bridge — separate phase, requires MCP Kotlin SDK or custom JSON-RPC-over-HTTP client
- **LRT-04**: Speculative Decoding toggle for capable models
- **BENCH-VIEW-01**: Benchmark history viewer (needs 1+ weeks of accumulated results)
- **DEEPLINK-01**: Deep links `warped://chat/<id>` (made easy by type-safe nav)
- **LRT-05**: Vulkan GPU acceleration for LiteRT-LM (when stable)
- **LRT-06**: Samsung Hexagon NPU acceleration for LiteRT-LM (when stable)

## Out of Scope

Explicitly excluded — do not re-add without discussion.

| Feature | Reason |
|---------|--------|
| Ask Image / multimodal | Out of scope — text-only focus |
| Audio Scribe / voice I/O | Out of scope — defer |
| Tiny Garden / Mobile Actions | Out of scope — games + autonomous tool use deferred |
| JS webview skill runtime | Anti-pattern A12 — heavy + XSS surface |
| Community Skills marketplace | Anti-pattern A12 — defer |
| AICore system service | Pixel-only, preview, narrow support — Anti-pattern A9/A13 |
| "Best for" model pinning | Out of scope — REC-03 fixed-list policy |
| Multi-tab model browser | REMOVED v1.5 — single search bar |
| Public trending scraping | Out of scope — REC-01 hand-curated only |
| Remote allowlist hosting | Out of scope — assets/model_allowlist.json only |
| iOS feature parity | Out of scope — Android-only |
| OpenAI / Anthropic / Ollama / Custom providers | REMOVED v1.8 — LM Studio v1 only |
| GGUF / llama.cpp local inference | REMOVED v1.5 — LiteRT-LM only |
| Certificate pinning for remote endpoints | Defer |
| Play Integrity / root detection | Defer |
| kapt / Firebase / Moshi / Gson / kotlin-reflect / Ktor / TFLite / mlkit-genai / AppAuth / CameraX / compose-richtext / Proto DataStore | Gallery anti-patterns — explicitly rejected |
| OkHttp 5.x | API-breaking — defer |
| SQLCipher removal without measurement | Measure first (PERF-13) |
| Fallback destructive Room migration | NEVER — data loss risk for production users |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| RUNTIME-01..12 | Phase 40 | Pending |
| CACHE-01..03 | Phase 40 (keystone) | Pending |
| THINK-01..07 | Phase 41 | Pending |
| BENCH-01..06 | Phase 41 | Pending |
| PROMPT-01..06 | Phase 42 | Pending |
| PERF-01..13 | Phase 43 (parallel to 41/42) | Pending |
| SKILLS-01..06 | Phase 44 (optional) | Pending |

**Coverage:**
- v2.0 requirements: 53 total
- Mapped to phases: 53
- Unmapped: 0 ✓

---
*Requirements defined: 2026-06-05*
*Last updated: 2026-06-05 after initial v2.0 definition*
