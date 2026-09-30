package com.warped.data.grounding

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLEncoder

/**
 * Quick-task (YouTube oEmbed): pure helper for the title/thumbnail
 * fallback in [SearchOgEnricher]. No sockets here — host allowlist,
 * request-URL builder, and JSON parsing only, JVM-testable with zero I/O.
 *
 * Deviation note: the plan suggested `org.json.JSONObject`, but `org.json`
 * is an Android-framework stub that throws in local JVM unit tests.
 * `kotlinx.serialization` (already in the stack) gives the same opt-string
 * contract and is JVM-safe.
 */
object YoutubeOembed {

    /** Locked host set: exact membership only — no subdomain wildcards. */
    val YOUTUBE_HOSTS = setOf(
        "youtube.com",
        "www.youtube.com",
        "youtu.be",
        "m.youtube.com",
    )

    private const val OEMBED_BASE = "https://www.youtube.com/oembed"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class OembedResponse(
        val title: String? = null,
        val thumbnail_url: String? = null,
    )

    /**
     * True only for http(s) URLs whose host is exactly in [YOUTUBE_HOSTS].
     * Other subdomains (`music.youtube.com`), lookalikes
     * (`youtube-nocookie.com`, `notyoutube.com`), and non-http(s) schemes
     * all return false.
     */
    fun isYouTubeUrl(url: String): Boolean {
        val uri = try {
            URI(url.trim())
        } catch (_: Exception) {
            return false
        }
        val scheme = uri.scheme?.lowercase().orEmpty()
        if (scheme != "http" && scheme != "https") return false
        return uri.host?.lowercase() in YOUTUBE_HOSTS
    }

    /**
     * Builds the constant-base oEmbed request URL with the video URL as an
     * encoded query param. Always `https://www.youtube.com/oembed?…`.
     */
    fun oembedRequestUrl(videoUrl: String): String =
        "$OEMBED_BASE?url=${URLEncoder.encode(videoUrl, Charsets.UTF_8.name())}&format=json"

    /**
     * Parses an oEmbed JSON body into `(title, thumbnailUrl)`. Blank
     * strings become null; `author_name` is deliberately unread and can
     * never land in any column. Malformed JSON returns `(null, null)` —
     * the caller keeps the pre-oEmbed row.
     */
    fun parseOembed(body: String): Pair<String?, String?> {
        val parsed = try {
            json.decodeFromString<OembedResponse>(body)
        } catch (_: Exception) {
            return null to null
        }
        return parsed.title?.trim()?.takeIf { it.isNotEmpty() } to
            parsed.thumbnail_url?.trim()?.takeIf { it.isNotEmpty() }
    }
}
