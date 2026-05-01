package com.warped.ui.huggingface

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
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
        ModelDetailScreen(
            model = uiState.selectedModel!!,
            siblings = uiState.modelSiblings,
            compatibilityByFileName = uiState.compatibilityByFileName,
            isDownloading = uiState.isDownloading,
            downloadProgress = uiState.downloadProgress,
            downloadingFileName = uiState.downloadingFileName,
            downloadError = uiState.downloadError,
            onDownload = { fileName, size ->
                viewModel.downloadFile(uiState.selectedModel!!.id, fileName, size)
            },
            onCancelDownload = { viewModel.pauseDownload() },
            onBack = { viewModel.clearDetail() }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Hugging Face") })
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

            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (uiState.searchResults.isNotEmpty()) {
                val compatibleCount = uiState.searchResults.count { uiState.compatibilityByModelId[it.id] == true }
                val filteredModels = if (uiState.showCompatibleOnly) {
                    uiState.searchResults.filter { uiState.compatibilityByModelId[it.id] == true }
                } else {
                    uiState.searchResults
                }

                Text(
                    text = "RAM: ${formatFileSize(uiState.availableMemoryBytes)} available · limit ${formatFileSize((uiState.availableMemoryBytes * 0.8).toLong())}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = !uiState.showCompatibleOnly,
                        onClick = { viewModel.toggleCompatibleFilter() },
                        label = { Text("All (${uiState.searchResults.size})") }
                    )
                    FilterChip(
                        selected = uiState.showCompatibleOnly,
                        onClick = { viewModel.toggleCompatibleFilter() },
                        label = {
                            Text("Compatible (${compatibleCount})")
                        },
                        colors = if (compatibleCount > 0) {
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            FilterChipDefaults.filterChipColors()
                        }
                    )
                }
                Spacer(Modifier.height(4.dp))

                if (filteredModels.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No compatible models found. Try a different search.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filteredModels) { model ->
                            ModelSearchResultCard(
                                model = model,
                                isCompatible = uiState.compatibilityByModelId[model.id] == true,
                                onClick = { viewModel.selectModel(model) }
                            )
                        }
                    }
                }
            } else if (!uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Search Hugging Face for GGUF models",
                        style = MaterialTheme.typography.bodyLarge
                    )
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
    isCompatible: Boolean,
    onClick: () -> Unit
) {
    val cardColors = if (isCompatible) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = cardColors,
        border = if (isCompatible) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        } else null,
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
                if (isCompatible) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.compatible_with_device),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
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
                if (isCompatible) {
                    AssistInfoChip(text = stringResource(R.string.compatible_with_device))
                }
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
    compatibilityLevel: Int,
    modelDownloads: Int,
    isDownloading: Boolean,
    onDownload: () -> Unit
) {
    val effectiveSize = sibling.size.takeIf { it > 0 } ?: sibling.lfs?.size ?: 0L
    val compatColor = when (compatibilityLevel) {
        2 -> MaterialTheme.colorScheme.primary
        1 -> Color(0xFFFF9800)
        else -> MaterialTheme.colorScheme.error
    }
    val sizeLabel = when (compatibilityLevel) {
        2 -> stringResource(R.string.compatible_with_device)
        else -> stringResource(R.string.may_be_too_large)
    }

    val cardColors = if (compatibilityLevel >= 1) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = cardColors,
        border = if (compatibilityLevel >= 1) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        } else null
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
                if (compatibilityLevel >= 1) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.compatible_with_device),
                        tint = compatColor
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${formatFileSize(effectiveSize)} · $sizeLabel",
                style = MaterialTheme.typography.bodySmall,
                color = compatColor,
                maxLines = 1
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (compatibilityLevel >= 1) {
                    AssistInfoChip(text = stringResource(R.string.compatible_with_device))
                }
                AssistInfoChip(text = formatFileSize(effectiveSize))
                AssistInfoChip(text = "$modelDownloads downloads")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDetailScreen(
    model: com.warped.data.remote.dto.HuggingFaceModelDetail,
    siblings: List<com.warped.data.remote.dto.HuggingFaceSibling>,
    compatibilityByFileName: Map<String, Int>,
    isDownloading: Boolean,
    downloadProgress: Float,
    downloadingFileName: String,
    downloadError: String?,
    onDownload: (fileName: String, size: Long) -> Unit,
    onCancelDownload: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(model.id.substringAfterLast("/")) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("\u2190 Back") }
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
                            Text(
                                model.id,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistInfoChip(text = "${model.downloads} downloads")
                                AssistInfoChip(text = "${model.likes} likes")
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Author: ${model.author.ifBlank { "Unknown" }}")
                            model.cardData?.license?.let { license ->
                                Text(
                                    "License: $license",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            model.cardData?.language?.takeIf { it.isNotEmpty() }?.let { languages ->
                                Text(
                                    "Language: ${languages.joinToString()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (model.tags.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    model.tags.take(3).forEach { tag ->
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

                if (isDownloading) {
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
                                    "Downloading: $downloadingFileName (${(downloadProgress * 100).toInt()}%)",
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = onCancelDownload) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                }

                if (downloadError != null) {
                    item {
                        Text(downloadError, color = MaterialTheme.colorScheme.error)
                    }
                }

                items(siblings) { sibling ->
                    val compatLevel = compatibilityByFileName[sibling.rfilename] ?: 0
                    val effectiveSize = sibling.size.takeIf { it > 0 } ?: sibling.lfs?.size ?: 0L
                    SiblingFileCard(
                        sibling = sibling,
                        compatibilityLevel = compatLevel,
                        modelDownloads = model.downloads,
                        isDownloading = isDownloading,
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
