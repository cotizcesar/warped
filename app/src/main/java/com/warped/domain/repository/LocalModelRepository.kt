package com.warped.domain.repository

import com.warped.domain.model.GenerationParameters
import com.warped.domain.model.LocalModel
import kotlinx.coroutines.flow.Flow

interface LocalModelRepository {
    fun observeModels(): Flow<List<LocalModel>>
    suspend fun getById(id: Long): LocalModel?
    suspend fun getByFilePath(filePath: String): LocalModel?
    suspend fun existsByFilePath(filePath: String): Boolean
    suspend fun saveModel(model: LocalModel): Long
    suspend fun updateParameters(modelId: Long, parameters: GenerationParameters)
    suspend fun deleteModel(id: Long)
    suspend fun deleteByFilePath(filePath: String): Int
}
