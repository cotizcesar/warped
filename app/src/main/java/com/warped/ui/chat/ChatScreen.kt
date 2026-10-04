package com.warped.ui.chat

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warped.R
import com.warped.data.local.inference.BackendType
import com.warped.domain.model.ProviderType
import com.warped.domain.model.Role
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber
import com.warped.ui.chat.components.ChatInputBar
import com.warped.ui.chat.components.MessageBubble
import com.warped.ui.chat.components.ModelSelectorSheet
import com.warped.ui.chat.voice.GateState
import com.warped.ui.components.WarpedAlertDialog

/**
 * Phase 67 (VMSG-01 full): post-grant intent distinguishing which input
 * mode requested RECORD_AUDIO, so the shared permission launcher routes
 * the grant result back to the right starter.
 */
private enum class PendingVoiceRequest {
    DICTATION,
    VOICE,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit = {},
    onNavigateToSelector: () -> Unit = {},
    onNavigateToCatalog: () -> Unit = {},
    onNavigateToHelp: () -> Unit = {},
    conversationId: Long = 0L,
    newChat: Boolean = false
) {
    // 48-01 (PERF-14): each region collects ONLY its sub-state flow — never
    // the monolith. Streaming tokens recompose the list; keystrokes recompose
    // only the input bar.
    val transcript by viewModel.transcriptState.collectAsStateWithLifecycle()
    val input by viewModel.inputState.collectAsStateWithLifecycle()
    val connection by viewModel.connectionState.collectAsStateWithLifecycle()
    // Phase 65 (VOICE-02 UI): dictation affordance state. Cached flows from
    // the ViewModel (platform probe ran once at init off the main thread) —
    // reading them here adds no composition-hot-path work.
    val speechAvailable by viewModel.speechAvailable.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    // Phase 67 (VMSG-01 tracer): voice-message recording state. The
    // permission gate is a temporary bypass — RECORD_AUDIO is assumed
    // granted for the tracer path (check before start, no-op otherwise,
    // no crash); the full rationale/Snackbar flow lands in Plan 02.
    val isVoiceRecording by viewModel.isVoiceRecording.collectAsStateWithLifecycle()
    // Phase 69 Plan 01 (VMSG-04/08): the VM voiceSendGate flow is the
    // single gate source — the button, hint, and send-block all read it
    // and flip live on model switch (no screen-local capability read).
    val voiceGate by viewModel.voiceSendGate.collectAsStateWithLifecycle()
    // Phase 69 Plan 03 (VMSG-03): one-shot coachmark state for the
    // voice-send button (VM persists dismissal — never re-shows).
    val showVoiceCoachmark by viewModel.showVoiceCoachmark.collectAsStateWithLifecycle()
    val hasVoiceClip by viewModel.hasVoiceClip.collectAsStateWithLifecycle()
    // Phase 70 (70-02): per-turn document attachment → chip params below.
    // Only READY attachments surface (chip); other statuses keep the text
    // sendable with their contracted Snackbar notices (VM-owned).
    val attachedDoc by viewModel.attachedDocument.collectAsStateWithLifecycle()
    val readyDoc = attachedDoc?.takeIf { it.status == ChatViewModel.AttachStatus.READY }
    // Phase 67 (VMSG-01 full): recording-row state (timer + amplitude).
    val voiceElapsedSec by viewModel.voiceElapsedSec.collectAsStateWithLifecycle()
    val voiceAmplitude by viewModel.voiceAmplitude.collectAsStateWithLifecycle()
    // Phase 68 (VMSG-02): draft preview state (clip duration + playback).
    val isDraftPlaying by viewModel.isDraftPlaying.collectAsStateWithLifecycle()
    val draftPositionMs by viewModel.draftPositionMs.collectAsStateWithLifecycle()
    val draftDurationMs by viewModel.draftDurationMs.collectAsStateWithLifecycle()
    // Phase 68 Plan 03 (VMSG-06): history playback state for voice bubbles.
    val playingMessageId by viewModel.playingMessageId.collectAsStateWithLifecycle()
    val isHistoryPlaying by viewModel.isHistoryPlaying.collectAsStateWithLifecycle()
    val historyPositionMs by viewModel.historyPositionMs.collectAsStateWithLifecycle()
    // 48-01 (PERF-15): keyed LazyColumn state. isAtBottom follows 48-UI-SPEC
    // §3 ("last item visible and within 48dp of the end").
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val bottomThresholdPx = remember(density) { with(density) { 48.dp.roundToPx() } }
    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.totalItemsCount == 0) return@derivedStateOf true
            val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            lastVisible.index == info.totalItemsCount - 1 &&
                (lastVisible.offset + lastVisible.size - info.viewportEndOffset) <= bottomThresholdPx
        }
    }
    // Latch for the Jump-to-latest pill: sets when content arrives while
    // scrolled up; clears on reaching bottom, pill tap, or send.
    var hasNewContentBelow by remember { mutableStateOf(false) }
    // One-shot: send / conversation-open land at the bottom regardless of
    // the current viewport.
    var snapToBottomOnNextContent by remember { mutableStateOf(false) }
    var attachedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var overlayHeightPx by remember { mutableIntStateOf(0) }
    var audioBytes by remember { mutableStateOf<ByteArray?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    // Phase 65 (VOICE-02): first-tap rationale visibility. Shown only when
    // RECORD_AUDIO is ungranted; confirm fires the system request.
    var showVoiceRationale by remember { mutableStateOf(false) }
    // Phase 67 (VMSG-01 full): post-grant intent distinguishing dictation
    // vs voice-send taps, routed through the EXISTING micPermissionLauncher
    // (no second launcher). Set before showing the rationale or firing
    // the request; consumed and cleared on the grant/denial result.
    // WR-04: persisted as the enum name via rememberSaveable (the enum
    // itself is not Parcelable) so rotation across the permission grant
    // no longer drops the intent into a silent no-op.
    var pendingVoiceRequestName by rememberSaveable { mutableStateOf<String?>(null) }
    // IN-02: first-tap rationale per UI-SPEC section 4. The dialog shows
    // once; later ungranted taps request the permission directly.
    // rememberSaveable so rotation does not re-trigger it (process death
    // re-shows once — the safe direction).
    var voiceRationaleSeen by rememberSaveable { mutableStateOf(false) }
    // API-03: system back dismisses the model picker through the same
    // onDismiss path as tap-outside/scrim — gesture and button identical.
    // (The sheet itself also self-dismisses; this is the explicit contract.)
    BackHandler(enabled = showModelPicker) { showModelPicker = false }

    LaunchedEffect(conversationId) {
        if (conversationId > 0) {
            snapToBottomOnNextContent = true
            viewModel.selectConversation(conversationId)
        }
    }
    // Per-conversation composer drafts: leaving a chat stashes the unsent
    // composer (text, images, send-bytes, document, settled voice draft)
    // under it; arriving restores the target's stash (or clears to fresh).
    // The dispose closure holds the leaving chat's values — never the
    // arriving chat's — so nothing is lost or leaked across chats.
    DisposableEffect(conversationId) {
        onDispose {
            viewModel.stashComposerDraft(
                transcript.conversationId,
                input.inputText,
                attachedImages,
                audioBytes,
            )
        }
    }
    LaunchedEffect(transcript.conversationId) {
        val draft = viewModel.popComposerDraft(transcript.conversationId)
        attachedImages = draft?.images.orEmpty()
        audioBytes = draft?.audioBytes
        viewModel.restoreComposerDraft(draft)
    }
    LaunchedEffect(Unit) {
        if (conversationId == 0L) {
            snapToBottomOnNextContent = true
            if (newChat) {
                viewModel.newConversation()
            } else {
                viewModel.loadLastConversation()
            }
        }
    }
    // Phase 68: screen Context hoisted for the lifecycle observer (the
    // ON_RESUME branch and permission flows below need it).
    val context = LocalContext.current
    DisposableEffect(Unit) {
        onDispose {
            // Phase 65 (VOICE-03): stop dictation when leaving the screen so
            // the recognizer never outlives the UI (destroy itself happens
            // in ChatViewModel.onCleared).
            viewModel.stopDictation()
            // Phase 68 (VMSG-02 + Plan 03): stop draft AND history playback
            // on chat exit — the player never leaks past the screen
            // (destroy itself happens in ChatViewModel.onCleared).
            viewModel.stopPlayback()
            viewModel.unloadLocalModels()
        }
    }
    // Phase 54 (RETRY-01): resume-only connectivity refresh flips the
    // Reintentar visibility gate — never triggers a fetch by itself
    // (no-auto-retry lock).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshConnectivity()
                // WR-04: re-probe recognizer availability on resume (cheap,
                // off the main thread) so a late-installed recognizer shows
                // the mic without a process restart.
                viewModel.refreshSpeechAvailability()
            }
            // Phase 67 (VMSG-01 full): backgrounding auto-stops recording
            // and keeps the clip (no foreground service). Silent stop —
            // the 60 s cap toast belongs to the ticker path only (CR-01).
            // Rotation is a config change, not a pause, so VM-owned state
            // survives it.
            if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.autoStopVoiceRecording(announceCap = false)
                // Phase 68 (VMSG-02 + Plan 03, WR-06): pause draft AND history
                // playback on every pause — rotation (config change) AND
                // plain backgrounding both keep bubble state (selection +
                // position) for one-tap resume, per CONTEXT. The player is
                // VM-owned (no Activity handle), so the paused handle
                // survives recreation; the draft file stays on disk either
                // way. Chat exit still stops via stopPlayback (DisposableEffect).
                viewModel.pauseVoiceDraft()
                viewModel.pauseHistoryVoice()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Memory warning dialog (app-styled card + warning icon).
    if (connection.memoryWarningModel != null) {
        val model = connection.memoryWarningModel!!
        val context = androidx.compose.ui.platform.LocalContext.current
        val info = com.warped.data.local.inference.MemoryChecker(context).getMemoryInfo()
        WarpedAlertDialog(
            onDismissRequest = { viewModel.dismissMemoryWarning() },
            icon = {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp),
                )
            },
            title = { Text(stringResource(R.string.memory_warning_title)) },
            text = {
                Text(stringResource(R.string.memory_warning_message,
                    model.sizeBytes / (1024 * 1024),
                    info.availableBytes / (1024 * 1024)))
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmLoadMemoryWarning() }) {
                    Text(stringResource(R.string.continue_text))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissMemoryWarning() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Phase 66 (RATE-01): Activity handle for the ambient Play review
    // flow. The ViewModel keeps only this provider (never the Activity);
    // cleared when the screen leaves so nothing leaks.
    DisposableEffect(context) {
        viewModel.setReviewActivityProvider { context as? Activity }
        onDispose { viewModel.setReviewActivityProvider(null) }
    }

    // Phase 53 (TOGGLE-01/SRC-02): one-shot ViewModel events (toggle
    // feedback, persist-failure notice) rendered as non-blocking Snackbars.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ChatEvent.Snackbar ->
                    snackbarHostState.showSnackbar(event.message, duration = SnackbarDuration.Short)
                // Phase 65 (VOICE-02): permanent-denial escape. Long duration
                // for reading time + action tap; the Settings action
                // deep-links to this app's OS details page only (fixed
                // ACTION_APPLICATION_DETAILS_SETTINGS + package URI, no
                // extras, no user-controlled destination — T-65-05).
                is ChatEvent.SnackbarWithAction ->
                    when (event.action) {
                        SnackbarAction.OPEN_APP_SETTINGS -> {
                            val result = snackbarHostState.showSnackbar(
                                message = event.message,
                                actionLabel = event.actionLabel,
                                duration = SnackbarDuration.Long,
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                )
                            }
                        }
                    }
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        val supported = mutableListOf<Uri>()
        val rejected = mutableListOf<String>()
        for (uri in uris) {
            val mime = context.contentResolver.getType(uri)?.lowercase() ?: ""
            if (mime == "image/png" || mime == "image/jpeg" || mime == "image/jpg") {
                supported.add(uri)
            } else {
                rejected.add(mime.ifBlank { "unknown" })
            }
        }
        attachedImages = supported
        if (rejected.isNotEmpty()) {
            val names = rejected.joinToString(", ") { it.substringAfterLast("/") }
            scope.launch {
                snackbarHostState.showSnackbar(
                    "Unsupported format: $names — only PNG and JPEG are allowed",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    // Phase 70 (70-02): document picker (SAF OpenDocument, text-MIME
    // filter — system picker only, per-file read-only grant, no storage
    // permission). A null result (dismissed picker) is a no-op; a new pick
    // replaces the previous attachment in the VM (one document per turn).
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.attachDocument(it) }
    }

    // Phase 65 (VOICE-02): RECORD_AUDIO runtime request. Follows the
    // imagePickerLauncher shape above with ActivityResultContracts
    // .RequestPermission. Phase 67 routes BOTH dictation and voice-send
    // intents through this launcher via pendingVoiceRequestName: granted
    // starts the requested mode; denial applies the per-mode policy
    // (dictation keeps the Phase 65 silent-transient + Settings-escape
    // permanent; voice-send emits the voice-denied Snackbar / escape).
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val request = pendingVoiceRequestName?.let { PendingVoiceRequest.valueOf(it) }
        pendingVoiceRequestName = null
        if (granted) {
            when (request) {
                PendingVoiceRequest.DICTATION -> viewModel.startDictation()
                PendingVoiceRequest.VOICE -> viewModel.startVoiceRecording()
                null -> Unit
            }
        } else {
            // IN-01: a non-Activity context (previews, wrapped test
            // contexts) used to misclassify silently. Log the fallback so
            // the transient assumption is observable; production always
            // composes under MainActivity.
            val activity = context as? Activity
            val permanent = if (activity != null) {
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
            } else {
                Timber.w("Voice: non-Activity context, assuming transient denial")
                false
            }
            when (request) {
                PendingVoiceRequest.VOICE -> {
                    // Phase 67: denial never starts recording and never
                    // crashes — transient gets the plain Snackbar, permanent
                    // reuses the Settings-escape channel with voice copy.
                    if (permanent) viewModel.emitVoiceDenied()
                    else viewModel.emitVoiceDeniedTransient()
                }
                else -> {
                    if (permanent) viewModel.emitMicDenied()
                }
            }
        }
    }

    // Phase 65 (VOICE-01): mic tap gate. The permission check runs inside
    // the click lambda — never on the composition hot path, so the input
    // bar never janks. Granted toggles start/stop; first ungranted tap
    // opens the in-context rationale (IN-02), later ungranted taps request
    // directly (whose confirm fires the launcher above).
    val onMicClick = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            if (isListening) viewModel.stopDictation() else viewModel.startDictation()
        } else if (!voiceRationaleSeen) {
            pendingVoiceRequestName = PendingVoiceRequest.DICTATION.name
            showVoiceRationale = true
        } else {
            pendingVoiceRequestName = PendingVoiceRequest.DICTATION.name
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Phase 69 Plan 01 (VMSG-04/08): gate-driven voice explainer. Gated
    // taps never dead-end: text-only links to the model catalog, remote
    // links to the Help voice section (Plan 03 wiring below).
    // Lint-safe: strings resolved via stringResource in composition —
    // context.getString() inside the launched coroutine trips
    // LocalContextGetResourceValueCall.
    val gateAudioMsg = stringResource(R.string.voice_msg_gate_audio)
    val gateViewModelsMsg = stringResource(R.string.voice_msg_view_models)
    val gateRemoteMsg = stringResource(R.string.voice_msg_gate_remote)
    val gateLearnMoreMsg = stringResource(R.string.voice_msg_learn_more)
    val showVoiceGateExplainer: (GateState) -> Unit = { gate ->
        scope.launch {
            when (gate) {
                GateState.GatedTextOnly -> {
                    val result = snackbarHostState.showSnackbar(
                        message = gateAudioMsg,
                        actionLabel = gateViewModelsMsg,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) onNavigateToCatalog()
                }
                GateState.GatedRemote -> {
                    val result = snackbarHostState.showSnackbar(
                        message = gateRemoteMsg,
                        actionLabel = gateLearnMoreMsg,
                        duration = SnackbarDuration.Long,
                    )
                    // Phase 69 Plan 03 (VMSG-03): the secondary explainer
                    // action lands on the Help voice section (all three
                    // chat entry points wire onNavigateToHelp in NavGraph).
                    if (result == SnackbarResult.ActionPerformed) onNavigateToHelp()
                }
                GateState.Allowed -> Unit
            }
        }
    }

    // Phase 67 (VMSG-01 full): voice-send tap gate. Order: the VM gate
    // first (explainer, never a dead button), then the permission flow
    // carrying the VOICE intent, then the toggle. isLoadingModel locks the
    // whole bar via inputLocked (existing gate — verified, not duplicated),
    // so the button is unreachable mid-load.
    val onVoiceClick = {
        val gate = voiceGate
        if (gate != GateState.Allowed) {
            showVoiceGateExplainer(gate)
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            if (isVoiceRecording) viewModel.stopVoiceRecording() else viewModel.startVoiceRecording()
        } else if (!voiceRationaleSeen) {
            pendingVoiceRequestName = PendingVoiceRequest.VOICE.name
            showVoiceRationale = true
        } else {
            pendingVoiceRequestName = PendingVoiceRequest.VOICE.name
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    // Phase 67 (VMSG-01 full): 60 s auto-stop toast. Collects the one-shot
    // voiceCapEvent flow (once per emission — never derived from
    // isVoiceRecording state, so recomposition cannot re-fire it).
    LaunchedEffect(Unit) {
        viewModel.voiceCapEvent.collect {
            Toast.makeText(context, R.string.voice_msg_cap_reached, Toast.LENGTH_SHORT).show()
        }
    }

    val selectedModelName = run {
        val effectiveModelId = connection.selectedLocalModelId ?: connection.selectedRemoteModelId
        val effectiveProvider = if (connection.selectedLocalModelId != null) ProviderType.LITE_RT_LM
            else connection.selectedRemoteProvider
        val local = connection.localModels.firstOrNull { it.filePath == effectiveModelId }
        val endpoint = connection.endpoints.firstOrNull {
            it.modelId == effectiveModelId && it.apiType == effectiveProvider
        }
        when {
            local != null -> local.name
            endpoint != null -> effectiveModelId?.substringAfterLast("/") ?: effectiveModelId
            else -> null
        }
    }

    // 48-01 (PERF-15): stick-to-bottom gating. The viewport moves on new
    // content ONLY when already at the bottom (scrollToItem pin — never the
    // per-token animateScrollTo defect); otherwise the hasNewContentBelow
    // latch sets and the pill takes over.
    val showStreamingBubble = transcript.streamingContent.isNotEmpty() || transcript.streamingReasoning.isNotEmpty()
    // Unified turn status (quick-turn-status): the streaming gap (streaming
    // with no content or reasoning yet, and no tool/fetch row covering it)
    // renders in the bottomBar slot via TurnStatusRow — never as a trailing
    // in-list row. Precomputed here so the gap math stays in one place;
    // mutually exclusive with the streaming bubble, exactly one trailing
    // row at most, same pin/pill math.
    val isStreamingGap = transcript.isStreaming &&
        transcript.streamingContent.isEmpty() &&
        transcript.streamingReasoning.isEmpty() &&
        !input.isFetchingWeb &&
        // 56-02: the tool row already covers the gap during tool calls.
        input.toolCallActive == null
    val trailingCount = if (showStreamingBubble) 1 else 0
    val totalItems = transcript.messages.size + trailingCount
    LaunchedEffect(
        transcript.messages.size,
        transcript.streamingContent.length,
        transcript.streamingReasoning.length,
    ) {
        if (totalItems == 0) return@LaunchedEffect
        if (snapToBottomOnNextContent || isAtBottom) {
            snapToBottomOnNextContent = false
            hasNewContentBelow = false
            listState.pinLastItemEnd(totalItems - 1)
        } else {
            hasNewContentBelow = true
        }
    }
    LaunchedEffect(isAtBottom) {
        if (isAtBottom) hasNewContentBelow = false
    }
    // 48-UI-SPEC §2 visibility rule: non-empty list AND scrolled up AND new
    // content arrived since. Never on empty state, never at bottom.
    val isEmpty = transcript.messages.isEmpty()
        && transcript.streamingContent.isEmpty()
        && transcript.streamingReasoning.isEmpty()
    val showPill = !isEmpty && !isAtBottom && hasNewContentBelow

    Scaffold(
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState, modifier = Modifier.padding(bottom = with(density) { overlayHeightPx.toDp() } + 16.dp)) },
        topBar = { /* CHAT-02: removed TopAppBar — model picker is now inline above messages */
            // Drawer remains reachable via swipe (ModalNavigationDrawer around the Scaffold)
            // and the parent NavHost provides the drawer gesture.
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            // CHAT-02/04/05: Inline model selector bar above messages
            // PERF-04: derivedStateOf wraps the traffic-light derivation so the inline
            // model selector doesn't re-derive it on every recomposition (e.g. when
            // a single streaming character arrives).
            // 48-01: derived over the two collected sub-states (combine-level,
            // low-frequency) — never the monolith.
            val trafficLight by remember(transcript, connection) {
                derivedStateOf { trafficLightState(transcript, connection) }
            }
            InlineModelSelectorBar(
                selectedModelName = selectedModelName,
                isLocal = connection.selectedLocalModelId != null,
                isLoading = connection.isLoadingModel,
                loadingModelName = connection.loadingModelName,
                trafficLight = trafficLight,
                onClick = {
                    showModelPicker = true
                    viewModel.fetchAllEndpointModels()
                },
                onOpenDrawer = onOpenDrawer,
                thinkingEnabled = input.enableThinking,
                supportsThinking = input.supportsThinking,
                onToggleThinking = { viewModel.toggleThinking() },
            )

            Box(modifier = Modifier.weight(1f)) {
                if (isEmpty) {
                    // Empty state
                    // Optical centering: the floating input overlay covers
                    // the bottom of this Box, so center within the area
                    // above it (overlay height as bottom padding) — logo +
                    // text sit truly centered in the visible space.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .fillMaxWidth()
                            .padding(bottom = with(density) { overlayHeightPx.toDp() }),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                painter = painterResource(id = com.warped.R.drawable.logo),
                                contentDescription = stringResource(R.string.cd_logo),
                                modifier = Modifier.size(128.dp)
                            )
                            Spacer(Modifier.height(24.dp))
                            Text(
                                stringResource(R.string.empty_state_message),
                                color = Color(0xFF9CA3AF),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 48-01 (PERF-15): keyed LazyColumn. Role.TOOL transcript rows
                        // are persisted ChatMessages with stable ids — keyed inline,
                        // zero extra work. Transient trailing rows use the constant
                        // ChatListKeys (never content hashes — T-48-01/T-48-02).
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = with(density) { overlayHeightPx.toDp() } + 4.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(transcript.messages, key = { it.id }) { message ->
                                // Phase 68 Plan 03: voice-bubble playback state.
                                // The file-exists check is remembered per
                                // message (VM helper, no streams) so composition
                                // never performs IO directly (T-68-08).
                                val voiceFileAvailable = remember(message.audioPath) {
                                    viewModel.hasVoiceFile(message.audioPath)
                                }
                                MessageBubble(
                                    message = message,
                                    codeTheme = connection.codeTheme,
                                    codeFontScale = connection.codeFontScale,
                                    isValidatedOnline = input.isValidatedOnline,
                                    isFetchingWeb = input.isFetchingWeb,
                                    isGenerating = input.isGenerating,
                                    onRetry = { viewModel.retryGrounding(it) },
                                    isVoicePlaying = playingMessageId == message.id && isHistoryPlaying,
                                    voicePositionMs = if (playingMessageId == message.id) historyPositionMs else 0,
                                    voiceFileMissing = message.audioPath != null && !voiceFileAvailable,
                                    onPlayVoice = { viewModel.toggleHistoryVoice(message) },
                                    onPauseVoice = { viewModel.pauseHistoryVoice() },
                                    onSeekVoice = { viewModel.seekHistoryVoice(it) },
                                )
                            }
                            if (showStreamingBubble) {
                                item(key = ChatListKeys.STREAMING) {
                                    MessageBubble(
                                        message = com.warped.domain.model.ChatMessage(
                                            role = Role.ASSISTANT,
                                            content = transcript.streamingContent,
                                            reasoning = transcript.streamingReasoning.ifEmpty { null }
                                        ),
                                        isStreaming = true,
                                        codeTheme = connection.codeTheme,
                                        codeFontScale = connection.codeFontScale
                                    )
                                }
                            }
                        }
                        // Top fade gradient overlay
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .align(Alignment.TopCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF1F1F1E),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        // 48-01 (48-UI-SPEC §2): Jump-to-latest pill — the only new
                        // composable. Renders above the fades, never dimmed.
                        JumpToLatestPillOverlay(
                            visible = showPill,
                            onClick = {
                                snapToBottomOnNextContent = false
                                hasNewContentBelow = false
                                scope.launch {
                                    if (totalItems > 0) {
                                        listState.animateScrollToItem(totalItems - 1)
                                        val info = listState.layoutInfo
                                        val item = info.visibleItemsInfo
                                            .firstOrNull { it.index == totalItems - 1 }
                                        if (item != null) {
                                            val overflow = item.offset + item.size -
                                                info.viewportEndOffset
                                            if (overflow > 0) {
                                                listState.animateScrollBy(overflow.toFloat())
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = with(density) { overlayHeightPx.toDp() } + 20.dp)
                        )
                    }
            }
                // Floating input overlay (shared by empty + history states):
                // the list scrolls UNDER a fade + the pill (transparent
                // surround), so text visibly travels behind instead of
                // clipping hard at an in-flow bar. Height is measured for
                // the list's bottom padding + the jump-pill offset.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { overlayHeightPx = it.height }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color(0xFF1F1F1E)
                                    )
                                )
                            )
                    )
                    // Unified turn status (quick-turn-status): ONE transient row
                    // above the input bar — priority model loading > tool >
                    // fetch/search > streaming gap. Never a transcript message, never
                    // persisted; unmounts on completion/failure/Stop. Null
                    // status renders nothing (and no spacer).
                    val turnStatus = resolveTurnStatus(
                        toolCallActive = input.toolCallActive,
                        isFetchingWeb = input.isFetchingWeb,
                        progress = input.webFetchProgress,
                        isStreamingGap = isStreamingGap,
                        isLoadingModel = connection.isLoadingModel,
                        loadingModelName = connection.loadingModelName,
                        loadingFirstTime = connection.loadingFirstTime,
                    )
                    TurnStatusRow(status = turnStatus)
                    // Phase 68 (VMSG-02): the draft card sends voice + caption
                    // through the same route as the input-row send (a kept clip
                    // always takes the transcode-and-send path).
                    val onSendMessage = {
                        // CR-03: never leave the mic live without its indicator
                        // — the mic hides while generating, so stop first.
                        if (isListening) viewModel.stopDictation()
                        snapToBottomOnNextContent = true
                        hasNewContentBelow = false
                        // Phase 67 (VMSG-05 tracer): a kept voice clip routes
                        // through the transcode-and-send path with the caption.
                        // Phase 69 Plan 01 (VMSG-04/08): a gated config with a
                        // kept draft routes to the explainer instead — the
                        // draft is kept, the send never attempted.
                        if (hasVoiceClip) {
                            if (voiceGate != GateState.Allowed) showVoiceGateExplainer(voiceGate)
                            else viewModel.sendVoiceMessage(input.inputText)
                        } else {
                            viewModel.sendMessage(input.inputText, attachedImages, audioBytes)
                        }
                        attachedImages = emptyList()
                        audioBytes = null
                    }
                    ChatInputBar(
                    text = input.inputText,
                    isGenerating = input.isGenerating,
                    canSend = (connection.selectedLocalModelId ?: connection.selectedRemoteModelId) != null,
                    onTextChange = { viewModel.updateInput(it) },
                    onSend = onSendMessage,
                    onStop = { viewModel.stopGeneration() },
                    // Verified-only capabilities: image/audio buttons hide when
                    // the selected local model lacks the modality. Remote or
                    // unselected → fail open (null → true).
                    modelHasVision = viewModel.verifiedLocalCapabilities(connection.selectedLocalModelId)?.vision
                        ?: true,
                    onAddImage = { imagePickerLauncher.launch("image/*") },
                    attachedImages = attachedImages,
                    onRemoveImage = { i -> attachedImages = attachedImages.filterIndexed { idx, _ -> idx != i } },
                    // Phase 69 Plan 01 (VMSG-04/08): the collected VM gate
                    // drives button visibility (hidden when gated — no
                    // screen-local capability read). Gated sends still
                    // route to the explainer via the VM send-path guards.
                    voiceGate = voiceGate,
                    // Phase 69 Plan 03 (VMSG-03): one-shot coachmark on the
                    // enabled voice button; any tap dismisses (VM persists).
                    showVoiceCoachmark = showVoiceCoachmark,
                    onCoachmarkDismiss = { viewModel.dismissVoiceCoachmark() },
                    onAudioRecorded = { bytes -> audioBytes = bytes },
                    onAudioRecordingChanged = { isRecording = it },
                    speechAvailable = speechAvailable,
                    isListening = isListening,
                    onMicClick = onMicClick,
                    // Phase 67 (VMSG-01 tracer): minimal voice-send toggle.
                    isVoiceRecording = isVoiceRecording,
                    onVoiceClick = onVoiceClick,
                    // Phase 67 (VMSG-01 full): recording row + cancel path.
                    voiceElapsedSec = voiceElapsedSec,
                    voiceAmplitude = voiceAmplitude,
                    onCancelRecording = { viewModel.cancelVoiceRecording() },
                    // Phase 68 (VMSG-02): draft preview card state + callbacks.
                    hasVoiceClip = hasVoiceClip,
                    isDraftPlaying = isDraftPlaying,
                    draftPositionMs = draftPositionMs,
                    draftDurationMs = draftDurationMs,
                    onPlayDraft = { viewModel.playVoiceDraft() },
                    onPauseDraft = { viewModel.pauseVoiceDraft() },
                    onSeekDraft = { viewModel.seekDraftVoice(it) },
                    onDeleteDraft = { viewModel.deleteVoiceDraft() },
                    // WR-03: report the caret so dictation inserts at cursor.
                    onCursorChange = { viewModel.updateInputCursor(it) },
                    // Model-loading gate: the whole bar locks while loading.
                    isLoadingModel = connection.isLoadingModel,
                    // Phase 70 (70-02): document attach affordance + chip. The
                    // existing SnackbarHost (Short) serves the VM
                    // unsupported/failed/truncation notices — no new channel.
                    onAttachDocument = {
                        documentPickerLauncher.launch(arrayOf("text/plain", "text/markdown", "text/*"))
                    },
                    attachedDocName = readyDoc?.filename,
                    attachedDocSize = readyDoc?.let { formatDocumentSize(it.sizeBytes) },
                    attachedDocTruncatedAt = readyDoc?.truncatedAt,
                    onRemoveDocument = { viewModel.clearDocument() },
                )
                }
            }

            if (connection.modelLoadError != null) {
                AutoDismissErrorBanner(
                    text = connection.modelLoadError ?: "",
                    onDismiss = { viewModel.clearModelLoadError() },
                )
            }

            if (transcript.error != null) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = { TextButton(onClick = { viewModel.clearError() }) { Text(stringResource(R.string.dismiss)) } }
                ) {
                    Text(
                        when (val error = transcript.error) {
                            is ChatError.Network -> error.message
                            is ChatError.Server -> error.message
                            is ChatError.Auth -> error.message
                            is ChatError.NoModelSelected -> stringResource(R.string.no_model_selected)
                            is ChatError.DownloadModelFirst -> stringResource(R.string.download_model_first)
                            is ChatError.ConnectionLost -> stringResource(R.string.connection_lost)
                            is ChatError.ModelUnavailable -> stringResource(R.string.error_model_unavailable)
                            is ChatError.Unknown -> error.message
                            null -> ""
                        }
                    )
                }
            }

            // Model unavailable banner
            if (connection.modelUnavailable) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    action = {
                        TextButton(onClick = { viewModel.dismissModelUnavailable() }) { Text(stringResource(R.string.dismiss)) }
                    }
                ) {
                    Text(stringResource(R.string.model_no_longer_installed))
                }
            }

            // Phase 65 (VOICE-02): first-tap in-context rationale. Copies the
            // WarpedAlertDialog slot pattern below; confirm is the sole
            // trigger of the system RECORD_AUDIO request (T-65-04), dismiss
            // reuses the existing dismiss string.
            if (showVoiceRationale) {
                WarpedAlertDialog(
                    onDismissRequest = {
                        voiceRationaleSeen = true
                        showVoiceRationale = false
                        // Dismissed without confirming: drop the pending
                        // intent so a later grant result cannot act on it.
                        pendingVoiceRequestName = null
                    },
                    title = { Text(stringResource(R.string.voice_rationale_title)) },
                    text = {
                        Text(
                            stringResource(
                                if (pendingVoiceRequestName == PendingVoiceRequest.VOICE.name) {
                                    R.string.voice_msg_rationale_body
                                } else {
                                    R.string.voice_rationale_body
                                }
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            voiceRationaleSeen = true
                            showVoiceRationale = false
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }) {
                            Text(stringResource(R.string.voice_rationale_allow))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            voiceRationaleSeen = true
                            showVoiceRationale = false
                            pendingVoiceRequestName = null
                        }) {
                            Text(stringResource(R.string.dismiss))
                        }
                    }
                )
            }

            // Model switch confirmation dialog
            if (connection.pendingModelSwitch != null) {
                WarpedAlertDialog(
                    onDismissRequest = { viewModel.cancelModelSwitch() },
                    title = { Text(stringResource(R.string.model_selected_new)) },
                    text = {
                        Text(
                            stringResource(R.string.dialog_model_switch_msg),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.confirmModelSwitch() }) {
                            Text(stringResource(R.string.new_chat))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.cancelModelSwitch() }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }
        }

        // CHAT-04/05: ModalBottomSheet model picker
        ModelSelectorSheet(
            visible = showModelPicker,
            selectedModelId = connection.selectedLocalModelId ?: connection.selectedRemoteModelId,
            selectedProvider = if (connection.selectedLocalModelId != null) ProviderType.LITE_RT_LM else connection.selectedRemoteProvider,
            localModels = connection.localModels,
            endpoints = connection.endpoints,
            endpointModels = connection.endpointModels,
            onDismiss = { showModelPicker = false },
            onModelSelected = { modelId, providerType, endpointId ->
                viewModel.launchModelSelection(modelId, providerType, endpointId)
            },
            onNavigateToCatalog = { showModelPicker = false; onNavigateToCatalog() }
        )
    }
}

/**
 * End-pinning follow helper. The list is NOT reversed, so scrollToItem(index)
 * pins the item TOP to the viewport top — as the trailing streaming bubble
 * grows downward, fresh tokens land below the fold. After the instant
 * scrollToItem, shift by the item's overflow past the viewport end so the
 * newest content stays visible. Pure list-state math, no new dependencies.
 * Instant (non-animated) — used by the per-token follow path (48-01 PERF-15).
 *
 * Quick-task (scroll-hardening): pure pin-target math for [pinLastItemEnd].
 * Layout can lag newly arrived items, so a target beyond the laid-out count
 * pins the current end instead of throwing. Returns -1 when there is
 * nothing to pin (empty list). Truth table: ChatPinTargetTest.
 */
internal fun clampPinTarget(index: Int, totalItemsCount: Int): Int {
    if (totalItemsCount <= 0) return -1
    return index.coerceIn(0, totalItemsCount - 1)
}

private suspend fun LazyListState.pinLastItemEnd(index: Int) {
    if (index < 0) return
    // Clamp: the follow LaunchedEffect clears its latch flags BEFORE this
    // call, so a throw here strands the viewport with no latch. Pinning the
    // current end on layout lag keeps follow alive; failures log and the
    // LaunchedEffect survives.
    val target = clampPinTarget(index, layoutInfo.totalItemsCount)
    if (target < 0) return
    try {
        scrollToItem(target)
        val info = layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == target } ?: return
        if (item.index != info.visibleItemsInfo.lastOrNull()?.index) return
        val overflow = item.offset + item.size - info.viewportEndOffset
        if (overflow > 0) scrollBy(overflow.toFloat())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "Chat: pinLastItemEnd failed for target %d", target)
    }
}

/**
 * 48-01: file-level wrapper so the pill's fade resolves to the top-level
 * AnimatedVisibility (a direct call inside ChatScreen's Column would bind
 * the ColumnScope overload and fail to compile).
 */
@Composable
private fun JumpToLatestPillOverlay(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(150)),
        modifier = modifier
    ) {
        JumpToLatestPill(onClick = onClick)
    }
}

/**
 * 48-01 (PERF-15, 48-UI-SPEC §2): "Jump to latest" affordance — the ONLY new
 * composable in this phase. Coral pill (36dp visual) centered in a 48dp touch
 * target; announces politely at most once per latch set (content unchanged
 * while visible, so no re-announcement per streaming token).
 */
@Composable
private fun JumpToLatestPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val jumpCd = stringResource(R.string.cd_jump_latest)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = jumpCd
                liveRegion = LiveRegionMode.Polite
            }
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = Color(0xFFD97757),
            contentColor = Color(0xFF1C1C1C),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .height(36.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.latest),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * CHAT-02/04/05: Inline model selector bar.
 * Replaces the TopAppBar — sits above the messages column, opens a ModalBottomSheet
 * when tapped. Shows the selected model name, a Local/Net pill, and the traffic light.
 * Also exposes a hamburger menu button on the left to open the drawer.
 */
@Composable
private fun InlineModelSelectorBar(
    selectedModelName: String?,
    isLocal: Boolean,
    isLoading: Boolean,
    loadingModelName: String,
    trafficLight: TrafficLightState,
    onClick: () -> Unit,
    onOpenDrawer: () -> Unit,
    // Thinking toggle (reasoning-capable models only): brain icon
    // switch left of the traffic light. Hidden otherwise — no dead
    // affordances.
    thinkingEnabled: Boolean = false,
    supportsThinking: Boolean = false,
    onToggleThinking: () -> Unit = {},
) {
    val pillColor = if (isLocal) Color(0xFF4CAF50) else Color(0xFF2196F3)
    val pillText = if (isLocal) "Local" else "Net"
    val lightColor = when {
        isLoading -> Color(0xFFFFC107)
        trafficLight == TrafficLightState.GREEN -> Color(0xFF4CAF50)
        trafficLight == TrafficLightState.YELLOW -> Color(0xFFFF9800)
        trafficLight == TrafficLightState.RED -> Color(0xFFF44336)
        else -> Color(0xFF666666)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        color = Color.Transparent
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    Icons.Filled.Menu,
                    contentDescription = stringResource(R.string.cd_open_drawer),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                color = Color.Transparent
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selectedModelName != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = pillColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = pillText,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = pillColor,
                                maxLines = 1
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = selectedModelName ?: stringResource(R.string.select_model),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    if (supportsThinking) {
                        IconButton(
                            onClick = onToggleThinking,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                Icons.Filled.Psychology,
                                contentDescription = stringResource(R.string.cd_toggle_thinking),
                                tint = if (thinkingEnabled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(2.dp))
                    }
                    Icon(
                        Icons.Filled.Circle,
                        contentDescription = stringResource(R.string.cd_connection_status),
                        tint = lightColor,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.cd_select_model),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Unified turn status row (quick-turn-status): the single transient row in
 * the bottomBar slot above the input bar — model loading > tool >
 * fetch/search > streaming gap. Same slot visuals as the rows it replaces (16dp ring + 8dp gap +
 * 14sp text, single-line ellipsis so long queries/hosts can't push the
 * input bar, trailing 8dp spacer when non-null; null renders nothing).
 * Single semantics contentDescription per resolved state. Copy reuse:
 * tool text passes through, fan-out keeps reading_multi copy, single keeps
 * reading_page copy, search uses the indeterminate searching copy (no
 * counts), the gap reuses the old in-list thinking-row copy (thinking_ellipsis).
 */
/**
 * Model-load error banner: rounded error card (icon + message + close)
 * that dismisses itself after [AUTO_DISMISS_MS]. Replaces the stuck
 * inline Snackbar — load errors (notably low-memory) must not linger
 * over the conversation.
 */
private const val ERROR_BANNER_AUTO_DISMISS_MS = 5_000L

@Composable
private fun AutoDismissErrorBanner(
    text: String,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(text) {
        kotlinx.coroutines.delay(ERROR_BANNER_AUTO_DISMISS_MS)
        onDismiss()
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_close),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun TurnStatusRow(status: TurnStatus?) {
    if (status == null) return
    val text = when (status) {
        is TurnStatus.Tool -> status.text
        is TurnStatus.FetchFanout -> stringResource(R.string.reading_multi_fmt, status.done, status.total)
        TurnStatus.FetchSingle -> stringResource(R.string.reading_page)
        TurnStatus.Searching -> stringResource(R.string.searching)
        TurnStatus.ThinkingGap -> stringResource(R.string.thinking_ellipsis)
        is TurnStatus.LoadingModel -> if (status.firstTime) {
            stringResource(R.string.loading_model_first, status.modelName.substringAfterLast("/"))
        } else {
            stringResource(R.string.loading_model, status.modelName.substringAfterLast("/"))
        }
    }
    val statusCd = when (status) {
        is TurnStatus.Tool -> stringResource(R.string.cd_running_tool, status.text)
        is TurnStatus.FetchFanout -> pluralStringResource(R.plurals.reading_multi_cd_fmt, status.total, status.done, status.total)
        TurnStatus.FetchSingle -> stringResource(R.string.reading_page_cd)
        TurnStatus.Searching -> stringResource(R.string.searching_cd)
        TurnStatus.ThinkingGap -> stringResource(R.string.cd_thinking_generating)
        is TurnStatus.LoadingModel -> stringResource(
            if (status.firstTime) R.string.loading_model_first_cd else R.string.loading_model_cd
        )
        is TurnStatus.LoadingModel -> stringResource(R.string.loading_model_cd)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .semantics {
                contentDescription = statusCd
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    Spacer(Modifier.height(8.dp))
}
