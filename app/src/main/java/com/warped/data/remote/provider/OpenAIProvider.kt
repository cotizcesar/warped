package com.warped.data.remote.provider

import com.warped.data.agentic.LocalToolLoop
import com.warped.data.agentic.PendingToolCall
import com.warped.data.agentic.ToolCallAccumulator
import com.warped.data.agentic.ToolCapabilityMatrix
import com.warped.data.agentic.parseToolArgs
import com.warped.data.grounding.GroundingPrecedence
import com.warped.data.grounding.MultiUrlFetcher
import com.warped.data.grounding.DuckDuckGoSearchRepository
import com.warped.data.grounding.WebPageFetcher
import com.warped.data.local.inference.InputSanitizer
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.remote.api.OpenAiApi
import com.warped.data.remote.dto.OpenAiChatRequest
import com.warped.data.remote.dto.OpenAiCompletedToolCall
import com.warped.data.remote.dto.OpenAiCompletionsRequest
import com.warped.data.remote.dto.OpenAiEmbeddingsRequest
import com.warped.data.remote.dto.OpenAiFunctionCall
import com.warped.data.remote.dto.OpenAiMessage
import com.warped.data.remote.dto.OpenAiNonStreamingResponse
import com.warped.data.remote.dto.OpenAiResponsesRequest
import com.warped.data.remote.dto.OpenAiStreamChoice
import com.warped.data.remote.dto.OpenAiStreamChunk
import com.warped.data.remote.dto.OpenAiStreamDelta
import com.warped.data.remote.dto.OpenAiTool
import com.warped.data.remote.dto.defaultRemoteTools
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit
import timber.log.Timber
import kotlin.coroutines.coroutineContext

class OpenAIProvider(
    private val baseUrl: String,
    private val modelId: String,
    endpointId: Long,
    apiKey: String? = null,
    private val inputSanitizer: InputSanitizer,
    /**
     * Phase 57 (57-01): tool-loop collaborators (Phase 55/52 singletons).
     * All-null by default so the legacy `resolve()` path (listModels /
     * testConnection / unarmed turns) behaves exactly as before — the loop
     * only arms when every collaborator is present (see [isLoopArmed]).
     * ONLY; loop code never references it (T-57-02).
     */
    /**
     * Quick-task (DDG-default): web_search goes through the DDG-only
     * repository (keyless outcome — downstream mapping untouched).
     */
    private val ddg: DuckDuckGoSearchRepository? = null,
    private val multiUrlFetcher: MultiUrlFetcher? = null,
    private val webPageFetcher: WebPageFetcher? = null,
    private val advancedPreferences: AdvancedPreferences? = null,
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

    /**
     * Phase 57 (57-01): retained cancellable round Call (LMStudioProvider
     * precedent). `cancelChat()` tears down the in-flight socket; the read
     * loop exits silently with no trailing tokens and no fake Error bubble.
     * Safe when idle (no-op). Single-round handle: rounds run sequentially
     * per turn, and a fresh provider is created per turn upstream.
     */
    @Volatile
    private var currentCall: Call? = null

    /** Belt-and-braces teardown of the in-flight round, if any. */
    fun cancelChat() {
        currentCall?.cancel()
    }

    override fun chat(request: ChatRequest): Flow<StreamToken> = flow {
        // Quick-task (remote-image-carry): history image turns ride as
        // `image_url` parts (K=3 newest-first, shared rule); text-only
        // rows map exactly as before.
        val messages = mapOpenAiHistory(
            request.messages,
            includeSystem = false,
            sanitizeUser = inputSanitizer::sanitize,
        )
        // Phase 57 (57-01): unarmed turns keep the exact pre-57 plain path;
        // armed turns run the tools[] round driver below.
        if (!isLoopArmed(request.webOverride)) {
            postPlainTurn(messages, request)
            return@flow
        }
        runTooledLoop(messages, request)
    }.flowOn(Dispatchers.IO)

    /**
     * Phase 57 (57-01): provider-authoritative loop-arming (mirrors the
     * 56-02 `isLoopArmed` shape with the matrix added). Grounding precedence
     * resolves the per-chat override against the global default; the matrix
     * must send the OpenAI dialect; internet must be validated. Missing
     * collaborators or any gate failure reads as unarmed (plain turn),
     * never a crash.
     */
    private suspend fun isLoopArmed(perChat: Boolean?): Boolean {
        val prefs = advancedPreferences ?: return false
        val net = webPageFetcher ?: return false
        if (ddg == null || multiUrlFetcher == null) return false
        return try {
            val global = try {
                prefs.webGroundingEnabled.first()
            } catch (e: Exception) {
                Timber.w(e, "OpenAI: global grounding read failed, treating as off")
                false
            }
            val online = try {
                net.hasValidatedInternet()
            } catch (e: Exception) {
                Timber.w(e, "OpenAI: connectivity check failed, treating as offline")
                false
            }
            ToolCapabilityMatrix.isRemoteLoopArmed(
                groundingOn = GroundingPrecedence.shouldGround(perChat, global),
                matrixAttemptsTools = ToolCapabilityMatrix.attemptsTools(
                    ToolCapabilityMatrix.modeFor(ProviderType.OPENAI),
                ),
                hasValidatedInternet = online,
            )
        } catch (_: Exception) {
            false
        }
    }

    /** Phase 57 (57-01): the exact pre-57 single-turn path, unchanged. */
    private suspend fun FlowCollector<StreamToken>.postPlainTurn(
        messages: List<OpenAiMessage>,
        request: ChatRequest,
    ) {
        val round = postRound(messages, tools = null, request = request)
        if (round.aborted) return
        if (round.error != null) {
            emit(StreamToken.Error(round.error))
            return
        }
        if (round.hadTokens) emit(StreamToken.Done())
        else emit(StreamToken.Error("No content in response"))
    }

    /**
     * Phase 57 (57-01): agentic round driver — mirrors
     * `LiteRTLmProvider.runToolLoop` over HTTP rounds (RESEARCH skeleton).
     * `LocalToolLoop` owns every provider-neutral decision (cap, dispatch,
     * validation, outcome mapping, status copy); this driver only owns the
     * wire (rounds, echoes, retry). Round echoes stay in-memory only, never
     * persisted to Room (T-57-05).
     */
    private suspend fun FlowCollector<StreamToken>.runTooledLoop(
        baseMessages: List<OpenAiMessage>,
        request: ChatRequest,
    ) {
        var attachedTools: List<OpenAiTool>? = defaultRemoteTools()
        val roundMessages = baseMessages.toMutableList()
        var callsUsed = 0
        var capFed = false
        var fallbackDone = false
        val contextSize = request.parameters.contextSize
        while (true) {
            coroutineContext.ensureActive()
            val round = postRound(roundMessages, attachedTools, request)
            if (round.aborted) return
            if (round.error != null) {
                emit(StreamToken.Error(round.error))
                return
            }
            if (round.toolsRejected && attachedTools != null && !fallbackDone) {
                // Locked: exactly one retry of the same turn with tools
                // null and a clean plain-message replay (partial echoes
                // dropped — strict servers reject unpaired role:tool),
                // plus the visible notice through the error/notice token
                // path. The turn otherwise completes normally.
                fallbackDone = true
                attachedTools = null
                roundMessages.clear()
                roundMessages.addAll(baseMessages)
                emit(StreamToken.ToolsUnsupported)
                continue
            }
            val toolCalls = round.toolCalls
            if (toolCalls.isEmpty()) {
                if (round.hadTokens) emit(StreamToken.Done())
                else emit(StreamToken.Error("No content in response"))
                return
            }
            if (capFed) {
                Timber.w("OpenAI: model requested tools after the cap string — finishing with gathered context")
                emit(StreamToken.Done())
                return
            }
            val assistantEcho = mutableListOf<OpenAiCompletedToolCall>()
            val toolResults = mutableListOf<OpenAiMessage>()
            for (call in toolCalls) {
                coroutineContext.ensureActive()
                val result: String
                if (LocalToolLoop.isCapReached(callsUsed)) {
                    result = LocalToolLoop.CAP_REACHED_STRING
                    capFed = true
                } else {
                    // Calls counted in CALLS, not rounds (locked credit
                    // bound) — validation short-circuits included, so
                    // garbage args cannot spin forever.
                    callsUsed++
                    val argsMap = parseToolArgs(call.argumentsJson)
                    val shortCircuit = if (argsMap == null) {
                        LocalToolLoop.toolFailureMessage(call.argumentsJson)
                    } else {
                        LocalToolLoop.validateArgs(call.name.orEmpty(), argsMap)
                    }
                    result = if (shortCircuit != null) {
                        // 56-02 IN-02 precedent: no transient status row
                        // for calls that never execute.
                        shortCircuit
                    } else {
                        val canonical = LocalToolLoop.mapToolCallName(call.name.orEmpty())
                            ?: call.name.orEmpty()
                        val display = LocalToolLoop.statusDisplay(canonical, argsMap!!)
                            ?: canonical
                        emit(StreamToken.ToolStatus(display))
                        try {
                            // Quick-task (agentic-rows): executed calls
                            // surface their structured sources via
                            // ToolCompleted so the VM persists Fuentes rows
                            // on Done (same shape as local loop turns).
                            val outcome = executeRemoteTool(canonical, argsMap, contextSize, request.documentBlock)
                            // Quick-task (tool-failure-note): failures ride
                            // the transient errorReason note (never
                            // persisted, model-fed text untouched).
                            if (outcome.sources.isNotEmpty() || outcome.failed) {
                                emit(
                                    StreamToken.ToolCompleted(
                                        call.id,
                                        summarizeForTranscript(outcome.text),
                                        errorReason = if (outcome.failed) {
                                            LocalToolLoop.failureNote(canonical)
                                        } else {
                                            null
                                        },
                                        sources = outcome.sources,
                                        images = outcome.images,
                                    ),
                                )
                            }
                            outcome.text
                        } finally {
                            emit(StreamToken.ToolStatus(null))
                        }
                    }
                }
                // Pairing (Pitfall 2): the echo carries the EXACT id and
                // the complete arguments string; every echo gets its
                // role:tool answer in the same round.
                assistantEcho += OpenAiCompletedToolCall(
                    id = call.id,
                    function = OpenAiFunctionCall(
                        name = call.name.orEmpty(),
                        arguments = call.argumentsJson,
                    ),
                )
                toolResults += OpenAiMessage(
                    role = "tool",
                    content = result,
                    toolCallId = call.id,
                )
            }
            // The cap string answers from gathered context — the answer
            // round needs no tools[].
            if (capFed) attachedTools = null
            roundMessages += OpenAiMessage(role = "assistant", toolCalls = assistantEcho)
            roundMessages += toolResults
        }
    }

    /**
     * Phase 57 (57-01): single tool-call executor. Total — never throws (47
     * never-throw lesson): arg validation short-circuits pre-socket,
     * unknown names return the error string without executing, offline
     * opens no socket, and every failure degrades to a concise English
     * string the model continues from. CancellationException rethrows so
     * Stop bounds residual latency.
     *
     * T-57-02: executors are the Phase 55/52 singletons ONLY — the endpoint
     * client (and its Authorization key) is never in scope here.
     */
    private suspend fun executeRemoteTool(
        toolName: String,
        args: Map<String, Any?>,
        contextSize: Int,
        documentBlock: String?,
    ): LocalToolLoop.ToolCallOutcome {
        // Unknown names fail closed here — the body below never runs them.
        LocalToolLoop.validateArgs(toolName, args)?.let { return LocalToolLoop.ToolCallOutcome(it) }
        val online = try {
            webPageFetcher?.hasValidatedInternet() == true
        } catch (_: Exception) {
            false
        }
        return withContext(Dispatchers.IO) {
            when (LocalToolLoop.mapToolCallName(toolName)) {
                LocalToolLoop.TOOL_WEB_SEARCH -> {
                    if (!online) return@withContext LocalToolLoop.ToolCallOutcome(LocalToolLoop.OFFLINE_STRING)
                    val query = (args["query"] as? String).orEmpty()
                    val repo = ddg
                        ?: return@withContext LocalToolLoop.ToolCallOutcome(
                            LocalToolLoop.toolFailureMessage("search unavailable"),
                        )
                    try {
                        // Explicit args (no Kotlin defaults): keeps the call
                        // on the instance method so MockK can stub it.
                        // Quick-task (loop-images): includeImages=true ALWAYS
                        // — the DDG leg ignores it (no image API); loop
                        // search calls fuse zero images.
                        val outcome = repo.search(
                            query = query,
                            maxResults = DuckDuckGoSearchRepository.DEFAULT_MAX_RESULTS,
                            contextSize = contextSize,
                            includeImages = true,
                        )
                        LocalToolLoop.ToolCallOutcome(
                            text = LocalToolLoop.mapSearchOutcome(outcome),
                            sources = LocalToolLoop.searchSources(outcome),
                            images = LocalToolLoop.searchImages(outcome),
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "OpenAI: web_search failed")
                        LocalToolLoop.ToolCallOutcome(LocalToolLoop.toolFailureMessage(e.message.orEmpty()), failed = true)
                    }
                }
                LocalToolLoop.TOOL_WEB_FETCH -> {
                    if (!online) return@withContext LocalToolLoop.ToolCallOutcome(LocalToolLoop.OFFLINE_STRING)
                    val url = ((args["url"] as? String).orEmpty()).trim()
                    val fetcher = multiUrlFetcher
                        ?: return@withContext LocalToolLoop.ToolCallOutcome(
                            LocalToolLoop.toolFailureMessage("fetch unavailable"),
                        )
                    try {
                        val result = fetcher.fetchAll(
                            urls = listOf(url),
                            contextSize = contextSize,
                            onProgress = null,
                        )
                        LocalToolLoop.ToolCallOutcome(
                            text = LocalToolLoop.mapFetchResult(result),
                            sources = LocalToolLoop.fetchSources(result),
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "OpenAI: web_fetch failed")
                        LocalToolLoop.ToolCallOutcome(LocalToolLoop.toolFailureMessage(e.message.orEmpty()), failed = true)
                    }
                }
                LocalToolLoop.TOOL_READ_TEXT -> {
                    // No internet gate: the block is already in hand (fused
                    // into the turn by the VM) — a local file read needs no
                    // socket. Unbound attachment degrades, never throws;
                    // sources stay empty (the VM owns the document Fuentes
                    // row directly, so no double-emit through ToolCompleted).
                    val block = documentBlock
                    if (block.isNullOrBlank()) {
                        return@withContext LocalToolLoop.ToolCallOutcome(
                            LocalToolLoop.DOCUMENT_READ_FAILED_STRING,
                        )
                    }
                    LocalToolLoop.ToolCallOutcome(LocalToolLoop.mapDocumentResult(block))
                }
                else -> LocalToolLoop.ToolCallOutcome(LocalToolLoop.unknownToolMessage(toolName))
            }
        }
    }

    /** Phase 57 (57-01): ≤200-char single-line transcript summary. */
    private fun summarizeForTranscript(result: String): String =
        result.trim().replace(SUMMARY_WHITESPACE, " ").take(TRANSCRIPT_SUMMARY_MAX_CHARS)

    /**
     * Phase 57 (57-01): one POST `/v1/chat/completions` round. Emits text /
     * reasoning deltas as they arrive (existing `<think>` handling kept);
     * feeds `delta.tool_calls` fragments into a fresh per-round accumulator.
     *
     * Completeness signal is `finish_reason:"tool_calls"` (or `[DONE]`
     * after partial `tool_calls`); non-streaming servers (Pitfall 3: first
     * line is not SSE framing) decode `choices[].message.tool_calls`
     * directly — same loop entry, no separate path.
     *
     * Cancellation contract (LMStudioProvider precedent): CE rethrown
     * first, `IOException("Canceled")` from `Call.cancel()` silent, any
     * other failure a fatal error string.
     */
    private suspend fun FlowCollector<StreamToken>.postRound(
        messages: List<OpenAiMessage>,
        tools: List<OpenAiTool>?,
        request: ChatRequest,
    ): RoundResult {
        coroutineContext.ensureActive()
        val body = OpenAiChatRequest(
            model = modelId,
            messages = messages,
            stream = true,
            temperature = request.parameters.temperature,
            topP = request.parameters.topP,
            maxTokens = request.parameters.maxTokens,
            tools = tools,
        )
        val call: Call
        try {
            val jsonBody = json.encodeToString(OpenAiChatRequest.serializer(), body)
            val okHttpRequest = Request.Builder()
                .url(baseUrl.trimEnd('/') + "/v1/chat/completions")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()
            call = client.newCall(okHttpRequest)
            currentCall = call
        } catch (e: CancellationException) {
            currentCall = null
            throw e
        } catch (e: Exception) {
            currentCall = null
            return RoundResult(error = "Connection failed: ${e.message}")
        }
        try {
            call.execute().use { okHttpResponse ->
                if (!okHttpResponse.isSuccessful) {
                    val snippet = try {
                        okHttpResponse.body?.string().orEmpty().take(ERROR_BODY_SNIPPET_CHARS)
                    } catch (_: Exception) {
                        ""
                    }
                    val code = okHttpResponse.code
                    if (tools != null && ToolCapabilityMatrix.isToolsRejection(code, snippet)) {
                        return RoundResult(toolsRejected = true)
                    }
                    return RoundResult(error = "HTTP $code: ${okHttpResponse.message}")
                }
                val responseBody = okHttpResponse.body
                    ?: return RoundResult(error = "Empty response")
                val source = responseBody.source()
                val firstLine = source.readUtf8Line() ?: ""
                val isSse = firstLine.startsWith("event: ") || firstLine.startsWith("data: ")
                var hasTokens = false
                val accumulator = ToolCallAccumulator()
                var toolFinishSeen = false

                fun feedToolDeltas(delta: OpenAiStreamDelta?) {
                    delta?.toolCalls?.forEach { toolDelta ->
                        accumulator.feed(
                            index = toolDelta.index,
                            id = toolDelta.id,
                            name = toolDelta.function?.name,
                            argumentsFragment = toolDelta.function?.arguments,
                        )
                    }
                }

                if (isSse) {
                    // SSE streaming — read incrementally from source.
                    // WR-04: finish-gated reassembly (see CompatToolLoop) —
                    // a truncated partial accumulation never executes.
                    // IN-01: the `event:`-line `currentEvent` was assigned
                    // but never read — removed.
                    var reasoningOpen = false
                    var streamTruncated = false
                    if (firstLine.startsWith("data: ")) {
                        val data = firstLine.removePrefix("data: ").trim()
                        try {
                            val choice = parseSseChoice(json, data)
                            if (choice?.finishReason == TOOL_CALLS_FINISH_REASON) toolFinishSeen = true
                            val delta = choice?.delta
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
                            feedToolDeltas(delta)
                        } catch (e: Exception) { Timber.e(e, "OpenAI: SSE first-line delta parse failed") }
                    }
                    try {
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            when {
                                // "event: " lines carry no payload — the type
                                // rides inside each data JSON object.
                                line.startsWith("event: ") -> { /* no-op */ }
                                line.startsWith("data: ") -> {
                                    val data = line.removePrefix("data: ").trim()
                                    if (data == "[DONE]") {
                                        if (reasoningOpen) emit(StreamToken.Delta("</think>"))
                                        break
                                    }
                                    try {
                                        val choice = parseSseChoice(json, data)
                                        if (choice?.finishReason == TOOL_CALLS_FINISH_REASON) toolFinishSeen = true
                                        val delta = choice?.delta
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
                                        feedToolDeltas(delta)
                                    } catch (e: Exception) { Timber.e(e, "OpenAI: SSE delta parse failed") }
                                }
                                line.isEmpty() -> { /* frame separator */ }
                            }
                        }
                    } catch (e: IOException) {
                        streamTruncated = true
                        Timber.e(e, "OpenAI: SSE stream read failed")
                    }
                    // WR-04: never execute a truncated partial accumulation.
                    if (streamTruncated && !toolFinishSeen && accumulator.hasCalls()) {
                        return RoundResult(error = "Connection failed: stream truncated")
                    }
                    return RoundResult(
                        toolCalls = accumulator.complete(),
                        hadTokens = hasTokens,
                    )
                } else {
                    // Non-streaming JSON — read remaining + first line
                    val remaining = source.readUtf8()
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
                        val nonStreamingCalls = msg?.toolCalls?.mapIndexed { index, completed ->
                            PendingToolCall(
                                id = completed.id.ifBlank { "call_$index" },
                                name = completed.function.name.ifBlank { null },
                                argumentsJson = completed.function.arguments,
                            )
                        }.orEmpty()
                        return RoundResult(
                            toolCalls = nonStreamingCalls,
                            hadTokens = hasTokens,
                        )
                    } catch (e: Exception) { Timber.e(e, "OpenAI: non-streaming JSON parse failed") }
                    return RoundResult(hadTokens = hasTokens)
                }
            }
        } catch (e: CancellationException) {
            // Stop means stop: never map coroutine cancellation to an Error token.
            throw e
        } catch (e: IOException) {
            // Includes IOException("Canceled") from Call.cancel() teardown — silent.
            if (e.message?.contains("Canceled", ignoreCase = true) == true) {
                Timber.d(e, "OpenAI: round transport canceled")
                return RoundResult(aborted = true)
            }
            Timber.e(e, "OpenAI: round transport failed")
            return RoundResult(error = "Connection failed: ${e.message}")
        } catch (e: Exception) {
            return RoundResult(error = "Connection failed: ${e.message}")
        } finally {
            currentCall = null
        }
    }

    private fun parseSseChoice(json: Json, data: String): OpenAiStreamChoice? {
        return try {
            json.decodeFromString<OpenAiStreamChunk>(data).choices.firstOrNull()
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

    companion object {
        /** `finish_reason` value signaling a complete `tool_calls` round. */
        private const val TOOL_CALLS_FINISH_REASON = "tool_calls"

        /** Error-body window fed to the `tools[]`-rejection classifier. */
        private const val ERROR_BODY_SNIPPET_CHARS = 2000

        /** `ToolCompleted` transcript summary cap (≤200 chars). */
        private const val TRANSCRIPT_SUMMARY_MAX_CHARS = 200

        private val SUMMARY_WHITESPACE = Regex("\\s+")
    }
}

/**
 * Phase 57 (57-01): one POST round outcome. Fatal [error] (existing error
 * path, unchanged) stops the turn; [toolsRejected] triggers exactly one
 * retry without tools plus the visible notice; [aborted] (Stop) emits
 * nothing and stops.
 */
private data class RoundResult(
    val toolCalls: List<PendingToolCall> = emptyList(),
    val hadTokens: Boolean = false,
    val error: String? = null,
    val toolsRejected: Boolean = false,
    val aborted: Boolean = false,
)
