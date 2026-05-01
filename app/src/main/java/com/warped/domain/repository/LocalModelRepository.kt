package com.warped.domain.repository

import com.warped.domain.model.LocalModel
import kotlinx.coroutines.flow.Flow

interface LocalModelRepository {
    fun observeModels(): Flow<List<LocalModel>>
    suspend fun getById(id: Long): LocalModel?
    suspend fun saveModel(model: LocalModel): Long
    suspend fun deleteModel(id: Long)
}
