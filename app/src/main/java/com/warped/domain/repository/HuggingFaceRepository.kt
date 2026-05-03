package com.warped.domain.repository

import com.warped.data.remote.dto.HuggingFaceModel
import com.warped.data.remote.dto.HuggingFaceModelDetail

interface HuggingFaceRepository {
    suspend fun searchModels(query: String? = null, format: String = "gguf", author: String? = null, limit: Int = 20): Result<List<HuggingFaceModel>>
    suspend fun getModelDetail(modelId: String): Result<HuggingFaceModelDetail>
}
