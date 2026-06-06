package com.warped.data.remote.provider

import com.warped.data.local.inference.InputSanitizer
import com.warped.data.local.security.ApiKeyStore
import com.warped.domain.llm.LlmModelHelper
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.Endpoint
import com.warped.domain.model.ProviderType
import com.warped.domain.model.StreamToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
 *    per-endpoint pattern in `ProviderRouter`) and runs `chat()` on `Dispatchers.IO`.
 *  - [stopResponse] cancels the in-flight `Job`; LM Studio has no native cancellation
 *    on the server, so the active OkHttp `Call` continues but its results are dropped.
 *    A future improvement is to track the `Call` reference and call `Call.cancel()`.
 *  - [cleanUp] calls `LMStudioProvider.unloadModel` with the stored `instanceId` so the
 *    server releases the model.
 */
@Singleton
class LmStudioHelper @Inject constructor(
    private val inputSanitizer: InputSanitizer,
    private val apiKeyStore: ApiKeyStore,
) : LlmModelHelper {

    override val type: ProviderType = ProviderType.LM_STUDIO

    private val activeEndpoint = AtomicReference<Endpoint?>(null)
    private val activeInstanceId = AtomicReference<String?>(null)
    private val activeJob = AtomicReference<Job?>(null)
    // PERF-08 / Phase 43: actual OkHttp Call lives inside LMStudioProvider.
    // Cancelling at the Flow level via activeJob is the effective cancellation
    // point today; deeper Call.cancel() requires threading the Call through
    // the provider. Tracked for v2.1.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        // 41-01: forward enableThinking via GenerationParameters.reasoningEnabled so
        // LMStudioProvider sets the request body `reasoning` field accordingly. When
        // false, also strip `<think>...</think>` markers client-side because some LM
        // Studio backends emit reasoning regardless of the request flag.
        val effectiveRequest = request.copy(
            parameters = request.parameters.copy(reasoningEnabled = enableThinking),
        )
        val provider = createProvider(endpoint, modelId)
        activeJob.set(scope.launch { /* sentinel: enables stopResponse() */ })
        val raw = provider.chat(effectiveRequest)
        return raw
            .let { upstream ->
                if (enableThinking) upstream
                else upstream.map { token ->
                    when (token) {
                        is StreamToken.Delta -> StreamToken.Delta(stripThinkTags(token.content))
                        is StreamToken.Done -> StreamToken.Done(stats = token.stats, reasoning = null)
                        is StreamToken.Error -> token
                    }
                }
            }
            .flowOn(Dispatchers.IO)
    }

    override fun resetConversation() {
        // LM Studio is stateless across requests; no conversation to reset.
    }

    override fun stopResponse() {
        val job = activeJob.getAndSet(null)
        if (job != null && job.isActive) {
            Timber.d("LmStudioHelper: cancelling active job")
            job.cancel()
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

    private fun createProvider(endpoint: Endpoint, modelId: String): LMStudioProvider {
        val key = apiKeyStore.getKey(endpoint.id)
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

    private companion object {
        private val THINK_TAG_REGEX = Regex("(?is)<think>.*?</think>")
        fun stripThinkTags(s: String): String =
            if (!s.contains("<think>", ignoreCase = true)) s
            else THINK_TAG_REGEX.replace(s, "").trimStart()
    }
}
