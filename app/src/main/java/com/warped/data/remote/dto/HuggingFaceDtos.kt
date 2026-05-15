package com.warped.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class HuggingFaceModel(
    val id: String = "",
    @SerialName("modelId") val modelIdAlias: String? = null,
    val author: String = "",
    val tags: List<String> = emptyList(),
    val downloads: Int = 0,
    val likes: Int = 0,
    val description: String = "",
    @SerialName("pipeline_tag") val pipelineTag: String = "",
    @SerialName("private") val isPrivate: Boolean = false,
    @SerialName("gated") val gated: String = "false",
    val lastModified: String = "",
    val siblings: List<HuggingFaceSibling> = emptyList()
)

@Serializable
data class HuggingFaceModelDetail(
    val id: String = "",
    @SerialName("modelId") val modelIdAlias: String? = null,
    val author: String = "",
    val tags: List<String> = emptyList(),
    val downloads: Int = 0,
    val likes: Int = 0,
    @SerialName("pipeline_tag") val pipelineTag: String = "",
    val siblings: List<HuggingFaceSibling> = emptyList(),
    val cardData: HuggingFaceCardData? = null,
    val config: Map<String, JsonElement>? = null,
    @SerialName("safetensors") val safeTensors: HuggingFaceSafeTensors? = null,
    @SerialName("gated") val gated: String = "false",
    @SerialName("private") val isPrivate: Boolean = false,
    val lastModified: String = "",
    val createdAt: String = "",
    @SerialName("sha") val sha: String? = null
)

@Serializable
data class HuggingFaceSafeTensors(
    val parameters: Map<String, JsonElement>? = null,
    val total: Long = 0
)

@Serializable
data class HuggingFaceCardData(
    val language: List<String>? = null,
    val license: String? = null,
    val libraryName: String? = null
)

@Serializable
data class HuggingFaceSibling(
    val rfilename: String = "",
    val size: Long = 0,
    val blobId: String? = null,
    val lfs: HuggingFaceLfsInfo? = null
)

@Serializable
data class HuggingFaceLfsInfo(
    val size: Long = 0,
    val sha256: String? = null,
    val pointerSize: Int? = null
)

@Serializable
data class HuggingFaceSearchResult(
    val id: String,
    val modelId: String,
    val author: String,
    val description: String = "",
    val downloads: Int = 0,
    val likes: Int = 0
)

