package com.warped.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.local.security.ApiKeyStore
import com.warped.data.grounding.TavilySearchOutcome
import com.warped.data.grounding.TavilySearchRepository
import com.warped.domain.model.SyntaxTheme
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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch

import javax.inject.Inject
import timber.log.Timber

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val endpointRepository: EndpointRepository,
    private val localModelRepository: LocalModelRepository,
    private val presetRepository: PresetRepository,
    private val apiKeyStore: ApiKeyStore,
    private val advancedPreferences: AdvancedPreferences,
    private val tavilySearchRepository: TavilySearchRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable, "Unhandled coroutine exception")
    }

    init {
        viewModelScope.launch(coroutineExceptionHandler) {
            chatRepository.observeConversations().collect { conversations ->
                _uiState.update { it.copy(chatCount = conversations.size) }
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            endpointRepository.observeEndpoints().collect { endpoints ->
                _uiState.update { it.copy(endpointCount = endpoints.size) }
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            localModelRepository.observeModels().collect { models ->
                _uiState.update { it.copy(modelCount = models.size) }
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            presetRepository.observePresets().collect { presets ->
                _uiState.update { it.copy(presetCount = presets.size) }
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            advancedPreferences.syntaxTheme.collect { theme ->
                _uiState.update { it.copy(codeTheme = theme) }
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            advancedPreferences.codeFontScale.collect { scale ->
                _uiState.update { it.copy(codeFontScale = scale) }
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            advancedPreferences.webGroundingEnabled.collect { enabled ->
                _uiState.update { it.copy(webGroundingEnabled = enabled) }
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            refreshTavilyPresence()
        }
    }

    fun selectTab(tab: SettingsTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun setCodeTheme(theme: SyntaxTheme) {
        viewModelScope.launch(coroutineExceptionHandler) {
            advancedPreferences.setSyntaxTheme(theme)
        }
    }

    fun setCodeFontScale(scale: Float) {
        viewModelScope.launch(coroutineExceptionHandler) {
            advancedPreferences.setCodeFontScale(scale)
        }
    }

    fun setWebGroundingEnabled(enabled: Boolean) {
        viewModelScope.launch(coroutineExceptionHandler) {
            advancedPreferences.setWebGroundingEnabled(enabled)
        }
    }

    fun showDeleteChatsDialog() {
        _uiState.update { it.copy(showDeleteChatsDialog = true) }
    }

    fun dismissDeleteChatsDialog() {
        _uiState.update { it.copy(showDeleteChatsDialog = false) }
    }

    fun deleteAllChats() {
        viewModelScope.launch(coroutineExceptionHandler) {
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
        viewModelScope.launch(coroutineExceptionHandler) {
            _uiState.update { it.copy(isDeletingKeys = true) }
            try {
                val endpoints = endpointRepository.observeEndpoints().first()
                apiKeyStore.deleteAllKeys(endpoints.map { it.id })
                _uiState.update {
                    it.copy(
                        isDeletingKeys = false,
                        showDeleteKeysDialog = false,
                        message = "All API keys deleted",
                        // Phase 55 (TAV-01): deleteAllKeys already wipes the
                        // Tavily alias (55-01 coverage) — reset the card state.
                        tavilyKeyInput = "",
                        tavilyKeyPresent = false,
                        tavilyTesting = false,
                        tavilyStatus = null,
                        tavilyStatusIsError = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isDeletingKeys = false, error = e.message) }
            }
        }
    }

    fun deleteEndpointKey(endpointId: Long) {
        viewModelScope.launch(coroutineExceptionHandler) {
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

    // =========================================
    // Phase 55 (TAV-01): Tavily key row (D-01)
    // =========================================

    /**
     * Reads key presence from the `tavily_api_key` Keystore alias. The
     * returned chars are zeroed immediately — presence only, never held.
     * Best-effort: a store failure reads as absent, never a crash (mirrors
     * the ChatViewModel connectivity-gate discipline). Never logs key
     * material (T-55-05).
     */
    private fun refreshTavilyPresence() {
        val chars = try {
            apiKeyStore.getTavilyKey()
        } catch (e: Exception) {
            Timber.w(e, "Settings: Tavily key presence check failed")
            null
        }
        val present = chars != null
        chars?.fill('0')
        _uiState.update { it.copy(tavilyKeyPresent = present) }
    }

    fun onTavilyKeyInputChange(value: String) {
        _uiState.update { it.copy(tavilyKeyInput = value) }
    }

    fun saveTavilyKey() {
        val input = _uiState.value.tavilyKeyInput.trim()
        if (input.isBlank()) {
            _uiState.update {
                it.copy(
                    tavilyStatus = "Paste a key before saving.",
                    tavilyStatusIsError = true,
                )
            }
            return
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                // storeTavilyKey zeroes the passed array; the String in state
                // is cleared right after (T-55-05: no lingering plain key).
                apiKeyStore.storeTavilyKey(input.toCharArray())
                _uiState.update {
                    it.copy(
                        tavilyKeyInput = "",
                        tavilyKeyPresent = true,
                        tavilyStatus = "Tavily API key saved.",
                        tavilyStatusIsError = false,
                    )
                }
            } catch (e: Exception) {
                Timber.w(e, "Settings: Tavily key save failed")
                _uiState.update {
                    it.copy(
                        tavilyStatus = "Couldn't save the key. Try again.",
                        tavilyStatusIsError = true,
                    )
                }
            }
        }
    }

    fun clearTavilyKey() {
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                apiKeyStore.deleteTavilyKey()
                _uiState.update {
                    it.copy(
                        tavilyKeyInput = "",
                        tavilyKeyPresent = false,
                        tavilyTesting = false,
                        tavilyStatus = "Tavily API key deleted.",
                        tavilyStatusIsError = false,
                    )
                }
            } catch (e: Exception) {
                Timber.w(e, "Settings: Tavily key delete failed")
                _uiState.update {
                    it.copy(
                        tavilyStatus = "Couldn't delete the key. Try again.",
                        tavilyStatusIsError = true,
                    )
                }
            }
        }
    }

    /**
     * Cheapest truthful probe (T-55-08): `search("test", max_results = 1,
     * basic)` costs exactly 1 credit — the card copy discloses this. Maps
     * the repository outcomes to distinct English states; the probe runs on
     * Dispatchers.IO inside the repository, never the UI thread. Never logs
     * the key or payload (T-55-05).
     */
    fun testTavilyConnection() {
        if (_uiState.value.tavilyTesting) return
        viewModelScope.launch(coroutineExceptionHandler) {
            _uiState.update {
                it.copy(
                    tavilyTesting = true,
                    tavilyStatus = "Testing connection...",
                    tavilyStatusIsError = false,
                )
            }
            val (status, isError) = try {
                when (tavilySearchRepository.search(TEST_QUERY, maxResults = 1)) {
                    is TavilySearchOutcome.Grounded ->
                        "Connection successful. Tavily search is working." to false
                    TavilySearchOutcome.InvalidKey ->
                        "Invalid API key. Check the key and try again." to true
                    TavilySearchOutcome.UsageLimit ->
                        "Usage limit reached (429). Check your Tavily plan." to true
                    is TavilySearchOutcome.ModelOnly ->
                        "Network error. Check your connection and try again." to true
                    TavilySearchOutcome.MissingKey ->
                        "No API key saved. Get one at tavily.com and paste it above." to true
                }
            } catch (e: Exception) {
                Timber.w(e, "Settings: Tavily test-connection failed")
                "Network error. Check your connection and try again." to true
            }
            _uiState.update {
                it.copy(
                    tavilyTesting = false,
                    tavilyStatus = status,
                    tavilyStatusIsError = isError,
                )
            }
        }
    }

    companion object {
        private const val TEST_QUERY = "test"
    }
}
