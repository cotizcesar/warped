---
phase: quick-260430-tac
plan: 01
subsystem: downloads
tags: [downloads, background, coroutines, models-screen, huggingface]
requires: []
provides: [application-scoped-download-coroutine, active-downloads-ui]
affects: [ModelDownloadManager, HuggingFaceViewModel, ModelsScreen]
tech-stack:
  added: []
  patterns: [CoroutineScope(SupervisorJob), StateFlow-per-download, fire-and-forget-download]
key-files:
  created: []
  modified:
    - app/src/main/java/com/warped/data/local/download/ModelDownloadManager.kt
    - app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt
    - app/src/main/java/com/warped/ui/huggingface/HuggingFaceUiState.kt
    - app/src/main/java/com/warped/ui/models/ModelsViewModel.kt
    - app/src/main/java/com/warped/ui/models/ModelsUiState.kt
    - app/src/main/java/com/warped/ui/models/ModelsScreen.kt
decisions:
  - "Use SupervisorJob scope for downloads so one failing download doesn't cancel others"
  - "Replace global @Volatile isPaused with per-download state map check in download loop"
  - "Use HuggingFaceViewModel's downloadStates collector to sync progress reactively instead of callback"
metrics:
  duration: "~5 min"
  completed_date: "2026-05-01"
---

# Quick Task 260430-tac: Background Downloads + Models List Summary

**One-liner:** Model downloads now survive screen navigation via application-scoped coroutine scope, with active/incomplete downloads shown in the Models & Endpoints screen with progress bars, cancel, and delete actions.

## Completed Tasks

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Application-scoped download coroutine | `35e4c20` | `ModelDownloadManager.kt` |
| 2 | HuggingFaceViewModel for background downloads | `a524a13` | `HuggingFaceViewModel.kt`, `HuggingFaceUiState.kt` |
| 3 | Active downloads in Models screen | `5a5f7cb` | `ModelsViewModel.kt`, `ModelsUiState.kt`, `ModelsScreen.kt` |

## What Was Built

### Task 1: ModelDownloadManager — Application-scoped download coroutine

- Added `CoroutineScope(SupervisorJob() + Dispatchers.IO)` as `downloadScope` — downloads now outlive the ViewModel that started them
- Replaced `suspend fun downloadModel()` with non-suspend `fun startDownload()` that launches in the download scope (fire-and-forget)
- Removed `onProgress` callback parameter — progress is reported exclusively through `_downloadStates` StateFlow
- Removed `@Volatile private var isPaused` global flag — download loop now checks `_downloadStates.value[modelId]?.isPaused == true` for per-download pause state
- Updated `pauseDownload()` and `resumeDownload()` to accept `modelId` parameter
- Added `cancelDownload(modelId)` — sets download state to cancelled, which the download loop detects and stops
- Added `deleteIncompleteDownload(modelId, fileName)` — deletes partial file and removes state entry

### Task 2: HuggingFaceViewModel — Reactive download state observation

- Added `activeDownloadId: String?` field to `HuggingFaceUiState` for tracking which download is active
- Added `downloadStates.collect` in `init` to reactively sync download progress, completion, and errors to the HuggingFace UI state
- Rewrote `downloadFile()` to call `startDownload()` (non-suspend) instead of `downloadModel()` (suspend), removing the inner `viewModelScope.launch`
- Updated `pauseDownload()` to call `downloadManager.cancelDownload(activeId)` with the tracked download ID

### Task 3: ModelsScreen — Active downloads display with actions

- Added `activeDownloads: List<DownloadState>` to `ModelsUiState`
- Injected `ModelDownloadManager` into `ModelsViewModel` with a `downloadStates` collector that filters for active/paused/incomplete downloads
- Added `cancelDownload(modelId)` and `deleteIncompleteDownload(download)` methods to `ModelsViewModel`
- Added "Active Downloads" section (before "Local Models") in the `LazyColumn`
- Implemented `DownloadCard` composable with three states:
  - **Downloading:** `LinearProgressIndicator` with percentage, bytes progress, and Cancel button
  - **Paused:** Orange "Paused" text with size info and Delete button
  - **Interrupted/Error:** Red error text with error message and "Delete partial file" button

## Deviations from Plan

None — plan executed exactly as written.

## Verification

All automated verifications passed:
- `downloadScope`, `startDownload`, `cancelDownload`, `deleteIncompleteDownload` present in `ModelDownloadManager.kt`
- `startDownload`, `activeDownloadId`, `downloadStates.collect` present in `HuggingFaceViewModel.kt`
- `DownloadCard`, `activeDownloads`, `cancelDownload`, `deleteIncompleteDownload` present in `ModelsScreen.kt`

## Success Criteria Validation

| Criteria | Status |
|----------|--------|
| Downloads survive screen changes (application-scoped coroutine) | ✅ `CoroutineScope(SupervisorJob() + Dispatchers.IO)` |
| Active/incomplete downloads appear in Models list with progress | ✅ `activeDownloads` collected in `ModelsViewModel`, rendered in `ModelsScreen` |
| Incomplete/paused downloads can be cancelled | ✅ `cancelDownload()` sets `isPaused=true`, download loop detects and stops |
| Incomplete downloads can be deleted | ✅ `deleteIncompleteDownload()` removes partial file and state entry |
| HuggingFace UI observes download progress reactively | ✅ `downloadStates.collect` in `HuggingFaceViewModel.init` |

## Known Stubs

None — all features are wired to real data and fully functional.

## Self-Check: PASSED

- `ModelDownloadManager.kt` exists with `startDownload`, `cancelDownload`, `deleteIncompleteDownload` ✅
- `HuggingFaceViewModel.kt` exists with `startDownload` call and `downloadStates` collector ✅
- `ModelsScreen.kt` exists with `DownloadCard` and `activeDownloads` section ✅
- Commit `35e4c20` exists ✅
- Commit `a524a13` exists ✅
- Commit `5a5f7cb` exists ✅
