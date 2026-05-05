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
import com.warped.data.local.inference.LlamaLoadError
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
            // Only restore model when resuming a conversation — no auto-select on startup
            activeModelSelection.activeModel.collect { activeModel ->
                if (activeModel != null && _uiState.value.conversationId != null) {
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
        viewModelScope.launch {
            endpointRepository.observeEndpoints().collect { endpoints ->
                _uiState.update { it.copy(endpoints = endpoints) }
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
                            tokenBuffer.add(token.content)
                            val now = System.currentTimeMillis()
                            if (now - lastEmitTime >= 50) {
                                val chunk = tokenBuffer.joinToString("")
                                rawBuffer.append(chunk)
                                val (cleanContent, reasoning) = parseThinkBlocks(rawBuffer.toString(), reasoningActive, modelMayThink)
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
        _uiState.update { it.copy(isStreaming = false) }
    }

    fun selectConversation(conversationId: Long) {
        engineManager.scheduleUnload()
        viewModelScope.launch {
            val result = chatRepository.loadConversation(conversationId)
            if (result != null) {
                val (conversation, messages) = result
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
                    // Model loads on-demand on first message
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
        viewModelScope.launch {
            setSelectedModel(modelId, providerType, isSameModel = false)
        }
    }

    fun confirmModelSwitch() {
        val pending = _uiState.value.pendingModelSwitch ?: return
        viewModelScope.launch {
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
        viewModelScope.launch {
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
                // Model loads on-demand on first message
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
        try { engineManager.scheduleUnload() } catch (_: Exception) {}
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

        val isLitertlm = model?.isLiteRtLm() == true || filePath.endsWith(".litertlm", ignoreCase = true)
        val modelName = filePath.substringAfterLast("/").removeSuffix(".gguf").removeSuffix(".litertlm")
        _uiState.update { it.copy(isLoadingModel = true, loadingModelName = modelName, modelLoadError = null) }
        try {
            withContext(Dispatchers.Default) {
                if (isLitertlm) {
                    engineManager.switchToLiteRT(filePath)
                } else {
                    val loadResult = engineManager.switchToLlama(filePath)
                    if (loadResult.isFailure) {
                        val error = loadResult.exceptionOrNull()
                        val msg = (error as? LlamaLoadError)?.userMessage ?: error?.message ?: "Failed to load GGUF model"
                        throw IllegalStateException(msg)
                    }
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
