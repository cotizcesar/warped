package com.warped.ui.huggingface

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warped.R
import com.warped.domain.model.SyntaxTheme
import com.warped.ui.chat.components.MarkdownText
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

    LaunchedEffect(uiState.searchQuery) {
        if (searchText != uiState.searchQuery) {
            searchText = uiState.searchQuery
        }
    }

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
            Spacer(Modifier.height(12.dp))

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
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

            val activeModelId = uiState.activeDownloadId?.substringBeforeLast("/")
            val downloadState = DownloadCardState(
                fileName = uiState.downloadingFileName,
                isDownloading = uiState.isDownloading,
                isPaused = uiState.isDownloadPaused,
                progress = uiState.downloadProgress,
                downloadedBytes = uiState.downloadedBytes,
                totalBytes = uiState.totalDownloadBytes,
                speed = uiState.downloadSpeedBytesPerSecond,
                error = uiState.downloadError
            )

            SearchResults(
                isLoading = uiState.isLoading,
                results = uiState.searchResults,
                error = uiState.error,
                searchQuery = uiState.searchQuery,
                activeModelId = activeModelId,
                downloadState = downloadState,
                onDownload = { model, fileName, size ->
                    viewModel.startDirectDownload(model, fileName, size)
                },
                onPause = { viewModel.pauseDownload() },
                onResume = { viewModel.resumeDownload() },
                onCancel = { viewModel.cancelDownload() },
                onClearError = { viewModel.clearError() }
            )
        }
    }
}

private data class DownloadCardState(
    val fileName: String,
    val isDownloading: Boolean,
    val isPaused: Boolean,
    val progress: Float,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speed: Long,
    val error: String?
)

@Composable
private fun SearchResults(
    isLoading: Boolean,
    results: List<com.warped.data.remote.dto.HuggingFaceModel>,
    error: String?,
    searchQuery: String,
    activeModelId: String?,
    downloadState: DownloadCardState,
    onDownload: (model: com.warped.data.remote.dto.HuggingFaceModel, fileName: String, fileSize: Long) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onClearError: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (results.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results, key = { it.id }) { model ->
                    val chips = buildList {
                        add("LiteRT-LM")
                        add("${model.downloads} downloads")
                        add("${model.likes} likes")
                        model.tags.firstOrNull()?.let { add(it) }
                    }
                    val litertlmFiles = model.siblings
                        .filter { it.rfilename.endsWith(".litertlm", ignoreCase = true) }
                        .sortedBy { it.size.takeIf { s -> s > 0 } ?: it.lfs?.size ?: 0L }
                    ModelListCard(
                        title = model.id.substringAfterLast("/"),
                        subtitle = model.id,
                        chips = chips,
                        description = model.description.takeIf { it.isNotBlank() },
                        descriptionAsMarkdown = true,
                        files = litertlmFiles,
                        isActive = activeModelId == model.id,
                        downloadState = downloadState,
                        onDownload = { fileName, size -> onDownload(model, fileName, size) },
                        onPause = onPause,
                        onResume = onResume,
                        onCancel = onCancel
                    )
                }
            }
        } else if (!isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (error != null) {
                        Text(
                            error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onClearError) { Text("Dismiss") }
                    } else {
                        Text(
                            if (searchQuery.isNotBlank()) "No results found" else "Search Hugging Face to find models",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelListCard(
    title: String,
    subtitle: String,
    chips: List<String>,
    description: String?,
    descriptionAsMarkdown: Boolean,
    files: List<com.warped.data.remote.dto.HuggingFaceSibling>,
    isActive: Boolean,
    downloadState: DownloadCardState,
    onDownload: (fileName: String, fileSize: Long) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            if (chips.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chips.forEach { chip ->
                        if (chip == "LiteRT-LM") FormatBadge() else AssistInfoChip(text = chip)
                    }
                }
            }
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                if (descriptionAsMarkdown) {
                    MarkdownText(
                        text = description,
                        maxLines = 4,
                        codeTheme = SyntaxTheme.MONOKAI,
                    )
                } else {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            if (files.isEmpty()) {
                Text(
                    text = "No LiteRT-LM files in this model",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (isActive) {
                InlineDownloadProgress(
                    state = downloadState,
                    onPause = onPause,
                    onResume = onResume,
                    onCancel = onCancel,
                    onOpenExternal = { url ->
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                )
            } else {
                val selectedFile = files.getOrNull(selectedIndex) ?: files.first()
                val selectedSize = selectedFile.size.takeIf { it > 0 } ?: selectedFile.lfs?.size ?: 0L
                val selectedLabel = buildString {
                    append(selectedFile.rfilename.substringAfterLast("/"))
                    if (selectedSize > 0) append("  ·  ").append(formatFileSize(selectedSize))
                }
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedLabel,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text("File") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = files.size > 1)
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        files.forEachIndexed { i, file ->
                            val size = file.size.takeIf { it > 0 } ?: file.lfs?.size ?: 0L
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = file.rfilename.substringAfterLast("/"),
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (size > 0) {
                                            Text(
                                                text = formatFileSize(size),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    selectedIndex = i
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onDownload(selectedFile.rfilename, selectedSize) },
                    modifier = Modifier.align(Alignment.End),
                    enabled = files.size > 0
                ) {
                    Text("Download")
                }
            }
        }
    }
}

@Composable
private fun InlineDownloadProgress(
    state: DownloadCardState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onOpenExternal: (String) -> Unit
) {
    val progressInt = (state.progress * 100).toInt().coerceIn(0, 100)
    Column {
        LinearProgressIndicator(
            progress = { state.progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${if (state.isPaused) "Paused" else "Downloading"}: ${state.fileName.substringAfterLast('/')} ($progressInt%)",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "${formatFileSize(state.downloadedBytes)} / ${formatFileSize(state.totalBytes)}" +
                if (state.isDownloading) " · ${formatDownloadSpeed(state.speed)}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.isPaused) {
                TextButton(onClick = onResume) { Text("Resume") }
            } else if (state.isDownloading) {
                TextButton(onClick = onPause) { Text("Pause") }
            }
            TextButton(
                onClick = onCancel,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Cancel") }
        }
        if (state.error != null) {
            val isGated = state.error.startsWith("Gated model", ignoreCase = true)
            Spacer(Modifier.height(6.dp))
            Text(
                text = state.error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            if (isGated) {
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = { onOpenExternal("https://huggingface.co/${state.fileName.substringBefore('/')}") }) {
                    Text("Open on Hugging Face")
                }
            }
        }
    }
}

@Composable
private fun FormatBadge() {
    val color = Color(0xFF4CAF50) // green for LiteRT-LM
    androidx.compose.material3.Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = color.copy(alpha = 0.15f),
        contentColor = color
    ) {
        Text(
            text = "LiteRT-LM",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

@Composable
private fun AssistInfoChip(text: String) {
    androidx.compose.material3.Surface(
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
