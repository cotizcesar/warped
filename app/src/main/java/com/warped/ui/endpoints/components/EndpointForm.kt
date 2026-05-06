package com.warped.ui.endpoints.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.warped.domain.model.ProviderType
import com.warped.domain.model.displayNameRes
import com.warped.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EndpointForm(
    name: String,
    url: String,
    apiType: String,
    modelId: String,
    apiKey: String,
    hasSavedKey: Boolean = false,
    onFieldChange: (String, String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    availableModels: List<String> = emptyList(),
    availableModelsData: List<com.warped.data.remote.dto.LmStudioModelData> = emptyList(),
    isFetchingModels: Boolean = false,
    onFetchModels: () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }
    val providerTypes = ProviderType.entries.filter {
        it != ProviderType.LOCAL && it != ProviderType.LITE_RT_LM
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Endpoint Configuration", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = name,
            onValueChange = { onFieldChange("name", it) },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = url,
            onValueChange = { onFieldChange("url", it) },
            label = { Text("URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("https://api.openai.com") }
        )

        val modelOptions = if (availableModels.isNotEmpty()) availableModels else listOf(modelId).filter { it.isNotBlank() }

        ExposedDropdownMenuBox(
            expanded = modelDropdownExpanded,
            onExpandedChange = { if (availableModels.isNotEmpty()) modelDropdownExpanded = !modelDropdownExpanded }
        ) {
            OutlinedTextField(
                value = modelId,
                onValueChange = { onFieldChange("modelId", it) },
                label = { Text("Model ID") },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                singleLine = true,
                placeholder = { Text("Select or type model ID") },
                trailingIcon = {
                    if (isFetchingModels) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else if (availableModels.isEmpty() && apiType == ProviderType.LM_STUDIO.name) {
                        TextButton(onClick = onFetchModels) { Text(stringResource(R.string.fetch)) }
                    } else {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded)
                    }
                },
                enabled = true
            )
            if (availableModels.isNotEmpty()) {
                ExposedDropdownMenu(
                    expanded = modelDropdownExpanded,
                    onDismissRequest = { modelDropdownExpanded = false }
                ) {
                    availableModels.forEachIndexed { index, model ->
                        val caps = availableModelsData.getOrNull(index)?.capabilities
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Text(model, modifier = Modifier.weight(1f))
                                    if (caps?.vision == true) {
                                        Icon(Icons.Filled.Visibility, contentDescription = "Vision",
                                            modifier = Modifier.size(14.dp), tint = Color(0xFF4CAF50))
                                        Spacer(Modifier.width(2.dp))
                                    }
                                    if (caps?.trainedForToolUse == true) {
                                        Icon(Icons.Filled.Build, contentDescription = "Tool use",
                                            modifier = Modifier.size(14.dp), tint = Color(0xFFFF9800))
                                    }
                                }
                            },
                            onClick = {
                                onFieldChange("modelId", model)
                                modelDropdownExpanded = false
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Custom (type manually)", color = MaterialTheme.colorScheme.primary) },
                        onClick = { modelDropdownExpanded = false }
                    )
                }
            }
        }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            val selectedType = ProviderType.entries.find { it.name == apiType }
            OutlinedTextField(
                value = if (selectedType != null) stringResource(selectedType.displayNameRes()) else apiType,
                onValueChange = {},
                readOnly = true,
                label = { Text("Provider Type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                providerTypes.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(stringResource(type.displayNameRes())) },
                        onClick = {
                            onFieldChange("apiType", type.name)
                            expanded = false
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = apiKey,
            onValueChange = { onFieldChange("apiKey", it) },
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { if (hasSavedKey && apiKey.isBlank()) Text("••••••••") },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(if (passwordVisible) "Hide" else "Show")
                }
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
            Button(onClick = onSave, modifier = Modifier.weight(1f)) {
                Text("Save")
            }
        }
    }
}
