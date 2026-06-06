package com.warped.domain.skill

sealed class SkillCategory {
    data object Tool : SkillCategory()
    data object PromptTemplate : SkillCategory()
}

data class Skill(
    val id: String,
    val name: String,
    val description: String,
    val category: SkillCategory,
    val icon: String,
    val promptPrefix: String? = null,
    val systemPrompt: String? = null,
)
