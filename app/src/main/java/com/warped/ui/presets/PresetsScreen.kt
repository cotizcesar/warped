package com.warped.ui.presets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warped.domain.model.GenerationParameters
import com.warped.domain.model.Preset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetsScreen(
    onParametersChanged: (GenerationParameters) -> Unit = {},
    viewModel: PresetsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val params = uiState.parameters

    LaunchedEffect(params) {
        onParametersChanged(params)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (uiState.selectedPresetName.isNotEmpty()) uiState.selectedPresetName else "Generation Parameters")
                },
                actions = {
                    TextButton(onClick = { viewModel.showSaveDialog() }) { Text("Save") }
                    TextButton(onClick = { viewModel.resetToDefaults() }) { Text("Reset") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (uiState.presets.isNotEmpty()) {
                item {
                    Text("Saved Presets", style = MaterialTheme.typography.titleMedium)
                }
                items(uiState.presets) { preset ->
                    PresetItem(
                        preset = preset,
                        isSelected = preset.id == uiState.selectedPresetId,
                        onLoad = { viewModel.loadPreset(preset) },
                        onDelete = { viewModel.deletePreset(preset.id) }
                    )
                }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            }

            item { Text("Parameters", style = MaterialTheme.typography.titleMedium) }

            item {
                ParameterSlider(
                    label = "Temperature",
                    value = params.temperature,
                    range = 0f..2f,
                    onValueChange = { viewModel.updateTemperature(it) }
                )
            }

            item {
                ParameterSlider(
                    label = "Top P",
                    value = params.topP,
                    range = 0f..1f,
                    onValueChange = { viewModel.updateTopP(it) }
                )
            }

            item {
                ParameterIntSlider(
                    label = "Top K",
                    value = params.topK,
                    range = 1..100,
                    onValueChange = { viewModel.updateTopK(it) }
                )
            }

            item {
                ParameterSlider(
                    label = "Repeat Penalty",
                    value = params.repeatPenalty,
                    range = 1f..2f,
                    onValueChange = { viewModel.updateRepeatPenalty(it) }
                )
            }

            item {
                ParameterIntSlider(
                    label = "Max Tokens",
                    value = params.maxTokens,
                    range = 64..8192,
                    steps = 30,
                    onValueChange = { viewModel.updateMaxTokens(it) }
                )
            }

            item {
                ParameterIntSlider(
                    label = "Context Size",
                    value = params.contextSize,
                    range = 512..32768,
                    steps = 20,
                    onValueChange = { viewModel.updateContextSize(it) }
                )
            }

            item {
                IntInputField(
                    label = "Seed (-1 for random)",
                    value = params.seed,
                    onValueChange = { viewModel.updateSeed(it) }
                )
            }

            item {
                ParameterIntSlider(
                    label = "Threads",
                    value = params.threads,
                    range = 1..16,
                    onValueChange = { viewModel.updateThreads(it) }
                )
            }
        }
    }

    if (uiState.saveDialogVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSaveDialog() },
            title = { Text("Save Preset") },
            text = {
                OutlinedTextField(
                    value = uiState.presetNameInput,
                    onValueChange = { viewModel.updatePresetName(it) },
                    label = { Text("Preset Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.savePreset() }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSaveDialog() }) { Text("Cancel") }
            }
        )
    }

    if (uiState.error != null) {
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                TextButton(onClick = { viewModel.clearError() }) { Text("Dismiss") }
            }
        ) { Text(uiState.error ?: "") }
    }
}

@Composable
fun ParameterSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("%.2f".format(value), style = MaterialTheme.typography.bodySmall)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
fun ParameterIntSlider(
    label: String,
    value: Int,
    range: IntRange,
    steps: Int = 0,
    onValueChange: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("$value", style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = steps
        )
    }
}

@Composable
fun IntInputField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    var textValue by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = textValue,
        onValueChange = {
            textValue = it
            it.toIntOrNull()?.let { intVal -> onValueChange(intVal) }
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true
    )
}

@Composable
fun PresetItem(
    preset: Preset,
    isSelected: Boolean,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isSelected) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) else CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(preset.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "T:${preset.temperature} P:${preset.topP} K:${preset.topK}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onLoad) { Text("Load") }
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Del") }
        }
    }
}
