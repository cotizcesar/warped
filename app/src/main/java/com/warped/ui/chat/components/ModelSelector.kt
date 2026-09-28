package com.warped.ui.chat.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.warped.domain.model.Endpoint
import com.warped.domain.model.LocalModel
import com.warped.domain.model.ProviderType

/**
 * CHAT-04 / CHAT-05: ModalBottomSheet model picker.
 * Triggered by an icon button near the chat input. Lists local models and network endpoints.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectorSheet(
    visible: Boolean,
    selectedModelId: String?,
    selectedProvider: ProviderType?,
    localModels: List<LocalModel>,
    endpoints: List<Endpoint> = emptyList(),
    endpointModels: Map<Long, List<String>> = emptyMap(),
    onDismiss: () -> Unit,
    onModelSelected: (String, ProviderType, Long?) -> Unit
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Select a model",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (localModels.isNotEmpty()) {
                SectionHeader("Local Models")
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(localModels, key = { it.filePath }) { model ->
                        ModelRow(
                            title = model.name,
                            subtitle = model.sizeBytes.humanReadableSize(),
                            typePill = "LiteRT-LM",
                            isSelected = selectedModelId == model.filePath,
                            onClick = {
                                onModelSelected(model.filePath, ProviderType.LITE_RT_LM, null)
                                onDismiss()
                            }
                        )
                    }
                }
            }

            if (endpoints.isNotEmpty()) {
                if (localModels.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
                SectionHeader("Network Endpoints")
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(endpoints, key = { it.id }) { endpoint ->
                        val modelId = endpoint.modelId
                        if (modelId != null) {
                            val models = endpointModels[endpoint.id].orEmpty()
                            val subtitle = if (models.isNotEmpty()) {
                                "${models.take(3).joinToString(", ")}${if (models.size > 3) "…" else ""}"
                            } else {
                                endpoint.url
                            }
                            ModelRow(
                                title = endpoint.name,
                                subtitle = subtitle,
                                typePill = "Net",
                                isSelected = selectedModelId == modelId && selectedProvider == endpoint.apiType,
                                onClick = {
                                    onModelSelected(modelId, endpoint.apiType, endpoint.id)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }

            if (localModels.isEmpty() && endpoints.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No models available. Download a .litertlm model or add an endpoint.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun ModelRow(
    title: String,
    subtitle: String,
    typePill: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val (pillColor, pillText) = when (typePill) {
        "LiteRT-LM" -> Color(0xFF4CAF50) to "LiteRT-LM"
        else -> Color(0xFF2196F3) to "Net"
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (isSelected) Color(0xFF2B2B29)
                else Color.Transparent,
        border = if (isSelected) BorderStroke(1.dp, Color(0xFFD97757)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Storage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = pillColor.copy(alpha = 0.15f),
                contentColor = pillColor
            ) {
                Text(
                    text = pillText,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
            if (isSelected) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun Long.humanReadableSize(): String = when {
    this >= 1024L * 1024 * 1024 -> "%.2f GB".format(this.toDouble() / (1024L * 1024 * 1024))
    this >= 1024 * 1024 -> "%.0f MB".format(this.toDouble() / (1024 * 1024))
    this >= 1024 -> "%.0f KB".format(this.toDouble() / 1024)
    else -> "$this B"
}
