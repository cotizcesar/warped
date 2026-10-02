package com.warped.ui.chat.voice

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Phase 67 (VMSG-05): JVM unit test for the pure PCM layer of
 * [PcmTranscoder]. The MediaCodec decode loop is device-smoke covered,
 * not JVM covered (android.jar stubs throw at runtime).
 */
class PcmTranscoderTest {

    @Test
    fun `stereo downmix averages channels`() {
        // L=1000, R=3000 -> mono 2000.
        val stereo = shortArrayOf(1000, 3000, -1000, -3000)
        val mono = PcmTranscoder.resampleTo16kMono(stereo, 16_000, 2)
        assertThat(mono.toList()).containsExactly(2000.toShort(), (-2000).toShort())
    }

    @Test
    fun `mono passthrough at 16k unchanged`() {
        val pcm = shortArrayOf(1, 2, 3, 4, 5)
        assertThat(PcmTranscoder.resampleTo16kMono(pcm, 16_000, 1).toList())
            .containsExactly(1.toShort(), 2.toShort(), 3.toShort(), 4.toShort(), 5.toShort())
    }

    @Test
    fun `resample 44_1k to 16k length within 1 percent`() {
        val srcLen = 44_100
        val pcm = ShortArray(srcLen) { (it % 1000).toShort() }
        val out = PcmTranscoder.resampleTo16kMono(pcm, 44_100, 1)
        assertThat(out.size).isWithin(160).of(16_000)
        assertThat(out.size).isGreaterThan(0)
    }

    @Test
    fun `resample 48k to 16k length within 1 percent`() {
        val pcm = ShortArray(48_000) { (it % 1000).toShort() }
        val out = PcmTranscoder.resampleTo16kMono(pcm, 48_000, 1)
        assertThat(out.size).isWithin(160).of(16_000)
    }

    @Test
    fun `resample preserves ramp endpoints approximately`() {
        // Linear ramp 0..22050 over 1 s at 44.1 kHz (halved to avoid
        // Short overflow): endpoints must map near the start/end of
        // the 16 k output.
        val pcm = ShortArray(44_100) { (it / 2).toShort() }
        val out = PcmTranscoder.resampleTo16kMono(pcm, 44_100, 1)
        assertThat(out.first().toInt()).isWithin(200).of(0)
        assertThat(out.last().toInt()).isWithin(500).of(22_050)
    }

    @Test
    fun `truncate boundary exactly 30s not flagged`() {
        val pcm = ShortArray(PcmTranscoder.MAX_SAMPLES) { 7 }
        val (out, truncated) = PcmTranscoder.truncateTo30s(pcm)
        assertThat(truncated).isFalse()
        assertThat(out.size).isEqualTo(PcmTranscoder.MAX_SAMPLES)
    }

    @Test
    fun `truncate 30s plus one sample flagged and capped`() {
        val pcm = ShortArray(PcmTranscoder.MAX_SAMPLES + 1) { 7 }
        val (out, truncated) = PcmTranscoder.truncateTo30s(pcm)
        assertThat(truncated).isTrue()
        assertThat(out.size).isEqualTo(PcmTranscoder.MAX_SAMPLES)
    }

    @Test
    fun `empty input resamples to empty`() {
        assertThat(PcmTranscoder.resampleTo16kMono(ShortArray(0), 44_100, 2)).isEmpty()
        val (out, truncated) = PcmTranscoder.truncateTo30s(ShortArray(0))
        assertThat(out).isEmpty()
        assertThat(truncated).isFalse()
    }
}
