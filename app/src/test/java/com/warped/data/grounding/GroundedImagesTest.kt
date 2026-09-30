package com.warped.data.grounding

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Quick-task (image-grid): render-side image-list mapping.
 */
class GroundedImagesTest {

    @Test
    fun `http and https pass through trimmed and distinct`() {
        val out = GroundedImages.visibleImages(
            listOf(
                "  https://img.example/a.png  ",
                "http://img.example/b.jpg",
                "https://img.example/a.png",
            ),
        )

        assertThat(out).containsExactly(
            "https://img.example/a.png",
            "http://img.example/b.jpg",
        ).inOrder()
    }

    @Test
    fun `non-http schemes drop`() {
        val out = GroundedImages.visibleImages(
            listOf(
                "data:image/png;base64,AAA",
                "javascript:alert(1)",
                "ftp://img.example/a.png",
                "/relative/path.png",
                "",
                "   ",
                "https://img.example/ok.png",
            ),
        )

        assertThat(out).containsExactly("https://img.example/ok.png")
    }

    @Test
    fun `list caps at ten`() {
        val out = GroundedImages.visibleImages(
            (1..15).map { "https://img.example/$it.png" },
        )

        assertThat(out).hasSize(10)
        assertThat(out.first()).isEqualTo("https://img.example/1.png")
    }
}
