# Quick Plan: Honest Delete + Unified Download Look

## Verified diagnosis (checked in code 2026-09-28, do not re-investigate)

- `ModelImportManager.deleteModel` (lines 65-71): CONFIRMED — `File.delete()` boolean unchecked, `deleteModel(model.id)`, EMPTY `catch (e: Exception) {}`. It returns `Unit`, never throws → `ModelsViewModel.deleteModel` catch (lines 97-105) is DEAD code. Net: tap trash → dialog closes → model stays.
- Duplicate-row risk CONFIRMED, not hypothetical: `LocalModelEntity.id` is `@PrimaryKey(autoGenerate = true)`, NO unique index on `file_path`; `upsert` = blind `@Insert REPLACE` (dedupes on PK only). Both `importFromUri` (entity id defaults 0 → always a new row) and `ModelDownloadWorker:272 saveModel` insert fresh rows per file → re-download/import of the same file piles duplicate rows. `deleteById` removes one → ghosts. `getByFilePath` returns a single entity.
- Error-path claim PARTIALLY STALE: `ModelsScreen` lines 322-325 IS a dead no-op block, BUT a working Snackbar path already exists — `SnackbarHostState` + `LaunchedEffect(uiState.error)` (lines 51-58) → `SnackbarHost` in Scaffold (line 96), and `ModelsViewModel.clearError()` exists (line 345). So: do NOT add a SnackbarHost; just delete the dead block. Delete errors never reach it only because the manager swallows them.
- Active-model entry point EXISTS, no engine surgery: `ActiveModelSelection.disconnectLocal()` (lines 75-79) is a trivial selection-clearing call. `useLocalModel` connects via `connectLocal(model.filePath, …)` so the guard compares `localSelection.value.modelId == model.filePath`. `ModelsViewModel` already injects `activeModelSelection`.
- Download UI differs CONFIRMED: catalog active = 24dp ring + pause/resume + X icon cluster (`CatalogDownloadActions`) + compact two-line text (`CatalogInlineProgress`); Models `DownloadCard` = `LinearProgressIndicator` + `"85% · x / y · speed"` status line + Cancel (plus paused/interrupted branches with Delete).
- Test infra: MockK + JUnit5 JVM tests are the pattern; `room-testing` is in the version catalog; `titleEndPaddingDp` is asserted in `CatalogCardTextTest` (52/128) — it WILL need updating when the wide active cluster goes away.

## Locked decisions (not up for debate during execution)

- D1 — Pause UI: EXACT MIRROR (Cancel only). The catalog active state loses its pause/resume icons. `ModelDownloadManager.pauseDownload/resumeDownload` and worker checkpoint logic are UNTOUCHED (no engine change) but after this plan have NO UI entry point anywhere (`ModelsViewModel` exposes no pause/resume; catalog unwires its only caller). `CatalogViewModel.startDownload/cancelDownload` stay; `pauseDownload/resumeDownload` passthroughs stay as dead-but-kept functions with a comment, so re-wiring is one line. State this consequence in the final summary so the user can object.
- D2 — Shared component location: `app/src/main/java/com/warped/ui/components/ActiveDownloadCard.kt` (matches existing `ui/components/` convention: `CapabilityBadges.kt`, `WarpedAlertDialog.kt`). Single copies of `formatFileSize`/`formatDownloadSpeed` live there as `internal` funs; both screens use them (ModelsScreen keeps its model-size usage pointed at the shared copy; delete the private duplicates).
- D3 — Repository approach: ADDITIVE, no breaking callers. Keep `deleteModel(id)`; add `deleteByFilePath(filePath): Int` to interface + impl. DAO adds `@Query("DELETE FROM local_models WHERE file_path = :filePath") suspend fun deleteByFilePath(filePath: String): Int`.
- D4 — Delete contract (English messages, no Spanish). `ModelImportManager.deleteModel(model: LocalModel)` throws `IllegalStateException`, never swallows:
  1. Disconnect-first is the ViewModel's job (guard), not the manager's.
  2. If `File(model.filePath)` exists and `delete()` returns false → throw `IllegalStateException("Could not delete model file at ${model.filePath}")`. No retry, no fallback.
  3. Delete ALL rows via `deleteByFilePath(model.filePath)`; if returned count == 0 → throw `IllegalStateException("Model file removed but no library entry found for ${model.filePath}")` (covers file-deleted-but-zero-rows).
  4. File already absent + rows deleted > 0 → SUCCESS (stale-row cleanup, e.g. user cleared app files externally).
  5. File absent + zero rows → throw `IllegalStateException("Model not found on device or in library: ${model.name}")`.
  6. NO empty catches anywhere on this path; no `try/catch` at all unless rethrowing with added context. Let unexpected exceptions (SecurityException, SQLiteException) propagate to the ViewModel catch → Snackbar.
- D5 — Out of scope: worker/engine changes, catalog data/flags, import flow, `deleteModel(id)` removal, download completion dedupe (re-download still inserts a row; delete-by-filepath makes that harmless — note as known residual, do NOT fix here).

## Task 1 (Wave 1): Honest delete — DAO → repository → manager → ViewModel guard → dead-block removal

Files:
- `app/src/main/java/com/warped/data/local/db/dao/LocalModelDao.kt` (add `deleteByFilePath`)
- `app/src/main/java/com/warped/domain/repository/LocalModelRepository.kt` (add `deleteByFilePath(filePath: String): Int`)
- `app/src/main/java/com/warped/data/repository/LocalModelRepositoryImpl.kt` (delegate, return DAO count)
- `app/src/main/java/com/warped/data/local/inference/ModelImportManager.kt` (rewrite `deleteModel` per D4)
- `app/src/main/java/com/warped/ui/models/ModelsViewModel.kt` (`deleteModel`: if `activeModelSelection.localSelection.value.modelId == model.filePath` → `activeModelSelection.disconnectLocal()` FIRST, then manager call; keep existing try/catch → `uiState.error`)
- `app/src/main/java/com/warped/ui/models/ModelsScreen.kt` (DELETE the dead `if (uiState.error != null)` no-op block lines ~322-325 ONLY; do not touch the working Snackbar/LaunchedEffect)

Notes: `deleteModel(id)` stays for other callers. Manager rewrite must keep `withContext(Dispatchers.IO)`. Guard uses `disconnectLocal()` only — no engine unload calls.

Verify: `./gradlew :app:assembleDebug` passes; `grep -n "catch" ModelImportManager.kt` shows no catch on the delete path (import's `Result.failure` catch is fine, out of scope).
Done: deleting a model removes the file AND every row with that filePath; deleting the connected model disconnects first; any failure surfaces English text in `uiState.error`; no empty catch on the path.

## Task 2 (Wave 2, depends on Task 1 — shared `ModelsScreen.kt` ownership): Unified download look

Files:
- `app/src/main/java/com/warped/ui/components/ActiveDownloadCard.kt` (NEW: `ActiveDownloadContent(download: DownloadState, onCancel: () -> Unit, onDeleteIncomplete: () -> Unit)` — linear bar + status line + Cancel for downloading; paused branch mirrors Models `DownloadCard` paused look `"Paused · x / y"` + Delete; interrupted branch mirrors `"Interrupted · …"` + error + Delete-partial; plus shared `internal formatFileSize/formatDownloadSpeed`)
- `app/src/main/java/com/warped/ui/models/ModelsScreen.kt` (`DownloadCard` delegates ALL branches to `ActiveDownloadContent`; delete its private `formatFileSize/formatDownloadSpeed`; rest of file byte-identical)
- `app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt` (REPLACE `CatalogDownloadActions` active branch + `CatalogInlineProgress` usage with `ActiveDownloadContent` + cancel-confirm dialog reuse; idle/downloaded/failed icon states, expanded section, `expandedText`, `isEffectivelyDownloaded` byte-identical; remove now-unused pause/resume wiring and `titleEndPaddingDp` active-slot logic — single constant padding since the wide cluster is gone; drop unused Pause/PlayArrow/CircularProgressIndicator imports)
- `app/src/main/java/com/warped/ui/huggingface/CatalogViewModel.kt` (keep `pauseDownload/resumeDownload` with `// No UI entry point after unified download look (Cancel only) — kept for re-wire` comment; read-only otherwise)

Notes: catalog keeps its cancel-confirm dialog ("The partial file will be deleted.") — the shared component takes `onCancel` and each call site keeps its own confirm UX (Models `DownloadCard` cancels directly, catalog confirms first). Per D1, pause icons disappear from catalog; record in summary.

Verify: `./gradlew :app:assembleDebug` passes; visual sameness needs on-device confirmation (no adb — state in summary, do not attempt).
Done: both screens render the identical linear-bar + status-line + Cancel active-download visual; catalog idle/downloaded/failed/expanded unchanged; no pause/resume UI entry remains; no unused imports (lint clean).

## Task 3 (Wave 3, depends on Tasks 1-2): Tests + full green

Files (create/update):
- `app/src/test/java/com/warped/data/local/inference/ModelImportManagerDeleteTest.kt` (NEW, JVM + MockK: fake `LocalModelRepository` + temp dir via `@TempDir` — file present + `deleteByFilePath` returns 1 → success and file gone; file present + `deleteByFilePath` returns 0 → throws with "no library entry"; file absent + returns 2 → success; file absent + returns 0 → throws "not found"; repository throwing → propagates, never swallowed. NOTE: `ModelImportManager` takes `@ApplicationContext Context` — mock `context.filesDir` with MockK or construct with a fake filesDir; `deleteModel` only uses `model.filePath` + repository, so a relaxed `mockk<Context>()` suffices.)
- `app/src/test/java/com/warped/ui/models/ModelsDeleteErrorTest.kt` (NEW, JVM + MockK + coroutines-test: manager mock throws on `deleteModel` → `uiState.error` set (Turbine or `runTest` + advance); `disconnectLocal` called when `localSelection` matches filePath — mock `ActiveModelSelection` relaxed, stub `localSelection` StateFlow; success path sets no error. Follow `CatalogDownloadUrlTest` MockK patterns for ViewModel construction.)
- DAO `deleteByFilePath` test: `room-testing` is cataloged but there is NO existing JVM Room test (only `androidTest` MigrationTest). Cheapest honest option: `app/src/androidTest/java/com/warped/data/local/db/LocalModelDaoFilePathTest.kt` with in-memory DB (`Room.inMemoryDatabaseBuilder`), insert 2 rows same filePath + 1 different → `deleteByFilePath` returns 2, others remain. If emulator/instrumentation is unavailable in this environment, write the test + document it as not-run rather than faking a pass.
- Update `CatalogCardTextTest` (`titleEndPaddingDp` expectations match Task 2 outcome) and any test referencing `CatalogInlineProgress`/`CatalogDownloadActions`/pause wiring; update any existing `LocalModelRepository` mocks broken by the interface addition (relaxed mocks are immune — check `SettingsGroundingToggleTest`, `CatalogDownloadUrlTest`).
- Shared-component logic: only add pure-logic tests if Task 2 extracted any (status-line formatting is already covered by duplication — if moved verbatim, no new tests needed; say so in summary).

Verify: `./gradlew :app:testDebugUnitTest` FULLY green + `:app:assembleDebug` passes. Honest note in summary: delete E2E + visual sameness need on-device confirmation (no adb here).
Done: new tests green, affected existing tests updated, full unit suite green.
