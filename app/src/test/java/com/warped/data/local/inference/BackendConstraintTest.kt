package com.warped.data.local.inference

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.warped.data.repository.AllowlistCapabilities
import com.warped.data.repository.AllowlistedModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * GPU-constrained models (e.g. gemma-4-12B-it: "requires one of [gpu]") fail on
 * the probed CPU backend. EngineManager retries once with the required backend;
 * these JVM tests pin the message parser (pure function, no native engine).
 */
class BackendConstraintTest {

    private val manager = EngineManager(
        liteRTLmEngine = mockk(relaxed = true),
        backendDetector = mockk(relaxed = true),
        context = mockk<Context>(relaxed = true),
        cacheManager = mockk(relaxed = true),
        allowlist = mockk(relaxed = true)
    )

    @Test
    fun `parses gpu requirement from INVALID_ARGUMENT message`() {
        assertThat(
            manager.parseRequiredBackend(
                "Failed to create engine: INVALID_ARGUMENT: Main backend constraint mismatch. " +
                    "Model requires one of [gpu] but Main backend is CPU"
            )
        ).isEqualTo(BackendType.GPU)
    }

    @Test
    fun `parses first of multiple candidates`() {
        assertThat(manager.parseRequiredBackend("Model requires one of [npu, cpu]"))
            .isEqualTo(BackendType.NPU)
    }

    @Test
    fun `returns null without constraint`() {
        assertThat(manager.parseRequiredBackend("Failed to create engine: OOM")).isNull()
        assertThat(manager.parseRequiredBackend(null)).isNull()
        assertThat(manager.parseRequiredBackend("Model requires one of [tpu]")).isNull()
    }

    // --- Slot parser truth table (quick plan 2026-09-28 vision-backend GPU fix) ---

    @Test
    fun `slot parser maps vision prefix to VISION`() {
        assertThat(
            manager.parseConstraintSlot(
                "Failed to create engine: INVALID_ARGUMENT: Vision backend constraint mismatch. " +
                    "Model requires one of [gpu] but Vision backend is CPU"
            )
        ).isEqualTo(BackendSlot.VISION)
    }

    @Test
    fun `slot parser maps audio prefix to AUDIO`() {
        assertThat(
            manager.parseConstraintSlot(
                "Failed to create engine: INVALID_ARGUMENT: Audio backend constraint mismatch. " +
                    "Model requires one of [cpu] but Audio backend is GPU"
            )
        ).isEqualTo(BackendSlot.AUDIO)
    }

    @Test
    fun `slot parser maps main prefix to MAIN`() {
        assertThat(
            manager.parseConstraintSlot(
                "Failed to create engine: INVALID_ARGUMENT: Main backend constraint mismatch. " +
                    "Model requires one of [gpu] but Main backend is CPU"
            )
        ).isEqualTo(BackendSlot.MAIN)
    }

    @Test
    fun `slot parser falls back to MAIN without slot prefix`() {
        assertThat(manager.parseConstraintSlot("Model requires one of [gpu]"))
            .isEqualTo(BackendSlot.MAIN)
    }

    @Test
    fun `slot parser returns null without constraint`() {
        assertThat(manager.parseConstraintSlot("Failed to create engine: OOM")).isNull()
        assertThat(manager.parseConstraintSlot(null)).isNull()
    }

    // --- initWith backend-resolution (quick plan 2026-09-28 vision-backend GPU fix) ---

    @TempDir
    lateinit var tempDir: File

    private fun visionManager(visionCapable: Boolean, audioCapable: Boolean = false): EngineManager {
        val engine = mockk<LiteRTLmEngine>(relaxed = true)
        val detector = mockk<BackendDetector>(relaxed = true)
        val allowlist = mockk<com.warped.data.repository.ModelAllowlistRepository>(relaxed = true)
        every { detector.probeBackend() } returns BackendType.CPU
        every { detector.probeVisionBackend() } returns BackendType.GPU
        every { detector.probeAudioBackend() } returns BackendType.CPU
        val fileName = if (visionCapable) "gemma-4-E2B-it.litertlm" else "gemma-4-12B-it.litertlm"
        every { allowlist.findByModelFile(fileName) } returns AllowlistedModel(
            name = "test-model",
            displayName = "Test Model",
            modelFile = fileName,
            sizeInBytes = 10,
            capabilities = AllowlistCapabilities(vision = visionCapable, audio = audioCapable)
        )
        return EngineManager(
            liteRTLmEngine = engine,
            backendDetector = detector,
            context = mockk(relaxed = true),
            cacheManager = mockk(relaxed = true),
            allowlist = allowlist
        )
    }

    private fun engineOf(m: EngineManager): LiteRTLmEngine {
        val field = EngineManager::class.java.getDeclaredField("liteRTLmEngine")
        field.isAccessible = true
        return field.get(m) as LiteRTLmEngine
    }

    @Test
    fun `initWith probes GPU vision for vision-capable allowlist models`() {
        val m = visionManager(visionCapable = true)
        val file = File(tempDir, "gemma-4-E2B-it.litertlm").also { it.writeText("weights") }
        m.switchToLiteRT(file.absolutePath)
        verify {
            engineOf(m).init(
                modelPath = file.absolutePath,
                backend = BackendType.CPU,
                visionBackend = BackendType.GPU,
                // Audio not flagged on this entry: slot stays unconfigured so
                // models without TF_LITE_AUDIO_ENCODER_HW (e.g. gemma-3-270m-it)
                // don't fail conversation creation with NOT_FOUND.
                audioBackend = null,
                enableSpeculativeDecoding = false
            )
        }
    }

    @Test
    fun `initWith keeps CPU vision for non-vision models`() {
        val m = visionManager(visionCapable = false)
        val file = File(tempDir, "gemma-4-12B-it.litertlm").also { it.writeText("weights") }
        m.switchToLiteRT(file.absolutePath)
        verify {
            engineOf(m).init(
                modelPath = file.absolutePath,
                backend = BackendType.CPU,
                visionBackend = BackendType.CPU,
                audioBackend = null,
                enableSpeculativeDecoding = false
            )
        }
    }

    @Test
    fun `initWith probes audio backend for audio-capable allowlist models`() {
        val m = visionManager(visionCapable = false, audioCapable = true)
        val file = File(tempDir, "gemma-4-12B-it.litertlm").also { it.writeText("weights") }
        m.switchToLiteRT(file.absolutePath)
        verify {
            engineOf(m).init(
                modelPath = file.absolutePath,
                backend = BackendType.CPU,
                visionBackend = BackendType.CPU,
                audioBackend = BackendType.CPU,
                enableSpeculativeDecoding = false
            )
        }
    }
}
