package com.warped.data.local.inference.tools

import com.google.ai.edge.litertlm.OpenApiTool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolRegistry @Inject constructor(
    private val preferences: ToolPreferences,
    private val executors: ToolExecutors
) {
    val enabledToolIds: Flow<Set<String>> = preferences.enabledTools

    fun buildOpenApiTools(enabledIds: Set<String>): List<OpenApiTool> {
        return ToolDefinitions.all
            .filter { it.id in enabledIds }
            .map { definition: ToolDefinition ->
                object : OpenApiTool {
                    override fun getToolDescriptionJsonString(): String = definition.openApiSchema.trimIndent()

                    override fun execute(paramsJsonString: String): String {
                        val params = parseJson(paramsJsonString)
                        return executors.execute(definition.id, params)
                    }
                }
            }
    }

    val enabledTokenCount: Flow<Int> = preferences.enabledTools.map { ids ->
        ToolDefinitions.all.filter { it.id in ids }.sumOf { it.tokenEstimate }
    }

    val totalTokenCount: Int = ToolDefinitions.totalTokens

    private fun parseJson(json: String): Map<String, Any?> {
        if (json.isBlank()) return emptyMap()
        return try {
            // Simple JSON parser for flat objects
            val trimmed = json.trim().removeSurrounding("{", "}").trim()
            if (trimmed.isEmpty()) return emptyMap()
            val map = mutableMapOf<String, Any?>()
            // Very basic parsing: split by commas outside of strings
            val pairs = splitJsonPairs(trimmed)
            for (pair in pairs) {
                val colonIdx = pair.indexOf(':')
                if (colonIdx < 0) continue
                val key = pair.substring(0, colonIdx).trim().removeSurrounding("\"")
                val value = pair.substring(colonIdx + 1).trim()
                map[key] = parseJsonValue(value)
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun splitJsonPairs(json: String): List<String> {
        val pairs = mutableListOf<String>()
        var depth = 0
        var inString = false
        var start = 0
        for (i in json.indices) {
            val c = json[i]
            if (c == '"' && (i == 0 || json[i-1] != '\\')) inString = !inString
            if (inString) continue
            when (c) {
                '{', '[' -> depth++
                '}', ']' -> depth--
                ',' -> if (depth == 0) {
                    pairs.add(json.substring(start, i))
                    start = i + 1
                }
            }
        }
        if (start < json.length) pairs.add(json.substring(start))
        return pairs
    }

    private fun parseJsonValue(value: String): Any? {
        val v = value.trim()
        return when {
            v == "null" -> null
            v == "true" -> true
            v == "false" -> false
            v.startsWith("\"") -> v.removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")
            v.contains(".") -> v.toDoubleOrNull() ?: v
            else -> v.toIntOrNull() ?: v.toLongOrNull() ?: v
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun runBlockingOnIo(block: suspend () -> String): String {
        return kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { block() }
    }
}
