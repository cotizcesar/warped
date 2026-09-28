package com.warped.ui.chat

import com.google.common.truth.Truth.assertThat
import com.warped.domain.model.GroundedSource
import com.warped.domain.model.GroundedSourceStatus
import com.warped.ui.chat.components.EMPTY_EXTRACT_COPY
import com.warped.ui.chat.components.browserTarget
import com.warped.ui.chat.components.fuenteItems
import com.warped.ui.chat.components.isEmptyExtract
import com.warped.ui.chat.components.previewForTap
import org.junit.jupiter.api.Test

/**
 * Phase 53 (SRC-01/02/03): JVM tests for the Fuentes → sheet mapping layer.
 *
 * Sheet selection is local `remember` state inside MessageBubble, so these
 * cover the pure mapper level: which taps resolve to preview details, how
 * numbering tracks fetch-block order, which rows take the empty-copy path,
 * and what the browser intent is allowed to carry.
 *
 * The ACTION_VIEW intent itself is not JVM-assertable — covered by code
 * review of the MessageBubble guard (resolved url only, never extracted
 * text, ActivityNotFoundException fallback) plus the WEB-06 device-smoke
 * precedent.
 */
class SourcePreviewMappingTest {

    private val okOne = GroundedSource(
        url = "https://example.com/page-1",
        extractedText = "First page text.",
        status = GroundedSourceStatus.OK,
    )
    private val omitida = GroundedSource(
        url = "https://example.com/dead",
        extractedText = null,
        status = GroundedSourceStatus.OMITIDA,
    )
    private val okTwo = GroundedSource(
        url = "https://example.com/page-2…-redirected",
        extractedText = "Second page text… [truncado]",
        status = GroundedSourceStatus.OK,
    )

    @Test
    fun `ok item maps to non-null preview details`() {
        val details = listOf(okOne, omitida, okTwo)

        assertThat(previewForTap(details, 0)).isEqualTo(okOne)
        assertThat(previewForTap(details, 2)).isEqualTo(okTwo)
    }

    @Test
    fun `omitida item maps to null and never opens the sheet`() {
        val details = listOf(okOne, omitida, okTwo)

        assertThat(previewForTap(details, 1)).isNull()
    }

    @Test
    fun `out-of-range index maps to null`() {
        val details = listOf(okOne)

        assertThat(previewForTap(details, 1)).isNull()
        assertThat(previewForTap(details, -1)).isNull()
        assertThat(previewForTap(emptyList(), 0)).isNull()
    }

    @Test
    fun `numbering matches fetch-block index across ok and omitida`() {
        val details = listOf(okOne, omitida, okTwo)

        val items = fuenteItems(details = details, legacyUrls = emptyList())

        assertThat(items.map { it.number }).containsExactly(1, 2, 3).inOrder()
        assertThat(items.map { it.url }).containsExactly(
            "https://example.com/page-1",
            "https://example.com/dead",
            "https://example.com/page-2…-redirected",
        ).inOrder()
        assertThat(items.map { it.clickable }).containsExactly(true, false, true).inOrder()
    }

    @Test
    fun `empty extract selects the empty-copy path`() {
        val blankExtract = GroundedSource(
            url = "https://example.com/empty",
            extractedText = "   ",
            status = GroundedSourceStatus.OK,
        )

        assertThat(isEmptyExtract(omitida)).isTrue()
        assertThat(isEmptyExtract(blankExtract)).isTrue()
        assertThat(isEmptyExtract(okOne)).isFalse()
        // The sheet renders this copy while keeping the browser button available.
        assertThat(EMPTY_EXTRACT_COPY).contains("browser")
    }

    @Test
    fun `browser target equals the resolved url and never pasted text`() {
        val rawPasted = "mira esto example.com/page-2 que me pasaron"
        val extracted = okTwo.extractedText!!

        val target = browserTarget(okTwo)

        assertThat(target).isEqualTo("https://example.com/page-2…-redirected")
        assertThat(target).isNotEqualTo(rawPasted)
        assertThat(target).doesNotContain(extracted)
    }

    @Test
    fun `legacy urls without details render all-clickable numbered rows`() {
        val items = fuenteItems(
            details = emptyList(),
            legacyUrls = listOf("https://a.example/", "https://b.example/"),
        )

        assertThat(items.map { it.number }).containsExactly(1, 2).inOrder()
        assertThat(items.all { it.clickable }).isTrue()
    }
}
