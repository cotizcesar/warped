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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import com.warped.ui.chat.components.ChatInputBar
import com.warped.ui.chat.components.MessageBubble
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

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris -> attachedImages = uris }

    val selectedModelName = run {
        val local = uiState.localModels.firstOrNull { it.filePath == uiState.selectedModelId }
        val endpoint = uiState.endpoints.firstOrNull {
            it.modelId == uiState.selectedModelId && it.apiType == uiState.selectedProvider
        }
        when {
            local != null -> local.name
            endpoint != null -> uiState.selectedModelId?.substringAfterLast("/") ?: uiState.selectedModelId
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
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.selectedProvider != null && selectedModelName != null) {
                            val isLocal = uiState.selectedProvider == ProviderType.LOCAL ||
                                uiState.selectedProvider == ProviderType.LITE_RT_LM
                            val typePillColor = if (isLocal) Color(0xFF4CAF50) else Color(0xFF2196F3)
                            val typePillText = if (isLocal) "Local" else "Net"
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = typePillColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = typePillText,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = typePillColor
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = selectedModelName ?: stringResource(R.string.select_model),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        IconButton(onClick = onNavigateToSelector) {
                            Icon(
                                Icons.Filled.Circle,
                                contentDescription = "Select model",
                                modifier = Modifier.size(20.dp).padding(2.dp),
                                tint = if (uiState.isLocalModelLoaded) Color(0xFF4CAF50) else Color(0xFF6B7280)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    val lightState = uiState.trafficLightState()
                    val statusText = uiState.trafficLightStatusText()
                    val isLocal = uiState.selectedLocalModelId != null && uiState.isLocalModelLoaded
                    val isRemote = uiState.selectedRemoteModelId != null && uiState.selectedRemoteProvider != null
                    
                    var showStatusSnackbar by remember { mutableStateOf(false) }
                    val snackbarHostState = remember { SnackbarHostState() }

                    LaunchedEffect(showStatusSnackbar) {
                        if (showStatusSnackbar) {
                            snackbarHostState.showSnackbar(statusText, duration = SnackbarDuration.Short)
                            showStatusSnackbar = false
                        }
                    }

                    val statusColor = when (lightState) {
                        TrafficLightState.GREEN -> Color(0xFF4CAF50)
                        TrafficLightState.YELLOW -> Color(0xFFFF9800)
                        TrafficLightState.RED -> Color(0xFFF44336)
                        TrafficLightState.GRAY -> Color(0xFF666666)
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isLocal) {
                            Text("Local", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9CA3AF))
                            Spacer(Modifier.width(4.dp))
                        } else if (isRemote) {
                            Text("Remote", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9CA3AF))
                            Spacer(Modifier.width(4.dp))
                        }
                        IconButton(onClick = { showStatusSnackbar = true }) {
                            Icon(
                                Icons.Filled.Circle,
                                contentDescription = "Connection status",
                                tint = statusColor,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    SnackbarHost(hostState = snackbarHostState)
                }
            )
        },
        bottomBar = {
            ChatInputBar(
                text = uiState.inputText,
                isGenerating = uiState.isStreaming,
                canSend = uiState.selectedModelId != null,
                onTextChange = { viewModel.updateInput(it) },
                onSend = {
                    viewModel.sendMessage(uiState.inputText, attachedImages, audioBytes)
                    attachedImages = emptyList()
                    audioBytes = null
                },
                onStop = { viewModel.stopGeneration() },
                reasoningEnabled = uiState.reasoningEnabled,
                onToggleReasoning = { viewModel.toggleReasoning() },
                modelHasReasoning = uiState.localModels.firstOrNull { it.filePath == uiState.selectedModelId }?.capabilities?.reasoning != false,
                onAddImage = { imagePickerLauncher.launch("image/*") },
                attachedImages = attachedImages,
                onRemoveImage = { i -> attachedImages = attachedImages.filterIndexed { idx, _ -> idx != i } },
                modelHasAudio = uiState.localModels.firstOrNull { it.filePath == uiState.selectedModelId }?.capabilities?.audio == true,
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

            if (uiState.isLoadingModel) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Loading ${uiState.loadingModelName}...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
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
    }
}
