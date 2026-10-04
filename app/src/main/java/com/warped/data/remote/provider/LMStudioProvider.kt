package com.warped.data.remote.provider

import com.warped.data.local.inference.InputSanitizer
import com.warped.data.remote.api.LmStudioApi
import com.warped.data.remote.dto.LmStudioChatRequest
import com.warped.data.remote.dto.LmStudioDownloadRequest
import com.warped.data.remote.dto.LmStudioInputItem
import com.warped.data.remote.dto.LmStudioIntegration
import com.warped.data.remote.dto.LmStudioSseEvent
import com.warped.domain.model.ChatMessage
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.ModelInfo
import com.warped.domain.model.ProviderType
import com.warped.domain.model.Role
import com.warped.domain.model.StreamToken
import com.warped.domain.model.toolDisplayNameCapitalized
import com.warped.domain.provider.LlmProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import com.warped.data.agentic.ToolCapabilityMatrix
import com.warped.data.grounding.GroundingPrecedence
import com.warped.data.grounding.MultiUrlFetcher
import com.warped.data.grounding.DuckDuckGoSearchRepository
import com.warped.data.grounding.WebPageFetcher
import com.warped.data.local.preferences.AdvancedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
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
import java.util.Locale
import java.util.concurrent.TimeUnit
import timber.log.Timber

class LMStudioProvider(
    private val baseUrl: String = "http://localhost:1234",
    private val modelId: String,
    apiKey: String? = null,
    private val inputSanitizer: InputSanitizer,
    /**
     * Phase 57 (57-02): tool-loop collaborators (Phase 55/52 singletons).
     * All-null by default so the helper/`resolve()` path behaves exactly
     * as before — the compat attempt only arms when every collaborator is
     * present (see [isLoopArmed]). The endpoint key stays on this
     * client's interceptor ONLY; loop code never references it (T-57-07).
     *
     * Quick-task (DDG-default): the search collaborator is the DDG-only
     * repository.
     */
    private val ddg: DuckDuckGoSearchRepository? = null,
    private val multiUrlFetcher: MultiUrlFetcher? = null,
    private val webPageFetcher: WebPageFetcher? = null,
    private val advancedPreferences: AdvancedPreferences? = null,
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
                    // Docs use `Authorization: Bearer` on the native REST
                    // API and accept `x-api-key` too — send both so every
                    // endpoint authenticates regardless of which header a
                    // given server version checks.
                    val request = chain.request().newBuilder()
                        .header("x-api-key", apiKey)
                        .header("Authorization", "Bearer $apiKey")
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

    override fun chat(request: ChatRequest): Flow<StreamToken> = flow {
        // Phase 57 (57-02): armed turns attempt the OpenAI-compat `/v1`
        // chat-completions path with the shared compat loop (model-dependent
        // tools support — the one-retry fallback absorbs a wrong pick);
        // unarmed turns keep the exact native `/api/v1/chat` path below
        // (images, integrations, reasoning, stats untouched).
        if (isLoopArmed(request.webOverride)) {
            // Quick-task (remote-image-carry): compat rounds carry history
            // images as `image_url` parts (shared K=3 rule).
            val baseMessages = mapOpenAiHistory(
                request.messages,
                includeSystem = false,
                sanitizeUser = inputSanitizer::sanitize,
                currentImages = request.images,
            )
            val ddgRepo = ddg
            val fetchAll = multiUrlFetcher
            val net = webPageFetcher
            if (ddgRepo != null && fetchAll != null && net != null) {
                with(CompatToolLoop) {
                    runTurn(
                        client = client,
                        json = json,
                        postUrl = baseUrl.trimEnd('/') + "/v1/chat/completions",
                        modelId = modelId,
                        baseMessages = baseMessages,
                        request = request,
                        ddg = ddgRepo,
                        multiUrlFetcher = fetchAll,
                        webPageFetcher = net,
                        logTag = "LMStudio",
                        // WR-01: forward the compat-loop socket to the
                        // helper-owned handle as well as the provider
                        // field, so stopResponse() reaches armed turns.
                        onCallCreated = { currentCall = it; callHook(it) },
                        onCallCleared = { currentCall = null },
                    )
                }
                return@flow
            }
        }
        chat(
            request,
            emptyList(),
            // WR-01: the unarmed fallback drops the hook by default —
            // forward it too so Stop keeps working on plain turns.
            onCallCreated = { currentCall = it; callHook(it) },
        ).collect { emit(it) }
    }.flowOn(Dispatchers.IO)

    /**
     * Phase 57 (57-02): provider-authoritative loop-arming. LM Studio
     * serves OpenAI-compat `/v1` model-dependently — attempt once, then
     * exactly one retry without tools plus a visible notice on a 400-class
     * rejection. Grounding precedence resolves the per-chat override
     * against the global default; internet must be validated. Missing
     * collaborators or any gate failure reads as unarmed (native turn),
     * never a crash.
     */
    private suspend fun isLoopArmed(perChat: Boolean?): Boolean {
        val prefs = advancedPreferences ?: return false
        val net = webPageFetcher ?: return false
        if (ddg == null || multiUrlFetcher == null) return false
        if (!ToolCapabilityMatrix.attemptsTools(
                ToolCapabilityMatrix.modeFor(ProviderType.LM_STUDIO),
            )
        ) return false
        return try {
            val global = try {
                prefs.webGroundingEnabled.first()
            } catch (e: Exception) {
                Timber.w(e, "LMStudio: global grounding read failed, treating as off")
                false
            }
            val online = try {
                net.hasValidatedInternet()
            } catch (e: Exception) {
                Timber.w(e, "LMStudio: connectivity check failed, treating as offline")
                false
            }
            ToolCapabilityMatrix.isRemoteLoopArmed(
                groundingOn = GroundingPrecedence.shouldGround(perChat, global),
                matrixAttemptsTools = true,
                hasValidatedInternet = online,
            )
        } catch (_: Exception) {
            false
        }
    }

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
     */
    @Volatile
    private var currentCall: Call? = null

    /**
     * Phase 57 (WR-01 fix): helper-owned Stop handle. `LmStudioHelper`
     * sets this per turn; both the armed compat branch and the unarmed
     * fallback forward every created [Call] here (in addition to
     * [currentCall]) so `stopResponse()` tears down the live socket on
     * either path. Defaults to no-op for legacy direct uses.
     */
    @Volatile
    var callHook: (Call) -> Unit = {}

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

    /**
     * Quick-task (remote-image-carry): native `/api/v1/chat` input builder
     * with the shared K=3 carry. The flat input list keeps its exact
     * pre-carry order (current-turn images first, then per-turn text);
     * history USER turns selected by [HistoryImageCarry] additionally emit
     * their image items immediately before their own text item. TOOL rows
     * replay as plain-text summaries (Phase 49 DEL-01) and never carry.
     */
    internal fun buildNativeInput(
        messages: List<ChatMessage>,
        currentImages: List<String>,
    ): Pair<String?, List<LmStudioInputItem>> {
        val systemMessage = messages.firstOrNull { it.role == Role.SYSTEM }?.content
        val kept = HistoryImageCarry.selectKeptUrls(messages)
        val allInput = mutableListOf<LmStudioInputItem>()
        allInput += currentImages.map { LmStudioInputItem(type = "image", dataUrl = it) }
        messages.forEachIndexed { index, msg ->
            if (msg.role == Role.SYSTEM || msg.content.isBlank()) return@forEachIndexed
            kept[index]?.forEach { url ->
                allInput += LmStudioInputItem(type = "image", dataUrl = url)
            }
            val content = when (msg.role) {
                Role.USER -> inputSanitizer.sanitize(msg.content)
                Role.TOOL -> {
                    val idx = msg.content.indexOf('\n')
                    val toolId = if (idx < 0) msg.content else msg.content.substring(0, idx)
                    val summary = if (idx < 0) "" else msg.content.substring(idx + 1)
                    "Used ${toolDisplayNameCapitalized(toolId)}: $summary"
                }
                else -> msg.content
            }
            allInput += LmStudioInputItem(type = "text", content = content)
        }
        return systemMessage to allInput
    }

    fun chat(
        request: ChatRequest,
        integrations: List<LmStudioIntegration> = emptyList(),
        onCallCreated: (Call) -> Unit = {},
    ): Flow<StreamToken> = callbackFlow {
        val (systemMessage, allInput) = buildNativeInput(request.messages, request.images)

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
                                                handleSseEvent(eventType, event).forEach {
                                                    send(it)
                                                    if (it is StreamToken.Delta) hasTokens = true
                                                }
                                            }
                                            if (eventType == "chat.end") {
                                                event.result?.stats?.let { stats ->
                                                    statsText = "${stats.totalOutputTokens} tokens · ${stats.inputTokens} in · ${String.format(Locale.US, "%.0f", stats.tokensPerSecond)} tok/s · ${String.format(Locale.US, "%.1f", stats.timeToFirstTokenSeconds * 1000)}ms first"
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
                                    statsText = "${stats.totalOutputTokens} tokens · ${stats.inputTokens} in · ${String.format(Locale.US, "%.0f", stats.tokensPerSecond)} tok/s · ${String.format(Locale.US, "%.1f", stats.timeToFirstTokenSeconds * 1000)}ms first"
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
            // Phase 49 (DEL-01): no tools are ever sent, so the server
            // emits no tool_call.* events. tool_call.success output still
            // renders as plain text below; control markers are dropped.
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
                Timber.d("LMStudioProvider.listModels: GET $path")
                val response = api.listModelsByPath(path)
                Timber.d("LMStudioProvider.listModels: $path -> HTTP ${response.code()}")
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
                    Timber.d("LMStudioProvider.listModels: $path parsed ${models.size} models (lmV1=${lmModels.size} openAi=${openAiModels.size})")
                    if (models.isNotEmpty()) return Result.success(models)
                    // 200 OK with empty list — try next endpoint before giving up
                    lastError = IllegalStateException("$path returned empty list")
                } else {
                    lastError = Exception("$path -> HTTP ${response.code()} ${response.message()}")
                }
            } catch (e: Exception) {
                Timber.w(e, "LMStudioProvider.listModels: $path failed")
                lastError = e
            }
        }
        return Result.failure(lastError ?: IllegalStateException("No LM Studio endpoint responded with models"))
    }

    /**
     * Keys (plus selected variants) with at least one loaded instance,
     * per `GET /api/v1/models` `loaded_instances` (docs: the list shows
     * AVAILABLE models — presence alone never means loaded). Used by the
     * load-skip check. Failure/empty → empty set (fail-safe: reload).
     */
    suspend fun listLoadedModelKeys(): Result<Set<String>> {
        return try {
            val response = api.listModelsByPath("api/v1/models")
            if (!response.isSuccessful) {
                return Result.failure(Exception("HTTP ${response.code()}"))
            }
            val keys = response.body()?.models.orEmpty()
                .filter { it.loadedInstances.isNotEmpty() }
                .flatMap { listOfNotNull(it.key, it.selectedVariant) }
                .toSet()
            Result.success(keys)
        } catch (e: Exception) {
            Timber.w(e, "LMStudioProvider.listLoadedModelKeys failed")
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
                // Surface the server's reason (e.g. CUDA OOM,
                // model_load_failed) — a bare HTTP code leaves the user
                // guessing why the load died.
                val detail = try {
                    val raw = response.errorBody()?.string().orEmpty()
                    val msg = json.parseToJsonElement(raw).jsonObject["error"]
                        ?.jsonObject?.get("message")?.jsonPrimitive?.content
                    msg?.takeIf { it.isNotBlank() } ?: raw.take(200).ifBlank { null }
                } catch (_: Exception) {
                    null
                }
                val suffix = detail?.let { ": $it" } ?: ""
                Result.failure(Exception("Load failed: HTTP ${response.code()}$suffix"))
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
