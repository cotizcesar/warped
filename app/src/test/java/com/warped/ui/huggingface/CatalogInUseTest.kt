package com.warped.ui.huggingface

import com.google.common.truth.Truth.assertThat
import com.warped.data.repository.AllowlistedModel
import org.junit.jupiter.api.Test

/**
 * "In use" badge rule (2026-10-02): a downloaded catalog card is marked
 * when the active local model id resolves to the same on-device file as
 * the entry (basename match — the DB row stores the absolute path).
 */
class CatalogInUseTest {

    private fun entry(modelFile: String) = AllowlistedModel(
        name = "x",
        displayName = "X",
        modelFile = modelFile,
        sizeInBytes = 1L,
    )

    @Test
    fun `matching basename is in use`() {
        assertThat(
            isEntryInUse(
                "/data/data/com.warped/files/models/gemma.task",
                entry("gemma.task"),
            ),
        ).isTrue()
    }

    @Test
    fun `different file is not in use`() {
        assertThat(
            isEntryInUse(
                "/data/data/com.warped/files/models/other.task",
                entry("gemma.task"),
            ),
        ).isFalse()
    }

    @Test
    fun `null active model is never in use`() {
        assertThat(isEntryInUse(null, entry("gemma.task"))).isFalse()
    }
}
