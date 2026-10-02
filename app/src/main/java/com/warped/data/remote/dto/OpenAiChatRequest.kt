package com.warped.data.remote.dto

import com.warped.data.agentic.LocalToolLoop
import com.warped.data.agentic.READ_TEXT_FILENAME_DESCRIPTION
import com.warped.data.agentic.READ_TEXT_TOOL_DESCRIPTION
import com.warped.data.agentic.WEB_FETCH_TOOL_DESCRIPTION
import com.warped.data.agentic.WEB_FETCH_URL_DESCRIPTION
import com.warped.data.agentic.WEB_SEARCH_QUERY_DESCRIPTION
import com.warped.data.agentic.WEB_SEARCH_TOOL_DESCRIPTION
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.jsonObject

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val stream: Boolean = true,
    val temperature: Float? = null,
    @SerialName("top_p") val topP: Float? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val stop: List<String>? = null,
    val seed: Int? = null,
    @SerialName("presence_penalty") val presencePenalty: Float? = null,
    @SerialName("frequency_penalty") val frequencyPenalty: Float? = null,
    @SerialName("response_format") val responseFormat: OpenAiResponseFormat? = null,
    /**
     * Phase 57 (57-01): Chat Completions `tools[]` (loose schemas — NO
     * `strict` flag, locked). `NEVER`-encoded so the plain path omits the
     * key entirely (unarmed turns stay byte-identical on the wire); the
     * retry-without-tools path replays with null (same omission). Never
     * `tool_choice` — rely on default auto behavior (Ollama lists it as
     * unsupported).
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val tools: List<OpenAiTool>? = null
)

/**
 * Phase 57 (57-01): one `tools[]` entry — `type:"function"` wrapping the
 * provider-neutral `web_search` / `web_fetch` / `read_text_file` schemas
 * (descriptions copied verbatim from the local `@Tool` constants, locked
 * identical surface).
 */
@Serializable
data class OpenAiTool(
    val type: String = "function",
    val function: OpenAiFunctionDef,
)

/** Phase 57 (57-01): the `function` half of an [OpenAiTool] entry. */
@Serializable
data class OpenAiFunctionDef(
    /** Exact snake_case contract: `web_search` | `web_fetch`. */
    val name: String,
    val description: String,
    /** `{type:object, properties:{…}, required:[…], additionalProperties:false}`. */
    val parameters: JsonObject,
)

/**
 * Phase 57 (57-01): the three-tool `tools[]` list for an armed round, built
 * once per turn. Single-required-string params (`query` / `url` /
 * `filename`) — strict buys nothing, loose maximizes compat-server
 * acceptance. Phase 70 (70-02) appends the `read_text_file` entry reusing
 * the local schema constants (same loose shape as `web_fetch`).
 */
fun defaultRemoteTools(): List<OpenAiTool> = listOf(
    OpenAiTool(
        function = OpenAiFunctionDef(
            name = LocalToolLoop.TOOL_WEB_SEARCH,
            description = WEB_SEARCH_TOOL_DESCRIPTION,
            parameters = buildJsonObject {
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
    ),
    OpenAiTool(
        function = OpenAiFunctionDef(
            name = LocalToolLoop.TOOL_WEB_FETCH,
            description = WEB_FETCH_TOOL_DESCRIPTION,
            parameters = buildJsonObject {
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
    ),
    OpenAiTool(
        function = OpenAiFunctionDef(
            name = LocalToolLoop.TOOL_READ_TEXT,
            description = READ_TEXT_TOOL_DESCRIPTION,
            parameters = buildJsonObject {
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
    ),
)

@Serializable
data class OpenAiResponseFormat(
    val type: String = "json_schema",
    @SerialName("json_schema") val jsonSchema: OpenAiJsonSchema? = null
)

@Serializable
data class OpenAiJsonSchema(
    val name: String,
    val strict: Boolean = true,
    val schema: kotlinx.serialization.json.JsonObject? = null
)

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable(with = OpenAiMessageSerializer::class)
data class OpenAiMessage(
    /**
     * Phase 49 (DEL-01): single-turn only. Legacy `role=tool` rows never hit
     * the wire as `role:"tool"` — providers replay them as plain user text.
     *
     * IN-02: null content is omitted (never explicit `"content":null`) —
     * the assistant tool_calls echo (content null) omits the key instead of
     * sending explicit null — strict compat servers may 400 the latter.
     * Plain messages always carry non-null content, so they are unaffected.
     */
    val role: String,
    val content: String? = null,
    /**
     * Phase 57 (57-01): assistant-echo `tool_calls` (exact ids + complete
     * arguments strings) and `role:"tool"` result messages
     * (`tool_call_id`). In-memory loop echoes only — never persisted to
     * Room. Omitted when null so plain messages stay byte-identical.
     */
    @SerialName("tool_calls") val toolCalls: List<OpenAiCompletedToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    /**
     * Quick-task (remote-image-carry): history image data URLs carried as
     * OpenAI `content` array parts (`{type:"image_url",
     * image_url:{url}}`) alongside the text part. Null/empty keeps the
     * legacy string-content shape byte-identically (see
     * [OpenAiMessageSerializer]). Encode-only in practice — the client
     * never decodes chat messages off the wire.
     */
    val imageUrls: List<String>? = null,
)

/**
 * Quick-task (remote-image-carry): custom serializer so `content` stays a
 * plain string for every pre-carry row (byte-identical keys, nulls
 * omitted) while image-carrying history rows encode the multipart array.
 * Replaces the `@EncodeDefault(NEVER)` annotations 1:1 — omission rules
 * are enforced here instead.
 */
object OpenAiMessageSerializer : KSerializer<OpenAiMessage> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("OpenAiMessage") {
            element<String>("role")
            element<String?>("content")
        }

    override fun serialize(encoder: Encoder, value: OpenAiMessage) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("OpenAiMessageSerializer requires JSON encoding")
        val json = jsonEncoder.json
        val obj = buildJsonObject {
            put("role", value.role)
            val images = value.imageUrls?.filter { it.isNotBlank() }.orEmpty()
            if (images.isNotEmpty()) {
                putJsonArray("content") {
                    if (!value.content.isNullOrEmpty()) {
                        addJsonObject {
                            put("type", "text")
                            put("text", value.content)
                        }
                    }
                    for (url in images) {
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") { put("url", url) }
                        }
                    }
                }
            } else if (value.content != null) {
                put("content", value.content)
            }
            if (value.toolCalls != null) {
                put(
                    "tool_calls",
                    json.encodeToJsonElement(
                        ListSerializer(OpenAiCompletedToolCall.serializer()),
                        value.toolCalls,
                    ),
                )
            }
            if (value.toolCallId != null) put("tool_call_id", value.toolCallId)
        }
        jsonEncoder.encodeJsonElement(obj)
    }

    override fun deserialize(decoder: Decoder): OpenAiMessage {
        val el = (decoder as? JsonDecoder)?.decodeJsonElement()?.jsonObject
            ?: throw SerializationException("OpenAiMessageSerializer requires JSON decoding")
        val contentEl = el["content"]
        val content = when {
            contentEl == null || contentEl is JsonNull -> null
            contentEl is JsonPrimitive -> contentEl.contentOrNull
            contentEl is JsonArray -> contentEl
                .mapNotNull { (it as? JsonObject)?.get("text") as? JsonPrimitive }
                .joinToString("") { it.content }
                .ifEmpty { null }
            else -> null
        }
        return OpenAiMessage(
            role = (el["role"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            content = content,
            toolCallId = (el["tool_call_id"] as? JsonPrimitive)?.contentOrNull,
        )
    }
}

/**
 * Phase 57 (57-01): completed-call shape for the assistant echo and for
 * non-streaming `choices[].message.tool_calls` (Pitfall 3: servers that
 * ignore `stream:true` return this instead of SSE deltas).
 */
@Serializable
data class OpenAiCompletedToolCall(
    val id: String = "",
    val type: String = "function",
    val function: OpenAiFunctionCall = OpenAiFunctionCall(),
)

/** Phase 57 (57-01): the `function` half of an [OpenAiCompletedToolCall]. */
@Serializable
data class OpenAiFunctionCall(
    val name: String = "",
    /** Complete (reassembled) arguments JSON string — exact-string echo. */
    val arguments: String = "",
)

@Serializable
data class OpenAiModelListResponse(
    val `object`: String = "list",
    val data: List<OpenAiModelData> = emptyList()
)

@Serializable
data class OpenAiModelData(
    val id: String,
    val `object`: String = "model",
    val ownedBy: String = ""
)

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class OpenAiResponsesRequest(
    val model: String,
    val input: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val stream: Boolean = true,
    @SerialName("previous_response_id") val previousResponseId: String? = null,
    val temperature: Float? = null,
    @SerialName("top_p") val topP: Float? = null,
    @SerialName("max_output_tokens") val maxOutputTokens: Int? = null
)

@Serializable
data class OpenAiResponsesResponse(
    val id: String = "",
    val `object`: String = "",
    val status: String = "",
    val output: List<OpenAiResponseOutputItem> = emptyList()
)

@Serializable
data class OpenAiResponseOutputItem(
    val id: String = "",
    val type: String = "",
    val status: String = "",
    val role: String? = null,
    val content: List<OpenAiResponseContentPart> = emptyList()
)

@Serializable
data class OpenAiResponseContentPart(
    val type: String = "",
    val text: String? = null
)

@Serializable
data class OpenAiResponsesStreamEvent(
    val type: String = "",
    val delta: OpenAiResponseDelta? = null,
    val item: OpenAiResponseOutputItem? = null
)

@Serializable
data class OpenAiResponseDelta(
    val type: String = "",
    val text: String? = null,
    val content: String? = null,
    val delta: String? = null
)

@Serializable
data class OpenAiEmbeddingsRequest(
    val model: String,
    val input: List<String>,
    val dimensions: Int? = null,
    @SerialName("encoding_format") val encodingFormat: String? = null
)

@Serializable
data class OpenAiEmbeddingsResponse(
    val `object`: String = "list",
    val data: List<OpenAiEmbeddingData> = emptyList(),
    val model: String = ""
)

@Serializable
data class OpenAiEmbeddingData(
    val `object`: String = "embedding",
    val embedding: List<Float> = emptyList(),
    val index: Int = 0
)

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class OpenAiCompletionsRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = true,
    val temperature: Float? = null,
    @SerialName("top_p") val topP: Float? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val stop: List<String>? = null
)

@Serializable
data class OpenAiCompletionsStreamChunk(
    val id: String = "",
    val `object`: String = "",
    val choices: List<OpenAiCompletionChoice> = emptyList()
)

@Serializable
data class OpenAiCompletionChoice(
    val text: String = "",
    val index: Int = 0,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class OpenAiNonStreamingResponse(
    val choices: List<OpenAiNonStreamingChoice> = emptyList()
)

@Serializable
data class OpenAiNonStreamingChoice(
    val message: OpenAiNonStreamingMessage? = null
)

@Serializable
data class OpenAiNonStreamingMessage(
    val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    /**
     * Phase 57 (57-01, Pitfall 3): non-streaming servers return complete
     * `tool_calls` on the message — same loop entry, no separate path.
     */
    @SerialName("tool_calls") val toolCalls: List<OpenAiCompletedToolCall>? = null
)
