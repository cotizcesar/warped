package com.warped.ui.models

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warped.data.local.download.DownloadState
import com.warped.data.local.download.ModelDownloadManager
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.local.inference.ModelImportManager
import com.warped.data.local.security.ApiKeyStore
import com.warped.data.remote.provider.ProviderRouter
import com.warped.domain.model.ActiveModelSelection
import com.warped.domain.model.Endpoint
import com.warped.domain.model.LocalModel
import com.warped.domain.model.ProviderType
import com.warped.domain.repository.EndpointRepository
import com.warped.domain.repository.LocalModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val localModelRepository: LocalModelRepository,
    private val endpointRepository: EndpointRepository,
    private val activeModelSelection: ActiveModelSelection,
    private val modelImportManager: ModelImportManager,
    private val modelDownloadManager: ModelDownloadManager,
    private val memoryChecker: MemoryChecker,
    private val apiKeyStore: ApiKeyStore,
    private val providerRouter: ProviderRouter
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelsUiState())
    val uiState: StateFlow<ModelsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            localModelRepository.observeModels().collect { models ->
                _uiState.update { it.copy(models = models) }
            }
        }
        viewModelScope.launch {
            endpointRepository.observeEndpoints().collect { endpoints ->
                _uiState.update { it.copy(endpoints = endpoints) }
            }
        }
        viewModelScope.launch {
            modelDownloadManager.downloadStates.collect { states ->
                val active = states.values.filter {
                    it.isDownloading || it.isPaused || it.progress < 1f
                }
                _uiState.update { it.copy(activeDownloads = active) }
            }
        }
    }

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, importProgress = 0f, error = null) }
            val result = modelImportManager.importFromUri(uri) { progress ->
                _uiState.update { it.copy(importProgress = progress) }
            }
            result.onSuccess {
                _uiState.update { it.copy(isImporting = false, importProgress = 1f) }
            }.onFailure { e ->
                _uiState.update { it.copy(isImporting = false, error = e.message) }
            }
        }
    }

    fun deleteModel(model: LocalModel) {
        viewModelScope.launch {
            try {
                modelImportManager.deleteModel(model)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun useLocalModel(model: LocalModel) {
        activeModelSelection.select(model.filePath, ProviderType.LOCAL)
    }

    fun showEndpointForm() {
        _uiState.update {
            it.copy(
                isEndpointFormVisible = true,
                formName = "",
                formUrl = "",
                formApiType = "OPENAI",
                formModelId = "",
                formApiKey = ""
            )
        }
    }

    fun dismissEndpointForm() {
        _uiState.update { it.copy(isEndpointFormVisible = false, error = null) }
    }

    fun updateEndpointField(field: String, value: String) {
        _uiState.update {
            when (field) {
                "name" -> it.copy(formName = value)
                "url" -> it.copy(formUrl = value)
                "apiType" -> it.copy(formApiType = value)
                "modelId" -> it.copy(formModelId = value)
                "apiKey" -> it.copy(formApiKey = value)
                else -> it
            }
        }
    }

    fun saveEndpoint() {
        val state = _uiState.value
        if (state.formName.isBlank() || state.formUrl.isBlank() || state.formModelId.isBlank()) {
            _uiState.update { it.copy(error = "Name, URL, and Model ID are required") }
            return
        }
        viewModelScope.launch {
            try {
                val savedId = endpointRepository.saveEndpoint(
                    Endpoint(
                        name = state.formName,
                        url = state.formUrl,
                        apiType = ProviderType.valueOf(state.formApiType),
                        modelId = state.formModelId,
                    )
                )
                if (state.formApiKey.isNotBlank()) {
                    apiKeyStore.storeKey(savedId, state.formApiKey.toCharArray())
                }
                _uiState.update { it.copy(isEndpointFormVisible = false, error = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun editEndpoint(endpoint: Endpoint) {
        _uiState.update {
            it.copy(
                isEditingEndpoint = true,
                editingEndpoint = endpoint,
                formName = endpoint.name,
                formUrl = endpoint.url,
                formApiType = endpoint.apiType.name,
                formModelId = endpoint.modelId.orEmpty(),
                formApiKey = ""
            )
        }
    }

    fun deleteEndpoint(endpoint: Endpoint) {
        viewModelScope.launch {
            try {
                endpointRepository.deleteEndpoint(endpoint.id)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun saveEndpointEdit() {
        val state = _uiState.value
        if (state.formName.isBlank() || state.formUrl.isBlank()) return
        viewModelScope.launch {
            try {
                val endpoint = Endpoint(
                    id = state.editingEndpoint?.id ?: 0,
                    name = state.formName,
                    url = state.formUrl,
                    apiType = ProviderType.valueOf(state.formApiType),
                    modelId = state.formModelId.ifBlank { null },
                    isActive = state.editingEndpoint?.isActive ?: false
                )
                val savedId = endpointRepository.saveEndpoint(endpoint)
                if (state.formApiKey.isNotBlank()) {
                    apiKeyStore.storeKey(savedId, state.formApiKey.toCharArray())
                }
                _uiState.update { it.copy(isEditingEndpoint = false, error = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun cancelEndpointEdit() {
        _uiState.update { it.copy(isEditingEndpoint = false, error = null) }
    }

    fun useEndpoint(endpoint: Endpoint) {
        val modelId = endpoint.modelId ?: return
        viewModelScope.launch {
            endpointRepository.activateEndpoint(endpoint.id)
            activeModelSelection.select(modelId, endpoint.apiType)
            // Fetch available models from the endpoint
            fetchEndpointModels(endpoint)
        }
    }

    private suspend fun fetchEndpointModels(endpoint: Endpoint) {
        val modelId = endpoint.modelId ?: return
        _uiState.update { it.copy(isFetchingModels = true) }
        try {
            val provider = providerRouter.resolve(endpoint, modelId)
            val result = provider.listModels()
            result.onSuccess { models ->
                _uiState.update { it.copy(availableEndpointModels = models, isFetchingModels = false) }
            }.onFailure { e ->
                _uiState.update { it.copy(isFetchingModels = false, error = "Failed to fetch models: ${e.message}") }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isFetchingModels = false, error = e.message) }
        }
    }

    fun canLoadModel(modelSizeBytes: Long): Boolean = memoryChecker.canLoadModel(modelSizeBytes)
    fun shouldWarnAboutMemory(modelSizeBytes: Long): Boolean = memoryChecker.shouldWarn(modelSizeBytes)

    fun cancelDownload(modelId: String) {
        modelDownloadManager.cancelDownload(modelId)
    }

    fun deleteIncompleteDownload(download: DownloadState) {
        modelDownloadManager.deleteIncompleteDownload(download.modelId, download.fileName)
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
