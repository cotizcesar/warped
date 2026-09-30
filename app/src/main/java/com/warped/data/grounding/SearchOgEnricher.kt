package com.warped.data.grounding

import com.warped.data.remote.network.AuthInterceptor
import com.warped.domain.model.GroundedSource
import com.warped.domain.model.GroundedSourceStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Quick-task (search OG enrichment): best-effort parallel `og:title` /
 * `og:image` / `og:description` scrape for fused search-result rows.
 *
 * Search-grounded cards show favicon + host only because DDG/Tavily `fuse()`
 * never captured page metadata. This enricher runs once per search turn
 * inside the two repository `search()` methods (right after their own
 * `fuse()` returns `Grounded`), covering the `ChatViewModel` pre-search
 * branch and tool-loop search callers for free. Fetched-page OG
 * (`WebPageFetcher` HTML branch) and `MultiUrlFetcher` are untouched.
 *
 * Light-path by design (NOT a `WebPageFetcher.fetch` reuse — that does a
 * 256 KB full-body read + markdown extraction + sanitization + prompt-block
 * build per URL on ~10-30 s budgets, all wasted for head-meta only): a
 * capped head-fetch (OG tags live in `<head>`, first bytes) + `Jsoup.parse`
 * via [OpenGraphParser.parse] (never `Jsoup.connect`).
 *
 * Merge precedence (locked): scraped `og:title` wins when enrichment
 * succeeds (page-authoritative — `OpenGraphParser.parse` never returns
 * blank, falling back `og:title → doc.title → host`); the threaded search
 * title from `fuse()` (Task 1) survives when enrichment fails or is
 * skipped; host remains the render fallback via the unchanged
 * `ogDisplayTitle`. `ogDescription`/`ogImageUrl` set only when scraped
 * non-null. No schema change — the `og_*` columns round-trip already.
 *
 * Budgets (locked — extra latency on EVERY search turn): 3 s total
 * (`withTimeout` around the fan-out), 2 s connect / 2 s read per-call,
 * 64 KB body cap streamed in 8 KB chunks, max 5 OK URLs per turn, max 1
 * manual redirect with `followRedirects(false)`, http(s) gate pre- AND
 * post-redirect. Failures keep the original row (never fail the turn).
 * `CancellationException` rethrows (Stop / new-turn cancel propagates);
 * only the outer `TimeoutCancellationException` returns originals.
 *
 * Pure Kotlin apart from the injected client — JVM-testable via
 * [headSupplier] (production leaves it null for the real fetch; tests set
 * it so no socket ever opens).
 */
@Singleton
class SearchOgEnricher @Inject constructor(
    baseClient: OkHttpClient,
) {

    /** Overridable for deterministic JVM tests; production stays on IO. */
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    /**
     * Test seam: when non-null, supplies `(rawHtml, contentType)` per URL
     * instead of opening a socket; a null return skips the row (keeps the
     * original). May throw to simulate transport failure; a
     * [CancellationException] propagates. Null in production.
     */
    internal var headSupplier: (suspend (url: String) -> Pair<String, String?>?)? = null

    /** Test seam for the total fan-out bound; production uses the locked 3 s. */
    internal var totalTimeoutMs: Long = TOTAL_TIMEOUT_MS

    private val client: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(PER_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(PER_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .followRedirects(false)
        .apply {
            // Same stripped-client policy as WebPageFetcher / DDG: endpoint
            // Authorization headers can never leak to arbitrary page hosts.
            interceptors().removeAll { it is AuthInterceptor }
        }
        .build()

    /**
     * Enrich up to [MAX_ENRICH] OK-status http(s) rows, order-preserving.
     * Non-OK / non-http(s) rows pass through untouched; any failure keeps
     * the original row and the turn still grounds.
     */
    suspend fun enrich(sources: List<GroundedSource>): List<GroundedSource> {
        val targets = sources.mapIndexedNotNull { index, source ->
            if (source.status == GroundedSourceStatus.OK && isHttpUrl(source.url)) {
                index to source
            } else {
                null
            }
        }.take(MAX_ENRICH)
        if (targets.isEmpty()) return sources
        val enriched: Map<Int, GroundedSource> = try {
            withTimeout(totalTimeoutMs) {
                coroutineScope {
                    targets.map { (index, source) ->
                        index to async(ioDispatcher) { fetchHead(source) }
                    }.map { (index, deferred) -> index to deferred.await() }
                        .toMap()
                }
            }
        } catch (e: TimeoutCancellationException) {
            // Total budget exceeded — status only, never URL contents.
            Timber.w("SearchOgEnricher: total budget exceeded for ${targets.size} urls, keeping originals")
            return sources
        }
        Timber.d("SearchOgEnricher: enriched ${enriched.size} of ${targets.size} urls")
        return sources.mapIndexed { index, source -> enriched[index] ?: source }
    }

    /**
     * One head-fetch + parse + merge. Returns the original row on any
     * skipped/failed scrape; rethrows [CancellationException] so Stop /
     * new-turn cancel propagates out of the fan-out.
     */
    private suspend fun fetchHead(original: GroundedSource): GroundedSource {
        try {
            val (raw, contentType) = headSupplier?.invoke(original.url)
                ?: fetchHeadHttp(original.url)
                ?: return original
            // Content-type gate: missing means HTML (same as WebPageFetcher);
            // a present non-`text/html` value skips the row.
            if (contentType != null && !contentType.contains("text/html", ignoreCase = true)) {
                return original
            }
            val scraped = OpenGraphParser.parse(raw, original.url)
            return original.copy(
                // Page-authoritative win on success; a blank/failed parse
                // (all-null OpenGraphData) keeps the threaded search title.
                ogTitle = scraped.ogTitle ?: original.ogTitle,
                ogDescription = scraped.ogDescription ?: original.ogDescription,
                ogImageUrl = scraped.ogImageUrl ?: original.ogImageUrl,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return original
        }
    }

    /**
     * Blocking capped head-fetch: GET with the desktop UA, at most one
     * manual redirect (http(s) re-gated), then stream at most
     * [MAX_HEAD_BYTES] in 8 KB chunks (never an unbounded read — mirrors
     * `WebPageFetcher`). Returns `(rawHtml, contentType)` or null when the
     * row should keep its original. Runs on [ioDispatcher] via the caller.
     */
    private fun fetchHeadHttp(startUrl: String): Pair<String, String?>? {
        if (!isHttpUrl(startUrl)) return null
        var current = startUrl
        var attempt = 0
        while (attempt < MAX_REQUESTS) {
            val request = Request.Builder()
                .url(current)
                .header("User-Agent", WebPageFetcher.USER_AGENT)
                .header("Accept", "text/html")
                .build()
            val response = try {
                client.newCall(request).execute()
            } catch (_: Exception) {
                return null
            }
            try {
                if (response.code in 300..399 && attempt == 0) {
                    val location = response.header("Location")?.trim().orEmpty()
                    val resolved = if (location.isEmpty()) {
                        null
                    } else {
                        try {
                            java.net.URI(current).resolve(location).toString()
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (resolved == null || !isHttpUrl(resolved)) return null
                    current = resolved
                    attempt++
                    continue
                }
                if (!response.isSuccessful) return null
                val contentType = response.header("Content-Type")
                if (contentType != null && !contentType.contains("text/html", ignoreCase = true)) {
                    return null
                }
                val body = response.body ?: return null
                val sink = Buffer()
                var remaining = MAX_HEAD_BYTES.toLong()
                val source = body.source()
                while (remaining > 0) {
                    val read = source.read(sink, min(CHUNK_BYTES, remaining))
                    if (read == -1L) break
                    remaining -= read
                }
                return sink.readUtf8() to contentType
            } finally {
                response.close()
            }
        }
        return null
    }

    private fun isHttpUrl(url: String): Boolean {
        val value = url.trim()
        return value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true)
    }

    companion object {
        /** Locked total fan-out bound (extra latency on every search turn). */
        internal const val TOTAL_TIMEOUT_MS = 3000L

        /** Locked per-call socket budgets. */
        internal const val PER_CALL_TIMEOUT_MS = 2000L

        /** Locked head-fetch body cap (OG tags live in `<head>`, first bytes). */
        internal const val MAX_HEAD_BYTES = 65536

        internal const val CHUNK_BYTES = 8192L

        /** Locked max OK URLs enriched per turn. */
        internal const val MAX_ENRICH = 5

        /** Initial request + at most 1 manual redirect. */
        internal const val MAX_REQUESTS = 2
    }
}
