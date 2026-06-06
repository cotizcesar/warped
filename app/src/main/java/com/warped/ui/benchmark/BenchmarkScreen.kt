package com.warped.ui.benchmark

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warped.ui.benchmark.components.BenchmarkConfigCard
import com.warped.ui.benchmark.components.BenchmarkResultsViewer
import com.warped.ui.benchmark.components.ModelDropdown

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkScreen(
    viewModel: BenchmarkViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Benchmark") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = "Model",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(4.dp))
            ModelDropdown(
                models = ui.downloadedModels,
                selected = ui.selectedModel,
                onSelect = viewModel::select,
            )
            Spacer(Modifier.height(16.dp))
            BenchmarkConfigCard(
                config = ui.config,
                onTemperature = viewModel::setTemperature,
                onTopK = viewModel::setTopK,
                onMaxTokens = viewModel::setMaxTokens,
                onTrials = viewModel::setTrials,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = viewModel::start,
                enabled = !ui.isRunning && ui.selectedModel != null,
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
                    Text("Start Benchmark")
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Recent results",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(4.dp))
            BenchmarkResultsViewer(
                results = ui.results,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
            )
        }
    }
}
