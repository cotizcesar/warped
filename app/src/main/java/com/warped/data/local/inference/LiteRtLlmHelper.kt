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
import kotlinx.coroutines.flow.map
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
        skills: List<com.warped.domain.skill.Skill>,
    ): Flow<StreamToken> {
        // 44-02: log the active skill list. LiteRT-LM 0.13.1 does not yet expose
        // a tool-registration entry point from a Skill value object, so the
        // skills are surfaced as metadata only. The model can still emit
        // `[tool:NAME]` markers and ChatViewModel handles them.
        if (skills.isNotEmpty()) {
            Timber.d("LiteRtLlmHelper: runInference with ${skills.size} skills: ${skills.joinToString { it.id }}")
        }
        // 41-01: thread `enableThinking` through by mutating the request's
        // GenerationParameters.reasoningEnabled flag. Then strip `<think>...</think>`
        // markers client-side when the user has the toggle off — belt+suspenders
        // in case the underlying provider emits reasoning anyway.
        val effectiveRequest = request.copy(
            parameters = request.parameters.copy(reasoningEnabled = enableThinking),
        )
        val raw = liteRTLmProvider.chat(effectiveRequest)
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
            .flowOn(Dispatchers.Default)
            .also { activeJob.set(scope.launch { /* sentinel: enables stopResponse() */ }) }
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

    private companion object {
        private val THINK_TAG_REGEX = Regex("(?is)<think>.*?</think>")
        fun stripThinkTags(s: String): String =
            if (!s.contains("<think>", ignoreCase = true)) s
            else THINK_TAG_REGEX.replace(s, "").trimStart()
    }
}
