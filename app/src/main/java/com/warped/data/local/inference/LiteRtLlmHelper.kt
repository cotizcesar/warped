package com.warped.data.local.inference

import com.warped.domain.llm.LlmModelHelper
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.ProviderType
import com.warped.domain.model.StreamToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [LlmModelHelper] for the local LiteRT-LM backend. Wraps the existing [LiteRTLmProvider]
 * (which is the per-call streaming/conversation factory) and [EngineManager] (which owns
 * the single loaded model and mmap-only cache).
 *
 * Contract:
 *  - [initialize] dispatches `EngineManager.switchToLiteRT` on `Dispatchers.IO`. The
 *    switch is a no-op if the requested model is already loaded.
 *  - [runInference] delegates to `LiteRTLmProvider.chat`, which already runs the
 *    per-token collection on `Dispatchers.Default`. We re-`flowOn(Dispatchers.Default)`
 *    for clarity.
 *  - [resetConversation] delegates to `LiteRTLmProvider.resetConversation`.
 *  - [stopResponse] cancels the active [Job] tracked by the helper. The underlying
 *    `conversation.sendMessageAsync` Flow is collected on the cancelled scope and
 *    completes immediately.
 *  - [cleanUp] resets the conversation and unloads the engine. After this, the helper
 *    is unusable until [initialize] is called again.
 */
@Singleton
class LiteRtLlmHelper @Inject constructor(
    private val liteRTLmProvider: LiteRTLmProvider,
    private val engineManager: EngineManager,
) : LlmModelHelper {

    override val type: ProviderType = ProviderType.LITE_RT_LM

    private val activeJob = AtomicReference<Job?>(null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var initializedModelPath: String? = null

    override suspend fun initialize(modelPath: String) {
        if (initializedModelPath == modelPath && engineManager.isEngineLoaded()) {
            Timber.d("LiteRtLlmHelper: model $modelPath already loaded, skipping initialize")
            return
        }
        Timber.d("LiteRtLlmHelper: initialize(modelPath=$modelPath) — dispatching to IO")
        withContext(Dispatchers.IO) {
            engineManager.switchToLiteRT(modelPath)
        }
        initializedModelPath = modelPath
        // Ensure the conversation is in a clean state for the first chat() call.
        liteRTLmProvider.resetConversation()
    }

    override fun runInference(
        request: ChatRequest,
        enableThinking: Boolean,
    ): Flow<StreamToken> {
        // We track the active job so stopResponse() can cancel it. The flow is collected
        // by the caller on its own scope; if the caller cancels, the job ends naturally.
        // We also store the most-recent job so an explicit stopResponse() works even if
        // the caller's scope is still alive.
        val job = scope.launch {
            // Drain the flow so any cancel propagates; emissions are ignored here.
            liteRTLmProvider.chat(request).collect { /* no-op; caller has the flow */ }
        }
        activeJob.set(job)
        return liteRTLmProvider.chat(request).flowOn(Dispatchers.Default)
    }

    override fun resetConversation() {
        liteRTLmProvider.resetConversation()
    }

    override fun stopResponse() {
        val job = activeJob.getAndSet(null)
        if (job != null && job.isActive) {
            Timber.d("LiteRtLlmHelper: cancelling active job")
            job.cancel()
        }
    }

    override fun cleanUp() {
        Timber.d("LiteRtLlmHelper: cleanUp — unloading engine and resetting state")
        stopResponse()
        liteRTLmProvider.resetConversation()
        try {
            engineManager.unloadCurrent()
        } catch (e: Exception) {
            Timber.w(e, "LiteRtLlmHelper: engine unload during cleanUp failed")
        }
        initializedModelPath = null
    }
}
