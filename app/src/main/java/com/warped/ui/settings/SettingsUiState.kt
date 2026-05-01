package com.warped.ui.settings

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
    val error: String? = null
)
