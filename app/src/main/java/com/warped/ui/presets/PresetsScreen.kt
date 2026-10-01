package com.warped.ui.presets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.warped.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warped.domain.model.GenerationParameters
import com.warped.ui.components.WarpedAlertDialog
import com.warped.domain.model.Preset
import com.warped.domain.model.MemoryTier
import androidx.compose.foundation.layout.height

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetsScreen(
    onParametersChanged: (GenerationParameters) -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: PresetsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val params = uiState.parameters
    val isLiteRTActive = uiState.activeFormat.equals("LITERTLM", ignoreCase = true)

    LaunchedEffect(params) {
        onParametersChanged(params)
    }

    // API-03: system back follows the same onBack path as the app-bar arrow —
    // gesture and button identical, predictive animation free on 36.
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (uiState.selectedPresetName.isNotEmpty()) uiState.selectedPresetName else stringResource(R.string.preset_params_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.showSaveDialog() }) { Text(stringResource(R.string.save)) }
                    TextButton(onClick = { viewModel.resetToDefaults() }) { Text(stringResource(R.string.preset_reset)) }
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
            if (uiState.smartPresetName != null) {
                item(key = "smart-preset-card") {
                    SmartPresetCard(
                        presetName = uiState.smartPresetName!!,
                        tier = uiState.smartPresetTier,
                        availableGb = uiState.availableGb,
                        totalGb = uiState.totalGb,
                        isActive = !uiState.isCustomOverride && uiState.selectedPresetId == null,
                        onApply = { viewModel.applySmartPreset() }
                    )
                }
            }

            if (uiState.presets.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.preset_saved_title), style = MaterialTheme.typography.titleMedium)
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

            item { Text(stringResource(R.string.preset_section_params), style = MaterialTheme.typography.titleMedium) }

            item {
                ParameterSlider(
                    label = stringResource(R.string.param_temperature),
                    value = params.temperature,
                    range = 0f..2f,
                    onValueChange = { viewModel.updateTemperature(it) }
                )
            }

            item {
                ParameterSlider(
                    label = stringResource(R.string.param_top_p),
                    value = params.topP,
                    range = 0f..1f,
                    onValueChange = { viewModel.updateTopP(it) }
                )
            }

            item {
                ParameterIntSlider(
                    label = stringResource(R.string.param_top_k),
                    value = params.topK,
                    range = 1..100,
                    onValueChange = { viewModel.updateTopK(it) }
                )
            }

            item {
                ParameterSlider(
                    label = stringResource(R.string.param_repeat_penalty),
                    value = params.repeatPenalty,
                    range = 1f..2f,
                    enabled = !isLiteRTActive,
                    unsupportedLabel = if (isLiteRTActive) stringResource(R.string.preset_unsupported_litertlm) else null,
                    onValueChange = { viewModel.updateRepeatPenalty(it) }
                )
            }

            item {
                ParameterIntSlider(
                    label = stringResource(R.string.param_max_tokens),
                    value = params.maxTokens,
                    range = 64..8192,
                    steps = 30,
                    onValueChange = { viewModel.updateMaxTokens(it) }
                )
            }

            item {
                ParameterIntSlider(
                    label = stringResource(R.string.param_context_size),
                    value = params.contextSize,
                    range = 512..32768,
                    steps = 20,
                    enabled = !isLiteRTActive,
                    unsupportedLabel = if (isLiteRTActive) stringResource(R.string.preset_unsupported_litertlm) else null,
                    onValueChange = { viewModel.updateContextSize(it) }
                )
            }

            item {
                IntInputField(
                    label = stringResource(R.string.param_seed_hint),
                    value = params.seed,
                    onValueChange = { viewModel.updateSeed(it) }
                )
            }

            item {
                ParameterIntSlider(
                    label = stringResource(R.string.param_threads),
                    value = params.threads,
                    range = 1..16,
                    enabled = !isLiteRTActive,
                    unsupportedLabel = if (isLiteRTActive) stringResource(R.string.preset_unsupported_litertlm) else null,
                    onValueChange = { viewModel.updateThreads(it) }
                )
            }
        }
    }

    if (uiState.saveDialogVisible) {
        WarpedAlertDialog(
            onDismissRequest = { viewModel.dismissSaveDialog() },
            title = { Text(stringResource(R.string.preset_save_title)) },
            text = {
                OutlinedTextField(
                    value = uiState.presetNameInput,
                    onValueChange = { viewModel.updatePresetName(it) },
                    label = { Text(stringResource(R.string.preset_name_label)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.savePreset() }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSaveDialog() }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (uiState.showFormatWarning && uiState.formatWarningPreset != null) {
        val preset = uiState.formatWarningPreset!!
        val formatLabel = if (preset.modelFormat.equals("LITERTLM", ignoreCase = true)) "LiteRT-LM" else preset.modelFormat
        WarpedAlertDialog(
            onDismissRequest = { viewModel.dismissFormatWarning() },
            title = { Text(stringResource(R.string.preset_format_mismatch)) },
            text = {
                Text(
                    stringResource(R.string.preset_format_msg_fmt, formatLabel)
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmLoadPreset() }) {
                    Text(stringResource(R.string.preset_apply_compatible))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissFormatWarning() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (uiState.error != null) {
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                TextButton(onClick = { viewModel.clearError() }) { Text(stringResource(R.string.dismiss)) }
            }
        ) { Text(uiState.error ?: "") }
    }
}

@Composable
fun ParameterSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    unsupportedLabel: String? = null,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.alpha(if (enabled) 1f else 0.38f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("%.2f".format(value), style = MaterialTheme.typography.bodySmall)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range, enabled = enabled)
        if (unsupportedLabel != null) {
            Text(
                unsupportedLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ParameterIntSlider(
    label: String,
    value: Int,
    range: IntRange,
    steps: Int = 0,
    enabled: Boolean = true,
    unsupportedLabel: String? = null,
    onValueChange: (Int) -> Unit
) {
    Column(modifier = Modifier.alpha(if (enabled) 1f else 0.38f)) {
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
            steps = steps,
            enabled = enabled
        )
        if (unsupportedLabel != null) {
            Text(
                unsupportedLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B29)),
        border = if (isSelected) BorderStroke(1.dp, Color(0xFFD97757)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(preset.name, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(8.dp))
                    FormatBadge(preset.modelFormat)
                }
                Text(
                    "T:${"%.1f".format(preset.temperature)} P:${"%.1f".format(preset.topP)} K:${preset.topK}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onLoad) { Text(stringResource(R.string.preset_load)) }
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text(stringResource(R.string.preset_delete_short)) }
        }
    }
}

@Composable
private fun FormatBadge(format: String) {
    val (color, label) = when {
        format.equals("LITERTLM", ignoreCase = true) ->
            Color(0xFF4CAF50) to "LiteRT-LM"
        else -> MaterialTheme.colorScheme.outline to format
    }
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = color.copy(alpha = 0.15f),
        contentColor = color
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

@Composable
private fun SmartPresetCard(
    presetName: String,
    tier: MemoryTier?,
    availableGb: Float,
    totalGb: Float,
    isActive: Boolean,
    onApply: () -> Unit
) {
    val tierColor = when (tier) {
        MemoryTier.LOW -> Color(0xFFFF9800)
        MemoryTier.MID -> Color(0xFF2196F3)
        MemoryTier.HIGH -> Color(0xFF4CAF50)
        null -> MaterialTheme.colorScheme.primary
    }
    val tierLabel = tier?.let {
        stringResource(
            when (it) {
                MemoryTier.LOW -> R.string.preset_tier_conservative
                MemoryTier.MID -> R.string.preset_tier_balanced
                MemoryTier.HIGH -> R.string.preset_tier_optimal
            }
        )
    } ?: ""

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isActive) CardDefaults.cardColors(containerColor = tierColor.copy(alpha = 0.12f))
            else CardDefaults.cardColors(containerColor = Color(0xFF2B2B29)),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(presetName, style = MaterialTheme.typography.titleSmall)
                    if (tier != null) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = tierColor.copy(alpha = 0.2f)
                        ) {
                            Text(
                                tierLabel,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = tierColor
                            )
                        }
                    }
                    if (isActive) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = Color(0xFF4CAF50).copy(alpha = 0.2f)
                        ) {
                            Text(
                                stringResource(R.string.badge_active),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(
                        R.string.preset_mem_fmt,
                        "%.1f".format(availableGb),
                        "%.1f".format(totalGb)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isActive) {
                TextButton(onClick = onApply) {
                    Text("Apply", color = tierColor)
                }
            }
        }
    }
}
