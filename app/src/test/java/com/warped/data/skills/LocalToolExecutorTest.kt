package com.warped.data.skills

import com.google.common.truth.Truth.assertThat
import com.warped.domain.skills.ToolResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * 47-02 (HARD-02, T-47-06/T-47-07): LocalToolExecutor allowlist dispatch,
 * schema validation, truncation/sanitization — never throws, never executes
 * unknown tools.
 */
class LocalToolExecutorTest {

    private val executor = LocalToolExecutor()

    private suspend fun successOf(name: String, argsJson: String): ToolResult.Success {
        val result = executor.execute(name, argsJson)
        assertThat(result).isInstanceOf(ToolResult.Success::class.java)
        return result as ToolResult.Success
    }

    private suspend fun failureOf(name: String, argsJson: String): String {
        val result = executor.execute(name, argsJson)
        assertThat(result).isInstanceOf(ToolResult.Failure::class.java)
        return (result as ToolResult.Failure).reason
    }

    @Test
    fun `calculator dispatches and computes`() = runTest {
        val result = successOf("calculator", """{"expression":"(2+3)*4"}""")
        assertThat(result.text).isEqualTo("20")
    }

    @Test
    fun `current_time dispatches with optional timezone absent`() = runTest {
        val result = successOf("current_time", """{}""")
        assertThat(result.text).isNotEmpty()
    }

    @Test
    fun `json_formatter dispatches`() = runTest {
        val result = successOf("json_formatter", """{"json":"{\"a\":1}"}""")
        assertThat(result.text).contains("\"a\": 1")
    }

    @Test
    fun `unknown tool name never executes`() = runTest {
        assertThat(failureOf("rm_rf", """{}""")).isEqualTo("unknown tool")
        assertThat(failureOf("Calculator", """{"expression":"1+1"}""")).isEqualTo("unknown tool")
        assertThat(failureOf("", """{}""")).isEqualTo("unknown tool")
    }

    @Test
    fun `malformed args JSON is a failure`() = runTest {
        assertThat(failureOf("calculator", "{bad")).isEqualTo("invalid arguments")
        assertThat(failureOf("calculator", "[1,2]")).isEqualTo("invalid arguments")
        assertThat(failureOf("calculator", "\"str\"")).isEqualTo("invalid arguments")
    }

    @Test
    fun `missing required param is a failure`() = runTest {
        assertThat(failureOf("calculator", """{}""")).isEqualTo("missing expression")
        assertThat(failureOf("json_formatter", """{}""")).isEqualTo("missing json")
    }

    @Test
    fun `wrong-typed param is a failure`() = runTest {
        assertThat(failureOf("calculator", """{"expression":42}""")).isEqualTo("invalid expression")
        assertThat(failureOf("calculator", """{"expression":null}""")).isEqualTo("invalid expression")
    }

    @Test
    fun `oversize param is a failure`() = runTest {
        val big = "1".repeat(201)
        assertThat(failureOf("calculator", """{"expression":"$big"}"""))
            .isEqualTo("expression too long")
    }

    @Test
    fun `tool-level failure passes through error-mapped`() = runTest {
        assertThat(failureOf("calculator", """{"expression":"1/0"}"""))
            .isEqualTo("division by zero")
    }

    @Test
    fun `success summary is truncated to transcript cap`() = runTest {
        // Large-but-valid JSON: pretty output must be capped, summary ≤ ~201 chars.
        val bigArray = (1..500).joinToString(",", "[", "]")
        val args = """{"json":"${bigArray.replace("\"", "\\\"")}"}"""
        val result = successOf("json_formatter", args)
        assertThat(result.summary.length).isAtMost(TOOL_SUMMARY_MAX_CHARS + 1)
        assertThat(result.text.length).isAtMost(TOOL_TEXT_MAX_CHARS + 1)
    }

    @Test
    fun `control chars are stripped from results`() = runTest {
        val args = """{"json":"{\"a\":\"hi\\u0000there\"}"}"""
        val result = successOf("json_formatter", args)
        assertThat(result.text).doesNotContain("\u0000")
    }
}
