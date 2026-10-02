package com.warped.ui.chat.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
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
    reasoningEnabled: Boolean = true,
    onToggleReasoning: () -> Unit = {},
    modelHasReasoning: Boolean = true,
    modelHasVision: Boolean = true,
    onAddImage: () -> Unit = {},
    attachedImages: List<Uri> = emptyList(),
    onRemoveImage: (Int) -> Unit = {},
    // Phase 69 Plan 01 (VMSG-04/08): voice-send gate. Replaces the dead
    // modelHasAudio flag (declared, passed, consumed nowhere) — the VM
    // voiceSendGate flow is the single source of truth. A gated button
    // RENDERS (never hidden) at reduced opacity with an inline hint and
    // stays TAPPABLE into onGatedVoiceClick (never a dead button).
    voiceGate: GateState = GateState.Allowed,
    onGatedVoiceClick: () -> Unit = {},
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
    onSendDraft: () -> Unit = {},
    onDeleteDraft: () -> Unit = {},
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
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 10.dp, end = 10.dp, top = 5.dp, bottom = 0.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
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
                    onSend = onSendDraft,
                    onDelete = onDeleteDraft,
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
            if (fieldValue.text != text) {
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
            // Phase 67 (VMSG-01 full): recording replaces the input row
            // inline — mm:ss timer + amplitude bar + explicit cancel (X).
            // The voice-send button in Row 2 doubles as the stop toggle.
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
                    Text(
                        "%d:%02d".format(voiceElapsedSec / 60, voiceElapsedSec % 60),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFeatureSettings = "tnum"
                        ),
                        color = recColor
                    )
                    Spacer(Modifier.width(8.dp))
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
                OutlinedTextField(
                value = fieldValue,
                onValueChange = { next ->
                    fieldValue = next
                    if (next.text != text) onTextChange(next.text)
                    onCursorChange(next.selection.start)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { inputFocused = it.isFocused }
                    .onKeyEvent { event ->
                        val hasContent = text.isNotBlank() || attachedImages.isNotEmpty() || attachedDocName != null || hasVoiceClip
                        if (event.key == Key.Enter && canSend && !isGenerating && !isLoadingModel && hasContent) {
                            onSend()
                            true
                        } else false
                    },
                placeholder = { Text(stringResource(R.string.type_message)) },
                enabled = !inputLocked,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    val hasContent = text.isNotBlank() || attachedImages.isNotEmpty() || attachedDocName != null || hasVoiceClip
                    if (canSend && !isGenerating && !isLoadingModel && hasContent) onSend()
                }),
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
                )
            }

            Spacer(Modifier.height(10.dp))

            // Row 2: Left (image + brain) | Right (model + send/stop)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left group: image moderator (vision-capable models only) +
                // Thinking toggle (reasoning-capable models only). Unsupported
                // buttons are hidden, not dimmed — no dead affordances.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (modelHasVision) {
                        IconButton(onClick = onAddImage, enabled = !inputLocked, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Filled.AddPhotoAlternate, stringResource(R.string.cd_add_image),
                                tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
                        }
                    }
                    // Phase 70 (70-02): document attach — same left icon row,
                    // same 40dp size and inputLocked gating as the image
                    // sibling, sibling tint (neutral affordance, never
                    // accent). Ungated by model (same UX local + remote);
                    // description swaps to replace when attached (a new pick
                    // replaces); the attached filename rides
                    // stateDescription (Phase 65 pattern).
                    if (modelHasVision) Spacer(Modifier.width(10.dp))
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
                    // Think toggle
                    val canThink = modelHasReasoning
                    if (canThink) {
                        if (modelHasVision) Spacer(Modifier.width(10.dp))
                        Button(
                            onClick = onToggleReasoning,
                            enabled = !inputLocked,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (reasoningEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                stringResource(R.string.thinking),
                                color = if (reasoningEnabled) Color.White else Color.White.copy(alpha = 0.6f),
                                fontSize = MaterialTheme.typography.labelSmall.fontSize
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

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

                // Phase 67 (VMSG-01 tracer): voice-send button beside the
                // dictation mic, same visibility conditions. Waveform
                // glyph (GraphicEq family), never a mic. Hidden while
                // recording — the Row-1 recording row owns stop/cancel.
                // Phase 69 Plan 01 (VMSG-04/08): gated rendering — the same
                // GraphicEq glyph at reduced opacity (~38% onSurface, no
                // container recolor, no error-red) with the inline hint in
                // onSurfaceVariant. Tappable into the explainer (never
                // enabled=false with no handler). Allowed keeps the
                // Phase 67/68 rendering untouched.
                if (speechAvailable && !isGenerating && !isLoadingModel && !isVoiceRecording) {
                    // Adopt the pre-existing dead onAudioRecordingChanged
                    // channel: it now fires with the live recording flag so
                    // the screen-level isRecording state stays real.
                    LaunchedEffect(isVoiceRecording) {
                        onAudioRecordingChanged?.invoke(isVoiceRecording)
                    }
                    if (voiceGate != GateState.Allowed) {
                        val gatedHint = stringResource(
                            if (voiceGate == GateState.GatedTextOnly) R.string.voice_msg_gate_audio_hint
                            else R.string.voice_msg_gate_remote_hint
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                gatedHint,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = onGatedVoiceClick,
                                modifier = Modifier.size(48.dp).semantics {
                                    stateDescription = gatedHint
                                },
                            ) {
                                Icon(Icons.Filled.GraphicEq, stringResource(R.string.voice_msg_record),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                    modifier = Modifier.size(24.dp))
                            }
                        }
                    } else {
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
                            modifier = Modifier.size(48.dp),
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
                            Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.cd_send), modifier = Modifier.size(24.dp))
                        }
                    } else {
                        IconButton(onClick = onSend, enabled = false, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.cd_send),
                                tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
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
    onSend: () -> Unit,
    onDelete: () -> Unit,
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
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { stateDescription = announceText },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { if (isPlaying) onPause() else onPlay() },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    stringResource(
                        if (isPlaying) R.string.voice_msg_pause_draft
                        else R.string.voice_msg_play_draft
                    ),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
            LinearProgressIndicator(
                progress = {
                    if (durationMs > 0) {
                        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                },
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "%d:%02d".format(totalSec / 60, totalSec % 60),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFeatureSettings = "tnum"
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onSend, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    stringResource(R.string.voice_msg_send_voice),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
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
