package com.warped.ui.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.warped.domain.model.GroundedSource
import com.warped.domain.model.GroundedSourceStatus

/**
 * Phase 53 (SRC-01/02/03): bottom-sheet preview of one grounded source.
 *
 * Pure render over hydrated [GroundedSource] state — props only, zero I/O.
 * Never re-fetches on open (offline-safe); never renders raw HTML (the
 * extracted text reaching here is already sanitized upstream). Dismiss via
 * swipe or system back performs no state change: preview never edits chat
 * or sources.
 *
 * Scaffold copied from [ModelSelectorSheet]: [ModalBottomSheet] with
 * `skipPartiallyExpanded = true` and surface container color.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcePreviewSheet(
    source: GroundedSource,
    number: Int,
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
            // Header block: title + [N] badge + resolved URL line.
            Text(
                text = "Fuente $number",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = "[$number]",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = source.url,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Scrollable body: persisted extracted text verbatim, never
            // truncated here (the Phase 52 truncado suffix is preserved as
            // part of the text). weight(1f, fill = false) keeps the action
            // row sticky while the sheet never grows past full-expanded.
            val bodyScroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(bodyScroll),
            ) {
                Text(
                    text = if (isEmptyExtract(source)) {
                        EMPTY_EXTRACT_COPY
                    } else {
                        source.extractedText.orEmpty()
                    },
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Sticky bottom action row.
            FilledTonalButton(
                onClick = { onOpenBrowser(source.url) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .heightIn(min = 44.dp),
            ) {
                Text(text = "Abrir en navegador")
            }
        }
    }
}

/** UI-SPEC empty-extract copy (Spanish). Browser button stays available. */
const val EMPTY_EXTRACT_COPY =
    "No se pudo extraer texto de esta página. Abre la página en el navegador para ver el contenido."

/**
 * Phase 53: render item for one Fuentes row. [clickable] is true only for ok
 * rows; omitida rows render struck/disabled with no preview.
 */
data class FuenteItem(
    val number: Int,
    val url: String,
    val clickable: Boolean,
)

/**
 * Phase 53: Fuentes rows in fetch-block order covering all N sources.
 * Prefers hydrated [details] (ok + omitida); falls back to the legacy
 * ok-only [legacyUrls] render list when details are absent (e.g. pre-53-02
 * history or tests).
 */
fun fuenteItems(
    details: List<GroundedSource>,
    legacyUrls: List<String>,
): List<FuenteItem> =
    if (details.isNotEmpty()) {
        details.mapIndexed { i, s ->
            FuenteItem(
                number = i + 1,
                url = s.url,
                clickable = s.status == GroundedSourceStatus.OK,
            )
        }
    } else {
        legacyUrls.mapIndexed { i, url ->
            FuenteItem(number = i + 1, url = url, clickable = true)
        }
    }

/**
 * Phase 53: tap resolution — an ok item maps to its preview details, an
 * omitida item (or out-of-range index) maps to null so the sheet never
 * opens for rows with no preview text.
 */
fun previewForTap(details: List<GroundedSource>, index: Int): GroundedSource? {
    val s = details.getOrNull(index) ?: return null
    return if (s.status == GroundedSourceStatus.OK) s else null
}

/** Phase 53: empty-extract selects the empty-copy path (button stays). */
fun isEmptyExtract(source: GroundedSource): Boolean =
    source.extractedText.isNullOrBlank()

/**
 * Phase 53 (T-53-09/T-53-10): the browser target is the fetcher-resolved
 * [GroundedSource.url] only — never raw user-pasted text, never extracted
 * text. The intent carries the URL alone.
 */
fun browserTarget(source: GroundedSource): String = source.url
