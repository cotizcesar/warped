package com.warped.ui.selector

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warped.data.local.download.DownloadState
import com.warped.data.local.download.ModelDownloadManager
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.InputSanitizer
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.local.inference.ModelImportManager
import com.warped.data.local.security.ApiKeyStore
import com.warped.data.remote.provider.LMStudioProvider
import com.warped.data.remote.provider.ProviderRouter
import com.warped.domain.model.*
import com.warped.domain.repository.EndpointRepository
import com.warped.domain.repository.LocalModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class UnifiedSelectorViewModel @Inject constructor(
    private val localModelRepository: LocalModelRepository,
    private val endpointRepository: EndpointRepository,
    private val activeModelSelection: ActiveModelSelection,
    private val engineManager: EngineManager,
    private val modelImportManager: ModelImportManager,
    private val modelDownloadManager: ModelDownloadManager,
    private val providerRouter: ProviderRouter,
    private val memoryChecker: MemoryChecker,
    private val apiKeyStore: ApiKeyStore,
    private val inputSanitizer: InputSanitizer,
    private val parameterStore: ParameterStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(UnifiedSelectorUiState())
    val uiState: StateFlow<UnifiedSelectorUiState> = _uiState.asStateFlow()

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable, "Unhandled coroutine exception in UnifiedSelectorVM")
    }

    init {
        viewModelScope.launch(coroutineExceptionHandler) {
            localModelRepository.observeModels().collect { models ->
                _uiState.update { it.copy(localModels = models) }
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            endpointRepository.observeEndpoints().collect { endpoints ->
                _uiState.update { it.copy(endpoints = endpoints) }
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            activeModelSelection.localSelection.collect { selection ->
                _uiState.update {
                    it.copy(
                        connectedLocalModelId = selection.modelId?.takeIf { selection.isConnected },
                        isLocalConnected = selection.isConnected
                    )
                }
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            activeModelSelection.remoteSelection.collect { selection ->
                _uiState.update {
                    it.copy(
                        selectedRemoteModelId = selection.modelId,
                        selectedRemoteProvider = selection.providerType,
                        selectedRemoteEndpointId = selection.endpointId
                    )
                }
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            modelDownloadManager.downloadStates.collect { states ->
                val active = states.values.filter {
                    it.isDownloading || it.isPaused || it.progress < 1f
                }
                _uiState.update { it.copy(activeDownloads = active.toList()) }
            }
        }
        refreshActiveBackend()
    }

    fun connectLocal(model: LocalModel) {
        val providerType = if (model.modelFormat.equals("LITERTLM", ignoreCase = true)
            || model.filePath.endsWith(".litertlm", ignoreCase = true)
        ) ProviderType.LITE_RT_LM else ProviderType.LITE_RT_LM

        _uiState.update { it.copy(isConnecting = true, connectingModelName = model.name) }
        parameterStore.update(model.parameters)
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                val activeEngine = engineManager.getActiveEngine()
                if (activeEngine?.modelPath == model.filePath) {
                    activeModelSelection.connectLocal(model.filePath, providerType)
                    _uiState.update { it.copy(isConnecting = false, error = null) }
                    refreshActiveBackend()
                    return@launch
                }
                activeModelSelection.markLocalLoading(model.filePath)
                withContext(Dispatchers.Default) {
                    engineManager.switchToLiteRT(model.filePath)
                }
                activeModelSelection.connectLocal(model.filePath, providerType)
                _uiState.update { it.copy(isConnecting = false, error = null) }
                refreshActiveBackend()
            } catch (e: Exception) {
                Timber.e(e, "UnifiedSelectorVM: connectLocal failed")
                activeModelSelection.disconnectLocal()
                _uiState.update { it.copy(isConnecting = false, error = "Failed to load model: ${e.message}") }
            }
        }
    }

    fun disconnectLocal() {
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                withContext(Dispatchers.Default) {
                    engineManager.unloadCurrent()
                }
            } catch (e: Exception) {
                Timber.w(e, "UnifiedSelectorVM: unloadCurrent failed")
            }
            activeModelSelection.disconnectLocal()
            _uiState.update { it.copy(isConnecting = false) }
            refreshActiveBackend()
        }
    }

    fun selectRemote(endpoint: Endpoint) {
        val modelId = endpoint.modelId ?: return
        activeModelSelection.selectRemote(modelId, endpoint.apiType, endpoint.id)
        viewModelScope.launch(coroutineExceptionHandler) {
            endpointRepository.activateEndpoint(endpoint.id)
        }
    }

    /**
     * Fetch models for the endpoint currently being configured in the form.
     * Uses a temp endpoint built from [SelectorFormState] fields, identical to
     * the [com.warped.ui.models.ModelsViewModel.fetchEndpointModels] flow.
     */
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
            _uiState.update { it.copy(isFetchingEndpointModels = true) }
            try {
                val provider = if (apiType == ProviderType.ANTHROPIC) {
                    LMStudioProvider(baseUrl = url, modelId = "fetch", inputSanitizer = inputSanitizer)
                } else {
                    providerRouter.resolve(tempEndpoint, "fetch")
                }
                val result = provider.listModels()
                result.onSuccess { models ->
                    val lmData = com.warped.data.repository.LmStudioModelCache.lastData
                    _uiState.update {
                        it.copy(
                            availableEndpointModels = models.map { m -> m.id },
                            availableEndpointModelsData = lmData,
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

    fun fetchEndpointModels(endpoint: Endpoint) {
        viewModelScope.launch(coroutineExceptionHandler) {
            _uiState.update {
                it.copy(
                    isFetchingModels = true,
                    fetchingEndpointId = endpoint.id,
                    endpointModelErrors = it.endpointModelErrors - endpoint.id
                )
            }
            try {
                val provider = if (endpoint.apiType == ProviderType.ANTHROPIC) {
                    LMStudioProvider(baseUrl = endpoint.url, modelId = "fetch", inputSanitizer = inputSanitizer)
                } else {
                    providerRouter.resolve(endpoint, "fetch")
                }
                val result = provider.listModels()
                result.onSuccess { models ->
                    _uiState.update {
                        it.copy(
                            isFetchingModels = false,
                            fetchingEndpointId = null,
                            endpointModels = it.endpointModels + (endpoint.id to models)
                        )
                    }
                }.onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isFetchingModels = false,
                            fetchingEndpointId = null,
                            endpointModelErrors = it.endpointModelErrors + (endpoint.id to "Failed to fetch: ${e.message}")
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isFetchingModels = false,
                        fetchingEndpointId = null,
                        endpointModelErrors = it.endpointModelErrors + (endpoint.id to (e.message ?: "Unknown error"))
                    )
                }
            }
        }
    }

    fun updateModelParameters(modelId: Long, parameters: GenerationParameters) {
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                localModelRepository.updateParameters(modelId, parameters)
                val activeId = activeModelSelection.localSelection.value.modelId
                if (activeId != null) {
                    val active = localModelRepository.getByFilePath(activeId)
                    if (active != null && active.id == modelId) {
                        parameterStore.update(parameters)
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun deleteModel(model: LocalModel) {
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                if (_uiState.value.connectedLocalModelId == model.filePath) {
                    disconnectLocal()
                }
                modelImportManager.deleteModel(model)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun shouldWarnAboutMemory(modelSizeBytes: Long): Boolean =
        memoryChecker.shouldWarn(modelSizeBytes)

    fun cancelDownload(modelId: String) {
        modelDownloadManager.cancelDownload(modelId)
    }

    fun deleteIncompleteDownload(download: DownloadState) {
        modelDownloadManager.deleteIncompleteDownload(download.modelId, download.fileName)
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun showEndpointForm() {
        _uiState.update {
            it.copy(
                isEndpointFormVisible = true,
                formName = "",
                formUrl = "",
                formApiType = ProviderType.LM_STUDIO.name,
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
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
        if (!url.endsWith("/")) url = "$url/"
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                val resolvedApiType = resolveApiType(state.formApiType, state.formLmStudioMode)
                val savedId = endpointRepository.saveEndpoint(
                    Endpoint(name = state.formName, url = url, apiType = resolvedApiType, modelId = state.formModelId)
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
        val hasKey = apiKeyStore.getKey(endpoint.id)?.let { true } ?: false
        val lmMode = when (endpoint.apiType) {
            ProviderType.LM_STUDIO -> "native"
            ProviderType.OPENAI -> "openai"
            ProviderType.ANTHROPIC -> "anthropic"
            else -> "native"
        }
        val displayApiType = if (endpoint.apiType == ProviderType.LM_STUDIO
            || endpoint.apiType == ProviderType.OPENAI
            || endpoint.apiType == ProviderType.ANTHROPIC
        ) ProviderType.LM_STUDIO.name else endpoint.apiType.name

        _uiState.update {
            it.copy(
                isEditingEndpoint = true,
                editingEndpointId = endpoint.id,
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

    fun saveEndpointEdit() {
        val state = _uiState.value
        if (state.formName.isBlank() || state.formUrl.isBlank()) return
        var url = state.formUrl
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
        if (!url.endsWith("/")) url = "$url/"
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                val resolvedApiType = resolveApiType(state.formApiType, state.formLmStudioMode)
                val endpoint = Endpoint(
                    id = state.editingEndpointId ?: 0,
                    name = state.formName,
                    url = url,
                    apiType = resolvedApiType,
                    modelId = state.formModelId.ifBlank { null },
                    isActive = false
                )
                val savedId = endpointRepository.saveEndpoint(endpoint)
                if (state.formApiKey.isNotBlank()) {
                    apiKeyStore.storeKey(savedId, state.formApiKey.toCharArray())
                }
                _uiState.update { it.copy(isEditingEndpoint = false, editingEndpointId = null, error = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun cancelEndpointEdit() {
        _uiState.update { it.copy(isEditingEndpoint = false, editingEndpointId = null, error = null) }
    }

    fun deleteEndpoint(endpoint: Endpoint) {
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                endpointRepository.deleteEndpoint(endpoint.id)
                val remote = activeModelSelection.remoteSelection.value
                if (remote.endpointId == endpoint.id) {
                    activeModelSelection.clearRemote()
                }
                _uiState.update {
                    it.copy(endpointModels = it.endpointModels - endpoint.id)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    private fun refreshActiveBackend() {
        val engine = engineManager.getActiveEngine()
        _uiState.update { it.copy(activeBackend = engine?.backend) }
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
