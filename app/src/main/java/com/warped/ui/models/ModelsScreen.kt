package com.warped.ui.models

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warped.data.local.download.DownloadState
import com.warped.data.local.inference.MemoryChecker
import com.warped.domain.model.Endpoint
import com.warped.domain.model.LocalModel
import com.warped.ui.endpoints.components.EndpointForm

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

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importModel(it) }
    }

    if (showMemoryWarning != null) {
        AlertDialog(
            onDismissRequest = { showMemoryWarning = null },
            title = { Text("Memory Warning") },
            text = {
                val model = showMemoryWarning!!
                val context = androidx.compose.ui.platform.LocalContext.current
                val info = MemoryChecker(context).getMemoryInfo()
                Text(
                    "This model requires ${model.sizeBytes / (1024 * 1024)} MB. " +
                    "Your device has ${info.availableBytes / (1024 * 1024)} MB available. " +
                    "Loading may cause instability. Continue?"
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
                title = { Text("Models & Endpoints") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Menu")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddWizard = true }) {
                Text("+")
            }
        }
    ) { padding ->
        if (showAddWizard) {
            AlertDialog(
                onDismissRequest = { showAddWizard = false },
                title = { Text("Add Model") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            showAddWizard = false
                            onOpenHuggingFace()
                        }) { Text("Local native from Hugging Face") }
                        TextButton(onClick = {
                            showAddWizard = false
                            filePickerLauncher.launch(arrayOf("*/*"))
                        }) { Text("Import local GGUF file") }
                        TextButton(onClick = {
                            showAddWizard = false
                            viewModel.showEndpointForm()
                        }) { Text("Network/API endpoint") }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showAddWizard = false }) { Text("Cancel") }
                }
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
                    modelId = uiState.formModelId,
                    apiKey = uiState.formApiKey,
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
                    modelId = uiState.formModelId,
                    apiKey = uiState.formApiKey,
                    onFieldChange = { field, value -> viewModel.updateEndpointField(field, value) },
                    onSave = { viewModel.saveEndpointEdit() },
                    onDismiss = { viewModel.cancelEndpointEdit() },
                    availableModels = uiState.availableEndpointModels,
                    availableModelsData = uiState.availableEndpointModelsData,
                    isFetchingModels = uiState.isFetchingEndpointModels,
                    onFetchModels = { viewModel.fetchEndpointModels() },
                )
            } else if (uiState.models.isEmpty() && uiState.endpoints.isEmpty() && !uiState.isImporting) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No models or endpoints yet", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { showAddWizard = true }) { Text("Add Model") }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.activeDownloads.isNotEmpty()) {
                        item { Text("Active Downloads", style = MaterialTheme.typography.titleMedium) }
                        items(uiState.activeDownloads, key = { "dl-${it.modelId}" }) { download ->
                            DownloadCard(
                                download = download,
                                onCancel = { viewModel.cancelDownload(download.modelId) },
                                onDeleteIncomplete = { viewModel.deleteIncompleteDownload(download) }
                            )
                        }
                    }
                    item { Text("Local Models", style = MaterialTheme.typography.titleMedium) }
                    items(uiState.models, key = { "local-${it.id}" }) { model ->
                        ModelCard(
                            model = model,
                            onLoad = {
                                if (viewModel.shouldWarnAboutMemory(model.sizeBytes)) {
                                    showMemoryWarning = model
                                } else {
                                    viewModel.useLocalModel(model)
                                    onUseInChat()
                                }
                            },
                            onDelete = { viewModel.deleteModel(model) }
                        )
                    }
                    item { Text("Network Endpoints", style = MaterialTheme.typography.titleMedium) }
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
        AlertDialog(
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

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(model.name, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${model.architecture} · ${model.quantization} · ${model.parameterCount} params",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Storage,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            formatFileSize(model.sizeBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onLoad, modifier = Modifier.weight(1f)) { Text("Use in chat") }
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.weight(1f),
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
private fun DownloadCard(
    download: DownloadState,
    onCancel: () -> Unit,
    onDeleteIncomplete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(download.fileName.substringAfterLast("/"), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            if (download.isDownloading) {
                LinearProgressIndicator(
                    progress = { download.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${(download.progress * 100).toInt()}% · ${formatFileSize(download.downloadedBytes)} / ${formatFileSize(download.totalBytes)}",
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

@Composable
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024 * 1024 -> "%.2f GB".format(bytes.toDouble() / (1024L * 1024 * 1024))
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes.toDouble() / (1024 * 1024))
        bytes >= 1024 -> "%.1f KB".format(bytes.toDouble() / 1024)
        else -> "$bytes B"
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
        AlertDialog(
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

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(endpoint.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "${endpoint.apiType.name} · ${endpoint.modelId ?: "No model id"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                endpoint.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onUseInChat, enabled = endpoint.modelId != null, modifier = Modifier.weight(1f)) {
                    Text("Use in chat")
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
