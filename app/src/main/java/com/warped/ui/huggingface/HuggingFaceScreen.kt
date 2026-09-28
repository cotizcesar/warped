package com.warped.ui.huggingface

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warped.data.local.download.DownloadState
import com.warped.data.repository.AllowlistedModel
import com.warped.ui.components.CapabilityIconBadge
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

/**
 * Pure helper joining the Spanish catalog-card details (RAM guidance + purpose
 * blurb). Returns null when both fields are absent/blank — the card then
 * renders with no expand affordance. Unit-testable on the JVM.
 */
fun expandedText(entry: AllowlistedModel): String? {
    val parts = listOfNotNull(
        entry.ramNote?.takeIf { it.isNotBlank() },
        entry.blurb?.takeIf { it.isNotBlank() }
    )
    return if (parts.isEmpty()) null else parts.joinToString(separator = "\n")
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
    val failed = !active && !downloaded &&
        downloadState?.error != null && downloadState.error != "Cancelled"
    var showCancelConfirm by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val details = remember(entry) { expandedText(entry) }
    val expandable = details != null

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
        modifier = Modifier.fillMaxWidth()
            .then(
                if (expandable) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = if (expanded) "Contraer detalles" else "Expandir detalles"
                    ) { expanded = !expanded }
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Collapsed row 1: title + download-state icon cluster.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                CatalogDownloadActions(
                    downloadState = downloadState,
                    active = active,
                    downloaded = downloaded,
                    failed = failed,
                    onDownload = onDownload,
                    onPause = onPause,
                    onResume = onResume,
                    onCancelClick = { showCancelConfirm = true }
                )
            }
            Spacer(Modifier.height(8.dp))
            // Collapsed row 2: capability icons + size.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CatalogCapabilityIcons(entry = entry)
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatFileSize(entry.sizeInBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            // Active download: compact progress parity (label + bytes/total).
            if (active) {
                Spacer(Modifier.height(6.dp))
                CatalogInlineProgress(state = downloadState!!)
            }
            // Retained error text (icon form keeps the message for a11y;
            // retry = download icon tap).
            if (failed) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = downloadState!!.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            // Expanded: Spanish RAM guidance + blurb + retained model file.
            if (expanded && expandable) {
                Spacer(Modifier.height(8.dp))
                if (!entry.ramNote.isNullOrBlank()) {
                    Text(
                        text = entry.ramNote,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (!entry.blurb.isNullOrBlank()) {
                    Text(
                        text = entry.blurb,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.modelFile,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Download-state icon cluster (1:1 with the pre-redesign states): idle
 * download, downloading mini-progress + pause + cancel, paused resume +
 * cancel, downloaded status, error retry. All content descriptions in Spanish.
 */
@Composable
private fun CatalogDownloadActions(
    downloadState: DownloadState?,
    active: Boolean,
    downloaded: Boolean,
    failed: Boolean,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancelClick: () -> Unit
) {
    when {
        active -> {
            val paused = downloadState?.isPaused == true
            if (!paused) {
                CircularProgressIndicator(
                    progress = { (downloadState?.progress ?: 0f).coerceIn(0f, 1f) },
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onPause) {
                    Icon(
                        imageVector = Icons.Filled.Pause,
                        contentDescription = "Pausar descarga"
                    )
                }
            } else {
                IconButton(onClick = onResume) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Reanudar descarga"
                    )
                }
            }
            IconButton(onClick = onCancelClick) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Cancelar descarga",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
        downloaded -> Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = "Descargado",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(12.dp).size(24.dp)
        )
        else -> IconButton(onClick = onDownload) {
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = "Descargar modelo",
                tint = if (failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        }
    }
}

/**
 * Capability icons reusing [CapabilityIconBadge] iconography (vision/audio/
 * thinking only — tools is never shown on catalog cards per Phase 49 DEL-01).
 */
@Composable
private fun CatalogCapabilityIcons(entry: AllowlistedModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (entry.capabilities.vision) {
            CapabilityIconBadge(
                icon = Icons.Filled.Visibility,
                contentDescription = "Visión",
                color = Color(0xFF9C27B0)
            )
        }
        if (entry.capabilities.audio) {
            CapabilityIconBadge(
                icon = Icons.Filled.Audiotrack,
                contentDescription = "Audio",
                color = Color(0xFF4CAF50)
            )
        }
        if (entry.capabilities.supportsThinking) {
            CapabilityIconBadge(
                icon = Icons.Filled.Psychology,
                contentDescription = "Razonamiento",
                color = Color(0xFFFF9800)
            )
        }
    }
}

@Composable
private fun CatalogInlineProgress(state: DownloadState) {
    val progressInt = (state.progress * 100).toInt().coerceIn(0, 100)
    Column {
        Text(
            text = "${if (state.isPaused) "En pausa" else "Descargando"}: " +
                "${state.fileName.substringAfterLast('/')} ($progressInt%)",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "${formatFileSize(state.downloadedBytes)} / ${formatFileSize(state.totalBytes)}" +
                if (state.isDownloading) " · ${formatDownloadSpeed(state.speedBytesPerSecond)}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.error != null && state.error != "Cancelled") {
            Spacer(Modifier.height(4.dp))
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
