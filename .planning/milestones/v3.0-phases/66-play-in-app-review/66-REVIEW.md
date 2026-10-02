---
phase: 66-play-in-app-review
reviewed: 2026-10-02T17:00:00Z
depth: standard
files_reviewed: 12
files_reviewed_list:
  - app/src/main/java/com/warped/data/local/preferences/ReviewPreferences.kt
  - app/src/main/java/com/warped/domain/review/ReviewHelper.kt
  - app/src/test/java/com/warped/domain/review/ReviewEligibilityTest.kt
  - gradle/libs.versions.toml
  - app/build.gradle.kts
  - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
  - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
  - app/src/main/java/com/warped/ui/settings/SettingsScreen.kt
  - app/src/main/java/com/warped/ui/chat/components/BrowserIntents.kt
  - app/src/main/res/values/strings.xml
  - app/src/main/res/values-es/strings.xml
findings:
  critical: 0
  warning: 5
  info: 4
  total: 9
status: issues_found
---

# Phase 66: Code Review Report

**Reviewed:** 2026-10-02T17:00:00Z
**Depth:** standard
**Files Reviewed:** 12
**Status:** issues_found

## Summary

Reviewed the 2 feat commits for Phase 66 (Play In-App Review): `ReviewPreferences`, `ReviewHelper` + `ReviewEligibility`, the `ChatViewModel` turn-Done hook, the `ChatScreen` Activity-provider wiring, the Settings Store entry + `openPlayStoreListing`, EN/ES strings, and the `review-ktx:2.0.2` dependency declaration.

Verified sound: **Activity lifecycle** (Activity is a method param, never stored; the provider lambda is nulled in `onDispose`), **non-blocking turn path** (`viewModelScope.launch` sibling off the turn coroutine, no `suspend`/`runBlocking` on the hot path), **silent ambient failures** (all Play/quota paths `Timber.w` + return, no toast), **market-first intent with https fallback** (URI derived from `packageName` only, no user input), **EN+ES key parity** (all three keys present in both locales), and **dependency hygiene** (`review-ktx` only, version pinned in catalog). No secrets, no injection surface, no Activity leak.

All 5 warnings are correctness/robustness defects in the new code; none is a ship-stopper on its own, but WR-01 (cancellation swallowing) and WR-05 (loop-turn inflation) directly undermine stated phase guarantees and should be fixed before building on these counters.

## Warnings

### WR-01: `CancellationException` swallowed by generic catch blocks

**File:** `app/src/main/java/com/warped/domain/review/ReviewHelper.kt:84`
**Issue:** `catch (e: Exception)` catches `CancellationException` and merely logs it, swallowing structured-concurrency cancellation. If the `ViewModelScope` is cancelled (e.g., ViewModel cleared while the Play `Task` is in flight), the coroutine never observes cancellation and the scope cannot complete normally. The same anti-pattern is duplicated in the hook at `ChatViewModel.kt:1143` (`catch (e: Exception)` around `maybePrompt`).
**Fix:**
```kotlin
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Timber.w(e, "Review: prompt flow failed silently")
}
```
(Apply in both `ReviewHelper.maybePrompt` and the `ChatViewModel` hook.)

### WR-02: Non-atomic increment-then-read allows double-prompt and cap bypass

**File:** `app/src/main/java/com/warped/domain/review/ReviewHelper.kt:64-67`
**Issue:** `incrementCompletedTurns()` and the three subsequent `prefs.*.first()` reads are four separate DataStore transactions with no mutex. Two overlapping `maybePrompt` invocations can interleave (both increment, both read the same `promptCount`, both pass `isEligible`, both launch the flow), producing a back-to-back double prompt and two `recordPrompt` writes that defeat the 21-day cooldown. The three `.first()` calls are also three separate snapshots — `completedTurns`, `lastPromptMillis`, and `promptCount` can come from different states.
**Fix:**
```kotlin
private val mutex = Mutex()
// in maybePrompt:
mutex.withLock {
    prefs.incrementCompletedTurns()
    val snapshot = prefs.reviewState.first() // single data-class snapshot
    ...
    if (eligible) { ...; prefs.recordPrompt(nowMillis) }
}
```
Expose a single `Flow<ReviewState>` from `ReviewPreferences` and read it once.

### WR-03: Play `Task` bridge resumes a potentially cancelled continuation

**File:** `app/src/main/java/com/warped/domain/review/ReviewHelper.kt:73-82`
**Issue:** Both `suspendCancellableCoroutine` bridges register success/failure listeners but never call `cont.invokeOnCancellation { }` and use unguarded `cont.resumeWith(...)`. If the parent job is cancelled while `requestReviewFlow()`/`launchReviewFlow()` is in flight, the later listener callback resumes an already-cancelled continuation, throwing `IllegalStateException` on the Play callback (main) thread. Combined with WR-01, cancellation can neither propagate nor complete cleanly.
**Fix:**
```kotlin
val reviewInfo = suspendCancellableCoroutine { cont ->
    val task = manager.requestReviewFlow()
    task.addOnSuccessListener { result ->
        if (cont.isActive) cont.resumeWith(Result.success(result))
    }
    task.addOnFailureListener { e ->
        if (cont.isActive) cont.resumeWithException(e)
        else Timber.w(e, "Review: request failed after cancellation")
    }
}
```

### WR-04: `openPlayStoreListing` skips https fallback on `SecurityException` and crashes non-Activity callers

**File:** `app/src/main/java/com/warped/ui/chat/components/BrowserIntents.kt:95-102`
**Issue:** (a) If the `market://` intent throws `SecurityException` (OEM exported-activity enforcement — the exact hazard the file header calls out for `openUrlInBrowser`), the outer catch toasts immediately without attempting the https fallback, unlike the `ActivityNotFoundException` path which does fall back. (b) Neither intent sets `FLAG_ACTIVITY_NEW_TASK`, and the catches handle only `ActivityNotFoundException`/`SecurityException` — any future caller passing an application context gets an uncaught `AndroidRuntimeException` (`Calling startActivity() from outside of an Activity context requires FLAG_ACTIVITY_NEW_TASK`).
**Fix:**
```kotlin
fun openPlayStoreListing(context: Context): Boolean {
    val packageName = context.packageName
    fun launch(uri: android.net.Uri): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        true
    } catch (_: Exception) { false } // ActivityNotFound + Security + bad-context
    if (launch("market://details?id=$packageName".toUri())) return true
    if (launch("https://play.google.com/store/apps/details?id=$packageName".toUri())) return true
    Toast.makeText(context, context.getString(R.string.toast_no_browser), Toast.LENGTH_SHORT).show()
    return false
}
```

### WR-05: Hook counts agentic loop-internal turns and unpersisted turns as completed turns

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:1115-1146`
**Issue:** The hook sits in the `StreamToken.Done` branch that the code's own comment (lines 1109-1114) states serves agentic loop turns through the identical path. Each loop-internal `Done` therefore increments `completed_turns`, so one user-visible turn with N tool-loop iterations counts as N turns — the 5-turn threshold can be reached after ~2 real turns, contradicting the RATE-01 policy. Separately, the hook fires unconditionally after the persist `try/catch` (line 1138 follows the catch at 1125), so a turn whose `saveMessage` failed still increments the counter for a turn that was never persisted.
**Fix:**
```kotlin
var turnPersisted = false
try {
    ...
    turnPersisted = true
} catch (e: Exception) { ... }
// only count user-visible, persisted, non-loop turns:
if (turnPersisted && !isLoopInternalTurn) {
    viewModelScope.launch(coroutineExceptionHandler) { ... }
}
```
At minimum, move the hook inside the `try` success path and gate on a loop-turn flag so only completed user turns advance the counter.

## Info

### IN-01: `recordPrompt` uses a pre-flow timestamp

**File:** `app/src/main/java/com/warped/domain/review/ReviewHelper.kt:68,83`
**Issue:** `nowMillis` is captured before `requestReviewFlow()`/`launchReviewFlow()` (which can take seconds on slow Play services) but recorded after. The 21-day cooldown therefore starts at check time rather than show time. Off by seconds — negligible, but capture the timestamp after the flow completes for exactness.
**Fix:** Call `System.currentTimeMillis()` fresh inside/after the launch block before `prefs.recordPrompt(...)`.

### IN-02: Duplicate Timber logging on the same failure

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:1144`
**Issue:** A `maybePrompt` failure is logged once inside `ReviewHelper` (`Timber.w`, line 85) and again by the hook's catch (`Timber.w`, line 1144). Every silent failure produces two identical log lines, obscuring log triage.
**Fix:** Remove the hook-level log (keep the inner one, which has the exception context), or remove the inner log and keep the hook-level one — not both.

### IN-03: Publicly mutable `reviewActivityProvider` on the ViewModel

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:227`
**Issue:** `var reviewActivityProvider` is public and settable from anywhere; any caller can overwrite or leak a capturing lambda. `@Volatile` covers visibility but not the encapsulation concern.
**Fix:** Make the setter internal or expose `fun setReviewActivityProvider(provider: (() -> Activity?)?)` with `@VisibleForTesting` if tests need it.

### IN-04: New ES string uses voseo in a file mixing tuteo and voseo

**File:** `app/src/main/res/values-es/strings.xml:91`
**Issue:** `settings_review_desc` uses voseo imperative ("Dejá una calificación") while nearby user-facing strings use tuteo ("Toca para reintentar", "Escribe un mensaje", "Selecciona un modelo"). The mix pre-exists in this file (voseo also appears in `help_*`/`lab_*` strings), so this follows one of the two existing registers — but the project should pick one register for ES and normalize.
**Fix:** Decide tuteo vs voseo project-wide; if tuteo, use "Deja una calificación". Copy review only — no functional impact.

---

_Note: the 17 test call-site files patched with `reviewHelper = mockk(relaxed = true)` (deviation #2 in 66-01-SUMMARY.md) were acknowledged as a mechanical compilation fix and spot-checked via the summary, not individually re-read._

_Reviewed: 2026-10-02T17:00:00Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
