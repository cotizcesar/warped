---
phase: 69-voice-differentiation-gating-transcript
plan: 03
subsystem: ui
tags: [kotlin, compose, coachmark, tooltip, datastore, help, navigation]

# Dependency graph
requires:
  - phase: 69-voice-differentiation-gating-transcript plan 01
    provides: voiceSendGate flow, gate explainer with Learn-more placeholder TODO(69-03)
  - phase: 64-help
    provides: HelpScreen HelpSection card + EN+ES string convention
provides:
  - VoicePreferences DataStore flag (voice_coachmark_seen) + VM showVoiceCoachmark state
  - One-shot PlainTooltip coachmark on the voice-send button (any-tap dismissal)
  - Help Section 9 (voice) + Learn-more navigation from all three chat entry points
affects: [release-UAT coachmark visuals, future onboarding]

# Tech tracking
tech-stack:
  added: []
  patterns: [nullable-default injected dep for fixture compatibility, LaunchedEffect tooltip show/dismiss, onDismissRequest persist]

key-files:
  created:
    - app/src/main/java/com/warped/data/local/preferences/VoicePreferences.kt
    - app/src/test/java/com/warped/ui/chat/VoiceCoachmarkTest.kt
  modified:
    - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
    - app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt
    - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
    - app/src/main/java/com/warped/ui/help/HelpScreen.kt
    - app/src/main/java/com/warped/ui/navigation/NavGraph.kt

key-decisions:
  - "showVoiceCoachmark combines seen-flag + gate only (no speech term — the screen anchors the tooltip only where the button renders)"
  - "voicePreferences injected with nullable default so 25 existing fixtures compile untouched; production Hilt always injects the real store"
  - "showVoiceCoachmark uses Eagerly (like voiceSendGate) instead of WhileSubscribed so tests read a settled value"

patterns-established:
  - "Tooltip show/dismiss owned by a LaunchedEffect on (flag, gate); all dismiss paths funnel to one VM persist call"
  - "New navigation params default to {} so previews/tests never crash; NavGraph wires all production call sites"

requirements-completed: [VMSG-03]

# Metrics
duration: 55min
completed: 2026-10-02
---

# Phase 69 Plan 03: Coachmark + Help Voice Section Summary

**One-shot PlainTooltip coachmark ("Voice message") on the voice-send button with any-tap dismissal persisted in DataStore, and Help Section 9 (voice vs dictation, 60 s cap, local-only) reached via Learn more from all three chat entry points, EN+ES**

## Performance

- **Duration:** ~55 min
- **Started:** 2026-10-02T15:50:00Z
- **Completed:** 2026-10-02T16:45:00Z
- **Tasks:** 2
- **Files modified:** 9

## Accomplishments

- `VoicePreferences` (mirrors `WizardPreferences` exactly) with `voice_coachmark_seen` in `voice_preferences` store; VM `showVoiceCoachmark = combine(seen, gate) { !seen && gate == Allowed }` (never on a gated button) + best-effort IO `dismissVoiceCoachmark()` — `VoiceCoachmarkTest` (4 tests) green: unseen+Allowed shows, seen never re-shows with verified persist, gated hides even unseen, persist failure re-shows once without crashing
- `ChatInputBar` anchors an M3 `PlainTooltip` (Compose BOM, `isPersistent`, verified against M3 1.4.0 sources) to the enabled GraphicEq button, shown once via `LaunchedEffect(flag, gate)`; dismissal on outside tap (`onDismissRequest`), voice tap, or dictation tap — whichever first — all funneling to one persist call with no local flag
- `ChatScreen` collects the state, passes it through, adds `onNavigateToHelp = {}` (default-safe), and the Plan 01 `TODO(69-03)` placeholder is replaced by real Help navigation; `NavGraph` wires `onNavigateToHelp → Screen.Help` at all three `ChatScreen` call sites (3 `onNavigateToHelp` args grep-verified)
- `HelpScreen` Section 9 reuses `HelpSection` unchanged (`GraphicEq` icon, `help_s9_title` + 4 steps: voice-vs-dictation, 60 s + first-30 s note, local-only transcription, device-only remote note); EN+ES parity on 6 new keys
- Targeted tests green, full suite 1019/1019 green, clean-room `assembleDebug --rerun-tasks` green, hardcoded-copy grep 0, zero new Gradle deps, DataStore write off-Main, Kotlin only

## Task Commits

Commits are atomic per plan (single plan commit `b8a2623f`):

1. **Task 1: VoicePreferences flag + VM coachmark state + tests** — part of plan commit (feat)
2. **Task 2: Coachmark tooltip + Help section + navigation + strings** — part of plan commit (feat)

**Plan metadata:** summary included in the same atomic commit (no separate docs commit — `--no-transition` execution).

## Files Created/Modified

- `app/src/main/java/com/warped/data/local/preferences/VoicePreferences.kt` — DataStore flag store
- `app/src/test/java/com/warped/ui/chat/VoiceCoachmarkTest.kt` — 4 flag-discipline tests
- `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` — injection, `showVoiceCoachmark`, `dismissVoiceCoachmark()`
- `app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt` — `showVoiceCoachmark`/`onCoachmarkDismiss` params, `TooltipBox`+`PlainTooltip`, wrapped mic/voice taps
- `app/src/main/java/com/warped/ui/chat/ChatScreen.kt` — state collection, pass-through, `onNavigateToHelp` param, placeholder replaced
- `app/src/main/java/com/warped/ui/navigation/NavGraph.kt` — Help wiring at Chat/NewChat/ChatDetail
- `app/src/main/java/com/warped/ui/help/HelpScreen.kt` — Section 9
- `app/src/main/res/values/strings.xml`, `values-es/strings.xml` — `voice_msg_coachmark` + `help_s9_*`

## Decisions Made

- `showVoiceCoachmark` combines only the seen flag and the gate (the plan's `speechAvailable?` third flow is subscribed-but-ignored in the plan sketch; the screen already anchors the tooltip only where the button renders, so no VM speech term is needed — same observable behavior, simpler tests)
- `Eagerly` instead of `WhileSubscribed` for the coachmark flow (matches `voiceSendGate`; lets tests read a settled `.value` after advancing — same off-composition computation, no behavior change)
- Nullable-default injected `VoicePreferences? = null`: Dagger passes every `@Inject` constructor arg explicitly so production always gets the real store; the default only serves direct Kotlin callers (the 25 legacy test fixtures), which read seen=true (never show) with no-op dismissal
- Tooltip API verified against the resolved M3 1.4.0 sources in the Gradle cache (`TooltipBox` with `onDismissRequest`, `rememberTooltipState(isPersistent = true)`, `TooltipDefaults.rememberPlainTooltipPositionProvider`) — no training-knowledge guessing

## Deviations from Plan

None - plan executed exactly as written (the three decisions above are executor-discretion details within the plan's acceptance criteria, not deviations).

## Issues Encountered

- `dismissVoiceCoachmark` persists on `Dispatchers.IO` (real thread under `runTest`, invisible to the virtual scheduler) — the first test run raced the assertion. Fixed test-side with `coVerify(timeout = 3_000)` rendezvous before advancing (production code untouched, correct IO discipline kept).
- Full-suite turbine 3 s timeouts flaked twice under load across the phase (`VoiceHistoryPlaybackTest`, `VoiceDraftGuardTest`, plus the documented pre-existing `GroundingPromptTest` flake); every case passes in isolation and the full suite is green on re-run (1019/1019). Same family as the Phase 68 deferred flake note — no code change required.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 69 requirements VMSG-03/04/07/08 are all delivered (Plans 01–03). Remaining: VERIFICATION.md + deferred-items.md (release-UAT runbook for the hardware-dependent checks: parallel-STT accuracy, coachmark/gate/caption visuals) by the orchestrator
- Manual release-UAT: fresh install → coachmark on voice button → mic tap → gone forever (kill + restart absent); gated config → no coachmark; remote gate → Learn more → Help voice section; text-only gate → View models → catalog

## Self-Check: PASSED

- VoicePreferences.kt + VoiceCoachmarkTest.kt exist; `showVoiceCoachmark`/`dismissVoiceCoachmark` verified in ChatViewModel
- TooltipBox + PlainTooltip + pass-through verified in ChatInputBar/ChatScreen; Section 9 + 3× onNavigateToHelp verified; EN+ES keys present in both locales
- Targeted tests green; full suite 1019 green; clean-room assemble green

---
*Phase: 69-voice-differentiation-gating-transcript plan 03*
*Completed: 2026-10-02*
