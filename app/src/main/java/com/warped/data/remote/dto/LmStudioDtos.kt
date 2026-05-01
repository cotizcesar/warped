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
    val reasoning: String? = null,
    val store: Boolean = false
)

@Serializable
data class LmStudioInputItem(
    val type: String,
    val content: String = "",
    @SerialName("data_url") val dataUrl: String? = null
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
    val format: String? = null,
    val capabilities: LmStudioCapabilities? = null
)

@Serializable
data class LmStudioCapabilities(
    val vision: Boolean = false,
    @SerialName("trained_for_tool_use") val trainedForToolUse: Boolean = false
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
    val error: LmStudioSseError? = null,
    val output: List<LmStudioOutputItem>? = null,
    val result: LmStudioChatResult? = null,
    val stats: LmStudioStats? = null
)

@Serializable
data class LmStudioChatResult(
    @SerialName("model_instance_id") val modelInstanceId: String = "",
    val output: List<LmStudioOutputItem> = emptyList(),
    val stats: LmStudioStats? = null
)

@Serializable
data class LmStudioStats(
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("total_output_tokens") val totalOutputTokens: Int = 0,
    @SerialName("reasoning_output_tokens") val reasoningOutputTokens: Int = 0,
    @SerialName("tokens_per_second") val tokensPerSecond: Double = 0.0,
    @SerialName("time_to_first_token_seconds") val timeToFirstTokenSeconds: Double = 0.0,
    @SerialName("model_load_time_seconds") val modelLoadTimeSeconds: Double? = null
)

@Serializable
data class LmStudioOutputItem(
    val type: String = "",
    val content: String = ""
)

@Serializable
data class LmStudioSseError(
    val message: String = "",
    val type: String = "",
    val code: String = ""
)

@Serializable
data class LmStudioLoadRequest(
    val model: String,
    @SerialName("context_length") val contextLength: Int? = null
)

@Serializable
data class LmStudioLoadResponse(
    val type: String = "",
    @SerialName("instance_id") val instanceId: String = "",
    @SerialName("load_time_seconds") val loadTimeSeconds: Double = 0.0,
    val status: String = ""
)

@Serializable
data class LmStudioUnloadRequest(
    @SerialName("instance_id") val instanceId: String
)

@Serializable
data class LmStudioUnloadResponse(
    @SerialName("instance_id") val instanceId: String = ""
)
