package com.warped.ui.chat.components

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
            // Row 1: Text input + send/stop button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
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
                Spacer(Modifier.width(8.dp))
                if (isGenerating) {
                    IconButton(onClick = onStop, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                } else {
                    IconButton(onClick = onSend, enabled = canSend && text.isNotBlank(), modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }

            Spacer(Modifier.height(5.dp))

            // Row 2: Image button (left), model picker + reasoning (right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onAddImage, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.AddPhotoAlternate, "Add image", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
                }

                Spacer(Modifier.weight(1f))

                // Model picker button with dropdown
                var modelExpanded by remember { mutableStateOf(false) }
                val modelLabel = selectedModelId?.substringAfterLast("/") ?: "Model"
                Box {
                    TextButton(onClick = { modelExpanded = true }) {
                        Text(modelLabel, color = Color.White.copy(alpha = 0.6f), maxLines = 1)
                        Icon(Icons.Filled.KeyboardArrowDown, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(
                        expanded = modelExpanded,
                        onDismissRequest = { modelExpanded = false },
                        modifier = Modifier.align(Alignment.TopEnd)
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

                // Reasoning toggle
                IconButton(onClick = onToggleReasoning, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Filled.Psychology,
                        contentDescription = "Reasoning",
                        tint = if (reasoningEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
