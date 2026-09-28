package com.warped.data.skills

import com.google.common.truth.Truth.assertThat
import com.warped.domain.skills.ToolResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

/**
 * 47-02 (HARD-02): Calculator valid compute + EVERY invalid class →
 * error-mapped, never throws.
 */
class CalculatorSkillTest {

    private fun successOf(input: String): String {
        val result = assertDoesNotThrow { calculateExpression(input) }
        assertThat(result).isInstanceOf(ToolResult.Success::class.java)
        return (result as ToolResult.Success).text
    }

    private fun failureOf(input: String): String {
        val result = assertDoesNotThrow { calculateExpression(input) }
        assertThat(result).isInstanceOf(ToolResult.Failure::class.java)
        return (result as ToolResult.Failure).reason
    }

    @Test
    fun `valid expressions compute`() {
        assertThat(successOf("(2+3)*4")).isEqualTo("20")
        assertThat(successOf("2 + 2")).isEqualTo("4")
        assertThat(successOf("10/4")).isEqualTo("2.5")
        assertThat(successOf("-5+3")).isEqualTo("-2")
        assertThat(successOf("2.5*2")).isEqualTo("5")
        assertThat(successOf("2*(3+4)/2")).isEqualTo("7")
        assertThat(successOf("((2+3)*4)-1")).isEqualTo("19")
        assertThat(successOf("7/2")).isEqualTo("3.5")
    }

    @Test
    fun `empty and blank are failures`() {
        assertThat(failureOf("")).isEqualTo("empty expression")
        assertThat(failureOf("   ")).isEqualTo("empty expression")
    }

    @Test
    fun `oversize input is a failure`() {
        assertThat(failureOf("1+".plus("1".repeat(200)))).isEqualTo("expression too long")
    }

    @Test
    fun `charset violations are failures`() {
        assertThat(failureOf("2+foo")).isEqualTo("invalid expression")
        assertThat(failureOf("2+2;")).isEqualTo("invalid expression")
        assertThat(failureOf("__import__")).isEqualTo("invalid expression")
    }

    @Test
    fun `malformed expressions are failures`() {
        assertThat(failureOf("(2+3")).isEqualTo("invalid expression")
        assertThat(failureOf("2++3")).isEqualTo("invalid expression")
        assertThat(failureOf("()")).isEqualTo("invalid expression")
        assertThat(failureOf("2 2")).isEqualTo("invalid expression")
    }

    @Test
    fun `division by zero is a failure`() {
        assertThat(failureOf("1/0")).isEqualTo("division by zero")
        assertThat(failureOf("5/(3-3)")).isEqualTo("division by zero")
    }

    @Test
    fun `evaluator-level overflow throws inside, never out of tool body`() {
        // Unreachable through calculateExpression (200-char cap bounds
        // products below 1e308), but the checked() guard is covered here.
        val huge = "9".repeat(200)
        org.junit.jupiter.api.assertThrows<ArithmeticException> {
            ExpressionEvaluator.evaluate("$huge*$huge")
        }
        // And the tool body still never throws, even at the cap edge.
        val capped = assertDoesNotThrow { calculateExpression("9".repeat(200)) }
        assertThat(capped).isInstanceOf(ToolResult.Success::class.java)
    }

    @Test
    fun `failure reasons never echo raw input`() {
        val evil = "2+<script>alert(1)</script>"
        val reason = failureOf(evil)
        assertThat(reason).doesNotContain("<script>")
    }
}
