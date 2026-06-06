# Milestones

## v2.0 Gallery Convergence & Performance Overhaul (Shipped: 2026-06-06)

**Phases completed:** 5 phases (40-44), 23 plans, 53 requirements
**Requirements:** 53 defined, 48 MET, 5 PARTIAL (Phase 43 PERF-01, PERF-05, PERF-06, PERF-12, PERF-13) + 2 carry-overs (Phase 44 SKILLS-02 Tool execution, SKILLS-03 LM Studio tools[] mapping)
**Known deferred items at close:** 7 (5 PARTIALs in Phase 43 with v2.1 follow-up, 2 v2.1 carry-overs in Phase 44)
**Audit:** [`.planning/v2.0-MILESTONE-AUDIT.md`](v2.0-MILESTONE-AUDIT.md) → status: **passed**
**Archived roadmap:** [`.planning/milestones/v2.0-ROADMAP.md`](milestones/v2.0-ROADMAP.md)

**Key accomplishments:**

1. **Keystone `LlmModelHelper` interface** — Unified local (LiteRT-LM) and remote (LM Studio) chat behind a single 5-method API (`initialize`, `runInference`, `resetConversation`, `stopResponse`, `cleanUp`) mirroring the [google-ai-edge/gallery](https://github.com/google-ai-edge/gallery) v1.0.16 surface; `@Binds @Singleton` Hilt wiring; `LiteRtLlmHelper` wraps `EngineManager` on `Dispatchers.IO`; `LmStudioHelper` wraps `LMStudioProvider` with Job-cancellation `stopResponse`
2. **`assets/model_allowlist.json`** — 8 hand-curated entries with the full Gallery schema (`name`, `displayName`, `modelFile`, `sizeInBytes`, `capabilities`, `llmPromptTemplates`, `taskTypes`); `ModelAllowlistRepository` exposes `supportsThinking()`, `supportsSpeculativeDecoding()`, `supportsVision()` capability queries
3. **Mechanical stack hardening** — Compose BOM 2026.05.01, Hilt 2.59.2, Room 2.8.4, Lifecycle 2.10.0, Navigation 2.9.x, LiteRT-LM 0.13.1, R8 full mode + comprehensive keep rules for `com.google.ai.edge.litertlm.**` JNI, type-safe `@Serializable` nav (14 destinations), `installSplashScreen()` + 200ms cross-fade, dark-mode no-recreate, three `<uses-native-library>` entries, `largeHeap="true"`, `extractNativeLibs="false"`
4. **Mmap-only model cache** — `EngineManager.getCachedModelPath()` file copy REMOVED; `EngineConfig.cacheDir` is `context.cacheDir/litertlm/<LITERTLM_VERSION>/` for mmap-only caching. Saves 1-3 GB/model + 3-10s cold start. 500MB LRU cap configurable via `AdvancedPreferences`; `handleTrimMemory` evicts on `TRIM_MEMORY_RUNNING_CRITICAL`
5. **Gallery anti-pattern audit (RUNTIME-12)** — `scripts/audit-dependencies.sh` fails the build on kapt, firebase, moshi, gson, kotlin-reflect, ktor, mcp, tflite, mlkit-genai, appauth, compose-richtext, camerax, datastore-proto. Warped stays KSP-only, kotlinx-serialization-only, no Kotlin reflection, no Firebase
6. **Thinking mode** — `LlmModelHelper.runInference(..., enableThinking: Boolean)` + `StreamToken.Delta` reasoning content; per-model allowlist gating (`litertlm-community/DeepSeek-R1-Distill-Qwen-1.5B` is the first thinking-capable entry); "Thinking ON/OFF" chip in the chat input; collapsible "Thinking..." panel in `MessageBubble`; client-side `<think>` tag stripping for backends that emit reasoning regardless
7. **Model benchmark** — `ui/benchmark/` package (4 files: `BenchmarkScreen`, `BenchmarkViewModel`, `BenchmarkResultsViewer`, `BenchmarkValueSeriesViewer`); `ModelBenchmarkWorker` (WorkManager + `setForeground()`); `MemorySampler` (100ms-interval JVM+native heap poller); Room `BenchmarkResult` table with `@AutoMigration(11, 12)`; `MigrationTest` round-trips 1 conversation + 50 messages; sparkline chart of last 10 decodeTokPerSec per model
8. **Prompt Lab** — Side-by-side single-turn workspace with 7 curated templates (rewrite, summarize, extractKeyPoints, codeExplain, translate, sentiment, tableToJson); `PromptLabViewModel` reuses the local `LlmModelHelper` path; output renders through the v1.6 `MarkdownText` (syntax highlighting parity); `TemplateDropdown` for picking
9. **Performance convergence** — `derivedStateOf` around traffic-light derivations; `MessageImageStack` extracted from `MessageBubble`; `PersistentList` boundaries for Compose 1.11 strong-skipping; separate `@Named("sse")` OkHttp client with `retryOnConnectionFailure(false)`, `callTimeout(60s)`, 50MB disk cache; `MessageEntity` composite index on `(conversation_id, created_at)` via `@AutoMigration(12, 13)`; `AppLifecycleProvider` (ProcessLifecycleOwner) + `ModelDownloadWorker` foreground-notification suppression; `ColdStartBenchmark` + `StreamingFrameBenchmark` macrobenchmarks; `BENCHMARKS.md` with targets + measurement procedure for cold-start (<1.5s), warm-start (<800ms), streaming (60fps), peak memory (<1.5× model size), SQLCipher overhead (drop if >20%)
10. **Agent Skills Lite** — Sealed `Skill` value object + `SkillCategory { Tool, PromptTemplate }`; 4 hand-curated skills (`Calculator`, `CurrentTime`, `JsonFormatter`, `Summarize`); `SkillPreferences` DataStore (defaults all-on); `SkillRepository` (interface + impl + Hilt bind); `SkillChipsRow` composable under the chat input; `LlmModelHelper.runInference(..., skills: List<Skill>)` plumbed end-to-end; `LmStudioHelper.applySkills` injects `PromptTemplate` skills into the system prompt

### Deferred
- **PERF-01 (full `ChatUiState` sub-state split):** Current single-state shape is consumed by ~10 subcomposables; the split is a structural improvement for Phase 45+ scale. v2.1.
- **PERF-06 (LazyColumn `key`):** `ChatScreen` uses a vertically-scrolling `Column` for messages. Switch to `LazyColumn` with `key = { it.id }` if message counts grow past 100 per conversation. v2.1.
- **PERF-12/13 actual numbers:** Code + procedure ship; `BENCHMARKS.md` cells are `[CI fills in]`. Requires a real Pixel 7 reference device to measure. CI gate.
- **SKILLS-02 Tool execution (LiteRT-LM):** `LiteRtLlmHelper` logs the skill list but does not yet register `@Tool` annotations. v2.1.
- **SKILLS-03 Tool execution (LM Studio):** `LmStudioHelper.applySkills` injects `PromptTemplate` skills into the system prompt only. The `LmStudioChatRequest.tools` DTO field is plumbed but unused at request-construction time. v2.1.
- **LlmModelHelper `runInference` double-collect:** `LiteRtLlmHelper.runInference` and `LmStudioHelper.runInference` launch an internal "drain" job AND return a separate `chat()` Flow. Refactor to `shareIn` / `MutableSharedFlow`. v2.1.
- **`LMStudioProvider` `Call` reference for `Call.cancel()`:** The actual OkHttp `Call` lives inside `LMStudioProvider`; the `activeCall` `AtomicReference` was removed in 43-02 (dead code). Plumbing the `Call` through the provider for true cancellation is v2.1.

### Tech debt
None introduced. No Gallery anti-patterns. R8 full mode is on; dependency audit is enforced in `check`; all `@Provides` are `@Singleton`; `ProviderRouter` uses `dagger.Lazy<>` for per-helper injection; no eager `LiteRtLlmEngine` SingletonComponent binding.

### Decisions captured
- v2.0: Reference implementation = google-ai-edge/gallery v1.0.16 (23.6k stars, 91.9% Kotlin). Gallery is the only major OSS in the "LiteRT-LM + Compose + Android" niche, making it the most defensible convergence target.
- v2.0: Drop Gallery's tech-debt surface — kapt, Moshi, Gson, kotlin-reflect, Firebase, Ktor, MCP SDK, compose-richtext, Proto DataStore, mlkit-genai, AppAuth, CameraX, JS webview skills. Verified by `scripts/audit-dependencies.sh` (RUNTIME-12).
- v2.0: Warped is **already ahead** of Gallery on several axes (Hilt 2.59.2 vs 2.58, KSP-only, kotlinx-serialization only, per-screen VMs vs Gallery's 850-line mega-VM). v2.0 is a **convergence, not a copy**.
- v2.0: Use `@Binds @Singleton` for the `LlmModelHelper` interface — never `@Inject constructor` on a concrete helper class.
- v2.0: Cache directory namespaced by `BuildConfig.LITERTLM_VERSION`; LiteRT-LM does not expose a cache-version API, so cache is silently stale after `EngineConfig` schema change.
- v2.0: `LlmModelHelper` is a stateful runtime; repositories are stateless or only-own-DB. Crossing the boundary is the bug.

---

## v1.7 App Optimization & Smart Presets (Shipped: 2026-05-25)

**Phases completed:** 4 phases (31-34), 4 plans, 19 requirements
**Requirements:** 19 defined, 19 satisfied
**Known deferred items at close:** 0

**Key accomplishments:**

1. LiteRT-LM upgraded from v0.11.0 to v0.12.0 with compilation verified and all existing functionality preserved
2. Unified Models & Endpoints Selector: single screen showing 1 local model (with connect/disconnect toggle) alongside infinite remote endpoint models
3. Traffic light status indicator (semaforo) integrated in ChatScreen TopAppBar for both local and remote providers
4. Smart Memory-Based Presets: dynamically calculated optimal preset based on available device RAM and selected model size, with manual override capability
5. All 19 requirements satisfied across 4 phases, compilation clean, no tech debt introduced

---

## v1.6 Code Syntax Highlighting (Shipped: 2026-05-15)

**Phases completed:** 3 phases (28-30), 8 plans, 22 tasks
**Requirements:** 20 defined, 18 satisfied, 1 deferred (INTG-03), 1 pending human verification (Phase 29)
**Known deferred items at close:** 18 (6 verification gaps + 12 quick tasks — see STATE.md Deferred Items)

**Key accomplishments:**

1. Highlights 1.1.0 integrated as syntax tokenization engine with 4 domain models — TokenType (13 enum values), SyntaxToken, SyntaxColor, and SyntaxTheme with Monokai/One Dark/GitHub/Dracula presets, each holding complete 13-color light/dark color tables
2. Syntax highlighting engine core: 43-entry LanguageDetector with 20+ alias mappings and keyword-frequency auto-detection, stateless TypeMapper with gap-fill and overlap resolution, Highlights-backed SyntaxHighlighterImpl with LRU cache, and DataStore-backed theme persistence with backward-compatible CodeTheme migration
3. 150 comprehensive unit tests covering language detection, token mapping, syntax highlighting integration, and theme validation — all passing
4. Block-based markdown rendering: MarkdownBlock sealed hierarchy (8 variants), pure parseMarkdown() function with LanguageDetector, and MarkdownText refactored from single Text(AnnotatedString) to composable Column enabling per-block UI elements
5. CodeBlock composable (545 lines): syntax-colored tokens via animateColorAsState, language header bar with copy button, line number gutter, expand/collapse for blocks over 200 lines, and syntax issue warning indicator
6. Full chat + settings integration: SyntaxTheme flows from Settings dropdown (with 4-color swatch preview) through ViewModels to all chat composables. Code font scale slider (0.8x–1.5x) wired end-to-end
7. Streaming transition fix: LaunchedEffect key includes isStreaming — flat monospace during active streaming, full syntax highlighting on closing fence. Code font scale wired through full 6-hop composable chain
8. Compilation verified, monospace coverage audit confirms canonical rendering (all FontFamily.Monospace usage flows through CodeBlock/MarkdownText), HuggingFace model descriptions render with MarkdownText

### Deferred
- **INTG-03:** README/markdown preview screen with syntax highlighting — requires new HuggingFace API endpoint and preview screen
- **Phase 29:** 4 human UI verification checks pending (theme dropdown, font scale slider, CodeBlock rendering, expand/collapse) — require device/emulator
- **Pre-existing:** 6 verification gaps (Phases 06-10, 29) + 12 quick tasks from earlier milestones

---
