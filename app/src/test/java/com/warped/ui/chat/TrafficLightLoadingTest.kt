package com.warped.ui.chat

import com.google.common.truth.Truth.assertThat
import com.warped.domain.model.ConnectionStatus
import org.junit.jupiter.api.Test

/**
 * 2026-10-04 entry cases: transitional loading reads YELLOW (work in
 * flight) instead of RED, settling to GREEN on success — local mount
 * and remote ping alike. Memory-warning and error RED are untouched.
 */
class TrafficLightLoadingTest {

    private fun connection(
        localId: String? = null,
        loaded: Boolean = false,
        loading: Boolean = false,
        remoteId: String? = null,
        remoteProvider: com.warped.domain.model.ProviderType? = null,
        status: ConnectionStatus = ConnectionStatus.Unknown,
    ) = ChatConnectionState(
        selectedLocalModelId = localId,
        selectedRemoteModelId = remoteId,
        selectedRemoteProvider = remoteProvider,
        isLocalModelLoaded = loaded,
        isLoadingModel = loading,
        connectionStatus = status,
    )

    @Test
    fun `local mount reads yellow`() {
        val light = trafficLightState(
            ChatTranscriptState(),
            connection(localId = "/models/gemma.litertlm", loading = true),
        )

        assertThat(light).isEqualTo(TrafficLightState.YELLOW)
    }

    @Test
    fun `remote probe reads yellow`() {
        val light = trafficLightState(
            ChatTranscriptState(),
            connection(
                remoteId = "qwen3-4b",
                remoteProvider = com.warped.domain.model.ProviderType.LM_STUDIO,
                status = ConnectionStatus.Connecting,
            ),
        )

        assertThat(light).isEqualTo(TrafficLightState.YELLOW)
    }

    @Test
    fun `remote connected reads green`() {
        val light = trafficLightState(
            ChatTranscriptState(),
            connection(
                remoteId = "qwen3-4b",
                remoteProvider = com.warped.domain.model.ProviderType.LM_STUDIO,
                status = ConnectionStatus.Connected,
            ),
        )

        assertThat(light).isEqualTo(TrafficLightState.GREEN)
    }

    @Test
    fun `local mounted reads green`() {
        val light = trafficLightState(
            ChatTranscriptState(),
            connection(localId = "/models/gemma.litertlm", loaded = true),
        )

        assertThat(light).isEqualTo(TrafficLightState.GREEN)
    }

    @Test
    fun `unselected reads gray`() {
        assertThat(
            trafficLightState(ChatTranscriptState(), connection()),
        ).isEqualTo(TrafficLightState.GRAY)
    }

    @Test
    fun `connecting status text says connecting`() {
        val text = trafficLightStatusText(
            ChatTranscriptState(),
            connection(
                remoteId = "qwen3-4b",
                remoteProvider = com.warped.domain.model.ProviderType.LM_STUDIO,
                status = ConnectionStatus.Connecting,
            ),
        )

        assertThat(text).isEqualTo("Connecting…")
    }
}
