package com.warped.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.collections.immutable.toPersistentList
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warped.R
import com.warped.data.local.inference.BackendType
import com.warped.domain.model.ProviderType
import com.warped.domain.model.Role
import kotlinx.coroutines.launch
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
    conversationId: Long = 0L,
    newChat: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    var attachedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var audioBytes by remember { mutableStateOf<ByteArray?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }

    LaunchedEffect(conversationId) {
        if (conversationId > 0) viewModel.selectConversation(conversationId)
    }
    LaunchedEffect(Unit) {
        if (conversationId == 0L) {
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

    // Memory warning dialog
    if (uiState.memoryWarningModel != null) {
        val model = uiState.memoryWarningModel!!
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
        val effectiveModelId = uiState.selectedLocalModelId ?: uiState.selectedRemoteModelId
        val effectiveProvider = if (uiState.selectedLocalModelId != null) ProviderType.LITE_RT_LM
            else uiState.selectedRemoteProvider
        val local = uiState.localModels.firstOrNull { it.filePath == effectiveModelId }
        val messages = remember(uiState.messages) { uiState.messages.toPersistentList() }
        val localModels = remember(uiState.localModels) { uiState.localModels.toPersistentList() }
        val endpoints = remember(uiState.endpoints) { uiState.endpoints.toPersistentList() }
        val endpoint = uiState.endpoints.firstOrNull {
            it.modelId == effectiveModelId && it.apiType == effectiveProvider
        }
        when {
            local != null -> local.name
            endpoint != null -> effectiveModelId?.substringAfterLast("/") ?: effectiveModelId
            else -> null
        }
    }

    // Auto-scroll to bottom during streaming
    LaunchedEffect(uiState.isStreaming, uiState.streamingContent.length) {
        if (uiState.isStreaming) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    // Snap to bottom when a new message is added
    LaunchedEffect(uiState.messages.size) {
        if (!uiState.isStreaming) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = { /* CHAT-02: removed TopAppBar — model picker is now inline above messages */
            // Drawer remains reachable via swipe (ModalNavigationDrawer around the Scaffold)
            // and the parent NavHost provides the drawer gesture.
        },
        bottomBar = {
            ChatInputBar(
                text = uiState.inputText,
                isGenerating = uiState.isStreaming,
                canSend = (uiState.selectedLocalModelId ?: uiState.selectedRemoteModelId) != null,
                onTextChange = { viewModel.updateInput(it) },
                onSend = {
                    viewModel.sendMessage(uiState.inputText, attachedImages, audioBytes)
                    attachedImages = emptyList()
                    audioBytes = null
                },
                onStop = { viewModel.stopGeneration() },
                reasoningEnabled = uiState.enableThinking,
                onToggleReasoning = { viewModel.toggleThinking() },
                modelHasReasoning = uiState.supportsThinking,
                onAddImage = { imagePickerLauncher.launch("image/*") },
                attachedImages = attachedImages,
                onRemoveImage = { i -> attachedImages = attachedImages.filterIndexed { idx, _ -> idx != i } },
                modelHasAudio = uiState.localModels.firstOrNull { it.filePath == uiState.selectedLocalModelId }?.capabilities?.audio == true,
                onAudioRecorded = { bytes -> audioBytes = bytes },
                onAudioRecordingChanged = { isRecording = it }
            )
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
            val trafficLight by remember(uiState) {
                derivedStateOf { uiState.trafficLightState() }
            }
            InlineModelSelectorBar(
                selectedModelName = selectedModelName,
                isLocal = uiState.selectedLocalModelId != null,
                isLoading = uiState.isLoadingModel,
                loadingModelName = uiState.loadingModelName,
                trafficLight = trafficLight,
                onClick = {
                    showModelPicker = true
                    viewModel.fetchAllEndpointModels()
                },
                onOpenDrawer = onOpenDrawer
            )

            // CHAT-07: Model loading indicator
            if (uiState.isLoadingModel) {
                ModelLoadingIndicator(loadingModelName = uiState.loadingModelName)
            }

            if (uiState.messages.isEmpty() && uiState.streamingContent.isEmpty()) {
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
                            contentDescription = "Warped",
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        uiState.messages.forEach { message ->
                            MessageBubble(message = message, codeTheme = uiState.codeTheme, codeFontScale = uiState.codeFontScale)
                        }
                        if (uiState.streamingContent.isNotEmpty() || uiState.streamingReasoning.isNotEmpty()) {
                            MessageBubble(
                                message = com.warped.domain.model.ChatMessage(
                                    role = Role.ASSISTANT,
                                    content = uiState.streamingContent,
                                    reasoning = uiState.streamingReasoning.ifEmpty { null }
                                ),
                                isStreaming = true,
                                codeTheme = uiState.codeTheme,
                                codeFontScale = uiState.codeFontScale
                            )
                        } else if (uiState.isStreaming) {
                            val statusText = uiState.toolCallActive?.let { tool ->
                                "Using ${tool.replace("_", " ")}..."
                            } ?: "Thinking..."
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF545450)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    statusText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF545450)
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
                }
            }

            if (uiState.modelLoadError != null) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = { TextButton(onClick = { viewModel.clearModelLoadError() }) { Text("Dismiss") } }
                ) { Text(uiState.modelLoadError ?: "") }
            }

            if (uiState.error != null) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = { TextButton(onClick = { viewModel.clearError() }) { Text("Dismiss") } }
                ) {
                    Text(
                        when (val error = uiState.error) {
                            is ChatError.Network -> error.message
                            is ChatError.Server -> error.message
                            is ChatError.Auth -> error.message
                            is ChatError.NoModelSelected -> stringResource(R.string.no_model_selected)
                            is ChatError.DownloadModelFirst -> stringResource(R.string.download_model_first)
                            is ChatError.ConnectionLost -> "Connection lost. Tap to retry."
                            is ChatError.ModelUnavailable -> "This model is no longer available. Please select a different model or re-download it."
                            is ChatError.Unknown -> error.message
                            null -> ""
                        }
                    )
                }
            }

            // Model unavailable banner
            if (uiState.modelUnavailable) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    action = {
                        TextButton(onClick = { viewModel.dismissModelUnavailable() }) { Text("Dismiss") }
                    }
                ) {
                    Text("The model for this conversation is no longer installed. Select a different model or re-download it.")
                }
            }

            // Model switch confirmation dialog
            if (uiState.pendingModelSwitch != null) {
                WarpedAlertDialog(
                    onDismissRequest = { viewModel.cancelModelSwitch() },
                    title = { Text("New model selected") },
                    text = {
                        Text(
                            "Switching models mid-conversation is not allowed. " +
                                "A new chat will be created with the selected model.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.confirmModelSwitch() }) {
                            Text("New Chat")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.cancelModelSwitch() }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }

        // CHAT-04/05: ModalBottomSheet model picker
        ModelSelectorSheet(
            visible = showModelPicker,
            selectedModelId = uiState.selectedLocalModelId ?: uiState.selectedRemoteModelId,
            selectedProvider = if (uiState.selectedLocalModelId != null) ProviderType.LITE_RT_LM else uiState.selectedRemoteProvider,
            localModels = uiState.localModels,
            endpoints = uiState.endpoints,
            endpointModels = uiState.endpointModels,
            onDismiss = { showModelPicker = false },
            onModelSelected = { modelId, providerType, endpointId ->
                viewModel.launchModelSelection(modelId, providerType, endpointId)
            }
        )
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
                    contentDescription = "Open drawer",
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
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = pillColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = pillText,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = pillColor
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = selectedModelName ?: stringResource(R.string.select_model),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.Filled.Circle,
                        contentDescription = "Connection status",
                        tint = lightColor,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Select model",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
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
            text = "Cargando ${loadingModelName.substringAfterLast("/")}…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
