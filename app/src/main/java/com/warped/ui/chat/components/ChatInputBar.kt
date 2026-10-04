package com.warped.ui.chat.components

import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import coil3.compose.AsyncImage
import com.warped.R
import com.warped.ui.chat.voice.GateState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputBar(
    text: String,
    isGenerating: Boolean,
    canSend: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modelHasVision: Boolean = true,
    onAddImage: () -> Unit = {},
    attachedImages: List<Uri> = emptyList(),
    onRemoveImage: (Int) -> Unit = {},
    // Phase 69 Plan 01 (VMSG-04/08): voice-send gate. The VM
    // voiceSendGate flow is the single source of truth. A gated button
    // is HIDDEN (no hint, no affordance) — unsupported models and
    // remote endpoints show no voice-send surface at all. Send-path
    // gate blocks (draft kept + explainer) still live in the VM.
    voiceGate: GateState = GateState.Allowed,
    onAudioRecorded: ((ByteArray) -> Unit)? = null,
    onAudioRecordingChanged: ((Boolean) -> Unit)? = null,
    // Phase 65 (VOICE-02 UI): dictation mic affordance. speechAvailable
    // gates visibility (hidden without a recognizer); isListening swaps
    // the icon to a stop toggle; onMicClick owns the permission gate.
    speechAvailable: Boolean = false,
    isListening: Boolean = false,
    onMicClick: () -> Unit = {},
    // Phase 65 fix (WR-03): cursor reporting for append-at-cursor
    // dictation (UI-SPEC section 3). Fires on every selection change;
    // the ViewModel inserts dictated text at the last reported position.
    onCursorChange: (Int) -> Unit = {},
    // Phase 67 (VMSG-01 tracer): voice-send toggle. Tapping toggles
    // start/stop through the ViewModel; content description flips with
    // state. TODO(67-02): adopt onAudioRecordingChanged for the
    // recording flag + recording-row UI; resource the copy.
    isVoiceRecording: Boolean = false,
    onVoiceClick: () -> Unit = {},
    // Phase 67 (VMSG-01 full): recording-row state. Timer + amplitude
    // render inline while recording; cancel discards immediately.
    voiceElapsedSec: Int = 0,
    voiceAmplitude: Int = 0,
    onCancelRecording: () -> Unit = {},
    // Phase 68 (VMSG-02): draft preview card state. Shown when a kept clip
    // exists and no recording is running; the caption input stays live
    // alongside the card and send transmits voice + caption as one bubble.
    hasVoiceClip: Boolean = false,
    isDraftPlaying: Boolean = false,
    draftPositionMs: Int = 0,
    draftDurationMs: Long = 0L,
    onPlayDraft: () -> Unit = {},
    onPauseDraft: () -> Unit = {},
    onDeleteDraft: () -> Unit = {},
    onSeekDraft: (Int) -> Unit = {},
    // Model-loading gate (2026-10-02): while a model loads, the WHOLE
    // input is disabled — text field, image/think buttons, mic, and send.
    isLoadingModel: Boolean = false,
    // Phase 70 (70-02): document attachment. onAttachDocument opens the
    // SAF picker at the call site; attachedDocName != null shows the chip
    // above the input (filename + size + truncation marker); remove is
    // single-tap, no dialog. Send includes the document (hasContent).
    onAttachDocument: () -> Unit = {},
    attachedDocName: String? = null,
    attachedDocSize: String? = null,
    attachedDocTruncatedAt: Int? = null,
    onRemoveDocument: () -> Unit = {},
    // Phase 69 Plan 03 (VMSG-03): first-use coachmark. When true AND the
    // voice button renders enabled, a one-shot M3 PlainTooltip anchors to
    // it; ANY tap through either button or outside dismisses via
    // onCoachmarkDismiss (the VM persists seen=true — no local flag that
    // could diverge, never re-shows).
    showVoiceCoachmark: Boolean = false,
    onCoachmarkDismiss: () -> Unit = {},
) {
    // Single gate for the entire bar: generating, no model, or loading.
    val inputLocked = isGenerating || !canSend || isLoadingModel
    Surface(
        color = Color(0xFF2B2B29),
        shape = MaterialTheme.shapes.extraLarge,
        // Halo shadow hugging the rounded bar (user decision 2026-10-03):
        // small elevation so the blur reads around the pill, never as a
        // slab floating above it.
        shadowElevation = 2.dp,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, top = 5.dp, bottom = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .animateContentSize()
        ) {
            // Image previews
            if (attachedImages.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(attachedImages.size) { i ->
                        Box(modifier = Modifier.size(72.dp)) {
                            // Coil (memory+disk cached, auto-downsampled) replaces the
                            // previous unbounded BitmapFactory.decodeStream — Play
                            // bitmap-memory warning. Null/error renders empty, same
                            // as the old failed-decode path (remove button stays).
                            AsyncImage(
                                model = attachedImages[i],
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 6.dp, y = (-6).dp)
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .clickable(enabled = !inputLocked) { onRemoveImage(i) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Close, stringResource(R.string.cd_remove), tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }

            // Phase 70 (70-02): document attachment chip — same above-input
            // slot as the image previews. SurfaceContainer background;
            // Description icon + filename (Label 14sp, single-line
            // ellipsis) + size readout (+ inline truncation marker) +
            // remove X (14dp glyph, 48dp hit target, single tap, no
            // dialog). Caption stays editable; send transmits text +
            // document as one turn.
            if (attachedDocName != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = attachedDocName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        val truncMarker = attachedDocTruncatedAt?.let { n ->
                            " · " + stringResource(R.string.doc_reader_showing_first, n)
                        }.orEmpty()
                        Text(
                            text = "· ${attachedDocSize.orEmpty()}$truncMarker",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        IconButton(onClick = onRemoveDocument, modifier = Modifier.size(48.dp)) {
                            Icon(
                                Icons.Filled.Close,
                                stringResource(R.string.doc_reader_remove),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Phase 68 (VMSG-02): persistent draft preview card above the
            // input row — play/pause + progress + total duration + send +
            // delete. Unlike the recording row it never replaces the text
            // field: the caption stays editable and sends with the voice.
            if (hasVoiceClip && !isVoiceRecording) {
                DraftPreviewCard(
                    isPlaying = isDraftPlaying,
                    positionMs = draftPositionMs,
                    durationMs = draftDurationMs,
                    onPlay = onPlayDraft,
                    onPause = onPauseDraft,
                    onDelete = onDeleteDraft,
                    onSeek = onSeekDraft,
                )
                Spacer(Modifier.height(8.dp))
            }

            // Row 1: Input only. WR-03: TextFieldValue (not raw String) so
            // dictation inserts at the selection via onCursorChange.
            // External text changes (dictation commits) snap the caret to
            // the END only when the field is NOT focused (user decision
            // 2026-10-02: never steal the caret mid-edit); when focused,
            // the caret is preserved. Either way the position is reported
            // so the ViewModel's lastKnownCursor stays in sync. Typing is
            // untouched: this block only runs on external change.
            var fieldValue by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
            var inputFocused by remember { mutableStateOf(false) }
            // Never overwrite a buffer the IME is actively composing
            // (autocorrect/predictions): fieldValue.text transiently differs
            // from the VM text mid-composition, and "syncing" it back
            // destroys the composition — typed characters get stuck,
            // duplicated, or undeletable. The IME commits the final text
            // through onValueChange, which converges both states without
            // any forced write here.
            if (fieldValue.composition == null && fieldValue.text != text) {
                fieldValue = if (inputFocused) {
                    val kept = fieldValue.selection
                    fieldValue.copy(
                        text = text,
                        selection = TextRange(
                            kept.start.coerceIn(0, text.length),
                            kept.end.coerceIn(0, text.length),
                        ),
                    )
                } else {
                    TextFieldValue(text, TextRange(text.length))
                }
                onCursorChange(fieldValue.selection.start)
            }
            // End padding reserving the overlaid right cluster (mic /
            // voice / send widths + breathing room). Function of model,
            // loading, and content state only — never of layout — so no
            // feedback loop is possible.
            val micVisible = speechAvailable && !isGenerating && !isLoadingModel
            val voiceVisible = micVisible && !isVoiceRecording && voiceGate == GateState.Allowed
            val sendVisible = (text.isNotBlank() || attachedImages.isNotEmpty() || attachedDocName != null || hasVoiceClip) && canSend && !isLoadingModel
            val rightClusterDp = ((if (micVisible) 40 else 0) + (if (voiceVisible) 40 else 0) + (if (sendVisible) 40 else 0) + 4).dp
            val focusRequester = remember { FocusRequester() }
            // Line-count mirror at exactly the compact field width (same
            // 48dp start + rightCluster end padding the field uses when
            // compact), so expansion triggers precisely when the text
            // would wrap to a second line there. Always composed at
            // constant width: monotonic in text, never oscillates. (The
            // TextFieldValue overload has no onTextLayout — hence a
            // mirror instead of measuring the field itself.)
            var inputLines by remember { mutableIntStateOf(0) }
            // Slots relocate ONLY while unfocused: moving the field
            // between slots mid-typing drops IME focus on real devices
            // (keyboard closes, the refocus net below reopens it —
            // the close/reopen flicker at line 2). While focused the
            // compact overlay layout persists (rightCluster padding
            // already reserves the buttons); the move happens on blur
            // or clear, where no focus can be lost.
            val expandedInput = inputLines > 1 && !inputFocused
            Text(
                text = text.ifEmpty { " " },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = Int.MAX_VALUE,
                onTextLayout = { if (it.lineCount != inputLines) inputLines = it.lineCount },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 48.dp, end = rightClusterDp)
                    .height(0.dp),
            )

            // Input-row pieces as local composables (single static row —
            // the row stretches in place as the text wraps, nothing moves).
            @Composable
            fun AttachGroup() {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val showPhotosItem = modelHasVision
                    if (showPhotosItem) {
                        AttachMenuButton(
                            enabled = !inputLocked,
                            onPickPhotos = onAddImage,
                            onPickFiles = onAttachDocument,
                        )
                    } else {
                        // Files-only: direct attach, no menu detour.
                        // description swaps to replace when attached (a new
                        // pick replaces); the attached filename rides
                        // stateDescription (Phase 65 pattern).
                        // Hoisted out of semantics{}: stringResource is
                        // @Composable and cannot run inside the semantics lambda.
                        val attachedStateDesc = attachedDocName?.let {
                            stringResource(R.string.doc_reader_attached, it)
                        }
                        IconButton(
                            onClick = onAttachDocument,
                            enabled = !inputLocked,
                            modifier = Modifier.size(40.dp).semantics {
                                attachedStateDesc?.let { stateDescription = it }
                            },
                        ) {
                            Icon(
                                Icons.Filled.AttachFile,
                                stringResource(
                                    if (attachedDocName != null) R.string.doc_reader_replace
                                    else R.string.doc_reader_attach
                                ),
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }

            @Composable
            fun InputField(mod: Modifier) {
                // BasicTextField (not OutlinedTextField): M3's outlined
                // field enforces a 56dp intrinsic min height that no
                // heightIn can shrink — Basic has no min, so the row wraps
                // the 16sp content exactly (~44dp single-line). Same
                // transparent look, same TextFieldValue cursor contract.
                BasicTextField(
                value = fieldValue,
                onValueChange = { next ->
                    // Soft-keyboard Enter committed as text (keyboards
                    // that ignore imeAction on multiline fields): a lone
                    // trailing newline appended to the buffer sends
                    // instead of growing the field. Pasted/multi-char
                    // edits and IME compositions take the normal path.
                    if (shouldSendOnNewline(fieldValue.text, next.text, next.composition != null)) {
                        val stripped = next.text.dropLast(1)
                        fieldValue = next.copy(text = stripped)
                        if (stripped != text) onTextChange(stripped)
                        val hasContent = stripped.isNotBlank() || attachedImages.isNotEmpty() || attachedDocName != null || hasVoiceClip
                        if (canSend && !isGenerating && !isLoadingModel && hasContent) {
                            onSend()
                        }
                    } else {
                        fieldValue = next
                        if (next.text != text) onTextChange(next.text)
                        onCursorChange(next.selection.start)
                    }
                },
                modifier = mod
                    .focusRequester(focusRequester)
                    .padding(vertical = 10.dp)
                    .onFocusChanged { inputFocused = it.isFocused }
                    .onKeyEvent { event ->
                        // KeyDown only: without the type check both press
                        // and release would send (the release no-ops on
                        // cleared text, but say what you mean).
                        val hasContent = text.isNotBlank() || attachedImages.isNotEmpty() || attachedDocName != null || hasVoiceClip
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && canSend && !isGenerating && !isLoadingModel && hasContent) {
                            onSend()
                            true
                        } else false
                    },
                enabled = !inputLocked,
                maxLines = 4,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    val hasContent = text.isNotBlank() || attachedImages.isNotEmpty() || attachedDocName != null || hasVoiceClip
                    if (canSend && !isGenerating && !isLoadingModel && hasContent) onSend()
                }),
                decorationBox = { innerTextField ->
                    Box {
                        // Rendered text (fieldValue), not the VM prop: the
                        // prop lags a frame behind while typing and the
                        // placeholder would bleed through over live text.
                        if (fieldValue.text.isEmpty()) {
                            Text(
                                stringResource(R.string.type_message),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                },
                )
            }

            @Composable
            fun MicButton() {
                // Phase 65 (VOICE-01/03): dictation mic, immediately left of
                // the send/stop slot. Hidden without a recognizer
                // (speechAvailable), while generating, and while a model
                // loads (inputLocked) so no two stop icons ever appear
                // together and nothing is tappable mid-load.
                if (speechAvailable && !isGenerating && !isLoadingModel) {
                    // Phase 65 UI-review: localized stateDescription so
                    // TalkBack announces the listening state beyond the
                    // content-description swap (which is not reliably
                    // re-announced on a stable node). Cleared when idle.
                    val listeningState = stringResource(R.string.voice_listening_state)
                    IconButton(
                        // Phase 69 Plan 03 (VMSG-03): a dictation tap is a
                        // first interaction — it dismisses the voice
                        // coachmark too (whichever comes first).
                        onClick = {
                            if (showVoiceCoachmark) onCoachmarkDismiss()
                            onMicClick()
                        },
                        modifier = Modifier.size(40.dp).semantics {
                            if (isListening) stateDescription = listeningState
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isListening) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent
                        )
                    ) {
                        if (isListening) {
                            Icon(Icons.Filled.Stop, stringResource(R.string.cd_stop_listening),
                                tint = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Filled.Mic, stringResource(R.string.cd_dictate),
                                tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                }
            }

            @Composable
            fun VoiceButton() {
                // Phase 67 voice-send button beside the dictation mic, same
                // visibility conditions. Waveform glyph (GraphicEq family),
                // never a mic. Hidden while recording — the recording row
                // owns stop/cancel. Gated configs (text-only model, remote
                // endpoint) render NOTHING — no button, no hint. The
                // send-path VM guards still block gated sends (draft kept +
                // explainer).
                if (speechAvailable && !isGenerating && !isLoadingModel && !isVoiceRecording) {
                    // Adopt the pre-existing dead onAudioRecordingChanged
                    // channel: it now fires with the live recording flag so
                    // the screen-level isRecording state stays real.
                    LaunchedEffect(isVoiceRecording) {
                        onAudioRecordingChanged?.invoke(isVoiceRecording)
                    }
                    if (voiceGate == GateState.Allowed) {
                        // Phase 69 Plan 03 (VMSG-03): first-use coachmark
                        // anchored to the enabled voice button. The M3
                        // PlainTooltip (Compose BOM, zero new deps) shows
                        // once while showVoiceCoachmark; outside taps
                        // dismiss via onDismissRequest, voice taps via the
                        // wrapped onClick below — whichever first.
                        val voiceTooltipState = rememberTooltipState(isPersistent = true)
                        LaunchedEffect(showVoiceCoachmark, voiceGate) {
                            if (showVoiceCoachmark && voiceGate == GateState.Allowed) voiceTooltipState.show()
                            else voiceTooltipState.dismiss()
                        }
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                            tooltip = {
                                PlainTooltip {
                                    Text(
                                        stringResource(R.string.voice_msg_coachmark),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            },
                            state = voiceTooltipState,
                            onDismissRequest = { if (showVoiceCoachmark) onCoachmarkDismiss() },
                        ) {
                            IconButton(
                                onClick = {
                                    if (showVoiceCoachmark) onCoachmarkDismiss()
                                    onVoiceClick()
                                },
                            modifier = Modifier.size(40.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (isVoiceRecording) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent
                            )
                        ) {
                            Icon(Icons.Filled.GraphicEq, stringResource(R.string.voice_msg_record),
                                tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
                        }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                }
            }

            @Composable
            fun SendSlot() {
                // Send/stop slot: stop while generating; up-arrow send
                // ONLY with content (text, images, document, or voice
                // draft) — empty input shows no send affordance at all.
                if (isGenerating) {
                    IconButton(onClick = onStop, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Stop, stringResource(R.string.cd_stop), tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                } else {
                    val hasContent = text.isNotBlank() || attachedImages.isNotEmpty() || attachedDocName != null || hasVoiceClip
                    if (hasContent && canSend && !isLoadingModel) {
                        IconButton(
                            onClick = onSend,
                            modifier = Modifier.size(40.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Filled.ArrowUpward, stringResource(R.string.cd_send), modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }

            // The text field MOVES between the compact row and the
            // expanded column — movableContentOf keeps the SAME composition
            // (text, selection, and focus node) across the move, so growth
            // never recreates the field. A refocus safety net below
            // re-opens the keyboard if the move still drops IME focus.
            val movableInputField = remember {
                movableContentOf { mod: Modifier -> InputField(mod) }
            }
            // Refocus safety net: runs ONLY when the layout flips (which
            // only text edits trigger). Re-asserts focus on the moved node
            // one frame after attach; harmless no-op when focus survived
            // the move, and never fires on rotation, recording toggles,
            // or manual keyboard dismissal (none of those flip expanded).
            // Skips the initial composition so entering a chat never pops
            // the keyboard uninvited.
            var expandEffectArmed by remember { mutableStateOf(false) }
            LaunchedEffect(expandedInput) {
                if (!expandEffectArmed) {
                    expandEffectArmed = true
                } else if (inputFocused) {
                    // Refocus ONLY when still focused (send-clear while
                    // typing): never yank the keyboard back after the
                    // user dismissed it (blur flips expandedInput too).
                    withFrameNanos { }
                    try {
                        focusRequester.requestFocus()
                    } catch (_: Exception) {
                    }
                }
            }

            // Phase 67 (VMSG-01 full): recording replaces the input row
            // inline — mm:ss timer + amplitude bar + explicit cancel (X).
            // Timer + bar render onSurfaceVariant, switching to error red
            // in the last 10 s (>= 50 s). No other recolor, no pulse.
            if (isVoiceRecording) {
                val capWarning = voiceElapsedSec >= 50
                val recColor = if (capWarning) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                // TalkBack throttle (Phase 65 pattern): the live-region
                // description only changes every 5 s so announcements
                // never spam, while the visual timer ticks each second.
                val announceBucket = voiceElapsedSec / 5
                val announceText = stringResource(
                    R.string.voice_msg_recording_state,
                    announceBucket * 5 / 60,
                    announceBucket * 5 % 60,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().semantics {
                        stateDescription = announceText
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCancelRecording, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, stringResource(R.string.voice_msg_cancel),
                            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "%d:%02d".format(voiceElapsedSec / 60, voiceElapsedSec % 60),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFeatureSettings = "tnum"
                        ),
                        color = recColor
                    )
                    Spacer(Modifier.width(12.dp))
                    LinearProgressIndicator(
                        progress = { (voiceAmplitude / 32767f).coerceIn(0f, 1f) },
                        modifier = Modifier.weight(1f),
                        color = recColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onVoiceClick, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Stop, stringResource(R.string.voice_msg_stop),
                            tint = recColor, modifier = Modifier.size(24.dp))
                    }
                }
            } else {
                // Field-first static skeleton: the FIELD (via movable
                // content) never changes slots, so focus and the keyboard
                // survive growth; ONLY the stateless buttons relocate —
                // overlaid at the row edges when compact, in a row below
                // once the text wraps past one line. The field padding
                // adapts per mode (full-bleed text when expanded).
                if (expandedInput) {
                    Column {
                        movableInputField(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AttachGroup()
                            Spacer(Modifier.weight(1f))
                            MicButton()
                            VoiceButton()
                            SendSlot()
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        movableInputField(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 48.dp, end = rightClusterDp)
                        )
                        Row(
                            modifier = Modifier.align(Alignment.BottomStart),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AttachGroup()
                        }
                        Row(
                            modifier = Modifier.align(Alignment.BottomEnd),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MicButton()
                            VoiceButton()
                            SendSlot()
                        }
                    }
                }
        }
    }
}}

/**
 * Unified attach affordance: a single "+" button opening an upward menu
 * with Photos + Files. The menu is a [Popup] with a custom
 * [PopupPositionProvider] (pinned above the anchor, left-aligned) instead
 * of a [androidx.compose.material3.DropdownMenu] — DropdownMenu only
 * offers a fixed below-anchor offset, while the input row sits at the
 * screen bottom. Outside-tap and back-press dismiss via Popup defaults.
 */
@Composable
private fun AttachMenuButton(
    enabled: Boolean,
    onPickPhotos: () -> Unit,
    onPickFiles: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val positionProvider = remember(density) {
        AboveAnchorPositionProvider(marginPx = with(density) { 8.dp.roundToPx() })
    }
    Box {
        IconButton(
            onClick = { expanded = !expanded },
            enabled = enabled,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                Icons.Filled.Add,
                stringResource(R.string.attach_menu_content_desc),
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp),
            )
        }
        if (expanded) {
            Popup(
                popupPositionProvider = positionProvider,
                onDismissRequest = { expanded = false },
            ) {
                Surface(
                    // Same look as the input bar itself (Color 0xFF2B2B29,
                    // extraLarge) so the menu reads as its extension.
                    shape = MaterialTheme.shapes.extraLarge,
                    color = Color(0xFF2B2B29),
                    tonalElevation = 2.dp,
                    shadowElevation = 8.dp,
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        AttachMenuRow(
                            icon = Icons.Filled.AddPhotoAlternate,
                            label = stringResource(R.string.attach_menu_photos),
                            description = stringResource(R.string.attach_menu_photos_desc),
                            onClick = {
                                expanded = false
                                onPickPhotos()
                            },
                        )
                        AttachMenuRow(
                            icon = Icons.Filled.AttachFile,
                            label = stringResource(R.string.attach_menu_files),
                            description = stringResource(R.string.attach_menu_files_desc),
                            onClick = {
                                expanded = false
                                onPickFiles()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Column {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        },
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    )
}

/**
 * Pins popup content above the anchor's top edge (minus [marginPx]),
 * left-aligned, clamped on-screen. [DropdownMenu]'s built-in provider
 * only supports below-anchor placement, unusable at the screen bottom.
 */
private class AboveAnchorPositionProvider(
    private val marginPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.left.coerceIn(
            0,
            (windowSize.width - popupContentSize.width).coerceAtLeast(0),
        )
        val y = (anchorBounds.top - popupContentSize.height - marginPx).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

/**
 * Phase 68 (VMSG-02): persistent voice-draft preview card. Rendered above
 * the input row while a kept clip exists (never while recording).
 *
 * Duration convention (locked): the readout is ALWAYS the total m:ss —
 * position shows only as progress fill. TalkBack announces total +
 * playing/paused, so the description changes only on play/pause toggles
 * (never per-tick) — the Phase 67 5 s throttle discipline holds by
 * construction with zero live-region spam.
 */
@Composable
private fun DraftPreviewCard(
    isPlaying: Boolean,
    positionMs: Int,
    durationMs: Long,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit,
    onSeek: (Int) -> Unit = {},
) {
    val totalSec = (durationMs / 1000).toInt().coerceAtLeast(0)
    val stateWord = stringResource(
        if (isPlaying) R.string.voice_msg_draft_playing else R.string.voice_msg_draft_paused
    )
    val announceText = stringResource(
        R.string.voice_msg_draft_state,
        totalSec / 60,
        totalSec % 60,
        stateWord,
    )
    // Same player component as the chat history bubbles (VoicePlayerRow:
    // play/pause + scrubbable progress + total m:ss), only wrapped
    // rounder (20dp) with a delete affordance instead of send — sending
    // happens through the input-row send button (hasVoiceClip counts as
    // content). No send button here by design.
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { stateDescription = announceText },
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VoicePlayerRow(
                durationMs = durationMs,
                isPlaying = isPlaying,
                positionMs = positionMs,
                fileMissing = false,
                onPlay = onPlay,
                onPause = onPause,
                onSeek = onSeek,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    stringResource(R.string.voice_msg_delete_draft),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/**
 * Soft-keyboard Enter committed as a text newline (keyboards that ignore
 * imeAction=Send on multiline fields): true only when the edit appends
 * exactly one trailing "\n" outside an IME composition. Pasted blocks,
 * mid-text newlines and composing buffers take the normal path.
 * Pure — unit-tested.
 */
internal fun shouldSendOnNewline(prevText: String, nextText: String, composing: Boolean): Boolean {
    if (composing) return false
    if (nextText.length != prevText.length + 1) return false
    if (!nextText.endsWith("\n")) return false
    return nextText.dropLast(1) == prevText
}
