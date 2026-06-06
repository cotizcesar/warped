package com.warped.ui.chat.components

import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import com.warped.ui.components.WarpedAlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.warped.domain.model.ChatMessage
import com.warped.domain.model.Role
import com.warped.domain.model.SyntaxTheme
import kotlinx.coroutines.launch

@Composable
fun MessageBubble(
    message: ChatMessage,
    isStreaming: Boolean = false,
    codeTheme: SyntaxTheme = SyntaxTheme.MONOKAI,
    codeFontScale: Float = 1.0f,
) {
    val isUser = message.role == Role.USER
    var showReasoning by remember { mutableStateOf(false) }
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
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
                    Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                }
            ),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isUser) Color(0xFF121212) else Color.Transparent,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .widthIn(max = 340.dp)
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
                            text = "Thinking",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF545450)
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = if (showReasoning) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = if (showReasoning) "Hide reasoning" else "Show reasoning",
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

                if (message.content.isNotBlank()) {
                    if (!isUser) {
                        SelectionContainer {
                            MarkdownText(
                                text = message.content + if (isStreaming) "▌" else "",
                                baseColor = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth(),
                                codeTheme = codeTheme,
                                codeFontScale = codeFontScale,
                                isStreaming = isStreaming
                            )
                        }
                    } else {
                        SelectionContainer {
                            Text(
                                text = message.content + if (isStreaming) "▌" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
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
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun MessageImageStack(imageUris: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        imageUris.forEach { dataUrl ->
            var showFullImage by remember { mutableStateOf(false) }
            val bitmap = remember(dataUrl) {
                try {
                    val base64 = dataUrl.substringAfter("base64,")
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
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
                                contentDescription = "Close",
                                tint = androidx.compose.ui.graphics.Color.White
                            )
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
