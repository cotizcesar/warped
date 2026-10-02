package com.warped.ui.chat.voice

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import timber.log.Timber

/**
 * Phase 68 (VMSG-02): thin platform MediaPlayer wrapper for voice-draft
 * preview and history playback. VM-owned sibling to [VoiceMessageRecorder];
 * created lazily by ChatViewModel, destroyed in onCleared.
 *
 * Contract (mirrors the recorder ownership discipline):
 * - Coroutine-free: no scopes inside. The ViewModel drives progress
 *   polling (~250 ms on Dispatchers.Default) and calls play/pause/stop
 *   off the Main thread (setDataSource + prepare on Dispatchers.IO).
 * - play() stops/releases any current clip first (single-player
 *   discipline — starting one clip stops any other, draft or history).
 * - pause() keeps the handle so resume() continues from the same
 *   position; stop() releases the handle entirely.
 * - Audio focus is advisory only: transient gain is requested before
 *   start, LOSS/TRANSIENT loss pauses, focus is abandoned on stop/destroy.
 *   A focus failure never blocks playback.
 * - All platform calls are best-effort with Timber-w-and-continue;
 *   nothing here ever throws to the caller.
 */
interface PlayerFactory {
    fun create(): PlayerHandle
}

/** Minimal MediaPlayer surface the player needs (JVM-testable seam). */
interface PlayerHandle {
    fun setDataSource(path: String)
    fun prepare()
    fun start()
    fun pause()
    fun stop()
    fun release()
    fun seekTo(ms: Int)
    fun getCurrentPosition(): Int
    fun getDuration(): Int
    fun isPlaying(): Boolean
    fun setOnCompletionListener(listener: (() -> Unit)?)
    fun setOnErrorListener(listener: (() -> Boolean)?)
}

internal class RealPlayerFactory : PlayerFactory {
    override fun create(): PlayerHandle = RealPlayerHandle(MediaPlayer())
}

private class RealPlayerHandle(private val player: MediaPlayer) : PlayerHandle {
    override fun setDataSource(path: String) = player.setDataSource(path)
    override fun prepare() = player.prepare()
    override fun start() = player.start()
    override fun pause() = player.pause()
    override fun stop() = player.stop()
    override fun release() = player.release()
    override fun seekTo(ms: Int) = player.seekTo(ms)
    override fun getCurrentPosition(): Int = player.currentPosition
    override fun getDuration(): Int = player.duration
    override fun isPlaying(): Boolean = player.isPlaying
    override fun setOnCompletionListener(listener: (() -> Unit)?) {
        if (listener == null) player.setOnCompletionListener(null)
        else player.setOnCompletionListener { listener() }
    }
    override fun setOnErrorListener(listener: (() -> Boolean)?) {
        if (listener == null) player.setOnErrorListener(null)
        else player.setOnErrorListener { _, _, _ -> listener() }
    }
}

class VoiceMessagePlayer(
    context: Context,
    private val factory: PlayerFactory = RealPlayerFactory(),
) {
    private val audioManager: AudioManager? = try {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    } catch (e: Exception) {
        Timber.w(e, "VoicePlay: no AudioManager")
        null
    }

    @Volatile
    var isPlaying: Boolean = false
        private set

    /** True while a clip handle is alive (playing or paused). */
    val hasClip: Boolean
        get() = handle != null

    private var handle: PlayerHandle? = null

    /** Fired on natural completion (internal threads — route via StateFlow). */
    var onCompletion: (() -> Unit)? = null

    /** Fired on platform errors; playback is already stopped. */
    var onError: (() -> Unit)? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        ) {
            pause()
        }
    }

    /**
     * Play [path], stopping any current clip first. Returns true only
     * when the platform accepted the start. Audio-focus failure still
     * plays — focus is advisory, never a blocker.
     */
    @Synchronized
    fun play(path: String): Boolean {
        // Single-player discipline: a new clip always preempts the old.
        stopLocked()
        var fresh: PlayerHandle? = null
        try {
            fresh = factory.create()
            fresh.setDataSource(path)
            fresh.setOnCompletionListener {
                isPlaying = false
                try {
                    onCompletion?.invoke()
                } catch (e: Exception) {
                    Timber.w(e, "VoicePlay: onCompletion failed")
                }
            }
            fresh.setOnErrorListener {
                isPlaying = false
                try {
                    onError?.invoke()
                } catch (e: Exception) {
                    Timber.w(e, "VoicePlay: onError failed")
                }
                true
            }
            fresh.prepare()
            requestFocus()
            fresh.start()
            handle = fresh
            isPlaying = true
            return true
        } catch (e: Exception) {
            Timber.w(e, "VoicePlay: play failed")
            try {
                fresh?.release()
            } catch (_: Exception) {
                // Best effort.
            }
            handle = null
            isPlaying = false
            return false
        }
    }

    /**
     * Resume a paused clip from its kept position. No-op (false) when no
     * clip handle is alive — callers fall back to play(path).
     */
    @Synchronized
    fun resume(): Boolean {
        val current = handle ?: return false
        if (isPlaying) return true
        return try {
            requestFocus()
            current.start()
            isPlaying = true
            true
        } catch (e: Exception) {
            Timber.w(e, "VoicePlay: resume failed")
            isPlaying = false
            false
        }
    }

    /** Pause, keeping the handle and position for resume. */
    @Synchronized
    fun pause() {
        if (!isPlaying) return
        try {
            handle?.pause()
        } catch (e: Exception) {
            Timber.w(e, "VoicePlay: pause failed")
        }
        isPlaying = false
        abandonFocus()
    }

    /** Stop and release the player handle. Safe when idle. */
    @Synchronized
    fun stop() {
        stopLocked()
    }

    /** Best-effort seek (no-op on failure). */
    fun seekTo(ms: Int) {
        try {
            handle?.seekTo(ms)
        } catch (e: Exception) {
            Timber.w(e, "VoicePlay: seekTo failed")
        }
    }

    /** Best-effort position in ms (0 when idle or on failure). */
    fun positionMs(): Int {
        return try {
            handle?.getCurrentPosition() ?: 0
        } catch (e: Exception) {
            Timber.w(e, "VoicePlay: positionMs failed")
            0
        }
    }

    /** Best-effort duration in ms (0 when idle or on failure). */
    fun durationMs(): Int {
        return try {
            handle?.getDuration() ?: 0
        } catch (e: Exception) {
            Timber.w(e, "VoicePlay: durationMs failed")
            0
        }
    }

    /**
     * Explicit teardown: stop, release, abandon focus, drop listeners.
     * Safe to call twice.
     */
    @Synchronized
    fun destroy() {
        stopLocked()
        onCompletion = null
        onError = null
    }

    private fun stopLocked() {
        try {
            handle?.stop()
        } catch (_: Exception) {
            // Idle/completed handles throw on stop — nothing to keep.
        }
        try {
            handle?.release()
        } catch (e: Exception) {
            Timber.w(e, "VoicePlay: release failed")
        } finally {
            handle = null
            isPlaying = false
        }
        abandonFocus()
    }

    @Suppress("DEPRECATION")
    private fun requestFocus() {
        val manager = audioManager ?: return
        try {
            manager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
            )
        } catch (e: Exception) {
            Timber.w(e, "VoicePlay: focus request failed (advisory)")
        }
    }

    private fun abandonFocus() {
        try {
            audioManager?.abandonAudioFocus(focusListener)
        } catch (e: Exception) {
            Timber.w(e, "VoicePlay: abandon focus failed")
        }
    }
}
