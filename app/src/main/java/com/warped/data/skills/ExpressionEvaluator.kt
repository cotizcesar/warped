package com.warped.data.skills

import kotlin.math.abs

/**
 * 47-02 (HARD-02, T-47-05): hand-rolled arithmetic evaluator for the
 * Calculator tool. Zero-new-dep constraint forbids exp4j-style libraries,
 * so this recursive-descent parser (~100 lines) lives in-tree.
 *
 * Contract: [evaluate] throws [IllegalArgumentException] on malformed input
 * and [ArithmeticException] on division by zero. Callers ([calculateExpression])
 * map those to sanitized [com.warped.domain.skills.ToolResult.Failure] reasons —
 * this object itself is never called from UI/engine threads directly.
 *
 * DoS guards: input length is capped by the caller (200 chars), recursion
 * depth is bounded ([MAX_DEPTH]), and every intermediate result is
 * finite-checked so pathological input cannot hang or overflow silently.
 */
internal object ExpressionEvaluator {

    private const val MAX_DEPTH = 64

    fun evaluate(input: String): Double {
        val parser = Parser(input)
        val result = parser.parseExpression(0)
        parser.skipWhitespace()
        if (!parser.atEnd) throw IllegalArgumentException("trailing characters")
        return result
    }

    /** Formats a finite result: integers without a decimal point. */
    fun formatResult(value: Double): String {
        require(value.isFinite()) { "non-finite result" }
        return if (value % 1.0 == 0.0 && abs(value) < 1e15) {
            value.toLong().toString()
        } else {
            value.toString()
        }
    }

    private class Parser(val s: String) {
        var pos: Int = 0

        val atEnd: Boolean
            get() {
                skipWhitespace()
                return pos >= s.length
            }

        fun skipWhitespace() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        // expr := term (('+' | '-') term)*
        fun parseExpression(depth: Int): Double {
            if (depth > MAX_DEPTH) throw IllegalArgumentException("nesting too deep")
            var value = parseTerm(depth + 1)
            while (true) {
                skipWhitespace()
                when (s.getOrNull(pos)) {
                    '+' -> {
                        pos++
                        value = checked(value + parseTerm(depth + 1))
                    }
                    '-' -> {
                        pos++
                        value = checked(value - parseTerm(depth + 1))
                    }
                    else -> return value
                }
            }
        }

        // term := factor (('*' | '/') factor)*
        private fun parseTerm(depth: Int): Double {
            if (depth > MAX_DEPTH) throw IllegalArgumentException("nesting too deep")
            var value = parseFactor(depth + 1)
            while (true) {
                skipWhitespace()
                when (s.getOrNull(pos)) {
                    '*' -> {
                        pos++
                        value = checked(value * parseFactor(depth + 1))
                    }
                    '/' -> {
                        pos++
                        val rhs = parseFactor(depth + 1)
                        if (rhs == 0.0) throw ArithmeticException("Division by zero")
                        value = checked(value / rhs)
                    }
                    else -> return value
                }
            }
        }

        // factor := '-' factor | '(' expr ')' | number
        private fun parseFactor(depth: Int): Double {
            if (depth > MAX_DEPTH) throw IllegalArgumentException("nesting too deep")
            skipWhitespace()
            when (s.getOrNull(pos)) {
                '-' -> {
                    pos++
                    return -parseFactor(depth + 1)
                }
                '(' -> {
                    pos++
                    val value = parseExpression(depth + 1)
                    skipWhitespace()
                    if (s.getOrNull(pos) != ')') {
                        throw IllegalArgumentException("unbalanced parenthesis")
                    }
                    pos++
                    return value
                }
            }
            return parseNumber()
        }

        // number := digits ['.' digits]
        private fun parseNumber(): Double {
            skipWhitespace()
            val start = pos
            var seenDigit = false
            var seenDot = false
            while (pos < s.length) {
                val c = s[pos]
                when {
                    c.isDigit() -> {
                        seenDigit = true
                        pos++
                    }
                    c == '.' && !seenDot -> {
                        seenDot = true
                        pos++
                    }
                    else -> break
                }
            }
            if (!seenDigit) throw IllegalArgumentException("expected number at offset $pos")
            return s.substring(start, pos).toDoubleOrNull()
                ?: throw IllegalArgumentException("malformed number")
        }

        private fun checked(value: Double): Double {
            if (!value.isFinite()) throw ArithmeticException("Overflow")
            return value
        }
    }
}
