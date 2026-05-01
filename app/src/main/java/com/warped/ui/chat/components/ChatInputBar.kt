package com.warped.ui.chat.components

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    onAddImage: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onAddImage) {
            Icon(Icons.Filled.AddPhotoAlternate, "Add image", modifier = Modifier.size(22.dp))
        }
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.type_message)) },
            enabled = !isGenerating && canSend,
            maxLines = 4,
            shape = MaterialTheme.shapes.extraLarge,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = Color.Transparent
            )
        )
        Spacer(Modifier.width(4.dp))
        // Reasoning toggle
        IconButton(onClick = onToggleReasoning) {
            Icon(
                Icons.Filled.Psychology,
                contentDescription = "Toggle reasoning",
                tint = if (reasoningEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
        if (isGenerating) {
            IconButton(onClick = onStop) {
                Icon(Icons.Filled.Stop, contentDescription = "Stop generating")
            }
        } else {
            IconButton(onClick = onSend, enabled = canSend && text.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message")
            }
        }
    }
}
