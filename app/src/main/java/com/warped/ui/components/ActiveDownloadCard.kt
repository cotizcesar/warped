package com.warped.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.warped.R
import com.warped.data.local.download.DownloadState

/**
 * Shared active-download content (quick plan 2026-09-28): identical
 * linear-bar + status-line + Cancel visual on both the Models screen
 * ([DownloadCard]) and the catalog ([CatalogModelCard] active branch).
 *
 * Quick-task (download-pause): pause/resume reuses the tested
 * `ModelDownloadManager.pauseDownload/resumeDownload` worker APIs — this
 * composable only adds the [onPause]/[onResume] entry points, wired
 * identically on both screens. The downloading branch shows Pause next to
 * Cancel; the `isPaused` branch shows Resume next to Delete; Cancel/Delete
 * behavior is unchanged.
 *
 * Each call site keeps its own confirm UX via [onCancel]: Models cancels
 * directly, the catalog confirms first ("The partial file will be
 * deleted.").
 */
@Composable
fun ActiveDownloadContent(
    download: DownloadState,
    onCancel: () -> Unit,
    onDeleteIncomplete: () -> Unit,
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    /**
     * Phase 60-02 (API-04): retry after a platform stop (resumes from the
     * persisted checkpoint). Rendered only when [DownloadState.stopReasonCopy]
     * is present, so call sites without retry wiring never show a dead button.
     */
    onRetry: () -> Unit = {}
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            download.fileName.substringAfterLast("/"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        if (download.isDownloading) {
            LinearProgressIndicator(
                progress = { download.progress },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${(download.progress * 100).toInt()}% · ${formatFileSize(download.downloadedBytes)} / " +
                    "${formatFileSize(download.totalBytes)} · ${formatDownloadSpeed(download.speedBytesPerSecond)}",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                IconButton(onClick = onPause) {
                    Icon(
                        imageVector = Icons.Filled.Pause,
                        contentDescription = stringResource(R.string.cd_pause_download)
                    )
                }
            }
        } else if (download.isPaused) {
            Text(
                stringResource(
                    R.string.dl_paused_fmt,
                    formatFileSize(download.downloadedBytes),
                    formatFileSize(download.totalBytes)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFF9800)
            )
            if (download.stopReasonCopy != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    download.stopReasonCopy,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onResume) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.cd_resume_download)
                    )
                }
                OutlinedButton(onClick = onDeleteIncomplete) { Text(stringResource(R.string.delete)) }
            }
        } else {
            Text(
                stringResource(R.string.dl_interrupted_fmt, formatFileSize(download.downloadedBytes)),
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
            if (download.stopReasonCopy != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    download.stopReasonCopy,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                if (download.stopReasonCopy != null) {
                    OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.dl_retry)) }
                }
                OutlinedButton(onClick = onDeleteIncomplete) { Text(stringResource(R.string.delete_partial_file)) }
            }
        }
    }
}

internal fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024 * 1024 -> "%.2f GB".format(bytes.toDouble() / (1024L * 1024 * 1024))
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes.toDouble() / (1024 * 1024))
        bytes >= 1024 -> "%.1f KB".format(bytes.toDouble() / 1024)
        else -> "$bytes B"
    }
}

internal fun formatDownloadSpeed(bytesPerSecond: Long): String {
    return if (bytesPerSecond > 0) {
        "${formatFileSize(bytesPerSecond)}/s"
    } else {
        "--/s"
    }
}
