package com.warped.data.remote.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val stream: Boolean = true,
    val temperature: Float? = null,
    @SerialName("top_p") val topP: Float? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val stop: List<String>? = null
)

@Serializable
data class OpenAiMessage(
    val role: String,
    val content: String
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
    @SerialName("reasoning_content") val reasoningContent: String? = null
)
