package com.warped.data.skills

import com.google.common.truth.Truth.assertThat
import com.warped.domain.skills.ToolResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

/**
 * 47-02 (HARD-02): CurrentTime valid + invalid-timezone classes →
 * error-mapped, never throws.
 */
class CurrentTimeSkillTest {

    @Test
    fun `null and blank use device timezone`() {
        val nullResult = assertDoesNotThrow { getCurrentTime(null) }
        assertThat(nullResult).isInstanceOf(ToolResult.Success::class.java)
        assertThat((nullResult as ToolResult.Success).text).isNotEmpty()

        val blankResult = assertDoesNotThrow { getCurrentTime("  ") }
        assertThat(blankResult).isInstanceOf(ToolResult.Success::class.java)
    }

    @Test
    fun `explicit timezone resolves`() {
        val result = assertDoesNotThrow { getCurrentTime("America/New_York") }
        assertThat(result).isInstanceOf(ToolResult.Success::class.java)
        val text = (result as ToolResult.Success).text
        assertThat(text).contains("America/New_York")
    }

    @Test
    fun `utc resolves`() {
        val result = assertDoesNotThrow { getCurrentTime("UTC") }
        assertThat(result).isInstanceOf(ToolResult.Success::class.java)
    }

    @Test
    fun `unknown timezone is a failure`() {
        val result = assertDoesNotThrow { getCurrentTime("Mars/Olympus") }
        assertThat(result).isInstanceOf(ToolResult.Failure::class.java)
        assertThat((result as ToolResult.Failure).reason).isEqualTo("unknown timezone")
    }

    @Test
    fun `oversize timezone is a failure`() {
        val result = assertDoesNotThrow { getCurrentTime("X".repeat(101)) }
        assertThat(result).isInstanceOf(ToolResult.Failure::class.java)
        assertThat((result as ToolResult.Failure).reason).isEqualTo("unknown timezone")
    }

    @Test
    fun `injection-looking timezone is a failure`() {
        val result = assertDoesNotThrow { getCurrentTime("../../etc/passwd") }
        assertThat(result).isInstanceOf(ToolResult.Failure::class.java)
    }
}
