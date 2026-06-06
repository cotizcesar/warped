package com.warped.ui.benchmark

import com.warped.domain.model.BenchmarkConfig
import com.warped.domain.model.BenchmarkResult
import com.warped.domain.model.LocalModel

data class BenchmarkUiState(
    val downloadedModels: List<LocalModel> = emptyList(),
    val selectedModel: LocalModel? = null,
    val config: BenchmarkConfig = BenchmarkConfig(),
    val results: List<BenchmarkResult> = emptyList(),
    val isRunning: Boolean = false,
)

sealed class BenchmarkError {
    data object NoDownloadedModels : BenchmarkError()
    data class WorkerFailed(val message: String) : BenchmarkError()
}
