package com.warped.data.grounding

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test

/**
 * Phase 53 (TOGGLE-02): truth table for [GroundingPrecedence.shouldGround].
 *
 * Precedence: one-off Sin web wins, then the per-chat override when non-null,
 * else the global default-ON. Pure Kotlin — JVM-testable, zero Android imports.
 */
class GroundingPrecedenceTest {

    private data class Row(
        val skipOnce: Boolean,
        val perChat: Boolean?,
        val global: Boolean,
        val expected: Boolean,
    )

    @Test
    fun `truth table - skipOnce first, then perChat, then global`() {
        val rows = listOf(
            // One-off Sin web wins over everything.
            Row(skipOnce = true, perChat = true, global = true, expected = false),
            Row(skipOnce = true, perChat = true, global = false, expected = false),
            Row(skipOnce = true, perChat = false, global = true, expected = false),
            Row(skipOnce = true, perChat = false, global = false, expected = false),
            Row(skipOnce = true, perChat = null, global = true, expected = false),
            Row(skipOnce = true, perChat = null, global = false, expected = false),
            // Per-chat override wins over global.
            Row(skipOnce = false, perChat = true, global = true, expected = true),
            Row(skipOnce = false, perChat = true, global = false, expected = true),
            Row(skipOnce = false, perChat = false, global = true, expected = false),
            Row(skipOnce = false, perChat = false, global = false, expected = false),
            // Null per-chat (Heredar) falls back to global default-ON.
            Row(skipOnce = false, perChat = null, global = true, expected = true),
            Row(skipOnce = false, perChat = null, global = false, expected = false),
        )

        for (row in rows) {
            assertWithMessage(
                "shouldGround(skipOnce=%s, perChat=%s, global=%s)",
                row.skipOnce,
                row.perChat,
                row.global,
            ).that(
                GroundingPrecedence.shouldGround(
                    skipOnce = row.skipOnce,
                    perChat = row.perChat,
                    global = row.global,
                ),
            ).isEqualTo(row.expected)
        }
    }

    @Test
    fun `skipOnce true never grounds even when chat and global agree on`() {
        assertThat(GroundingPrecedence.shouldGround(true, true, true)).isFalse()
    }

    @Test
    fun `inherit with global off stays model-only`() {
        assertThat(GroundingPrecedence.shouldGround(false, null, false)).isFalse()
    }
}
