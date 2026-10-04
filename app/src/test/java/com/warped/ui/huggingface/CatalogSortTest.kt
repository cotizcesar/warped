package com.warped.ui.huggingface

import com.google.common.truth.Truth.assertThat
import com.warped.data.repository.AllowlistedModel
import org.junit.jupiter.api.Test

/**
 * Catalog display order: downloadable smallest-first, coming-soon trailing.
 */
class CatalogSortTest {

    private fun model(name: String, size: Long, comingSoon: Boolean = false) =
        AllowlistedModel(
            name = name,
            displayName = name,
            modelFile = "$name.litertlm",
            sizeInBytes = size,
            comingSoon = comingSoon,
        )

    @Test
    fun `downloadable models sort smallest first`() {
        val out = sortCatalogModels(
            listOf(model("big", 300L), model("small", 100L), model("mid", 200L))
        )

        assertThat(out.map { it.name }).containsExactly("small", "mid", "big").inOrder()
    }

    @Test
    fun `coming-soon entries always trail regardless of size`() {
        val out = sortCatalogModels(
            listOf(
                model("tiny-soon", 10L, comingSoon = true),
                model("big", 900L),
                model("huge-soon", 10L, comingSoon = true),
                model("small", 100L),
            )
        )

        assertThat(out.map { it.name }).containsExactly(
            "small",
            "big",
            "tiny-soon",
            "huge-soon",
        ).inOrder()
    }
}
