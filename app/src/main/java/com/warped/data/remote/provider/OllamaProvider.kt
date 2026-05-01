package com.warped.data.remote.provider

import com.warped.data.remote.api.OllamaApi
import com.warped.data.remote.dto.OllamaChatRequest
import com.warped.data.remote.dto.OllamaMessage
import com.warped.data.remote.network.asOllamaFlow
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.ModelInfo
import com.warped.domain.model.ProviderType
import com.warped.domain.model.StreamToken
import com.warped.domain.provider.LlmProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class OllamaProvider(
    private val baseUrl: String,
    private val modelId: String
) : LlmProvider {
    override val type = ProviderType.OLLAMA
    private val json = Json { ignoreUnknownKeys = true }

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val api = retrofit.create(OllamaApi::class.java)

    override fun chat(request: ChatRequest): Flow<StreamToken> = flow {
        val messages = request.messages.map {
            OllamaMessage(role = it.role.name.lowercase(), content = it.content)
        }
        val body = OllamaChatRequest(
            model = modelId,
            messages = messages,
            stream = true,
            options = null
        )
        try {
            val response = api.chat(body)
            if (response.isSuccessful) {
                response.body()?.asOllamaFlow(json)?.collect { emit(it) }
            } else {
                emit(StreamToken.Error("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(StreamToken.Error("Connection failed: ${e.message}"))
        }
    }

    override suspend fun listModels(): Result<List<ModelInfo>> {
        return try {
            val response = api.listModels()
            if (response.isSuccessful) {
                val models = response.body()?.models?.map {
                    ModelInfo(id = it.name, name = it.name, providerType = ProviderType.OLLAMA)
                } ?: emptyList()
                Result.success(models)
            } else {
                Result.failure(Exception("HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun testConnection(): Result<ConnectionStatus> {
        return try {
            val response = api.listModels()
            if (response.isSuccessful) {
                Result.success(ConnectionStatus.Connected)
            } else {
                Result.success(ConnectionStatus.Disconnected)
            }
        } catch (e: Exception) {
            Result.success(ConnectionStatus.Disconnected)
        }
    }
}
