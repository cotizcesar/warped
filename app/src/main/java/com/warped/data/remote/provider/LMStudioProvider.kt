package com.warped.data.remote.provider

import com.warped.data.remote.api.LmStudioApi
import com.warped.data.remote.dto.OpenAiChatRequest
import com.warped.data.remote.dto.OpenAiMessage
import com.warped.data.remote.network.asSseFlow
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.ModelInfo
import com.warped.domain.model.ProviderType
import com.warped.domain.model.StreamToken
import com.warped.domain.provider.LlmProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class LMStudioProvider(
    private val baseUrl: String = "http://localhost:1234",
    private val modelId: String
) : LlmProvider {
    override val type = ProviderType.LM_STUDIO
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val api = retrofit.create(LmStudioApi::class.java)

    override fun chat(request: ChatRequest): Flow<StreamToken> = flow {
        val messages = request.messages.map {
            OpenAiMessage(role = it.role.name.lowercase(), content = it.content)
        }
        val body = OpenAiChatRequest(
            model = modelId,
            messages = messages,
            stream = true,
            temperature = request.parameters.temperature,
            topP = request.parameters.topP,
            maxTokens = request.parameters.maxTokens.takeIf { it > 0 } ?: -1
        )
        try {
            val response = api.chat(body)
            if (response.isSuccessful) {
                response.body()?.asSseFlow(json)?.collect { emit(it) }
            } else {
                val errorBody = response.errorBody()?.string() ?: response.message()
                emit(StreamToken.Error("HTTP ${response.code()}: $errorBody"))
            }
        } catch (e: Exception) {
            emit(StreamToken.Error("Connection failed: ${e.message}"))
        }
    }

    override suspend fun listModels(): Result<List<ModelInfo>> {
        return try {
            val response = api.listModels()
            if (response.isSuccessful) {
                val models = response.body()?.data?.map {
                    ModelInfo(id = it.id, name = it.id, providerType = ProviderType.LM_STUDIO)
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
            if (response.isSuccessful) Result.success(ConnectionStatus.Connected)
            else Result.success(ConnectionStatus.Disconnected)
        } catch (e: Exception) {
            Result.success(ConnectionStatus.Disconnected)
        }
    }
}
