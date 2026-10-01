---
phase: 60-api36-behavior-audit
plan: "02"
subsystem: downloads
tags: [android-16, sdk-36, workmanager, quotas, stop-reason, dataSync, large-screen]
requires:
  - phase: 60-api36-behavior-audit
    provides: 60-01 edge-to-edge + back-handler baseline on device surfaces
provides:
  - Pure DownloadStopReason mapper (18 WorkManager codes + generic fallback)
  - UI-side stop-reason observation with Timber logging + surfaced retry copy
  - dataSync audit: every download/retry path covered, no new FGS types
  - Large-screen fill audit dispositions (zero width-cap hits)
affects: [60-02 hardware session, release-UAT, quota device evidence]
tech-stack:
  added: []
  patterns: ["Workers never read their own stop reason — observe WorkInfo UI-side", "Retry gated on stopReasonCopy presence (no dead retry buttons)"]
key-files:
  created: ["app/src/main/java/com/warped/data/local/download/DownloadStopReason.kt", "app/src/test/java/com/warped/data/local/download/DownloadStopReasonTest.kt"]
  modified: ["app/src/main/java/com/warped/data/local/download/ModelDownloadManager.kt", "app/src/main/java/com/warped/ui/models/ModelsViewModel.kt", "app/src/main/java/com/warped/ui/models/ModelsScreen.kt", "app/src/main/java/com/warped/ui/components/ActiveDownloadCard.kt", "app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt", "app/src/main/java/com/warped/ui/benchmark/BenchmarkViewModel.kt", "app/src/main/res/values/strings.xml", "app/src/main/res/values-es/strings.xml"]
key-decisions:
  - "Mapper defines its own int constants (no androidx import) with a parity test against WorkInfo — pure JVM-testable, zero drift risk"
  - "User pause removes the WorkInfo observer up-front (clean pause UI); surviving CANCELLED is always a genuine platform stop with copy"
  - "Retry button renders only when stopReasonCopy is present — HTTP-error failures keep error + Delete, never a dead Retry"
  - "MessageBubble 340dp widthIn cap left untouched: bubble readability, not pillarboxing"
requirements-completed: []
duration: ~45min
completed: 2026-10-01
---

# Phase 60 Plan 02: Quota Stop-Reasons + Large-Screen Fill (Code + Unit Tests) Summary

**Stop-reason mapper + UI-side observation with surfaced retry copy, dataSync audit clean on every path, large-screen fill audit with zero width-cap hits — all automated-green; hardware verification explicitly PENDING (Task 3 not executed)**

## Performance

- **Duration:** ~45 min
- **Started:** 2026-10-01 (phase execution)
- **Completed:** 2026-10-01
- **Tasks:** 2 / 2 AUTO tasks (Task 3 hardware checkpoint intentionally not executed)
- **Files created:** 2 (mapper + test)
- **Files modified:** 8

## Accomplishments

- **API-04 (Task 1, TDD):** `DownloadStopReason` pure mapper covers all 18 documented work-runtime 2.12.0 stop-reason ints with specific English retry copy; unknown/future ints fall back to a safe generic copy. `DownloadStopReasonTest` (4 tests) green.
- **Stop-reason wiring:** `ModelDownloadManager.observeWorkProgress` reads `getStopReason()` UI-side (API-31-guarded) on CANCELLED/FAILED, logs the platform int via Timber only (T-60-03), and stores the mapped copy on new `DownloadState.stopReasonCopy`.
- **Surfaced retry UI:** `ActiveDownloadContent` shows the copy in paused + interrupted branches; the interrupted branch gains a Retry button (gated on copy presence) wired to `ModelsViewModel.retryDownload` (Models) and `resumeDownload` (catalog).
- **Benchmark stops:** `BenchmarkViewModel` logs stopped unique-work reasons once each (int only, no paths/tokens).
- **dataSync audit:** Manifest (perms + dataSync service) plus all `setForeground` sites (3 in download worker, 3 in benchmark incl. trials loop) run under `FOREGROUND_SERVICE_TYPE_DATA_SYNC`. No new FGS types. v2.3 message-scoped offline-retry (`retryGrounding`) is coroutine-based with zero WorkManager/FGS involvement — untouched.
- **API-05 (Task 2):** fill audit complete — `maxWidth|fillMaxWidth(0.` grep across chat/huggingface/models returns **0 hits**; every fixed-width hit dispositioned fill-safe (see table); no breakpoints, no two-pane, no bespoke layouts.

## Task Commits

Each task was committed atomically:

1. **Task 1 RED: Stop-reason mapper test** — `1da5dd65` (test; fails to compile = RED, mapper absent)
2. **Task 1 GREEN: Mapper + wiring** — `2d629e85` (feat; 9 files, no deletions)
3. **Task 2: Large-screen fill audit** — verify-only, zero code changes, no commit

**Plan metadata:** SUMMARY.md created on disk, uncommitted (orchestrator owns final metadata commit per --no-transition).

## Files Created/Modified

- `data/local/download/DownloadStopReason.kt` — **created**: pure mapper, own int constants mirroring WorkInfo, `stopReasonCopy()` + `GENERIC_COPY` export.
- `data/local/download/DownloadStopReasonTest.kt` — **created**: 4 tests (all-18-codes specific copy, WorkInfo parity, unknown/future fallback, purity).
- `data/local/download/ModelDownloadManager.kt` — **modified**: `DownloadState.stopReasonCopy` field; stop-reason read/log/map on CANCELLED + FAILED; copy cleared on start/resume/RUNNING; `readStopReason()` API-31 guard.
- `ui/models/ModelsViewModel.kt` — **modified**: `retryDownload()` (logs acted-on copy, delegates to checkpoint resume).
- `ui/models/ModelsScreen.kt` — **modified**: `DownloadCard` takes `onRetry` → `viewModel.retryDownload`.
- `ui/components/ActiveDownloadCard.kt` — **modified**: `onRetry` param (default no-op); copy shown in paused/interrupted branches; Retry button gated on copy presence.
- `ui/huggingface/HuggingFaceScreen.kt` — **modified**: `CatalogModelCard.onRetry` passthrough, wired to `resumeDownload` (same manager API as Models).
- `ui/benchmark/BenchmarkViewModel.kt` — **modified**: init collector logs stopped unique-work reasons once each (T-60-03 int-only).
- `res/values/strings.xml`, `res/values-es/strings.xml` — **modified**: `dl_retry` "Retry"/"Reintentar" (parity test demanded the ES twin).
- `data/local/download/ModelDownloadWorker.kt` — **verified-as-is**: all setForeground sites route through `createForegroundInfo` (dataSync on 34+); resume re-enqueues the same worker.
- `data/local/benchmark/ModelBenchmarkWorker.kt` + `BenchmarkNotifier.kt` — **verified-as-is**: foregroundInfo carries dataSync; workers correctly do NOT read their own stop reason.
- `ui/models/ModelsUiState.kt` — **verified-as-is**: no change needed, copy flows through existing `activeDownloads: List<DownloadState>`.
- `AndroidManifest.xml` — **verified-as-is**: FOREGROUND_SERVICE + DATA_SYNC + dataSync SystemForegroundService already declared.

## Width-Cap Dispositions (Task 2)

| Hit | File | Disposition |
|---|---|---|
| `maxWidth` / `fillMaxWidth(0.` | chat, huggingface, models | **0 hits** — no pillarboxing caps exist |
| `.width(4–12.dp)` spacers | various | Fill-safe (layout gaps) |
| `.width(1.dp)` divider, `.width(32.dp)` gutter | CodeBlock.kt | Fill-safe (internal decoration) |
| `.widthIn(max = 340.dp)` | MessageBubble.kt | Fill-safe by design — bubble readability cap, NOT a screen cap; untouched (adaptive-fill only) |
| `cardWidth` param | OgSourceCard.kt | Fill-safe — sole call site (AllSourcesSheet:110) passes null → fillMaxWidth |
| Root containers | ChatScreen, HuggingFaceScreen, ModelsScreen, sheets | fillMaxSize/fillMaxWidth throughout; no CenteredTopLevel containers |

## Decisions Made

- Mapper owns its int constants with a parity test rather than importing WorkInfo — keeps the mapper dependency-free and JVM-testable while catching work-runtime drift at test time.
- Explicit user cancel keeps its observer attached so CANCELLED_BY_APP surfaces "Download cancelled."; only user *pause* detaches early (clean pause UI). This preserves the hardware-checkable "cancel → copy appears" path.
- Retry is checkpoint-resume, not restart: after explicit cancel (checkpoint deleted) Retry surfaces the no-resume error honestly instead of pretending.
- Catalog retry reuses `resumeDownload` (the pre-existing "kept for re-wire" API) rather than adding a new path.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing critical] Benchmark stop logging needed BenchmarkViewModel.kt (not in plan files)**
- **Found during:** Task 1 (benchmark stop-reason wiring)
- **Issue:** Plan files listed ModelBenchmarkWorker.kt + BenchmarkNotifier.kt for the benchmark side, but workers cannot read their own stop reason — the only UI-side observer is BenchmarkViewModel's unique-work flow.
- **Fix:** Added init collector logging stopped-work reasons once each (int-only per T-60-03); worker/notifier verified-as-is.
- **Files modified:** BenchmarkViewModel.kt

**2. [Rule 2 - Missing critical] Catalog retry entry point needed HuggingFaceScreen.kt wiring**
- **Found during:** Task 1 (shared-card Retry button)
- **Issue:** The shared `ActiveDownloadContent` Retry button would be dead on the catalog side without an `onRetry` passthrough (catalog shows the same interrupted branch via shared DownloadState).
- **Fix:** Added `onRetry` param to CatalogModelCard wired to the existing `resumeDownload`; default `{}` keeps the composable safe for any future call site.
- **Files modified:** HuggingFaceScreen.kt

**3. [Rule 1 - Bug] StringResourceParityTest failed on new dl_retry**
- **Found during:** Task 1 verification (full suite)
- **Issue:** `values/strings.xml` gained `dl_retry` without its `values-es` twin — parity test enforces exact key match.
- **Fix:** Added `dl_retry` → "Reintentar" to values-es (matches `bubble_retry` convention).
- **Files modified:** values-es/strings.xml

---

**Total deviations:** 3 auto-fixed (2 missing-critical, 1 bug)
**Impact on plan:** Zero scope creep — all three are correctness requirements of the plan's own verify gates. No new FGS types, no provider stopReason touched, no WindowSizeClass.

## Issues Encountered

- Intermediate full-suite run failed solely on the string-parity test (deviation 3); rerun after the ES fix: **894 tests, 0 failures** (890 baseline + 4 new).

## Known Stubs

None introduced. New/modified code paths carry no TODO/FIXME/placeholder; Retry is checkpoint-backed, never mocked.

## Threat Flags

None beyond the mitigated register items: stop-reason logs carry platform ints only (T-60-03, verified by reading the two Timber call sites — modelId + int, no URLs/headers/tokens); dataSync on every path (T-60-04 code half; device half is PENDING below). No new network endpoints, auth paths, or schema changes.

## PENDING — Hardware Verification Session (Task 3, NOT executed)

> The terminal hardware checkpoint was deliberately not attempted: no device/emulator
> verification, per scope. A human session must walk the matrix below on the
> physical Pixel 8 (`ANDROID_SERIAL=37141FDJH0065Y`, adb at
> `/home/cotizcesar/Android/Sdk/platform-tools/adb` — NOT on PATH) and a healthy
> sw>=600dp AVD (emulator-5554 crash-loops — exclude it). Anything unverifiable
> becomes a named release-UAT entry — never a silent pass.

### Quota pressure (API-04) — validates Task 1 code

1. **Progress renders:** start a model download on the Pixel 8, confirm progress renders in ActiveDownloadCard (Models screen) and the catalog card.
2. **Cancel → stop copy:** cancel mid-download, confirm cancel lands AND the stop-reason copy ("Download cancelled.") appears in the retry UI — in-app, not just logcat (`adb logcat | grep "work stopped"` should also show `stopReason=1`).
3. **Retry resumes:** tap Retry, confirm progress resumes from the checkpoint (downloaded-bytes continuity, not restart-from-zero). NOTE: after explicit *cancel* the checkpoint is deleted, so Retry honestly reports "Cannot resume" — retry-resume is proven after a *pause* or system stop, not after cancel-delete.
4. **Offline-to-retry under 36 quotas:** toggle offline mid-flow then back online, confirm the v2.3 message-scoped retry path fires (chat `retryGrounding`) and downloads resume via WorkManager CONNECTED constraint.

### Edge-to-edge visuals (API-02, confirms 60-01 on device)

5. **Nav × theme matrix:** on the Pixel 8 in gesture-nav AND 3-button nav (Settings > System > Navigation mode), in dark theme (app is dark-only — `WarpedTheme(darkTheme = true)` hardcoded, light mode unverifiable until a toggle lands): open chat pill input with keyboard, each bottom sheet (model picker, sources preview, all-sources), and the catalog/Fuentes list — confirm no overlap/bleed under bars, icon contrast readable.

### Back behavior (API-03, confirms 60-01 on device)

6. **Back parity:** back-gesture and back-button each dismiss chat picker, every sheet, settings, and presets identically (gesture animated, button direct).

### Large-screen (API-05) — validates Task 2 audit

7. **sw>=600dp fill:** on the healthy tablet/foldable AVD, open chat, catalog, and a sheet — confirm they fill the window with no pillarboxing or broken constraints. Pay attention to MessageBubble 340dp cap (intentional — bubbles must NOT span full width) vs any true pillarboxing.

### Rule

Anything that cannot be verified on the available hardware/emulators is recorded as a named release-UAT entry in the orchestrator's record — never a silent pass, never blocking the phase.

## Self-Check: PASSED

- Mapper + test exist on disk; RED (`1da5dd65`) then GREEN (`2d629e85`) commits verified in `git log`; no deletions in GREEN commit; full suite 894/0/0/0; `assembleDebug` green; `stopReason` grep >= 1 in ModelsViewModel (3) + ActiveDownloadCard (6); width-cap grep 0 hits; AnthropicProvider untouched.
