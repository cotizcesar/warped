package com.warped.data.grounding

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Phase 58 (OG-01): OpenGraphParser pure-JVM unit tests.
 *
 * Covers the happy path, the locked fallback chain (og:title to title to
 * host), relative image resolution, the http(s) image gate (T-58-01), the
 * 300/500-char hostile-bloat caps (T-58-03), and the blank-HTML edge.
 * Plain/markdown bodies yielding null OG is enforced at the WebPageFetcher
 * call site (HTML branch only), not in the parser.
 */
class OpenGraphParserTest {

    @Test
    fun `og tags happy path`() {
        val html = """
            <html><head>
            <meta property="og:title" content="OG Title" />
            <meta property="og:description" content="OG description here" />
            <meta property="og:image" content="https://cdn.example.com/img.png" />
            <title>Doc Title</title>
            </head><body><p>Hello</p></body></html>
        """.trimIndent()

        val og = OpenGraphParser.parse(html, "https://example.com/page")

        assertThat(og.ogTitle).isEqualTo("OG Title")
        assertThat(og.ogDescription).isEqualTo("OG description here")
        assertThat(og.ogImageUrl).isEqualTo("https://cdn.example.com/img.png")
    }

    @Test
    fun `no og title falls back to document title`() {
        val html = "<html><head><title>Doc Title</title></head><body><p>x</p></body></html>"

        val og = OpenGraphParser.parse(html, "https://example.com/page")

        assertThat(og.ogTitle).isEqualTo("Doc Title")
    }

    @Test
    fun `no og title and no title falls back to host`() {
        val html = "<html><head></head><body><p>x</p></body></html>"

        val og = OpenGraphParser.parse(html, "https://example.com/page")

        assertThat(og.ogTitle).isEqualTo("example.com")
    }

    @Test
    fun `description falls back to meta name description`() {
        val html = """
            <html><head>
            <meta name="description" content="SEO description" />
            </head><body><p>x</p></body></html>
        """.trimIndent()

        val og = OpenGraphParser.parse(html, "https://example.com/page")

        assertThat(og.ogDescription).isEqualTo("SEO description")
    }

    @Test
    fun `missing description and image stay null`() {
        val html = "<html><head><title>T</title></head><body><p>x</p></body></html>"

        val og = OpenGraphParser.parse(html, "https://example.com/page")

        assertThat(og.ogDescription).isNull()
        assertThat(og.ogImageUrl).isNull()
    }

    @Test
    fun `relative image url resolves against base`() {
        val html = """
            <html><head>
            <meta property="og:image" content="/assets/og.png" />
            </head><body><p>x</p></body></html>
        """.trimIndent()

        val og = OpenGraphParser.parse(html, "https://example.com/blog/page")

        assertThat(og.ogImageUrl).isEqualTo("https://example.com/assets/og.png")
    }

    @Test
    fun `twitter image is accepted as fallback`() {
        val html = """
            <html><head>
            <meta name="twitter:image" content="https://cdn.example.com/t.png" />
            </head><body><p>x</p></body></html>
        """.trimIndent()

        val og = OpenGraphParser.parse(html, "https://example.com/page")

        assertThat(og.ogImageUrl).isEqualTo("https://cdn.example.com/t.png")
    }

    @Test
    fun `data uri image is rejected to null`() {
        val html = """
            <html><head>
            <meta property="og:image" content="data:image/png;base64,iVBORw0KGgo=" />
            </head><body><p>x</p></body></html>
        """.trimIndent()

        val og = OpenGraphParser.parse(html, "https://example.com/page")

        assertThat(og.ogImageUrl).isNull()
    }

    @Test
    fun `javascript scheme image is rejected to null`() {
        val html = """
            <html><head>
            <meta property="og:image" content="javascript:alert(1)" />
            </head><body><p>x</p></body></html>
        """.trimIndent()

        val og = OpenGraphParser.parse(html, "https://example.com/page")

        assertThat(og.ogImageUrl).isNull()
    }

    @Test
    fun `blank html yields empty data`() {
        val og = OpenGraphParser.parse("   ", "https://example.com/page")

        assertThat(og.ogTitle).isNull()
        assertThat(og.ogDescription).isNull()
        assertThat(og.ogImageUrl).isNull()
    }

    @Test
    fun `hostile 50KB description is capped at 500 chars`() {
        val huge = "d".repeat(50 * 1024)
        val html = "<html><head><meta property=\"og:description\" content=\"$huge\" /></head>" +
            "<body><p>x</p></body></html>"

        val og = OpenGraphParser.parse(html, "https://example.com/page")

        assertThat(og.ogDescription).isNotNull()
        assertThat(og.ogDescription!!.length).isAtMost(500)
    }

    @Test
    fun `hostile long title is capped at 300 chars`() {
        val huge = "t".repeat(5000)
        val html = "<html><head><meta property=\"og:title\" content=\"$huge\" /></head>" +
            "<body><p>x</p></body></html>"

        val og = OpenGraphParser.parse(html, "https://example.com/page")

        assertThat(og.ogTitle).isNotNull()
        assertThat(og.ogTitle!!.length).isAtMost(300)
    }

    @Test
    fun `grounded and source default to null og so existing callers compile untouched`() {
        val grounded = GroundingResult.Grounded(block = "b", url = "https://example.com", text = "t")

        assertThat(grounded.openGraph).isNull()
    }
}
