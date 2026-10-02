---
phase: 65-voice-dictation
plan: "02"
subsystem: chat-voice-dictation-ui
tags: [speech-recognizer, record-audio, dictation, chat-input, permission-flow, snackbar]
dependency_graph:
  requires:
    - phase: 65-01
      provides: VoiceDictationManager wrapper, ChatEvent.SnackbarWithAction denial event, ViewModel dictation ownership (speechAvailable, isListening, start/stopDictation, emitMicDenied), RECORD_AUDIO manifest declaration plus 7 EN+ES dictation strings
  provides:
    - ChatInputBar mic button with speechAvailable gating, listening stop-toggle, and generation hiding
    - ChatScreen RECORD_AUDIO permission launcher with first-tap rationale and permanent-denial Settings Snackbar
    - Recognizer lifecycle teardown on screen leave
  affects:
    - future chat-input work (mic slot is left of send/stop; dead modelHasAudio params still untouched per UI-SPEC non-goals)
tech_stack:
  added: []
  patterns:
    - Runtime permission via rememberLauncherForActivityResult with ActivityResultContracts.RequestPermission
    - Permanent-denial detection via shouldShowRequestPermissionRationale false after denial
    - Settings escape via fixed ACTION_APPLICATION_DETAILS_SETTINGS package-URI intent
key_files:
  created: []
  modified:
    - app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt
    - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
key_decisions:
  - "Mic teardown reuses the existing DisposableEffect block instead of a second one so all screen-leave cleanup stays in one place"
  - "Denial collector resolves the SnackbarAction enum with an explicit when so future actions extend without touching the intent code"
patterns-established:
  - "Permission checks live inside click lambdas, never on the composition hot path"
  - "Transient denial and recognition errors stay silent; only permanent denial surfaces a Snackbar"
requirements-completed: [VOICE-01, VOICE-02, VOICE-03]
duration: ~20 min
completed: 2026-10-02
---

# Phase 65 Plan 02: Voice-Dictation UI Wiring Summary

**Chat input mic button with listening stop-toggle plus first-tap rationale, RECORD_AUDIO launcher, permanent-denial Settings Snackbar, and screen-leave teardown — all reusing existing components with zero new composables**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-10-02T12:00:00Z
- **Completed:** 2026-10-02
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- Mic button in the chat input bar: renders left of the send/stop slot only when `speechAvailable` is true, swaps to a full-opacity stop icon on a 0.5-alpha primary container while listening, hidden while generating so no two stop icons ever appear together
- Complete permission flow: first mic tap shows the in-context `WarpedAlertDialog` rationale, Allow Microphone confirm fires the system `RECORD_AUDIO` request, permanent denial shows a Long Snackbar with a Settings action deep-linking to the app details page, transient denial stays silent
- Recognizer lifecycle teardown: `stopDictation()` in the screen's `DisposableEffect.onDispose` (ViewModel `onCleared` destroy from 65-01 stays as the second guarantee)
- All three Phase 65 success criteria hold: dictated text lands editable without auto-sending (65-01 append path now reachable from the UI), rationale + permission + Settings escape wired, mic hides without a recognizer or while generating

## Task Commits

Each task was committed atomically:

1. **Task 1: Mic button in ChatInputBar with listening toggle** - `3755342e` (feat)
2. **Task 2: ChatScreen permission flow, rationale, Snackbar, and lifecycle** - `3ddcc912` (feat)

**Plan metadata:** docs commit owned by orchestrator (STATE.md/ROADMAP.md writes excluded per dispatch instructions)

## Files Created/Modified

- `app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt` - Three defaulted params (`speechAvailable`, `isListening`, `onMicClick`); mic `IconButton` (40dp button, 24dp icon) left of the send/stop slot with `cd_dictate`/`cd_stop_listening` descriptions, listening container tint, `speechAvailable && !isGenerating` gate, 8dp spacing only; dead `modelHasAudio`/`onAudioRecorded`/`onAudioRecordingChanged` params untouched
- `app/src/main/java/com/warped/ui/chat/ChatScreen.kt` - `speechAvailable`/`isListening` collection via `collectAsStateWithLifecycle`, click-lambda permission gate, `RequestPermission` launcher with permanent/transient denial split, `WarpedAlertDialog` rationale, `SnackbarWithAction` collector with `OPEN_APP_SETTINGS` deep-link intent, `ChatInputBar` prop wiring, `stopDictation` teardown

## Decisions Made

- Mic teardown reuses the existing `DisposableEffect(Unit)` block (extended with `stopDictation()` beside `unloadLocalModels()`) instead of adding a second teardown block — one screen-leave cleanup site.
- Denial collector resolves the `SnackbarAction` enum with an explicit `when` (currently only `OPEN_APP_SETTINGS`) so future actions extend without touching the intent code.
- `onMicClick` and the permission check live inside the click lambda, never on the composition hot path — the input bar never janks from permission I/O.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. Both plan grep gates passed on first run (Task 1: 11 hits on mic params, `10.dp` count unchanged at the pre-phase baseline of 5; Task 2: 10 hits on launcher/rationale/denial, 4 hits on deep-link/teardown). `./gradlew :app:assembleDebug` succeeds.

## Known Stubs

None. No placeholder copy, no unwired surfaces: the 65-01 denial event now has its emitter (the permission launcher) and its action wiring (the Settings deep-link); the mic button is fully fed from ViewModel state.

## Threat Flags

None beyond the plan's threat register: T-65-04 mitigated (rationale uses the exact Play-disclosure body copy and confirm is the sole trigger of the system request), T-65-05 mitigated (intent fixed to `ACTION_APPLICATION_DETAILS_SETTINGS` with the app package URI only), T-65-06 mitigated (`speechAvailable && !isGenerating` gate), T-65-SC accepted (no package installs).

## Backstop Compliance

- `backstop:max-new-spacing-tokens: 0` — holds: new spacer reuses 8dp; `10.dp` count unchanged at baseline 5 (verified via `git stash` comparison).
- `backstop:new-composables: 0` — holds: zero new `@Composable` functions (verified via diff grep); rationale reuses `WarpedAlertDialog`, mic is an inline `IconButton`.
- `backstop:new-type-roles: 0` — holds: rationale body reuses `bodyMedium`; all touched text on surveyed Material 3 roles.
- Only the two plan-listed files touched (verified via `git diff --stat` on `app/src/main/java/`).

## Next Phase Readiness

- Phase 65 complete: foundation (65-01) + UI wiring (65-02) deliver all three success criteria (VOICE-01, VOICE-02, VOICE-03).
- The dead `modelHasAudio`/`onAudioRecorded`/`onAudioRecordingChanged` audio-message path remains untouched per UI-SPEC non-goals — a future audio-messages phase must decide whether to remove or repurpose it.
- Device/emulator exercise (first-tap rationale, grant, deny-twice Settings Snackbar, no-recognizer hidden-mic) is the remaining manual verification per the plan.

## Self-Check: PASSED

- Modified files exist: `ChatInputBar.kt` FOUND, `ChatScreen.kt` FOUND
- Commits exist: 3755342e, 3ddcc912 both FOUND in `git log`
- No tracked-file deletions in either task commit
- Backstops hold: 0 new composables, 0 new spacing tokens, 0 new type roles

---
*Phase: 65-voice-dictation*
*Completed: 2026-10-02*
