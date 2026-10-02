package com.warped.data.agentic

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Phase 70 (70-01): exit gates for the `read_text_file` loop policy.
 *
 * JVM-only (no `litertlm` imports — `LocalToolLoop` only, 47 `ToolGating`
 * precedent): exact-name dispatch, filename validation, filename-only
 * status copy, verbatim outcome mapping, and the three-tool error copy.
 */
class DocumentToolTest {

    // Exact-name dispatch (AGENT-04, ASVS V4).

    @Test
    fun `read_text_file dispatches by exact name and unknown still rejected`() {
        assertThat(LocalToolLoop.mapToolCallName("read_text_file"))
            .isEqualTo("read_text_file")
        // Existing tools unaffected.
        assertThat(LocalToolLoop.mapToolCallName("web_search")).isEqualTo("web_search")
        assertThat(LocalToolLoop.mapToolCallName("web_fetch")).isEqualTo("web_fetch")
        // Near-miss casing never dispatches.
        assertThat(LocalToolLoop.mapToolCallName("Read_Text_File")).isNull()
        assertThat(LocalToolLoop.mapToolCallName("read-text-file")).isNull()
        assertThat(LocalToolLoop.mapToolCallName("shell_exec")).isNull()
    }

    // Filename validation (pre-execution, fail-closed).

    @Test
    fun `valid filename passes validation`() {
        assertThat(
            LocalToolLoop.validateArgs("read_text_file", mapOf("filename" to "notes.txt")),
        ).isNull()
    }

    @Test
    fun `blank filename degrades without executing`() {
        assertThat(
            LocalToolLoop.validateArgs("read_text_file", mapOf("filename" to "   ")),
        ).isEqualTo(LocalToolLoop.MODEL_ONLY_STRING)
        assertThat(LocalToolLoop.validateArgs("read_text_file", emptyMap()))
            .isEqualTo(LocalToolLoop.MODEL_ONLY_STRING)
        // Non-String values fail closed (0.17.1 nullable-optional precedent).
        assertThat(LocalToolLoop.validateArgs("read_text_file", mapOf("filename" to 42)))
            .isEqualTo(LocalToolLoop.MODEL_ONLY_STRING)
        assertThat(LocalToolLoop.validateArgs("read_text_file", mapOf("filename" to null)))
            .isEqualTo(LocalToolLoop.MODEL_ONLY_STRING)
    }

    // Status copy: filename only, never content.

    @Test
    fun `status shows filename and never document content`() {
        val display = LocalToolLoop.statusDisplay(
            "read_text_file",
            mapOf("filename" to "notes.txt"),
        )
        assertThat(display).contains("notes.txt")
        assertThat(display).doesNotContain("secret document body")
    }

    @Test
    fun `status falls back to bare verb on blank filename`() {
        assertThat(
            LocalToolLoop.statusDisplay("read_text_file", mapOf("filename" to "  ")),
        ).isEqualTo("Reading document…")
    }

    // Outcome mapping: sanitized block passes through verbatim.

    @Test
    fun `document result passes block verbatim`() {
        val block = "--- Document: notes.txt ---\nbounded text\n--- End of document ---"
        assertThat(LocalToolLoop.mapDocumentResult(block)).isEqualTo(block)
    }

    // Unknown-tool copy names all three tools.

    @Test
    fun `unknown tool message lists all three tools`() {
        val err = LocalToolLoop.unknownToolMessage("shell_exec")
        assertThat(err).contains("web_search")
        assertThat(err).contains("web_fetch")
        assertThat(err).contains("read_text_file")
    }

    // Degradation string for the unbound-attachment executor path.

    @Test
    fun `document read failure string degrades gracefully`() {
        assertThat(LocalToolLoop.DOCUMENT_READ_FAILED_STRING).isNotEmpty()
        assertThat(LocalToolLoop.DOCUMENT_READ_FAILED_STRING).contains("without it")
    }
}
