package com.warped.data.grounding

import com.warped.data.remote.network.AuthInterceptor
import com.warped.domain.model.GroundedSource
import com.warped.domain.model.GroundedSourceStatus
import com.warped.domain.model.GROUNDED_SNIPPET_MAX_CHARS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import timber.log.Timber
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DDG-only search producer (parse-only HTML client, no API key).
 *
 * Policy (locked, Phase 63): DuckDuckGo's HTML endpoint
 * `https://html.duckduckgo.com/html/?q=...` is the SINGLE `web_search`
 * producer and needs no API key. There is no fallback chain — when DDG
 * yields nothing usable the outcome is `ModelOnly(FETCH_FAILED)` via the
 * existing unkeyed path. Image-intent turns (`includeImages = true`) fuse
 * an empty images list with text grounding preserved (the DDG HTML
 * endpoint has no image API); the single fusion path is [fuse].
 *
 * Producer shape: DDG title + snippet pairs flow into
 * [GroundingPrompt.buildFusedBlock] with the same OK/OMITIDA semantics,
 * snippets through [WebContextSanitizer], same top-N (default 5, cap 10)
 * and query-cap constants, and the keyless [SearchOutcome] sealed
 * interface — every caller compiles and behaves identically downstream.
 *
 * Stripped-client policy (mirrors [WebPageFetcher]): the derived client
 * removes [AuthInterceptor] so endpoint keys can never leak to
 * duckduckgo.com, uses the same desktop Chrome User-Agent, and never logs
 * query contents (status-only logging).
 *
 * Honest brittleness note: DDG HTML scraping is inherently brittle — the
 * parser keys on the `a.result__a` / `.result__snippet` markup shape. A
 * DDG markup change yields zero usable results, which routes to the
 * fetch-failed path. This is fail-safe by construction (never a crash,
 * never a silent wrong answer), but search quality depends on DDG markup
 * stability.
 *
 * Pure Kotlin apart from the injected client + collaborators — JVM-testable
 * by setting [htmlSupplier] (no socket opened when it is set).
 */
/**
 * Phase 63: keyless DDG-only search outcome. `Grounded` carries the fused
 * `Source [N]` block; `ModelOnly` carries the failure (OFFLINE or
 * FETCH_FAILED) for the model-only path. No key states exist — DDG needs
 * no API key.
 */
sealed interface SearchOutcome {

    /** Search fused into the identical `Source [N]` block as URL grounding. */
    data class Grounded(val fused: MultiUrlResult.Fused) : SearchOutcome

    /** No usable results (or transport failure) — model-only path. */
    data class ModelOnly(val failed: MultiUrlResult.AllFailed) : SearchOutcome
}

@Singleton
class DuckDuckGoSearchRepository @Inject constructor(
    baseClient: OkHttpClient,
    private val webPageFetcher: WebPageFetcher,
    private val enricher: SearchOgEnricher,
) {

    /** Overridable for deterministic JVM tests; production stays on IO. */
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    /**
     * Test seam: when non-null, supplies raw DDG HTML for the URL-encoded
     * query instead of opening a socket (may throw to simulate transport
     * failure; a [CancellationException] propagates). Null in production.
     */
    internal var htmlSupplier: (suspend (encodedQuery: String) -> String)? = null

    private val client: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .apply {
            // Same stripped-client policy as WebPageFetcher: the fetch
            // request carries no endpoint tag, but strip AuthInterceptor
            // anyway so endpoint Authorization headers can never leak to
            // duckduckgo.com.
            interceptors().removeAll { it is AuthInterceptor }
        }
        .build()

    suspend fun search(
        query: String,
        maxResults: Int = DEFAULT_MAX_RESULTS,
        contextSize: Int = 4096,
        /**
         * Image-intent turns pass true; the DDG HTML endpoint has no image
         * API so the fused images list stays empty (text grounding
         * preserved, grid empty). Kept as a parameter so existing call
         * sites compile unchanged.
         */
        includeImages: Boolean = false,
    ): SearchOutcome = withContext(ioDispatcher) {
        val trimmedQuery = query.take(MAX_QUERY_CHARS)
        if (trimmedQuery.isBlank()) {
            return@withContext SearchOutcome.ModelOnly(
                MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
            )
        }
        // Offline short-circuits to OFFLINE with no socket (same gate as
        // WebPageFetcher.fetch; the callers re-check upstream too).
        val online = try {
            webPageFetcher.hasValidatedInternet()
        } catch (e: Exception) {
            Timber.w(e, "DDG: connectivity check failed, treating as offline")
            false
        }
        if (!online) {
            return@withContext SearchOutcome.ModelOnly(
                MultiUrlResult.AllFailed(GroundingResult.Reason.OFFLINE),
            )
        }
        val pairs: List<DdgResult>? = try {
            val encoded = URLEncoder.encode(trimmedQuery, StandardCharsets.UTF_8.toString())
            val html = htmlSupplier?.invoke(encoded) ?: fetchHtml(encoded)
            parseResults(html, maxResults)
        } catch (e: CancellationException) {
            // Cooperative cancel (Stop / new turn) — rethrow, never model-only.
            throw e
        } catch (e: Exception) {
            // Never log the query contents — status only.
            Timber.e(e, "DDG: search fetch failed")
            null
        }
        if (!pairs.isNullOrEmpty()) {
            val outcome = fuse(pairs, contextSize)
            if (outcome is SearchOutcome.Grounded) {
                return@withContext outcome.copy(
                    fused = outcome.fused.copy(details = enricher.enrich(outcome.fused.details)),
                )
            }
            return@withContext outcome
        }
        // DDG yielded nothing usable: DDG-only policy — model-only with
        // FETCH_FAILED (no fallback chain, no key probe).
        return@withContext SearchOutcome.ModelOnly(
            MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
        )
    }

    /**
     * Plain GET on the documented DDG HTML endpoint. Parse-only downstream —
     * never a form POST, never `Jsoup.connect()`.
     */
    private fun fetchHtml(encodedQuery: String): String {
        val request = Request.Builder()
            .url("$HTML_ENDPOINT?q=$encodedQuery")
            .header("User-Agent", WebPageFetcher.USER_AGENT)
            .header("Accept", "text/html")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("DDG search failed with HTTP ${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    }

    /**
     * DDG HTML → candidate results (pure function, no I/O — unit-testable).
     *
     * Result links are `//duckduckgo.com/l/?uddg=<urlencoded real URL>`
     * wrappers ([resolveResultUrl] unwraps + http(s)-gates them; bare
     * http(s) links pass through; anything else drops). The snippet is the
     * nearest `.result__snippet` node inside the same `.result` container —
     * missing snippet yields a blank snippet (an OMITIDA row downstream),
     * never a crash on malformed HTML. `Jsoup.parse` only (never connect).
     */
    internal fun parseResults(html: String, maxResults: Int): List<DdgResult> {
        if (html.isBlank()) return emptyList()
        val count = maxResults.coerceIn(1, MAX_RESULTS_CAP)
        val doc = Jsoup.parse(html)
        val out = mutableListOf<DdgResult>()
        for (anchor in doc.select(RESULT_ANCHOR_SELECTOR)) {
            if (out.size >= count) break
            val url = resolveResultUrl(anchor.attr("href")) ?: continue
            val title = anchor.text().trim()
            val snippet = anchor.closest(RESULT_CONTAINER_SELECTOR)
                ?.selectFirst(RESULT_SNIPPET_SELECTOR)
                ?.text()?.trim().orEmpty()
            out.add(DdgResult(title = title, url = url, snippet = snippet))
        }
        return out
    }

    /**
     * Unwrap a DDG outlink to its real target, http(s)-gated.
     *
     * - `//duckduckgo.com/l/?uddg=<real>` (or the `https:` / `/l/` spelling
     *   variants) → URL-decode `uddg`; accept only `http`/`https` targets.
     * - Bare absolute `http(s)` links pass through (tolerates DDG markup
     *   drift toward direct links).
     * - Anything else (missing/empty `uddg`, non-http schemes like
     *   `javascript:`/`data:`, relative paths, the bare wrapper) → null.
     */
    internal fun resolveResultUrl(href: String): String? {
        val raw = href.trim()
        if (raw.isEmpty()) return null
        val candidate = if (isDdgWrapper(raw)) {
            extractUddg(raw) ?: return null
        } else {
            raw
        }
        if (!candidate.startsWith("http://", ignoreCase = true) &&
            !candidate.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }
        return candidate
    }

    private fun isDdgWrapper(href: String): Boolean {
        val noScheme = href
            .removePrefix("https:")
            .removePrefix("http:")
        return noScheme.startsWith("//duckduckgo.com/l/", ignoreCase = true) ||
            noScheme.startsWith("duckduckgo.com/l/", ignoreCase = true) ||
            noScheme.startsWith("/l/", ignoreCase = true)
    }

    private fun extractUddg(href: String): String? {
        val query = href.substringAfter('?', "")
        if (query.isEmpty()) return null
        // Jsoup's attr() already unescapes &amp;, so split on raw '&'.
        for (param in query.split('&')) {
            if (!param.substringBefore('=').equals("uddg", ignoreCase = true)) continue
            val value = param.substringAfter('=', "")
            if (value.isEmpty()) return null
            return try {
                URLDecoder.decode(value, StandardCharsets.UTF_8.toString())
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    /**
     * DDG results → fused block: top-N pairs map to one
     * numbered source each (text = title + newline + sanitized snippet,
     * truncated to the per-page budget); blank url/snippet items become
     * OMITIDA rows (never silent drops); all-blank collapses to
     * [MultiUrlResult.AllFailed] with FETCH_FAILED.
     */
    private fun fuse(
        results: List<DdgResult>,
        contextSize: Int,
    ): SearchOutcome {
        if (results.isEmpty()) {
            return SearchOutcome.ModelOnly(
                MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
            )
        }
        val perPage = GroundingBudget.perPageBudget(contextSize, results.size)
        val okPairs = mutableListOf<Pair<String, String>>()
        val skipped = mutableListOf<String>()
        val details = results.map { result ->
            val url = result.url.trim()
            val content = result.snippet.trim()
            if (url.isBlank() || content.isBlank()) {
                val label = url.ifBlank {
                    result.title.trim().ifBlank { UNKNOWN_SOURCE }
                }
                if (url.isNotBlank()) skipped.add(url)
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
                        ogTitle = result.title.trim().take(OpenGraphParser.MAX_TITLE_CHARS)
                            .takeIf { it.isNotEmpty() },
                        // Quick-task (card-snippet): sanitized excerpt for
                        // the card description fallback (same sanitizer as
                        // the fused text above; render caps at 160).
                        snippet = WebContextSanitizer.sanitize(result.snippet)
                            .trim()
                            .take(GROUNDED_SNIPPET_MAX_CHARS)
                            .takeIf { it.isNotEmpty() },
                    )
                }
            }
        }

        if (okPairs.isEmpty()) {
            return SearchOutcome.ModelOnly(
                MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
            )
        }
        return SearchOutcome.Grounded(
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
        /** Default result count for top-N searches. */
        const val DEFAULT_MAX_RESULTS = 5

        /** Hard cap per CONTEXT (top-N, max 10). */
        const val MAX_RESULTS_CAP = 10

        /** Client-side query cap (pass-through, no rewriting). */
        const val MAX_QUERY_CHARS = 500

        /** Documented DDG HTML endpoint (GET only — parse-only, never form-POST). */
        internal const val HTML_ENDPOINT = "https://html.duckduckgo.com/html/"

        /** Result anchors in the DDG HTML markup (brittle — see class KDoc). */
        private const val RESULT_ANCHOR_SELECTOR = "a.result__a"

        /** Per-result container holding the anchor + its snippet node. */
        private const val RESULT_CONTAINER_SELECTOR = "div.result"

        /** Snippet node inside a result container (may be absent). */
        private const val RESULT_SNIPPET_SELECTOR = ".result__snippet"

        private const val UNKNOWN_SOURCE = "(unknown source)"
    }
}

/** One parsed DDG result: title + unwrapped http(s) URL + snippet (may be blank). */
internal data class DdgResult(
    val title: String,
    val url: String,
    val snippet: String,
)
