package com.warped.data.agentic

import com.google.common.truth.Truth.assertThat
import com.warped.domain.model.ProviderType
import org.junit.jupiter.api.Test

/**
 * Phase 57 (57-01): exit gates for the static capability matrix, the
 * `tools[]`-rejection classifier, and the remote-arm predicate.
 *
 * Every case is JVM-local: the matrix and classifier are pure policy with
 * zero network imports (47 `ToolGating` / 56-01 precedent).
 */
class ToolCapabilityMatrixTest {

    // Matrix contents per ProviderType.

    @Test
    fun `openai attempts natively`() {
        assertThat(ToolCapabilityMatrix.modeFor(ProviderType.OPENAI)).isEqualTo(ToolMode.ATTEMPT)
    }

    @Test
    fun `custom defaults to attempt then fallback`() {
        assertThat(ToolCapabilityMatrix.modeFor(ProviderType.CUSTOM))
            .isEqualTo(ToolMode.ATTEMPT_FALLBACK)
    }

    @Test
    fun `ollama attempts via openai compat shape`() {
        assertThat(ToolCapabilityMatrix.modeFor(ProviderType.OLLAMA)).isEqualTo(ToolMode.ATTEMPT)
    }

    @Test
    fun `lm studio attempts then falls back`() {
        assertThat(ToolCapabilityMatrix.modeFor(ProviderType.LM_STUDIO))
            .isEqualTo(ToolMode.ATTEMPT_FALLBACK)
    }

    @Test
    fun `anthropic routes to its native dialect`() {
        assertThat(ToolCapabilityMatrix.modeFor(ProviderType.ANTHROPIC))
            .isEqualTo(ToolMode.NATIVE_ANTHROPIC)
    }

    @Test
    fun `local types are excluded via the non remote sentinel`() {
        assertThat(ToolCapabilityMatrix.modeFor(ProviderType.LITE_RT_LM))
            .isEqualTo(ToolMode.NO_REMOTE_TOOLS)
        assertThat(ToolCapabilityMatrix.modeFor(ProviderType.LOCAL))
            .isEqualTo(ToolMode.NO_REMOTE_TOOLS)
    }

    @Test
    fun `only openai dialect modes attempt tools`() {
        assertThat(ToolCapabilityMatrix.attemptsTools(ToolMode.ATTEMPT)).isTrue()
        assertThat(ToolCapabilityMatrix.attemptsTools(ToolMode.ATTEMPT_FALLBACK)).isTrue()
        assertThat(ToolCapabilityMatrix.attemptsTools(ToolMode.NATIVE_ANTHROPIC)).isFalse()
        assertThat(ToolCapabilityMatrix.attemptsTools(ToolMode.NO_REMOTE_TOOLS)).isFalse()
    }

    // Rejection classifier.

    @Test
    fun `four hundred naming tools is a rejection`() {
        assertThat(ToolCapabilityMatrix.isToolsRejection(400, "Unsupported parameter: 'tools'")).isTrue()
        assertThat(ToolCapabilityMatrix.isToolsRejection(400, "Unknown field tool_calls")).isTrue()
        assertThat(ToolCapabilityMatrix.isToolsRejection(400, "function calling not supported")).isTrue()
        assertThat(ToolCapabilityMatrix.isToolsRejection(400, "TOOL_USE is not available")).isTrue()
    }

    @Test
    fun `four hundred without a feature mention is not a rejection`() {
        assertThat(ToolCapabilityMatrix.isToolsRejection(400, "Invalid request: bad model id")).isFalse()
        assertThat(ToolCapabilityMatrix.isToolsRejection(400, "")).isFalse()
        assertThat(ToolCapabilityMatrix.isToolsRejection(400, null)).isFalse()
    }

    @Test
    fun `non four hundred never rejects even when naming tools`() {
        assertThat(ToolCapabilityMatrix.isToolsRejection(401, "tools require auth")).isFalse()
        assertThat(ToolCapabilityMatrix.isToolsRejection(404, "no such tool")).isFalse()
        assertThat(ToolCapabilityMatrix.isToolsRejection(429, "tool rate limited")).isFalse()
        assertThat(ToolCapabilityMatrix.isToolsRejection(500, "tool executor crashed")).isFalse()
        assertThat(ToolCapabilityMatrix.isToolsRejection(200, "tools")).isFalse()
    }

    // Notice copy.

    @Test
    fun `retry notice is english actionable and non empty`() {
        val notice = ToolCapabilityMatrix.TOOLS_UNSUPPORTED_NOTICE
        assertThat(notice).isNotEmpty()
        assertThat(notice).contains("tool calling")
        assertThat(notice).contains("Model-only")
    }

    @Test
    fun `retry notice names the next step`() {
        // UI-review fix: the banner must never be a dead end — the copy
        // tells the user to switch to a tool-capable endpoint.
        assertThat(ToolCapabilityMatrix.TOOLS_UNSUPPORTED_NOTICE)
            .contains("Switch to a tool-capable endpoint to restore search.")
    }

    // Remote-arm predicate (Pitfall 5: VM pre-search skip mirrors this).

    @Test
    fun `armed only when grounding matrix and internet all hold`() {
        assertThat(ToolCapabilityMatrix.isRemoteLoopArmed(true, true, true)).isTrue()
    }

    @Test
    fun `any single gate low disarms`() {
        assertThat(ToolCapabilityMatrix.isRemoteLoopArmed(false, true, true)).isFalse()
        assertThat(ToolCapabilityMatrix.isRemoteLoopArmed(true, false, true)).isFalse()
        assertThat(ToolCapabilityMatrix.isRemoteLoopArmed(true, true, false)).isFalse()
        assertThat(ToolCapabilityMatrix.isRemoteLoopArmed(false, false, false)).isFalse()
    }
}
