# Quick 01: Vision-Backend GPU Fix — Summary

**Status:** complete
**Date:** 2026-09-28
**Plan:** `.planning/quick/20260928-vision-backend-gpu/PLAN.md`
**Requirement:** QUICK-VISION-GPU

## What was wrong

Device log 2026-09-28: loading vision-capable gemma-4-E2B-it failed with JNI
`INVALID_ARGUMENT` — "Vision backend constraint mismatch. Model requires one of
[gpu] but Vision backend is CPU". Two compounding defects in `EngineManager`:

1. `initWith` hardcoded `visionBackend = CPU` for every model, guaranteeing the
   JNI failure for any vision-capable `.litertlm` that requires GPU vision.
2. The constraint-mismatch retry was main-backend-only (`target.copy(backend =
   required)`), so a vision-slot error retried the identical configuration and
   failed the same way.

## Changes

**`app/src/main/java/com/warped/data/local/inference/EngineManager.kt`** (commit `09fe19d7`):
- New `BackendSlot` enum (`MAIN`/`VISION`/`AUDIO`) + pure `parseConstraintSlot`
  function: "vision backend" → VISION, "audio backend" → AUDIO, "main backend"
  → MAIN, any other `requires one of [...]` message → MAIN fallback (preserves
  legacy main-only retry), null/no-constraint → null.
- `initWith` is now `initWith(target, visionOverride = null, audioOverride =
  null)`. Vision resolves to `backendDetector.probeVisionBackend()` (GPU when
  EGL is present) for allowlisted models with `capabilities.vision == true`,
  else explicit CPU — byte-identical to the old hardcode for non-vision models.
  Audio resolves via `probeAudioBackend()` (CPU today). Same resolution
  extracted into `resolveVisionBackend`/`resolveAudioBackend` so the retry path
  recomputes the current slot value (`ActiveEngine` carries main-backend only,
  unchanged per plan). Speculative-decoding semantics untouched.
- Catch block is slot-aware: parses slot (default MAIN) + required backend,
  compares required against the CURRENT value of the named slot, skips retry
  when equal (no identical retry), otherwise retries once flipping ONLY the
  named slot (main → `target.copy`, vision/audio → `initWith` override).
  Retry-failure message shape unchanged.
- `parseRequiredBackend` signature and behavior byte-identical. No NPU probing,
  ThinkingConfig, speculative-decoding, or UI changes.

**`app/src/test/java/com/warped/data/local/inference/BackendConstraintTest.kt`**
(commit `7a3d15df`): extended, not rewritten — all 3 pre-existing
`parseRequiredBackend` tests unmodified and passing. Added 5 slot-parser truth
table tests + 2 `initWith` capture tests (GPU vision for vision-capable
allowlist stub via `probeVisionBackend` stub; CPU vision for non-vision file).

## Test results

- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL.
- `./gradlew :app:testDebugUnitTest` — BUILD SUCCESSFUL, **328 tests, 0
  failures, 0 errors, 0 skipped**.
- `BackendConstraintTest`: 10/10 green (3 pre-existing + 7 new).

## Deviations from plan

None — plan executed exactly as written.

## On-device validation (required — no adb in this environment)

Unit tests pin the parser and the init wiring, but real GPU-vision load can only
be confirmed on hardware. The user validates by loading the previously failing
model (gemma-4-E2B-it) on device: expected behavior is a successful load with
probed GPU vision instead of the `Vision backend constraint mismatch` JNI error.

## Self-Check: PASSED

- `EngineManager.kt` contains `parseConstraintSlot`, `BackendSlot`,
  `visionOverride`/`audioOverride` — FOUND.
- `BackendConstraintTest.kt` contains 10 tests, 0 failures — FOUND.
- Commits `09fe19d7` (feat) and `7a3d15df` (test) exist — FOUND.
