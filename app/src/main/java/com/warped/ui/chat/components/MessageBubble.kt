package com.warped.ui.chat.components

import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import com.warped.ui.components.WarpedAlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.warped.R
import com.warped.domain.model.ChatMessage
import com.warped.domain.model.GroundedSource
import com.warped.domain.model.ModelOnlyNotice
import com.warped.domain.model.Role
import com.warped.domain.model.SyntaxTheme
import com.warped.domain.model.parseToolRow
import com.warped.domain.model.toolDisplayName
import com.warped.domain.model.toolDisplayNameCapitalized
import kotlinx.coroutines.launch

@Composable
fun MessageBubble(
    message: ChatMessage,
    isStreaming: Boolean = false,
    codeTheme: SyntaxTheme = SyntaxTheme.MONOKAI,
    codeFontScale: Float = 1.0f,
    isValidatedOnline: Boolean = false,
    isFetchingWeb: Boolean = false,
    isGenerating: Boolean = false,
    onRetry: (messageId: String) -> Unit = {},
    // Phase 68 (VMSG-06): history voice playback. Resolved by the caller
    // (ChatScreen compares playingMessageId == message.id); per-row
    // duration reads message.audioDurationMs. Defaults keep previews and
    // non-voice callers compiling unchanged.
    isVoicePlaying: Boolean = false,
    voicePositionMs: Int = 0,
    voiceFileMissing: Boolean = false,
    onPlayVoice: () -> Unit = {},
    onPauseVoice: () -> Unit = {},
    // Voice-bubble scrub: caller seeks to an absolute ms position.
    // Defaults keep previews and non-voice callers compiling unchanged.
    onSeekVoice: (Int) -> Unit = {},
    // Phase 69 Plan 02 (VMSG-07): transcript caption source override.
    // Defaults to null so previews and non-voice callers compile unchanged;
    // the caption reads this first, then message.transcript hydrated from
    // history load.
    transcript: String? = null,
) {
    val isUser = message.role == Role.USER
    // Phase 49 (DEL-01): persisted tool rows render as collapsed transcript
    // rows mirroring the Thinking panel — outside any bubble. Read-only;
    // no new tool calls can be produced.
    if (message.role == Role.TOOL) {
        ToolResultRow(content = message.content)
        return
    }
    var showReasoning by remember { mutableStateOf(false) }
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedText = stringResource(R.string.copied)
    // Quick-task (citation taps): Fuentes resolution lives above the
    // content render so the assistant-answer MarkdownText can plumb
    // `onCitationClick` into the same preview state the cards use.
    // Numbering stays 1-based fetch-block order (matches card taps).
    val sourceDetails = message.groundedSourceDetails
    val fuenteList = remember(sourceDetails, message.groundedSources) {
        fuenteItems(details = sourceDetails, legacyUrls = message.groundedSources)
    }
    // Local sheet state only — never the hydrated data itself, so
    // recomposition always re-resolves from the message param. Never
    // lifted into the ViewModel.
    var previewSource by remember { mutableStateOf<GroundedSource?>(null) }
    var previewNumber by remember { mutableIntStateOf(1) }
    // A null callback renders plain (no annotations, no affordance) on
    // no-source turns; out-of-range/omitida taps resolve to null and are
    // ignored — no crash, no sheet.
    val citationClick: ((Int) -> Unit)? =
        if (fuenteList.any { it.clickable }) {
            { n ->
                val item = fuenteList.firstOrNull { it.number == n }
                if (item != null && item.clickable) {
                    val resolved = if (sourceDetails.isNotEmpty()) {
                        previewForTap(sourceDetails, n - 1)
                    } else {
                        // Legacy ok-only rows predate hydrated details:
                        // same text-only construction as the card path.
                        GroundedSource(url = item.url)
                    }
                    if (resolved != null) {
                        previewSource = resolved
                        previewNumber = n
                    }
                }
            }
        } else {
            null
        }
    if (isStreaming && !message.reasoning.isNullOrBlank()) {
        showReasoning = true
    }

    @OptIn(ExperimentalFoundationApi::class)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    clipboardManager.setText(AnnotatedString(message.content))
                    Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
                }
            ),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Phase 50 (WEB-06): UI-rendered model-only banner — never model
        // text, never error text as context. 4dp above the qualified
        // assistant message; grounded answers render no banner.
        if (!isUser && message.modelOnlyNotice != null) {
            ModelOnlyBanner(
                notice = message.modelOnlyNotice,
                totalSources = message.modelOnlySourceCount,
                isValidatedOnline = isValidatedOnline,
                isFetchingWeb = isFetchingWeb,
                isGenerating = isGenerating,
                onRetry = { onRetry(message.id) }
            )
            Spacer(Modifier.height(4.dp))
        }
        Surface(
            color = if (isUser) Color(0xFF121212) else Color.Transparent,
            // Voice messages render rounder (20dp pill feel); everything
            // else keeps the 12dp bubble.
            shape = RoundedCornerShape(if (isUser && message.audioPath != null) 20.dp else 12.dp),
            // Assistant messages use the full chat width (ChatGPT-style
            // plain text, no bubble cap) and grow line-by-line with a
            // smooth size transition while streaming — the container
            // expands instead of jumping when text wraps. User bubbles
            // keep the 340dp cap.
            modifier = if (isUser) {
                Modifier.widthIn(max = 340.dp)
            } else {
                Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            }
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = if (isUser) 12.dp else 0.dp,
                    vertical = 10.dp
                )
            ) {
                if (!isUser && !message.reasoning.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .clickable { showReasoning = !showReasoning }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.thinking),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF545450)
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = if (showReasoning) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = if (showReasoning) stringResource(R.string.bubble_hide_reasoning) else stringResource(R.string.bubble_show_reasoning),
                            tint = Color(0xFF545450),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    AnimatedVisibility(
                        visible = showReasoning,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        val scrollState = rememberScrollState()
                        if (isStreaming) {
                            LaunchedEffect(message.reasoning) {
                                scrollState.animateScrollTo(scrollState.maxValue)
                            }
                        }
                        val modifier = if (isStreaming) {
                            Modifier
                                .padding(start = 16.dp)
                                .heightIn(max = 72.dp)
                                .verticalScroll(scrollState)
                        } else {
                            Modifier.padding(start = 16.dp)
                        }
                        Surface(
                            color = Color.Transparent,
                            modifier = modifier
                        ) {
                            SelectionContainer {
                                MarkdownText(
                                    text = message.reasoning,
                                    baseColor = Color(0xFF545450),
                                    modifier = Modifier
                                        .padding(vertical = 4.dp),
                                    fontStyle = FontStyle.Italic,
                                    codeTheme = codeTheme,
                                    codeFontScale = codeFontScale,
                                    isStreaming = isStreaming
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }

                // PERF-03: extracted subcomposable. Decoding images lives in
                // its own remember(dataUrl) scope so streaming-text recomposition
                // doesn't churn the image bitmaps.
                if (isUser && message.imageUris.isNotEmpty()) {
                    MessageImageStack(imageUris = message.imageUris)
                }

                // Phase 68 (VMSG-06): own-voice bubble player row — after
                // images, above the caption Text so Phase 69 transcript
                // captions slot underneath without re-layout. Players ONLY
                // on own (USER) sent bubbles; received/model-side audio is
                // future VF-03, out of scope.
                if (isUser && message.audioPath != null) {
                    VoicePlayerRow(
                        durationMs = message.audioDurationMs,
                        isPlaying = isVoicePlaying,
                        positionMs = voicePositionMs,
                        fileMissing = voiceFileMissing,
                        onPlay = onPlayVoice,
                        onPause = onPauseVoice,
                        onSeek = onSeekVoice,
                    )
                    // Phase 69 Plan 02 (VMSG-07): transcript caption
                    // UNDERNEATH the player row (Phase 68 chrome untouched —
                    // player above, caption below). NULL/blank renders the
                    // honest duration-only fallback (never empty, never a
                    // fake transcript). Missing-file rows stay caption-free;
                    // assistant bubbles never reach this branch (isUser).
                    if (!voiceFileMissing) {
                        Spacer(Modifier.height(4.dp))
                        VoiceTranscriptCaption(
                            transcript = transcript ?: message.transcript,
                            durationMs = message.audioDurationMs,
                            messageKey = message.id,
                        )
                    }
                }

                if (message.content.isNotBlank()) {
                    if (!isUser) {
                        SelectionContainer {
                            MarkdownText(
                                text = message.content + if (isStreaming) "▌" else "",
                                baseColor = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16f,
                                modifier = Modifier.fillMaxWidth(),
                                codeTheme = codeTheme,
                                codeFontScale = codeFontScale,
                                isStreaming = isStreaming,
                                onCitationClick = citationClick,
                            )
                        }
                    } else {
                        SelectionContainer {
                            Text(
                                text = message.content + if (isStreaming) "▌" else "",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Quick-task (DDG-default + sources carousel): clickable numbered
        // Fuentes render as a horizontal carousel of 272dp compact cards in
        // fetch-block order covering all N sources (ok + omitida from
        // hydrated details). Ok cards open the SourcePreviewSheet on body
        // tap (thumb tap fires the guarded browser intent); omitida rows
        // render struck/disabled below the carousel with no preview so no
        // source is silently dropped. Zero ok sources renders no block at
        // all (unchanged Phase 50 behavior).
        // Quick-task (all-sources sheet): ephemeral open flag only — never
        // the hydrated data, same discipline as previewSource above.
        var showAllSources by remember { mutableStateOf(false) }
        // Quick-task (phase53-trio): the block renders whenever rows exist
        // — all-omitida turns show struck rows + count instead of hiding
        // (honesty: no silently dropped sources). Title + carousel only
        // when ok rows exist; struck omitida rows keep their existing
        // language in both variants.
        if (fuentesVisible(fuenteList, isUser)) {
            if (omitidaOnly(fuenteList)) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.fuentes_all_skipped_fmt, fuenteList.size),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
            Spacer(Modifier.height(4.dp))
            // Quick-task (all-sources sheet): title + trailing "View all
            // sources" icon on the same row. The icon renders only when
            // ≥2 sources exist (single source needs no drawer).
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.sources_title),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (shouldShowViewAll(fuenteList)) {
                    IconButton(onClick = { showAllSources = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = stringResource(R.string.cd_view_all_sources),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // Clickable ok items first (carousel); [N] numbering stays in
            // fetch-block order so cards match the fused Source [N] block
            // and the answer-text citations.
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(
                    items = fuenteList.mapIndexedNotNull { index, item ->
                        if (item.clickable) index to item else null
                    },
                    key = { (_, item) -> item.number },
                ) { (index, item) ->
                    // Ok sources render compact carousel cards reusing the
                    // OgSourceCard pieces; tap body opens the sheet, thumb
                    // fires the guarded browser intent.
                    val cardSource = if (sourceDetails.isNotEmpty()) {
                        previewForTap(sourceDetails, index)
                    } else {
                        // Legacy ok-only rows predate hydrated details:
                        // text-only card (title falls back to host); tap
                        // opens the sheet with the empty-extract copy and
                        // the browser button available.
                        GroundedSource(url = item.url)
                    } ?: return@items
                    CompactSourceCard(
                        source = cardSource,
                        number = item.number,
                        onPreview = {
                            previewSource = cardSource
                            previewNumber = item.number
                        },
                        onOpenBrowser = { url -> openUrlInBrowser(context, url) },
                    )
                }
            }
            }
            // Omitida rows stay as struck text below the carousel (and as
            // the full honesty block on all-omitida turns).
            fuenteList.filter { !it.clickable }.forEach { item ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.fuente_skipped_fmt, item.number, item.url),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = TextDecoration.LineThrough,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                )
            }
        }
        // Quick-task (image-grid): image results grid below the Fuentes
        // carousel on image-intent turns. Ephemeral render-only state
        // (message.groundedImages) — tap opens the modal + gallery
        // download. Empty on non-intent turns: renders nothing.
        if (!isUser && message.groundedImages.isNotEmpty()) {
            GroundedImageGrid(images = message.groundedImages)
        }
        // Sheet host: tap an ok item sets previewSource, dismiss nulls it.
        // Reads the sheet props from local state resolved above — zero I/O.
        val currentPreview = previewSource
        if (currentPreview != null) {
            SourcePreviewSheet(
                source = currentPreview,
                number = previewNumber,
                onDismiss = { previewSource = null },
                onOpenBrowser = { url ->
                    // T-53-09/T-53-10 + T-58-08: single guarded gate in
                    // BrowserIntents; dismiss the sheet only when the
                    // browser intent actually launched.
                    if (openUrlInBrowser(context, url)) {
                        previewSource = null
                    }
                }
            )
        }
        // Quick-task (all-sources sheet): host next to the single-source
        // sheet above. Sources mirror the carousel's resolution so row
        // numbering matches fetch-block order: hydrated details when
        // present (omitida rows pass through for struck rendering),
        // legacy text-only rows otherwise. Dismiss-only-on-launch, same
        // as the single-source sheet. Drawer rows are the same
        // CompactSourceCard as the carousel (full-width): card tap opens
        // the preview sheet (drawer dismissed first — no stacked sheets),
        // thumb tap fires the guarded browser intent.
        if (showAllSources) {
            val allSheetSources = if (sourceDetails.isNotEmpty()) {
                sourceDetails
            } else {
                fuenteList.map { item -> GroundedSource(url = item.url) }
            }
            AllSourcesSheet(
                sources = allSheetSources,
                onDismiss = { showAllSources = false },
                onOpenBrowser = { url ->
                    if (openUrlInBrowser(context, url)) {
                        showAllSources = false
                    }
                },
                onPreview = { src, n ->
                    showAllSources = false
                    previewSource = src
                    previewNumber = n
                },
            )
        }

        // Stats below the bubble
        if (!isUser && !message.stats.isNullOrBlank()) {
            Text(
                text = message.stats,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF545450),
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

/**
 * Phase 50 (WEB-06): model-only notice banner. UI-rendered from ephemeral
 * state (never model-generated, never a Snackbar, not dismissible) — scrolls
 * with its message inside MessageBubble.
 *
 * Phase 52 (FETCH-02): renders ONLY on all-fail (the ViewModel never sets
 * a notice on partial grounding). The fetch-failure copy pluralizes when
 * M > 1 attempted URLs died; OFFLINE copy is unchanged (worst-case
 * collapse already resolved upstream).
 */
@Composable
private fun ModelOnlyBanner(
    notice: ModelOnlyNotice,
    totalSources: Int = 1,
    isValidatedOnline: Boolean = false,
    isFetchingWeb: Boolean = false,
    isGenerating: Boolean = false,
    onRetry: () -> Unit = {},
) {
    // Phase 54 (RETRY-01): queued OFFLINE row — existing v2.2 copy + the
    // " En espera." suffix on the same row, plus a trailing-slot Reintentar
    // TextButton gated on validated-online && idle. FETCH_FAILED branch
    // below is byte-identical (OFFLINE-only scope).
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = when (notice) {
                ModelOnlyNotice.OFFLINE ->
                    stringResource(R.string.bubble_offline_queued)
                ModelOnlyNotice.FETCH_FAILED ->
                    if (totalSources > 1) {
                        stringResource(R.string.bubble_fetch_failed_many)
                    } else {
                        stringResource(R.string.bubble_fetch_failed_one)
                    }
                // Phase 57 (57-02): tools[] rejected by the endpoint —
                // one retry without tools, model-only answer. Copy
                // mirrors ToolCapabilityMatrix.TOOLS_UNSUPPORTED_NOTICE
                // (routing keys on the typed StreamToken.ToolsUnsupported
                // token, never on this string; this branch renders the
                // same words in the banner slot). Ends with the next
                // step — retrying the same endpoint cannot help, so no
                // Retry button (OFFLINE-only gate below untouched).
                ModelOnlyNotice.TOOLS_UNSUPPORTED ->
                    stringResource(R.string.bubble_tools_unsupported)
            },
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false)
        )
        // WR-03: hidden while a retry fetch is in flight AND while
        // inference is streaming — mirrors the retryGrounding VM guard
        // so the button is never visible-but-dead.
        if (notice == ModelOnlyNotice.OFFLINE && isValidatedOnline && !isFetchingWeb && !isGenerating) {
            val retryHint = stringResource(R.string.bubble_retry_hint)
            TextButton(
                onClick = onRetry,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .semantics {
                        contentDescription = retryHint
                    }
            ) {
                Text(
                    text = stringResource(R.string.bubble_retry),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Phase 49 (DEL-01): collapsed transcript row for a completed tool call
 * (role:tool), mirroring the Thinking panel above byte-for-byte in styling.
 * Collapsed by default; expanded shows the truncated result (~200 chars).
 * Malformed payloads render as plain muted text without crashing history load.
 */
@Composable
fun ToolResultRow(
    content: String,
    codeTheme: SyntaxTheme = SyntaxTheme.MONOKAI,
    codeFontScale: Float = 1.0f,
) {
    val (toolId, summary) = remember(content) { parseToolRow(content) }
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp)
                .semanticsForToolResult(toolId, expanded),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatToolTranscriptHeader(toolId),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF545450)
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFF545450),
                modifier = Modifier.size(16.dp)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Surface(
                color = Color.Transparent,
                modifier = Modifier.padding(start = 16.dp)
            ) {
                SelectionContainer {
                    MarkdownText(
                        text = truncateToolSummary(summary),
                        baseColor = Color(0xFF545450),
                        modifier = Modifier.padding(vertical = 4.dp),
                        fontStyle = FontStyle.Italic,
                        codeTheme = codeTheme,
                        codeFontScale = codeFontScale
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

private fun Modifier.semanticsForToolResult(toolId: String, expanded: Boolean): Modifier =
    this.then(
        Modifier.semantics {
            contentDescription = formatToolTranscriptA11y(toolId, expanded)
        }
    )

/**
 * Phase 49 (DEL-01): read-only legacy transcript header. The `"Used {Display}"`
 * header renders, never stored, so history reload shows the collapsed rows.
 */
private const val TOOL_TRANSCRIPT_TEMPLATE = "Used {Display}"
private const val TOOL_TRANSCRIPT_A11Y_TEMPLATE = "Tool result from {display}, {state}, tap to toggle"

private fun formatToolTranscriptHeader(toolId: String): String =
    TOOL_TRANSCRIPT_TEMPLATE.replace("{Display}", toolDisplayNameCapitalized(toolId))

private fun formatToolTranscriptA11y(toolId: String, expanded: Boolean): String =
    TOOL_TRANSCRIPT_A11Y_TEMPLATE
        .replace("{display}", toolDisplayName(toolId))
        .replace("{state}", if (expanded) "expanded" else "collapsed")

/** Truncated results cap at ~200 chars + "…". */
private fun truncateToolSummary(text: String, maxChars: Int = 200): String =
    if (text.length <= maxChars) text else text.take(maxChars) + "…"

/**
 * Bounded in-memory decode for base64 data-URL images (Play bitmap-memory
 * warning). Bounds-first pass + power-of-2 `inSampleSize` caps the decoded
 * bitmap at [MAX_MESSAGE_IMAGE_DIMENSION_PX] on the long edge, so a
 * multi-megapixel camera photo attached to a message can never OOM the
 * transcript. Null on any failure (caller renders nothing, as before).
 */
private const val MAX_MESSAGE_IMAGE_DIMENSION_PX = 1024

private fun decodeSampledBitmap(bytes: ByteArray, maxDimension: Int): android.graphics.Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    } catch (_: Exception) { null }
}

@Composable
private fun MessageImageStack(imageUris: List<String>) {    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        imageUris.forEach { dataUrl ->
            var showFullImage by remember { mutableStateOf(false) }
            val bitmap = remember(dataUrl) {
                try {
                    val base64 = dataUrl.substringAfter("base64,")
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    decodeSampledBitmap(bytes, MAX_MESSAGE_IMAGE_DIMENSION_PX)
                } catch (_: Exception) { null }
            }
            if (showFullImage) {
                WarpedAlertDialog(
                    onDismissRequest = { showFullImage = false },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showFullImage = false }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.cd_close),
                                tint = androidx.compose.ui.graphics.Color.White
                            )
                        }
                    },
                    text = {
                        bitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = stringResource(R.string.cd_full_image),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                )
            }
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = stringResource(R.string.cd_image_tap_enlarge),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { showFullImage = true },
                    contentScale = ContentScale.FillWidth
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

/**
 * Phase 68 (VMSG-06): own-voice bubble player row. Renders inside the exact
 * same user-bubble chrome (container, shape, padding untouched).
 *
 * Duration convention (locked, same as the draft card): the readout is
 * ALWAYS the total m:ss — live position shows only as progress fill
 * (kept across pause for one-tap resume, clamped 0..1 against absurd
 * stored durations).
 * A missing file renders the informational unavailable row in the same
 * chrome — onSurfaceVariant icon + text, no play button, never error-red,
 * never a silent drop, never a crash.
 */
@Composable
fun VoicePlayerRow(
    durationMs: Long,
    isPlaying: Boolean,
    positionMs: Int,
    fileMissing: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (fileMissing) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.VolumeOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.voice_msg_clip_unavailable),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val totalSec = (durationMs / 1000).toInt().coerceAtLeast(0)
    // Draft-card parity: row-level TalkBack state so the bubble announces
    // play state without focusing the inner button first.
    val rowState = stringResource(
        if (isPlaying) R.string.voice_msg_pause_message
        else R.string.voice_msg_play_message,
        totalSec / 60,
        totalSec % 60,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.semantics { stateDescription = rowState },
    ) {
        IconButton(
            onClick = { if (isPlaying) onPause() else onPlay() },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                stringResource(
                    if (isPlaying) R.string.voice_msg_pause_message
                    else R.string.voice_msg_play_message,
                    totalSec / 60,
                    totalSec % 60,
                ),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(4.dp))
        // Scrubbable progress: tap jumps, horizontal drag previews and
        // seeks on release. The touch target (28dp box) is taller than
        // the bar visual so a finger lands it easily; the bar itself
        // keeps the LinearProgressIndicator look. While scrubbing, the
        // preview fraction wins over the polled position; release hands
        // the absolute ms to the VM (which also refreshes its flow, so
        // the bar never snaps back).
        var barWidthPx by remember { mutableIntStateOf(1) }
        var scrubFraction by remember { mutableStateOf<Float?>(null) }
        val baseFraction = if (durationMs > 0) {
            (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        } else {
            0f
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .onSizeChanged { barWidthPx = it.width.coerceAtLeast(1) },
            contentAlignment = Alignment.CenterStart,
        ) {
            LinearProgressIndicator(
                progress = { scrubFraction ?: baseFraction },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Spacer(
                Modifier
                    .matchParentSize()
                    .pointerInput(durationMs) {
                        detectTapGestures { offset ->
                            val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                            onSeek((fraction * durationMs).toInt())
                        }
                    }
                    .pointerInput(durationMs) {
                        var dragX = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { offset -> dragX = offset.x },
                            onDragCancel = { scrubFraction = null },
                            onDragEnd = {
                                scrubFraction?.let { onSeek((it * durationMs).toInt()) }
                                scrubFraction = null
                            },
                        ) { _, dragAmount ->
                            dragX += dragAmount
                            scrubFraction = (dragX / barWidthPx).coerceIn(0f, 1f)
                        }
                    }
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "%d:%02d".format(totalSec / 60, totalSec % 60),
            style = MaterialTheme.typography.labelLarge.copy(
                fontFeatureSettings = "tnum"
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Phase 69 Plan 02 (VMSG-07): own-voice bubble transcript caption. Label
 * 14sp in onSurfaceVariant (the existing duration-readout convention):
 * non-blank transcripts cap at 2 lines with ellipsis plus a
 * primary-tinted expand/collapse affordance shown ONLY when the text
 * exceeds 2 lines (latched from onTextLayout overflow — expanded text
 * never overflows, so a plain read would hide the collapse affordance). NULL/blank
 * renders nothing (the row's m:ss readout already covers duration).
 *
 * Expansion state is plain remember keyed by message id — rotation resets
 * to collapsed, acceptable per UI-SPEC.
 */
@Composable
private fun VoiceTranscriptCaption(
    transcript: String?,
    durationMs: Long,
    messageKey: String,
) {
    // No transcript → no caption line. The player row already shows the
    // total m:ss readout, so the old duration-only fallback
    // ("0:07 voice message") was redundant — removed per design. Real
    // transcripts still render below.
    if (transcript.isNullOrBlank()) return
    var expanded by remember(messageKey) { mutableStateOf(false) }
    var overflowed by remember(messageKey) { mutableStateOf(false) }
    Text(
        text = transcript,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = if (expanded) Int.MAX_VALUE else 2,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout ->
            if (layout.hasVisualOverflow) overflowed = true
        },
    )
    if (overflowed) {
        val toggleStateDescription = stringResource(
            if (expanded) R.string.voice_msg_transcript_expanded
            else R.string.voice_msg_transcript_collapsed
        )
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.sizeIn(minHeight = 48.dp).semantics {
                stateDescription = toggleStateDescription
            },
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(
                text = stringResource(
                    if (expanded) R.string.voice_msg_show_less
                    else R.string.voice_msg_show_more
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
