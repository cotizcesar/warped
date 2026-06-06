package com.warped.domain.prompt

data class PromptTemplate(
    val id: String,
    val name: String,
    val description: String,
    val systemPrompt: String,
    val userPromptTemplate: (Map<String, String>) -> String,
    val outputMarkdown: Boolean = true,
    val requiresLanguage: Boolean = false,
)
