package com.warped.data.grounding

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 52 (FETCH-01..FETCH-03): parallel fan-out orchestrator over the
 * frozen single-page fetch policy.
 *
 * Contract: dedupe preserving first-seen order, hard cap of 5 (6th+ URLs
 * are ignored deterministically and never enter progress state), fan-out
 * with `coroutineScope` + `async(ioDispatcher)` + `awaitAll()` zipped back
 * by index so Fuentes order == block order always. Per-page extraction is
 * truncated once at the global-budget split (`perPageBudget`) inside the
 * fetch call; fusion concatenates only (no second truncation).
 *
 * Routing: any [GroundingResult.Grounded] wins → [MultiUrlResult.Fused]
 * with ok URLs in paste order plus the skipped list (never silent drops);
 * all-[GroundingResult.ModelOnly] → [MultiUrlResult.AllFailed] with the
 * worst-case reason (OFFLINE wins over FETCH_FAILED).
 *
 * Cancellation: `coroutineScope` (NOT supervisorScope) so the single
 * cancel path (Stop button / new-turn pre-cancel) aborts every in-flight
 * child; [CancellationException] propagates and is never converted to
 * model-only. Pure Kotlin apart from the injected fetcher — JVM-testable.
 */
sealed interface MultiUrlResult {

    data class Fused(
        val block: String,
        val okUrls: List<String>,
        val skippedUrls: List<String>,
    ) : MultiUrlResult

    data class AllFailed(
        val reason: GroundingResult.Reason,
    ) : MultiUrlResult
}

@Singleton
class MultiUrlFetcher @Inject constructor(
    private val fetcher: WebPageFetcher,
) {

    /** Overridable for deterministic JVM tests; production stays on IO. */
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    suspend fun fetchAll(
        urls: List<String>,
        contextSize: Int,
        onProgress: ((done: Int, total: Int) -> Unit)? = null,
    ): MultiUrlResult = coroutineScope {
        val targets = urls.distinct().take(MAX_URLS)
        if (targets.isEmpty()) {
            return@coroutineScope MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED)
        }
        val perPage = GroundingBudget.perPageBudget(contextSize, targets.size)
        val completed = AtomicInteger(0)
        val results = targets.map { url ->
            async(ioDispatcher) {
                try {
                    url to fetcher.fetch(url, perPage)
                } finally {
                    onProgress?.invoke(completed.incrementAndGet(), targets.size)
                }
            }
        }.awaitAll()

        val okPages = mutableListOf<Pair<String, String>>()
        val skipped = mutableListOf<String>()
        var sawOffline = false
        for ((pastedUrl, result) in results) {
            when (result) {
                is GroundingResult.Grounded -> okPages.add(result.url to result.text)
                is GroundingResult.ModelOnly -> {
                    skipped.add(pastedUrl)
                    if (result.reason == GroundingResult.Reason.OFFLINE) sawOffline = true
                }
            }
        }

        if (okPages.isEmpty()) {
            val reason = if (sawOffline) {
                GroundingResult.Reason.OFFLINE
            } else {
                GroundingResult.Reason.FETCH_FAILED
            }
            MultiUrlResult.AllFailed(reason)
        } else {
            MultiUrlResult.Fused(
                block = GroundingPrompt.buildFusedBlock(okPages),
                okUrls = okPages.map { (url, _) -> url },
                skippedUrls = skipped,
            )
        }
    }

    companion object {
        /**
         * Single source of truth for the FETCH-01 cap (2–5 range; 6th+
         * URLs are ignored deterministically). [UrlDetector.allUrls]
         * defaults its [max] to this — never duplicate the literal.
         */
        const val MAX_URLS = 5
    }
}
