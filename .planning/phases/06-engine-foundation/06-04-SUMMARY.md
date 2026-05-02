---
phase: 06-engine-foundation
plan: 04
subsystem: inference-engine
tags: [engine-manager, memory-checker, mutual-exclusion, litertlm, pol-01, pol-02]
requires:
  - 06-01 (litertlm-android Maven dependency)
  - 06-03 (BackendDetector + LiteRTLmEngine)
provides:
  - EngineManager mutual exclusion between llama.cpp and LiteRT-LM
  - MemoryChecker .litertlm RAM warning
affects: []
tech-stack:
  added: []
  patterns:
    - "@Synchronized engine lifecycle coordination via EngineManager"
    - "try/catch engine teardown with finally-block state reset"
    - "Hilt @Provides wiring for multi-dependency singletons"
key-files:
  created:
    - app/src/main/java/com/warped/data/local/inference/EngineManager.kt
  modified:
    - app/src/main/java/com/warped/data/local/inference/MemoryChecker.kt
    - app/src/main/java/com/warped/di/InferenceModule.kt
decisions:
  - "D-15: EngineManager @Singleton managing LlamaEngine + LiteRTLmEngine — only one local engine loaded at a time"
  - "D-16: Synchronous engine switch via unloadCurrent() before init — @Synchronized prevents concurrent switches"
  - "ActiveEngine data class tracks type + modelPath + backend for dedup on redundant switch calls"
  - "BackendDetector.probeBackend() called lazily inside switchToLiteRT() — cached result via @Volatile"
metrics:
  duration: "1m 20s"
  completed_date: "2026-05-02T16:44:16Z"
  task_count: 3
  file_count: 3
---

# Phase 6 Plan 4: EngineManager + MemoryChecker updates (POL-01, POL-02)

**One-liner:** EngineManager enforces mutual exclusion between llama.cpp and LiteRT-LM engines with @Synchronized lifecycle coordination; MemoryChecker extended with .litertlm-specific RAM warnings.

## Tasks Completed

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 | Create EngineManager enforcing mutual exclusion | `52b6425` | `EngineManager.kt` (new) |
| 2 | Extend MemoryChecker with .litertlm model RAM warning (POL-02) | `63c2e45` | `MemoryChecker.kt` (modified) |
| 3 | Register EngineManager in InferenceModule | `63df101` | `InferenceModule.kt` (modified) |

## What Was Built

### Task 1: EngineManager.kt
Created `@Singleton class EngineManager` that coordinates `LlamaEngine` and `LiteRTLmEngine` lifecycle. Key design elements:

- **`enum class EngineType { LLAMA_CPP, LITE_RT_LM }`** — Exactly two engine types.
- **`data class ActiveEngine(type, modelPath, backend?)`** — Tracks currently loaded engine state; `backend` is null for llama.cpp which has no backend concept.
- **`switchToLiteRT(modelPath)`** — Calls `unloadCurrent()` first, then auto-detects backend via `BackendDetector.probeBackend()`, then calls `liteRTLmEngine.init()`. Skips redundant switches if same engine+model already loaded.
- **`switchToLlama(modelPath)`** — Calls `unloadCurrent()` first, then `llamaEngine.loadModel()`. Throws `IllegalStateException` if load fails before setting `activeEngine` (prevents inconsistent state).
- **`unloadCurrent()`** — Type-safe: calls `llamaEngine.stop()` + `llamaEngine.unload()` for LlamaCPP, `liteRTLmEngine.close()` for LiteRT-LM. Wrapped in try/catch for resilience; `activeEngine = null` in finally block.
- **All public methods `@Synchronized`** — Prevents concurrent engine switches and double-load memory exhaustion (D-16, T-06-10).

### Task 2: MemoryChecker.kt Extension (POL-02)
Added two methods to the existing `MemoryChecker` class:

- **`canLoadLitertlmModel(modelSizeBytes): Boolean`** — Delegates to existing `canLoadModel()` (80% threshold). Separate method allows future differentiation if LiteRT-LM has different RAM overhead characteristics.
- **`getLitertlmMemoryWarning(modelSizeBytes): String?`** — Two-tier warning system:
  - **80% threshold**: "Warning: This model (X MB) may not fit in available RAM (Y MB available)..." — blocking warning
  - **60% threshold**: "Note: This model (X MB) uses Z% of available RAM (Y MB)..." — advisory note
  - Returns `null` if no warning needed

Existing methods (`getMemoryInfo`, `canLoadModel`, `shouldWarn`) are unchanged and continue to serve GGUF models.

### Task 3: InferenceModule.kt Hilt Registration
Added `@Provides @Singleton fun provideEngineManager(llamaEngine, liteRTLmEngine, backendDetector): EngineManager` to the existing `InferenceModule`. Hilt resolves all three constructor dependencies from existing `@Provides` methods in the module. No existing providers were modified.

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None. All methods are fully implemented with no placeholders, TODOs, or hardcoded empty values.

## Threat Flags

None. All security surface is covered by the plan's threat model (T-06-10 through T-06-13).

## Self-Check

- [x] `EngineManager.kt` exists at `app/src/main/java/com/warped/data/local/inference/EngineManager.kt`
- [x] `MemoryChecker.kt` has `canLoadLitertlmModel` at line 48
- [x] `InferenceModule.kt` has `provideEngineManager` at line 50
- [x] Commit `52b6425` exists (Task 1)
- [x] Commit `63c2e45` exists (Task 2)
- [x] Commit `63df101` exists (Task 3)

## Self-Check: PASSED
