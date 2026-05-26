package com.warped.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SyntaxThemeTest {

    @Nested
    inner class PresetThemes {

        // === Variant size checks ===

        @Test
        fun `MONOKAI has 13 entries in both dark and light variants`() {
            assertThat(SyntaxTheme.MONOKAI.darkVariant).hasSize(13)
            assertThat(SyntaxTheme.MONOKAI.lightVariant).hasSize(13)
        }

        @Test
        fun `ONE_DARK has 13 entries in both dark and light variants`() {
            assertThat(SyntaxTheme.ONE_DARK.darkVariant).hasSize(13)
            assertThat(SyntaxTheme.ONE_DARK.lightVariant).hasSize(13)
        }

        @Test
        fun `GITHUB has 13 entries in both dark and light variants`() {
            assertThat(SyntaxTheme.GITHUB.darkVariant).hasSize(13)
            assertThat(SyntaxTheme.GITHUB.lightVariant).hasSize(13)
        }

        @Test
        fun `DRACULA has 13 entries in both dark and light variants`() {
            assertThat(SyntaxTheme.DRACULA.darkVariant).hasSize(13)
            assertThat(SyntaxTheme.DRACULA.lightVariant).hasSize(13)
        }

        // === Key and label checks ===

        @Test
        fun `MONOKAI has correct key and label`() {
            assertThat(SyntaxTheme.MONOKAI.key).isEqualTo("monokai")
            assertThat(SyntaxTheme.MONOKAI.label).isEqualTo("Monokai")
        }

        @Test
        fun `ONE_DARK has correct key and label`() {
            assertThat(SyntaxTheme.ONE_DARK.key).isEqualTo("one_dark")
            assertThat(SyntaxTheme.ONE_DARK.label).isEqualTo("One Dark")
        }

        @Test
        fun `GITHUB has correct key and label`() {
            assertThat(SyntaxTheme.GITHUB.key).isEqualTo("github")
            assertThat(SyntaxTheme.GITHUB.label).isEqualTo("GitHub")
        }

        @Test
        fun `DRACULA has correct key and label`() {
            assertThat(SyntaxTheme.DRACULA.key).isEqualTo("dracula")
            assertThat(SyntaxTheme.DRACULA.label).isEqualTo("Dracula")
        }

        // === Monokai Dark color verification ===

        @Test
        fun `MONOKAI dark BACKGROUND is 0xFF272822`() {
            assertThat(SyntaxTheme.MONOKAI.darkVariant[TokenType.BACKGROUND]!!.argb)
                .isEqualTo(0xFF272822.toInt())
        }

        @Test
        fun `MONOKAI dark KEYWORD is 0xFFF92672`() {
            assertThat(SyntaxTheme.MONOKAI.darkVariant[TokenType.KEYWORD]!!.argb)
                .isEqualTo(0xFFF92672.toInt())
        }

        @Test
        fun `MONOKAI dark STRING is 0xFFE6DB74`() {
            assertThat(SyntaxTheme.MONOKAI.darkVariant[TokenType.STRING]!!.argb)
                .isEqualTo(0xFFE6DB74.toInt())
        }

        @Test
        fun `MONOKAI dark NUMBER is 0xFFAE81FF`() {
            assertThat(SyntaxTheme.MONOKAI.darkVariant[TokenType.NUMBER]!!.argb)
                .isEqualTo(0xFFAE81FF.toInt())
        }

        @Test
        fun `MONOKAI dark COMMENT is 0xFF88846F`() {
            assertThat(SyntaxTheme.MONOKAI.darkVariant[TokenType.COMMENT]!!.argb)
                .isEqualTo(0xFF88846F.toInt())
        }

        // === Monokai Light color verification ===

        @Test
        fun `MONOKAI light BACKGROUND is 0xFFF8F8F2`() {
            assertThat(SyntaxTheme.MONOKAI.lightVariant[TokenType.BACKGROUND]!!.argb)
                .isEqualTo(0xFFF8F8F2.toInt())
        }

        @Test
        fun `MONOKAI light KEYWORD is 0xFFE62D6B`() {
            assertThat(SyntaxTheme.MONOKAI.lightVariant[TokenType.KEYWORD]!!.argb)
                .isEqualTo(0xFFE62D6B.toInt())
        }

        // === One Dark Dark color verification ===

        @Test
        fun `ONE_DARK dark BACKGROUND is 0xFF282C34`() {
            assertThat(SyntaxTheme.ONE_DARK.darkVariant[TokenType.BACKGROUND]!!.argb)
                .isEqualTo(0xFF282C34.toInt())
        }

        @Test
        fun `ONE_DARK dark KEYWORD is 0xFFC678DD`() {
            assertThat(SyntaxTheme.ONE_DARK.darkVariant[TokenType.KEYWORD]!!.argb)
                .isEqualTo(0xFFC678DD.toInt())
        }

        @Test
        fun `ONE_DARK dark STRING is 0xFF98C379`() {
            assertThat(SyntaxTheme.ONE_DARK.darkVariant[TokenType.STRING]!!.argb)
                .isEqualTo(0xFF98C379.toInt())
        }

        @Test
        fun `ONE_DARK dark NUMBER is 0xFFD19A66`() {
            assertThat(SyntaxTheme.ONE_DARK.darkVariant[TokenType.NUMBER]!!.argb)
                .isEqualTo(0xFFD19A66.toInt())
        }

        // === One Dark Light color verification ===

        @Test
        fun `ONE_DARK light BACKGROUND is 0xFFFAFAFA`() {
            assertThat(SyntaxTheme.ONE_DARK.lightVariant[TokenType.BACKGROUND]!!.argb)
                .isEqualTo(0xFFFAFAFA.toInt())
        }

        @Test
        fun `ONE_DARK light KEYWORD is 0xFFA626A4`() {
            assertThat(SyntaxTheme.ONE_DARK.lightVariant[TokenType.KEYWORD]!!.argb)
                .isEqualTo(0xFFA626A4.toInt())
        }

        // === GitHub Dark color verification ===

        @Test
        fun `GITHUB dark BACKGROUND is 0xFF0D1117`() {
            assertThat(SyntaxTheme.GITHUB.darkVariant[TokenType.BACKGROUND]!!.argb)
                .isEqualTo(0xFF0D1117.toInt())
        }

        @Test
        fun `GITHUB dark KEYWORD is 0xFFFF7B72`() {
            assertThat(SyntaxTheme.GITHUB.darkVariant[TokenType.KEYWORD]!!.argb)
                .isEqualTo(0xFFFF7B72.toInt())
        }

        @Test
        fun `GITHUB dark STRING is 0xFFA5D6FF`() {
            assertThat(SyntaxTheme.GITHUB.darkVariant[TokenType.STRING]!!.argb)
                .isEqualTo(0xFFA5D6FF.toInt())
        }

        // === Dracula Dark color verification ===

        @Test
        fun `DRACULA dark BACKGROUND is 0xFF282A36`() {
            assertThat(SyntaxTheme.DRACULA.darkVariant[TokenType.BACKGROUND]!!.argb)
                .isEqualTo(0xFF282A36.toInt())
        }

        @Test
        fun `DRACULA dark KEYWORD is 0xFFFF79C6`() {
            assertThat(SyntaxTheme.DRACULA.darkVariant[TokenType.KEYWORD]!!.argb)
                .isEqualTo(0xFFFF79C6.toInt())
        }

        @Test
        fun `DRACULA dark STRING is 0xFFF1FA8C`() {
            assertThat(SyntaxTheme.DRACULA.darkVariant[TokenType.STRING]!!.argb)
                .isEqualTo(0xFFF1FA8C.toInt())
        }

        // === Dracula Light color verification ===

        @Test
        fun `DRACULA light BACKGROUND is 0xFFF8F8F2`() {
            assertThat(SyntaxTheme.DRACULA.lightVariant[TokenType.BACKGROUND]!!.argb)
                .isEqualTo(0xFFF8F8F2.toInt())
        }
    }

    @Nested
    inner class AllAndFromKey {

        @Test
        fun `all returns exactly 4 presets`() {
            val themes = SyntaxTheme.all()
            assertThat(themes).hasSize(4)
        }

        @Test
        fun `all contains all four named presets`() {
            val keys = SyntaxTheme.all().map { it.key }
            assertThat(keys).containsExactly("monokai", "one_dark", "github", "dracula")
        }

        @Test
        fun `fromKey monokai returns MONOKAI`() {
            assertThat(SyntaxTheme.fromKey("monokai")).isEqualTo(SyntaxTheme.MONOKAI)
        }

        @Test
        fun `fromKey one_dark returns ONE_DARK`() {
            assertThat(SyntaxTheme.fromKey("one_dark")).isEqualTo(SyntaxTheme.ONE_DARK)
        }

        @Test
        fun `fromKey github returns GITHUB`() {
            assertThat(SyntaxTheme.fromKey("github")).isEqualTo(SyntaxTheme.GITHUB)
        }

        @Test
        fun `fromKey dracula returns DRACULA`() {
            assertThat(SyntaxTheme.fromKey("dracula")).isEqualTo(SyntaxTheme.DRACULA)
        }

        @Test
        fun `fromKey unknown falls back to MONOKAI`() {
            assertThat(SyntaxTheme.fromKey("nonexistent")).isEqualTo(SyntaxTheme.MONOKAI)
        }

        @Test
        fun `fromKey empty string falls back to MONOKAI`() {
            assertThat(SyntaxTheme.fromKey("")).isEqualTo(SyntaxTheme.MONOKAI)
        }
    }

    @Nested
    inner class CodeThemeMigration {

        private fun migrateCodeTheme(oldName: String): SyntaxTheme = when (oldName) {
            "MONOKAI" -> SyntaxTheme.MONOKAI
            "DRACULA" -> SyntaxTheme.DRACULA
            "NORD" -> SyntaxTheme.ONE_DARK
            "ONE_DARK" -> SyntaxTheme.ONE_DARK
            "GITHUB" -> SyntaxTheme.GITHUB
            "SOLARIZED_DARK" -> SyntaxTheme.MONOKAI
            else -> SyntaxTheme.MONOKAI
        }

        @Test
        fun `MONOKAI migrates to MONOKAI`() {
            assertThat(migrateCodeTheme("MONOKAI")).isEqualTo(SyntaxTheme.MONOKAI)
        }

        @Test
        fun `DRACULA migrates to DRACULA`() {
            assertThat(migrateCodeTheme("DRACULA")).isEqualTo(SyntaxTheme.DRACULA)
        }

        @Test
        fun `NORD migrates to ONE_DARK as closest replacement`() {
            assertThat(migrateCodeTheme("NORD")).isEqualTo(SyntaxTheme.ONE_DARK)
        }

        @Test
        fun `ONE_DARK migrates to ONE_DARK directly`() {
            assertThat(migrateCodeTheme("ONE_DARK")).isEqualTo(SyntaxTheme.ONE_DARK)
        }

        @Test
        fun `GITHUB migrates to GITHUB directly`() {
            assertThat(migrateCodeTheme("GITHUB")).isEqualTo(SyntaxTheme.GITHUB)
        }

        @Test
        fun `SOLARIZED_DARK migrates to MONOKAI as closest replacement`() {
            assertThat(migrateCodeTheme("SOLARIZED_DARK")).isEqualTo(SyntaxTheme.MONOKAI)
        }

        @Test
        fun `unknown old name falls back to MONOKAI`() {
            assertThat(migrateCodeTheme("INVALID_NAME")).isEqualTo(SyntaxTheme.MONOKAI)
        }

        @Test
        fun `empty old name falls back to MONOKAI`() {
            assertThat(migrateCodeTheme("")).isEqualTo(SyntaxTheme.MONOKAI)
        }
    }
}
