---
phase: 08-model-acquisition
plan: GAP-01
subsystem: model-acquisition
tags: [gap-closure, workmanager, foreground-service, notifications, pause-resume, checkpoint, download]
requires: []
provides: [ACQ-08-foreground, ACQ-09-persistent-pause-resume]
affects: [ModelDownloadManager, ModelDownloadWorker, AppDatabase(v8), WarpedApplication, AndroidManifest]
tech-stack:
  added: [hilt-work:1.2.0]
  patterns: [WorkManager-delegation, LiveData-observation, checkpoint-persistence, foreground-notification, Range-header-resume, isStopped-handling]
key-files:
  created:
    - app/src/main/java/com/warped/data/local/db/entity/DownloadCheckpointEntity.kt
    - app/src/main/java/com/warped/data/local/db/dao/DownloadCheckpointDao.kt
    - app/src/main/java/com/warped/data/local/download/ModelDownloadWorker.kt
  modified:
    - app/src/main/java/com/warped/data/local/download/ModelDownloadManager.kt
    - app/src/main/java/com/warped/data/local/db/AppDatabase.kt (v7→v8)
    - app/src/main/java/com/warped/data/local/db/Migrations.kt (MIGRATION_7_8)
    - app/src/main/java/com/warped/di/DatabaseModule.kt
    - app/src/main/java/com/warped/WarpedApplication.kt (Configuration.Provider)
    - app/src/main/AndroidManifest.xml (foreground service permissions)
    - gradle/libs.versions.toml (hilt-work)
    - app/build.gradle.kts (hilt-work deps)
    - app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt (pauseDownload fix)
decisions:
  - D-GAP-01-01: WorkManager replaces CoroutineScope for download execution — survives process death, provides foreground notification, handles retry with backoff
  - D-GAP-01-02: Room-backed checkpoint persistence every ~1MB — balances safety vs DB write overhead
  - D-GAP-01-03: isStopped handling returns Result.success() — Worker exits cleanly, Manager handles re-enqueue on resume
  - D-GAP-01-04: Range header resume — Worker reads checkpoint offset from Room, validates against actual file length
  - D-GAP-01-05: IMPORTANCE_LOW notification channel — downloads run for minutes/hours, non-interruptive progress
  - D-GAP-01-06: LiveData observeForever with explicit cleanup on terminal states — avoids memory leaks in singleton scope
  - D-GAP-01-07: MIGRATION_7_8 added to existing Migrations.kt (not separate file) — follows project convention
  - D-GAP-01-08: HuggingFaceViewModel.pauseDownload() fixed to call Manager.pauseDownload() instead of cancelDownload() — avoid destructive cancel on pause
metrics:
  duration: 576s (GAP-01 + GAP-02 combined)
  tasks: 3
  files: 10
  completed: "2026-05-02T19:03:41Z"
---

# Phase 8 Plan GAP-01: Foreground Notifications + Pause/Resume Summary

**One-liner:** WorkManager-backed download infrastructure with foreground notifications, Room-persisted checkpoints, and resumable downloads that survive process death.

## What Was Built

Replaced the volatile `CoroutineScope(SupervisorJob() + Dispatchers.IO)` download infrastructure with a WorkManager-backed system that:
1. Shows a foreground progress notification during downloads (survives app backgrounding)
2. Persists download checkpoints to Room every ~1MB (survives process death)
3. Supports pause/resume with Range header and checkpoint restoration
4. Preserves the existing `DownloadState` StateFlow API for UI consumers

### Architecture Change

| Aspect | Before (Volatile) | After (WorkManager) |
|--------|------------------|---------------------|
| Download execution | `downloadScope.launch { ... }` | `workManager.enqueue(OneTimeWorkRequest)` |
| Pause | Sets in-memory flag (coroutine continues but skips writes) | Cancels WorkRequest → Worker saves checkpoint on `isStopped` |
| Resume | Clears flag (does nothing — download never restarted) | Reads checkpoint from Room → enqueues new Worker with Range header |
| Cancel | Sets error flag | Cancels Worker + deletes checkpoint + deletes partial file |
| Survives process death | No | Yes (WorkManager + Room checkpoint) |
| Foreground notification | None | `setForeground()` in Worker with progress bar + MB stats |
| Progress tracking | In-memory StateFlow during download | WorkManager `setProgress()` → observed via `getWorkInfoByIdLiveData()` |

### New Components

1. **`ModelDownloadWorker`** — `@HiltWorker` `CoroutineWorker` that:
   - Downloads via OkHttp with `Range` header for resume support
   - Shows foreground notification with progress bar, MB downloaded/total, cancel action
   - Persists checkpoint to Room every ~1MB
   - Handles `isStopped` for pause (saves checkpoint, returns `Result.success()`)
   - On error: saves checkpoint, returns `Result.retry()` (WorkManager exponential backoff)
   - On completion: parses GGUF metadata, saves model to Room, deletes checkpoint

2. **`DownloadCheckpointEntity`** — Room entity (`download_checkpoints` table) storing:
   - `model_id` (PK), `file_name`, `file_url`, `total_bytes`, `downloaded_bytes`

3. **`DownloadCheckpointDao`** — DAO with `getCheckpoint()`, `upsertCheckpoint()`, `deleteCheckpoint()`

4. **`MIGRATION_7_8`** — Creates `download_checkpoints` table (AppDatabase v7→v8)

### Infrastructure Changes

5. **`WarpedApplication`** — Implements `Configuration.Provider` to provide `HiltWorkerFactory`. Creates "Model Downloads" `IMPORTANCE_LOW` notification channel on startup.

6. **`AndroidManifest.xml`** — Declares `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS` permissions. Disables default `WorkManagerInitializer`.

7. **`ModelDownloadManager` refactored** — `startDownload()` enqueues `OneTimeWorkRequest` with network constraint. `pauseDownload()` cancels Worker (saves checkpoint on isStopped). `resumeDownload()` reads checkpoint from Room, enqueues new Worker. `cancelDownload()` cancels Worker, deletes checkpoint and partial file. Progress observed via `WorkManager.getWorkInfoByIdLiveData()` with explicit observer cleanup on terminal states.

### Task Summary

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Infrastructure setup (deps, permissions, HiltWorkerFactory, notification channel) | `b3e828c` | libs.versions.toml, build.gradle.kts, AndroidManifest.xml, WarpedApplication.kt |
| 2 | Checkpoint persistence layer + ModelDownloadWorker | `da5dd08` | DownloadCheckpointEntity.kt, DownloadCheckpointDao.kt, Migrations.kt, AppDatabase.kt, DatabaseModule.kt, ModelDownloadWorker.kt |
| 3 | Refactor ModelDownloadManager to delegate to WorkManager | `7f05598` | ModelDownloadManager.kt, HuggingFaceViewModel.kt |

## Verification

All plan verification checks pass:
- `./gradlew :app:compileDebugKotlin`: BUILD SUCCESSFUL
- `@HiltWorker` in Worker: 1 ✓
- `setForeground` in Worker: 3 ✓ (≥2)
- `WorkManager` in Manager: 3 ✓ (≥3)
- `checkpointDao` in Worker: 7 ✓ (≥1)
- `FOREGROUND_SERVICE` in manifest: 2 ✓ (≥1)
- `POST_NOTIFICATIONS` in manifest: 1 ✓ (≥1)
- `Configuration.Provider` in Application: 1 ✓ (=1)
- `CHANNEL_DOWNLOADS` in Application: 2 ✓ (≥2)
- `download_checkpoints` in migration: 1 ✓ (=1)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] HuggingFaceViewModel.pauseDownload() called cancelDownload() — destructive after refactoring**

- **Found during:** Task 3
- **Issue:** The ViewModel's `pauseDownload()` method called `downloadManager.cancelDownload()`, which after the refactoring deletes the checkpoint and partial file (destructive). Previously, `cancelDownload()` only set in-memory flags so this was harmless — but now it would break pause functionality.
- **Fix:** Changed ViewModel to call `downloadManager.pauseDownload()` instead. This cancels the Worker (triggering checkpoint save on isStopped) without deleting persisted data.
- **Files modified:** `HuggingFaceViewModel.kt`
- **Commit:** `7f05598`

**2. [Rule 3 - Convention] MIGRATION_7_8 added to existing Migrations.kt instead of separate file**

- **Found during:** Task 2
- **Issue:** Plan specified creating `MIGRATION_7_8.kt` as a separate file, but the project convention is to keep all migrations in a single `Migrations.kt` file.
- **Fix:** Added MIGRATION_7_8 to the existing `app/src/main/java/com/warped/data/local/db/Migrations.kt` alongside MIGRATION_4_5, MIGRATION_5_6, and MIGRATION_6_7.
- **Files modified:** `Migrations.kt` (not a new file)
- **Commit:** `da5dd08`

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: information-disclosure | ModelDownloadWorker.kt | Foreground notification shows model file name — public HF model ID, no secrets |
| threat_flag: dos | ModelDownloadManager.kt | Rapid pause/resume cycles handled by WorkManager rate limiting + activeWorkIds dedup |

(These match the threat model dispositions — informational only, no new untracked surface.)

## Known Stubs

None — all implemented functionality is fully operational.

## Self-Check: PASSED

- [x] `DownloadCheckpointEntity.kt` exists
- [x] `DownloadCheckpointDao.kt` exists
- [x] `ModelDownloadWorker.kt` exists
- [x] `Migrations.kt` contains MIGRATION_7_8
- [x] `AppDatabase.kt` has v8 + DownloadCheckpointEntity + DAO
- [x] `DatabaseModule.kt` wires MIGRATION_7_8 + DownloadCheckpointDao
- [x] `ModelDownloadManager.kt` delegates to WorkManager
- [x] `WarpedApplication.kt` implements Configuration.Provider
- [x] `AndroidManifest.xml` has foreground service permissions
- [x] Commit `b3e828c` exists in git log
- [x] Commit `da5dd08` exists in git log
- [x] Commit `7f05598` exists in git log
