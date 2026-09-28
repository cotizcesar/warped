package com.warped.data.skills

import com.warped.data.repository.ModelAllowlistRepository
import com.warped.domain.skills.SkillIds

/**
 * 47-02 (D-04/D-07, LRT-08): pure gating decisions for local tool wiring.
 *
 * Single source of truth shared by [com.warped.data.local.inference.LiteRTLmProvider]
 * (ConversationConfig tools wiring) and `ChatViewModel` (no-support notice),
 * so both sides gate identically. JVM-testable: no engine types involved.
 *
 * Gating defaults CLOSED — per the verified-only rule no
 * `supportsFunctionCalling` flag flips without on-device evidence
 * (assumption A5); unsupported models fall back to prompt injection.
 */
sealed interface ToolGateDecision {
    /** Engine tools on: fresh ToolSets + automaticToolCalling=true. */
    data class UseTools(val ids: List<String>) : ToolGateDecision

    /** Skills enabled but model lacks support: no tools[], injection fallback + notice. */
    data class NoSupportFallback(val ids: List<String>) : ToolGateDecision

    /** Zero enabled: plain chat, no tools[], no notice. */
    data object PlainChat : ToolGateDecision
}

object ToolGating {

    fun decide(
        enabledIds: List<String>,
        supportsFunctionCalling: Boolean,
    ): ToolGateDecision {
        val ids = enabledIds.filter { it in SkillIds.TOOL_IDS }
        return when {
            ids.isEmpty() -> ToolGateDecision.PlainChat
            supportsFunctionCalling -> ToolGateDecision.UseTools(ids)
            else -> ToolGateDecision.NoSupportFallback(ids)
        }
    }

    /**
     * True only if the allowlist verifies function calling for the model file
     * behind [modelPath] (a file path — matched via `findByModelFile`).
     * Unknown paths and missing assets gate CLOSED.
     */
    fun supportsLocalTools(
        allowlist: ModelAllowlistRepository,
        modelPath: String?,
    ): Boolean {
        if (modelPath.isNullOrBlank()) return false
        val file = modelPath.substringAfterLast("/")
        return allowlist.findByModelFile(file)?.capabilities?.supportsFunctionCalling == true
    }

    /**
     * Prompt-injection fallback (v2.0 behavior preserved): skill one-liners
     * from the shared descriptors, injected as a system message when gating
     * says no-support.
     */
    fun fallbackSystemPrompt(ids: List<String>): String {
        val lines = ids.mapNotNull { skillDescriptor(it) }
            .joinToString("\n") { "- ${it.name}: ${it.description}" }
        return "This model has no function-calling support; answer directly " +
            "without claiming tool use. Enabled skills (background context only):\n$lines"
    }
}
