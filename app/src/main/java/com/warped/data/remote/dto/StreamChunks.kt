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
    val role: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    @SerialName("tool_calls") val toolCalls: List<OpenAiStreamToolCall>? = null
)

@Serializable
data class OpenAiStreamToolCall(
    val index: Int = 0,
    val id: String? = null,
    val function: OpenAiStreamFunction? = null
)

@Serializable
data class OpenAiStreamFunction(
    val name: String? = null,
    val arguments: String? = null
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

@Serializable
data class OllamaGenerateChunk(
    val model: String? = null,
    val response: String = "",
    val done: Boolean = false
)

@Serializable
data class OllamaPullChunk(
    val status: String = "",
    val digest: String? = null,
    val total: Long? = null,
    val completed: Long? = null
)
