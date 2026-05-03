package com.warped.ui.chat

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warped.data.local.inference.BackendType
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.LlamaEngine
import com.warped.data.local.inference.MemoryChecker
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
import kotlinx.coroutines.withContext
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
    private val engineManager: EngineManager,
    private val memoryChecker: MemoryChecker,
    @param:ApplicationContext private val context: Context
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
                    if (state.selectedProvider == ProviderType.LOCAL ||
                        state.selectedProvider == ProviderType.LITE_RT_LM) {
                        val selectedModel = models.firstOrNull { it.filePath == state.selectedModelId }
                        val selectedExists = selectedModel != null
                        val normalizedProvider = if (selectedModel?.isLiteRtLm() == true) {
                            ProviderType.LITE_RT_LM
                        } else {
                            state.selectedProvider
                        }
                        state.copy(
                            localModels = models,
                            selectedModelId = state.selectedModelId.takeIf { selectedExists },
                            selectedProvider = normalizedProvider.takeIf { selectedExists }
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
                    // Only preload local models — LM_STUDIO loads on-demand via chat request
                    if (activeModel.providerType == ProviderType.LOCAL ||
                        activeModel.providerType == ProviderType.LITE_RT_LM) {
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

                val selectedProvider = resolvedSelectedProvider(state)
                val modelId = state.selectedModelId!!

                // Validate model capabilities
                if (selectedProvider == ProviderType.LOCAL || selectedProvider == ProviderType.LITE_RT_LM) {
                    val capabilities = state.localModels.firstOrNull { it.filePath == modelId }?.capabilities
                    if (images.isNotEmpty() && capabilities?.vision != true) {
                        _uiState.update {
                            it.copy(
                                error = ChatError.Unknown("This model does not support images (no vision capability)."),
                                isStreaming = false
                            )
                        }
                        return@launch
                    }
                }

                // Auto-reload local model if engine was unloaded (e.g. memory pressure)
                if (selectedProvider == ProviderType.LOCAL || selectedProvider == ProviderType.LITE_RT_LM) {
                    val modelFile = java.io.File(modelId)
                    if (!modelFile.exists()) {
                        _uiState.update {
                            it.copy(
                                error = ChatError.DownloadModelFirst,
                                isStreaming = false
                            )
                        }
                        return@launch
                    }
                    val isEngineLoaded = when (selectedProvider) {
                        ProviderType.LITE_RT_LM -> engineManager.getActiveEngine() != null
                        ProviderType.LOCAL -> llamaEngine.isLoaded()
                        else -> true
                    }
                    if (!isEngineLoaded) {
                        preloadLocalModel(modelId)
                    }
                }

                val provider = when (selectedProvider) {
                    ProviderType.LOCAL,
                    ProviderType.LITE_RT_LM -> providerRouter.resolveLocal(
                        selectedProvider,
                        modelId
                    )
                    else -> {
                        val activeEndpoint = state.endpoints.firstOrNull {
                            it.apiType == selectedProvider && it.modelId == state.selectedModelId
                        } ?: endpointRepository.getActive()?.takeIf {
                            it.apiType == selectedProvider && it.modelId == state.selectedModelId
                        } ?: throw IllegalStateException("No endpoint selected")

                        providerRouter.resolve(activeEndpoint, state.selectedModelId!!)
                    }
                }

                val request = ChatRequest(
                    messages = _uiState.value.messages,
                    parameters = _uiState.value.generationParameters.copy(
                        reasoningEnabled = _uiState.value.reasoningEnabled
                    ),
                    images = imageDataUrls
                )
                val tokenBuffer = mutableListOf<String>()
                var lastEmitTime = System.currentTimeMillis()

                val rawBuffer = StringBuilder()
                val reasoningActive = _uiState.value.reasoningEnabled

                provider.chat(request).collect { token ->
                    when (token) {
                        is StreamToken.Delta -> {
                            tokenBuffer.add(token.content)
                            val now = System.currentTimeMillis()
                            if (now - lastEmitTime >= 50) {
                                val chunk = tokenBuffer.joinToString("")
                                rawBuffer.append(chunk)
                                val (cleanContent, reasoning) = parseThinkBlocks(rawBuffer.toString(), reasoningActive)
                                _uiState.update {
                                    it.copy(
                                        streamingContent = cleanContent,
                                        streamingReasoning = reasoning
                                    )
                                }
                                tokenBuffer.clear()
                                lastEmitTime = now
                            }
                        }
                        is StreamToken.Done -> {
                            rawBuffer.append(tokenBuffer.joinToString(""))
                            val (finalClean, finalReasoning) = parseThinkBlocks(rawBuffer.toString(), reasoningActive)
                            val content = if (finalClean.isBlank()) finalClean else finalClean.trimStart()
                            if (content.isNotBlank() || finalReasoning.isNotBlank()) {
                                val assistantMessage = ChatMessage(
                                    role = Role.ASSISTANT,
                                    content = content,
                                    tokenCount = content.length / 4,
                                    reasoning = finalReasoning.ifEmpty { token.reasoning },
                                    stats = token.stats
                                )
                                _uiState.update {
                                    it.copy(
                                        messages = it.messages + assistantMessage,
                                        streamingContent = "",
                                        streamingReasoning = "",
                                        isStreaming = false
                                    )
                                }
                                chatRepository.saveMessage(conversationId, assistantMessage)
                            } else {
                                _uiState.update {
                                    it.copy(
                                        streamingContent = "",
                                        streamingReasoning = "",
                                        isStreaming = false
                                    )
                                }
                            }
                        }
                        is StreamToken.Error -> {
                            _uiState.update {
                                it.copy(
                                    error = ChatError.Network(token.message),
                                    streamingContent = "",
                                    streamingReasoning = "",
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
                        streamingReasoning = "",
                        error = null
                    )
                }
                if (conversation.modelId != null) {
                    activeModelSelection.select(conversation.modelId, conversation.providerType)
                    if (conversation.providerType == ProviderType.LOCAL || conversation.providerType == ProviderType.LITE_RT_LM) {
                        preloadLocalModel(conversation.modelId)
                    }
                }
                activeModelSelection.saveLastConversation(conversation.id)
                refreshActiveBackend()
            }
        }
    }

    fun newConversation() {
        _uiState.update {
            it.copy(conversationId = null, messages = emptyList(), streamingContent = "", streamingReasoning = "", error = null)
        }
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun launchModelSelection(modelId: String, providerType: ProviderType) {
        // Check memory for local models
        if (providerType == ProviderType.LOCAL || providerType == ProviderType.LITE_RT_LM) {
            val model = _uiState.value.localModels.firstOrNull { it.filePath == modelId }
            if (model != null && memoryChecker.shouldWarn(model.sizeBytes)) {
                _uiState.update { it.copy(memoryWarningModel = model) }
                return
            }
        }
        // Close dropdown first, then load model to prevent UI hang
        viewModelScope.launch {
            setSelectedModel(modelId, providerType)
        }
    }

    fun confirmLoadMemoryWarning() {
        val model = _uiState.value.memoryWarningModel ?: return
        _uiState.update { it.copy(memoryWarningModel = null) }
        val providerType = if (model.isLiteRtLm()) ProviderType.LITE_RT_LM else ProviderType.LOCAL
        viewModelScope.launch {
            setSelectedModel(model.filePath, providerType)
        }
    }

    fun dismissMemoryWarning() {
        _uiState.update { it.copy(memoryWarningModel = null) }
    }

    fun setSelectedModel(modelId: String, providerType: ProviderType) {
        val oldProvider = _uiState.value.selectedProvider
        val oldModelId = _uiState.value.selectedModelId
        val oldInstance = _uiState.value.loadedInstanceId
        val isSameModel = modelId == oldModelId && providerType == oldProvider
        
        activeModelSelection.select(modelId, providerType)
        _uiState.update { it.copy(selectedModelId = modelId, selectedProvider = providerType) }
        
        if (isSameModel) {
            // Even if same model, reload if engine was unloaded (memory pressure)
            val needsReload = when (providerType) {
                ProviderType.LITE_RT_LM -> engineManager.getActiveEngine() == null
                ProviderType.LOCAL -> !llamaEngine.isLoaded()
                else -> false
            }
            if (!needsReload) return
        }
        
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
            ProviderType.LITE_RT_LM -> {
                viewModelScope.launch(Dispatchers.Default) {
                    try { engineManager.unloadCurrent() } catch (_: Exception) {}
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
                viewModelScope.launch { preloadLocalModel(modelId) }
            }
            ProviderType.LITE_RT_LM -> {
                viewModelScope.launch { preloadLocalModel(modelId) }
            }
            else -> {}
        }
        refreshActiveBackend()
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

    private fun refreshActiveBackend() {
        val isLocal = _uiState.value.selectedProvider == ProviderType.LOCAL ||
            _uiState.value.selectedProvider == ProviderType.LITE_RT_LM
        val backend = if (_uiState.value.selectedProvider == ProviderType.LITE_RT_LM) {
            engineManager.getActiveEngine()?.backend
        } else null
        val isLoaded = when {
            _uiState.value.selectedProvider == ProviderType.LITE_RT_LM -> engineManager.getActiveEngine() != null
            _uiState.value.selectedProvider == ProviderType.LOCAL -> llamaEngine.isLoaded()
            else -> false
        }
        _uiState.update { it.copy(activeBackend = backend, isLocalModelLoaded = isLocal && isLoaded) }
    }

    fun unloadLocalModels() {
        viewModelScope.launch(Dispatchers.Default) {
            try { engineManager.unloadCurrent() } catch (_: Exception) {}
            try { llamaEngine.stop(); llamaEngine.unload() } catch (_: Exception) {}
            _uiState.update { it.copy(isLocalModelLoaded = false, activeBackend = null) }
        }
    }

    private suspend fun preloadLocalModel(filePath: String) {
        val model = _uiState.value.localModels.firstOrNull { it.filePath == filePath }
        val isLitertlm = model?.isLiteRtLm() == true || filePath.endsWith(".litertlm", ignoreCase = true)
        val modelName = filePath.substringAfterLast("/").removeSuffix(".gguf").removeSuffix(".litertlm")
        _uiState.update { it.copy(isLoadingModel = true, loadingModelName = modelName, modelLoadError = null) }
        try {
            withContext(Dispatchers.Default) {
                if (isLitertlm) {
                    engineManager.switchToLiteRT(filePath)
                } else {
                    val loaded = llamaEngine.loadModel(filePath)
                    if (!loaded) throw IllegalStateException("Failed to load GGUF model")
                }
            }
            _uiState.update { it.copy(isLoadingModel = false, loadingModelName = "") }
            refreshActiveBackend()
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoadingModel = false, modelLoadError = e.message) }
        }
    }

    private fun resolvedSelectedProvider(state: ChatUiState): ProviderType {
        val selectedModelId = state.selectedModelId ?: return state.selectedProvider ?: ProviderType.LOCAL
        val selectedLocalModel = state.localModels.firstOrNull { it.filePath == selectedModelId }
        return if (selectedLocalModel?.isLiteRtLm() == true || selectedModelId.endsWith(".litertlm", ignoreCase = true)) {
            ProviderType.LITE_RT_LM
        } else {
            state.selectedProvider ?: ProviderType.LOCAL
        }
    }

    private fun LocalModel.isLiteRtLm(): Boolean =
        modelFormat.equals("LITERTLM", ignoreCase = true) || filePath.endsWith(".litertlm", ignoreCase = true)

    private fun parseThinkBlocks(raw: String, enabled: Boolean = true): Pair<String, String> {
        if (!enabled) return Pair(raw.trim(), "")

        // Extract complete think blocks — case-insensitive for DeepSeek/other variants
        val completeRegex = Regex("<think>([\\s\\S]*?)</think>", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
        val reasoning = StringBuilder()
        var clean = raw

        completeRegex.findAll(clean).forEach { match ->
            reasoning.append(match.groupValues[1].trim()).append("\n")
        }
        clean = completeRegex.replace(clean, "")

        // Handle incomplete <think> at the end (streaming hasn't received <｜end▁of▁thinking｜> yet)
        val lastThinkOpen = clean.lowercase().lastIndexOf("<think>")
        if (lastThinkOpen >= 0) {
            val beforeTag = clean.substring(0, lastThinkOpen)
            val afterTag = clean.substring(lastThinkOpen + "<think>".length)
            reasoning.append(afterTag.trim())
            clean = beforeTag
        }

        return Pair(clean.trim(), reasoning.toString().trim())
    }

    private suspend fun ensureConversation(firstMessage: String): Long {
        val state = _uiState.value
        if (state.conversationId != null) return state.conversationId

        val title = if (firstMessage.length > 50) firstMessage.take(50) + "..." else firstMessage
        val conversationId = chatRepository.createConversation(
            title = title,
            providerType = resolvedSelectedProvider(state),
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
