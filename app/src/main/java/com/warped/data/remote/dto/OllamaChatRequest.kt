package com.warped.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = true,
    val options: OllamaOptions? = null
)

@Serializable
data class OllamaMessage(
    val role: String,
    val content: String
)

@Serializable
data class OllamaOptions(
    val temperature: Float? = null,
    @SerialName("top_p") val topP: Float? = null,
    @SerialName("top_k") val topK: Int? = null,
    @SerialName("num_predict") val numPredict: Int? = null
)

@Serializable
data class OllamaModelListResponse(
    val models: List<OllamaModelData> = emptyList()
)

@Serializable
data class OllamaModelData(
    val name: String,
    @SerialName("modified_at") val modifiedAt: String = "",
    val size: Long = 0
)
