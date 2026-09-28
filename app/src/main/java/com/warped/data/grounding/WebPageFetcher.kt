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
 * opening a socket; otherwise one fetch with an 8s connect / 10s read / 20s
 * call budget, max 3 manual redirects, 64KB body cap streamed in 8KB chunks
 * (never an unbounded whole-body read), text/html + text/plain only, browser User-Agent.
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
        const val MAX_BODY_BYTES = 65536
        private const val CHUNK_BYTES = 8192L
        private const val MAX_REDIRECTS = 3
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
    }

    private val client: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
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
                    .header("Accept", "text/html, text/plain")
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
                        // text/html and text/plain bodies are grounded.
                        val contentType = response.header("Content-Type")?.lowercase().orEmpty()
                        if (contentType.isNotEmpty() &&
                            !contentType.contains("text/html") &&
                            !contentType.contains("text/plain")
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
                        val extracted = HtmlToTextExtractor.extract(raw, currentUrl, safeBudget)
                        if (extracted.isBlank()) {
                            return@withContext GroundingResult.ModelOnly(GroundingResult.Reason.FETCH_FAILED)
                        }
                        // A 64KB-cap abort still grounds: partial content is
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
