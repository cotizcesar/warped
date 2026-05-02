package com.warped.data.local.download

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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

        fun createInputData(
            modelId: String,
            fileName: String,
            fileUrl: String,
            fileSizeBytes: Long
        ) = workDataOf(
            KEY_MODEL_ID to modelId,
            KEY_FILE_NAME to fileName,
            KEY_FILE_URL to fileUrl,
            KEY_FILE_SIZE to fileSizeBytes
        )
    }

    override suspend fun doWork(): Result {
        val modelId = inputData.getString(KEY_MODEL_ID) ?: return Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: return Result.failure()
        val fileUrl = inputData.getString(KEY_FILE_URL) ?: return Result.failure()
        val fileSizeBytes = inputData.getLong(KEY_FILE_SIZE, 0)

        val localFileName = fileName.substringAfterLast("/")
        val modelsDir =
            File(applicationContext.filesDir, "models").also { it.mkdirs() }
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
        setForeground(
            createForegroundInfo(modelId, localFileName, 0, resumeOffset, fileSizeBytes)
        )

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

            // Parse metadata and save model (same logic as existing ModelDownloadManager)
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

            // Final notification — 100% complete
            setForeground(
                createForegroundInfo(modelId, localFileName, 100, totalSize, totalSize)
            )

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

        val notification = NotificationCompat.Builder(
            applicationContext,
            WarpedApplication.CHANNEL_DOWNLOADS
        )
            .setContentTitle("Downloading $fileName")
            .setContentText(
                if (totalBytes > 0) {
                    "${downloadedBytes / (1024 * 1024)} MB / ${totalBytes / (1024 * 1024)} MB"
                } else {
                    "${downloadedBytes / (1024 * 1024)} MB downloaded"
                }
            )
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, progressPercent, totalBytes <= 0)
            .setContentIntent(tapIntent)
            .addAction(android.R.drawable.ic_media_pause, "Cancel", cancelIntent)
            .build()

        return ForegroundInfo(NOTIFICATION_ID_BASE + modelId.hashCode(), notification)
    }
}
