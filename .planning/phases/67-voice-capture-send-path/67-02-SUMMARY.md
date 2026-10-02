---
phase: 67-voice-capture-send-path
plan: 02
type: execute
wave: 2
depends_on: ["01"]
files_modified:
  - app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt
  - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
  - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
  - app/src/main/res/values/strings.xml
  - app/src/main/res/values-es/strings.xml
  - app/src/test/java/com/warped/ui/chat/VoiceMessageGuardTest.kt
autonomous: true
requirements: [VMSG-01, VMSG-05]
must_haves:
  truths:
    - "User sees a live mm:ss timer plus amplitude bar while recording, both turning red in the last 10 seconds"
    - "User hitting the 60 s cap keeps the clip with a 60s-limit toast; cancel discards the file immediately"
    - "User sending a clip longer than 30 s sees a First-30s note; voice send on text-only models or remote endpoints is blocked with feedback, never a dead button"
    - "RECORD_AUDIO denial never starts recording and never crashes, with a Settings escape on permanent denial"
  artifacts:
    - path: "app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt"
      provides: "Recording row (timer plus amplitude plus cancel) and guarded voice button"
      contains: "voiceElapsedSec"
    - path: "app/src/main/java/com/warped/ui/chat/ChatScreen.kt"
      provides: "Permission flow, toast/note channels, background auto-stop observer"
      contains: "pendingVoiceRequest"
    - path: "app/src/main/res/values/strings.xml"
      provides: "Voice-message copy with matching ES translations"
      contains: "voice_msg_cap_reached"
subsystem: voice-capture
tags: [voice, recording-ui, permissions, guards, i18n]
dependency_graph:
  requires:
    - voice-recorder-tracer
    - pcm-transcode-path
    - voice-send-wiring
  provides:
    - voice-recording-ux
    - voice-permission-flow
    - voice-guards-i18n
  affects:
    - phase-68-voice-draft
    - phase-69-voice-polish
tech_stack:
  added: []
  patterns:
    - "Shared permission launcher with pending-intent routing (no second launcher)"
    - "One-shot SharedFlow toast events (collect flow, never derive from state)"
key_files:
  created:
    - app/src/test/java/com/warped/ui/chat/VoiceMessageGuardTest.kt
  modified:
    - app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt
    - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
    - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
    - app/src/main/res/values/strings.xml
    - app/src/main/res/values-es/strings.xml
decisions:
  - "Nine voice_msg_* strings shipped (six spec'd plus remote-blocked, transcode-failed, recording-state) so zero hardcoded voice copy remains"
  - "startDictation stops an active voice recording (keeps clip); cancelVoiceRecording also deletes a previously kept clip"
  - "Device smoke recorded as deferred release-UAT (no hardware); phase passes on unit + build evidence"
metrics:
  duration: "~90 min"
  completed: "2026-10-02"
---

# Phase 67 Plan 02: Recording UX + Guards Summary

**One-liner:** Shippable VMSG-01/VMSG-05 experience — inline recording row with red last-10 s, shared-launcher permission flow with Settings escape, cap toast, First-30s note, text-only/remote guard toasts, background auto-stop, and nine EN+ES strings with the full 955-test suite green.

## Tasks Completed

| # | Name | Commit | Files |
|---|------|--------|-------|
| 1 | Recording row UI plus permission flow | fcbc202f | ChatInputBar.kt, ChatScreen.kt, ChatViewModel.kt, VoiceMessageRecorder.kt |
| 2 | Cap toast, truncation note, minimal guards, lifecycle | 555f330c | ChatScreen.kt, VoiceMessageGuardTest.kt (+VM cancel fix) |
| 3 | EN+ES strings plus full-suite gate | cd3b0f32 | strings.xml ×2, ChatViewModel.kt, ChatScreen.kt, ChatInputBar.kt |
| 4 | Device smoke — record, send, model response | — | DEFERRED (no hardware; see deferred-items.md) |

## What Was Built

- **Recording row (ChatInputBar):** replaces the text field inline while recording — mm:ss timer (Label 14sp, tabular-nums) + LinearProgressIndicator amplitude bar + 48dp cancel X + stop toggle. Timer and bar render `onSurfaceVariant`, switch to `colorScheme.error` at ≥ 50 s. TalkBack `stateDescription` ("Recording, m:ss" resourced with format args) throttled to 5 s buckets. Dead `onAudioRecordingChanged` adopted — now fires with the live flag via LaunchedEffect. Voice button uses GraphicEq glyph with Record/Stop content descriptions.
- **Permission flow (ChatScreen + VM):** `pendingVoiceRequest` (DICTATION/VOICE) intent routes through the EXISTING micPermissionLauncher — no second launcher. First ungranted tap shows the rationale; confirm fires the system request; rationale dismiss clears the intent. Granted starts the requested mode. Voice transient denial → plain voice-denied Snackbar (`emitVoiceDeniedTransient`); permanent → SnackbarWithAction Settings escape (`emitVoiceDenied`). Dictation behavior unchanged.
- **Mutual exclusion:** `startVoiceRecording` stops dictation (Plan 01); `startDictation` now stops an active voice recording, keeping the clip.
- **Cap toast + note + guards:** `voiceCapEvent` collected once-per-emission as a LENGTH_SHORT toast (never state-derived). >30 s sends emit the First-30s Snackbar via the existing host (wired in Plan 01, resourced here). Text-only model tap → `error_no_audio` toast (existing resource); remote endpoint tap → `voice_msg_remote_blocked` toast; `isLoadingModel` locks the bar via the existing `inputLocked` gate (verified, not duplicated). Failed sends keep the file for retry.
- **Lifecycle:** existing ON_RESUME observer extended with ON_PAUSE → `autoStopVoiceRecording()` (auto-stop-and-keep, no foreground service). Rotation survives via VM-owned recorder + flows. `cancelVoiceRecording` also deletes a previously kept clip (cancel-after-stop discards).
- **Strings:** nine `voice_msg_*` names identical in `values/` and `values-es/` (cap_reached, first_30s, denied, cancel, record, stop per spec — plus remote_blocked, transcode_failed, recording_state so zero hardcoded voice copy remains; deviation documented below). All Plan 01/02 hardcoded copy replaced; `grep` for the literal English copy in main sources returns 0.
- **Guard tests:** `VoiceMessageGuardTest` (JUnit5 + VoiceDictationTest-style harness + turbine + fake recorder): text-only + audioBytes hits the `error_no_audio` backstop; blank-text/null-audio send is a no-op; cancel-after-stop deletes the kept clip from disk.

## Verification Evidence

- `./gradlew :app:assembleDebug`: **BUILD SUCCESSFUL** (after Task 1 and after Task 3).
- `grep -c "voiceElapsedSec\|onCancelRecording\|pendingVoiceRequest"` across ChatInputBar/ChatScreen: non-zero (recording UI wiring present).
- `./gradlew :app:testDebugUnitTest --tests "com.warped.ui.chat.VoiceMessageGuardTest"`: **BUILD SUCCESSFUL** (3 tests).
- `./gradlew :app:testDebugUnitTest` (FULL suite): **BUILD SUCCESSFUL — 955 tests, 0 failures, 0 errors, 99 test classes** (2026-10-02).
- Hardcoded-copy sweep `grep -rn "60s limit reached\|First 30s sent to model" app/src/main/java/ | grep -v test | wc -l`: **0**. Full sweep for all other voice literals: **0**.
- Zero new Gradle dependencies; no manifest, build-config, or dependency change (strings-only resources, per T-67-SC).
- Task 4 device smoke: **deferred** — recorded in `deferred-items.md` with a release-UAT runbook; unit + build evidence listed there.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing critical] cancelVoiceRecording did not delete a previously kept clip**
- **Found during:** Task 2 (guard-test requirement: cancel-after-stop deletes the file)
- **Issue:** Plan 01 cancel only cleared the in-progress session; a kept clip from an earlier stop survived on disk with no UI path to remove it.
- **Fix:** `cancelVoiceRecording` now also deletes `voiceClipFile` from disk before nulling it.
- **Files modified:** ChatViewModel.kt (in fcbc202f scope via Task 1 commit; covered by guard test)
- **Commit:** 555f330c (test) over fcbc202f (fix)

**2. [Rule 2 - Missing critical] Three extra strings beyond the six spec'd**
- **Found during:** Task 3 (acceptance: zero hardcoded voice copy)
- **Issue:** The remote-blocked toast (Task 2), transcode-failure Snackbar (Plan 01), and TalkBack recording-state format had no spec'd resource home; leaving any hardcoded would fail acceptance.
- **Fix:** Shipped `voice_msg_remote_blocked`, `voice_msg_transcode_failed`, `voice_msg_recording_state` EN+ES (nine total). All user-visible voice copy resourced.
- **Files modified:** values/strings.xml, values-es/strings.xml
- **Commit:** cd3b0f32

### Pre-existing Failure (out of scope, not fixed)

**[Flake] `ModelSwitchUnloadTest.failed mount surfaces error and keeps draft`**
- Same finding as Plan 01 (fails intermittently in full-package runs on base sources too; passes in isolation and passed in the final 955-test gate). Logged in `deferred-items.md`; not fixed.

## Known Stubs

None. Every Plan 02 behavior is wired to real state/events/resources. The deferred device smoke is environment-gated verification, not a code stub.

## Threat Flags

None — no new surface beyond the plan's threat model (T-67-04 fixed resource strings only, never interpolated clip data; T-67-05 reuses the Phase 65 rationale→request→Settings chain with grant-gated start; T-67-06 non-mic glyph + distinct Record/Stop/Cancel descriptions; T-67-SC strings-only resources).

## Self-Check: PASSED

- ChatInputBar.kt, ChatScreen.kt, ChatViewModel.kt, both strings.xml, VoiceMessageGuardTest.kt: FOUND on disk.
- Commits fcbc202f, 555f330c, cd3b0f32: FOUND in `git log`.
- Full suite 955/955 green; assembleDebug BUILD SUCCESSFUL; hardcoded-copy grep 0.
