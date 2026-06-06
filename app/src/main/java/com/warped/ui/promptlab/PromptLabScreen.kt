package com.warped.ui.promptlab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warped.domain.prompt.PromptTemplate
import com.warped.ui.promptlab.components.TemplateDropdown

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptLabScreen(
    viewModel: PromptLabViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val codeTheme by viewModel.codeTheme.collectAsStateWithLifecycle()
    val codeFontScale by viewModel.codeFontScale.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val configuration = LocalConfiguration.current
    val isWide = configuration.screenWidthDp >= 600

    LaunchedEffect(ui.error) {
        val msg = ui.error
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Prompt Lab") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            TemplateDropdown(
                templates = ui.templates,
                selectedId = ui.selectedTemplateId,
                onSelect = viewModel::selectTemplate,
            )
            Spacer(Modifier.height(12.dp))
            val currentTemplate = ui.templates.firstOrNull { it.id == ui.selectedTemplateId }
            if (currentTemplate != null) {
                Text(
                    text = currentTemplate.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (currentTemplate?.requiresLanguage == true) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = ui.targetLanguage,
                    onValueChange = viewModel::setLanguage,
                    label = { Text("Target language") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(12.dp))
            if (isWide) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    InputColumn(
                        ui = ui,
                        viewModel = viewModel,
                        modifier = Modifier.weight(1f),
                    )
                    OutputColumn(
                        ui = ui,
                        codeTheme = codeTheme,
                        codeFontScale = codeFontScale,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                InputColumn(
                    ui = ui,
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                Spacer(Modifier.height(12.dp))
                OutputColumn(
                    ui = ui,
                    codeTheme = codeTheme,
                    codeFontScale = codeFontScale,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }
}

@Composable
private fun InputColumn(
    ui: PromptLabUiState,
    viewModel: PromptLabViewModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = ui.input,
            onValueChange = viewModel::setInput,
            label = { Text("Prompt input") },
            placeholder = { Text("Paste text, code, or a table…") },
            minLines = 8,
            maxLines = 24,
            enabled = !ui.isRunning,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = viewModel::run,
            enabled = !ui.isRunning && ui.selectedTemplateId != null && ui.input.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (ui.isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text("Running…")
            } else {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Run")
            }
        }
    }
}

@Composable
private fun OutputColumn(
    ui: PromptLabUiState,
    codeTheme: com.warped.domain.model.SyntaxTheme,
    codeFontScale: Float,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    ) {
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)) {
            Text(
                text = "Output",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(6.dp))
            if (ui.output.isEmpty() && !ui.isRunning) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Pick a template, enter input, then tap Run.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val scroll = rememberScrollState()
                com.warped.ui.chat.components.MarkdownText(
                    text = ui.output,
                    codeTheme = codeTheme,
                    codeFontScale = codeFontScale,
                    isStreaming = ui.isRunning,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll),
                )
            }
        }
    }
}
