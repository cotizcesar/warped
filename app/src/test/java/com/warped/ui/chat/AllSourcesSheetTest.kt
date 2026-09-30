package com.warped.ui.chat

import com.google.common.truth.Truth.assertThat
import com.warped.domain.model.GroundedSource
import com.warped.domain.model.GroundedSourceStatus
import com.warped.ui.chat.components.browserTarget
import com.warped.ui.chat.components.fuenteItems
import com.warped.ui.chat.components.previewForTap
import com.warped.ui.chat.components.shouldShowViewAll
import org.junit.jupiter.api.Test

/**
 * Quick-task (all-sources sheet): JVM tests for the view-all visibility
 * rule, the sheet row-tap routing, and omitida-disabled rows.
 *
 * Pure-mapper level (JUnit5 + Truth), mirroring [SourcePreviewMappingTest]:
 * the guarded ACTION_VIEW intent itself is not JVM-assertable — covered by
 * code review of the MessageBubble/AllSourcesSheet guard (resolved url
 * only, never extracted text, dismiss-only-on-launch).
 */
class AllSourcesSheetTest {

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
        url = "https://example.com/page-2",
        extractedText = "Second page text.",
        status = GroundedSourceStatus.OK,
    )

    @Test
    fun `zero sources hides the view-all icon`() {
        val items = fuenteItems(details = emptyList(), legacyUrls = emptyList())

        assertThat(shouldShowViewAll(items)).isFalse()
    }

    @Test
    fun `single ok source hides the view-all icon`() {
        val items = fuenteItems(details = listOf(okOne), legacyUrls = emptyList())

        assertThat(items).hasSize(1)
        assertThat(shouldShowViewAll(items)).isFalse()
    }

    @Test
    fun `single legacy url hides the view-all icon`() {
        val items = fuenteItems(
            details = emptyList(),
            legacyUrls = listOf("https://a.example/"),
        )

        assertThat(shouldShowViewAll(items)).isFalse()
    }

    @Test
    fun `two ok sources show the view-all icon`() {
        val items = fuenteItems(details = listOf(okOne, okTwo), legacyUrls = emptyList())

        assertThat(shouldShowViewAll(items)).isTrue()
    }

    @Test
    fun `one ok plus one omitida shows the view-all icon`() {
        // Omitida rows count toward the >=2 rule — the drawer lists all N.
        val items = fuenteItems(details = listOf(okOne, omitida), legacyUrls = emptyList())

        assertThat(items.map { it.clickable }).containsExactly(true, false).inOrder()
        assertThat(shouldShowViewAll(items)).isTrue()
    }

    @Test
    fun `two legacy urls show the view-all icon`() {
        val items = fuenteItems(
            details = emptyList(),
            legacyUrls = listOf("https://a.example/", "https://b.example/"),
        )

        assertThat(shouldShowViewAll(items)).isTrue()
    }

    @Test
    fun `ok row taps route to the resolved url and never pasted text`() {
        val details = listOf(okOne, omitida, okTwo)
        val items = fuenteItems(details = details, legacyUrls = emptyList())
        val rawPasted = "mira esto example.com/page-1 que me pasaron"

        items.filter { it.clickable }.forEach { item ->
            val index = item.number - 1
            val resolved = previewForTap(details, index)
            assertThat(resolved).isNotNull()

            val target = browserTarget(resolved!!)

            assertThat(target).isEqualTo(resolved.url)
            assertThat(target).isNotEqualTo(rawPasted)
            resolved.extractedText?.let { assertThat(target).doesNotContain(it) }
        }
    }

    @Test
    fun `omitida rows never resolve to a preview and render without tap`() {
        val details = listOf(okOne, omitida, okTwo)

        assertThat(previewForTap(details, 1)).isNull()

        val items = fuenteItems(details = details, legacyUrls = emptyList())
        val skipped = items.first { !it.clickable }

        assertThat(skipped.clickable).isFalse()
        assertThat(skipped.number).isEqualTo(2)
    }

    @Test
    fun `sheet row order follows fetch-block order across ok and omitida`() {
        val details = listOf(okOne, omitida, okTwo)

        val items = fuenteItems(details = details, legacyUrls = emptyList())

        assertThat(items.map { it.number }).containsExactly(1, 2, 3).inOrder()
        assertThat(items.map { it.url }).containsExactly(
            "https://example.com/page-1",
            "https://example.com/dead",
            "https://example.com/page-2",
        ).inOrder()
    }
}
