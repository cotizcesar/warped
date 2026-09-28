package com.warped.data.remote.provider

import com.warped.data.local.inference.InputSanitizer
import com.warped.data.remote.dto.OpenAiChatRequest
import com.warped.data.remote.dto.OpenAiFunctionDef
import com.warped.data.remote.dto.OpenAiMessage
import com.warped.data.remote.dto.OpenAiNonStreamingFunction
import com.warped.data.remote.dto.OpenAiNonStreamingResponse
import com.warped.data.remote.dto.OpenAiNonStreamingToolCall
import com.warped.data.remote.dto.OpenAiStreamChunk
import com.warped.data.remote.dto.OpenAiTool
import com.warped.data.skills.skillDescriptor
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.Role
import com.warped.domain.model.StreamToken
import com.warped.domain.skills.ToolExecutor
import com.warped.domain.skills.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.IOException

/**
 * 47-03 (D-04/D-07, RESEARCH Pattern 3): multi-round tool loop over the
 * OpenAI-compatible `POST {base}/v1/chat/completions`.
 *
 * PARALLEL path — the native `/api/v1/chat` provider
 * ([LMStudioProvider.chat] + `handleSseEvent`) is byte-identical for plain
 * chat and never receives `tools[]` (threat T-47-13). The provider exposes
 * this loop via [LMStudioProvider.chatCompletionsWithTools]; this class owns
 * the loop so the provider does not bloat.
 *
 * Per-round contract (mirrors the 46-01 raw-`Call` discipline):
 * - Every round creates its `Call` via the shared x-api-key client and
 *   reports it through [onCallCreated] so the helper's `activeCall` always
 *   points at the live round — `stopResponse()` → `Call.cancel()` kills
 *   mid-round and between-round waits alike. Rounds without `onCallCreated`
 *   are forbidden (stale handle = unkillable round, T-47-12).
 * - `currentCoroutineContext().ensureActive()` at the loop top AND after
 *   each local execution — Stop between rounds aborts before the next
 *   re-POST. `Call.cancel()` surfacing as `IOException("Canceled")` is
 *   silent, exactly like the native path.
 * - Round cap uses `>=` semantics (`check(toolRounds >= MAX_TOOL_ROUNDS)`);
 *   `>` would let a 6th tool round run (RESEARCH anti-pattern).
 * - Progress uses [StreamToken.ToolStatus] directly — NEVER fake
 *   `[tool:NAME]` text markers (they would persist into content).
 * - Execution goes through [ToolExecutor] (Plan 02 allowlisted dispatcher)
 *   on its own `Dispatchers.Default`; unknown model-requested names are
 *   rejected there with an error-string `role:tool` reply (T-47-10).
 */
const val MAX_TOOL_ROUNDS = 5

/** `role:tool` re-POST content cap (executor text is already ≤2000). */
private const val TOOL_REPLY_MAX_CHARS = 2000

/**
 * Build the `tools[]` payload from the shared descriptors (no drift:
 * the same source feeding `@ToolParam` descriptions, SKILLS-09).
 * `tool_choice` is left null — the server default is `auto` (A2).
 */
fun buildCompletionsTools(enabledIds: List<String>): List<OpenAiTool> =
    enabledIds.mapNotNull { skillDescriptor(it) }.map { descriptor ->
        OpenAiTool(
            function = OpenAiFunctionDef(
                name = descriptor.name,
                description = descriptor.description,
                parameters = descriptor.toOpenAiParameters(),
            ),
        )
    }

/** Append-only fragment accumulator for one `tool_calls[index]` stream. */
private class PendingToolCall {
    var id: String? = null
    val name = StringBuilder()
    val args = StringBuilder()
}

private data class MaterializedCall(val id: String, val name: String, val argsJson: String)

private data class RoundOutcome(
    val toolCalls: List<MaterializedCall> = emptyList(),
    /** Assistant text streamed this round (already emitted as Deltas). */
    val roundText: String = "",
    /** `tool_calls` arrived but were malformed → content fallback, no crash. */
    val malformed: Boolean = false,
    /** Transport-level failure (HTTP error) — terminate the turn. */
    val failed: Boolean = false,
)

class LmStudioToolLoop(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val modelId: String,
    private val executor: ToolExecutor,
    private val inputSanitizer: InputSanitizer,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    fun run(
        request: ChatRequest,
        tools: List<OpenAiTool>,
        onCallCreated: (Call) -> Unit = {},
    ): Flow<StreamToken> = callbackFlow {
        val liveCall = arrayOfNulls<Call>(1)
        // Structured-concurrency backstop (46-01 pattern): a bare
        // collector-cancel also tears down the blocking read.
        val teardown = launch {
            try {
                awaitCancellation()
            } finally {
                runCatching { liveCall[0]?.cancel() }
            }
        }
        val reportCall: (Call) -> Unit = { call ->
            liveCall[0] = call
            onCallCreated(call)
        }
        try {
            runLoop(request, tools, reportCall)
            if (currentCoroutineContext().isActive) {
                send(StreamToken.Done(null, null))
            }
            // Finite flow (unlike the endless native SSE stream): complete
            // after Done so collectors terminate. A bare awaitClose here
            // would suspend forever — close() first, then awaitClose only
            // covers the cancellation path (returns at once when closed).
            close()
        } catch (e: CancellationException) {
            // Stop means stop: never map cancellation to an Error token.
            throw e
        } catch (e: IOException) {
            // Includes IOException("Canceled") from Call.cancel() — silent.
            Timber.d(e, "LmStudioToolLoop: transport closed")
        } catch (e: Exception) {
            Timber.e(e, "LmStudioToolLoop: failed")
            trySend(StreamToken.Error("Connection failed: ${e.message}"))
        } finally {
            liveCall[0] = null
            teardown.cancel()
        }
        awaitClose { liveCall[0] = null }
    }.flowOn(Dispatchers.IO)

    private suspend fun ProducerScope<StreamToken>.runLoop(
        request: ChatRequest,
        tools: List<OpenAiTool>,
        onCallCreated: (Call) -> Unit,
    ) {
        val messages = initialMessages(request)
        val turnText = StringBuilder()
        var toolRounds = 0
        var hadToolActivity = false

        while (true) {
            // 46-01 hook contract: cancellation checkpoint at every
            // round boundary, BEFORE the next request is built.
            currentCoroutineContext().ensureActive()
            val outcome = doRound(messages, tools, turnText, onCallCreated)
            if (outcome.failed) return
            // Plain content finish (including malformed-call fallback).
            if (outcome.toolCalls.isEmpty()) break
            // tool_calls finish: enforce the cap with >= semantics —
            // a 6th tool round must never fire.
            if (toolRounds >= MAX_TOOL_ROUNDS) {
                Timber.w("LmStudioToolLoop: tool-round cap (%d) hit — content fallback", MAX_TOOL_ROUNDS)
                break
            }
            toolRounds++
            hadToolActivity = true
            val echo = mutableListOf<OpenAiNonStreamingToolCall>()
            val toolMessages = mutableListOf<OpenAiMessage>()
            for (call in outcome.toolCalls) {
                send(StreamToken.ToolStatus(call.name))
                // ensureActive between dispatch and re-POST: Stop lands here.
                currentCoroutineContext().ensureActive()
                val result = executor.execute(call.name, call.argsJson)
                currentCoroutineContext().ensureActive()
                val (summary, errorReason, replyText) = mapResult(result)
                // toolId = skill id (call.name): the transcript row renders
                // "Used {Display}" from it; the wire id stays on the echo.
                send(StreamToken.ToolCompleted(call.name, summary, errorReason))
                echo += OpenAiNonStreamingToolCall(
                    id = call.id,
                    function = OpenAiNonStreamingFunction(name = call.name, arguments = call.argsJson),
                )
                toolMessages += OpenAiMessage(role = "tool", content = replyText, toolCallId = call.id)
            }
            messages += OpenAiMessage(role = "assistant", content = outcome.roundText, toolCalls = echo)
            messages += toolMessages
            // Loop continues → re-POST with the appended tool context.
        }
        // Silent-model fallback: tools ran but the turn produced no text —
        // re-POST once WITHOUT tools for a plain answer (never synthesize
        // fake text). Guarantees the error row never sits above an
        // empty bubble.
        if (turnText.isBlank() && hadToolActivity) {
            currentCoroutineContext().ensureActive()
            doRound(messages, null, turnText, onCallCreated)
        }
    }

    private fun mapResult(result: ToolResult): Triple<String, String?, String> =
        when (result) {
            is ToolResult.Success ->
                Triple(result.summary, null, result.text.take(TOOL_REPLY_MAX_CHARS))
            is ToolResult.Failure ->
                // Error-string role:tool reply (T-47-10); summary persists
                // the short reason for the transcript row.
                Triple(result.reason, result.reason, "Error: ${result.reason}")
        }

    /**
     * One `POST /v1/chat/completions` round. Streams content Deltas as they
     * arrive (emitted alongside tool accumulation), parses the tool_calls
     * index-keyed accumulator ONCE at the terminal `finish_reason`
     * (Pitfall 3), and also handles the non-streaming JSON body shape.
     * Malformed calls (blank name / non-object args) yield
     * `malformed=true` → the caller falls back to plain content, never
     * crashes (T-47-10/T-47-11).
     */
    private suspend fun ProducerScope<StreamToken>.doRound(
        messages: List<OpenAiMessage>,
        tools: List<OpenAiTool>?,
        turnText: StringBuilder,
        onCallCreated: (Call) -> Unit,
    ): RoundOutcome {
        val body = OpenAiChatRequest(
            model = modelId,
            messages = messages.toList(),
            stream = true,
            tools = tools,
            toolChoice = null,
        )
        val call = client.newCall(
            Request.Builder()
                .url("${baseUrl.trimEnd('/')}/v1/chat/completions")
                .post(
                    json.encodeToString(OpenAiChatRequest.serializer(), body)
                        .toRequestBody("application/json".toMediaType()),
                )
                .build(),
        )
        onCallCreated(call)
        call.execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = runCatching { response.body?.string() }.getOrNull()
                    ?: response.message
                send(StreamToken.Error("HTTP ${response.code}: $errorBody"))
                return RoundOutcome(failed = true)
            }
            val responseBody = response.body
                ?: run {
                    send(StreamToken.Error("Empty response"))
                    return RoundOutcome(failed = true)
                }
            val source = responseBody.source()
            val pending = mutableMapOf<Int, PendingToolCall>()
            val roundText = StringBuilder()
            var finishReason: String? = null
            var sawSse = false
            val rawAccumulator = StringBuilder()
            try {
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    rawAccumulator.appendLine(line)
                    if (!line.startsWith("data:")) {
                        // SSE comments/keep-alives still prove streaming framing.
                        if (line.startsWith(":")) sawSse = true
                        continue
                    }
                    sawSse = true
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    try {
                        val chunk = json.decodeFromString<OpenAiStreamChunk>(data)
                        val choice = chunk.choices.firstOrNull() ?: continue
                        choice.delta?.content?.let { content ->
                            if (content.isNotEmpty()) {
                                send(StreamToken.Delta(content))
                                roundText.append(content)
                                turnText.append(content)
                            }
                        }
                        choice.delta?.toolCalls?.forEach { tc ->
                            val slot = pending.getOrPut(tc.index) { PendingToolCall() }
                            tc.id?.let { slot.id = it }
                            tc.function?.name?.let { slot.name.append(it) }
                            tc.function?.arguments?.let { slot.args.append(it) }
                        }
                        choice.finishReason?.let { finishReason = it }
                    } catch (e: Exception) {
                        Timber.e(e, "LmStudioToolLoop: stream chunk parse failed")
                    }
                }
            } catch (e: IOException) {
                // Includes Canceled from Call.cancel() — silent per contract.
                Timber.d(e, "LmStudioToolLoop: stream read closed")
                return RoundOutcome(roundText = roundText.toString())
            }
            if (!sawSse) {
                return decodeNonStreaming(rawAccumulator.toString(), turnText)
            }
            if (finishReason == "tool_calls" && pending.isNotEmpty()) {
                val materialized = pending.toSortedMap().map { (index, slot) ->
                    MaterializedCall(
                        id = slot.id ?: "call-$index",
                        name = slot.name.toString(),
                        argsJson = slot.args.toString().ifBlank { "{}" },
                    )
                }
                // Validate BEFORE execution (T-47-11): blank names or
                // non-object args → malformed content fallback, never throw.
                for (callItem in materialized) {
                    if (callItem.name.isBlank()) {
                        Timber.w("LmStudioToolLoop: malformed tool call (blank name) — content fallback")
                        return RoundOutcome(roundText = roundText.toString(), malformed = true)
                    }
                    val parsed = runCatching { json.parseToJsonElement(callItem.argsJson) }.getOrNull()
                    if (parsed == null || parsed !is JsonObject) {
                        Timber.w("LmStudioToolLoop: malformed tool args — content fallback")
                        return RoundOutcome(roundText = roundText.toString(), malformed = true)
                    }
                }
                return RoundOutcome(toolCalls = materialized, roundText = roundText.toString())
            }
            return RoundOutcome(roundText = roundText.toString())
        }
    }

    /**
     * Non-streaming body shape (`{choices:[{message:{content,tool_calls}}]}`).
     * Content streams as Deltas; tool calls materialize directly (no
     * accumulator needed — the object is already complete).
     */
    private suspend fun ProducerScope<StreamToken>.decodeNonStreaming(
        rawBody: String,
        turnText: StringBuilder,
    ): RoundOutcome {
        val message = try {
            json.decodeFromString<OpenAiNonStreamingResponse>(rawBody)
                .choices.firstOrNull()?.message
        } catch (e: Exception) {
            Timber.e(e, "LmStudioToolLoop: non-streaming parse failed")
            return RoundOutcome()
        } ?: return RoundOutcome()
        val roundText = StringBuilder()
        message.content?.let { content ->
            if (content.isNotEmpty()) {
                send(StreamToken.Delta(content))
                roundText.append(content)
                turnText.append(content)
            }
        }
        val calls = message.toolCalls.orEmpty().mapIndexed { index, tc ->
            MaterializedCall(
                id = tc.id ?: "call-$index",
                name = tc.function?.name.orEmpty(),
                argsJson = tc.function?.arguments?.ifBlank { "{}" } ?: "{}",
            )
        }
        if (calls.isEmpty()) return RoundOutcome(roundText = roundText.toString())
        for (call in calls) {
            if (call.name.isBlank()) {
                Timber.w("LmStudioToolLoop: malformed non-streaming call — content fallback")
                return RoundOutcome(roundText = roundText.toString(), malformed = true)
            }
            val parsed = runCatching { json.parseToJsonElement(call.argsJson) }.getOrNull()
            if (parsed == null || parsed !is JsonObject) {
                Timber.w("LmStudioToolLoop: malformed non-streaming args — content fallback")
                return RoundOutcome(roundText = roundText.toString(), malformed = true)
            }
        }
        return RoundOutcome(toolCalls = calls, roundText = roundText.toString())
    }

    /**
     * Seed messages from the turn request (sanitized-content discipline,
     * T-46-01: user text re-sanitized; history replayed verbatim).
     * `Role.TOOL` rows (persisted as `"<toolId>\n<summary>"` per the 47-01
     * ToolCopy contract) resume as `role:tool` context with
     * `tool_call_id` = tool id.
     */
    private fun initialMessages(request: ChatRequest): MutableList<OpenAiMessage> {
        val out = mutableListOf<OpenAiMessage>()
        for (message in request.messages) {
            when (message.role) {
                Role.SYSTEM ->
                    if (message.content.isNotBlank()) {
                        out += OpenAiMessage(role = "system", content = message.content)
                    }
                Role.USER ->
                    out += OpenAiMessage(
                        role = "user",
                        content = inputSanitizer.sanitize(message.content),
                    )
                Role.ASSISTANT ->
                    if (message.content.isNotBlank()) {
                        out += OpenAiMessage(role = "assistant", content = message.content)
                    }
                Role.TOOL -> {
                    // 47-01 persistence encoding; kept local (3 lines) so the
                    // data layer never imports the UI-layer ToolCopy helper.
                    val raw = message.content
                    val idx = raw.indexOf('\n')
                    val toolId = if (idx < 0) "calculator" else raw.substring(0, idx)
                    val summary = if (idx < 0) raw else raw.substring(idx + 1)
                    if (summary.isNotBlank()) {
                        out += OpenAiMessage(role = "tool", content = summary, toolCallId = toolId)
                    }
                }
            }
        }
        return out
    }
}
