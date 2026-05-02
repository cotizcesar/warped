package com.warped.data.local.inference

import android.content.Context
import android.net.Uri
import com.warped.domain.model.LocalModel
import com.warped.domain.repository.LocalModelRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelImportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localModelRepository: LocalModelRepository
) {
    private val modelsDir: File
        get() = File(context.filesDir, "models").also { it.mkdirs() }

    suspend fun importFromUri(uri: Uri, onProgress: (Float) -> Unit = {}): Result<LocalModel> =
        withContext(Dispatchers.IO) {
            try {
                val fileName = getFileName(uri)?.takeIf { it.isNotBlank() }
                    ?: "model.gguf" // keep backward-compatible default
                val destFile = File(modelsDir, fileName)

                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalRead = 0L
                        val totalSize = context.contentResolver.openFileDescriptor(uri, "r")?.statSize ?: 0

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (totalSize > 0) {
                                onProgress(totalRead.toFloat() / totalSize)
                            }
                        }
                    }
                }

                val isLitertlm = fileName.endsWith(".litertlm", ignoreCase = true)

                val metadata = if (!isLitertlm) {
                    GgufMetadataParser.parse(destFile)
                } else {
                    Result.success(GgufMetadata()) // .litertlm has different binary header
                }
                val modelMetadata = metadata.getOrDefault(GgufMetadata())

                val localModel = LocalModel(
                    name = modelMetadata.name.ifEmpty {
                        fileName.removeSuffix(".gguf").removeSuffix(".litertlm")
                    },
                    filePath = destFile.absolutePath,
                    sizeBytes = destFile.length(),
                    quantization = if (isLitertlm) "N/A" else modelMetadata.quantization,
                    parameterCount = if (isLitertlm) "Unknown" else modelMetadata.parameterCount,
                    architecture = if (isLitertlm) "LiteRT-LM" else modelMetadata.architecture,
                    modelFormat = if (isLitertlm) "LITERTLM" else "GGUF",
                    importedAt = Instant.now()
                )

                val id = localModelRepository.saveModel(localModel)
                Result.success(localModel.copy(id = id))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun deleteModel(model: LocalModel) = withContext(Dispatchers.IO) {
        try {
            File(model.filePath).delete()
            localModelRepository.deleteModel(model.id)
        } catch (e: Exception) {
        }
    }

    private fun getFileName(uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) it.getString(index) else null
            } else null
        }
    }
}
