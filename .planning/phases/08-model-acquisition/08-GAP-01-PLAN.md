---
phase: 08-model-acquisition
plan: GAP-01
type: execute
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/com/warped/data/local/download/ModelDownloadManager.kt
  - app/src/main/java/com/warped/data/local/download/ModelDownloadWorker.kt
  - app/src/main/java/com/warped/data/local/db/entity/DownloadCheckpointEntity.kt
  - app/src/main/java/com/warped/data/local/db/dao/DownloadCheckpointDao.kt
  - app/src/main/java/com/warped/data/local/db/AppDatabase.kt
  - app/src/main/java/com/warped/data/local/db/MIGRATION_7_8.kt
  - app/src/main/java/com/warped/di/DatabaseModule.kt
  - app/src/main/AndroidManifest.xml
  - app/src/main/java/com/warped/WarpedApplication.kt
  - app/build.gradle.kts
  - gradle/libs.versions.toml
autonomous: true
requirements: [ACQ-08, ACQ-09]
gap_closure: true

must_haves:
  truths:
    - "User sees a foreground progress notification during model downloads, and downloads continue if the app is backgrounded"
    - "User can pause an in-progress download, close the app, return, and resume without data loss"
    - "Download progress is observable in real-time via the existing DownloadState StateFlow (unchanged API for UI consumers)"
  artifacts:
    - path: "app/src/main/java/com/warped/data/local/download/ModelDownloadWorker.kt"
      provides: "WorkManager CoroutineWorker that performs download with foreground notification and checkpoint persistence"
      contains: "@HiltWorker"
    - path: "app/src/main/java/com/warped/data/local/db/entity/DownloadCheckpointEntity.kt"
      provides: "Room entity storing persistent download state (modelId, fileName, fileUrl, totalBytes, downloadedBytes)"
      contains: "@Entity"
    - path: "app/src/main/java/com/warped/data/local/db/dao/DownloadCheckpointDao.kt"
      provides: "DAO for upsert/delete/query download checkpoints"
      contains: "@Dao"
    - path: "app/src/main/java/com/warped/data/local/download/ModelDownloadManager.kt"
      provides: "Refactored download manager that delegates to WorkManager, supports pause/resume/cancel with persistent state"
      contains: "WorkManager"
    - path: "app/src/main/AndroidManifest.xml"
      provides: "FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC, POST_NOTIFICATIONS permissions"
      contains: "FOREGROUND_SERVICE"
  key_links:
    - from: "ModelDownloadManager.startDownload()"
      to: "ModelDownloadWorker (via WorkManager.enqueue)"
      via: "OneTimeWorkRequest with input data"
      pattern: "WorkManager.*enqueue"
    - from: "ModelDownloadManager.resumeDownload()"
      to: "DownloadCheckpointDao.getCheckpoint()"
      via: "reads checkpoint to determine resume offset"
      pattern: "checkpointDao\\.getCheckpoint"
    - from: "ModelDownloadWorker.doWork()"
      to: "DownloadCheckpointDao.upsertCheckpoint()"
      via: "periodic checkpoint writes during download loop"
      pattern: "checkpointDao\\.upsertCheckpoint"
    - from: "ModelDownloadWorker.doWork()"
      to: "Notification (via setForeground)"
      via: "ForegroundInfo with progress notification"
      pattern: "setForeground"
    - from: "ModelDownloadManager"
      to: "DownloadState StateFlow"
      via: "WorkManager WorkInfo observation mapped to DownloadState"
      pattern: "getWorkInfoByIdLiveData"
---

<objective>
Replace the volatile `CoroutineScope`-based download infrastructure with a WorkManager-backed system that survives process death, shows foreground download notifications, and supports persistent pause/resume with checkpoint state.

Purpose: Downloads of multi-GB model files must survive backgrounding and process death. The current `CoroutineScope(SupervisorJob() + Dispatchers.IO)` approach dies on extended backgrounding — any app switch kills the download. This plan builds the Android-standard foreground download infrastructure that Phase 3 was meant to deliver but never built.

Output: `ModelDownloadWorker` (WorkManager CoroutineWorker with foreground notification), `DownloadCheckpointEntity` + DAO (Room-backed persistent state), refactored `ModelDownloadManager` (delegates to WorkManager, pause/resume/cancel with checkpoint support).
</objective>

<execution_context>
@$HOME/.config/opencode/get-shit-done/workflows/execute-plan.md
@$HOME/.config/opencode/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/REQUIREMENTS.md
@.planning/phases/08-model-acquisition/08-CONTEXT.md
@.planning/phases/08-model-acquisition/08-VERIFICATION.md
@.planning/phases/08-model-acquisition/08-03-SUMMARY.md

<interfaces>
<!-- Key types and contracts the executor needs. Extracted from codebase. -->

From `app/src/main/java/com/warped/data/local/download/ModelDownloadManager.kt`:
```kotlin
// Current download entry point (to be refactored — delegate to WorkManager):
fun startDownload(modelId: String, fileName: String, fileUrl: String, fileSizeBytes: Long)
fun pauseDownload(modelId: String)
fun resumeDownload(modelId: String, fileUrl: String)  // Currently hollow — only clears flag, doesn't restart
fun cancelDownload(modelId: String)
fun deleteIncompleteDownload(modelId: String, fileName: String)

// Current state (preserve API — UI consumers depend on this):
val downloadStates: Flow<Map<String, DownloadState>>

data class DownloadState(
    val modelId: String = "",
    val fileName: String = "",
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val isDownloading: Boolean = false,
    val isPaused: Boolean = false,
    val error: String? = null,
    val progress: Float = 0f
)
```

From `app/src/main/java/com/warped/WarpedApplication.kt`:
```kotlin
@HiltAndroidApp
class WarpedApplication : Application() {
    // Does NOT implement Configuration.Provider — needs HiltWorkerFactory
}
```

From `app/src/main/AndroidManifest.xml`:
```xml
<!-- Current permissions: INTERNET, ACCESS_NETWORK_STATE -->
<!-- Only uses-native-library declarations for LiteRT-LM -->
<!-- No foreground service or notification permission declarations -->
```

From `app/src/main/java/com/warped/data/local/db/AppDatabase.kt`:
```kotlin
@Database(entities = [ConversationEntity, MessageEntity, RemoteEndpointEntity, LocalModelEntity, PresetEntity], version = 7)
abstract class AppDatabase : RoomDatabase() {
    abstract fun localModelDao(): LocalModelDao  // Pattern for adding new DAOs
    // ... other DAOs
}
```

From `app/src/main/java/com/warped/di/DatabaseModule.kt`:
```kotlin
// Pattern for providing DAOs:
@Provides fun provideLocalModelDao(db: AppDatabase): LocalModelDao = db.localModelDao()
// Migrations: MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7
// fallbackToDestructiveMigration() configured
```

From `gradle/libs.versions.toml`:
```toml
workmanager = "2.10.0"
work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workmanager" }
hilt = "2.59.2"
hilt-navigation-compose = "1.2.0"
```

From `app/build.gradle.kts` (line 119):
```kotlin
implementation(libs.work.runtime.ktx)  // Already present
```

**Gap context from 08-VERIFICATION.md:**
- Gap 1 (ACQ-08): Zero WorkManager, foreground service, or notification code. Downloads die on process death.
- Gap 2 (ACQ-09): `pauseDownload()` only sets in-memory flag. `resumeDownload()` is hollow (lines 220-222 of ModelDownloadManager.kt). No checkpoint persistence.
- Anti-pattern: `resumeDownload()` only clears `isPaused` flag, doesn't restart download loop.

**Existing code that stays:** The format-detection + metadata extraction block (lines 179-200 of ModelDownloadManager.kt) does NOT change — only the download execution mechanism changes. The `.litertlm`/`.gguf` format detection, GGUF metadata parse skip, and model save logic all move into the Worker verbatim.
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Add hilt-work dependency, AndroidManifest permissions, HiltWorkerFactory, and notification channel</name>
  <files>gradle/libs.versions.toml, app/build.gradle.kts, app/src/main/AndroidManifest.xml, app/src/main/java/com/warped/WarpedApplication.kt</files>
  <action>
Set up the foundational infrastructure required by the download Worker (Task 2) before any download logic is written.

**1. libs.versions.toml — Add hilt-work library entry:**

Add under `[versions]` (keep alphabetical, after `hilt`):
```toml
hilt-work = "1.2.0"
```

Add under `[libraries]` (after `hilt-navigation-compose`):
```toml
hilt-work = { group = "androidx.hilt", name = "hilt-work", version.ref = "hilt-work" }
```

**Note:** `hilt-work` is from `androidx.hilt` (not Dagger). Version `1.2.0` is the latest stable that pairs with `hilt-navigation-compose:1.2.0`. It provides `@HiltWorker` annotation and `HiltWorkerFactory`.

**2. app/build.gradle.kts — Add dependencies:**

After the existing WorkManager dependency (around line 119), add:
```kotlin
implementation(libs.hilt.work)
```
Also add the KSP processor for Hilt extensions (needed for `@HiltWorker` code generation). After `ksp(libs.hilt.compiler)` (find existing line), add:
```kotlin
ksp(libs.hilt.work)
```

Actually, check the existing `dependencies {}` block for the ksp configuration of hilt. The `hilt-work` library needs its own annotation processing. The standard pattern is:
```kotlin
implementation(libs.hilt.work)
ksp(libs.hilt.work)  // Or use kapt if kapt is preferred for Hilt
```

But wait — `hilt-work` doesn't need a separate KSP processor. The `@HiltWorker` annotation is processed by the Hilt compiler which is already configured via `ksp(libs.hilt.compiler)`. The `hilt-work` runtime library just needs `implementation`. So only add:
```kotlin
// Hilt WorkManager integration
implementation(libs.hilt.work)
```

**3. AndroidManifest.xml — Add permissions and service declaration:**

After `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />`, add:
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

Inside `<application>`, after the `</activity>` closing tag, add WorkManager's default initializer disable (HiltWorkerFactory replaces it):
```xml
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false"
    tools:node="merge">
    <meta-data
        android:name="androidx.work.WorkManagerInitializer"
        android:value="androidx.startup"
        tools:node="remove" />
</provider>
```

Also add `xmlns:tools="http://schemas.android.com/tools"` to the `<manifest>` element if not already present.

**4. WarpedApplication.kt — Implement Configuration.Provider + notification channel:**

Replace the class declaration:
```kotlin
@HiltAndroidApp
class WarpedApplication : Application(), Configuration.Provider {
```

Add imports:
```kotlin
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import javax.inject.Inject
```

Add injected field:
```kotlin
@Inject lateinit var workerFactory: HiltWorkerFactory
```

Override `Configuration.Provider`:
```kotlin
override val workManagerConfiguration: Configuration
    get() = Configuration.Builder()
        .setWorkerFactory(workerFactory)
        .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.INFO)
        .build()
```

Create notification channel in `onCreate()` BEFORE the existing debug block:
```kotlin
override fun onCreate() {
    super.onCreate()
    createNotificationChannels()
    if (BuildConfig.DEBUG) {
        // ... existing debug setup
    }
}

private fun createNotificationChannels() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            CHANNEL_DOWNLOADS,
            "Model Downloads",
            NotificationManager.IMPORTANCE_LOW  // Low = no sound, but visible
        ).apply {
            description = "Shows progress of model file downloads"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}

companion object {
    const val CHANNEL_DOWNLOADS = "model_downloads"
}
```

The notification channel uses `IMPORTANCE_LOW` — the download runs for minutes/hours, so interruptive notifications would be annoying. User sees progress in the notification shade without sound/vibration.
</action>
  <verify>
    <automated>./gradlew :app:compileDebugKotlin 2>&1 | tail -20</automated>
  </verify>
  <done>Hilt-work dependency added to version catalog and build.gradle.kts; AndroidManifest declares FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC, POST_NOTIFICATIONS, and removes default WorkManagerInitializer; WarpedApplication implements Configuration.Provider with HiltWorkerFactory and creates "Model Downloads" notification channel with IMPORTANCE_LOW; project compiles</done>
</task>

<task type="auto">
  <name>Task 2: Create DownloadCheckpoint Room entity + DAO + migration, and ModelDownloadWorker</name>
  <files>app/src/main/java/com/warped/data/local/db/entity/DownloadCheckpointEntity.kt, app/src/main/java/com/warped/data/local/db/dao/DownloadCheckpointDao.kt, app/src/main/java/com/warped/data/local/db/MIGRATION_7_8.kt, app/src/main/java/com/warped/data/local/db/AppDatabase.kt, app/src/main/java/com/warped/di/DatabaseModule.kt, app/src/main/java/com/warped/data/local/download/ModelDownloadWorker.kt</files>
  <action>
Build the persistent checkpoint layer and the WorkManager Worker that performs downloads with foreground notifications.

**PART A: DownloadCheckpoint Room persistence**

**1. DownloadCheckpointEntity.kt** — New file at `app/src/main/java/com/warped/data/local/db/entity/DownloadCheckpointEntity.kt`:
```kotlin
package com.warped.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_checkpoints")
data class DownloadCheckpointEntity(
    @PrimaryKey
    @ColumnInfo(name = "model_id")
    val modelId: String,       // e.g., "litert-community/ModelName/file.litertlm"
    @ColumnInfo(name = "file_name")
    val fileName: String,
    @ColumnInfo(name = "file_url")
    val fileUrl: String,
    @ColumnInfo(name = "total_bytes")
    val totalBytes: Long,
    @ColumnInfo(name = "downloaded_bytes")
    val downloadedBytes: Long
)
```

**2. DownloadCheckpointDao.kt** — New file at `app/src/main/java/com/warped/data/local/db/dao/DownloadCheckpointDao.kt`:
```kotlin
package com.warped.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.warped.data.local.db.entity.DownloadCheckpointEntity

@Dao
interface DownloadCheckpointDao {
    @Query("SELECT * FROM download_checkpoints WHERE model_id = :modelId")
    suspend fun getCheckpoint(modelId: String): DownloadCheckpointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCheckpoint(checkpoint: DownloadCheckpointEntity)

    @Query("DELETE FROM download_checkpoints WHERE model_id = :modelId")
    suspend fun deleteCheckpoint(modelId: String)
}
```

**3. MIGRATION_7_8.kt** — New file at `app/src/main/java/com/warped/data/local/db/MIGRATION_7_8.kt`:
```kotlin
package com.warped.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS download_checkpoints (
                model_id TEXT NOT NULL PRIMARY KEY,
                file_name TEXT NOT NULL,
                file_url TEXT NOT NULL,
                total_bytes INTEGER NOT NULL DEFAULT 0,
                downloaded_bytes INTEGER NOT NULL DEFAULT 0
            )
        """)
    }
}
```

**4. AppDatabase.kt** — Add entity + DAO + bump version:

- Add `DownloadCheckpointEntity::class` to the `entities` list
- Bump `version = 7` to `version = 8`
- Add abstract DAO method: `abstract fun downloadCheckpointDao(): DownloadCheckpointDao`

**5. DatabaseModule.kt** — Wire migration and DAO:

- Import `MIGRATION_7_8` and `DownloadCheckpointDao`
- Add `MIGRATION_7_8` to the migrations list: `.addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)`
- Add provider method:
```kotlin
@Provides
fun provideDownloadCheckpointDao(db: AppDatabase): DownloadCheckpointDao = db.downloadCheckpointDao()
```

**PART B: ModelDownloadWorker**

**6. ModelDownloadWorker.kt** — New file at `app/src/main/java/com/warped/data/local/download/ModelDownloadWorker.kt`:

```kotlin
package com.warped.data.local.download

import android.content.Context
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.warped.R
import com.warped.MainActivity
import com.warped.WarpedApplication
import com.warped.data.local.db.dao.DownloadCheckpointDao
import com.warped.data.local.db.entity.DownloadCheckpointEntity
import com.warped.data.local.inference.GgufMetadata
import com.warped.data.local.inference.GgufMetadataParser
import com.warped.domain.model.LocalModel
import com.warped.domain.repository.LocalModelRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.time.Instant

@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val okHttpClient: OkHttpClient,
    private val localModelRepository: LocalModelRepository,
    private val checkpointDao: DownloadCheckpointDao
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_MODEL_ID = "model_id"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_FILE_URL = "file_url"
        const val KEY_FILE_SIZE = "file_size_bytes"
        const val PROGRESS = "progress"
        const val NOTIFICATION_ID_BASE = 1000

        fun createInputData(modelId: String, fileName: String, fileUrl: String, fileSizeBytes: Long) =
            workDataOf(
                KEY_MODEL_ID to modelId,
                KEY_FILE_NAME to fileName,
                KEY_FILE_URL to fileUrl,
                KEY_FILE_SIZE to fileSizeBytes
            )
    }

    // ... implementation of doWork(), createForegroundInfo(), downloadToFile()
    // SEE ACTION BLOCK BELOW for full implementation
}
```

The **full Worker implementation** in `doWork()`:

```kotlin
override suspend fun doWork(): Result {
    val modelId = inputData.getString(KEY_MODEL_ID) ?: return Result.failure()
    val fileName = inputData.getString(KEY_FILE_NAME) ?: return Result.failure()
    val fileUrl = inputData.getString(KEY_FILE_URL) ?: return Result.failure()
    val fileSizeBytes = inputData.getLong(KEY_FILE_SIZE, 0)

    val localFileName = fileName.substringAfterLast("/")
    val modelsDir = File(applicationContext.filesDir, "models").also { it.mkdirs() }
    val destFile = File(modelsDir, localFileName)

    // Restore checkpoint — priority: Room > file length > 0
    val checkpoint = checkpointDao.getCheckpoint(modelId)
    val resumeOffset = when {
        checkpoint != null && destFile.exists() ->
            destFile.length().coerceAtLeast(checkpoint.downloadedBytes)
        destFile.exists() -> destFile.length()
        else -> 0L
    }

    // Show foreground notification BEFORE HTTP call
    setForeground(createForegroundInfo(modelId, localFileName, 0, resumeOffset, fileSizeBytes))

    return try {
        val request = Request.Builder()
            .url(fileUrl)
            .header("Range", "bytes=$resumeOffset-")
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful && response.code != 206) {
            return Result.failure()
        }

        val body = response.body ?: return Result.failure()

        val totalSize = when {
            fileSizeBytes > 0 -> fileSizeBytes
            body.contentLength() > 0 -> body.contentLength() + resumeOffset
            else -> 0L
        }

        val outputFile = if (resumeOffset > 0) {
            RandomAccessFile(destFile, "rw").apply { seek(resumeOffset) }
        } else {
            RandomAccessFile(destFile, "rw")
        }

        body.byteStream().use { input ->
            outputFile.use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = resumeOffset
                var lastCheckpointUpdate = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    // Check for cancellation (pause/cancel from Manager)
                    if (isStopped) {
                        // Persist checkpoint before exiting
                        checkpointDao.upsertCheckpoint(
                            DownloadCheckpointEntity(
                                modelId = modelId,
                                fileName = fileName,
                                fileUrl = fileUrl,
                                totalBytes = totalSize,
                                downloadedBytes = totalRead
                            )
                        )
                        return Result.success() // success = don't retry; pause handled by Manager
                    }

                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead

                    // Update WorkManager progress (for UI observation via WorkInfo)
                    val progress = if (totalSize > 0) {
                        (totalRead * 100 / totalSize).toInt().coerceIn(0, 100)
                    } else 0
                    setProgress(workDataOf(PROGRESS to progress))

                    // Update foreground notification progress periodically
                    val notification = createForegroundInfo(
                        modelId, localFileName, progress, totalRead, totalSize
                    )
                    setForeground(notification)

                    // Persist checkpoint every ~1MB to minimize DB writes
                    if (totalRead - lastCheckpointUpdate > 1_048_576) {
                        checkpointDao.upsertCheckpoint(
                            DownloadCheckpointEntity(
                                modelId = modelId,
                                fileName = fileName,
                                fileUrl = fileUrl,
                                totalBytes = totalSize,
                                downloadedBytes = totalRead
                            )
                        )
                        lastCheckpointUpdate = totalRead
                    }
                }
            }
        }

        // Final checkpoint update (100% complete)
        checkpointDao.upsertCheckpoint(
            DownloadCheckpointEntity(
                modelId = modelId,
                fileName = fileName,
                fileUrl = fileUrl,
                totalBytes = totalSize,
                downloadedBytes = totalSize
            )
        )

        // Parse metadata and save model (same logic as existing ModelDownloadManager lines 179-202)
        val isLitertlm = localFileName.endsWith(".litertlm", ignoreCase = true)
        val modelMetadata = if (!isLitertlm) {
            try {
                GgufMetadataParser.parse(destFile).getOrDefault(GgufMetadata())
            } catch (e: Exception) {
                GgufMetadata()
            }
        } else {
            GgufMetadata()
        }

        val localModel = LocalModel(
            name = localFileName.removeSuffix(".gguf").removeSuffix(".litertlm"),
            filePath = destFile.absolutePath,
            sizeBytes = destFile.length().takeIf { it > 0 } ?: fileSizeBytes,
            quantization = if (isLitertlm) "N/A" else modelMetadata.quantization,
            parameterCount = if (isLitertlm) "Unknown" else modelMetadata.parameterCount,
            architecture = if (isLitertlm) "LiteRT-LM" else modelMetadata.architecture,
            modelFormat = if (isLitertlm) "LITERTLM" else "GGUF",
            importedAt = Instant.now()
        )
        localModelRepository.saveModel(localModel)

        // Delete checkpoint on clean completion
        checkpointDao.deleteCheckpoint(modelId)

        // Final notification — 100% complete, then auto-cancel after 5 seconds
        setForeground(createForegroundInfo(modelId, localFileName, 100, totalSize, totalSize))

        Result.success()
    } catch (e: Exception) {
        // Save checkpoint on error so user can retry/resume
        checkpointDao.upsertCheckpoint(
            DownloadCheckpointEntity(
                modelId = modelId,
                fileName = fileName,
                fileUrl = fileUrl,
                totalBytes = fileSizeBytes,
                downloadedBytes = destFile.length()
            )
        )
        Result.retry()
    }
}

private fun createForegroundInfo(
    modelId: String,
    fileName: String,
    progressPercent: Int,
    downloadedBytes: Long,
    totalBytes: Long
): ForegroundInfo {
    val cancelIntent = WorkManager.getInstance(applicationContext)
        .createCancelPendingIntent(id)

    val tapIntent = Intent(applicationContext, MainActivity::class.java).let {
        PendingIntent.getActivity(
            applicationContext, 0, it,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    val notification = NotificationCompat.Builder(applicationContext, WarpedApplication.CHANNEL_DOWNLOADS)
        .setContentTitle("Downloading $fileName")
        .setContentText(if (totalBytes > 0) {
            "${downloadedBytes / (1024 * 1024)} MB / ${totalBytes / (1024 * 1024)} MB"
        } else {
            "${downloadedBytes / (1024 * 1024)} MB downloaded"
        })
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setOngoing(true)
        .setProgress(100, progressPercent, totalBytes <= 0)
        .setContentIntent(tapIntent)
        .addAction(android.R.drawable.ic_media_pause, "Cancel", cancelIntent)
        .build()

    return ForegroundInfo(NOTIFICATION_ID_BASE + modelId.hashCode(), notification)
}
```

**Design decisions:**
- **Checkpoint interval:** Every ~1MB (1_048_576 bytes) — balances persistence safety vs DB write overhead. On crash, user loses at most 1MB of progress.
- **isStopped handling:** When WorkManager cancels the Worker (pause/cancel from Manager), `isStopped` becomes true. The download loop detects this, saves a final checkpoint, and returns `Result.success()` (so WorkManager doesn't retry — the Manager handles re-enqueue on resume).
- **Error handling:** On exceptions, checkpoint is saved and `Result.retry()` is returned — WorkManager's default exponential backoff retries the download.
- **Foreground notification:** Shows model name, progress bar, and MB downloaded/total. Cancel action calls WorkManager's `createCancelPendingIntent()`. Tap opens MainActivity.
- **Notification ID:** `1000 + modelId.hashCode()` ensures unique per-download notifications are updated (not duplicated).
- **Metadata parsing:** Copied verbatim from existing ModelDownloadManager (lines 179-202 from 08-03-SUMMARY). No logic changes — format detection, GGUF parse skip, and LocalModel construction are identical.
</action>
  <verify>
    <automated>./gradlew :app:compileDebugKotlin 2>&1 | tail -20</automated>
  </verify>
  <done>DownloadCheckpointEntity + DAO persist download state in Room; MIGRATION_7_8 creates download_checkpoints table; AppDatabase bumped to version 8 with new entity+DAO; DatabaseModule wires migration+DAO; ModelDownloadWorker performs downloads with foreground notification, checkpoint persistence every ~1MB, isStopped handling for pause/cancel, and metadata-driven model save on completion; project compiles</done>
</task>

<task type="auto">
  <name>Task 3: Refactor ModelDownloadManager to delegate to WorkManager + add pause/resume with checkpoint support</name>
  <files>app/src/main/java/com/warped/data/local/download/ModelDownloadManager.kt</files>
  <action>
Refactor `ModelDownloadManager` to replace the volatile `CoroutineScope`-based download execution with WorkManager delegation. The `DownloadState` StateFlow API (consumed by HuggingFaceViewModel, etc.) is preserved — only the internal execution mechanism changes.

**Key refactoring:**

1. **Replace `downloadScope` with WorkManager injection:**
   - Remove: `private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)`
   - Add: `@Inject constructor(..., private val workManager: WorkManager, private val checkpointDao: DownloadCheckpointDao)`
   - Import: `androidx.work.WorkManager`, `androidx.work.OneTimeWorkRequestBuilder`, `androidx.work.WorkInfo`

2. **Rewrite `startDownload()` — enqueue WorkRequest instead of launching coroutine:**

```kotlin
fun startDownload(
    modelId: String,
    fileName: String,
    fileUrl: String,
    fileSizeBytes: Long
) {
    updateState(modelId) {
        it.copy(
            modelId = modelId,
            fileName = fileName,
            totalBytes = fileSizeBytes,
            isDownloading = true,
            isPaused = false,
            error = null,
            progress = 0f
        )
    }

    // Storage check
    if (fileSizeBytes > 0 && !hasEnoughStorage(fileSizeBytes)) {
        updateState(modelId) {
            it.copy(
                isDownloading = false,
                error = "Not enough storage. Need ${fileSizeBytes / (1024 * 1024)} MB"
            )
        }
        return
    }

    // Clear any stale checkpoint from a previous completed download
    // (don't clear if this is a resume — handled by resumeDownload())
    // Actually, startDownload always starts fresh. Clear any existing checkpoint.
    downloadScope.launch {
        checkpointDao.deleteCheckpoint(modelId)
    }

    val inputData = ModelDownloadWorker.createInputData(modelId, fileName, fileUrl, fileSizeBytes)
    val workRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
        .setInputData(inputData)
        .addTag(modelId)  // Tag for lookup
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .build()

    workManager.enqueue(workRequest)

    // Store work request ID for later observation
    activeWorkIds[modelId] = workRequest.id

    // Observe progress from WorkManager
    observeWorkProgress(modelId, workRequest.id)
}
```

3. **Rewrite `pauseDownload()` — cancel Worker + persist checkpoint:**

```kotlin
fun pauseDownload(modelId: String) {
    val workId = activeWorkIds[modelId]
    if (workId != null) {
        // Cancel the WorkRequest — this sets isStopped=true in the Worker,
        // which triggers the checkpoint-save-and-exit path in doWork()'s isStopped check.
        workManager.cancelWorkById(workId)
    }
    updateState(modelId) {
        it.copy(isPaused = true, isDownloading = false)
    }
    // Checkpoint is already saved by the Worker's isStopped handler.
    // activeWorkIds entry is removed by observeWorkProgress when WorkInfo transitions to CANCELLED.
}
```

4. **Rewrite `resumeDownload()` — read checkpoint + enqueue new Worker:**

```kotlin
fun resumeDownload(modelId: String, fileUrl: String) {
    downloadScope.launch {
        val checkpoint = checkpointDao.getCheckpoint(modelId)
        if (checkpoint == null) {
            updateState(modelId) {
                it.copy(error = "Cannot resume — no saved progress found", isPaused = false)
            }
            return@launch
        }

        updateState(modelId) {
            it.copy(
                isPaused = false,
                isDownloading = true,
                totalBytes = checkpoint.totalBytes,
                downloadedBytes = checkpoint.downloadedBytes,
                progress = if (checkpoint.totalBytes > 0)
                    checkpoint.downloadedBytes.toFloat() / checkpoint.totalBytes.toFloat()
                else 0f
            )
        }

        // Enqueue new Worker with same modelId — Worker reads checkpoint from Room
        // to determine resume offset. The Worker's doWork() already handles this.
        val inputData = ModelDownloadWorker.createInputData(
            modelId = modelId,
            fileName = checkpoint.fileName,
            fileUrl = checkpoint.fileUrl,
            fileSizeBytes = checkpoint.totalBytes
        )
        val workRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(inputData)
            .addTag(modelId)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        workManager.enqueue(workRequest)
        activeWorkIds[modelId] = workRequest.id
        observeWorkProgress(modelId, workRequest.id)
    }
}
```

**Note:** The `resumeDownload()` signature currently takes `modelId: String, fileUrl: String`. The `fileUrl` parameter is now ignored — the Worker reads the URL from the checkpoint. But we keep the signature for backward compatibility. Callers can pass any value.

5. **Rewrite `cancelDownload()` — cancel Worker + delete checkpoint + delete partial file:**

```kotlin
fun cancelDownload(modelId: String) {
    val workId = activeWorkIds[modelId]
    if (workId != null) {
        workManager.cancelWorkById(workId)
    }
    val state = _downloadStates.value[modelId]
    val fileName = state?.fileName ?: ""
    downloadScope.launch {
        checkpointDao.deleteCheckpoint(modelId)
        if (fileName.isNotBlank()) {
            val localName = fileName.substringAfterLast("/")
            val file = File(modelsDir, localName)
            if (file.exists()) file.delete()
        }
    }
    updateState(modelId) {
        it.copy(isDownloading = false, isPaused = false, error = "Cancelled")
    }
}
```

Wait — `cancelDownload()` currently sets `isPaused = true` and `error = "Cancelled"`. But the verify action says `pauseDownload()` calls `cancelDownload()` (an anti-pattern). Keep cancel behavior as-is (sets error) but fix the anti-pattern separately: the `pauseDownload()` method above uses WorkManager cancellation directly, not `cancelDownload()`.

6. **Add WorkManager progress observation:**

```kotlin
// Map to store active work IDs keyed by modelId
private val activeWorkIds = mutableMapOf<String, UUID>()

private fun observeWorkProgress(modelId: String, workId: UUID) {
    workManager.getWorkInfoByIdLiveData(workId).observeForever { workInfo ->
        if (workInfo == null) return@observeForever

        when (workInfo.state) {
            WorkInfo.State.RUNNING -> {
                val progress = workInfo.progress.getInt(ModelDownloadWorker.PROGRESS, 0)
                updateState(modelId) {
                    it.copy(
                        isDownloading = true,
                        isPaused = false,
                        progress = progress / 100f
                    )
                }
            }
            WorkInfo.State.SUCCEEDED -> {
                updateState(modelId) {
                    it.copy(
                        isDownloading = false,
                        isPaused = false,
                        progress = 1f,
                        downloadedBytes = it.totalBytes
                    )
                }
                activeWorkIds.remove(modelId)
            }
            WorkInfo.State.FAILED -> {
                updateState(modelId) {
                    it.copy(
                        isDownloading = false,
                        error = "Download failed"
                    )
                }
                activeWorkIds.remove(modelId)
            }
            WorkInfo.State.CANCELLED -> {
                // Cancelled = paused (checkpoint saved by Worker)
                // Don't set error — pause is not an error state
                activeWorkIds.remove(modelId)
            }
            else -> { /* BLOCKED, ENQUEUED — no action */ }
        }
    }
}
```

**IMPORTANT:** `observeForever` on LiveData requires removal to avoid leaks. Since `ModelDownloadManager` is `@Singleton`, it lives for the app's lifetime, so `observeForever` is acceptable. But to be safe, add a `removeObserver` call when a download completes/fails/cancels. Store observer references in a map and remove them:

```kotlin
private val activeObservers = mutableMapOf<String, Observer<WorkInfo>>()

// In observeWorkProgress: store observer, remove on terminal states
```

Actually, for simplicity with a singleton scope, use `observeForever` and accept the minimal leak. If a download completes, the observer stays registered but the LiveData won't emit for a removed WorkInfo. To be properly clean, use a Flow-based approach:

Simpler alternative — keep `observeForever` but remove on non-RUNNING states:
```kotlin
workManager.getWorkInfoByIdLiveData(workId).observeForever { workInfo ->
    // ... state handling ...
    if (workInfo.state.isFinished) {
        workManager.getWorkInfoByIdLiveData(workId).removeObserver { }
    }
}
```

Actually, the cleanest approach: store the observer lambda and remove it explicitly:

```kotlin
private val workObservers = mutableMapOf<UUID, Observer<WorkInfo>>()

private fun observeWorkProgress(modelId: String, workId: UUID) {
    val observer = Observer<WorkInfo> { workInfo ->
        // ... state handling as above ...
        if (workInfo.state.isFinished) {
            workManager.getWorkInfoByIdLiveData(workId).removeObserver(this)
            workObservers.remove(workId)
        }
    }
    workObservers[workId] = observer
    workManager.getWorkInfoByIdLiveData(workId).observeForever(observer)
}
```

Wait, `removeObserver(this)` won't work in a lambda context. Use:
```kotlin
if (workInfo.state.isFinished) {
    workObservers[workId]?.let { workManager.getWorkInfoByIdLiveData(workId).removeObserver(it) }
    workObservers.remove(workId)
}
```

**7. Keep `downloadScope` for lightweight async (checkpoint CRUD) only:**

The `CoroutineScope` is retained but ONLY for lightweight non-download operations (checkpoint delete in cancel, checkpoint read in resume). The download itself runs entirely in the Worker. Rename for clarity:
```kotlin
private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
```

**8. Update imports** — Remove unused imports (OkHttpClient if no longer directly used), add new imports:
```kotlin
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.lifecycle.Observer
import com.warped.data.local.db.dao.DownloadCheckpointDao
import com.warped.data.local.db.entity.DownloadCheckpointEntity
import java.util.UUID
```

**9. The `deleteIncompleteDownload()` method stays as-is** — it operates on the file system, not the download execution.

**10. `hasEnoughStorage()` stays as-is** — it's a synchronous check that doesn't need WorkManager.

**Summary of behavior changes:**
| Operation | Before (Volatile) | After (WorkManager) |
|-----------|-------------------|---------------------|
| Start download | `downloadScope.launch { ... }` | `workManager.enqueue(OneTimeWorkRequest)` |
| Pause | Sets in-memory flag | Cancels WorkRequest → Worker saves checkpoint on `isStopped` |
| Resume | Clears flag (does nothing) | Reads checkpoint from Room → enqueues new Worker |
| Cancel | Sets error flag | Cancels WorkRequest + deletes checkpoint + deletes partial file |
| Survives process death | No | Yes (WorkManager + Room checkpoint) |
| Foreground notification | None | `setForeground()` in Worker |
| Progress tracking | In-memory StateFlow during download | WorkManager `setProgress()` → observed via `getWorkInfoByIdLiveData()` |

The HuggingFaceViewModel's `downloadStates` collector (lines 34-53) continues to work because `_downloadStates` StateFlow is still updated — now from `observeWorkProgress` instead of from the coroutine's progress loop.
</action>
  <verify>
    <automated>./gradlew :app:compileDebugKotlin 2>&1 | tail -20</automated>
  </verify>
  <done>ModelDownloadManager.startDownload() enqueues WorkManager requests instead of launching coroutine downloads; pauseDownload() cancels Worker (triggering checkpoint save in Worker's isStopped handler); resumeDownload() reads checkpoint from Room and enqueues new Worker; cancelDownload() cancels Worker, deletes checkpoint, and deletes partial file; DownloadState StateFlow updates from WorkManager progress observation; existing format-detection and metadata-parsing logic preserved in Worker; project compiles without errors</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| App → Android System | Foreground Service lifecycle. WorkManager calls `startForeground()` which the system may kill on low memory. Handled by WorkManager's retry mechanism. |
| Worker → Room DB | Checkpoint data written during download loop. Room provides thread-safe writes. Data is transient (download progress) — corruption only affects resume offset, not model integrity. |
| Worker → File System | Model binary written to `filesDir/models/`. Android sandbox protects this directory. Partial files on crash are recoverable via Range header resume. |
| Notification → User | Foreground notification visible in shade. Shows progress and model name — no secrets exposed. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-GAP-01-01 | Spoofing | ModelDownloadWorker — checkpoint resume offset | mitigate | Resume offset is validated against actual file length (`destFile.length().coerceAtLeast(checkpoint.downloadedBytes)`). If checkpoint is corrupted (offset > file length), uses file length. If file is deleted, starts from 0. |
| T-GAP-01-02 | Tampering | DownloadCheckpointEntity — Room data | accept | Checkpoint is stored in Room (app-private SQLite). Android sandbox prevents other apps from modifying. User has no direct filesystem access to the database. |
| T-GAP-01-03 | Information Disclosure | Foreground notification — model name | accept | Notification shows model file name (public information — Hugging Face model IDs). No API keys, endpoints, or user data exposed. |
| T-GAP-01-04 | Denial of Service | WorkManager — rapid pause/resume cycles | mitigate | WorkManager has built-in rate limiting (minimum retry backoff 10 seconds). Rapid enqueue/dequeue cycles are queued internally. `activeWorkIds` map prevents duplicate Workers for the same modelId. |
| T-GAP-01-05 | Elevation of Privilege | Notification cancel action (PendingIntent) | mitigate | Cancel PendingIntent created via `WorkManager.createCancelPendingIntent(id)` — scoped to this specific Worker, cannot cancel other Workers or perform arbitrary actions. |
</threat_model>

<verification>
- `./gradlew :app:compileDebugKotlin` exits 0
- `grep -c "@HiltWorker" app/src/main/java/com/warped/data/local/download/ModelDownloadWorker.kt` returns 1
- `grep -c "setForeground" app/src/main/java/com/warped/data/local/download/ModelDownloadWorker.kt` returns >= 2 (start + progress updates)
- `grep -c "WorkManager" app/src/main/java/com/warped/data/local/download/ModelDownloadManager.kt` returns >= 3 (enqueue, cancel, observe)
- `grep -c "checkpointDao" app/src/main/java/com/warped/data/local/download/ModelDownloadWorker.kt` returns >= 1
- `grep -c "FOREGROUND_SERVICE" app/src/main/AndroidManifest.xml` returns >= 1
- `grep -c "POST_NOTIFICATIONS" app/src/main/AndroidManifest.xml` returns >= 1
- `grep -c "Configuration.Provider" app/src/main/java/com/warped/WarpedApplication.kt` returns 1
- `grep -c "CHANNEL_DOWNLOADS" app/src/main/java/com/warped/WarpedApplication.kt` returns >= 2 (companion object + createNotificationChannels)
- `grep -c "download_checkpoints" app/src/main/java/com/warped/data/local/db/MIGRATION_7_8.kt` returns 1
</verification>

<success_criteria>
1. `ModelDownloadWorker` is a `@HiltWorker` `CoroutineWorker` that performs downloads with `setForeground()` notification, persists checkpoint to Room every ~1MB, handles `isStopped` for pause, and saves model metadata on completion
2. `DownloadCheckpointEntity` + `DownloadCheckpointDao` provide persistent download state in Room (survives process death)
3. `ModelDownloadManager.startDownload()` enqueues a WorkManager `OneTimeWorkRequest` instead of launching a coroutine
4. `ModelDownloadManager.pauseDownload()` cancels the Worker — the Worker's `isStopped` handler saves checkpoint before exiting
5. `ModelDownloadManager.resumeDownload()` reads checkpoint from Room, enqueues a new Worker that resumes from the saved offset
6. `ModelDownloadManager.cancelDownload()` cancels the Worker, deletes checkpoint, and deletes the partial file
7. AndroidManifest declares `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, and `POST_NOTIFICATIONS`
8. `WarpedApplication` implements `Configuration.Provider` with `HiltWorkerFactory` and creates the "Model Downloads" notification channel
9. Existing `DownloadState` StateFlow API preserved — UI observers (HuggingFaceViewModel) see progress without changes
10. Project compiles successfully with `./gradlew :app:compileDebugKotlin`
</success_criteria>

<output>
After completion, create `.planning/phases/08-model-acquisition/08-GAP-01-SUMMARY.md`
</output>
