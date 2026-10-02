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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warped.R
import com.warped.data.local.download.DownloadState
import com.warped.data.local.inference.MemoryChecker
import com.warped.domain.model.Endpoint
import com.warped.domain.model.LocalModel
import com.warped.ui.endpoints.components.EndpointForm
import com.warped.ui.components.ActiveDownloadContent
import com.warped.ui.components.WarpedAlertDialog
import com.warped.ui.theme.WarpedAccent
import com.warped.ui.components.formatFileSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    viewModel: ModelsViewModel = hiltViewModel(),
    onUseInChat: (conversationId: Long) -> Unit = {},
    onOpenHuggingFace: () -> Unit = {},
    onOpenDrawer: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showMemoryWarning by remember { mutableStateOf<LocalModel?>(null) }
    var showAddWizard by remember { mutableStateOf(false) }
    val isEndpointFormOpen = uiState.isEndpointFormVisible || uiState.isEditingEndpoint
    val snackbarHostState = remember { SnackbarHostState() }

    // Quick-task (activation-new-chat): activation creates the bound
    // conversation row asynchronously — navigate once its id lands, then
    // consume so a later recomposition never re-navigates.
    val pendingChatId by viewModel.pendingChatId.collectAsStateWithLifecycle()
    LaunchedEffect(pendingChatId) {
        pendingChatId?.let { id ->
            onUseInChat(id)
            viewModel.consumePendingChat()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { msg ->
            snackbarHostState.showSnackbar(message = msg, actionLabel = "Dismiss", withDismissAction = true)
            viewModel.clearError()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importModel(it) }
    }

    if (showMemoryWarning != null) {
        WarpedAlertDialog(
            onDismissRequest = { showMemoryWarning = null },
            title = { Text(stringResource(R.string.memory_warning_title)) },
            text = {
                val model = showMemoryWarning!!
                val context = androidx.compose.ui.platform.LocalContext.current
                val checker = MemoryChecker(context)
                val memInfo = checker.getMemoryInfo()
                val neededMB = model.sizeBytes / (1024 * 1024)
                val availableMB = memInfo.availableBytes / (1024 * 1024)
                Text(
                    stringResource(R.string.models_memory_msg_fmt, neededMB, availableMB)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.useLocalModel(showMemoryWarning!!)
                    showMemoryWarning = null
                }) { Text(stringResource(R.string.continue_text)) }
            },
            dismissButton = {
                TextButton(onClick = { showMemoryWarning = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEndpointFormOpen) stringResource(R.string.connect_api_title) else stringResource(R.string.models_title_endpoints),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                windowInsets = TopAppBarDefaults.windowInsets,
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back_to_models))
                        }
                    } else {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.cd_menu))
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
                    Icon(Icons.Filled.Add, stringResource(R.string.cd_add), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
                }
            }
        }
    ) { padding ->
        if (showAddWizard) {
            WarpedAlertDialog(
                onDismissRequest = { showAddWizard = false },
                title = { Text(stringResource(R.string.add_model), style = MaterialTheme.typography.titleLarge) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = { showAddWizard = false; onOpenHuggingFace() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Download, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.hf_download_model), style = MaterialTheme.typography.bodyLarge)
                                Text(stringResource(R.string.models_catalog_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                Text(stringResource(R.string.import_model_file), style = MaterialTheme.typography.bodyLarge)
                                Text(stringResource(R.string.import_model_file_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                Text(stringResource(R.string.connect_api_title), style = MaterialTheme.typography.bodyLarge)
                                Text(stringResource(R.string.connect_lm_studio_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { showAddWizard = false }) { Text(stringResource(R.string.cancel)) } }
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
                    stringResource(R.string.models_importing_fmt, (uiState.importProgress * 100).toInt()),
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        Text(stringResource(R.string.no_models_endpoints_yet), style = MaterialTheme.typography.bodyLarge, color = Color(0xFF9CA3AF))
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.models_empty_hint), style = MaterialTheme.typography.bodySmall, color = Color(0xFF9CA3AF))
                        Spacer(Modifier.height(16.dp))
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onOpenHuggingFace,
                                colors = ButtonDefaults.buttonColors(containerColor = WarpedAccent),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text(stringResource(R.string.models_empty_download_cta), color = Color.White) }
                            OutlinedButton(
                                onClick = { viewModel.showEndpointForm() },
                                shape = RoundedCornerShape(8.dp)
                            ) { Text(stringResource(R.string.models_empty_add_endpoint_cta)) }
                        }
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
                                stringResource(R.string.active_downloads),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        items(uiState.activeDownloads, key = { "dl-${it.modelId}" }) { download ->
                            DownloadCard(
                                download = download,
                                onCancel = { viewModel.cancelDownload(download.modelId) },
                                onDeleteIncomplete = { viewModel.deleteIncompleteDownload(download) },
                                onPause = { viewModel.pauseDownload(download.modelId) },
                                onResume = { viewModel.resumeDownload(download.modelId) },
                                onRetry = { viewModel.retryDownload(download.modelId) }
                            )
                        }
                    }
                    item {
                        Text(
                            stringResource(R.string.local_models),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    items(uiState.models, key = { "local-${it.id}" }) { model ->
                        ModelCard(
                            model = model,
                            capabilities = viewModel.effectiveCapabilities(model),
                            onLoad = {
                                if (viewModel.shouldWarnAboutMemory(model.sizeBytes)) {
                                    showMemoryWarning = model
                                } else {
                                    viewModel.useLocalModel(model)
                                }
                            },
                            onDelete = { viewModel.deleteModel(model) }
                        )
                    }
                    if (uiState.endpoints.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.network_endpoints),
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
                                },
                                onEdit = { viewModel.editEndpoint(endpoint) },
                                onDelete = { viewModel.deleteEndpoint(endpoint) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModelCard(
    model: LocalModel,
    capabilities: com.warped.domain.model.ModelCapabilities? = null,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        WarpedAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_model_title)) },
            text = { Text(stringResource(R.string.delete_model_message, model.name, formatFileSize(model.sizeBytes))) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
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
            val caps = capabilities ?: model.capabilities
            if (caps.vision || caps.reasoning || caps.tools || caps.audio) {
                Spacer(Modifier.height(8.dp))
                com.warped.ui.components.CapabilityIconRow(caps)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onLoad,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97757)),
                    shape = RoundedCornerShape(8.dp)
                ) { Text(stringResource(R.string.use_in_chat), color = Color.White) }
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.delete))
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
private fun DownloadCard(
    download: DownloadState,
    onCancel: () -> Unit,
    onDeleteIncomplete: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B29)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            ActiveDownloadContent(
                download = download,
                onCancel = onCancel,
                onDeleteIncomplete = onDeleteIncomplete,
                onPause = onPause,
                onResume = onResume,
                onRetry = onRetry
            )
        }
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
            title = { Text(stringResource(R.string.delete_endpoint_title)) },
            text = { Text(stringResource(R.string.delete_endpoint_message, endpoint.name, endpoint.apiType.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
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
                ) { Text(stringResource(R.string.use_in_chat), color = Color.White) }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, stringResource(R.string.edit), tint = Color(0xFF9CA3AF))
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
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
