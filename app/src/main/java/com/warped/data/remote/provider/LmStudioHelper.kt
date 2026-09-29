package com.warped.data.remote.provider

import com.warped.data.local.inference.InputSanitizer
import com.warped.data.local.security.ApiKeyStore
import com.warped.data.grounding.MultiUrlFetcher
import com.warped.data.grounding.TavilySearchRepository
import com.warped.data.grounding.WebPageFetcher
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.domain.llm.LlmModelHelper
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.Endpoint
import com.warped.domain.model.ProviderType
import com.warped.domain.model.StreamToken
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
 *    per-endpoint pattern in `ProviderRouter`), sets its `callHook` so the
 *    live OkHttp `Call` is retained on EITHER path (armed compat loop or
 *    native turn), and runs the 1-arg armed dispatcher `chat()` on
 *    `Dispatchers.IO`.
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
    /**
     * Phase 57 (57-02, CR-01 fix): tool-loop collaborators (Phase 55/52
     * singletons) forwarded in [createProvider] so the compat loop arms
     * on the live chat path. Nullable with null defaults so legacy
     * manual call sites keep compiling; Hilt always provides bindings.
     */
    private val tavily: TavilySearchRepository? = null,
    private val multiUrlFetcher: MultiUrlFetcher? = null,
    private val webPageFetcher: WebPageFetcher? = null,
    private val advancedPreferences: AdvancedPreferences? = null,
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
        // Phase 49 (DEL-01): single-turn plain chat — no skills, no tool
        // loop, no prompt-injection fallback. Legacy Role.TOOL history rows
        // replay provider-side as plain text (see LMStudioProvider.chat).
        //
        // Phase 57 (WR-01 fix): collect the 1-arg armed dispatcher (NOT
        // the 3-arg native overload — a trailing lambda here would bind
        // `onCallCreated` on the native path and bypass the compat loop
        // entirely). The socket still reaches `stopResponse()` via
        // `callHook`, which both branches forward.
        provider.callHook = { call -> activeCall.set(call) }
        val raw: Flow<StreamToken> = provider.chat(effectiveRequest)
        return raw
            .onCompletion { activeCall.set(null) } // no stale handle: follow-up turns are safe (pitfall 2)
            .let { upstream ->
                if (enableThinking) upstream
                else upstream.map { token ->
                    when (token) {
                        is StreamToken.Delta -> StreamToken.Delta(stripThinkTags(token.content))
                        is StreamToken.Done -> StreamToken.Done(stats = token.stats, reasoning = null)
                        is StreamToken.Error -> token
                        // Legacy StreamToken variants: no producer remains
                        // post-DEL-01; passed through untouched.
                        is StreamToken.ToolStatus -> token
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

    private fun createProvider(endpoint: Endpoint, modelId: String): LMStudioProvider {        val key = apiKeyStore.getKey(endpoint.id)
        val keyStr = if (key != null && key.isNotEmpty()) {
            String(key).also { key.fill('0') }
        } else null
        return LMStudioProvider(
            baseUrl = endpoint.url,
            modelId = modelId,
            apiKey = keyStr,
            inputSanitizer = inputSanitizer,
            tavily = tavily,
            multiUrlFetcher = multiUrlFetcher,
            webPageFetcher = webPageFetcher,
            advancedPreferences = advancedPreferences,
        )
    }

    companion object {
        private val THINK_TAG_REGEX = Regex("<think>.*?</think>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        fun stripThinkTags(s: String): String =
            if (!s.contains("<think>", ignoreCase = true)) s
            else THINK_TAG_REGEX.replace(s, "").trimStart()
    }
}
