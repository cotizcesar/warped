package com.warped.ui.chat.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.warped.domain.model.ChatMessage
import com.warped.domain.model.Role

@Composable
fun MessageBubble(
    message: ChatMessage,
    isStreaming: Boolean = false
) {
    val isUser = message.role == Role.USER
    var showReasoning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isUser) Color(0xFF121212) else Color.Transparent,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(
                modifier = if (isUser) Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                           else Modifier.padding(top = 1.dp)
            ) {
                if (!isUser && !message.reasoning.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .clickable { showReasoning = !showReasoning }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Thinking",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF545450)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (showReasoning) "▼" else "▶",
                            color = Color(0xFF545450),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    AnimatedVisibility(
                        visible = showReasoning,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Surface(
                            color = Color.Transparent,
                            modifier = Modifier.padding(start = 16.dp)
                        ) {
                            MarkdownText(
                                text = message.reasoning,
                                baseColor = Color(0xFF545450),
                                modifier = Modifier.padding(vertical = 4.dp),
                                fontStyle = FontStyle.Italic
                            )
                        }
                    }
                }

                // Images in user messages
                if (isUser && message.imageUris.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        message.imageUris.forEach { dataUrl ->
                            var showFullImage by remember { mutableStateOf(false) }
                            val bitmap = remember(dataUrl) {
                                try {
                                    val base64 = dataUrl.substringAfter("base64,")
                                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                } catch (_: Exception) { null }
                            }
                            if (showFullImage) {
                                androidx.compose.material3.AlertDialog(
                                    onDismissRequest = { showFullImage = false },
                                    confirmButton = {},
                                    dismissButton = {
                                        TextButton(onClick = { showFullImage = false }) {
                                            Text("✕", color = androidx.compose.ui.graphics.Color.White)
                                        }
                                    },
                                    text = {
                                        bitmap?.let {
                                            Image(
                                                bitmap = it.asImageBitmap(),
                                                contentDescription = "Full image",
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                )
                            }
                            bitmap?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "Image (tap to enlarge)",
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

                if (message.content.isNotBlank()) {
                    if (!isUser) {
                        MarkdownText(
                            text = message.content + if (isStreaming) "▌" else "",
                            baseColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = message.content + if (isStreaming) "▌" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Stats below the bubble
        if (!isUser && !message.stats.isNullOrBlank()) {
            Text(
                text = message.stats,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF545450),
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
    }
}
