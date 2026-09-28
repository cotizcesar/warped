package com.warped.data.skills

import com.google.common.truth.Truth.assertThat
import com.warped.domain.skills.ToolResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

/**
 * 47-02 (HARD-02): JsonFormatter valid/invalid/size-cap classes →
 * error-mapped, never throws.
 */
class JsonFormatterSkillTest {

    @Test
    fun `valid JSON pretty-prints`() {
        val result = assertDoesNotThrow { formatJson("""{"b":2,"a":1}""") }
        assertThat(result).isInstanceOf(ToolResult.Success::class.java)
        val text = (result as ToolResult.Success).text
        assertThat(text).contains("\"a\": 1")
        assertThat(text).contains("\"b\": 2")
    }

    @Test
    fun `arrays and nesting format`() {
        val result = assertDoesNotThrow { formatJson("""[1,{"x":[true,null]}]""") }
        assertThat(result).isInstanceOf(ToolResult.Success::class.java)
        assertThat((result as ToolResult.Success).text).contains("\"x\"")
    }

    @Test
    fun `malformed JSON is a failure`() {
        val bad = listOf("{bad", "[1,2", "not json", "{'single':1}", "")
        for (input in bad) {
            val result = assertDoesNotThrow { formatJson(input) }
            assertThat(result).isInstanceOf(ToolResult.Failure::class.java)
        }
    }

    @Test
    fun `blank is empty failure`() {
        val result = assertDoesNotThrow { formatJson("   ") }
        assertThat(result).isInstanceOf(ToolResult.Failure::class.java)
        assertThat((result as ToolResult.Failure).reason).isEqualTo("empty JSON")
    }

    @Test
    fun `oversize JSON is a failure`() {
        val result = assertDoesNotThrow { formatJson("x".repeat(JSON_MAX_CHARS + 1)) }
        assertThat(result).isInstanceOf(ToolResult.Failure::class.java)
        assertThat((result as ToolResult.Failure).reason).isEqualTo("JSON too large")
    }
}
