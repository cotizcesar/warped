package com.warped.data.grounding

import com.warped.data.local.security.ApiKeyStore
import com.warped.data.remote.network.AuthInterceptor
import com.warped.domain.model.GroundedSource
import com.warped.domain.model.GroundedSourceStatus
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
 * DDG-default search producer (parse-only HTML client + DDG-primary /
 * Tavily-fallback executor).
 *
 * Policy (locked): DuckDuckGo's HTML endpoint
 * `https://html.duckduckgo.com/html/?q=...` is the DEFAULT `web_search`
 * producer and needs no API key. [TavilySearchRepository] is used ONLY when
 * (a) DDG returns zero usable results or the fetch throws, AND (b) a Tavily
 * key is stored — the delegate outcome passes through verbatim (including
 * `InvalidKey` / `UsageLimit` / the `MissingKey` key-race edge). No key +
 * DDG-OK = silent success; no key + DDG-fail = FETCH_FAILED (no key nag).
 *
 * Image-turn exception (quick-task image-turn routing): when the caller
 * passes `includeImages = true` (image-intent turn) AND a Tavily key is
 * stored, the DDG leg is skipped entirely and Tavily runs direct with
 * `include_images=true` — the DDG HTML endpoint has no image API, so a
 * DDG-OK turn would otherwise starve the image grid. Unkeyed image-intent
 * turns keep the DDG-primary policy above (text grounding, empty grid).
 *
 * Producer shape mirrors [TavilySearchRepository.fuse] exactly: DDG title +
 * snippet pairs flow into [GroundingPrompt.buildFusedBlock] with the same
 * OK/OMITIDA semantics, snippets through [WebContextSanitizer], same
 * top-N (default 5, cap 10) and query-cap constants, and the SAME
 * [TavilySearchOutcome] sealed interface — no new outcome type, so every
 * caller (`ChatViewModel`, tool-loop executors, `LocalToolLoop`) compiles
 * and behaves identically downstream. `MissingKey` is nearly unreachable
 * now but stays for the key-race edge.
 *
 * Stripped-client policy (mirrors [WebPageFetcher]): the derived client
 * removes [AuthInterceptor] so endpoint keys can never leak to
 * duckduckgo.com, uses the same desktop Chrome User-Agent, and never logs
 * query or key contents (status-only logging).
 *
 * Honest brittleness note: DDG HTML scraping is inherently brittle — the
 * parser keys on the `a.result__a` / `.result__snippet` markup shape. A
 * DDG markup change yields zero usable results, which routes keyed users
 * to the Tavily fallback and unkeyed users to the fetch-failed path. This
 * is fail-safe by construction (never a crash, never a silent wrong
 * answer), but unkeyed search quality depends on DDG markup stability.
 *
 * Pure Kotlin apart from the injected client + collaborators — JVM-testable
 * by setting [htmlSupplier] (no socket opened when it is set).
 */
@Singleton
class DuckDuckGoSearchRepository @Inject constructor(
    baseClient: OkHttpClient,
    private val webPageFetcher: WebPageFetcher,
    private val apiKeyStore: ApiKeyStore,
    private val tavily: TavilySearchRepository,
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
        maxResults: Int = TavilySearchRepository.DEFAULT_MAX_RESULTS,
        contextSize: Int = 4096,
        /**
         * Quick-task (image-turn routing): pass-through to Tavily image
         * search. When a key is stored, image-intent turns skip the DDG leg
         * entirely and go STRAIGHT to Tavily with `include_images=true`
         * (the DDG HTML endpoint has no image API, so a DDG-OK turn would
         * fuse zero images and starve the grid). Unkeyed image-intent turns
         * fall through to the DDG leg below (text grounding, empty grid) —
         * the caller attaches the images-need-key notice.
         */
        includeImages: Boolean = false,
    ): TavilySearchOutcome = withContext(ioDispatcher) {
        val trimmedQuery = query.take(TavilySearchRepository.MAX_QUERY_CHARS)
        if (trimmedQuery.isBlank()) {
            return@withContext TavilySearchOutcome.ModelOnly(
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
            return@withContext TavilySearchOutcome.ModelOnly(
                MultiUrlResult.AllFailed(GroundingResult.Reason.OFFLINE),
            )
        }
        // Quick-task (image-turn routing): image-intent turns with a stored
        // key skip the DDG leg entirely and go STRAIGHT to Tavily with
        // include_images=true — the DDG HTML endpoint has no image API, so
        // a DDG-OK turn would fuse zero images and starve the grid (the
        // Tavily fallback below only fires when DDG yields nothing usable).
        // The key copy is zeroed after the presence check; the delegate
        // re-reads the key itself when it runs (the MissingKey key-race
        // edge is preserved). Unkeyed image-intent turns fall through to
        // the DDG leg (text grounding, empty grid) — the caller attaches
        // the images-need-key notice.
        if (includeImages) {
            val directKey = apiKeyStore.getTavilyKey()
            val hasDirectKey = directKey != null && directKey.isNotEmpty()
            directKey?.fill('0')
            if (hasDirectKey) {
                // Explicit args (no Kotlin defaults): keeps the call on the
                // instance method so MockK can stub it in JVM tests.
                return@withContext tavily.search(
                    query = trimmedQuery,
                    maxResults = maxResults,
                    contextSize = contextSize,
                    includeImages = true,
                )
            }
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
            if (outcome is TavilySearchOutcome.Grounded) {
                return@withContext outcome.copy(
                    fused = outcome.fused.copy(details = enricher.enrich(outcome.fused.details)),
                )
            }
            return@withContext outcome
        }
        // DDG yielded nothing usable: Tavily fallback ONLY when keyed.
        // The key copy is zeroed after the presence check; the delegate
        // re-reads the key itself when it runs.
        val keyChars = apiKeyStore.getTavilyKey()
        val hasKey = keyChars != null && keyChars.isNotEmpty()
        keyChars?.fill('0')
        if (!hasKey) {
            return@withContext TavilySearchOutcome.ModelOnly(
                MultiUrlResult.AllFailed(GroundingResult.Reason.FETCH_FAILED),
            )
        }
        // Explicit args (no Kotlin defaults): keeps the call on the
        // instance method so MockK can stub it in JVM tests.
        return@withContext tavily.search(
            query = trimmedQuery,
            maxResults = maxResults,
            contextSize = contextSize,
            includeImages = includeImages,
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
        val count = maxResults.coerceIn(1, TavilySearchRepository.MAX_RESULTS_CAP)
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
     * DDG mirror of `TavilySearchRepository.fuse`: top-N pairs map to one
     * numbered source each (text = title + newline + sanitized snippet,
     * truncated to the per-page budget); blank url/snippet items become
     * OMITIDA rows (never silent drops); all-blank collapses to
     * [MultiUrlResult.AllFailed] with FETCH_FAILED.
     */
    private fun fuse(
        results: List<DdgResult>,
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
