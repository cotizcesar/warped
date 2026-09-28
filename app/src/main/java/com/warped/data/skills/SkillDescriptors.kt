package com.warped.data.skills

import com.warped.domain.skills.Skill
import com.warped.domain.skills.SkillIds
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * 47-01 (D-04): single shared Skill→schema source of truth.
 *
 * Each [SkillDescriptor] fans out to (a) the `@ToolParam` description strings
 * Plan 02 uses (written to MATCH — enforced by reflection test) and (b) the
 * OpenAI `parameters` JSON schema feeding `LmStudioToolFunction.parameters`
 * (remote loop, Plan 03). No string-templated JSON (threat T-47-01).
 */

// region @ToolParam description constants (single-write; Plan 02 imports these)

const val CALC_TOOL_DESCRIPTION = "Evaluate an arithmetic expression and return the result as a string."
const val CALC_EXPRESSION_DESCRIPTION =
    "Arithmetic expression using + - * / ( ) and decimals, e.g. (2+3)*4. Max 200 chars."

const val TIME_TOOL_DESCRIPTION = "Get the current date and time."
const val TIME_TIMEZONE_DESCRIPTION =
    "IANA timezone name, e.g. America/New_York. Defaults to device timezone."

const val JSON_TOOL_DESCRIPTION = "Format and validate a JSON string."
const val JSON_TEXT_DESCRIPTION = "JSON text to format and validate. Max 64KB."

// endregion

data class ParamSpec(
    val name: String,
    val jsonType: String,
    val description: String,
    val required: Boolean,
    val maxLength: Int? = null,
)

data class SkillDescriptor(
    val id: String,
    val name: String,
    val description: String,
    val params: List<ParamSpec>,
) {
    /**
     * Projection (b): OpenAI function `parameters` object
     * (`{type:object, properties, required}`).
     */
    fun toOpenAiParameters(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            params.forEach { p ->
                putJsonObject(p.name) {
                    put("type", p.jsonType)
                    put("description", p.description)
                    p.maxLength?.let { put("maxLength", it) }
                }
            }
        }
        putJsonArray("required") {
            params.filter { it.required }.forEach { add(JsonPrimitive(it.name)) }
        }
    }

    fun toTool(): Skill.Tool = Skill.Tool(id = id, displayName = name, description = description)
}

val SKILL_DESCRIPTORS: List<SkillDescriptor> = listOf(
    SkillDescriptor(
        id = SkillIds.CALCULATOR,
        name = SkillIds.CALCULATOR,
        description = CALC_TOOL_DESCRIPTION,
        params = listOf(
            ParamSpec(
                name = "expression",
                jsonType = "string",
                description = CALC_EXPRESSION_DESCRIPTION,
                required = true,
                maxLength = 200,
            ),
        ),
    ),
    SkillDescriptor(
        id = SkillIds.CURRENT_TIME,
        name = SkillIds.CURRENT_TIME,
        description = TIME_TOOL_DESCRIPTION,
        params = listOf(
            ParamSpec(
                name = "timezone",
                jsonType = "string",
                description = TIME_TIMEZONE_DESCRIPTION,
                required = false,
            ),
        ),
    ),
    SkillDescriptor(
        id = SkillIds.JSON_FORMATTER,
        name = SkillIds.JSON_FORMATTER,
        description = JSON_TOOL_DESCRIPTION,
        params = listOf(
            ParamSpec(
                name = "json",
                jsonType = "string",
                description = JSON_TEXT_DESCRIPTION,
                required = true,
                maxLength = 65536,
            ),
        ),
    ),
)

fun skillDescriptor(id: String): SkillDescriptor? = SKILL_DESCRIPTORS.firstOrNull { it.id == id }
