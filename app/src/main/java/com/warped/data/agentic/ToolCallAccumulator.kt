package com.warped.data.agentic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Phase 57 (57-01): a completed `tool_calls` entry reassembled from Chat
 * Completions SSE deltas.
 *
 * [argumentsJson] is the concatenated raw arguments string across every
 * fragment seen for the index (NOT parsed — the consumer parses it via
 * [parseToolArgs], which never throws). [name] is null when no fragment
 * carried a function name (the driver fails the call closed via
 * `validateArgs`, never executes blind).
 */
data class PendingToolCall(
    val id: String,
    val name: String?,
    val argumentsJson: String,
)

/**
 * Phase 57 (57-01): pure index-keyed SSE `tool_calls` arguments reassembly
 * (AGENT-03, RESEARCH Pattern 2).
 *
 * Streaming chunks carry `choices[0].delta.tool_calls` entries whose
 * `function.arguments` is a JSON **string fragment**; chunk boundaries are
 * transport-level and may split mid-unicode-escape. Fragments accumulate
 * per `index` in a `StringBuilder` and are only interpreted after the round
 * completes (`finish_reason:"tool_calls"`, or `[DONE]` after partials).
 *
 * JVM-testable by design — zero network imports (47 `ToolGating` / 56-01
 * precedent). Every function is total (never throws).
 *
 * Rules: first-seen `id`/`name` win per index (continuation chunks often
 * repeat neither); a missing `id` synthesizes `call_<index>`; null/empty
 * fragments are ignored; [complete] returns calls in ascending index order.
 */
class ToolCallAccumulator {

    private data class Slot(
        var id: String? = null,
        var name: String? = null,
        val args: StringBuilder = StringBuilder(),
    )

    private val slots = mutableMapOf<Int, Slot>()

    /** Feeds one SSE `tool_calls` entry. Never throws. */
    fun feed(index: Int, id: String?, name: String?, argumentsFragment: String?) {
        val slot = slots.getOrPut(index) { Slot() }
        if (slot.id == null && !id.isNullOrEmpty()) slot.id = id
        if (slot.name == null && !name.isNullOrEmpty()) slot.name = name
        if (!argumentsFragment.isNullOrEmpty()) slot.args.append(argumentsFragment)
    }

    /** True once at least one index has been seen (partial or whole). */
    fun hasCalls(): Boolean = slots.isNotEmpty()

    /**
     * Reassembled calls in ascending index order. The arguments strings are
     * raw — parse them with [parseToolArgs] (never throws; unparseable input
     * degrades to `LocalToolLoop.toolFailureMessage` at the call site).
     * Never throws.
     */
    fun complete(): List<PendingToolCall> =
        slots.entries.sortedBy { it.key }.map { (index, slot) ->
            PendingToolCall(
                id = slot.id ?: "call_$index",
                name = slot.name,
                argumentsJson = slot.args.toString(),
            )
        }

    /** Clears all slots (fresh accumulator per round; never reuse across rounds). */
    fun reset() {
        slots.clear()
    }
}

private val toolArgsJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Phase 57 (57-01): parses a reassembled tool-arguments string into the
 * `Map<String, Any?>` shape `LocalToolLoop.validateArgs`/`statusDisplay`
 * consume. Total — never throws:
 *
 * - blank input → empty map (the call site short-circuits via
 *   `validateArgs`: `MODEL_ONLY_STRING` / invalid-URL copy, no socket);
 * - valid JSON object → shallow map (primitives converted, nested
 *   structures carried as their JSON text — `validateArgs` only reads
 *   top-level `query`/`url` strings and fails closed on anything else);
 * - anything else (garbage, arrays, scalars) → null (the call site feeds
 *   `LocalToolLoop.toolFailureMessage(raw)` back, never executes).
 */
fun parseToolArgs(raw: String): Map<String, Any?>? {
    if (raw.isBlank()) return emptyMap()
    return try {
        val element = toolArgsJson.parseToJsonElement(raw)
        val obj = element as? JsonObject ?: return null
        obj.mapValues { (_, value) -> jsonElementToAny(value) }
    } catch (_: Exception) {
        null
    }
}

private fun jsonElementToAny(element: JsonElement): Any? = when (element) {
    is JsonNull -> null
    is JsonPrimitive -> element.booleanOrNull
        ?: element.longOrNull
        ?: element.doubleOrNull
        ?: element.content
    is JsonObject -> element.toString()
    is JsonArray -> element.toString()
}
