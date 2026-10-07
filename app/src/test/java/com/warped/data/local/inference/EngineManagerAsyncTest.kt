package com.warped.data.local.inference

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.warped.data.repository.ModelAllowlistRepository
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * 2026-10-04 async-load fix: the old method-level `@Synchronized`
 * held one monitor across the whole multi-second native init/close,
 * so every Main-thread `getActiveEngine()` parked until the mount
 * finished — the full UI freeze on model load. Locking is now split
 * (field-only guard + background sequence lock): reads never block,
 * unloads flip state synchronously and close async.
 */
class EngineManagerAsyncTest {

    @TempDir
    lateinit var tempDir: File

    private fun manager(
        engine: LiteRTLmEngine = mockk(relaxed = true),
    ): EngineManager {
        val backendDetector = mockk<BackendDetector>()
        every { backendDetector.probeBackend() } returns BackendType.CPU
        val cacheManager = mockk<LiteRtLmCacheManager>()
        every { cacheManager.cacheRoot } returns tempDir
        return EngineManager(
            liteRTLmEngine = engine,
            backendDetector = backendDetector,
            context = mockk<Context>(relaxed = true),
            cacheManager = cacheManager,
            allowlist = mockk(relaxed = true),
        )
    }

    private fun modelFile(): String =
        File(tempDir, "m.litertlm").apply { writeText("fake") }.absolutePath

    @Test
    fun `reads never block while a mount holds the sequence lock`() {
        val enteredInit = CountDownLatch(1)
        val releaseInit = CountDownLatch(1)
        val engine = mockk<LiteRTLmEngine>()
        every { engine.init(any(), any(), any(), any(), any()) } answers {
            enteredInit.countDown()
            assertThat(releaseInit.await(10, TimeUnit.SECONDS)).isTrue()
        }
        val manager = manager(engine)
        val path = modelFile()

        val mount = thread(name = "mount") { manager.switchToLiteRT(path) }
        assertThat(enteredInit.await(10, TimeUnit.SECONDS)).isTrue()

        // The old coarse monitor parked here until releaseInit — the UI
        // freeze. Now the field read returns in nanoseconds (null: the
        // mount has not published yet).
        val startNs = System.nanoTime()
        val seen = manager.getActiveEngine()
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000

        assertThat(seen).isNull()
        assertThat(elapsedMs).isLessThan(2000)

        releaseInit.countDown()
        mount.join(15_000)
        assertThat(manager.getActiveEngine()?.modelPath).isEqualTo(path)
    }

    @Test
    fun `unload flips state synchronously and closes async`() {
        val engine = mockk<LiteRTLmEngine>()
        every { engine.init(any(), any(), any(), any(), any()) } just Runs
        val manager = manager(engine)
        val path = modelFile()
        manager.switchToLiteRT(path)
        assertThat(manager.getActiveEngine()?.modelPath).isEqualTo(path)

        manager.scheduleUnload()

        // Callers (Main included) observe the unload immediately…
        assertThat(manager.getActiveEngine()).isNull()
        // …while the native close lands shortly after, off-thread.
        verify(timeout = 5000) { engine.close() }
    }

    @Test
    fun `unload during an in-flight switch never kills the fresh engine`() {
        val enteredInit = CountDownLatch(1)
        val releaseInit = CountDownLatch(1)
        val engine = mockk<LiteRTLmEngine>()
        every { engine.init(any(), any(), any(), any(), any()) } answers {
            enteredInit.countDown()
            assertThat(releaseInit.await(10, TimeUnit.SECONDS)).isTrue()
        }
        val manager = manager(engine)
        val path = modelFile()
        val mount = thread(name = "mount") { manager.switchToLiteRT(path) }
        assertThat(enteredInit.await(10, TimeUnit.SECONDS)).isTrue()

        // Unload requested mid-mount: the grab finds the field already
        // cleared by the switch sequence, so no close is scheduled for
        // the engine being born — exactly one init, zero closes.
        manager.scheduleUnload()
        releaseInit.countDown()
        mount.join(15_000)

        assertThat(manager.getActiveEngine()?.modelPath).isEqualTo(path)
        verify(exactly = 0) { engine.close() }
    }
}
