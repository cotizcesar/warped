package com.warped.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warped.data.local.security.ApiKeyStore
import com.warped.domain.repository.ChatRepository
import com.warped.domain.repository.EndpointRepository
import com.warped.domain.repository.LocalModelRepository
import com.warped.domain.repository.PresetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val endpointRepository: EndpointRepository,
    private val localModelRepository: LocalModelRepository,
    private val presetRepository: PresetRepository,
    private val apiKeyStore: ApiKeyStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            chatRepository.observeConversations().collect { conversations ->
                _uiState.update { it.copy(chatCount = conversations.size) }
            }
        }
        viewModelScope.launch {
            endpointRepository.observeEndpoints().collect { endpoints ->
                _uiState.update { it.copy(endpointCount = endpoints.size) }
            }
        }
        viewModelScope.launch {
            localModelRepository.observeModels().collect { models ->
                _uiState.update { it.copy(modelCount = models.size) }
            }
        }
        viewModelScope.launch {
            presetRepository.observePresets().collect { presets ->
                _uiState.update { it.copy(presetCount = presets.size) }
            }
        }
    }

    fun showDeleteChatsDialog() {
        _uiState.update { it.copy(showDeleteChatsDialog = true) }
    }

    fun dismissDeleteChatsDialog() {
        _uiState.update { it.copy(showDeleteChatsDialog = false) }
    }

    fun deleteAllChats() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeletingChats = true) }
            try {
                chatRepository.deleteAllConversations()
                _uiState.update {
                    it.copy(
                        isDeletingChats = false,
                        showDeleteChatsDialog = false,
                        message = "All chat history deleted",
                        chatCount = 0
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isDeletingChats = false, error = e.message) }
            }
        }
    }

    fun showDeleteKeysDialog() {
        _uiState.update { it.copy(showDeleteKeysDialog = true) }
    }

    fun dismissDeleteKeysDialog() {
        _uiState.update { it.copy(showDeleteKeysDialog = false) }
    }

    fun deleteAllApiKeys() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeletingKeys = true) }
            try {
                val endpoints = endpointRepository.observeEndpoints().first()
                apiKeyStore.deleteAllKeys(endpoints.map { it.id })
                _uiState.update {
                    it.copy(
                        isDeletingKeys = false,
                        showDeleteKeysDialog = false,
                        message = "All API keys deleted"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isDeletingKeys = false, error = e.message) }
            }
        }
    }

    fun deleteEndpointKey(endpointId: Long) {
        viewModelScope.launch {
            try {
                apiKeyStore.deleteKey(endpointId)
                _uiState.update { it.copy(message = "API key deleted") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null, error = null) }
    }
}
