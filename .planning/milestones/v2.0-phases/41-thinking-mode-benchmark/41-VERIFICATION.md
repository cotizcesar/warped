# Phase 41 — Thinking Mode + Model Benchmark

**Status:** ✅ Complete
**Plans executed:** 5/5
**Plans:** 41-01 (refactor), 41-02 (UI), 41-03 (Room), 41-04 (Worker), 41-05 (Screen)
**Requirements:** THINK-01..07 ✅, BENCH-01..06 ✅

## Plan-by-plan

### 41-01: refactor — thread enableThinking through helpers
- `LiteRtLlmHelper.kt`: `runInference(..., enableThinking)` copies it into `request.parameters.copy(reasoningEnabled=...)`. Strips `<think>`/`</think>` tags from streamed content when disabled.
- `LmStudioHelper.kt`: same threading. Added `stripThinkTags()` companion helper.
- Fixed pre-existing compile errors uncovered during execution:
  - `InferenceModule.kt`: `EngineManager` provider was missing `cacheManager: LiteRtLmCacheManager` parameter.
  - `Screen.kt`: removed `import androidx.navigation.NavKey` (Nav 3 only; project uses Nav 2.9).
- `compileDebugKotlin` ✅.

### 41-02: UI — chat chip + collapsible thinking panel
- `AdvancedPreferences.kt`: `KEY_THINKING_ENABLED` (booleanPreferencesKey, default false), `thinkingEnabled: Flow<Boolean>`, `setThinkingEnabled(Boolean)`.
- `ChatUiState.kt`: `enableThinking: Boolean = false` + `supportsThinking: Boolean = false`.
- `ChatViewModel.kt`: collect thinkingEnabled, derive `supportsThinking` from `ModelAllowlistRepository.supportsThinking(modelId)`, added `toggleThinking()`, pass `enableThinking && supportsThinking` to `runInference`.
- `ChatScreen.kt`: pass new state into existing `ChatInputBar` parameters.
- `MessageBubble.kt`: already had the collapsible Thinking panel from v1.6 (no change needed).
- `compileDebugKotlin` ✅.

### 41-03: Room — BenchmarkResult + auto migration v11→v12
- New `domain/model/BenchmarkResult.kt` + `BenchmarkConfig.kt`.
- New `db/entity/BenchmarkResultEntity.kt` (indices on `modelId`, `createdAt`).
- New `db/dao/BenchmarkResultDao.kt` (insert, observeAll, observeByModel, count).
- New `db/entity/BenchmarkResultMappers.kt` (entity ↔ domain).
- `AppDatabase.kt`: version 11→12, `exportSchema = true`, `autoMigrations = [AutoMigration(11, 12)]`, added `BenchmarkResultEntity` + `benchmarkResultDao()`.
- Bootstrapped v11 schema by temporarily exporting, then committed v12.
- New `domain/repository/BenchmarkRepository.kt` + `data/repository/BenchmarkRepositoryImpl.kt`.
- Wired in `RepositoryModule.kt` + `DatabaseModule.kt`.
- New `androidTest/MigrationTest.kt`: round-trips 1 conversation + 50 messages through v11→v12 and asserts they survive.
- `app/build.gradle.kts`: moved `room-testing` from `testImplementation` to `androidTestImplementation` (where it actually works).
- **Pre-existing Hilt fix:** `LlmHelperModule.kt` was binding `LmStudioHelper` as `LlmModelHelper` (interface) but `ProviderRouter` injects the concrete `LmStudioHelper` to call `setEndpoint()`. Added second `@Binds` for the concrete type. (Caught by `assembleDebug`, not `compileDebugKotlin`.)
- `assembleDebug` ✅, `compileDebugAndroidTestKotlin` ✅, schema files 11.json + 12.json present (gitignored).

### 41-04: Worker — ModelBenchmarkWorker + scheduler
- New `data/local/benchmark/ModelBenchmarkWorker.kt` (@HiltWorker + @AssistedInject): measures init, runs N trials against `assets/benchmark_prompt.txt`, calculates prefill tok/s, decode tok/s, peak memory, persists via `BenchmarkRepository`.
- New `BenchmarkScheduler.kt` (@Singleton): wraps `WorkManager.enqueueUniqueWork` with `Constraints(BATTERY_NOT_LOW)` and `ExistingWorkPolicy.REPLACE`.
- New `MemorySampler.kt`: 100ms-interval poller of JVM + native heap.
- New `BenchmarkNotifier.kt`: low-importance foreground notification with progress.
- New `assets/benchmark_prompt.txt`: ~512-token distributed-systems essay for repeatable decode measurements.
- Worker falls back to a synthetic 64× "quick brown fox" prompt if the asset is missing, so it can be enqueued from tests/dev.
- `assembleDebug` ✅.

### 41-05: UI — BenchmarkScreen + ViewModel + sparkline
- `BenchmarkViewModel` (@HiltViewModel): combines `observeModels`, `BenchmarkRepository.observeAll`, `WorkManager.getWorkInfosForUniqueWorkFlow` into `StateFlow<BenchmarkUiState>`.
- `BenchmarkScreen`: Scaffold + TopAppBar, ModelDropdown, BenchmarkConfigCard, Start button, BenchmarkResultsViewer.
- `BenchmarkConfigCard`: temperature (0-2), topK (1-100), maxTokens (64-2048) sliders + 1-10 trials stepper.
- `BenchmarkResultsViewer`: cards per result (init / prefill / decode / peak memory) with timestamp; per-model sparkline of last 10 decodeTokPerSec.
- `BenchmarkValueSeriesViewer`: Compose Canvas sparkline.
- `NavGraph.kt`: `composable<Screen.Benchmark>` now invokes `BenchmarkScreen()` (not the placeholder).
- `PlaceholderScreens.kt`: removed `BenchmarkPlaceholderScreen`; `PromptLabPlaceholderScreen` retained for Phase 42.
- `assembleDebug` ✅.

## Risks / Carry-overs
- **Pre-existing carry-overs still pending** (deferred to Phase 43 PERF):
  1. `LlmModelHelper.runInference` is double-collected when there are concurrent `enableThinking` / `reasoningEnabled` paths. (Fixed in 41-01 for the new path; the legacy `reasoningActive`/`modelMayThink` block in `sendMessage` is now dead code.)
  2. `LmStudioHelper.activeCall` `AtomicReference<Call?>` is never assigned, so `stopResponse()` cannot actually cancel an in-flight OkHttp call.
- `ChatUiState.reasoningEnabled` field is now redundant with `enableThinking`. Kept for backward compat; safe to remove in a future cleanup.

## Verification commands run

- `./gradlew :app:compileDebugKotlin` — passes (with pre-existing `hiltViewModel` deprecation warning).
- `./gradlew :app:assembleDebug` — passes.
- `./gradlew :app:compileDebugAndroidTestKotlin` — passes (MigrationTest compiles; runtime needs a device).
- `app/schemas/com.warped.data.local.db.AppDatabase/11.json` + `12.json` generated and present (gitignored).

## Commits

- `c94f5cc` docs(41): plan + UI spec for 5 plans
- `6b78037` refactor(41-01): thread enableThinking through helpers + filter reasoning at boundary
- `2750ff5` feat(41-02): thinking toggle wired to allowlist + DataStore
- `756f03f` feat(41-03): BenchmarkResult Room table + auto migration v11→v12
- `f973f85` feat(41-04): ModelBenchmarkWorker + BenchmarkScheduler + MemorySampler
- `2026c3e` feat(41-05): BenchmarkScreen + ViewModel + sparkline results viewer
