package com.warped.data.local.benchmark

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.warped.domain.model.BenchmarkConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BenchmarkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun enqueue(modelPath: String, modelId: String, config: BenchmarkConfig) {
        val request = OneTimeWorkRequestBuilder<ModelBenchmarkWorker>()
            .setInputData(
                workDataOf(
                    ModelBenchmarkWorker.KEY_MODEL_PATH to modelPath,
                    ModelBenchmarkWorker.KEY_MODEL_ID to modelId,
                    ModelBenchmarkWorker.KEY_TEMPERATURE to config.temperature,
                    ModelBenchmarkWorker.KEY_TOP_K to config.topK,
                    ModelBenchmarkWorker.KEY_MAX_TOKENS to config.maxTokens,
                    ModelBenchmarkWorker.KEY_TRIALS to config.trials,
                )
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ModelBenchmarkWorker.UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
