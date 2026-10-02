---
phase: 69-voice-differentiation-gating-transcript
reviewed: 2026-10-02T17:30:00Z
depth: standard
files_reviewed: 15
files_reviewed_list:
  - app/src/main/java/com/warped/ui/chat/voice/VoiceSendGate.kt
  - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
  - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
  - app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt
  - app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt
  - app/src/main/java/com/warped/data/local/preferences/VoicePreferences.kt
  - app/src/main/java/com/warped/ui/help/HelpScreen.kt
  - app/src/main/java/com/warped/ui/navigation/NavGraph.kt
  - app/src/main/res/values/strings.xml
  - app/src/main/res/values-es/strings.xml
  - app/src/test/java/com/warped/ui/chat/voice/VoiceSendGateTest.kt
  - app/src/test/java/com/warped/ui/chat/VoiceGatingTest.kt
  - app/src/test/java/com/warped/ui/chat/VoiceMessageGuardTest.kt
  - app/src/test/java/com/warped/ui/chat/VoiceTranscriptTest.kt
  - app/src/test/java/com/warped/ui/chat/VoiceCoachmarkTest.kt
findings:
  critical: 1
  warning: 3
  info: 4
  total: 8
status: issues_found
---

# Phase 69: Code Review Report

**Reviewed:** 2026-10-02T17:30:00Z
**Depth:** standard
**Files Reviewed:** 15
**Status:** issues_found

## Summary

Reviewed all Phase 69 source changes (commits `3a2bdcf2`, `1b1db167`, `fb834919`):
provider-keyed `VoiceSendGate` + VM gate flow + gated input-row surface (plan 01),
parallel transcript STT session + send-time stamping + bubble captions (plan 02),
and DataStore coachmark flag + tooltip + Help Section 9 + nav wiring (plan 03).

Gate-helper ordering, draft-kept send blocks, dispatcher usage (`Dispatchers.IO`
for DataStore persist), EN/ES string parity (all new keys present in both locales,
`Cambiá` voseo consistent with pre-existing `bubble_tools_unsupported`), Help
navigation safety (internal `Screen.Help` route, no external intent/URI, no PII
in route), and transcript handling (plain-text Compose rendering, no transcript
text in any `Timber` call, Room-only persistence) all check out.

One critical mutual-exclusion race survives in the recorder spin-up window, plus
three warnings (legacy provider mis-gating, trailing-STT callback pollution,
repetition-swallowing dedup). No new permissions, components, or secrets introduced.

## Critical Issues

### CR-01: Dictation started during recorder spin-up runs two STT sessions concurrently

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:1788`
**Issue:** `startDictation()` (line 1788) checks only `_isVoiceRecording.value`
before starting the dictation recognizer. During the recorder spin-up window
(`voiceStarting == true`, `_isVoiceRecording == false` — set at line 2169 and
in the IO coroutine), that check is false, so dictation starts while the pending
IO block is still alive. When the recorder accepts, the IO block sets
`_isVoiceRecording = true` and starts the transcript STT session
(`getTranscriptManager().start()`) while dictation is live — two concurrent
`SpeechRecognizer` sessions, i.e. exactly the `ERROR_RECOGNIZER_BUSY` collision
the phase claims to prevent ("strictly sequential" per the comments at 1788).
The reverse direction is safe (`startVoiceRecording` calls `stopDictation()`
synchronously first), and `stopVoiceRecording`/`autoStopVoiceRecording` honor
the spin-up window via `PendingVoiceStop` (lines 2299, 2392) — `startDictation`
is the only entry point that ignores `voiceStarting`. Result: STT error on one
session plus dual live input modes, breaking the VMSG mutual-exclusion requirement.
**Fix:**
```kotlin
fun startDictation() {
    stopTranscriptSession()
    if (voiceStarting && !_isVoiceRecording.value) {
        // WR-05 mirror: a stop tapped during spin-up is honored by the IO
        // coroutine — the session never becomes visible, so the transcript
        // STT session below never overlaps dictation.
        pendingVoiceStop = PendingVoiceStop.KEEP
    } else if (_isVoiceRecording.value) stopVoiceRecording()
    ...
}
```
The pending-stop path already freezes a null transcript holder and never starts
STT, so dictation then starts on a clean, recognizer-free state in both interleavings.

## Warnings

### WR-01: `VoiceSendGate.evaluate` mis-gates legacy `ProviderType.LOCAL` as remote

**File:** `app/src/main/java/com/warped/ui/chat/voice/VoiceSendGate.kt:40`
**Issue:** Any provider that is not `LITE_RT_LM` returns `GatedRemote`, so the
deprecated-but-persisted `ProviderType.LOCAL` evaluates to `GatedRemote` with the
wrong reason string. The VM never passes it today (it derives `LITE_RT_LM` from
`selectedLocalModelId`), but the helper is documented as the single future-proof
entry point ("Unlocking remote voice-send later means changing this one function,
never call sites"), and persisted rows can still carry `LOCAL`
(`ActiveModelSelection.kt:67`, with an explicit legacy branch in `ChatViewModel`
send routing). A future caller passing the stored provider type gets a
"Device-only" remote explainer for an on-device model.
**Fix:**
```kotlin
if (providerType == ProviderType.LITE_RT_LM) {
    return if (localAudioCapable == false) GateState.GatedTextOnly else GateState.Allowed
}
@Suppress("DEPRECATION")
if (providerType == ProviderType.LOCAL) {
    return if (localAudioCapable == false) GateState.GatedTextOnly else GateState.Allowed
}
return GateState.GatedRemote
```

### WR-02: Trailing transcript callbacks can pollute the next session's buffer

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:2069`
**Issue:** `stopTranscriptSession()` calls `stopListening()`, which delivers a
final `onResults` asynchronously on the binder thread. `onTranscriptFinal` /
`onTranscriptPartial` mutate `transcriptFinalized` / `_voiceTranscriptLive`
unconditionally, so a trailing final from session N can land after session N+1
reset the buffer at entry (lines 2177, 2236), freezing stale words into N+1's
holder. Same-manager listener replacement in `VoiceDictationManager.start()`
does not help: the trailing result is delivered to the newly set listener.
The read-modify-write in `onTranscriptFinal` is also unsynchronized beyond
`@Volatile` (lost update under concurrent binder callbacks).
**Fix:** Guard callbacks with a session generation counter, e.g. capture
`val session = transcriptSession` at session start and return early when
`session != transcriptSession`; bump the counter in `stopTranscriptSession`,
`clearTranscriptState`, and at recording entry alongside the existing
`voiceClipSession++` discipline.

### WR-03: `endsWith` dedup swallows intentional word repetitions

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:2072`
**Issue:** `onTranscriptFinal` drops any final that is a suffix of the committed
text, and `joinTranscript` (line 2093) does the same for partials. A user who
says "yes … yes" (two utterance finals) gets one "yes" frozen into the holder —
a suffix check cannot distinguish duplicate delivery from genuine repetition.
Honest-caption requirement (VMSG-07) is weakly violated for repeated short phrases.
**Fix:** Track delivery identity instead of text equality — e.g. only skip when
the final arrives without an intervening partial (true duplicate delivery), or
compare against the last *partial* hypothesis rather than the committed buffer:
```kotlin
if (clean.isEmpty() || (transcriptFinalized.endsWith(clean) && _voiceTranscriptLive.value == transcriptFinalized)) return
```

## Info

### IN-01: Stale Plan-01 extension-point comment left beside Plan-02 implementation comment

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:1788-1798`
**Issue:** `startDictation` carries two adjacent comments saying the same thing
(the Plan-01 "extension point (VMSG-07, Plan 02 owns)" block plus the Plan-02
"stops FIRST" block). Dead planning scaffolding in production code.
**Fix:** Delete the superseded "Phase 69 Plan 01 extension point" comment block,
keep the Plan-02 one.

### IN-02: Unnecessary `@Suppress("DEPRECATION")` on `VoiceSendGate.evaluate`

**File:** `app/src/main/java/com/warped/ui/chat/voice/VoiceSendGate.kt:34`
**Issue:** The function body references no deprecated declaration (comparison
against `LITE_RT_LM` only), so the suppression is dead and masks future
legitimate deprecation warnings in this function.
**Fix:** Remove the annotation (re-add scoped to the `LOCAL` branch if WR-01 is fixed).

### IN-03: `voiceTranscriptLive` exposed but never collected

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:2030`
**Issue:** The `internal` live-transcript flow has no collector (no live caption
during recording; tests drive the callbacks directly). Either dead API surface
or an unfinished live-caption hook — pick one.
**Fix:** If no live UI is planned, make it `private` (tests already reach the
`internal` callbacks); if a live caption is intended, file a follow-up task.

### IN-04: Help section number embedded in localized strings; triplicated nav lambda

**File:** `app/src/main/java/com/warped/ui/help/HelpScreen.kt:186`, `app/src/main/java/com/warped/ui/navigation/NavGraph.kt:360,382,408`
**Issue:** `"9. Voice messages"` hardcodes ordering into `help_s9_title` (EN+ES) —
inserting a section renumbers by re-translation. The identical
`onNavigateToHelp = { navController.navigate(Screen.Help) { launchSingleTop = true } }`
lambda is pasted at all three chat destinations instead of a shared local val.
**Fix:** Number sections programmatically (`"${index + 1}. $title"`) or accept
and document; hoist the nav lambda to one `val navigateToHelp` in `WarpedNavGraph`.

## Fix notes (2026-10-02, gsd-code-fixer)

- **CR-01 — fixed** (`e4df0e7d`; logic fix, requires human verification of the
  interleaving): `startDictation()` now mirrors the WR-05 `PendingVoiceStop`
  discipline — dictation tapped during recorder spin-up (`voiceStarting &&
  !_isVoiceRecording`) records `PendingVoiceStop.KEEP` so the in-flight IO
  block freezes a null transcript holder and returns before transcript STT
  start. New regression test `dictation during recorder spin-up suppresses
  transcript STT` in `VoiceTranscriptTest` (`76f5e48c`, passes; fails on the
  old code by design since `transcript.start()` would fire).
- **WR-01 — fixed** (`99b36741`): `VoiceSendGate.evaluate` treats legacy
  `ProviderType.LOCAL` as on-device (same `localAudioCapable` rule as
  `LITE_RT_LM`). `VoiceSendGateTest.legacy LOCAL…` updated — it previously
  encoded the buggy `GatedRemote` expectation.
- **WR-02 — fixed** (`e4df0e7d`; concurrency logic, requires human
  verification): `transcriptSession: AtomicLong` generation bumped at
  recording entry, STT start, `stopTranscriptSession`, and (via stop)
  `clearTranscriptState`; the transcript manager is torn down and recreated
  per STT session with the generation captured in its listener lambdas so
  trailing binder-thread `onResults` from session N are dropped; the
  read-modify-write in `onTranscriptPartial`/`onTranscriptFinal` is
  synchronized on `transcriptLock`. Test seam (`transcriptManagerOverride`)
  keeps its instance, so existing direct-drive callback tests are unaffected.
- **WR-03 — fixed** (`e4df0e7d`): `onTranscriptFinal` now skips only true
  duplicate delivery (`endsWith(clean) && live == committed`, i.e. no new
  partial hypothesis arrived); genuine repetitions with an intervening
  partial are appended. `joinTranscript` unchanged (cumulative-hypothesis
  handling for partials is correct as-is).
- **IN-01 — fixed** (`e4df0e7d`): stale Plan-01 extension-point comment removed
  in the same `startDictation` edit.
- **IN-02 — resolved as a side effect** of WR-01: the function-level
  `@Suppress("DEPRECATION")` is now genuinely needed (references
  `ProviderType.LOCAL`), so the suppression is no longer dead. No diff.
- **IN-03 — skipped** (needs a product decision, not a safe cleanup):
  `voiceTranscriptLive` is still uncollected. Making it `private` breaks the
  existing `VoiceTranscriptTest` reads; deleting vs wiring a live caption is
  a follow-up task call, not a review fix.
- **IN-04 — partially fixed** (`f42aa985`): the triplicated
  `onNavigateToHelp` lambda is hoisted to one `navigateToHelp` val in
  `WarpedNavGraph`. The hardcoded `"9. …"` section numbering in localized
  strings is intentionally left alone — renumbering programmatically touches
  EN+ES strings and Help layout for zero behavioral gain.

Verification: affected suites green (`VoiceSendGateTest` 7/7,
`VoiceTranscriptTest` 7/7, `VoiceGatingTest` 6/6, `VoiceMessageGuardTest`
3/3, `VoiceCoachmarkTest` 4/4); full `:app:testDebugUnitTest` 1019 tests,
0 failures/errors; `:app:assembleDebug` BUILD SUCCESSFUL.

---
_Reviewed: 2026-10-02T17:30:00Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
