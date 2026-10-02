package com.warped.ui.chat

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warped.data.grounding.DuckDuckGoSearchRepository
import com.warped.data.grounding.GroundingPrecedence
import com.warped.data.grounding.GroundingPrompt
import com.warped.data.grounding.GroundingResult
import com.warped.data.grounding.ImageIntent
import com.warped.data.grounding.MultiUrlFetcher
import com.warped.data.grounding.CodeIntent
import com.warped.data.grounding.AnaphoraAnchor
import com.warped.data.grounding.NeedsWeb
import com.warped.data.grounding.MultiUrlResult
import com.warped.data.grounding.SearchOutcome
import com.warped.data.grounding.UrlDetector
import com.warped.data.grounding.WebPageFetcher
import com.warped.data.local.inference.BackendType
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.remote.provider.ProviderRouter
import com.warped.data.repository.ModelAllowlistRepository
import com.warped.domain.llm.LlmModelHelper
import com.warped.domain.review.ReviewHelper
import com.warped.domain.model.*
import com.warped.domain.repository.ChatRepository
import com.warped.domain.repository.EndpointRepository
import com.warped.domain.repository.LocalModelRepository
import com.warped.ui.chat.voice.VoiceDictationManager
import com.warped.ui.chat.voice.VoiceMessageRecorder
import com.warped.ui.chat.voice.PcmTranscoder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val reviewHelper: ReviewHelper,
    private val fetcher: WebPageFetcher,
    private val multiUrlFetcher: MultiUrlFetcher,
    /**
     * Quick-task (DDG-default): the search branch goes through the
     * DDG-only repository (keyless outcome, same signatures in/out).
     */
    private val ddgSearchRepository: DuckDuckGoSearchRepository,
    /**
     * Quick-task (always-search): the VM no longer mirrors loop-arming —
     * the provider owns arming (computeArmSnapshot /
     * ConversationConfig.tools) and the DDG-primary pre-search runs on
     * every grounded turn including armed ones. Retained in the graph so
     * Hilt construction sites stay untouched.
     */
    @Suppress("unused")
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
     * Phase 65 (VOICE-03): speech-recognition availability, resolved once at
     * init on Dispatchers.IO via the manager's isAvailable so the input bar
     * never pays the PackageManager cost on the main thread. The mic
     * affordance renders only while true (graceful no-recognizer fallback).
     */
    private val _speechAvailable = MutableStateFlow(false)
    val speechAvailable: StateFlow<Boolean> = _speechAvailable.asStateFlow()

    /**
     * Phase 65 (VOICE-01): true while the platform recognizer is listening.
     * Drives the mic/stop toggle plus the listening indicator (plan 65-02).
     */
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    /**
     * Phase 65 (VOICE-03): lazily created platform recognizer wrapper.
     * Created on first startDictation (and once at init for the
     * availability probe, then reused). Destroyed in [onCleared] so no
     * recognizer leaks past the screen or ViewModel.
     */
    private var dictationManager: VoiceDictationManager? = null

    /**
     * Phase 65 fix (CR-01): single-insertion dictation tracking. Platform
     * partials are cumulative hypotheses ("hel" → "hello" → "hello world"),
     * not deltas, so each [onDictationPartial] REPLACES the previous
     * hypothesis in place instead of appending. [lastPartial] is the text
     * currently standing in the draft for this utterance; [partialAnchor]
     * is the draft offset where it starts. The final result replaces the
     * standing hypothesis once — one utterance yields exactly one insertion.
     */
    private var lastPartial: String = ""
    private var partialAnchor: Int? = null

    /**
     * Phase 65 fix (WR-03): last cursor position reported by the input bar.
     * -1 means unknown (fall back to end-of-text). Recorded on every
     * selection change so dictation inserts where the user is editing,
     * not always at the end.
     */
    private var lastKnownCursor: Int = -1

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

    /**
     * Phase 66 (RATE-01): Activity handle for the ambient Play review
     * flow. Set by ChatScreen from LocalContext (the ViewModel holds only
     * `@ApplicationContext Context`); cleared when the screen leaves, so
     * no Activity is ever retained. The turn-Done hook resolves it through
     * this provider at fire time — a null provider simply skips the
     * prompt (previews, tests).
     *
     * IN-03: privately settable — ChatScreen assigns via
     * [setReviewActivityProvider] so no arbitrary caller can overwrite
     * or leak a capturing lambda.
     */
    @Volatile
    var reviewActivityProvider: (() -> Activity?)? = null
        private set

    fun setReviewActivityProvider(provider: (() -> Activity?)?) {
        reviewActivityProvider = provider
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
                // Auto-select: a fresh download/import leaves no selection,
                // which disables the chat input (NoModelSelected) until the
                // user finds Models & Endpoints. Mark the newest model
                // pending so chat is immediately usable — the engine mounts
                // on the first send, never on selection.
                // The toggle this replaces is gone: unloading happens
                // implicitly on switch/delete, never by hand.
                val selectedId = _connection.value.selectedLocalModelId
                    ?: activeModelSelection.localSelection.value.modelId
                pickAutoSelectModel(models, selectedId)
                    ?.takeIf { it != autoSelectedModelPath }
                    ?.let { autoPick ->
                        autoSelectedModelPath = autoPick
                        // Lazy load: mark pending only — the engine mounts
                        // on the first send. Never preload on selection.
                        activeModelSelection.selectLocalPending(autoPick)
                    }
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
                // Recompute capability-driven input flags: the selection
                // collectors fail open while this list is empty, which would
                // otherwise stick (e.g. Thinking visible on a text-only
                // model selected before the repo emitted).
                updateInput {
                    it.copy(
                        supportsThinking = supportsThinkingFor(
                            _connection.value.selectedLocalModelId,
                            _connection.value.selectedRemoteModelId
                        )
                    )
                }
            }
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            activeModelSelection.localSelection.collect { local ->
                val modelId = local.modelId
                val connected = local.isConnected
                // Lazy load: the spinner is the explicit load signal
                // (LocalSelection.isLoading), never the
                // selected-but-unconnected derivation — a pending
                // selection (marked, engine not yet mounted) shows
                // selected-not-loaded with the input enabled.
                val loading = local.isLoading
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
                        // First-time copy (user decision 2026-10-02): true
                        // only when this path never completed a load in this
                        // process and the engine isn't already serving it.
                        loadingFirstTime = loading && modelId != null &&
                            modelId !in everLoadedPaths &&
                            engineManager.getActiveEngine()?.modelPath != modelId,
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
        // Phase 65 (VOICE-03): resolve recognizer availability off the main
        // thread; the input bar reads the cached speechAvailable flow.
        viewModelScope.launch(Dispatchers.IO) {
            _speechAvailable.value = getDictationManager().isAvailable()
        }
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
     * Phase 65 fix (WR-04): re-probe recognizer availability. The init probe
     * runs once; if the service is installed/enabled/updated afterwards (or
     * the first probe raced a slow package load) the mic would never appear
     * for the process lifetime. Called on ON_RESUME next to
     * [refreshConnectivity] — cheap, off the main thread, never on the
     * composition hot path.
     */
    fun refreshSpeechAvailability() {
        viewModelScope.launch(Dispatchers.IO) {
            _speechAvailable.value = getDictationManager().isAvailable()
        }
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
        // CR-03: sending while listening tears the recognizer down first.
        // Otherwise it keeps listening with no on-screen stop affordance
        // (the mic hides while generating) and its late results pollute
        // the fresh empty draft. Defense in depth next to the
        // screen-level stop in ChatScreen onSend; committed partial text
        // stays in the draft and is sent normally.
        stopDictation()
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
                // Lazy model load (quick-task lazy-model-load): selection
                // only marks pending — the engine mounts here on the first
                // send, then the turn generates. Remote path untouched.
                if (effectiveProvider == ProviderType.LITE_RT_LM) {
                    val modelFile = java.io.File(effectiveModelId)
                    if (!modelFile.exists()) {
                        updateTranscript {
                            it.copy(
                                messages = it.messages.filterNot { m -> m.id == userMessage.id },
                                error = ChatError.DownloadModelFirst,
                                isStreaming = false
                            )
                        }
                        updateInput { it.copy(isGenerating = false, inputText = text) }
                        return@launch
                    }
                    if (engineManager.getActiveEngine()?.modelPath != effectiveModelId) {
                        activeModelSelection.markLocalLoading(effectiveModelId)
                        preloadLocalModel(effectiveModelId)
                    }
                    if (engineManager.getActiveEngine()?.modelPath != effectiveModelId) {
                        val loadError = _connection.value.modelLoadError
                            ?: context.getString(R.string.error_unknown_short)
                        updateTranscript {
                            it.copy(
                                messages = it.messages.filterNot { m -> m.id == userMessage.id },
                                error = ChatError.Unknown(loadError),
                                isStreaming = false
                            )
                        }
                        updateInput { it.copy(isGenerating = false, inputText = text) }
                        return@launch
                    }
                }
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
                // Quick-task (image-grid): ephemeral render list for the
                // image grid. Set on image-intent Grounded turns only;
                // never persisted (same ephemeral contract as the rows).
                var groundedImages: List<String> = emptyList()
                // Quick-task (agentic-rows): per-turn tool-source
                // accumulator. Loop drivers (local `runToolLoop`,
                // `CompatToolLoop`, OpenAI/Anthropic inline loops) emit one
                // `ToolCompleted` per executed search/fetch call carrying
                // the call's structured rows; they union here (first-seen
                // order, distinct by URL — same union semantics as the
                // fetch/search branches) and merge with the pre-search
                // details on Done below. The always-on VM pre-search runs
                // on every grounded turn INCLUDING armed ones (floor); loop
                // ToolCompleted rows union with the pre-search details on
                // Done, so armed turns normally carry both sources.
                val loopSourceDetails = mutableListOf<GroundedSource>()
                // Quick-task (loop-images): per-turn loop-image
                // accumulator. Loop drivers emit one `ToolCompleted` per
                // executed search call carrying the call's fused search
                // `images[]`; they union here (first-seen order, distinct)
                // and merge with the pre-search `groundedImages` on Done
                // below. The always-on VM pre-search runs on every grounded
                // turn INCLUDING armed ones, so armed turns normally carry
                // both pre-search and loop image sources.
                val loopImages = mutableListOf<String>()
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
                    // Quick-task (attachments-skip-search): the no-URL
                    // branch below runs ONLY on attachment-free turns.
                    // Attachment turns (images/audio) skip the heuristic
                    // no-URL pre-search entirely — v2.4 Phase 55
                    // regression: blind text search on image/audio-question
                    // turns injects junk context about the question words
                    // while the question is about the attachment, and the
                    // small model answers from the injected text ignoring
                    // the attachment. Skipped turns keep requestUserText as
                    // the original text with grounded*/notice empty/null
                    // (no augment, no banner — the skip is deliberate, not
                    // a failure). The URL branch above still fetches with
                    // attachments; loop arming, the capability media gate,
                    // and the provider attach path are untouched (the model
                    // may still tool-search with full multimodal context).
                    } else if (images.isEmpty() && audioBytes == null) {
                        // Quick-task (code-intent-gate): social/identity OR
                        // code-generation turns skip the pre-search entirely
                        // — no socket, no credit, no notice, no progress
                        // state. Identical to grounding-off for this turn.
                        // This gate touches the heuristic pre-search ONLY:
                        // the armed loop REMAINS the model's escape hatch —
                        // it can still web_search mid-turn for versioned or
                        // fresh API facts the weights don't cover.
                        if (!NeedsWeb.needsWeb(userMessage.content) ||
                            CodeIntent.isCodeTurn(userMessage.content)
                        ) {
                            Timber.d("Chat: social/code turn — skipping pre-search")
                        } else {
                        // Quick-task (DDG-default): DDG-primary search
                        // branch. Runs ONLY when all hold — doGround (the
                        // once-per-send GroundingPrecedence.shouldGround read
                        // above, same precedence as fetch, never re-read
                        // mid-turn), validated internet (offline yields the
                        // existing OFFLINE model-only path with no socket
                        // opened), and — NEW — no key gate at all: the DDG
                        // repository searches keylessly, so a DDG-OK turn
                        // is a silent success (no notice) and a DDG-fail
                        // turn is the FETCH_FAILED notice.
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
                        // Always-on pre-search (quick-task always-search): the
                        // DDG-primary branch runs on EVERY grounded no-URL
                        // turn INCLUDING armed ones. DDG is free/keyless so
                        // this adds 0 credits when DDG serves the turn; the
                        // loop stays armed provider-side (computeArmSnapshot
                        // / ConversationConfig.tools untouched) for deeper
                        // model-driven fetch, and loop ToolCompleted rows
                        // keep merging with pre-search details in the Done
                        // union below. There is deliberately NO VM-level
                        // direct keyed call and NO loop-cap change — the
                        // DDG-only `DuckDuckGoSearchRepository.search`
                        // serves every turn keylessly; image-intent turns
                        // fuse zero images (DDG has no image API).
                        if (!online) {
                            modelOnlyNotice = ModelOnlyNotice.OFFLINE
                            requestUserText = GroundingPrompt.augment(
                                requestUserText,
                                null,
                                groundingEnabled = doGround,
                            )
                        } else {
                            // Quick-task (image-grid): intent-gated
                            // include_images — the DDG leg has no image API
                            // and fuses zero images. Non-intent turns pass
                            // false: byte-identical to today, no extra
                            // payload.
                            val wantImages = ImageIntent.hasImageIntent(userMessage.content)
                            val searchCount = DuckDuckGoSearchRepository.DEFAULT_MAX_RESULTS
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
                                // Quick-task (always-presearch-anchored):
                                // anaphoric follow-ups ("Quien es su
                                // hermanastro?") search the prior turn's
                                // topic words, not the bare message — anchor
                                // = most recent prior USER turn (else last
                                // assistant), capped + deduped inside
                                // AnaphoraAnchor. No-history turns return the
                                // raw message (today's behavior,
                                // byte-identical). Query-text only: DDG stays
                                // free/keyless (latency, not credits),
                                // loop cap untouched.
                                val priorMessages = _transcript.value.messages.dropLast(1)
                                val anchoredQuery = AnaphoraAnchor.buildQuery(
                                    userMessage.content,
                                    priorMessages.filter { it.role == Role.USER }.map { it.content },
                                    priorMessages.lastOrNull { it.role == Role.ASSISTANT }?.content,
                                )
                                when (
                                    val outcome = ddgSearchRepository.search(
                                        query = anchoredQuery,
                                        maxResults = searchCount,
                                        contextSize = contextSize,
                                        includeImages = wantImages,
                                    )
                                ) {
                                    is SearchOutcome.Grounded -> {
                                        val fused = outcome.fused
                                        requestUserText = GroundingPrompt.augment(
                                            requestUserText,
                                            fused.block,
                                            groundingEnabled = doGround,
                                        )
                                        groundedSources = fused.okUrls
                                        groundedSourceDetails = fused.details
                                        groundedImages = fused.images
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
                                    is SearchOutcome.ModelOnly -> {
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
                                }
                                // Image-intent turns fuse zero images (DDG
                                // has no image API) with text grounding
                                // preserved — no notice attached.
                            } finally {
                                updateInput { it.copy(isFetchingWeb = false, webFetchProgress = null) }
                            }
                        }
                        } // needs-web gate: social turns skip the block above
                    }
                }

                val selectedProvider = effectiveProvider
                val modelId = effectiveModelId

                // Validate model capabilities (allowlist-verified — never the
                // raw LocalModel.capabilities all-true default).
                if (selectedProvider == ProviderType.LITE_RT_LM) {
                    val capabilities = verifiedLocalCapabilities(modelId)
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

                // (Lazy load above already mounted the engine when the
                // selection was pending, and rejected missing files before
                // persisting.)

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
                // Loading-flag heal, selected-but-unconnected ONLY: the
                // provider lazy-loads the engine without touching
                // selection, which would leave the traffic light spinning
                // ("Loading…") forever on an auto-selected model. Reconcile
                // engine truth into selection. Skipped when already
                // connected (the historically tested steady state) or
                // remote — refreshActiveBackend touches the engine manager,
                // which strict test doubles don't stub.
                if (selectedProvider == ProviderType.LITE_RT_LM &&
                    activeModelSelection.localSelection.value.let { it.modelId != null && !it.isConnected }
                ) {
                    refreshActiveBackend()
                }

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
                // Quick-task (live-thinking): native thought deltas stream
                // here DURING generation (Thinking), not just at Done.
                // liveThought is the streamed authoritative mirror of the
                // provider's thought accumulator (same deltas, same
                // no-separator join — no parsing needed, so deltas append
                // directly and only the UI emit is throttled). tagReasoning
                // carries the last <think>-tag parse so both branches
                // resolve the live panel with Done-branch precedence
                // (tags win, native thought fills when tags are absent).
                val liveThought = StringBuilder()
                var tagReasoning = ""
                var lastThoughtEmitTime = System.currentTimeMillis()

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
                            // Quick-task (tool-failure-note): a failed tool
                            // call surfaces one transient, auto-clearing,
                            // non-persisted note via the existing Snackbar
                            // precedent — cleared implicitly (one-shot
                            // event, never written to the transcript).
                            token.errorReason?.let { reason ->
                                _events.tryEmit(ChatEvent.Snackbar(reason))
                            }
                            for (source in token.sources) {
                                if (loopSourceDetails.none { it.url == source.url }) {
                                    loopSourceDetails += source
                                }
                            }
                            // Quick-task (loop-images): union the call's
                            // fused images (first-seen order, distinct).
                            for (image in token.images) {
                                if (image !in loopImages) {
                                    loopImages += image
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
                                // Quick-task (live-thinking): a text flush
                                // must not blank an in-flight native thought
                                // panel — fall back to the streamed thought
                                // when the text carries no <think> tags
                                // (identical to Done-branch precedence).
                                tagReasoning = reasoning
                                updateTranscript {
                                    it.copy(
                                        streamingContent = cleanContent,
                                        streamingReasoning = tagReasoning.ifEmpty { liveThought.toString() },
                                    )
                                }
                                tokenBuffer.clear()
                                lastEmitTime = now
                            }
                        }
                        // Quick-task (live-thinking): native thought deltas
                        // update the Thinking panel live, throttled like
                        // content (50ms). Done stays the final — the
                        // streamed value reconciles to Done.reasoning.
                        is StreamToken.Thinking -> {
                            if (token.delta.isNotEmpty()) {
                                liveThought.append(token.delta)
                                val now = System.currentTimeMillis()
                                if (now - lastThoughtEmitTime >= 50) {
                                    lastThoughtEmitTime = now
                                    updateTranscript {
                                        it.copy(
                                            streamingReasoning = tagReasoning.ifEmpty { liveThought.toString() },
                                        )
                                    }
                                }
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
                                // Quick-task (loop-images): loop-turn images
                                // merge with the pre-search list (pre-search
                                // keeps precedence, first-seen order).
                                // Ephemeral — never persisted (toEntity
                                // untouched, no migration).
                                val allImages = (groundedImages + loopImages).distinct()
                                val assistantMessage = ChatMessage(
                                    role = Role.ASSISTANT,
                                    content = content,
                                    tokenCount = content.length / 4,
                                    reasoning = finalReasoning.ifEmpty { token.reasoning },
                                    stats = token.stats,
                                    groundedSources = allSources,
                                    groundedSourceDetails = allDetails,
                                    groundedImages = allImages,
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
                                var turnPersisted = false
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
                                    turnPersisted = true
                                } catch (e: Exception) {
                                    Timber.e(e, "Chat: failed to persist assistant sources")
                                    _events.tryEmit(
                                        ChatEvent.Snackbar(
                                            context.getString(R.string.snack_sources_not_saved),
                                        ),
                                    )
                                }
                                // Phase 66 (RATE-01): ambient review —
                                // fire-and-forget child launch off the turn
                                // path (never a suspend on this path); a
                                // null Activity handle skips silently
                                // (previews/tests). WR-05: only persisted
                                // user-visible turns advance the counter —
                                // a turn whose save failed was never
                                // persisted, so it must not count. (Loop
                                // drivers emit ToolCompleted into this same
                                // turn; exactly one Done runs per user
                                // turn, so no loop-turn flag is needed.)
                                if (turnPersisted) {
                                    viewModelScope.launch(coroutineExceptionHandler) {
                                        try {
                                            val activity = reviewActivityProvider?.invoke()
                                                ?: return@launch
                                            reviewHelper.maybePrompt(activity)
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (_: Exception) {
                                            // WR-01/IN-02: already logged
                                            // inside ReviewHelper — stay
                                            // silent, never swallow
                                            // cancellation.
                                        }
                                    }
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
                        // Lazy load: mark pending only — the engine mounts
                        // on the first send. Never preload on open.
                        activeModelSelection.selectLocalPending(conversation.modelId)
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
                    // Lazy load: no preload on open — the traffic light
                    // shows selected-not-loaded until the first send
                    // mounts the engine.
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

    /**
     * Phase 65 fix (WR-03): cursor position reported by the input bar's
     * TextFieldValue selection. Dictation inserts here; -1 (unknown)
     * falls back to end-of-text.
     */
    fun updateInputCursor(position: Int) {
        lastKnownCursor = position
    }

    /**
     * Phase 65 fix (CR-01): provisional partial handler. Platform partials
     * are cumulative hypotheses ("hel" → "hello" → "hello world"), not
     * deltas — each call REPLACES the previous hypothesis ([lastPartial]
     * at [partialAnchor]) instead of appending, so one utterance yields
     * exactly one insertion. A new utterance (or the user editing under
     * us, breaking the anchor match) falls back to a fresh cursor insert.
     *
     * Late callbacks arriving after the session ended (listening cleared
     * by send/stop/error) are dropped so an orphaned recognizer can never
     * pollute the next draft (CR-03).
     */
    internal fun onDictationPartial(hypothesis: String) {
        if (!_isListening.value) return
        val trimmed = hypothesis.trim()
        if (trimmed.isEmpty()) return
        updateInput { state ->
            val current = state.inputText
            val anchor = partialAnchor
            if (anchor != null && lastPartial.isNotEmpty() &&
                anchor <= current.length &&
                current.regionMatches(anchor, lastPartial, 0, lastPartial.length)
            ) {
                // Same utterance, hypothesis revised: swap in place.
                val oldLength = lastPartial.length
                lastPartial = trimmed
                state.copy(
                    inputText = current.substring(0, anchor) + trimmed +
                        current.substring(anchor + oldLength),
                )
            } else {
                // New utterance (or the draft moved under us): fresh insert
                // at the last known cursor, remembering where it starts.
                val (inserted, start) = insertAtCursor(current, trimmed, lastKnownCursor)
                lastPartial = trimmed
                partialAnchor = start
                lastKnownCursor = start + trimmed.length
                state.copy(inputText = inserted)
            }
        }
    }

    /**
     * Auto-stop final handler (user decision 2026-10-02, reverting the
     * continuous session): the platform recognizer is single-shot — it
     * ends the utterance on silence/timeout and fires onResults — so the
     * final commits the text and the session STOPS. Listening ends on
     * platform end or user tap. Blank finals still end the session
     * (flag cleared, draft untouched).
     */
    internal fun onDictationFinal(text: String) {
        if (!_isListening.value) return
        _isListening.value = false
        val trimmed = text.trim()
        val anchor = partialAnchor
        val standing = lastPartial
        lastPartial = ""
        partialAnchor = null
        if (trimmed.isEmpty()) return
        updateInput { state ->
            val current = state.inputText
            if (anchor != null && standing.isNotEmpty() &&
                anchor <= current.length &&
                current.regionMatches(anchor, standing, 0, standing.length)
            ) {
                lastKnownCursor = anchor + trimmed.length
                state.copy(
                    inputText = current.substring(0, anchor) + trimmed +
                        current.substring(anchor + standing.length),
                )
            } else {
                val (inserted, start) = insertAtCursor(current, trimmed, lastKnownCursor)
                lastKnownCursor = start + trimmed.length
                state.copy(inputText = inserted)
            }
        }
    }

    /**
     * Auto-stop error policy (user decision 2026-10-02): any recognition
     * error (no speech, timeout, permissions, busy, server, language)
     * ends the session — a pause is a stop. Clears listening state plus
     * the partial tracking; the draft keeps whatever partial text
     * arrived. No event emission, no error copy.
     */
    internal fun onDictationError(error: Int) {
        if (!_isListening.value) return
        Timber.w("Voice: recognition error $error, keeping partial draft")
        lastPartial = ""
        partialAnchor = null
        _isListening.value = false
    }

    /**
     * Insert [insertion] into [current] at [cursor] with single-space
     * separation. Returns the new text plus the offset where the inserted
     * text starts (the partial-replacement anchor). Negative cursor means
     * unknown → end-of-text.
     */
    private fun insertAtCursor(current: String, insertion: String, cursor: Int): Pair<String, Int> {
        val clamped = if (cursor < 0) current.length else cursor.coerceIn(0, current.length)
        val before = current.substring(0, clamped)
        val after = current.substring(clamped)
        val sepBefore = if (before.isEmpty() || before.endsWith(" ") || before.endsWith("\n")) "" else " "
        val sepAfter = if (after.isEmpty() || after.startsWith(" ") || after.startsWith("\n")) "" else " "
        val start = before.length + sepBefore.length
        return (before + sepBefore + insertion + sepAfter + after) to start
    }

    /** Test seam: a MockK fake replaces the platform manager in unit tests. */
    internal var dictationManagerOverride: VoiceDictationManager? = null

    private fun getDictationManager(): VoiceDictationManager {
        dictationManagerOverride?.let { return it }
        return dictationManager ?: VoiceDictationManager(
            context = context,
            onPartial = { onDictationPartial(it) },
            onFinal = { onDictationFinal(it) },
            // Silent-error policy (UI-SPEC section 3): recognition failures
            // (network, no-speech, timeout) only clear listening state. No
            // event emission, no error copy — the draft keeps whatever
            // partial text arrived.
            onError = { onDictationError(it) },
        ).also { dictationManager = it }
    }

    /**
     * Phase 65 (VOICE-01): start platform dictation. Creates the manager
     * lazily and streams partial/final results into the single-insertion
     * handlers. Callers own the RECORD_AUDIO runtime-permission gate
     * (plan 65-02).
     *
     * WR-01: the listening flag flips ONLY when the platform accepted the
     * start ([VoiceDictationManager.start] returns false on synchronous
     * failure after firing onError). Unconditional set-true here used to
     * overwrite the error path's clear, stranding the UI on a dead
     * recognizer.
     */
    fun startDictation() {
        // Phase 67 (VMSG-01): single live input mode — starting dictation
        // stops an active voice recording (keeps the clip for send).
        if (_isVoiceRecording.value) stopVoiceRecording()
        lastPartial = ""
        partialAnchor = null
        _isListening.value = getDictationManager().start()
    }

    /**
     * Phase 65 (VOICE-01): stop platform dictation and clear listening.
     * Partial tracking resets (the committed hypothesis text stays in the
     * draft); any late platform callback after this is dropped by the
     * listening guard in [onDictationPartial]/[onDictationFinal]/
     * [onDictationError]. The flag clears FIRST so a platform flush
     * triggered by stop() itself can never re-arm the session.
     */
    fun stopDictation() {
        _isListening.value = false
        lastPartial = ""
        partialAnchor = null
        try {
            (dictationManagerOverride ?: dictationManager)?.stop()
        } catch (e: Exception) {
            Timber.w(e, "Voice: stopDictation failed")
        }
    }

    /**
     * Phase 65 (VOICE-02): emit the permanent-denial Snackbar event carrying
     * the voice_denied copy plus the voice_open_settings action label. Call
     * ONLY on permanent denial (shouldShowRequestPermissionRationale ==
     * false after denial, resolved by the screen in plan 65-02) — transient
     * denial stays silent.
     */
    fun emitMicDenied() {
        _events.tryEmit(
            ChatEvent.SnackbarWithAction(
                message = context.getString(R.string.voice_denied),
                actionLabel = context.getString(R.string.voice_open_settings),
                action = SnackbarAction.OPEN_APP_SETTINGS,
            ),
        )
    }

    /**
     * Phase 67 (VMSG-01): permanent-denial escape for voice-send. Mirrors
     * [emitMicDenied] with the voice-message copy. Call ONLY on permanent
     * denial — transient denial uses [emitVoiceDeniedTransient].
     */
    fun emitVoiceDenied() {
        _events.tryEmit(
            ChatEvent.SnackbarWithAction(
                message = context.getString(R.string.voice_msg_denied),
                actionLabel = context.getString(R.string.voice_open_settings),
                action = SnackbarAction.OPEN_APP_SETTINGS,
            ),
        )
    }

    /**
     * Phase 67 (VMSG-01): transient-denial notice for voice-send. Plain
     * non-blocking Snackbar (no Settings action); recording never starts.
     */
    fun emitVoiceDeniedTransient() {
        _events.tryEmit(
            ChatEvent.Snackbar(context.getString(R.string.voice_msg_denied)),
        )
    }

    /**
     * Phase 67 (VMSG-01/05): voice-message capture state. The recorder is
     * VM-owned (sibling to the dictation manager): created lazily with
     * output dir filesDir/voice, destroyed in [onCleared] so no recorder
     * leaks past the screen. Rotation survives by construction (flows).
     *
     * Session lifecycle: [startVoiceRecording] (Dispatchers.IO) flips
     * [isVoiceRecording] ONLY on platform accept (WR-01 discipline) and
     * launches a 1 s ticker (elapsed + 60 s auto-stop-and-keep) plus a
     * ~100 ms amplitude sampler as children of [voiceSessionJob]; every
     * exit path (stop/cancel/auto-stop/destroy) cancels the job.
     */
    private val _isVoiceRecording = MutableStateFlow(false)
    val isVoiceRecording: StateFlow<Boolean> = _isVoiceRecording.asStateFlow()

    private val _voiceElapsedSec = MutableStateFlow(0)
    val voiceElapsedSec: StateFlow<Int> = _voiceElapsedSec.asStateFlow()

    private val _voiceAmplitude = MutableStateFlow(0)
    val voiceAmplitude: StateFlow<Int> = _voiceAmplitude.asStateFlow()

    private val _hasVoiceClip = MutableStateFlow(false)
    val hasVoiceClip: StateFlow<Boolean> = _hasVoiceClip.asStateFlow()

    /** One-shot 60 s auto-stop event (the UI toasts once per emission). */
    private val _voiceCapEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val voiceCapEvent: SharedFlow<Unit> = _voiceCapEvent.asSharedFlow()

    private var voiceRecorder: VoiceMessageRecorder? = null

    /** Test seam: a fake replaces the platform recorder in unit tests. */
    internal var voiceRecorderOverride: VoiceMessageRecorder? = null

    private var voiceClipFile: java.io.File? = null
    private var voiceSessionJob: Job? = null

    /** Single-flight guard: taps during recorder spin-up are ignored. */
    @Volatile
    private var voiceStarting = false

    private fun getVoiceRecorder(): VoiceMessageRecorder {
        voiceRecorderOverride?.let { return it }
        return voiceRecorder ?: VoiceMessageRecorder(
            context = context,
            outputDir = java.io.File(context.filesDir, "voice"),
        ).also { voiceRecorder = it }
    }

    private fun peekVoiceRecorder(): VoiceMessageRecorder? =
        voiceRecorderOverride ?: voiceRecorder

    /**
     * Start voice capture. Stops dictation first (single live input mode).
     * The recording flag flips only when the platform accepted the start;
     * RECORD_AUDIO gating is owned by the caller (ChatScreen).
     */
    fun startVoiceRecording() {
        if (voiceStarting || _isVoiceRecording.value) return
        stopDictation()
        voiceStarting = true
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                val recorder = getVoiceRecorder()
                if (!recorder.start()) return@launch
                _isVoiceRecording.value = true
                _voiceElapsedSec.value = 0
                _voiceAmplitude.value = 0
                _hasVoiceClip.value = false
                voiceSessionJob?.cancel()
                voiceSessionJob = viewModelScope.launch(coroutineExceptionHandler) {
                    launch {
                        while (true) {
                            delay(100)
                            _voiceAmplitude.value = recorder.maxAmplitude()
                        }
                    }
                    launch {
                        while (true) {
                            delay(1_000)
                            val next = _voiceElapsedSec.value + 1
                            _voiceElapsedSec.value = next
                            if (next >= 60) {
                                autoStopVoiceRecording()
                                break
                            }
                        }
                    }
                }
            } finally {
                voiceStarting = false
            }
        }
    }

    /** Shared keep-and-stop core: cancel jobs, stop recorder, keep file. */
    private fun keepAndStopVoice(): java.io.File? {
        voiceSessionJob?.cancel()
        voiceSessionJob = null
        val file = try {
            peekVoiceRecorder()?.stop()
        } catch (e: Exception) {
            Timber.w(e, "VoiceMsg: stop failed")
            null
        }
        _isVoiceRecording.value = false
        _voiceAmplitude.value = 0
        return file
    }

    /** Manual stop (second tap): keep the clip in memory for send. */
    fun stopVoiceRecording() {
        if (!_isVoiceRecording.value) return
        val file = keepAndStopVoice()
        voiceClipFile = file
        _hasVoiceClip.value = file != null
    }

    /**
     * Idempotent keep-and-stop entry for the 60 s ticker (announceCap =
     * true) and the background lifecycle observer (announceCap = false).
     * The cap toast fires ONLY when a clip was actually kept at the cap
     * (CR-01: background auto-stop and too-short null stops stay
     * silent — no bogus "limit reached" toast).
     */
    fun autoStopVoiceRecording(announceCap: Boolean = true) {
        if (!_isVoiceRecording.value) return
        val file = keepAndStopVoice()
        voiceClipFile = file
        _hasVoiceClip.value = file != null
        if (announceCap && file != null) _voiceCapEvent.tryEmit(Unit)
    }

    /**
     * Explicit cancel (X): discard the file immediately — both an
     * in-progress recording and a previously kept clip.
     */
    fun cancelVoiceRecording() {
        voiceSessionJob?.cancel()
        voiceSessionJob = null
        try {
            peekVoiceRecorder()?.cancel()
        } catch (e: Exception) {
            Timber.w(e, "VoiceMsg: cancel failed")
        }
        // A kept clip from an earlier stop is also discarded: cancel
        // means the user rejected the recording, not deferred it.
        try {
            voiceClipFile?.takeIf { it.exists() }?.delete()
        } catch (e: Exception) {
            Timber.w(e, "VoiceMsg: cancel delete of kept clip failed")
        }
        voiceClipFile = null
        _hasVoiceClip.value = false
        _isVoiceRecording.value = false
        _voiceAmplitude.value = 0
    }

    /**
     * Transcode the kept clip (first 30 s to mono 16 kHz PCM on
     * Dispatchers.IO) and send through the existing
     * [sendMessage] audioBytes path so the capabilities.audio gate stays
     * the backstop. Transcode failure emits a Snackbar and keeps the file
     * for retry; failed sends keep the file (Phase 68 draft basis).
     */
    fun sendVoiceMessage(caption: String) {
        val file = voiceClipFile ?: return
        voiceClipFile = null
        _hasVoiceClip.value = false
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            val result = try {
                PcmTranscoder.transcodeFirst30s(file.absolutePath)
            } catch (e: Exception) {
                Timber.w(e, "VoiceMsg: transcode failed")
                voiceClipFile = file
                _hasVoiceClip.value = true
                _events.tryEmit(ChatEvent.Snackbar(context.getString(R.string.voice_msg_transcode_failed)))
                return@launch
            }
            if (result.truncated) {
                _events.tryEmit(ChatEvent.Snackbar(context.getString(R.string.voice_msg_first_30s)))
            }
            sendMessage(caption, audioBytes = result.bytes)
        }
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
            // Lazy load: mark pending only — the engine mounts on the
            // first send. Never preload on selection.
            activeModelSelection.selectLocalPending(modelId)
            updateConnection {
                it.copy(
                    selectedLocalModelId = modelId,
                    selectedRemoteModelId = null,
                    selectedRemoteProvider = null
                )
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

        // Unload previous model. Local->local needs NO explicit unload:
        // preload's switchToLiteRT unloads the old engine atomically first
        // (same monitor). A concurrent unload here could win the monitor
        // AFTER the fresh init and kill the just-mounted engine — the
        // switch that never finished loading. Local->remote keeps the
        // explicit unload (nothing else will free the RAM).
        if (oldLocalId != null && oldLocalId != modelId &&
            providerType != ProviderType.LITE_RT_LM
        ) {
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
                // Lazy load: the model mounts on the first send
                // (sendMessage drives markLocalLoading + preloadLocalModel
                // when the engine path mismatches the selection).
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
     * Verified capabilities for a local model file. Uses the allowlist
     * (verified-only) via [ModelAllowlistRepository.effectiveCapabilities] —
     * NEVER the raw `LocalModel.capabilities` lazy default (all-true), which
     * silently disables every capability gate. Null when no local model is
     * selected or the file is unknown (callers fail open for remote/unknown).
     */
    fun verifiedLocalCapabilities(filePath: String?): ModelCapabilities? {
        if (filePath == null) return null
        val model = _connection.value.localModels.firstOrNull { it.filePath == filePath }
            ?: return null
        return modelAllowlistRepository.effectiveCapabilities(model)
    }

    /**
     * 41-02: Decide whether the active model supports thinking.
     * Local model id has priority; falls back to the remote model id.
     * Returns false when no model is selected.
     *
     * 48 (WR-03): capability check, not a presence check — consults the
     * allowlist-verified reasoning flag. Fail-open while the model list
     * has not loaded yet (unknown ≠ unsupported). Remote models have no
     * capability data source yet, so presence still gates there.
     */
    private fun supportsThinkingFor(localId: String?, remoteId: String?): Boolean {
        if (localId != null) {
            val models = _connection.value.localModels
            if (models.isEmpty()) return true
            return verifiedLocalCapabilities(localId)?.reasoning == true
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
        // Loading-flag heal: the selection collector derives isLoadingModel
        // from localSelection.isLoading, but some paths leave
        // LocalSelection(modelId, connected=false, loading=false) behind
        // while the engine is already loaded for that same path (restart
        // restore rehydrates pending; the provider lazy-loads without
        // touching selection). Reconcile against engine truth here — read the raw engine path and
        // the raw selection (NOT isLocalModelLoaded, which derives from the
        // stuck connected flag and would be circular). Emitting connectLocal
        // lets the untouched collector clear isLoadingModel itself.
        val selectedLocalId = _connection.value.selectedLocalModelId
        val enginePath = engineManager.getActiveEngine()?.modelPath
        if (selectedLocalId != null &&
            enginePath == selectedLocalId &&
            !activeModelSelection.localSelection.value.isConnected
        ) {
            activeModelSelection.connectLocal(selectedLocalId, ProviderType.LITE_RT_LM)
        }
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

    /**
     * Lazy-load driver (quick-task lazy-model-load): mounts [filePath] into
     * the engine. Called ONLY from the send path (after `markLocalLoading`)
     * — never from selection sites. State flows ONLY through
     * [ActiveModelSelection] (`connectLocal` / `markLocalDisconnected`);
     * the selection collector owns `isLoadingModel`/`loadingModelName`.
     * This function writes `modelLoadError` only. Failures keep the
     * selection (pending) so retrying just works.
     */
    private suspend fun preloadLocalModel(filePath: String) {
        val model = _connection.value.localModels.firstOrNull { it.filePath == filePath }
        if (model != null && !memoryChecker.canLoadModel(model.sizeBytes)) {
            val memInfo = memoryChecker.getMemoryInfo()
            val modelMB = model.sizeBytes / (1024 * 1024)
            val availMB = memInfo.availableBytes / (1024 * 1024)
            // Memory guard: no engine load follows — clear the loading
            // flag via selection (kept for retry after freeing memory).
            activeModelSelection.markLocalDisconnected()
            updateConnection {
                it.copy(
                    modelLoadError = context.getString(R.string.error_no_memory_fmt, modelMB, availMB),
                )
            }
            return
        }

        if (model != null) {
            parameterStore.update(model.parameters)
        }

        updateConnection { it.copy(modelLoadError = null) }
        try {
            withContext(Dispatchers.Default) {
                engineManager.switchToLiteRT(filePath)
            }
            activeModelSelection.connectLocal(filePath, ProviderType.LITE_RT_LM)
            everLoadedPaths.add(filePath)
            refreshActiveBackend()
        } catch (e: Exception) {
            activeModelSelection.markLocalDisconnected()
            updateConnection { it.copy(modelLoadError = e.message) }
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
        if (state.conversationId != null) {
            // Quick-task (activation-new-chat): activation-created rows
            // arrive titled "New Chat" with zero messages — title from the
            // first message so history keeps the first-message convention.
            // Rows that already carry messages are never retitled.
            if (state.messages.isEmpty()) {
                try {
                    chatRepository.updateConversationTitle(
                        state.conversationId,
                        conversationTitle(firstMessage, hasMedia),
                    )
                } catch (e: Exception) {
                    Timber.w(e, "Chat: activation row retitle failed")
                }
            }
            return state.conversationId
        }

        val title = conversationTitle(firstMessage, hasMedia)
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

    private fun conversationTitle(firstMessage: String, hasMedia: Boolean): String = when {
        firstMessage.isBlank() && hasMedia -> "Image conversation"
        firstMessage.length > 50 -> firstMessage.take(50) + "..."
        else -> firstMessage
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

    /**
     * Last model auto-selected by the observeModels hook (loop guard — the
     * selection flow round-trips asynchronously, so the guard above could
     * re-fire on the next emission before the collector delivers).
     */
    private var autoSelectedModelPath: String? = null

    /**
     * Model paths that completed at least one engine load in this process
     * (user decision 2026-10-02: the loading row says "for the first time"
     * only for paths never loaded before). Main-thread confined: written in
     * [preloadLocalModel]'s post-load continuation, read by the selection
     * collector — both on the main dispatcher.
     */
    @VisibleForTesting
    internal val everLoadedPaths = mutableSetOf<String>()

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
        dictationManager?.destroy()
        dictationManager = null
        // Phase 67 (VMSG-01): cancel the recording session jobs and
        // destroy the recorder so nothing leaks past the screen.
        voiceSessionJob?.cancel()
        voiceSessionJob = null
        voiceRecorder?.destroy()
        voiceRecorder = null
        unloadLocalModels()
    }
}

/**
 * Pick the model to auto-select when nothing is selected (fresh
 * download/import leaves the chat input disabled with NoModelSelected).
 * Returns the newest model by import time, or null when a selection exists
 * or the list is empty. Pure — unit-tested without a ViewModel.
 */
internal fun pickAutoSelectModel(models: List<LocalModel>, selectedId: String?): String? {
    if (selectedId != null || models.isEmpty()) return null
    return models.maxByOrNull { it.importedAt }?.filePath
}
