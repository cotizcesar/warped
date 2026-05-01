package com.warped.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenAiStreamChunk(
    val choices: List<OpenAiStreamChoice> = emptyList()
)

@Serializable
data class OpenAiStreamChoice(
    val delta: OpenAiStreamDelta? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class OpenAiStreamDelta(
    val content: String? = null,
    val role: String? = null
)

@Serializable
data class OllamaStreamChunk(
    val model: String? = null,
    val message: OllamaStreamMessage? = null,
    val done: Boolean = false
)

@Serializable
data class OllamaStreamMessage(
    val content: String? = null,
    val role: String? = null
)
