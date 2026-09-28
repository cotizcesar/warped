package com.warped.ui.settings

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
    val codeTheme: SyntaxTheme = SyntaxTheme.MONOKAI,
    val codeFontScale: Float = 1.0f,
    val webGroundingEnabled: Boolean = true,
    // Phase 55 (TAV-01): Tavily web-search key UI state. The stored key
    // itself is never held here — only the unsaved text-field input (cleared
    // on save/clear) plus presence + test-connection status.
    val tavilyKeyInput: String = "",
    val tavilyKeyPresent: Boolean = false,
    val tavilyTesting: Boolean = false,
    val tavilyStatus: String? = null,
    val tavilyStatusIsError: Boolean = false,
    val contextSize: Int = 4096,
    val maxTokens: Int = 2048,
    val selectedTab: SettingsTab = SettingsTab.General,
)
