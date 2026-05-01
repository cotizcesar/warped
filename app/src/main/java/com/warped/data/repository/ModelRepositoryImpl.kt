package com.warped.data.repository

import com.warped.domain.model.ModelInfo
import com.warped.domain.repository.ModelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRepositoryImpl @Inject constructor() : ModelRepository {
    private val models = MutableStateFlow<List<ModelInfo>>(emptyList())

    override fun observeModels(): Flow<List<ModelInfo>> = models

    override suspend fun refreshModels(endpointId: Long) {
        // Phase 1: models are fetched on-demand from the provider, not persisted.
    }
}
