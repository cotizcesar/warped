package com.warped.ui.selector

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warped.data.local.download.DownloadState
import com.warped.data.local.inference.MemoryChecker
import com.warped.domain.model.Endpoint
import com.warped.domain.model.LocalModel
import com.warped.domain.model.ProviderType
import com.warped.ui.components.WarpedAlertDialog
import com.warped.ui.endpoints.components.EndpointForm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedSelectorScreen(
    viewModel: UnifiedSelectorViewModel = hiltViewModel(),
    onNavigateToChat: () -> Unit = {},
    onOpenHuggingFace: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showMemoryWarning by remember { mutableStateOf<LocalModel?>(null) }
    val isEndpointFormOpen = uiState.isEndpointFormVisible || uiState.isEditingEndpoint

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importModel(it) }
    }

    if (showMemoryWarning != null) {
        WarpedAlertDialog(
            onDismissRequest = { showMemoryWarning = null },
            title = { Text("Memory Warning") },
            text = {
                val model = showMemoryWarning!!
                val context = LocalContext.current
                val checker = MemoryChecker(context)
                val memInfo = checker.getMemoryInfo()
                val neededMB = model.sizeBytes / (1024 * 1024)
                val availableMB = memInfo.availableBytes / (1024 * 1024)
                Text("This model needs ~$neededMB MB, your device has $availableMB MB available. Loading may cause instability.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.connectLocal(showMemoryWarning!!)
                    showMemoryWarning = null
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showMemoryWarning = null }) { Text("Cancel") }
            }
        )
    }

    if (uiState.showAddWizard) {
        WarpedAlertDialog(
            onDismissRequest = { viewModel.dismissAddWizard() },
            title = { Text("Add Model", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { viewModel.dismissAddWizard(); onOpenHuggingFace() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Search, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Download from Hugging Face", style = MaterialTheme.typography.bodyLarge)
                            Text("Browse and download LiteRT-LM models", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider()
                    TextButton(onClick = { viewModel.dismissAddWizard(); filePickerLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Storage, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Import Model File", style = MaterialTheme.typography.bodyLarge)
                            Text("Import a .litertlm file from your device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider()
                    TextButton(onClick = { viewModel.dismissAddWizard(); viewModel.showEndpointForm() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Dns, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Connect to an API", style = MaterialTheme.typography.bodyLarge)
                            Text("Add a remote LM Studio server", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { viewModel.dismissAddWizard() }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEndpointFormOpen) "Connect to an API" else "Models & Endpoints") },
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
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isEndpointFormOpen) {
                FloatingActionButton(
                    onClick = { viewModel.showAddWizard() },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Filled.Add, "Add", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
                }
            }
        }
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
                    availableModels = emptyList(),
                    availableModelsData = emptyList(),
                    isFetchingModels = false,
                    onFetchModels = {}
                )
            }
        } else if (uiState.localModels.isEmpty() && uiState.endpoints.isEmpty() && uiState.activeDownloads.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No models or endpoints yet", style = MaterialTheme.typography.bodyLarge, color = Color(0xFF9CA3AF))
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.showAddWizard() },
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("Add Model") }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.activeDownloads.isNotEmpty()) {
                    item(key = "downloads-header") {
                        Text("Active Downloads", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
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
                        Text("Local Models", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${uiState.localModels.size}", style = MaterialTheme.typography.labelMedium, color = Color(0xFF9CA3AF))
                    }
                }

                if (uiState.localModels.isEmpty()) {
                    item(key = "local-empty") {
                        Text("No local models downloaded", style = MaterialTheme.typography.bodySmall, color = Color(0xFF6B7280), modifier = Modifier.padding(vertical = 8.dp))
                    }
                }

                items(uiState.localModels, key = { "local-${it.id}" }) { model ->
                    LocalModelSelectorCard(
                        model = model,
                        isConnected = uiState.connectedLocalModelId == model.filePath && uiState.isLocalConnected,
                        isConnecting = uiState.isConnecting && uiState.connectingModelName == model.name,
                        isAnotherConnected = uiState.isLocalConnected && uiState.connectedLocalModelId != model.filePath,
                        onConnect = {
                            if (viewModel.shouldWarnAboutMemory(model.sizeBytes)) {
                                showMemoryWarning = model
                            } else {
                                viewModel.connectLocal(model)
                            }
                        },
                        onDisconnect = { viewModel.disconnectLocal() },
                        onDelete = { viewModel.deleteModel(model) }
                    )
                }

                if (uiState.endpoints.isNotEmpty()) {
                    item(key = "remote-header") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Network Endpoints", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${uiState.endpoints.size}", style = MaterialTheme.typography.labelMedium, color = Color(0xFF9CA3AF))
                        }
                    }

                    items(uiState.endpoints, key = { "endpoint-${it.id}" }) { endpoint ->
                        EndpointSelectorCard(
                            endpoint = endpoint,
                            isSelected = uiState.selectedRemoteEndpointId == endpoint.id,
                            isFetchingModels = uiState.isFetchingModels && uiState.fetchingEndpointId == endpoint.id,
                            fetchedModels = uiState.endpointModels[endpoint.id] ?: emptyList(),
                            fetchError = uiState.endpointModelErrors[endpoint.id],
                            onUseInChat = {
                                viewModel.selectRemote(endpoint)
                                onNavigateToChat()
                            },
                            onFetchModels = { viewModel.fetchEndpointModels(endpoint) },
                            onEdit = { viewModel.editEndpoint(endpoint) },
                            onDelete = { viewModel.deleteEndpoint(endpoint) }
                        )
                    }
                }
            }
        }

        if (uiState.error != null) {
            Snackbar(
                modifier = Modifier.padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.clearError() }) { Text("Dismiss") }
                }
            ) { Text(uiState.error ?: "") }
        }
    }
}

@Composable
private fun LocalModelSelectorCard(
    model: LocalModel,
    isConnected: Boolean,
    isConnecting: Boolean,
    isAnotherConnected: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        WarpedAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete model") },
            text = { Text("Delete ${model.name} (${formatFileSize(model.sizeBytes)}) from the device?") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
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
                if (model.capabilities.vision || model.capabilities.reasoning || model.capabilities.tools || model.capabilities.audio) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (model.capabilities.vision) CapabilityBadge("Vision", Color(0xFF9C27B0))
                        if (model.capabilities.audio) CapabilityBadge("Audio", Color(0xFF4CAF50))
                        if (model.capabilities.reasoning) CapabilityBadge("Thinking", Color(0xFFFF9800))
                        if (model.capabilities.tools) CapabilityBadge("Tools", Color(0xFF2196F3))
                    }
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
                    enabled = !isAnotherConnected || isConnected
                )
            }

            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Filled.Delete, "Delete", tint = Color(0xFF6B7280), modifier = Modifier.size(18.dp))
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
    isFetchingModels: Boolean,
    fetchedModels: List<com.warped.domain.model.ModelInfo>,
    fetchError: String?,
    onUseInChat: () -> Unit,
    onFetchModels: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        WarpedAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete endpoint") },
            text = { Text("Delete ${endpoint.name} (${endpoint.apiType.name})? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
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
                    Icon(Icons.Filled.Edit, "Edit", tint = Color(0xFF9CA3AF), modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    if (endpoint.modelId != null) onUseInChat()
                    else { expanded = !expanded; if (!expanded && fetchedModels.isEmpty()) onFetchModels() }
                },
                enabled = endpoint.modelId != null || fetchedModels.isNotEmpty() || !isFetchingModels,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    if (endpoint.modelId != null) "Use in chat" else "Browse models",
                    color = Color.White
                )
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                if (isFetchingModels) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp).align(Alignment.CenterHorizontally), strokeWidth = 2.dp)
                } else if (fetchError != null) {
                    Text(fetchError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onFetchModels) { Text("Retry") }
                } else if (fetchedModels.isEmpty() && endpoint.modelId == null) {
                    TextButton(onClick = onFetchModels) { Text("Fetch models") }
                } else {
                    fetchedModels.forEach { modelInfo ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                modelInfo.name.ifBlank { modelInfo.id },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                // Handled by Parent UI (uses active Endpoint)
                            }) {
                                Text("Select", fontSize = 12.sp)
                            }
                        }
                    }
                }
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
private fun CapabilityBadge(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
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
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
            } else if (download.isPaused) {
                Text("Paused · ${formatFileSize(download.downloadedBytes)} / ${formatFileSize(download.totalBytes)}",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF9800))
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onDeleteIncomplete) { Text("Delete") }
            } else {
                Text("Interrupted · ${formatFileSize(download.downloadedBytes)} downloaded",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onDeleteIncomplete) { Text("Delete partial file") }
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
