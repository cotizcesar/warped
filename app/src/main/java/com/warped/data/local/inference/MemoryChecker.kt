package com.warped.data.local.inference

import android.app.ActivityManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class MemoryInfo(
    val availableBytes: Long,
    val totalBytes: Long,
    val usedPercent: Int
)

data class RamCheckResult(
    val hasEnough: Boolean,
    val availableBytes: Long,
    val requiredBytes: Long
)

@Singleton
class MemoryChecker @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun getMemoryInfo(): MemoryInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return MemoryInfo(
            availableBytes = memInfo.availMem,
            totalBytes = memInfo.totalMem,
            usedPercent = ((1.0 - memInfo.availMem.toDouble() / memInfo.totalMem) * 100).toInt()
        )
    }

    /**
     * Whether the model is loadable under current memory pressure.
     *
     * Heuristic, deliberately looser than file-size fitting: LiteRT-LM mmaps
     * weights, so residency is a fraction of the file — the real costs are
     * the resident set plus KV cache and runtime overhead. Estimated need =
     * 60% of file bytes + a fixed 512 MiB KV/overhead reserve, which must
     * fit in 90% of available RAM (10% system breathing room).
     *
     * This still blocks absurd cases (e.g. a 4.9 GB model on a 2 GB-free
     * device) but no longer rejects a 2.5 GB mmap'd model with 2.4 GB
     * available (device evidence 2026-10-01: the old file-size rule
     * blocked loads that succeed in practice). OOM under extreme pressure
     * remains possible — load failures surface as errors, never silent.
     */
    fun canLoadModel(modelSizeBytes: Long): Boolean {
        val avail = getMemoryInfo().availableBytes
        return fitsInMemory(modelSizeBytes, avail)
    }

    fun shouldWarn(modelSizeBytes: Long): Boolean {
        val memInfo = getMemoryInfo()
        return modelSizeBytes > memInfo.availableBytes * 0.6
    }

    companion object {
        private const val RESIDENT_FRACTION = 0.6
        private const val KV_RESERVE_BYTES = 512L * 1024L * 1024L
        private const val HEADROOM_FRACTION = 0.9

        /**
         * Pure core of [canLoadModel] — unit-testable without Android.
         */
        internal fun fitsInMemory(modelSizeBytes: Long, availableBytes: Long): Boolean {
            val estimatedNeed = (modelSizeBytes * RESIDENT_FRACTION).toLong() + KV_RESERVE_BYTES
            return estimatedNeed <= (availableBytes * HEADROOM_FRACTION).toLong()
        }
    }

    /**
     * Check if the device has enough available RAM to load a .litertlm model.
     * Uses an 80% threshold for safe model loading.
     * LiteRT-LM models typically require more RAM headroom for GPU/NPU backend overhead.
     *
     * @param modelSizeBytes The size of the .litertlm model file in bytes
     * @return true if the model can safely be loaded without OOM risk
     */
    fun canLoadLitertlmModel(modelSizeBytes: Long): Boolean {
        return canLoadModel(modelSizeBytes)
    }

    /**
     * Returns a human-readable warning message if the model exceeds 80% of available RAM.
     * Returns null if no warning is needed.
     *
     * @param modelSizeBytes The size of the .litertlm model file in bytes
     * @return Warning string or null
     */
    fun getLitertlmMemoryWarning(modelSizeBytes: Long): String? {
        val memInfo = getMemoryInfo()
        val modelMB = modelSizeBytes / (1024 * 1024)
        val availableMB = memInfo.availableBytes / (1024 * 1024)

        if (modelSizeBytes > memInfo.availableBytes * 0.8) {
            return "Warning: This model (${modelMB} MB) may not fit in available RAM " +
                    "(${availableMB} MB available). The app may crash or the model may " +
                    "fail to load. Consider downloading a smaller quantization."
        }
        if (modelSizeBytes > memInfo.availableBytes * 0.6) {
            return "Note: This model (${modelMB} MB) uses ${((modelSizeBytes.toDouble() / memInfo.availableBytes) * 100).toInt()}% " +
                    "of available RAM (${availableMB} MB). Other apps may be affected."
        }
        return null
    }
}
