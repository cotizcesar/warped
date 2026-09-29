package com.warped.data.remote.provider

import com.warped.data.agentic.LocalToolLoop
import com.warped.data.agentic.PendingToolCall
import com.warped.data.agentic.ToolCallAccumulator
import com.warped.data.agentic.ToolCapabilityMatrix
import com.warped.data.agentic.parseToolArgs
import com.warped.data.grounding.MultiUrlFetcher
import com.warped.data.grounding.TavilySearchRepository
import com.warped.data.grounding.WebPageFetcher
import com.warped.data.remote.dto.OpenAiChatRequest
import com.warped.data.remote.dto.OpenAiCompletedToolCall
import com.warped.data.remote.dto.OpenAiFunctionCall
import com.warped.data.remote.dto.OpenAiMessage
import com.warped.data.remote.dto.OpenAiNonStreamingResponse
import com.warped.data.remote.dto.OpenAiStreamChoice
import com.warped.data.remote.dto.OpenAiStreamChunk
import com.warped.data.remote.dto.OpenAiStreamDelta
import com.warped.data.remote.dto.OpenAiTool
import com.warped.data.remote.dto.defaultRemoteTools
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.StreamToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import timber.log.Timber
import kotlin.coroutines.coroutineContext

/**
 * Phase 57 (57-02): shared OpenAI-compat `tools[]` round driver for the
 * attempt-then-fallback dialects (Ollama `/v1`, LM Studio `/v1`, Custom
 * `chatPath`).
 *
 * This is the 57-01 `OpenAIProvider` round loop reused verbatim — same
 * call-counted 5-call cap, same `validateArgs` pre-socket short-circuit,
 * same singleton executors on `Dispatchers.IO`, same
 * `mapSearchOutcome`/`mapFetchResult` mapping, same
 * `ToolStatus`/`ToolCompleted` rows with `finally`-clear, same in-memory
 * echoes only, same retained-`Call` cancel contract, same non-streaming
 * `tool_calls` handling (Pitfall 3), and the same exactly-one retry
 * without tools plus `TOOLS_UNSUPPORTED_NOTICE` on a 400-class
 * `tools[]` rejection (never silent, never more than one retry). Never
 * sends `tool_choice`.
 *
 * Provider-specific differences stay at the call site: the POST URL, the
 * base-message mapping (sanitization is per-provider), the arming gate,
 * and the `Call` retention. `LocalToolLoop` owns every provider-neutral
 * decision; this driver only owns the wire. Round echoes stay in-memory
 * only, never persisted to Room.
 *
 * Ollama envelope rule (Pitfall 6): compat-path rounds use the Chat
 * Completions envelope throughout (`tool_calls` echo +
 * `role:"tool"`/`tool_call_id` results) — never the native
 * `tool_name` envelope.
 */
internal object CompatToolLoop {

    /**
     * Runs one full agentic turn: attach `tools[]`, loop POST rounds until
     * the model answers without tool calls (or the cap/retry rules stop
     * the loop), emitting text deltas and tool progress along the way.
     * Terminal state is always `Done` or a single fatal `Error`.
     */
    suspend fun FlowCollector<StreamToken>.runTurn(
        client: OkHttpClient,
        json: Json,
        postUrl: String,
        modelId: String,
        baseMessages: List<OpenAiMessage>,
        request: ChatRequest,
        tavily: TavilySearchRepository,
        multiUrlFetcher: MultiUrlFetcher,
        webPageFetcher: WebPageFetcher,
        logTag: String,
        onCallCreated: (Call) -> Unit,
        onCallCleared: () -> Unit,
    ) {
        var attachedTools: List<OpenAiTool>? = defaultRemoteTools()
        val roundMessages = baseMessages.toMutableList()
        var callsUsed = 0
        var capFed = false
        var fallbackDone = false
        val contextSize = request.parameters.contextSize
        while (true) {
            coroutineContext.ensureActive()
            val round = postRound(
                client, json, postUrl, modelId, roundMessages, attachedTools, request,
                onCallCreated, onCallCleared,
            )
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
                emit(StreamToken.Error(ToolCapabilityMatrix.TOOLS_UNSUPPORTED_NOTICE))
                continue
            }
            val toolCalls = round.toolCalls
            if (toolCalls.isEmpty()) {
                if (round.hadTokens) emit(StreamToken.Done())
                else emit(StreamToken.Error("No content in response"))
                return
            }
            if (capFed) {
                Timber.w("$logTag: model requested tools after the cap string — finishing with gathered context")
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
                            val outcome = executeRemoteTool(
                                canonical, argsMap, contextSize,
                                tavily, multiUrlFetcher, webPageFetcher, logTag,
                            )
                            emit(StreamToken.ToolCompleted(call.id, summarizeForTranscript(outcome)))
                            outcome
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
     * Single tool-call executor — the 57-01 OpenAI executor verbatim.
     * Total — never throws: arg validation short-circuits pre-socket,
     * unknown names return the error string without executing, offline
     * opens no socket, and every failure degrades to a concise English
     * string the model continues from. CancellationException rethrows so
     * Stop bounds residual latency.
     *
     * Secret isolation: executors are the Phase 55/52 singletons ONLY —
     * the endpoint client (and its Authorization key) is never in scope
     * here (proven by `RemoteSecretIsolationTest` with distinct fake keys).
     */
    private suspend fun executeRemoteTool(
        toolName: String,
        args: Map<String, Any?>,
        contextSize: Int,
        tavily: TavilySearchRepository,
        multiUrlFetcher: MultiUrlFetcher,
        webPageFetcher: WebPageFetcher,
        logTag: String,
    ): String {
        // Unknown names fail closed here — the body below never runs them.
        LocalToolLoop.validateArgs(toolName, args)?.let { return it }
        val online = try {
            webPageFetcher.hasValidatedInternet()
        } catch (_: Exception) {
            false
        }
        return withContext(Dispatchers.IO) {
            when (LocalToolLoop.mapToolCallName(toolName)) {
                LocalToolLoop.TOOL_WEB_SEARCH -> {
                    if (!online) return@withContext LocalToolLoop.OFFLINE_STRING
                    val query = (args["query"] as? String).orEmpty()
                    try {
                        // Explicit args (no Kotlin defaults): keeps the call
                        // on the instance method so MockK can stub it.
                        LocalToolLoop.mapSearchOutcome(
                            tavily.search(
                                query = query,
                                maxResults = TavilySearchRepository.DEFAULT_MAX_RESULTS,
                                contextSize = contextSize,
                            ),
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "$logTag: web_search failed")
                        LocalToolLoop.toolFailureMessage(e.message.orEmpty())
                    }
                }
                LocalToolLoop.TOOL_WEB_FETCH -> {
                    if (!online) return@withContext LocalToolLoop.OFFLINE_STRING
                    val url = ((args["url"] as? String).orEmpty()).trim()
                    try {
                        LocalToolLoop.mapFetchResult(
                            multiUrlFetcher.fetchAll(
                                urls = listOf(url),
                                contextSize = contextSize,
                                onProgress = null,
                            ),
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "$logTag: web_fetch failed")
                        LocalToolLoop.toolFailureMessage(e.message.orEmpty())
                    }
                }
                else -> LocalToolLoop.unknownToolMessage(toolName)
            }
        }
    }

    /** ≤200-char single-line transcript summary. */
    private fun summarizeForTranscript(result: String): String =
        result.trim().replace(SUMMARY_WHITESPACE, " ").take(TRANSCRIPT_SUMMARY_MAX_CHARS)

    /**
     * One POST round. Emits text / reasoning deltas as they arrive;
     * feeds `delta.tool_calls` fragments into a fresh per-round
     * accumulator. Completeness signal is `finish_reason:"tool_calls"`
     * (or `[DONE]` after partial `tool_calls`); non-streaming servers
     * (Pitfall 3) decode `choices[].message.tool_calls` directly.
     *
     * Cancellation contract (LMStudioProvider precedent): CE rethrown
     * first, `IOException("Canceled")` from `Call.cancel()` silent, any
     * other failure a fatal error string.
     */
    private suspend fun FlowCollector<StreamToken>.postRound(
        client: OkHttpClient,
        json: Json,
        postUrl: String,
        modelId: String,
        messages: List<OpenAiMessage>,
        tools: List<OpenAiTool>?,
        request: ChatRequest,
        onCallCreated: (Call) -> Unit,
        onCallCleared: () -> Unit,
    ): CompatRoundResult {
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
                .url(postUrl)
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()
            call = client.newCall(okHttpRequest)
            onCallCreated(call)
        } catch (e: CancellationException) {
            onCallCleared()
            throw e
        } catch (e: Exception) {
            onCallCleared()
            return CompatRoundResult(error = "Connection failed: ${e.message}")
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
                    if (body.tools != null && ToolCapabilityMatrix.isToolsRejection(code, snippet)) {
                        return CompatRoundResult(toolsRejected = true)
                    }
                    return CompatRoundResult(error = "HTTP $code: ${okHttpResponse.message}")
                }
                val responseBody = okHttpResponse.body
                    ?: return CompatRoundResult(error = "Empty response")
                val source = responseBody.source()
                val firstLine = source.readUtf8Line() ?: ""
                val isSse = firstLine.startsWith("event: ") || firstLine.startsWith("data: ")
                var hasTokens = false
                val accumulator = ToolCallAccumulator()

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
                    // WR-04: finish-gated reassembly — `finish_reason:
                    // "tool_calls"` proves the accumulated fragments are
                    // complete. A transport-truncated stream (IOException
                    // mid-read) must never execute a partial prefix that
                    // happens to parse as valid args (wallet + wrong-result
                    // risk); it degrades to a transport error instead.
                    // IN-01: the `event:`-line `currentEvent` was assigned
                    // but never read (the type rides inside data JSON) —
                    // removed.
                    var toolFinishSeen = false
                    var streamTruncated = false
                    var reasoningOpen = false
                    if (firstLine.startsWith("data: ")) {
                        val data = firstLine.removePrefix("data: ").trim()
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
                        } catch (e: Exception) { Timber.e(e, "CompatLoop: SSE first-line delta parse failed") }
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
                                    } catch (e: Exception) { Timber.e(e, "CompatLoop: SSE delta parse failed") }
                                }
                                line.isEmpty() -> { /* frame separator */ }
                            }
                        }
                    } catch (e: IOException) {
                        streamTruncated = true
                        Timber.e(e, "CompatLoop: SSE stream read failed")
                    }
                    // WR-04: never execute a truncated partial accumulation.
                    if (streamTruncated && !toolFinishSeen && accumulator.hasCalls()) {
                        return CompatRoundResult(error = "Connection failed: stream truncated")
                    }
                    return CompatRoundResult(
                        toolCalls = accumulator.complete(),
                        hadTokens = hasTokens,
                    )
                } else {
                    // Non-streaming JSON — read remaining + first line.
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
                        return CompatRoundResult(
                            toolCalls = nonStreamingCalls,
                            hadTokens = hasTokens,
                        )
                    } catch (e: Exception) { Timber.e(e, "CompatLoop: non-streaming JSON parse failed") }
                    return CompatRoundResult(hadTokens = hasTokens)
                }
            }
        } catch (e: CancellationException) {
            // Stop means stop: never map coroutine cancellation to an Error token.
            throw e
        } catch (e: IOException) {
            // Includes IOException("Canceled") from Call.cancel() teardown — silent.
            if (e.message?.contains("Canceled", ignoreCase = true) == true) {
                Timber.d(e, "CompatLoop: round transport canceled")
                return CompatRoundResult(aborted = true)
            }
            Timber.e(e, "CompatLoop: round transport failed")
            return CompatRoundResult(error = "Connection failed: ${e.message}")
        } catch (e: Exception) {
            return CompatRoundResult(error = "Connection failed: ${e.message}")
        } finally {
            onCallCleared()
        }
    }

    private fun parseSseChoice(json: Json, data: String): OpenAiStreamChoice? {
        return try {
            json.decodeFromString<OpenAiStreamChunk>(data).choices.firstOrNull()
        } catch (_: Exception) { null }
    }

    /** Error-body window fed to the `tools[]`-rejection classifier. */
    private const val ERROR_BODY_SNIPPET_CHARS = 2000

    /** `finish_reason` value signaling a complete `tool_calls` round. */
    private const val TOOL_CALLS_FINISH_REASON = "tool_calls"

    /** `ToolCompleted` transcript summary cap (≤200 chars). */
    private const val TRANSCRIPT_SUMMARY_MAX_CHARS = 200

    private val SUMMARY_WHITESPACE = Regex("\\s+")
}

/**
 * Phase 57 (57-02): one compat-POST round outcome. Fatal [error] stops the
 * turn; [toolsRejected] triggers exactly one retry without tools plus the
 * visible notice; [aborted] (Stop) emits nothing and stops.
 */
internal data class CompatRoundResult(
    val toolCalls: List<PendingToolCall> = emptyList(),
    val hadTokens: Boolean = false,
    val error: String? = null,
    val toolsRejected: Boolean = false,
    val aborted: Boolean = false,
)
