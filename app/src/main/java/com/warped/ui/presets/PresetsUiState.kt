package com.warped.ui.presets

import com.warped.domain.model.GenerationParameters
import com.warped.domain.model.Preset

data class PresetsUiState(
    val parameters: GenerationParameters = GenerationParameters(),
    val presets: List<Preset> = emptyList(),
    val selectedPresetId: Long? = null,
    val selectedPresetName: String = "",
    val isSaving: Boolean = false,
    val saveDialogVisible: Boolean = false,
    val presetNameInput: String = "",
    val activeFormat: String = "LITERTLM",
    val showFormatWarning: Boolean = false,
    val formatWarningPreset: Preset? = null,
    val error: String? = null
)
