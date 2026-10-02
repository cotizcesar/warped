---
phase: 69-voice-differentiation-gating-transcript
plan: 01
subsystem: ui
tags: [kotlin, compose, voice-send, gating, mutual-exclusion, snackbar]

# Dependency graph
requires:
  - phase: 68-voice-draft-playback-history
    provides: Voice-send recorder/draft/bubble chrome, Room transcript placeholder column, guard toasts
  - phase: 67-voice-recording
    provides: Voice-send toggle, 60s cap, GraphicEq/Mic buttons, permission flow
provides:
  - Pure provider-keyed VoiceSendGate helper (Allowed/GatedTextOnly/GatedRemote)
  - VM voiceSendGate StateFlow + gated voice-send block (draft kept, reason Snackbar)
  - Disabled-with-reason input-row surface + explainer (catalog link, Learn-more placeholder)
  - Mutual-exclusion locks both directions + Plan-02 transcript-STT extension points
affects: [69-02 transcript capture, 69-03 coachmark/help, future remote voice-send unlock]

# Tech tracking
tech-stack:
  added: []
  patterns: [derived gate StateFlow off-composition, gate-reason Snackbar events, repurposed-dead-param]

key-files:
  created:
    - app/src/main/java/com/warped/ui/chat/voice/VoiceSendGate.kt
    - app/src/test/java/com/warped/ui/chat/voice/VoiceSendGateTest.kt
    - app/src/test/java/com/warped/ui/chat/VoiceGatingTest.kt
  modified:
    - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
    - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
    - app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt
    - app/src/test/java/com/warped/ui/chat/VoiceMessageGuardTest.kt

key-decisions:
  - "sendVoiceMessage carries its own pre-clear gate guard so a kept draft survives a model switch (sendMessage block alone would strand holders without the card)"
  - "Legacy error_no_audio transcript-error contract replaced by gate-reason Snackbar; model-side backstop retained for the fail-open path"

patterns-established:
  - "All local-only branching lives in VoiceSendGate.evaluate; call sites pass providerType + capability only"
  - "Gate param replaces dead capability flags (modelHasAudio) rather than adding parallel sources of truth"

requirements-completed: [VMSG-03, VMSG-04, VMSG-08]

# Metrics
duration: 45min
completed: 2026-10-02
---

# Phase 69 Plan 01: Voice-Send Gating Tracer Summary

**Provider-keyed VoiceSendGate helper, live-flipping VM gate flow with draft-kept send block, and disabled-with-reason voice button (38% opacity + hint + tappable explainer) with catalog / Learn-more actions**

## Performance

- **Duration:** ~45 min
- **Started:** 2026-10-02T15:00:00Z
- **Completed:** 2026-10-02T15:45:00Z
- **Tasks:** 3
- **Files modified:** 9

## Accomplishments

- Pure-Kotlin `VoiceSendGate.evaluate(providerType, localAudioCapable)` with fail-open semantics (null provider / null capability allow) and remote-wins ordering, branch-covered by `VoiceSendGateTest` (7 tests)
- `ChatViewModel.voiceSendGate` derived `StateFlow<GateState>` flipping live on model/endpoint switch; gated voice turns blocked with reason Snackbar before holder consumption; `sendVoiceMessage` guarded pre-clear so kept drafts survive; mutual exclusion locked both directions by test
- Input-row gated surface: GraphicEq at 38% onSurface + inline hint (`Needs audio model` / `Device-only for now`) + tappable explainer Snackbar (`View models` → catalog, `Learn more` → Plan-03 Help placeholder TODO(69-03)); dead `modelHasAudio` param replaced by `voiceGate`; `voice_msg_remote_blocked` retired EN+ES; EN+ES parity on 6 new strings
- Full unit suite green, `:app:assembleDebug` green, hardcoded-copy grep 0, zero new Gradle deps

## Task Commits

Commits are atomic per plan (single plan commit):

1. **Task 1: VoiceSendGate pure helper + unit tests** — part of `0d3f68a0` (feat)
2. **Task 2: VM gate flow + draft-kept send block + exclusion hardening** — part of `0d3f68a0` (feat)
3. **Task 3: Input-row gated surface + explainer + strings** — part of `0d3f68a0` (feat)

**Plan metadata:** summary included in the same atomic commit (no separate docs commit — `--no-transition` execution).

## Files Created/Modified

- `app/src/main/java/com/warped/ui/chat/voice/VoiceSendGate.kt` — pure gate helper (Allowed / GatedTextOnly / GatedRemote)
- `app/src/test/java/com/warped/ui/chat/voice/VoiceSendGateTest.kt` — 7 branch-complete tests (JVM, no Robolectric)
- `app/src/test/java/com/warped/ui/chat/VoiceGatingTest.kt` — 6 VM tests (live flip, both send blocks, text bypass, exclusion both directions)
- `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` — voiceSendGate flow, sendMessage + sendVoiceMessage gate blocks, Plan-02 STT extension-point comments
- `app/src/main/java/com/warped/ui/chat/ChatScreen.kt` — gate-driven onVoiceClick, explainer, gate-aware onSendMessage, call-site wiring
- `app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt` — voiceGate param + gated rendering
- `app/src/test/java/com/warped/ui/chat/VoiceMessageGuardTest.kt` — legacy gate test updated to the gate-block contract (deviation)
- `app/src/main/res/values/strings.xml`, `values-es/strings.xml` — 6 new gate strings, retired `voice_msg_remote_blocked`

## Decisions Made

- `sendVoiceMessage` needs its own gate guard before draft-state clearing: the `sendMessage` block alone preserves holders but the card would already be gone. Both blocks emit the same reason event.
- The in-coroutine `error_no_audio` model-side backstop stays as defense-in-depth for the fail-open path (unknown capabilities → Allowed → model rejects); only the ChatScreen guard toasts were retired.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Legacy guard test asserted the superseded error contract**
- **Found during:** Task 2 verification (full suite)
- **Issue:** `VoiceMessageGuardTest.text-only model plus audioBytes` expected `ChatError.Unknown(error_no_audio)`; the new gate block returns before the turn starts, so no transcript error is set (by design — blocked with reason Snackbar instead)
- **Fix:** Updated the test to expect the gate-reason `ChatEvent.Snackbar`, empty messages, null error, never generating
- **Files modified:** `app/src/test/java/com/warped/ui/chat/VoiceMessageGuardTest.kt`
- **Verification:** Full `:app:testDebugUnitTest` suite green
- **Committed in:** plan commit (part of atomic plan commit)

**2. [Rule 1 - Bug] Exclusion test leaked a live recording into @TempDir cleanup**
- **Found during:** Task 2 verification (`VoiceGatingTest.startVoiceRecording with dictation live`)
- **Issue:** The test left a live recording (IO ticker + sampler) running; the real-IO `mkdirs` raced JUnit temp-dir deletion → `DirectoryNotEmptyException` teardown failure
- **Fix:** Turbine-park until the IO side accepts, then `cancelVoiceRecording()` teardown at test end (both exclusion tests)
- **Files modified:** `app/src/test/java/com/warped/ui/chat/VoiceGatingTest.kt`
- **Verification:** `VoiceGatingTest` green in isolation and in full suite
- **Committed in:** plan commit (part of atomic plan commit)

---

**Total deviations:** 2 auto-fixed (both Rule 1 test-correctness)
**Impact on plan:** Both required for a green suite under the new gate contract. No scope creep, no production-behavior change beyond plan intent.

## Issues Encountered

None beyond the deviations above.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plan 02 (transcript capture) can build on the `startVoiceRecording` / `startDictation` extension-point comments; `lastSentVoiceTranscript` holder pattern mirrors `lastSentVoicePath`
- Plan 03 replaces the `TODO(69-03)` Learn-more placeholder with Help navigation
- Manual release-UAT: text-only model → gated button + hint → explainer → catalog; remote → remote reason; switch back → live with no reload; draft kept across switch, send blocked with reason

## Self-Check: PASSED

- VoiceSendGate.kt, VoiceSendGateTest.kt, VoiceGatingTest.kt exist on disk
- ChatViewModel voiceSendGate + ChatScreen explainer + ChatInputBar voiceGate verified by grep
- Full unit suite BUILD SUCCESSFUL, assembleDebug BUILD SUCCESSFUL

---
*Phase: 69-voice-differentiation-gating-transcript plan 01*
*Completed: 2026-10-02*
