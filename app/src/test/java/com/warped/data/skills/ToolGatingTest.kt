package com.warped.data.skills

import com.google.common.truth.Truth.assertThat
import com.warped.data.repository.ModelAllowlistRepository
import com.warped.domain.skills.SkillIds
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

/**
 * 47-02 (D-07, LRT-08): config-builder tests — enabled-chips × allowlist →
 * gate decision (+ automaticToolCalling mapping), with fakes. The provider
 * builds `ConversationConfig(tools, automaticToolCalling)` from exactly this
 * decision; empty→PlainChat and gated→NoSupportFallback branches pinned here.
 */
class ToolGatingTest {

    private val allIds = SkillIds.TOOL_IDS

    @Test
    fun `zero enabled is plain chat even when supported`() {
        assertThat(ToolGating.decide(emptyList(), true))
            .isEqualTo(ToolGateDecision.PlainChat)
    }

    @Test
    fun `enabled plus support uses tools`() {
        val decision = ToolGating.decide(allIds, true)
        assertThat(decision).isEqualTo(ToolGateDecision.UseTools(allIds))
        // automaticToolCalling mapping: true iff UseTools.
        assertThat(decision is ToolGateDecision.UseTools).isTrue()
    }

    @Test
    fun `enabled without support falls back with notice`() {
        val decision = ToolGating.decide(allIds, false)
        assertThat(decision).isEqualTo(ToolGateDecision.NoSupportFallback(allIds))
        assertThat(decision is ToolGateDecision.UseTools).isFalse()
    }

    @Test
    fun `unknown ids never reach tools`() {
        assertThat(ToolGating.decide(listOf("calculator", "rm_rf"), true))
            .isEqualTo(ToolGateDecision.UseTools(listOf("calculator")))
        // Unknown-only degrades to plain chat (never executed).
        assertThat(ToolGating.decide(listOf("rm_rf"), true))
            .isEqualTo(ToolGateDecision.PlainChat)
    }

    @Test
    fun `supportsLocalTools gates closed on unknown paths`() {
        val allowlist = mockk<ModelAllowlistRepository>()
        every { allowlist.findByModelFile(any()) } returns null
        assertThat(ToolGating.supportsLocalTools(allowlist, null)).isFalse()
        assertThat(ToolGating.supportsLocalTools(allowlist, "")).isFalse()
        assertThat(
            ToolGating.supportsLocalTools(allowlist, "/data/models/mystery.litertlm")
        ).isFalse()
    }

    @Test
    fun `fallback prompt carries descriptor one-liners`() {
        val prompt = ToolGating.fallbackSystemPrompt(allIds)
        assertThat(prompt).contains("no function-calling support")
        assertThat(prompt).contains(CALC_TOOL_DESCRIPTION)
        assertThat(prompt).contains(TIME_TOOL_DESCRIPTION)
        assertThat(prompt).contains(JSON_TOOL_DESCRIPTION)
    }
}
