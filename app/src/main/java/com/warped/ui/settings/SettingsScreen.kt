package com.warped.ui.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
    onNavigateToWizard: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // API-03: system back follows the same onBack path wired in the nav graph
    // (popBackStack) — gesture and button identical. Delete-confirm dialogs
    // self-dismiss and keep priority while visible.
    BackHandler(onBack = onBack)

    if (uiState.showDeleteChatsDialog) {
            WarpedAlertDialog(
            onDismissRequest = { viewModel.dismissDeleteChatsDialog() },
            title = { Text(stringResource(R.string.settings_delete_chats_title)) },
            text = {
                Text(
                    pluralStringResource(R.plurals.settings_delete_chats_msg, uiState.chatCount, uiState.chatCount)
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
            SettingsSectionHeader(
                icon = Icons.Filled.Storage,
                title = stringResource(R.string.settings_section_data)
            )
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
            SettingsSectionHeader(
                icon = Icons.Filled.Public,
                title = stringResource(R.string.settings_section_web)
            )
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
            SettingsSectionHeader(
                icon = Icons.Filled.Palette,
                title = stringResource(R.string.settings_section_display)
            )
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
            SettingsSectionHeader(
                icon = Icons.Filled.Apps,
                title = stringResource(R.string.settings_section_app)
            )
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
            SettingsSectionHeader(
                icon = Icons.Filled.Lock,
                title = stringResource(R.string.settings_section_security)
            )
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

/**
 * Section header in the model-card language: muted icon badge + semibold
 * title, consistent with card headers across the app.
 */
@Composable
private fun SettingsSectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFFD97757),
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
