---
phase: 69-voice-differentiation-gating-transcript
plan: 02
subsystem: ui
tags: [kotlin, voice-transcript, stt, speech-recognizer, room, compose, captions]

# Dependency graph
requires:
  - phase: 69-voice-differentiation-gating-transcript plan 01
    provides: VoiceSendGate flow, gated send blocks, mutual-exclusion locks, STT extension points
  - phase: 68-voice-draft-playback-history
    provides: Room transcript column (17-18), holder stamp pattern, VoicePlayerRow chrome
provides:
  - Parallel transcript STT session during recording (second VM-owned VoiceDictationManager)
  - Send-time transcript stamping into the Room transcript column
  - Own-bubble transcript captions (2-line + expand) with duration-only fallback
affects: [69-03 coachmark/help, release-UAT caption rendering]

# Tech tracking
tech-stack:
  added: []
  patterns: [second VM-owned platform wrapper with override seam, freeze-on-stop holder, latch-overflow expand affordance]

key-files:
  created:
    - app/src/test/java/com/warped/ui/chat/VoiceTranscriptTest.kt
  modified:
    - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
    - app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt

key-decisions:
  - "Transcript callbacks are internal funs driven directly in tests (same functions the recognizer invokes); session start/stop verified against a MockK manager"
  - "Spin-up stop that never ran STT freezes a null holder (buffer reset at session entry); cancel/delete clears holder + buffer"

patterns-established:
  - "Parallel platform sessions stay strictly sequential (stop-before-start both directions); any start failure degrades to unavailable, never a crash"
  - "Caption slots underneath the player row without re-layout (Phase 68 forward-compat lock holds)"

requirements-completed: [VMSG-07]

# Metrics
duration: 50min
completed: 2026-10-02
---

# Phase 69 Plan 02: Parallel Transcript Capture + Captions Summary

**Second on-device STT session during recording with send-time stamping into the Room transcript column, and own-bubble captions (2-line cap + Show more/Less, duration-only fallback when STT is unavailable)**

## Performance

- **Duration:** ~50 min
- **Started:** 2026-10-02T15:45:00Z
- **Completed:** 2026-10-02T16:35:00Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments

- `ChatViewModel` owns a second `VoiceDictationManager` (`transcriptManager` + override seam, same holder discipline): partial replaces hypothesis, final appends-if-new (trimmed, space-joined, duplicate-suffix skipped), error sets sticky unavailable — callbacks touch only the buffer, never `inputText`, never UI events
- Strictly sequential lifecycle: transcript STT starts after `stopDictation()` once the recorder accepts (best-effort, never blocks voice-send); every recording end (manual / 60 s auto / background / spin-up KEEP) stops the session and freezes `lastSentVoiceTranscript` (trimmed, null when blank/unavailable); cancel/delete/spin-up-DISCARD clears; `startDictation` stops the transcript session first; `onCleared` destroys it
- `sendMessage` stamps the holder onto voice turns into the existing Room transcript column (no migration, no mapper change — hydrated on history load for free); text/image sends leave null
- `MessageBubble` renders `VoiceTranscriptCaption` underneath the player row: Label 14sp `onSurfaceVariant`, 2-line cap + ellipsis, primary-tinted expand affordance shown only when overflowing (latched), 48 dp touch floor, duration-only fallback (`%1$d:%2$02d voice message`) in identical styling; missing-file rows caption-free, assistant bubbles untouched; EN+ES parity on 3 new strings
- `VoiceTranscriptTest` (6 tests) green; `VoiceHistoryPlaybackTest` (12) green; full suite 1015/1015 green; `assembleDebug` green; zero new hardcoded user copy in added lines; no Timber call carries transcript text (T-69-04); zero new Gradle deps

## Task Commits

Commits are atomic per plan (single plan commit `227fea76`):

1. **Task 1: Parallel transcript STT session + send-time stamping** — part of plan commit (feat)
2. **Task 2: Bubble transcript caption + fallback + strings** — part of plan commit (feat)

**Plan metadata:** summary included in the same atomic commit (no separate docs commit — `--no-transition` execution).

## Files Created/Modified

- `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` — transcript manager holder/seam, buffer + callbacks, freeze/clear helpers, lifecycle wiring (start/stop/cancel/delete/dictation/onCleared), send-time stamping
- `app/src/test/java/com/warped/ui/chat/VoiceTranscriptTest.kt` — 6 tests (accumulation + dedup, error→null, stop-freeze, cancel-clear, stamp voice/text, dictation-mutex)
- `app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt` — `transcript` param + `VoiceTranscriptCaption` composable
- `app/src/main/res/values/strings.xml`, `values-es/strings.xml` — `voice_msg_show_more`, `voice_msg_show_less`, `voice_msg_transcript_fallback`

## Decisions Made

- Transcript STT callbacks are `internal` (not private) so unit tests drive the exact functions the platform invokes; manager start/stop is verified with MockK — no Robolectric, no new seams beyond the established override pattern
- A new recording resets the live buffer at session entry (synchronously) but leaves the holder to the previous draft until this session freezes/clears — a spin-up stop that never ran STT freezes null (honest), while a failed recorder start keeps the previous draft + holder intact
- `transcript` param on `MessageBubble` defaults null with `?: message.transcript` fallback, so the existing ChatScreen call site (history hydration) works unchanged and previews can override

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- Full-suite turbine 3 s timeouts flaked twice under load (different test each run: `VoiceHistoryPlaybackTest.sendVoiceMessage/...transcode failure`, then `VoiceDraftGuardTest.auto-stop path...same choke`); both pass in isolation and the full suite is green on re-run (1015/1015). Same family as the documented Phase 68 `GroundingPromptTest` flake (real-IO threads + fixed turbine timeouts). No code change required; noted for release-UAT awareness.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plan 03 (coachmark + Help) builds on the Plan 01 `TODO(69-03)` placeholder; transcript work is independent and complete
- Manual release-UAT (deferred per house precedent for hardware-dependent checks): record with speech → caption + expand/collapse; airplane mode / denied STT → duration-only fallback with working send; restart → captions persist; rotation collapses expanded caption without clipping; parallel-STT accuracy on device

## Self-Check: PASSED

- VoiceTranscriptTest.kt exists; transcript manager/buffer/holder/freeze code verified by grep in ChatViewModel
- VoiceTranscriptCaption + transcript param verified in MessageBubble; EN+ES string keys present in both locales
- Targeted tests (VoiceTranscriptTest + VoiceHistoryPlaybackTest) green; full suite 1015 green; assembleDebug green

---
*Phase: 69-voice-differentiation-gating-transcript plan 02*
*Completed: 2026-10-02*
