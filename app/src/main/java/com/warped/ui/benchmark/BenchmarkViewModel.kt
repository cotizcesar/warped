package com.warped.ui.benchmark

import android.os.Build
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
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

    init {
        // Phase 60-02 (API-04): ModelBenchmarkWorker cannot read its own
        // stop reason, so it is logged here UI-side off the unique-work
        // flow. Each stopped work is logged once (platform int only —
        // never model paths, tokens, or headers per T-60-03).
        viewModelScope.launch {
            val loggedStops = mutableSetOf<UUID>()
            workInfosFlow.collect { infos ->
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@collect
                infos
                    .filter { it.state == WorkInfo.State.CANCELLED || it.state == WorkInfo.State.FAILED }
                    .filter { it.id !in loggedStops && it.stopReason != WorkInfo.STOP_REASON_NOT_STOPPED }
                    .forEach { info ->
                        loggedStops += info.id
                        Timber.d("Benchmark: work stopped — stopReason=%d", info.stopReason)
                    }
            }
        }
    }

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
