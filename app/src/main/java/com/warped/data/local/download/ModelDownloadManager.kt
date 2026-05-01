package com.warped.data.local.download

import android.content.Context
import android.os.StatFs
import com.warped.data.local.inference.GgufMetadata
import com.warped.data.local.inference.GgufMetadataParser
import com.warped.domain.model.LocalModel
import com.warped.domain.repository.LocalModelRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

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

@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val localModelRepository: LocalModelRepository
) {
    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: Flow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val modelsDir: File
        get() = File(context.filesDir, "models").also { it.mkdirs() }

    fun hasEnoughStorage(requiredBytes: Long): Boolean {
        val stat = StatFs(context.filesDir.absolutePath)
        val availableBytes = stat.availableBytes
        return availableBytes >= requiredBytes * 1.1
    }

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

        downloadScope.launch {
            try {
                if (fileSizeBytes > 0 && !hasEnoughStorage(fileSizeBytes)) {
                    updateState(modelId) {
                        it.copy(
                            isDownloading = false,
                            error = "Not enough storage. Need ${fileSizeBytes / (1024 * 1024)} MB"
                        )
                    }
                    return@launch
                }

                val localFileName = fileName.substringAfterLast("/")
                val destFile = File(modelsDir, localFileName)
                var resumeOffset = 0L

                if (destFile.exists()) {
                    resumeOffset = destFile.length()
                }

                val request = Request.Builder()
                    .url(fileUrl)
                    .header("Range", "bytes=$resumeOffset-")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful && response.code != 206) {
                    updateState(modelId) {
                        it.copy(isDownloading = false, error = "Download failed: HTTP ${response.code}")
                    }
                    return@launch
                }

                val body = response.body
                    ?: run {
                        updateState(modelId) {
                            it.copy(isDownloading = false, error = "Empty response")
                        }
                        return@launch
                    }
                val expectedTotalBytes = if (fileSizeBytes > 0) {
                    fileSizeBytes
                } else {
                    body.contentLength().takeIf { it > 0 }?.plus(resumeOffset) ?: 0L
                }

                if (expectedTotalBytes > 0 && !hasEnoughStorage(expectedTotalBytes)) {
                    updateState(modelId) {
                        it.copy(
                            isDownloading = false,
                            error = "Not enough storage. Need ${expectedTotalBytes / (1024 * 1024)} MB"
                        )
                    }
                    return@launch
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
                        val totalSize = expectedTotalBytes.takeIf { it > 0 }
                            ?: (body.contentLength() + resumeOffset)

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            val currentState = _downloadStates.value[modelId]
                            if (currentState?.isPaused == true) {
                                updateState(modelId) {
                                    it.copy(isPaused = true, isDownloading = false)
                                }
                                return@launch
                            }

                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            val progress =
                                if (totalSize > 0) totalRead.toFloat() / totalSize.toFloat() else 0f
                            updateState(modelId) {
                                it.copy(
                                    downloadedBytes = totalRead,
                                    totalBytes = totalSize,
                                    progress = progress
                                )
                            }
                        }
                    }
                }

                updateState(modelId) {
                    it.copy(
                        isDownloading = false,
                        isPaused = false,
                        progress = 1f,
                        downloadedBytes = expectedTotalBytes.takeIf { it > 0 }
                            ?: destFile.length()
                    )
                }

                val modelMetadata = try {
                    GgufMetadataParser.parse(destFile).getOrDefault(GgufMetadata())
                } catch (e: Exception) {
                    GgufMetadata()
                }

                val localModel = LocalModel(
                    name = localFileName.removeSuffix(".gguf"),
                    filePath = destFile.absolutePath,
                    sizeBytes = destFile.length().takeIf { it > 0 } ?: fileSizeBytes,
                    quantization = modelMetadata.quantization,
                    parameterCount = modelMetadata.parameterCount,
                    architecture = modelMetadata.architecture,
                    importedAt = Instant.now()
                )

                localModelRepository.saveModel(localModel)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                updateState(modelId) {
                    it.copy(error = e.message, isDownloading = false)
                }
            } catch (e: OutOfMemoryError) {
                updateState(modelId) {
                    it.copy(error = "Out of memory", isDownloading = false)
                }
            }
        }
    }

    fun pauseDownload(modelId: String) {
        updateState(modelId) { it.copy(isPaused = true, isDownloading = false) }
    }

    fun resumeDownload(modelId: String, fileUrl: String) {
        updateState(modelId) { it.copy(isPaused = false, isDownloading = true) }
        // Re-trigger the download if needed
    }

    fun cancelDownload(modelId: String) {
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

    private fun updateState(key: String, transform: (DownloadState) -> DownloadState) {
        _downloadStates.update { map ->
            map.toMutableMap().also { mutable ->
                mutable[key] = transform(mutable[key] ?: DownloadState(modelId = key))
            }
        }
    }
}
