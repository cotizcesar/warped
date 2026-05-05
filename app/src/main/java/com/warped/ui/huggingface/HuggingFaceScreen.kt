package com.warped.ui.huggingface

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warped.R
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HuggingFaceScreen(
    viewModel: HuggingFaceViewModel = hiltViewModel(),
    onNavigateToModels: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var searchText by remember { mutableStateOf(uiState.searchQuery) }
    val canSearch = searchText.trim().length >= 3

    LaunchedEffect(searchText) {
        val query = searchText.trim()
        if (query.length < 3) return@LaunchedEffect
        delay(400)
        if (searchText.trim() != query) return@LaunchedEffect
        viewModel.search(query)
    }

    LaunchedEffect(uiState.downloadSuccess) {
        if (uiState.downloadSuccess) {
            viewModel.clearDownloadSuccess()
            onNavigateToModels()
        }
    }

    if (uiState.selectedModel != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.clearDetail() },
            sheetState = sheetState,
            containerColor = Color(0xFF1F1F1E)
        ) {
            ModelDetailScreen(
                model = uiState.selectedModel!!,
                siblings = uiState.modelSiblings,
                ggufFileDetails = uiState.ggufFileDetails,
                isDownloading = uiState.isDownloading,
                isDownloadPaused = uiState.isDownloadPaused,
                downloadProgress = uiState.downloadProgress,
                downloadedBytes = uiState.downloadedBytes,
                totalDownloadBytes = uiState.totalDownloadBytes,
                downloadSpeedBytesPerSecond = uiState.downloadSpeedBytesPerSecond,
                downloadingFileName = uiState.downloadingFileName,
                downloadError = uiState.downloadError,
                onDownload = { fileName, size ->
                    viewModel.downloadFile(uiState.selectedModel!!.id, fileName, size)
                },
                onPauseDownload = { viewModel.pauseDownload() },
                onResumeDownload = { viewModel.resumeDownload() },
                onBack = { viewModel.clearDetail() }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hugging Face") },
                navigationIcon = {
                    IconButton(onClick = onNavigateToModels) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to models")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = searchText,
                onValueChange = {
                    searchText = it
                    viewModel.onSearchTextChanged(it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.search_models)) },
                supportingText = {
                    Text(
                        if (canSearch) {
                            stringResource(R.string.search_models_hint)
                        } else {
                            stringResource(R.string.search_minimum_length)
                        }
                    )
                },
                singleLine = true,
                trailingIcon = {
                    TextButton(
                        onClick = { viewModel.search(searchText) },
                        enabled = canSearch
                    ) {
                        Text("Search")
                    }
                }
            )

            Spacer(Modifier.height(8.dp))

            Spacer(Modifier.height(4.dp))

            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (uiState.searchResults.isNotEmpty()) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.searchResults) { model ->
                        ModelSearchResultCard(
                            model = model,
                            activeFormat = uiState.activeFormat,
                            onClick = { viewModel.selectModel(model) }
                        )
                    }
                }
            } else if (!uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (uiState.error != null) {
                            Text(
                                uiState.error ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(
                            if (uiState.searchQuery.isNotBlank()) "No results found" else "Loading models...",
                            style = MaterialTheme.typography.bodyLarge
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
private fun ModelSearchResultCard(
    model: com.warped.data.remote.dto.HuggingFaceModel,
    activeFormat: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    model.id.substringAfterLast("/"),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                model.id,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormatBadge(activeFormat)
                AssistInfoChip(text = "${model.downloads} downloads")
                AssistInfoChip(text = "${model.likes} likes")
                model.tags.firstOrNull()?.let { tag ->
                    AssistInfoChip(text = tag)
                }
            }
        }
    }
}

@Composable
private fun FormatBadge(format: String) {
    val (color, label) = when {
        format.equals("gguf", ignoreCase = true) -> Color(0xFF2196F3) to "GGUF"
        format.equals("litertlm", ignoreCase = true) || format.equals("staffpicks", ignoreCase = true) -> Color(0xFF4CAF50) to "LiteRT-LM"
        else -> MaterialTheme.colorScheme.outline to format
    }
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = color.copy(alpha = 0.15f),
        contentColor = color
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

@Composable
private fun AssistInfoChip(text: String) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

@Composable
private fun SiblingFileCard(
    sibling: com.warped.data.remote.dto.HuggingFaceSibling,
    ggufDetail: GgufFileDetail?,
    modelDownloads: Int,
    isDownloading: Boolean,
    onDownload: () -> Unit
) {
    val effectiveSize = sibling.size.takeIf { it > 0 } ?: sibling.lfs?.size ?: 0L

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    sibling.rfilename.substringAfterLast("/"),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2
                )
                ggufDetail?.quantization?.let { quant ->
                    Spacer(Modifier.width(8.dp))
                    QuantizationBadge(quant)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (effectiveSize > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistInfoChip(text = formatFileSize(effectiveSize))
                    AssistInfoChip(text = "$modelDownloads downloads")
                    if (ggufDetail != null && ggufDetail.ramEstimateBytes > 0) {
                        AssistInfoChip(text = "~${com.warped.data.local.inference.GgufQuantizationParser.formatRamBytes(ggufDetail.ramEstimateBytes)} RAM")
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistInfoChip(text = "$modelDownloads downloads")
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onDownload,
                enabled = !isDownloading,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Download")
            }
        }
    }
}

@Composable
private fun QuantizationBadge(quantization: String) {
    val color = when {
        quantization.startsWith("Q2", ignoreCase = true) -> Color(0xFFE53935) // red for low quant
        quantization.startsWith("Q3", ignoreCase = true) -> Color(0xFFFB8C00) // orange
        quantization.startsWith("Q4", ignoreCase = true) -> Color(0xFF43A047) // green
        quantization.startsWith("Q5", ignoreCase = true) -> Color(0xFF1E88E5) // blue
        quantization.startsWith("Q6", ignoreCase = true) -> Color(0xFF8E24AA) // purple
        quantization.startsWith("Q8", ignoreCase = true) -> Color(0xFF00897B) // teal
        quantization.startsWith("F", ignoreCase = true) -> Color(0xFFFFB300) // amber for fp16/fp32
        quantization.startsWith("IQ", ignoreCase = true) -> Color(0xFF00ACC1) // cyan for IQ
        quantization.startsWith("TQ", ignoreCase = true) -> Color(0xFF7CB342) // light green for TQ
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = color.copy(alpha = 0.15f),
        contentColor = color
    ) {
        Text(
            text = quantization,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDetailScreen(
    model: com.warped.data.remote.dto.HuggingFaceModelDetail,
    siblings: List<com.warped.data.remote.dto.HuggingFaceSibling>,
    ggufFileDetails: Map<String, GgufFileDetail>,
    isDownloading: Boolean,
    isDownloadPaused: Boolean,
    downloadProgress: Float,
    downloadedBytes: Long,
    totalDownloadBytes: Long,
    downloadSpeedBytesPerSecond: Long,
    downloadingFileName: String,
    downloadError: String?,
    onDownload: (fileName: String, size: Long) -> Unit,
    onPauseDownload: () -> Unit,
    onResumeDownload: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(model.id.substringAfterLast("/")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to models")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                model.id.substringAfterLast("/"),
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Spacer(Modifier.height(4.dp))
                            val isGated = model.gated != "false"
                            if (isGated) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = null,
                                        tint = Color(0xFFFF9800),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "Gated model — requires Access Token in Settings",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFFF9800)
                                    )
                                }
                            }
                            if (model.tags.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    model.tags.take(5).forEach { tag ->
                                        AssistInfoChip(text = tag)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Text("Available Models", style = MaterialTheme.typography.titleLarge)
                }

                if (isDownloading || isDownloadPaused) {
                    item {
                        Column {
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${if (isDownloadPaused) "Paused" else "Downloading"}: $downloadingFileName (${(downloadProgress * 100).toInt()}%)",
                                    modifier = Modifier.weight(1f)
                                )
                                if (isDownloadPaused) {
                                    TextButton(onClick = onResumeDownload) {
                                        Text("Resume")
                                    }
                                } else {
                                    TextButton(onClick = onPauseDownload) {
                                        Text("Pause")
                                    }
                                }
                            }
                            Text(
                                "${formatFileSize(downloadedBytes)} / ${formatFileSize(totalDownloadBytes)}" +
                                    if (isDownloading) " · ${formatDownloadSpeed(downloadSpeedBytesPerSecond)}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (downloadError != null) {
                    item {
                        Text(downloadError, color = MaterialTheme.colorScheme.error)
                    }
                }

                items(siblings) { sibling ->
                    val effectiveSize = sibling.size.takeIf { it > 0 } ?: sibling.lfs?.size ?: 0L
                    val detail = ggufFileDetails[sibling.rfilename]
                    SiblingFileCard(
                        sibling = sibling,
                        ggufDetail = detail,
                        modelDownloads = model.downloads,
                        isDownloading = isDownloading || isDownloadPaused,
                        onDownload = { onDownload(sibling.rfilename, effectiveSize) }
                    )
                }
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
