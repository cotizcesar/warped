package com.warped.data.remote.provider

import com.warped.data.agentic.LocalToolLoop
import com.warped.data.agentic.PendingToolCall
import com.warped.data.agentic.ToolCallAccumulator
import com.warped.data.agentic.ToolCapabilityMatrix
import com.warped.data.agentic.ToolMode
import com.warped.data.agentic.parseToolArgs
import com.warped.data.grounding.GroundingPrecedence
import com.warped.data.grounding.MultiUrlFetcher
import com.warped.data.grounding.DuckDuckGoSearchRepository
import com.warped.data.grounding.TavilySearchRepository
import com.warped.data.grounding.WebPageFetcher
import com.warped.data.local.inference.InputSanitizer
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.remote.api.AnthropicApi
import com.warped.data.remote.dto.AnthropicChatRequest
import com.warped.data.remote.dto.AnthropicMessage
import com.warped.data.remote.dto.AnthropicNonStreamingResponse
import com.warped.data.remote.dto.AnthropicSseEvent
import com.warped.data.remote.dto.AnthropicThinking
import com.warped.data.remote.dto.AnthropicTool
import com.warped.data.remote.dto.defaultAnthropicTools
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.ModelInfo
import com.warped.domain.model.ProviderType
import com.warped.domain.model.Role
import com.warped.domain.model.StreamToken
import com.warped.domain.model.toProviderText
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

class AnthropicProvider(
    private val baseUrl: String,
    private val modelId: String,
    apiKey: String?,
    private val inputSanitizer: InputSanitizer,
    /**
     * Phase 57 (57-02): tool-loop collaborators (Phase 55/52 singletons).
     * All-null by default so the legacy `resolve()` path (listModels /
     * testConnection / unarmed turns) behaves exactly as before — the loop
     * only arms when every collaborator is present (see [isLoopArmed]).
     * The endpoint key stays on this client's interceptor ONLY; loop code
     * never references it (T-57-07).
     */
    /**
     * Quick-task (DDG-default): web_search goes through the DDG-primary /
     * Tavily-fallback repository (same outcome type — downstream mapping
     * untouched).
     */
    private val ddg: DuckDuckGoSearchRepository? = null,
    private val multiUrlFetcher: MultiUrlFetcher? = null,
    private val webPageFetcher: WebPageFetcher? = null,
    private val advancedPreferences: AdvancedPreferences? = null,
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

    /**
     * Phase 57 (57-02): retained cancellable round Call (LMStudioProvider
     * precedent, same as the 57-01 OpenAI loop). `cancelChat()` tears down
     * the in-flight socket; the read loop exits silently with no trailing
     * tokens and no fake Error bubble. Safe when idle (no-op).
     */
    @Volatile
    private var currentCall: Call? = null

    /** Belt-and-braces teardown of the in-flight round, if any. */
    fun cancelChat() {
        currentCall?.cancel()
    }

    override fun chat(request: ChatRequest): Flow<StreamToken> = flow {
        val systemMessage = request.messages.firstOrNull { it.role.name == "SYSTEM" }?.content
        val baseMessages = request.messages
            .filter { it.role.name != "SYSTEM" && it.content.isNotBlank() }
            .map {
                // Phase 49 (DEL-01): Anthropic has no tool role (only
                // user/assistant are valid) — TOOL rows replay as
                // user-adjacent text.
                if (it.role == Role.TOOL) {
                    val (role, text) = it.toProviderText()
                    AnthropicMessage.text(role, text)
                } else {
                    val content = if (it.role == Role.USER) inputSanitizer.sanitize(it.content) else it.content
                    AnthropicMessage.text(it.role.name.lowercase(), content)
                }
            }
        // Phase 57 (57-02): unarmed turns keep the exact pre-57 plain path;
        // armed turns run the native tools/tool_use/tool_result round driver.
        if (!isLoopArmed(request.webOverride)) {
            postPlainTurn(systemMessage, baseMessages, request)
            return@flow
        }
        runTooledLoop(systemMessage, baseMessages, request)
    }.flowOn(Dispatchers.IO)

    /**
     * Phase 57 (57-02): provider-authoritative loop-arming. Grounding
     * precedence resolves the per-chat override against the global default;
     * the matrix must select the native Anthropic dialect; internet must be
     * validated. Missing collaborators or any gate failure reads as unarmed
     * (plain turn), never a crash.
     */
    private suspend fun isLoopArmed(perChat: Boolean?): Boolean {
        val prefs = advancedPreferences ?: return false
        val net = webPageFetcher ?: return false
        if (ddg == null || multiUrlFetcher == null) return false
        if (ToolCapabilityMatrix.modeFor(ProviderType.ANTHROPIC) != ToolMode.NATIVE_ANTHROPIC) return false
        return try {
            val global = try {
                prefs.webGroundingEnabled.first()
            } catch (e: Exception) {
                Timber.w(e, "Anthropic: global grounding read failed, treating as off")
                false
            }
            val online = try {
                net.hasValidatedInternet()
            } catch (e: Exception) {
                Timber.w(e, "Anthropic: connectivity check failed, treating as offline")
                false
            }
            // NATIVE_ANTHROPIC never sends the OpenAI dialect, so the
            // shared AND-predicate is fed a literal true for the
            // matrix-attempts-tools input (the mode check above already
            // passed) — grounding off or unvalidated internet each still
            // force the exact pre-57 plain-turn behavior.
            ToolCapabilityMatrix.isRemoteLoopArmed(
                groundingOn = GroundingPrecedence.shouldGround(perChat, global),
                matrixAttemptsTools = true,
                hasValidatedInternet = online,
            )
        } catch (_: Exception) {
            false
        }
    }

    /** Phase 57 (57-02): the exact pre-57 single-turn path, unchanged. */
    private suspend fun FlowCollector<StreamToken>.postPlainTurn(
        systemMessage: String?,
        messages: List<AnthropicMessage>,
        request: ChatRequest,
    ) {
        val body = buildBody(systemMessage, messages, tools = null, request = request)
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

    /** Phase 57 (57-02): one request body for plain and tooled rounds alike. */
    private fun buildBody(
        systemMessage: String?,
        messages: List<AnthropicMessage>,
        tools: List<AnthropicTool>?,
        request: ChatRequest,
    ): AnthropicChatRequest = AnthropicChatRequest(
        model = modelId,
        maxTokens = request.parameters.maxTokens.takeIf { it > 0 } ?: 4096,
        messages = messages,
        system = systemMessage,
        stream = true,
        temperature = request.parameters.temperature,
        topP = request.parameters.topP,
        topK = request.parameters.topK,
        thinking = if (request.parameters.reasoningEnabled != false) {
            val budget = (request.parameters.maxTokens * 0.75).toInt().coerceIn(1024, 8192)
            AnthropicThinking(type = "enabled", budgetTokens = budget)
        } else null,
        tools = tools,
    )

    /**
     * Phase 57 (57-02): agentic round driver — the 57-01 OpenAI loop
     * translated to the native Anthropic dialect (RESEARCH Pattern 4).
     * `LocalToolLoop` owns every provider-neutral decision (cap, dispatch,
     * validation, outcome mapping, status copy); this driver only owns the
     * wire (rounds, echoes, retry). Round echoes stay in-memory only, never
     * persisted to Room (T-57-05). Parallel `tool_use` blocks each count
     * toward the call-counted cap. Never sends any `tool_choice`
     * equivalent.
     */
    private suspend fun FlowCollector<StreamToken>.runTooledLoop(
        systemMessage: String?,
        baseMessages: List<AnthropicMessage>,
        request: ChatRequest,
    ) {
        var attachedTools: List<AnthropicTool>? = defaultAnthropicTools()
        val roundMessages = baseMessages.toMutableList()
        var callsUsed = 0
        var capFed = false
        var fallbackDone = false
        val contextSize = request.parameters.contextSize
        while (true) {
            coroutineContext.ensureActive()
            val round = postRound(systemMessage, roundMessages, attachedTools, request)
            if (round.aborted) return
            if (round.error != null) {
                emit(StreamToken.Error(round.error))
                return
            }
            if (round.toolsRejected && attachedTools != null && !fallbackDone) {
                // Locked: exactly one retry of the same turn without tools
                // and a clean plain-message replay (partial echoes
                // dropped), plus the visible notice through the
                // error/notice token path. The turn otherwise completes
                // normally.
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
                Timber.w("Anthropic: model requested tools after the cap string — finishing with gathered context")
                emit(StreamToken.Done())
                return
            }
            val textParts = round.textParts.filter { it.isNotEmpty() }
            val toolResults = mutableListOf<Pair<String, String>>()
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
                            val outcome = executeRemoteTool(canonical, argsMap, contextSize)
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
                toolResults += call.id to result
            }
            // The cap string answers from gathered context — the answer
            // round needs no tools.
            if (capFed) attachedTools = null
            roundMessages += AnthropicMessage.toolUseEcho(textParts, toolCalls, json)
            roundMessages += AnthropicMessage.toolResults(toolResults)
        }
    }

    /**
     * Phase 57 (57-02): single tool-call executor — the 57-01 OpenAI
     * executor verbatim. Total — never throws: arg validation
     * short-circuits pre-socket, unknown names return the error string
     * without executing, offline opens no socket, and every failure
     * degrades to a concise English string the model continues from.
     * CancellationException rethrows so Stop bounds residual latency.
     *
     * T-57-07: executors are the Phase 55/52 singletons ONLY — the endpoint
     * client (and its x-api-key) is never in scope here.
     */
    private suspend fun executeRemoteTool(
        toolName: String,
        args: Map<String, Any?>,
        contextSize: Int,
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
                        // — the DDG leg ignores it; only the keyed Tavily
                        // fallback leg uses it (credit-capped).
                        val outcome = repo.search(
                            query = query,
                            maxResults = TavilySearchRepository.DEFAULT_MAX_RESULTS,
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
                        Timber.w(e, "Anthropic: web_search failed")
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
                        Timber.w(e, "Anthropic: web_fetch failed")
                        LocalToolLoop.ToolCallOutcome(LocalToolLoop.toolFailureMessage(e.message.orEmpty()), failed = true)
                    }
                }
                else -> LocalToolLoop.ToolCallOutcome(LocalToolLoop.unknownToolMessage(toolName))
            }
        }
    }

    /** Phase 57 (57-02): ≤200-char single-line transcript summary. */
    private fun summarizeForTranscript(result: String): String =
        result.trim().replace(SUMMARY_WHITESPACE, " ").take(TRANSCRIPT_SUMMARY_MAX_CHARS)

    /**
     * Phase 57 (57-02): one POST `/v1/messages` round. Emits text /
     * thinking deltas as they arrive (existing `<think>` handling kept);
     * feeds `input_json_delta`/`partial_json` fragments into a fresh
     * per-round [ToolCallAccumulator] keyed by content-block index (ids
     * from `content_block_start`, missing ids tolerated exactly like the
     * OpenAI dialect).
     *
     * Completeness signal is `stop_reason:"tool_use"` (carried on
     * `message_delta`); non-streaming servers (first line is not SSE
     * framing) decode the `content[]` blocks directly — same loop entry,
     * no separate path.
     *
     * Cancellation contract (LMStudioProvider precedent): CE rethrown
     * first, `IOException("Canceled")` from `Call.cancel()` silent, any
     * other failure a fatal error string.
     */
    private suspend fun FlowCollector<StreamToken>.postRound(
        systemMessage: String?,
        messages: List<AnthropicMessage>,
        tools: List<AnthropicTool>?,
        request: ChatRequest,
    ): AnthropicRoundResult {
        coroutineContext.ensureActive()
        val body = buildBody(systemMessage, messages, tools, request)
        val call: Call
        try {
            val jsonBody = json.encodeToString(AnthropicChatRequest.serializer(), body)
            val okHttpRequest = Request.Builder()
                .url(baseUrl.trimEnd('/') + "/v1/messages")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .header("anthropic-version", "2023-06-01")
                .build()
            call = client.newCall(okHttpRequest)
            currentCall = call
        } catch (e: CancellationException) {
            currentCall = null
            throw e
        } catch (e: Exception) {
            currentCall = null
            return AnthropicRoundResult(error = "Connection failed: ${e.message}")
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
                    // WR-05: thinking-shape 400s (naming `thinking` /
                    // `signature`, never `tool`) ride the same exactly-one
                    // graceful fallback as tools[] rejections.
                    if (tools != null && (
                        ToolCapabilityMatrix.isToolsRejection(code, snippet) ||
                            ToolCapabilityMatrix.isAnthropicThinkingRejection(code, snippet)
                        )
                    ) {
                        return AnthropicRoundResult(toolsRejected = true)
                    }
                    return AnthropicRoundResult(error = "HTTP $code: ${okHttpResponse.message}")
                }
                val responseBody = okHttpResponse.body
                    ?: return AnthropicRoundResult(error = "Empty response")
                val source = responseBody.source()
                val firstLine = source.readUtf8Line() ?: ""
                val isSse = firstLine.startsWith("event: ") || firstLine.startsWith("data: ")
                var hasTokens = false
                val textParts = mutableListOf<String>()
                val accumulator = ToolCallAccumulator()
                var stopReason: String? = null

                if (isSse) {
                    var thinkingOpen = false
                    // WR-03: transport-level "error" events are data, never
                    // mid-round emits — a mid-round Error token would drive
                    // the VM into the error terminal state while the driver
                    // keeps running, and the driver would then emit a second
                    // terminal Error. Collected here, returned as
                    // AnthropicRoundResult.error below (emitted exactly once
                    // by the caller).
                    var pendingError: String? = null
                    // Single-event feeder. Suspend so emits stay direct
                    // (same-coroutine backpressure); total — decode
                    // failures log and continue, unknown types ignored,
                    // fragment reassembly tolerates missing ids via the
                    // accumulator. Returns true on `message_stop`/`error`.
                    suspend fun feedEvent(data: String): Boolean {
                        val event = try {
                            json.decodeFromString<AnthropicSseEvent>(data)
                        } catch (e: Exception) {
                            Timber.e(e, "Anthropic: SSE event parse failed")
                            return false
                        }
                        when (event.type) {
                            "content_block_start" -> {
                                val block = event.contentBlock
                                if (block?.type == "tool_use") {
                                    accumulator.feed(
                                        index = event.index ?: 0,
                                        id = block.id,
                                        name = block.name,
                                        argumentsFragment = null,
                                    )
                                }
                            }
                            "content_block_delta" -> {
                                val delta = event.delta ?: return false
                                // WR-05: provider-synthesized thinking is UI
                                // signal only — it must never enter
                                // `textParts` (the in-memory assistant echo).
                                // Flattened `<think>` markers replayed as
                                // plain `text` blocks make strict endpoints
                                // 400 the follow-up round; the echo carries
                                // user-visible text + `tool_use` only.
                                if (!delta.thinking.isNullOrEmpty()) {
                                    if (!thinkingOpen) {
                                        emit(StreamToken.Delta("<think>"))
                                        thinkingOpen = true
                                    }
                                    emit(StreamToken.Delta(delta.thinking))
                                    hasTokens = true
                                } else if (delta.text != null) {
                                    if (thinkingOpen) {
                                        emit(StreamToken.Delta("</think>"))
                                        thinkingOpen = false
                                    }
                                    emit(StreamToken.Delta(delta.text))
                                    textParts += delta.text
                                    hasTokens = true
                                }
                                // IN-04: only `input_json_delta` fragments
                                // feed tool reassembly — a text-block index
                                // colliding with a tool-use index must never
                                // poison the accumulator.
                                if (delta.type == INPUT_JSON_DELTA_TYPE) {
                                    delta.partialJson?.let { fragment ->
                                        accumulator.feed(
                                            index = event.index ?: 0,
                                            id = null,
                                            name = null,
                                            argumentsFragment = fragment,
                                        )
                                    }
                                }
                            }
                            "message_delta" -> {
                                event.delta?.stopReason?.let { stopReason = it }
                            }
                            "message_stop" -> return true
                            "error" -> {
                                pendingError =
                                    event.delta?.text ?: event.delta?.thinking ?: "Anthropic error"
                                return true
                            }
                        }
                        return false
                    }
                    if (firstLine.startsWith("data: ")) {
                        if (feedEvent(firstLine.removePrefix("data: ").trim())) {
                            // A single-line `message_stop`/`error` ends the round.
                            pendingError?.let { return AnthropicRoundResult(error = it) }
                            return AnthropicRoundResult(
                                textParts = textParts.toList(),
                                hadTokens = hasTokens,
                            )
                        }
                    }
                    try {
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            when {
                                line.startsWith("data: ") -> {
                                    val data = line.removePrefix("data: ").trim()
                                    if (feedEvent(data)) break
                                }
                                line.isEmpty() -> { /* frame separator */ }
                                // "event: " lines carry no payload — the type
                                // rides inside each data JSON object.
                            }
                        }
                    } catch (e: IOException) {
                        Timber.e(e, "Anthropic: SSE stream read failed")
                    }
                    pendingError?.let { return AnthropicRoundResult(error = it) }
                    if (thinkingOpen) {
                        emit(StreamToken.Delta("</think>"))
                    }
                    val toolCalls =
                        if (stopReason == TOOL_USE_STOP_REASON) accumulator.complete()
                        else emptyList()
                    return AnthropicRoundResult(
                        toolCalls = toolCalls,
                        textParts = textParts.toList(),
                        hadTokens = hasTokens,
                    )
                } else {
                    // Non-streaming JSON — read remaining + first line.
                    val remaining = source.readUtf8()
                    val rawBody = firstLine + "\n" + remaining
                    try {
                        val result = json.decodeFromString<AnthropicNonStreamingResponse>(rawBody)
                        result.content.forEach { block ->
                            when (block.type) {
                                "text" -> {
                                    block.text?.let {
                                        emit(StreamToken.Delta(it))
                                        textParts += it
                                        hasTokens = true
                                    }
                                }
                                "thinking" -> {
                                    // WR-05: thinking blocks stay UI-only —
                                    // never echoed (see content_block_delta).
                                    block.thinking?.let {
                                        emit(StreamToken.Delta("<think>$it</think>"))
                                        hasTokens = true
                                    }
                                }
                            }
                        }
                        val nonStreamingCalls =
                            if (result.stopReason == TOOL_USE_STOP_REASON) {
                                result.content.filter { it.type == "tool_use" }.mapIndexed { index, block ->
                                    PendingToolCall(
                                        id = block.id?.takeIf { it.isNotBlank() }
                                            ?: "call_$index",
                                        name = block.name?.ifBlank { null },
                                        argumentsJson = block.input?.toString().orEmpty(),
                                    )
                                }
                            } else emptyList()
                        return AnthropicRoundResult(
                            toolCalls = nonStreamingCalls,
                            textParts = textParts.toList(),
                            hadTokens = hasTokens,
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "Anthropic: non-streaming JSON parse failed")
                    }
                    return AnthropicRoundResult(
                        textParts = textParts.toList(),
                        hadTokens = hasTokens,
                    )
                }
            }
        } catch (e: CancellationException) {
            // Stop means stop: never map coroutine cancellation to an Error token.
            throw e
        } catch (e: IOException) {
            // Includes IOException("Canceled") from Call.cancel() teardown — silent.
            if (e.message?.contains("Canceled", ignoreCase = true) == true) {
                Timber.d(e, "Anthropic: round transport canceled")
                return AnthropicRoundResult(aborted = true)
            }
            Timber.e(e, "Anthropic: round transport failed")
            return AnthropicRoundResult(error = "Connection failed: ${e.message}")
        } catch (e: Exception) {
            return AnthropicRoundResult(error = "Connection failed: ${e.message}")
        } finally {
            currentCall = null
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
                messages = listOf(AnthropicMessage.text("user", "Hi")),
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
        /** `stop_reason` value signaling a complete `tool_use` round. */
        private const val TOOL_USE_STOP_REASON = "tool_use"

        /** `content_block_delta` type carrying tool-input fragments. */
        private const val INPUT_JSON_DELTA_TYPE = "input_json_delta"

        /** Error-body window fed to the `tools`-rejection classifier. */
        private const val ERROR_BODY_SNIPPET_CHARS = 2000

        /** `ToolCompleted` transcript summary cap (≤200 chars). */
        private const val TRANSCRIPT_SUMMARY_MAX_CHARS = 200

        private val SUMMARY_WHITESPACE = Regex("\\s+")

        private fun parseAnthropicSse(body: okhttp3.ResponseBody, json: Json): Flow<StreamToken> = flow {
            val source = body.source()
            // IN-01: the `event:`-line payload was assigned but never read
            // (the type rides inside data JSON) — lines consumed as no-ops.
            var thinkingOpen = false
            try {
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    when {
                        line.startsWith("event: ") -> { /* no-op */ }
                        line.startsWith("data: ") -> {
                            val data = line.removePrefix("data: ").trim()
                            try {
                                val event = json.decodeFromString<AnthropicSseEvent>(data)
                                when (event.type) {
                                    "content_block_start" -> { /* marker */ }
                                    "content_block_delta" -> {
                                        if (event.delta?.thinking != null) {
                                            if (!thinkingOpen) {
                                                emit(StreamToken.Delta("<think>"))
                                                thinkingOpen = true
                                            }
                                            emit(StreamToken.Delta(event.delta.thinking))
                                        } else if (event.delta?.text != null) {
                                            if (thinkingOpen) {
                                                emit(StreamToken.Delta("</think>"))
                                                thinkingOpen = false
                                            }
                                            emit(StreamToken.Delta(event.delta.text))
                                        }
                                    }
                                    "content_block_stop" -> { /* marker */ }
                                    "message_delta" -> { }
                                    "message_stop" -> {
                                        if (thinkingOpen) emit(StreamToken.Delta("</think>"))
                                        emit(StreamToken.Done())
                                        return@flow
                                    }
                                    "error" -> {
                                        val errorText = event.delta?.text ?: event.delta?.thinking ?: "Anthropic error"
                                        emit(StreamToken.Error(errorText))
                                        return@flow
                                    }
                                }
                            } catch (e: Exception) { Timber.e(e, "Anthropic: SSE event parse failed") }
                        }
                        line.isEmpty() -> { /* frame separator */ }
                    }
                }
                if (thinkingOpen) emit(StreamToken.Delta("</think>"))
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

/**
 * Phase 57 (57-02): one POST round outcome. Fatal [error] (existing error
 * path, unchanged) stops the turn; [toolsRejected] triggers exactly one
 * retry without tools plus the visible notice; [aborted] (Stop) emits
 * nothing and stops. [textParts] accumulate the round's user-visible text
 * for the in-memory assistant echo.
 */
private data class AnthropicRoundResult(
    val toolCalls: List<PendingToolCall> = emptyList(),
    val textParts: List<String> = emptyList(),
    val hadTokens: Boolean = false,
    val error: String? = null,
    val toolsRejected: Boolean = false,
    val aborted: Boolean = false,
)
