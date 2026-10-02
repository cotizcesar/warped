package com.warped.data.local.inference

import android.util.Base64
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import com.google.ai.edge.litertlm.LiteRtLmJniException
import com.google.ai.edge.litertlm.tool
import com.warped.data.agentic.LocalToolLoop
import com.warped.data.agentic.WebFetchToolSet
import com.warped.data.agentic.WebSearchToolSet
import com.warped.data.grounding.DuckDuckGoSearchRepository
import com.warped.data.grounding.GroundingPrecedence
import com.warped.data.grounding.MultiUrlFetcher
import com.warped.data.grounding.WebPageFetcher
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.remote.provider.HistoryImageCarry
import com.warped.data.repository.ModelAllowlistRepository
import com.warped.domain.model.ActiveModelSelection
import com.warped.domain.model.ChatMessage
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.GenerationParameters
import com.warped.domain.model.ModelInfo
import com.warped.domain.model.ProviderType
import com.warped.domain.model.Role
import com.warped.domain.model.StreamToken
import com.warped.domain.provider.LlmProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Phase 56 (56-02): one collected tool-loop turn. The streamed text/thought
 * chunks are emitted by the transport as they arrive; [terminal] is the last
 * message of the turn and decides the next step (empty toolCalls = final).
 */
internal data class AgenticTurn(val terminal: Message)

/**
 * Phase 56 (56-02): seam between the tool-loop driver and the engine.
 * Production streams via `sendMessageAsync`; tests script terminals.
 */
internal interface AgenticTurnTransport {
    val isAlive: Boolean
    suspend fun collectTurn(
        first: Contents?,
        reply: Message?,
        onText: suspend (String) -> Unit,
        onThought: suspend (String) -> Unit,
    ): AgenticTurn
}

@Singleton
class LiteRTLmProvider @Inject constructor(
    private val engineManager: EngineManager,
    private val inputSanitizer: InputSanitizer,
    private val activeModelSelection: ActiveModelSelection,
    /**
     * Quick-task (DDG-default): web_search goes through the DDG-only
     * repository (keyless outcome — downstream mapping untouched).
     */
    private val ddg: DuckDuckGoSearchRepository,
    private val multiUrlFetcher: MultiUrlFetcher,
    private val webPageFetcher: WebPageFetcher,
    private val allowlist: ModelAllowlistRepository,
    private val advancedPreferences: AdvancedPreferences,
) : LlmProvider {

    override val type = ProviderType.LITE_RT_LM

    companion object {
        /** 0.17.x reasoning channel name (see [extractThoughtContent]). */
        const val THOUGHT_CHANNEL = "thought"

        /**
         * Phase 56 (56-02): provider-neutral tool-use hint pinned via
         * `systemInstruction` (survives conversation reuse, unlike a leading
         * system message). Short on purpose — the fused Source[N] blocks do
         * the heavy lifting. Pinned verbatim by LiteRTLmLoopTest.
         *
         * Quick-task (reference-resolution): reference-resolution +
         * citation-hygiene rule — resolve pronouns/references against the
         * conversation history first (including the resolved names in
         * web_search queries) and never reuse earlier turns' citation
         * numbers; facts not covered by earlier tool results still trigger
         * a fresh `web_search`, never an answer from stale results.
         *
         * Quick-task (code-intent-gate): code-from-knowledge rule — code
         * comes from the model's own weights first; `web_search` is only
         * for fresh or versioned API facts.
         */
        const val TOOL_USE_SYSTEM_HINT =
            "Use web_search when the question needs current or external facts, " +
                "and web_fetch to read a full page from the results or the user. " +
                "Answer with the gathered context. " +
                "Resolve pronouns and references (he/she/it/this/that, él/ella/su/eso/este, and names) against the conversation history first, and include the resolved names in web_search queries; if the current answer needs facts not covered by earlier tool results, call web_search again instead of answering from stale results. " +
                "Cite only sources fetched for the current answer; never reuse citation numbers from earlier turns. " +
                "Write code from your own knowledge first; call web_search only for " +
                "fresh or versioned API facts. " +
                "Do not call web_search/web_fetch for greetings, thanks, or " +
                "questions about yourself."

        /**
         * Quick-task (needs-web-gate): local identity line. Set as
         * `systemInstruction` on EVERY local turn (armed or not) so the
         * ~2B model can answer "quién eres" from prompt — ungrounded turns
         * carry no other persona today. Static: the display name is not
         * trivially available at this layer. Remote providers untouched
         * (local-only by design — see plan SUMMARY follow-up).
         */
        const val IDENTITY_LINE =
            "You are Warped, a mobile AI assistant running locally. " +
                "Always reply in the same language the user writes in."

        /** `ToolCompleted` transcript summary cap (≤200 chars, remote parity). */
        const val TRANSCRIPT_SUMMARY_MAX_CHARS = 200
        val SUMMARY_WHITESPACE = Regex("\\s+")

        /**
         * Quick-task (thinking-config): reasoning trace budget (tokens) applied
         * when the Thinking toggle is on AND the model is capable. The SDK
         * default budget is 0 (channel off); this bounded value keeps
         * thinking traces short on-device while leaving the answer path
         * untouched. No `<|think|>` manual injection — the engine owns the
         * channel; the existing tag-strip/display path is unchanged.
         */
        const val THINKING_TOKEN_BUDGET = 1024

        /**
         * Quick-task (image-history-carry): max history USER messages whose
         * images are carried into the engine history. Each carried image is
         * a full ImageBytes payload in native context — unbounded carry
         * risks context-window eviction of the actual conversation text and
         * OOM on large photos. 3 covers the realistic multi-image-turn case
         * (user attaches 1-2 images then follows up) while bounding the
         * worst case; newest-first because recency predicts relevance for
         * follow-ups.
         */
        const val HISTORY_IMAGE_CARRY_MAX = HistoryImageCarry.CARRY_MAX
    }

    @Volatile
    private var activeConversation: Conversation? = null

    @Volatile
    private var activeConversationConfig: ConversationConfig? = null

    /**
     * Phase 56 (56-02, T-56-10): arming inputs baked into
     * [activeConversationConfig] at creation. ConversationConfig.tools apply
     * at creation only — when the snapshot differs (grounding toggle flip,
     * per-chat override change, model switch, connectivity transition) the
     * conversation is reset so tools can never fire with grounding off.
     * Stored alongside the config, nulled together everywhere the config is.
     */
    internal data class LoopArmSnapshot(
        val perChat: Boolean?,
        val global: Boolean,
        val modelPath: String?,
        val capable: Boolean,
        val online: Boolean,
        /**
         * Quick-task (thinking-config): the toggle+capability thinking state
         * (`request.parameters.reasoningEnabled`, already AND-gated upstream
         * by `ChatViewModel`: toggle && supportsThinking — no new gate).
         * Part of the snapshot so a toggle flip rebuilds the conversation
         * (config applies at creation only) instead of reusing a channel
         * created under the other state.
         */
        val thinking: Boolean = false,
    ) {
        val armed: Boolean
            get() = LocalToolLoop.isLoopArmed(
                groundingOn = GroundingPrecedence.shouldGround(perChat, global),
                supportsFunctionCalling = capable,
                hasValidatedInternet = online,
            )
    }

    @Volatile
    private var activeLoopArm: LoopArmSnapshot? = null

    /**
     * 46-01 RUNTIME-14: halt the in-flight native generation WITHOUT closing the
     * conversation — `cancelProcess()` keeps the handle reusable for the next turn
     * (close would force a full re-create). Safe when idle (no-op). Fire-and-forget
     * best-effort alongside coroutine cancellation; failures are logged, never thrown.
     */
    fun cancelActiveGeneration() {
        try {
            activeConversation?.cancelProcess()
        } catch (e: Exception) {
            Timber.w(e, "LiteRTLm: cancelProcess failed")
        }
    }

    /**
     * Reset the active conversation. Called when the engine is reloaded or the model changes.
     * The next chat() call will lazily create a new conversation.
     */
    fun resetConversation() {
        synchronized(this) {
            activeConversation?.let { prev ->
                try { prev.close() } catch (e: Exception) { Timber.w(e, "LiteRTLm: resetConversation.close() failed") }
            }
            activeConversation = null
            activeConversationConfig = null
            // 56-02 (T-56-10): arming snapshot dies with the config it describes.
            activeLoopArm = null
        }
    }

    override fun chat(request: ChatRequest): Flow<StreamToken> {
        // Phase 49 (DEL-01): single-turn plain chat — no tools, no status
        // forwarding. Legacy Role.TOOL history maps assistant-adjacent.
        return flow {
            chatInternal(request)
        }.flowOn(Dispatchers.Default)
    }

    private suspend fun FlowCollector<StreamToken>.chatInternal(request: ChatRequest) {
        // Step 1: Sanitize all messages
        val sanitizedMessages = request.messages.map { msg ->
            msg.copy(content = inputSanitizer.sanitize(msg.content))
        }

        // Step 2: Load engine on-demand if not loaded
        val activeEngine = engineManager.getActiveEngine()
        if (activeEngine == null || activeEngine.type != EngineType.LITE_RT_LM) {
            // Try to load the engine from the active model path
            val modelPath = activeModelSelection.activeModel.value?.modelId
            // Engine-supported containers: .litertlm and .task (allowlist ships .task files).
            if (modelPath != null && (modelPath.endsWith(".litertlm", ignoreCase = true) ||
                modelPath.endsWith(".task", ignoreCase = true))) {
                try {
                    engineManager.switchToLiteRT(modelPath)
        } catch (e: LiteRtLmJniException) {
            Timber.e(e, "LiteRTLmProvider: JNI native error — ${e.message}")
            emit(StreamToken.Error("LiteRT-LM native error: ${e.message ?: "Unknown JNI error"}"))
            return
        } catch (e: Exception) {
                    emit(StreamToken.Error("Failed to load LiteRT-LM engine: ${e.message}"))
                    return
                }
            } else {
                emit(StreamToken.Error("No LiteRT-LM engine is loaded. Select a .litertlm or .task model first."))
                return
            }
        }

        // Step 3: Build history messages — text plus recent history images.
        // Follow-up turns are blind to earlier images if history is
        // text-only, so the last K image-bearing USER turns carry their
        // images explicitly (see buildHistoryMessages: K newest-first,
        // deduped, skip-on-failure). dropLast(1) lives inside the builder
        // and excludes the current message, so the carried set NEVER
        // includes it and Step 4 attachments are never double-sent. Both
        // send paths consume this conversationConfig, so the single-site
        // fix applies to fresh-create AND reused-conversation turns
        // uniformly. SCOPE: local LiteRT-LM path only — remote providers
        // forward current-turn request.images only (own fix, not here).
        val hasImages = request.images.isNotEmpty()
        val historyMessages = buildHistoryMessages(sanitizedMessages)

        // Step 4: Build current message contents (text + images + audio)
        val currentUserText = sanitizedMessages.lastOrNull { it.role == Role.USER }?.content ?: ""
        val audioBytes = request.audioBytes
        val hasAudio = audioBytes != null && audioBytes.isNotEmpty()
        val hasText = currentUserText.isNotBlank()
        val currentContents = if (hasImages || hasAudio) {
            val contentList = mutableListOf<Content>()
            if (hasAudio) {
                contentList.add(Content.AudioBytes(audioBytes))
                Timber.d("LiteRTLmProvider: attaching audio (${audioBytes.size} bytes)")
            }
            request.images.forEach { dataUrl ->
                decodeImage(dataUrl)?.let { contentList.add(Content.ImageBytes(it)) }
            }
            // Only include text content if the user actually provided text.
            // An image-only / audio-only message should not have an empty text part,
            // which LiteRT-LM rejects.
            if (hasText) {
                contentList.add(Content.Text(currentUserText))
            }
            Timber.d("LiteRTLmProvider: sending ${request.images.size} image(s) + audio=${hasAudio} + text=${hasText}")
            Contents.of(contentList)
        } else {
            Contents.of(currentUserText)
        }

        // Step 5: Map GenerationParameters -> SamplerConfig
        val params = request.parameters
        val samplerConfig = SamplerConfig(
            topK = clamp(params.topK, 1, 100, "topK"),
            topP = clamp(params.topP.toDouble(), 0.0, 1.0, "topP"),
            temperature = clamp(params.temperature.toDouble(), 0.0, 2.0, "temperature"),
            seed = if (params.seed != -1) params.seed else 0
        )

        // Step 6: Create conversation config — plain single-turn chat unless
        // the 56-02 agentic loop is armed (capable model + grounding on +
        // validated internet). Unarmed stays byte-identical to Phase 49
        // DEL-01 (no tools, no prompt-injection fallback).
        val armSnapshot = computeArmSnapshot(
            request.webOverride,
            // Quick-task (thinking-config): reasoningEnabled arrives
            // already AND-gated upstream (ChatViewModel passes
            // enableThinking = toggle && supportsThinking into
            // runInference, which copies it here) — thread it, don't
            // re-gate it.
            request.parameters.reasoningEnabled,
        )
        val conversationConfig = if (armSnapshot.armed) {
            ConversationConfig(
                initialMessages = historyMessages,
                samplerConfig = samplerConfig,
                // 47 precedent: FRESH ToolSet instances per creation — never singletons.
                tools = listOf(tool(WebSearchToolSet()), tool(WebFetchToolSet())),
                automaticToolCalling = false,
                systemInstruction = Contents.of("$IDENTITY_LINE $TOOL_USE_SYSTEM_HINT"),
                extraContext = emptyMap()
            )
        } else {
            ConversationConfig(
                initialMessages = historyMessages,
                samplerConfig = samplerConfig,
                systemInstruction = Contents.of(IDENTITY_LINE),
                extraContext = emptyMap()
            )
        }

        // Diagnostics: history depth reaching the native layer, reuse inputs.
        // If turns "start from 0", this line shows whether history was
        // empty (VM side) or the conversation rebuilt (online flap below).
        Timber.d(
            "LiteRTLm: history=%d msgs online=%b armed=%b thinking=%b",
            conversationConfig.initialMessages.size,
            armSnapshot.online,
            armSnapshot.armed,
            armSnapshot.thinking
        )
        if (armSnapshot.armed) {
            Timber.d("LiteRTLm: agentic loop armed (model=%s)", armSnapshot.modelPath?.substringAfterLast("/"))
            sendAgenticWithRetry(currentContents, conversationConfig, armSnapshot, params.contextSize, 0)
        } else {
            sendContentsWithRetry(currentContents, conversationConfig, armSnapshot, 0)
        }
    }

    /**
     * Phase 56 (56-02): loop-arming snapshot for this turn. Grounding
     * precedence resolves the per-chat override the VM carried on the
     * request against the global DataStore default; capability comes from
     * the allowlist via the LOADED engine path (never selection state, so a
     * stale selector cannot arm tools); internet from the same validated
     * gate the fetch path uses. Never throws — a gate failure reads as
     * unarmed (plain turn), never a crash.
     */
    internal suspend fun computeArmSnapshot(
        perChat: Boolean?,
        thinking: Boolean = false,
    ): LoopArmSnapshot {
        val global = try {
            advancedPreferences.webGroundingEnabled.first()
        } catch (e: Exception) {
            Timber.w(e, "LiteRTLm: global grounding read failed, treating as off")
            false
        }
        val modelPath = try {
            engineManager.getActiveEngine()?.modelPath
        } catch (e: Exception) {
            Timber.w(e, "LiteRTLm: active engine read failed")
            null
        }
        // Verified-only rule: unlisted models (null entry) default CLOSED.
        val capable = modelPath?.substringAfterLast("/")?.let { fileName ->
            try {
                allowlist.findByModelFile(fileName)?.capabilities?.supportsFunctionCalling == true
            } catch (e: Exception) {
                Timber.w(e, "LiteRTLm: allowlist read failed, treating as incapable")
                false
            }
        } == true
        val online = try {
            webPageFetcher.hasValidatedInternet()
        } catch (e: Exception) {
            Timber.w(e, "LiteRTLm: connectivity check failed, treating as offline")
            false
        }
        return LoopArmSnapshot(
            perChat = perChat,
            global = global,
            modelPath = modelPath,
            capable = capable,
            online = online,
            thinking = thinking,
        )
    }

    /**
     * Quick-task (thinking-config): map the toggle+capability thinking state
     * to the 0.17.x engine config. Non-null (channel enabled) only when the
     * upstream-gated flag is true; null selects engine defaults (channel
     * off) for toggle-off or incapable models. Pure function —
     * unit-testable without the native engine. `maxOutputToken` stays null
     * (out of scope).
     */
    internal fun thinkingConfigFor(reasoningEnabled: Boolean): ThinkingConfig? =
        if (reasoningEnabled) ThinkingConfig(true, THINKING_TOKEN_BUDGET) else null

    /**
     * Phase 56 (56-02, T-56-10): single conversation-acquire point for both
     * paths. Reuses the live conversation ONLY when the arming snapshot
     * matches what the config was created with — any arming-input change
     * (toggle, override, model, connectivity) resets so tools can never
     * fire with grounding off (Pitfall 2: config applies at creation only).
     */
    private fun acquireConversation(
        conversationConfig: ConversationConfig,
        snapshot: LoopArmSnapshot,
    ): Conversation = synchronized(this@LiteRTLmProvider) {
        val existing = activeConversation
        if (existing != null && existing.isAlive && activeLoopArm == snapshot) {
            Timber.d("LiteRTLm: reusing live conversation")
            existing
        } else {
            if (existing != null) {
                try { existing.close() } catch (e: Exception) { Timber.w(e, "LiteRTLm: acquire.close() failed") }
            } else if (activeLoopArm != null && activeLoopArm != snapshot) {
                Timber.d("LiteRTLm: arming inputs changed — rebuilding conversation")
            }
            engineManager.createLiteRTConversation(
                conversationConfig,
                thinkingConfigFor(snapshot.thinking),
            ).also {
                activeConversation = it
                activeConversationConfig = conversationConfig
                activeLoopArm = snapshot
            }
        }
    }

    /**
     * Phase 56 (56-02): app-driven manual tool loop (AGENT-01,
     * `automaticToolCalling=false` — SDK ReflectionTool.execute is
     * synchronous, so network tools would need runBlocking on engine threads
     * with no Stop propagation). Round driver, bounded by
     * [LocalToolLoop.MAX_TOOL_CALLS] counting CALLS:
     *
     * - ensureActive() per round (Stop contract, 46-01 pattern).
     * - Terminal with empty toolCalls = final answer: text Deltas were
     *   already streamed Content.Text-only by the transport (ToolResponse
     *   contents never reach Delta), thought accumulated separately, Done
     *   carries it as reasoning (Thinking panel, never the answer).
     * - Non-empty toolCalls: exact-name dispatch to [executeToolCall];
     *   ToolStatus display posted on start, null in finally; results fed
     *   back as Message.tool(ToolResponse) — T-56-07: only
     *   LocalToolLoop-mapped fused strings re-enter model context.
     * - Cap reached: the pending call's result is CAP_REACHED_STRING (answer
     *   with gathered context, no hard error); a SECOND tool request after
     *   the cap was fed finishes the turn instead of ping-ponging forever.
     * - CancellationException always rethrows (never Error, never retry).
     * - Legacy Role.TOOL history replay stays Message.model read-only at the
     *   chatInternal build site — Message.tool is used ONLY here, for live
     *   loop resume (T-56-12).
     */
    internal suspend fun FlowCollector<StreamToken>.runToolLoop(
        transport: AgenticTurnTransport,
        first: Contents,
        contextSize: Int,
    ) {
        var callsUsed = 0
        var capFed = false
        var reply: Message? = null
        var pendingFirst: Contents? = first
        val thought = StringBuilder()
        while (true) {
            // Stop contract (46-01 pattern): FlowCollector is not a scope,
            // so checkpoint against the collection context directly.
            coroutineContext.ensureActive()
            val terminal = transport.collectTurn(
                first = pendingFirst,
                reply = reply,
                onText = { text -> if (text.isNotEmpty()) emit(StreamToken.Delta(text)) },
                onThought = { thinking ->
                    if (thinking.isNotEmpty()) {
                        thought.append(thinking)
                        // Quick-task (live-thinking): forward every native
                        // thought delta live (same string, no separator —
                        // identical to the accumulator above). Done stays
                        // the final; the VM throttles and reconciles.
                        emit(StreamToken.Thinking(thinking))
                    }
                },
            ).terminal
            pendingFirst = null
            val toolCalls = terminal.toolCalls
            if (toolCalls.isEmpty()) {
                if (!transport.isAlive) {
                    throw IllegalStateException("Conversation not alive after streaming")
                }
                emit(StreamToken.Done(reasoning = thought.toString().takeIf { it.isNotEmpty() }))
                return
            }
            if (capFed) {
                Timber.w("LiteRTLm: model requested tools after the cap string — finishing with gathered context")
                emit(StreamToken.Done(reasoning = thought.toString().takeIf { it.isNotEmpty() }))
                return
            }
            val responses = mutableListOf<Content>()
            for (call in toolCalls) {
                coroutineContext.ensureActive()
                if (LocalToolLoop.isCapReached(callsUsed)) {
                    responses += Content.ToolResponse(call.name, LocalToolLoop.CAP_REACHED_STRING)
                    capFed = true
                } else {
                    // Every dispatched call consumes budget — validation
                    // short-circuits included (Pitfall 5: no infinite
                    // garbage loops). Validation failures feed back
                    // WITHOUT posting a transient status row (IN-02: no
                    // flash for calls that never execute); executeToolCall
                    // re-validates internally as the fail-closed guard.
                    callsUsed++
                    val shortCircuit = LocalToolLoop.validateArgs(call.name, call.arguments)
                    if (shortCircuit != null) {
                        responses += Content.ToolResponse(call.name, shortCircuit)
                    } else {
                        val display = LocalToolLoop.statusDisplay(call.name, call.arguments)
                            ?: call.name
                        emit(StreamToken.ToolStatus(display))
                        try {
                            // Quick-task (agentic-rows): executed calls
                            // surface their structured sources via
                            // ToolCompleted so the VM persists Fuentes rows
                            // on Done. Short-circuits/cap/offline carry no
                            // rows and emit nothing (IN-02 extended).
                            val outcome = executeToolCallDetailed(call, contextSize)
                            // Quick-task (tool-failure-note): executed
                            // calls surface ToolCompleted when they carry
                            // rows OR when they failed outright — failures
                            // ride the transient errorReason note (never
                            // persisted, model-fed text untouched).
                            if (outcome.sources.isNotEmpty() || outcome.failed) {
                                emit(
                                    StreamToken.ToolCompleted(
                                        toolId = "local:${call.name}#$callsUsed",
                                        summary = summarizeForTranscript(outcome.text),
                                        errorReason = if (outcome.failed) {
                                            LocalToolLoop.failureNote(call.name)
                                        } else {
                                            null
                                        },
                                        sources = outcome.sources,
                                        images = outcome.images,
                                    ),
                                )
                            }
                            responses += Content.ToolResponse(call.name, outcome.text)
                        } finally {
                            emit(StreamToken.ToolStatus(null))
                        }
                    }
                }
            }
            reply = Message.tool(Contents.of(responses))
        }
    }

    /**
     * Phase 56 (56-02): single tool-call executor. Total — never throws
     * (47 never-throw lesson): arg validation short-circuits pre-socket,
     * unknown names return the error string without executing, offline
     * opens no socket, and every failure degrades to a concise English
     * string the model continues from. CancellationException rethrows so
     * Stop bounds residual latency to client timeouts.
     */
    internal suspend fun executeToolCall(call: com.google.ai.edge.litertlm.ToolCall, contextSize: Int): String =
        executeToolCallDetailed(call, contextSize).text

    /**
     * Quick-task (agentic-rows): richer [executeToolCall] capturing the
     * structured Fuentes details alongside the mapped model-facing string
     * (same outcome object, no fused-string re-parsing). [executeToolCall]
     * delegates for the `.text` so existing callers/tests are untouched.
     */
    internal suspend fun executeToolCallDetailed(
        call: com.google.ai.edge.litertlm.ToolCall,
        contextSize: Int,
    ): LocalToolLoop.ToolCallOutcome {
        // Unknown names fail closed here — the body below never runs them.
        LocalToolLoop.validateArgs(call.name, call.arguments)?.let { return LocalToolLoop.ToolCallOutcome(it) }
        return when (LocalToolLoop.mapToolCallName(call.name)) {
            LocalToolLoop.TOOL_WEB_SEARCH -> {
                if (!hasValidatedInternet()) return LocalToolLoop.ToolCallOutcome(LocalToolLoop.OFFLINE_STRING)
                val query = (call.arguments["query"] as? String).orEmpty()
                try {
                    // Explicit args (no Kotlin defaults): keeps the call on the
                    // instance method so MockK can stub it in JVM tests.
                    // Quick-task (loop-images): includeImages=true ALWAYS —
                    // the DDG leg ignores it (no image API); keyless
                    // turns behave byte-identically.
                    val outcome = ddg.search(
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
                    Timber.w(e, "LiteRTLm: web_search failed")
                    LocalToolLoop.ToolCallOutcome(LocalToolLoop.toolFailureMessage(e.message.orEmpty()), failed = true)
                }
            }
            LocalToolLoop.TOOL_WEB_FETCH -> {
                if (!hasValidatedInternet()) return LocalToolLoop.ToolCallOutcome(LocalToolLoop.OFFLINE_STRING)
                val url = ((call.arguments["url"] as? String).orEmpty()).trim()
                try {
                    // Explicit onProgress=null (no Kotlin default): keeps the
                    // call on the instance method so MockK can stub it.
                    val result = multiUrlFetcher.fetchAll(
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
                    Timber.w(e, "LiteRTLm: web_fetch failed")
                    LocalToolLoop.ToolCallOutcome(LocalToolLoop.toolFailureMessage(e.message.orEmpty()), failed = true)
                }
            }
            else -> LocalToolLoop.ToolCallOutcome(LocalToolLoop.unknownToolMessage(call.name))
        }
    }

    private fun hasValidatedInternet(): Boolean = try {
        webPageFetcher.hasValidatedInternet()
    } catch (e: Exception) {
        Timber.w(e, "LiteRTLm: connectivity check failed, treating as offline")
        false
    }

    /**
     * Quick-task (agentic-rows): ≤200-char single-line ToolCompleted
     * summary — same cap/shape as the remote loops
     * (`CompatToolLoop.summarizeForTranscript` and the OpenAI/Anthropic
     * inline copies).
     */
    private fun summarizeForTranscript(result: String): String =
        result.trim().replace(SUMMARY_WHITESPACE, " ").take(TRANSCRIPT_SUMMARY_MAX_CHARS)

    /**
     * Phase 56 (56-02): production transport — streams each turn via
     * `sendMessageAsync` (status rows still render, final answer still
     * streams) and returns the last emission as the terminal carrying
     * toolCalls. If the streaming Flow ever stops surfacing toolCalls with
     * automatic=false (A1 risk), the device checkpoint step 7 catches it
     * and this transport swaps to blocking sendMessage on
     * Dispatchers.Default.
     */
    internal inner class ConversationTurnTransport(
        private val conversation: Conversation,
    ) : AgenticTurnTransport {
        override val isAlive: Boolean get() = conversation.isAlive

        override suspend fun collectTurn(
            first: Contents?,
            reply: Message?,
            onText: suspend (String) -> Unit,
            onThought: suspend (String) -> Unit,
        ): AgenticTurn {
            val flow = if (reply != null) {
                conversation.sendMessageAsync(reply)
            } else {
                conversation.sendMessageAsync(
                    first ?: error("LiteRTLm: loop turn needs initial contents or a tool reply")
                )
            }
            var terminal: Message? = null
            flow.collect { msg ->
                terminal = msg
                val text = extractTextContent(msg)
                if (text.isNotEmpty()) onText(text)
                extractThoughtContent(msg)?.let { thinking ->
                    if (thinking.isNotEmpty()) onThought(thinking)
                }
            }
            return AgenticTurn(
                terminal ?: throw IllegalStateException("LiteRTLm: engine returned an empty turn")
            )
        }
    }

    private suspend fun FlowCollector<StreamToken>.sendAgenticWithRetry(
        contents: Contents,
        conversationConfig: ConversationConfig,
        snapshot: LoopArmSnapshot,
        contextSize: Int,
        attempt: Int
    ) {
        val maxRetries = 2

        try {
            val engine = engineManager.getLiteRTLmEngine()
            if (!engine.isInitialized()) {
                throw IllegalStateException("Engine not initialized")
            }

            val conversation = acquireConversation(conversationConfig, snapshot)
            runToolLoop(ConversationTurnTransport(conversation), contents, contextSize)
        } catch (e: CancellationException) {
            // Stop means stop: same contract as the plain path — terminate,
            // never retry, never surface as Error.
            throw e
        } catch (e: Exception) {
            coroutineScope { ensureActive() }
            val isEngineError = e is IllegalStateException ||
                e.message?.contains("not alive", ignoreCase = true) == true ||
                e.message?.contains("not initialized", ignoreCase = true) == true

            if (isEngineError && attempt < maxRetries) {
                Timber.w(e, "LiteRTLmProvider: agentic engine error (attempt ${attempt + 1}/3), recovering...")
                activeConversation = null
                activeConversationConfig = null
                activeLoopArm = null
                recoverEngine()
                Timber.w("LiteRTLmProvider: Engine recovered, retrying agentic turn... (attempt ${attempt + 1})")
                sendAgenticWithRetry(contents, conversationConfig, snapshot, contextSize, attempt + 1)
            } else if (isEngineError) {
                Timber.e(e, "LiteRTLmProvider: agentic engine failed to recover after $maxRetries retries")
                emit(StreamToken.Error("Engine failed to recover. Please reload the model manually."))
            } else {
                Timber.e(e, "LiteRTLmProvider: unexpected agentic error")
                emit(StreamToken.Error("Chat error: ${e.message ?: "Unknown error"}"))
            }
        }
    }

    override suspend fun listModels(): Result<List<ModelInfo>> {
        return try {
            val active = engineManager.getActiveEngine()
            if (active != null && active.type == EngineType.LITE_RT_LM) {
                val modelInfo = ModelInfo(
                    id = active.modelPath,
                    name = active.modelPath.substringAfterLast("/"),
                    providerType = ProviderType.LITE_RT_LM
                )
                Result.success(listOf(modelInfo))
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun testConnection(): Result<ConnectionStatus> {
        return try {
            val engine = engineManager.getLiteRTLmEngine()
            if (engine.isInitialized()) {
                Result.success(ConnectionStatus.Connected)
            } else {
                Result.success(ConnectionStatus.Disconnected)
            }
        } catch (e: Exception) {
            Result.success(ConnectionStatus.Disconnected)
        }
    }

    // --- Private helpers ---

    private suspend fun FlowCollector<StreamToken>.sendContentsWithRetry(
        contents: Contents,
        conversationConfig: ConversationConfig,
        snapshot: LoopArmSnapshot,
        attempt: Int
    ) {
        val maxRetries = 2

        try {
            val engine = engineManager.getLiteRTLmEngine()
            if (!engine.isInitialized()) {
                throw IllegalStateException("Engine not initialized")
            }

            // LRT-02: Reuse a single long-lived Conversation across chat() calls.
            // The conversation is only recreated when the underlying engine is reloaded
            // (see resetConversation()), when a fatal error invalidates the native handle
            // (see retry path below), or when the 56-02 arming snapshot changes
            // (see acquireConversation — tools must never linger with grounding off).
            // Initial history is set on first creation; subsequent
            // calls append to the conversation in-place.
            val conversation = acquireConversation(conversationConfig, snapshot)

            conversation.sendMessageAsync(contents).collect { responseMsg ->
                val content = extractTextContent(responseMsg)
                if (content.isNotEmpty()) {
                    Timber.d("LiteRTLmProvider: delta (%d chars)", content.length)
                    emit(StreamToken.Delta(content))
                }
            }

            if (!conversation.isAlive) {
                throw IllegalStateException("Conversation not alive after streaming")
            }

            emit(StreamToken.Done())

        } catch (e: CancellationException) {
            // Stop means stop: a cancelled turn terminates, never retries, never
            // surfaces as a fake Error token.
            throw e
        } catch (e: Exception) {
            // 46-01 RUNTIME-14: a cancelled turn must never retry — Stop surfacing as
            // an engine-flavored error must not resurrect the turn (pitfall 4).
            // Active cancellation check (scope-inherited: throws
            // CancellationException when this turn's job is cancelled) BEFORE any
            // retry logic below. `coroutineScope` (not supervisorScope) inherits the
            // cancelled job, so ensureActive() observes it.
            coroutineScope { ensureActive() }
            val isEngineError = e is IllegalStateException ||
                e.message?.contains("not alive", ignoreCase = true) == true ||
                e.message?.contains("not initialized", ignoreCase = true) == true

            if (isEngineError && attempt < maxRetries) {
                Timber.w(e, "LiteRTLmProvider: engine error (attempt ${attempt + 1}/3), recovering...")
                // Null out the conversation — its native handle may be invalid after the error
                activeConversation = null
                activeConversationConfig = null
                activeLoopArm = null
                recoverEngine()
                Timber.w("LiteRTLmProvider: Engine recovered, retrying... (attempt ${attempt + 1})")
                sendContentsWithRetry(contents, conversationConfig, snapshot, attempt + 1)
            } else if (isEngineError) {
                Timber.e(e, "LiteRTLmProvider: engine failed to recover after $maxRetries retries")
                emit(StreamToken.Error("Engine failed to recover. Please reload the model manually."))
            } else {
                Timber.e(e, "LiteRTLmProvider: unexpected error")
                emit(StreamToken.Error("Chat error: ${e.message ?: "Unknown error"}"))
            }
        }
    }

    private fun recoverEngine() {
        val active = engineManager.getActiveEngine()
        if (active != null && active.type == EngineType.LITE_RT_LM) {
            val modelPath = active.modelPath
            // LRT-02: Drop the dead conversation; a new one will be created lazily on the
            // next chat() call. This must happen BEFORE switchToLiteRT() so we don't leak
            // a handle into a now-stale engine instance.
            resetConversation()
            try {
                engineManager.switchToLiteRT(modelPath)
                Timber.d("LiteRTLmProvider: engine recovered successfully")
            } catch (e: Exception) {
                Timber.e(e, "LiteRTLmProvider: engine recovery failed")
                throw e
            }
        }
    }

    /**
     * Quick-task (image-history-carry): map sanitized messages to engine
     * history, carrying recent history images. Pure function (JVM-testable)
     * extracted verbatim from the Step 3 inline mapping plus the carry.
     *
     * - The current message (last element) is excluded via dropLast(1).
     * - Among history USER turns with non-empty imageUris, the K most
     *   recent (K = HISTORY_IMAGE_CARRY_MAX, newest-first) carry their
     *   images as Content.ImageBytes via the Message.user(Contents)
     *   overload; that turn's text rides along as Content.Text.
     * - Identical data-URL strings are deduped within the carried set
     *   (newest occurrence wins; same image attached twice counts once).
     * - Each URL is decoded via decodeImage; null (malformed payload) is
     *   skipped silently — a bad history image never throws, never Errors
     *   the turn. A turn whose images all fail (or all dedupe away) keeps
     *   the exact pre-fix shape Message.user(String).
     * - SYSTEM/ASSISTANT/TOOL mapping is untouched (Phase 49 DEL-01:
     *   legacy tool rows map assistant-adjacent, read-only history).
     */
    internal fun buildHistoryMessages(sanitized: List<ChatMessage>): List<Message> {
        val history = sanitized.dropLast(1)
        // K newest image-bearing USER turns, by history index — via the
        // shared [HistoryImageCarry] rule (newest-first, dedupe,
        // skip-malformed); the decode check is this transport's `isUsable`.
        val keptByIndex = HistoryImageCarry.selectKeptUrls(
            sanitized,
            HISTORY_IMAGE_CARRY_MAX,
            isUsable = { decodeImage(it) != null },
        )
        return history.mapIndexed { index, msg ->
            when (msg.role) {
                Role.SYSTEM -> Message.system(msg.content)
                // Phase 49 (DEL-01): legacy tool rows map assistant-adjacent
                // (same shape as LocalLlmProvider.buildPrompt) — read-only
                // history, never re-executed.
                Role.ASSISTANT, Role.TOOL -> Message.model(msg.content)
                Role.USER -> {
                    val urls = keptByIndex[index]
                    if (urls.isNullOrEmpty()) {
                        Message.user(msg.content)
                    } else {
                        val imageContents = mutableListOf<Content>()
                        urls.forEach { dataUrl ->
                            decodeImage(dataUrl)?.let { imageContents.add(Content.ImageBytes(it)) }
                        }
                        if (imageContents.isEmpty()) {
                            Message.user(msg.content)
                        } else {
                            // Image-only history turns carry no empty text
                            // part (LiteRT-LM rejects empty text), same
                            // guard as the Step 4 current-turn block.
                            if (msg.content.isNotBlank()) imageContents.add(Content.Text(msg.content))
                            Message.user(Contents.of(imageContents))
                        }
                    }
                }
            }
        }
    }

    private fun decodeImage(dataUrl: String): ByteArray? {
        return try {
            val base64 = dataUrl.substringAfter("base64,")
            Base64.decode(base64, Base64.DEFAULT)
        } catch (e: Exception) {
            Timber.w(e, "LiteRTLmProvider: failed to decode image")
            null
        }
    }

    internal fun extractTextContent(message: Message): String {
        val raw = try {
            message.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
        } catch (e: Exception) {
            Timber.w(e, "LiteRTLmProvider: failed to extract text from message")
            ""
        }
        return Normalizer.normalize(raw, Normalizer.Form.NFC)
    }

    /**
     * 45-02 LRT-09 (0.17.x re-verification): read the reasoning stream from
     * `response.channels["thought"]` (verified: `Message.getChannels()` returns
     * `Map<String, String>` in litertlm-android-0.17.1). Returns null when the active
     * conversation has no thinking enabled. Quick-task (thinking-config):
     * the channel is now enabled via [thinkingConfigFor] when the Thinking
     * toggle is on AND the model is capable — thought tokens still never
     * interleave into answer Deltas (thinking UX THINK-02 owns display).
     * Safe by construction: with thinking disabled the channel is absent
     * and this returns null.
     */
    fun extractThoughtContent(message: Message): String? {
        return try {
            message.channels[THOUGHT_CHANNEL]?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Timber.w(e, "LiteRTLmProvider: failed to extract thought channel")
            null
        }
    }

    private fun clamp(value: Int, min: Int, max: Int, paramName: String): Int {
        return if (value < min) {
            Timber.w("LiteRTLmProvider: $paramName=$value clamped to min=$min")
            min
        } else if (value > max) {
            Timber.w("LiteRTLmProvider: $paramName=$value clamped to max=$max")
            max
        } else value
    }

    private fun clamp(value: Double, min: Double, max: Double, paramName: String): Double {
        return if (value < min) {
            Timber.w("LiteRTLmProvider: $paramName=$value clamped to min=$min")
            min
        } else if (value > max) {
            Timber.w("LiteRTLmProvider: $paramName=$value clamped to max=$max")
            max
        } else value
    }
}
