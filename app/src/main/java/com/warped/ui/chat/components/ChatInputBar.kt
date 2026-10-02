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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.warped.R

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
    modelHasAudio: Boolean = false,
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
    // Model-loading gate (2026-10-02): while a model loads, the WHOLE
    // input is disabled — text field, image/think buttons, mic, and send.
    isLoadingModel: Boolean = false,
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

            // Row 1: Input only. WR-03: TextFieldValue (not raw String) so
            // the cursor survives programmatic updates — dictation inserts
            // at the selection via onCursorChange, and external text changes
            // preserve the caret instead of jumping to the end.
            var fieldValue by remember { mutableStateOf(TextFieldValue(text)) }
            if (fieldValue.text != text) {
                val kept = fieldValue.selection
                fieldValue = fieldValue.copy(
                    text = text,
                    selection = TextRange(
                        kept.start.coerceIn(0, text.length),
                        kept.end.coerceIn(0, text.length),
                    ),
                )
            }
            OutlinedTextField(
                value = fieldValue,
                onValueChange = { next ->
                    fieldValue = next
                    if (next.text != text) onTextChange(next.text)
                    onCursorChange(next.selection.start)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onKeyEvent { event ->
                        val hasContent = text.isNotBlank() || attachedImages.isNotEmpty()
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
                    val hasContent = text.isNotBlank() || attachedImages.isNotEmpty()
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
                        onClick = onMicClick,
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

                if (isGenerating) {
                    IconButton(onClick = onStop, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Stop, stringResource(R.string.cd_stop), tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                } else {
                    val hasContent = text.isNotBlank() || attachedImages.isNotEmpty()
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
