package com.warped.ui.huggingface

import com.google.common.truth.Truth.assertThat
import com.warped.data.repository.AllowlistedModel
import org.junit.jupiter.api.Test

/**
 * Catalog card expand-text contract (quick plan 2026-09-28): `expandedText`
 * joins non-blank ramNote + blurb; null when both are absent/blank so the
 * card renders with no expand affordance.
 */
class CatalogCardTextTest {

    private fun entry(ramNote: String?, blurb: String?) = AllowlistedModel(
        name = "x",
        displayName = "X",
        modelFile = "x.task",
        sizeInBytes = 1L,
        ramNote = ramNote,
        blurb = blurb
    )

    @Test
    fun `both present are joined`() {
        val text = expandedText(entry("Desde ~4 GB de RAM", "Chat general."))

        assertThat(text).isNotNull()
        assertThat(text!!).contains("Desde ~4 GB de RAM")
        assertThat(text).contains("Chat general.")
    }

    @Test
    fun `missing fields return null`() {
        assertThat(expandedText(entry(null, null))).isNull()
    }

    @Test
    fun `blank strings are treated as absent`() {
        assertThat(expandedText(entry("  ", ""))).isNull()
        assertThat(expandedText(entry("", null))).isNull()
    }

    @Test
    fun `single present field returns non-null`() {
        assertThat(expandedText(entry("Desde ~4 GB de RAM", null)))
            .isEqualTo("Desde ~4 GB de RAM")
        assertThat(expandedText(entry(null, "Chat general.")))
            .isEqualTo("Chat general.")
    }

    @Test
    fun `title end padding is narrow when idle`() {
        assertThat(titleEndPaddingDp(false)).isEqualTo(52)
    }

    @Test
    fun `title end padding stays narrow when active (unified look, no wide cluster)`() {
        assertThat(titleEndPaddingDp(true)).isEqualTo(52)
    }
}
