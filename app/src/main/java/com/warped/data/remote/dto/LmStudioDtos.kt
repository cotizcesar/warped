package com.warped.data.remote.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class LmStudioChatRequest(
    val model: String,
    val input: List<LmStudioInputItem>,
    @SerialName("system_prompt") val systemPrompt: String? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val stream: Boolean = true,
    val temperature: Float? = null,
    @SerialName("top_p") val topP: Float? = null,
    @SerialName("top_k") val topK: Int? = null,
    @SerialName("repeat_penalty") val repeatPenalty: Float? = null,
    @SerialName("max_output_tokens") val maxOutputTokens: Int? = null,
    @SerialName("context_length") val contextLength: Int? = null,
    val reasoning: String? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val store: Boolean = false,
    val integrations: List<LmStudioIntegration> = emptyList()
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
    val type: String? = null,
    val content: String? = null,
    // chat.start / model_load.*
    @SerialName("model_instance_id") val modelInstanceId: String? = null,
    val progress: Float? = null,
    @SerialName("load_time_seconds") val loadTimeSeconds: Double? = null,
    // tool_call.*
    val tool: String? = null,
    val arguments: kotlinx.serialization.json.JsonObject? = null,
    @SerialName("provider_info") val providerInfo: LmStudioProviderInfo? = null,
    // tool_call.failure
    val reason: String? = null,
    val metadata: kotlinx.serialization.json.JsonObject? = null,
    // error event
    val error: LmStudioSseError? = null,
    // chat.end
    val result: LmStudioChatResult? = null,
    // non-streaming fallback response
    val output: List<LmStudioOutputItem>? = null,
    val stats: LmStudioStats? = null
)

@Serializable
data class LmStudioProviderInfo(
    val type: String = "",
    @SerialName("plugin_id") val pluginId: String? = null,
    @SerialName("server_label") val serverLabel: String? = null
)

@Serializable
data class LmStudioChatResult(
    @SerialName("model_instance_id") val modelInstanceId: String = "",
    val output: List<LmStudioOutputItem> = emptyList(),
    val stats: LmStudioStats? = null,
    @SerialName("response_id") val responseId: String? = null
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

@Serializable
data class LmStudioDownloadRequest(
    val model: String,
    val quantization: String? = null
)

@Serializable
data class LmStudioDownloadResponse(
    @SerialName("job_id") val jobId: String = "",
    val status: String = ""
)

@Serializable
data class LmStudioDownloadStatusResponse(
    @SerialName("job_id") val jobId: String = "",
    val status: String = "",
    val progress: Float = 0f,
    @SerialName("bytes_per_second") val bytesPerSecond: Long = 0,
    @SerialName("estimated_completion") val estimatedCompletion: String? = null,
    @SerialName("downloaded_bytes") val downloadedBytes: Long = 0,
    @SerialName("total_bytes") val totalBytes: Long? = null
)

@Serializable
data class LmStudioIntegration(
    val type: String,
    @SerialName("server_label") val serverLabel: String? = null,
    @SerialName("server_url") val serverUrl: String? = null,
    @SerialName("allowed_tools") val allowedTools: List<String> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val id: String? = null
)
