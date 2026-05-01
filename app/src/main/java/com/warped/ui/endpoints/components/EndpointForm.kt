package com.warped.ui.endpoints.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EndpointForm(
    name: String,
    url: String,
    apiType: String,
    modelId: String,
    apiKey: String,
    onFieldChange: (String, String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    val providerTypes = listOf("LM_STUDIO")

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

        OutlinedTextField(
            value = modelId,
            onValueChange = { onFieldChange("modelId", it) },
            label = { Text("Model ID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("gpt-4o-mini, claude-3-5-sonnet, llama3.2") }
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = apiType,
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
                        text = { Text(type) },
                        onClick = {
                            onFieldChange("apiType", type)
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
