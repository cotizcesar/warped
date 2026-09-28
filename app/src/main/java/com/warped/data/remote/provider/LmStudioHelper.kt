package com.warped.data.remote.provider

import com.warped.data.local.inference.InputSanitizer
import com.warped.data.local.security.ApiKeyStore
import com.warped.data.repository.ModelAllowlistRepository
import com.warped.data.skills.ToolGateDecision
import com.warped.data.skills.ToolGating
import com.warped.domain.llm.LlmModelHelper
import com.warped.domain.model.ChatMessage
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.Endpoint
import com.warped.domain.model.ProviderType
import com.warped.domain.model.Role
import com.warped.domain.model.StreamToken
import com.warped.domain.skills.SkillIds
import com.warped.domain.skills.SkillRepository
import com.warped.domain.skills.ToolExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [LlmModelHelper] for the remote LM Studio v1 backend. Wraps the existing
 * [LMStudioProvider] (which is constructed per-call with the endpoint URL, model id, and
 * API key) and tracks the loaded `instanceId` returned by `/api/v1/models/load`.
 *
 * Lifecycle:
 *  - [setEndpoint] MUST be called before [initialize] (this is the only state needed
 *    to address a remote endpoint). It's a helper-specific method (not on the
 *    [LlmModelHelper] interface) because the LiteRT-LM helper is configured via its
 *    `modelPath` alone.
 *  - [initialize] dispatches `LMStudioProvider.loadModel` on `Dispatchers.IO` and stores
 *    the returned `instanceId`.
 *  - [runInference] creates a fresh `LMStudioProvider` per call (matching the existing
 *    per-endpoint pattern in `ProviderRouter`), retains the live OkHttp `Call` via
 *    the provider's `onCallCreated` hook, and runs `chat()` on `Dispatchers.IO`.
 *  - [stopResponse] calls `Call.cancel()` on the retained handle: the socket is
 *    torn down immediately and the SSE read loop exits with no trailing tokens
 *    and no fake Error bubble. Safe when idle (no-op).
 *  - [cleanUp] calls `LMStudioProvider.unloadModel` with the stored `instanceId` so the
 *    server releases the model.
 */
@Singleton
class LmStudioHelper @Inject constructor(
    private val inputSanitizer: InputSanitizer,
    private val apiKeyStore: ApiKeyStore,
    // 47-03: loop-vs-native routing needs the skill toggles, the remote
    // gating verdict, and the shared executor. All Hilt-bound already
    // (SkillsModule binds SkillRepository + ToolExecutor; allowlist is
    // @Singleton @Inject).
    private val skillRepository: SkillRepository,
    private val modelAllowlistRepository: ModelAllowlistRepository,
    private val toolExecutor: ToolExecutor,
) : LlmModelHelper {

    override val type: ProviderType = ProviderType.LM_STUDIO

    private val activeEndpoint = AtomicReference<Endpoint?>(null)
    private val activeInstanceId = AtomicReference<String?>(null)
    // 46-01 RUNTIME-14: the real cancellable handle — the live OkHttp Call retained
    // via the provider's onCallCreated hook. No sentinel Job: stopResponse() must
    // reach the socket, not a no-op coroutine.
    private val activeCall = AtomicReference<Call?>(null)

    @Volatile
    private var initializedModelId: String? = null

    /** Set the active endpoint. Must be called before [initialize] / [runInference]. */
    fun setEndpoint(endpoint: Endpoint) {
        activeEndpoint.set(endpoint)
    }

    /** Returns the model instance id returned by `/api/v1/models/load`, or null. */
    fun getInstanceId(): String? = activeInstanceId.get()

    override suspend fun initialize(modelPath: String) {
        val endpoint = activeEndpoint.get()
            ?: error("LmStudioHelper: setEndpoint() must be called before initialize()")
        val provider = createProvider(endpoint, modelPath)
        Timber.d("LmStudioHelper: initialize(modelPath=$modelPath) — calling loadModel")
        val result = provider.loadModel(modelPath)
        result.fold(
            onSuccess = { instanceId ->
                activeInstanceId.set(instanceId)
                initializedModelId = modelPath
                Timber.d("LmStudioHelper: loaded model instance=$instanceId")
            },
            onFailure = { e ->
                throw IllegalStateException(
                    "Failed to load LM Studio model: ${e.message}",
                    e,
                )
            }
        )
    }

    override fun runInference(
        request: ChatRequest,
        enableThinking: Boolean,
    ): Flow<StreamToken> {
        val endpoint = activeEndpoint.get()
            ?: error("LmStudioHelper: setEndpoint() must be called before runInference()")
        val modelId = initializedModelId
            ?: error("LmStudioHelper: initialize() must be called before runInference()")
        val effectiveRequest = request.copy(
            parameters = request.parameters.copy(reasoningEnabled = enableThinking),
        )
        val provider = createProvider(endpoint, modelId)
        // 47-03 (D-04/D-07): loop-vs-plain routing. Skills enabled + gate
        // open (allowlist OR trained_for_tool_use) → the parallel
        // /v1/chat/completions loop; otherwise the untouched native
        // /api/v1/chat path, with the prompt-injection fallback preserved
        // for the no-support case. Zero enabled → silent plain chat.
        val enabledIds = skillRepository.enabledMap.value
            .filterValues { it }.keys.filter { it in SkillIds.TOOL_IDS }
        val gate = ToolGating.decide(
            enabledIds,
            ToolGating.supportsRemoteTools(modelAllowlistRepository, modelId),
        )
        val raw: Flow<StreamToken> = when (gate) {
            is ToolGateDecision.UseTools -> {
                val tools = buildCompletionsTools(gate.ids)
                provider.chatCompletionsWithTools(effectiveRequest, tools, toolExecutor) { call ->
                    activeCall.set(call)
                }
            }
            is ToolGateDecision.NoSupportFallback -> {
                provider.chat(withFallbackSystemPrompt(effectiveRequest, gate.ids)) { call ->
                    activeCall.set(call)
                }
            }
            ToolGateDecision.PlainChat -> {
                provider.chat(effectiveRequest) { call -> activeCall.set(call) }
            }
        }
        return raw
            .onCompletion { activeCall.set(null) } // no stale handle: follow-up turns are safe (pitfall 2)
            .let { upstream ->
                if (enableThinking) upstream
                else upstream.map { token ->
                    when (token) {
                        is StreamToken.Delta -> StreamToken.Delta(stripThinkTags(token.content))
                        is StreamToken.Done -> StreamToken.Done(stats = token.stats, reasoning = null)
                        is StreamToken.Error -> token
                        // 47-02: tool status passes through untouched.
                        is StreamToken.ToolStatus -> token
                        // 47-03: remote-loop completion records pass through
                        // (the ViewModel persists them + drives error rows).
                        is StreamToken.ToolCompleted -> token
                    }
                }
            }
            .flowOn(Dispatchers.IO)
    }

    override fun resetConversation() {
        // LM Studio is stateless across requests; no conversation to reset.
    }

    override fun stopResponse() {
        // 46-01 RUNTIME-14: immediate socket teardown. Safe when idle (no-op).
        val call = activeCall.getAndSet(null)
        if (call != null) {
            Timber.d("LmStudioHelper: cancelling active Call")
            try {
                call.cancel()
            } catch (e: Exception) {
                Timber.w(e, "LmStudioHelper: Call.cancel failed")
            }
        }
    }

    override fun cleanUp() {
        Timber.d("LmStudioHelper: cleanUp — unloading model instance")
        stopResponse()
        val endpoint = activeEndpoint.get() ?: return
        val instanceId = activeInstanceId.getAndSet(null) ?: return
        try {
            val provider = createProvider(endpoint, initializedModelId ?: "")
            runBlocking { provider.unloadModel(instanceId) }
        } catch (e: Exception) {
            Timber.w(e, "LmStudioHelper: unload during cleanUp failed")
        }
        initializedModelId = null
    }

    /**
     * 47-03: merge the prompt-injection fallback into the request's SYSTEM
     * message (append when one exists, prepend otherwise) — v2.0 behavior
     * preserved for models without tool support.
     */
    private fun withFallbackSystemPrompt(request: ChatRequest, ids: List<String>): ChatRequest {
        val fallback = ToolGating.fallbackSystemPrompt(ids)
        val existing = request.messages.firstOrNull { it.role == Role.SYSTEM }
        val merged = if (existing == null) {
            fallback
        } else {
            "${existing.content}\n$fallback"
        }
        val rest = request.messages.filter { it.role != Role.SYSTEM }
        return request.copy(
            messages = listOf(ChatMessage(role = Role.SYSTEM, content = merged)) + rest,
        )
    }

    private fun createProvider(endpoint: Endpoint, modelId: String): LMStudioProvider {        val key = apiKeyStore.getKey(endpoint.id)
        val keyStr = if (key != null && key.isNotEmpty()) {
            String(key).also { key.fill('0') }
        } else null
        return LMStudioProvider(
            baseUrl = endpoint.url,
            modelId = modelId,
            apiKey = keyStr,
            inputSanitizer = inputSanitizer,
        )
    }

    companion object {
        private val THINK_TAG_REGEX = Regex("<think>.*?</think>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        fun stripThinkTags(s: String): String =
            if (!s.contains("<think>", ignoreCase = true)) s
            else THINK_TAG_REGEX.replace(s, "").trimStart()
    }
}
