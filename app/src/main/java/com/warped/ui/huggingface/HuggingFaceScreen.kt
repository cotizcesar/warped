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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warped.data.local.download.DownloadState
import com.warped.data.repository.AllowlistedModel
import com.warped.ui.components.WarpedAlertDialog

/**
 * Phase 49 (DEL-05): static model catalog.
 *
 * One card per `model_allowlist.json` catalog entry — no text field, no
 * timed query, no gated-model branch, no external link. Downloads reuse the
 * inline progress pattern backed by ModelDownloadManager/Worker with no
 * Authorization header.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HuggingFaceScreen(
    viewModel: CatalogViewModel = hiltViewModel(),
    onNavigateToModels: () -> Unit = {}
) {
    val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Model catalog") },
                navigationIcon = {
                    IconButton(onClick = onNavigateToModels) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to models")
                    }
                }
            )
        }
    ) { padding ->
        if (viewModel.models.isEmpty()) {
            // Zero entries means the bundled asset failed to load (it always
            // ships at least 1 entry) — render the load-failure copy, no skeleton.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Couldn't load the model catalog. Restart the app and try again.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
            ) {
                items(viewModel.models, key = { it.name }) { entry ->
                    val downloadId = viewModel.downloadId(entry)
                    CatalogModelCard(
                        entry = entry,
                        downloadState = downloadStates[downloadId],
                        onDownload = { viewModel.startDownload(entry) },
                        onPause = { viewModel.pauseDownload(downloadId) },
                        onResume = { viewModel.resumeDownload(downloadId) },
                        onCancel = { viewModel.cancelDownload(downloadId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogModelCard(
    entry: AllowlistedModel,
    downloadState: DownloadState?,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    // A "Cancelled" error is terminal-idle: the partial file is deleted and a
    // fresh Download restarts cleanly.
    val active = downloadState != null &&
        (downloadState.isDownloading || downloadState.isPaused) &&
        downloadState.error != "Cancelled"
    val downloaded = downloadState != null && !active &&
        downloadState.error == null && downloadState.progress >= 1f
    var showCancelConfirm by remember { mutableStateOf(false) }

    if (showCancelConfirm) {
        WarpedAlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Cancel download?") },
            text = { Text("The partial file will be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = { showCancelConfirm = false; onCancel() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Cancel download") }
            },
            dismissButton = { TextButton(onClick = { showCancelConfirm = false }) { Text("Keep") } }
        )
    }

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
                        text = entry.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                    Text(
                        text = entry.modelFile,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            val badges = buildList {
                if (entry.capabilities.vision) add("vision")
                if (entry.capabilities.audio) add("audio")
            }
            if (badges.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    badges.forEach { cap ->
                        CatalogCapabilityBadge(capability = cap)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = formatFileSize(entry.sizeInBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )

            Spacer(Modifier.height(10.dp))

            when {
                active -> CatalogDownloadProgress(
                    state = downloadState!!,
                    onPause = onPause,
                    onResume = onResume,
                    onCancelClick = { showCancelConfirm = true }
                )
                downloaded -> Text(
                    text = "Downloaded",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                else -> {
                    if (downloadState?.error != null && downloadState.error != "Cancelled") {
                        Text(
                            text = downloadState.error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(6.dp))
                    }
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
}

@Composable
private fun CatalogCapabilityBadge(capability: String) {
    val color = when (capability) {
        "vision" -> androidx.compose.ui.graphics.Color(0xFF2196F3)
        "audio" -> androidx.compose.ui.graphics.Color(0xFFFF9800)
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
private fun CatalogDownloadProgress(
    state: DownloadState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancelClick: () -> Unit
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
                if (state.isDownloading) " · ${formatDownloadSpeed(state.speedBytesPerSecond)}" else "",
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
                onClick = onCancelClick,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Cancel") }
        }
        if (state.error != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = state.error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
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
