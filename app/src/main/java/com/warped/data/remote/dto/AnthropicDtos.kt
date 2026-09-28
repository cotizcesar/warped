package com.warped.data.remote.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

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
    val thinking: AnthropicThinking? = null
)

@Serializable
data class AnthropicThinking(
    val type: String,
    @SerialName("budget_tokens") val budgetTokens: Int
)

@Serializable
data class AnthropicMessage(
    val role: String,
    val content: String
)

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
