package com.warped.ui.endpoints

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warped.data.local.security.ApiKeyStore
import com.warped.data.remote.provider.ProviderRouter
import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.Endpoint
import com.warped.domain.model.ProviderType
import com.warped.domain.repository.EndpointRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EndpointsViewModel @Inject constructor(
    private val endpointRepository: EndpointRepository,
    private val providerRouter: ProviderRouter,
    private val apiKeyStore: ApiKeyStore,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(EndpointsUiState())
    val uiState: StateFlow<EndpointsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
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
                formApiType = "OPENAI",
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
        _uiState.update {
            it.copy(
                isFormVisible = true,
                editingEndpoint = endpoint,
                formName = endpoint.name,
                formUrl = endpoint.url,
                formApiType = endpoint.apiType.name,
                formModelId = endpoint.modelId.orEmpty(),
                formApiKey = "",
                hasSavedApiKey = hasKey
            )
        }
    }

    fun saveEndpoint() {
        val state = _uiState.value
        if (state.formName.isBlank() || state.formUrl.isBlank()) {
            _uiState.update { it.copy(error = "Name and URL are required") }
            return
        }
        var url = state.formUrl
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        if (!url.endsWith("/")) {
            url = "$url/"
        }
        viewModelScope.launch {
            try {
                val endpoint = Endpoint(
                    id = state.editingEndpoint?.id ?: 0,
                    name = state.formName,
                    url = url,
                    apiType = ProviderType.valueOf(state.formApiType),
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
        viewModelScope.launch {
            try {
                endpointRepository.deleteEndpoint(endpointId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun testConnection(endpointId: Long) {
        viewModelScope.launch {
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
        viewModelScope.launch {
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
                "apiType" -> it.copy(formApiType = value)
                "modelId" -> it.copy(formModelId = value)
                "apiKey" -> it.copy(formApiKey = value)
                else -> it
            }
        }
    }
}
