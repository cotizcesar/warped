package com.warped.ui.chat.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
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
import com.warped.domain.model.Endpoint
import com.warped.domain.model.LocalModel
import com.warped.domain.model.ProviderType

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
    onAddImage: () -> Unit = {},
    localModels: List<LocalModel> = emptyList(),
    endpoints: List<Endpoint> = emptyList(),
    selectedModelId: String? = null,
    attachedImages: List<Uri> = emptyList(),
    onRemoveImage: (Int) -> Unit = {},
    onModelSelected: (String, ProviderType) -> Unit = { _, _ -> }
) {
    Surface(
        color = Color(0xFF2B2B29),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Column(modifier = Modifier.padding(5.dp)) {
            // Image previews
            if (attachedImages.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
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
                        Box(modifier = Modifier.size(112.dp)) {
                            bitmap?.let { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            IconButton(
                                onClick = { onRemoveImage(i) },
                                modifier = Modifier.align(Alignment.TopEnd).size(22.dp).offset(x = 8.dp, y = (-8).dp)
                            ) {
                                Icon(Icons.Filled.Close, "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            // Row 1: Input only
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.type_message)) },
                enabled = !isGenerating && canSend,
                maxLines = 4,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )

            Spacer(Modifier.height(5.dp))

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
                    Button(
                        onClick = onToggleReasoning,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (reasoningEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            "Thinking",
                            color = if (reasoningEnabled) Color.White else Color.White.copy(alpha = 0.6f),
                            fontSize = MaterialTheme.typography.labelSmall.fontSize
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // Right group: Model dropdown + send/stop
                var modelExpanded by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { modelExpanded = true }) {
                        Text(
                            selectedModelId?.substringAfterLast("/") ?: "Model",
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1
                        )
                        Icon(Icons.Filled.KeyboardArrowDown, null,
                            tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(
                        expanded = modelExpanded,
                        onDismissRequest = { modelExpanded = false }
                    ) {
                        localModels.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.name) },
                                onClick = {
                                    onModelSelected(model.filePath, ProviderType.LOCAL)
                                    modelExpanded = false
                                }
                            )
                        }
                        if (localModels.isNotEmpty() && endpoints.isNotEmpty()) {
                            HorizontalDivider()
                        }
                        endpoints.forEach { ep ->
                            val mid = ep.modelId
                            if (mid != null) {
                                DropdownMenuItem(
                                    text = { Text("${ep.name} · ${mid.substringAfterLast("/")}") },
                                    onClick = {
                                        onModelSelected(mid, ep.apiType)
                                        modelExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (isGenerating) {
                    IconButton(onClick = onStop, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Stop, "Stop", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                } else {
                    val hasText = text.isNotBlank()
                    if (hasText && canSend) {
                        IconButton(
                            onClick = onSend,
                            modifier = Modifier.size(40.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Filled.ArrowUpward, "Send", modifier = Modifier.size(24.dp))
                        }
                    } else {
                        IconButton(onClick = onSend, enabled = false, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Filled.ArrowUpward, "Send",
                                tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}
