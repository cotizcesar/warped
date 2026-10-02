package com.warped.ui.chat.voice

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Phase 67 (VMSG-05): AAC (m4a) to mono-16kHz-PCM transcoder, zero new
 * dependencies (android.media only).
 *
 * Two layers:
 * - Layer 1 (pure, JVM-tested): [resampleTo16kMono] and [truncateTo30s].
 * - Layer 2 (platform, device-smoke covered): [transcodeFirst30s] runs a
 *   synchronous MediaExtractor + MediaCodec decode loop on the caller's
 *   dispatcher (the ViewModel passes Dispatchers.IO — never Main).
 *
 * NOTE: the MediaCodec decode loop is NOT JVM-coverable (android.jar
 * stubs throw at runtime). It is covered by the on-device record → send
 * → model-response smoke; the watchdog + TranscodeException contract
 * below is what the smoke asserts.
 */
object PcmTranscoder {
    const val TARGET_RATE = 16_000
    const val MAX_SECONDS = 30
    const val MAX_SAMPLES = TARGET_RATE * MAX_SECONDS

    /**
     * Average [srcChannels] interleaved channels to mono, then
     * linear-interpolate from [srcRate] to 16 kHz.
     */
    fun resampleTo16kMono(pcm16: ShortArray, srcRate: Int, srcChannels: Int): ShortArray {
        if (pcm16.isEmpty()) return pcm16
        val channels = srcChannels.coerceAtLeast(1)
        val frames = pcm16.size / channels
        if (frames == 0) return ShortArray(0)
        // Downmix: average channels.
        val mono = ShortArray(frames) { f ->
            var sum = 0
            for (c in 0 until channels) {
                sum += pcm16.getOrElse(f * channels + c) { 0 }.toInt()
            }
            (sum / channels).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        if (srcRate == TARGET_RATE) return mono
        if (srcRate <= 0) return mono
        val ratio = srcRate.toDouble() / TARGET_RATE.toDouble()
        val outLen = ((frames - 1) / ratio).toInt() + 1
        if (outLen <= 0) return ShortArray(0)
        return ShortArray(outLen) { i ->
            val pos = i * ratio
            val lo = pos.toInt().coerceIn(0, frames - 1)
            val hi = (lo + 1).coerceIn(0, frames - 1)
            val frac = (pos - lo).toFloat()
            (mono[lo] * (1f - frac) + mono[hi] * frac).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /**
     * Cap mono PCM at 30 s (480 000 samples). Second is true when
     * truncation happened. Exactly 30 s is NOT flagged.
     */
    fun truncateTo30s(pcm16Mono: ShortArray): Pair<ShortArray, Boolean> {
        if (pcm16Mono.size <= MAX_SAMPLES) return pcm16Mono to false
        return pcm16Mono.copyOf(MAX_SAMPLES) to true
    }

    data class TranscodeResult(
        val bytes: ByteArray,
        val truncated: Boolean,
        val durationMs: Long,
    )

    class TranscodeException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /**
     * Decode [path] (m4a/AAC) to mono 16 kHz PCM, keeping only the first
     * 30 s. Runs fully on [ioDispatcher] (caller passes Dispatchers.IO).
     * Throws [TranscodeException] on corrupt/empty input — the caller maps
     * it to a user message, never a crash. Watchdog: ~30 s wall-clock.
     */
    suspend fun transcodeFirst30s(
        path: String,
        ioDispatcher: CoroutineContext = Dispatchers.IO,
        timeoutMs: Long = 30_000L,
    ): TranscodeResult = withContext(ioDispatcher) {
        val deadline = System.currentTimeMillis() + timeoutMs
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            try {
                extractor.setDataSource(path)
            } catch (e: Exception) {
                throw TranscodeException("cannot open audio file", e)
            }
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = f
                    break
                }
            }
            if (trackIndex < 0 || format == null) {
                throw TranscodeException("no audio track found")
            }
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: throw TranscodeException("missing audio mime")
            extractor.selectTrack(trackIndex)
            codec = try {
                MediaCodec.createDecoderByType(mime)
            } catch (e: Exception) {
                throw TranscodeException("no decoder for $mime", e)
            }
            try {
                codec.configure(format, null, null, 0)
            } catch (e: Exception) {
                throw TranscodeException("decoder configure failed", e)
            }
            codec.start()

            var actualRate = 0
            var actualChannels = 0
            val samples = mutableListOf<Short>()
            val bufferInfo = MediaCodec.BufferInfo()
            var inputEos = false
            var outputEos = false
            val timeoutUs = 10_000L
            while (!outputEos) {
                if (System.currentTimeMillis() > deadline) {
                    throw TranscodeException("transcode timed out")
                }
                if (!inputEos) {
                    val inIndex = codec.dequeueInputBuffer(timeoutUs)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex) ?: ByteBuffer.allocate(0)
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, 0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputEos = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                when {
                    outIndex >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIndex)
                        if (outBuf != null && bufferInfo.size > 0) {
                            val dup = outBuf.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                            dup.position(bufferInfo.offset)
                            dup.limit(bufferInfo.offset + bufferInfo.size)
                            while (dup.remaining() >= 2) {
                                samples.add(dup.short)
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputEos = true
                        }
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // Read the ACTUAL decoded format — never hardcode
                        // the encoder's 44.1 kHz assumption (Pitfall 3).
                        val outFormat = codec.outputFormat
                        actualRate = if (outFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        } else 0
                        actualChannels = if (outFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        } else 0
                    }
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (inputEos) {
                            // No more progress possible — avoid a hot spin.
                            // WR-01: coroutine delay (never Thread.sleep in
                            // a suspend function on Dispatchers.IO).
                            delay(2)
                        }
                    }
                }
            }
            if (samples.isEmpty()) throw TranscodeException("decoded zero samples")
            val rate = if (actualRate > 0) actualRate else 44_100
            val channels = if (actualChannels > 0) actualChannels else 1
            val mono = resampleTo16kMono(samples.toShortArray(), rate, channels)
            val (capped, truncated) = truncateTo30s(mono)
            val bytes = ByteBuffer.allocate(capped.size * 2)
                .order(ByteOrder.LITTLE_ENDIAN)
                .also { buf -> capped.forEach { buf.putShort(it) } }
                .array()
            val durationMs = (capped.size * 1000L) / TARGET_RATE
            TranscodeResult(bytes, truncated, durationMs)
        } catch (e: TranscodeException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "VoiceMsg: transcode failed")
            throw TranscodeException("transcode failed: ${e.message}", e)
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
            try {
                codec?.release()
            } catch (_: Exception) {
            }
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }
}
