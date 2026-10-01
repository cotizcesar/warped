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
                        isOnDevice = entry.modelFile in downloadedFileNames,
                        onDownload = { viewModel.startDownload(entry) },
                        onCancel = { viewModel.cancelDownload(downloadId) },
                        onPause = { viewModel.pauseDownload(downloadId) },
                        onResume = { viewModel.resumeDownload(downloadId) },
                        onRetry = { viewModel.resumeDownload(downloadId) }
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
    onRetry: () -> Unit = {}
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
    var expanded by remember { mutableStateOf(false) }
    val details = remember(entry) { expandedText(entry) }
    val expandable = details != null

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

    Card(
        modifier = Modifier.fillMaxWidth()
            .then(
                if (expandable) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = if (expanded) stringResource(R.string.hf_collapse) else stringResource(R.string.hf_expand)
                    ) { expanded = !expanded }
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2B2B29)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Collapsed row 1: title + download-state icon cluster (overlay:
            // title alone defines the row height; actions float centered-end
            // on top, free to bleed symmetrically into card padding).
            // Unified download look: the active cluster is gone — active
            // downloads render in the shared ActiveDownloadContent section
            // below, so the title always reserves the 52dp idle slot.
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = titleEndPaddingDp(active).dp)
                )
                Box(Modifier.align(Alignment.CenterEnd)) {
                    CatalogDownloadActions(
                        downloadState = downloadState,
                        downloaded = downloaded,
                        failed = failed,
                        onDownload = onDownload
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
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
            // Active download: shared linear-bar + status-line + Cancel look
            // (identical to the Models screen DownloadCard). Cancel and
            // Delete both go through the cancel-confirm dialog — cancelling
            // deletes the partial file (see dialog copy).
            if (active) {
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
            // Retained error text (icon form keeps the message for a11y;
            // retry = download icon tap).
            if (failed) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = downloadState.error,
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
                // Capability table (LM-Studio-style): what each badge means.
                // Modalities the model lacks show "–" instead of a status.
                Spacer(Modifier.height(4.dp))
                com.warped.ui.components.CapabilityTable(
                    vision = entry.capabilities.vision,
                    audio = entry.capabilities.audio,
                    reasoning = entry.capabilities.supportsThinking,
                    tools = entry.capabilities.supportsFunctionCalling
                )
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

/**
 * Capability icons reusing [CapabilityIconBadge] iconography (vision/audio/
 * thinking only — tools is never shown on catalog cards per Phase 49 DEL-01).
 */
@Composable
private fun CatalogCapabilityIcons(entry: AllowlistedModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (entry.capabilities.vision) {
            CapabilityIconBadge(
                icon = Icons.Filled.Visibility,
                contentDescription = stringResource(R.string.badge_vision),
                color = Color(0xFF64B5F6)
            )
        }
        if (entry.capabilities.audio) {
            CapabilityIconBadge(
                icon = Icons.Filled.Audiotrack,
                contentDescription = stringResource(R.string.badge_audio),
                color = Color(0xFF4CAF50)
            )
        }
        if (entry.capabilities.supportsThinking) {
            CapabilityIconBadge(
                icon = Icons.Filled.Psychology,
                contentDescription = stringResource(R.string.cap_reasoning),
                color = Color(0xFFFF9800)
            )
        }
    }
}

