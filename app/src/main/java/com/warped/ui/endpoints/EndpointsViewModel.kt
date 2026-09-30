package com.warped.ui.endpoints

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warped.R
import com.warped.data.local.security.ApiKeyStore
import com.warped.data.remote.provider.ProviderRouter
import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.Endpoint
import com.warped.domain.model.ProviderType
import com.warped.domain.repository.EndpointRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch

import javax.inject.Inject
import timber.log.Timber

@HiltViewModel
class EndpointsViewModel @Inject constructor(
    private val endpointRepository: EndpointRepository,
    private val providerRouter: ProviderRouter,
    private val apiKeyStore: ApiKeyStore,
    private val savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(EndpointsUiState())
    val uiState: StateFlow<EndpointsUiState> = _uiState.asStateFlow()

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        timber.log.Timber.e(throwable, "Unhandled coroutine exception")
    }

    init {
        viewModelScope.launch(coroutineExceptionHandler) {
            endpointRepository.observeEndpoints().collect { endpoints ->
                _uiState.update { it.copy(endpoints = endpoints) }
            }
        }
    }

    fun showAddForm() {
        _uiState.update {
            it.copy(
                isFormVisible = true,
                editingEndpoint = null,
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

    fun showEditForm(endpoint: Endpoint) {
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
                isFormVisible = true,
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

    fun saveEndpoint() {
        val state = _uiState.value
        if (state.formName.isBlank() || state.formUrl.isBlank()) {
            _uiState.update { it.copy(error = context.getString(R.string.form_error_required)) }
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
                val resolvedApiType = if (state.formApiType == ProviderType.LM_STUDIO.name) {
                    when (state.formLmStudioMode) {
                        "openai" -> ProviderType.OPENAI
                        "anthropic" -> ProviderType.ANTHROPIC
                        else -> ProviderType.LM_STUDIO
                    }
                } else {
                    ProviderType.valueOf(state.formApiType)
                }
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
                _uiState.update { it.copy(isFormVisible = false, error = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun deleteEndpoint(endpointId: Long) {
        _uiState.update { it.copy(endpoints = it.endpoints.filter { e -> e.id != endpointId }) }
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                endpointRepository.deleteEndpoint(endpointId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun testConnection(endpointId: Long) {
        viewModelScope.launch(coroutineExceptionHandler) {
            _uiState.update { it.copy(testStatus = it.testStatus + (endpointId to ConnectionStatus.Connecting)) }
            try {
                val endpoint = _uiState.value.endpoints.find { it.id == endpointId } ?: return@launch
                val provider = providerRouter.resolve(endpoint, "test")
                val result = provider.testConnection()
                _uiState.update {
                    it.copy(testStatus = it.testStatus + (endpointId to result.getOrElse { ConnectionStatus.Disconnected }))
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(testStatus = it.testStatus + (endpointId to ConnectionStatus.Disconnected)) }
            }
        }
    }

    fun activateEndpoint(endpointId: Long) {
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                endpointRepository.activateEndpoint(endpointId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun dismissForm() {
        _uiState.update { it.copy(isFormVisible = false, error = null) }
    }

    fun updateFormField(field: String, value: String) {
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
}
