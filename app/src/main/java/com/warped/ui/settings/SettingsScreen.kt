package com.warped.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.showDeleteChatsDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteChatsDialog() },
            title = { Text("Delete All Chats") },
            text = {
                Text(
                    "This will permanently delete all ${uiState.chatCount} conversations " +
                        "and their messages. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteAllChats() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteChatsDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (uiState.showDeleteKeysDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteKeysDialog() },
            title = { Text("Delete All API Keys") },
            text = {
                Text(
                    "This will delete all stored API keys for ${uiState.endpointCount} " +
                        "endpoints. You will need to re-enter them to connect."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteAllApiKeys() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteKeysDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Warped",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            "v0.1.0",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "LM Studio for Android — run LLMs locally and remotely.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item {
                Text("Data", style = MaterialTheme.typography.titleMedium)
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "Chat History",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "${uiState.chatCount} conversations",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(
                            onClick = { viewModel.showDeleteChatsDialog() },
                            enabled = uiState.chatCount > 0 && !uiState.isDeletingChats,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(
                                if (uiState.isDeletingChats) "Deleting..." else "Delete All"
                            )
                        }
                    }
                }
            }

            item {
                Text("API Keys", style = MaterialTheme.typography.titleMedium)
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "Stored API Keys",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "${uiState.endpointCount} endpoints with keys",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(
                            onClick = { viewModel.showDeleteKeysDialog() },
                            enabled = uiState.endpointCount > 0 && !uiState.isDeletingKeys,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(
                                if (uiState.isDeletingKeys) "Deleting..." else "Delete All"
                            )
                        }
                    }
                }
            }

            item {
                Text("Storage", style = MaterialTheme.typography.titleMedium)
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Local Models",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text("${uiState.modelCount} imported")
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Saved Presets",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text("${uiState.presetCount} presets")
                        }
                    }
                }
            }

            item {
                Text("Hugging Face", style = MaterialTheme.typography.titleMedium)
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Access Token", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (uiState.hasHfToken) "Token configured" else "Required for gated/private models",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.hasHfToken) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = uiState.hfToken,
                                onValueChange = { viewModel.updateHfToken(it) },
                                label = { Text("Token") },
                                placeholder = { Text("hf_...") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { viewModel.saveHfToken() }) {
                                Text("Save")
                            }
                        }
                        if (uiState.hasHfToken) {
                            Spacer(Modifier.height(4.dp))
                            TextButton(
                                onClick = { viewModel.deleteHfToken() },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Remove token")
                            }
                        }
                    }
                }
            }

            item {
                Text("Security", style = MaterialTheme.typography.titleMedium)
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "API keys are encrypted using Android Keystore (AES-256-GCM)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Keys never appear in logs — RedactingTree filters sensitive data",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Network: cleartext blocked except for local LAN addresses",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }

    if (uiState.message != null || uiState.error != null) {
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                TextButton(onClick = { viewModel.clearMessage() }) {
                    Text("OK")
                }
            }
        ) {
            Text(uiState.message ?: uiState.error ?: "")
        }
    }
}
