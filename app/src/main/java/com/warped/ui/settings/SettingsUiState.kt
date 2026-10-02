package com.warped.ui.settings

import com.warped.domain.model.SyntaxTheme

data class SettingsUiState(
    val message: String? = null,
    val error: String? = null,
    val codeTheme: SyntaxTheme = SyntaxTheme.MONOKAI,
    val codeFontScale: Float = 1.0f,
    val webGroundingEnabled: Boolean = true,
    val contextSize: Int = 4096,
    val maxTokens: Int = 2048,
    val selectedTab: SettingsTab = SettingsTab.General,
)
