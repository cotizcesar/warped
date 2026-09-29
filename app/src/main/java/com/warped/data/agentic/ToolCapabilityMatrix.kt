package com.warped.data.agentic

import com.warped.domain.model.ProviderType

/**
 * Phase 57 (57-01): per-provider tool-attempt mode for the remote agentic
 * loop (AGENT-03, locked Capability Gating decision, RESEARCH Pattern 5).
 *
 * The matrix lives in code (not remote config). Unknown/custom servers
 * default to attempt-then-fallback: one `tools[]` attempt, then exactly one
 * retry without tools plus a visible notice — never silent, never a hard
 * error.
 */
enum class ToolMode {
    /** Native Chat Completions `tools[]`; retry only on 400-rejection. */
    ATTEMPT,

    /** Unknown/variable servers: attempt once, fall back gracefully. */
    ATTEMPT_FALLBACK,

    /** Anthropic native dialect (`tools`/`tool_use`/`tool_result`) — 57-02. */
    NATIVE_ANTHROPIC,

    /**
     * Non-remote sentinel (local LiteRT-LM loop owns its own arming).
     * Never passes [ToolCapabilityMatrix.attemptsTools].
     */
    NO_REMOTE_TOOLS,
}

/**
 * Phase 57 (57-01): static capability matrix + `tools[]`-rejection
 * classifier + retry notice copy + remote-arm predicate (AGENT-03).
 *
 * Pure JVM-testable object — zero network imports (47 `ToolGating` / 56-01
 * precedent). Every function is total (never throws).
 */
object ToolCapabilityMatrix {

    /**
     * Retry-without-tools notice (RESEARCH Pattern 5 draft, locked: English,
     * actionable). Emitted as render copy for the `TOOLS_UNSUPPORTED`
     * banner slot when a server rejects `tools[]`; the turn otherwise
     * completes normally. Routing keys on the typed
     * `StreamToken.ToolsUnsupported` token — never on this string — so
     * copy edits stay render-only. Ends with the next step (switch to a
     * tool-capable endpoint) so the banner is never a dead end.
     */
    const val TOOLS_UNSUPPORTED_NOTICE =
        "This endpoint doesn't support tool calling. Model-only answer — " +
            "no web sources this turn. Switch to a tool-capable endpoint " +
            "to restore search."

    /**
     * Static map `ProviderType` → [ToolMode]. Ollama rides the OpenAI-compat
     * `/v1` shape (Tools:✓ per Ollama docs); LM Studio serves OpenAI-compat
     * `/v1` model-dependently (LOW confidence — the fallback absorbs a wrong
     * pick); `LITE_RT_LM`/`LOCAL` are excluded via the non-remote sentinel.
     */
    fun modeFor(type: ProviderType): ToolMode = when (type) {
        ProviderType.OPENAI -> ToolMode.ATTEMPT
        ProviderType.CUSTOM -> ToolMode.ATTEMPT_FALLBACK
        ProviderType.OLLAMA -> ToolMode.ATTEMPT
        ProviderType.LM_STUDIO -> ToolMode.ATTEMPT_FALLBACK
        ProviderType.ANTHROPIC -> ToolMode.NATIVE_ANTHROPIC
        ProviderType.LITE_RT_LM -> ToolMode.NO_REMOTE_TOOLS
        ProviderType.LOCAL -> ToolMode.NO_REMOTE_TOOLS
    }

    /**
     * True when the mode sends Chat Completions `tools[]` (attempting, with
     * or without fallback). `NATIVE_ANTHROPIC` and `NO_REMOTE_TOOLS` never
     * send the OpenAI dialect shape.
     */
    fun attemptsTools(mode: ToolMode): Boolean =
        mode == ToolMode.ATTEMPT || mode == ToolMode.ATTEMPT_FALLBACK

    /**
     * Pure rejection classifier: `code == 400` plus an error body naming the
     * feature (`tool`, `tool_calls`, `function`, `tool_use` — all contain the
     * `tool` or `function` substring, matched case-insensitively) means the
     * server rejected `tools[]` → exactly one retry without tools plus
     * [TOOLS_UNSUPPORTED_NOTICE]. Anything else keeps the existing error
     * path unchanged. Null-body safe; never throws.
     */
    fun isToolsRejection(httpCode: Int, errorBody: String?): Boolean {
        if (httpCode != 400) return false
        if (errorBody.isNullOrEmpty()) return false
        return try {
            val body = errorBody.lowercase()
            "tool" in body || "function" in body
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Phase 57 (WR-05 fix): Anthropic thinking-shape rejection. Follow-up
     * rounds whose echo a strict endpoint rejects for thinking/signature
     * reasons 400 with a body naming `thinking`/`signature` — never the
     * `tool`/`function` substring [isToolsRejection] matches. Same
     * exactly-one retry without tools plus [TOOLS_UNSUPPORTED_NOTICE].
     * Anthropic-scoped by convention (only the Anthropic driver calls it);
     * total, never throws.
     */
    fun isAnthropicThinkingRejection(httpCode: Int, errorBody: String?): Boolean {
        if (httpCode != 400) return false
        if (errorBody.isNullOrEmpty()) return false
        return try {
            val body = errorBody.lowercase()
            "thinking" in body || "signature" in body
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Phase 57 remote-arm predicate (Pitfall 5: the plan-02 VM pre-search
     * skip mirrors this). All three inputs are ANDed — grounding off,
     * matrix refusing the OpenAI dialect, or unvalidated internet each
     * independently force the exact pre-57 plain-turn behavior.
     */
    fun isRemoteLoopArmed(
        groundingOn: Boolean,
        matrixAttemptsTools: Boolean,
        hasValidatedInternet: Boolean,
    ): Boolean = groundingOn && matrixAttemptsTools && hasValidatedInternet
}
