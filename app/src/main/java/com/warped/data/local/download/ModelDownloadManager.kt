package com.warped.data.local.download

import android.content.Context
import android.os.Build
import android.os.StatFs
import androidx.lifecycle.Observer
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.warped.R
import com.warped.data.local.db.dao.DownloadCheckpointDao
import com.warped.data.local.db.entity.DownloadCheckpointEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadState(
    val modelId: String = "",
    val fileName: String = "",
    val fileUrl: String = "",
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val speedBytesPerSecond: Long = 0,
    val isDownloading: Boolean = false,
    val isPaused: Boolean = false,
    val error: String? = null,
    val progress: Float = 0f,
    /**
     * Phase 60-02 (API-04): mapped [WorkInfo.getStopReason] copy surfaced in
     * the progress/retry UI. Set when a platform stop is observed, cleared
     * when a fresh download starts, resumes, or reaches RUNNING again.
     */
    val stopReasonCopy: String? = null
)

@Singleton
class ModelDownloadManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val workManager: WorkManager,
    private val checkpointDao: DownloadCheckpointDao
) {
    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: Flow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val modelsDir: File
        get() = File(context.filesDir, "models").also { it.mkdirs() }

    // Map to store active work IDs keyed by modelId
    private val activeWorkIds = mutableMapOf<String, UUID>()

    // Map to store LiveData observers for cleanup
    private val workObservers = mutableMapOf<UUID, Observer<WorkInfo?>>()

    fun hasEnoughStorage(requiredBytes: Long): Boolean {
        val stat = StatFs(context.filesDir.absolutePath)
        val availableBytes = stat.availableBytes
        return availableBytes >= requiredBytes * 1.1
    }

    fun startDownload(
        modelId: String,
        fileName: String,
        fileUrl: String,
        fileSizeBytes: Long,
        isGated: Boolean = false
    ) {
        updateState(modelId) {
            it.copy(
                modelId = modelId,
                fileName = fileName,
                fileUrl = fileUrl,
                totalBytes = fileSizeBytes,
                downloadedBytes = 0,
                speedBytesPerSecond = 0,
                isDownloading = true,
                isPaused = false,
                error = null,
                stopReasonCopy = null,
                progress = 0f
            )
        }

        // Storage check
        if (fileSizeBytes > 0 && !hasEnoughStorage(fileSizeBytes)) {
            updateState(modelId) {
                it.copy(
                    isDownloading = false,
                    error = context.getString(R.string.dl_err_storage_fmt, fileSizeBytes / (1024 * 1024))
                )
            }
            return
        }

        // Clear any stale checkpoint from a previous completed download
        ioScope.launch {
            checkpointDao.deleteCheckpoint(modelId)
        }

        val inputData = ModelDownloadWorker.createInputData(
            modelId, fileName, fileUrl, fileSizeBytes, isGated
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
        Timber.d("ModelDownloadManager: download enqueued — modelId=$modelId workId=${workRequest.id}")

        // Store work request ID for later observation
        activeWorkIds[modelId] = workRequest.id

        // Observe progress from WorkManager
        observeWorkProgress(modelId, workRequest.id)
    }

    fun pauseDownload(modelId: String) {
        val workId = activeWorkIds[modelId]
        val state = _downloadStates.value[modelId]
        if (workId != null) {
            workManager.cancelWorkById(workId)
            cleanupObserver(workId)
            activeWorkIds.remove(modelId)
        }
        if (state != null && state.fileName.isNotBlank() && state.fileUrl.isNotBlank()) {
            ioScope.launch {
                checkpointDao.upsertCheckpoint(
                    DownloadCheckpointEntity(
                        modelId = modelId,
                        fileName = state.fileName,
                        fileUrl = state.fileUrl,
                        totalBytes = state.totalBytes,
                        downloadedBytes = state.downloadedBytes
                    )
                )
            }
        }
        updateState(modelId) {
            it.copy(isPaused = true, isDownloading = false)
        }
    }

    fun resumeDownload(modelId: String) {
        val oldWorkId = activeWorkIds.remove(modelId)
        if (oldWorkId != null) {
            workManager.cancelWorkById(oldWorkId)
            cleanupObserver(oldWorkId)
        }
        ioScope.launch {
            // Wait for any previous worker to fully stop before starting new one
            if (oldWorkId != null) {
                try {
                    var attempts = 0
                    while (attempts < 10) {
                        val info = workManager.getWorkInfoById(oldWorkId).get()
                        if (info == null || info.state.isFinished) break
                        kotlinx.coroutines.delay(200)
                        attempts++
                    }
                } catch (e: Exception) { Timber.e(e, "ModelDownload: checkpoint wait failed") }
            }
            val checkpoint = checkpointDao.getCheckpoint(modelId)
            if (checkpoint == null) {
                updateState(modelId) {
                    it.copy(
                        error = context.getString(R.string.dl_err_no_resume),
                        isPaused = false
                    )
                }
                return@launch
            }

            updateState(modelId) {
                it.copy(
                    isPaused = false,
                    isDownloading = true,
                    fileName = checkpoint.fileName,
                    fileUrl = checkpoint.fileUrl,
                    totalBytes = checkpoint.totalBytes,
                    downloadedBytes = checkpoint.downloadedBytes,
                    speedBytesPerSecond = 0,
                    stopReasonCopy = null,
                    progress = if (checkpoint.totalBytes > 0)
                        checkpoint.downloadedBytes.toFloat() / checkpoint.totalBytes.toFloat()
                    else 0f
                )
            }

            // Enqueue new Worker — Worker reads checkpoint from Room to determine resume offset
            val inputData = ModelDownloadWorker.createInputData(
                modelId = modelId,
                fileName = checkpoint.fileName,
                fileUrl = checkpoint.fileUrl,
                fileSizeBytes = checkpoint.totalBytes,
                isGated = checkpoint.isGated
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

    fun cancelDownload(modelId: String) {
        val workId = activeWorkIds[modelId]
        if (workId != null) {
            workManager.cancelWorkById(workId)
        }
        val state = _downloadStates.value[modelId]
        val fileName = state?.fileName ?: ""
        ioScope.launch {
            checkpointDao.deleteCheckpoint(modelId)
            if (fileName.isNotBlank()) {
                val localName = fileName.substringAfterLast("/")
                val file = File(modelsDir, localName)
                if (file.exists()) file.delete()
            }
        }
        updateState(modelId) {
            it.copy(isDownloading = false, isPaused = true, error = "Cancelled")
        }
    }

    fun deleteIncompleteDownload(modelId: String, fileName: String) {
        val localName = fileName.substringAfterLast("/")
        val file = File(modelsDir, localName)
        if (file.exists()) file.delete()
        _downloadStates.update { it - modelId }
    }

    fun getDownloadState(modelId: String): DownloadState {
        return _downloadStates.value[modelId] ?: DownloadState(modelId = modelId)
    }

    private fun observeWorkProgress(modelId: String, workId: UUID) {
        val observer = Observer<WorkInfo?> { workInfo ->
            if (workInfo == null) return@Observer

            when (workInfo.state) {
                WorkInfo.State.RUNNING -> {
                    val progress = workInfo.progress.getInt(ModelDownloadWorker.PROGRESS, 0)
                    val downloadedBytes = workInfo.progress.getLong(ModelDownloadWorker.DOWNLOADED_BYTES, 0L)
                    val totalBytes = workInfo.progress.getLong(ModelDownloadWorker.TOTAL_BYTES, 0L)
                    val speedBytesPerSecond = workInfo.progress.getLong(
                        ModelDownloadWorker.SPEED_BYTES_PER_SECOND,
                        0L
                    )
                    updateState(modelId) {
                        it.copy(
                            isDownloading = true,
                            isPaused = false,
                            progress = progress / 100f,
                            downloadedBytes = downloadedBytes.takeIf { bytes -> bytes > 0 } ?: it.downloadedBytes,
                            totalBytes = totalBytes.takeIf { bytes -> bytes > 0 } ?: it.totalBytes,
                            speedBytesPerSecond = speedBytesPerSecond,
                            // Retry landed — a previous stop copy is stale.
                            stopReasonCopy = null
                        )
                    }
                }
                WorkInfo.State.SUCCEEDED -> {
                    updateState(modelId) {
                        it.copy(
                            isDownloading = false,
                            isPaused = false,
                            progress = 1f,
                            downloadedBytes = it.totalBytes,
                            speedBytesPerSecond = 0
                        )
                    }
                    activeWorkIds.remove(modelId)
                    cleanupObserver(workId)
                }
                WorkInfo.State.FAILED -> {
                    val errorMsg = workInfo.outputData.getString("error") ?: "Download failed"
                    val reason = readStopReason(workInfo)
                    if (reason != WorkInfo.STOP_REASON_NOT_STOPPED) {
                        // T-60-03: platform int only — never tokens, URLs, or headers.
                        Timber.d("ModelDownload: work failed after stop — modelId=%s stopReason=%d", modelId, reason)
                    }
                    updateState(modelId) {
                        it.copy(
                            isDownloading = false,
                            speedBytesPerSecond = 0,
                            error = errorMsg,
                            stopReasonCopy = if (reason != WorkInfo.STOP_REASON_NOT_STOPPED) {
                                DownloadStopReason.stopReasonCopy(reason)
                            } else {
                                it.stopReasonCopy
                            }
                        )
                    }
                    activeWorkIds.remove(modelId)
                    cleanupObserver(workId)
                }
                WorkInfo.State.CANCELLED -> {
                    // Phase 60-02 (API-04): workers cannot read their own
                    // stop reason, so it is observed here UI-side. User pause
                    // removes this observer up-front (clean pause UI, no
                    // copy); a surviving CANCELLED is a genuine platform stop
                    // (quota, constraints, user cancel) with Copy to surface.
                    val reason = readStopReason(workInfo)
                    if (reason != WorkInfo.STOP_REASON_NOT_STOPPED) {
                        // T-60-03: platform int only — never tokens, URLs, or headers.
                        Timber.d("ModelDownload: work stopped — modelId=%s stopReason=%d", modelId, reason)
                    }
                    updateState(modelId) {
                        it.copy(
                            isDownloading = false,
                            isPaused = true,
                            speedBytesPerSecond = 0,
                            stopReasonCopy = if (reason != WorkInfo.STOP_REASON_NOT_STOPPED) {
                                DownloadStopReason.stopReasonCopy(reason)
                            } else {
                                it.stopReasonCopy
                            }
                        )
                    }
                    activeWorkIds.remove(modelId)
                    cleanupObserver(workId)
                }
                else -> { /* BLOCKED, ENQUEUED — no action */ }
            }
        }
        workObservers[workId] = observer
        workManager.getWorkInfoByIdLiveData(workId).observeForever(observer)
    }

    private fun cleanupObserver(workId: UUID) {
        workObservers.remove(workId)?.let { observer ->
            workManager.getWorkInfoByIdLiveData(workId).removeObserver(observer)
        }
    }

    /**
     * Phase 60-02 (API-04): `WorkInfo.getStopReason()` is `@RequiresApi(31)`.
     * Below S the platform never reports a stop, so return NOT_STOPPED.
     * Reading the property performs no framework call — the value is
     * materialized by WorkManager itself — so the guarded read is safe.
     */
    private fun readStopReason(workInfo: WorkInfo): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            workInfo.stopReason
        } else {
            WorkInfo.STOP_REASON_NOT_STOPPED
        }

    private fun updateState(key: String, transform: (DownloadState) -> DownloadState) {
        _downloadStates.update { map ->
            map.toMutableMap().also { mutable ->
                mutable[key] = transform(mutable[key] ?: DownloadState(modelId = key))
            }
        }
    }
}
