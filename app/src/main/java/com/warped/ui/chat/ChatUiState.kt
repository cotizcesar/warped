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
    val selectedProvider: ProviderType? = null,
    val selectedModelId: String? = null,
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
    val toolCallActive: String? = null,  // tool name while tool is executing (e.g. "web_search")
)

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
