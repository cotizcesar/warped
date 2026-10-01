---
audit_acknowledged:
  milestone: v2.5
  at: 2026-10-01
  status: unknown
---

# SUMMARY: Honest Delete + Unified Download Look

**Dir:** `.planning/quick/20260928-honest-delete-unified-download/`
**Status:** COMPLETE — all 3 waves executed, committed, full unit suite green.
**Date:** 2026-09-28

## Commits

| Wave | Task | Commit | Description |
|------|------|--------|-------------|
| 1 | Honest delete | `f7dd606c` | DAO/repository `deleteByFilePath`, D4 manager rewrite, disconnect-first guard, dead-block removal |
| 2 | Unified download look | `a4b518ef` | Shared `ActiveDownloadContent`, Cancel-only catalog, constant 52dp title padding |
| 3 | Tests + full green | `d80458e9` | 8 new JVM tests, padding expectation update, DAO instrumentation test |

## What changed

**Wave 1 — honest delete:** `ModelImportManager.deleteModel` now follows the D4
contract exactly — throws `IllegalStateException` (English) when the file can't
be deleted, when zero rows match after a file delete, and when neither file nor
rows exist; file-absent + rows-deleted is success (stale-row cleanup). No
`try/catch` on the path (only remaining `catch` in the file is the import-flow
`Result.failure`, out of scope). `ModelsViewModel.deleteModel` calls
`activeModelSelection.disconnectLocal()` first when the deleted model's
`filePath` matches the connected local selection. The dead `if (uiState.error)`
no-op block is gone from `ModelsScreen`; the working Snackbar path is untouched.

**Wave 2 — unified download look:** new `ui/components/ActiveDownloadCard.kt`
with `ActiveDownloadContent(download, onCancel, onDeleteIncomplete)` plus the
single shared copies of `internal formatFileSize` / `formatDownloadSpeed`.
Models `DownloadCard` delegates to it; its private format dupes are deleted.
Catalog active branch renders the same component; pause/resume icons,
`CatalogInlineProgress`, `CatalogDownloadActions` active branch, and the 128dp
active title slot are gone. Catalog keeps its cancel-confirm dialog ("The
partial file will be deleted.") — both Cancel and Delete route through it.
Idle/downloaded/failed states, expanded section, `expandedText`, and
`isEffectivelyDownloaded` are byte-identical.

**Wave 3 — tests:** `ModelImportManagerDeleteTest` (5 tests),
`ModelsDeleteErrorTest` (3 tests), `CatalogCardTextTest` active-padding
expectation 128 → 52, new `LocalModelDaoFilePathTest` instrumentation test.
No new pure-logic tests for the shared component — status-line formatting moved
verbatim, already covered by duplication.

## Test results

- `./gradlew :app:assembleDebug` — PASS
- `./gradlew :app:testDebugUnitTest` — FULLY GREEN: **321 tests, 0 failures,
  0 errors, 0 skipped** (37 suites), including the 8 new tests
- `./gradlew :app:compileDebugAndroidTestKotlin` — PASS (compile only)
- `LocalModelDaoFilePathTest` — **written but NOT RUN** (no adb/device in this
  environment); honest status, not faked
- Delete E2E + visual sameness need on-device confirmation (no adb here)

## D1 pause disclosure (locked decision, user-visible consequence)

Per D1 exact mirror (Cancel only): the catalog active state lost its
pause/resume icons. `ModelDownloadManager.pauseDownload/resumeDownload` and the
worker checkpoint logic are **untouched**, but after this plan have **no UI
entry point anywhere** (`ModelsViewModel` exposes no pause/resume; the catalog
unwired its only caller). `CatalogViewModel.pauseDownload/resumeDownload`
passthroughs stay as dead-but-kept functions with a `// No UI entry point
after unified download look (Cancel only) — kept for re-wire` comment, so
re-wiring is one line. If you want pause back in the UI, say so — it's a
one-line re-wire per call site.

## Deviations from plan

1. **Brace repair (Wave 1):** the dead-block removal initially dropped the
   `Column`'s closing brace (build break); restored one `}`. Fix-forward, no
   plan change.
2. **DAO test framework (Wave 3):** first drafted with JUnit5 + Truth + `runTest`,
   but `androidTest` has no Truth/JUnit5 on its classpath (existing
   `MigrationTest` uses JUnit4 + `org.junit.Assert`). Rewrote in matching
   JUnit4 + `runBlocking` style. Test compiles; still not run (no device).
3. **Catalog `onDeleteIncomplete` routing:** the catalog has no
   `deleteIncompleteDownload` wiring, so both Cancel and Delete route through
   the existing cancel-confirm dialog → `cancelDownload` (which deletes the
   partial file per the dialog copy). Noted, not a plan violation — the plan
   left per-site confirm UX to the call site.

## Known residual (per D5, intentionally not fixed)

Re-download still inserts a duplicate row (no completion dedupe); delete-by-
filePath makes that harmless (all dup rows removed at once). Future plan may
add a unique index on `file_path` if desired.
