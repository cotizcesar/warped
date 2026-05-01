package com.warped.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warped.R
import com.warped.domain.model.ProviderType
import com.warped.domain.model.Role
import com.warped.ui.chat.components.ChatInputBar
import com.warped.ui.chat.components.MessageBubble

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit = {},
    conversationId: Long = 0L
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var modelDropdownExpanded by remember { mutableStateOf(false) }
    var attachedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }

    LaunchedEffect(conversationId) {
        if (conversationId > 0) viewModel.selectConversation(conversationId)
    }
    LaunchedEffect(Unit) {
        if (conversationId == 0L) {
            viewModel.loadLastConversation()
        }
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
            endpoint != null -> {
                val shortId = uiState.selectedModelId?.substringAfterLast("/") ?: uiState.selectedModelId
                "${endpoint.name} ($shortId)"
            }
            else -> null
        }
    }

    LaunchedEffect(uiState.streamingContent.length, uiState.messages.size) {
        if (uiState.messages.isNotEmpty() || uiState.streamingContent.isNotEmpty()) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            if (lastVisible >= totalItems - 3 || totalItems == 0) {
                listState.animateScrollToItem(maxOf(0, totalItems - 1))
            }
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    ExposedDropdownMenuBox(
                        expanded = modelDropdownExpanded,
                        onExpandedChange = { modelDropdownExpanded = it }
                    ) {
                        Row(
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .clickable { modelDropdownExpanded = true },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedModelName ?: stringResource(R.string.select_model),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Select model",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        ExposedDropdownMenu(
                            expanded = modelDropdownExpanded,
                            onDismissRequest = { modelDropdownExpanded = false }
                        ) {
                            if (uiState.localModels.isNotEmpty()) {
                                uiState.localModels.forEach { model ->
                                    DropdownMenuItem(
                                        text = { Text(model.name) },
                                        onClick = {
                                            viewModel.setSelectedModel(model.filePath, ProviderType.LOCAL)
                                            modelDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                            if (uiState.localModels.isNotEmpty() && uiState.endpoints.isNotEmpty()) {
                                HorizontalDivider()
                            }
                            uiState.endpoints.forEach { endpoint ->
                                val modelId = endpoint.modelId
                                if (modelId != null) {
                                    val label = "${endpoint.name} · ${modelId.substringAfterLast("/")}"
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            viewModel.setSelectedModel(modelId, endpoint.apiType)
                                            modelDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                            if (uiState.localModels.isEmpty() && uiState.endpoints.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.no_models_select_model)) },
                                    onClick = { modelDropdownExpanded = false },
                                    enabled = false
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
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
                    viewModel.sendMessage(uiState.inputText, attachedImages)
                    attachedImages = emptyList()
                },
                onStop = { viewModel.stopGeneration() },
                reasoningEnabled = uiState.reasoningEnabled,
                onToggleReasoning = { viewModel.toggleReasoning() },
                onAddImage = { imagePickerLauncher.launch("image/*") },
                attachedImages = attachedImages,
                onRemoveImage = { i -> attachedImages = attachedImages.filterIndexed { idx, _ -> idx != i } },
                localModels = uiState.localModels,
                endpoints = uiState.endpoints,
                selectedModelId = uiState.selectedModelId,
                onModelSelected = { modelId, provider ->
                    viewModel.setSelectedModel(modelId, provider)
                }
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

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    MessageBubble(message = message)
                }
                if (uiState.streamingContent.isNotEmpty()) {
                    item(key = "streaming") {
                        MessageBubble(
                            message = com.warped.domain.model.ChatMessage(
                                role = Role.ASSISTANT,
                                content = uiState.streamingContent
                            ),
                            isStreaming = true
                        )
                    }
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
                            is ChatError.Unknown -> error.message
                            null -> ""
                        }
                    )
                }
            }
        }
    }
}
