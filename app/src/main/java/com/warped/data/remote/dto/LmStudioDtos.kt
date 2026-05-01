package com.warped.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LmStudioChatRequest(
    val model: String,
    val input: List<LmStudioInputItem>,
    @SerialName("system_prompt") val systemPrompt: String? = null,
    val stream: Boolean = true,
    val temperature: Float? = null,
    @SerialName("top_p") val topP: Float? = null,
    @SerialName("top_k") val topK: Int? = null,
    @SerialName("repeat_penalty") val repeatPenalty: Float? = null,
    @SerialName("max_output_tokens") val maxOutputTokens: Int? = null,
    @SerialName("context_length") val contextLength: Int? = null,
    val store: Boolean = false
)

@Serializable
data class LmStudioInputItem(
    val type: String = "message",
    val content: String
)

@Serializable
data class LmStudioModelListResponse(
    val models: List<LmStudioModelData> = emptyList()
)

@Serializable
data class LmStudioModelData(
    val key: String,
    @SerialName("display_name") val displayName: String = "",
    val type: String = "llm",
    val publisher: String = "",
    val architecture: String? = null,
    val quantization: LmStudioQuantization? = null,
    @SerialName("size_bytes") val sizeBytes: Long = 0,
    @SerialName("params_string") val paramsString: String? = null,
    @SerialName("max_context_length") val maxContextLength: Int = 0,
    val format: String? = null
)

@Serializable
data class LmStudioQuantization(
    val name: String? = null,
    @SerialName("bits_per_weight") val bitsPerWeight: Int? = null
)

@Serializable
data class LmStudioSseEvent(
    val content: String? = null,
    val token: String? = null,
    val type: String? = null,
    val done: Boolean = false,
    val error: LmStudioSseError? = null
)

@Serializable
data class LmStudioSseError(
    val message: String = "",
    val type: String = "",
    val code: String = ""
)
