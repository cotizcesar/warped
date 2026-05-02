package com.warped.ui.huggingface

import com.warped.data.remote.dto.HuggingFaceModel
import com.warped.data.remote.dto.HuggingFaceModelDetail
import com.warped.data.remote.dto.HuggingFaceSibling

data class HuggingFaceUiState(
    val searchQuery: String = "",
    val searchResults: List<HuggingFaceModel> = emptyList(),
    val compatibilityByModelId: Map<String, Boolean> = emptyMap(),
    val showCompatibleOnly: Boolean = false,
    val isLoading: Boolean = false,
    val availableMemoryBytes: Long = 0,
    val selectedModel: HuggingFaceModelDetail? = null,
    val modelSiblings: List<HuggingFaceSibling> = emptyList(),
    val compatibilityByFileName: Map<String, Int> = emptyMap(),
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadingFileName: String = "",
    val downloadError: String? = null,
    val downloadSuccess: Boolean = false,
    val activeDownloadId: String? = null,
    val error: String? = null,
    val activeFormat: String = "gguf"
)
