package com.warped.data.local.benchmark

import android.content.Context
import android.os.SystemClock
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.warped.di.LlmHelperQualifiers
import com.warped.domain.llm.LlmModelHelper
import com.warped.domain.model.BenchmarkConfig
import com.warped.domain.model.BenchmarkResult
import com.warped.domain.model.ChatMessage
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.GenerationParameters
import com.warped.domain.model.Role
import com.warped.domain.model.StreamToken
import com.warped.domain.repository.BenchmarkRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import javax.inject.Named

@HiltWorker
class ModelBenchmarkWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    @Named(LlmHelperQualifiers.LITE_RT_LM) private val helper: LlmModelHelper,
    private val benchmarkRepo: BenchmarkRepository,
) : CoroutineWorker(context, workerParams) {

    private val notifier = BenchmarkNotifier(applicationContext)

    override suspend fun doWork(): Result {
        val modelPath = inputData.getString(KEY_MODEL_PATH) ?: return Result.failure()
        val modelId = inputData.getString(KEY_MODEL_ID) ?: return Result.failure()
        val temperature = inputData.getFloat(KEY_TEMPERATURE, 0.7f)
        val topK = inputData.getInt(KEY_TOP_K, 40)
        val maxTokens = inputData.getInt(KEY_MAX_TOKENS, 512)
        val trials = inputData.getInt(KEY_TRIALS, 3)
        val config = BenchmarkConfig(temperature, topK, maxTokens, trials)
        val configHash = config.hash()

        runCatching { setForeground(notifier.foregroundInfo("Starting benchmark", 0)) }
            .onFailure { Timber.w(it, "setForeground denied; continuing without notification") }

        val prompt = runCatching {
            applicationContext.assets.open("benchmark_prompt.txt").bufferedReader().use { it.readText() }
        }.getOrElse {
            // Fallback prompt if asset missing — still produces a valid measurement
            "The quick brown fox jumps over the lazy dog. ".repeat(64)
        }

        val request = ChatRequest(
            messages = listOf(ChatMessage(role = Role.USER, content = prompt)),
            parameters = GenerationParameters(
                temperature = temperature,
                topK = topK,
                maxTokens = maxTokens,
            ),
        )

        // Measure init
        val initStart = SystemClock.elapsedRealtime()
        val initOk = runCatching { helper.initialize(modelPath) }.isSuccess
        if (!initOk) {
            Timber.e("Benchmark: helper.initialize failed for %s", modelPath)
            return Result.retry()
        }
        val initTimeMs = SystemClock.elapsedRealtime() - initStart

        var prefillSum = 0.0
        var decodeSum = 0.0
        var peakMem = 0L

        val inputTokens = (prompt.length / 4).coerceAtLeast(1)

        for (trial in 1..trials) {
            setForegroundAsync(notifier.foregroundInfo("Trial $trial/$trials", trial * 100 / trials))
            val sampler = MemorySampler.start()
            val prefillStart = SystemClock.elapsedRealtime()
            var firstDeltaAt = -1L
            var tokenCount = 0

            try {
                helper.runInference(request, enableThinking = false).collect { token ->
                    when (token) {
                        is StreamToken.Delta -> {
                            if (firstDeltaAt < 0) firstDeltaAt = SystemClock.elapsedRealtime()
                            tokenCount++
                        }
                        is StreamToken.Done -> Unit
                        is StreamToken.Error -> throw IllegalStateException(token.message)
                        // 47-02: tool status is not text — ignored by benchmarks.
                        is StreamToken.ToolStatus -> Unit
                        // 47-03: remote-loop completion records carry no text.
                        is StreamToken.ToolCompleted -> Unit
                        // Phase 57 UI-review: typed tools-unsupported
                        // notice carries no text — ignored by benchmarks.
                        is StreamToken.ToolsUnsupported -> Unit
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Benchmark: trial %d failed", trial)
                sampler.stop()
                return Result.retry()
            }
            val end = SystemClock.elapsedRealtime()
            sampler.stop()

            val prefillMs = if (firstDeltaAt > 0) firstDeltaAt - prefillStart else (end - prefillStart)
            val decodeMs = if (firstDeltaAt > 0) end - firstDeltaAt else 0L
            val prefillTps = if (prefillMs > 0) inputTokens * 1000.0 / prefillMs else 0.0
            val decodeTps = if (decodeMs > 0) tokenCount * 1000.0 / decodeMs else 0.0
            prefillSum += prefillTps
            decodeSum += decodeTps
            peakMem = maxOf(peakMem, sampler.peakBytes())
        }

        val result = BenchmarkResult(
            modelId = modelId,
            configHash = configHash,
            initTimeMs = initTimeMs,
            prefillTokPerSec = prefillSum / trials,
            decodeTokPerSec = decodeSum / trials,
            peakMemoryBytes = peakMem,
            createdAt = System.currentTimeMillis(),
        )
        benchmarkRepo.insert(result)
        setForegroundAsync(notifier.foregroundInfo("Benchmark complete", 100))
        return Result.success(
            workDataOf(
                "prefillTokPerSec" to result.prefillTokPerSec,
                "decodeTokPerSec" to result.decodeTokPerSec,
            )
        )
    }

    companion object {
        const val UNIQUE_NAME = "model_benchmark"
        const val KEY_MODEL_PATH = "model_path"
        const val KEY_MODEL_ID = "model_id"
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_TOP_K = "top_k"
        const val KEY_MAX_TOKENS = "max_tokens"
        const val KEY_TRIALS = "trials"
    }
}
