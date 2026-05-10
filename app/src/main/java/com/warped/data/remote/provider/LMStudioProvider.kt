package com.warped.data.remote.provider

import com.warped.data.local.inference.InputSanitizer
import com.warped.data.remote.api.LmStudioApi
import com.warped.data.remote.dto.LmStudioChatRequest
import com.warped.data.remote.dto.LmStudioDownloadRequest
import com.warped.data.remote.dto.LmStudioInputItem
import com.warped.data.remote.dto.LmStudioIntegration
import com.warped.data.remote.dto.LmStudioSseEvent
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.ModelInfo
import com.warped.domain.model.ProviderType
import com.warped.domain.model.Role
import com.warped.domain.model.StreamToken
import com.warped.domain.provider.LlmProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

class LMStudioProvider(
    private val baseUrl: String = "http://localhost:1234",
    private val modelId: String,
    apiKey: String? = null,
    private val inputSanitizer: InputSanitizer
) : LlmProvider {
    override val type = ProviderType.LM_STUDIO
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .apply {
            if (!apiKey.isNullOrBlank()) {
                addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("x-api-key", apiKey)
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

    private val api = retrofit.create(LmStudioApi::class.java)

    override fun chat(request: ChatRequest): Flow<StreamToken> = chat(request, emptyList())

    fun chat(request: ChatRequest, integrations: List<LmStudioIntegration>): Flow<StreamToken> = flow {
        val systemMessage = request.messages.firstOrNull { it.role == Role.SYSTEM }?.content
        val chatMessages = request.messages
            .filter { it.role != Role.SYSTEM }
            .map {
                val content = if (it.role == Role.USER) inputSanitizer.sanitize(it.content) else it.content
                LmStudioInputItem(type = "text", content = content)
            }
        
        val imageItems = request.images.map { LmStudioInputItem(type = "image", dataUrl = it) }
        val allInput = imageItems + chatMessages

        val body = LmStudioChatRequest(
            model = modelId,
            input = allInput,
            systemPrompt = systemMessage,
            stream = true,
            temperature = request.parameters.temperature,
            topP = request.parameters.topP,
            topK = request.parameters.topK,
            repeatPenalty = request.parameters.repeatPenalty,
            maxOutputTokens = request.parameters.maxTokens.takeIf { it > 0 },
            reasoning = if (request.parameters.reasoningEnabled != false) null else "off",
            integrations = integrations
        )
        try {
            val response = api.chat(body)
            if (response.isSuccessful) {
                val responseBody = response.body() ?: run {
                    emit(StreamToken.Error("Empty response"))
                    return@flow
                }
                val source = responseBody.source()
                var currentEvent = ""
                var statsText: String? = null
                var hasTokens = false
                var sawSse = false
                val bodyAccumulator = StringBuilder()

                try {
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        bodyAccumulator.appendLine(line)
                        when {
                            line.startsWith("event: ") -> {
                                currentEvent = line.removePrefix("event: ").trim()
                                sawSse = true
                            }
                            line.startsWith("data: ") -> {
                                sawSse = true
                                val data = line.removePrefix("data: ").trim()
                                if (data == "[DONE]") break
                                try {
                                    val eventType = currentEvent.ifBlank {
                                        val obj = json.decodeFromString<JsonObject>(data)
                                        obj["type"]?.jsonPrimitive?.content ?: ""
                                    }
                                    val event = json.decodeFromString<LmStudioSseEvent>(data)
                                    if (eventType == "tool_call.success") {
                                        val obj = json.decodeFromString<JsonObject>(data)
                                        val out = obj["output"]?.jsonPrimitive?.content ?: ""
                                        emit(StreamToken.Delta(" → $out"))
                                        hasTokens = true
                                    } else {
                                        handleSseEvent(eventType, event).forEach {
                                            emit(it)
                                            if (it is StreamToken.Delta) hasTokens = true
                                        }
                                    }
                                    if (eventType == "chat.end") {
                                        event.result?.stats?.let { stats ->
                                            statsText = "${stats.totalOutputTokens} tokens · ${stats.inputTokens} in · ${String.format("%.0f", stats.tokensPerSecond)} tok/s · ${String.format("%.1f", stats.timeToFirstTokenSeconds * 1000)}ms first"
                                        }
                                    }
                                } catch (e: Exception) { Timber.e(e, "LMStudio: SSE event parse failed") }
                            }
                            line.isEmpty() -> { currentEvent = "" }
                        }
                    }
                } catch (e: IOException) { Timber.e(e, "LMStudio: SSE stream read failed") }

                // Fallback: non-streaming JSON response
                if (!sawSse || !hasTokens) {
                    val rawBody = bodyAccumulator.toString()
                    try {
                        val event = json.decodeFromString<LmStudioSseEvent>(rawBody)
                        val s = event.stats ?: event.result?.stats
                        s?.let { stats ->
                            statsText = "${stats.totalOutputTokens} tokens · ${stats.inputTokens} in · ${String.format("%.0f", stats.tokensPerSecond)} tok/s · ${String.format("%.1f", stats.timeToFirstTokenSeconds * 1000)}ms first"
                        }
                        val out = event.output ?: event.result?.output
                        out?.forEach { item ->
                            when (item.type) {
                                "reasoning" -> if (item.content.isNotEmpty()) {
                                    emit(StreamToken.Delta("<think>${item.content}</think>"))
                                    hasTokens = true
                                }
                                "message" -> if (item.content.isNotEmpty()) {
                                    emit(StreamToken.Delta(item.content))
                                    hasTokens = true
                                }
                            }
                        }
                    } catch (e: Exception) { Timber.e(e, "LMStudio: non-streaming JSON parse failed") }
                }
                emit(StreamToken.Done(statsText, null))
            } else {
                val errorBody = response.errorBody()?.string() ?: response.message()
                emit(StreamToken.Error("HTTP ${response.code()}: $errorBody"))
            }
        } catch (e: Exception) {
            emit(StreamToken.Error("Connection failed: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    private fun handleSseEvent(eventType: String, event: LmStudioSseEvent): List<StreamToken> {
        val tokens = mutableListOf<StreamToken>()
        when {
            // reasoning — open/close <think> once so formatting is preserved
            eventType == "reasoning.start" -> {
                tokens.add(StreamToken.Delta("<think>"))
            }
            eventType == "reasoning.delta" -> {
                event.content?.let { tokens.add(StreamToken.Delta(it)) }
            }
            eventType == "reasoning.end" -> {
                tokens.add(StreamToken.Delta("</think>"))
            }
            // message
            eventType == "message.start" -> { /* marker */ }
            eventType == "message.delta" -> {
                event.content?.let { if (it.isNotEmpty()) tokens.add(StreamToken.Delta(it)) }
            }
            eventType == "message.end" -> { /* marker */ }
            // tool_call
            eventType == "tool_call.start" -> {
                event.tool?.let { tokens.add(StreamToken.Delta("[tool:$it]")) }
            }
            eventType == "tool_call.arguments" -> {
                event.arguments?.let { args ->
                    tokens.add(StreamToken.Delta("(${json.encodeToString(JsonObject.serializer(), args)})"))
                }
            }
            // tool_call.success handled inline (output field is a string, not a list)
            eventType == "tool_call.failure" -> {
                val r = event.reason ?: "unknown error"
                tokens.add(StreamToken.Delta(" ✗ $r"))
            }
            // model load / prompt processing — silent (user sees spinner in UI)
            eventType == "model_load.progress" -> { /* silent */ }
            eventType == "model_load.end" -> { /* silent */ }
            eventType == "prompt_processing.progress" -> { /* silent */ }
            // error
            eventType == "error" -> {
                tokens.add(StreamToken.Error(event.error?.message ?: "LM Studio error"))
            }
        }
        return tokens
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

    suspend fun loadModel(modelKey: String): Result<String> {
        return try {
            val request = com.warped.data.remote.dto.LmStudioLoadRequest(model = modelKey)
            val response = api.loadModel(request)
            if (response.isSuccessful) {
                Result.success(response.body()?.instanceId ?: modelKey)
            } else {
                Result.failure(Exception("Load failed: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unloadModel(instanceId: String): Result<Unit> {
        return try {
            val request = com.warped.data.remote.dto.LmStudioUnloadRequest(instanceId = instanceId)
            val response = api.unloadModel(request)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Unload failed: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadModel(model: String, quantization: String? = null): Result<String> {
        return try {
            val request = LmStudioDownloadRequest(model = model, quantization = quantization)
            val response = api.downloadModel(request)
            if (response.isSuccessful) {
                val jobId = response.body()?.jobId ?: ""
                Result.success(jobId)
            } else {
                Result.failure(Exception("Download failed: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadStatus(jobId: String): Result<com.warped.data.remote.dto.LmStudioDownloadStatusResponse> {
        return try {
            val response = api.downloadStatus(jobId)
            if (response.isSuccessful) {
                Result.success(response.body() ?: com.warped.data.remote.dto.LmStudioDownloadStatusResponse())
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
