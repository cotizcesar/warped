package com.warped.domain.skills

/**
 * 47-01: Skills Lite surface (tracer foundation for Plans 02/03).
 *
 * Sealed [Skill] hierarchy per CONTEXT locked decision: exactly 3 real tools
 * (Calculator, CurrentTime, JsonFormatter). Summarize stays a [Skill.PromptTemplate]
 * persona — it gets no chip and no function.
 */
sealed interface Skill {
    val id: String
    val category: SkillCategory

    data class Tool(
        override val id: String,
        val displayName: String,
        val description: String,
    ) : Skill {
        override val category: SkillCategory = SkillCategory.Tool
    }

    data class PromptTemplate(
        override val id: String,
        val systemPrompt: String,
    ) : Skill {
        override val category: SkillCategory = SkillCategory.PromptTemplate
    }
}

enum class SkillCategory { Tool, PromptTemplate }

/**
 * Fixed skill ids. These are DataStore keys and tool names — never rendered
 * directly (threat T-47-03: labels are fixed constants, ids never shown).
 */
object SkillIds {
    const val CALCULATOR = "calculator"
    const val CURRENT_TIME = "current_time"
    const val JSON_FORMATTER = "json_formatter"
    const val SUMMARIZE = "summarize"

    val TOOL_IDS: List<String> = listOf(CALCULATOR, CURRENT_TIME, JSON_FORMATTER)
}

/** User-facing chip labels (UI-SPEC §8 copy deck, exact strings). */
fun skillChipLabel(skillId: String): String = when (skillId) {
    SkillIds.CALCULATOR -> "Calculator"
    SkillIds.CURRENT_TIME -> "Current time"
    SkillIds.JSON_FORMATTER -> "JSON format"
    else -> skillId
}

/**
 * Lowercase sentence-style display name used in live status
 * ("Using {display}…") and transcript headers (capitalized).
 *
 * UI-SPEC §3 map: calculator → "calculator",
 * current_time → "current time", json_formatter → "JSON formatter".
 * Unknown ids degrade to underscore→space (never crash, never blank).
 */
fun toolDisplayName(toolId: String): String = when (toolId) {
    SkillIds.CALCULATOR -> "calculator"
    SkillIds.CURRENT_TIME -> "current time"
    SkillIds.JSON_FORMATTER -> "JSON formatter"
    else -> toolId.replace('_', ' ').ifBlank { toolId }
}

/** `{Display}` casing rule: first letter capitalized (acronyms keep caps). */
fun toolDisplayNameCapitalized(toolId: String): String =
    toolDisplayName(toolId).replaceFirstChar { it.uppercase() }
