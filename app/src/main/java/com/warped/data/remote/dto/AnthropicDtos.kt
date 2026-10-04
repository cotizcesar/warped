package com.warped.data.remote.dto

import com.warped.data.agentic.LocalToolLoop
import com.warped.data.agentic.READ_TEXT_FILENAME_DESCRIPTION
import com.warped.data.agentic.READ_TEXT_TOOL_DESCRIPTION
import com.warped.data.agentic.WEB_FETCH_TOOL_DESCRIPTION
import com.warped.data.agentic.WEB_FETCH_URL_DESCRIPTION
import com.warped.data.agentic.WEB_SEARCH_QUERY_DESCRIPTION
import com.warped.data.agentic.WEB_SEARCH_TOOL_DESCRIPTION
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class AnthropicChatRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val messages: List<AnthropicMessage>,
    val system: String? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val stream: Boolean = true,
    val temperature: Float? = null,
    @SerialName("top_p") val topP: Float? = null,
    @SerialName("top_k") val topK: Int? = null,
    val thinking: AnthropicThinking? = null,
    /**
     * Phase 57 (57-02): native Anthropic `tools` (name + description +
     * `input_schema` — no `strict` flag, no `tool_choice` equivalent ever
     * sent). `NEVER`-encoded so unarmed turns omit the key entirely and
     * stay byte-identical to the pre-57 request shape.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val tools: List<AnthropicTool>? = null
)

/**
 * Phase 57 (57-02): one native Anthropic tool entry. Schemas mirror the
 * 57-01 OpenAI `parameters` verbatim (single-required-string
 * `query`/`url`/`filename`, descriptions copied from the local `@Tool`
 * constants) — only the envelope key differs (`input_schema`, no
 * `type:"function"` wrapper).
 */
@Serializable
data class AnthropicTool(
    /** Exact snake_case contract: `web_search` | `web_fetch`. */
    val name: String,
    val description: String,
    /** `{type:object, properties:{…}, required:[…]}`. */
    @SerialName("input_schema") val inputSchema: JsonObject,
)

/**
 * Phase 57 (57-02): the three-tool `tools` list for an armed round, built
 * once per turn. Same provider-neutral surface as [defaultRemoteTools].
 * Phase 70 (70-02) appends the `read_text_file` entry (same description
 * constants, Anthropic schema shape).
 */
fun defaultAnthropicTools(): List<AnthropicTool> = listOf(
    AnthropicTool(
        name = LocalToolLoop.TOOL_WEB_SEARCH,
        description = WEB_SEARCH_TOOL_DESCRIPTION,
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("query") {
                    put("type", "string")
                    put("description", WEB_SEARCH_QUERY_DESCRIPTION)
                }
            }
            putJsonArray("required") { add(JsonPrimitive("query")) }
            put("additionalProperties", false)
        },
    ),
    AnthropicTool(
        name = LocalToolLoop.TOOL_WEB_FETCH,
        description = WEB_FETCH_TOOL_DESCRIPTION,
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("url") {
                    put("type", "string")
                    put("description", WEB_FETCH_URL_DESCRIPTION)
                }
            }
            putJsonArray("required") { add(JsonPrimitive("url")) }
            put("additionalProperties", false)
        },
    ),
    AnthropicTool(
        name = LocalToolLoop.TOOL_READ_TEXT,
        description = READ_TEXT_TOOL_DESCRIPTION,
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("filename") {
                    put("type", "string")
                    put("description", READ_TEXT_FILENAME_DESCRIPTION)
                }
            }
            putJsonArray("required") { add(JsonPrimitive("filename")) }
            put("additionalProperties", false)
        },
    ),
)

@Serializable
data class AnthropicThinking(
    val type: String,
    @SerialName("budget_tokens") val budgetTokens: Int
)

/**
 * Phase 57 (57-02): message content upgraded from `String` to
 * [JsonElement] so tool rounds can carry `tool_use`/`tool_result` content
 * blocks. Plain-text turns encode [JsonPrimitive] and serialize
 * byte-identically to the pre-57 string shape — use [text] for those.
 */
@Serializable
data class AnthropicMessage(
    val role: String,
    val content: JsonElement
) {
    companion object {
        /** Plain-text turn message — the exact pre-57 wire shape. */
        fun text(role: String, text: String): AnthropicMessage =
            AnthropicMessage(role, JsonPrimitive(text))

        /**
         * User turn carrying current-turn images as native `image` blocks
         * (`{type:image, source:{type:base64, media_type, data}}` per the
         * Anthropic-compat docs). Media type comes from the data-URL
         * prefix (default `image/jpeg`); blanks and prefix-less payloads
         * are dropped so the turn keeps its exact text shape.
         */
        fun userWithImages(text: String, dataUrls: List<String>): AnthropicMessage {
            val blocks = buildJsonArray {
                if (text.isNotEmpty()) {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", text)
                    })
                }
                dataUrls.forEach { url ->
                    val mediaType = url.substringAfter("data:", "")
                        .substringBefore(";").takeIf { it.contains("/") }
                        ?: "image/jpeg"
                    val data = url.substringAfter(",", "")
                    if (data.isNotBlank()) {
                        add(buildJsonObject {
                            put("type", "image")
                            putJsonObject("source") {
                                put("type", "base64")
                                put("media_type", mediaType)
                                put("data", data)
                            }
                        })
                    }
                }
            }
            return AnthropicMessage("user", blocks)
        }

        /**
         * Assistant echo carrying the completed `tool_use` blocks for one
         * round ([PendingToolCall.id]/name plus the complete reassembled
         * input object). In-memory loop echo only — never persisted.
         */
        fun toolUseEcho(
            textParts: List<String>,
            calls: List<com.warped.data.agentic.PendingToolCall>,
            json: kotlinx.serialization.json.Json,
        ): AnthropicMessage {
            val blocks = buildJsonArray {
                textParts.forEach { part ->
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", part)
                    })
                }
                calls.forEach { call ->
                    add(buildJsonObject {
                        put("type", "tool_use")
                        put("id", call.id)
                        put("name", call.name.orEmpty())
                        put("input", coerceInputObject(call.argumentsJson, json))
                    })
                }
            }
            return AnthropicMessage("assistant", blocks)
        }

        /**
         * User turn carrying one `tool_result` block per executed call
         * (fused-block content verbatim). In-memory only — never persisted.
         */
        fun toolResults(results: List<Pair<String, String>>): AnthropicMessage {
            val blocks = buildJsonArray {
                results.forEach { (toolUseId, content) ->
                    add(buildJsonObject {
                        put("type", "tool_result")
                        put("tool_use_id", toolUseId)
                        put("content", content)
                    })
                }
            }
            return AnthropicMessage("user", blocks)
        }

        /**
         * The `tool_use.input` must be a JSON object. Reassembled arguments
         * that fail to parse (or parse to a non-object) coerce to `{}` —
         * the call itself already short-circuited via `validateArgs`, so
         * the echo only needs to stay well-formed. Never throws.
         */
        private fun coerceInputObject(
            raw: String,
            json: kotlinx.serialization.json.Json,
        ): JsonElement = try {
            when (val element = json.parseToJsonElement(raw)) {
                is JsonObject -> element
                else -> buildJsonObject { }
            }
        } catch (_: Exception) {
            buildJsonObject { }
        }
    }
}

@Serializable
data class AnthropicSseEvent(
    val type: String = "",
    val delta: AnthropicDelta? = null,
    val message: AnthropicSseMessage? = null,
    val index: Int? = null,
    @SerialName("content_block") val contentBlock: AnthropicContentBlock? = null
)

@Serializable
data class AnthropicContentBlock(
    val type: String = "",
    val text: String? = null,
    val thinking: String? = null,
    val id: String? = null,
    val name: String? = null,
    val input: JsonElement? = null
)

@Serializable
data class AnthropicDelta(
    val type: String = "",
    val text: String? = null,
    val thinking: String? = null,
    @SerialName("partial_json") val partialJson: String? = null,
    @SerialName("stop_reason") val stopReason: String? = null
)

@Serializable
data class AnthropicSseMessage(
    val id: String = "",
    val model: String = "",
    val role: String = ""
)

/**
 * Phase 57 (57-02): non-streaming `POST /v1/messages` response shape
 * (Pitfall-3 parity with the OpenAI dialect: servers that ignore
 * `stream:true` return one JSON body). `content` reuses
 * [AnthropicContentBlock] (`text` blocks + `tool_use` blocks with
 * complete `input`); [stopReason] `"tool_use"` marks a tool round.
 */
@Serializable
data class AnthropicNonStreamingResponse(
    val content: List<AnthropicContentBlock> = emptyList(),
    @SerialName("stop_reason") val stopReason: String? = null,
)
