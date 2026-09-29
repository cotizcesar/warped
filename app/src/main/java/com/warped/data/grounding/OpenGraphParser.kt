package com.warped.data.grounding

import org.jsoup.Jsoup

/**
 * Phase 58 (OG-01): pure OpenGraph scraper over already-fetched HTML.
 *
 * Parse-only over the in-memory capped raw string — never opens a socket
 * (parse path only, no network connect call anywhere in grounding sources). Called from
 * [WebPageFetcher.fetch] on the HTML branch only; plain/markdown bodies
 * yield `openGraph = null` at the call site.
 *
 * All fields nullable: null means "no OG captured" (Tavily rows, plain-text
 * sources, pre-58 rows). Images are accepted only when they resolve to
 * http(s) (T-58-01); they are NEVER fetched at scrape time — Coil loads
 * them on demand in plan 58-02.
 */
data class OpenGraphData(
    val ogTitle: String? = null,
    val ogDescription: String? = null,
    val ogImageUrl: String? = null,
)

object OpenGraphParser {

    const val MAX_TITLE_CHARS = 300
    const val MAX_DESCRIPTION_CHARS = 500

    fun parse(html: String, baseUrl: String): OpenGraphData {
        if (html.isBlank()) return OpenGraphData()
        val doc = try {
            Jsoup.parse(html, baseUrl)
        } catch (_: Exception) {
            return OpenGraphData()
        }

        val title = doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim().orEmpty()
            .ifEmpty { doc.title().trim() }
            .ifEmpty { hostOf(baseUrl) }

        val description = doc.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: doc.selectFirst("meta[name=description]")?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }

        val rawImage = doc.selectFirst("meta[property=og:image]")?.attr("content")?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: doc.selectFirst("meta[property=og:image:secure_url]")?.attr("content")?.trim()
                ?.takeIf { it.isNotEmpty() }
            ?: doc.selectFirst("meta[name=twitter:image]")?.attr("content")?.trim()
                ?.takeIf { it.isNotEmpty() }

        return OpenGraphData(
            ogTitle = title.take(MAX_TITLE_CHARS),
            ogDescription = description?.take(MAX_DESCRIPTION_CHARS),
            ogImageUrl = rawImage?.let { resolveHttpUrl(doc.baseUri(), it) },
        )
    }

    /**
     * Resolve [raw] against [baseUri] and accept only http(s).
     * data:, javascript:, and other non-http(s) schemes resolve to null (T-58-01).
     */
    private fun resolveHttpUrl(baseUri: String, raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val absolute = try {
            java.net.URI(baseUri.ifEmpty { "https://localhost/" }).resolve(trimmed).toString()
        } catch (_: Exception) {
            trimmed
        }
        if (!absolute.startsWith("http://", ignoreCase = true) &&
            !absolute.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }
        return absolute
    }

    /**
     * Locked fallback terminal: og:title to doc.title() to host-of-URL (never empty).
     */
    internal fun hostOf(url: String): String {
        return try {
            val host = java.net.URI(url).host.orEmpty()
            host.ifEmpty { url }
        } catch (_: Exception) {
            url
        }
    }
}
