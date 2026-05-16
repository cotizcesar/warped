package com.warped.ui.chat.components

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import timber.log.Timber
import java.io.File

class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    val isRecording: Boolean get() = recorder != null

    fun startRecording(): File {
        outputFile = File(context.cacheDir, "audio_record_${System.currentTimeMillis()}.m4a")
        outputFile!!.parentFile?.mkdirs()

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16000)
            setAudioEncodingBitRate(32000)
            setAudioChannels(1)
            setOutputFile(outputFile!!.absolutePath)
            prepare()
            start()
        }

        Timber.d("AudioRecorder: started recording to ${outputFile!!.absolutePath}")
        return outputFile!!
    }

    fun stopRecording(): ByteArray? {
        val file = outputFile ?: return null
        try {
            recorder?.apply {
                try { stop() } catch (_: Exception) {}
                try { release() } catch (_: Exception) {}
            }
            recorder = null

            val bytes = if (file.exists() && file.length() > 0) {
                file.readBytes()
            } else null

            Timber.d("AudioRecorder: stopped recording, ${bytes?.size ?: 0} bytes")
            outputFile = null
            return bytes
        } catch (e: Exception) {
            Timber.e(e, "AudioRecorder: stop failed")
            recorder = null
            outputFile = null
            return null
        }
    }

    fun cancel() {
        try {
            recorder?.apply {
                try { stop() } catch (_: Exception) {}
                try { release() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        recorder = null
        outputFile?.delete()
        outputFile = null
        Timber.d("AudioRecorder: cancelled")
    }
}
