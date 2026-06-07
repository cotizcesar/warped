package com.warped.ui.chat

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warped.data.local.inference.BackendType
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.local.preferences.AdvancedPreferences
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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch

import kotlinx.coroutines.withContext
import timber.log.Timber
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
    private val engineManager: EngineManager,
    private val memoryChecker: MemoryChecker,
    private val advancedPreferences: AdvancedPreferences,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        timber.log.Timber.e(throwable, "Unhandled coroutine exception")
    }

    init {
        // Restore persisted loaded instance ID
        activeModelSelection.activeModel.value?.instanceId?.let {
            _uiState.value = _uiState.value.copy(loadedInstanceId = it)
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            chatRepository.observeConversations().collect { conversations ->
                _uiState.update { it.copy(conversations = conversations) }
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            parameterStore.parameters.collect { params ->
                _uiState.update { it.copy(generationParameters = params) }
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            localModelRepository.observeModels().collect { models ->
                _uiState.update { state ->
                    val activeLocalId = state.selectedLocalModelId
                    if (activeLocalId != null) {
                        val selectedModel = models.firstOrNull { it.filePath == activeLocalId }
                        val selectedExists = selectedModel != null
                        state.copy(
                            localModels = models,
                            selectedLocalModelId = activeLocalId.takeIf { selectedExists },
                            isLocalModelLoaded = selectedExists && state.isLocalModelLoaded
                        )
                    } else {
                        state.copy(localModels = models)
                    }
                }
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            activeModelSelection.localSelection.collect { local ->
                val modelId = local.modelId
                val connected = local.isConnected
                val loading = modelId != null && !connected
                val justConnected = modelId != null && connected

                if (justConnected) {
                    autoApplySmartPreset(modelId)
                }
                if (modelId == null) {
                    lastAutoAppliedModelId = null
                }

                _uiState.update {
                    it.copy(
                        selectedLocalModelId = modelId,
                        isLocalModelLoaded = connected,
                        isLoadingModel = loading,
                        loadingModelName = modelId?.substringAfterLast("/") ?: it.loadingModelName,
                        loadedInstanceId = local.instanceId ?: it.loadedInstanceId,
                        supportsThinking = supportsThinkingFor(modelId, it.selectedRemoteModelId),
                    )
                }
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            activeModelSelection.remoteSelection.collect { remote ->
                _uiState.update {
                    it.copy(
                        selectedRemoteModelId = remote.modelId,
                        selectedRemoteProvider = remote.providerType,
                        supportsThinking = supportsThinkingFor(it.selectedLocalModelId, remote.modelId),
                    )
                }
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            activeModelSelection.activeModel.collect { activeModel ->
                if (activeModel != null) {
                    _uiState.update {
                        it.copy(
                            selectedModelId = activeModel.modelId,
                            selectedProvider = activeModel.providerType,
                            error = null
                        )
                    }
                }
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            endpointRepository.observeEndpoints().collect { endpoints ->
                _uiState.update { it.copy(endpoints = endpoints) }
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            advancedPreferences.syntaxTheme.collect { theme ->
                _uiState.update { it.copy(codeTheme = theme) }
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            advancedPreferences.codeFontScale.collect { scale ->
                _uiState.update { it.copy(codeFontScale = scale) }
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            advancedPreferences.thinkingEnabled.collect { enabled ->
                _uiState.update { it.copy(enableThinking = enabled) }
            }
        }
    }

    fun sendMessage(text: String, images: List<Uri> = emptyList(), audioBytes: ByteArray? = null) {
        val state = _uiState.value

        val effectiveModelId = state.selectedLocalModelId ?: state.selectedRemoteModelId
        val effectiveProvider = state.selectedLocalModelId?.let { ProviderType.LITE_RT_LM }
            ?: state.selectedRemoteProvider

        if (text.isBlank() && images.isEmpty() && audioBytes == null) return
        if (effectiveModelId == null || effectiveProvider == null) {
            _uiState.update { it.copy(error = ChatError.NoModelSelected) }
            return
        }

        if (state.modelUnavailable) {
            _uiState.update { it.copy(error = ChatError.ModelUnavailable) }
            return
        }

        val imageDataUrls = images.mapNotNull { uriToBase64(it) }
        val userMessage = ChatMessage(role = Role.USER, content = text.trim(), imageUris = imageDataUrls)
        _uiState.update { it.copy(messages = it.messages + userMessage, inputText = "", isStreaming = true, toolCallActive = null) }

        generationJob = viewModelScope.launch(coroutineExceptionHandler) {
            try {
                val conversationId = ensureConversation(text, hasMedia = images.isNotEmpty() || audioBytes != null)
                chatRepository.saveMessage(conversationId, userMessage)

                val selectedProvider = effectiveProvider
                val modelId = effectiveModelId

                // Validate model capabilities
                if (selectedProvider == ProviderType.LITE_RT_LM) {
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
                    if (audioBytes != null && capabilities?.audio != true) {
                        _uiState.update {
                            it.copy(
                                error = ChatError.Unknown("This model does not support audio input."),
                                isStreaming = false
                            )
                        }
                        return@launch
                    }
                }

                // Auto-reload local model if engine was unloaded (e.g. memory pressure)
                if (selectedProvider == ProviderType.LITE_RT_LM) {
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
                    // Model loads on-demand on first message
                }

                val helper = when (selectedProvider) {
                    ProviderType.LITE_RT_LM,
                    ProviderType.LITE_RT_LM -> providerRouter.resolveLocalHelper(
                        selectedProvider,
                        modelId
                    )
                    else -> {
                        val activeEndpoint = state.endpoints.firstOrNull {
                            it.apiType == selectedProvider && it.modelId == modelId
                        } ?: endpointRepository.getActive()?.takeIf {
                            it.apiType == selectedProvider && it.modelId == modelId
                        } ?: throw IllegalStateException("No endpoint selected")

                        providerRouter.resolveHelper(activeEndpoint, modelId)
                    }
                }

                // RUNTIME-04: initialize() is idempotent on the helper — if the helper
                // already serves this model, it is a no-op. We call it from sendMessage
                // so the unified chat path is self-contained.
                helper.initialize(modelId)

                val request = ChatRequest(
                    messages = _uiState.value.messages,
                    parameters = _uiState.value.generationParameters.copy(
                        reasoningEnabled = _uiState.value.reasoningEnabled
                    ),
                    images = imageDataUrls,
                    audioBytes = audioBytes
                )
                val tokenBuffer = mutableListOf<String>()
                var lastEmitTime = System.currentTimeMillis()

                val rawBuffer = StringBuilder()
                val reasoningActive = _uiState.value.reasoningEnabled
                val modelMayThink = state.localModels.firstOrNull { it.filePath == modelId }?.capabilities?.reasoning == true
                Timber.d("ChatVM: sendMessage reasoningActive=%b modelMayThink=%b", reasoningActive, modelMayThink)

                helper.runInference(
                    request = request,
                    enableThinking = state.enableThinking && state.supportsThinking,
                ).collect { token ->

                    when (token) {
                        is StreamToken.Delta -> {
                            // Detect tool call patterns [tool:NAME] and show indicator
                            val toolMatch = Regex("\\[tool:(\\w+)\\]").find(token.content)
                            if (toolMatch != null) {
                                _uiState.update { it.copy(toolCallActive = toolMatch.groupValues[1]) }
                            }
                            tokenBuffer.add(token.content)
                            val now = System.currentTimeMillis()
                            if (now - lastEmitTime >= 50) {
                                val chunk = tokenBuffer.joinToString("")
                                rawBuffer.append(chunk)
                                val (cleanContent, reasoning) = parseThinkBlocks(rawBuffer.toString(), reasoningActive, modelMayThink)
                                _uiState.update {
                                    it.copy(
                                        streamingContent = cleanContent,
                                        streamingReasoning = reasoning,
                                        toolCallActive = null  // clear tool indicator once content arrives
                                    )
                                }
                                tokenBuffer.clear()
                                lastEmitTime = now
                            }
                        }
                        is StreamToken.Done -> {
                            _uiState.update { it.copy(toolCallActive = null) }
                            rawBuffer.append(tokenBuffer.joinToString(""))
                            val (finalClean, finalReasoning) = parseThinkBlocks(rawBuffer.toString(), reasoningActive, modelMayThink)
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
        _uiState.update { it.copy(
            isStreaming = false,
            streamingContent = "",
            streamingReasoning = "",
            toolCallActive = null
        ) }
    }

    fun selectConversation(conversationId: Long) {
        viewModelScope.launch(coroutineExceptionHandler) {
            val result = chatRepository.loadConversation(conversationId)
            if (result != null) {
                val (conversation, messages) = result

                // BUG-02: Only unload if the conversation's model differs from the currently loaded engine.
                // If the same model is already loaded, keep it warm and skip the reload cost.
                val activeModelPath = engineManager.getActiveEngine()?.modelPath
                val needsReload = conversation.modelId != null &&
                    conversation.providerType == ProviderType.LITE_RT_LM &&
                    activeModelPath != conversation.modelId

                if (needsReload) {
                    engineManager.scheduleUnload()
                }

                val modelMissing = conversation.modelId != null && !isModelAvailable(conversation.modelId, conversation.providerType)
                _uiState.update {
                    val isLocalConv = conversation.providerType == ProviderType.LITE_RT_LM
                    it.copy(
                        conversationId = conversation.id,
                        messages = messages,
                        selectedLocalModelId = if (isLocalConv) conversation.modelId else null,
                        selectedRemoteModelId = if (!isLocalConv) conversation.modelId else null,
                        selectedRemoteProvider = if (!isLocalConv) conversation.providerType else null,
                        streamingContent = "",
                        streamingReasoning = "",
                        error = null,
                        conversationModelId = conversation.modelId,
                        conversationProviderType = conversation.providerType,
                        modelUnavailable = modelMissing
                    )
                }
                if (conversation.modelId != null && !modelMissing) {
                    if (conversation.providerType == ProviderType.LITE_RT_LM) {
                        activeModelSelection.markLocalLoading(conversation.modelId)
                    } else {
                        val endpoint = if (conversation.endpointId != 0L) {
                            endpointRepository.getById(conversation.endpointId)
                        } else {
                            endpointRepository.getActive()
                        }
                        if (endpoint != null) {
                            if (endpointRepository.getActive()?.id != endpoint.id) {
                                endpointRepository.activateEndpoint(endpoint.id)
                            }
                            activeModelSelection.selectRemote(conversation.modelId, conversation.providerType, endpoint.id)
                        }
                    }
                    // BUG-02: Preload the model if reload is needed, so the loading indicator shows.
                    // If engine already matches, preloadLocalModel will be a no-op (EngineManager.switchToLiteRT
                    // skips when activeEngine matches the target).
                    if (conversation.providerType == ProviderType.LITE_RT_LM) {
                        preloadLocalModel(conversation.modelId)
                    }
                }
                activeModelSelection.saveLastConversation(conversation.id)
                refreshActiveBackend()
            }
        }
    }

    fun newConversation() {
        unloadLocalModels()
        _uiState.update {
            it.copy(
                conversationId = null,
                messages = emptyList(),
                streamingContent = "",
                streamingReasoning = "",
                error = null,
                conversationModelId = null,
                conversationProviderType = null,
                modelUnavailable = false
            )
        }
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun launchModelSelection(modelId: String, providerType: ProviderType, endpointId: Long? = null) {
        val state = _uiState.value

        // If we're in a conversation and the model is different, block and show dialog
        val conversationModelId = state.conversationModelId
        if (conversationModelId != null && state.messages.isNotEmpty() &&
            (modelId != conversationModelId || providerType != state.conversationProviderType)) {
            _uiState.update { it.copy(pendingModelSwitch = ModelSwitchRequest(modelId, providerType, endpointId)) }
            return
        }

        // Check memory for local models
        if (providerType == ProviderType.LITE_RT_LM) {
            val model = _uiState.value.localModels.firstOrNull { it.filePath == modelId }
            if (model != null && memoryChecker.shouldWarn(model.sizeBytes)) {
                _uiState.update { it.copy(memoryWarningModel = model) }
                return
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            setSelectedModel(modelId, providerType, endpointId, isSameModel = false)
        }
    }

    fun confirmModelSwitch() {
        val pending = _uiState.value.pendingModelSwitch ?: return
        viewModelScope.launch(coroutineExceptionHandler) {
            // Create new conversation, clearing old ID and messages
            _uiState.update {
                it.copy(
                    conversationId = null,
                    messages = emptyList(),
                    streamingContent = "",
                    streamingReasoning = "",
                    conversationModelId = pending.modelId,
                    conversationProviderType = pending.providerType,
                    pendingModelSwitch = null,
                    error = null
                )
            }
            setSelectedModel(pending.modelId, pending.providerType, pending.endpointId, isSameModel = false)
        }
    }

    fun cancelModelSwitch() {
        _uiState.update { it.copy(pendingModelSwitch = null) }
    }

    fun dismissModelUnavailable() {
        _uiState.update { it.copy(
            modelUnavailable = false,
            conversationModelId = null,
            conversationProviderType = null
        ) }
    }

    fun confirmLoadMemoryWarning() {
        val model = _uiState.value.memoryWarningModel ?: return
        _uiState.update { it.copy(memoryWarningModel = null) }
        val providerType = if (model.isLiteRtLm()) ProviderType.LITE_RT_LM else ProviderType.LITE_RT_LM
        viewModelScope.launch(coroutineExceptionHandler) {
            setSelectedModel(model.filePath, providerType, null, isSameModel = false)
        }
    }

    fun dismissMemoryWarning() {
        _uiState.update { it.copy(memoryWarningModel = null) }
    }

    private fun setSelectedModel(modelId: String, providerType: ProviderType, endpointId: Long?, isSameModel: Boolean) {
        val oldLocalId = _uiState.value.selectedLocalModelId
        val oldRemoteId = _uiState.value.selectedRemoteModelId
        val oldInstance = _uiState.value.loadedInstanceId

        if (providerType == ProviderType.LITE_RT_LM) {
            activeModelSelection.markLocalLoading(modelId)
            _uiState.update {
                it.copy(
                    selectedLocalModelId = modelId,
                    selectedRemoteModelId = null,
                    selectedRemoteProvider = null
                )
            }
            viewModelScope.launch(coroutineExceptionHandler) {
                preloadLocalModel(modelId)
            }
        } else {
            viewModelScope.launch(coroutineExceptionHandler) {
                val endpoint = if (endpointId != null && endpointId != 0L) {
                    endpointRepository.getById(endpointId)
                } else {
                    endpointRepository.getActive()
                }
                if (endpoint != null) {
                    if (endpointRepository.getActive()?.id != endpoint.id) {
                        endpointRepository.activateEndpoint(endpoint.id)
                    }
                    activeModelSelection.selectRemote(modelId, providerType, endpoint.id)
                }
            }
            _uiState.update {
                it.copy(
                    selectedLocalModelId = null,
                    selectedRemoteModelId = modelId,
                    selectedRemoteProvider = providerType
                )
            }
        }

        if (isSameModel) {
            val needsReload = when (providerType) {
                ProviderType.LITE_RT_LM -> engineManager.getActiveEngine() == null
                else -> false
            }
            if (!needsReload) return
        }

        // Unload previous model
        if (oldLocalId != null && oldLocalId != modelId) {
            viewModelScope.launch(coroutineExceptionHandler + Dispatchers.Default) {
                try { engineManager.unloadCurrent() } catch (e: Exception) { Timber.e(e, "Chat: unloadCurrent failed") }
            }
            activeModelSelection.disconnectLocal()
        }
        if (oldRemoteId != null && oldRemoteId != modelId) {
            viewModelScope.launch(coroutineExceptionHandler) {
                try {
                    if (oldInstance != null) {
                        val endpoint = endpointRepository.getActive()
                        if (endpoint != null) {
                            // RUNTIME-04: cleanUp() unloads the model on the helper
                            // (the helper stores the instanceId internally after
                            // initialize() returned).
                            val helper = providerRouter.resolveHelper(endpoint, oldRemoteId)
                            helper.cleanUp()
                        }
                    }
                } catch (e: Exception) { Timber.e(e, "Chat: LMStudio unload failed") }
            }
            activeModelSelection.clearRemote()
        }

        // Load new model
        when (providerType) {
            ProviderType.LM_STUDIO -> {
                viewModelScope.launch(coroutineExceptionHandler) {
                    try {
                        _uiState.update { it.copy(loadedInstanceId = null) }
                        val endpoint = endpointRepository.getActive()
                        if (endpoint != null) {
                            // RUNTIME-04: route through the unified LlmModelHelper surface.
                            val helper = providerRouter.resolveHelper(endpoint, modelId)
                            helper.initialize(modelId)
                            val instanceId =
                                (helper as? com.warped.data.remote.provider.LmStudioHelper)
                                    ?.getInstanceId()
                            if (instanceId != null) {
                                _uiState.update { it.copy(loadedInstanceId = instanceId) }
                                activeModelSelection.connectLocal(
                                    modelId, providerType, instanceId,
                                )
                            }
                        }
                    } catch (e: Exception) { Timber.e(e, "Chat: LMStudio load failed") }
                }
            }
            ProviderType.LITE_RT_LM -> {
                // Model loads on-demand on first message (handled by helper.initialize
                // in sendMessage). preloadLocalModel() also pre-warms the engine so the
                // traffic-light UI flips to "connected" faster.
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

    /**
     * 41-02: Decide whether the active model supports thinking.
     * Local model id has priority; falls back to the remote model id.
     * Returns false when no model is selected.
     */
    private fun supportsThinkingFor(localId: String?, remoteId: String?): Boolean {
        if (localId != null) {
            val model = _uiState.value.localModels.firstOrNull { it.filePath == localId }
            return model?.capabilities?.reasoning == true
        }
        return remoteId != null
    }

    /**
     * 41-02: Flip the "Thinking" toggle and persist via DataStore. The actual UI
     * state value is updated by the AdvancedPreferences collector in init().
     */
    fun toggleThinking() {
        val next = !_uiState.value.enableThinking
        viewModelScope.launch(coroutineExceptionHandler) {
            advancedPreferences.setThinkingEnabled(next)
        }
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

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch(coroutineExceptionHandler) {
            chatRepository.deleteMessage(messageId)
            _uiState.update { state ->
                state.copy(messages = state.messages.filter { it.id != messageId.toString() })
            }
        }
    }

    fun clearModelLoadError() {
        _uiState.update { it.copy(modelLoadError = null) }
    }

    /**
     * CHAT-08: Fetch the available models for a network endpoint via the provider's
     * `listModels()` API. Populates `uiState.endpointModels[endpointId]`.
     */
    fun fetchEndpointModels(endpointId: Long) {
        viewModelScope.launch(coroutineExceptionHandler) {
            val endpoint = _uiState.value.endpoints.firstOrNull { it.id == endpointId } ?: return@launch
            val modelId = endpoint.modelId ?: ""
            val provider = providerRouter.resolve(endpoint, modelId)
            provider.listModels()
                .onSuccess { models ->
                    _uiState.update { state ->
                        state.copy(
                            endpointModels = state.endpointModels + (endpointId to models.map { it.id })
                        )
                    }
                }
                .onFailure { e ->
                    Timber.w(e, "ChatViewModel: fetchEndpointModels($endpointId) failed")
                }
        }
    }

    /**
     * CHAT-08: Eagerly fetch models for all known network endpoints.
     * Called when the model picker sheet opens.
     */
    fun fetchAllEndpointModels() {
        _uiState.value.endpoints.forEach { endpoint ->
            fetchEndpointModels(endpoint.id)
        }
    }

    private fun refreshActiveBackend() {
        val isLocal = _uiState.value.selectedLocalModelId != null && _uiState.value.isLocalModelLoaded
        val backend = if (isLocal) {
            engineManager.getActiveEngine()?.backend
        } else null
        val isLoaded = isLocal && engineManager.getActiveEngine() != null
        _uiState.update { it.copy(activeBackend = backend, isLocalModelLoaded = isLocal && isLoaded) }
    }

    fun unloadLocalModels() {
        try { engineManager.scheduleUnload() } catch (e: Exception) { Timber.e(e, "Chat: scheduleUnload failed") }
        _uiState.update { it.copy(isLocalModelLoaded = false, activeBackend = null) }
    }

    private suspend fun preloadLocalModel(filePath: String) {
        val model = _uiState.value.localModels.firstOrNull { it.filePath == filePath }
        if (model != null && !memoryChecker.canLoadModel(model.sizeBytes)) {
            val memInfo = memoryChecker.getMemoryInfo()
            val modelMB = model.sizeBytes / (1024 * 1024)
            val availMB = memInfo.availableBytes / (1024 * 1024)
            _uiState.update {
                it.copy(
                    modelLoadError = "Not enough memory: model needs ${modelMB} MB but only ${availMB} MB available. Free up memory or use a smaller quantization."
                )
            }
            return
        }

        if (model != null) {
            parameterStore.update(model.parameters)
        }

        val modelName = filePath.substringAfterLast("/").removeSuffix(".litertlm")
        _uiState.update { it.copy(isLoadingModel = true, loadingModelName = modelName, modelLoadError = null) }
        try {
            withContext(Dispatchers.Default) {
                engineManager.switchToLiteRT(filePath)
            }
            activeModelSelection.connectLocal(filePath, ProviderType.LITE_RT_LM)
            _uiState.update { it.copy(isLoadingModel = false, loadingModelName = "") }
            refreshActiveBackend()
        } catch (e: Exception) {
            activeModelSelection.disconnectLocal()
            _uiState.update { it.copy(isLoadingModel = false, modelLoadError = e.message) }
        }
    }

    private fun resolvedSelectedProvider(state: ChatUiState, overrideProvider: ProviderType? = null): ProviderType {
        return overrideProvider ?: ProviderType.LITE_RT_LM
    }

    private fun LocalModel.isLiteRtLm(): Boolean =
        modelFormat.equals("LITERTLM", ignoreCase = true) || filePath.endsWith(".litertlm", ignoreCase = true)

    private fun parseThinkBlocks(raw: String, enabled: Boolean = true, modelMayThink: Boolean = false): Pair<String, String> {
        if (!enabled) {
            Timber.d("ChatVM: parseThinkBlocks disabled — raw=%d chars, mayThink=%b", raw.length, modelMayThink)
            val closeThink = raw.lowercase().lastIndexOf("<｜end▁of▁thinking｜>.)**")
            val clean = if (closeThink >= 0) {
                // Gemma 4: thinking ends with "response.)**" transition
                val after = raw.substring(closeThink + " response.)**".length)
                // Skip any leading whitespace/newlines, then return
                after.trimStart()
            } else {
                // Check for <think>/</think> or <channel|>/<|channel> patterns
                val closeIdx = raw.lowercase().lastIndexOf("</think>")
                if (closeIdx >= 0) {
                    raw.substring(closeIdx + "</think>".length).trim()
                } else {
                    val channelClose = raw.lastIndexOf("<|channel>")
                    if (channelClose >= 0) {
                        raw.substring(channelClose + "<|channel>".length).trim()
                    } else if (!modelMayThink) {
                        Regex("<[/]?think>", setOf(RegexOption.IGNORE_CASE)).replace(raw, "").trim()
                    } else if (raw.length > 400) {
                        // Model can think but no tags — strip any markers we can
                        Regex("<[/]?think>|<[/]?channel\\|?>", setOf(RegexOption.IGNORE_CASE)).replace(raw, "").trim()
                    } else {
                        ""
                    }
                }
            }
            Timber.d("ChatVM: parseThinkBlocks disabled result — clean=%d chars", clean.length)
            return Pair(clean, "")
        }
        Timber.d("ChatVM: parseThinkBlocks raw (%d chars) last 200: %s", raw.length, raw.takeLast(200))
        // Check for Gemma 4 channel-based thinking: <channel|>...<|channel>
        val channelRegex = Regex("<channel\\|>([\\s\\S]*?)<\\|channel>", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
        val reasoning = StringBuilder()
        var clean = raw
        val hasChannels = channelRegex.containsMatchIn(clean)
        channelRegex.findAll(clean).forEach { match -> reasoning.append(match.groupValues[1].trim()).append("\n") }
        clean = channelRegex.replace(clean, "")
        // Handle <think> tags as fallback (e.g. DeepSeek models)
        val thinkRegex = Regex("<think>([\\s\\S]*?)</think>", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
        val hasThinkTags = thinkRegex.containsMatchIn(clean)
        thinkRegex.findAll(clean).forEach { match -> reasoning.append(match.groupValues[1].trim()).append("\n") }
        clean = thinkRegex.replace(clean, "")
        // Handle incomplete tags
        val lastThinkOpen = clean.lowercase().lastIndexOf("<think>")
        if (lastThinkOpen >= 0) {
            val beforeTag = clean.substring(0, lastThinkOpen)
            val afterTag = clean.substring(lastThinkOpen + "<think>".length)
            reasoning.append(afterTag.trim())
            clean = beforeTag
        }
        if (reasoning.isEmpty()) {
            val closeIdx = clean.lowercase().lastIndexOf("</think>")
            if (closeIdx >= 0) {
                reasoning.append(clean.substring(0, closeIdx).trim())
                clean = clean.substring(closeIdx + "</think>".length)
            }
        }
        // If model can think and no tags found, treat entire content as reasoning
        if (reasoning.isEmpty() && modelMayThink && !hasThinkTags && !hasChannels &&
            !raw.contains("<think>", ignoreCase = true) && !raw.contains("</think>", ignoreCase = true)) {
            reasoning.append(clean.trim())
            clean = ""
        }
        Timber.d("ChatVM: parseThinkBlocks result — clean=%d reasoning=%d", clean.length, reasoning.length)
        return Pair(clean.trim(), reasoning.toString().trim())
    }

    private suspend fun ensureConversation(firstMessage: String, hasMedia: Boolean = false): Long {
        val state = _uiState.value
        if (state.conversationId != null) return state.conversationId

        val title = when {
            firstMessage.isBlank() && hasMedia -> "Image conversation"
            firstMessage.length > 50 -> firstMessage.take(50) + "..."
            else -> firstMessage
        }
        val effectiveModelId = state.selectedLocalModelId ?: state.selectedRemoteModelId
        val effectiveProvider = state.selectedLocalModelId?.let { ProviderType.LITE_RT_LM }
            ?: state.selectedRemoteProvider ?: ProviderType.LITE_RT_LM
        val effectiveEndpointId = if (effectiveProvider == ProviderType.LITE_RT_LM) {
            0L
        } else {
            activeModelSelection.remoteSelection.value.endpointId ?: 0L
        }
        val conversationId = chatRepository.createConversation(
            title = title,
            providerType = effectiveProvider,
            modelId = effectiveModelId,
            endpointId = effectiveEndpointId
        )
        _uiState.update {
            it.copy(
                conversationId = conversationId,
                conversationModelId = effectiveModelId,
                conversationProviderType = effectiveProvider
            )
        }
        activeModelSelection.saveLastConversation(conversationId)
        return conversationId
    }

    private suspend fun isModelAvailable(modelId: String, providerType: ProviderType): Boolean {
        return when (providerType) {
            ProviderType.LITE_RT_LM, ProviderType.LITE_RT_LM -> {
                localModelRepository.existsByFilePath(modelId)
            }
            else -> {
                _uiState.value.endpoints.any { it.modelId == modelId && it.apiType == providerType }
            }
        }
    }

    private var lastAutoAppliedModelId: String? = null

    private fun autoApplySmartPreset(modelId: String) {
        if (modelId == lastAutoAppliedModelId) return
        lastAutoAppliedModelId = modelId
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                val models = localModelRepository.observeModels().first()
                val model = models.firstOrNull { it.filePath == modelId } ?: return@launch
                val memInfo = memoryChecker.getMemoryInfo()
                val result = SmartPresetCalculator.calculate(memInfo, model.sizeBytes)
                parameterStore.update(result.parameters)
                Timber.d("ChatVM: auto-applied smart preset — tier=${result.tier} context=${result.parameters.contextSize} threads=${result.parameters.threads}")
            } catch (e: Exception) {
                Timber.w(e, "ChatVM: autoApplySmartPreset failed")
            }
        }
    }

    private val supportedImageTypes = setOf("image/png", "image/jpeg", "image/jpg")

    private fun uriToBase64(uri: Uri): String? {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val bytes = input.use { it.readBytes() }
            val mime = context.contentResolver.getType(uri)?.lowercase() ?: "image/png"
            if (mime !in supportedImageTypes) {
                Timber.w("ChatVM: unsupported image type $mime — only PNG and JPEG are supported")
                return null
            }
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            "data:$mime;base64,$base64"
        } catch (_: Exception) {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        unloadLocalModels()
    }
}
