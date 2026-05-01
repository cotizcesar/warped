package com.warped.ui.chat.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
        selectedLocal != null -> "${selectedLocal.name} (${selectedProvider?.name ?: "LOCAL"})"
        selectedEndpoint != null -> "${selectedEndpoint.name} (${selectedProvider?.name ?: ""})"
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
            // Local models section
            if (localModels.isNotEmpty()) {
                localModels.forEach { model ->
                    DropdownMenuItem(
                        text = { Text("${model.name} (local)") },
                        onClick = {
                            onModelSelected(model.filePath, ProviderType.LOCAL)
                            expanded = false
                        }
                    )
                }
            }
            // Network endpoints section (with divider if both exist)
            if (localModels.isNotEmpty() && endpoints.isNotEmpty()) {
                HorizontalDivider()
            }
            if (endpoints.isNotEmpty()) {
                endpoints.forEach { endpoint ->
                    val modelId = endpoint.modelId
                    if (modelId != null) {
                        DropdownMenuItem(
                            text = { Text("${endpoint.name} · ${endpoint.apiType.name}") },
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
