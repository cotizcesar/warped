package com.warped.ui.models

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warped.data.local.download.DownloadState
import com.warped.data.local.inference.MemoryChecker
import com.warped.domain.model.Endpoint
import com.warped.domain.model.LocalModel
import com.warped.ui.endpoints.components.EndpointForm
import com.warped.ui.components.WarpedAlertDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    viewModel: ModelsViewModel = hiltViewModel(),
    onUseInChat: () -> Unit = {},
    onOpenHuggingFace: () -> Unit = {},
    onOpenDrawer: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showMemoryWarning by remember { mutableStateOf<LocalModel?>(null) }
    var showAddWizard by remember { mutableStateOf(false) }
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
                val context = androidx.compose.ui.platform.LocalContext.current
                val checker = MemoryChecker(context)
                val memInfo = checker.getMemoryInfo()
                val neededMB = model.sizeBytes / (1024 * 1024)
                val availableMB = memInfo.availableBytes / (1024 * 1024)
                Text(
                    "This model needs ~$neededMB MB, your device has $availableMB MB available. " +
                    "Loading may cause instability."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.useLocalModel(showMemoryWarning!!)
                    onUseInChat()
                    showMemoryWarning = null
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showMemoryWarning = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEndpointFormOpen) "Connect LM Studio" else "Models & Endpoints") },
                navigationIcon = {
                    if (isEndpointFormOpen) {
                        IconButton(
                            onClick = {
                                if (uiState.isEditingEndpoint) {
                                    viewModel.cancelEndpointEdit()
                                } else {
                                    viewModel.dismissEndpointForm()
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to models")
                        }
                    } else {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isEndpointFormOpen) {
                FloatingActionButton(
                    onClick = { showAddWizard = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Filled.Add, "Add", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
                }
            }
        }
    ) { padding ->
        if (showAddWizard) {
            WarpedAlertDialog(
                onDismissRequest = { showAddWizard = false },
                title = { Text("Add Model", style = MaterialTheme.typography.titleLarge) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = { showAddWizard = false; onOpenHuggingFace() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Search, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Download from Hugging Face", style = MaterialTheme.typography.bodyLarge)
                                Text("Browse and download LiteRT-LM models", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider()
                        TextButton(
                            onClick = { showAddWizard = false; filePickerLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Storage, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Import Model File", style = MaterialTheme.typography.bodyLarge)
                                Text("Load a .gguf or .litertlm model from your device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider()
                        TextButton(
                            onClick = { showAddWizard = false; viewModel.showEndpointForm() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Dns, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Connect LM Studio", style = MaterialTheme.typography.bodyLarge)
                                Text("Add a remote LM Studio server", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { showAddWizard = false }) { Text("Cancel") } }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isImporting) {
                LinearProgressIndicator(
                    progress = { uiState.importProgress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Importing model... ${(uiState.importProgress * 100).toInt()}%",
                    modifier = Modifier.padding(16.dp)
                )
            }

            if (uiState.isEndpointFormVisible) {
                EndpointForm(
                    name = uiState.formName,
                    url = uiState.formUrl,
                    apiType = uiState.formApiType,
                    lmStudioMode = uiState.formLmStudioMode,
                    modelId = uiState.formModelId,
                    apiKey = uiState.formApiKey,
                    hasSavedKey = uiState.hasSavedApiKey,
                    onFieldChange = { field, value -> viewModel.updateEndpointField(field, value) },
                    onSave = { viewModel.saveEndpoint() },
                    onDismiss = { viewModel.dismissEndpointForm() },
                    availableModels = uiState.availableEndpointModels,
                    availableModelsData = uiState.availableEndpointModelsData,
                    isFetchingModels = uiState.isFetchingEndpointModels,
                    onFetchModels = { viewModel.fetchEndpointModels() },
                )
            } else if (uiState.isEditingEndpoint) {
                EndpointForm(
                    name = uiState.formName,
                    url = uiState.formUrl,
                    apiType = uiState.formApiType,
                    lmStudioMode = uiState.formLmStudioMode,
                    modelId = uiState.formModelId,
                    apiKey = uiState.formApiKey,
                    hasSavedKey = uiState.hasSavedApiKey,
                    onFieldChange = { field, value -> viewModel.updateEndpointField(field, value) },
                    onSave = { viewModel.saveEndpointEdit() },
                    onDismiss = { viewModel.cancelEndpointEdit() },
                    availableModels = uiState.availableEndpointModels,
                    availableModelsData = uiState.availableEndpointModelsData,
                    isFetchingModels = uiState.isFetchingEndpointModels,
                    onFetchModels = { viewModel.fetchEndpointModels() },
                )
            } else if (uiState.models.isEmpty() && uiState.endpoints.isEmpty() && !uiState.isImporting && uiState.activeDownloads.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No models or endpoints yet", style = MaterialTheme.typography.bodyLarge, color = Color(0xFF9CA3AF))
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showAddWizard = true },
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("Add Model") }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.activeDownloads.isNotEmpty()) {
                        item {
                            Text(
                                "Active Downloads",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        items(uiState.activeDownloads, key = { "dl-${it.modelId}" }) { download ->
                            DownloadCard(
                                download = download,
                                onCancel = { viewModel.cancelDownload(download.modelId) },
                                onDeleteIncomplete = { viewModel.deleteIncompleteDownload(download) }
                            )
                        }
                    }
                    item {
                        Text(
                            "Local Models",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    items(uiState.models, key = { "local-${it.id}" }) { model ->
                        ModelCard(
                            model = model,
                            onLoad = {
                                if (viewModel.shouldWarnAboutMemory(model.sizeBytes, isGguf = false)) {
                                    showMemoryWarning = model
                                } else {
                                    viewModel.useLocalModel(model)
                                    onUseInChat()
                                }
                            },
                            onDelete = { viewModel.deleteModel(model) }
                        )
                    }
                    if (uiState.endpoints.isNotEmpty()) {
                        item {
                            Text(
                                "Network Endpoints",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        items(uiState.endpoints, key = { "endpoint-${it.id}" }) { endpoint ->
                            DeployedEndpointCard(
                                endpoint = endpoint,
                                onUseInChat = {
                                    viewModel.useEndpoint(endpoint)
                                    onUseInChat()
                                },
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
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Dismiss")
                        }
                    }
                ) { Text(uiState.error ?: "") }
            }
        }
    }
}

@Composable
fun ModelCard(
    model: LocalModel,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        WarpedAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete model") },
            text = { Text("Delete ${model.name} (${formatFileSize(model.sizeBytes)}) from the device?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    model.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                ModelFormatBadge(model.modelFormat)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModelMetaChip("${formatFileSize(model.sizeBytes)}")
                if (model.quantization.isNotBlank() && model.quantization != "N/A") {
                    ModelMetaChip(model.quantization)
                }
                if (model.parameterCount.isNotBlank() && model.parameterCount != "Unknown") {
                    ModelMetaChip(model.parameterCount)
                }
            }
            if (model.capabilities.vision || model.capabilities.reasoning || model.capabilities.tools || model.capabilities.audio) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (model.capabilities.vision) CapabilityBadge("Vision", Color(0xFF9C27B0))
                    if (model.capabilities.audio) CapabilityBadge("Audio", Color(0xFF4CAF50))
                    if (model.capabilities.reasoning) CapabilityBadge("Thinking", Color(0xFFFF9800))
                    if (model.capabilities.tools) CapabilityBadge("Tools", Color(0xFF2196F3))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onLoad,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97757)),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Use in chat", color = Color.White) }
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete")
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
private fun ModelFormatBadge(format: String) {
    val color = Color(0xFF4CAF50)
    val label = "LiteRT"
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
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
                LinearProgressIndicator(
                    progress = { download.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${(download.progress * 100).toInt()}% · ${formatFileSize(download.downloadedBytes)} / " +
                        "${formatFileSize(download.totalBytes)} · ${formatDownloadSpeed(download.speedBytesPerSecond)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
            } else if (download.isPaused) {
                Text(
                    "Paused · ${formatFileSize(download.downloadedBytes)} / ${formatFileSize(download.totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF9800)
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDeleteIncomplete) { Text("Delete") }
                }
            } else {
                Text(
                    "Interrupted · ${formatFileSize(download.downloadedBytes)} downloaded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                if (download.error != null) {
                    Text(
                        download.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onDeleteIncomplete) { Text("Delete partial file") }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024 * 1024 -> "%.2f GB".format(bytes.toDouble() / (1024L * 1024 * 1024))
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes.toDouble() / (1024 * 1024))
        bytes >= 1024 -> "%.1f KB".format(bytes.toDouble() / 1024)
        else -> "$bytes B"
    }
}

private fun formatDownloadSpeed(bytesPerSecond: Long): String {
    return if (bytesPerSecond > 0) {
        "${formatFileSize(bytesPerSecond)}/s"
    } else {
        "--/s"
    }
}

@Composable
private fun DeployedEndpointCard(
    endpoint: Endpoint,
    onUseInChat: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        WarpedAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete endpoint") },
            text = { Text("Delete ${endpoint.name} (${endpoint.apiType.name})? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
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
            Text(endpoint.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModelMetaChip(endpoint.apiType.name)
                if (endpoint.modelId != null) ModelMetaChip("ID: ${endpoint.modelId}")
            }
            Spacer(Modifier.height(4.dp))
            Text(
                endpoint.url,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9CA3AF)
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onUseInChat,
                    enabled = endpoint.modelId != null,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97757)),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Use in chat", color = Color.White) }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, "Edit", tint = Color(0xFF9CA3AF))
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun RamRecommendationBadge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f),
        contentColor = color
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2
        )
    }
}
