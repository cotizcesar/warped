package com.warped.ui.wizard

data class WizardUiState(
    val currentPage: Int = 0,
    val isWizardComplete: Boolean = false,
    val skippedSteps: Set<String> = emptySet(),
    val showSkipAllConfirm: Boolean = false
)
