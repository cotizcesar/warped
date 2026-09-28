package com.warped.data.skills

import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import com.warped.domain.skills.SkillIds
import com.warped.domain.skills.ToolEventSink
import com.warped.domain.skills.ToolResult

/**
 * 47-02 (HARD-02, T-47-05): Calculator pure tool body.
 *
 * [calculateExpression] is a PURE SYNC function that NEVER throws out —
 * every invalid class maps to [ToolResult.Failure] with a short sanitized
 * reason (never echoes raw input, no stack traces — T-47-08). The `@Tool`
 * ToolSet wrapper lands in Task 2 and delegates here.
 */
const val CALCULATOR_MAX_CHARS = 200

private val CALC_ALLOWED = Regex("^[0-9+\\-*/().\\s]+$")
fun calculateExpression(expression: String): ToolResult {
    return try {
        if (expression.isBlank()) return ToolResult.Failure("empty expression")
        if (expression.length > CALCULATOR_MAX_CHARS) {
            return ToolResult.Failure("expression too long")
        }
        if (!CALC_ALLOWED.matches(expression)) {
            return ToolResult.Failure("invalid expression")
        }
        val value = try {
            ExpressionEvaluator.evaluate(expression)
        } catch (e: ArithmeticException) {
            // Division by zero / overflow from the evaluator.
            val msg = e.message.orEmpty()
            return if (msg.contains("zero", ignoreCase = true)) {
                ToolResult.Failure("division by zero")
            } else {
                ToolResult.Failure("result out of range")
            }
        } catch (e: IllegalArgumentException) {
            return ToolResult.Failure("invalid expression")
        }
        if (!value.isFinite()) return ToolResult.Failure("result out of range")
        val text = ExpressionEvaluator.formatResult(value)
        ToolResult.Success(text = text, summary = summarizeToolOutput(text))
    } catch (e: Exception) {
        // Belt-and-braces: the trust boundary guarantees no throw-out.
        ToolResult.Failure("invalid expression")
    }
}

/**
 * 47-02 (D-04): Calculator `@Tool` via [ToolSet]. The `@Tool`/`@ToolParam`
 * descriptions import the Plan 01 descriptor constants verbatim (SKILLS-09
 * no-drift, enforced by reflection test).
 *
 * The body wraps the pure [calculateExpression] with [ToolEventSink]
 * start/finish posts in try/finally (automatic mode emits no engine events —
 * RESEARCH Pitfall 4). Belt-and-braces catch-all returns an "Error: …"
 * string — never throws across JNI or the conversation dies.
 */
class CalculatorToolSet(private val events: ToolEventSink) : ToolSet {
    @Tool(description = CALC_TOOL_DESCRIPTION)
    fun calculator(
        @ToolParam(description = CALC_EXPRESSION_DESCRIPTION) expression: String,
    ): String {
        events.onStart(SkillIds.CALCULATOR)
        return try {
            when (val r = calculateExpression(expression)) {
                is ToolResult.Success -> sanitizeToolOutput(r.text)
                is ToolResult.Failure -> "Error: ${r.reason}"
            }
        } catch (e: Exception) {
            "Error: evaluation failed"
        } finally {
            events.onFinish(SkillIds.CALCULATOR)
        }
    }
}
