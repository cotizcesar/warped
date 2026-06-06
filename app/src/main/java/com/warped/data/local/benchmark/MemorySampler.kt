package com.warped.data.local.benchmark

import android.content.Context
import android.os.Debug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MemorySampler private constructor() {
    @Volatile
    private var peak: Long = 0L
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun stop() {
        job?.cancel()
        scope.cancel()
    }

    fun peakBytes(): Long = peak

    companion object {
        fun start(): MemorySampler {
            val s = MemorySampler()
            s.job = s.scope.launch {
                while (isActive) {
                    val rt = Runtime.getRuntime()
                    val jvm = rt.totalMemory() - rt.freeMemory()
                    val native = Debug.getNativeHeapAllocatedSize()
                    val sample = jvm + native
                    if (sample > s.peak) s.peak = sample
                    delay(100)
                }
            }
            return s
        }
    }
}
