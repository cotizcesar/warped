package com.warped.data.grounding

import com.warped.data.local.security.ApiKeyStore
import com.warped.data.remote.api.TavilyApi
import com.warped.data.remote.dto.TavilySearchRequest
import com.warped.data.remote.dto.TavilySearchResult
import com.warped.domain.model.GroundedSource
import com.warped.domain.model.GroundedSourceStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 55 (TAV-02): search-to-fused-block producer for Tavily.
 *
 * A *producer* of `(url, text)` pairs into the frozen grounding
 * pipeline — every downstream consumer (fusion, persist, Fuentes,
 * preview, citations) stays untouched:
 *
 * - Top-N results (default 5, max 10) map to one numbered source each:
 *   text = title + newline + sanitized snippet, truncated to
 *   [GroundingBudget.perPageBudget].
 * - Every snippet runs through [WebContextSanitizer] (T-55-03: search
 *   snippets are untrusted input crossing into prompt context, same
 *   trust boundary as fetched pages); `include_answer` stays false and
 *   `include_raw_content` stays unset.
 * - Blank content/URL items become OMITIDA rows in [MultiUrlResult.Fused.details]
 *   (never silent drops); all-blank collapses to
 *   [MultiUrlResult.AllFailed] with FETCH_FAILED.
 * - Query passes through verbatim (no rewriting — Phase 56 owns that),
 *   length-capped client-side to avoid 400s.
 * - Auth is a per-call `Bearer` header from the `tavily_api_key`
 *   Keystore alias; key bytes are zeroed after use like
 *   `AuthInterceptor`, and the key is never logged (T-55-01/T-55-02).
 *
 * Pure Kotlin apart from the injected API + key store — JVM-testable
 * with a MockK `TavilyApi` fake (no network, no key).
 */
sealed interface TavilySearchOutcome {

    /** Search fused into the identical `Source [N]` block as URL grounding. */
    data class Grounded(val fused: MultiUrlResult.Fused) : TavilySearchOutcome

    /** No usable results (or transport failure) — model-only path. */
    data class ModelOnly(val failed: MultiUrlResult.AllFailed) : TavilySearchOutcome

    /** No key stored — caller renders the actionable missing-key message. */
    data object MissingKey : TavilySearchOutcome

    /** HTTP 401 — caller renders the distinct invalid-key message. */
    data object InvalidKey : TavilySearchOutcome

    /** HTTP 429 — caller renders the limit-exceeded message. */
    data object UsageLimit : TavilySearchOutcome
}

@Singleton
class TavilySearchRepository @Inject constructor(
    private val api: TavilyApi,
    private val apiKeyStore: ApiKeyStore,
) {

    /** Overridable for deterministic JVM tests; production stays on IO. */
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    suspend fun search(
        query: String,
        maxResults: Int = DEFAULT_MAX_RESULTS,
        contextSize: Int = 4096,
    ): TavilySearchOutcome = withContext(ioDispatcher) {
        val keyChars = apiKeyStore.getTavilyKey()
        if (keyChars == null || keyChars.isEmpty()) {
            keyChars?.fill('0')
            return@withContext TavilySearchOutcome.MissingKey
        }
        val key = keyChars.concatToString()
        keyChars.fill('0')

        val trimmedQuery = query.take(MAX_QUERY_CHARS)
        if (trimmedQuery.isBlank()) {
            return@withContext TavilySearchOutcome.ModelOnly(
                MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
            )
        }
        val count = maxResults.coerceIn(1, MAX_RESULTS_CAP)
        try {
            val request = TavilySearchRequest(
                query = trimmedQuery,
                max_results = count,
            )
            val response = api.search("Bearer $key", request)
            when (response.code()) {
                401 -> TavilySearchOutcome.InvalidKey
                429 -> TavilySearchOutcome.UsageLimit
                else -> {
                    if (!response.isSuccessful) {
                        Timber.e("Tavily: search failed with HTTP %d", response.code())
                        return@withContext TavilySearchOutcome.ModelOnly(
                            MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
                        )
                    }
                    val body = response.body()
                    if (body == null) {
                        return@withContext TavilySearchOutcome.ModelOnly(
                            MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
                        )
                    }
                    fuse(body.results.take(count), contextSize)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Never log the key or query contents — status only.
            Timber.e(e, "Tavily: search call failed")
            TavilySearchOutcome.ModelOnly(
                MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
            )
        }
    }

    private fun fuse(
        results: List<TavilySearchResult>,
        contextSize: Int,
    ): TavilySearchOutcome {
        if (results.isEmpty()) {
            return TavilySearchOutcome.ModelOnly(
                MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
            )
        }
        val perPage = GroundingBudget.perPageBudget(contextSize, results.size)
        val okPairs = mutableListOf<Pair<String, String>>()
        val skipped = mutableListOf<String>()
        val details = results.map { result ->
            val url = result.url.trim()
            val content = result.content.trim()
            if (url.isBlank() || content.isBlank()) {
                val label = url.ifBlank {
                    result.title.trim().ifBlank { UNKNOWN_SOURCE }
                }
                skipped.add(label)
                GroundedSource(
                    url = label,
                    extractedText = null,
                    status = GroundedSourceStatus.OMITIDA,
                )
            } else {
                val text = WebContextSanitizer
                    .sanitize("${result.title.trim()}\n$content")
                    .trim()
                    .take(perPage)
                if (text.isBlank()) {
                    skipped.add(url)
                    GroundedSource(
                        url = url,
                        extractedText = null,
                        status = GroundedSourceStatus.OMITIDA,
                    )
                } else {
                    okPairs.add(url to text)
                    GroundedSource(
                        url = url,
                        extractedText = text,
                        status = GroundedSourceStatus.OK,
                    )
                }
            }
        }

        if (okPairs.isEmpty()) {
            return TavilySearchOutcome.ModelOnly(
                MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
            )
        }
        return TavilySearchOutcome.Grounded(
            MultiUrlResult.Fused(
                block = GroundingPrompt.buildFusedBlock(okPairs),
                okUrls = okPairs.map { (url, _) -> url },
                skippedUrls = skipped,
                pageTexts = okPairs.toMap(),
                details = details,
            ),
        )
    }

    companion object {
        /** Default result count (1 Tavily credit per search at basic depth). */
        const val DEFAULT_MAX_RESULTS = 5

        /** Hard cap per CONTEXT (top-N, max 10). */
        const val MAX_RESULTS_CAP = 10

        /** Client-side query cap (pass-through, no rewriting — Phase 56 owns that). */
        const val MAX_QUERY_CHARS = 500

        private const val UNKNOWN_SOURCE = "(unknown source)"
    }
}
