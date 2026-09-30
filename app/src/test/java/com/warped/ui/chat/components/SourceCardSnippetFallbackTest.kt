package com.warped.ui.chat.components

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Quick-task (card-snippet): card description fallback chain
 * `og:description` → `snippet` → hidden.
 *
 * Rendering rules are identical for both legs (same trim/cap/hide
 * behavior, same Text style/visibility branch at the call sites — no new
 * card layout). Snippets arrive pre-sanitized from the repositories, so
 * these tests pin the pure render function only.
 */
class SourceCardSnippetFallbackTest {

    @Test
    fun `null ogDescription with snippet shows snippet`() {
        assertThat(ogDisplayDescription(null, "Search excerpt here."))
            .isEqualTo("Search excerpt here.")
    }

    @Test
    fun `blank ogDescription falls back to snippet`() {
        assertThat(ogDisplayDescription("   ", "Fallback text."))
            .isEqualTo("Fallback text.")
    }

    @Test
    fun `ogDescription present wins over snippet`() {
        assertThat(ogDisplayDescription("Author description.", "Search excerpt."))
            .isEqualTo("Author description.")
    }

    @Test
    fun `blank snippet and blank og hides the line`() {
        assertThat(ogDisplayDescription("  ", "   ")).isNull()
    }

    @Test
    fun `both null hides the line`() {
        assertThat(ogDisplayDescription(null, null)).isNull()
    }

    @Test
    fun `snippet is capped at the same 160 chars as ogDescription`() {
        val long = "s".repeat(400)
        assertThat(ogDisplayDescription(null, long)).hasLength(160)
        assertThat(ogDisplayDescription("o".repeat(400), long)).hasLength(160)
    }

    @Test
    fun `snippet is trimmed before display`() {
        assertThat(ogDisplayDescription(null, "  padded excerpt  "))
            .isEqualTo("padded excerpt")
    }

    @Test
    fun `legacy single-arg callers keep exact behavior`() {
        assertThat(ogDisplayDescription("A short description."))
            .isEqualTo("A short description.")
        assertThat(ogDisplayDescription("")).isNull()
        assertThat(ogDisplayDescription(null)).isNull()
    }
}
