package com.warped.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warped.R
import com.warped.domain.model.SyntaxTheme
import com.warped.domain.model.TokenType
import com.warped.ui.components.WarpedAlertDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit = {},
    onNavigateToWizard: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.showDeleteChatsDialog) {
            WarpedAlertDialog(
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
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete All") }
            },
            dismissButton = { TextButton(onClick = { viewModel.dismissDeleteChatsDialog() }) { Text("Cancel") } }
        )
    }

    if (uiState.showDeleteKeysDialog) {
            WarpedAlertDialog(
            onDismissRequest = { viewModel.dismissDeleteKeysDialog() },
            title = { Text("Delete All API Keys") },
            text = { Text("This will permanently delete all stored API keys. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteAllApiKeys() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete All") }
            },
            dismissButton = { TextButton(onClick = { viewModel.dismissDeleteKeysDialog() }) { Text("Cancel") } }
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Tab row
            PrimaryTabRow(
                selectedTabIndex = uiState.selectedTab.ordinal,
                modifier = Modifier.fillMaxWidth()
            ) {
                SettingsTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(tab.name) }
                    )
                }
            }

            when (uiState.selectedTab) {
                SettingsTab.General -> GeneralTab(uiState, viewModel, onNavigateToWizard)
            }
        }

        if (uiState.message != null) {
            Snackbar(modifier = Modifier.padding(16.dp)) { Text(uiState.message!!) }
        }
        if (uiState.error != null) {
            Snackbar(modifier = Modifier.padding(16.dp)) { Text(uiState.error!!, color = MaterialTheme.colorScheme.error) }
        }
    }
}

// =========================================
// GENERAL TAB
// =========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeneralTab(uiState: SettingsUiState, viewModel: SettingsViewModel, onNavigateToWizard: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        // Hugging Face section
        item {
            Text("Hugging Face", style = MaterialTheme.typography.titleMedium)
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B29)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Access Token", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (uiState.hasHfToken) "Token configured" else "Required for gated/private models",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.hasHfToken) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!uiState.hasHfToken) {
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = uiState.hfToken,
                                onValueChange = { viewModel.updateHfToken(it) },
                                label = { Text("Token") },
                                placeholder = { Text("hf_...") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { viewModel.saveHfToken() }) { Text("Save") }
                        }
                    }
                    if (uiState.hasHfToken) {
                        Spacer(Modifier.height(4.dp))
                        TextButton(
                            onClick = { viewModel.deleteHfToken() },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("Remove token") }
                    }
                }
            }
        }

        // Data section
        item {
            Text("Data", style = MaterialTheme.typography.titleMedium)
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B29)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Chats", style = MaterialTheme.typography.bodyLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${uiState.chatCount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                onClick = { viewModel.showDeleteChatsDialog() },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text("Delete") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Endpoints", style = MaterialTheme.typography.bodyLarge)
                        Text("${uiState.endpointCount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Models", style = MaterialTheme.typography.bodyLarge)
                        Text("${uiState.modelCount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Presets", style = MaterialTheme.typography.bodyLarge)
                        Text("${uiState.presetCount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Display section
        item {
            Text("Display", style = MaterialTheme.typography.titleMedium)
        }
        // Code theme selector
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B29)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Code Theme", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Color scheme for code blocks in chat",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    var themeExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = themeExpanded,
                        onExpandedChange = { themeExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = uiState.codeTheme.label,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExpanded) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color(0xFF374151)
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = themeExpanded,
                            onDismissRequest = { themeExpanded = false }
                        ) {
                            SyntaxTheme.all().forEach { theme ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            ThemeSwatchStrip(theme = theme)
                                            Spacer(Modifier.width(12.dp))
                                            Text(theme.label, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    },
                                    onClick = {
                                        viewModel.setCodeTheme(theme)
                                        themeExpanded = false
                                    },
                                    trailingIcon = {
                                        if (theme.key == uiState.codeTheme.key) {
                                            Icon(
                                                Icons.Outlined.Check,
                                                "Selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        // Code font scale slider
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B29)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Code font size", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "%.1fx".format(uiState.codeFontScale),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.width(40.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Slider(
                            value = uiState.codeFontScale,
                            onValueChange = { viewModel.setCodeFontScale(it) },
                            valueRange = 0.8f..1.5f,
                            steps = 6,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // App section
        item {
            Text("App", style = MaterialTheme.typography.titleMedium)
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B29)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.settings_wizard_title), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_wizard_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onNavigateToWizard) {
                        Text(stringResource(R.string.settings_wizard_run), color = Color(0xFFD97757))
                    }
                }
            }
        }

        // Security section
        item {
            Text("Security", style = MaterialTheme.typography.titleMedium)
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B29)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "API keys are encrypted using Android Keystore (AES-256-GCM)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { viewModel.showDeleteKeysDialog() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete all keys") }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// =========================================
// TOOLS TAB
// =========================================
// ADVANCED TAB (removed) — params are now per-model
// =========================================

@Composable
private fun ThemeSwatchStrip(theme: SyntaxTheme) {
    val isDark = isSystemInDarkTheme()
    val variant = if (isDark) theme.darkVariant else theme.lightVariant
    val swatches = listOf(TokenType.KEYWORD, TokenType.STRING, TokenType.COMMENT, TokenType.BACKGROUND)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        swatches.forEach { tokenType ->
            val color = Color(variant[tokenType]?.argb ?: 0xFF000000.toInt())
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
    }
}

private operator fun <T : Comparable<T>> ClosedFloatingPointRange<T>.component1(): T = start
private operator fun <T : Comparable<T>> ClosedFloatingPointRange<T>.component2(): T = endInclusive
