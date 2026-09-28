package com.warped.data.grounding

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * WebFetch parity (quick task 20260928): HtmlToMarkdown converter
 * verification. Documents the fallback contract (blank → "").
 */
class HtmlToMarkdownTest {

    @Test
    fun `headings convert to atx`() {
        val html = "<html><head><title>T</title></head><body><h1>Hello</h1><h3>Sub</h3></body></html>"
        val out = HtmlToMarkdown.convert(html, "https://x.test", 6000)

        assertThat(out).contains("# Hello")
        assertThat(out).contains("### Sub")
    }

    @Test
    fun `unordered list uses dash bullets`() {
        val html = "<html><body><ul><li>a</li><li>b</li></ul></body></html>"
        val out = HtmlToMarkdown.convert(html, "https://x.test", 6000)

        assertThat(out).contains("- a")
        assertThat(out).contains("- b")
    }

    @Test
    fun `ordered list uses numbered markers`() {
        val html = "<html><body><ol><li>a</li><li>b</li></ol></body></html>"
        val out = HtmlToMarkdown.convert(html, "https://x.test", 6000)

        assertThat(out).contains("1. a")
    }

    @Test
    fun `pre code keeps language fence`() {
        val html = "<html><body><pre><code class=\"language-kotlin\">val x = 1</code></pre></body></html>"
        val out = HtmlToMarkdown.convert(html, "https://x.test", 6000)

        assertThat(out).contains("```kotlin")
        assertThat(out).contains("val x = 1")
    }

    @Test
    fun `table renders github style rows`() {
        val html = "<html><body><table><tr><th>h</th></tr><tr><td>v</td></tr></table></body></html>"
        val out = HtmlToMarkdown.convert(html, "https://x.test", 6000)

        assertThat(out).contains("| h |")
        assertThat(out).contains("| --- |")
        assertThat(out).contains("| v |")
    }

    @Test
    fun `https link kept, javascript link unwrapped`() {
        val html = "<html><body><a href=\"https://ok.test\">good</a> " +
            "<a href=\"javascript:alert(1)\">bad</a></body></html>"
        val out = HtmlToMarkdown.convert(html, "https://x.test", 6000)

        assertThat(out).contains("[good](https://ok.test)")
        assertThat(out).contains("bad")
        assertThat(out).doesNotContain("javascript:")
    }

    @Test
    fun `img keeps alt text only`() {
        val html = "<html><body><img alt=\"pic\" src=\"x.png\"></body></html>"
        val out = HtmlToMarkdown.convert(html, "https://x.test", 6000)

        assertThat(out).contains("pic")
        assertThat(out).doesNotContain("x.png")
    }

    @Test
    fun `script nav footer content absent`() {
        val html = "<html><body><script>evil()</script><nav>menu</nav>" +
            "<footer>foot</footer><p>real</p></body></html>"
        val out = HtmlToMarkdown.convert(html, "https://x.test", 6000)

        assertThat(out).doesNotContain("evil()")
        assertThat(out).doesNotContain("menu")
        assertThat(out).doesNotContain("foot")
        assertThat(out).contains("real")
    }

    @Test
    fun `blank input returns empty for fallback contract`() {
        assertThat(HtmlToMarkdown.convert("", "https://x.test", 6000)).isEqualTo("")
        assertThat(HtmlToMarkdown.convert("<html><body></body></html>", "https://x.test", 6000))
            .isEqualTo("")
    }

    @Test
    fun `over budget output carries truncation marker`() {
        val big = "<html><body>" + (1..200).joinToString("") { "<p>line $it with padding text</p>" } +
            "</body></html>"
        val out = HtmlToMarkdown.convert(big, "https://x.test", 500)

        assertThat(out).contains(HtmlToTextExtractor.TRUNCATION_MARKER)
    }

    @Test
    fun `bold italic blockquote hr convert`() {
        val html = "<html><body><p><b>bold</b> and <i>ital</i></p>" +
            "<blockquote>quoted</blockquote><hr></body></html>"
        val out = HtmlToMarkdown.convert(html, "https://x.test", 6000)

        assertThat(out).contains("**bold**")
        assertThat(out).contains("*ital*")
        assertThat(out).contains("> quoted")
        assertThat(out).contains("---")
    }
}
