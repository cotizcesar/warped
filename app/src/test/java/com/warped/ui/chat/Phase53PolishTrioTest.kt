package com.warped.ui.chat

import com.google.common.truth.Truth.assertThat
import com.warped.domain.model.GroundedSource
import com.warped.domain.model.GroundedSourceStatus
import com.warped.ui.chat.components.FuenteItem
import com.warped.ui.chat.components.fuenteItems
import com.warped.ui.chat.components.fuentesVisible
import com.warped.ui.chat.components.omitidaOnly
import org.junit.jupiter.api.Test

/**
 * Quick-task (phase53-trio):
 * (a) the selector bar shows the per-chat web-override state
 * (inherit/on/off) read from the existing tri-state;
 * (b) the redundant "Fuente N" sheet heading is gone (the [N] badge
 * labels the row — deletion pinned by build, no restyle);
 * (c) all-omitida turns render struck rows + count instead of hiding.
 */
class Phase53PolishTrioTest {

    // ------------------------------------------------------------------
    // (a) override indicator — removed with the dead WebOverrideIndicator
    // helpers (phase 64 IN-01); the per-chat tri-state persists only in
    // the data layer via ChatViewModel.setWebOverride.
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // (c) all-omitida honesty block.
    // ------------------------------------------------------------------

    private fun ok(url: String) = FuenteItem(number = 1, url = url, clickable = true)

    private fun omitted(number: Int, url: String) =
        FuenteItem(number = number, url = url, clickable = false)

    private fun hydratedAllOmitida() = fuenteItems(
        details = listOf(
            GroundedSource(url = "https://a.example/x", status = GroundedSourceStatus.OMITIDA),
            GroundedSource(url = "https://b.example/y", status = GroundedSourceStatus.OMITIDA),
        ),
        legacyUrls = emptyList(),
    )

    @Test
    fun `assistant turn with ok rows shows the block`() {
        assertThat(fuentesVisible(listOf(ok("https://a.example/x")), isUser = false)).isTrue()
    }

    @Test
    fun `user turns never show the block`() {
        assertThat(fuentesVisible(listOf(ok("https://a.example/x")), isUser = true)).isFalse()
        assertThat(fuentesVisible(hydratedAllOmitida(), isUser = true)).isFalse()
    }

    @Test
    fun `empty rows show nothing`() {
        assertThat(fuentesVisible(emptyList(), isUser = false)).isFalse()
        assertThat(omitidaOnly(emptyList())).isFalse()
    }

    @Test
    fun `all-omitida turn shows the block in omitida-only mode`() {
        val list = hydratedAllOmitida()
        assertThat(fuentesVisible(list, isUser = false)).isTrue()
        assertThat(omitidaOnly(list)).isTrue()
        // Count legibility: every row is struck-rendered, size feeds the
        // visible count line.
        assertThat(list).hasSize(2)
        assertThat(list.none { it.clickable }).isTrue()
    }

    @Test
    fun `mixed turn is not omitida-only`() {
        val list = listOf(ok("https://a.example/x"), omitted(2, "https://b.example/y"))
        assertThat(fuentesVisible(list, isUser = false)).isTrue()
        assertThat(omitidaOnly(list)).isFalse()
    }
}
