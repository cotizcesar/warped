---
phase: 46-runtime-hardening
verified: 2026-09-28T00:00:00Z
status: passed
score: 3/3 must-haves verified
overrides_applied: 0
re_verification: false
human_verification:
  - test: "Remote Stop halts tokens immediately"
    expected: "Long remote generation stops with no trailing tokens and no Error bubble containing Canceled or Connection failed"
    why_human: "Requires real arm64 device + LM Studio endpoint; JVM loopback tests prove the contract, not socket-teardown latency (plan 46-02 step 2)"
  - test: "Local Stop halts with no retry resurrection"
    expected: "On-device generation halts promptly and no tokens resume ~1s later; cancelProcess() promptness/terminal behavior confirmed"
    why_human: "Requires on-device model; cancelProcess() promptness is an unverified AAR assumption A1 (plan 46-02 step 3)"
  - test: "Stop then immediate follow-up streams cleanly"
    expected: "Follow-up message streams normally, no hang/wedge/stuck spinner, after each Stop on both backends"
    why_human: "Requires real device interaction timing (plan 46-02 step 4)"
  - test: "Rotation mid-stream is token-safe on both backends"
    expected: "Finished message has no duplicated or dropped tokens; inference not restarted"
    why_human: "Requires physical rotation during streaming on device (plan 46-02 steps 5-6)"
  - test: "Logcat shows no secrets during manual test"
    expected: "No API key, prompt body, or tool arguments in logcat at BASIC level"
    why_human: "Requires device logcat inspection (threat T-46-03)"
---

# Phase 46: Runtime Hardening Verification Report

**Phase Goal:** Streaming is truly cancellable on both backends through one shared inference Flow — Stop means stop
**Verified:** 2026-09-28T00:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | User taps Stop mid-generation (local or remote) and tokens halt immediately — no trailing tokens after Stop | ✓ VERIFIED (code) / pending device | `LmStudioHelper.stopResponse` → `activeCall.getAndSet(null)?.cancel()` (LmStudioHelper.kt:124-135); `LiteRtLlmHelper.stopResponse` → `cancelActiveGeneration()` → `activeConversation?.cancelProcess()` (LiteRTLmProvider.kt:59-63); `CancellationException` rethrown first on both paths (LMStudioProvider.kt:270, LiteRTLmProvider.kt:249, ChatViewModel.kt:375); teardown watcher bridges collector-cancel to socket (LMStudioProvider.kt:163-169); 12/12 cancellation tests green incl. no-trailing-token loopback test |
| 2 | User rotates mid-stream and sees no duplicated or dropped tokens | ✓ VERIFIED (code) / pending device | Per-turn `shareIn(this, Eagerly, replay=1)` in ViewModel scope, single collector (ChatViewModel.kt:302-305); collection lives in ViewModel (survives config change); replay/upstream-once test green |
| 3 | User can Stop and immediately send follow-up with no hang, wedge, or stale spinner | ✓ VERIFIED (code) / pending device | Transport-first stopGeneration (ChatViewModel.kt:402-419) + prior-turn cancel in sendMessage (ChatViewModel.kt:200-205) + `onCompletion` handle clear + `generationSeq` stale-finally guard; stop-then-follow-up tests green at harness and real-ViewModel level |

**Score:** 3/3 truths verified (code-level; device confirmation pending via plan 46-02)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` | per-turn shareIn replay=1 + stopGeneration calling helper.stopResponse | ✓ VERIFIED | shareIn(:305), stopResponse in stopGeneration(:408) + prior-turn cancel(:200-205), CancellationException rethrow(:375), AtomicLong seq(:64), WR-01 fixed(:255) |
| `app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt` | raw OkHttp Call retention + Call.cancel + CancellationException transparency | ✓ VERIFIED | `client.newCall` retained(:151-158), teardown watcher(:163-169), rethrow-first(:270), trySend terminals(:179,265), WR-06 fallback guard(:241), WR-07 logged read failure(:173-178) |
| `app/src/main/java/com/warped/data/remote/provider/LmStudioHelper.kt` | sentinel removed, stopResponse → Call.cancel | ✓ VERIFIED | `AtomicReference<Call>` (:58), onCallCreated retention(:104), onCompletion clear(:106), stopResponse cancels(:124-135); sentinel grep = 0 |
| `app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt` | cancelActiveGeneration + ensureActive retry guard | ✓ VERIFIED | cancelActiveGeneration(:59-63), CancellationException rethrow(:249), `coroutineScope { ensureActive() }` retry guard(:266), Phase 47 hook contract comment(:261-264) |
| `app/src/main/java/com/warped/data/local/inference/LiteRtLlmHelper.kt` | sentinel removed, stopResponse → cancelProcess | ✓ VERIFIED | Delegates to cancelActiveGeneration(:90); sentinel grep = 0 |
| `app/src/test/java/com/warped/ui/chat/ChatCancellationTest.kt` | turbine cancellation + replay tests | ✓ VERIFIED | 6/6 green (fresh run this session) |
| `app/src/test/java/com/warped/data/remote/provider/LmStudioCancelTest.kt` | Call.cancel + CancellationException regression tests | ✓ VERIFIED | 6/6 green (fresh run this session) |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| Stop button | `ChatViewModel.stopGeneration()` | onStop handler (pre-existing) | ✓ WIRED | Unchanged UI path, behavior fix only per CONTEXT |
| `stopGeneration()` | helper transport | `activeHelper?.stopResponse()` transport-first (:405-411) | ✓ WIRED | Snapshot-then-null (WR-05 fix) |
| `LmStudioHelper` | OkHttp socket | retained `Call` via onCallCreated → `Call.cancel()` | ✓ WIRED | Plus `flowOn(Dispatchers.IO)`, onCompletion clear |
| `LiteRtLlmHelper` | native engine | `cancelActiveGeneration()` → `Conversation.cancelProcess()` | ✓ WIRED | try/catch Timber.w; never `close()` on Stop |
| `sendMessage` | prior turn | prior `stopResponse()` + `cancel()` before new turn (:200-205) | ✓ WIRED | CR-02 fix; no orphaned concurrent collectors |
| Future tool loop | cancellation | `ensureActive()` + `>=` round-cap hook contract comments | ✓ WIRED | Hooks only, full loop is Phase 47 |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|-------------------|--------|
| ChatViewModel accumulator | `streamingContent` | shared per-turn Flow from `helper.runInference` | ✓ FLOWING | Real helper flows collected; no hardcoded/static tokens; fake helpers confined to tests |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Cancellation suite green | `./gradlew :app:testDebugUnitTest --tests ChatCancellationTest --tests LmStudioCancelTest` | BUILD SUCCESSFUL; 6/6 + 6/6, 0 failures | ✓ PASS |
| Sentinels gone | `grep sentinel/activeJob.set(scope.launch app/.../data` | 0 production matches (comments only) | ✓ PASS |

### Probe Execution

Step 7c: SKIPPED (no probes declared for this phase; not a migration/tooling phase).

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| RUNTIME-13 | 46-01 | Single shared Flow via shareIn replay=1 per-turn; sentinel removed | ✓ SATISFIED (code) / device rotation pending | shareIn per-turn, sentinels deleted, replay test green; rotation device check → 46-02 |
| RUNTIME-14 | 46-01 | True Call.cancel + cancelProcess wired to Stop; tool-loop cancellation hooks | ✓ SATISFIED (code) / device latency pending | Transport plumbing + rethrow-first + retry guard + hook contract; halt-immediately device check → 46-02 |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| ChatViewModel.kt | 564 | Dead ternary `if (model.isLiteRtLm()) LITE_RT_LM else LITE_RT_LM` (review IN-03) | ℹ️ Info | No behavioral impact; cleanup candidate, not a gap |
| LMStudioProvider.kt | 214, 221 | Suspending `send()` for Delta tokens inside callbackFlow (review WR-03 partial) | ℹ️ Info | Terminal tokens use trySend; Delta `send` to active collector is correct-path fine; Stop-race surfaces as clean cancellation via rethrow-first |

Note: review CR-01, CR-02, WR-01, WR-02, WR-04, WR-05, WR-06, WR-07 were all fixed in follow-up commits `64606c3` + `9af9bfc` — verified present in code this session. No BLOCKERs remain.

### Human Verification Required

Plan 46-02 is a blocking device gate with no `46-02-SUMMARY.md` yet (confirmed absent). Automated gates pass; the following need a real arm64 device:

### 1. Remote Stop halts tokens immediately

**Test:** Start a long remote generation, tap Stop mid-stream
**Expected:** Tokens halt immediately, no trailing tokens, no Error bubble with Canceled/Connection failed
**Why human:** No device in CI; JVM loopback proves contract, not socket-teardown latency

### 2. Local Stop halts with no retry resurrection

**Test:** Same against on-device model
**Expected:** Halt is prompt; no tokens resume ~1s later
**Why human:** `cancelProcess()` promptness/terminal behavior is AAR-assumption A1; needs on-device confirmation

### 3. Stop then immediate follow-up streams cleanly

**Test:** Immediately after each Stop, send a follow-up message
**Expected:** Streams normally, no hang/wedge/stuck spinner; note partial-message observation (either outcome acceptable)
**Why human:** Requires real device interaction timing

### 4. Rotation mid-stream is token-safe (both backends)

**Test:** Start generation, rotate mid-stream, on local then remote
**Expected:** Finished message has no duplicated/dropped tokens; inference not restarted
**Why human:** Requires physical rotation during streaming

### 5. Logcat shows no secrets

**Test:** Inspect logcat during manual test
**Expected:** No API key, prompt body, or tool arguments at BASIC level
**Why human:** Requires device logcat inspection (T-46-03)

### Gaps Summary

No code gaps. All 46-01 must-haves verified at all four levels (exists, substantive, wired, data flowing); review blockers already fixed and confirmed; 12/12 new tests green on a fresh run. The only outstanding item is the planned human device gate (46-02), which is by design — JVM tests cannot prove halt latency or rotation safety. Phase 47 must wait for 46-02 approval since the tool loop builds on a verified-stoppable `runInference`.

---

_Verified: 2026-09-28T00:00:00Z_
_Verifier: the agent (gsd-verifier)_
