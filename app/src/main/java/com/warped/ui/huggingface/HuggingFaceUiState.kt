package com.warped.ui.huggingface

import com.warped.data.remote.dto.HuggingFaceModel
import com.warped.data.remote.dto.HuggingFaceModelDetail
import com.warped.data.remote.dto.HuggingFaceSibling

data class HuggingFaceUiState(
    val searchQuery: String = "",
    val searchResults: List<HuggingFaceModel> = emptyList(),
    val isLoading: Boolean = false,
    val selectedModel: HuggingFaceModelDetail? = null,
    val modelSiblings: List<HuggingFaceSibling> = emptyList(),
    val isDownloading: Boolean = false,
    val isDownloadPaused: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadedBytes: Long = 0,
    val totalDownloadBytes: Long = 0,
    val downloadSpeedBytesPerSecond: Long = 0,
    val downloadingFileName: String = "",
    val downloadError: String? = null,
    val downloadSuccess: Boolean = false,
    val activeDownloadId: String? = null,
    val error: String? = null,
    val activeFormat: String = "litertlm"
)
