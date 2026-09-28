---
phase: 46-runtime-hardening
plan: "01"
subsystem: inference-cancellation
tags: [coroutines, shareIn, okhttp, litertlm, cancellation, RUNTIME-13, RUNTIME-14]
requires: [phase-45-engine-apis]
provides: [per-turn-shared-inference-flow, transport-stop-plumbing, phase47-hook-contract]
affects: [phase-47-tool-loop]
tech-stack:
  added: []
  patterns: [shareIn-per-turn-scope, raw-okhttp-call-retention, cancelProcess-not-close, ensureActive-retry-guard]
key-files:
  created:
    - app/src/test/java/com/warped/ui/chat/ChatCancellationTest.kt
    - app/src/test/java/com/warped/data/remote/provider/LmStudioCancelTest.kt
  modified:
    - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
    - app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt
    - app/src/main/java/com/warped/data/remote/provider/LmStudioHelper.kt
    - app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt
    - app/src/main/java/com/warped/data/local/inference/LiteRtLlmHelper.kt
decisions:
  - callbackFlow-plus-teardown-watcher for SSE chat (bare collector-cancel tears down socket)
  - coroutineScope-ensureActive retry guard (bare coroutineContext intrinsic unresolvable in FlowCollector extension)
  - generationSeq stale-finally guard for activeHelper (same singleton helper across turns defeats identity check)
metrics:
  duration: ~40 min (commits 18:53-19:04 plus tracer iteration)
  completed: 2026-09-27
  tasks: 3/3
  files: 7
  tests: 12 new, 192 total green
---

# Phase 46 Plan 01: Runtime Hardening (Cancellation) Summary

**One-liner:** Stop means stop — single per-turn shared inference Flow with true OkHttp `Call.cancel()` (remote) and `Conversation.cancelProcess()` (local) wired to the Stop button.

## Objective

Collapse inference cancellation onto one shared per-turn Flow and make Stop halt both transports (RUNTIME-13 single shared Flow via `shareIn replay=1` per-turn scope with sentinel no-op jobs deleted; RUNTIME-14 true `Call.cancel()` remote plus `cancelProcess()` local with `ChatViewModel.stopGeneration` calling `helper.stopResponse()`, `CancellationException` transparency, and the `ensureActive` hook contract for the Phase 47 tool loop).

## Tasks Completed

| # | Name | Commit | Files |
|---|------|--------|-------|
| 1 | Tracer — failing cancellation tests first | `685e7df` | ChatCancellationTest.kt, LmStudioCancelTest.kt (new) |
| 2 | Transport stop plumbing — raw Call plus cancelProcess | `d58b075` | LMStudioProvider.kt, LmStudioHelper.kt, LiteRTLmProvider.kt, LiteRtLlmHelper.kt |
| 3 | Single shared per-turn Flow plus stopGeneration wiring | `20af4c1` | ChatViewModel.kt |

## Behavior Achieved

- **Stop halts tokens immediately on both backends** — no trailing arrivals (loopback test proves tok3/tok4 never arrive post-Stop), no fake `StreamToken.Error` bubble (`CancellationException` rethrown first on both paths).
- **Rotation-safe single collection** — `helper.runInference(...).shareIn(this, Eagerly, replay=1)` scoped to the per-turn generation job; accumulator is the single collector; replay covers re-collect with the upstream started exactly once (test-pinned).
- **Stop-then-follow-up streams cleanly** — transport-stop → scope-cancel → state reset ordering, `onCompletion` Call-handle clear plus sequence-guarded `activeHelper` nulling; assistant message persists, spinner clears (test-pinned at both harness and real-ViewModel level).
- **Cancelled local turns never resurrect** — retry guard asserts single attempt under a cancelled scope (was 3 attempts pre-fix).
- **Zero new dependencies** — T-46-SC stays green (JDK HttpServer + MockK + Turbine + runTest only).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 — Correctness] Teardown watcher bridging collector-cancel to the socket**
- **Found during:** Task 2 (LMStudioProvider rewrite)
- **Issue:** Plan's `finally` + `awaitClose` cannot preempt the blocking `readUtf8Line()` — a bare collector-cancel would leak the connection until the next server chunk (proven by tracer test (d) design: holding server).
- **Fix:** Child `launch { awaitCancellation() } finally { call.cancel() }` inside `callbackFlow`; `finally { teardown.cancel() }` on the main path. Transport-stop via `stopResponse` remains the primary path; the watcher is the structured-concurrency backstop.
- **Files:** `LMStudioProvider.kt`
- **Commit:** `d58b075`

**2. [Rule 2 — Correctness] `coroutineScope { ensureActive() }` retry guard**
- **Found during:** Task 2 (LiteRTLmProvider)
- **Issue:** Bare `coroutineContext` intrinsic does not resolve inside `suspend fun FlowCollector<X>.foo()` in this toolchain (Kotlin 2.3.20: "Unresolved reference 'coroutineContext'"; `currentCoroutineContext()` needs a CoroutineScope receiver, which FlowCollector is not).
- **Fix:** `coroutineScope { ensureActive() }` as the first statement of the retry `catch` (plus explicit `CancellationException`-rethrow-first). `coroutineScope` — not `supervisorScope` — inherits the cancelled job so the guard observes it.
- **Files:** `LiteRTLmProvider.kt`
- **Commit:** `d58b075`

**3. [Rule 1 — Bug] OkHttp deprecated `response.message()` / `response.code()`**
- **Found during:** Task 2 (raw-Call rewrite surfaced deprecation errors as compile failures)
- **Issue:** `Response.message()` / `Response.code()` are deprecated in OkHttp 4.12.0.
- **Fix:** Migrated to `.message` / `.code` properties.
- **Files:** `LMStudioProvider.kt`
- **Commit:** `d58b075`

**4. [Rule 2 — Correctness] `generationSeq` stale-finally guard for `activeHelper`**
- **Found during:** Task 3 (ChatViewModel wiring)
- **Issue:** Helpers are `@Singleton` — an identity (`===`) guard cannot distinguish a stale turn's `finally` from a follow-up turn using the same helper instance; the old turn could null the new turn's handle.
- **Fix:** Monotonic `generationSeq` bumped per `sendMessage`; `finally { if (turnId == generationSeq) activeHelper = null }`; `stopGeneration` nulls unconditionally.
- **Files:** `ChatViewModel.kt`
- **Commit:** `20af4c1`

### Test-harness fixes (tracer iteration, pre-commit)

- `Dispatchers.setMain(StandardTestDispatcher(testScheduler))` inside `runTest` (field dispatcher is detached from the test scheduler — selection never delivered).
- `just Runs` stub for `ActiveModelSelection.saveLastConversation` (strict-mock answer missing surfaced as Network error).
- Harness turn jobs on an explicit worker scope with deterministic cancel (TestScope `backgroundScope` launches never executed under `advanceUntilIdle`).
- `switchToLiteRT` stub (`just Runs`) so the retry test asserts genuine 3-vs-1 attempts.
- Typed `any<Contents>()` for the overloaded `sendMessageAsync` (verified overloads + `cancelProcess()` presence via `javap` on the cached 0.17.1 AAR).
- Removed `expectNoEvents()` after Turbine `cancelAndIgnoreRemainingEvents()` (cancellation itself reads as an event).

## Verification

- `./gradlew :app:testDebugUnitTest` — **192/192 green** (180 pre-existing + 12 new), 0 failures/errors/skips.
- `./gradlew :app:assembleDebug` — success.
- `grep scope.launch sentinel` in `data/` — **0** (both sentinels deleted).
- `grep CancellationException` in `LMStudioProvider.kt` — rethrow-first branch present.
- `grep shareIn` in `ChatViewModel.kt` — per-turn `shareIn(this, Eagerly, replay=1)`.
- `grep stopResponse` in `ChatViewModel.kt` — called inside `stopGeneration` (transport-first ordering).
- Red-first proof recorded in `685e7df` commit body: collector-cancel hung past 5s timeout; late tok3/tok4 arrived; `cancelChat`/`cancelActiveGeneration` absent; `cancelProcess` never called; retry ran 3 attempts; `stopResponse() was not called` (MockK verify).

## Known Stubs

None — stub scan over all created/modified files returned zero matches.

## Decisions Made

- `callbackFlow` + teardown watcher for SSE chat (see deviation 1).
- `coroutineScope`-inherited `ensureActive` retry guard (see deviation 2).
- `generationSeq` over identity check for the stale-helper guard (see deviation 4).
- HTTP tests use `runBlocking` (real socket IO incompatible with `runTest` virtual time); mock-only tests use `runTest` + Turbine.
- Natural early completion without `Done` keeps existing behavior; Stop discards the partial buffer (open question 2 default: smallest change).

## Deferred Issues

- **On-device Stop-latency + rotation verification** — needs a real arm64 device (same precedent as 45-02 device smoke); JVM tests prove the contracts, not token-halt milliseconds.
- **`cancelProcess()` promptness/terminal behavior** (research open question 1) — code is defensive (belt-and-braces coroutine cancel alongside `cancelProcess`); confirm halt latency on-device.
- `ChatViewModel`'s `catch (e: Exception)` still maps a cancelled collect to a `Network` error bubble — pre-existing, out of plan scope (accumulator unchanged per plan); candidate follow-up.

## Self-Check: PASSED

- All 7 created/modified files exist on disk.
- All 3 commits (`685e7df`, `d58b075`, `20af4c1`) present in `git log`.
- Cancellation suite 12/12 green; full suite 192/192 green; `assembleDebug` succeeds.
