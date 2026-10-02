package com.warped.ui.chat.voice

import com.google.common.truth.Truth.assertThat
import com.warped.domain.model.ProviderType
import org.junit.jupiter.api.Test

/**
 * Phase 69 (VMSG-04/08): branch-complete gate tests. Plain JUnit + Truth,
 * no Robolectric — the helper has no Android imports.
 */
class VoiceSendGateTest {

    @Test
    fun `local plus audio returns Allowed`() {
        assertThat(VoiceSendGate.evaluate(ProviderType.LITE_RT_LM, true))
            .isEqualTo(GateState.Allowed)
    }

    @Test
    fun `local plus no audio returns GatedTextOnly`() {
        assertThat(VoiceSendGate.evaluate(ProviderType.LITE_RT_LM, false))
            .isEqualTo(GateState.GatedTextOnly)
    }

    @Test
    fun `local plus null capability fails open to Allowed`() {
        assertThat(VoiceSendGate.evaluate(ProviderType.LITE_RT_LM, null))
            .isEqualTo(GateState.Allowed)
    }

    @Test
    fun `null provider fails open to Allowed`() {
        assertThat(VoiceSendGate.evaluate(null, false))
            .isEqualTo(GateState.Allowed)
        assertThat(VoiceSendGate.evaluate(null, null))
            .isEqualTo(GateState.Allowed)
    }

    @Test
    fun `each remote provider returns GatedRemote`() {
        val remotes = listOf(
            ProviderType.OPENAI,
            ProviderType.ANTHROPIC,
            ProviderType.OLLAMA,
            ProviderType.LM_STUDIO,
            ProviderType.CUSTOM,
        )
        for (remote in remotes) {
            assertThat(VoiceSendGate.evaluate(remote, true)).isEqualTo(GateState.GatedRemote)
            assertThat(VoiceSendGate.evaluate(remote, false)).isEqualTo(GateState.GatedRemote)
            assertThat(VoiceSendGate.evaluate(remote, null)).isEqualTo(GateState.GatedRemote)
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun `legacy LOCAL provider returns GatedRemote`() {
        assertThat(VoiceSendGate.evaluate(ProviderType.LOCAL, true))
            .isEqualTo(GateState.GatedRemote)
    }

    @Test
    fun `remote wins over audio-capable local`() {
        assertThat(VoiceSendGate.evaluate(ProviderType.OPENAI, true))
            .isEqualTo(GateState.GatedRemote)
        assertThat(VoiceSendGate.evaluate(ProviderType.OLLAMA, true))
            .isEqualTo(GateState.GatedRemote)
    }
}
