package com.warped.ui.chat

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.warped.ui.components.WarpedAlertDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit = {},
    onNavigateToSelector: () -> Unit = {},
    onNavigateToCatalog: () -> Unit = {},
    conversationId: Long = 0L,
    newChat: Boolean = false
) {
    // 48-01 (PERF-14): each region collects ONLY its sub-state flow — never
    // the monolith. Streaming tokens recompose the list; keystrokes recompose
    // only the input bar.
    val transcript by viewModel.transcriptState.collectAsStateWithLifecycle()
    val input by viewModel.inputState.collectAsStateWithLifecycle()
    val connection by viewModel.connectionState.collectAsStateWithLifecycle()
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
    var audioBytes by remember { mutableStateOf<ByteArray?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
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
    DisposableEffect(Unit) {
        onDispose {
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
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Memory warning dialog
    if (connection.memoryWarningModel != null) {
        val model = connection.memoryWarningModel!!
        val context = androidx.compose.ui.platform.LocalContext.current
        val info = com.warped.data.local.inference.MemoryChecker(context).getMemoryInfo()
        AlertDialog(
            onDismissRequest = { viewModel.dismissMemoryWarning() },
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

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Phase 53 (TOGGLE-01/SRC-02): one-shot ViewModel events (toggle
    // feedback, persist-failure notice) rendered as non-blocking Snackbars.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ChatEvent.Snackbar ->
                    snackbarHostState.showSnackbar(event.message, duration = SnackbarDuration.Short)
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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = { /* CHAT-02: removed TopAppBar — model picker is now inline above messages */
            // Drawer remains reachable via swipe (ModalNavigationDrawer around the Scaffold)
            // and the parent NavHost provides the drawer gesture.
        },
        bottomBar = {
            Column {
                // Unified turn status (quick-turn-status): ONE transient row
                // above the input bar — priority tool > fetch/search >
                // streaming gap. Never a transcript message, never
                // persisted; unmounts on completion/failure/Stop. Null
                // status renders nothing (and no spacer).
                val turnStatus = resolveTurnStatus(
                    toolCallActive = input.toolCallActive,
                    isFetchingWeb = input.isFetchingWeb,
                    progress = input.webFetchProgress,
                    isStreamingGap = isStreamingGap,
                )
                TurnStatusRow(status = turnStatus)
                ChatInputBar(
                text = input.inputText,
                isGenerating = input.isGenerating,
                canSend = (connection.selectedLocalModelId ?: connection.selectedRemoteModelId) != null,
                onTextChange = { viewModel.updateInput(it) },
                onSend = {
                    snapToBottomOnNextContent = true
                    hasNewContentBelow = false
                    viewModel.sendMessage(input.inputText, attachedImages, audioBytes)
                    attachedImages = emptyList()
                    audioBytes = null
                },
                onStop = { viewModel.stopGeneration() },
                reasoningEnabled = input.enableThinking,
                onToggleReasoning = { viewModel.toggleThinking() },
                modelHasReasoning = input.supportsThinking,
                // Verified-only capabilities: image/audio buttons hide when
                // the selected local model lacks the modality. Remote or
                // unselected → fail open (null → true).
                modelHasVision = viewModel.verifiedLocalCapabilities(connection.selectedLocalModelId)?.vision
                    ?: true,
                onAddImage = { imagePickerLauncher.launch("image/*") },
                attachedImages = attachedImages,
                onRemoveImage = { i -> attachedImages = attachedImages.filterIndexed { idx, _ -> idx != i } },
                modelHasAudio = viewModel.verifiedLocalCapabilities(connection.selectedLocalModelId)?.audio
                    ?: true,
                onAudioRecorded = { bytes -> audioBytes = bytes },
                onAudioRecordingChanged = { isRecording = it },
            )
            }
        }
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
            )

            // CHAT-07: Model loading indicator
            if (connection.isLoadingModel) {
                ModelLoadingIndicator(loadingModelName = connection.loadingModelName)
            }

            if (isEmpty) {
                // Empty state
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
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
                Box(modifier = Modifier.weight(1f)) {
                    // 48-01 (PERF-15): keyed LazyColumn. Role.TOOL transcript rows
                    // are persisted ChatMessages with stable ids — keyed inline,
                    // zero extra work. Transient trailing rows use the constant
                    // ChatListKeys (never content hashes — T-48-01/T-48-02).
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(transcript.messages, key = { it.id }) { message ->
                            MessageBubble(
                                message = message,
                                codeTheme = connection.codeTheme,
                                codeFontScale = connection.codeFontScale,
                                isValidatedOnline = input.isValidatedOnline,
                                isFetchingWeb = input.isFetchingWeb,
                                isGenerating = input.isGenerating,
                                onRetry = { viewModel.retryGrounding(it) }
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
                    // Bottom fade gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color(0xFF1F1F1E)
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
                            .padding(end = 16.dp, bottom = 8.dp)
                    )
                }
            }

            if (connection.modelLoadError != null) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = { TextButton(onClick = { viewModel.clearModelLoadError() }) { Text(stringResource(R.string.dismiss)) } }
                ) { Text(connection.modelLoadError ?: "") }
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
    onOpenDrawer: () -> Unit
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
 * the bottomBar slot above the input bar — tool > fetch/search > streaming
 * gap. Same slot visuals as the rows it replaces (16dp ring + 8dp gap +
 * 14sp text, single-line ellipsis so long queries/hosts can't push the
 * input bar, trailing 8dp spacer when non-null; null renders nothing).
 * Single semantics contentDescription per resolved state. Copy reuse:
 * tool text passes through, fan-out keeps reading_multi copy, single keeps
 * reading_page copy, search uses the indeterminate searching copy (no
 * counts), the gap reuses the old in-list thinking-row copy (thinking_ellipsis).
 */
@Composable
private fun TurnStatusRow(status: TurnStatus?) {
    if (status == null) return
    val text = when (status) {
        is TurnStatus.Tool -> status.text
        is TurnStatus.FetchFanout -> stringResource(R.string.reading_multi_fmt, status.done, status.total)
        TurnStatus.FetchSingle -> stringResource(R.string.reading_page)
        TurnStatus.Searching -> stringResource(R.string.searching)
        TurnStatus.ThinkingGap -> stringResource(R.string.thinking_ellipsis)
    }
    val statusCd = when (status) {
        is TurnStatus.Tool -> stringResource(R.string.cd_running_tool, status.text)
        is TurnStatus.FetchFanout -> pluralStringResource(R.plurals.reading_multi_cd_fmt, status.total, status.done, status.total)
        TurnStatus.FetchSingle -> stringResource(R.string.reading_page_cd)
        TurnStatus.Searching -> stringResource(R.string.searching_cd)
        TurnStatus.ThinkingGap -> stringResource(R.string.cd_thinking_generating)
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

/**
 * CHAT-07: "Cargando modelo" indicator shown in the chat while a local model is loading.
 */
@Composable
private fun ModelLoadingIndicator(loadingModelName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.loading_model, loadingModelName.substringAfterLast("/")),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
