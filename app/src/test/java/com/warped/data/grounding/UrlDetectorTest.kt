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

    // Phase 52 (FETCH-01): deterministic fan-out input.

    @Test
    fun `allUrls dedupes preserving first-seen order`() {
        assertThat(
            UrlDetector.allUrls("a https://x.com/1 b https://y.com/2 a https://x.com/1 c https://y.com/2")
        ).containsExactly("https://x.com/1", "https://y.com/2").inOrder()
    }

    @Test
    fun `allUrls caps at 5 dropping 6th and later deterministically`() {
        val text = (1..7).joinToString(" ") { "https://sitio.com/pagina$it" }

        val urls = UrlDetector.allUrls(text)

        assertThat(urls).hasSize(5)
        assertThat(urls).containsExactly(
            "https://sitio.com/pagina1",
            "https://sitio.com/pagina2",
            "https://sitio.com/pagina3",
            "https://sitio.com/pagina4",
            "https://sitio.com/pagina5",
        ).inOrder()
    }

    @Test
    fun `allUrls trims trailing punctuation like firstUrl`() {
        assertThat(UrlDetector.allUrls("mira https://a.com/x. y https://b.com/y!"))
            .containsExactly("https://a.com/x", "https://b.com/y").inOrder()
    }

    @Test
    fun `allUrls empty when no URLs and honors max`() {
        assertThat(UrlDetector.allUrls("hola mundo sin enlaces")).isEmpty()
        assertThat(UrlDetector.allUrls("")).isEmpty()
        val text = "https://a.com/1 https://b.com/2 https://c.com/3"
        assertThat(UrlDetector.allUrls(text, max = 2))
            .containsExactly("https://a.com/1", "https://b.com/2").inOrder()
    }
}
