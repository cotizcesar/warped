---
phase: 60-api36-behavior-audit
verified: 2026-09-30T22:45:00-05:00
status: passed
score: 5/5 must-haves verified
overrides_applied: 0
device_followups:
  - "3-button nav visual pass (gesture-nav verified; 3-button untested)"
  - "Light-theme visual pass (blocked: WarpedTheme(darkTheme=true) hardcoded, no toggle — product work)"
  - "Play Console target-API warnings after uploading 36/36 artifact"
  - "Foldable posture (tablet sw800dp covers adaptive-fill; posture transitions untested)"
  - "Platform-initiated stop under real quota pressure (user-cancel path verified; quota-stop copy is unit-tested)"
---

# Phase 60: API-36 Behavior Audit Verification Report

**Phase Goal:** App runs correctly under Android 16 platform contracts
**Verified:** 2026-09-30
**Status:** passed (5/5, with 5 named release-UAT follow-ups above)
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | App targets Android 16 (compileSdk 36 + targetSdk 36), `assembleRelease` + R8 green, no local target-API warnings | ✓ VERIFIED | `app/build.gradle.kts` 36/36; `:app:assembleRelease` BUILD SUCCESSFUL (AGP 9.3.0, R8 path executed) 2026-09-30; 60-01 verify-only task, no code change needed |
| 2 | Chat pill, bottom sheets, Fuentes list render edge-to-edge with correct insets on gesture nav, dark theme | ✓ VERIFIED | `enableEdgeToEdge()` + per-screen `contentWindowInsets` (commits `8d9e7bd5`); hardware: user confirmed pill/settings/presets correct on Pixel 8 gesture nav; tablet screenshots (`tablet-chat5/6.png`): full-bleed chat, pill above gesture bar, sheet centered no bleed |
| 3 | Back gesture/button dismisses chat, sheets, settings/preset screens; no dead `onBackPressed()` paths | ✓ VERIFIED | `onBackPressed` grep → 0 matches; `BackHandler` in all 6 screen files (commit `d731636c`); hardware: user confirmed dismiss works everywhere (Pixel 8) |
| 4 | Downloads + offline retry survive Android 16 quotas — FGS types declared, stop reasons logged, progress/cancel/retry verified | ✓ VERIFIED | `dataSync` on all 6 `setForeground` sites + manifest (audit, no new types); `DownloadStopReason` mapper + `getStopReason()` observation + retry copy UI (commit `2d629e85`); `DownloadStopReasonTest` 4/4; full suite **894/894**; hardware drill: E4B download started, progress rendered, user cancel → WorkManager tore down worker (`reason: 1`), app stable, zero FATAL. User-cancel silence is by design (observer removed up-front; copy path serves platform stops, unit-tested) |
| 5 | Chat, catalog, sheets fill large-screen windows (sw ≥ 600dp) without pillarboxing or broken constraints | ✓ VERIFIED | Tablet AVD `Tablet_API36` (API 36 plain image, 2560×1600 @320dpi = sw800dp): chat full-bleed, empty state centered, model sheet centered with scrim, no pillarboxing, no broken constraints (screenshots `tablet-chat5.png`, `tablet-chat6.png`) |

**Score:** 5/5 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `60-01-SUMMARY.md` | Insets + back closeout | ✓ VERIFIED | Commits `8d9e7bd5`, `d731636c`; 890 tests green at that point |
| `60-02-SUMMARY.md` | Stop reasons + hardware checklist | ✓ VERIFIED | Commit `2d629e85`; 894 tests green; checklist executed in-session |
| Theme/sheets/screens BackHandler wiring | API-02/03 code | ✓ VERIFIED | 7 files, deviation `contentWindowInsets` (M3 1.4.0 API) documented |
| `DownloadStopReason` + test | API-04 code | ✓ VERIFIED | Pure mapper, 4/4 tests; 3 auto-fixed deviations documented |
| Tablet screenshots | API-05 evidence | ✓ VERIFIED | `tablet-chat5.png` (chat fill), `tablet-chat6.png` (sheet) |

### Security Notes

- T-60-03: only the platform stop int is logged/stored — never tokens, URLs, headers (Timber line verified).
- No new permissions; FGS `dataSync` unchanged.

## Recommendation

Phase 60 complete. The 5 follow-ups are staging-bound (need 3-button nav, a theme
toggle that is product work, Play Console access, foldable hardware, real quota
pressure) and tracked as release-UAT — none blocks the milestone's compliance value.
