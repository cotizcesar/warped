package com.warped.data.local.inference

import android.content.Context
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class DeviceToolSet @Inject constructor(
    @ApplicationContext private val context: Context
) : com.google.ai.edge.litertlm.ToolSet {

    @Tool(description = "Get the current date and time")
    fun getCurrentTime(
        @ToolParam(description = "Format: 'time' for time only, 'date' for date only, 'full' for both") format: String = "full"
    ): Map<String, String> {
        val now = Date()
        return when (format.lowercase()) {
            "time" -> mapOf("time" to SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now))
            "date" -> mapOf("date" to SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now))
            else -> mapOf(
                "date" to SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now),
                "time" to SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now),
                "timezone" to java.util.TimeZone.getDefault().id
            )
        }
    }

    @Tool(description = "Calculate a mathematical expression. Supports +, -, *, /, %, ^, sqrt, sin, cos, tan, log, abs, floor, ceil, round.")
    fun calculate(
        @ToolParam(description = "The mathematical expression to evaluate, e.g., '2 + 3 * 4' or 'sqrt(16)'") expression: String
    ): Map<String, Any> {
        return try {
            val result = evaluateExpression(expression)
            mapOf("expression" to expression, "result" to result)
        } catch (e: Exception) {
            Timber.w(e, "Tool: calculation failed for '$expression'")
            mapOf("expression" to expression, "error" to (e.message ?: "Unknown error"))
        }
    }

    @Tool(description = "Get information about the device and app capabilities")
    fun getDeviceInfo(): Map<String, String> {
        return mapOf(
            "platform" to "Android",
            "osVersion" to android.os.Build.VERSION.RELEASE,
            "device" to android.os.Build.MODEL,
            "manufacturer" to android.os.Build.MANUFACTURER,
            "app" to "Warped",
            "capabilities" to "text generation, image input, audio input, tool calling"
        )
    }

    private fun evaluateExpression(expr: String): Double {
        val sanitized = expr.trim()
            .replace("^", "@")
            .replace(" ", "")

        if (sanitized.isEmpty()) throw IllegalArgumentException("Empty expression")
        return evaluateSimple(sanitized)
    }

    private fun evaluateSimple(expr: String): Double {
        if (expr == "pi" || expr == "PI" || expr == "\u03C0") return Math.PI
        if (expr == "e" || expr == "E") return Math.E

        expr.toDoubleOrNull()?.let { return it }

        val funcMatch = Regex("^(sqrt|sin|cos|tan|log|abs|floor|ceil|round)\\((.+)\\)$").find(expr)
        if (funcMatch != null) {
            val inner = evaluateSimple(funcMatch.groupValues[2])
            return when (funcMatch.groupValues[1]) {
                "sqrt" -> Math.sqrt(inner)
                "sin" -> Math.sin(Math.toRadians(inner))
                "cos" -> Math.cos(Math.toRadians(inner))
                "tan" -> Math.tan(Math.toRadians(inner))
                "log" -> Math.log10(inner)
                "abs" -> Math.abs(inner)
                "floor" -> Math.floor(inner)
                "ceil" -> Math.ceil(inner)
                "round" -> Math.round(inner).toDouble()
                else -> throw IllegalArgumentException("Unknown function: ${funcMatch.groupValues[1]}")
            }
        }

        var depth = 0
        var opIndex = -1
        var opChar = ' '
        var opPriority = Int.MAX_VALUE

        for (i in expr.indices) {
            when (expr[i]) {
                '(' -> depth++
                ')' -> depth--
                '+', '-' -> {
                    if (depth == 0) {
                        if (expr[i] == '-' && i == 0) continue
                        if (i > 0 && expr[i - 1] in "+-*/%@") continue
                        if (1 < opPriority) { opIndex = i; opChar = expr[i]; opPriority = 1 }
                    }
                }
                '*', '/', '%' -> {
                    if (depth == 0 && 2 < opPriority) { opIndex = i; opChar = expr[i]; opPriority = 2 }
                }
                '@' -> {
                    if (depth == 0 && 3 < opPriority) { opIndex = i; opChar = expr[i]; opPriority = 3 }
                }
            }
        }

        if (opIndex >= 0) {
            val left = evaluateSimple(expr.substring(0, opIndex))
            val right = evaluateSimple(expr.substring(opIndex + 1))
            return when (opChar) {
                '+' -> left + right
                '-' -> left - right
                '*' -> left * right
                '/' -> left / right
                '%' -> left % right
                '@' -> Math.pow(left, right)
                else -> throw IllegalArgumentException("Unknown operator: $opChar")
            }
        }

        if (expr.startsWith("(") && expr.endsWith(")")) {
            return evaluateSimple(expr.substring(1, expr.length - 1))
        }

        if (expr.startsWith("-")) {
            return -evaluateSimple(expr.substring(1))
        }

        throw IllegalArgumentException("Cannot parse: $expr")
    }
}
