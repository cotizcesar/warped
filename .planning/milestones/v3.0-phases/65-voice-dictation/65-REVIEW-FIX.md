---
phase: 65-voice-dictation
fixed_at: 2026-10-02T16:09:41Z
review_path: .planning/phases/65-voice-dictation/65-REVIEW.md
iteration: 1
findings_in_scope: 10
fixed: 10
skipped: 0
status: all_fixed
---

# Phase 65: Code Review Fix Report

**Fixed at:** 2026-10-02T16:09:41Z
**Source review:** .planning/phases/65-voice-dictation/65-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 10 (3 critical + 5 warnings + 2 info)
- Fixed: 10
- Skipped: 0

**Verification:**
- `./gradlew :app:testDebugUnitTest --offline` — 91 suites, 899 tests, 0 failures, 0 errors (includes 8 new VoiceDictationTest tests)
- `./gradlew :app:assembleDebug --offline` — BUILD SUCCESSFUL
- No source files left broken; no uncommitted source changes remain in the fix branch
- STATE.md / ROADMAP.md untouched per instructions

## Fixed Issues

### CR-01: Partial and final results both appended — dictated text is duplicated

**Files modified:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
**Commit:** 0082c908
**Applied fix:** Replaced the `onPartial/onFinal -> appendDictation` wiring (and removed `appendDictation`, which had no other callers) with a single-insertion state machine: `lastPartial` + `partialAnchor` track the standing hypothesis; `onDictationPartial` replaces it in place (new utterances or user-edited drafts fall back to a fresh cursor insert); `onDictationFinal` swaps the standing hypothesis for the final exactly once. Late callbacks after session end are dropped by a listening guard (shared with CR-03). Added `internal` handlers plus a `dictationManagerOverride` MockK seam for tests.

### CR-02: `isListening` never cleared when recognition ends on its own — stuck stop-toggle

**Files modified:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` (final handler clears the flag), `app/src/main/java/com/warped/ui/chat/voice/VoiceDictationManager.kt` (empty-final forwarding)
**Commit:** 8003a5d6
**Applied fix:** `onDictationFinal` always clears `_isListening` (blank finals clear the flag with the draft untouched), and the manager's `onResults` now forwards `firstResult(...).orEmpty()` instead of dropping blank bundles — an empty natural end-of-speech still ends the session. (Flag-clearing half of the final handler shipped with the CR-01 state machine; this commit covers the empty-result path the reviewer called out.)

### CR-03: Sending while listening orphans a live recognizer with no stop affordance

**Files modified:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`, `app/src/main/java/com/warped/ui/chat/ChatScreen.kt`
**Commit:** d821dc9a
**Applied fix:** `ChatScreen.onSend` stops dictation first when listening (mic never live without its indicator), and `ChatViewModel.sendMessage()` calls `stopDictation()` at its top as defense-in-depth so no future caller can repeat this. Committed partial text stays in the draft and sends normally; post-send late callbacks are dropped by the CR-01 listening guard.

### WR-01: `startDictation` reports listening=true even when `start()` fails

**Files modified:** `app/src/main/java/com/warped/ui/chat/voice/VoiceDictationManager.kt`, `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
**Commit:** 7814ed50
**Applied fix:** `VoiceDictationManager.start()` now returns `Boolean` (false in the catch branch, after firing `onError` as before); `startDictation()` assigns `_isListening.value = getDictationManager().start()` and resets partial tracking up front. A failed start leaves the UI idle instead of stranded on a dead recognizer.

### WR-02: No `cancel()` before restart — recognizer-busy failures on immediate re-tap

**Files modified:** `app/src/main/java/com/warped/ui/chat/voice/VoiceDictationManager.kt`
**Commit:** 90ebff6f
**Applied fix:** `start()` issues a best-effort `recognizer?.cancel()` before (re)attaching the listener and calling `startListening`, resetting post-error busy state (`stopListening` alone does not). Failures log via Timber and fall through to the normal start path.

### WR-03: Dictation appends at end-of-text, not at cursor — UI-SPEC section 3 violation

**Files modified:** `app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt`, `app/src/main/java/com/warped/ui/chat/ChatScreen.kt`, `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` (insert helper + `updateInputCursor`, shipped with CR-01)
**Commit:** d3d94a79
**Applied fix:** True append-at-cursor per UI-SPEC section 3: `ChatInputBar` Row 1 now holds a `TextFieldValue` (selection-preserving sync on external dictation updates, cursor reported via new `onCursorChange` param), `ChatScreen` wires it to `ChatViewModel.updateInputCursor()`, and insertion uses single-space-separated cursor placement with the partial anchor recorded at the insertion start. Unknown cursor (-1) falls back to end-of-text.

### WR-04: `speechAvailable` probed once at init and never refreshed

**Files modified:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`, `app/src/main/java/com/warped/ui/chat/ChatScreen.kt`
**Commit:** e21d7b73
**Applied fix:** New `refreshSpeechAvailability()` (same `Dispatchers.IO` off-hot-path pattern as init) called from the existing `ON_RESUME` observer next to `refreshConnectivity()`, so a late-installed/enabled recognizer reveals the mic without a process restart.

### WR-05: `RECORD_AUDIO` without `<uses-feature android.hardware.microphone required=false>` filters mic-less devices

**Files modified:** `app/src/main/AndroidManifest.xml`
**Commit:** 02cfd3a1
**Applied fix:** Added `<uses-feature android:name="android.hardware.microphone" android:required="false" />` with an explanatory comment, preserving VOICE-03 installability alongside the graceful no-recognizer fallback.

### IN-01: Permanent-denial detection silently degrades if context is not an Activity

**Files modified:** `app/src/main/java/com/warped/ui/chat/ChatScreen.kt`
**Commits:** cb96fe35 + d7f49968 (follow-up fixing the `activity` reference flagged by `compileDebugKotlin`)
**Applied fix:** Restructured to an explicit null-branch that logs `Timber.w("Voice: non-Activity context, assuming transient denial")` and assumes transient, keeping the Settings escape robust for the real Activity path.

### IN-02: Rationale dialog reappears on every ungranted tap, not just the first

**Files modified:** `app/src/main/java/com/warped/ui/chat/ChatScreen.kt`
**Commit:** 15f60b80
**Applied fix:** Behavior aligned to the spec (first-tap rationale): new `rememberSaveable voiceRationaleSeen` flag; confirm/dismiss mark it seen, and later ungranted taps fire the system permission request directly instead of re-showing the dialog. Rotation-safe; process death re-shows once (safe direction).

## Tests

**Commit:** 731a4538 — new `app/src/test/java/com/warped/ui/chat/VoiceDictationTest.kt` (MockK + runTest + Truth, mirroring the `ChatSubStateTest` ViewModel harness with a `dictationManagerOverride` fake):

- partials replace in place — one utterance yields exactly one insertion (CR-01)
- final without partials appends once and clears listening (CR-01/CR-02)
- blank final clears listening without touching the draft (CR-02)
- error clears listening and keeps the partial draft (silent-error policy)
- failed start never reports listening (WR-01)
- send stops dictation and late callbacks never pollute the fresh draft (CR-03)
- dictation inserts at the reported cursor, finals revise in place (WR-03)
- stop clears listening and drops the trailing final

## Notes

- `gsd-tools` is not installed in this environment, so per-finding commits were made with `git commit` directly in the required `fix(65): …` conventional format (message first, explicit file list staged per commit).
- The isolated worktree needed a copy of the gitignored `local.properties` (SDK path) to run Gradle; it is gitignored and was never staged or committed.
- Gradle build outputs under `app/build/` and `.gradle/` are tracked in this repo and were dirtied by verification builds; they were reverted (`git checkout --`) after each run so fix commits contain only source/test files.
- Commit order follows dependency order (WR-02 → WR-01 → CR-01 → CR-02 → CR-03 → WR-03 → WR-04 → WR-05 → IN-01 → IN-02 → tests) rather than severity order, because the manager contract changes (Boolean start, empty-final forwarding) are prerequisites of the ViewModel state machine.

---

_Fixed: 2026-10-02T16:09:41Z_
_Fixer: the agent (gsd-code-fixer)_
_Iteration: 1_
