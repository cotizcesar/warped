# Milestones

## v2.3 Web Grounding v2 (Shipped: 2026-09-28)

**Phases completed:** 3 phases (52-54), 8 plans, 12 requirements
**Requirements:** 12 defined, 12 verified (automatable evidence 100%; 3 device-smoke follow-ups accepted as release-UAT, user-approved)
**Known verification overrides:** 0 partials — all phases passed verification (52: 5/5, 53: 6/6 via gap-closure static migration gate, 54: 3/3); 289/289 unit green; SECURED all phases
**Audit:** [`.planning/milestones/v2.3-MILESTONE-AUDIT.md`](milestones/v2.3-MILESTONE-AUDIT.md) → status: **gaps_found** (accepted)
**Archived roadmap:** [`.planning/milestones/v2.3-ROADMAP.md`](milestones/v2.3-ROADMAP.md)
**Archived requirements:** [`.planning/milestones/v2.3-REQUIREMENTS.md`](milestones/v2.3-REQUIREMENTS.md)
**Closeout type:** override_closeout

**Key accomplishments:**

1. **Multi-URL fetch foundation** — Parallel fan-out (`coroutineScope`+`awaitAll`, cap 5, paste-order numbering), fused `[WEB CONTEXT 1..N]` blocks, partial grounding with all-fail-only banner, per-source `Leyendo N de M…` progress, concurrent-safe cancel set
2. **Jsoup extraction swap** — Jsoup 1.23.2 parse-only (`Jsoup.parse`, never `connect()`, zero `Jsoup.connect` grep-clean) replacing the regex core with fallback; fetch policy frozen (stripped client, 64KB cap, 8/10/20s timeouts, 3 redirects)
3. **Global grounding budget** — Model-window-aware tiers (4500/6000/scaled) divided across pages (800 floor), 5×max-size exit gate; multi-page adversarial suite (dead-of-3, all-dead, cap, cross-page hijack)
4. **Sources preview + persistence** — Bottom-sheet preview (zero-I/O open, `Abrir en navegador` guarded intent), clickable Fuentes (omitida struck), `grounded_sources` table + single `MIGRATION_14_15` with JVM static gate
5. **Per-chat toggle** — Tri-state `web_override` (Sí/No/Heredar, null inherits) + one-off `Sin web` chip; pure `GroundingPrecedence` (skipOnce > perChat > global) with truth-table tests
6. **Offline retry** — Queued `En espera` banner + validated-online-gated `Reintentar`; same-entry `fetchAll`, sources-only attach (assistant text byte-identical, inference never runs), same-row `replaceSources`, Stop/overlap/streaming guards

### Known Gaps (accepted, release-UAT device smokes)
- **MIG-01:** on-device MigrationTest v14→v15 (live SQLite/SQLCipher row survival)
- **WEB-07:** grounding visuals on hardware, both themes (chip, Fuentes, sheet, queued/Reintentar banner)
- **WEB-08:** offline→resume→tap→Fuentes E2E with live fetch on hardware

### Tech debt
- GroundingBudget tiers LOW-confidence until on-device validation (TUNE-01 trigger-gated)
- Missing UNIQUE index on `grounded_sources(message_id, source_index)` (delete-then-insert covers; hardening follow-up)
- Phase 53 UI polish trio (override indicator, Fuente-heading redundancy, all-omitida visibility)
- Nyquist: no `VALIDATION.md` in any v2.3 phase — coverage TODO (same as v2.2)

## v2.2 Simplificación + Web Grounding (Shipped: 2026-09-28)

**Phases completed:** 3 phases (49-51), 5 plans, 11 tasks, 14 requirements
**Requirements:** 14 defined, 10 MET, 4 PARTIAL (DEL-06, WEB-05, WEB-06, THEME-01 — all 4 partials are deferred on-device smokes; zero code gaps)
**Known verification overrides:** 4 partials accepted as release-UAT tech debt (user-approved); all automated gates pass (232/232 unit, assembleDebug + assembleRelease green, all grep gates green, dependency audit green)
**Audit:** [`.planning/milestones/v2.2-MILESTONE-AUDIT.md`](milestones/v2.2-MILESTONE-AUDIT.md) → status: **gaps_found** (accepted)
**Archived roadmap:** [`.planning/milestones/v2.2-ROADMAP.md`](milestones/v2.2-ROADMAP.md)
**Archived requirements:** [`.planning/milestones/v2.2-REQUIREMENTS.md`](milestones/v2.2-REQUIREMENTS.md)
**Closeout type:** override_closeout

**Key accomplishments:**

1. **Surface removal** — Entire skills/tool-execution surface deleted (26 files: local `@Tool` skills, chips/prefs/repo/gating, Summarize template, remote `tools[]` loop); `runInference` back to skill-free signature; legacy `Role.TOOL` rows render read-only with no Room migration
2. **Static model catalog** — HF token field/headers/prefs deleted; model search surface deleted; `model_allowlist.json` catalog with direct unauthenticated downloads (progress/cancel) via new `CatalogViewModel`; R8 skills keeps narrowed with LiteRT-LM 0.17.x keeps intact; net −4721/+1378 lines
3. **Web grounding pipeline** — `data/grounding/` package: first-URL detection, bounded cancelable OkHttp fetch (64KB cap, 3 redirects, 8/10/20s timeouts, browser UA, `Call.cancel()` on Stop), hand-rolled HTML→text (4000-char budget), hijack sanitizer, `[WEB CONTEXT]` augmentation; `LlmModelHelper`/providers unchanged; zero new dependencies
4. **Grounding surfaces** — Transient "Leyendo página…" chip, numbered Fuentes list, UI-rendered model-only banner (offline vs failure copy), default-ON Web settings toggle (Data → Web → Display)
5. **Syntax-theme fix** — `SyntaxHighlighter.highlight` accepts `theme` (was Monokai-hardcoded); `CodeBlock` threads `syntaxTheme` with theme-keyed `LaunchedEffect`; theme-aware cache key; all-4-preset regression tests (per-preset loop, cache separation, 6-test PresetDistinctness)

### Known Gaps (accepted, release-UAT device smokes)
- **DEL-06:** on-device release smoke (launch → allowlisted model → local turn → remote turn → legacy TOOL chat)
- **WEB-05:** rendered model-only banner appearance (offline vs failure copy)
- **WEB-06:** chip transient behavior, Fuentes rendering, E2E paste-URL flow
- **THEME-01:** visible per-preset result in light + dark mode

### Tech debt
- Orphaned Keystore `huggingface_token` entry on upgraded installs (accepted, harmless, never read)
- 49-01/49-02 SUMMARY.md files lack `requirements-completed` frontmatter (process debt)
- Live-fetch behaviors code-reviewed/gate-checked but never exercised against a live server
- Nyquist: no `VALIDATION.md` in any v2.2 phase — coverage TODO

## v2.1 Finish v2.0 Leftovers (Shipped: 2026-09-28)

**Phases completed:** 4 phases (45-48), 10 plans, 18 requirements
**Requirements:** 18 defined, 18 MET, 0 partials
**Known deferred items at close:** Pixel 7 reference-device numbers (PERF-16 + PERF-12/13 gate) — emulator note in BENCHMARKS.md, CI-gated
**Audit:** [`.planning/v2.1-MILESTONE-AUDIT.md`](v2.1-MILESTONE-AUDIT.md) → status: **passed**
**Archived roadmap:** [`.planning/milestones/v2.1-ROADMAP.md`](milestones/v2.1-ROADMAP.md)
**Archived requirements:** [`.planning/milestones/v2.1-REQUIREMENTS.md`](milestones/v2.1-REQUIREMENTS.md)

**Key accomplishments:**

1. **Foundation refresh** — Full catalog to latest stable (Hilt 2.60.1, Room 2.8.5, AGP 9.3.0, serialization 1.11.0…) + LiteRT-LM 0.13.1 → 0.17.1 with EngineConfig/ConversationConfig re-verification, R8 keeps, version-namespaced mmap cache; `model_allowlist.json` created with verified-only flags (+ gemma-4-E2B-it)
2. **Runtime hardening** — Single shared inference Flow (`shareIn` replay=1 per-turn) + true Stop (`Call.cancel()`/`cancelProcess()`); sentinel no-op jobs deleted; rotation-safe; suite 192/192
3. **Real tool execution** — Skills surface rebuilt Kotlin-only (Calculator, CurrentTime, JsonFormatter + Summarize template); local `@Tool` ToolSets + remote `/v1/chat/completions` tools[] loop (cap 5, cancel-checked); shared schema mapper; Role.TOOL transcript persistence; trust boundary + per-tool tests; per-model gating (default closed)
4. **Chat perf + startup + release** — Atomic ChatUiState sub-state split + keyed LazyColumn + Jump-to-latest pill; Baseline Profiles seed + lazy native load; log-secret strip (takeLast(100) → length-only); R8 full-mode re-verified; BENCHMARKS <1s target
5. **Device-driven hardening** — Thinking-fallback fix + history repair migration v14; engine session lifecycle; GPU-constraint retry + spec-decode opt-in (12B loads); icon capability badges + Thinking opt-in; seamless model switch

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
