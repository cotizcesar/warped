package com.warped.ui.benchmark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.warped.data.local.benchmark.BenchmarkScheduler
import com.warped.data.local.benchmark.ModelBenchmarkWorker
import com.warped.domain.model.BenchmarkConfig
import com.warped.domain.model.LocalModel
import com.warped.domain.repository.BenchmarkRepository
import com.warped.domain.repository.LocalModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class BenchmarkViewModel @Inject constructor(
    private val localModelRepo: LocalModelRepository,
    private val benchmarkRepo: BenchmarkRepository,
    private val scheduler: BenchmarkScheduler,
    workManager: WorkManager,
) : ViewModel() {

    private val _config = MutableStateFlow(BenchmarkConfig())
    private val _selected = MutableStateFlow<LocalModel?>(null)

    private val workInfosFlow = workManager
        .getWorkInfosForUniqueWorkFlow(ModelBenchmarkWorker.UNIQUE_NAME)

    val uiState: StateFlow<BenchmarkUiState> = combine(
        localModelRepo.observeModels(),
        _selected,
        _config,
        benchmarkRepo.observeAll(),
        workInfosFlow,
    ) { models, selected, config, results, workInfos ->
        val running = workInfos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
        BenchmarkUiState(
            downloadedModels = models,
            selectedModel = selected ?: models.firstOrNull(),
            config = config,
            results = results,
            isRunning = running,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BenchmarkUiState())

    fun select(m: LocalModel) {
        _selected.value = m
    }

    fun setTemperature(v: Float) {
        _config.update { it.copy(temperature = v) }
    }

    fun setTopK(v: Int) {
        _config.update { it.copy(topK = v) }
    }

    fun setMaxTokens(v: Int) {
        _config.update { it.copy(maxTokens = v) }
    }

    fun setTrials(v: Int) {
        _config.update { it.copy(trials = v.coerceIn(1, 10)) }
    }

    fun start() {
        val m = _selected.value ?: return
        scheduler.enqueue(m.filePath, m.name, _config.value)
    }
}
