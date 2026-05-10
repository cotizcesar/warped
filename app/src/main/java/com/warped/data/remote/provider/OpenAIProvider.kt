package com.warped.data.remote.provider

import com.warped.data.local.inference.InputSanitizer
import com.warped.data.remote.api.OpenAiApi
import com.warped.data.remote.dto.OpenAiChatRequest
import com.warped.data.remote.dto.OpenAiCompletionsRequest
import com.warped.data.remote.dto.OpenAiEmbeddingsRequest
import com.warped.data.remote.dto.OpenAiMessage
import com.warped.data.remote.dto.OpenAiNonStreamingResponse
import com.warped.data.remote.dto.OpenAiResponsesRequest
import com.warped.data.remote.dto.OpenAiStreamChunk
import com.warped.data.remote.network.asCompletionsSseFlow
import com.warped.data.remote.network.asResponsesSseFlow
import com.warped.data.remote.network.asSseFlow
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.Role
import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.ModelInfo
import com.warped.domain.model.ProviderType
import com.warped.domain.model.StreamToken
import com.warped.domain.provider.LlmProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAIProvider(
    private val baseUrl: String,
    private val modelId: String,
    endpointId: Long,
    apiKey: String? = null,
    private val inputSanitizer: InputSanitizer
) : LlmProvider {
    override val type = ProviderType.OPENAI
    private val json = Json { ignoreUnknownKeys = true }

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
            val content = if (it.role == Role.USER) inputSanitizer.sanitize(it.content) else it.content
            OpenAiMessage(role = it.role.name.lowercase(), content = content)
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
            val jsonBody = json.encodeToString(OpenAiChatRequest.serializer(), body)
            val okHttpRequest = Request.Builder()
                .url(baseUrl.trimEnd('/') + "/v1/chat/completions")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()
            val okHttpResponse = client.newCall(okHttpRequest).execute()
            if (okHttpResponse.isSuccessful) {
                val responseBody = okHttpResponse.body ?: run {
                    emit(StreamToken.Error("Empty response"))
                    return@flow
                }
                val source = responseBody.source()
                val firstLine = source.readUtf8Line() ?: ""
                val isSse = firstLine.startsWith("event: ") || firstLine.startsWith("data: ")
                var hasTokens = false

                if (isSse) {
                    // SSE streaming — read incrementally from source
                    var currentEvent = if (firstLine.startsWith("event: ")) {
                        firstLine.removePrefix("event: ").trim()
                    } else ""
                    var reasoningOpen = false
                    if (firstLine.startsWith("data: ")) {
                        val data = firstLine.removePrefix("data: ").trim()
                        try {
                            val delta = parseSseData(json, data)
                            delta?.reasoningContent?.let { reasoning ->
                                if (!reasoningOpen) { emit(StreamToken.Delta("<think>")); reasoningOpen = true }
                                emit(StreamToken.Delta(reasoning))
                                hasTokens = true
                            }
                            delta?.content?.let { content ->
                                if (reasoningOpen) { emit(StreamToken.Delta("</think>")); reasoningOpen = false }
                                emit(StreamToken.Delta(content))
                                hasTokens = true
                            }
                            delta?.toolCalls?.forEach { tc ->
                                tc.function?.name?.let { emit(StreamToken.Delta("[tool:$it]")); hasTokens = true }
                                tc.function?.arguments?.let { emit(StreamToken.Delta("($it)")); hasTokens = true }
                            }
                        } catch (e: Exception) { Timber.e(e, "OpenAI: SSE first-line delta parse failed") }
                    }
                    try {
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            when {
                                line.startsWith("event: ") -> currentEvent = line.removePrefix("event: ").trim()
                                line.startsWith("data: ") -> {
                                    val data = line.removePrefix("data: ").trim()
                                    if (data == "[DONE]") {
                                        if (reasoningOpen) emit(StreamToken.Delta("</think>"))
                                        break
                                    }
                                    try {
                                        val delta = parseSseData(json, data)
                                        delta?.reasoningContent?.let { reasoning ->
                                            if (!reasoningOpen) {
                                                emit(StreamToken.Delta("<think>"))
                                                reasoningOpen = true
                                            }
                                            emit(StreamToken.Delta(reasoning))
                                            hasTokens = true
                                        }
                                        delta?.content?.let { content ->
                                            if (reasoningOpen) {
                                                emit(StreamToken.Delta("</think>"))
                                                reasoningOpen = false
                                            }
                                            emit(StreamToken.Delta(content))
                                            hasTokens = true
                                        }
                                        delta?.toolCalls?.forEach { tc ->
                                            tc.function?.name?.let { name ->
                                                emit(StreamToken.Delta("[tool:$name]"))
                                                hasTokens = true
                                            }
                                            tc.function?.arguments?.let { args ->
                                                emit(StreamToken.Delta("($args)"))
                                                hasTokens = true
                                            }
                                        }
                                    } catch (e: Exception) { Timber.e(e, "OpenAI: SSE delta parse failed") }
                                }
                                line.isEmpty() -> currentEvent = ""
                            }
                        }
                    } catch (e: IOException) { Timber.e(e, "OpenAI: SSE stream read failed") }
                } else {
                    // Non-streaming JSON — read remaining + first line
                    val remaining = source.readUtf8() ?: ""
                    val rawBody = firstLine + "\n" + remaining
                    try {
                        val result = json.decodeFromString<OpenAiNonStreamingResponse>(rawBody)
                        val msg = result.choices.firstOrNull()?.message
                        msg?.reasoningContent?.let {
                            emit(StreamToken.Delta("<think>$it</think>"))
                            hasTokens = true
                        }
                        msg?.content?.let {
                            emit(StreamToken.Delta(it))
                            hasTokens = true
                        }
                        msg?.toolCalls?.forEach { tc ->
                            tc.function?.name?.let { name ->
                                emit(StreamToken.Delta("[tool:$name]"))
                                hasTokens = true
                            }
                            tc.function?.arguments?.let { args ->
                                emit(StreamToken.Delta("($args)"))
                                hasTokens = true
                            }
                        }
                    } catch (e: Exception) { Timber.e(e, "OpenAI: non-streaming JSON parse failed") }
                }
                if (hasTokens) emit(StreamToken.Done())
                else emit(StreamToken.Error("No content in response"))
            } else {
                emit(StreamToken.Error("HTTP ${okHttpResponse.code}: ${okHttpResponse.message}"))
            }
        } catch (e: Exception) {
            emit(StreamToken.Error("Connection failed: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    private fun parseSseData(json: Json, data: String): com.warped.data.remote.dto.OpenAiStreamDelta? {
        return try {
            json.decodeFromString<com.warped.data.remote.dto.OpenAiStreamChunk>(data).choices.firstOrNull()?.delta
        } catch (_: Exception) { null }
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
