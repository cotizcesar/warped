package com.warped.data.grounding

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test

/**
 * Truth table for [GroundingPrecedence.shouldGround].
 *
 * Precedence: the per-chat override when non-null, else the global
 * default-ON. Pure Kotlin — JVM-testable, zero Android imports.
 */
class GroundingPrecedenceTest {

    private data class Row(
        val perChat: Boolean?,
        val global: Boolean,
        val expected: Boolean,
    )

    @Test
    fun `truth table - perChat, then global`() {
        val rows = listOf(
            // Per-chat override wins over global.
            Row(perChat = true, global = true, expected = true),
            Row(perChat = true, global = false, expected = true),
            Row(perChat = false, global = true, expected = false),
            Row(perChat = false, global = false, expected = false),
            // Null per-chat (Heredar) falls back to global default-ON.
            Row(perChat = null, global = true, expected = true),
            Row(perChat = null, global = false, expected = false),
        )

        for (row in rows) {
            assertWithMessage(
                "shouldGround(perChat=%s, global=%s)",
                row.perChat,
                row.global,
            ).that(
                GroundingPrecedence.shouldGround(
                    perChat = row.perChat,
                    global = row.global,
                ),
            ).isEqualTo(row.expected)
        }
    }

    @Test
    fun `per-chat Yes forces grounding even when global is off`() {
        assertThat(GroundingPrecedence.shouldGround(true, false)).isTrue()
    }

    @Test
    fun `inherit with global off stays model-only`() {
        assertThat(GroundingPrecedence.shouldGround(null, false)).isFalse()
    }
}
