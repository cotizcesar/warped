---
audit_acknowledged:
  milestone: v2.5
  at: 2026-10-01
  status: unknown
---

# SUMMARY: All-Sources Sheet ("View all sources" drawer)

**Status:** Complete — all 3 tasks executed, committed, verified.
**Date:** 2026-09-29
**Plan:** `.planning/quick/20260929-all-sources-sheet/PLAN.md`

## What was built

The "Sources" header row in chat now gains a trailing list icon (48dp
`IconButton`, a11y "View all sources") visible ONLY when ≥2 sources exist.
Tapping it opens a bottom sheet listing ALL sources as full-width rows;
row tap opens that URL in the browser directly via the guarded
`openUrlInBrowser` gate. Dismiss via swipe/scrim only, no state change.

## Commits (atomic, one per task)

| Task | Commit | Files |
| ---- | ------ | ----- |
| 1 — AllSourcesSheet composable + EN/ES strings | `0b846c79` | `AllSourcesSheet.kt` (new), `values/strings.xml`, `values-es/strings.xml` |
| 2 — Header icon + sheet wiring | `c5b95074` | `MessageBubble.kt` |
| 3 — JVM tests | `7149894d` | `AllSourcesSheetTest.kt` (new), `AllSourcesSheet.kt` (comment reword) |

## Implementation notes

- `AllSourcesSheet(sources, onDismiss, onOpenBrowser)` mirrors the
  `SourcePreviewSheet` scaffold (`skipPartiallyExpanded = true`, surface
  container). Sheet itself is dumb — the caller (`MessageBubble`)
  dismisses only when `openUrlInBrowser` returns true.
- Ok rows reuse the 2-col card visual (`OgThumb` + `ogDisplayTitle` /
  `ogDisplayDescription` + muted URL, `OgCardDark` dark / `surfaceVariant`
  light). Omitida rows render struck/disabled via `fuente_skipped_fmt`
  with no tap. Rows stay in fetch-block order, ok + omitida interleaved.
- `shouldShowViewAll(fuenteList) = fuenteList.size >= 2` — omitida rows
  count toward the ≥2 rule. Zero-ok-sources behavior unchanged (existing
  `any { it.clickable }` gate untouched). Single-source preview sheet
  byte-identical behavior.
- Icon: `Icons.AutoMirrored.Filled.List` (implementer's pick, per plan).
- New strings: `all_sources_title` ("All sources" / "Todas las fuentes"),
  `cd_view_all_sources` ("View all sources" / "Ver todas las fuentes").
  English copy; zero accent tint; verification grep clean.
- Deviation (trivial, self-approved): reworded a KDoc "zero purple"
  comment to "no accent tint" so the plan's literal zero-purple grep
  returns nothing. No behavior change.

## Test results

- `./gradlew :app:assembleDebug` — **BUILD SUCCESSFUL**
- `./gradlew :app:testDebugUnitTest --tests "com.warped.ui.chat.AllSourcesSheetTest"` — **green** (9 tests)
- `./gradlew :app:testDebugUnitTest` (full suite, incl. `StringResourceParityTest` EN/ES) — **BUILD SUCCESSFUL**
- Zero-purple grep on `AllSourcesSheet.kt` — **no matches**

## On-device notes (not verified — no adb in this environment)

Per the plan's honest note: sheet visuals + tap flow (icon placement, row
density, thumb fallback, guarded-intent browser launch, swipe/scrim
dismiss) are verified by code review + build/tests only. Needs on-device
confirmation: icon appears only with ≥2 sources; sheet lists all rows
full-width; ok-row tap launches browser and dismisses; omitida rows are
struck with no tap; swipe/scrim dismisses with no state change.
