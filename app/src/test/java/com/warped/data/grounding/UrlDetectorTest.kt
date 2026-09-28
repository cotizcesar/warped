package com.warped.data.grounding

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Phase 50 (WEB-01): first-URL heuristic verification.
 */
class UrlDetectorTest {

    @Test
    fun `first URL wins, second ignored`() {
        assertThat(UrlDetector.firstUrl("mira https://a.com/x y https://b.com/y"))
            .isEqualTo("https://a.com/x")
    }

    @Test
    fun `trailing sentence period trimmed`() {
        assertThat(UrlDetector.firstUrl("mira https://a.com/x."))
            .isEqualTo("https://a.com/x")
    }

    @Test
    fun `trailing punctuation variants trimmed`() {
        assertThat(UrlDetector.firstUrl("ver (https://a.com/x), ok"))
            .isEqualTo("https://a.com/x")
        assertThat(UrlDetector.firstUrl("lee https://a.com/x! y https://b.com"))
            .isEqualTo("https://a.com/x")
    }

    @Test
    fun `no URL returns null`() {
        assertThat(UrlDetector.firstUrl("hola mundo sin enlaces")).isNull()
        assertThat(UrlDetector.firstUrl("")).isNull()
    }

    @Test
    fun `non-http schemes ignored`() {
        assertThat(UrlDetector.firstUrl("abre ftp://a.com/x o mailto:a@b.com")).isNull()
    }

    @Test
    fun `http and https both match`() {
        assertThat(UrlDetector.firstUrl("ver http://a.com/x")).isEqualTo("http://a.com/x")
    }
}
