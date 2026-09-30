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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.warped.R
import com.warped.domain.model.GenerationParameters

@Composable
fun ModelParamsDialog(
    title: String,
    initial: GenerationParameters,
    onDismiss: () -> Unit,
    onSave: (GenerationParameters) -> Unit
) {
    var params by remember { mutableStateOf(initial) }
    val context = LocalContext.current

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
                    label = stringResource(R.string.param_temperature),
                    value = params.temperature,
                    range = 0f..2f,
                    steps = 19,
                    description = stringResource(R.string.param_desc_temperature),
                    format = { "%.1f".format(it) }
                ) { params = params.copy(temperature = it) }

                ParamSlider(
                    label = stringResource(R.string.param_top_p),
                    value = params.topP,
                    range = 0f..1f,
                    steps = 9,
                    description = stringResource(R.string.param_desc_top_p),
                    format = { "%.2f".format(it) }
                ) { params = params.copy(topP = it) }

                ParamSlider(
                    label = stringResource(R.string.param_top_k),
                    value = params.topK.toFloat(),
                    range = 1f..100f,
                    steps = 9,
                    description = stringResource(R.string.param_desc_top_k),
                    format = { it.toInt().toString() }
                ) { params = params.copy(topK = it.toInt()) }

                ParamSlider(
                    label = stringResource(R.string.param_repeat_penalty),
                    value = params.repeatPenalty,
                    range = 1f..2f,
                    steps = 9,
                    description = stringResource(R.string.param_desc_repeat),
                    format = { "%.2f".format(it) }
                ) { params = params.copy(repeatPenalty = it) }

                ParamSlider(
                    label = stringResource(R.string.param_max_tokens),
                    value = params.maxTokens.toFloat(),
                    range = 128f..8192f,
                    steps = 8,
                    description = stringResource(R.string.param_desc_maxtokens),
                    format = { it.toInt().toString() }
                ) { params = params.copy(maxTokens = it.toInt()) }

                ParamSlider(
                    label = stringResource(R.string.param_context_size),
                    value = params.contextSize.toFloat(),
                    range = 512f..32768f,
                    steps = 6,
                    description = stringResource(R.string.param_desc_context),
                    format = { it.toInt().toString() }
                ) { params = params.copy(contextSize = it.toInt()) }

                ParamSlider(
                    label = stringResource(R.string.param_seed),
                    value = params.seed.toFloat(),
                    range = -1f..100000f,
                    steps = 10,
                    description = stringResource(R.string.param_desc_seed),
                    format = { if (it.toInt() == -1) context.getString(R.string.param_random) else it.toInt().toString() }
                ) { params = params.copy(seed = it.toInt()) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(params) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
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
