package com.warped.ui.chat

import com.google.common.truth.Truth.assertThat
import com.warped.domain.model.LocalModel
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Auto-select on empty selection (no manual connect switch): a fresh
 * download/import leaves nothing selected, which disables the chat input.
 * [pickAutoSelectModel] picks the newest model so chat is immediately
 * usable; the engine loads on first send.
 */
class AutoSelectModelTest {

    private fun model(path: String, at: Instant) = LocalModel(
        id = 1,
        name = path.substringAfterLast("/"),
        filePath = path,
        sizeBytes = 10,
        quantization = "Q4",
        parameterCount = "1B",
        architecture = "gemma",
        importedAt = at
    )

    @Test
    fun `returns null when a selection exists`() {
        val models = listOf(model("/m/a.litertlm", Instant.EPOCH))
        assertThat(pickAutoSelectModel(models, "/m/a.litertlm")).isNull()
    }

    @Test
    fun `returns null when the list is empty`() {
        assertThat(pickAutoSelectModel(emptyList(), null)).isNull()
    }

    @Test
    fun `picks the newest model by import time`() {
        val models = listOf(
            model("/m/old.litertlm", Instant.EPOCH),
            model("/m/new.litertlm", Instant.EPOCH.plusSeconds(3600))
        )
        assertThat(pickAutoSelectModel(models, null)).isEqualTo("/m/new.litertlm")
    }

    @Test
    fun `single model is picked when nothing selected`() {
        val models = listOf(model("/m/only.litertlm", Instant.EPOCH))
        assertThat(pickAutoSelectModel(models, null)).isEqualTo("/m/only.litertlm")
    }
}
