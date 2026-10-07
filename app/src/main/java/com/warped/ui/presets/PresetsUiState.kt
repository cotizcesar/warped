package com.warped.ui.presets

import com.warped.domain.model.GenerationParameters
import com.warped.domain.model.Preset
import com.warped.domain.model.MemoryTier

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
    val error: String? = null,
    val smartPresetName: String? = null,
    val smartPresetTier: MemoryTier? = null,
    val availableGb: Float = 0f,
    val totalGb: Float = 0f,
    val isCustomOverride: Boolean = false,
    /**
     * 2026-10-04 params-mode select: PRESET = vendor/family tuning
     * applied (managed), CUSTOM = hand-editable values. Default per
     * model: tuned families open on PRESET, everything else on CUSTOM
     * prefilled with the smart-preset values. Any slider edit or saved
     * preset load moves to CUSTOM; applying the smart preset moves to
     * PRESET. In-memory (screen session); the default rule reproduces
     * the requested behavior on every model switch.
     */
    val paramsMode: ParamsMode = ParamsMode.CUSTOM,
    /** True when the active model matches a vendor family row. */
    val hasVendorTuning: Boolean = false
)

/** Source selector for the parameter values (Preset vs Custom). */
enum class ParamsMode {
    PRESET,
    CUSTOM
}
