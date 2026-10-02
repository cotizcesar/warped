# Phase 69 Deferred Items

## Release-UAT / Device-Smoke (Phase 69 verification)

**Status:** DEFERRED — no mic-capable hardware available in this execution environment (house precedent: Phases 67/68 defer device checks the same way).

**What requires a physical device:**

Gating + differentiation (VMSG-03/04/08):
1. Select a text-only local model → voice button renders disabled-with-reason (reduced opacity + `Needs audio model` hint, still visible); tap → explainer Snackbar + `View models` → lands on the model catalog.
2. Switch to a remote endpoint → button shows the remote reason (`Device-only for now`); tap → one-line reason + `Learn more` → lands on Help Section 9 (voice).
3. Switch models/endpoints back and forth → the button flips live with no screen reload; an in-progress draft survives every switch, and send on a gated config shows the reason (draft kept, never silently dropped, never attempted).
4. Record while dictating and dictate while recording → last-tap-wins both directions; the active button wears the primary tint; TalkBack announces `Record voice message` vs `Dictate text` distinctly.
5. Coachmark visuals: fresh install → one-shot `Voice message` tooltip anchored to the enabled voice button; tap mic / voice / outside → gone forever (kill + restart → absent); gated config → no coachmark ever.

Transcript captions (VMSG-07):
6. Record 5–10 s of clear speech → send → own bubble shows the transcript caption under the player row; a long transcript caps at 2 lines with a working `Show more` / `Show less` affordance (no clipping of the player row, no layout push).
7. Record in airplane mode (or with STT disabled) → bubble shows the duration-only fallback (`0:1x voice message`), send still works; restart the app → captions persist (Room transcript column).
8. Parallel-STT accuracy on a real recognizer (Spanish + English device locales): partials don't duplicate, finals append once, error mid-session degrades to the fallback without affecting recording or send.
9. Rotation with an expanded caption → collapses without clipping (accepted per UI-SPEC).
10. Received/assistant bubbles show no caption row; missing-file rows show the unavailable row with no caption.

**What WAS verified without hardware (evidence in VERIFICATION.md):**
- Gate helper: 7 JVM unit tests green (all ProviderType values, fail-open rules, remote-wins).
- VM gate flow: 6 JVM tests green (live flip across model/endpoint switches, both send blocks with holder/draft preservation, text bypass, exclusion both directions).
- Transcript session: 6 JVM tests green (accumulation + dedup, error→null with recording intact, stop-freeze, cancel-clear, voice/text stamping, dictation mutex).
- Coachmark flag: 4 JVM tests green (show/hide/persist/failure discipline).
- Regression: VoiceHistoryPlaybackTest (12) + VoiceMessageGuardTest (3, updated to the gate-block contract) green.
- Full unit suite: 1019 tests, 0 failures (`:app:testDebugUnitTest`, 2026-10-02).
- Debug build assembles clean-room (`:app:assembleDebug --rerun-tasks`, 42/42 executed).
- Zero hardcoded gate/coachmark/Help copy in main sources; `voice_msg_*` 30==30 and `help_s9_*` 5==5 EN+ES parity.
- Tooltip API verified against the resolved M3 1.4.0 sources (no training-knowledge guessing).

**Suggested release-UAT runbook:** install the debug APK on a mic-capable device with (a) an audio-capable allowlist model and (b) a text-only model downloaded, plus one remote endpoint configured; grant RECORD_AUDIO; (1) text-only model → check hint + explainer → `View models` → catalog → switch to the audio model → button live, no reload; (2) start a recording, switch model mid-draft → draft kept → send blocked with reason; (3) remote endpoint → `Learn more` → Help §9, back-stack returns to chat; (4) fresh install → coachmark → dismiss via mic tap → force-stop → relaunch → absent; (5) record speech → caption + expand/collapse; (6) airplane-mode record → fallback caption, send works → restart → captions persist; (7) Spanish-locale device → transcript follows device locale; (8) rotate with expanded caption → collapsed, no clipping.

## Full-suite flake watch (informational, not a Phase 69 gap)

- Turbine 3 s timeouts flaked three times under full-suite load during this phase (`VoiceHistoryPlaybackTest.sendVoiceMessage/...transcode failure`, `VoiceDraftGuardTest.auto-stop path...same choke`, plus the documented pre-existing `GroundingPromptTest.detector throw` flake). Every case passes in isolation and the full suite is green on re-run (1019/1019). Root-cause class: real-IO threads (recorder/transcode/MediaPlayer paths) + fixed turbine timeouts + JVM-global singletons — same family as the Phase 68 deferred flake note. No action required; consider raising turbine timeouts or sharding IO-heavy voice tests in a future hardening pass.
