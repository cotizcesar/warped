package com.warped.domain.model

import com.google.common.truth.Truth.assertThat
import com.warped.data.local.inference.MemoryInfo
import org.junit.jupiter.api.Test

/**
 * Per-model auto-tune: small models get strict sampling (precision),
 * big models stay expressive. Memory tiers still own efficiency
 * (context/threads/tokens).
 */
class SmartPresetSamplingTest {

    private fun memInfo() = MemoryInfo(
        availableBytes = 8L * 1024L * 1024L * 1024L,
        totalBytes = 12L * 1024L * 1024L * 1024L,
        usedPercent = 33
    )

    private fun paramsFor(sizeBytes: Long) =
        SmartPresetCalculator.calculate(memInfo(), sizeBytes).parameters

    @Test
    fun `tiny model gets strict sampling`() {
        val p = paramsFor(300L * 1024L * 1024L)

        assertThat(p.temperature).isEqualTo(0.3f)
        assertThat(p.topK).isEqualTo(15)
        assertThat(p.topP).isEqualTo(0.85f)
        assertThat(p.repeatPenalty).isEqualTo(1.15f)
    }

    @Test
    fun `small model gets focused sampling`() {
        val p = paramsFor(1500L * 1024L * 1024L)

        assertThat(p.temperature).isEqualTo(0.5f)
        assertThat(p.topK).isEqualTo(25)
        assertThat(p.topP).isEqualTo(0.9f)
        assertThat(p.repeatPenalty).isEqualTo(1.12f)
    }

    @Test
    fun `mid model stays near defaults`() {
        val p = paramsFor(4L * 1024L * 1024L * 1024L)

        assertThat(p.temperature).isEqualTo(0.7f)
        assertThat(p.topK).isEqualTo(40)
        assertThat(p.topP).isEqualTo(0.95f)
        assertThat(p.repeatPenalty).isEqualTo(1.08f)
    }

    @Test
    fun `large model stays expressive`() {
        val p = paramsFor(9L * 1024L * 1024L * 1024L)

        assertThat(p.temperature).isEqualTo(0.8f)
        assertThat(p.topK).isEqualTo(40)
        assertThat(p.topP).isEqualTo(0.95f)
        assertThat(p.repeatPenalty).isEqualTo(1.05f)
    }

    @Test
    fun `memory tiers still own efficiency knobs`() {
        val p = paramsFor(300L * 1024L * 1024L)

        // HIGH device tier (8GB free): full context/threads.
        assertThat(p.contextSize).isEqualTo(8192)
        assertThat(p.threads).isEqualTo(6)
    }

    @Test
    fun `precision hint only for small models`() {
        assertThat(SmartPresetCalculator.precisionHintFor(300L * 1024L * 1024L))
            .contains("briefly")
        // 2026-10-04: bare "briefly" elicited one-word answers ("Bien")
        // on 1B models — the tiny hint now demands complete sentences
        // with correct grammar in the user's language only.
        assertThat(SmartPresetCalculator.precisionHintFor(300L * 1024L * 1024L))
            .contains("complete sentences")
        assertThat(SmartPresetCalculator.precisionHintFor(300L * 1024L * 1024L))
            .contains("user's language only")
        assertThat(SmartPresetCalculator.precisionHintFor(1500L * 1024L * 1024L))
            .contains("concise")
        assertThat(SmartPresetCalculator.precisionHintFor(1500L * 1024L * 1024L))
            .contains("user's language")
        assertThat(SmartPresetCalculator.precisionHintFor(4L * 1024L * 1024L * 1024L))
            .isNull()
        assertThat(SmartPresetCalculator.precisionHintFor(9L * 1024L * 1024L * 1024L))
            .isNull()
    }
}
