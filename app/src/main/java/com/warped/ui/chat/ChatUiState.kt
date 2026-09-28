package com.warped.ui.chat

import androidx.compose.runtime.Immutable
import com.warped.data.local.inference.BackendType
import com.warped.domain.model.ChatMessage
import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.Conversation
import com.warped.domain.model.Endpoint
import com.warped.domain.model.GenerationParameters
import com.warped.domain.model.LocalModel
import com.warped.domain.model.ProviderType
import com.warped.domain.model.SyntaxTheme

/**
 * 48-01 (PERF-14): single-owner @Immutable sub-states replacing the 30-field
 * [ChatUiState] monolith as the source of truth.
 *
 * Ownership (each field has exactly one writer group):
 * - [ChatTranscriptState]: streaming Delta collector (streamingContent/
 *   streamingReasoning), send/Done/Error/stop/new-conversation turn boundaries
 *   (messages/isStreaming/clears), error paths (error).
 * - [ChatInputState]: updateInput + send-clear (inputText),
 *   toggleReasoning (reasoningEnabled),
 *   AdvancedPreferences collector (enableThinking), selection collectors
 *   (supportsThinking), turn code mirrors isStreaming (isGenerating).
 * - [ChatConnectionState]: selection/endpoint/prefs collectors and model
 *   lifecycle paths (everything low-frequency).
 *
 * Streaming tokens never touch the input flow; keystrokes never touch the
 * transcript flow.
 */
@Immutable
data class ChatTranscriptState(
    val conversationId: Long? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val streamingContent: String = "",
    val streamingReasoning: String = "",
    val error: ChatError? = null,
)

@Immutable
data class ChatInputState(
    val inputText: String = "",
    val reasoningEnabled: Boolean = true,
    val enableThinking: Boolean = false,
    val supportsThinking: Boolean = false,
    val isGenerating: Boolean = false,
    // Phase 50 (WEB-06): true while the grounding fetch is in flight.
    // Owner: send/stop turn code, mirrors isGenerating.
    val isFetchingWeb: Boolean = false,    // Phase 53 (TOGGLE-03): one-shot composer "Sin web" flag. Set by the
    // composer chip, consumed once at send start, reset after every send
    // regardless of outcome. Never persisted, never changes the toggle.
    val skipWebOnce: Boolean = false,
    // Phase 52 (FETCH-03): N-de-M fan-out progress. Nullable: present only
    // while fetching; cleared on completion/failure/Stop (transient, never
    // persisted, never a transcript message). The Phase 52 chip renders
    // done/total counts ONLY ("Leyendo N de M…") per the UI-SPEC chip
    // contract — perSource rows are NOT rendered in this phase. Rows start
    // as LOADING and the terminal pass records the final OK/OMITIDA
    // snapshot (transient: published, then cleared with progress in the
    // same turn); the recording — not a live rendered surface — is the
    // FETCH-02 "no silent drops" contract, retained for Phase 53
    // bottom-sheet consumers. Counts-only is sufficient for FETCH-03
    // because completions land out of order under parallel fan-out and
    // OMITIDA is only knowable at the terminal pass.
    val webFetchProgress: WebFetchProgress? = null,
    // Phase 54 (RETRY-01): validated-connectivity flag gating the Reintentar
    // affordance. Refreshed on init/resume, after each send, and after each
    // retry — never a live observer (no auto-retry by lock). Default false;
    // init calls refreshConnectivity() for the real value.
    val isValidatedOnline: Boolean = false,
)

/**
 * Phase 52 (FETCH-02/FETCH-03): per-source outcome of a fan-out turn.
 * Every attempted URL (up to the cap of 5) lands as [OK] or [OMITIDA] —
 * no silent drops. 6th+ URLs are ignored deterministically and never
 * enter this state.
 *
 * Recorded, not rendered, in Phase 52: the chip shows done/total counts
 * only (see `webFetchProgress`); these states are the terminal snapshot
 * for Phase 53 consumers.
 */
enum class PerSourceStatus {
    LOADING,
    OK,
    OMITIDA,
}

@Immutable
data class SourceFetchState(
    val url: String,
    val status: PerSourceStatus = PerSourceStatus.LOADING,
)

@Immutable
data class WebFetchProgress(
    val done: Int,
    val total: Int,
    val perSource: List<SourceFetchState> = emptyList(),
)

@Immutable
data class ChatConnectionState(
    @Deprecated("Use selectedLocalModelId or selectedRemoteModelId instead")
    val selectedProvider: ProviderType? = null,
    @Deprecated("Use selectedLocalModelId or selectedRemoteModelId instead")
    val selectedModelId: String? = null,
    val selectedLocalModelId: String? = null,
    val selectedRemoteModelId: String? = null,
    val selectedRemoteProvider: ProviderType? = null,
    val isLocalModelConnected: Boolean = false,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Unknown,
    val conversations: List<Conversation> = emptyList(),
    val localModels: List<LocalModel> = emptyList(),
    val endpoints: List<Endpoint> = emptyList(),
    val endpointModels: Map<Long, List<String>> = emptyMap(),
    val generationParameters: GenerationParameters = GenerationParameters(),
    val isLoadingModel: Boolean = false,
    val loadingModelName: String = "",
    val modelLoadError: String? = null,
    val loadedInstanceId: String? = null,
    val activeBackend: BackendType? = null,  // null unless LITE_RT_LM is loaded
    val isLocalModelLoaded: Boolean = false,
    val memoryWarningModel: com.warped.domain.model.LocalModel? = null,
    val codeTheme: SyntaxTheme = SyntaxTheme.MONOKAI,
    val codeFontScale: Float = 1.0f,
    val modelUnavailable: Boolean = false,
    val pendingModelSwitch: ModelSwitchRequest? = null,
    val conversationModelId: String? = null,
    val conversationProviderType: ProviderType? = null,
    // Phase 53 (TOGGLE-01): per-conversation tri-state web override
    // (null = Heredar, inherit the global default-ON). Loaded once per
    // selectConversation; written via setWebOverride. Never hot-observed
    // in the turn path (RESEARCH pitfall 6).
    val webOverride: Boolean? = null,
    // Phase 53 (TOGGLE-01): live global default for the Heredar hint
    // ("Heredar (activado/desactivado global)"). Mirrors DataStore.
    val webGroundingEnabled: Boolean = true,
)

/**
 * 48-01 (PERF-15, threat T-48-01/T-48-02): synthetic keys for the transient
 * trailing LazyColumn items. Constants by construction — never derived from
 * content hashes — so rotation can never duplicate the streaming bubble and
 * fling reuse can never cross-contaminate rows.
 */
object ChatListKeys {
    const val STREAMING = "streaming"
}

/**
 * 48-01 compat snapshot: the pre-split monolith, now DERIVED (combine of the
 * three sub-states in ChatViewModel) instead of written directly. No external
 * screen collects ChatViewModel.uiState (verified: all other `uiState`
 * collectors belong to their own ViewModels), so this stays for one phase as
 * the migration shim for existing tests/callers, then goes away.
 */
@Deprecated("PERF-14 shim: collect transcriptState/inputState/connectionState instead")
data class ChatUiState(
    val conversationId: Long? = null,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val streamingContent: String = "",
    val streamingReasoning: String = "",
    @Deprecated("Use selectedLocalModelId or selectedRemoteModelId instead")
    val selectedProvider: ProviderType? = null,
    @Deprecated("Use selectedLocalModelId or selectedRemoteModelId instead")
    val selectedModelId: String? = null,
    val selectedLocalModelId: String? = null,
    val selectedRemoteModelId: String? = null,
    val selectedRemoteProvider: ProviderType? = null,
    val isLocalModelConnected: Boolean = false,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Unknown,
    val error: ChatError? = null,
    val conversations: List<Conversation> = emptyList(),
    val localModels: List<LocalModel> = emptyList(),
    val endpoints: List<Endpoint> = emptyList(),
    val endpointModels: Map<Long, List<String>> = emptyMap(),
    val isStreaming: Boolean = false,
    val generationParameters: GenerationParameters = GenerationParameters(),
    val isFetchingWeb: Boolean = false,
    val webFetchProgress: WebFetchProgress? = null,
    val isValidatedOnline: Boolean = false,
    val isLoadingModel: Boolean = false,
    val loadingModelName: String = "",
    val modelLoadError: String? = null,
    val loadedInstanceId: String? = null,
    val reasoningEnabled: Boolean = true,
    val enableThinking: Boolean = false,
    val supportsThinking: Boolean = false,
    val activeBackend: BackendType? = null,  // null unless LITE_RT_LM is loaded
    val isLocalModelLoaded: Boolean = false,
    val memoryWarningModel: com.warped.domain.model.LocalModel? = null,
    val codeTheme: SyntaxTheme = SyntaxTheme.MONOKAI,
    val codeFontScale: Float = 1.0f,
    val modelUnavailable: Boolean = false,
    val pendingModelSwitch: ModelSwitchRequest? = null,
    val conversationModelId: String? = null,
    val conversationProviderType: ProviderType? = null,
    val webOverride: Boolean? = null,
    val webGroundingEnabled: Boolean = true,
)

/** 48-01: the single derivation point monolith-shim ← sub-states. */
@Suppress("DEPRECATION")
fun combineSnapshot(
    transcript: ChatTranscriptState,
    input: ChatInputState,
    connection: ChatConnectionState,
): ChatUiState = ChatUiState(
    conversationId = transcript.conversationId,
    messages = transcript.messages,
    inputText = input.inputText,
    isGenerating = input.isGenerating,
    streamingContent = transcript.streamingContent,
    streamingReasoning = transcript.streamingReasoning,
    selectedProvider = connection.selectedProvider,
    selectedModelId = connection.selectedModelId,
    selectedLocalModelId = connection.selectedLocalModelId,
    selectedRemoteModelId = connection.selectedRemoteModelId,
    selectedRemoteProvider = connection.selectedRemoteProvider,
    isLocalModelConnected = connection.isLocalModelConnected,
    connectionStatus = connection.connectionStatus,
    error = transcript.error,
    conversations = connection.conversations,
    localModels = connection.localModels,
    endpoints = connection.endpoints,
    endpointModels = connection.endpointModels,
    isStreaming = transcript.isStreaming,
    isFetchingWeb = input.isFetchingWeb,
    webFetchProgress = input.webFetchProgress,
    isValidatedOnline = input.isValidatedOnline,
    generationParameters = connection.generationParameters,
    isLoadingModel = connection.isLoadingModel,
    loadingModelName = connection.loadingModelName,
    modelLoadError = connection.modelLoadError,
    loadedInstanceId = connection.loadedInstanceId,
    reasoningEnabled = input.reasoningEnabled,
    enableThinking = input.enableThinking,
    supportsThinking = input.supportsThinking,
    activeBackend = connection.activeBackend,
    isLocalModelLoaded = connection.isLocalModelLoaded,
    memoryWarningModel = connection.memoryWarningModel,
    codeTheme = connection.codeTheme,
    codeFontScale = connection.codeFontScale,
    modelUnavailable = connection.modelUnavailable,
    pendingModelSwitch = connection.pendingModelSwitch,
    conversationModelId = connection.conversationModelId,
    conversationProviderType = connection.conversationProviderType,
    webOverride = connection.webOverride,
    webGroundingEnabled = connection.webGroundingEnabled,
)

enum class TrafficLightState {
    GREEN, YELLOW, RED, GRAY
}

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
    val endpointId: Long? = null,
)

/**
 * Phase 53 (TOGGLE-01/SRC-02): one-shot UI events from ChatViewModel to
 * ChatScreen. Emitted with tryEmit (never suspends the turn); the screen
 * renders them as Snackbars. Chat continues regardless (non-blocking).
 */
sealed interface ChatEvent {
    data class Snackbar(val message: String) : ChatEvent
}

/**
 * 48-01: traffic-light derivation over the split states (PERF-04 precedent:
 * a low-frequency combine/derivedStateOf, never a monolith read). The
 * [ChatUiState] extension below delegates here so behavior is defined once.
 */
fun trafficLightState(
    transcript: ChatTranscriptState,
    connection: ChatConnectionState,
): TrafficLightState {
    val isLocal = connection.selectedLocalModelId != null && connection.isLocalModelLoaded
    val isRemote = connection.selectedRemoteModelId != null && connection.selectedRemoteProvider != null
    return when {
        transcript.isStreaming -> TrafficLightState.YELLOW
        connection.memoryWarningModel != null -> TrafficLightState.RED
        transcript.error != null -> TrafficLightState.RED
        isLocal && connection.isLocalModelLoaded -> TrafficLightState.GREEN
        isRemote && connection.connectionStatus == ConnectionStatus.Connected -> TrafficLightState.GREEN
        isLocal || isRemote -> TrafficLightState.RED
        else -> TrafficLightState.GRAY
    }
}

fun trafficLightStatusText(
    transcript: ChatTranscriptState,
    connection: ChatConnectionState,
): String {
    val light = trafficLightState(transcript, connection)
    val isLocal = connection.selectedLocalModelId != null
    val isRemote = connection.selectedRemoteModelId != null
    val localName = connection.localModels.firstOrNull { it.filePath == connection.selectedLocalModelId }?.name
        ?: connection.selectedLocalModelId?.substringAfterLast("/") ?: "Unknown model"
    val remoteName = connection.selectedRemoteModelId?.substringAfterLast("/") ?: "Unknown model"
    val error = transcript.error
    return when {
        light == TrafficLightState.YELLOW -> "Generating response…"
        light == TrafficLightState.GREEN && isLocal -> "Local: $localName — Connected"
        light == TrafficLightState.GREEN && isRemote -> "Remote: $remoteName — Connected"
        light == TrafficLightState.RED && error != null -> "Error: ${
            when (error) { is ChatError.Network -> error.message; is ChatError.Server -> error.message; is ChatError.Auth -> error.message; is ChatError.Unknown -> error.message; else -> "Connection error" }
        }"
        light == TrafficLightState.RED && isLocal -> "Local: $localName — Not connected"
        light == TrafficLightState.RED && isRemote -> "Remote: $remoteName — Disconnected"
        else -> "No model selected"
    }
}

@Suppress("DEPRECATION")
fun ChatUiState.trafficLightState(): TrafficLightState = trafficLightState(
    ChatTranscriptState(
        isStreaming = isStreaming,
        error = error,
    ),
    ChatConnectionState(
        selectedLocalModelId = selectedLocalModelId,
        selectedRemoteModelId = selectedRemoteModelId,
        selectedRemoteProvider = selectedRemoteProvider,
        connectionStatus = connectionStatus,
        localModels = localModels,
        isLocalModelLoaded = isLocalModelLoaded,
        memoryWarningModel = memoryWarningModel,
    ),
)

@Suppress("DEPRECATION")
fun ChatUiState.trafficLightStatusText(): String = trafficLightStatusText(
    ChatTranscriptState(
        isStreaming = isStreaming,
        error = error,
    ),
    ChatConnectionState(
        selectedLocalModelId = selectedLocalModelId,
        selectedRemoteModelId = selectedRemoteModelId,
        selectedRemoteProvider = selectedRemoteProvider,
        connectionStatus = connectionStatus,
        localModels = localModels,
        isLocalModelLoaded = isLocalModelLoaded,
        memoryWarningModel = memoryWarningModel,
    ),
)
