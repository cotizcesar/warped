package com.warped.ui.chat.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import com.google.common.truth.Truth.assertThat
import com.warped.domain.model.GroundedSource
import com.warped.domain.model.GroundedSourceStatus
import org.junit.jupiter.api.Test

/**
 * Tap-chain regression coverage: annotations + fuente numbering + tap
 * resolution stay consistent for citations [1]-[5] (SIARHI answer shape).
 */
class CitationTapChainTest {

    private fun fiveOkDetails() = List(5) { i ->
        GroundedSource(
            url = "https://example.com/page$i",
            extractedText = "text $i",
            status = GroundedSourceStatus.OK,
        )
    }

    @Test
    fun `repro - fuenteItems numbers 5 ok details 1 to 5 all clickable`() {
        val items = fuenteItems(fiveOkDetails(), emptyList())
        assertThat(items.map { it.number }).containsExactly(1, 2, 3, 4, 5).inOrder()
        assertThat(items.all { it.clickable }).isTrue()
    }

    @Test
    fun `repro - previewForTap resolves indices 0 to 4`() {
        val details = fiveOkDetails()
        (1..5).forEach { n ->
            assertThat(previewForTap(details, n - 1)).isNotNull()
        }
    }

    @Test
    fun `repro - answer text annotates all five citation numbers`() {
        val text = "Es una aplicación ERP para la industria minera [4]. " +
            "Administrar instancias [1]. Proceso productivo y financiero [1]. " +
            "Ver también [2], [3] y [5]."
        val annotated = parseInlineMarkdownAsAnnotatedString(
            text = text,
            baseStyle = SpanStyle(),
            inlineCodeBgColor = Color.Transparent,
            onCitationClick = {},
        )
        val found = annotated.getStringAnnotations(CITATION_TAG, 0, annotated.length)
            .map { it.item }
        assertThat(found).containsExactly("4", "1", "1", "2", "3", "5").inOrder()
    }
}
