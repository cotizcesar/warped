---
audit_acknowledged:
  milestone: v2.5
  at: 2026-10-01
  status: unknown
---

# Quick Summary — Active download cluster overlap fix (2026-09-28)

## Status: COMPLETE

Fixed the on-device overlap of the active download icon cluster on catalog cards.
The active branch of `CatalogDownloadActions` emitted its children directly into
the overlay `Box`, so the progress ring + pause/resume + cancel icons stacked on
top of each other instead of laying out horizontally.

## Changes

- `app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt`
  - New pure helper `titleEndPaddingDp(active: Boolean): Int` → `128` when
    active, `52` otherwise (dp value as Int; call site applies `.dp`).
  - `CatalogModelCard` title now uses
    `Modifier.padding(end = titleEndPaddingDp(active).dp)` — idle/downloaded/
    failed cards keep exactly 52dp; only active cards reserve the 128dp slot
    (≈124dp cluster: 24 ring + 4 spacer + 48 pause/resume + 48 cancel).
  - Active branch wrapped in `Row(verticalAlignment = Alignment.CenterVertically)`
    inside the existing overlay `Box` — single Row child, no more Box-stacking.
    Downloading order preserved: ring + 4dp spacer + pause + cancel.
    Paused order preserved: resume + cancel.
  - Active `CircularProgressIndicator` now explicit:
    `color = MaterialTheme.colorScheme.primary`,
    `trackColor = Color(0xFF333333)` (matches app dividers).
  - Idle/downloaded/failed branches untouched — visuals byte-identical.
- `app/src/test/java/com/warped/ui/huggingface/CatalogCardTextTest.kt`
  - +2 tests: `titleEndPaddingDp(false) == 52`,
    `titleEndPaddingDp(true) == 128`.

## Test results

- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL (only pre-existing
  unnecessary-`!!` warnings in HuggingFaceScreen.kt, unrelated to this change).
- `./gradlew :app:testDebugUnitTest` — BUILD SUCCESSFUL, **313 tests,
  0 failures, 0 errors**. `CatalogCardTextTest`: 6/6 pass (4 pre-existing +
  2 new).
- Commit: `6d3531bb` — `fix(quick-active-cluster): wrap active download
  cluster in Row with dynamic title padding`.

## On-device screenshot note

Final visual confirmation (ring | pause | cancel in one centered horizontal row,
long titles ellipsizing before the cluster) requires an on-device screenshot —
no adb/device available in this environment. Debug APK builds clean and is
ready to install for that check.
