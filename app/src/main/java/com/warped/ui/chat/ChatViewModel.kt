package com.warped.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warped.data.local.inference.LlamaEngine
import com.warped.data.remote.provider.ProviderRouter
import com.warped.domain.model.ActiveModelSelection
import com.warped.domain.model.ChatMessage
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.GenerationParameters
import com.warped.domain.model.ParameterStore
import com.warped.domain.model.ProviderType
import com.warped.domain.model.Role
import com.warped.domain.model.StreamToken
import com.warped.domain.repository.ChatRepository
import com.warped.domain.repository.EndpointRepository
import com.warped.domain.repository.LocalModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val llamaEngine: LlamaEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null

    init {
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
                    val selectedExists = models.any { it.filePath == state.selectedModelId }
                    state.copy(
                        localModels = models,
                        selectedModelId = state.selectedModelId.takeIf { selectedExists },
                        selectedProvider = state.selectedProvider.takeIf { selectedExists }
                    )
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
                            error = null
                        )
                    }
                    // Pre-load local models immediately when selected
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

    fun sendMessage(text: String) {
        val state = _uiState.value
        if (text.isBlank()) return
        if (state.selectedModelId == null || state.selectedProvider == null) {
            _uiState.update { it.copy(error = ChatError.NoModelSelected) }
            return
        }

        val userMessage = ChatMessage(role = Role.USER, content = text)
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
                    parameters = _uiState.value.generationParameters
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
                                val finalContent = if (!token.stats.isNullOrBlank()) "$content\n\n${token.stats}" else content
                                val assistantMessage = ChatMessage(
                                    role = Role.ASSISTANT,
                                    content = finalContent,
                                    tokenCount = content.length / 4,
                                    reasoning = token.reasoning
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
                        selectedProvider = conversation.providerType,
                        streamingContent = "",
                        error = null
                    )
                }
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
        activeModelSelection.select(modelId, providerType)
        _uiState.update { it.copy(selectedModelId = modelId, selectedProvider = providerType) }
    }

    fun updateParameters(params: GenerationParameters) {
        _uiState.update { it.copy(generationParameters = params) }
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
            endpointId = 0
        )
        _uiState.update { it.copy(conversationId = conversationId) }
        return conversationId
    }
}
