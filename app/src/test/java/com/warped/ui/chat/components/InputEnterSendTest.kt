package com.warped.ui.chat.components

import androidx.compose.ui.text.TextRange
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Soft-keyboard Enter-as-newline detection: only a lone appended
 * trailing newline (outside composition) sends; everything else
 * keeps the normal multiline path.
 */
class InputEnterSendTest {

    @Test
    fun `appended trailing newline sends`() {
        assertThat(shouldSendOnNewline("hola", "hola\n", composing = false)).isTrue()
    }

    @Test
    fun `newline on empty buffer still detected (send gated by content downstream)`() {
        assertThat(shouldSendOnNewline("", "\n", composing = false)).isTrue()
    }

    @Test
    fun `composing buffer never sends`() {
        assertThat(shouldSendOnNewline("hola", "hola\n", composing = true)).isFalse()
    }

    @Test
    fun `pasted multiline block does not send`() {
        assertThat(shouldSendOnNewline("hola", "hola\nmundo", composing = false)).isFalse()
    }

    @Test
    fun `mid-text newline does not send`() {
        assertThat(shouldSendOnNewline("hlamundo", "hola\nmundo", composing = false)).isFalse()
    }

    @Test
    fun `plain typing does not send`() {
        assertThat(shouldSendOnNewline("hola", "holas", composing = false)).isFalse()
        assertThat(shouldSendOnNewline("hola", "hola", composing = false)).isFalse()
    }

    @Test
    fun `cleared value is empty composition-free at zero`() {
        val cleared = clearedFieldValue()

        assertThat(cleared.text).isEmpty()
        assertThat(cleared.composition).isNull()
        assertThat(cleared.selection).isEqualTo(TextRange.Zero)
    }
}
