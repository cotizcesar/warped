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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                label = { Text("Search models") },
                supportingText = {
                    Text(
                        if (canSearch) "Enter 3+ characters to search" else "Type to search..."
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
                    val capabilities = deriveCapabilities(model.tags, model.pipelineTag)
                    val litertlmFile = model.siblings
                        .firstOrNull { it.rfilename.endsWith(".litertlm", ignoreCase = true) }
                    val fileSize = litertlmFile?.size?.takeIf { it > 0 }
                        ?: litertlmFile?.lfs?.size ?: 0L
                    ModelListCard(
                        title = model.id.substringAfterLast("/"),
                        subtitle = model.id,
                        capabilities = capabilities,
                        description = model.description.takeIf { it.isNotBlank() },
                        descriptionAsMarkdown = true,
                        fileName = litertlmFile?.rfilename,
                        fileSize = fileSize,
                        isActive = activeModelId == model.id,
                        downloadState = downloadState,
                        onDownload = {
                            if (litertlmFile != null) onDownload(model, litertlmFile.rfilename, fileSize)
                        },
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
                            if (searchQuery.isNotBlank()) "No results found" else "Loading warped-community models...",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

private fun deriveCapabilities(tags: List<String>, pipelineTag: String): List<String> {
    val caps = mutableListOf<String>()
    if (pipelineTag == "image-text-to-text") caps.add("vision")
    val lower = tags.map { it.lowercase() }
    if (lower.any { it.contains("audio") }) caps.add("audio")
    if (lower.any { it in listOf("tool_use", "function-calling", "tools") || it.contains("tool") }) caps.add("tools")
    if (lower.any { it in listOf("thinking", "reasoning") || it.contains("think") }) caps.add("thinking")
    return caps
}

@Composable
private fun ModelListCard(
    title: String,
    subtitle: String,
    capabilities: List<String>,
    description: String?,
    descriptionAsMarkdown: Boolean,
    fileName: String?,
    fileSize: Long,
    isActive: Boolean,
    downloadState: DownloadCardState,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
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
            if (capabilities.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    capabilities.forEach { cap ->
                        CapabilityBadge(capability = cap)
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

            if (fileName == null) {
                Text(
                    text = "No .litertlm file found",
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
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onDownload,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Download")
                }
            }
        }
    }
}

@Composable
private fun CapabilityBadge(capability: String) {
    val color = when (capability) {
        "vision" -> androidx.compose.ui.graphics.Color(0xFF2196F3)
        "audio" -> androidx.compose.ui.graphics.Color(0xFFFF9800)
        "tools" -> androidx.compose.ui.graphics.Color(0xFF9C27B0)
        "thinking" -> androidx.compose.ui.graphics.Color(0xFF00BCD4)
        else -> MaterialTheme.colorScheme.primary
    }
    androidx.compose.material3.Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = color.copy(alpha = 0.15f),
        contentColor = color
    ) {
        Text(
            text = capability,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
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
