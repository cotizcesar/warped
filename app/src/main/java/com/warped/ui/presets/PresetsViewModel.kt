package com.warped.ui.presets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warped.domain.model.GenerationParameters
import com.warped.domain.model.ParameterStore
import com.warped.domain.model.Preset
import com.warped.domain.repository.PresetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PresetsViewModel @Inject constructor(
    private val presetRepository: PresetRepository,
    private val parameterStore: ParameterStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(PresetsUiState())
    val uiState: StateFlow<PresetsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            presetRepository.observePresets().collect { presets ->
                _uiState.update { it.copy(presets = presets) }
            }
        }
    }

    fun updateTemperature(value: Float) {
        update { it.copy(temperature = value) }
    }

    fun updateTopP(value: Float) {
        update { it.copy(topP = value) }
    }

    fun updateTopK(value: Int) {
        update { it.copy(topK = value) }
    }

    fun updateRepeatPenalty(value: Float) {
        update { it.copy(repeatPenalty = value) }
    }

    fun updateMaxTokens(value: Int) {
        update { it.copy(maxTokens = value) }
    }

    fun updateContextSize(value: Int) {
        update { it.copy(contextSize = value) }
    }

    fun updateSeed(value: Int) {
        update { it.copy(seed = value) }
    }

    fun updateThreads(value: Int) {
        update { it.copy(threads = value) }
    }

    private inline fun update(crossinline transform: (GenerationParameters) -> GenerationParameters) {
        _uiState.update { state ->
            val newParams = transform(state.parameters)
            parameterStore.update(newParams)
            state.copy(parameters = newParams)
        }
    }

    fun loadPreset(preset: Preset) {
        val params = preset.toGenerationParameters()
        parameterStore.update(params)
        _uiState.update {
            it.copy(
                parameters = params,
                selectedPresetId = preset.id,
                selectedPresetName = preset.name
            )
        }
    }

    fun showSaveDialog() {
        _uiState.update { it.copy(saveDialogVisible = true, presetNameInput = it.selectedPresetName) }
    }

    fun dismissSaveDialog() {
        _uiState.update { it.copy(saveDialogVisible = false) }
    }

    fun updatePresetName(name: String) {
        _uiState.update { it.copy(presetNameInput = name) }
    }

    fun savePreset() {
        val state = _uiState.value
        if (state.presetNameInput.isBlank()) return

        viewModelScope.launch {
            try {
                val params = state.parameters
                val preset = Preset(
                    id = state.selectedPresetId ?: 0,
                    name = state.presetNameInput,
                    temperature = params.temperature,
                    topP = params.topP,
                    topK = params.topK,
                    repeatPenalty = params.repeatPenalty,
                    maxTokens = params.maxTokens,
                    contextSize = params.contextSize,
                    seed = params.seed,
                    threads = params.threads
                )
                val id = presetRepository.save(preset)
                _uiState.update {
                    it.copy(
                        selectedPresetId = id,
                        selectedPresetName = state.presetNameInput,
                        saveDialogVisible = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun deletePreset(id: Long) {
        viewModelScope.launch {
            try {
                presetRepository.delete(id)
                if (_uiState.value.selectedPresetId == id) {
                    _uiState.update { it.copy(selectedPresetId = null, selectedPresetName = "") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun resetToDefaults() {
        val defaults = GenerationParameters()
        parameterStore.update(defaults)
        _uiState.update {
            it.copy(
                parameters = defaults,
                selectedPresetId = null,
                selectedPresetName = ""
            )
        }
    }

    fun getParameters(): GenerationParameters = _uiState.value.parameters

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
