package com.warped.ui.chat

import com.google.common.truth.Truth.assertThat
import com.warped.data.local.db.entity.MessageEntity
import com.warped.data.local.db.entity.toDomain
import com.warped.domain.model.ChatMessage
import com.warped.domain.model.Role
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * 48-01 (PERF-15, threats T-48-01/T-48-02): LazyColumn key-stability proof.
 *
 * Every ChatMessage carries a distinct stable UUID id by default, the Room
 * mapper preserves the entity id into the domain id (no regeneration on
 * reload), and the synthetic trailing keys are content-independent constants
 * (rotation can never duplicate the streaming bubble; fling reuse can never
 * cross-contaminate rows).
 */
class ChatKeyStabilityTest {

    @Test
    fun `messages carry distinct stable UUID ids by default`() {
        val messages = List(200) { ChatMessage(role = Role.USER, content = "message $it") }

        val ids = messages.map { it.id }
        assertThat(ids).containsNoDuplicates()
        assertThat(ids.size).isEqualTo(200)
        // Each id round-trips through UUID parsing: stable UUID format.
        ids.forEach { id ->
            assertThat(UUID.fromString(id).toString()).isEqualTo(id)
        }
    }

    @Test
    fun `room mapper preserves the entity id`() {
        val entity = MessageEntity(
            id = 7L,
            conversationId = 42L,
            role = "USER",
            content = "hello",
            createdAt = 1_700_000_000_000L,
        )

        val domain = entity.toDomain()

        assertThat(domain.id).isEqualTo("7")
        assertThat(domain.role).isEqualTo(Role.USER)
        assertThat(domain.content).isEqualTo("hello")
    }

    @Test
    fun `room mapper preserves tool row identity`() {
        val first = MessageEntity(
            id = 11L,
            conversationId = 42L,
            role = "TOOL",
            content = "calculator\n20",
            createdAt = 1_700_000_000_001L,
        ).toDomain()
        val second = MessageEntity(
            id = 12L,
            conversationId = 42L,
            role = "TOOL",
            content = "calculator\n20",
            createdAt = 1_700_000_000_002L,
        ).toDomain()

        // Same content, distinct rows: keys never collide.
        assertThat(first.role).isEqualTo(Role.TOOL)
        assertThat(first.id).isNotEqualTo(second.id)
    }

    @Test
    fun `synthetic trailing keys are content-independent constants`() {
        assertThat(ChatListKeys.STREAMING).isEqualTo("streaming")
        assertThat(ChatListKeys.TOOL_STATUS).isEqualTo("tool-status")
        assertThat(ChatListKeys.NO_TOOL_SUPPORT).isEqualTo("no-tool-support")
        assertThat(ChatListKeys.toolError("calculator")).isEqualTo("tool-error-calculator")

        // Error keys are per-tool (stable across re-emissions), never content hashes.
        assertThat(ChatListKeys.toolError("calculator")).isNotEqualTo(ChatListKeys.toolError("json_formatter"))
        assertThat(ChatListKeys.toolError("calculator")).isEqualTo(ChatListKeys.toolError("calculator"))
    }
}
