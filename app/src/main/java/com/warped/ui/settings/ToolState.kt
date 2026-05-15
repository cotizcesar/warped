package com.warped.ui.settings

data class ToolState(
    val id: String,
    val name: String,
    val description: String,
    val tokenEstimate: Int,
    val defaultEnabled: Boolean,
    val enabled: Boolean,
)
