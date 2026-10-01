package com.warped.ui.chat.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.warped.R
import com.warped.domain.model.GroundedSource
import com.warped.domain.model.GroundedSourceStatus
import kotlinx.coroutines.launch

/**
 * Quick-task (all-sources sheet): bottom sheet listing ALL grounded sources
 * as full-width rows in fetch-block order (ok + omitida interleaved — never
 * reordered).
 *
 * Scaffold mirrors [SourcePreviewSheet]: [ModalBottomSheet] with
 * `skipPartiallyExpanded = true` and surface container color. Pure render
 * over hydrated [GroundedSource] state — props only, zero I/O, zero state
 * change on dismiss (swipe/scrim/back only).
 *
 * Ok rows are the SAME [CompactSourceCard] composable used in the chat
 * carousel (shared code, not copy-paste), presented full-width
 * (`cardWidth = null`) stacked vertically. Taps are identical across
 * hosts: card body opens the per-source preview sheet via [onPreview],
 * thumbnail fires the guarded browser intent via [onOpenBrowser]. Omitida
 * rows render struck/disabled with no tap, consistent with the chat
 * carousel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllSourcesSheet(
    sources: List<GroundedSource>,
    onDismiss: () -> Unit,
    onOpenBrowser: (url: String) -> Unit,
    onPreview: (source: GroundedSource, number: Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // API-02: explicit system-bars insets (never draw-under-bars, no content
    // padding hacks). Same value as the ModalBottomSheet default, stated so
    // the edge-to-edge contract is visible at the call site.
    val sheetWindowInsets: WindowInsets = BottomSheetDefaults.windowInsets

    // API-03: system back hides then dismisses via the same onDismiss path
    // as swipe/scrim — gesture and button identical.
    val backScope = rememberCoroutineScope()
    BackHandler {
        backScope.launch {
            try {
                sheetState.hide()
            } finally {
                onDismiss()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { sheetWindowInsets }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.all_sources_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            val bodyScroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(bodyScroll)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sources.forEachIndexed { index, source ->
                    if (source.status == GroundedSourceStatus.OK) {
                        CompactSourceCard(
                            source = source,
                            number = index + 1,
                            cardWidth = null,
                            modifier = Modifier.fillMaxWidth(),
                            onPreview = { onPreview(source, index + 1) },
                            onOpenBrowser = onOpenBrowser,
                        )
                    } else {
                        Text(
                            text = stringResource(
                                R.string.fuente_skipped_fmt,
                                index + 1,
                                source.url,
                            ),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = TextDecoration.LineThrough,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Quick-task (all-sources sheet): header-icon visibility rule. The
 * "View all sources" icon renders only when ≥2 sources exist (ok + omitida
 * count — single source needs no drawer; zero sources renders no header at
 * all via the existing `any { it.clickable }` gate).
 */
fun shouldShowViewAll(fuenteList: List<FuenteItem>): Boolean = fuenteList.size >= 2
