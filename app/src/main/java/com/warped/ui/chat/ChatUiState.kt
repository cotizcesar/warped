package com.warped.ui.chat

import com.warped.data.local.inference.BackendType
import com.warped.domain.model.ChatMessage
import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.Conversation
import com.warped.domain.model.Endpoint
import com.warped.domain.model.GenerationParameters
import com.warped.domain.model.LocalModel
import com.warped.domain.model.ProviderType
import com.warped.domain.model.SyntaxTheme

data class ChatUiState(
    val conversationId: Long? = null,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val streamingContent: String = "",
    val streamingReasoning: String = "",
    @Deprecated("Use selectedLocalModelId or selectedRemoteModelId instead")
    val selectedProvider: ProviderType? = null,
    @Deprecated("Use selectedLocalModelId or selectedRemoteModelId instead")
    val selectedModelId: String? = null,
    val selectedLocalModelId: String? = null,
    val selectedRemoteModelId: String? = null,
    val selectedRemoteProvider: ProviderType? = null,
    val isLocalModelConnected: Boolean = false,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Unknown,
    val error: ChatError? = null,
    val conversations: List<Conversation> = emptyList(),
    val localModels: List<LocalModel> = emptyList(),
    val endpoints: List<Endpoint> = emptyList(),
    val isStreaming: Boolean = false,
    val generationParameters: GenerationParameters = GenerationParameters(),
    val isLoadingModel: Boolean = false,
    val loadingModelName: String = "",
    val modelLoadError: String? = null,
    val loadedInstanceId: String? = null,
    val reasoningEnabled: Boolean = true,
    val activeBackend: BackendType? = null,  // null unless LITE_RT_LM is loaded
    val isLocalModelLoaded: Boolean = false,
    val memoryWarningModel: com.warped.domain.model.LocalModel? = null,
    val codeTheme: SyntaxTheme = SyntaxTheme.MONOKAI,
    val codeFontScale: Float = 1.0f,
    val modelUnavailable: Boolean = false,
    val pendingModelSwitch: ModelSwitchRequest? = null,
    val conversationModelId: String? = null,
    val conversationProviderType: ProviderType? = null,
    val toolCallActive: String? = null  // tool name while tool is executing (e.g. "web_search")
)

enum class TrafficLightState {
    GREEN, YELLOW, RED, GRAY
}

sealed class ChatError {
    data class Network(val message: String) : ChatError()
    data class Server(val code: Int, val message: String) : ChatError()
    data class Auth(val message: String) : ChatError()
    data object NoModelSelected : ChatError()
    data object DownloadModelFirst : ChatError()
    data object ConnectionLost : ChatError()
    data object ModelUnavailable : ChatError()
    data class Unknown(val message: String) : ChatError()
}

data class ModelSwitchRequest(
    val modelId: String,
    val providerType: ProviderType,
)

fun ChatUiState.trafficLightState(): TrafficLightState {
    val isLocal = selectedLocalModelId != null && isLocalModelLoaded
    val isRemote = selectedRemoteModelId != null && selectedRemoteProvider != null
    return when {
        isStreaming -> TrafficLightState.YELLOW
        memoryWarningModel != null -> TrafficLightState.RED
        error != null -> TrafficLightState.RED
        isLocal && isLocalModelLoaded -> TrafficLightState.GREEN
        isRemote && connectionStatus == ConnectionStatus.Connected -> TrafficLightState.GREEN
        isLocal || isRemote -> TrafficLightState.RED
        else -> TrafficLightState.GRAY
    }
}

fun ChatUiState.trafficLightStatusText(): String {
    val light = trafficLightState()
    val isLocal = selectedLocalModelId != null
    val isRemote = selectedRemoteModelId != null
    val localName = localModels.firstOrNull { it.filePath == selectedLocalModelId }?.name
    val remoteName = selectedRemoteModelId?.substringAfterLast("/")
    return when {
        light == TrafficLightState.YELLOW -> "Generating response…"
        light == TrafficLightState.GREEN && isLocal -> "Local: $localName — Connected"
        light == TrafficLightState.GREEN && isRemote -> "Remote: $remoteName — Connected"
        light == TrafficLightState.RED && error != null -> "Error: ${(error as? ChatError)?.let { 
            when (it) { is ChatError.Network -> it.message; is ChatError.Server -> it.message; is ChatError.Auth -> it.message; is ChatError.Unknown -> it.message; else -> "Connection error" } 
        } ?: "Connection error"}"
        light == TrafficLightState.RED && isLocal -> "Local: $localName — Not connected"
        light == TrafficLightState.RED && isRemote -> "Remote: $remoteName — Disconnected"
        else -> "No model selected"
    }
}
