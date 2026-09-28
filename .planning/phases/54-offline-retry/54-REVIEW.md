---
phase: 54-offline-retry
reviewed: 2026-09-28T19:00:00Z
depth: standard
files_reviewed: 9
files_reviewed_list:
  - app/src/main/java/com/warped/data/grounding/WebPageFetcher.kt
  - app/src/main/java/com/warped/data/local/db/dao/MessageDao.kt
  - app/src/main/java/com/warped/domain/repository/ChatRepository.kt
  - app/src/main/java/com/warped/data/repository/ChatRepositoryImpl.kt
  - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
  - app/src/main/java/com/warped/ui/chat/ChatUiState.kt
  - app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt
  - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
  - app/src/test/java/com/warped/ui/chat/ChatGroundingRetryTest.kt
findings:
  critical: 0
  warning: 3
  info: 2
  total: 5
status: fixed
---

# Phase 54: Code Review Report

**Reviewed:** 2026-09-28T19:00:00Z
**Depth:** standard
**Files Reviewed:** 9
**Status:** fixed (WR-03 e0da8e1, WR-02 58cec8c, WR-01 5fd0be2, tests 43855f1; IN-01/IN-02 no action)

## Summary

Reviewed the Phase 54 offline-retry scope (commits `bd1150c`..`00cb375`: `replaceSources` row-reuse write path, `findAssistantRowId` DAO query, `retryGrounding` + `retryJob` + `refreshConnectivity`, queued banner + Reintentar button, resume wiring) at standard depth, tracing each key threat area claimed in the brief.

**Threat areas verified clean (traced, not assumed):** retry is gated OFFLINE-only on transcript data (`ChatViewModel.kt:739-744`, `idx <= 0` return, FETCH_FAILED ineligible by construction) with the banner branch-gated in `MessageBubble.kt:394`; no auto-retry on resume (`ChatScreen.kt` observer calls only `refreshConnectivity()`); no inference re-run in the retry path (no `GroundingPrompt.augment`, no helper/`runInference` call — only `fetchAll` + `replaceSources` + transcript copy); no history rewrite (`replaceSources` touches only `grounded_sources` rows + conversation timestamp, never `MessageDao.insert`, so the REPLACE CASCADE hazard is avoided); row lookup is correct (`EntityMappers.kt:44` writes `role.name`, so the `'ASSISTANT'` literal in `findAssistantRowId` is exact; `createdAt` millis round-trips via `toEpochMilli`/`ofEpochMilli`; covering index on `(conversation_id, created_at)` exists); no schema change (SELECT-only DAO addition); no provider files touched. `modelOnlyNotice` is documented ephemeral (`ChatMessage.kt:15`), so there is no stale-notice-after-restart divergence — see IN-02.

**Concerns:** no crashes or security holes found, but three warnings break behavior contracts the phase claims: the Stop affordance is unreachable during retry (chip copy promises it), rapid double-taps can defeat the overlap guard via a launch race, and retry is allowed during active inference streaming. Two info items record a defensive inconsistency and an accepted scope limitation.

## Warnings

### WR-01: Stop button unreachable during retry — chip copy promises cancel that does not exist

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:754-765` + `app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt:197-199`
**Issue:** `retryGrounding()` sets `isFetchingWeb = true` but never `isGenerating = true`. `ChatInputBar` renders the Stop `IconButton` only `if (isGenerating)`. So during a retry the input bar shows Send (which stays enabled: `enabled = !isGenerating && canSend`), never Stop — while the Leyendo chip copy added in this phase's surface says "Pulsa Detener para cancelar la lectura." The `stopGeneration()` retry-cancel branch (`ChatViewModel.kt:849-850`) is therefore dead code from the UI: the only live cancel is a new send. The "Stop during retry returns to queued state" contract is proven only at VM level by the unit test calling `stopGeneration()` directly, never reachable by a user tap.
**Fix:**
```kotlin
// In retryGrounding, after the guards pass and before launching:
updateInput { it.copy(isGenerating = true, isFetchingWeb = true, webFetchProgress = ...) }
// ... and in the finally:
updateInput { it.copy(isGenerating = false, isFetchingWeb = false, webFetchProgress = null) }
```
Setting `isGenerating` surfaces the existing Stop button during retry (matching the chip copy), disables Send mid-retry, and keeps `stopGeneration()` semantics unchanged. Alternatively, change the chip copy during retry to not reference Detener — but wiring the existing button is the smaller, contract-preserving fix.

### WR-02: Overlap guard races the launched coroutine — double-tap can start concurrent retries

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:730-737`
**Issue:** The no-op guard reads `_input.value.isFetchingWeb`, but that flag is set *inside* the launched coroutine (`updateInput` at line 754), which dispatches asynchronously on `viewModelScope`. Two rapid taps both observe `isFetchingWeb == false` and both pass the guard; the second tap's `retryJob?.cancel()` then races the first job's startup. If Job 1 already entered its `try`, its `finally` (line 817-820) clears `isFetchingWeb = false` *after* Job 2 set it `true` — the gate then reads idle while a fetch is in flight, so a third tap (or the in-flight pair) yields concurrent `fetchAll` calls and concurrent `replaceSources` delete-then-insert cycles on the same row (lost-update interleaving: Job A's insert followed by Job B's delete). The exit-gate test passes because test dispatchers execute the launch body synchronously, hiding the production race.
**Fix:**
```kotlin
fun retryGrounding(assistantMessageId: String) {
    if (_input.value.isFetchingWeb) return
    if (retryJob?.isActive == true) return // synchronous overlap guard, checked before launch
    ...
    // remove the retryJob?.cancel() pre-cancel; an active job now returns above,
    // a completed job needs no cancel
    retryJob = viewModelScope.launch(coroutineExceptionHandler) { ... }
}
```
Checking `retryJob.isActive` synchronously on the caller thread closes the window the flag-based guard leaves open.

### WR-03: Retry allowed during active inference streaming — fetch runs concurrently with token generation

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:730-731` + `app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt:394`
**Issue:** The entry guard checks only `isFetchingWeb`. During inference streaming (`isGenerating = true`, `isFetchingWeb = false`) the Reintentar button still renders (`notice == OFFLINE && isValidatedOnline && !isFetchingWeb`) and `retryGrounding()` proceeds, running a network fan-out concurrently with token streaming — a state the send path never produces (fetch-then-infer, strictly sequential). Consequences are contained (different rows, disjoint state fields) but Stop then kills *both*, discarding the in-progress answer's streaming buffer alongside the retry; and sustained concurrent load (radio + GPU/NPU inference) is exactly what the sequential send path avoids.
**Fix:**
```kotlin
if (_input.value.isFetchingWeb || _input.value.isGenerating) return
```
and mirror it in the banner gate (`&& !isGenerating` via a plumbed flag, or reuse the existing `isFetchingWeb`-style param). Either both or neither; the VM guard alone leaves a visible-but-dead button.

## Info

### IN-01: Unguarded direct `hasValidatedInternet()` call duplicates the best-effort wrapper

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:732`
**Issue:** `retryGrounding()` calls `fetcher.hasValidatedInternet()` directly on the Main thread, while `refreshConnectivity()` (line 269-277) wraps the identical call in try/catch with an offline fallback. Today this cannot crash — `hasValidatedInternet()` is internally exception-safe (`WebPageFetcher.kt:175-186`, catches `Exception` → `false`) — so this is robustness drift, not a live bug: if a future revision of the gate throws (new `Error` path, binder failure outside the catch), the tap path crashes synchronously in the Compose onClick while the refresh path survives. It also double-checks connectivity per tap (gate at 732 + `refreshConnectivity()` in `finally`).
**Fix:** Have `refreshConnectivity()` return the `Boolean` and reuse it: `if (!refreshConnectivity()) return`. Single call site, single best-effort policy.

### IN-02: OFFLINE notice and retry affordance are session-ephemeral — no post-restart retry

**File:** `app/src/main/java/com/warped/domain/model/ChatMessage.kt:15-16`
**Issue:** `modelOnlyNotice` is documented "never persisted to Room" and `loadConversation` hydrates only sources, not notices. After process death, an OFFLINE turn reloads as a plain answer: no queued banner, no Reintentar, and (if never retried) no Fuentes — the user cannot discover or retry the failed grounding for that turn. This matches the phase's "message-scoped foreground retry" scope and the summary's own acknowledgement that ephemeral state is gone after restart, so it is recorded here as an accepted limitation, not a defect. If product wants retry to survive restart, persist the notice column (schema migration) or derive "retryable" as `hasUrls(userContent) && sources.isEmpty()`.
**Fix:** No code change required for this phase; confirm the limitation is accepted product behavior.

---

_Reviewed: 2026-09-28T19:00:00Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
