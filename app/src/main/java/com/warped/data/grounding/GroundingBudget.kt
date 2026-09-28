package com.warped.data.grounding

/**
 * Phase 52 (EXTRACT-02): global model-window-aware grounding budget.
 *
 * A single char budget is split evenly across N pages so the fused
 * [WEB CONTEXT 1..N] block fits small local-model windows. Numbers are
 * LOW-confidence estimates (see CONTEXT.md) — keep every constant behind
 * these two pure functions (Int in, Int out) so TUNE-01 can refine them
 * later. The per-page floor is sized for markdown (denser per char than
 * flat text); the global tiers stay fit for 4K-token windows.
 * Pure Kotlin — no Android imports, unit-testable on the JVM.
 */
object GroundingBudget {

    /** Floor so a page is never truncated into uselessness (markdown-sized). */
    const val MIN_PER_PAGE = 1500

    /**
     * Global char budget for the fused block, keyed on the model window
     * signal (`GenerationParameters.contextSize`, default 4096).
     *
     * Small windows (<= 2048) shrink one tier so the fused block still
     * fits; larger windows scale up modestly (+1 char per 4 over 4096).
     */
    fun globalBudget(contextSize: Int): Int = when {
        contextSize <= 2048 -> 4500
        contextSize <= 4096 -> 6000
        else -> 6000 + (contextSize - 4096) / 4
    }

    /**
     * Per-page slice of the global budget for [n] pages, never below
     * [MIN_PER_PAGE].
     */
    fun perPageBudget(contextSize: Int, n: Int): Int =
        (globalBudget(contextSize) / n.coerceAtLeast(1)).coerceAtLeast(MIN_PER_PAGE)
}
