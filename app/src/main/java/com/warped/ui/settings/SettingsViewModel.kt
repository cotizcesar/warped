package com.warped.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.local.security.ApiKeyStore
import com.warped.domain.model.SyntaxTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch

import javax.inject.Inject
import com.warped.R
import timber.log.Timber

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val apiKeyStore: ApiKeyStore,
    private val advancedPreferences: AdvancedPreferences,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable, "Unhandled coroutine exception")
    }

    init {
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

    fun deleteEndpointKey(endpointId: Long) {
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                apiKeyStore.deleteKey(endpointId)
                _uiState.update { it.copy(message = context.getString(R.string.settings_msg_key_deleted)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null, error = null) }
    }
}
