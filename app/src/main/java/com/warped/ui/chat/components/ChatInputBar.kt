package com.warped.ui.chat.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
    onAddImage: () -> Unit = {},
    attachedImages: List<Uri> = emptyList(),
    onRemoveImage: (Int) -> Unit = {},
    modelHasAudio: Boolean = false,
    onAudioRecorded: ((ByteArray) -> Unit)? = null,
    onAudioRecordingChanged: ((Boolean) -> Unit)? = null,
) {
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
                        val ctx = LocalContext.current
                        val bitmap = remember(attachedImages[i]) {
                            try {
                                ctx.contentResolver.openInputStream(attachedImages[i])?.use {
                                    BitmapFactory.decodeStream(it)
                                }
                            } catch (_: Exception) { null }
                        }
                        Box(modifier = Modifier.size(72.dp)) {
                            bitmap?.let { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 6.dp, y = (-6).dp)
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .clickable { onRemoveImage(i) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Close, "Remove", tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }

            // Row 1: Input only
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onKeyEvent { event ->
                        val hasContent = text.isNotBlank() || attachedImages.isNotEmpty()
                        if (event.key == Key.Enter && canSend && !isGenerating && hasContent) {
                            onSend()
                            true
                        } else false
                    },
                placeholder = { Text(stringResource(R.string.type_message)) },
                enabled = !isGenerating && canSend,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    val hasContent = text.isNotBlank() || attachedImages.isNotEmpty()
                    if (canSend && !isGenerating && hasContent) onSend()
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
                // Left group
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onAddImage, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.AddPhotoAlternate, "Add image",
                            tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
                    }
                    // Think toggle
                    Spacer(Modifier.width(10.dp))
                    val canThink = modelHasReasoning
                    Button(
                        onClick = onToggleReasoning,
                        enabled = canThink,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (reasoningEnabled && canThink) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            stringResource(R.string.thinking),
                            color = if (!canThink) Color.White.copy(alpha = 0.25f)
                                else if (reasoningEnabled) Color.White
                                else Color.White.copy(alpha = 0.6f),
                            fontSize = MaterialTheme.typography.labelSmall.fontSize
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                if (isGenerating) {
                    IconButton(onClick = onStop, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Stop, "Stop", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                } else {
                    val hasContent = text.isNotBlank() || attachedImages.isNotEmpty()
                    if (hasContent && canSend) {
                        IconButton(
                            onClick = onSend,
                            modifier = Modifier.size(40.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Send", modifier = Modifier.size(24.dp))
                        }
                    } else {
                        IconButton(onClick = onSend, enabled = false, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Send",
                                tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}
