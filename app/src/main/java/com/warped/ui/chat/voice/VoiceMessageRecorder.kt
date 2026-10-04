package com.warped.ui.chat.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import timber.log.Timber

/**
 * Phase 67 (VMSG-01): thin MediaRecorder wrapper for voice-message capture.
 * VM-owned sibling to [VoiceDictationManager]; created lazily by
 * ChatViewModel with output dir filesDir/voice, destroyed in onCleared.
 *
 * Contract:
 * - Coroutine-free: no scopes inside. The ViewModel drives polling
 *   (amplitude sampler, 1 s ticker, 60 s auto-stop). This class only
 *   exposes state-friendly primitives.
 * - start() returns true only when the platform accepted capture; the
 *   owner flips its recording flag ONLY on true (WR-01 discipline from
 *   dictation). Double-start while recording is ignored (single-flight
 *   inside the recorder too).
 * - stop() keeps the file (caller sends it); cancel() deletes it.
 * - All platform calls are best-effort with Timber-w-and-continue;
 *   nothing here ever throws to the caller.
 */
interface RecorderFactory {
    fun create(): RecorderHandle
}

/** Minimal MediaRecorder surface the recorder needs (JVM-testable seam). */
interface RecorderHandle {
    fun setAudioSource(source: Int)
    fun setOutputFormat(format: Int)
    fun setAudioEncoder(encoder: Int)
    fun setAudioEncodingBitRate(bitRate: Int)
    fun setAudioSamplingRate(rate: Int)
    fun setOutputFile(path: String)
    fun prepare()
    fun start()
    fun stop()
    fun getMaxAmplitude(): Int
    fun release()
}

internal class RealRecorderFactory(private val context: Context) : RecorderFactory {
    // 2026-10-04 warning fix: the no-arg MediaRecorder constructor is
    // deprecated since API 31 — use the Context version there (minSdk 28
    // keeps the legacy path below it).
    override fun create(): RecorderHandle = RealRecorderHandle(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context)
        else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        },
    )
}

private class RealRecorderHandle(private val recorder: MediaRecorder) : RecorderHandle {
    @Suppress("DEPRECATION")
    override fun setAudioSource(source: Int) = recorder.setAudioSource(source)
    @Suppress("DEPRECATION")
    override fun setOutputFormat(format: Int) = recorder.setOutputFormat(format)
    @Suppress("DEPRECATION")
    override fun setAudioEncoder(encoder: Int) = recorder.setAudioEncoder(encoder)
    @Suppress("DEPRECATION")
    override fun setAudioEncodingBitRate(bitRate: Int) = recorder.setAudioEncodingBitRate(bitRate)
    @Suppress("DEPRECATION")
    override fun setAudioSamplingRate(rate: Int) = recorder.setAudioSamplingRate(rate)
    @Suppress("DEPRECATION")
    override fun setOutputFile(path: String) = recorder.setOutputFile(path)
    override fun prepare() = recorder.prepare()
    override fun start() = recorder.start()
    override fun stop() = recorder.stop()
    override fun getMaxAmplitude(): Int = recorder.maxAmplitude
    override fun release() = recorder.release()
}

class VoiceMessageRecorder(
    context: Context,
    private val outputDir: File,
    private val factory: RecorderFactory = RealRecorderFactory(context),
) {
    @Volatile
    var isRecording: Boolean = false
        private set

    private var handle: RecorderHandle? = null
    private var outputFile: File? = null

    /**
     * Start capture into filesDir/voice/vm-<epoch>.m4a. Caller ensures the
     * dir exists (best-effort mkdirs here as backstop). Returns true only
     * when the platform accepted the start.
     */
    @Synchronized
    fun start(): Boolean {
        if (isRecording) return true
        val file = File(outputDir, "vm-${System.currentTimeMillis()}.m4a")
        try {
            if (!outputDir.exists()) outputDir.mkdirs()
            val rec = factory.create()
            @Suppress("DEPRECATION")
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            @Suppress("DEPRECATION")
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            @Suppress("DEPRECATION")
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioEncodingBitRate(128_000)
            rec.setAudioSamplingRate(44_100)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()
            handle = rec
            outputFile = file
            isRecording = true
            return true
        } catch (e: Exception) {
            Timber.w(e, "VoiceMsg: recorder start failed")
            try {
                handle?.release()
            } catch (_: Exception) {
                // Best effort.
            } finally {
                handle = null
            }
            // A failed start must not leave a zero-byte file behind.
            try {
                if (file.exists()) file.delete()
            } catch (_: Exception) {
            }
            isRecording = false
            return false
        }
    }

    /**
     * Stop capture, keeping the file for send. Returns the clip file or
     * null on failure (stop() throws RuntimeException on too-short or
     * failed recordings — treated as discard-with-log).
     */
    @Synchronized
    fun stop(): File? {
        if (!isRecording) return null
        val file = outputFile
        return try {
            try {
                handle?.stop()
            } catch (e: RuntimeException) {
                Timber.w(e, "VoiceMsg: recorder stop failed")
                try {
                    file?.takeIf { it.exists() }?.delete()
                } catch (_: Exception) {
                }
                return null
            }
            file?.takeIf { it.exists() }
        } finally {
            try {
                handle?.release()
            } catch (e: Exception) {
                Timber.w(e, "VoiceMsg: recorder release failed")
            } finally {
                handle = null
                outputFile = null
                isRecording = false
            }
        }
    }

    /** Stop capture and delete the file immediately (explicit cancel). */
    @Synchronized
    fun cancel() {
        if (!isRecording && outputFile == null) return
        try {
            try {
                handle?.stop()
            } catch (_: RuntimeException) {
                // Too-short/failed recording — nothing to keep anyway.
            }
        } finally {
            try {
                handle?.release()
            } catch (e: Exception) {
                Timber.w(e, "VoiceMsg: cancel release failed")
            } finally {
                handle = null
                isRecording = false
            }
            try {
                outputFile?.takeIf { it.exists() }?.delete()
            } catch (e: Exception) {
                Timber.w(e, "VoiceMsg: cancel delete failed")
            } finally {
                outputFile = null
            }
        }
    }

    /** Best-effort amplitude sample 0..32767 (0 when idle or on failure). */
    fun maxAmplitude(): Int {
        if (!isRecording) return 0
        return try {
            handle?.getMaxAmplitude() ?: 0
        } catch (e: Exception) {
            Timber.w(e, "VoiceMsg: maxAmplitude failed")
            0
        }
    }

    /** Explicit teardown. Safe to call twice. Destroy means discard:
     * a partial clip must never survive teardown unreferenced
     * (CR-02: keep only happens through stop()). */
    @Synchronized
    fun destroy() {
        try {
            if (isRecording) {
                try {
                    handle?.stop()
                } catch (_: RuntimeException) {
                }
            }
        } finally {
            try {
                handle?.release()
            } catch (e: Exception) {
                Timber.w(e, "VoiceMsg: destroy failed")
            } finally {
                handle = null
                isRecording = false
            }
            // Never leave a partial clip behind: destroy discards it.
            try {
                outputFile?.takeIf { it.exists() }?.delete()
            } catch (e: Exception) {
                Timber.w(e, "VoiceMsg: destroy delete failed")
            } finally {
                outputFile = null
            }
        }
    }
}
