package com.warped.ui.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.warped.R
import com.warped.data.grounding.GroundedImages
import com.warped.data.grounding.ImageSaver
import kotlinx.coroutines.launch

/**
 * Quick-task (image-grid): image result grid attached to a grounded answer.
 *
 * Renders below the Fuentes carousel in [MessageBubble]. Thumbs are Coil
 * [AsyncImage] (singleton ImageLoader with the bare OkHttp client — never
 * the authed endpoint client). Rows are plain chunked [Row]s (max 10
 * images upstream) so there is no nested-scroll clash with the transcript
 * list. Tap opens the full-view modal with a DOWNLOAD button (gallery via
 * [ImageSaver], MediaStore on Q+).
 *
 * Compose/Android-framework code: JVM coverage stops at the pure
 * [visibleGridImages] mapping — grid/modal/download need on-device
 * confirmation (thumb sizing, dark theme, MediaStore write + permission
 * UX on targetSdk 35).
 */
@Composable
fun GroundedImageGrid(
    images: List<String>,
    modifier: Modifier = Modifier,
) {
    val visible = remember(images) { GroundedImages.visibleImages(images) }
    if (visible.isEmpty()) return
    var selected by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.images_title),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        visible.chunked(GRID_COLUMNS).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                row.forEach { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(MaterialTheme.shapes.small)
                            .clickable { selected = url },
                    )
                }
                // Pad a short final row so thumbs keep equal widths.
                repeat(GRID_COLUMNS - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    val current = selected
    if (current != null) {
        ImagePreviewModal(
            imageUrl = current,
            onDismiss = { selected = null },
        )
    }
}

/**
 * Render-side mapping lives in [com.warped.data.grounding.GroundedImages]
 * (pure, JVM-tested) — defense-in-depth over the fuse-time gate.
 */

private const val GRID_COLUMNS = 3

@Composable
private fun ImagePreviewModal(
    imageUrl: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saver = remember { ImageSaver() }
    var status by remember { mutableStateOf(ModalStatus.IDLE) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.images_title),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                        )
                    }
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(MaterialTheme.shapes.small),
                ) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                when (status) {
                    ModalStatus.DOWNLOADING -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    ModalStatus.SAVED -> Text(
                        text = stringResource(R.string.image_saved),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    ModalStatus.FAILED -> Text(
                        text = stringResource(R.string.image_save_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    ModalStatus.IDLE -> Unit
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(R.string.image_close))
                    }
                    Button(
                        onClick = {
                            if (status == ModalStatus.DOWNLOADING) return@Button
                            status = ModalStatus.DOWNLOADING
                            scope.launch {
                                status = when (
                                    saver.saveToGallery(context, imageUrl)
                                ) {
                                    ImageSaver.SaveResult.Saved -> ModalStatus.SAVED
                                    ImageSaver.SaveResult.Failed -> ModalStatus.FAILED
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(R.string.image_download))
                    }
                }
            }
        }
    }
}

private enum class ModalStatus {
    IDLE,
    DOWNLOADING,
    SAVED,
    FAILED,
}
