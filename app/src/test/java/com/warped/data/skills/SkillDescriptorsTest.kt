package com.warped.data.skills

import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import com.google.common.truth.Truth.assertThat
import com.warped.domain.skills.SkillIds
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

class SkillDescriptorsTest {

    private val json = Json { prettyPrint = false }

    @Test
    fun `exactly three tool descriptors exist`() {
        assertThat(SKILL_DESCRIPTORS.map { it.id })
            .containsExactly(
                SkillIds.CALCULATOR,
                SkillIds.CURRENT_TIME,
                SkillIds.JSON_FORMATTER,
            )
            .inOrder()
    }

    @Test
    fun `calculator golden parameters JSON`() {
        val params = skillDescriptor(SkillIds.CALCULATOR)!!.toOpenAiParameters()
        assertThat(params["type"]!!.jsonPrimitive.content).isEqualTo("object")
        val props = params["properties"]!!.jsonObject
        assertThat(props.keys).containsExactly("expression")
        val expr = props["expression"]!!.jsonObject
        assertThat(expr["type"]!!.jsonPrimitive.content).isEqualTo("string")
        assertThat(expr["description"]!!.jsonPrimitive.content).isEqualTo(CALC_EXPRESSION_DESCRIPTION)
        assertThat(expr["maxLength"]!!.jsonPrimitive.content).isEqualTo("200")
        assertThat(params["required"]!!.jsonArray.map { it.jsonPrimitive.content })
            .containsExactly("expression")
        // Wire-shape pin (threat T-47-01): exact serialized form.
        assertThat(json.encodeToString(JsonObject.serializer(), params)).isEqualTo(
            """{"type":"object","properties":{"expression":{"type":"string","description":"$CALC_EXPRESSION_DESCRIPTION","maxLength":200}},"required":["expression"]}"""
        )
    }

    @Test
    fun `current_time golden parameters JSON has optional timezone`() {
        val params = skillDescriptor(SkillIds.CURRENT_TIME)!!.toOpenAiParameters()
        val props = params["properties"]!!.jsonObject
        assertThat(props.keys).containsExactly("timezone")
        assertThat(props["timezone"]!!.jsonObject["description"]!!.jsonPrimitive.content)
            .isEqualTo(TIME_TIMEZONE_DESCRIPTION)
        assertThat(params["required"]!!.jsonArray).isEmpty()
    }

    @Test
    fun `json_formatter golden parameters JSON`() {
        val params = skillDescriptor(SkillIds.JSON_FORMATTER)!!.toOpenAiParameters()
        val props = params["properties"]!!.jsonObject
        assertThat(props.keys).containsExactly("json")
        assertThat(props["json"]!!.jsonObject["maxLength"]!!.jsonPrimitive.content).isEqualTo("65536")
        assertThat(params["required"]!!.jsonArray.map { it.jsonPrimitive.content })
            .containsExactly("json")
    }

    @Test
    fun `descriptor descriptions equal exported TOOL_PARAM constants (no drift)`() {        // Drift-by-construction guard (SKILLS-09): the strings Plan 02 wires
        // into @ToolParam MUST be these constants. If a description is edited
        // in one place but not the other, this fails.
        val calc = skillDescriptor(SkillIds.CALCULATOR)!!
        assertThat(calc.description).isEqualTo(CALC_TOOL_DESCRIPTION)
        assertThat(calc.params.single { it.name == "expression" }.description)
            .isEqualTo(CALC_EXPRESSION_DESCRIPTION)

        val time = skillDescriptor(SkillIds.CURRENT_TIME)!!
        assertThat(time.description).isEqualTo(TIME_TOOL_DESCRIPTION)
        assertThat(time.params.single { it.name == "timezone" }.description)
            .isEqualTo(TIME_TIMEZONE_DESCRIPTION)

        val jsonFmt = skillDescriptor(SkillIds.JSON_FORMATTER)!!
        assertThat(jsonFmt.description).isEqualTo(JSON_TOOL_DESCRIPTION)
        assertThat(jsonFmt.params.single { it.name == "json" }.description)
            .isEqualTo(JSON_TEXT_DESCRIPTION)
    }

    // 47-02 (SKILLS-09 local side): @Tool/@ToolParam descriptions MUST be the
    // descriptor constants verbatim — reflection over the ToolSets proves it.
    // Plain Java reflection: no kotlin-reflect, JVM-safe (no engine init).

    @Test
    fun `calculator ToolSet annotations match descriptors`() {
        assertToolShape(
            CalculatorToolSet::class.java,
            CALC_TOOL_DESCRIPTION,
            listOf(CALC_EXPRESSION_DESCRIPTION),
        )
    }

    @Test
    fun `current_time ToolSet annotations match descriptors`() {
        assertToolShape(
            CurrentTimeToolSet::class.java,
            TIME_TOOL_DESCRIPTION,
            listOf(TIME_TIMEZONE_DESCRIPTION),
        )
    }

    @Test
    fun `json_formatter ToolSet annotations match descriptors`() {
        assertToolShape(
            JsonFormatterToolSet::class.java,
            JSON_TOOL_DESCRIPTION,
            listOf(JSON_TEXT_DESCRIPTION),
        )
    }

    @Test
    fun `all three skill files expose a ToolSet`() {
        val toolSets = listOf(
            CalculatorToolSet::class.java,
            CurrentTimeToolSet::class.java,
            JsonFormatterToolSet::class.java,
        )
        for (cls in toolSets) {
            assertThat(ToolSet::class.java.isAssignableFrom(cls)).isTrue()
        }
    }

    private fun assertToolShape(
        toolSet: Class<*>,
        toolDescription: String,
        paramDescriptions: List<String>,
    ) {
        val methods = toolSet.declaredMethods
            .filter { it.isAnnotationPresent(Tool::class.java) }
        assertThat(methods).hasSize(1)
        val method = methods.single()
        assertThat(method.getAnnotation(Tool::class.java).description)
            .isEqualTo(toolDescription)
        // Positional (parameter names need -parameters): count + descriptions.
        assertThat(method.parameters).hasLength(paramDescriptions.size)
        val actual = method.parameters.map {
            it.getAnnotation(ToolParam::class.java)?.description
        }
        assertThat(actual).containsExactlyElementsIn(paramDescriptions).inOrder()
    }
}
