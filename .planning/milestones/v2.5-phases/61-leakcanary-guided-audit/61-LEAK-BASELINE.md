# LEAK-BASELINE.md — Triaged Leak Baseline (Phase 61-02)

**Date:** 2026-09-30
**Device:** Physical Pixel 8, USB `37141FDJH0065Y`, debug APK with LeakCanary 2.14
**Model:** `gemma-4-E2B-it` (2 588 147 712 bytes, complete, GPU backend mmap)
**Method:** `LEAK-TOUR.md` 6 legs; LeakCanary active (ServiceWatcher reflection
confirmed in logcat); 60 s background per leg before reading notifications.

## Result table

| Leg | Observed | Heap evidence ref | Triage owner |
|-----|----------|-------------------|--------------|
| 1 load/unload (E2B) | clean | — (no notification in 60 s) | n/a |
| 1B switch to model B | DEFERRED — see below | — | — |
| 2 streaming + Stop ×2 | clean | — | n/a |
| 3 grounding (URLs) + cancel | clean | — | n/a |
| 4 offline → retry | clean | — | n/a |
| 5 OG thumbnail scroll | clean | — | n/a |
| 6 rotation + process death | clean, conversation restores | — | n/a |

**Zero leaks found.** No owner-local fixes required for LEAK-02..05 from this
baseline — Phase 62 scope becomes: regression tests locking the clean paths +
full release hardening (REL-01).

## Notes

- Leg 6 substitution: `am kill` could not kill the app (service in progress);
  used `am force-stop` instead. Restore verified from recents — equivalent for
  retention purposes, recorded here.
- Engine init healthy throughout (`LiteRTLmEngine: initialization complete`,
  `EngineManager: LiteRT-LM engine now active`, GPU backend).

## DEFERRED entries

```
DEFERRED — Leg 1B (model-B switch): only one complete .litertlm on-device
  (E2B); second file present (gemma-3-270m-it, 304 MB partial) cannot load.
  Needs: second complete model download (~3 GB) | Owner: user | Carry to: release-UAT
```

## Product feedback (out of milestone scope, NOT a leak)

User observation during Leg 1: after download, chat stays blocked until a model
is manually activated via Models & Endpoints — models should load automatically
(or offer one-tap activation from chat). Pre-existing behavior (untouched by
Phases 59–61), recorded as a v2.6 candidate, not a Phase 62 fix.
