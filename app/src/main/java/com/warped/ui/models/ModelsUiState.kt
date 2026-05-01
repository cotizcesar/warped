package com.warped.ui.models

import com.warped.data.local.download.DownloadState
import com.warped.domain.model.LocalModel
import com.warped.domain.model.Endpoint

data class ModelsUiState(
    val models: List<LocalModel> = emptyList(),
    val endpoints: List<Endpoint> = emptyList(),
    val activeDownloads: List<DownloadState> = emptyList(),
    val isLoading: Boolean = false,
    val isImporting: Boolean = false,
    val importProgress: Float = 0f,
    val isEndpointFormVisible: Boolean = false,
    val formName: String = "",
    val formUrl: String = "",
    val formApiType: String = "OPENAI",
    val formModelId: String = "",
    val formApiKey: String = "",
    val error: String? = null
)
