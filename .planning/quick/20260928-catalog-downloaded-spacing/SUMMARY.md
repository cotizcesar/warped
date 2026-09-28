# Summary: Catalog Downloaded State + Card Spacing

**Phase:** quick-20260928-catalog-downloaded-spacing / Plan 01
**Status:** Complete
**Date:** 2026-09-28

## What was built

Fixed two catalog-card defects in the Hugging Face model catalog:

1. **On-device downloaded state** — models already on-device previously showed
   a Download button because `downloaded` derived only from in-session
   WorkManager state. `CatalogViewModel` now exposes
   `downloadedFileNames: StateFlow<Set<String>>` derived from
   `LocalModelRepository.observeModels()`, mapping each
   `LocalModel.filePath` to its file name via `substringAfterLast('/')`.
   Match key is `entry.modelFile` (catalog file name), never
   downloadId/modelId. `HuggingFaceScreen` collects the flow, passes
   `isOnDevice` per entry into `CatalogModelCard`, and the card computes
   `downloaded = isEffectivelyDownloaded(downloadState, isOnDevice)` through
   a new top-level pure helper. On-device entries render the existing
   CheckCircle branch (`"Descargado"`, no click handler) — tap is a no-op by
   construction. No select/load-from-catalog behavior added (out of scope).

2. **Card spacing** — exactly three edits: title-to-action spacer 8dp → 4dp,
   row gap 8dp → 4dp, capability-icon `spacedBy(6.dp)` → `spacedBy(3.dp)`.
   Lazy-column 8dp arrangement, 14dp card padding, and progress/error spacers
   untouched.

## Commits

- `07b916b` — feat: ViewModel on-device presence flow
  (`CatalogViewModel.kt`: inject `LocalModelRepository`, add
  `downloadedFileNames` with `SharingStarted.Eagerly`)
- `1235bd7` — feat: screen wiring plus helper plus spacing
  (`HuggingFaceScreen.kt`: collect flow, `isOnDevice` param defaulting to
  false, `isEffectivelyDownloaded` helper, 3 spacing edits)
- `c0e7e5b3` — test: on-device mapping plus helper truth table
  (new `CatalogDownloadedTest.kt`; `CatalogDownloadUrlTest.kt` constructors
  updated for the new third param)

## Test results

- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL
- `./gradlew :app:testDebugUnitTest --tests "com.warped.ui.huggingface.*"` —
  green (CatalogCardTextTest 4/4, CatalogDownloadedTest 6/6,
  CatalogDownloadUrlTest 4/4)
- Full `./gradlew :app:testDebugUnitTest` — BUILD SUCCESSFUL, 309 tests,
  0 failures, 0 errors

## Grep gates

- `downloadedFileNames` present in `CatalogViewModel.kt` — yes
- `isEffectivelyDownloaded` present in `HuggingFaceScreen.kt` — yes
- No `width(8.dp)` at the title-to-action site / no `spacedBy(6.dp)` in
  `CatalogCapabilityIcons` — confirmed via grep

## Deviations

None — plan executed exactly as written.
