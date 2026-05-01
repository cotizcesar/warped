package com.warped.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnthropicChatRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val messages: List<AnthropicMessage>,
    val system: String? = null,
    val stream: Boolean = true,
    val temperature: Float? = null,
    @SerialName("top_p") val topP: Float? = null,
    @SerialName("top_k") val topK: Int? = null
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
    val index: Int? = null
)

@Serializable
data class AnthropicDelta(
    val type: String = "",
    val text: String? = null,
    @SerialName("stop_reason") val stopReason: String? = null
)

@Serializable
data class AnthropicSseMessage(
    val id: String = "",
    val model: String = "",
    val role: String = ""
)
