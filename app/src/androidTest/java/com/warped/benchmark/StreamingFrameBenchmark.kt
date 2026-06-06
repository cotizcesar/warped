package com.warped.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Streaming-frame macrobenchmark.
 *
 * Drives the chat screen with a fixed prompt and measures frame timing
 * while tokens stream. Target: 60fps median (16.67ms / frame) on
 * Pixel 7 / Android 14.
 *
 * Assumes a downloaded model is loaded and the chat screen is reachable.
 */
@RunWith(AndroidJUnit4::class)
class StreamingFrameBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun streamingFrameRate() {
        rule.measureRepeated(
            packageName = "com.warped",
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.DEFAULT,
            iterations = 3,
        ) {
            pressHome()
            startActivityAndWait()
            // Let the activity settle and stream for ~10s.
            device.waitForIdle(10_000)
        }
    }
}
