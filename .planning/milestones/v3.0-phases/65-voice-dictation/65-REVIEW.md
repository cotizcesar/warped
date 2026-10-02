---
phase: 65-voice-dictation
reviewed: 2026-10-02T12:30:00Z
depth: standard
files_reviewed: 8
files_reviewed_list:
  - app/src/main/java/com/warped/ui/chat/voice/VoiceDictationManager.kt
  - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
  - app/src/main/java/com/warped/ui/chat/ChatUiState.kt
  - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
  - app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt
  - app/src/main/AndroidManifest.xml
  - app/src/main/res/values/strings.xml
  - app/src/main/res/values-es/strings.xml
findings:
  critical: 3
  warning: 5
  info: 2
  total: 10
status: issues_found
---

# Phase 65: Code Review Report

**Reviewed:** 2026-10-02T12:30:00Z
**Depth:** standard
**Files Reviewed:** 8
**Status:** issues_found

## Summary

Reviewed all 5 feat commits of Phase 65 (voice dictation) against the implementation summaries, the 65-UI-SPEC design contract, and AGENTS.md constraints (never-block-UI-thread, Keystore-only secrets, offline-first). The permission flow, rationale dialog, Settings escape, availability gating off the main thread, ApplicationContext ownership (no activity leak), EN+ES string parity (7/7 keys), and rotation safety (activity handles `orientation|screenSize` via `configChanges`, so no recreation mid-dictation) are all sound.

Three defects block shipping: the partial+final callback design guarantees duplicated dictated text (the core VOICE-01 behavior is wrong, not just rough), the listening flag is never cleared when the platform ends recognition on its own (stuck stop-toggle), and sending while listening orphans a live recognizer whose late results pollute the next draft with no on-screen way to stop it. Five warnings cover failure-state handling, cursor placement, stale availability, and PlayStore filtering. No secrets, injection, or threading violations found — `isAvailable()` runs on `Dispatchers.IO`, `StateFlow.update` is thread-safe under recognizer callbacks, and the app never records audio itself.

## Critical Issues

### CR-01: Partial and final results both appended — dictated text is duplicated

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:1430-1441` (callback wiring), `app/src/main/java/com/warped/ui/chat/voice/VoiceDictationManager.kt:69-75` (forwarding)
**Issue:** `onPartial` and `onFinal` both route into `appendDictation`. Platform partials are cumulative hypotheses, not deltas: for one utterance the listener fires `onPartialResults("hel")`, `onPartialResults("hello")`, `onPartialResults("hello world")`, then `onResults("hello world")`. Each one is appended with a separating space, producing `hel hello hello world hello world` in the draft. The 65-01 summary defers "dedup policy (if needed) to 65-02", but 65-02 shipped no dedup, so the duplication is live. This violates UI-SPEC section 3 (words appear as spoken, editable draft) and VOICE-01.
**Fix:**
```kotlin
// Track the last interim hypothesis; replace it instead of appending.
private var lastPartial = ""
onPartial = { hypothesis ->
    val trimmed = hypothesis.trim()
    updateInput { state ->
        val base = if (lastPartial.isNotEmpty() && state.inputText.endsWith(lastPartial)) {
            state.inputText.removeSuffix(lastPartial)
        } else state.inputText
        lastPartial = trimmed
        state.copy(inputText = (base.trimEnd() + " " + trimmed).trim())
    }
},
onFinal = { text ->
    lastPartial = ""
    appendDictation(text)
},
```
At minimum, drop the `onPartial -> appendDictation` path and append final-only until interim replacement is implemented.

### CR-02: `isListening` never cleared when recognition ends on its own — stuck stop-toggle

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:1433-1439`
**Issue:** The platform recognizer is single-shot: on silence/timeout it delivers `onResults` (or `onError`) and stops by itself. `onError` clears `_isListening`, but `onFinal` only calls `appendDictation` — the flag stays `true` forever. The input bar then permanently shows the stop icon with 0.5-alpha primary container for a recognizer that is no longer listening; tapping it calls `stopListening()` on an idle recognizer (harmless) and clears the flag, but until the user does so the UI lies. Same for the empty-final path: `firstResult` returns null on blank bundles, `onFinal` is never invoked, flag stays set.
**Fix:**
```kotlin
onFinal = {
    _isListening.value = false
    lastPartial = ""
    appendDictation(it)
},
```
Consider also clearing the flag when `firstResult` yields null (genuinely empty result still ends the session).

### CR-03: Sending while listening orphans a live recognizer with no stop affordance

**File:** `app/src/main/java/com/warped/ui/chat/ChatScreen.kt:378-384`, `app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt:179`
**Issue:** Mic and send coexist, and `onSend` never calls `stopDictation()`. Sequence: user taps mic, dictates, taps send while still listening. Generation starts → the `speechAvailable && !isGenerating` gate hides the mic entirely → the recognizer keeps listening with no on-screen indicator or stop control. Worse, `sendMessage` clears `inputText`, so late-arriving partials/finals from the orphaned session append phantom text into the fresh empty draft after send. The only teardown is leaving the screen. A microphone staying live with no in-app indicator is also a privacy regression (only the OS-level mic icon remains).
**Fix:**
```kotlin
onSend = {
    if (isListening) viewModel.stopDictation()
    snapToBottomOnNextContent = true
    ...
},
```
Defense-in-depth: also call `stopDictation()` at the top of `ChatViewModel.sendMessage()` so no future caller can repeat this.

## Warnings

### WR-01: `startDictation` reports listening=true even when `start()` fails

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:1449-1452`
**Issue:** `getDictationManager().start()` catches its own exceptions and synchronously invokes `onError`, which sets `_isListening.value = false` — then the next line unconditionally sets `_isListening.value = true`. A failed start (no permission edge, `ERROR_CLIENT`, recognizer busy) leaves the UI in the listening state with a dead recognizer (compounds CR-02's stuck toggle). The flag write order defeats the error path.
**Fix:**
```kotlin
fun startDictation() {
    getDictationManager().start()
    // start() reports failure via onError; only flip the flag if no error arrived.
    // Simplest correct form: have start() return Boolean success.
}
```
Change `VoiceDictationManager.start()` to return `Boolean` (false in the catch branch) and set `_isListening.value = result`.

### WR-02: No `cancel()` before restart — recognizer-busy failures on immediate re-tap

**File:** `app/src/main/java/com/warped/ui/chat/voice/VoiceDictationManager.kt:81-95`
**Issue:** After an error, `SpeechRecognizer` typically requires `cancel()` before the next `startListening()`, or the next start fails with `ERROR_RECOGNIZER_BUSY`. The manager reuses the same instance and only exposes `stop()`; a rapid stop→start tap (the exact toggle gesture this UI encourages) can fail, which then trips WR-01's stuck flag. `stopListening()` after an error is a no-op — it does not reset the error state.
**Fix:**
```kotlin
fun start() {
    try { recognizer?.cancel() } catch (e: Exception) { Timber.w(e, "Voice: cancel failed") }
    ... // then create-if-null and startListening
}
```

### WR-03: Dictation appends at end-of-text, not at cursor — UI-SPEC section 3 violation

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:1416-1428`
**Issue:** UI-SPEC section 3 requires "final + partial text appends at cursor". `appendDictation` always appends to `current.trimEnd()`. If the user placed the cursor mid-draft (e.g., to fix a word by voice), dictated text lands at the end instead. Honoring this needs `TextFieldValue` with `TextRange` plumbed through `ChatInputState`/`ChatInputBar`, which is a larger change — but the current behavior contradicts the contract the phase claims to satisfy.
**Fix:** Either implement cursor-aware insertion via `TextFieldValue` selection, or amend UI-SPEC section 3 to append-at-end and note the limitation. Do not leave spec and code disagreeing silently.

### WR-04: `speechAvailable` probed once at init and never refreshed

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:347-349`
**Issue:** If the recognition service is installed, enabled, or updated after the ViewModel is created (or the first probe raced a slow package load and read false), the mic never appears for the lifetime of the process. The screen already re-checks connectivity on `ON_RESUME`; availability deserves the same treatment.
**Fix:**
```kotlin
// Next to refreshConnectivity() on ON_RESUME:
fun refreshSpeechAvailability() {
    viewModelScope.launch(Dispatchers.IO) {
        _speechAvailable.value = dictationManager?.isAvailable() ?: getDictationManager().isAvailable()
    }
}
```

### WR-05: `RECORD_AUDIO` without `<uses-feature android.hardware.microphone required=false>` filters mic-less devices

**File:** `app/src/main/AndroidManifest.xml:9`
**Issue:** Declaring `RECORD_AUDIO` implies a microphone hardware requirement at Play filtering time. Devices without a mic (some TVs, headless test devices) would be excluded from Play even though the app was explicitly engineered for graceful no-recognizer fallback (VOICE-03, `speechAvailable` gate). Verified: no `uses-feature` declaration exists in the manifest.
**Fix:**
```xml
<uses-feature android:name="android.hardware.microphone" android:required="false" />
```

## Info

### IN-01: Permanent-denial detection silently degrades if context is not an Activity

**File:** `app/src/main/java/com/warped/ui/chat/ChatScreen.kt:272-275`
**Issue:** `val permanent = activity?.let { ... } ?: false` — if `LocalContext.current` ever returns a non-Activity context (previews, wrapped contexts in tests), a permanent denial is misclassified as transient and stays silent with no Settings escape and no log. Practically unreachable in production (the screen always composes under `MainActivity`), but a one-line `Timber.w` on the null branch would make the fallback observable.
**Fix:** Add `?: run { Timber.w("Voice: non-Activity context, assuming transient denial"); false }`.

### IN-02: Rationale dialog reappears on every ungranted tap, not just the first

**File:** `app/src/main/java/com/warped/ui/chat/ChatScreen.kt:284-292`
**Issue:** UI-SPEC frames the rationale as first-tap; the implementation shows `WarpedAlertDialog` on every tap while ungranted, including immediately after a transient denial. This is arguably better UX than the spec (each tap re-explains), but spec and behavior differ. Either persist a "rationale seen" flag and request directly on later taps, or update the UI-SPEC trigger wording to "every ungranted tap".
**Fix:** Doc-or-behavior alignment only; no crash or data risk.

---

_Reviewed: 2026-10-02T12:30:00Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
