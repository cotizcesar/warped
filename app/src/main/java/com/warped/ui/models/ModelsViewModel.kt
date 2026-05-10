package com.warped.ui.models

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warped.data.local.download.DownloadState
import com.warped.data.local.download.ModelDownloadManager
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.local.inference.ModelImportManager
import com.warped.data.local.security.ApiKeyStore
import com.warped.data.remote.provider.LMStudioProvider
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
import kotlinx.coroutines.CoroutineExceptionHandler
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

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        timber.log.Timber.e(throwable, "Unhandled coroutine exception")
    }

    init {
        viewModelScope.launch(coroutineExceptionHandler) {
            localModelRepository.observeModels().collect { models ->
                _uiState.update { it.copy(models = models) }
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            endpointRepository.observeEndpoints().collect { endpoints ->
                _uiState.update { it.copy(endpoints = endpoints) }
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            modelDownloadManager.downloadStates.collect { states ->
                val active = states.values.filter {
                    it.isDownloading || it.isPaused || it.progress < 1f
                }
                _uiState.update { it.copy(activeDownloads = active) }
            }
        }
    }

    fun importModel(uri: Uri) {
        viewModelScope.launch(coroutineExceptionHandler) {
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
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                modelImportManager.deleteModel(model)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun useLocalModel(model: LocalModel) {
        val providerType = if (model.isLiteRtLm()) ProviderType.LITE_RT_LM else ProviderType.LOCAL
        activeModelSelection.select(model.filePath, providerType)
    }

    private fun LocalModel.isLiteRtLm(): Boolean =
        modelFormat.equals("LITERTLM", ignoreCase = true) || filePath.endsWith(".litertlm", ignoreCase = true)

    fun showEndpointForm() {
        _uiState.update {
            it.copy(
                isEndpointFormVisible = true,
                formName = "",
                formUrl = "",
                formApiType = "LM_STUDIO",
                formLmStudioMode = "native",
                formModelId = "",
                formApiKey = "",
                hasSavedApiKey = false
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
                "apiType" -> it.copy(
                    formApiType = value,
                    formLmStudioMode = if (value == ProviderType.LM_STUDIO.name) it.formLmStudioMode else "native"
                )
                "lmStudioMode" -> it.copy(formLmStudioMode = value)
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
        var url = state.formUrl
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        if (!url.endsWith("/")) {
            url = "$url/"
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                val resolvedApiType = resolveApiType(state.formApiType, state.formLmStudioMode)
                val savedId = endpointRepository.saveEndpoint(
                    Endpoint(
                        name = state.formName,
                        url = url,
                        apiType = resolvedApiType,
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
        val hasKey = apiKeyStore.getKey(endpoint.id)?.let { key ->
            key.fill('0')
            true
        } ?: false
        val lmMode = when (endpoint.apiType) {
            ProviderType.LM_STUDIO -> "native"
            ProviderType.OPENAI -> "openai"
            ProviderType.ANTHROPIC -> "anthropic"
            else -> "native"
        }
        val displayApiType = if (endpoint.apiType == ProviderType.LM_STUDIO
            || endpoint.apiType == ProviderType.OPENAI
            || endpoint.apiType == ProviderType.ANTHROPIC) {
            "LM_STUDIO"
        } else {
            endpoint.apiType.name
        }
        _uiState.update {
            it.copy(
                isEditingEndpoint = true,
                editingEndpoint = endpoint,
                formName = endpoint.name,
                formUrl = endpoint.url,
                formApiType = displayApiType,
                formLmStudioMode = lmMode,
                formModelId = endpoint.modelId.orEmpty(),
                formApiKey = "",
                hasSavedApiKey = hasKey
            )
        }
    }

    fun deleteEndpoint(endpoint: Endpoint) {
        _uiState.update { it.copy(endpoints = it.endpoints.filter { e -> e.id != endpoint.id }) }
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                endpointRepository.deleteEndpoint(endpoint.id)
                // If this endpoint was the active selection, clear it from chat
                val active = activeModelSelection.activeModel.value
                if (active != null && active.modelId == endpoint.modelId && active.providerType == endpoint.apiType) {
                    activeModelSelection.clear()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun saveEndpointEdit() {
        val state = _uiState.value
        if (state.formName.isBlank() || state.formUrl.isBlank()) return
        var url = state.formUrl
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        if (!url.endsWith("/")) {
            url = "$url/"
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                val resolvedApiType = resolveApiType(state.formApiType, state.formLmStudioMode)
                val endpoint = Endpoint(
                    id = state.editingEndpoint?.id ?: 0,
                    name = state.formName,
                    url = url,
                    apiType = resolvedApiType,
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
        activeModelSelection.select(modelId, endpoint.apiType)
        viewModelScope.launch(coroutineExceptionHandler) {
            endpointRepository.activateEndpoint(endpoint.id)
            fetchEndpointModels(endpoint)
        }
    }

    fun fetchEndpointModels() {
        val state = _uiState.value
        var url = state.formUrl.ifBlank { return }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        if (!url.endsWith("/")) {
            url = "$url/"
        }
        val apiType = try { ProviderType.valueOf(state.formApiType) } catch (_: IllegalArgumentException) { ProviderType.CUSTOM }
        val tempEndpoint = Endpoint(id = 0, name = "temp", url = url, apiType = apiType, modelId = "fetch")
        viewModelScope.launch(coroutineExceptionHandler) {
            _uiState.update { it.copy(isFetchingEndpointModels = true, availableEndpointModels = emptyList(), availableEndpointModelsData = emptyList()) }
            try {
                val provider = if (apiType == ProviderType.ANTHROPIC) {
                    LMStudioProvider(baseUrl = url, modelId = "fetch")
                } else {
                    providerRouter.resolve(tempEndpoint, "fetch")
                }
                val result = provider.listModels()
                result.onSuccess { models ->
                    _uiState.update {
                        it.copy(
                            availableEndpointModels = models.map { m -> m.id },
                            isFetchingEndpointModels = false
                        )
                    }
                }.onFailure { e ->
                    _uiState.update { it.copy(isFetchingEndpointModels = false, error = "Failed to fetch: ${e.message}") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isFetchingEndpointModels = false, error = e.message) }
            }
        }
    }

    private suspend fun fetchEndpointModels(endpoint: Endpoint) {
        val modelId = endpoint.modelId ?: return
        _uiState.update { it.copy(isFetchingEndpointModels = true) }
        try {
            val provider = providerRouter.resolve(endpoint, modelId)
            val result = provider.listModels()
            result.onSuccess { models ->
                _uiState.update { it.copy(availableEndpointModels = models.map { it.id }, isFetchingEndpointModels = false) }
            }.onFailure { e ->
                _uiState.update { it.copy(isFetchingEndpointModels = false, error = "Failed to fetch models: ${e.message}") }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isFetchingEndpointModels = false, error = e.message) }
        }
    }

    fun canLoadModel(modelSizeBytes: Long): Boolean = memoryChecker.canLoadModel(modelSizeBytes)
    fun shouldWarnAboutMemory(modelSizeBytes: Long): Boolean {
        return memoryChecker.shouldWarn(modelSizeBytes)
    }

    fun cancelDownload(modelId: String) {
        modelDownloadManager.cancelDownload(modelId)
    }

    fun deleteIncompleteDownload(download: DownloadState) {
        modelDownloadManager.deleteIncompleteDownload(download.modelId, download.fileName)
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun resolveApiType(formApiType: String, lmStudioMode: String): ProviderType {
        if (formApiType == ProviderType.LM_STUDIO.name) {
            return when (lmStudioMode) {
                "openai" -> ProviderType.OPENAI
                "anthropic" -> ProviderType.ANTHROPIC
                else -> ProviderType.LM_STUDIO
            }
        }
        return try { ProviderType.valueOf(formApiType) } catch (_: IllegalArgumentException) { ProviderType.CUSTOM }
    }
}
