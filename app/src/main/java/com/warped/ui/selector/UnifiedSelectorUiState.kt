package com.warped.ui.selector

import com.warped.data.local.inference.EngineType
import com.warped.data.local.download.DownloadState
import com.warped.domain.model.Endpoint
import com.warped.domain.model.LocalModel
import com.warped.domain.model.ProviderType

data class UnifiedSelectorUiState(
    val localModels: List<LocalModel> = emptyList(),
    val endpoints: List<Endpoint> = emptyList(),
    val connectedLocalModelId: String? = null,
    val isLocalConnected: Boolean = false,
    val selectedRemoteModelId: String? = null,
    val selectedRemoteProvider: ProviderType? = null,
    val selectedRemoteEndpointId: Long? = null,
    val isConnecting: Boolean = false,
    val connectingModelName: String = "",
    val isFetchingModels: Boolean = false,
    val fetchingEndpointId: Long? = null,
    val endpointModels: Map<Long, List<com.warped.domain.model.ModelInfo>> = emptyMap(),
    val endpointModelErrors: Map<Long, String> = emptyMap(),
    val activeDownloads: List<DownloadState> = emptyList(),
    val activeBackend: com.warped.data.local.inference.BackendType? = null,
    val error: String? = null,

    val isEndpointFormVisible: Boolean = false,
    val isEditingEndpoint: Boolean = false,
    val editingEndpointId: Long? = null,
    val formName: String = "",
    val formUrl: String = "",
    val formApiType: String = ProviderType.LM_STUDIO.name,
    val formLmStudioMode: String = "native",
    val formModelId: String = "",
    val formApiKey: String = "",
    val hasSavedApiKey: Boolean = false,
    val availableEndpointModels: List<String> = emptyList(),
    val availableEndpointModelsData: List<com.warped.data.remote.dto.LmStudioModelData> = emptyList(),
    val isFetchingEndpointModels: Boolean = false,
)
