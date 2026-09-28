package com.warped.data.grounding

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.warped.data.remote.network.AuthInterceptor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Phase 50 (WEB-01, WEB-02): bounded, cancelable single-page fetcher.
 *
 * Policy: offline short-circuits to [GroundingResult.ModelOnly] without
 * opening a socket; otherwise one fetch with a 10s connect / 15s read / 30s
 * call budget, max 3 manual redirects, 256KB body cap streamed in 8KB chunks
 * (never an unbounded whole-body read), text/html + text/plain +
 * text/markdown only, desktop Chrome User-Agent with markdown-first Accept
 * negotiation (mirrors OpenCode webfetch). HTML grounds as structured
 * markdown via [HtmlToMarkdown] with a flat-text fallback
 * ([HtmlToTextExtractor]) when markdown converts to blank. The endpoint
 * [AuthInterceptor] is stripped from the derived client so API
 * keys (tag-gated per-endpoint) can never leak to arbitrary fetched hosts.
 * Runs on Dispatchers.IO; cancel via [cancel] (Stop button / new-turn
 * pre-cancel). Fetch failures inject zero bytes — the turn goes model-only.
 * The endpoint [AuthInterceptor] is stripped from the derived client so API
 * keys (tag-gated per-endpoint) can never leak to arbitrary fetched hosts.
 * Runs on Dispatchers.IO; cancel via [cancel] (Stop button / new-turn
 * pre-cancel). Fetch failures inject zero bytes — the turn goes model-only.
 *
 * Phase 52 (FETCH-01, T-52-05): the in-flight set is fan-out-safe. The v2.2
 * single `activeCall` field raced under parallel fetch (a second call
 * overwrote the first, so Stop leaked the earlier socket); the concurrent
 * set below tracks every in-flight call and [cancel] aborts all of them.
 * The [budget] parameter threads the per-page slice of the global grounding
 * budget into extraction (default keeps the frozen single-page behavior).
 */
@Singleton
class WebPageFetcher @Inject constructor(
    baseClient: OkHttpClient,
    @ApplicationContext private val context: Context,
) {

    companion object {
        const val MAX_BODY_BYTES = 262144
        private const val CHUNK_BYTES = 8192L
        private const val MAX_REDIRECTS = 3
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"
    }

    private val client: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .apply {
            // AuthInterceptor is tag-gated per endpoint, but the fetch
            // request carries no endpoint tag — strip it anyway so endpoint
            // Authorization headers can never leak to arbitrary hosts.
            interceptors().removeAll { it is AuthInterceptor }
        }
        .build()

    /**
     * Phase 52 (T-52-05): every in-flight call, tracked concurrently.
     * Added right after `newCall()`, removed in `finally` — [cancel]
     * iterates a snapshot so Stop / new-turn pre-cancel aborts ALL
     * in-flight sockets under fan-out (no post-Stop grounding).
     */
    private val activeCalls = ConcurrentHashMap.newKeySet<Call>()

    fun cancel() {
        activeCalls.toList().forEach { call ->
            try {
                call.cancel()
            } catch (_: Exception) {
                // Best-effort: one dead call must not shield the rest.
            }
        }
    }

    suspend fun fetch(url: String, budget: Int = HtmlToTextExtractor.MAX_CHARS): GroundingResult = withContext(Dispatchers.IO) {
        if (!hasValidatedInternet()) {
            return@withContext GroundingResult.ModelOnly(GroundingResult.Reason.OFFLINE)
        }
        // Floor the per-page extraction budget at the extractor's
        // crash-preventing minimum so a degenerate caller value can never
        // reach truncation arithmetic as a negative (see HtmlToTextExtractor).
        // Production callers pass perPageBudget (>= MIN_PER_PAGE) — this only
        // guards direct callers of this overload.
        val safeBudget = budget.coerceAtLeast(HtmlToTextExtractor.TRUNCATION_MARKER.length + 1)
        try {
            var currentUrl = url
            var hops = 0
            while (true) {
                val request = Request.Builder()
                    .url(currentUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/markdown;q=1.0, text/plain;q=0.8, text/html;q=0.7, */*;q=0.1")
                    .build()
                val call = client.newCall(request)
                activeCalls.add(call)
                try {
                    call.execute().use { response ->
                        if (response.isRedirect) {
                            if (hops >= MAX_REDIRECTS) {
                                return@withContext GroundingResult.ModelOnly(GroundingResult.Reason.FETCH_FAILED)
                            }
                            val location = response.header("Location")
                            val resolved = location?.let { request.url.resolve(it) }
                            if (resolved == null || (resolved.scheme != "http" && resolved.scheme != "https")) {
                                return@withContext GroundingResult.ModelOnly(GroundingResult.Reason.FETCH_FAILED)
                            }
                            currentUrl = resolved.toString()
                            hops++
                            return@use
                        }
                        if (!response.isSuccessful) {
                            return@withContext GroundingResult.ModelOnly(GroundingResult.Reason.FETCH_FAILED)
                        }
                        // Missing content-type is treated as html; only
                        // text/html, text/plain, and text/markdown bodies ground.
                        val contentType = response.header("Content-Type")?.lowercase().orEmpty()
                        val isMarkdown = contentType.contains("text/markdown")
                        if (contentType.isNotEmpty() &&
                            !contentType.contains("text/html") &&
                            !contentType.contains("text/plain") &&
                            !isMarkdown
                        ) {
                            return@withContext GroundingResult.ModelOnly(GroundingResult.Reason.FETCH_FAILED)
                        }
                        val body = response.body ?: return@withContext GroundingResult.ModelOnly(
                            GroundingResult.Reason.FETCH_FAILED
                        )
                        val sink = Buffer()
                        var remaining = MAX_BODY_BYTES.toLong()
                        val source = body.source()
                        while (remaining > 0) {
                            val read = source.read(sink, min(CHUNK_BYTES, remaining))
                            if (read == -1L) break
                            remaining -= read
                        }
                        val raw = sink.readUtf8()
                        val extracted = if (isMarkdown) {
                            // text/markdown direct passthrough (no conversion).
                            raw.trim().takeIf { it.isNotEmpty() }
                                ?.let { truncatePassthrough(it, safeBudget) }
                                .orEmpty()
                        } else if (contentType.contains("text/plain")) {
                            HtmlToTextExtractor.extract(raw, currentUrl, safeBudget)
                        } else {
                            val markdown = HtmlToMarkdown.convert(raw, currentUrl, safeBudget)
                            if (markdown.isNotBlank()) markdown
                            else HtmlToTextExtractor.extract(raw, currentUrl, safeBudget)
                        }
                        if (extracted.isBlank()) {
                            return@withContext GroundingResult.ModelOnly(GroundingResult.Reason.FETCH_FAILED)
                        }
                        // A 256KB-cap abort still grounds: partial content is
                        // usable and the extractor marks truncation.
                        val sanitized = WebContextSanitizer.sanitize(extracted)
                        val block = GroundingPrompt.buildBlock(currentUrl, sanitized)
                        return@withContext GroundingResult.Grounded(block, currentUrl, sanitized)
                    }
                } finally {
                    activeCalls.remove(call)
                }
            }
        } catch (e: CancellationException) {
            // Cooperative cancel (Stop / new turn) — rethrow, never model-only.
            throw e
        } catch (e: IOException) {
            Timber.w(e, "WebPageFetcher: fetch failed, model-only path")
            return@withContext GroundingResult.ModelOnly(GroundingResult.Reason.FETCH_FAILED)
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "WebPageFetcher: bad URL, model-only path")
            return@withContext GroundingResult.ModelOnly(GroundingResult.Reason.FETCH_FAILED)
        }
        @Suppress("UNREACHABLE_CODE")
        GroundingResult.ModelOnly(GroundingResult.Reason.FETCH_FAILED)
    }

    /**
     * Line-boundary truncation for text/markdown passthrough bodies,
     * mirroring the extractor marker contract.
     */
    private fun truncatePassthrough(text: String, budget: Int): String {
        if (text.length <= budget) return text
        val marker = HtmlToTextExtractor.TRUNCATION_MARKER
        val cut = budget - marker.length - 1
        val head = if (cut <= 0) text.take(budget) else {
            val nl = text.lastIndexOf('\n', cut)
            if (nl > 0) text.take(nl) else text.take(cut)
        }
        return "$head\n$marker"
    }

    internal fun hasValidatedInternet(): Boolean {
        return try {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            Timber.w(e, "WebPageFetcher: connectivity check failed")
            false
        }
    }
}
