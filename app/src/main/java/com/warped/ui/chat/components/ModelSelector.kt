package com.warped.ui.chat.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelector(
    selectedModelId: String?,
    selectedProvider: ProviderType?,
    localModels: List<LocalModel>,
    endpoints: List<Endpoint> = emptyList(),
    onModelSelected: (String, ProviderType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLocal = localModels.firstOrNull { it.filePath == selectedModelId }
    val selectedEndpoint = endpoints.firstOrNull {
        it.modelId == selectedModelId && it.apiType == selectedProvider
    }
    val hasItems = localModels.isNotEmpty() || endpoints.isNotEmpty()
    val label = when {
        selectedLocal != null -> "${selectedLocal.name} (${formatLabel(selectedLocal)})"
        selectedEndpoint != null -> "${selectedEndpoint.name} (${endpointTypeLabel(selectedEndpoint)})"
        !hasItems -> stringResource(R.string.no_models_select_model)
        else -> stringResource(R.string.select_model)
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (hasItems) expanded = !expanded }
    ) {
        TextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            enabled = hasItems,
            textStyle = MaterialTheme.typography.titleMedium,
            colors = ExposedDropdownMenuDefaults.textFieldColors(
                disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledIndicatorColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = MaterialTheme.colorScheme.surface,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            trailingIcon = {
                if (hasItems) {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = hasItems)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (localModels.isNotEmpty()) {
                localModels.forEach { model ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(model.name)
                                Spacer(Modifier.width(8.dp))
                                ModelTypePill(formatLabel(model))
                            }
                        },
                        onClick = {
                            onModelSelected(model.filePath, ProviderType.LOCAL)
                            expanded = false
                        }
                    )
                }
            }
            if (localModels.isNotEmpty() && endpoints.isNotEmpty()) {
                HorizontalDivider()
            }
            if (endpoints.isNotEmpty()) {
                endpoints.forEach { endpoint ->
                    val modelId = endpoint.modelId
                    if (modelId != null) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(endpoint.name)
                                    Spacer(Modifier.width(8.dp))
                                    ModelTypePill(endpointTypeLabel(endpoint))
                                }
                            },
                            onClick = {
                                onModelSelected(modelId, endpoint.apiType)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun formatLabel(model: LocalModel): String = "LiteRT-LM"

private fun endpointTypeLabel(endpoint: Endpoint): String =
    when {
        endpoint.apiType == ProviderType.LM_STUDIO -> "Net"
        endpoint.apiType == ProviderType.OLLAMA -> "Net"
        endpoint.apiType == ProviderType.OPENAI -> "Net"
        else -> "Net"
    }

@Composable
private fun ModelTypePill(type: String) {
    val (color, label) = when (type) {
        "LiteRT-LM" -> Color(0xFF4CAF50) to "LiteRT-LM"
        else -> MaterialTheme.colorScheme.outline to type
    }
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = color.copy(alpha = 0.15f),
        contentColor = color
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}
