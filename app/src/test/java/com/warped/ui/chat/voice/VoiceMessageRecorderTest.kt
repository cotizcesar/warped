package com.warped.ui.chat.voice

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Phase 67 (VMSG-01): JVM unit test for [VoiceMessageRecorder] using a
 * fake [RecorderFactory] (MediaRecorder is not JVM-mockable).
 */
class VoiceMessageRecorderTest {

    @TempDir
    lateinit var tempDir: File

    private class FakeHandle(var failPrepare: Boolean = false) : RecorderHandle {
        var started = false
        var stopped = false
        var released = false
        var outputPath: String? = null
        var throwOnStop = false

        override fun setAudioSource(source: Int) = Unit
        override fun setOutputFormat(format: Int) = Unit
        override fun setAudioEncoder(encoder: Int) = Unit
        override fun setAudioEncodingBitRate(bitRate: Int) = Unit
        override fun setAudioSamplingRate(rate: Int) = Unit
        override fun setOutputFile(path: String) { outputPath = path }
        override fun prepare() {
            if (failPrepare) throw java.io.IOException("fake prepare failure")
        }
        override fun start() { started = true }
        override fun stop() {
            if (throwOnStop) throw RuntimeException("fake stop failure")
            stopped = true
        }
        override fun getMaxAmplitude(): Int = 1234
        override fun release() { released = true }
    }

    private class FakeFactory(val handle: FakeHandle = FakeHandle()) : RecorderFactory {
        var created = 0
        override fun create(): RecorderHandle {
            created++
            return handle
        }
    }

    private fun fakeContext(): Context =
        mockk<Context>().also { every { it.filesDir } returns tempDir }

    @Test
    fun `failed start returns false and never sets isRecording`() {
        val factory = FakeFactory(FakeHandle(failPrepare = true))
        val rec = VoiceMessageRecorder(fakeContext(), File(tempDir, "voice"), factory)
        assertThat(rec.start()).isFalse()
        assertThat(rec.isRecording).isFalse()
    }

    @Test
    fun `stop after failed start returns null`() {
        val factory = FakeFactory(FakeHandle(failPrepare = true))
        val rec = VoiceMessageRecorder(fakeContext(), File(tempDir, "voice"), factory)
        rec.start()
        assertThat(rec.stop()).isNull()
    }

    @Test
    fun `stop without start returns null`() {
        val factory = FakeFactory()
        val rec = VoiceMessageRecorder(fakeContext(), File(tempDir, "voice"), factory)
        assertThat(rec.stop()).isNull()
        assertThat(factory.created).isEqualTo(0)
    }

    @Test
    fun `cancel deletes the file`() {
        val factory = FakeFactory()
        val outDir = File(tempDir, "voice")
        val rec = VoiceMessageRecorder(fakeContext(), outDir, factory)
        assertThat(rec.start()).isTrue()
        // Simulate the platform having written the file (the fake
        // handle records the path but creates nothing on JVM).
        val produced = File(requireNotNull(factory.handle.outputPath))
        produced.parentFile?.mkdirs()
        produced.writeText("fake-audio")
        assertThat(produced.exists()).isTrue()
        rec.cancel()
        assertThat(produced.exists()).isFalse()
        assertThat(rec.isRecording).isFalse()
    }

    @Test
    fun `destroy is idempotent`() {
        val factory = FakeFactory()
        val rec = VoiceMessageRecorder(fakeContext(), File(tempDir, "voice"), factory)
        rec.start()
        rec.destroy()
        rec.destroy()
        assertThat(rec.isRecording).isFalse()
    }

    @Test
    fun `double start while recording is ignored`() {
        val factory = FakeFactory()
        val rec = VoiceMessageRecorder(fakeContext(), File(tempDir, "voice"), factory)
        assertThat(rec.start()).isTrue()
        assertThat(rec.start()).isTrue()
        assertThat(factory.created).isEqualTo(1)
        rec.destroy()
    }

    @Test
    fun `stop failure returns null and clears recording flag`() {
        val handle = FakeHandle().also { it.throwOnStop = true }
        val factory = FakeFactory(handle)
        val outDir = File(tempDir, "voice")
        val rec = VoiceMessageRecorder(fakeContext(), outDir, factory)
        assertThat(rec.start()).isTrue()
        val produced = File(requireNotNull(factory.handle.outputPath))
        produced.parentFile?.mkdirs()
        produced.writeText("fake-audio")
        assertThat(rec.stop()).isNull()
        assertThat(rec.isRecording).isFalse()
        assertThat(handle.released).isTrue()
    }
}
