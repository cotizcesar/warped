package com.warped.ui.settings

import com.warped.domain.model.GenerationParameters
import com.warped.domain.model.SyntaxTheme

data class SettingsUiState(
    val isDeletingChats: Boolean = false,
    val isDeletingKeys: Boolean = false,
    val chatCount: Int = 0,
    val endpointCount: Int = 0,
    val modelCount: Int = 0,
    val presetCount: Int = 0,
    val showDeleteChatsDialog: Boolean = false,
    val showDeleteKeysDialog: Boolean = false,
    val showDeleteEndpointDialog: Long? = null,
    val message: String? = null,
    val error: String? = null,
    val hfToken: String = "",
    val hasHfToken: Boolean = false,
    val codeTheme: SyntaxTheme = SyntaxTheme.MONOKAI,
    val codeFontScale: Float = 1.0f,
    val advancedParams: GenerationParameters = GenerationParameters(),
    val toolStates: List<ToolState> = emptyList(),
    val enabledToolIds: Set<String> = emptySet(),
    val selectedTab: SettingsTab = SettingsTab.General,
    val manualToolCalling: Boolean = false,
)
