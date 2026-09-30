package com.warped.data.agentic

import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Phase 56 (56-01): no-drift pins for the fixed two-tool surface (47
 * precedent). Exactly `web_search(query: String)` and
 * `web_fetch(url: String)` exist with non-empty descriptions; stub bodies
 * return [HOST_EXECUTED] (schema only — never real logic, never
 * `runBlocking`).
 *
 * Param NAMES (`query`, `url`) are compile-time source — JVM reflection
 * cannot see them without `-java-parameters`, so this test pins names via
 * method identity plus the single-`String`-param shape; any rename breaks
 * engine dispatch AND the Phase-57 `tools[]` mapping loudly.
 */
class ToolSetSchemaTest {

    @Test
    fun `web_search schema is exactly one String param with descriptions`() {
        val toolMethods = WebSearchToolSet::class.java.declaredMethods
            .filter { it.isAnnotationPresent(Tool::class.java) }

        assertThat(toolMethods).hasSize(1)
        val method = toolMethods.single()
        assertThat(method.name).isEqualTo("web_search")
        assertThat(method.parameterTypes.toList()).containsExactly(String::class.java)

        val tool = method.getAnnotation(Tool::class.java)
        assertThat(tool?.description).isNotEmpty()

        val param = method.parameterAnnotations.single().single() as ToolParam
        assertThat(param.description).isNotEmpty()
    }

    @Test
    fun `web_fetch schema is exactly one String param with descriptions`() {
        val toolMethods = WebFetchToolSet::class.java.declaredMethods
            .filter { it.isAnnotationPresent(Tool::class.java) }

        assertThat(toolMethods).hasSize(1)
        val method = toolMethods.single()
        assertThat(method.name).isEqualTo("web_fetch")
        assertThat(method.parameterTypes.toList()).containsExactly(String::class.java)

        val tool = method.getAnnotation(Tool::class.java)
        assertThat(tool?.description).isNotEmpty()

        val param = method.parameterAnnotations.single().single() as ToolParam
        assertThat(param.description).isNotEmpty()
    }

    @Test
    fun `both toolsets implement ToolSet`() {
        assertThat(WebSearchToolSet()).isInstanceOf(ToolSet::class.java)
        assertThat(WebFetchToolSet()).isInstanceOf(ToolSet::class.java)
    }

    @Test
    fun `stub bodies are schema-only and return host marker`() {
        // Proves the bodies hold no logic: safe to construct fresh per
        // conversation (47 precedent) with zero side effects.
        assertThat(WebSearchToolSet().web_search("anything")).isEqualTo(HOST_EXECUTED)
        assertThat(WebFetchToolSet().web_fetch("https://example.com")).isEqualTo(HOST_EXECUTED)
    }

    @Test
    fun `web_search description carries the re-search rule`() {
        // Quick-task (agentic-rows): this description is the prompt surface
        // every armed loop sees — local @Tool schema, OpenAI tools[], and
        // Anthropic tools all copy it verbatim.
        assertThat(WEB_SEARCH_TOOL_DESCRIPTION)
            .contains("call again instead of answering from stale results")
    }
}
