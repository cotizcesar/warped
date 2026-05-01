package com.warped.domain.repository

import com.warped.domain.model.ModelInfo
import kotlinx.coroutines.flow.Flow

interface ModelRepository {
    fun observeModels(): Flow<List<ModelInfo>>
    suspend fun refreshModels(endpointId: Long)
}
