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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
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
            title = { Text(stringResource(R.string.settings_delete_chats_title)) },
            text = {
                Text(
                    stringResource(R.string.settings_delete_chats_msg, uiState.chatCount)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteAllChats() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.settings_delete_all)) }
            },
            dismissButton = { TextButton(onClick = { viewModel.dismissDeleteChatsDialog() }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (uiState.showDeleteKeysDialog) {
            WarpedAlertDialog(
            onDismissRequest = { viewModel.dismissDeleteKeysDialog() },
            title = { Text(stringResource(R.string.settings_delete_keys_title)) },
            text = { Text(stringResource(R.string.settings_delete_keys_msg)) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteAllApiKeys() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.settings_delete_all)) }
            },
            dismissButton = { TextButton(onClick = { viewModel.dismissDeleteKeysDialog() }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.cd_menu))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            GeneralTab(uiState, viewModel, onNavigateToWizard)
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
        // Data section
        item {
            Text(stringResource(R.string.settings_section_data), style = MaterialTheme.typography.titleMedium)
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B29)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.settings_row_chats), style = MaterialTheme.typography.bodyLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${uiState.chatCount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                onClick = { viewModel.showDeleteChatsDialog() },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text(stringResource(R.string.delete)) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.settings_row_endpoints), style = MaterialTheme.typography.bodyLarge)
                        Text("${uiState.endpointCount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.settings_row_models), style = MaterialTheme.typography.bodyLarge)
                        Text("${uiState.modelCount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.settings_row_presets), style = MaterialTheme.typography.bodyLarge)
                        Text("${uiState.presetCount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Web section
        item {
            Text(stringResource(R.string.settings_section_web), style = MaterialTheme.typography.titleMedium)
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B29)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_grounding_title), style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.settings_grounding_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    val groundingCd = stringResource(R.string.settings_grounding_title)
                    Switch(
                        checked = uiState.webGroundingEnabled,
                        onCheckedChange = viewModel::setWebGroundingEnabled,
                        modifier = Modifier.semantics {
                            contentDescription = groundingCd
                        }
                    )
                }
            }
        }

        // Display section
        item {
            Text(stringResource(R.string.settings_section_display), style = MaterialTheme.typography.titleMedium)
        }
        // Code theme selector
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B29)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.settings_code_theme), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_code_theme_desc),
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
                                                stringResource(R.string.cd_selected),
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
                    Text(stringResource(R.string.settings_code_font), style = MaterialTheme.typography.bodyLarge)
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
            Text(stringResource(R.string.settings_section_app), style = MaterialTheme.typography.titleMedium)
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

        // Web Search section (Phase 55 TAV-01: dedicated card above
        // Security so the Security card stays untouched)
        item {
            Text(stringResource(R.string.settings_section_websearch), style = MaterialTheme.typography.titleMedium)
        }
        item {
            TavilyKeyCard(uiState, viewModel)
        }

        // Security section
        item {
            Text(stringResource(R.string.settings_section_security), style = MaterialTheme.typography.titleMedium)
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B29)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.settings_security_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { viewModel.showDeleteKeysDialog() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text(stringResource(R.string.settings_delete_all_keys_btn)) }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// =========================================
// Phase 55 (TAV-01): Tavily key row (D-01)
//
// Password-style field (T-55-05: no echo of the stored key — the saved key
// is never read back into the field, only a presence line), Save + Clear +
// Test connection, status line with the four test states. Never logs key
// material.
// =========================================
@Composable
private fun TavilyKeyCard(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B29)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.settings_tavily_title), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.settings_tavily_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (uiState.tavilyKeyPresent) stringResource(R.string.settings_tavily_present) else stringResource(R.string.settings_tavily_absent),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.tavilyKeyInput,
                onValueChange = viewModel::onTavilyKeyInputChange,
                label = { Text(stringResource(R.string.settings_tavily_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = !uiState.tavilyTesting,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color(0xFF374151)
                )
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { viewModel.saveTavilyKey() },
                    enabled = !uiState.tavilyTesting
                ) { Text(stringResource(R.string.save)) }
                TextButton(
                    onClick = { viewModel.clearTavilyKey() },
                    enabled = !uiState.tavilyTesting,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.action_clear)) }
                TextButton(
                    onClick = { viewModel.testTavilyConnection() },
                    enabled = !uiState.tavilyTesting
                ) { Text(if (uiState.tavilyTesting) stringResource(R.string.tavily_testing_short) else stringResource(R.string.tavily_test)) }
            }
            if (uiState.tavilyStatus != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    uiState.tavilyStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (uiState.tavilyStatusIsError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Test connection uses one search credit.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
