package com.warped.domain.provider

import com.warped.domain.model.ChatRequest
import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.ModelInfo
import com.warped.domain.model.ProviderType
import com.warped.domain.model.StreamToken
import kotlinx.coroutines.flow.Flow

interface LlmProvider {
    val type: ProviderType
    fun chat(request: ChatRequest): Flow<StreamToken>
    suspend fun listModels(): Result<List<ModelInfo>>
    suspend fun testConnection(): Result<ConnectionStatus>
}
