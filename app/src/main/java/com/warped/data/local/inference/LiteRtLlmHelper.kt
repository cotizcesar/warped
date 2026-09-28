package com.warped.data.local.inference

import com.warped.domain.llm.LlmModelHelper
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.ProviderType
import com.warped.domain.model.StreamToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
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
 *  - [stopResponse] delegates to `LiteRTLmProvider.cancelActiveGeneration`, which
 *    halts native generation via `Conversation.cancelProcess()` while keeping the
 *    conversation reusable for the next turn. No sentinel Job: Stop must reach the
 *    native handle.
 *  - [cleanUp] resets the conversation and unloads the engine. After this, the helper
 *    is unusable until [initialize] is called again.
 */
@Singleton
class LiteRtLlmHelper @Inject constructor(
    private val liteRTLmProvider: LiteRTLmProvider,
    private val engineManager: EngineManager,
) : LlmModelHelper {

    override val type: ProviderType = ProviderType.LITE_RT_LM

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
                        // 47-02: live tool status passes through untouched
                        // (never think-stripped, never filtered).
                        is StreamToken.ToolStatus -> token
                    }
                }
            }
            .flowOn(Dispatchers.Default)
    }

    override fun resetConversation() {
        liteRTLmProvider.resetConversation()
    }

    override fun stopResponse() {
        // 46-01 RUNTIME-14: halt native generation, keep the conversation alive.
        // Never throws: Stop must be safe when idle.
        try {
            liteRTLmProvider.cancelActiveGeneration()
        } catch (e: Exception) {
            Timber.w(e, "LiteRtLlmHelper: cancelActiveGeneration failed")
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
