package com.warped.data.grounding

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Phase 50 (WEB-03): hand-rolled HTML→text quality bar.
 */
class HtmlToTextExtractorTest {

    private val fixture = """
        <html><head><title>Hierba mate — Wikipedia</title>
        <style>.x { color: red; }</style>
        <script>var x = "<div>evil</div>";</script>
        </head><body>
        <!-- un comentario -->
        <h1>Hierba mate</h1>
        <p>La <b>yerba mate</b> es una planta &amp; un cultivo importante.</p>
        <p>Se consume como infusión &#8212; el mate &#x2014; en Sudamérica.</p>
        <div><p>Más texto con &quot;comillas&quot; y &#39;apóstrofes&apos; y espacios&nbsp;duros.</p></div>
        <noscript>solo sin js</noscript>
        </body></html>
    """.trimIndent()

    @Test
    fun `script style noscript and comments stripped`() {
        val out = HtmlToTextExtractor.extract(fixture, "https://es.wikipedia.org/wiki/X")

        assertThat(out).doesNotContain("evil")
        assertThat(out).doesNotContain("color: red")
        assertThat(out).doesNotContain("solo sin js")
        assertThat(out).doesNotContain("un comentario")
    }

    @Test
    fun `entities decoded`() {
        val out = HtmlToTextExtractor.extract(fixture, "https://example.com")

        assertThat(out).contains("planta & un cultivo importante.")
        assertThat(out).contains("\"comillas\"")
        assertThat(out).contains("—")
    }

    @Test
    fun `wikipedia style fixture keeps title headings and prose without tag remnants`() {
        val out = HtmlToTextExtractor.extract(fixture, "https://es.wikipedia.org/wiki/X")

        assertThat(out).contains("Hierba mate — Wikipedia")
        assertThat(out).contains("Hierba mate")
        assertThat(out).contains("yerba mate es una planta")
        assertThat(out.length).isAtLeast(200)
        assertThat(out).doesNotContain("<")
        assertThat(out).doesNotContain(">")
    }

    @Test
    fun `long pages truncate at line boundary with marker`() {
        val long = buildString {
            append("<html><body>")
            repeat(500) { i -> append("<p>Línea de contenido número $i con texto de relleno.</p>") }
            append("</body></html>")
        }
        val out = HtmlToTextExtractor.extract(long, "https://example.com/big")

        assertThat(out).endsWith(HtmlToTextExtractor.TRUNCATION_MARKER)
        assertThat(out.length).isAtMost(HtmlToTextExtractor.MAX_CHARS + HtmlToTextExtractor.TRUNCATION_MARKER.length + 1)
        // Line-boundary cut: the char before the marker newline completes a line.
        val withoutMarker = out.removeSuffix("\n" + HtmlToTextExtractor.TRUNCATION_MARKER)
        assertThat(withoutMarker).doesNotContain("\n\n")
    }

    @Test
    fun `short pages have no truncation marker`() {
        val out = HtmlToTextExtractor.extract("<p>Hola</p>", "https://example.com")

        assertThat(out).isEqualTo("Hola")
    }
}
