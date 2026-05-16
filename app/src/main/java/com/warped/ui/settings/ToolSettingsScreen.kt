package com.warped.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warped.data.local.inference.tools.ToolCategory
import com.warped.data.local.inference.tools.ToolDefinitions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ToolSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val totalTokens = ToolDefinitions.totalTokens
    val enabledTokens = uiState.enabledIds.filter { it in (uiState.toolStates.map { s -> s.id.takeIf { s.enabled } }) }.count()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tools") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Token Usage", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Each tool you enable consumes tokens from the model's 32K context window. " +
                                    "More tools = less room for conversation history.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        val enabledCount = uiState.toolStates.count { it.enabled }
                        val enabledTokenSum = uiState.toolStates.filter { it.enabled }.sumOf { it.tokenEstimate }
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Column {
                                Text(
                                    "$enabledCount / ${ToolDefinitions.all.size} tools",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text("enabled", style = MaterialTheme.typography.labelSmall)
                            }
                            Column {
                                Text(
                                    "~$enabledTokenSum tokens",
                                    fontWeight = FontWeight.Bold,
                                    color = if (enabledTokenSum > 3000) Color(0xFFFF9800) else MaterialTheme.colorScheme.primary
                                )
                                Text("of $totalTokens total", style = MaterialTheme.typography.labelSmall)
                            }
                            Column {
                                Text(
                                    "${32000 - enabledTokenSum} left",
                                    fontWeight = FontWeight.Bold,
                                    color = if (enabledTokenSum > 8000) Color(0xFFFF4444)
                                        else Color(0xFF4CAF50)
                                )
                                Text("for conversation", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Group by category
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (enabled)
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    tool.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (enabled) FontWeight.Medium else FontWeight.Normal,
                                    color = if (enabled)
                                        MaterialTheme.colorScheme.onSurface
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Text(
                                    tool.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                                Text(
                                    "~${tool.tokenEstimate} tokens",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Switch(
                                checked = enabled,
                                onCheckedChange = { viewModel.toggleTool(tool.id) }
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }

            // Manual tool calling toggle
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Manual tool calling",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "When enabled, shows which tool is being used and lets you see tool results before sending them to the model.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = uiState.manualToolCalling,
                            onCheckedChange = { viewModel.setManualToolCalling(it) }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
