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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.warped.ui.chat.components.openPlayStoreListing

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
    // (popBackStack) — gesture and button identical.
    BackHandler(onBack = onBack)

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
        // Phase 66 (RATE-02): always-reachable Store listing — same
        // Card/Column/TextButton shape as the wizard card above, so the
        // row stays reachable even when the review dialog is
        // quota-suppressed. market:// first with https fallback.
        item {
            val storeContext = LocalContext.current
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B29)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.settings_review_title), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_review_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { openPlayStoreListing(storeContext) }) {
                        Text(stringResource(R.string.settings_review_action), color = Color(0xFFD97757))
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }

        // Build marker (debug aid): version + code so a screenshot proves
        // which build is installed. No strings needed (numeric only).
        item {
            Text(
                "v${com.warped.BuildConfig.VERSION_NAME} (${com.warped.BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
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
