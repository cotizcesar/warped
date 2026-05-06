package com.warped.data.remote.provider

import com.warped.data.remote.api.AnthropicApi
import com.warped.data.remote.dto.AnthropicChatRequest
import com.warped.data.remote.dto.AnthropicMessage
import com.warped.data.remote.dto.AnthropicSseEvent
import com.warped.data.remote.dto.AnthropicThinking
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

class AnthropicProvider(
    private val baseUrl: String,
    private val modelId: String,
    apiKey: String?
) : LlmProvider {
    override val type = ProviderType.ANTHROPIC
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .apply {
            if (!apiKey.isNullOrBlank()) {
                addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", "2023-06-01")
                        .build()
                    chain.proceed(request)
                }
            }
        }
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val api = retrofit.create(AnthropicApi::class.java)

    override fun chat(request: ChatRequest): Flow<StreamToken> = flow {
        val systemMessage = request.messages.firstOrNull { it.role.name == "SYSTEM" }?.content
        val chatMessages = request.messages
            .filter { it.role.name != "SYSTEM" }
            .map { AnthropicMessage(role = it.role.name.lowercase(), content = it.content) }

        val body = AnthropicChatRequest(
            model = modelId,
            maxTokens = request.parameters.maxTokens.takeIf { it > 0 } ?: 4096,
            messages = chatMessages,
            system = systemMessage,
            stream = true,
            temperature = request.parameters.temperature,
            topP = request.parameters.topP,
            topK = request.parameters.topK,
            thinking = if (request.parameters.reasoningEnabled != false) {
                val budget = (request.parameters.maxTokens * 0.75).toInt().coerceIn(1024, 8192)
                AnthropicThinking(type = "enabled", budgetTokens = budget)
            } else null
        )
        try {
            val response = api.chatCompletions(body)
            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null) {
                    parseAnthropicSse(responseBody, json).collect { emit(it) }
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: response.message()
                emit(StreamToken.Error("HTTP ${response.code()}: $errorBody"))
            }
        } catch (e: Exception) {
            emit(StreamToken.Error("Connection failed: ${e.message}"))
        }
    }

    override suspend fun listModels(): Result<List<ModelInfo>> {
        return Result.success(emptyList()) // Anthropic doesn't have a public models list API
    }

    override suspend fun testConnection(): Result<ConnectionStatus> {
        return try {
            // Quick test: send a minimal request to see if auth works
            val testRequest = AnthropicChatRequest(
                model = modelId,
                maxTokens = 1,
                messages = listOf(AnthropicMessage("user", "Hi")),
                stream = false
            )
            val response = api.chatCompletions(testRequest)
            if (response.isSuccessful || response.code() == 401 || response.code() == 403) {
                // 401/403 means the endpoint is reachable but auth failed
                Result.success(if (response.isSuccessful) ConnectionStatus.Connected else ConnectionStatus.Disconnected)
            } else {
                Result.success(ConnectionStatus.Disconnected)
            }
        } catch (e: Exception) {
            Result.success(ConnectionStatus.Disconnected)
        }
    }

    companion object {
        private fun parseAnthropicSse(body: okhttp3.ResponseBody, json: Json): Flow<StreamToken> = flow {
            val source = body.source()
            var currentEvent: String? = null
            var thinkingOpen = false
            var toolOpen = false
            try {
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    when {
                        line.startsWith("event: ") -> currentEvent = line.removePrefix("event: ").trim()
                        line.startsWith("data: ") -> {
                            val data = line.removePrefix("data: ").trim()
                            try {
                                val event = json.decodeFromString<AnthropicSseEvent>(data)
                                when (event.type) {
                                    "content_block_start" -> {
                                        event.contentBlock?.let { block ->
                                            if (block.type == "tool_use") {
                                                toolOpen = true
                                                val toolName = block.name ?: "unknown"
                                                emit(StreamToken.Delta("[tool:$toolName]("))
                                            }
                                        }
                                    }
                                    "content_block_delta" -> {
                                        if (event.delta?.thinking != null) {
                                            if (!thinkingOpen) {
                                                emit(StreamToken.Delta("<think>"))
                                                thinkingOpen = true
                                            }
                                            emit(StreamToken.Delta(event.delta.thinking))
                                        } else if (event.delta?.partialJson != null && toolOpen) {
                                            emit(StreamToken.Delta(event.delta.partialJson))
                                        } else if (event.delta?.text != null) {
                                            if (thinkingOpen) {
                                                emit(StreamToken.Delta("</think>"))
                                                thinkingOpen = false
                                            }
                                            emit(StreamToken.Delta(event.delta.text))
                                        }
                                    }
                                    "content_block_stop" -> {
                                        if (toolOpen) {
                                            emit(StreamToken.Delta(")"))
                                            toolOpen = false
                                        }
                                    }
                                    "message_delta" -> { }
                                    "message_stop" -> {
                                        if (thinkingOpen) emit(StreamToken.Delta("</think>"))
                                        if (toolOpen) emit(StreamToken.Delta(")"))
                                        emit(StreamToken.Done())
                                        return@flow
                                    }
                                    "error" -> {
                                        val errorText = event.delta?.text ?: event.delta?.thinking ?: "Anthropic error"
                                        emit(StreamToken.Error(errorText))
                                        return@flow
                                    }
                                }
                            } catch (_: Exception) { }
                        }
                        line.isEmpty() -> currentEvent = null
                    }
                }
                if (thinkingOpen) emit(StreamToken.Delta("</think>"))
                if (toolOpen) emit(StreamToken.Delta(")"))
                if (source.exhausted()) {
                    emit(StreamToken.Done())
                }
            } catch (e: Exception) {
                if (thinkingOpen) emit(StreamToken.Delta("</think>"))
                emit(StreamToken.Error("SSE parse error: ${e.message}"))
            }
        }
    }
}
