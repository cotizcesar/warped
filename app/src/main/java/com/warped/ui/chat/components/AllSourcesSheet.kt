package com.warped.ui.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.warped.R
import com.warped.domain.model.GroundedSource
import com.warped.domain.model.GroundedSourceStatus
import com.warped.ui.theme.OgCardDark

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
 * Ok rows reuse the 2-col card visual (favicon/thumb, title, URL,
 * description, muted, ellipsis) with the locked neutral container
 * ([OgCardDark] dark / M3 surfaceVariant light — zero new color constants,
 * no accent tint). Text-only sources render without the thumb slot (same
 * silent-collapse fallback as the carousel cards). Row tap fires the
 * guarded browser intent via [onOpenBrowser] — single level, no nested
 * detail navigation (per-source preview stays reachable via card tap in
 * chat). Omitida rows render struck/disabled with no tap, consistent with
 * the chat carousel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllSourcesSheet(
    sources: List<GroundedSource>,
    onDismiss: () -> Unit,
    onOpenBrowser: (url: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
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
                        AllSourcesRow(
                            source = source,
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
 * Quick-task (all-sources sheet): one full-width ok row reusing the 2-col
 * card visual — left thumb, right title (Semibold, ≤2 lines) + URL (muted,
 * 1 line) + description (muted, ≤2 lines). Whole-row tap opens the URL in
 * the browser directly via [onOpenBrowser] (guarded intent, same as the
 * carousel thumb rule).
 */
@Composable
private fun AllSourcesRow(
    source: GroundedSource,
    onOpenBrowser: (url: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayTitle = ogDisplayTitle(source.ogTitle, source.url)
    val desc = ogDisplayDescription(source.ogDescription, source.snippet)
    val gatedImage = gatedHttpImageUrl(source.ogImageUrl)
        ?: faviconFallbackUrl(source.url)?.let(::gatedHttpImageUrl)
    var imageFailed by remember(gatedImage) { mutableStateOf(false) }
    val showThumb = gatedImage != null && !imageFailed
    val container = if (isSystemInDarkTheme()) {
        OgCardDark
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val openBrowserCd = stringResource(R.string.cd_open_in_browser)

    Surface(
        color = container,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = { onOpenBrowser(browserTarget(source)) })
            .semantics {
                contentDescription = openBrowserCd
            },
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showThumb) {
                OgThumb(
                    imageUrl = gatedImage,
                    contentDescription = null,
                    onError = { imageFailed = true },
                )
                Spacer(Modifier.width(8.dp))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = displayTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = source.url,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (desc != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = desc,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
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
