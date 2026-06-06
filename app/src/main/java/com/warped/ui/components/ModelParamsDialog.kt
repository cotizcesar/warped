package com.warped.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.warped.domain.model.GenerationParameters

@Composable
fun ModelParamsDialog(
    title: String,
    initial: GenerationParameters,
    onDismiss: () -> Unit,
    onSave: (GenerationParameters) -> Unit
) {
    var params by remember { mutableStateOf(initial) }

    WarpedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ParamSlider(
                    label = "Temperature",
                    value = params.temperature,
                    range = 0f..2f,
                    steps = 19,
                    description = "Controls randomness. Lower = more deterministic.",
                    format = { "%.1f".format(it) }
                ) { params = params.copy(temperature = it) }

                ParamSlider(
                    label = "Top P",
                    value = params.topP,
                    range = 0f..1f,
                    steps = 9,
                    description = "Nucleus sampling. Lower = more focused.",
                    format = { "%.2f".format(it) }
                ) { params = params.copy(topP = it) }

                ParamSlider(
                    label = "Top K",
                    value = params.topK.toFloat(),
                    range = 1f..100f,
                    steps = 9,
                    description = "Limits token selection to top K.",
                    format = { it.toInt().toString() }
                ) { params = params.copy(topK = it.toInt()) }

                ParamSlider(
                    label = "Repeat Penalty",
                    value = params.repeatPenalty,
                    range = 1f..2f,
                    steps = 9,
                    description = "Penalizes token repetition. Higher = less repetition.",
                    format = { "%.2f".format(it) }
                ) { params = params.copy(repeatPenalty = it) }

                ParamSlider(
                    label = "Max Tokens",
                    value = params.maxTokens.toFloat(),
                    range = 128f..8192f,
                    steps = 8,
                    description = "Maximum output tokens per response.",
                    format = { it.toInt().toString() }
                ) { params = params.copy(maxTokens = it.toInt()) }

                ParamSlider(
                    label = "Context Size",
                    value = params.contextSize.toFloat(),
                    range = 512f..32768f,
                    steps = 6,
                    description = "Maximum context window size.",
                    format = { it.toInt().toString() }
                ) { params = params.copy(contextSize = it.toInt()) }

                ParamSlider(
                    label = "Seed",
                    value = params.seed.toFloat(),
                    range = -1f..100000f,
                    steps = 10,
                    description = "Random seed. -1 = random each time.",
                    format = { if (it.toInt() == -1) "Random" else it.toInt().toString() }
                ) { params = params.copy(seed = it.toInt()) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(params) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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
