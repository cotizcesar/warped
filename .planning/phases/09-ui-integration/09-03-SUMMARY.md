---
phase: 09-ui-integration
plan: 03
type: execute
wave: 1
subsystem: ui/models, data/inference
tags: [format-badge, cache-dir, lifecycle, memory-management, litertlm]
depends_on: []
requires: []
provides: ModelsScreen format badges, format-aware routing, cacheDir caching, ComponentCallbacks2 lifecycle
affects: []
tech-stack:
  added: []
  patterns: [cacheDir caching, size-mismatch detection, ComponentCallbacks2 delegation, injection guard]
key-files:
  created: []
  modified:
    - app/src/main/java/com/warped/ui/models/ModelsScreen.kt
    - app/src/main/java/com/warped/ui/models/ModelsViewModel.kt
    - app/src/main/java/com/warped/data/local/inference/EngineManager.kt
    - app/src/main/java/com/warped/WarpedApplication.kt
    - app/src/main/java/com/warped/di/InferenceModule.kt
decisions:
  - "Cache integrity check: size-mismatch detection prevents corrupt/partial cache usage"
  - "handleTrimMemory only triggers on TRIM_MEMORY_RUNNING_CRITICAL (level 15) — lower levels ignored"
  - "WarpedApplication uses ::engineManager.isInitialized guard to prevent NPE before Hilt injection"
  - "Cache directory: context.cacheDir/litertlm_cache/ — app-private, survives restart but may be cleared by system"
  - "ModelsViewModel.useLocalModel falls back to ProviderType.LOCAL for unknown modelFormat"
metrics:
  duration: "~3 min"
  tasks: 2
  files_modified: 5
  total_commits: 2
  completed_date: 2026-05-02
---

# Phase 09 Plan 03: ModelsScreen Badges + Caching + Lifecycle Summary

**One-liner:** Added format badges to downloaded model cards on ModelsScreen, wired format-aware routing in ModelsViewModel, implemented cacheDir-based model caching in EngineManager for faster LiteRT-LM loads, and registered ComponentCallbacks2 in WarpedApplication for lifecycle-aware memory management.

## Tasks

### Task 1: Format badge on ModelCard + format-aware routing
- **Commit:** `e6bcea8`
- **Changes:** 2 files (ModelsScreen.kt, ModelsViewModel.kt)
- **What:**
  - ModelsScreen: added `FormatBadge` composable (blue GGUF, green LiteRT-LM) displayed below model name in each `ModelCard`
  - Updated "Add Model" dialog text: "Browse and download GGUF & LiteRT-LM models", "Import Model File", "Load a .gguf or .litertlm model from your device"
  - ModelsViewModel: `useLocalModel()` now routes by `model.modelFormat` — `"LITERTLM"` → `ProviderType.LITE_RT_LM`, everything else → `ProviderType.LOCAL`

### Task 2: CacheDir caching + lifecycle memory management
- **Commit:** `0f6bc28`
- **Changes:** 3 files (EngineManager.kt, WarpedApplication.kt, InferenceModule.kt)
- **What:**
  - EngineManager:
    - Added `@ApplicationContext context: Context` constructor parameter
    - `switchToLiteRT()`: copies .litertlm model to `context.cacheDir/litertlm_cache/` on first init; reuses cached copy on subsequent loads
    - Size-mismatch check: if cached file length ≠ source file length, recopies (prevents corrupt/partial cache usage)
    - `handleTrimMemory(level)`: on `TRIM_MEMORY_RUNNING_CRITICAL`, unloads engine + deletes cache directory recursively
    - `getCachedModelPath()`: resolves cache path, creates directory if needed
  - WarpedApplication:
    - Implements `ComponentCallbacks2`
    - Injects `EngineManager` via Hilt field injection
    - Registers `registerComponentCallbacks(this)` in `onCreate()`
    - `onTrimMemory()`: delegates to `engineManager.handleTrimMemory(level)` with `::engineManager.isInitialized` guard
    - `onLowMemory()`: triggers `handleTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL)` with same guard
    - `onConfigurationChanged()`: no-op (required by interface)
  - InferenceModule: added `@ApplicationContext context: Context` parameter to `provideEngineManager()`

### Deviation: InferenceModule required context injection
- **Found during:** Task 2 compilation
- **Issue:** EngineManager's new `context` constructor parameter was not provided by Dagger
- **Fix:** Added `@ApplicationContext context: Context` to `provideEngineManager()` in InferenceModule.kt
- **Type:** [Rule 3 - Blocking Issue]

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking Issue] Added context parameter to InferenceModule**
- **Found during:** Task 2 compilation
- **Issue:** EngineManager constructor now requires `context: Context`, but `InferenceModule.provideEngineManager()` didn't pass it
- **Fix:** Added `@ApplicationContext context: Context` parameter to `provideEngineManager()` and passed it to the EngineManager constructor
- **Files modified:** `app/src/main/java/com/warped/di/InferenceModule.kt`
- **Commit:** `0f6bc28`

## Verification

- [x] `./gradlew :app:compileDebugKotlin` succeeds
- [x] ModelCard shows FormatBadge (GGUF blue, LiteRT-LM green)
- [x] useLocalModel() routes LITERTLM → LITE_RT_LM
- [x] Add Model dialog text mentions both GGUF and .litertlm
- [x] EngineManager caches .litertlm to cacheDir on first switchToLiteRT
- [x] Size-mismatch check prevents corrupt cache
- [x] handleTrimMemory unloads engine + clears cache
- [x] WarpedApplication implements ComponentCallbacks2
- [x] ::engineManager.isInitialized guard prevents NPE

## Self-Check: PASSED

- [x] ModelsScreen.kt contains FormatBadge composable and dialog text updates
- [x] ModelsViewModel.kt contains format-aware routing
- [x] EngineManager.kt contains caching logic and handleTrimMemory
- [x] WarpedApplication.kt implements ComponentCallbacks2
- [x] InferenceModule.kt provides context to EngineManager
- [x] Commit `e6bcea8` exists (Task 1)
- [x] Commit `0f6bc28` exists (Task 2)
- [x] Build compiles successfully
