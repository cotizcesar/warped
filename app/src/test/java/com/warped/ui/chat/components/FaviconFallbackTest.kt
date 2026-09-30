package com.warped.ui.chat.components

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Quick-task (favicon fallback): JVM unit tests for [faviconFallbackUrl].
 * Pure Kotlin — [java.net.URI] host parsing plus the exact S2 URL shape.
 * No Android/Compose dependencies; the card, compact card, and preview
 * sheet all render whatever this returns through the existing gated Coil
 * `OgThumb` (verified in [OgSourceCardHelpersTest]).
 */
class FaviconFallbackTest {

    @Test
    fun `https page returns exact s2 url`() {
        assertThat(faviconFallbackUrl("https://example.com/some/article"))
            .isEqualTo("https://www.google.com/s2/favicons?domain=example.com&sz=128")
    }

    @Test
    fun `http page returns exact s2 url`() {
        assertThat(faviconFallbackUrl("http://example.org/"))
            .isEqualTo("https://www.google.com/s2/favicons?domain=example.org&sz=128")
    }

    @Test
    fun `subdomain is preserved verbatim`() {
        assertThat(faviconFallbackUrl("https://news.bbc.co.uk/world"))
            .isEqualTo("https://www.google.com/s2/favicons?domain=news.bbc.co.uk&sz=128")
    }

    @Test
    fun `www prefix is preserved verbatim`() {
        assertThat(faviconFallbackUrl("https://www.example.com/page"))
            .isEqualTo("https://www.google.com/s2/favicons?domain=www.example.com&sz=128")
    }

    @Test
    fun `port query and fragment are stripped by host parsing`() {
        assertThat(faviconFallbackUrl("https://example.com:8443/a?x=1#top"))
            .isEqualTo("https://www.google.com/s2/favicons?domain=example.com&sz=128")
    }

    @Test
    fun `uppercase scheme and host parse`() {
        assertThat(faviconFallbackUrl("HTTPS://EXAMPLE.COM/Page"))
            .isEqualTo("https://www.google.com/s2/favicons?domain=EXAMPLE.COM&sz=128")
    }

    @Test
    fun `surrounding whitespace is trimmed before parsing`() {
        assertThat(faviconFallbackUrl("  https://example.com/a  "))
            .isEqualTo("https://www.google.com/s2/favicons?domain=example.com&sz=128")
    }

    @Test
    fun `s2 output always passes the http gate`() {
        // Defense in depth at the call sites routes the S2 URL through
        // gatedHttpImageUrl — it must survive by construction.
        assertThat(gatedHttpImageUrl(faviconFallbackUrl("https://example.com/a")))
            .isEqualTo("https://www.google.com/s2/favicons?domain=example.com&sz=128")
    }

    // Bad-host cases: null, never a raw-URL leak into Coil.

    @Test
    fun `empty string returns null`() {
        assertThat(faviconFallbackUrl("")).isNull()
    }

    @Test
    fun `blank string returns null`() {
        assertThat(faviconFallbackUrl("   ")).isNull()
    }

    @Test
    fun `unparseable url returns null`() {
        assertThat(faviconFallbackUrl("not a url")).isNull()
    }

    @Test
    fun `non-hierarchical scheme with no host returns null`() {
        assertThat(faviconFallbackUrl("javascript:alert(1)")).isNull()
        assertThat(faviconFallbackUrl("data:text/plain,hi")).isNull()
        assertThat(faviconFallbackUrl("mailto:user@example.com")).isNull()
    }

    @Test
    fun `relative path returns null`() {
        assertThat(faviconFallbackUrl("/relative/path")).isNull()
    }

    @Test
    fun `bare hostname without scheme returns null`() {
        // URI parses this as a path, not a host — no silent wrong-domain
        // favicon; the card stays text-only.
        assertThat(faviconFallbackUrl("example.com/page")).isNull()
    }
}
