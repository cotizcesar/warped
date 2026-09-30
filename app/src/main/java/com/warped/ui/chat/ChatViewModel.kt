package com.warped.ui.chat

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warped.data.grounding.DuckDuckGoSearchRepository
import com.warped.data.grounding.GroundingPrecedence
import com.warped.data.grounding.GroundingPrompt
import com.warped.data.grounding.GroundingResult
import com.warped.data.grounding.MultiUrlFetcher
import com.warped.data.grounding.MultiUrlResult
import com.warped.data.grounding.TavilySearchOutcome
import com.warped.data.grounding.TavilySearchRepository
import com.warped.data.grounding.UrlDetector
import com.warped.data.grounding.WebPageFetcher
import com.warped.data.agentic.LocalToolLoop
import com.warped.data.agentic.ToolCapabilityMatrix
import com.warped.data.agentic.ToolMode
import com.warped.data.local.inference.BackendType
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.remote.provider.ProviderRouter
import com.warped.data.repository.ModelAllowlistRepository
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
import com.warped.R
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
    private val fetcher: WebPageFetcher,
    private val multiUrlFetcher: MultiUrlFetcher,
    /**
     * Quick-task (DDG-default): the search branch goes through the
     * DDG-primary/Tavily-fallback repository (same outcome type, same
     * signatures in/out). Tavily stays in the graph as the DDG repo's
     * internal fallback delegate + the Settings key probe — not as a
     * direct VM dependency.
     */
    private val ddgSearchRepository: DuckDuckGoSearchRepository,
    /**
     * Phase 56 (56-02): allowlist capability read for the VM-side
     * loop-arming check (skip VM pre-search when the provider loop will
     * fire). Verified-only semantics live in the repository.
     */
    private val modelAllowlistRepository: ModelAllowlistRepository,
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
     * Phase 54 (RETRY-01): foreground retry scope, sibling to
     * [generationJob]. A second tap while a retry is in flight is a no-op
     * (WR-02: synchronous `retryJob.isActive` guard on the caller thread —
     * the `isFetchingWeb` flag is set inside the coroutine and races).
     * Cancelled on new send (same pre-cancel position as [generationJob])
     * and in [stopGeneration]; the retry `finally` clears state only when
     * it still owns the job (stale-finally guard) so Stop/new-send state
     * is never clobbered. Stop-during-retry leaves the transcript
     * untouched — the queued OFFLINE banner survives.
     */
    private var retryJob: Job? = null

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

    /**
     * Phase 50 (WEB-06): grounding toggle snapshot, collected from
     * DataStore (default ON). Read at turn start; toggle takes effect on
     * the next sent message.
     */
    @Volatile
    private var webGroundingEnabled = true

    /**
     * Phase 53 (TOGGLE-01/SRC-02): one-shot Snackbar events for ChatScreen.
     * tryEmit only — the turn never suspends waiting for a collector.
     */
    private val _events = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ChatEvent> = _events.asSharedFlow()

    /**
     * Phase 53 (TOGGLE-01): override chosen before the first send (no
     * conversation row yet). Applied once in [ensureConversation], then
     * cleared. Either this or hiding the control pre-conversation satisfies
     * RESEARCH open question 3 — holding pending keeps the control always
     * visible with zero new surfaces.
     */
    @Volatile
    private var pendingWebOverride: Boolean? = null

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
        viewModelScope.launch(coroutineExceptionHandler) {
            advancedPreferences.webGroundingEnabled.collect { enabled ->
                webGroundingEnabled = enabled
                updateConnection { it.copy(webGroundingEnabled = enabled) }
            }
        }
        // Phase 54 (RETRY-01): seed the validated-online flag from the same
        // NET_CAPABILITY_VALIDATED gate the fetcher uses.
        refreshConnectivity()
    }

    /**
     * Phase 54 (RETRY-01): re-read the fetcher's validated-connectivity gate
     * into input state. Called on init, after each send completes, and after
     * each retry completes/fails — never a live observer (reconnect alone
     * must NOT fetch, per the no-auto-retry lock). Best-effort: a gate
     * failure reads as offline, never a crash.
     */
    fun refreshConnectivity() {
        val online = try {
            fetcher.hasValidatedInternet()
        } catch (e: Exception) {
            Timber.w(e, "Chat: connectivity check failed, treating as offline")
            false
        }
        updateInput { it.copy(isValidatedOnline = online) }
    }

    /**
     * Phase 53 (TOGGLE-01): tri-state per-chat override write. Applies to the
     * next send only — history is never refetched. Before the first send
     * (no conversation row yet) the value is held as [pendingWebOverride]
     * and applied at [ensureConversation].
     */
    fun setWebOverride(override: Boolean?) {
        val conversationId = _transcript.value.conversationId
        if (conversationId == null) {
            pendingWebOverride = override
            updateConnection { it.copy(webOverride = override) }
            _events.tryEmit(
                ChatEvent.Snackbar(context.getString(R.string.snack_web_pref_updated)),
            )
            return
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                chatRepository.setWebOverride(conversationId, override)
                updateConnection { it.copy(webOverride = override) }
                _events.tryEmit(
                    ChatEvent.Snackbar(context.getString(R.string.snack_web_pref_updated)),
                )
            } catch (e: Exception) {
                Timber.e(e, "Chat: setWebOverride failed")
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
        // 56-02: fresh turn clears any stale tool row (same position as the
        // 47 toolCallActive reset).
        updateInput { it.copy(inputText = "", isGenerating = true, toolCallActive = null) }

        // CR-02: cancel any in-flight turn before starting a new one — otherwise
        // two collectors interleave tokens into one bubble and activeHelper is
        // clobbered (Stop then only halts the newest turn's transport).
        // Phase 50: also cancel any in-flight grounding fetch Call.
        try { fetcher.cancel() } catch (e: Exception) { Timber.e(e, "Chat: prior fetch cancel failed") }
        generationJob?.let {
            try { activeHelper?.stopResponse() } catch (e: Exception) { Timber.e(e, "Chat: prior stopResponse failed") }
            it.cancel()
        }
        generationJob = null
        activeHelper = null
        // Phase 54 (RETRY-01): a new send supersedes any in-flight retry —
        // same pre-cancel position as generationJob so a retry can never
        // write rows for a turn the new send is replacing.
        retryJob?.cancel()
        retryJob = null
        // WR-04: atomic increment — @Volatile ++ is a non-atomic read-modify-write.
        val turnId = generationSeq.incrementAndGet()
        generationJob = viewModelScope.launch(coroutineExceptionHandler) {
            try {
                val conversationId = ensureConversation(text, hasMedia = images.isNotEmpty() || audioBytes != null)
                chatRepository.saveMessage(conversationId, userMessage)

                // Phase 52 (FETCH-01..FETCH-03): multi-URL grounding fan-out —
                // detect → fan-out → augment. Same position as the v2.2 hook
                // (after save, before helper resolution). History keeps
                // persisted originals; only the outgoing request's current
                // message carries the augmented text. Providers/helpers stay
                // untouched (prompt-prefix augmentation only).
                var requestUserText = userMessage.content
                var groundedSources: List<String> = emptyList()
                var groundedSourceDetails: List<GroundedSource> = emptyList()
                // Quick-task (agentic-rows): per-turn tool-source
                // accumulator. Loop drivers (local `runToolLoop`,
                // `CompatToolLoop`, OpenAI/Anthropic inline loops) emit one
                // `ToolCompleted` per executed search/fetch call carrying
                // the call's structured rows; they union here (first-seen
                // order, distinct by URL — same union semantics as the
                // fetch/search branches) and merge with the pre-search
                // details on Done below. Armed turns skip the VM
                // pre-search, so this is normally the only source.
                val loopSourceDetails = mutableListOf<GroundedSource>()
                var modelOnlyNotice: ModelOnlyNotice? = null
                var modelOnlySourceCount: Int = 1
                // Grounding precedence (threat T-53-05): single decision
                // at the top of the hook — the per-chat override read ONCE
                // for this send (never a hot Flow: avoids mid-turn flips and
                // recomposition storms), then the global default-ON. False
                // skips grounding entirely with the model-only behavior
                // unchanged.
                val perChatOverride = try {
                    chatRepository.getWebOverride(conversationId)
                } catch (e: Exception) {
                    Timber.w(e, "Chat: override read failed, falling back to global")
                    null
                }
                val doGround = GroundingPrecedence.shouldGround(
                    perChat = perChatOverride,
                    global = webGroundingEnabled,
                )
                if (doGround) {
                    val groundedUrls = UrlDetector.allUrls(userMessage.content)
                    if (groundedUrls.isNotEmpty()) {
                        updateInput {
                            it.copy(
                                isFetchingWeb = true,
                                webFetchProgress = WebFetchProgress(
                                    done = 0,
                                    total = groundedUrls.size,
                                    perSource = groundedUrls.map { url ->
                                        SourceFetchState(url, PerSourceStatus.LOADING)
                                    },
                                ),
                            )
                        }
                        try {
                            val contextSize = state.generationParameters.contextSize
                            when (
                                val result = multiUrlFetcher.fetchAll(
                                    urls = groundedUrls,
                                    contextSize = contextSize,
                                    onProgress = { done, total ->
                                        updateInput { s ->
                                            s.copy(
                                                webFetchProgress = s.webFetchProgress?.copy(
                                                    done = done,
                                                    total = total,
                                                ),
                                            )
                                        }
                                    },
                                )
                            ) {
                                is MultiUrlResult.Fused -> {
                                    requestUserText = GroundingPrompt.augment(
                                        requestUserText,
                                        result.block,
                                        groundingEnabled = doGround,
                                    )
                                    groundedSources = result.okUrls
                                    // Phase 53 (SRC-02): CR-01 — persist the fusion-time
                                    // details union directly (resolved URLs + text
                                    // survive redirects). The legacy pasted-key
                                    // lookup is fallback-only for hand-built Fused
                                    // carriers that predate details.
                                    groundedSourceDetails = if (result.details.isNotEmpty()) {
                                        result.details
                                    } else {
                                        buildSourceDetails(groundedUrls, result)
                                    }
                                    val skipped = result.skippedUrls.toSet()
                                    // Terminal per-source snapshot: records OK/OMITIDA
                                    // into progress state (FETCH-02 no-silent-drops
                                    // contract). Transient by construction — the
                                    // enclosing finally clears progress right after —
                                    // and not rendered in Phase 52 (chip is
                                    // counts-only per UI-SPEC); kept as the recorded
                                    // state for Phase 53 bottom-sheet consumers.
                                    updateInput { s ->
                                        s.copy(
                                            webFetchProgress = s.webFetchProgress?.copy(
                                                done = groundedUrls.size,
                                                perSource = groundedUrls.map { url ->
                                                    SourceFetchState(
                                                        url,
                                                        if (url in skipped) {
                                                            PerSourceStatus.OMITIDA
                                                        } else {
                                                            PerSourceStatus.OK
                                                        },
                                                    )
                                                },
                                            ),
                                        )
                                    }
                                }
                                is MultiUrlResult.AllFailed -> {
                                    modelOnlyNotice = when (result.reason) {
                                        GroundingResult.Reason.OFFLINE -> ModelOnlyNotice.OFFLINE
                                        GroundingResult.Reason.FETCH_FAILED -> ModelOnlyNotice.FETCH_FAILED
                                    }
                                    modelOnlySourceCount = groundedUrls.size
                                }
                            }
                        } finally {
                            updateInput { it.copy(isFetchingWeb = false, webFetchProgress = null) }
                        }
                    } else {
                        // Quick-task (DDG-default): DDG-primary search
                        // branch. Runs ONLY when all hold — doGround (the
                        // once-per-send GroundingPrecedence.shouldGround read
                        // above, same precedence as fetch, never re-read
                        // mid-turn), validated internet (offline yields the
                        // existing OFFLINE model-only path with no socket
                        // opened), and — NEW — no key gate at all: the DDG
                        // repository searches keylessly, so no-key + DDG-OK
                        // is a silent success (no notice) and no-key +
                        // DDG-fail is the FETCH_FAILED notice (no key nag).
                        // Key present-but-bad (401 via the Tavily fallback
                        // leg) keeps the invalid-key message; MissingKey
                        // survives only as the key-race edge.
                        // Success fuses through the IDENTICAL downstream
                        // path as URL grounding: GroundingPrompt.augment of
                        // requestUserText, groundedSources/okUrls, the
                        // details-union persist below, per-source OK/OMITIDA
                        // progress snapshot, Fuentes/preview/citations
                        // untouched. Runs on Dispatchers.IO inside the
                        // repository (same as fetchAll) — never the UI
                        // thread (T-55-06/T-55-07).
                        val online = try {
                            fetcher.hasValidatedInternet()
                        } catch (e: Exception) {
                            Timber.w(e, "Chat: connectivity check failed, treating as offline")
                            false
                        }
                        // Phase 56 (56-02): when the local agentic loop is
                        // armed, the MODEL searches for itself — VM
                        // pre-search would burn a Tavily credit AND starve
                        // the loop (the model would never need web_search, so
                        // no status row would ever render and the 5-call
                        // wallet bound would stack on the pre-search credit).
                        // Skip the branch but keep the SYSTEM_PROMPT persona
                        // via the null-block augment. Unarmed turns and
                        // non-local providers keep the exact Phase-55 path.
                        //
                        // Phase 57 (57-02, CR-02 fixed by CR-01 wiring): the
                        // skip broadens to armed REMOTE loops — every matrix
                        // dialect that attempts tools (ATTEMPT/
                        // ATTEMPT_FALLBACK/NATIVE_ANTHROPIC) skips the VM
                        // pre-search so worst case stays 5 credits per
                        // message. The provider stays authoritative (it
                        // re-checks grounding, matrix, internet, plus its
                        // own collaborators); the VM mirrors for the
                        // pre-search skip only. The mirror is exact because
                        // CR-01 injects the same singletons at both
                        // production construction sites (ProviderRouter +
                        // LmStudioHelper.createProvider), so a VM-armed
                        // turn is provider-armed too — no silent loss of
                        // grounding on either path.
                        val localArmed = effectiveProvider == ProviderType.LITE_RT_LM &&
                            LocalToolLoop.isLoopArmed(
                                groundingOn = doGround,
                                supportsFunctionCalling = isFunctionCallingCapable(effectiveModelId),
                                hasValidatedInternet = online,
                            )
                        val remoteMode = ToolCapabilityMatrix.modeFor(effectiveProvider)
                        val remoteArmed = ToolCapabilityMatrix.isRemoteLoopArmed(
                            groundingOn = doGround,
                            matrixAttemptsTools = remoteMode == ToolMode.ATTEMPT ||
                                remoteMode == ToolMode.ATTEMPT_FALLBACK ||
                                remoteMode == ToolMode.NATIVE_ANTHROPIC,
                            hasValidatedInternet = online,
                        )
                        val loopArmed = localArmed || remoteArmed
                        if (loopArmed) {
                            requestUserText = GroundingPrompt.augment(
                                requestUserText,
                                null,
                                groundingEnabled = doGround,
                            )
                        } else if (!online) {
                            modelOnlyNotice = ModelOnlyNotice.OFFLINE
                            requestUserText = GroundingPrompt.augment(
                                requestUserText,
                                null,
                                groundingEnabled = doGround,
                            )
                        } else {
                            val searchCount = TavilySearchRepository.DEFAULT_MAX_RESULTS
                            updateInput {
                                it.copy(
                                    isFetchingWeb = true,
                                    webFetchProgress = WebFetchProgress(
                                        done = 0,
                                        total = searchCount,
                                        perSource = emptyList(),
                                    ),
                                )
                            }
                            try {
                                val contextSize = state.generationParameters.contextSize
                                when (
                                    val outcome = ddgSearchRepository.search(
                                        query = userMessage.content,
                                        maxResults = searchCount,
                                        contextSize = contextSize,
                                    )
                                ) {
                                    is TavilySearchOutcome.Grounded -> {
                                        val fused = outcome.fused
                                        requestUserText = GroundingPrompt.augment(
                                            requestUserText,
                                            fused.block,
                                            groundingEnabled = doGround,
                                        )
                                        groundedSources = fused.okUrls
                                        groundedSourceDetails = fused.details
                                        val total =
                                            fused.okUrls.size + fused.skippedUrls.size
                                        updateInput { s ->
                                            s.copy(
                                                webFetchProgress = s.webFetchProgress?.copy(
                                                    done = total,
                                                    perSource = fused.okUrls.map { url ->
                                                        SourceFetchState(url, PerSourceStatus.OK)
                                                    } + fused.skippedUrls.map { url ->
                                                        SourceFetchState(url, PerSourceStatus.OMITIDA)
                                                    },
                                                ),
                                            )
                                        }
                                    }
                                    is TavilySearchOutcome.ModelOnly -> {
                                        modelOnlyNotice = when (outcome.failed.reason) {
                                            GroundingResult.Reason.OFFLINE -> ModelOnlyNotice.OFFLINE
                                            GroundingResult.Reason.FETCH_FAILED -> ModelOnlyNotice.FETCH_FAILED
                                        }
                                        requestUserText = GroundingPrompt.augment(
                                            requestUserText,
                                            null,
                                            groundingEnabled = doGround,
                                        )
                                    }
                                    TavilySearchOutcome.MissingKey -> {
                                        modelOnlyNotice = ModelOnlyNotice.TAVILY_MISSING_KEY
                                        requestUserText = GroundingPrompt.augment(
                                            requestUserText,
                                            null,
                                            groundingEnabled = doGround,
                                        )
                                    }
                                    TavilySearchOutcome.InvalidKey -> {
                                        modelOnlyNotice = ModelOnlyNotice.TAVILY_INVALID_KEY
                                        requestUserText = GroundingPrompt.augment(
                                            requestUserText,
                                            null,
                                            groundingEnabled = doGround,
                                        )
                                    }
                                    TavilySearchOutcome.UsageLimit -> {
                                        modelOnlyNotice = ModelOnlyNotice.TAVILY_LIMIT
                                        requestUserText = GroundingPrompt.augment(
                                            requestUserText,
                                            null,
                                            groundingEnabled = doGround,
                                        )
                                    }
                                }
                            } finally {
                                updateInput { it.copy(isFetchingWeb = false, webFetchProgress = null) }
                            }
                        }
                    }
                }

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
                                    error = ChatError.Unknown(context.getString(R.string.error_no_vision)),
                                    isStreaming = false
                                )
                            }
                            updateInput { it.copy(isGenerating = false) }
                            return@launch
                        }
                        if (audioBytes != null && !capabilities.audio) {
                            updateTranscript {
                                it.copy(
                                    error = ChatError.Unknown(context.getString(R.string.error_no_audio)),
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

                // The LOCAL branch below is a mandatory exhaustive reference to
                // the deprecated legacy entry (persisted rows may still carry
                // it) — not new use.
                @Suppress("DEPRECATION")
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

                // Phase 50: the persisted history keeps original text; the
                // outgoing request's current message carries the augmented
                // text for BOTH local and remote paths — including the
                // always-on SYSTEM_PROMPT on grounded turns with no pasted
                // URLs (groundedSources is empty there, so the gate is
                // doGround, not source count).
                //
                // Quick-task (agentic-rows) HISTORY-SEMANTICS DECISION
                // (explicit — no silent choice): KEEP-AS-IS. Room keeps the
                // ORIGINAL user text (saved at send time) and only the
                // outgoing request's current message carries augmented text.
                // Trade-off: (a) history stays clean and remote replay stays
                // token-lean — no stale fused blocks are ever re-sent or
                // persisted; (b) the local native conversation DOES reuse
                // Message.tool results engine-side across turns (acquire),
                // so stale context can linger there, and stateless remote
                // replays see prior-turn citations without blocks; (c)
                // per-turn conversation resets would buy freshness at the
                // cost of multi-turn coherence + reload latency, and history
                // rewrites are forbidden. The re-search prompt rule
                // (TOOL_USE_SYSTEM_HINT + SYSTEM_PROMPT + web_search tool
                // description) is therefore the freshness mechanism: the
                // model must treat each new message independently and
                // re-search instead of answering from stale results.
                // On-device multi-turn confirmation still pending.
                val historyMessages = _transcript.value.messages
                val requestMessages = if (doGround && historyMessages.isNotEmpty()) {
                    historyMessages.dropLast(1) + historyMessages.last().copy(content = requestUserText)
                } else {
                    historyMessages
                }
                val request = ChatRequest(
                    messages = requestMessages,
                    parameters = _connection.value.generationParameters.copy(
                        reasoningEnabled = _input.value.reasoningEnabled
                    ),
                    images = imageDataUrls,
                    audioBytes = audioBytes,
                    // 56-02: per-chat override travels so the provider
                    // computes its own loop-arming (it has no
                    // conversationId to read the row itself).
                    webOverride = perChatOverride,
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
                        // Phase 56 (56-02): live tool-execution rows from the
                        // manual loops. Non-null ToolStatus sets the
                        // transient row (query/URL display, provider
                        // formatted); null clears it. ToolCompleted carries
                        // the call's persistable Fuentes rows
                        // (quick-task agentic-rows) — accumulated across
                        // the turn (union, first-seen order, distinct by
                        // URL) for the Done save below. Transient status
                        // handling is untouched. Rows NEVER become
                        // Role.TOOL transcript rows (Phase 49 DEL-01);
                        // Delta/parseThinkBlocks accumulation below stays
                        // untouched.
                        is StreamToken.ToolStatus -> updateInput { it.copy(toolCallActive = token.toolName) }
                        is StreamToken.ToolCompleted -> {
                            updateInput { it.copy(toolCallActive = null) }
                            for (source in token.sources) {
                                if (loopSourceDetails.none { it.url == source.url }) {
                                    loopSourceDetails += source
                                }
                            }
                        }
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
                                // Quick-task (agentic-rows): loop-turn rows
                                // merge with the pre-search details in the
                                // SAME shape (details union distinct by URL,
                                // first-seen order; ok-URL list mirrors the
                                // okUrls-only convention of the fetch/search
                                // branches). Armed turns carry loop rows
                                // only; pre-search turns carry pre-search
                                // rows only; zero-tool turns stay sourceless.
                                val loopOkUrls = loopSourceDetails
                                    .filter { it.status == GroundedSourceStatus.OK }
                                    .map { it.url }
                                val allSources = (groundedSources + loopOkUrls).distinct()
                                val allDetails = (groundedSourceDetails + loopSourceDetails)
                                    .distinctBy { it.url }
                                val assistantMessage = ChatMessage(
                                    role = Role.ASSISTANT,
                                    content = content,
                                    tokenCount = content.length / 4,
                                    reasoning = finalReasoning.ifEmpty { token.reasoning },
                                    stats = token.stats,
                                    groundedSources = allSources,
                                    groundedSourceDetails = allDetails,
                                    modelOnlyNotice = modelOnlyNotice,
                                    modelOnlySourceCount = modelOnlySourceCount,
                                )
                                updateTranscript {
                                    it.copy(
                                        messages = it.messages + assistantMessage,
                                        streamingContent = "",
                                        streamingReasoning = "",
                                        isStreaming = false
                                    )
                                }
                                // 56-02: turn Done clears the transient tool row.
                                updateInput { it.copy(isGenerating = false, toolCallActive = null) }
                                // Phase 53 (SRC-02/threat T-53-07): persist rows
                                // post-fetch, pre-inference-visibility. Failure
                                // is non-blocking — Timber plus the UI-SPEC
                                // Snackbar, chat continues, preview may degrade
                                // post-restart only. Quick-task
                                // (agentic-rows): the IDENTICAL path serves
                                // loop turns — same call, same
                                // replaceSources-safe one-shot save (the
                                // retry path keeps owning replaceSources;
                                // this turn never rewrites history), same
                                // Snackbar, same post-restart preview.
                                try {
                                    if (allDetails.isNotEmpty()) {
                                        chatRepository.saveMessageWithSources(
                                            conversationId,
                                            assistantMessage,
                                            allDetails,
                                        )
                                    } else {
                                        chatRepository.saveMessage(conversationId, assistantMessage)
                                    }
                                } catch (e: Exception) {
                                    Timber.e(e, "Chat: failed to persist assistant sources")
                                    _events.tryEmit(
                                        ChatEvent.Snackbar(
                                            context.getString(R.string.snack_sources_not_saved),
                                        ),
                                    )
                                }
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
                                updateInput { it.copy(isGenerating = false, toolCallActive = null) }
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
                            updateInput { it.copy(isGenerating = false, toolCallActive = null) }
                        }
                        // Phase 57 UI-review fix: the tools-unsupported
                        // retry notice routes on this typed token — never
                        // on exact-string match of render copy, so copy
                        // edits can't silently re-route the informational
                        // notice into the hard-error banner above. The
                        // turn continues to a plain retry + Done.
                        is StreamToken.ToolsUnsupported -> {
                            modelOnlyNotice = ModelOnlyNotice.TOOLS_UNSUPPORTED
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
                updateInput { it.copy(isGenerating = false, toolCallActive = null) }
            } finally {
                // 46-01: clear the serving helper on turn end — but only if no newer
                // turn has started since (stale-finally guard via turnId/seq).
                if (turnId == generationSeq.get()) activeHelper = null
                // Phase 54 (RETRY-01): the send may have crossed a
                // connectivity transition — refresh the Reintentar gate.
                refreshConnectivity()
            }
        }
    }

    /**
     * Phase 54 (RETRY-01): message-scoped foreground retry for
     * OFFLINE-grounded turns. Sources-only attach on success: refetch via the
     * same [MultiUrlFetcher.fetchAll] entry point, persist rows via
     * [ChatRepository.replaceSources] (never a re-save), clear the notice and
     * render Fuentes — assistant text byte-identical, no
     * [GroundingPrompt.augment], no inference call. AllFailed leaves the
     * transcript untouched (banner + retry intact). Guards (threats T-54-02/
     * T-54-04): OFFLINE-only eligibility re-checked on data (not UI
     * visibility); taps while a fetch is in flight are no-ops; validated
     * connectivity re-checked synchronously (stale flag → hide, no fetch).
     */
    fun retryGrounding(assistantMessageId: String) {
        // WR-03: never run a network fan-out concurrently with token
        // streaming — the send path is strictly sequential
        // (fetch-then-infer), so retry refuses while generating or
        // streaming. Mirrored in the banner gate (ModelOnlyBanner).
        if (_input.value.isFetchingWeb || _input.value.isGenerating) return
        if (_transcript.value.isStreaming) return
        // WR-02: synchronous overlap guard — checked on the caller
        // thread before launch. isFetchingWeb is set inside the
        // coroutine (async dispatch), so rapid double-taps both observed
        // false and raced; retryJob.isActive closes that window. A
        // completed job needs no cancel, so the pre-cancel is gone —
        // an active job no-ops above instead of racing startup.
        if (retryJob?.isActive == true) return
        if (!fetcher.hasValidatedInternet()) {
            refreshConnectivity()
            return
        }
        var myJob: Job? = null
        myJob = viewModelScope.launch(coroutineExceptionHandler) {
            val msgs = _transcript.value.messages
            val idx = msgs.indexOfFirst {
                it.id == assistantMessageId && it.modelOnlyNotice == ModelOnlyNotice.OFFLINE
            }
            // OFFLINE-only gate on data: stale taps on FETCH_FAILED turns
            // (or unknown ids) return without fetching.
            if (idx <= 0) return@launch
            val conversationId = _transcript.value.conversationId ?: return@launch
            // OFFLINE turns persist zero source rows, so re-derive the
            // turn's original URLs from the nearest preceding USER message
            // (persisted content survives restarts; ephemeral groundedUrls
            // are long gone).
            val userContent = msgs.take(idx).lastOrNull { it.role == Role.USER }?.content
                ?: return@launch
            val urls = UrlDetector.allUrls(userContent)
            if (urls.isEmpty()) return@launch
            updateInput {
                it.copy(
                    // WR-01: isGenerating surfaces the existing Stop button
                    // during retry (matching the Leyendo chip copy) and
                    // disables Send mid-retry; stopGeneration() semantics
                    // unchanged. Cleared in the owned finally below.
                    isGenerating = true,
                    isFetchingWeb = true,
                    webFetchProgress = WebFetchProgress(
                        done = 0,
                        total = urls.size,
                        perSource = urls.map { url ->
                            SourceFetchState(url, PerSourceStatus.LOADING)
                        },
                    ),
                )
            }
            try {
                val contextSize = _connection.value.generationParameters.contextSize
                when (
                    val result = multiUrlFetcher.fetchAll(
                        urls = urls,
                        contextSize = contextSize,
                        onProgress = { done, total ->
                            updateInput { s ->
                                s.copy(
                                    webFetchProgress = s.webFetchProgress?.copy(
                                        done = done,
                                        total = total,
                                    ),
                                )
                            }
                        },
                    )
                ) {
                    is MultiUrlResult.Fused -> {
                        val details = if (result.details.isNotEmpty()) {
                            result.details
                        } else {
                            buildSourceDetails(urls, result)
                        }
                        try {
                            chatRepository.replaceSources(
                                conversationId,
                                msgs[idx].createdAt,
                                details,
                            )
                        } catch (e: Exception) {
                            Timber.e(e, "Chat: retry failed to persist sources")
                        }
                        updateTranscript { s ->
                            s.copy(
                                messages = s.messages.mapIndexed { i, m ->
                                    if (i == idx) {
                                        m.copy(
                                            modelOnlyNotice = null,
                                            groundedSources = result.okUrls,
                                            groundedSourceDetails = details,
                                        )
                                    } else {
                                        m
                                    }
                                },
                            )
                        }
                    }
                    is MultiUrlResult.AllFailed -> Unit
                }
            } finally {
                // WR-02: stale-finally guard — only the owning job clears
                // state and refreshes. A cancelled job (Stop / new send,
                // which null retryJob first) must not clobber flags a newer
                // turn or retry set after it.
                if (retryJob === myJob) {
                    retryJob = null
                    updateInput { it.copy(isGenerating = false, isFetchingWeb = false, webFetchProgress = null) }
                    refreshConnectivity()
                }
            }
        }
        retryJob = myJob
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
        // Phase 50: cancel the in-flight grounding fetch BEFORE the helper's
        // transport stop (same snapshot-then-null ordering).
        try { fetcher.cancel() } catch (e: Exception) { Timber.e(e, "Chat: fetch cancel failed") }
        val helper = activeHelper
        activeHelper = null
        try {
            helper?.stopResponse()
        } catch (e: Exception) {
            Timber.e(e, "Chat: stopResponse failed")
        }
        generationJob?.cancel()
        generationJob = null
        // Phase 54 (RETRY-01): Stop during retry returns to the queued
        // state — cancel the retry scope; the transcript is untouched so the
        // OFFLINE banner + Reintentar survive with zero extra work.
        retryJob?.cancel()
        retryJob = null
        updateTranscript { it.copy(
            isStreaming = false,
            streamingContent = "",
            streamingReasoning = ""
        ) }
        // 56-02: Stop clears the transient tool row (single-cancel-path:
        // generationJob cancel + fetcher.cancel() above already reach the
        // provider loop and in-flight sockets; this only drops the row).
        updateInput { it.copy(isGenerating = false, isFetchingWeb = false, webFetchProgress = null, toolCallActive = null) }
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
                // Phase 53 (TOGGLE-01): per-conversation override read once per
                // open (never hot-observed). Missing rows / DB failure fall
                // back to inherit (null) without breaking the open.
                val webOverride = try {
                    chatRepository.getWebOverride(conversation.id)
                } catch (e: Exception) {
                    Timber.w(e, "Chat: override read failed on open, using inherit")
                    null
                }
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
                        modelUnavailable = modelMissing,
                        webOverride = webOverride,
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
        pendingWebOverride = null
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
                modelUnavailable = false,
                webOverride = null,
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
     * Phase 56 (56-02): VM-side loop-arming capability read. Verified-only:
     * unlisted models default CLOSED. Reads the allowlist (same source as
     * the provider gate) — LocalModel.capabilities is a hardcoded all-true
     * lazy and is NEVER consulted here. Never throws: a failed read arms
     * nothing (plain Phase-55 turn).
     */
    private fun isFunctionCallingCapable(modelId: String?): Boolean {
        if (modelId == null) return false
        return try {
            modelAllowlistRepository.findByModelFile(modelId.substringAfterLast("/"))
                ?.capabilities?.supportsFunctionCalling == true
        } catch (e: Exception) {
            Timber.w(e, "Chat: allowlist read failed, treating as incapable")
            false
        }
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
                    modelLoadError = context.getString(R.string.error_no_memory_fmt, modelMB, availMB)
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

    /**
     * Phase 53 (SRC-02): union of all N fetched sources in fetch-block order
     * from the terminal per-source snapshot. OK rows carry their extracted
     * text; skipped rows carry null text with OMITIDA status. 6th+ ignored
     * URLs never entered progress state and stay excluded.
     */
    private fun buildSourceDetails(
        blockOrder: List<String>,
        fused: MultiUrlResult.Fused,
    ): List<GroundedSource> =
        blockOrder.distinct().take(MultiUrlFetcher.MAX_URLS).map { url ->
            val text = fused.pageTexts[url]
            if (text != null) {
                GroundedSource(url = url, extractedText = text, status = GroundedSourceStatus.OK)
            } else {
                GroundedSource(url = url, extractedText = null, status = GroundedSourceStatus.OMITIDA)
            }
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
        // Phase 53 (TOGGLE-01): apply a pre-first-send override held as
        // pending, then clear it — subsequent reads hit the row.
        pendingWebOverride?.let { pending ->
            try {
                chatRepository.setWebOverride(conversationId, pending)
                updateConnection { it.copy(webOverride = pending) }
            } catch (e: Exception) {
                Timber.e(e, "Chat: applying pending web override failed")
            }
            pendingWebOverride = null
        }
        activeModelSelection.saveLastConversation(conversationId)
        return conversationId
    }

    private suspend fun isModelAvailable(modelId: String, providerType: ProviderType): Boolean {
        // The LOCAL branch below is a mandatory exhaustive reference to the
        // deprecated legacy entry (persisted rows may still carry it) — not new use.
        @Suppress("DEPRECATION")
        val available = when (providerType) {
            ProviderType.LOCAL, ProviderType.LITE_RT_LM -> {
                localModelRepository.existsByFilePath(modelId)
            }
            else -> {
                _connection.value.endpoints.any { it.modelId == modelId && it.apiType == providerType }
            }
        }
        return available
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
