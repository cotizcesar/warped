# Research Summary — Warped v2.0 Gallery Convergence & Performance Overhaul

**Project:** Warped (Android LM Studio equivalent — Kotlin + Jetpack Compose + LiteRT-LM + LM Studio v1)
**Domain:** On-device LLM chat with remote provider bridge
**Researched:** 2026-06-05
**Confidence:** **HIGH** overall — version facts, Gallery repo architecture, LiteRT-LM API surface all verified against primary sources within 24 hours. **MEDIUM** on performance impact estimates (no Warped-hardware profiling yet).

**Reference implementation:** [google-ai-edge/gallery](https://github.com/google-ai-edge/gallery) v1.0.16 main branch (23.6k stars, 91.9% Kotlin). Gallery is the only major OSS in the "LiteRT-LM + Compose + Android" niche, making it the most defensible convergence target.

---

## Executive Summary

Warped v2.0 is a **two-pronged milestone**: (1) **port Gallery's runtime abstraction** so that Thinking Mode, Model Benchmark, Prompt Lab, and (optionally) Agent Skills can plug in cleanly, and (2) **file-by-line performance overhaul** to make the app feel as fast, smooth, and resource-light as the reference implementation. The keystone of the feature work is a single `LlmModelHelper` interface (mirroring Gallery's `runtime/LlmModelHelper.kt`) that unifies `LiteRTLmProvider` and `LMStudioProvider` behind one streaming API. Without it, every v2.0 feature needs its own bespoke plumbing for local vs remote; with it, all four features share a surface. The keystone of the perf work is **dropping Warped's file-copy model cache** (1–3 GB of dead I/O in `EngineManager.getCachedModelPath()`) and keeping only LiteRT-LM's mmap cache — a 3–10 second cold-start win plus halved on-device storage footprint.

**Warped is already ahead of Gallery on several axes** (Hilt 2.59.2 vs Gallery's 2.58, KSP-only vs Gallery's kapt, kotlinx-serialization only vs Gallery's triple-JSON mess, per-screen ViewModels vs Gallery's 850-line mega-VM, EncryptedSharedPreferences + Preferences DataStore vs Gallery's 5-Proto-DataStore approach). v2.0 is a **convergence, not a copy**: adopt Gallery's interface and manifest patterns, keep Warped's existing strengths, **do not import** Gallery's tech-debt surface (kapt, kotlin-reflect, Moshi, Gson, Firebase, Ktor, mlkit-genai-prompt, CameraX, TFLite, AppAuth, compose-richtext, Proto DataStore, JS webview skills).

**The biggest risks** are (a) R8 stripping LiteRT-LM JNI / kotlinx-serialization in release builds, (b) `Engine.initialize()` blocking the main thread if not dispatched to `Dispatchers.IO`, (c) Compose 1.11 strong-skipping mode silently making every `List<T>` parameter unstable, and (d) LiteRT-LM 0.12→0.13 file-format breakage leaving some existing user models unloadable. All four have known prevention recipes in the research and should be addressed in **Phase 40 — Runtime & Allowlist Foundation** before any feature work.

---

## Top 10 Actionable Items (Highest-ROI for v2.0)

Ranked by leverage × risk-reduction. Each item has a STACK → FEATURE → PITFALL chain citation.

| # | Action | Why It Matters | Cross-Reference |
|---|--------|----------------|-----------------|
| **1** | **Port `LlmModelHelper` interface + 2 impls** (`LiteRtLlmHelper`, `LmStudioHelper`) and refactor `ChatViewModel` to depend on the interface. | Keystone for all v2.0 features. Without it, Thinking/Benchmark/PromptLab/Skills each need their own local-vs-remote plumbing. Estimated 7–10 days; touches the chat hot path. | FEATURES §Table Stakes; ARCHITECTURE §5 (#MUST REFACTOR); PITFALLS Critical #2 (off-main), #7 (backpressure), #8 (Hilt cycle) |
| **2** | **Drop the model file copy in `EngineManager.getCachedModelPath()`**; pass `context.cacheDir.absolutePath` (or external) directly to `EngineConfig.cacheDir` for mmap only. | Single biggest perf win: 1–3 GB disk savings per model + 3–10s cold-start I/O removed. Gallery's pattern. | STACK §3 (LiteRT-LM); ARCHITECTURE §8; PITFALLS Critical #4 (cache invalidation), Anti-Pattern A14 |
| **3** | **Add `core-splashscreen:1.2.0-beta01`** + `installSplashScreen()` + cross-fade mask in `MainActivity`. Add `android:configChanges="uiMode"` + `<uses-native-library>` for `libvndksupport.so`/`libOpenCL.so`/`libcdsprpc.so` to manifest. | Eliminates cold-start flash. Required manifest entries unlock GPU/NPU backend on supported devices (no auto-detection without them). | STACK §13, §14, §3; ARCHITECTURE §4, §Cross-Cutting Manifest; PITFALLS Anti-Pattern A15, A16 |
| **4** | **Bump dependencies**: compose-bom 2026.04.01→2026.05.01, hilt-navigation-compose/hilt-work 1.2.0→1.3.0, Room 2.7.1→2.8.4, Lifecycle 2.8.7→2.10.0, Navigation 2.8.8→2.9.x, LiteRT-LM 0.13.0→0.13.1, add `lifecycle-process:2.10.0`. | Aligns with Gallery's proven version matrix. Lifecycle-process enables app-wide foreground/background awareness. LiteRT-LM 0.13.1 adds ToolProvider + Agent Skills. | STACK §1, §2, §3, §4, §5, §6 (Version Compatibility Matrix); PITFALLS Critical #3 (0.13 file format) |
| **5** | **Add `AppLifecycleProvider`** (interface + impl) and wire `LifecycleEventObserver` in `MainActivity` to gate download notifications. Use `ProcessLifecycleOwner` from `lifecycle-process`. | Suppresses download notifications when app is foregrounded. Warped has no such gate today. Gallery does this. | ARCHITECTURE §4 (#MUST REFACTOR #3); STACK §5; PITFALLS Critical #12 (battery optimizer) |
| **6** | **Plumb `enableThinking` + `partialThinkingResult`** through `LlmModelHelper.runInference`. Render collapsible "Thinking" panel in `MessageBubble`. Gate by `capabilities: ["llm_thinking"]` in allowlist. | Newer reasoning models (Gemma 4 E2B/E4B, DeepSeek-R1-Distill) expose thinking; users running these expect it. LM Studio v1 `/api/v1/chat` returns `reasoning_content`. Both engines supported. | FEATURES §Table Stakes; ARCHITECTURE §5; PITFALLS Critical #7, UX Pitfall (toggle hidden) |
| **7** | **Add `assets/model_allowlist.json`** matching Gallery's schema (`name`, `modelFile`, `sizeInBytes`, `capabilities`, `llmPromptTemplates`). Replace `RecommendedModels` Kotlin constant. New `ModelAllowlistRepository`. | Required for capability gating (Thinking, Speculative Decoding). Static asset = no network dep, aligns with v1.8 REC-03. | FEATURES §Table Stakes; ARCHITECTURE §6; PITFALLS "Looks Done" #5 |
| **8** | **Add R8 keep rules** for `com.google.ai.edge.litertlm.**` (JNI), `MessageCallback`, `ToolProvider`, and canonical kotlinx-serialization `$$serializer` companions. Enable R8 full mode. | **Release builds only.** Without these, `UnsatisfiedLinkError` on first model load + `Serializer for class 'X' is not found` on first DTO. Verify with `aapt2 dump strings` on release APK. | PITFALLS Critical #5, #6; STACK §8; Anti-Pattern A1 |
| **9** | **Split `ChatUiState` (50 fields) into 3 sub-states** + add `@Immutable` annotation. Audit `MessageBubble` / `CodeBlock` / `ChatInputBar` for hoisted state, stable lambdas, `key()` in `LazyColumn`. Add `kotlinx-collections-immutable:0.4.0` and convert `List<T>` params to `ImmutableList<T>`. | Compose 1.11 strong-skipping mode (default in BOM 2026.05.01) treats `List<T>` as unstable → every streaming token recomposes every row. ChatScreen during streaming will become visibly jankier. **Must be addressed before Thinking Mode is wired up** (Thinking adds a new StateFlow subscription = worst-case scenario). | ARCHITECTURE §2, §7; PITFALLS Critical #11; STACK §1 (BOM bump) |
| **10** | **Add Room migration infrastructure** for v1.8 → v2.0 schema changes (new `BenchmarkResult` table in Phase 41, new `thinking` column on `Message`). Set `exportSchema = true`, commit `schemas/` to git, write a `MigrationTest` in `androidTest/`. **Never** use `fallbackToDestructiveMigration()`. | v2.0 install on a device with v1.8 chat history will silently wipe the DB if migration is missing. Warped now has production users (39 phases, 234 requirements shipped) — this is no longer a non-issue. | PITFALLS Critical #9; STACK §4; "Looks Done" #8 |

**Run order (critical path):** 1, 4, 8, 2, 3, 5, 7 → 6, 9, 10 (6 and 9 are blocking-start for Phase 41/43; 10 is inline with Phase 41 schema work).

---

## Cross-Dimensional Dependencies (STACK → FEATURE → PITFALL chains)

The following chains show where a stack decision unlocks a feature that triggers a pitfall. Each chain is one decision to make consciously.

### Chain A: `LlmModelHelper` (keystone)
- **STACK:** LiteRT-LM 0.13.1 adds `ToolProvider` + `MessageCallback` (STACK §3). PITFALLS Critical #2 requires `withContext(Dispatchers.IO)` wrap around `Engine.initialize()`.
- **FEATURE:** Enables Thinking Mode (FEATURES §Thinking Mode), Model Benchmark (FEATURES §Model Benchmark), Prompt Lab (FEATURES §Prompt Lab), Agent Skills Lite (FEATURES §Agent Skills Lite). All four depend on the keystone.
- **PITFALLS:** Critical #2 (off-Main init), #7 (callbackFlow backpressure with `Channel.UNLIMITED`), #8 (Hilt cycle — use `@Binds` for interface), #10 (SSE cancellation — `stopResponse` must cancel the read loop and `Call`). All must be addressed in the same phase.
- **Verdict:** Phase 40 is non-negotiable. Touches the chat hot path; needs 7–10 days.

### Chain B: `EngineConfig.cacheDir` mmap-only
- **STACK:** LiteRT-LM `EngineConfig` accepts `cacheDir: String?` for mmap warm-start (STACK §3).
- **FEATURE:** Doesn't unlock a new feature but enables **5–10× faster warm starts** (FEATURES §Performance), and **1–3 GB disk savings** (ARCHITECTURE §8).
- **PITFALLS:** Critical #4 — LiteRT-LM does NOT expose a cache-version API. The cache is silently stale after `EngineConfig` schema change. Must namespace by `BuildConfig.LITERTLM_VERSION` and cap at 500MB with LRU eviction. Anti-Pattern A14 (Gallery's mistake).
- **Verdict:** Phase 43 (Performance). The cleanest single-file change in the v2.0 mandate.

### Chain C: Compose BOM 2026.05.01 → strong-skipping mode
- **STACK:** BOM 2026.05.01 includes Compose 1.11.0 with strong skipping as default (STACK §1).
- **FEATURE:** Doesn't unlock a feature but **all v2.0 features assume the recomposition perf budget holds**. Adding Thinking Mode = new StateFlow subscription = worst case for unstable params.
- **PITFALLS:** Critical #11 — every `List<T>` parameter becomes unstable. `kotlinx-collections-immutable:0.4.0` is the standard escape hatch. Layout Inspector in Android Studio is the verification tool.
- **Verdict:** Phase 43 (Performance Convergence). Must be addressed **before** or **concurrent with** Phase 41 (Thinking Mode wiring) — not after.

### Chain D: AndroidManifest `<uses-native-library>` + `largeHeap` + `extractNativeLibs="false"`
- **STACK:** Native library declarations required for GPU/NPU/DSP auto-detection (STACK §3, §13).
- **FEATURE:** Unlocks `Backend.GPU()` runtime selection → 2–5× faster inference on flagship devices (STACK §3, PITFALLS LiteRT-LM integration table).
- **PITFALLS:** Critical #12 — `largeHeap="true"` is already in Warped but `extractNativeLibs="true"` would balloon APK by 10–20 MB (Anti-Pattern A16). `windowSoftInputMode="adjustResize"` is required for proper chat behavior.
- **Verdict:** Phase 40 manifest changes. One manifest file edit; large downstream impact.

### Chain E: Room → benchmark history → migration risk
- **STACK:** Room 2.8.4 stable, KSP-only (STACK §4).
- **FEATURE:** Model Benchmark needs a `BenchmarkResult` table (FEATURES §Model Benchmark).
- **PITFALLS:** Critical #9 — missing migration = silent data loss for all v1.8 users on upgrade. `@AutoMigration` for additive, `Migration` for breaking. `MigrationTest` in `androidTest/`. NEVER `fallbackToDestructiveMigration()`.
- **Verdict:** Phase 41 inline. Address at table-add time, not as cleanup.

---

## Implications for Roadmap

### Recommended Phase Structure (5 phases, 35–47 days)

```
Phase 40 — Runtime & Allowlist Foundation   [P0]  5–7 days   ← KEYS ALL OTHERS
Phase 41 — Thinking Mode + Benchmark         [P1]  7–10 days
Phase 42 — Prompt Lab                        [P1]  5–7 days
Phase 43 — Performance Convergence          [P0]  7–10 days  ← can parallel 41/42
Phase 44 — Agent Skills Lite (optional)      [P2]  10–14 days ← DEFER to v2.1 if scope tight
```

**Total: 34–48 days** for all 5 phases, depending on Phase 44 inclusion.

### Phase 40 — Runtime & Allowlist Foundation [P0, keystone]

**Rationale:** Without `LlmModelHelper`, every other feature phase is duplicated plumbing. This is the single biggest v2.0 leverage point. Also the cheapest place to do all the **mechanical stack bumps** and **R8 keep rules** before any feature code lands.

**Delivers:**
- `domain/runtime/LlmModelHelper.kt` interface (mirror Gallery's 5 methods + `ResultListener`/`CleanUpListener` typealiases, drop `image`/`audio` params).
- `data/runtime/LiteRtLlmHelper.kt` (wraps `EngineManager` + `LiteRTLmEngine`).
- `data/runtime/LmStudioHelper.kt` (wraps `LMStudioProvider`; SSE cancellation-aware).
- `ChatViewModel` accepts `LlmModelHelper` (interface) via `ProviderRouter` factory.
- `assets/model_allowlist.json` (Gallery schema subset: `name`, `displayName`, `modelFile`, `sizeInBytes`, `capabilities`, `llmPromptTemplates`, `taskTypes`).
- `ModelAllowlistRepository` reads asset, surfaces capabilities.
- Type-safe nav (`@Serializable` destinations) for 7 routes.
- `app/src/main/AndroidManifest.xml`: add `configChanges="uiMode"`, `<uses-native-library>` x3, `windowSoftInputMode="adjustResize"`, `extractNativeLibs="false"`, `largeHeap="true"`, `theme="...SplashScreen"`.
- `libs.versions.toml` bumps (compose-bom, hilt-nav, hilt-work, room, lifecycle, navigation, litertlm).
- `gradle.properties`: `android.nonTransitiveRClass=true`, `-Xmx4g`, `kotlin.incremental=true`.
- `proguard-rules.pro`: LiteRT-LM JNI keep rules + kotlinx-serialization $$serializer keep rules + R8 full mode.

**Addresses:** All 4 STACK MUST-ADOPT buckets in one PR; FEATURES §LlmModelHelper + §Model Allowlist JSON (both P0); ARCHITECTURE §MUST REFACTOR #1, #4, #5, #7, #10; PITFALLS Critical #1, #2, #3 (defensive try/catch), #5, #6, #8, #10.

**Avoids:** All Gallery anti-patterns A1–A6, A11, A15, A16 (verified by `dependencies` audit empty for kapt/firebase/moshi/gson/kotlin-reflect/ktor + manifest audit + proguard audit).

**Research flag:** Needs `--research-phase` for: (a) exact `EngineConfig.cacheDir` parameter name (ARCHITECTURE §Open Q #1), (b) `Message.thinking` accessor name in LiteRT-LM 0.13.1 (FEATURES §Open Q #1, ARCHITECTURE §Open Q #3), (c) `@AutoMigration` schema design for `thinking` column (PITFALLS Critical #9).

### Phase 41 — Thinking Mode + Benchmark [P1]

**Rationale:** Both features ride on the `LlmModelHelper` keystone and need the same data plumbing (`partialThinkingResult` + benchmark history persistence). Co-locating them shares the Room migration work.

**Delivers:**
- `LlmModelHelper` extended with `enableThinking` via `extraContext: Map<String, String>` (per ARCHITECTURE §5; type-safe sealed class preferred over Map for benchmark hot path per PITFALLS Performance Traps row 12).
- `ResultListener.partialThinkingResult: String?` plumbing in both impls.
- UI: collapsible "Thinking..." panel in `MessageBubble` (above the response); chip in chat input bar shows "💭 Thinking ON/OFF" (PITFALLS UX Pitfall).
- Capability gate: only show toggle if `model.capabilities.contains(LLM_THINKING)`.
- `ui/benchmark/` package: `BenchmarkScreen`, `BenchmarkViewModel`, `BenchmarkResultsViewer`, `BenchmarkValueSeriesViewer` (mirrors Gallery's 4 files).
- `worker/ModelBenchmarkWorker.kt` (WorkManager, `setForeground()` for long runs, `Constraints(UNMETERED, BATTERY_NOT_LOW)`).
- `domain/repository/BenchmarkRepository.kt` + impl (Room table, NOT Proto DataStore per Anti-Pattern A7).
- Room schema: add `BenchmarkResult` table (id, modelId, configHash, initTimeMs, prefillTokPerSec, decodeTokPerSec, peakMemoryBytes, createdAt) with `@AutoMigration(from=v1, to=v2)`. **NEVER** `fallbackToDestructiveMigration()`.
- `MigrationTest` in `androidTest/` covering v1.8 → v2.0 (50 messages round-trip).
- `lifecycle-process:2.10.0` added; `AppLifecycleProvider` interface in `domain/lifecycle/`.
- `DownloadWorker` reads `appLifecycleProvider.isAppInForeground` before posting notification (PITFALLS Critical #12).

**Addresses:** FEATURES §Thinking Mode + §Model Benchmark; ARCHITECTURE §MUST REFACTOR #3 (AppLifecycleProvider), #7 (per-feature module); PITFALLS Critical #9, #12.

**Avoids:** Proto DataStore (Anti-Pattern A7), JS webview skills (Anti-Pattern A12), exposing thinking panel for non-reasoning models (PITFALLS "Looks Done" #3).

**Research flag:** Needs `--research-phase` for: (a) exact `Message.thinking` API (carries over from Phase 40), (b) LM Studio's `reasoning_content` exact JSON path (FEATURES §Open Q #1), (c) WorkManager `setForeground()` reliability on Chinese OEM ROMs (PITFALLS Critical #12).

### Phase 42 — Prompt Lab [P1]

**Rationale:** Pure feature on top of the keystone. Independent from Phase 41 (no shared schema work). Reuses the existing `MarkdownText` renderer.

**Delivers:**
- `ui/promptlab/` package: `PromptLabScreen`, `PromptLabViewModel`, `PromptTemplateConfigs.kt` (5–8 templates: rewrite, summarize, extract-key-points, code-explain, translate, sentiment, table-to-json).
- `PromptLabTaskModule` Hilt module with `@IntoSet` binding per Gallery pattern.
- Side-by-side prompt input + output composable; single-turn, no conversation state.
- Reuses `LlmModelHelper` from Phase 40 (no new inference plumbing).

**Addresses:** FEATURES §Prompt Lab; ARCHITECTURE §MUST REFACTOR #7 (per-feature Hilt).

**Avoids:** Gallery's `compose-richtext` + `commonmark` (Anti-Pattern A6) — reuse Warped's `MarkdownText` (v1.6).

**Research flag:** Standard pattern. No research needed unless template content selection is contentious (FEATURES §Open Q #7 — decide during phase planning).

### Phase 43 — Performance Convergence [P0, cross-cutting]

**Rationale:** Explicit v2.0 mandate. Largely orthogonal to the feature ports; can run **in parallel** with Phase 41/42 once `LlmModelHelper` is interface-defined (even before Phase 40 ships — interface signature is the dependency, not the full impl).

**Delivers (ranked by ROI):**
1. **Drop `EngineManager.getCachedModelPath()`** file copy → mmap only. Pass `context.cacheDir.absolutePath` (or external) directly to `EngineConfig.cacheDir`. Namespaced by `BuildConfig.LITERTLM_VERSION` per Critical #4. **Biggest single win: 1–3 GB disk savings + 3–10s cold-start removed.**
2. **Compose recomposition audit**: split `ChatUiState` (50 fields) into 3 sub-states (`ChatListState`, `ChatInputState`, `ChatStreamingState`). Add `@Immutable` annotation. Add `kotlinx-collections-immutable:0.4.0`. Convert `List<T>` → `ImmutableList<T>` on all public composable params. Extract subcomposables in `MessageBubble` and `CodeBlock` (565 lines, the largest composable). Hoist `codeTheme`, `codeFontScale`, `attachedImages` to parent. Add `derivedStateOf` for `trafficLightState` and `trafficLightStatusText`.
3. **`AppLifecycleProvider` + `ProcessLifecycleOwner` + download notification gate** (also listed in Phase 41; can split — Phase 41 adds the interface, Phase 43 does the perf-side wiring).
4. **`installSplashScreen()` + cross-fade mask** in `MainActivity`. `androidx.core:core-splashscreen:1.2.0-beta01` added.
5. **Room composite index** on `messages(conversation_id, created_at)` (PITFALLS Performance Traps + Integration Gotchas). `EXPLAIN QUERY PLAN` to confirm.
6. **Hilt graph audit**: every `@Provides` is either `@Singleton` (true singleton) or `@ViewModelScoped`. No eager `LiteRtLlmEngine` SingletonComponent injection (PITFALLS Performance Traps first row).
7. **OkHttp interceptor chain audit**: `retryOnConnectionFailure(false)` for SSE, `callTimeout(60s)`, `Cache(50MB)` TTL (PITFALLS Performance Traps).
8. **R8 full mode + ProGuard rule audit** (also Phase 40 for the keep rules; Phase 43 measures APK size and adds `Modifier.drawWithCache` audit per ARCHITECTURE §CONSIDER #2).
9. **SQLCipher microbench** (with/without) — measure first, decide removal (STACK §4, PITFALLS Security row 7).
10. **Macrobenchmark cold-start baseline** on Pixel 7 reference (target: < 1.5s cold, < 800ms warm, 60fps streaming, peak memory < 1.5× model size). Numbers recorded in `BENCHMARKS.md`.

**Addresses:** STACK §12 gradle.properties + §13 manifest + §3 LiteRT-LM cacheDir; FEATURES §Performance Convergence; ARCHITECTURE §2, §7, §8 + §MUST REFACTOR #2, #6, #8, #9; PITFALLS Critical #4, #11, #12 + Performance Traps.

**Avoids:** OkHttp 5.x (API-breaking, defer), SQLCipher removal without measurement (audit first).

**Research flag:** Needs `--research-phase` for: (a) Compose 1.11 stability annotation behavior (ARCHITECTURE §Open Q #6), (b) Macrobenchmark setup (PITFALLS Sources — Android Dev Guide), (c) `EngineManager.handleTrimMemory` semantics after cache refactor (ARCHITECTURE §Open Q #7).

### Phase 44 — Agent Skills Lite [P2, optional, defer to v2.1 if scope tight]

**Rationale:** XL scope. High user value (calculator, JSON-formatter, etc.) but 10–14 days. Defer to v2.1 unless v2.0 timeline permits.

**Delivers (Lite variant only):**
- 3–5 built-in Kotlin `@Tool`-annotated skills (calculator, JSON-formatter, text-summarizer-template, current-time, code-block-extractor).
- `LlmModelHelper.tools: List<ToolProvider>` plumbed to LiteRT-LM 0.13.1 (and to LM Studio's `/api/v1/chat` `tools` field).
- UI: skill chips under chat input.
- `domain/repository/SkillRepository.kt` + impl.
- `skills/` package with one file per skill.

**Addresses:** FEATURES §Agent Skills Lite (P2).

**Avoids:** Gallery's full Agent Skills (JS webview, native intents, MCP) — Anti-Pattern A12, A13. PROJECT.md explicitly defers "autonomous tool use".

**Research flag:** Needs `--research-phase` to verify `ToolProvider` Kotlin API surface in LiteRT-LM 0.13.1 (FEATURES §Open Q #2, ARCHITECTURE §Open Q #4) — this is the gating question for the whole phase.

### Deferred to v2.1+

- **LM Studio MCP Bridge** [L]: separate phase. Requires MCP Kotlin SDK or custom JSON-RPC-over-HTTP client. Reuses the `ToolProvider` plumbing from Agent Skills Lite.
- **Speculative Decoding toggle** [S]: small but peripheral. Requires `capabilities: ["speculative_decoding"]` in allowlist. Land in Phase 40 if time.
- **Benchmark history viewer** [S]: needs accumulated benchmark data. Land after Phase 41 ships and users have 1+ weeks of results.
- **Deep links** (`warped://chat/<id>`) [M]: nice-to-have. Phase 40's type-safe nav migration makes it easy.
- **Gallery `Model.runtimeHelper` extension property** [S]: cosmetic. Skip unless pattern is widely useful.

### Excluded (Anti-Features, also excluded in v2.0)

Per FEATURES §Anti-Features: Ask Image, Audio Scribe, Tiny Garden, Mobile Actions (native intents), Scheduled Notifications, JS webview skills, Community Skills marketplace, AICore system service, "Best for" model pinning, Multi-tab browser, Public trending scraping, Remote allowlist hosting, iOS feature parity.

### Phase Ordering Rationale

- **Phase 40 is keystone** — every other phase depends on `LlmModelHelper`. Must land first. Even Phase 43 (Performance) needs the interface signature defined to do the right `Lazy<LiteRtLlmEngine>` refactor (PITFALLS Performance Traps row 1).
- **Phase 43 is parallel** — it touches engine plumbing, manifest, and Compose state but NOT the new `LlmModelHelper` surface. Can start as soon as Phase 40's interface is defined (could be day 3 of Phase 40). Hot path: `EngineManager.getCachedModelPath()` deletion, manifest updates, Compose state split.
- **Phase 41 and 42 are independent** — both depend on Phase 40 but not on each other. Can run in parallel.
- **Phase 44 is optional** — XL scope, defer to v2.1 if v2.0 timeline is tight. LiteRT-LM 0.13.1 ToolProvider API verification is the gating question (FEATURES §Open Q #2).
- **Build order also respects data layer**: Room migration (Phase 41 schema work) must be the first thing touching `@Database` — schema changes are not reversible. Set `exportSchema = true` immediately in Phase 40, commit `schemas/` to git, even if no schema change yet.

### Research Flags

Phases needing deeper research during planning (`/gsd-plan-phase --research-phase`):
- **Phase 40:** EngineConfig `cacheDir` parameter exact name; `Message.thinking` accessor; Hilt `@Binds` scoping for the new interface.
- **Phase 41:** LM Studio `reasoning_content` JSON path; WorkManager `setForeground()` reliability on Xiaomi/Oppo ROMs; `@AutoMigration` schema design.
- **Phase 43:** Compose 1.11 strong-skipping behavior with `@Immutable`; Macrobenchmark setup; `EngineManager.handleTrimMemory` after cache refactor.
- **Phase 44:** LiteRT-LM 0.13.1 `ToolProvider` Kotlin API surface; LM Studio `/api/v1/chat` `tools` field schema.

Phases with standard patterns (skip `--research-phase`):
- **Phase 42 (Prompt Lab):** Well-documented Gallery pattern. 5–8 template content is the only design question; no API research needed.

---

## Gallery Anti-Patterns to NOT Copy

These are Gallery's tech-debt items Warped must explicitly reject. Each is grounded in PITFALLS §Gallery Anti-Patterns and STACK §What NOT to Add.

| # | Anti-Pattern | Why Gallery Has It | Why Warped Skips |
|---|--------------|--------------------|------------------|
| **A1** | kapt for Hilt compiler | Legacy pre-Hilt 2.48 (Dec 2023) | Hilt KSP stable since 2.48. Warped is KSP-only. Adding kapt = 2–5× slower builds. |
| **A2** | Three JSON libs (kotlinx-serialization + Moshi + Gson) | Firebase uses Gson; HF OAuth uses Moshi | Warped has neither. kotlinx-serialization only. 4+ MB APK savings. |
| **A3** | `kotlin-reflect:2.2.21` | Pulled by `compose-richtext` | Warped doesn't use `compose-richtext`. 2.5 MB savings. |
| **A4** | Firebase BOM + Analytics + Messaging | Gallery uses for analytics + push | PROJECT.md §"Out of Scope" excludes Firebase/cloud sync. Zero Firebase. |
| **A5** | Ktor 3.4.3 + MCP Kotlin SDK 0.8.0 | HF + MCP use Ktor | Warped has Retrofit + OkHttp. Adopting MCP SDK would force Ktor. PROJECT.md scopes MCP via LM Studio REST, not the SDK. |
| **A6** | `compose-richtext` + `commonmark` | Gallery's markdown renderer | Warped has custom `MarkdownText` (v1.6). Per-block composables are critical. |
| **A7** | Proto DataStore (5 instances) | Benchmark results, user data, cutout collection, skills | Warped has Preferences DataStore + Room. Add Proto only for deeply nested schemas. Use Room for benchmark history. |
| **A8** | CameraX 1.4.2 | "Ask Image" task (multimodal) | PROJECT.md §"Out of Scope" — text-only. Zero CameraX. |
| **A9** | AICore system service + `mlkit-genai-prompt` | Pixel 8+ system LLM | Pixel-only, preview, narrow support. v2 deferred in REQUIREMENTS.md. |
| **A10** | `play-services-tflite-*` | Legacy TFLite for older devices | Warped uses LiteRT-LM directly via AAR. v1.5 already removed TFLite. |
| **A11** | AppAuth 0.11.1 | HF OAuth | Warped has no OAuth. HF models are public; LM Studio uses API keys (EncryptedSharedPreferences). |
| **A12** | JS webview skill runtime (full Skills) | "Spin a wheel", "show a map" demos | Heavy (200–500ms init per skill), XSS surface. Agent Skills Lite is Kotlin-only. |
| **A13** | `com.google.mlkit:genai-prompt` | AICore backend | Same as A9. Pixel-only. |
| **A14** | mmap cache shared across all models (no version namespacing) | `cacheDir = context.cacheDir.path` | LiteRT-LM bumps break cache. Warped must namespace by `BuildConfig.LITERTLM_VERSION`. |
| **A15** | `android:configChanges` missing `uiMode` | Toggling dark mode recreates Activity | Compose can re-theme. 100–300ms savings. Add `uiMode|orientation|screenSize|smallestScreenSize|screenLayout`. |
| **A16** | `extractNativeLibs="true"` | "Easier debugging" | 10–20 MB APK bloat. `extractNativeLibs="false"` is AGP 8+ default. |

**Verification (Phase 40 exit criterion):**
```bash
# Must all return empty:
./gradlew :app:dependencies --configuration kapt | grep -v "^$"
./gradlew :app:dependencies | grep -E "(firebase|moshi|gson|kotlin-reflect|ktor|mcp|tflite|mlkit-genai|appauth|compose-richtext|cameraX|datastore.*proto)"
# Must be present in libs.versions.toml:
grep "litertlm-android" gradle/libs.versions.toml
grep "core-splashscreen" gradle/libs.versions.toml
grep "lifecycle-process" gradle/libs.versions.toml
# Must be present in AndroidManifest.xml:
grep "uses-native-library" app/src/main/AndroidManifest.xml
grep "configChanges.*uiMode" app/src/main/AndroidManifest.xml
grep "extractNativeLibs.*false" app/src/main/AndroidManifest.xml
```

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack versions | **HIGH** | All verified against Google Maven, GitHub releases, Compose BOM mapping. Fetched within 24h of research. |
| Feature surface mapping (Gallery → Warped) | **HIGH** | Direct repo exploration: README, `model_allowlist.json`, `LlmModelHelper.kt`, `Model.kt`, `customtasks/*/`, `ui/*/`, `mcp/README.md`, `skills/README.md`. |
| Architecture refactor targets | **HIGH** | Warped source files directly inspected (line counts, signatures). Gallery patterns verified. |
| LiteRT-LM 0.13.1 API surface | **HIGH** | Engine.initialize, cacheDir, Backend.GPU, MessageCallback, ToolProvider all confirmed in Maven listing + official docs. |
| LiteRT-LM 0.12 → 0.13 file format breaking change | **MEDIUM** | One open issue (#2454) confirmed. Other breaking changes may emerge — needs regression smoke tests for all v1.8-era `.litertlm` files. |
| Compose 1.11 strong-skipping impact | **MEDIUM** | Documented in dev.to article + Compose 1.11 release notes. Migration cost depends on how many `List<T>` params exist in Warped — needs Layout Inspector audit. |
| `Message.thinking` exact accessor name | **MEDIUM** | Inferred from Gallery's `ResultListener` typealias. Actual field name on `Message` should be verified in the AAR. |
| LM Studio `reasoning_content` exact JSON path | **LOW-MEDIUM** | OpenAI-compatible convention. Verify in LM Studio's API docs or via integration test before promising Thinking Mode for remote. |
| `ToolProvider` Kotlin API stability | **MEDIUM** | Visible in `LlmModelHelper.kt` interface. Specific API surface should be verified before committing to Agent Skills Lite scope. |
| Performance impact estimates (mmap 5–10×, SQLCipher 10–30%) | **LOW** | Well-documented in respective docs but not measured on Warped hardware. Verify with Macrobenchmark in Phase 43. |
| WorkManager reliability on Chinese OEM ROMs | **LOW** | Anecdotal from Gallery's open issues. Needs real-device test in Phase 41. |

**Overall confidence:** **HIGH** for the strategic direction (what to port, what to skip, what to defer). **MEDIUM** for the precise implementation details that will be resolved in Phase 40's research-phase. **LOW** for performance impact numbers, which need Macrobenchmark validation.

### Gaps to Address During Implementation

1. **LiteRT-LM 0.13.1 `EngineConfig.cacheDir` exact parameter name** (Phase 40, ARCHITECTURE §Open Q #1) — verify in AAR source.
2. **`Message.thinking` accessor in LiteRT-LM 0.13.1** (Phase 40, FEATURES §Open Q #1) — `message.thinking` vs `message.channels["thought"]` vs other.
3. **LiteRT-LM 0.13.1 `ToolProvider` Kotlin API** (Phase 44 gating, FEATURES §Open Q #2) — does the local engine actually wire `tools`, or is it AICore-only?
4. **`LlmModelHelper` `ResultListener` suspend vs sync** (Phase 40, ARCHITECTURE §Open Q #2) — for Flow backpressure.
5. **LM Studio `reasoning_content` exact JSON path** (Phase 41, FEATURES §Open Q #1) — top-level vs nested in `message`.
6. **WorkManager `setForeground()` reliability** on Xiaomi/Oppo/ColorOS ROMs (Phase 41, PITFALLS Critical #12) — needs real-device test matrix.
7. **Compose 1.11 strong-skipping behavior** with `@Immutable` annotations (Phase 43, ARCHITECTURE §Open Q #6).
8. **Macrobenchmark baseline numbers** on Pixel 7 (Phase 43, PITFALLS "Looks Done" #13, #14) — required for Performance exit criteria.
9. **Model allowlist schema subset** (Phase 40, FEATURES §Open Q #6) — full Gallery schema copy or Warped-specific leaner subset.
10. **Prompt Lab template content** (Phase 42, FEATURES §Open Q #7) — 5–8 templates selection.
11. **SQLCipher threat model re-evaluation** (Phase 43, PITFALLS Security row 7) — measure first, decide removal.

---

## Sources (Aggregated)

### Primary (HIGH confidence)

**Gallery source tree** (verified 2026-06-05):
- [google-ai-edge/gallery](https://github.com/google-ai-edge/gallery) — main branch, versionCode 34, 1.0.16
- [`runtime/LlmModelHelper.kt`](https://raw.githubusercontent.com/google-ai-edge/gallery/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/runtime/LlmModelHelper.kt) — interface signature
- [`ui/llmchat/LlmChatModelHelper.kt`](https://raw.githubusercontent.com/google-ai-edge/gallery/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatModelHelper.kt) — 340-line impl wrapping Engine + Conversation
- [`data/Model.kt`](https://raw.githubusercontent.com/google-ai-edge/gallery/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/data/Model.kt) — Model data class with capabilities, runtimeHelper
- [`model_allowlists/1_0_15.json`](https://github.com/google-ai-edge/gallery/raw/refs/heads/main/model_allowlists/1_0_15.json) — versioned allowlist schema
- [`libs.versions.toml`](https://github.com/google-ai-edge/gallery/blob/main/Android/src/gradle/libs.versions.toml) — confirmed kapt, Gson, Moshi, kotlin-reflect, Firebase, Ktor, MCP, TFLite, mlkit-genai-prompt, AppAuth, CameraX, compose-richtext, commonmark, Proto DataStore
- [`AndroidManifest.xml`](https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/AndroidManifest.xml) — configChanges, uses-native-library, splash screen

**Library version sources:**
- [LiteRT-LM Maven metadata](https://dl.google.com/android/maven2/com/google/ai/edge/litertlm/litertlm-android/maven-metadata.xml) — 0.13.1 latest 2026-06-04
- [Compose BOM mapping](https://developer.android.com/jetpack/compose/bom/bom-mapping) — 2026.05.01 = Compose 1.11.0
- [Compose Testing v2 migration](https://developer.android.com/develop/ui/compose/testing/migrations/testing-v2)
- [Android Developers Blog — R8 Keep Rules](https://developer.android.com/blog/posts/configure-and-troubleshoot-r8-keep-rules) (Nov 2025)
- [Android Developer Guide — Migrate Room DB](https://developer.android.com/training/data-storage/room/migrating-db-versions)
- [LiteRT-LM Android getting started](https://developers.google.com/edge/litert-lm/android) — `Backend.GPU()`, `cacheDir`, Engine.initialize() blocking warning
- [LiteRT-LM v0.13 release notes](https://github.com/google-ai-edge/LiteRT-LM/releases) — ToolProvider, MTP, speculative decoding
- [LiteRT-LM open issue #2454](https://github.com/google-ai-edge/LiteRT-LM/issues/2454) — 0.12 → 0.13 file format breaking change
- [Dagger Hilt KSP support](https://dagger.dev/dev-guide/ksp) — stable since Hilt 2.48
- [Hilt 2.59 release notes](https://github.com/google/dagger/releases) — AGP 9+ requirement

### Secondary (MEDIUM confidence)

- [SoftwareDevs mvpfactory.io — Compose Recomposition at Scale](https://dev.to/software_mvp-factory/jetpack-compose-recomposition-at-scale-how-strong-skipping-mode-changes-the-stability-rules-you-4a80) (Mar 2026)
- [Davide Agostini — Hilt Deep Dive](https://www.davideagostini.com/android/2026-02-18-hilt-di-deep-dive) (Feb 2026)
- [Davide Agostini — Baseline Profiles](https://www.davideagostini.com/android/2026-02-25-baseline-profiles) (Feb 2026)
- [Google Developers Blog — Blazing fast on-device GenAI with LiteRT-LM](https://developers.googleblog.com/blazing-fast-on-device-genai-with-litert-lm/) (May 2026)
- [Google Cloud Blog — Benchmark LLMs on-device with AI Edge Portal](https://cloud.google.com/blog/products/ai-machine-learning/benchmark-llms-on-device-with-ai-edge-portal) (May 2026)
- [StackOverflow — kotlinx-serialization ProGuard rules](https://stackoverflow.com/questions/70663076/how-to-make-proguard-keep-kotlinx-serializers-for-objects)

### Warped internal (HIGH confidence)

- [PROJECT.md](.planning/PROJECT.md) — v2.0 milestone, scope, out-of-scope boundaries
- [REQUIREMENTS.md](.planning/REQUIREMENTS.md) — v1.8 feature surface, v2 deferred list
- [STACK.md](.planning/research/STACK.md) — confirmed stack (Kotlin 2.3.20, AGP 9.2.1, Hilt 2.59.2, etc.)
- [FEATURES.md](.planning/research/FEATURES.md) — v2.0 feature plan + Gallery surface map
- [ARCHITECTURE.md](.planning/research/ARCHITECTURE.md) — refactor candidates + side-by-side comparison
- [PITFALLS.md](.planning/research/PITFALLS.md) — critical pitfalls + Gallery anti-patterns

---

*Research completed: 2026-06-05*
*Ready for requirements definition: yes — v2.0 phase plan (40–44) is concrete, dependencies are mapped, and Gallery anti-patterns are explicitly enumerated for rejection.*
