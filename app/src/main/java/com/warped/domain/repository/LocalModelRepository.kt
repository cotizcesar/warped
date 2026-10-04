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
    /**
     * One-shot heal (user decision 2026-10-03): rename rows still
     * carrying the raw file stem ("SmolLM3-3B_q4_block32_ekv4096") to
     * the catalog display name (or prettified stem for imports).
     * Only touches exact stem matches — anything else is left alone.
     * Returns the renamed count. Idempotent.
     */
    suspend fun healModelNames(): Int
}
