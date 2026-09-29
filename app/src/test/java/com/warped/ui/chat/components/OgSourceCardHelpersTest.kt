package com.warped.ui.chat.components

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Phase 58 (OG-02): JVM unit tests for the pure render-side OG helpers.
 * No Android/Compose dependencies — [java.net.URI] host parsing and the
 * http(s) image re-gate (T-58-06) are verified here; the composables
 * themselves are thin render over these values.
 */
class OgSourceCardHelpersTest {

    // ogHostOf

    @Test
    fun `hostOf returns bare host`() {
        assertThat(ogHostOf("https://example.com/page")).isEqualTo("example.com")
    }

    @Test
    fun `hostOf strips www prefix`() {
        assertThat(ogHostOf("https://www.example.com/page")).isEqualTo("example.com")
    }

    @Test
    fun `hostOf returns raw url when unparseable`() {
        assertThat(ogHostOf("not a url")).isEqualTo("not a url")
    }

    // gatedHttpImageUrl (T-58-06)

    @Test
    fun `image gate accepts http and https`() {
        assertThat(gatedHttpImageUrl("https://cdn.example.com/img.jpg"))
            .isEqualTo("https://cdn.example.com/img.jpg")
        assertThat(gatedHttpImageUrl("http://cdn.example.com/img.jpg"))
            .isEqualTo("http://cdn.example.com/img.jpg")
    }

    @Test
    fun `image gate accepts uppercase scheme`() {
        assertThat(gatedHttpImageUrl("HTTPS://cdn.example.com/img.jpg"))
            .isEqualTo("HTTPS://cdn.example.com/img.jpg")
    }

    @Test
    fun `image gate rejects non-http schemes`() {
        assertThat(gatedHttpImageUrl("data:image/png;base64,AAA")).isNull()
        assertThat(gatedHttpImageUrl("javascript:alert(1)")).isNull()
        assertThat(gatedHttpImageUrl("ftp://cdn.example.com/img.jpg")).isNull()
        assertThat(gatedHttpImageUrl("file:///sdcard/img.jpg")).isNull()
    }

    @Test
    fun `image gate rejects null blank and schemeless`() {
        assertThat(gatedHttpImageUrl(null)).isNull()
        assertThat(gatedHttpImageUrl("   ")).isNull()
        assertThat(gatedHttpImageUrl("/relative/path.jpg")).isNull()
    }

    // ogDisplayTitle

    @Test
    fun `displayTitle prefers trimmed og title`() {
        assertThat(ogDisplayTitle("  Hello  ", "https://example.com/x"))
            .isEqualTo("Hello")
    }

    @Test
    fun `displayTitle falls back to host and never empty`() {
        assertThat(ogDisplayTitle(null, "https://example.com/x"))
            .isEqualTo("example.com")
        assertThat(ogDisplayTitle("   ", "https://example.com/x"))
            .isEqualTo("example.com")
    }
}
