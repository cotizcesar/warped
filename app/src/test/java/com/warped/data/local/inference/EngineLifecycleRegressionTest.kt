package com.warped.data.local.inference

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Phase 62-01 (LEAK-02 regression): locks the Phase 61 Leg 1 clean result
 * (load/unload with zero retained native handles).
 *
 * Drives the REAL [EngineManager] with a MockK [LiteRTLmEngine] (never the
 * native library): load → unload → reload must release the old handle
 * exactly once and leave no stale [ActiveEngine] behind.
 *
 * Mutation-sanity (by inspection): deleting `liteRTLmEngine.close()` from
 * `unloadCurrent`/`scheduleUnload` fails `unload releases...` /
 * `scheduleUnload releases...`; deleting `activeEngine = null` fails every
 * `isEngineLoaded` assertion below.
 */
class EngineLifecycleRegressionTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var engine: LiteRTLmEngine

    private fun manager(): EngineManager {
        engine = mockk(relaxed = true)
        val detector = mockk<BackendDetector>(relaxed = true)
        every { detector.probeBackend() } returns BackendType.CPU
        return EngineManager(
            liteRTLmEngine = engine,
            backendDetector = detector,
            context = mockk<Context>(relaxed = true),
            cacheManager = mockk(relaxed = true),
            allowlist = mockk<com.warped.data.repository.ModelAllowlistRepository>(relaxed = true),
        )
    }

    private fun modelFile(name: String): File =
        File(tempDir, name).also { it.writeText("fake-weights") }

    @Test
    fun `unload releases native handle exactly once and clears active engine`() {
        val m = manager()
        val file = modelFile("e2b-a.litertlm")
        m.switchToLiteRT(file.absolutePath)
        assertThat(m.isEngineLoaded()).isTrue()

        m.unloadCurrent()

        // 2026-10-04 async-close contract: state flips synchronously,
        // the native close lands shortly after, off-thread.
        assertThat(m.isEngineLoaded()).isFalse()
        assertThat(m.getActiveEngine()).isNull()
        verify(timeout = 5000) { engine.close() }
    }

    @Test
    fun `reload after unload starts clean with no stale state`() {
        val m = manager()
        val first = modelFile("e2b-a.litertlm")
        val second = modelFile("e2b-b.litertlm")
        m.switchToLiteRT(first.absolutePath)
        m.unloadCurrent()
        // Drain the pending async close first: deterministic sequencing
        // (otherwise the close may land after the reload and stand down
        // on seeing the fresh engine — also correct, but uncountable).
        verify(timeout = 5000) { engine.close() }
        m.switchToLiteRT(second.absolutePath)

        // Two inits (one per load), exactly one close (the unload) — the
        // reloaded engine is a fresh handle, never the released one.
        verify(exactly = 2) { engine.init(any(), any(), any(), any(), any()) }
        verify(exactly = 1) { engine.close() }
        assertThat(m.isEngineLoaded()).isTrue()
        assertThat(m.getActiveEngine()?.modelPath).isEqualTo(second.absolutePath)
    }

    @Test
    fun `unload with nothing loaded is a safe no-op`() {
        val m = manager()

        m.unloadCurrent()

        verify(exactly = 0) { engine.close() }
        assertThat(m.isEngineLoaded()).isFalse()
    }

    @Test
    fun `scheduleUnload releases handle and clears state`() {
        val m = manager()
        m.switchToLiteRT(modelFile("e2b-a.litertlm").absolutePath)

        m.scheduleUnload()

        assertThat(m.isEngineLoaded()).isFalse()
        assertThat(m.getActiveEngine()).isNull()
        verify(timeout = 5000) { engine.close() }
    }

    @Test
    fun `switching models unloads the old engine before init`() {
        val m = manager()
        val first = modelFile("e2b-a.litertlm")
        val second = modelFile("e2b-b.litertlm")
        m.switchToLiteRT(first.absolutePath)
        m.switchToLiteRT(second.absolutePath)

        // Old handle released once; both models initialized; active is B.
        verify(exactly = 1) { engine.close() }
        verify(exactly = 2) { engine.init(any(), any(), any(), any(), any()) }
        assertThat(m.getActiveEngine()?.modelPath).isEqualTo(second.absolutePath)
    }

    @Test
    fun `switching to the already-loaded model skips reinit`() {
        val m = manager()
        val file = modelFile("e2b-a.litertlm")
        m.switchToLiteRT(file.absolutePath)
        m.switchToLiteRT(file.absolutePath)

        verify(exactly = 1) { engine.init(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { engine.close() }
    }
}
