package com.warped.ui.wizard

data class WizardUiState(
    val currentPage: Int = 0,
    val isWizardComplete: Boolean = false,
    val skippedSteps: Set<String> = emptySet(),
    val showSkipAllConfirm: Boolean = false,
    val showExitConfirm: Boolean = false,
    val isReEntry: Boolean = false,
    val contextData: WizardContextData = WizardContextData()
)

data class WizardContextData(
    val litertlmModelCount: Int = 0,
    val endpointCount: Int = 0,
    val chatCount: Int = 0,
    val presetCount: Int = 0
)
