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
    @param:ApplicationContext private val context: Context,
    private val localModelRepository: LocalModelRepository
) {
    private val modelsDir: File
        get() = File(context.filesDir, "models").also { it.mkdirs() }

    suspend fun importFromUri(uri: Uri, onProgress: (Float) -> Unit = {}): Result<LocalModel> =
        withContext(Dispatchers.IO) {
            try {
                val fileName = getFileName(uri)?.takeIf { it.isNotBlank() }
                    ?: "model.litertlm" // keep backward-compatible default
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

                val localModel = LocalModel(
                    name = fileName.removeSuffix(".litertlm"),
                    filePath = destFile.absolutePath,
                    sizeBytes = destFile.length(),
                    quantization = "N/A",
                    parameterCount = "Unknown",
                    architecture = "LiteRT-LM",
                    modelFormat = "LITERTLM",
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
