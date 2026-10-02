package com.warped.data.remote

import com.google.common.truth.Truth.assertThat
import com.warped.data.agentic.LocalToolLoop
import com.warped.data.agentic.READ_TEXT_TOOL_DESCRIPTION
import com.warped.data.agentic.ToolCapabilityMatrix
import com.warped.data.remote.dto.defaultAnthropicTools
import com.warped.data.remote.dto.defaultRemoteTools
import org.junit.jupiter.api.Test

/**
 * Phase 70 (70-02): exit gates for the remote `read_text_file` mapping.
 *
 * JVM-only: the armed remote turns carry three `tools[]` entries (OpenAI
 * dialect + Anthropic native) with the description identical to the local
 * schema constant; the allowlist round-trips the remote entry name; the
 * tools-rejection classifier still pins the 3-tool list fallback.
 */
class DocumentRemoteToolsTest {

    @Test
    fun `default remote tools carry three entries with read_text_file`() {
        val tools = defaultRemoteTools()
        assertThat(tools).hasSize(3)
        assertThat(tools.map { it.function.name }).containsExactly(
            LocalToolLoop.TOOL_WEB_SEARCH,
            LocalToolLoop.TOOL_WEB_FETCH,
            LocalToolLoop.TOOL_READ_TEXT,
        ).inOrder()
    }

    @Test
    fun `remote read_text_file description matches the local constant`() {
        val entry = defaultRemoteTools().first { it.function.name == LocalToolLoop.TOOL_READ_TEXT }
        assertThat(entry.function.description).isEqualTo(READ_TEXT_TOOL_DESCRIPTION)
    }

    @Test
    fun `anthropic native tools include read_text_file with identical description`() {
        val tools = defaultAnthropicTools()
        assertThat(tools).hasSize(3)
        val entry = tools.first { it.name == LocalToolLoop.TOOL_READ_TEXT }
        assertThat(entry.description).isEqualTo(READ_TEXT_TOOL_DESCRIPTION)
    }

    @Test
    fun `allowlist round-trips the remote entry name`() {
        val remoteName = defaultRemoteTools()
            .first { it.function.name == LocalToolLoop.TOOL_READ_TEXT }
            .function.name
        assertThat(LocalToolLoop.mapToolCallName(remoteName))
            .isEqualTo(LocalToolLoop.TOOL_READ_TEXT)
        assertThat(LocalToolLoop.validateArgs(remoteName, mapOf("filename" to "notes.txt")))
            .isNull()
    }

    @Test
    fun `tools rejection still classifies a tools-naming four hundred`() {
        // Regression pin: a server rejecting the 3-tool list flows through
        // the existing exactly-one-retry fallback path.
        assertThat(
            ToolCapabilityMatrix.isToolsRejection(400, "Unsupported parameter: 'tools'"),
        ).isTrue()
        assertThat(
            ToolCapabilityMatrix.isToolsRejection(400, "Invalid request: bad model id"),
        ).isFalse()
    }
}
