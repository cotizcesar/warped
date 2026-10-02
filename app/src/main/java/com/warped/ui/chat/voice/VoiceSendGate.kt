package com.warped.ui.chat.voice

import com.warped.domain.model.ProviderType

/**
 * Phase 69 (VMSG-04/08): provider-keyed voice-send gate.
 *
 * Pure Kotlin, zero Android imports — unit-testable on JVM.
 *
 * Unlocking remote voice-send later means changing [evaluate] only,
 * never call sites.
 */
sealed interface GateState {
    data object Allowed : GateState
    data object GatedTextOnly : GateState
    data object GatedRemote : GateState
}

object VoiceSendGate {
    /**
     * Evaluate the voice-send gate.
     *
     * - providerType null (nothing selected / unknown) → [GateState.Allowed]
     *   (fail-open, matches the existing `?: true` convention).
     * - providerType == LITE_RT_LM (or legacy LOCAL) → [GateState.Allowed]
     *   when localAudioCapable != false, else [GateState.GatedTextOnly]
     *   (null capability = allowlist not loaded yet → fail open).
     * - Any other provider (remote) → [GateState.GatedRemote]. The remote
     *   check runs first — a stale local id never un-gates remote.
     *
     * Unlocking remote voice-send later means changing this one function,
     * never call sites.
     */
    @Suppress("DEPRECATION")
    fun evaluate(
        providerType: ProviderType?,
        localAudioCapable: Boolean?,
    ): GateState {
        if (providerType == null) return GateState.Allowed
        if (providerType == ProviderType.LITE_RT_LM || providerType == ProviderType.LOCAL) {
            return if (localAudioCapable == false) GateState.GatedTextOnly else GateState.Allowed
        }
        return GateState.GatedRemote
    }
}
