package com.warped.ui.selector

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.warped.R
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warped.data.local.download.DownloadState
import com.warped.data.local.inference.MemoryChecker
import com.warped.domain.model.Endpoint
import com.warped.domain.model.LocalModel
import com.warped.domain.model.ModelCapabilities
import com.warped.domain.model.ProviderType
import com.warped.ui.components.WarpedAlertDialog
import com.warped.ui.components.ModelParamsDialog
import com.warped.ui.endpoints.components.EndpointForm
import com.warped.domain.model.GenerationParameters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedSelectorScreen(
    viewModel: UnifiedSelectorViewModel = hiltViewModel(),
    onNavigateToChat: () -> Unit = {},
    onOpenHuggingFace: () -> Unit = {},
    onNavigateToPresets: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showMemoryWarning by remember { mutableStateOf<LocalModel?>(null) }
    var editingModel by remember { mutableStateOf<LocalModel?>(null) }
    val isEndpointFormOpen = uiState.isEndpointFormVisible || uiState.isEditingEndpoint

    if (showMemoryWarning != null) {
        WarpedAlertDialog(
            onDismissRequest = { showMemoryWarning = null },
            title = { Text(stringResource(R.string.memory_warning_title)) },
            text = {
                val model = showMemoryWarning!!
                val context = LocalContext.current
                val checker = MemoryChecker(context)
                val memInfo = checker.getMemoryInfo()
                val neededMB = model.sizeBytes / (1024 * 1024)
                val availableMB = memInfo.availableBytes / (1024 * 1024)
                Text(stringResource(R.string.models_memory_msg_fmt, neededMB, availableMB))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.connectLocal(showMemoryWarning!!)
                    showMemoryWarning = null
                }) { Text(stringResource(R.string.continue_text)) }
            },
            dismissButton = {
                TextButton(onClick = { showMemoryWarning = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (editingModel != null) {
        ModelParamsDialog(
            title = stringResource(R.string.params_dialog_title_fmt, editingModel!!.name),
            initial = editingModel!!.parameters,
            onDismiss = { editingModel = null },
            onSave = { newParams ->
                viewModel.updateModelParameters(editingModel!!.id, newParams)
                editingModel = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEndpointFormOpen) stringResource(R.string.connect_api_title) else stringResource(R.string.models_title_endpoints)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isEndpointFormOpen) {
                                if (uiState.isEditingEndpoint) viewModel.cancelEndpointEdit()
                                else viewModel.dismissEndpointForm()
                            } else onBack()
                        }
                    ) {
                        Icon(
                            if (isEndpointFormOpen) Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
    ) { padding ->
        if (isEndpointFormOpen) {
            Column(modifier = Modifier.padding(padding)) {
                EndpointForm(
                    name = uiState.formName,
                    url = uiState.formUrl,
                    apiType = uiState.formApiType,
                    lmStudioMode = uiState.formLmStudioMode,
                    modelId = uiState.formModelId,
                    apiKey = uiState.formApiKey,
                    hasSavedKey = uiState.hasSavedApiKey,
                    onFieldChange = { field, value -> viewModel.updateEndpointField(field, value) },
                    onSave = { if (uiState.isEditingEndpoint) viewModel.saveEndpointEdit() else viewModel.saveEndpoint() },
                    onDismiss = { viewModel.dismissEndpointForm() },
                    availableModels = uiState.availableEndpointModels,
                    availableModelsData = uiState.availableEndpointModelsData,
                    isFetchingModels = uiState.isFetchingEndpointModels,
                    onFetchModels = { viewModel.fetchEndpointModels() }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.activeDownloads.isNotEmpty()) {
                    item(key = "downloads-header") {
                        Text(stringResource(R.string.active_downloads), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    }
                    items(uiState.activeDownloads, key = { "dl-${it.modelId}" }) { download ->
                        DownloadCard(
                            download = download,
                            onCancel = { viewModel.cancelDownload(download.modelId) },
                            onDeleteIncomplete = { viewModel.deleteIncompleteDownload(download) }
                        )
                    }
                }

                item(key = "local-header") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.local_models), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Text("${uiState.localModels.size}", style = MaterialTheme.typography.labelMedium, color = Color(0xFF9CA3AF))
                        }
                        IconButton(onClick = onOpenHuggingFace) {
                            Icon(Icons.Filled.Add, stringResource(R.string.cd_add_model), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        }
                    }
                }

                if (uiState.localModels.isEmpty()) {
                    item(key = "local-empty") {
                        Text(stringResource(R.string.selector_no_local), style = MaterialTheme.typography.bodySmall, color = Color(0xFF6B7280), modifier = Modifier.padding(vertical = 8.dp))
                    }
                }

                items(uiState.localModels, key = { "local-${it.id}" }) { model ->
                    LocalModelSelectorCard(
                        model = model,
                        capabilities = viewModel.effectiveCapabilities(model),
                        isConnected = uiState.connectedLocalModelId == model.filePath && uiState.isLocalConnected,
                        isConnecting = uiState.isConnecting && uiState.connectingModelName == model.name,
                        // Seamless switch: rows stay interactive while another model is
                        // connected — connectLocal unloads the previous engine itself.
                        // Locked only while a connection is in flight (avoids racing
                        // two engine inits); the connected row stays tappable to
                        // disconnect.
                        connectLocked = uiState.isConnecting,
                        onConnect = {
                            if (viewModel.shouldWarnAboutMemory(model.sizeBytes)) {
                                showMemoryWarning = model
                            } else {
                                viewModel.connectLocal(model)
                            }
                        },
                        onDisconnect = { viewModel.disconnectLocal() },
                        onDelete = { viewModel.deleteModel(model) },
                        onEditParams = { editingModel = model }
                    )
                }

                item(key = "remote-header") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.endpoints_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Text("${uiState.endpoints.size}", style = MaterialTheme.typography.labelMedium, color = Color(0xFF9CA3AF))
                        }
                        IconButton(onClick = { viewModel.showEndpointForm() }) {
                            Icon(Icons.Filled.Add, stringResource(R.string.cd_add_endpoint), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        }
                    }
                }

                    items(uiState.endpoints, key = { "endpoint-${it.id}" }) { endpoint ->
                        EndpointSelectorCard(
                            endpoint = endpoint,
                            isSelected = uiState.selectedRemoteEndpointId == endpoint.id,
                            onUseInChat = {
                                viewModel.selectRemote(endpoint)
                                onNavigateToChat()
                            },
                            onEdit = { viewModel.editEndpoint(endpoint) },
                            onDelete = { viewModel.deleteEndpoint(endpoint) }
                        )
                    }
            }
        }

        if (uiState.error != null) {
            Snackbar(
                modifier = Modifier.padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.clearError() }) { Text(stringResource(R.string.dismiss)) }
                }
            ) { Text(uiState.error ?: "") }
        }
    }
}

@Composable
private fun LocalModelSelectorCard(
    model: LocalModel,
    capabilities: ModelCapabilities,
    isConnected: Boolean,
    isConnecting: Boolean,
    connectLocked: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onDelete: () -> Unit,
    onEditParams: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        WarpedAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_model_title)) },
            text = { Text(stringResource(R.string.delete_model_message, model.name, formatFileSize(model.sizeBytes))) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B29)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConnectionDot(isConnected = isConnected, isLoading = isConnecting)

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(model.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModelMetaChip(formatFileSize(model.sizeBytes))
                    if (model.quantization.isNotBlank() && model.quantization != "N/A") {
                        ModelMetaChip(model.quantization)
                    }
                    if (model.parameterCount.isNotBlank() && model.parameterCount != "Unknown") {
                        ModelMetaChip(model.parameterCount)
                    }
                }
                if (capabilities.vision || capabilities.reasoning || capabilities.tools || capabilities.audio) {
                    Spacer(Modifier.height(4.dp))
                    com.warped.ui.components.CapabilityIconRow(capabilities)
                }
            }

            if (isConnecting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Switch(
                    checked = isConnected,
                    onCheckedChange = { checked ->
                        if (checked) onConnect() else onDisconnect()
                    },
                    enabled = !connectLocked || isConnected
                )
            }

            IconButton(onClick = onEditParams) {
                Icon(Icons.Filled.Tune, stringResource(R.string.cd_parameters), tint = Color(0xFF9CA3AF), modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = Color(0xFF6B7280), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ConnectionDot(isConnected: Boolean, isLoading: Boolean) {
    val color = when {
        isLoading -> Color(0xFFFFC107)
        isConnected -> Color(0xFF4CAF50)
        else -> Color(0xFF6B7280)
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun EndpointSelectorCard(
    endpoint: Endpoint,
    isSelected: Boolean,
    onUseInChat: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        WarpedAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_endpoint_title)) },
            text = { Text(stringResource(R.string.delete_endpoint_message, endpoint.name, endpoint.apiType.name)) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B29)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSelected) {
                    Box(
                        modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF4CAF50))
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(endpoint.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ModelMetaChip(endpoint.apiType.name)
                        if (endpoint.modelId != null) ModelMetaChip("ID: ${endpoint.modelId}")
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, stringResource(R.string.edit), tint = Color(0xFF9CA3AF), modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }

            // Model fetch and selection happen in the endpoint form (Add/Edit).
            // The selector card only shows what's already saved.
            if (endpoint.modelId != null) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onUseInChat,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.use_in_chat), color = Color.White)
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.selector_no_model),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9CA3AF)
                )
            }
        }
    }
}

@Composable
private fun ModelMetaChip(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color.White.copy(alpha = 0.08f)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF9CA3AF),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun DownloadCard(
    download: DownloadState,
    onCancel: () -> Unit,
    onDeleteIncomplete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B29)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(download.fileName.substringAfterLast("/"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            if (download.isDownloading) {
                LinearProgressIndicator(progress = { download.progress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(
                    "${(download.progress * 100).toInt()}% · ${formatFileSize(download.downloadedBytes)} / ${formatFileSize(download.totalBytes)} · ${formatDownloadSpeed(download.speedBytesPerSecond)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            } else if (download.isPaused) {
                Text(stringResource(
                    R.string.dl_paused_fmt,
                    formatFileSize(download.downloadedBytes),
                    formatFileSize(download.totalBytes)
                ),
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF9800))
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onDeleteIncomplete) { Text(stringResource(R.string.delete)) }
            } else {
                Text(stringResource(R.string.dl_interrupted_fmt, formatFileSize(download.downloadedBytes)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onDeleteIncomplete) { Text(stringResource(R.string.delete_partial_file)) }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.2f GB".format(bytes.toDouble() / (1024L * 1024 * 1024))
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes.toDouble() / (1024 * 1024))
    bytes >= 1024 -> "%.1f KB".format(bytes.toDouble() / 1024)
    else -> "$bytes B"
}

private fun formatDownloadSpeed(bytesPerSecond: Long): String =
    if (bytesPerSecond > 0) "${formatFileSize(bytesPerSecond)}/s" else "--/s"
