# Phase 68 Deferred Items

## Release-UAT / Device-Smoke (Phase 68 verification)

**Status:** acknowledged

**What requires a physical device:**

Draft preview (VMSG-02):

1. Record 3 s → draft card appears with total duration → play/pause/progress behave → send transmits voice + caption as one bubble.
2. Record <1 s → "Recording too short" Snackbar, no card, file deleted (manual stop AND 60 s auto-stop AND background auto-stop paths).
3. Draft delete (trash icon) removes the file immediately with no dialog.
4. Audio focus: incoming call / other-app audio pauses draft playback; focus never held past the screen.
5. Rotation with a kept draft: draft survives, playback resets to 0 paused, one tap resumes.
6. Backgrounding mid-playback pauses and keeps position; chat exit stops playback.

History playback (VMSG-06):

7. Send voice → bubble renders play + duration + progress → kill + relaunch app → bubble still plays (Room path + duration survive restart).
8. Play draft then play history (and history A then history B) → first clip stops (single-player on hardware MediaPlayer).
9. Delete the audio file via device explorer → bubble renders the "clip unavailable" row (no crash, no silent drop).
10. Delete a voice message → its clip file is gone from filesDir/voice.
11. Long caption beside a voice row wraps without clipping (held-out visual check from UI-SPEC).
12. Audibility + progress smoothness on real MediaPlayer (emulator/JVM fakes assert state only).

**What WAS verified without hardware (evidence in VERIFICATION.md):**

- Player state machine: 13 JVM unit tests green (single-player preempt, restart, idle-pause no-op, stop-clears, play-failure, double-destroy, completion hook, focus-loss pauses, zero-when-idle, currentPath tracking).
- Draft guard: 7 JVM tests green (sub-1 s reject on manual + auto-stop with file delete + Snackbar, long-clip keep with duration, delete-clears, play-pause-keeps-position, completion-resets, play-without-clip no-op).
- History playback: 11 JVM tests green (play sets id + duration, single-player stop, draft-preempt, missing-file grace, blank-path no-op, completion-clear, pause-keeps, send-time holders, row stamping, text-send clean, delete-removes-file).
- Migration: 8 JVM static tests green (17→18 delta, SQL exactness, registration, round-trips, legacy defaults, blank-path degrade).
- Full unit suite: 995 tests, 0 failures (`:app:testDebugUnitTest`, 2026-10-02).
- Debug build assembles (`:app:assembleDebug` BUILD SUCCESSFUL).
- Zero hardcoded draft/history copy in main sources; 21 voice_msg_* strings EN+ES parity.
- Player is coroutine-free (only "scope" match is the `no scopes inside` contract comment).

**Suggested release-UAT runbook:** install debug APK on a mic-capable device with an audio-capable allowlist model downloaded; grant RECORD_AUDIO; (a) record 3 s → play/pause draft → send with caption → confirm bubble + response; (b) record <1 s → confirm Snackbar + no card; (c) delete draft → confirm file gone; (d) send voice → force-stop app → relaunch → play bubble from history; (e) play draft, then play the history bubble → confirm preempt; (f) delete a voice message → confirm file gone via device explorer; (g) background mid-playback → confirm pause-keeps-position; rotate mid-playback → confirm paused-kept-position, one-tap resume (changed by review-fix WR-06 — was paused-at-0); (h) incoming call during playback → confirm pause.

## Review-fix follow-up (WR-05 remainder, 2026-10-02)

**Status:** OPEN — not implemented; needs a phase decision.

**Orphan-clip vacuum:** delete cleanup now resolves the path from the Room row (source of truth) with transcript fallback, confined to `filesDir/voice`, but there is still no startup reaper: clips whose rows vanished through older builds (or a failed file delete after a successful DB delete) accumulate silently. Options: (a) startup vacuum deleting `filesDir/voice/*` files referenced by no messages row; (b) periodic WorkManager sweep; (c) accept the leak (clips are small, user-deletable via app storage clear). Recommend (a) in the next voice-touching phase — trivial Room query (`SELECT audio_path FROM messages`) + file listing on IO at startup.

## Pre-existing flake (informational, not a Phase 68 gap)

- `GroundingPromptTest.detector throw falls back to the regex` fails intermittently in full-suite runs (expected null, got true) but passes in isolation (32/32) and on full-suite re-runs. Root cause class: JVM-global `LanguageDetectorHolder` singleton + background warm thread — a timing/order race, same family as the Phase 67 `ModelSwitchUnloadTest` flake. Proven unrelated to Phase 68 voice UI edits via stash A/B (baseline green, with-fix green on re-run; failures do not correlate with code content). No action required.
  status: acknowledged
