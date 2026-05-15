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
import com.warped.data.local.inference.tools.ToolCategory
import com.warped.data.local.inference.tools.ToolDefinitions
import com.warped.domain.model.SyntaxTheme
import com.warped.domain.model.TokenType
import com.warped.ui.components.WarpedAlertDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit = {},
    onNavigateToTools: () -> Unit = {},
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
                SettingsTab.Tools -> ToolsTab(uiState, viewModel)
                SettingsTab.Advanced -> AdvancedTab(uiState, viewModel)
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
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
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
@Composable
private fun ToolsTab(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val enabledCount = uiState.toolStates.count { it.enabled }
    val enabledTokenSum = uiState.toolStates.filter { it.enabled }.sumOf { it.tokenEstimate }
    val contextSize = uiState.advancedParams.contextSize
    val maxOutTokens = uiState.advancedParams.maxTokens
    val availableForConv = contextSize - enabledTokenSum - maxOutTokens
    val toolsRatio = (enabledTokenSum.toFloat() / contextSize.toFloat() * 100).toInt()
    val totalRatio = ((enabledTokenSum + maxOutTokens).toFloat() / contextSize.toFloat() * 100).toInt()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2B2B29)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Token Budget", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Context window: $contextSize tokens. Max output: $maxOutTokens tokens. " +
                            "Each tool you enable consumes tokens from what's left for conversation history.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    // Progress bar: tools / total
                    LinearProgressIndicator(
                        progress = { (enabledTokenSum.toFloat() / contextSize).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = when { toolsRatio > 25 -> Color(0xFFFF4444); toolsRatio > 10 -> Color(0xFFFF9800); else -> Color(0xFF4CAF50) },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column {
                            Text(
                                "$enabledCount / ${ToolDefinitions.all.size}",
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary
                            )
                            Text("tools on", style = MaterialTheme.typography.labelSmall)
                        }
                        Column {
                            Text(
                                "~$enabledTokenSum",
                                fontWeight = FontWeight.Bold,
                                color = if (toolsRatio > 25) Color(0xFFFF4444)
                                    else if (toolsRatio > 10) Color(0xFFFF9800)
                                    else Color(0xFF4CAF50)
                            )
                            Text("tool tokens ($toolsRatio%)", style = MaterialTheme.typography.labelSmall)
                        }
                        Column {
                            Text(
                                "$maxOutTokens",
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface
                            )
                            Text("max output", style = MaterialTheme.typography.labelSmall)
                        }
                        Column {
                            Text(
                                "$availableForConv",
                                fontWeight = FontWeight.Bold,
                                color = if (availableForConv < 1000) Color(0xFFFF4444)
                                    else if (availableForConv < 4096) Color(0xFFFF9800)
                                    else Color(0xFF4CAF50)
                            )
                            Text("for history (${100 - totalRatio}%)", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        val categories = ToolDefinitions.all.groupBy { it.category }
        categories.forEach { (category, tools) ->
            item {
                Text(
                    category.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
            }
            items(tools, key = { it.id }) { tool ->
                val toolState = uiState.toolStates.find { it.id == tool.id }
                val enabled = toolState?.enabled ?: tool.defaultEnabled
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (enabled) Color(0xFF2B2B29)
                            else Color(0xFF2B2B29).copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                tool.name, style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (enabled) FontWeight.Medium else FontWeight.Normal,
                                color = if (enabled) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Text(
                                tool.description, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2
                            )
                            Text(
                                "~${tool.tokenEstimate} tokens",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = enabled, onCheckedChange = { viewModel.toggleTool(tool.id) })
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// =========================================
// ADVANCED TAB
// =========================================
@Composable
private fun AdvancedTab(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val p = uiState.advancedParams

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Text("Model Defaults", style = MaterialTheme.typography.titleMedium)
            Text(
                "These parameters apply to new conversations",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Temperature
        item {
            ParamSlider(
                label = "Temperature",
                value = p.temperature,
                range = 0f..2f,
                steps = 19,
                description = "Controls randomness. Lower = more deterministic.",
                format = { "%.1f".format(it) }
            ) { v -> viewModel.updateAdvancedParam { it.copy(temperature = v) } }
        }

        // Top P
        item {
            ParamSlider(
                label = "Top P",
                value = p.topP,
                range = 0f..1f,
                steps = 9,
                description = "Nucleus sampling. Lower = more focused.",
                format = { "%.1f".format(it) }
            ) { v -> viewModel.updateAdvancedParam { it.copy(topP = v) } }
        }

        // Top K
        item {
            ParamSlider(
                label = "Top K",
                value = p.topK.toFloat(),
                range = 1f..100f,
                steps = 9,
                description = "Limits token selection to top K.",
                format = { it.toInt().toString() }
            ) { v -> viewModel.updateAdvancedParam { it.copy(topK = v.toInt()) } }
        }

        // Repeat Penalty
        item {
            ParamSlider(
                label = "Repeat Penalty",
                value = p.repeatPenalty,
                range = 1f..2f,
                steps = 9,
                description = "Penalizes token repetition. Higher = less repetition.",
                format = { "%.2f".format(it) }
            ) { v -> viewModel.updateAdvancedParam { it.copy(repeatPenalty = v) } }
        }

        // Max Tokens
        item {
            ParamSlider(
                label = "Max Tokens",
                value = p.maxTokens.toFloat(),
                range = 128f..8192f,
                steps = 8,
                description = "Maximum output tokens per response.",
                format = { it.toInt().toString() }
            ) { v -> viewModel.updateAdvancedParam { it.copy(maxTokens = v.toInt()) } }
        }

        // Context Size
        item {
            ParamSlider(
                label = "Context Size",
                value = p.contextSize.toFloat(),
                range = 512f..32768f,
                steps = 6,
                description = "Maximum context window size.",
                format = { it.toInt().toString() }
            ) { v -> viewModel.updateAdvancedParam { it.copy(contextSize = v.toInt()) } }
        }

        // Seed
        item {
            ParamSlider(
                label = "Seed",
                value = p.seed.toFloat(),
                range = -1f..100000f,
                steps = 10,
                description = "Random seed. -1 = random each time.",
                format = { if (it.toInt() == -1) "Random" else it.toInt().toString() }
            ) { v -> viewModel.updateAdvancedParam { it.copy(seed = v.toInt()) } }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ParamSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    description: String,
    format: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B29)),
                shape = RoundedCornerShape(12.dp)
            ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    format(value),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                steps = steps,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

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
