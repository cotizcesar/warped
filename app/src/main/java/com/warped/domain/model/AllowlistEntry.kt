package com.warped.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AllowlistEntry(
    val name: String,
    val displayName: String,
    val modelFile: String,
    val sizeInBytes: Long,
    val capabilities: List<String> = emptyList(),
    val llmPromptTemplates: Map<String, String> = emptyMap(),
    val taskTypes: List<String> = emptyList(),
) {
    val id: String get() = name

    fun hasCapability(cap: String): Boolean = cap in capabilities
}

@Serializable
data class ModelAllowlist(
    val models: List<AllowlistEntry> = emptyList(),
)
