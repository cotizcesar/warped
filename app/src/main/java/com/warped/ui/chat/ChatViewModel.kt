package com.warped.ui.chat

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warped.data.local.inference.BackendType
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.InputSanitizer
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
    private val inputSanitizer: InputSanitizer,
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
        viewModelScope.launch(coroutineExceptionHandler) {
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
    }

    fun sendMessage(text: String, images: List<Uri> = emptyList(), audioBytes: ByteArray? = null) {
        val state = _uiState.value
        if (text.isBlank() && images.isEmpty() && audioBytes == null) return
        if (state.selectedModelId == null || state.selectedProvider == null) {
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
                    // Model loads on-demand on first message
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
                    images = imageDataUrls,
                    audioBytes = audioBytes
                )
                val tokenBuffer = mutableListOf<String>()
                var lastEmitTime = System.currentTimeMillis()

                val rawBuffer = StringBuilder()
                val reasoningActive = _uiState.value.reasoningEnabled
                val modelMayThink = state.localModels.firstOrNull { it.filePath == modelId }?.capabilities?.reasoning == true
                Timber.d("ChatVM: sendMessage reasoningActive=%b modelMayThink=%b", reasoningActive, modelMayThink)

                provider.chat(request).collect { token ->
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
                    it.copy(
                        conversationId = conversation.id,
                        messages = messages,
                        selectedModelId = conversation.modelId,
                        selectedProvider = conversation.providerType,
                        streamingContent = "",
                        streamingReasoning = "",
                        error = null,
                        conversationModelId = conversation.modelId,
                        conversationProviderType = conversation.providerType,
                        modelUnavailable = modelMissing
                    )
                }
                if (conversation.modelId != null && !modelMissing) {
                    activeModelSelection.select(conversation.modelId, conversation.providerType)
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

    fun launchModelSelection(modelId: String, providerType: ProviderType) {
        val state = _uiState.value

        // If we're in a conversation and the model is different, block and show dialog
        val conversationModelId = state.conversationModelId
        if (conversationModelId != null && state.messages.isNotEmpty() &&
            (modelId != conversationModelId || providerType != state.conversationProviderType)) {
            _uiState.update { it.copy(pendingModelSwitch = ModelSwitchRequest(modelId, providerType)) }
            return
        }

        // Check memory for local models
        if (providerType == ProviderType.LOCAL || providerType == ProviderType.LITE_RT_LM) {
            val model = _uiState.value.localModels.firstOrNull { it.filePath == modelId }
            if (model != null && memoryChecker.shouldWarn(model.sizeBytes)) {
                _uiState.update { it.copy(memoryWarningModel = model) }
                return
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            setSelectedModel(modelId, providerType, isSameModel = false)
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
            setSelectedModel(pending.modelId, pending.providerType, isSameModel = false)
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
        val providerType = if (model.isLiteRtLm()) ProviderType.LITE_RT_LM else ProviderType.LOCAL
        viewModelScope.launch(coroutineExceptionHandler) {
            setSelectedModel(model.filePath, providerType, isSameModel = false)
        }
    }

    fun dismissMemoryWarning() {
        _uiState.update { it.copy(memoryWarningModel = null) }
    }

    private fun setSelectedModel(modelId: String, providerType: ProviderType, isSameModel: Boolean) {
        val oldProvider = _uiState.value.selectedProvider
        val oldModelId = _uiState.value.selectedModelId
        val oldInstance = _uiState.value.loadedInstanceId
        
        activeModelSelection.select(modelId, providerType)
        _uiState.update { it.copy(selectedModelId = modelId, selectedProvider = providerType) }
        
        if (isSameModel) {
            // Even if same model, reload if engine was unloaded (memory pressure)
            val needsReload = when (providerType) {
                ProviderType.LITE_RT_LM -> engineManager.getActiveEngine() == null
                else -> false
            }
            if (!needsReload) return
        }
        
        // Unload previous model
        when (oldProvider) {
            ProviderType.LM_STUDIO -> {
                if (oldInstance != null && oldModelId != null) {
                    viewModelScope.launch(coroutineExceptionHandler) {
                        try {
                            val endpoint = endpointRepository.getActive()
                            if (endpoint != null) {
                                val provider = com.warped.data.remote.provider.LMStudioProvider(endpoint.url, oldModelId, inputSanitizer = inputSanitizer)
                                provider.unloadModel(oldInstance)
                            }
                        } catch (e: Exception) { Timber.e(e, "Chat: LMStudio unload failed") }
                    }
                }
            }
            ProviderType.LITE_RT_LM -> {
                viewModelScope.launch(coroutineExceptionHandler + Dispatchers.Default) {
                    try { engineManager.unloadCurrent() } catch (e: Exception) { Timber.e(e, "Chat: unloadCurrent failed") }
                }
            }
            else -> {}
        }
        
        // Load new model
        when (providerType) {
            ProviderType.LM_STUDIO -> {
                viewModelScope.launch(coroutineExceptionHandler) {
                    try {
                        _uiState.update { it.copy(loadedInstanceId = null) }
                        val endpoint = endpointRepository.getActive()
                        if (endpoint != null) {
                            val provider = com.warped.data.remote.provider.LMStudioProvider(endpoint.url, modelId, inputSanitizer = inputSanitizer)
                            val result = provider.loadModel(modelId)
                            result.onSuccess { instanceId ->
                                _uiState.update { it.copy(loadedInstanceId = instanceId) }
                                activeModelSelection.select(modelId, providerType, instanceId)
                            }
                        }
                    } catch (e: Exception) { Timber.e(e, "Chat: LMStudio load failed") }
                }
            }
            ProviderType.LITE_RT_LM -> {
                // Model loads on-demand on first message
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

    private fun refreshActiveBackend() {
        val isLocal = _uiState.value.selectedProvider == ProviderType.LITE_RT_LM
        val backend = if (_uiState.value.selectedProvider == ProviderType.LITE_RT_LM) {
            engineManager.getActiveEngine()?.backend
        } else null
        val isLoaded = _uiState.value.selectedProvider == ProviderType.LITE_RT_LM && engineManager.getActiveEngine() != null
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

        val modelName = filePath.substringAfterLast("/").removeSuffix(".litertlm")
        _uiState.update { it.copy(isLoadingModel = true, loadingModelName = modelName, modelLoadError = null) }
        try {
            withContext(Dispatchers.Default) {
                engineManager.switchToLiteRT(filePath)
            }
            _uiState.update { it.copy(isLoadingModel = false, loadingModelName = "") }
            refreshActiveBackend()
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoadingModel = false, modelLoadError = e.message) }
        }
    }

    private fun resolvedSelectedProvider(state: ChatUiState): ProviderType {
        val provider = state.selectedProvider ?: return ProviderType.LITE_RT_LM
        return if (provider == ProviderType.LOCAL) ProviderType.LITE_RT_LM else provider
    }

    private fun LocalModel.isLiteRtLm(): Boolean =
        modelFormat.equals("LITERTLM", ignoreCase = true) || filePath.endsWith(".litertlm", ignoreCase = true)

    private fun parseThinkBlocks(raw: String, enabled: Boolean = true, modelMayThink: Boolean = false): Pair<String, String> {
        if (!enabled) {
            Timber.d("ChatVM: parseThinkBlocks disabled — raw=%d chars, mayThink=%b", raw.length, modelMayThink)
            val closeIdx = raw.lowercase().lastIndexOf("</think>")
            val clean = if (closeIdx >= 0) {
                raw.substring(closeIdx + "</think>".length).trim()
            } else if (!modelMayThink || raw.length > 400) {
                // No </think> and model doesn't think, or enough chars without it
                Regex("<[/]?think>", setOf(RegexOption.IGNORE_CASE)).replace(raw, "").trim()
            } else {
                "" // Waiting for </think>
            }
            Timber.d("ChatVM: parseThinkBlocks disabled result — clean=%d chars", clean.length)
            return Pair(clean, "")
        }
        Timber.d("ChatVM: parseThinkBlocks raw (%d chars) last 200: %s", raw.length, raw.takeLast(200))
        val completeRegex = Regex("<think>([\\s\\S]*?)</think>", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
        val reasoning = StringBuilder()
        var clean = raw
        val hasCompleteTags = completeRegex.containsMatchIn(clean)
        completeRegex.findAll(clean).forEach { match -> reasoning.append(match.groupValues[1].trim()).append("\n") }
        clean = completeRegex.replace(clean, "")
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
        if (reasoning.isEmpty() && modelMayThink && !hasCompleteTags && !raw.contains("<think>", ignoreCase = true) && !raw.contains("</think>", ignoreCase = true)) {
            reasoning.append(clean.trim())
            clean = ""
        }
        Timber.d("ChatVM: parseThinkBlocks result — clean=%d reasoning=%d", clean.length, reasoning.length)
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
        _uiState.update {
            it.copy(
                conversationId = conversationId,
                conversationModelId = state.selectedModelId,
                conversationProviderType = resolvedSelectedProvider(state)
            )
        }
        activeModelSelection.saveLastConversation(conversationId)
        return conversationId
    }

    private fun isModelAvailable(modelId: String, providerType: ProviderType): Boolean {
        return when (providerType) {
            ProviderType.LOCAL, ProviderType.LITE_RT_LM -> {
                _uiState.value.localModels.any { it.filePath == modelId }
            }
            else -> {
                _uiState.value.endpoints.any { it.modelId == modelId && it.apiType == providerType }
            }
        }
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

    override fun onCleared() {
        super.onCleared()
        unloadLocalModels()
    }
}
