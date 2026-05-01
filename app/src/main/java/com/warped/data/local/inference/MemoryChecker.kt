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
}
