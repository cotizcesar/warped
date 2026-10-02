package com.warped.ui.chat.voice

import android.content.Context
import android.media.AudioManager
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Phase 68 (VMSG-02): [VoiceMessagePlayer] state-machine contract with a
 * fake [PlayerFactory] (platform MediaPlayer is not JVM-mockable).
 *
 * - play stops a previous clip first (single-player discipline).
 * - pause when idle is a no-op; stop releases and clears isPlaying.
 * - destroy is idempotent; completion callback fires the VM hook.
 * - double-play of the same path restarts (stop-then-start).
 * - audio-focus LOSS pauses playback.
 */
class VoiceMessagePlayerTest {

    private class FakeHandle : PlayerHandle {
        var capturedSource: String? = null
        var prepared = false
        var starts = 0
        var pauses = 0
        var stops = 0
        var releases = 0
        var playing = false
        var position = 0
        var fakeDuration = 5_000
        var completion: (() -> Unit)? = null
        var error: (() -> Boolean)? = null
        var failOnStart = false

        override fun setDataSource(path: String) { capturedSource = path }
        override fun prepare() { prepared = true }
        override fun start() {
            if (failOnStart) throw RuntimeException("boom")
            starts++
            playing = true
        }
        override fun pause() {
            pauses++
            playing = false
        }
        override fun stop() {
            stops++
            playing = false
        }
        override fun release() { releases++ }
        override fun seekTo(ms: Int) { position = ms }
        override fun getCurrentPosition(): Int = position
        override fun getDuration(): Int = fakeDuration
        override fun isPlaying(): Boolean = playing
        override fun setOnCompletionListener(listener: (() -> Unit)?) { completion = listener }
        override fun setOnErrorListener(listener: (() -> Boolean)?) { error = listener }

        fun fireCompletion() = completion?.invoke()
    }

    private lateinit var context: Context
    private lateinit var audioManager: AudioManager
    private lateinit var handles: MutableList<FakeHandle>

    private val factory = object : PlayerFactory {
        override fun create(): PlayerHandle =
            FakeHandle().also { handles.add(it) }
    }

    @BeforeEach
    fun setUp() {
        handles = mutableListOf()
        audioManager = mockk(relaxed = true)
        every {
            audioManager.requestAudioFocus(
                any(),
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
            )
        } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        context = mockk()
        every { context.getSystemService(Context.AUDIO_SERVICE) } returns audioManager
    }

    private fun player() = VoiceMessagePlayer(context, factory)

    @Test
    fun `play starts the clip and reports playing`() {
        val player = player()

        assertThat(player.play("/voice/a.m4a")).isTrue()
        assertThat(player.isPlaying).isTrue()
        assertThat(player.hasClip).isTrue()

        val handle = handles.single()
        assertThat(handle.capturedSource).isEqualTo("/voice/a.m4a")
        assertThat(handle.prepared).isTrue()
        assertThat(handle.starts).isEqualTo(1)
    }

    @Test
    fun `play stops a previous clip first - single-player`() {
        val player = player()

        assertThat(player.play("/voice/a.m4a")).isTrue()
        assertThat(player.play("/voice/b.m4a")).isTrue()

        val first = handles[0]
        assertThat(first.stops).isEqualTo(1)
        assertThat(first.releases).isEqualTo(1)
        assertThat(handles[1].starts).isEqualTo(1)
        assertThat(player.isPlaying).isTrue()
    }

    @Test
    fun `double-play of the same path restarts stop-then-start`() {
        val player = player()

        assertThat(player.play("/voice/a.m4a")).isTrue()
        assertThat(player.play("/voice/a.m4a")).isTrue()

        assertThat(handles).hasSize(2)
        assertThat(handles[0].stops).isEqualTo(1)
        assertThat(handles[1].starts).isEqualTo(1)
        assertThat(player.isPlaying).isTrue()
    }

    @Test
    fun `pause when idle is a no-op`() {
        val player = player()

        player.pause()

        assertThat(player.isPlaying).isFalse()
        assertThat(handles).isEmpty()
    }

    @Test
    fun `pause keeps the handle for resume`() {
        val player = player()
        assertThat(player.play("/voice/a.m4a")).isTrue()
        handles.single().position = 1_500

        player.pause()

        assertThat(player.isPlaying).isFalse()
        assertThat(player.hasClip).isTrue()
        assertThat(player.positionMs()).isEqualTo(1_500)

        assertThat(player.resume()).isTrue()
        assertThat(player.isPlaying).isTrue()
    }

    @Test
    fun `resume without a clip is a no-op`() {
        val player = player()

        assertThat(player.resume()).isFalse()
        assertThat(player.isPlaying).isFalse()
    }

    @Test
    fun `stop releases and clears isPlaying`() {
        val player = player()
        assertThat(player.play("/voice/a.m4a")).isTrue()

        player.stop()

        val handle = handles.single()
        assertThat(handle.stops).isEqualTo(1)
        assertThat(handle.releases).isEqualTo(1)
        assertThat(player.isPlaying).isFalse()
        assertThat(player.hasClip).isFalse()
        verify { audioManager.abandonAudioFocus(any()) }
    }

    @Test
    fun `play failure returns false and leaves no clip`() {
        val failing = object : PlayerFactory {
            override fun create(): PlayerHandle =
                FakeHandle().also { it.failOnStart = true; handles.add(it) }
        }
        val player = VoiceMessagePlayer(context, failing)

        assertThat(player.play("/voice/a.m4a")).isFalse()
        assertThat(player.isPlaying).isFalse()
        assertThat(player.hasClip).isFalse()
    }

    @Test
    fun `destroy is idempotent`() {
        val player = player()
        assertThat(player.play("/voice/a.m4a")).isTrue()

        player.destroy()
        player.destroy()

        assertThat(player.isPlaying).isFalse()
        assertThat(player.hasClip).isFalse()
        // One handle released exactly once despite the double destroy.
        assertThat(handles.single().releases).isEqualTo(1)
        verify(atLeast = 1) { audioManager.abandonAudioFocus(any()) }
    }

    @Test
    fun `completion callback fires the VM hook and clears playing`() {
        val player = player()
        var completed = 0
        player.onCompletion = { completed++ }
        assertThat(player.play("/voice/a.m4a")).isTrue()

        handles.single().fireCompletion()

        assertThat(completed).isEqualTo(1)
        assertThat(player.isPlaying).isFalse()
    }

    @Test
    fun `audio-focus loss pauses playback`() {
        val listenerSlot = slot<AudioManager.OnAudioFocusChangeListener>()
        every {
            audioManager.requestAudioFocus(
                capture(listenerSlot),
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
            )
        } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        val player = player()
        assertThat(player.play("/voice/a.m4a")).isTrue()

        listenerSlot.captured.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)

        assertThat(player.isPlaying).isFalse()
        assertThat(handles.single().pauses).isEqualTo(1)
    }

    @Test
    fun `position and duration default to zero when idle`() {
        val player = player()

        assertThat(player.positionMs()).isEqualTo(0)
        assertThat(player.durationMs()).isEqualTo(0)
    }
}
