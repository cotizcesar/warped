---
phase: 69-voice-differentiation-gating-transcript
verified: 2026-10-02T16:50:00Z
status: passed
score: 9/9 must-haves verified
overrides_applied: 0
re_verification: false
---

# Phase 69 Verification Report

**Date:** 2026-10-02
**Scope:** Voice Differentiation + Gating + Transcript (VMSG-03 mutual-exclusion/coachmark, VMSG-04 text-only gating, VMSG-08 remote gating, VMSG-07 transcript captions)
**Status:** PASSED with deferred hardware-dependent items (human_needed) — see §5 and `deferred-items.md`.

## 1. Unit Tests (automated)

| Command | Result |
|---------|--------|
| `:app:testDebugUnitTest --tests "com.warped.ui.chat.voice.VoiceSendGateTest"` | BUILD SUCCESSFUL — 7 tests (local/audio, local/no-audio, null-capability fail-open, null-provider fail-open, each remote, legacy LOCAL, remote-wins) |
| `:app:testDebugUnitTest --tests "com.warped.ui.chat.VoiceGatingTest"` | BUILD SUCCESSFUL — 6 tests (live flip Allowed/GatedTextOnly/GatedRemote/Allowed, gated voice block + holder preservation, remote reason, text-send bypass + holder keep, exclusion both directions) |
| `:app:testDebugUnitTest --tests "com.warped.ui.chat.VoiceTranscriptTest"` | BUILD SUCCESSFUL — 6 tests (partial-replace/final-append/dedup/blank-skip, error→null holder with recording intact, stop-freeze, cancel-clear, stamp voice/text, dictation-mutex) |
| `:app:testDebugUnitTest --tests "com.warped.ui.chat.VoiceCoachmarkTest"` | BUILD SUCCESSFUL — 4 tests (unseen+Allowed shows, seen never re-shows + persist verified, gated hides unseen, persist-failure re-shows without crash) |
| `:app:testDebugUnitTest --tests "com.warped.ui.chat.VoiceHistoryPlaybackTest"` | BUILD SUCCESSFUL — 12 tests (regression: holders, row stamping, preempt) |
| `:app:testDebugUnitTest --tests "com.warped.ui.chat.VoiceMessageGuardTest"` | BUILD SUCCESSFUL — 3 tests (legacy gate contract updated to gate-block Snackbar) |
| `:app:testDebugUnitTest` (FULL suite) | BUILD SUCCESSFUL — **1019 tests, 0 failures, 0 errors, 107 classes** |

Test-result XML tallied from `app/build/test-results/testDebugUnitTest/*.xml` post-gate. Targeted classes re-verified green on clean re-run (18/18 for 69-02 pair).

## 2. Build (automated)

| Command | Result |
|---------|--------|
| `:app:assembleDebug` (after 69-01) | BUILD SUCCESSFUL |
| `:app:assembleDebug` (after 69-02) | BUILD SUCCESSFUL |
| `:app:assembleDebug --rerun-tasks` (after 69-03, clean-room) | BUILD SUCCESSFUL (42/42 executed) |

Zero new Gradle dependencies (no `*.toml`, `build.gradle.kts`, or manifest change; STT reuses the platform `SpeechRecognizer` via the existing `VoiceDictationManager`; tooltip is Compose BOM M3). Kotlin only. Gate evaluation is pure compute on a combine transform; DataStore writes and STT callbacks stay off-Main (callbacks touch only StateFlows).

## 3. Static Checks (automated)

| Check | Result |
|-------|--------|
| `grep -rn "Voice messages need an on-device\|Needs audio model\|Device-only for now" app/src/main/java/` | 0 (no hardcoded gate copy; pre-existing `Migrations.kt` comment untouched) |
| `grep -rn "Show more\|Show less\|voice message" new Plan-02 lines` (`git diff -U0` added-line scan) | 0 (KDoc reworded to expand/collapse language) |
| `grep -rn "Voice message\|Muestra\|Learn more\|Más información" app/src/main/java/` | 0 (no hardcoded coachmark/Help copy; comments reworded) |
| `voice_msg_*` name parity `values/` vs `values-es/` | 30 == 30 (6 gate + 3 caption + 1 coachmark added this phase) |
| `help_s9_*` name parity `values/` vs `values-es/` | 5 == 5 (title + 4 steps) |
| Gate wiring (`voiceSendGate` in ChatViewModel + ChatScreen + ChatInputBar) | present; dead `modelHasAudio` param replaced (no parallel source of truth) |
| Help navigation (`onNavigateToHelp` args in NavGraph.kt) | 3 (Chat, NewChat, ChatDetail) |
| `voice_msg_remote_blocked` retired | 0 references in `*.kt`/`*.xml` (only caller was the replaced guard) |
| Threat surface (`Timber` carrying transcript buffer; STT output execution paths) | 0 — transcript rendered as plain `Text`, never logged, never interpreted as HTML/Markdown |
| No Android imports in `VoiceSendGate.kt` | verified by inspection (pure Kotlin + `ProviderType`) |

## 4. Requirement Coverage

| Req | Behavior | Evidence |
|-----|----------|----------|
| VMSG-04 | Text-only local model → disabled-with-reason voice button (38% opacity + `Needs audio model` hint), tappable explainer with `View models` → catalog; gate flips live on switch; draft kept, send blocked with reason | `VoiceSendGate` (GatedTextOnly) + `voiceSendGate` flow + `ChatInputBar` gated branch + `showVoiceGateExplainer` (catalog action) + send blocks in `sendMessage`/`sendVoiceMessage`; gate tests (flip, block, holder preservation) |
| VMSG-08 | Remote endpoint → same pattern with `Device-only for now` + one-line reason + `Learn more` → Help voice section; provider-keyed helper (no hardcoded local-only checks at call sites) | `GateState.GatedRemote` (remote-wins) + explainer `onNavigateToHelp` + `NavGraph` 3-site wiring + `HelpScreen` Section 9; gate tests (each remote provider, remote-wins) |
| VMSG-07 | Parallel on-device STT during recording → transcript caption under own voice bubbles (2-line + expand), duration-only fallback when STT unavailable, Room persistence across restarts | Second VM-owned `VoiceDictationManager` + buffer/freeze/holder discipline + `sendMessage` stamping into the existing 17–18 column (no migration) + `VoiceTranscriptCaption` (latch-overflow affordance, fallback, caption-free missing-file rows); transcript tests (accumulation, error→null, freeze, clear, stamp) |
| VMSG-03 | Voice-send (GraphicEq) vs dictation (Mic) distinct with mutual exclusion; first-use `Voice message` coachmark, one-shot, never on gated buttons; Help explains the difference | Both-directions stops locked by test + distinct TalkBack preserved + `VoicePreferences.voice_coachmark_seen` + `showVoiceCoachmark` (unseen AND Allowed) + M3 `PlainTooltip` with any-tap dismissal + Help s9 (voice-vs-dictation step); coachmark tests (show/hide/persist/failure) |

Must-have truths (9 across the three plans): all verified — disabled-with-reason on both gates with catalog/Help paths (never dead, never hidden); mutual exclusion with active-mode tint and distinct TalkBack; draft kept with blocked-send reasons on model/endpoint switch; transcript captions from parallel STT with honest duration-only fallback and Room survival; one-shot coachmark distinct from dictation with Help landing.

## 5. Gaps / Human-Needed

1. **Hardware-dependent checks — HUMAN_NEEDED / DEFERRED** (house precedent: Phases 67/68 defer device checks the same way). Recorded in `deferred-items.md` with a release-UAT runbook: parallel-STT accuracy on a real recognizer, coachmark visuals/anchoring, gate explainer visuals, caption rendering/expand behavior, and the manual end-to-end passes (gated button → explainer → catalog/Help; draft-kept-across-switch; airplane-mode fallback; restart persistence; rotation collapse).
2. **Full-suite turbine 3 s timeouts flaked three times under load during this phase** (different test each run; all pass in isolation and the suite is green on re-run, 1019/1019). Same family as the documented Phase 68 `GroundingPromptTest` flake (real-IO threads + fixed turbine timeouts). No code change required; noted for awareness, not a gap.

## Self-Check: PASSED

- All three plan SUMMARY.md files exist with substantive content and correct commit hashes
- Commits `3a2bdcf2` (69-01), `1b1db167` (69-02), `fb834919` (69-03) verified in `git log`
- 1019/1019 unit tests green; clean-room assemble green; string parity confirmed both locales
