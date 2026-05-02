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

@Singleton
class MemoryChecker @Inject constructor(
    @ApplicationContext private val context: Context
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

    fun canLoadModel(modelSizeBytes: Long): Boolean {
        val memInfo = getMemoryInfo()
        return modelSizeBytes <= memInfo.availableBytes * 0.8
    }

    fun shouldWarn(modelSizeBytes: Long): Boolean {
        val memInfo = getMemoryInfo()
        return modelSizeBytes > memInfo.availableBytes * 0.6
    }

    /**
     * Check if the device has enough available RAM to load a .litertlm model.
     * Uses the same 80% threshold as GGUF models (canLoadModel).
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
