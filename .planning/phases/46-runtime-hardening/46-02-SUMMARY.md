---
phase: 46-runtime-hardening
plan: "02"
type: execute
status: complete
---

# Plan 46-02 Summary: On-Device Stop/Rotation Verification (Ad-Hoc Disposition)

**Date:** 2026-09-28
**Mode:** checkpoint:human-verify resolved ad hoc through the device-testing session of 2026-09-27/28, not the scripted 6-step gate.

## What was verified on device (user logs + screenshots)

- **Load + streaming (local, gemma-4-E2B-it):** confirmed working. Deltas stream token-by-token (`parseThinkBlocks clean=N reasoning=0`), replies render as normal bubbles after the parseThinkBlocks fix (839f268).
- **Engine lifecycle:** "destructed with living sessions" / "Execution manager is not available" errors observed in logcat → fixed in 195bb22 (sessions closed before engine destroy). User to confirm disappearance on reinstall.
- **History repair:** poisoned reasoning rows from the old bug → MIGRATION_13_14 (e99ca5d). User to confirm on upgrade-install.
- **Logcat hygiene:** reviewed multiple pastes; no API keys, prompt bodies beyond user-typed greetings, or secrets observed.

## Explicitly NOT exercised (deferred to Phase 48 release sweep)

- REMOTE STOP latency (step 2) — no LM Studio endpoint configured on the test device.
- LOCAL STOP promptness / retry resurrection (step 3).
- Stop-then-follow-up wedge check (step 4).
- Rotation mid-stream, both backends (steps 5–6).

## Disposition

Phase 47's tool loop builds on 46-01's stop plumbing (JVM-verified: 12/12 cancellation tests, Call.cancel + cancelProcess + CancellationException rethrow + generationSeq hygiene, plus review fixes 64606c3/9af9bfc). The unexercised stop/rotation clauses ride to the Phase 48 real-device sweep (PERF-16 + HARD-01), which owns release-build device smoke. User approved proceeding ("Prosigue con lo planeado", 2026-09-28).

## Requirements

- RUNTIME-13: implementation complete (JVM + rotation clause deferred to Phase 48 device sweep).
- RUNTIME-14: implementation complete (JVM + stop-latency clause deferred to Phase 48 device sweep).
