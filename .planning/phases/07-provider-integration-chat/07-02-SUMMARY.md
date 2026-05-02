---
phase: 07-provider-integration-chat
plan: 02
subsystem: di-provider-routing
tags: [hilt, provider-router, lite-rt-lm, dependency-injection]
depends_on:
  - 07-01
requires:
  - LITE-05
provides:
  - Hilt singleton bindings for LiteRTLmProvider and InputSanitizer
  - ProviderRouter LITE_RT_LM branch wiring
affects:
  - InferenceModule
  - ProviderRouter
tech-stack:
  added: []
  patterns:
    - Hilt @Provides @Singleton factory methods
    - dagger.Lazy injection in ProviderRouter
key-files:
  created: []
  modified:
    - app/src/main/java/com/warped/di/InferenceModule.kt
    - app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt
decisions:
  - D-01: LiteRTLmProvider and InputSanitizer explicitly provided via @Provides (not relying on @Inject auto-discovery)
  - D-02: LiteRTLmProvider injected via dagger.Lazy in ProviderRouter (lazy init, consistent with LocalLlmProvider pattern)
metrics:
  duration: 2m 19s
  completed_date: 2026-05-02T17:48:15Z
  tasks: 2
  files: 2
---

# Phase 07 Plan 02: Hilt + ProviderRouter Wiring Summary

**One-liner:** LiteRTLmProvider and InputSanitizer wired into Hilt DI graph with ProviderRouter LITE_RT_LM branch routing via Lazy injection.

## Tasks Executed

### Task 1: Register LiteRTLmProvider and InputSanitizer in Hilt InferenceModule
- **Commit:** `9c629e0`
- **Action:** Added `provideInputSanitizer()` and `provideLiteRTLmProvider()` as `@Provides @Singleton` factory methods in `InferenceModule`
- **Key details:**
  - `provideInputSanitizer()`: zero-dependency factory, returns `InputSanitizer()`
  - `provideLiteRTLmProvider(engineManager, inputSanitizer)`: depends on `EngineManager` (already provided) and `InputSanitizer` (new)
  - Both follow existing module convention: explicit `@Provides` rather than relying on `@Inject` auto-discovery
  - Added imports for `InputSanitizer` and `LiteRTLmProvider`
  - Existing methods unchanged

### Task 2: Wire LITE_RT_LM branch in ProviderRouter with Lazy injection
- **Commit:** `8f55432`
- **Action:** Added `liteRTLmProvider: dagger.Lazy<LiteRTLmProvider>` constructor parameter and `ProviderType.LITE_RT_LM -> liteRTLmProvider.get()` branch in `resolve()`
- **Key details:**
  - Replaced TODO placeholder that was routing to `localLlmProvider.get().configure(modelId)`
  - Uses `dagger.Lazy` injection (same pattern as `localLlmProvider`) for lazy initialization
  - No `.configure(modelId)` call — LiteRTLmProvider gets model path from `EngineManager.getActiveEngine()`
  - All existing `when` branches unchanged

## Deviations from Plan

None — plan executed exactly as written.

## Verification

1. `./gradlew :app:compileDebugKotlin` — **BUILD SUCCESSFUL** (Hilt graph validates at compile time)
2. `grep -rl "LITE_RT_LM" app/src/main/java/` returns matches in 4 files:
   - `ProviderType.kt` (enum value, plan 07-01)
   - `LiteRTLmProvider.kt` (provider type override, plan 07-01)
   - `EngineManager.kt` (engine tracking, phase 06 — pre-existing)
   - `ProviderRouter.kt` (routing, plan 07-02 — newly added)
3. Hilt dependency graph validates: no missing bindings for `LiteRTLmProvider` or `InputSanitizer`
4. ProviderRouter can `Lazy`-load `LiteRTLmProvider` without early initialization

## Self-Check: PASSED

- `InferenceModule.kt` — FOUND, contains `provideInputSanitizer()` and `provideLiteRTLmProvider()`
- `ProviderRouter.kt` — FOUND, contains `liteRTLmProvider: dagger.Lazy<LiteRTLmProvider>` and `LITE_RT_LM -> liteRTLmProvider.get()`
- Commit `9c629e0` — FOUND
- Commit `8f55432` — FOUND
