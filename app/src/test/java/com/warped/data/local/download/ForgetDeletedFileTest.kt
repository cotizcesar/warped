package com.warped.data.local.download

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Deleting a model must drop its download records, or the catalog keeps
 * showing a ghost "downloaded" card off the stale in-memory state.
 */
class ForgetDeletedFileTest {

    private fun state(id: String, file: String) =
        DownloadState(modelId = id, fileName = file, progress = 1f)

    @Test
    fun `drops records matching the deleted file by key suffix or field`() {
        val records = mapOf(
            "warped-community/a-litert-lm" to state("warped-community/a-litert-lm", "a.litertlm"),
            "warped-community/b-litert-lm" to state("warped-community/b-litert-lm", "b.litertlm"),
        )

        val out = filterOutDeletedFile(records, "a.litertlm")

        assertThat(out.keys).containsExactly("warped-community/b-litert-lm")
    }

    @Test
    fun `keeps unrelated records untouched`() {
        val records = mapOf(
            "warped-community/a-litert-lm" to state("warped-community/a-litert-lm", "a.litertlm"),
        )

        val out = filterOutDeletedFile(records, "other.litertlm")

        assertThat(out).hasSize(1)
    }

    @Test
    fun `empty map stays empty`() {
        assertThat(filterOutDeletedFile(emptyMap(), "a.litertlm")).isEmpty()
    }
}
