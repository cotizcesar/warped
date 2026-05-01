package com.warped.ui.chat

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warped.data.local.inference.LlamaEngine
import com.warped.data.remote.provider.ProviderRouter
import com.warped.domain.model.*
import com.warped.domain.repository.ChatRepository
import com.warped.domain.repository.EndpointRepository
import com.warped.domain.repository.LocalModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val endpointRepository: EndpointRepository,
    private val localModelRepository: LocalModelRepository,
    private val activeModelSelection: ActiveModelSelection,
    private val providerRouter: ProviderRouter,
    private val savedStateHandle: SavedStateHandle,
    private val parameterStore: ParameterStore,
    private val llamaEngine: LlamaEngine,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null

    init {
        // Restore persisted loaded instance ID
        activeModelSelection.activeModel.value?.instanceId?.let {
            _uiState.value = _uiState.value.copy(loadedInstanceId = it)
        }
        viewModelScope.launch {
            chatRepository.observeConversations().collect { conversations ->
                _uiState.update { it.copy(conversations = conversations) }
            }
        }
        viewModelScope.launch {
            parameterStore.parameters.collect { params ->
                _uiState.update { it.copy(generationParameters = params) }
            }
        }
        viewModelScope.launch {
            localModelRepository.observeModels().collect { models ->
                _uiState.update { state ->
                    if (state.selectedProvider == ProviderType.LOCAL) {
                        val selectedExists = models.any { it.filePath == state.selectedModelId }
                        state.copy(
                            localModels = models,
                            selectedModelId = state.selectedModelId.takeIf { selectedExists },
                            selectedProvider = state.selectedProvider.takeIf { selectedExists }
                        )
                    } else {
                        state.copy(localModels = models)
                    }
                }
            }
        }
        viewModelScope.launch {
            activeModelSelection.activeModel.collect { activeModel ->
                if (activeModel != null) {
                    _uiState.update {
                        it.copy(
                            selectedModelId = activeModel.modelId,
                            selectedProvider = activeModel.providerType,
                            loadedInstanceId = activeModel.instanceId ?: it.loadedInstanceId,
                            error = null
                        )
                    }
                    // Only preload LOCAL models — LM_STUDIO loads on-demand via chat request
                    if (activeModel.providerType == ProviderType.LOCAL) {
                        preloadLocalModel(activeModel.modelId)
                    }
                }
            }
        }
        viewModelScope.launch {
            endpointRepository.observeEndpoints().collect { endpoints ->
                _uiState.update { it.copy(endpoints = endpoints) }
            }
        }
    }

    fun sendMessage(text: String, images: List<Uri> = emptyList()) {
        val state = _uiState.value
        if (text.isBlank() && images.isEmpty()) return
        if (state.selectedModelId == null || state.selectedProvider == null) {
            _uiState.update { it.copy(error = ChatError.NoModelSelected) }
            return
        }

        val imageDataUrls = images.mapNotNull { uriToBase64(it) }
        val userMessage = ChatMessage(role = Role.USER, content = text, imageUris = imageDataUrls)
        _uiState.update { it.copy(messages = it.messages + userMessage, inputText = "", isStreaming = true) }

        generationJob = viewModelScope.launch {
            try {
                val conversationId = ensureConversation(text)
                chatRepository.saveMessage(conversationId, userMessage)

                val activeEndpoint = endpointRepository.getActive()
                    ?: throw IllegalStateException("No active endpoint")

                val provider = providerRouter.resolve(activeEndpoint, state.selectedModelId!!)

                val request = ChatRequest(
                    messages = _uiState.value.messages,
                    parameters = _uiState.value.generationParameters.copy(
                        reasoningEnabled = _uiState.value.reasoningEnabled
                    ),
                    images = imageDataUrls
                )
                val tokenBuffer = mutableListOf<String>()
                var lastEmitTime = System.currentTimeMillis()

                provider.chat(request).collect { token ->
                    when (token) {
                        is StreamToken.Delta -> {
                            tokenBuffer.add(token.content)
                            val now = System.currentTimeMillis()
                            if (now - lastEmitTime >= 50) {
                                val content = tokenBuffer.joinToString("")
                                _uiState.update { it.copy(streamingContent = content) }
                                tokenBuffer.clear()
                                lastEmitTime = now
                            }
                        }
                        is StreamToken.Done -> {
                            val content = _uiState.value.streamingContent + tokenBuffer.joinToString("")
                            if (content.isNotBlank()) {
                                val assistantMessage = ChatMessage(
                                    role = Role.ASSISTANT,
                                    content = content,
                                    tokenCount = content.length / 4,
                                    reasoning = token.reasoning,
                                    stats = token.stats
                                )
                                _uiState.update {
                                    it.copy(
                                        messages = it.messages + assistantMessage,
                                        streamingContent = "",
                                        isStreaming = false
                                    )
                                }
                                chatRepository.saveMessage(conversationId, assistantMessage)
                            }
                        }
                        is StreamToken.Error -> {
                            _uiState.update {
                                it.copy(
                                    error = ChatError.Network(token.message),
                                    isStreaming = false
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = ChatError.Network(e.message ?: "Unknown error"),
                        isStreaming = false
                    )
                }
            }
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
        _uiState.update { it.copy(isStreaming = false) }
    }

    fun selectConversation(conversationId: Long) {
        viewModelScope.launch {
            val result = chatRepository.loadConversation(conversationId)
            if (result != null) {
                val (conversation, messages) = result
                _uiState.update {
                    it.copy(
                        conversationId = conversation.id,
                        messages = messages,
                        selectedModelId = conversation.modelId,
                        selectedProvider = conversation.providerType,
                        streamingContent = "",
                        error = null
                    )
                }
                if (conversation.modelId != null) {
                    activeModelSelection.select(conversation.modelId, conversation.providerType)
                }
                activeModelSelection.saveLastConversation(conversation.id)
            }
        }
    }

    fun newConversation() {
        _uiState.update {
            it.copy(conversationId = null, messages = emptyList(), streamingContent = "", error = null)
        }
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun setSelectedModel(modelId: String, providerType: ProviderType) {
        val oldProvider = _uiState.value.selectedProvider
        val oldModelId = _uiState.value.selectedModelId
        val oldInstance = _uiState.value.loadedInstanceId
        val isSameModel = modelId == oldModelId && providerType == oldProvider
        
        activeModelSelection.select(modelId, providerType)
        _uiState.update { it.copy(selectedModelId = modelId, selectedProvider = providerType) }
        
        if (isSameModel) return
        
        // Unload previous model
        when (oldProvider) {
            ProviderType.LM_STUDIO -> {
                if (oldInstance != null && oldModelId != null) {
                    viewModelScope.launch {
                        try {
                            val endpoint = endpointRepository.getActive()
                            if (endpoint != null) {
                                val provider = com.warped.data.remote.provider.LMStudioProvider(endpoint.url, oldModelId)
                                provider.unloadModel(oldInstance)
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
            ProviderType.LOCAL -> {
                viewModelScope.launch(Dispatchers.Default) {
                    try { llamaEngine.unload() } catch (_: Exception) {}
                }
            }
            else -> {}
        }
        
        // Load new model
        when (providerType) {
            ProviderType.LM_STUDIO -> {
                viewModelScope.launch {
                    try {
                        _uiState.update { it.copy(loadedInstanceId = null) }
                        val endpoint = endpointRepository.getActive()
                        if (endpoint != null) {
                            val provider = com.warped.data.remote.provider.LMStudioProvider(endpoint.url, modelId)
                            val result = provider.loadModel(modelId)
                            result.onSuccess { instanceId ->
                                _uiState.update { it.copy(loadedInstanceId = instanceId) }
                                activeModelSelection.select(modelId, providerType, instanceId)
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
            ProviderType.LOCAL -> {
                preloadLocalModel(modelId)
            }
            else -> {}
        }
    }

    fun updateParameters(params: GenerationParameters) {
        _uiState.update { it.copy(generationParameters = params) }
    }

    fun toggleReasoning() {
        _uiState.update { it.copy(reasoningEnabled = !it.reasoningEnabled) }
    }

    fun loadLastConversation() {
        val lastId = activeModelSelection.getLastConversation()
        if (lastId > 0) {
            selectConversation(lastId)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearModelLoadError() {
        _uiState.update { it.copy(modelLoadError = null) }
    }

    private fun preloadLocalModel(filePath: String) {
        val modelName = filePath.substringAfterLast("/").removeSuffix(".gguf")
        _uiState.update { it.copy(isLoadingModel = true, loadingModelName = modelName, modelLoadError = null) }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val loaded = llamaEngine.loadModel(filePath)
                if (loaded) {
                    _uiState.update { it.copy(isLoadingModel = false, loadingModelName = "") }
                } else {
                    _uiState.update { it.copy(isLoadingModel = false, modelLoadError = "Failed to load model") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingModel = false, modelLoadError = e.message) }
            }
        }
    }

    private suspend fun ensureConversation(firstMessage: String): Long {
        val state = _uiState.value
        if (state.conversationId != null) return state.conversationId

        val title = if (firstMessage.length > 50) firstMessage.take(50) + "..." else firstMessage
        val conversationId = chatRepository.createConversation(
            title = title,
            providerType = state.selectedProvider!!,
            modelId = state.selectedModelId,
            endpointId = 0
        )
        _uiState.update { it.copy(conversationId = conversationId) }
        activeModelSelection.saveLastConversation(conversationId)
        return conversationId
    }

    private fun uriToBase64(uri: Uri): String? {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val bytes = input.use { it.readBytes() }
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val mime = context.contentResolver.getType(uri) ?: "image/png"
            "data:$mime;base64,$base64"
        } catch (_: Exception) {
            null
        }
    }
}
