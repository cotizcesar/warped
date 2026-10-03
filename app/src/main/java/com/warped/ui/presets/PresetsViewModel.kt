package com.warped.ui.presets

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warped.R
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.MemoryChecker
import com.warped.domain.model.*
import com.warped.domain.repository.PresetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch

import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PresetsViewModel @Inject constructor(
    private val presetRepository: PresetRepository,
    private val parameterStore: ParameterStore,
    private val engineManager: EngineManager,
    private val memoryChecker: MemoryChecker,
    private val activeModelSelection: ActiveModelSelection,
    private val localModelRepository: com.warped.domain.repository.LocalModelRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(PresetsUiState())
    val uiState: StateFlow<PresetsUiState> = _uiState.asStateFlow()

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        timber.log.Timber.e(throwable, "Unhandled coroutine exception")
    }

    private var smartPresetParams: GenerationParameters? = null
    private var cachedModels: List<LocalModel> = emptyList()

    init {
        viewModelScope.launch(coroutineExceptionHandler) {
            presetRepository.observePresets().collect { presets ->
                _uiState.update { it.copy(presets = presets) }
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            localModelRepository.observeModels().collect { models ->
                cachedModels = models
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            activeModelSelection.localSelection.collect { local ->
                if (local.modelId != null) {
                    recalculateSmartPreset(local.modelId)
                }
            }
        }
        refreshActiveFormat()
        viewModelScope.launch(coroutineExceptionHandler) { refreshSmartPreset() }
    }

    /** Derive the currently active format string from the loaded engine. */
    private fun refreshActiveFormat() {
        val format = "LITERTLM"
        _uiState.update { it.copy(activeFormat = format) }
        Timber.d("PresetsViewModel: activeFormat=$format")
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

    fun updateReasoning(enabled: Boolean) {
        update { it.copy(reasoningEnabled = enabled) }
    }

    private inline fun update(crossinline transform: (GenerationParameters) -> GenerationParameters) {
        _uiState.update { state ->
            val newParams = transform(state.parameters)
            parameterStore.update(newParams)
            val isCustom = smartPresetParams == null || newParams != smartPresetParams
            state.copy(parameters = newParams, isCustomOverride = isCustom, selectedPresetName = if (isCustom) context.getString(R.string.preset_custom_name) else state.selectedPresetName)
        }
    }

    fun applySmartPreset() {
        val smart = smartPresetParams ?: return
        parameterStore.update(smart)
        _uiState.update {
            it.copy(
                parameters = smart,
                isCustomOverride = false,
                selectedPresetId = null,
                selectedPresetName = it.smartPresetName ?: context.getString(R.string.preset_smart_name)
            )
        }
    }

    private suspend fun refreshSmartPreset() {
        val localId = activeModelSelection.localSelection.value.modelId
        if (localId != null) recalculateSmartPreset(localId)
    }

    private fun recalculateSmartPreset(modelId: String) {
        val model = cachedModels.firstOrNull { it.filePath == modelId } ?: return
        val memInfo = memoryChecker.getMemoryInfo()
        val result = SmartPresetCalculator.calculate(memInfo, model.sizeBytes, modelName = model.name)
        smartPresetParams = result.parameters
        val label = context.getString(R.string.preset_smart_fmt, "%.1f".format(result.availableGb))
        _uiState.update {
            it.copy(
                smartPresetName = label,
                smartPresetTier = result.tier,
                availableGb = result.availableGb,
                totalGb = result.totalGb,
                isCustomOverride = it.isCustomOverride || (it.parameters != result.parameters)
            )
        }
    }

    fun loadPreset(preset: Preset) {
        refreshActiveFormat()
        val currentFormat = _uiState.value.activeFormat
        val engineLoaded = engineManager.isEngineLoaded()

        // Cross-format detection: only relevant when a local engine is loaded
        if (engineLoaded && preset.modelFormat != currentFormat) {
            Timber.d("PresetsViewModel: cross-format load — preset=${preset.modelFormat} active=$currentFormat")
            _uiState.update {
                it.copy(showFormatWarning = true, formatWarningPreset = preset)
            }
            return
        }

        applyPresetParameters(preset)
    }

    /** User confirmed loading a cross-format preset — apply only compatible params. */
    fun confirmLoadPreset() {
        val preset = _uiState.value.formatWarningPreset ?: return
        applyPresetParameters(preset)
        _uiState.update { it.copy(showFormatWarning = false, formatWarningPreset = null) }
    }

    /** User dismissed the cross-format warning — cancel load. */
    fun dismissFormatWarning() {
        _uiState.update { it.copy(showFormatWarning = false, formatWarningPreset = null) }
    }

    /** Apply preset parameters to current state and parameter store. */
    private fun applyPresetParameters(preset: Preset) {
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

        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                val params = state.parameters
                refreshActiveFormat()
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
                    threads = params.threads,
                    modelFormat = state.activeFormat
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
        viewModelScope.launch(coroutineExceptionHandler) {
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
