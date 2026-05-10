package com.warped.data.local.download

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.warped.MainActivity
import com.warped.WarpedApplication
import com.warped.data.local.db.dao.DownloadCheckpointDao
import com.warped.data.local.db.entity.DownloadCheckpointEntity
import com.warped.data.local.inference.GgufMetadata
import com.warped.data.local.security.ApiKeyStore
import com.warped.domain.model.LocalModel
import com.warped.domain.repository.LocalModelRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.time.Instant

@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val okHttpClient: OkHttpClient,
    private val localModelRepository: LocalModelRepository,
    private val checkpointDao: DownloadCheckpointDao,
    private val apiKeyStore: ApiKeyStore
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_MODEL_ID = "model_id"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_FILE_URL = "file_url"
        const val KEY_FILE_SIZE = "file_size_bytes"
        const val KEY_IS_GATED = "is_gated"
        const val PROGRESS = "progress"
        const val DOWNLOADED_BYTES = "downloaded_bytes"
        const val TOTAL_BYTES = "total_bytes"
        const val SPEED_BYTES_PER_SECOND = "speed_bytes_per_second"
        const val NOTIFICATION_ID_BASE = 1000
        private const val PROGRESS_UPDATE_INTERVAL_MS = 500L
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 2_000L

        fun createInputData(
            modelId: String,
            fileName: String,
            fileUrl: String,
            fileSizeBytes: Long,
            isGated: Boolean = false
        ) = workDataOf(
            KEY_MODEL_ID to modelId,
            KEY_FILE_NAME to fileName,
            KEY_FILE_URL to fileUrl,
            KEY_FILE_SIZE to fileSizeBytes,
            KEY_IS_GATED to isGated
        )
    }

    override suspend fun doWork(): Result {
        val modelId = inputData.getString(KEY_MODEL_ID) ?: return Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: return Result.failure()
        val fileUrl = inputData.getString(KEY_FILE_URL) ?: return Result.failure()
        val fileSizeBytes = inputData.getLong(KEY_FILE_SIZE, 0)
        val isGated = inputData.getBoolean(KEY_IS_GATED, false)
        val checkpoint = checkpointDao.getCheckpoint(modelId)
        val effectiveGated = isGated || (checkpoint?.isGated == true)

        Timber.d("ModelDownloadWorker: starting download — modelId=$modelId isGated=$isGated url=$fileUrl")

        val localFileName = fileName.substringAfterLast("/")
        val modelsDir =
            File(applicationContext.filesDir, "models").also { it.mkdirs() }
        val destFile = File(modelsDir, localFileName)

        // Restore checkpoint — priority: Room > file length > 0
        val resumeOffset = when {
            checkpoint != null && destFile.exists() ->
                destFile.length().coerceAtLeast(checkpoint.downloadedBytes)
            destFile.exists() -> destFile.length()
            else -> 0L
        }

        var foregroundUpdatesAllowed = true

        // Show foreground notification before the HTTP call when Android allows it.
        try {
            setForeground(
                createForegroundInfo(modelId, localFileName, 0, resumeOffset, fileSizeBytes)
            )
        } catch (e: Exception) {
            foregroundUpdatesAllowed = false
            Timber.w(e, "ModelDownloadWorker: foreground notification unavailable; continuing download")
        }

        return try {
            val authUrl = if (effectiveGated && fileUrl.contains("huggingface.co")) {
                val token = apiKeyStore.getHuggingFaceToken()
                if (token != null) {
                    val tokenStr = String(token)
                    token.fill('0')
                    val sep = if (fileUrl.contains("?")) "&" else "?"
                    "$fileUrl${sep}token=$tokenStr"
                } else fileUrl
            } else fileUrl

            val request = Request.Builder()
                .url(authUrl)
                .header("Range", "bytes=$resumeOffset-")
                .build()

            val response = okHttpClient.newCall(request).execute()
            Timber.d("ModelDownloadWorker: HTTP ${response.code} for $fileUrl")
            if (!response.isSuccessful && response.code != 206) {
                val hfErrorBody = if (response.code in 400..403) {
                    try { response.body?.charStream()?.readText()?.take(500) } catch (_: Exception) { null }
                } else null
                Timber.e("ModelDownloadWorker: HF error body — $hfErrorBody")
                val errorMsg = when (response.code) {
                    401 -> "Not authenticated — add your HuggingFace token in Settings → Hugging Face."
                    403 -> {
                        if (!hfErrorBody.isNullOrBlank()) {
                            val modelId = hfErrorBody.substringAfter("model ").substringBefore(" is restricted").ifBlank { null }
                            if (modelId != null) "Access denied — visit huggingface.co/$modelId to accept terms, then retry."
                            else "Access denied — $hfErrorBody"
                        } else {
                            "Access denied — visit the model page on Hugging Face to accept terms."
                        }
                    }
                    404 -> "File not found on HuggingFace."
                    else -> "HTTP ${response.code}: ${response.message}"
                }
                Timber.e("ModelDownloadWorker: download failed — $errorMsg")
                return Result.failure(workDataOf("error" to errorMsg))
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
                    var lastProgressUpdateTime = 0L
                    var lastNotificationUpdateTime = 0L
                    var lastSpeedSampleTime = System.currentTimeMillis()
                    var lastSpeedSampleBytes = totalRead
                    var speedBytesPerSecond = 0L
                    var lastProgress = -1

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        // Check for cancellation (pause/cancel from Manager)
                        if (isStopped) {
                            // Persist checkpoint before exiting
                            checkpointDao.upsertCheckpoint(
                                DownloadCheckpointEntity(
                                    isGated = effectiveGated,
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
                        val now = System.currentTimeMillis()
                        val speedElapsedMs = now - lastSpeedSampleTime
                        if (speedElapsedMs >= PROGRESS_UPDATE_INTERVAL_MS) {
                            speedBytesPerSecond = ((totalRead - lastSpeedSampleBytes) * 1000 / speedElapsedMs)
                                .coerceAtLeast(0L)
                            lastSpeedSampleBytes = totalRead
                            lastSpeedSampleTime = now
                        }

                        if (now - lastProgressUpdateTime >= PROGRESS_UPDATE_INTERVAL_MS || progress != lastProgress) {
                            setProgress(
                                workDataOf(
                                    PROGRESS to progress,
                                    DOWNLOADED_BYTES to totalRead,
                                    TOTAL_BYTES to totalSize,
                                    SPEED_BYTES_PER_SECOND to speedBytesPerSecond
                                )
                            )
                            lastProgress = progress
                            lastProgressUpdateTime = now
                        }

                        if (foregroundUpdatesAllowed && now - lastNotificationUpdateTime >= NOTIFICATION_UPDATE_INTERVAL_MS) {
                            try {
                                val notification = createForegroundInfo(
                                    modelId,
                                    localFileName,
                                    progress,
                                    totalRead,
                                    totalSize,
                                    speedBytesPerSecond
                                )
                                setForeground(notification)
                                lastNotificationUpdateTime = now
                            } catch (e: Exception) {
                                foregroundUpdatesAllowed = false
                                Timber.w(e, "ModelDownloadWorker: disabling foreground notification updates")
                            }
                        }

                        // Persist checkpoint every ~1MB to minimize DB writes
                        if (totalRead - lastCheckpointUpdate > 1_048_576) {
                            checkpointDao.upsertCheckpoint(
                                DownloadCheckpointEntity(
                                    isGated = effectiveGated,
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
                                    isGated = effectiveGated,
                    modelId = modelId,
                    fileName = fileName,
                    fileUrl = fileUrl,
                    totalBytes = totalSize,
                    downloadedBytes = totalSize
                )
            )

            // Parse metadata and save model (LiteRT-LM only)
            val modelMetadata = GgufMetadata()

            val localModel = LocalModel(
                name = localFileName.removeSuffix(".litertlm"),
                filePath = destFile.absolutePath,
                sizeBytes = destFile.length().takeIf { it > 0 } ?: fileSizeBytes,
                quantization = "N/A",
                parameterCount = "Unknown",
                architecture = "LiteRT-LM",
                modelFormat = "LITERTLM",
                importedAt = Instant.now()
            )
            localModelRepository.saveModel(localModel)

            Timber.d("ModelDownloadWorker: download complete — $localFileName (${destFile.length()} bytes)")

            // Delete checkpoint on clean completion
            checkpointDao.deleteCheckpoint(modelId)

            if (foregroundUpdatesAllowed) {
                try {
                    setForeground(
                        createForegroundInfo(modelId, localFileName, 100, totalSize, totalSize)
                    )
                } catch (e: Exception) {
                    Timber.w(e, "ModelDownloadWorker: final notification update failed")
                }
            }

            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "ModelDownloadWorker: download exception — modelId=$modelId")
            if (isStopped) {
                // Cancelled by user (pause) — save checkpoint and don't retry
                checkpointDao.upsertCheckpoint(
                    DownloadCheckpointEntity(
                        isGated = effectiveGated,
                        modelId = modelId,
                        fileName = fileName,
                        fileUrl = fileUrl,
                        totalBytes = fileSizeBytes,
                        downloadedBytes = destFile.length()
                    )
                )
                return Result.success()
            }
            // Genuine error — save checkpoint and retry
            checkpointDao.upsertCheckpoint(
                DownloadCheckpointEntity(
                                    isGated = effectiveGated,
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
        totalBytes: Long,
        speedBytesPerSecond: Long = 0L
    ): ForegroundInfo {
        val cancelIntent = WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(id)

        val tapIntent = Intent(applicationContext, MainActivity::class.java).let {
            PendingIntent.getActivity(
                applicationContext, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(
            applicationContext,
            WarpedApplication.CHANNEL_DOWNLOADS
        )
            .setContentTitle("Downloading model")
            .setContentText(fileName)
            .setStyle(NotificationCompat.BigTextStyle().bigText(downloadStatusText(fileName, progressPercent, downloadedBytes, totalBytes, speedBytesPerSecond)))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progressPercent, totalBytes <= 0)
            .setContentIntent(tapIntent)
            .addAction(android.R.drawable.ic_media_pause, "Cancel", cancelIntent)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                NOTIFICATION_ID_BASE + modelId.hashCode(),
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID_BASE + modelId.hashCode(), notification)
        }
    }

    private fun downloadStatusText(
        fileName: String,
        progressPercent: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSecond: Long
    ): String {
        val amount = if (totalBytes > 0) {
            "${formatFileSize(downloadedBytes)} / ${formatFileSize(totalBytes)} ($progressPercent%)"
        } else {
            "${formatFileSize(downloadedBytes)} downloaded"
        }
        val speed = if (speedBytesPerSecond > 0) " · ${formatFileSize(speedBytesPerSecond)}/s" else ""
        return "$fileName\n$amount$speed"
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024 * 1024 -> "%.2f GB".format(bytes.toDouble() / (1024L * 1024 * 1024))
            bytes >= 1024L * 1024 -> "%.1f MB".format(bytes.toDouble() / (1024L * 1024))
            bytes >= 1024L -> "%.1f KB".format(bytes.toDouble() / 1024L)
            else -> "$bytes B"
        }
    }
}
