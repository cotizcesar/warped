package com.warped.i18n

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Quick-task langmatch-i18n-paragraphs: EN (`values`) is canonical, ES
 * (`values-es`) must carry the exact same key set. Fails listing the
 * missing/extra keys so key drift breaks CI instead of leaking English
 * (or crashing on a missing resource) on ES-locale devices. JVM-only.
 */
class StringResourceParityTest {

    private fun keys(variant: String): Set<String> {
        val file = File("src/main/res/$variant/strings.xml")
            .takeIf { it.exists() }
            ?: File("app/src/main/res/$variant/strings.xml")
        assertThat(file.exists()).isTrue()
        val doc = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder().parse(file)
        val out = mutableSetOf<String>()
        val strings = doc.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            out += strings.item(i).attributes.getNamedItem("name").nodeValue
        }
        val arrays = doc.getElementsByTagName("string-array")
        for (i in 0 until arrays.length) {
            out += arrays.item(i).attributes.getNamedItem("name").nodeValue
        }
        return out
    }

    @Test
    fun `values-es has exact key parity with values`() {
        val en = keys("values")
        val es = keys("values-es")

        assertThat(es - en).isEmpty()
        assertThat(en - es).isEmpty()
        assertThat(en).isNotEmpty()
    }
}
