---
phase: 46-runtime-hardening
reviewed: 2026-09-28T00:00:00Z
depth: standard
files_reviewed: 7
files_reviewed_list:
  - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
  - app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt
  - app/src/main/java/com/warped/data/remote/provider/LmStudioHelper.kt
  - app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt
  - app/src/main/java/com/warped/data/local/inference/LiteRtLlmHelper.kt
  - app/src/test/java/com/warped/ui/chat/ChatCancellationTest.kt
  - app/src/test/java/com/warped/data/remote/provider/LmStudioCancelTest.kt
findings:
  critical: 2
  warning: 7
  info: 3
  total: 12
status: issues_found
---

# Phase 46: Code Review Report

**Reviewed:** 2026-09-28T00:00:00Z
**Depth:** standard
**Files Reviewed:** 7
**Status:** issues_found

## Summary

Reviewed the Phase 46 cancellation hardening (per-turn `shareIn`, OkHttp `Call.cancel()` + `cancelProcess()` plumbing, `stopGeneration` wiring, 12 new tests). The transport-level work is sound — the teardown watcher, `CancellationException`-rethrow-first in both providers, and the `ensureActive` retry guard are correct. Two blockers remain in `ChatViewModel`: the turn-level `catch (e: Exception)` re-introduces the exact fake-`Error` bubble the providers just eliminated (the SUMMARY itself flags it as deferred), and `sendMessage` overwrites `generationJob` without cancelling the prior turn, so rapid double-send runs two concurrent inferences. A cluster of warnings covers duplicated `when` branches that misroute the deprecated `LOCAL` provider, dead `cancelChat()` code, and small concurrency/logic hazards. Tests are genuine regression tests (loopback SSE + MockK verify), not tautologies.

## Critical Issues

### CR-01: ViewModel `catch (Exception)` maps coroutine cancellation to a fake `Network` error

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:365-371`
**Issue:** The per-turn `try` ends with `catch (e: Exception)`, and `CancellationException` is a subclass of `Exception`. When `stopGeneration()` calls `generationJob?.cancel()`, the `CancellationException` thrown out of the shared-flow `collect` lands here and is mapped to `ChatError.Network(...)` with `isStreaming = false`. This re-introduces at the ViewModel layer the exact defect RUNTIME-14 eliminated at the provider layer (both providers correctly rethrow `CancellationException` first). Net effect: pressing Stop shows a "Network" error bubble for a user-initiated stop. The phase SUMMARY (§Deferred Issues) admits this is still present — it is a correctness regression against the phase's own success criterion ("no fake Error bubble").
**Fix:**
```kotlin
} catch (e: CancellationException) {
    // Stop means stop: user-initiated cancel is not an error. Rethrow so
    // structured concurrency observes cancellation; do not touch uiState.error.
    throw e
} catch (e: Exception) {
    _uiState.update {
        it.copy(
            error = ChatError.Network(e.message ?: "Unknown error"),
            isStreaming = false
        )
    }
}
```
Add `import kotlinx.coroutines.CancellationException`. The existing `finally` (stale-seq guard) still runs on the rethrow path.

### CR-02: `sendMessage` overwrites `generationJob` without cancelling the prior turn

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:197-198`
**Issue:** `generationJob = viewModelScope.launch { ... }` unconditionally replaces the handle. If the user sends a second message while turn 1 is still streaming (or Stop-then-send races the old `finally`), turn 1 keeps running: two concurrent collectors append to `tokenBuffer`/`rawBuffer` and both update `streamingContent`, interleaving tokens from two turns into one bubble. `activeHelper` is also clobbered — a subsequent `stopGeneration()` only halts the newest turn's transport while the orphaned turn streams on, and the orphan's `finally` is the only thing the `generationSeq` guard was designed for, not a live duplicate collector.
**Fix:**
```kotlin
// At the top of sendMessage, after the early-return guards:
generationJob?.let {
    activeHelper?.stopResponse()
    it.cancel()
}
generationJob = null
activeHelper = null
val turnId = ++generationSeq
generationJob = viewModelScope.launch(coroutineExceptionHandler) { ... }
```
Alternatively, early-return with `if (generationJob?.isActive == true) return` when a turn is in flight — but then the UI must disable Send while `isStreaming`, which it currently does not enforce.

## Warnings

### WR-01: Duplicated `when` branches misroute the deprecated `LOCAL` provider to the remote path

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:244-246`, `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:870-878`
**Issue:** Both `when` blocks list `ProviderType.LITE_RT_LM` twice:
```kotlin
when (selectedProvider) {
    ProviderType.LITE_RT_LM,
    ProviderType.LITE_RT_LM -> providerRouter.resolveLocalHelper(...)
    else -> { /* remote endpoint resolution; throws "No endpoint selected" */ }
}
```
The second entry is dead; the evident intent was `ProviderType.LOCAL, ProviderType.LITE_RT_LM` (`LOCAL` is `@Deprecated("Use LITE_RT_LM instead")` but still a valid enum value that old conversations/DB rows can carry). Consequence: any turn with `providerType == LOCAL` falls into `else`, throws `IllegalStateException("No endpoint selected")`, and in `isModelAvailable` returns "endpoint not found" → `modelMissing = true` → the conversation is blocked with `ModelUnavailable`. Compiler does not flag duplicate branches.
**Fix:**
```kotlin
ProviderType.LOCAL, ProviderType.LITE_RT_LM -> providerRouter.resolveLocalHelper(...)
```
Apply at both sites (`sendMessage` helper resolution and `isModelAvailable`).

### WR-02: `LMStudioProvider.cancelChat()` is dead code — the retained `currentCall` is never the cancel path

**File:** `app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt:104-110`
**Issue:** The helper creates a **fresh** `LMStudioProvider` per `runInference` call and retains the `Call` via the `onCallCreated` callback into its own `activeCall`; `stopResponse()` cancels that handle. Nobody ever calls `provider.cancelChat()`, and the provider's own `currentCall` field is written but never read for cancellation (the teardown watcher closes over the local `call`, not `currentCall`). The field + method suggest a second cancel path that does not exist, inviting a future caller to cancel the wrong provider instance (stale `currentCall` from a previous turn on a reused instance).
**Fix:** Either delete `currentCall`/`cancelChat()` and close over the local `call` only, or — if a provider-level cancel API is wanted — document that the helper-owned handle is canonical and make `cancelChat()` delegate to the same `AtomicReference` the helper reads. Do not leave two apparent handles.

### WR-03: Suspending `send()` used for terminal tokens inside `callbackFlow` instead of `trySend`

**File:** `app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt:169, 173, 252`
**Issue:** `send(StreamToken.Error(...))` and `send(StreamToken.Done(...))` suspend. If the collector was cancelled (Stop) and the channel is closed, `send` throws `ClosedSendChannelException` / `CancellationException` from a path that is *supposed* to be terminal handling — the HTTP-error branch (line 169) has no `CancellationException`-rethrow guard around the `send` itself, so a Stop racing an HTTP error can surface the send failure rather than clean cancellation. `callbackFlow` guidance is `trySend` for all emissions precisely because the producer/consumer lifetimes race here.
**Fix:**
```kotlin
trySend(StreamToken.Error("HTTP ${response.code}: $errorBody"))
// ...
if (currentCoroutineContext().isActive) trySend(StreamToken.Done(statsText, null))
```

### WR-04: `generationSeq` increment is a non-atomic read-modify-write on a `@Volatile` Long

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:62-63, 197`
**Issue:** `val turnId = ++generationSeq` on a `@Volatile var` is not atomic. Two rapid `sendMessage` calls (double-tap Send, or Stop-then-send racing the old turn's teardown) can read the same value, breaking the stale-`finally` guard (`turnId == generationSeq`) that the whole `activeHelper` lifetime depends on — both turns then share one id and the older `finally` can null the newer turn's helper.
**Fix:**
```kotlin
private val generationSeq = java.util.concurrent.atomic.AtomicLong(0L)
// ...
val turnId = generationSeq.incrementAndGet()
// finally:
if (turnId == generationSeq.get()) activeHelper = null
```

### WR-05: `stopGeneration()` unconditionally nulls `activeHelper`, clobbering a newer turn

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:387-402`
**Issue:** `stopGeneration` captures nothing: it calls `activeHelper?.stopResponse()` then sets `activeHelper = null`. If Stop is pressed (or a stale Stop event delivered) after a follow-up `sendMessage` has already resolved a *new* helper into `activeHelper`, this cancels and nulls the new turn's transport while `generationJob` still points at the new job (or vice versa — `generationJob?.cancel()` cancels whatever job is current, which may be the innocent follow-up). The `finally` guard is sequence-checked but `stopGeneration` is not.
**Fix:** Snapshot under the same sequence:
```kotlin
fun stopGeneration() {
    val helper = activeHelper
    activeHelper = null
    try { helper?.stopResponse() } catch (e: Exception) { Timber.e(e, "Chat: stopResponse failed") }
    generationJob?.cancel()
    generationJob = null
    ...
}
```
Combined with the CR-02 fix (send cancels the prior turn first), the window shrinks to a single UI-thread interleaving, which the snapshot makes benign.

### WR-06: Non-streaming fallback tries to JSON-decode the SSE-framed accumulator

**File:** `app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt:227-250`
**Issue:** When `sawSse == true` but `hasTokens == false` (e.g. only `model_load.*` / `prompt_processing.*` progress events arrived before the server closed), the fallback decodes `bodyAccumulator` — which contains `event: ...` / `data: ...` framing lines — as a single `LmStudioSseEvent` JSON object. That decode *always* fails and logs `Timber.e("LMStudio: non-streaming JSON parse failed")` as an error for a normal (if token-less) stream. It also masks the real outcome: no `Done`/empty-content handling distinguishes "server sent nothing" from "parse failed".
**Fix:** Only attempt the non-streaming decode when `!sawSse` (true JSON body); when `sawSse && !hasTokens`, emit `StreamToken.Done(statsText, null)` directly or surface a dedicated empty-response error at most once.

### WR-07: Swallowed-exception variable and lossy error-body read

**File:** `app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt:164-168`
**Issue:** `catch (e: Exception) { response.message }` ignores the read failure `e` (unused variable) and falls back to `response.message` — which for OkHttp is the HTTP reason phrase, often empty. The real I/O cause is dropped from logs and from the user-facing `HTTP <code>` message, complicating diagnosis of error-body truncation.
**Fix:**
```kotlin
val errorBody = try {
    response.body?.string()
} catch (e: Exception) {
    Timber.w(e, "LMStudio: error-body read failed")
    response.message
} ?: response.message
```

## Info

### IN-01: `shareIn(Eagerly, replay = 1)` with a single collector adds machinery for no sharing

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:292-295`
**Issue:** The accumulator is the *only* collector, so `shareIn` never shares — it adds a second job, a replay cache, and an extra dispatch hop per token for rotation-safety that `StateFlow`-backed `streamingContent` already provides. Harmless but misleading: a future reader will add a second collector expecting broadcast semantics and hit replay-cache staleness (the re-collect test pins replay delivering the *last* token, which is correct for rotation but surprising for a chat stream).
**Fix:** Keep if rotation-replay is load-bearing, but document *why* the single-collector `shareIn` exists (rotation replay, upstream-starts-once) — or drop it and collect the cold flow directly.

### IN-02: Loopback tests use real-time sleeps (slow suite, timing-sensitive windows)

**File:** `app/src/test/java/com/warped/data/remote/provider/LmStudioCancelTest.kt:211-221`
**Issue:** `delay(2_000)` past the tok3/tok4 schedule plus `Thread.sleep`-scripted server makes test (e) take seconds and the `at > tStop + 400` late-delta window depend on CI scheduling jitter. Currently passing, but a loaded emulator/CI worker can flake the 400 ms boundary.
**Fix:** Shorten the script (tok3/tok4 at +600/+800 ms, assert window +300 ms) or gate on token indexes rather than wall-clock arrival times.

### IN-03: Dead ternary in `confirmLoadMemoryWarning` — both branches identical

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:546`
**Issue:** `val providerType = if (model.isLiteRtLm()) ProviderType.LITE_RT_LM else ProviderType.LITE_RT_LM` — the condition is dead; the line is just an alias. Left over from the LOCAL→LITE_RT_LM migration, same family as WR-01.
**Fix:** `val providerType = ProviderType.LITE_RT_LM` (or restore the intended `LOCAL` fallback if `isLiteRtLm()` can still be false for legacy rows).

---

_Reviewed: 2026-09-28T00:00:00Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
