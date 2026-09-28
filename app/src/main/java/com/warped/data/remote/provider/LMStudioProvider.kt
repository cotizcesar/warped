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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit
import timber.log.Timber

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
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
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

    /**
     * 46-01 RUNTIME-14: raw-OkHttp SSE chat with a retained cancellable [Call].
     *
     * The request body is built from the SAME [LmStudioChatRequest] DTO and the SAME
     * sanitized content as before (threat T-46-01: never rebuild from unsanitized
     * fields). Logging stays at BASIC — never BODY/HEADERS, the x-api-key
     * interceptor is reused unchanged (threat T-46-02).
     *
     * Cancellation contract:
     * - [onCallCreated] hands the live [Call] to the caller (the helper retains it
     *   and invokes [Call.cancel] from `stopResponse()`).
     * - [CancellationException] is rethrown FIRST — Stop must terminate, never
     *   surface as a fake `StreamToken.Error` (defect 6).
     * - `Call.cancel()` while blocked in `readUtf8Line()` surfaces as
     *   `IOException("Canceled")`, handled silently like any read failure.
     * - The teardown watcher bridges structured-concurrency cancellation to the
     *   socket: a bare collector-cancel also tears down the Call, because
     *   `readUtf8Line()` is blocking and would otherwise ignore cancellation and
     *   leak the connection until the server sends again.
     *
     * 46-01 Phase 47 hook contract (hooks only, no loop): every tool-round
     * boundary in the future tool loop must call
     * `currentCoroutineContext().ensureActive()` and enforce the round cap with
     * `>=` semantics (`check(round >= MAX_TOOL_ROUNDS)`). Full loop is Phase 47.
     */
    @Volatile
    private var currentCall: Call? = null

    /**
     * Belt-and-braces teardown of the in-flight SSE call, if any. Safe when idle.
     *
     * WR-02: the canonical cancel path is the helper-owned handle — the helper
     * retains the live [Call] via the `onCallCreated` hook into its own
     * `activeCall` and cancels it from `stopResponse()`. This method only covers
     * the same [currentCall] retained on this instance; prefer the helper path
     * when cancelling a turn (a fresh provider is created per turn, so this
     * handle must never be confused with another turn's call).
     */
    fun cancelChat() {
        currentCall?.cancel()
    }

    fun chat(
        request: ChatRequest,
        integrations: List<LmStudioIntegration> = emptyList(),
        onCallCreated: (Call) -> Unit = {},
    ): Flow<StreamToken> = callbackFlow {
        val systemMessage = request.messages.firstOrNull { it.role == Role.SYSTEM }?.content
        val chatMessages = request.messages
            .filter { it.role != Role.SYSTEM }
            .filter { it.content.isNotBlank() }
            .map {
                // 47-03: resumed role:tool rows (persisted as
                // "<toolId>\n<summary>") replay as plain-text summaries on
                // the native path, which has no role:tool concept. Plain
                // chat (no TOOL rows) is byte-identical to before.
                val content = when (it.role) {
                    Role.USER -> inputSanitizer.sanitize(it.content)
                    Role.TOOL -> {
                        val idx = it.content.indexOf('\n')
                        val toolId = if (idx < 0) it.content else it.content.substring(0, idx)
                        val summary = if (idx < 0) "" else it.content.substring(idx + 1)
                        "Used ${com.warped.domain.skills.toolDisplayNameCapitalized(toolId)}: $summary"
                    }
                    else -> it.content
                }
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
        val call = client.newCall(
            Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/v1/chat")
                .post(Json.encodeToString(LmStudioChatRequest.serializer(), body).toRequestBody("application/json".toMediaType()))
                .build()
        )
        currentCall = call
        onCallCreated(call)
        // Bridge collector cancellation to the socket: readUtf8Line() is blocking,
        // so without this a bare cancel would leak the connection until the next
        // server chunk. Transport-stop (helper.stopResponse -> Call.cancel) is the
        // primary path; this watcher is the structured-concurrency backstop.
        val teardown = launch {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = try {
                        response.body?.string()
                    } catch (e: Exception) {
                        Timber.w(e, "LMStudio: error-body read failed")
                        response.message
                    } ?: response.message
                    trySend(StreamToken.Error("HTTP ${response.code}: $errorBody"))
                } else {
                    val responseBody = response.body
                    if (responseBody == null) {
                        trySend(StreamToken.Error("Empty response"))
                    } else {
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
                                                send(StreamToken.Delta("$out"))
                                                hasTokens = true
                                            } else {
                                                // 46-01 Phase 47 hook site: tool_call.* events are
                                                // round boundaries of the future tool loop —
                                                // ensureActive() + >= round-cap check land here.
                                                handleSseEvent(eventType, event).forEach {
                                                    send(it)
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

                        // WR-06: only attempt the non-streaming decode for a true JSON
                        // body (never saw SSE framing). When SSE events arrived but
                        // carried no tokens (e.g. only progress events), decoding the
                        // SSE-framed accumulator as JSON always fails — emit Done.
                        if (!sawSse && !hasTokens) {
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
                                            trySend(StreamToken.Delta("<think>${item.content}</think>"))
                                            hasTokens = true
                                        }
                                        "message" -> if (item.content.isNotEmpty()) {
                                            trySend(StreamToken.Delta(item.content))
                                            hasTokens = true
                                        }
                                    }
                                }
                            } catch (e: Exception) { Timber.e(e, "LMStudio: non-streaming JSON parse failed") }
                        }
                        if (currentCoroutineContext().isActive) {
                            trySend(StreamToken.Done(statsText, null))
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            // Stop means stop: never map coroutine cancellation to an Error token.
            currentCall = null
            throw e
        } catch (e: IOException) {
            // Includes IOException("Canceled") from Call.cancel() teardown — silent.
            Timber.d(e, "LMStudio: chat transport closed")
        } catch (e: Exception) {
            Timber.e(e, "LMStudio: chat failed")
            trySend(StreamToken.Error("Connection failed: ${e.message}"))
        } finally {
            currentCall = null
            teardown.cancel()
        }
        awaitClose { currentCall = null }
    }.flowOn(Dispatchers.IO)

    /**
     * 47-03 (D-04/D-07): OpenAI-compatible tool-loop entry — PARALLEL path.
     *
     * Speaks `POST {base}/v1/chat/completions` with `tools[]` via
     * [LmStudioToolLoop] (index-keyed SSE accumulator, local execution,
     * `role:tool` re-POST, 5-round cap, malformed fallback). The native
     * `/api/v1/chat` [chat] path above is untouched for plain chat.
     */
    fun chatCompletionsWithTools(
        request: ChatRequest,
        tools: List<com.warped.data.remote.dto.OpenAiTool>,
        executor: com.warped.domain.skills.ToolExecutor,
        onCallCreated: (Call) -> Unit = {},
    ): Flow<StreamToken> {
        val loop = LmStudioToolLoop(
            client = client,
            baseUrl = baseUrl,
            modelId = modelId,
            executor = executor,
            inputSanitizer = inputSanitizer,
        )
        return loop.run(request, tools, onCallCreated)
    }

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
                tokens.add(StreamToken.Delta("$r"))
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
        // Try multiple LM Studio endpoints in order. Different LM Studio versions
        // expose different model-listing paths:
        //  - v1 REST API (0.4+): /api/v1/models   -> { models: [...] }
        //  - OpenAI compat:      /v1/models        -> { data: [...] }
        //  - v0 REST (legacy):   /api/v0/models   -> { data: [...] }
        // We hit them in order and use the first response that yields ≥1 model.
        val candidates = listOf("api/v1/models", "v1/models", "api/v0/models")
        var lastError: Throwable? = null
        for (path in candidates) {
            try {
                val url = "${baseUrl}$path"
                Timber.d("LMStudioProvider.listModels: GET $url")
                val response = api.listModelsByPath(path)
                Timber.d("LMStudioProvider.listModels: $url -> HTTP ${response.code()}")
                if (response.isSuccessful) {
                    val body = response.body()
                    val lmDtos = body?.models.orEmpty()
                    val openAiDtos = body?.data.orEmpty()
                    val lmModels = lmDtos.map { d ->
                        ModelInfo(
                            id = d.key,
                            name = d.displayName.ifBlank { d.key },
                            providerType = ProviderType.LM_STUDIO
                        )
                    }
                    val openAiModels = openAiDtos.map { d ->
                        ModelInfo(
                            id = d.id,
                            name = d.displayName?.ifBlank { d.id } ?: d.id,
                            providerType = ProviderType.LM_STUDIO
                        )
                    }
                    val models = lmModels.ifEmpty { openAiModels }
                    val dtos = lmDtos.ifEmpty { openAiDtos.map {
                        com.warped.data.remote.dto.LmStudioModelData(
                            key = it.id,
                            displayName = it.displayName.orEmpty()
                        )
                    } }
                    com.warped.data.repository.LmStudioModelCache.lastData = dtos
                    Timber.d("LMStudioProvider.listModels: $url parsed ${models.size} models (lmV1=${lmModels.size} openAi=${openAiModels.size})")
                    if (models.isNotEmpty()) return Result.success(models)
                    // 200 OK with empty list — try next endpoint before giving up
                    lastError = IllegalStateException("$url returned empty list")
                } else {
                    lastError = Exception("$url -> HTTP ${response.code()} ${response.message()}")
                }
            } catch (e: Exception) {
                Timber.w(e, "LMStudioProvider.listModels: $path failed")
                lastError = e
            }
        }
        return Result.failure(lastError ?: IllegalStateException("No LM Studio endpoint responded with models"))
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
