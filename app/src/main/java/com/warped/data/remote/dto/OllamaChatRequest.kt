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

@Serializable
data class OllamaGenerateRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = true,
    val images: List<String>? = null,
    val format: String? = null,
    val system: String? = null,
    val options: OllamaOptions? = null,
    @SerialName("keep_alive") val keepAlive: String? = null,
    val raw: Boolean? = null
)

@Serializable
data class OllamaEmbedRequest(
    val model: String,
    val input: List<String>,
    val truncate: Boolean? = null,
    val dimensions: Int? = null
)

@Serializable
data class OllamaEmbedResponse(
    val embeddings: List<List<Float>> = emptyList()
)

@Serializable
data class OllamaPsResponse(
    val models: List<OllamaRunningModel> = emptyList()
)

@Serializable
data class OllamaRunningModel(
    val name: String = "",
    val model: String = "",
    val size: Long = 0,
    @SerialName("size_vram") val sizeVram: Long = 0,
    @SerialName("context_length") val contextLength: Int = 0,
    @SerialName("expires_at") val expiresAt: String? = null
)

@Serializable
data class OllamaShowRequest(
    val model: String
)

@Serializable
data class OllamaShowResponse(
    val license: String? = null,
    val modelfile: String? = null,
    val parameters: String? = null,
    val template: String? = null,
    @SerialName("model_info") val modelInfo: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap()
)

@Serializable
data class OllamaCreateRequest(
    val model: String,
    val modelfile: String? = null,
    val path: String? = null,
    val stream: Boolean = true
)

@Serializable
data class OllamaDeleteRequest(
    val model: String
)

@Serializable
data class OllamaPullRequest(
    val model: String,
    val stream: Boolean = true,
    val insecure: Boolean = false
)
