package com.warped.data.local.inference

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.jupiter.api.Test

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
        cacheManager = mockk(relaxed = true)
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
}
