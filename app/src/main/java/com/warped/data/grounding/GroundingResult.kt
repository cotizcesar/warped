package com.warped.data.grounding

/**
 * Phase 50 (WEB-01..WEB-04): outcome of the web-grounding fetch step.
 *
 * [Grounded] carries the ready-to-inject sources block plus its source
 * URL. [ModelOnly] means fetch produced zero injectable bytes — the turn
 * proceeds with the original prompt and the UI renders a model-only banner
 * (offline vs fetch-failure copy) from the reason.
 *
 * Phase 52 (FETCH-01): [Grounded.text] is the sanitized extracted text
 * (already truncated at the fetch budget) backing the fused multi-page
 * block — the orchestrator fuses texts, never re-parses framed blocks.
 */
sealed interface GroundingResult {

    data class Grounded(
        val block: String,
        val url: String,
        val text: String,
    ) : GroundingResult

    enum class Reason {
        OFFLINE,
        FETCH_FAILED,
    }

    data class ModelOnly(
        val reason: Reason,
    ) : GroundingResult
}
