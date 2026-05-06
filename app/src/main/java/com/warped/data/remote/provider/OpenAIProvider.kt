package com.warped.data.remote.provider

import com.warped.data.remote.api.OpenAiApi
import com.warped.data.remote.dto.OpenAiChatRequest
import com.warped.data.remote.dto.OpenAiCompletionsRequest
import com.warped.data.remote.dto.OpenAiEmbeddingsRequest
import com.warped.data.remote.dto.OpenAiMessage
import com.warped.data.remote.dto.OpenAiResponsesRequest
import com.warped.data.remote.network.asCompletionsSseFlow
import com.warped.data.remote.network.asResponsesSseFlow
import com.warped.data.remote.network.asSseFlow
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
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class OpenAIProvider(
    private val baseUrl: String,
    private val modelId: String,
    endpointId: Long,
    apiKey: String? = null
) : LlmProvider {
    override val type = ProviderType.OPENAI
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .apply {
            if (!apiKey.isNullOrBlank()) {
                addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("Authorization", "Bearer $apiKey")
                        .build()
                    chain.proceed(request)
                }
            }
        }
        .build()

    private val retrofit = Retrofit.Builder()
        .client(client)
        .baseUrl(baseUrl)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val api = retrofit.create(OpenAiApi::class.java)

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
            maxTokens = request.parameters.maxTokens
        )
        try {
            val response = api.chatCompletions(body)
            if (response.isSuccessful) {
                response.body()?.asSseFlow(json)?.collect { emit(it) }
            } else {
                emit(StreamToken.Error("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(StreamToken.Error("Connection failed: ${e.message}"))
        }
    }

    fun responses(input: String, previousResponseId: String? = null, params: com.warped.domain.model.GenerationParameters): Flow<StreamToken> = flow {
        val body = OpenAiResponsesRequest(
            model = modelId,
            input = input,
            stream = true,
            previousResponseId = previousResponseId,
            temperature = params.temperature,
            topP = params.topP,
            maxOutputTokens = params.maxTokens.takeIf { it > 0 }
        )
        try {
            val response = api.responses(body)
            if (response.isSuccessful) {
                response.body()?.asResponsesSseFlow(json)?.collect { emit(it) }
            } else {
                emit(StreamToken.Error("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(StreamToken.Error("Connection failed: ${e.message}"))
        }
    }

    fun completions(prompt: String, params: com.warped.domain.model.GenerationParameters): Flow<StreamToken> = flow {
        val body = OpenAiCompletionsRequest(
            model = modelId,
            prompt = prompt,
            stream = true,
            temperature = params.temperature,
            topP = params.topP,
            maxTokens = params.maxTokens.takeIf { it > 0 }
        )
        try {
            val response = api.completions(body)
            if (response.isSuccessful) {
                response.body()?.asCompletionsSseFlow(json)?.collect { emit(it) }
            } else {
                emit(StreamToken.Error("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(StreamToken.Error("Connection failed: ${e.message}"))
        }
    }

    suspend fun embed(input: List<String>): Result<List<List<Float>>> {
        return try {
            val body = OpenAiEmbeddingsRequest(model = modelId, input = input)
            val response = api.embeddings(body)
            if (response.isSuccessful) {
                val embeddings = response.body()?.data?.map { it.embedding } ?: emptyList()
                Result.success(embeddings)
            } else {
                Result.failure(Exception("HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listModels(): Result<List<ModelInfo>> {
        return try {
            val response = api.listModels()
            if (response.isSuccessful) {
                val models = response.body()?.data?.map {
                    ModelInfo(id = it.id, name = it.id, providerType = ProviderType.OPENAI)
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
