package com.warped.ui.benchmark.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.warped.R
import com.warped.domain.model.BenchmarkConfig

@Composable
fun BenchmarkConfigCard(
    config: BenchmarkConfig,
    onTemperature: (Float) -> Unit,
    onTopK: (Int) -> Unit,
    onMaxTokens: (Int) -> Unit,
    onTrials: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.bench_config),
                style = MaterialTheme.typography.titleMedium,
            )
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = stringResource(R.string.bench_temp_fmt, "%.2f".format(config.temperature)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = config.temperature,
                    onValueChange = onTemperature,
                    valueRange = 0f..2f,
                )
            }
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = stringResource(R.string.bench_topk_fmt, config.topK),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = config.topK.toFloat(),
                    onValueChange = { onTopK(it.toInt()) },
                    valueRange = 1f..100f,
                )
            }
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = stringResource(R.string.bench_maxtok_fmt, config.maxTokens),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = config.maxTokens.toFloat(),
                    onValueChange = { onMaxTokens(it.toInt()) },
                    valueRange = 64f..2048f,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.bench_trials_fmt, config.trials),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row {
                    IconButton(
                        onClick = { onTrials(config.trials - 1) },
                        enabled = config.trials > 1,
                    ) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.bench_fewer))
                    }
                    IconButton(
                        onClick = { onTrials(config.trials + 1) },
                        enabled = config.trials < 10,
                    ) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.bench_more))
                    }
                }
            }
        }
    }
}
