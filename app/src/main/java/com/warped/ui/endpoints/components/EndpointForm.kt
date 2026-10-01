package com.warped.ui.endpoints.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.warped.domain.model.ProviderType
import com.warped.domain.model.displayNameRes
import com.warped.R

/**
 * Provider-agnostic endpoint form.
 *
 * The [apiType] field drives the shape of the rest of the form:
 *  - LM_STUDIO: Connection Type (Native) + URL + Model ID
 *  - OPENAI / ANTHROPIC / OLLAMA / CUSTOM: URL + Model ID
 *
 * The `Provider Type` selector is the FIRST field so the form is always
 * rendered top-down from "what kind of endpoint is this?" to "give it
 * the details it needs".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EndpointForm(
    name: String,
    url: String,
    apiType: String,
    lmStudioMode: String = "native",
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
    var providerExpanded by remember { mutableStateOf(false) }
    var modeExpanded by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    // Auto-open the model dropdown the first time models arrive.
    var hasAutoOpened by remember { mutableStateOf(false) }
    LaunchedEffect(availableModels) {
        if (availableModels.isNotEmpty() && !hasAutoOpened) {
            modelDropdownExpanded = true
            hasAutoOpened = true
        }
    }

    val providerTypes = listOf(
        ProviderType.LM_STUDIO,
        ProviderType.OPENAI,
        ProviderType.ANTHROPIC,
        ProviderType.OLLAMA,
        ProviderType.CUSTOM
    )
    val lmStudioModes = listOf(
        "native" to "Native",
        "openai" to "OpenAI-compatible",
        "anthropic" to "Anthropic-compatible"
    )

    val selectedType = ProviderType.entries.find { it.name == apiType } ?: ProviderType.LM_STUDIO
    val urlPlaceholder = when (selectedType) {
        ProviderType.LM_STUDIO -> "http://localhost:1234"
        ProviderType.OPENAI -> "https://api.openai.com"
        ProviderType.ANTHROPIC -> "https://api.anthropic.com"
        ProviderType.OLLAMA -> "http://localhost:11434"
        ProviderType.CUSTOM -> "https://your-server.com"
        else -> ""
    }
    val requiresApiKey = selectedType == ProviderType.OPENAI ||
        selectedType == ProviderType.ANTHROPIC ||
        selectedType == ProviderType.CUSTOM

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScrollIfNeeded(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Dns,
                contentDescription = null,
                tint = Color(0xFFD97757),
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.endpoint_configuration), style = MaterialTheme.typography.headlineMedium)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B29)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ProviderTypeSelector(
            selected = selectedType,
            options = providerTypes,
            expanded = providerExpanded,
            onExpandedChange = { providerExpanded = !providerExpanded },
            onSelected = {
                onFieldChange("apiType", it.name)
                providerExpanded = false
            }
        )

        OutlinedTextField(
            value = name,
            onValueChange = { onFieldChange("name", it) },
            label = { Text(stringResource(R.string.name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = url,
            onValueChange = { onFieldChange("url", it) },
            label = { Text(stringResource(R.string.url)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(urlPlaceholder) }
        )

        if (selectedType == ProviderType.LM_STUDIO) {
            LmStudioModeSelector(
                current = lmStudioMode,
                options = lmStudioModes,
                expanded = modeExpanded,
                onExpandedChange = { modeExpanded = !modeExpanded },
                onSelected = {
                    onFieldChange("lmStudioMode", it)
                    modeExpanded = false
                }
            )
        }

        ModelIdField(
            modelId = modelId,
            availableModels = availableModels,
            availableModelsData = availableModelsData,
            isFetching = isFetchingModels,
            modelDropdownExpanded = modelDropdownExpanded,
            onModelDropdownToggle = { modelDropdownExpanded = !modelDropdownExpanded },
            onModelDropdownDismiss = { modelDropdownExpanded = false },
            onFieldChange = onFieldChange,
            onFetchModels = onFetchModels
        )

        if (requiresApiKey) {
            ApiKeyField(
                apiKey = apiKey,
                hasSavedKey = hasSavedKey,
                passwordVisible = passwordVisible,
                onToggleVisibility = { passwordVisible = !passwordVisible },
                onFieldChange = onFieldChange
            )
        }
        }
    }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.cancel))
            }
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97757))
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderTypeSelector(
    selected: ProviderType,
    options: List<ProviderType>,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    onSelected: (ProviderType) -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { onExpandedChange() }
    ) {
        OutlinedTextField(
            value = stringResource(selected.displayNameRes()),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.provider_type)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = onExpandedChange
        ) {
            options.forEach { type ->
                DropdownMenuItem(
                    text = { Text(stringResource(type.displayNameRes())) },
                    onClick = { onSelected(type) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LmStudioModeSelector(
    current: String,
    options: List<Pair<String, String>>,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    onSelected: (String) -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { onExpandedChange() }
    ) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == current }?.second ?: "Native",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.connection_type)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = onExpandedChange
        ) {
            options.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onSelected(key) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelIdField(
    modelId: String,
    availableModels: List<String>,
    availableModelsData: List<com.warped.data.remote.dto.LmStudioModelData>,
    isFetching: Boolean,
    modelDropdownExpanded: Boolean,
    onModelDropdownToggle: () -> Unit,
    onModelDropdownDismiss: () -> Unit,
    onFieldChange: (String, String) -> Unit,
    onFetchModels: () -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = modelDropdownExpanded,
        onExpandedChange = { if (availableModels.isNotEmpty()) onModelDropdownToggle() }
    ) {
        OutlinedTextField(
            value = modelId,
            onValueChange = { onFieldChange("modelId", it) },
            label = { Text(stringResource(R.string.model_id)) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.select_or_type_model)) },
            trailingIcon = {
                if (isFetching) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else if (availableModels.isEmpty()) {
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
                onDismissRequest = onModelDropdownDismiss
            ) {
                availableModels.forEachIndexed { index, model ->
                    val caps = availableModelsData.getOrNull(index)?.capabilities
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(model, modifier = Modifier.weight(1f))
                                if (caps?.vision == true) {
                                    Icon(Icons.Filled.Visibility, contentDescription = stringResource(R.string.badge_vision),
                                        modifier = Modifier.size(14.dp), tint = Color(0xFF4CAF50))
                                    Spacer(Modifier.width(2.dp))
                                }
                                if (caps?.trainedForToolUse == true) {
                                    Icon(Icons.Filled.Build, contentDescription = stringResource(R.string.cap_tool_use),
                                        modifier = Modifier.size(14.dp), tint = Color(0xFFFF9800))
                                }
                            }
                        },
                        onClick = {
                            onFieldChange("modelId", model)
                            onModelDropdownDismiss()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.custom_manual), color = MaterialTheme.colorScheme.primary) },
                    onClick = onModelDropdownDismiss
                )
            }
        }
    }
}

@Composable
private fun ApiKeyField(
    apiKey: String,
    hasSavedKey: Boolean,
    passwordVisible: Boolean,
    onToggleVisibility: () -> Unit,
    onFieldChange: (String, String) -> Unit
) {
    OutlinedTextField(
        value = apiKey,
        onValueChange = { onFieldChange("apiKey", it) },
        label = { Text(stringResource(R.string.api_key)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { if (hasSavedKey && apiKey.isBlank()) Text("••••••••") },
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = onToggleVisibility) {
                Text(if (passwordVisible) stringResource(R.string.hide) else stringResource(R.string.show))
            }
        }
    )
}

@Composable
private fun Modifier.verticalScrollIfNeeded(): Modifier {
    val state = rememberScrollState()
    return this.then(Modifier.verticalScroll(state))
}
