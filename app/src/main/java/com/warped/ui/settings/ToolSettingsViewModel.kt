package com.warped.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warped.data.local.inference.tools.ToolDefinitions
import com.warped.data.local.inference.tools.ToolPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch

import javax.inject.Inject
import timber.log.Timber

data class ToolSettingsUiState(
    val toolStates: List<ToolState> = ToolDefinitions.all.map {
        ToolState(it.id, it.name, it.description, it.tokenEstimate, it.defaultEnabled, it.defaultEnabled)
    },
    val enabledIds: Set<String> = emptySet()
)

@HiltViewModel
class ToolSettingsViewModel @Inject constructor(
    private val preferences: ToolPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ToolSettingsUiState())
    val uiState: StateFlow<ToolSettingsUiState> = _uiState.asStateFlow()

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        timber.log.Timber.e(throwable, "Unhandled coroutine exception")
    }

    init {
        viewModelScope.launch(coroutineExceptionHandler) {
            preferences.enabledTools.collect { enabledIds ->
                _uiState.update { current ->
                    current.copy(
                        enabledIds = enabledIds,
                        toolStates = ToolDefinitions.all.map { tool ->
                            ToolState(
                                id = tool.id,
                                name = tool.name,
                                description = tool.description,
                                tokenEstimate = tool.tokenEstimate,
                                defaultEnabled = tool.defaultEnabled,
                                enabled = tool.id in enabledIds
                            )
                        }
                    )
                }
            }
        }
    }

    fun toggleTool(toolId: String) {
        viewModelScope.launch(coroutineExceptionHandler) {
            val current = _uiState.value.toolStates.find { it.id == toolId } ?: return@launch
            preferences.setEnabled(toolId, !current.enabled)
        }
    }
}
