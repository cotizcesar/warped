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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.warped.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warped.data.local.download.DownloadState
import com.warped.data.repository.AllowlistedModel
import com.warped.ui.components.ActiveDownloadContent
import com.warped.ui.components.CapabilityIconBadge
import com.warped.ui.components.WarpedAlertDialog
import com.warped.ui.components.formatFileSize

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
    val downloadedFileNames by viewModel.downloadedFileNames.collectAsStateWithLifecycle()

    Scaffold(
        // API-02: explicit system-bars content insets (same as the Scaffold
        // default) — the catalog list never draws under status/nav bars, in
        // gesture-nav and 3-button nav alike.
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.hf_catalog)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateToModels) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back_to_models))
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
                    stringResource(R.string.hf_load_failed),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            // Smallest-first display order comes from the ViewModel; sections
            // split downloaded from available within that order.
            val downloaded = remember(viewModel.models, downloadedFileNames) {
                viewModel.models.filter { it.modelFile in downloadedFileNames }
            }
            val available = remember(viewModel.models, downloadedFileNames) {
                viewModel.models.filter { it.modelFile !in downloadedFileNames }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
            ) {
                if (downloaded.isNotEmpty()) {
                    item(key = "section-downloaded") {
                        CatalogSectionHeader(
                            title = stringResource(R.string.hf_section_downloaded),
                            count = downloaded.size
                        )
                    }
                    items(downloaded, key = { "dl-${it.name}" }) { entry ->
                        CatalogCardItem(
                            entry = entry,
                            viewModel = viewModel,
                            downloadStates = downloadStates,
                            isOnDevice = true,
                            onDeleteDownloaded = { viewModel.deleteDownloaded(entry) }
                        )
                    }
                }
                if (available.isNotEmpty()) {
                    item(key = "section-available") {
                        CatalogSectionHeader(
                            title = stringResource(R.string.hf_section_available),
                            count = available.size
                        )
                    }
                    items(available, key = { "av-${it.name}" }) { entry ->
                        CatalogCardItem(
                            entry = entry,
                            viewModel = viewModel,
                            downloadStates = downloadStates,
                            isOnDevice = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogSectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF9CA3AF)
        )
    }
}

@Composable
private fun CatalogCardItem(
    entry: AllowlistedModel,
    viewModel: CatalogViewModel,
    downloadStates: Map<String, DownloadState>,
    isOnDevice: Boolean,
    onDeleteDownloaded: () -> Unit = {}
) {
    val downloadId = viewModel.downloadId(entry)
    CatalogModelCard(
        entry = entry,
        downloadState = downloadStates[downloadId],
        isOnDevice = isOnDevice,
        onDownload = { viewModel.startDownload(entry) },
        onCancel = { viewModel.cancelDownload(downloadId) },
        onPause = { viewModel.pauseDownload(downloadId) },
        onResume = { viewModel.resumeDownload(downloadId) },
        onRetry = { viewModel.resumeDownload(downloadId) },
        onDeleteDownloaded = onDeleteDownloaded
    )
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

/**
 * Title end-padding (quick plan 2026-09-28, unified download look): the
 * active download cluster is gone (Cancel only, rendered in the shared
 * [ActiveDownloadContent] section below), so the title row always reserves
 * the 52dp idle/downloaded/failed icon slot. The [active] parameter is
 * retained for call-site stability; both states return 52.
 */
@Suppress("UNUSED_PARAMETER")
fun titleEndPaddingDp(active: Boolean): Int = 52

/**
 * Pure on-device/session downloaded rule: true when the in-session
 * WorkManager state is terminal-complete (non-null, not active, no error,
 * progress >= 1f) OR the file is already on-device. JVM-testable.
 */
fun isEffectivelyDownloaded(downloadState: DownloadState?, isOnDevice: Boolean): Boolean {
    val active = downloadState != null &&
        (downloadState.isDownloading || downloadState.isPaused) &&
        downloadState.error != "Cancelled"
    val sessionDownloaded = downloadState != null && !active &&
        downloadState.error == null && downloadState.progress >= 1f
    return sessionDownloaded || isOnDevice
}

@Composable
private fun CatalogModelCard(
    entry: AllowlistedModel,
    downloadState: DownloadState?,
    isOnDevice: Boolean = false,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit = {},
    onDeleteDownloaded: () -> Unit = {}
) {
    // A "Cancelled" error is terminal-idle: the partial file is deleted and a
    // fresh Download restarts cleanly.
    val active = downloadState != null &&
        (downloadState.isDownloading || downloadState.isPaused) &&
        downloadState.error != "Cancelled"
    val downloaded = isEffectivelyDownloaded(downloadState, isOnDevice)
    val failed = !active && !downloaded &&
        downloadState?.error != null && downloadState.error != "Cancelled"
    var showCancelConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val details = remember(entry) { expandedText(entry) }
    val expandable = details != null

    if (showDeleteConfirm) {
        WarpedAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_model_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.delete_model_message,
                        entry.displayName,
                        formatFileSize(entry.sizeInBytes)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDeleteDownloaded() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showCancelConfirm) {
        WarpedAlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text(stringResource(R.string.hf_cancel_title)) },
            text = { Text(stringResource(R.string.hf_cancel_msg)) },
            confirmButton = {
                TextButton(
                    onClick = { showCancelConfirm = false; onCancel() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.hf_cancel_dl)) }
            },
            dismissButton = { TextButton(onClick = { showCancelConfirm = false }) { Text(stringResource(R.string.hf_keep)) } }
        )
    }

    // Unified card shared with Models & Endpoints — only the trailing
    // action switches (download cluster here, delete there).
    com.warped.ui.components.ModelCard(
        title = entry.displayName,
        sizeText = formatFileSize(entry.sizeInBytes),
        ramText = entry.ramNote,
        vision = entry.capabilities.vision,
        audio = entry.capabilities.audio,
        reasoning = entry.capabilities.supportsThinking,
        tools = entry.capabilities.supportsFunctionCalling,
        dotConnected = downloaded,
        expandable = expandable,
        trailingActions = {
            if (downloaded) {
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                CatalogDownloadActions(
                    downloadState = downloadState,
                    downloaded = false,
                    failed = failed,
                    onDownload = onDownload
                )
            }
        },
        downloadContent = {
            // Active download: shared linear-bar + status-line + Cancel look.
            // Cancel goes through the cancel-confirm dialog — cancelling
            // deletes the partial file (see dialog copy).
            if (active && downloadState != null) {
                Spacer(Modifier.height(6.dp))
                ActiveDownloadContent(
                    download = downloadState,
                    onCancel = { showCancelConfirm = true },
                    onDeleteIncomplete = { showCancelConfirm = true },
                    onPause = onPause,
                    onResume = onResume,
                    onRetry = onRetry
                )
            }
        },
        // Retained error text (icon form keeps the message for a11y;
        // retry = download icon tap).
        errorText = if (failed) downloadState?.error else null,
        detailsContent = {
            // Blurb constrained to two lines, separated above and below so
            // it never blends into the header or the table. RAM guidance
            // lives next to the size pill; the file name is intentionally
            // not shown.
            if (!entry.blurb.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.blurb,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    )
}

/**
 * Download-state icon cluster (unified download look, quick plan
 * 2026-09-28): downloaded status or idle download / error retry only. The
 * active pause/resume/cancel cluster is gone — active downloads render in
 * the shared ActiveDownloadContent section with Cancel only (D1).
 */
@Composable
private fun CatalogDownloadActions(
    downloadState: DownloadState?,
    downloaded: Boolean,
    failed: Boolean,
    onDownload: () -> Unit
) {
    when {
        downloaded -> Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = stringResource(R.string.hf_downloaded),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
        else -> IconButton(onClick = onDownload) {
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = stringResource(R.string.hf_download_model),
                tint = if (failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        }
    }
}

