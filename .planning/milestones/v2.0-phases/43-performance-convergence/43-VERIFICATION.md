# Phase 43 — Performance Convergence

**Status:** ✅ Complete
**Plans executed:** 4/4
**Plans:** 43-01 (Compose), 43-02 (DI/network), 43-03 (data/lifecycle/splash), 43-04 (macrobench + docs)
**Requirements:** PERF-01..13 (partial — see Notes)

## Plan-by-plan

### 43-01: Compose (PERF-02, PERF-03, PERF-04)
- **PERF-04**: `ChatScreen.kt` wraps `uiState.trafficLightState()` in `derivedStateOf` so single-character streaming updates do not re-derive the model-selector traffic light.
- **PERF-03**: Extracted `MessageImageStack` from `MessageBubble` as a private composable. Image bitmap `remember(dataUrl)` cache lives in its own composable scope, so streaming-text recomposition no longer churns the cache.
- **PERF-02**: `ChatScreen` now keeps three `PersistentList` views (`messages`, `localModels`, `endpoints`) at the screen boundary via `remember(uiState.X) { uiState.X.toPersistentList() }`, enabling Compose 1.11 strong-skipping.
- **`compileDebugKotlin` ✅.**

### 43-02: DI + network (PERF-07, PERF-08)
- **PERF-08**: `NetworkModule.kt` provides a new `@Named("sse")` `OkHttpClient` singleton with `retryOnConnectionFailure(false)`, `callTimeout(60.seconds)`, `readTimeout(0)` (SSE), 50MB on-disk cache, and a no-store `Cache-Control` interceptor. The default `OkHttpClient` (used for model downloads + REST) is unchanged.
- **PERF-07**: Hilt scoping audit passed — every `@Provides` in `Database`, `Network`, `Security`, `Inference`, and `PromptLab` modules is `@Singleton`. No eager `@Singleton` binding of `LiteRtLlmEngine` exists. `ProviderRouter` uses `dagger.Lazy<>` for the per-helper injection.
- **Carry-over fix**: Removed the dead `activeCall: AtomicReference<Call?>` field from `LmStudioHelper` (it was never assigned). The actual `Call` lives inside `LMStudioProvider`; cancellation today is effective at the Flow level via `activeJob.cancel()`. Tracked for v2.1.
- **`compileDebugKotlin` ✅.**

### 43-03: Data, lifecycle, splash (PERF-09, PERF-10, PERF-11)
- **PERF-09**: `MessageEntity.indices` is now `[Index(value = ["conversation_id", "created_at"])]`. The Room `@AutoMigration(from = 12, to = 13)` regenerates from the schema diff (drops the old single-column index, creates the composite one). `AppDatabase` bumped to v13.
- **PERF-10**: New `AppLifecycleProvider` interface backed by `ProcessLifecycleOwner` (via `DefaultLifecycleObserver`). Hilt-bound in `AppModule`. `ModelDownloadWorker` reads `isAppInForeground` before calling `setForeground` — when the user is already in the app looking at the in-app progress UI, the system notification is suppressed.
- **PERF-11**: `MainActivity` adds a 200ms cross-fade exit animation listener on the splash view (`ObjectAnimator` alpha 1→0 + `remove()`). The existing `installSplashScreen()` call stays. Splash is system-managed on Android 12+.
- **`assembleDebug` ✅.**

### 43-04: Macrobenchmark + BENCHMARKS.md (PERF-12, PERF-13)
- **PERF-12**: `ColdStartBenchmark` and `StreamingFrameBenchmark` ship in `:app/src/androidTest/java/com/warped/benchmark/`. The modern AGP 9.0 pattern (macros in `androidTest`) replaces a standalone `:benchmark` module that tripped on dynamic-feature configuration.
- **PERF-13**: `BENCHMARKS.md` documents the procedure and target numbers for cold-start, warm-start, streaming frame rate, peak memory, SQLCipher overhead, Compose recomposition, and Hilt startup. All actual numbers are placeholders — they require a real device to measure and are intended to be filled in by CI.
- **`compileDebugAndroidTestKotlin` ✅.**

## Notes / carry-overs

- **PERF-01 (full `ChatUiState` sub-state split)**: Deferred. The current single-state shape is consumed by ~10 subcomposables and the split requires coordinated changes in `ChatViewModel` + `ChatScreen` + each subcomposable. Tracked for v2.1.
- **PERF-05 (full hoisted-state audit)**: `codeTheme` + `codeFontScale` were already hoisted in 41-02. `attachedImages` is the only local state in `ChatScreen` — it stays in the parent because the `OutlinedTextField` needs it. No change needed.
- **PERF-06 (LazyColumn `key`)**: `ChatScreen` uses a vertically-scrolling `Column` (not `LazyColumn`) for messages. TODO: switch to `LazyColumn` with `key = { it.id }` if message counts grow past 100 per conversation.
- **PERF-12/13 actual numbers**: Cannot be measured without a real device. Code and procedure ship; CI fills in the cells.

## Commits

- `48d6166` (docs)
- `d568fa8` (43-01): derivedStateOf + MessageImageStack + PersistentList
- `0d3d9b2` (43-02): SSE OkHttp + LmStudioHelper dead-code cleanup
- `b749d25` (43-03): Room composite index + AppLifecycleProvider + splash cross-fade
- `c378e2e` (43-04): macrobenchmark androidTest + BENCHMARKS.md
