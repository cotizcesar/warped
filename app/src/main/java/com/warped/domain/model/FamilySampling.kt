package com.warped.domain.model

/**
 * Vendor-recommended sampling per model family, scraped from upstream
 * model cards (2026-10-03, see `/tmp/opencode/readme_params.log`).
 * Null field = no vendor guidance → the size-tier default applies.
 *
 * Sources (all from the official base-model README):
 * - Qwen3/Qwen3.5: thinking 0.6/20/0.95, non-thinking 0.7/20/0.8
 * - Gemma 4: 1.0/64/0.95 (overrides size tiers — Google tunes hot)
 * - SmolLM2: 0.2/0.9 · SmolLM3: 0.6/0.95 · DeepSeek-R1-Distill: 0.6/0.95
 * - Ministral: instruct 0.1, reasoning 0.7/0.95
 * - Phi-4: instruct 0.0, reasoning 0.8/0.95
 * - LFM2.5: instruct 0.1/topK 50/rep 1.05, thinking 0.6/topK 50/rep 1.05
 * - TinyLlama-Chat: 0.7/50/0.95 · InternVL3: 0.8/40/0.8
 *
 * No card data (gated or stub READMEs — gemma-3/3n, Llama, OLMo, Qwen2.5,
 * Granite, FastVLM, Ovis, Falcon): null → size-tier fallback. Gemma-3/3n
 * inherit the Gemma-4 row (same family guidance).
 */
data class FamilySampling(
    val temperature: Float? = null,
    val topK: Int? = null,
    val topP: Float? = null,
    val repeatPenalty: Float? = null,
)

fun familySamplingFor(modelName: String, thinking: Boolean): FamilySampling? {
    val name = modelName.lowercase()
    val reasoning = thinking || hasReasoningMarker(modelName)
    return when {
        "qwen3" in name -> if (reasoning) {
            FamilySampling(temperature = 0.6f, topK = 20, topP = 0.95f)
        } else {
            FamilySampling(temperature = 0.7f, topK = 20, topP = 0.8f)
        }
        "gemma" in name ->
            FamilySampling(temperature = 1.0f, topK = 64, topP = 0.95f)
        "smollm2" in name ->
            FamilySampling(temperature = 0.2f, topP = 0.9f)
        "smollm3" in name ->
            FamilySampling(temperature = 0.6f, topP = 0.95f)
        "deepseek" in name ->
            FamilySampling(temperature = 0.6f, topP = 0.95f)
        "ministral" in name -> if (reasoning) {
            FamilySampling(temperature = 0.7f, topP = 0.95f)
        } else {
            FamilySampling(temperature = 0.1f)
        }
        "phi-4" in name -> if (reasoning) {
            FamilySampling(temperature = 0.8f, topP = 0.95f)
        } else {
            FamilySampling(temperature = 0.0f)
        }
        "lfm2.5" in name -> if (reasoning) {
            FamilySampling(temperature = 0.6f, topK = 50, repeatPenalty = 1.05f)
        } else {
            FamilySampling(temperature = 0.1f, topK = 50, repeatPenalty = 1.05f)
        }
        "tinyllama" in name ->
            FamilySampling(temperature = 0.7f, topK = 50, topP = 0.95f)
        "internvl" in name ->
            FamilySampling(temperature = 0.8f, topK = 40, topP = 0.8f)
        else -> null
    }
}

/**
 * Name markers for dedicated reasoning models (R1 distills, thinking /
 * reasoning variants). These ALWAYS emit a thought trace, so live
 * think-routing may assume tag-less mid-stream text is thought.
 * Hybrid thinkers (SmolLM3: answers directly when the question is
 * simple) carry no marker — their plain answers must stream in the
 * bubble, routing to the panel only once think markers are seen.
 */
internal fun hasReasoningMarker(modelName: String): Boolean {
    val name = modelName.lowercase()
    return "thinking" in name || "reasoning" in name ||
        "r1" in name || "distill" in name
}
