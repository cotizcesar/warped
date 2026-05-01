package com.warped.data.remote.provider

import com.warped.data.remote.api.LmStudioApi
import com.warped.data.remote.dto.LmStudioChatRequest
import com.warped.data.remote.dto.LmStudioInputItem
import com.warped.data.remote.dto.LmStudioSseEvent
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.ModelInfo
import com.warped.domain.model.ProviderType
import com.warped.domain.model.Role
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
        val systemMessage = request.messages.firstOrNull { it.role == Role.SYSTEM }?.content
        val chatMessages = request.messages
            .filter { it.role != Role.SYSTEM }
            .map { LmStudioInputItem(type = "text", content = it.content) }

        val body = LmStudioChatRequest(
            model = modelId,
            input = chatMessages,
            systemPrompt = systemMessage,
            stream = true,
            temperature = request.parameters.temperature,
            topP = request.parameters.topP,
            topK = request.parameters.topK,
            repeatPenalty = request.parameters.repeatPenalty,
            maxOutputTokens = request.parameters.maxTokens.takeIf { it > 0 }
        )
        try {
            val response = api.chat(body)
            if (response.isSuccessful) {
                val responseBody = response.body() ?: run {
                    emit(StreamToken.Error("Empty response"))
                    return@flow
                }
                val fullBody = responseBody.string()
                val lines = fullBody.lines()
                var hasTokens = false
                var currentEvent = ""
                var statsText: String? = null
                for (line in lines) {
                    when {
                        line.startsWith("event: ") -> currentEvent = line.removePrefix("event: ").trim()
                        line.startsWith("data: ") -> {
                            val data = line.removePrefix("data: ").trim()
                            if (data == "[DONE]") {
                                emit(StreamToken.Done(statsText))
                                return@flow
                            }
                            try {
                                val event = json.decodeFromString<LmStudioSseEvent>(data)
                                if (currentEvent == "message.delta" || event.type == "message.delta") {
                                    val text = event.content ?: ""
                                    if (text.isNotEmpty()) {
                                        emit(StreamToken.Delta(text))
                                        hasTokens = true
                                    }
                                }
                                if (currentEvent == "chat.end" || event.type == "chat.end") {
                                    event.result?.stats?.let { stats ->
                                        statsText = " · ${stats.totalOutputTokens} tokens (${stats.inputTokens} in, ${String.format("%.0f", stats.tokensPerSecond)} tok/s, ${String.format("%.1f", stats.timeToFirstTokenSeconds * 1000)}ms first)"
                                    }
                                }
                                if (event.error != null) {
                                    emit(StreamToken.Error(event.error.message))
                                    return@flow
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
                // Fallback: non-streaming
                if (!hasTokens) {
                    try {
                        val event = json.decodeFromString<LmStudioSseEvent>(fullBody)
                        event.result?.stats?.let { stats ->
                            statsText = " · ${stats.totalOutputTokens} tokens (${stats.inputTokens} in, ${String.format("%.0f", stats.tokensPerSecond)} tok/s, ${String.format("%.1f", stats.timeToFirstTokenSeconds * 1000)}ms first)"
                        }
                        event.result?.output?.forEach { item ->
                            if (item.type == "message" && item.content.isNotEmpty()) {
                                emit(StreamToken.Delta(item.content))
                                hasTokens = true
                            }
                        }
                    } catch (_: Exception) {}
                }
                emit(StreamToken.Done(statsText))
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
                val models = response.body()?.models?.map {
                    ModelInfo(
                        id = it.key,
                        name = it.displayName.ifBlank { it.key },
                        providerType = ProviderType.LM_STUDIO
                    )
                } ?: emptyList()
                Result.success(models)
            } else {
                Result.failure(Exception("HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listModelsRaw(): Result<List<com.warped.data.remote.dto.LmStudioModelData>> {
        return try {
            val response = api.listModels()
            if (response.isSuccessful) {
                Result.success(response.body()?.models ?: emptyList())
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
