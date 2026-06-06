package com.warped.domain.llm

import com.warped.domain.model.ChatRequest
import com.warped.domain.model.ProviderType
import com.warped.domain.model.StreamToken
import com.warped.domain.skill.Skill
import kotlinx.coroutines.flow.Flow

/**
 * Unified inference surface for local (LiteRT-LM) and remote (LM Studio) chat backends.
 * Mirrors google-ai-edge/gallery's LlmModelHelper interface shape (5 lifecycle methods +
 * one streaming method) so every Warped feature can speak to either backend through the
 * same call sites.
 *
 * Lifecycle:
 *  1. [initialize] — load the model. May take seconds; the impl blocks on the appropriate
 *     dispatcher internally. The caller is responsible for offloading to its own scope.
 *  2. [runInference] — zero or more times, returning a [Flow] of [StreamToken].
 *  3. [resetConversation] — drop the conversation context. Next [runInference] starts fresh.
 *  4. [stopResponse] — cooperatively cancel an in-flight [runInference] (if any).
 *  5. [cleanUp] — release resources. After this, the helper is unusable until [initialize]
 *     is called again.
 */
interface LlmModelHelper {
    /** Backend type this helper represents. */
    val type: ProviderType

    /**
     * Load the model at [modelPath]. For local backends this is a filesystem path
     * (e.g. `/data/data/com.warped.app/files/models/foo.litertlm`). For remote backends
     * this is the model identifier (e.g. `qwen2.5-7b-instruct`).
     *
     * The implementation MUST perform the IO/blocking work on `Dispatchers.IO` (or
     * `Dispatchers.Default` for the LiteRT-LM JNI init) and only return when the model
     * is ready to serve inference.
     */
    suspend fun initialize(modelPath: String)

    /**
     * Run inference on the loaded model.
     *
     * @param request chat history + sampling parameters
     * @param enableThinking when true, the helper asks the backend to surface reasoning
     *        (e.g. DeepSeek-R1 `<think>` trace, LM Studio `reasoning.delta`). The helper
     *        emits the reasoning text as `StreamToken.Delta(content)` so existing UI
     *        pipelines (which already split on `<think>` tags) work unchanged.
     * @return a [Flow] of [StreamToken] — exactly one terminal `Done` or `Error`.
     */
    fun runInference(
        request: ChatRequest,
        enableThinking: Boolean = false,
        skills: List<Skill> = emptyList(),
    ): Flow<StreamToken>

    /**
     * Drop the active conversation context. The next [runInference] call will start with
     * an empty history. Safe to call when no conversation is active.
     */
    fun resetConversation()

    /**
     * Cooperatively cancel an in-flight [runInference] Flow. After this returns, the
     * flow collected by the caller completes (or yields a single `StreamToken.Error`).
     * For remote backends this also cancels the underlying OkHttp `Call`.
     */
    fun stopResponse()

    /**
     * Release all resources. After this, the helper is unusable until [initialize] is
     * called again. Safe to call multiple times.
     */
    fun cleanUp()
}
