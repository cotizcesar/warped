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
import com.warped.domain.llm.LlmModelHelper
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicLong
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

    // 48-01 (PERF-14): single-owner sub-states. Each field is written ONLY
    // by its owner updater (updateTranscript/updateInput/updateConnection);
    // reads spanning owners use snapshot(). uiState is the deprecated
    // combine-derived shim (no direct writes, ever).
    private val _transcript = MutableStateFlow(ChatTranscriptState())
    val transcriptState: StateFlow<ChatTranscriptState> = _transcript.asStateFlow()

    private val _input = MutableStateFlow(ChatInputState())
    val inputState: StateFlow<ChatInputState> = _input.asStateFlow()

    private val _connection = MutableStateFlow(ChatConnectionState())
    val connectionState: StateFlow<ChatConnectionState> = _connection.asStateFlow()

    @Deprecated("PERF-14 shim: collect transcriptState/inputState/connectionState instead")
    @Suppress("DEPRECATION")
    val uiState: StateFlow<ChatUiState> = combine(_transcript, _input, _connection, ::combineSnapshot)
        .stateIn(viewModelScope, SharingStarted.Eagerly, combineSnapshot(ChatTranscriptState(), ChatInputState(), ChatConnectionState()))

    @Suppress("DEPRECATION")
    private fun snapshot(): ChatUiState =
        combineSnapshot(_transcript.value, _input.value, _connection.value)

    private fun updateTranscript(op: (ChatTranscriptState) -> ChatTranscriptState) {
        _transcript.update(op)
    }

    private fun updateInput(op: (ChatInputState) -> ChatInputState) {
        _input.update(op)
    }

    private fun updateConnection(op: (ChatConnectionState) -> ChatConnectionState) {
        _connection.update(op)
    }

    private var generationJob: Job? = null

    /**
     * 46-01 RUNTIME-13/14: the helper serving the current turn, retained at
     * provider-resolution time so [stopGeneration] can reach `stopResponse()`
     * (transport-level halt). Nulled on stop and on turn completion (sequence-guarded
     * so a stale turn's `finally` never clears a follow-up turn's helper).
     */
    @Volatile
    private var activeHelper: LlmModelHelper? = null

    /** Monotonic turn counter backing the [activeHelper] stale-finally guard. */
    private val generationSeq = AtomicLong(0L)

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        timber.log.Timber.e(throwable, "Unhandled coroutine exception")
    }

    init {
        // Restore persisted loaded instance ID
        activeModelSelection.activeModel.value?.instanceId?.let {
            updateConnection { state -> state.copy(loadedInstanceId = it) }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            chatRepository.observeConversations().collect { conversations ->
                updateConnection { it.copy(conversations = conversations) }
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            parameterStore.parameters.collect { params ->
                updateConnection { it.copy(generationParameters = params) }
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            localModelRepository.observeModels().collect { models ->
                updateConnection { state ->
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

                updateConnection {
                    it.copy(
                        selectedLocalModelId = modelId,
                        isLocalModelLoaded = connected,
                        isLoadingModel = loading,
                        loadingModelName = modelId?.substringAfterLast("/") ?: it.loadingModelName,
                        loadedInstanceId = local.instanceId ?: it.loadedInstanceId,
                    )
                }
                updateInput {
                    it.copy(supportsThinking = supportsThinkingFor(modelId, _connection.value.selectedRemoteModelId))
                }
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            activeModelSelection.remoteSelection.collect { remote ->
                updateConnection {
                    it.copy(
                        selectedRemoteModelId = remote.modelId,
                        selectedRemoteProvider = remote.providerType,
                    )
                }
                updateInput {
                    it.copy(supportsThinking = supportsThinkingFor(_connection.value.selectedLocalModelId, remote.modelId))
                }
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            activeModelSelection.activeModel.collect { activeModel ->
                if (activeModel != null) {
                    updateConnection {
                        it.copy(
                            selectedModelId = activeModel.modelId,
                            selectedProvider = activeModel.providerType,
                        )
                    }
                    updateTranscript { it.copy(error = null) }
                }
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            endpointRepository.observeEndpoints().collect { endpoints ->
                updateConnection { it.copy(endpoints = endpoints) }
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            advancedPreferences.syntaxTheme.collect { theme ->
                updateConnection { it.copy(codeTheme = theme) }
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            advancedPreferences.codeFontScale.collect { scale ->
                updateConnection { it.copy(codeFontScale = scale) }
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            advancedPreferences.thinkingEnabled.collect { enabled ->
                updateInput { it.copy(enableThinking = enabled) }
            }
        }
    }

    fun sendMessage(text: String, images: List<Uri> = emptyList(), audioBytes: ByteArray? = null) {
        val state = snapshot()

        val effectiveModelId = state.selectedLocalModelId ?: state.selectedRemoteModelId
        val effectiveProvider = state.selectedLocalModelId?.let { ProviderType.LITE_RT_LM }
            ?: state.selectedRemoteProvider

        if (text.isBlank() && images.isEmpty() && audioBytes == null) return
        if (effectiveModelId == null || effectiveProvider == null) {
            updateTranscript { it.copy(error = ChatError.NoModelSelected) }
            return
        }

        if (state.modelUnavailable) {
            updateTranscript { it.copy(error = ChatError.ModelUnavailable) }
            return
        }

        val imageDataUrls = images.mapNotNull { uriToBase64(it) }
        val userMessage = ChatMessage(role = Role.USER, content = text.trim(), imageUris = imageDataUrls)
        updateTranscript { it.copy(messages = it.messages + userMessage, isStreaming = true) }
        updateInput { it.copy(inputText = "", isGenerating = true) }

        // CR-02: cancel any in-flight turn before starting a new one — otherwise
        // two collectors interleave tokens into one bubble and activeHelper is
        // clobbered (Stop then only halts the newest turn's transport).
        generationJob?.let {
            try { activeHelper?.stopResponse() } catch (e: Exception) { Timber.e(e, "Chat: prior stopResponse failed") }
            it.cancel()
        }
        generationJob = null
        activeHelper = null
        // WR-04: atomic increment — @Volatile ++ is a non-atomic read-modify-write.
        val turnId = generationSeq.incrementAndGet()
        generationJob = viewModelScope.launch(coroutineExceptionHandler) {
            try {
                val conversationId = ensureConversation(text, hasMedia = images.isNotEmpty() || audioBytes != null)
                chatRepository.saveMessage(conversationId, userMessage)

                val selectedProvider = effectiveProvider
                val modelId = effectiveModelId

                // Validate model capabilities
                if (selectedProvider == ProviderType.LITE_RT_LM) {
                    val capabilities = state.localModels.firstOrNull { it.filePath == modelId }?.capabilities
                    if (capabilities == null) {
                        Timber.w("ChatVM: capabilities unknown for $modelId — skipping media gate")
                    } else {
                        if (images.isNotEmpty() && !capabilities.vision) {
                            updateTranscript {
                                it.copy(
                                    error = ChatError.Unknown("This model does not support images (no vision capability)."),
                                    isStreaming = false
                                )
                            }
                            updateInput { it.copy(isGenerating = false) }
                            return@launch
                        }
                        if (audioBytes != null && !capabilities.audio) {
                            updateTranscript {
                                it.copy(
                                    error = ChatError.Unknown("This model does not support audio input."),
                                    isStreaming = false
                                )
                            }
                            updateInput { it.copy(isGenerating = false) }
                            return@launch
                        }
                    }
                }

                // Auto-reload local model if engine was unloaded (e.g. memory pressure)
                if (selectedProvider == ProviderType.LITE_RT_LM) {
                    val modelFile = java.io.File(modelId)
                    if (!modelFile.exists()) {
                        updateTranscript {
                            it.copy(
                                error = ChatError.DownloadModelFirst,
                                isStreaming = false
                            )
                        }
                        updateInput { it.copy(isGenerating = false) }
                        return@launch
                    }
                    // Model loads on-demand on first message
                }

                val helper = when (selectedProvider) {
                    ProviderType.LOCAL,
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
                //
                // 46-01 RUNTIME-14: retain the serving helper so stopGeneration() can
                // reach stopResponse() (transport-level halt).
                activeHelper = helper
                helper.initialize(modelId)

                val request = ChatRequest(
                    messages = _transcript.value.messages,
                    parameters = _connection.value.generationParameters.copy(
                        reasoningEnabled = _input.value.reasoningEnabled
                    ),
                    images = imageDataUrls,
                    audioBytes = audioBytes
                )
                // Phase 49 (DEL-01): single-turn chat — no skills, no tool
                // loop, no no-tool-support notice. Legacy Role.TOOL history
                // rows replay provider-side as plain text.
                val tokenBuffer = mutableListOf<String>()
                var lastEmitTime = System.currentTimeMillis()

                val rawBuffer = StringBuilder()
                val reasoningActive = _input.value.reasoningEnabled
                val modelMayThink = state.localModels.firstOrNull { it.filePath == modelId }?.capabilities?.reasoning == true
                Timber.d("ChatVM: sendMessage reasoningActive=%b modelMayThink=%b", reasoningActive, modelMayThink)

                // 46-01 RUNTIME-13: the ONE real collection — the helper's cold flow is
                // shared per-turn (shareIn replay=1 scoped to the generation job
                // itself, never in @Singleton helpers/Router) and the accumulator
                // below is the single collector. Rotation-safe: collection lives in
                // the ViewModel (survives config change); replay covers UI
                // re-subscription with no duplicate upstream work.
                helper.runInference(
                    request = request,
                    enableThinking = _input.value.enableThinking && _input.value.supportsThinking,
                ).shareIn(this, SharingStarted.Eagerly, replay = 1).collect { token ->

                    when (token) {
                        // Phase 49 (DEL-01): legacy tool tokens have no
                        // producer and carry no text — ignored single-turn.
                        is StreamToken.ToolStatus -> Unit
                        is StreamToken.ToolCompleted -> Unit
                        is StreamToken.Delta -> {
                            tokenBuffer.add(token.content)
                            val now = System.currentTimeMillis()
                            if (now - lastEmitTime >= 50) {
                                val chunk = tokenBuffer.joinToString("")
                                rawBuffer.append(chunk)
                                val (cleanContent, reasoning) = parseThinkBlocks(rawBuffer.toString(), reasoningActive, modelMayThink)
                                updateTranscript {
                                    it.copy(
                                        streamingContent = cleanContent,
                                        streamingReasoning = reasoning,
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
                            // Phase 49 (DEL-01): single-turn only — the turn
                            // persists exactly one assistant message. Legacy
                            // Role.TOOL rows are read-only history, never
                            // produced here.
                            if (content.isNotBlank() || finalReasoning.isNotBlank()) {
                                val assistantMessage = ChatMessage(
                                    role = Role.ASSISTANT,
                                    content = content,
                                    tokenCount = content.length / 4,
                                    reasoning = finalReasoning.ifEmpty { token.reasoning },
                                    stats = token.stats
                                )
                                updateTranscript {
                                    it.copy(
                                        messages = it.messages + assistantMessage,
                                        streamingContent = "",
                                        streamingReasoning = "",
                                        isStreaming = false
                                    )
                                }
                                updateInput { it.copy(isGenerating = false) }
                                chatRepository.saveMessage(conversationId, assistantMessage)
                            } else {
                                // Silent turn: clear streaming state so the
                                // bubble does not hang.
                                updateTranscript {
                                    it.copy(
                                        streamingContent = "",
                                        streamingReasoning = "",
                                        isStreaming = false
                                    )
                                }
                                updateInput { it.copy(isGenerating = false) }
                            }
                        }
                        is StreamToken.Error -> {
                            updateTranscript {
                                it.copy(
                                    error = ChatError.Network(token.message),
                                    streamingContent = "",
                                    streamingReasoning = "",
                                    isStreaming = false
                                )
                            }
                            updateInput { it.copy(isGenerating = false) }
                        }
                    }
                }
            } catch (e: CancellationException) {
                // CR-01: Stop means stop — user-initiated cancel is not an error.
                // Rethrow so structured concurrency observes cancellation; the
                // finally (stale-seq guard) still runs on the rethrow path.
                throw e
            } catch (e: Exception) {
                updateTranscript {
                    it.copy(
                        error = ChatError.Network(e.message ?: "Unknown error"),
                        isStreaming = false
                    )
                }
                updateInput { it.copy(isGenerating = false) }
            } finally {
                // 46-01: clear the serving helper on turn end — but only if no newer
                // turn has started since (stale-finally guard via turnId/seq).
                if (turnId == generationSeq.get()) activeHelper = null
            }
        }
    }

    /**
     * 46-01 RUNTIME-14: Stop means stop. Transport-stop FIRST (socket/native halt
     * via the serving helper — no trailing tokens, no fake Error), then
     * scope-cancel (shareIn upstream), then streaming-state reset. Both handles
     * are nulled so an immediate follow-up sendMessage resolves a fresh helper
     * with no stale handle (pitfall 2). Safe when idle.
     */
    fun stopGeneration() {
        // WR-05: snapshot-then-null so a stale Stop never clobbers a newer turn's
        // transport started after this call was dispatched.
        val helper = activeHelper
        activeHelper = null
        try {
            helper?.stopResponse()
        } catch (e: Exception) {
            Timber.e(e, "Chat: stopResponse failed")
        }
        generationJob?.cancel()
        generationJob = null
        updateTranscript { it.copy(
            isStreaming = false,
            streamingContent = "",
            streamingReasoning = ""
        ) }
        updateInput { it.copy(isGenerating = false) }
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
                updateTranscript {
                    it.copy(
                        conversationId = conversation.id,
                        messages = messages,
                        streamingContent = "",
                        streamingReasoning = "",
                        error = null,
                    )
                }
                updateConnection {
                    val isLocalConv = conversation.providerType == ProviderType.LITE_RT_LM
                    it.copy(
                        selectedLocalModelId = if (isLocalConv) conversation.modelId else null,
                        selectedRemoteModelId = if (!isLocalConv) conversation.modelId else null,
                        selectedRemoteProvider = if (!isLocalConv) conversation.providerType else null,
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
        updateTranscript {
            it.copy(
                conversationId = null,
                messages = emptyList(),
                streamingContent = "",
                streamingReasoning = "",
                error = null
            )
        }
        updateConnection {
            it.copy(
                conversationModelId = null,
                conversationProviderType = null,
                modelUnavailable = false
            )
        }
    }

    fun updateInput(text: String) {
        updateInput { it.copy(inputText = text) }
    }

    fun launchModelSelection(modelId: String, providerType: ProviderType, endpointId: Long? = null) {
        val state = snapshot()

        // If we're in a conversation and the model is different, block and show dialog
        val conversationModelId = state.conversationModelId
        if (conversationModelId != null && state.messages.isNotEmpty() &&
            (modelId != conversationModelId || providerType != state.conversationProviderType)) {
            updateConnection { it.copy(pendingModelSwitch = ModelSwitchRequest(modelId, providerType, endpointId)) }
            return
        }

        // Check memory for local models
        if (providerType == ProviderType.LITE_RT_LM) {
            val model = _connection.value.localModels.firstOrNull { it.filePath == modelId }
            if (model != null && memoryChecker.shouldWarn(model.sizeBytes)) {
                updateConnection { it.copy(memoryWarningModel = model) }
                return
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            setSelectedModel(modelId, providerType, endpointId, isSameModel = false)
        }
    }

    fun confirmModelSwitch() {
        val pending = _connection.value.pendingModelSwitch ?: return
        viewModelScope.launch(coroutineExceptionHandler) {
            // Create new conversation, clearing old ID and messages
            updateTranscript {
                it.copy(
                    conversationId = null,
                    messages = emptyList(),
                    streamingContent = "",
                    streamingReasoning = "",
                    error = null
                )
            }
            updateConnection {
                it.copy(
                    conversationModelId = pending.modelId,
                    conversationProviderType = pending.providerType,
                    pendingModelSwitch = null,
                )
            }
            setSelectedModel(pending.modelId, pending.providerType, pending.endpointId, isSameModel = false)
        }
    }

    fun cancelModelSwitch() {
        updateConnection { it.copy(pendingModelSwitch = null) }
    }

    fun dismissModelUnavailable() {
        updateConnection { it.copy(
            modelUnavailable = false,
            conversationModelId = null,
            conversationProviderType = null
        ) }
    }

    fun confirmLoadMemoryWarning() {
        val model = _connection.value.memoryWarningModel ?: return
        updateConnection { it.copy(memoryWarningModel = null) }
        val providerType = if (model.isLiteRtLm()) ProviderType.LITE_RT_LM else ProviderType.LITE_RT_LM
        viewModelScope.launch(coroutineExceptionHandler) {
            setSelectedModel(model.filePath, providerType, null, isSameModel = false)
        }
    }

    fun dismissMemoryWarning() {
        updateConnection { it.copy(memoryWarningModel = null) }
    }

    private fun setSelectedModel(modelId: String, providerType: ProviderType, endpointId: Long?, isSameModel: Boolean) {
        val oldLocalId = _connection.value.selectedLocalModelId
        val oldRemoteId = _connection.value.selectedRemoteModelId
        val oldInstance = _connection.value.loadedInstanceId

        if (providerType == ProviderType.LITE_RT_LM) {
            activeModelSelection.markLocalLoading(modelId)
            updateConnection {
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
            updateConnection {
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
                        updateConnection { it.copy(loadedInstanceId = null) }
                        val endpoint = endpointRepository.getActive()
                        if (endpoint != null) {
                            // RUNTIME-04: route through the unified LlmModelHelper surface.
                            val helper = providerRouter.resolveHelper(endpoint, modelId)
                            helper.initialize(modelId)
                            val instanceId =
                                (helper as? com.warped.data.remote.provider.LmStudioHelper)
                                    ?.getInstanceId()
                            if (instanceId != null) {
                                updateConnection { it.copy(loadedInstanceId = instanceId) }
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
        updateConnection { it.copy(generationParameters = params) }
    }

    fun toggleReasoning() {
        updateInput { it.copy(reasoningEnabled = !it.reasoningEnabled) }
    }

    /**
     * 41-02: Decide whether the active model supports thinking.
     * Local model id has priority; falls back to the remote model id.
     * Returns false when no model is selected.
     *
     * 48 (WR-03): capability check, not a presence check — consults the
     * local model's reasoning capability. Fail-open while the model list
     * has not loaded yet (unknown ≠ unsupported). Remote models have no
     * capability data source yet, so presence still gates there.
     */
    private fun supportsThinkingFor(localId: String?, remoteId: String?): Boolean {
        if (localId != null) {
            val models = _connection.value.localModels
            if (models.isEmpty()) return true
            return models.firstOrNull { it.filePath == localId }?.capabilities?.reasoning == true
        }
        return remoteId != null
    }

    /**
     * 41-02: Flip the "Thinking" toggle and persist via DataStore. The actual UI
     * state value is updated by the AdvancedPreferences collector in init().
     */
    fun toggleThinking() {
        val next = !_input.value.enableThinking
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
        updateTranscript { it.copy(error = null) }
    }

    /**
     * 48 (WR-06): takes the row's String id — [ChatMessage.id] defaults to a
     * UUID, so a just-sent message not yet reloaded from Room keeps a
     * non-numeric id a Long parameter cannot express. The DB delete still
     * needs the numeric key, parsed best-effort; the local filter always
     * matches on the String id so the bubble disappears immediately.
     */
    fun deleteMessage(messageId: String) {
        viewModelScope.launch(coroutineExceptionHandler) {
            messageId.toLongOrNull()?.let { chatRepository.deleteMessage(it) }
            updateTranscript { state ->
                state.copy(messages = state.messages.filter { it.id != messageId })
            }
        }
    }

    /** Room-sourced call sites holding the numeric key. */
    fun deleteMessage(messageId: Long) = deleteMessage(messageId.toString())

    fun clearModelLoadError() {
        updateConnection { it.copy(modelLoadError = null) }
    }

    /**
     * CHAT-08: Fetch the available models for a network endpoint via the provider's
     * `listModels()` API. Populates `uiState.endpointModels[endpointId]`.
     */
    fun fetchEndpointModels(endpointId: Long) {
        viewModelScope.launch(coroutineExceptionHandler) {
            val endpoint = _connection.value.endpoints.firstOrNull { it.id == endpointId } ?: return@launch
            val modelId = endpoint.modelId ?: ""
            val provider = providerRouter.resolve(endpoint, modelId)
            provider.listModels()
                .onSuccess { models ->
                    updateConnection { state ->
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
        _connection.value.endpoints.forEach { endpoint ->
            fetchEndpointModels(endpoint.id)
        }
    }

    private fun refreshActiveBackend() {
        val connection = _connection.value
        val isLocal = connection.selectedLocalModelId != null && connection.isLocalModelLoaded
        val backend = if (isLocal) {
            engineManager.getActiveEngine()?.backend
        } else null
        val isLoaded = isLocal && engineManager.getActiveEngine() != null
        updateConnection { it.copy(activeBackend = backend, isLocalModelLoaded = isLocal && isLoaded) }
    }

    fun unloadLocalModels() {
        try { engineManager.scheduleUnload() } catch (e: Exception) { Timber.e(e, "Chat: scheduleUnload failed") }
        updateConnection { it.copy(isLocalModelLoaded = false, activeBackend = null) }
    }

    private suspend fun preloadLocalModel(filePath: String) {
        val model = _connection.value.localModels.firstOrNull { it.filePath == filePath }
        if (model != null && !memoryChecker.canLoadModel(model.sizeBytes)) {
            val memInfo = memoryChecker.getMemoryInfo()
            val modelMB = model.sizeBytes / (1024 * 1024)
            val availMB = memInfo.availableBytes / (1024 * 1024)
            updateConnection {
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
        updateConnection { it.copy(isLoadingModel = true, loadingModelName = modelName, modelLoadError = null) }
        try {
            withContext(Dispatchers.Default) {
                engineManager.switchToLiteRT(filePath)
            }
            activeModelSelection.connectLocal(filePath, ProviderType.LITE_RT_LM)
            updateConnection { it.copy(isLoadingModel = false, loadingModelName = "") }
            refreshActiveBackend()
        } catch (e: Exception) {
            activeModelSelection.disconnectLocal()
            updateConnection { it.copy(isLoadingModel = false, modelLoadError = e.message) }
        }
    }

    private fun resolvedSelectedProvider(state: ChatUiState, overrideProvider: ProviderType? = null): ProviderType {
        return overrideProvider ?: ProviderType.LITE_RT_LM
    }

    private fun LocalModel.isLiteRtLm(): Boolean =
        modelFormat.equals("LITERTLM", ignoreCase = true) || filePath.endsWith(".litertlm", ignoreCase = true)

    private fun parseThinkBlocks(raw: String, enabled: Boolean = true, @Suppress("UNUSED_PARAMETER") modelMayThink: Boolean = false): Pair<String, String> {
        if (!enabled) {
            val clean = Regex("<[/]?think>|<[/]?channel\\|?>", setOf(RegexOption.IGNORE_CASE))
                .replace(raw, "").trim()
            return Pair(clean, "")
        }
        val reasoning = StringBuilder()
        var clean = raw

        val channelRegex = Regex("<channel\\|>([\\s\\S]*?)<\\|channel>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
        channelRegex.findAll(clean).forEach { m -> reasoning.append(m.groupValues[1].trim()).append("\n") }
        clean = channelRegex.replace(clean, "")

        val thinkRegex = Regex("<think>([\\s\\S]*?)</think>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
        thinkRegex.findAll(clean).forEach { m -> reasoning.append(m.groupValues[1].trim()).append("\n") }
        clean = thinkRegex.replace(clean, "")

        val openIdx = clean.lowercase().lastIndexOf("<think>")
        if (openIdx >= 0) {
            reasoning.append(clean.substring(openIdx + "<think>".length).trim())
            clean = clean.substring(0, openIdx)
        }

        // Untagged output is the answer, not reasoning: without explicit
        // <think>/<channel|> markers there is no evidence the model was thinking,
        // and routing plain replies into the collapsed Thinking panel produces
        // empty assistant bubbles (local-empty-response, 2026-09-28). Genuine
        // 0.17.x thought-channel streaming stays a later-phase wire-up
        // (LiteRTLmProvider.extractThoughtContent) — never inferred from absence.

        Timber.d("ChatVM: parseThinkBlocks result — clean=%d reasoning=%d", clean.length, reasoning.length)
        return Pair(clean.trim(), reasoning.toString().trim())
    }

    private suspend fun ensureConversation(firstMessage: String, hasMedia: Boolean = false): Long {
        val state = snapshot()
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
        updateTranscript { it.copy(conversationId = conversationId) }
        updateConnection {
            it.copy(
                conversationModelId = effectiveModelId,
                conversationProviderType = effectiveProvider
            )
        }
        activeModelSelection.saveLastConversation(conversationId)
        return conversationId
    }

    private suspend fun isModelAvailable(modelId: String, providerType: ProviderType): Boolean {
        return when (providerType) {
            ProviderType.LOCAL, ProviderType.LITE_RT_LM -> {
                localModelRepository.existsByFilePath(modelId)
            }
            else -> {
                _connection.value.endpoints.any { it.modelId == modelId && it.apiType == providerType }
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
