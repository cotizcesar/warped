package com.warped.data.remote.provider

import com.warped.data.local.inference.InputSanitizer
import com.warped.data.local.security.ApiKeyStore
import com.warped.data.grounding.DuckDuckGoSearchRepository
import com.warped.data.grounding.MultiUrlFetcher
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
import kotlinx.coroutines.flow.mapNotNull
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
     *
     * Quick-task (DDG-default): the search collaborator is the DDG-only
     * repository.
     */
    private val ddg: DuckDuckGoSearchRepository? = null,
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

    /**
     * True when the server reports [modelPath] loaded (exact key,
     * selected-variant or slug-suffix match against non-empty
     * `loaded_instances` — endpoint ids are often short names).
     * False (reload) on any error or empty set — a needless reload is
     * today's behavior, a wrong skip would chat against another model.
     */
    private suspend fun isModelLoaded(
        provider: LMStudioProvider,
        modelPath: String,
    ): Boolean = try {
        provider.listLoadedModelKeys().getOrNull()?.any { key ->
            LMStudioProvider.isLoadedKeyMatch(key, modelPath)
        } == true
    } catch (e: Exception) {
        Timber.w(e, "LmStudioHelper: loaded-list check failed, reloading")
        false
    }

    override suspend fun initialize(modelPath: String) {
        val endpoint = activeEndpoint.get()
            ?: error("LmStudioHelper: setEndpoint() must be called before initialize()")
        val provider = createProvider(endpoint, modelPath)
        // Idempotency (user report 2026-10-03): every send calls
        // initialize(), which used to POST /models/load unconditionally —
        // the server reloaded (and OOM-killed) the model on every
        // question. Consult the server first on EVERY call (one cheap
        // GET): if this model is loaded under any id form, skip the
        // reload — this also covers fresh starts against an already
        // holding server. Fail-safe direction: any doubt reloads
        // (today's behavior), a skip needs a positive exact/slug match.
        if (isModelLoaded(provider, modelPath)) {
            if (initializedModelId != modelPath) {
                Timber.d("LmStudioHelper: $modelPath already loaded server-side — skipping reload")
            }
            initializedModelId = modelPath
            return
        }
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
                else {
                    // Thinking off: drop thought spans with a cross-token
                    // state machine. The provider fans reasoning out as
                    // SPLIT markers ("<think>" … thought … "</think>" in
                    // separate Deltas), so the old single-token pair strip
                    // let the orphan markers through — and the ViewModel
                    // then truncated the whole turn at the first "<think>"
                    // (empty bubble, 2026-10-04).
                    val thinkFilter = ThinkStripFilter()
                    upstream.mapNotNull { token ->
                        when (token) {
                            is StreamToken.Delta -> StreamToken.Delta(thinkFilter.filterDelta(token.content))
                            is StreamToken.Done -> StreamToken.Done(stats = token.stats, reasoning = null)
                            is StreamToken.Error -> token
                            // Quick-task (live-thinking): hidden when the toggle
                            // is off — dropped, never surfaced as text.
                            is StreamToken.Thinking -> null
                            // Legacy StreamToken variants: no producer remains
                            // post-DEL-01; passed through untouched.
                            is StreamToken.ToolStatus -> token
                            is StreamToken.ToolCompleted -> token
                            // Phase 57 UI-review: typed tools-unsupported
                            // notice passes through untouched (never
                            // think-stripped, never filtered).
                            is StreamToken.ToolsUnsupported -> token
                        }
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
            ddg = ddg,
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

/**
 * Cross-token thought-span filter for the thinking-off path (2026-10-04).
 *
 * The provider fans reasoning out as SPLIT markers — `"<think>"`,
 * thought text and `"</think>"` arrive in separate [StreamToken.Delta]s
 * (native `reasoning.*` events and compat `reasoning_content` alike) —
 * so a single-token pair strip can never remove them. This state machine
 * drops thought spans across token boundaries and keeps the answer text
 * around them. Complete pairs inside one token are removed first (same
 * regex as [LmStudioHelper.stripThinkTags]); a lone orphan `</think>`
 * with no open span is marker noise (the answer follows it, R1-style).
 *
 * Sequential use only: one instance per turn (the thinking-off
 * `mapNotNull` owns it). Pure — unit-tested.
 */
internal class ThinkStripFilter {
    private var inThought = false

    fun filterDelta(s: String): String {
        var rest = LmStripPairRegex.replace(s, "")
        if (!rest.contains('<', ignoreCase = false) && !inThought) return rest
        val out = StringBuilder()
        while (true) {
            val lower = rest.lowercase()
            if (inThought) {
                val close = lower.indexOf(CLOSE_TAG)
                if (close < 0) return out.toString()
                rest = rest.substring(close + CLOSE_TAG.length)
                inThought = false
            } else {
                val open = lower.indexOf(OPEN_TAG)
                val close = lower.indexOf(CLOSE_TAG)
                if (close >= 0 && (open < 0 || close < open)) {
                    rest = rest.substring(close + CLOSE_TAG.length)
                    continue
                }
                if (open < 0) {
                    out.append(rest)
                    return out.toString()
                }
                out.append(rest.substring(0, open))
                rest = rest.substring(open + OPEN_TAG.length)
                inThought = true
            }
        }
    }

    companion object {
        private const val OPEN_TAG = "<think>"
        private const val CLOSE_TAG = "</think>"
        private val LmStripPairRegex =
            Regex("<think>.*?</think>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    }
}
