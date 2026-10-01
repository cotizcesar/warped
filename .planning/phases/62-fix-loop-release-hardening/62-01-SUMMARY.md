---
phase: 62-fix-loop-release-hardening
plan: "01"
subsystem: leak-regression-tests
tags: [leak-canary, regression, jvm-tests, LEAK-02, LEAK-03, LEAK-04, LEAK-05]
requires: ["61-LEAK-BASELINE.md zero-leak baseline (6/6 legs clean)"]
provides: ["5 leak-path regression test files locking the clean paths"]
affects: ["62-02 release-UAT (device-only remainder listed below)"]
tech-stack:
  added: []
  patterns: ["JUnit5 + MockK + Turbine-adjacent runTest (Phase 60 DownloadStopReason precedent)", "real production class + faked collaborator", "architectural source-scan assertions with positive controls"]
key-files:
  created:
    - app/src/test/java/com/warped/data/local/inference/EngineLifecycleRegressionTest.kt
    - app/src/test/java/com/warped/chat/InferenceCancelRegressionTest.kt
    - app/src/test/java/com/warped/data/grounding/GroundingScopeRegressionTest.kt
    - app/src/test/java/com/warped/data/local/download/DownloadObserverRegressionTest.kt
    - app/src/test/java/com/warped/di/SingletonScopeRegressionTest.kt
  modified: []
decisions:
  - "Tests only, zero production changes — baseline was clean, so the plan proves clean paths stay clean"
  - "GroundingScopeRegressionTest lives under data/grounding (repo convention), not the plan's com/warped/grounding path"
  - "cancelDownload observer survival until CANCELLED pinned as designed behavior (owns the stop-reason copy)"
metrics:
  duration: "~15 min"
  completed: "2026-10-01"
---

# Phase 62 Plan 01: Leak-Baseline Regression Lock Summary

**One-liner:** Five JVM regression suites (32 tests) locking the Phase 61 zero-leak baseline across engine load/unload, inference cancel, grounding scope cancel, download observers, and singleton discipline — full suite green at 932 tests, zero production changes.

## Tasks Completed

| # | Name | Commit | Files |
|---|------|--------|-------|
| 1 | LEAK-02 + LEAK-03 regression tests | `dec5dbb8` | EngineLifecycleRegressionTest.kt (6 tests), InferenceCancelRegressionTest.kt (6 tests) |
| 2 | LEAK-04 + LEAK-05 regression tests | `ebe38148` | GroundingScopeRegressionTest.kt (5 tests), DownloadObserverRegressionTest.kt (6 tests), SingletonScopeRegressionTest.kt (9 tests) |
| 3 | Full suite green on 900-test baseline | (no commit — verification only) | 932 tests, 0 failures, 0 errors, 0 skipped |

## Test Results

- Targeted runs: `EngineLifecycleRegressionTest` 6/6, `InferenceCancelRegressionTest` 6/6, `GroundingScopeRegressionTest` 5/5, `DownloadObserverRegressionTest` 6/6, `SingletonScopeRegressionTest` 9/9.
- Full suite (`:app:testDebugUnitTest`): **88 test classes, 932 tests, 0 failures, 0 errors, 0 skipped** — baseline 900 + 32 new (6+6+5+6+9 = 32, exact match).
- No production file modified; no regressions introduced by the new tests (nothing to fix — tests only).

## What Each Suite Locks (trace to owner)

- **LEAK-02 → `EngineManager.kt`**: real manager + MockK engine; unload releases the native handle exactly once and clears `ActiveEngine`; reload after unload is a fresh handle (2 inits / 1 close); model switch unloads-before-init; same-model switch skips reinit.
- **LEAK-03 → `ChatViewModel.kt` single-flight/`runInference` contract + provider SSE streams**: Flow-level single-flight harness mirroring CR-02 pre-cancel and RUNTIME-14 stop order; mid-stream cancel emits nothing further; a REAL OkHttp `ResponseBody` (SSE stand-in, never mocked/socketed) is closed on Stop AND on normal completion; follow-up runs start cleanly (exactly 2 upstream starts, no zombie).
- **LEAK-04 → `data/grounding/` fan-out + Tavily + retry**: real `MultiUrlFetcher` (blocking fake fetcher) + real `TavilySearchRepository` (suspending fake API) as children of one per-send scope — cancel kills both, `CancellationException` propagates (never converted to model-only); retry reuses the assistant row via `replaceSources` (delete+insert on row 7L, `messageDao.insert` verified never called, double-retry re-targets the same row, deleted-message retry is a silent no-op).
- **LEAK-05 part 1 → `ModelDownloadManager.kt`**: production observer lambda captured from `observeForever` and driven with terminal `WorkInfo` fakes — SUCCEEDED/FAILED unregister + clear the work id; `cancelDownload` deliberately keeps the observer until CANCELLED arrives (owns the Phase 60-02 stop-reason copy); `pauseDownload` unregisters up-front; two concurrent downloads clean up independently.
- **LEAK-05 part 2 → `NetworkModule.kt` + `WarpedApplication.kt` + image/socket discipline**: all three OkHttp bindings carry `@Singleton` (reflection); source scan finds zero `*Client.close()` call sites; no `ActivityContext` in `di/` (positive control: `ApplicationContext` present); `WarpedApplication` implements Coil's `SingletonImageLoader.Factory` (application-scoped by contract) with its own bare OkHttp client; thumbs use `AsyncImage` with no `SubcomposeAsyncImage` import/call site; `WebPageFetcher` auto-closes via `execute().use` and aborts all in-flight calls on `cancel()`; `stopGeneration()` reaches `fetcher.cancel()`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] GroundingScopeRegressionTest package/path corrected to repo convention**
- **Found during:** Task 2 compile (`Unresolved reference 'WebPageFetcher'` cascade)
- **Issue:** Plan lists path `com/warped/grounding/` + class `com.warped.grounding.*`, but the production package is `com.warped.data.grounding` and every existing grounding test lives under `data/grounding/`.
- **Fix:** File placed at `app/src/test/java/com/warped/data/grounding/GroundingScopeRegressionTest.kt`, package `com.warped.data.grounding`. Verify with `--tests "com.warped.data.grounding.GroundingScopeRegressionTest"` (not the plan's `com.warped.grounding.*` selector).
- **Commit:** `ebe38148`

**2. [Rule 1 - Bug] Singleton image test tripped on its own rule's KDoc mention**
- **Found during:** Task 2 test run (`SingletonScopeRegressionTest`: 9 tests, 1 failure)
- **Issue:** Raw `doesNotContain("SubcomposeAsyncImage")` matched the `OgSourceCard.kt` KDoc line that *states* the rule ("not SubcomposeAsyncImage — the docs-flagged slow path").
- **Fix:** Assertion now bans the import (`import coil3.compose.SubcomposeAsyncImage`) and call sites (`SubcomposeAsyncImage(`) — usage is what leaks lists; the comment is the documented rule.
- **Commit:** `ebe38148`

**3. [Rule 2 - Missing critical] Replaced a vacuous self-test with a real regression test**
- **Found during:** Task 1 review
- **Issue:** Draft `InferenceCancelRegressionTest` contained a `Dispatchers.getMain() isNotNull` sanity test — vacuous, asserts nothing about leaks.
- **Fix:** Replaced with `cancelled run never reaches Done — streaming flag cleared by Stop not by terminal` (pins the no-wedged-spinner half of Leg 2).
- **Commit:** `dec5dbb8`

Or otherwise: plan executed as written — no production fixes needed (baseline clean, as expected), no scope creep (no largeHeap, no LeakCanary-in-release, no compat-flag changes, no dependency changes of any kind).

## Release-UAT Carryover for 62-02 (JVM-untestable, explicit — never silent)

1. **Leg 1B model-B switch** (baseline DEFERRED): only one complete `.litertlm` on-device; needs a second complete model download. Unit level pins unload-before-init on switch; the real two-model switch stays device-only.
2. **LiveData main-thread observer delivery** (`DownloadObserverRegressionTest` drives the observer lambda synchronously with fakes; on-device WorkManager delivers CANCELLED/SUCCEEDED via the real `WorkInfo` LiveData — covered by the Leg 5 tour replay).
3. **Coil dispose-cancel on real recycle** (statically pinned to `AsyncImage`; actual scroll-recycle behavior is Leg 5 tour territory).
4. **Leg 6 rotation + process death restore** (no JVM-meaningful form; device-only by nature).

## Known Stubs

None — all five files are fully wired behavioral tests; no placeholder data, no TODOs, no mocked production class under test.

## Self-Check: PASSED

- All 5 files exist on disk (verified via `ls`).
- Commits `dec5dbb8` + `ebe38148` exist in `git log`.
- No commit deleted tracked files (`git diff --diff-filter=D` clean for both).
- `git status` shows no unintended staged files (only the 5 test files were staged per commit; build/ + pre-existing working-tree modifications untouched).
