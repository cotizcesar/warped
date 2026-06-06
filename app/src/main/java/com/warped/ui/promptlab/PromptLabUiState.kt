package com.warped.ui.promptlab

import com.warped.domain.prompt.PromptTemplate

data class PromptLabUiState(
    val templates: List<PromptTemplate> = emptyList(),
    val selectedTemplateId: String? = null,
    val input: String = "",
    val output: String = "",
    val isRunning: Boolean = false,
    val error: String? = null,
    val activeModelId: String? = null,
    val targetLanguage: String = "English",
)
