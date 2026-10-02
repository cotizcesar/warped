package com.warped.ui.chat.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import timber.log.Timber

/**
 * Phase 65 (VOICE-01/VOICE-03): thin platform SpeechRecognizer wrapper for
 * voice dictation. Greenfield file — no codebase analog.
 *
 * Contract:
 * - Partial results stream through [onPartial], final text through [onFinal].
 * - Errors surface only as a code on [onError] with zero UI side effects;
 *   the owner (ChatViewModel) applies the silent-error policy.
 * - Recognition language follows the system locale (no EXTRA_LANGUAGE
 *   override, no in-app picker).
 * - All platform calls are async with no synchronous waits — never blocks
 *   the UI thread. Availability is resolved by the owner off the
 *   composition path (Dispatchers.IO) and cached.
 * - The owner MUST call [destroy] with the UI lifecycle (ChatViewModel
 *   onCleared) so no recognizer leaks past the screen.
 */
class VoiceDictationManager(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (Int) -> Unit,
) {
    @Volatile
    private var recognizer: SpeechRecognizer? = null

    /**
     * True when the platform can serve recognition on this device. The
     * caller resolves this off the main thread and caches the result.
     */
    fun isAvailable(): Boolean = try {
        SpeechRecognizer.isRecognitionAvailable(context)
    } catch (e: Exception) {
        Timber.w(e, "Voice: availability check failed")
        false
    }

    /**
     * Start listening. Creates the platform recognizer once, attaches the
     * listener, and issues the async start call. Recognition language
     * follows the system locale.
     *
     * @return true when the platform accepted the start request. False
     * when the synchronous start threw (the async [onError] path also
     * fired, so the owner must NOT flip its listening flag on in this
     * case — WR-01). Async failures always surface via [onError].
     */
    fun start(): Boolean {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                firstResult(partialResults)?.let { onPartial(it) }
            }

            override fun onResults(results: Bundle?) {
                // CR-02: an empty final still ends the session — forward it
                // blank so the owner clears its listening flag. The
                // platform recognizer is single-shot: on silence/timeout
                // it stops by itself, and without this the UI stays stuck
                // on the stop toggle for a dead recognizer.
                onFinal(firstResult(results).orEmpty())
            }

            override fun onError(error: Int) {
                onError(error)
            }
        }
        try {
            // WR-02: reset any error/busy state before (re)starting. After
            // an error the platform typically requires cancel() before the
            // next startListening, or it fails with ERROR_RECOGNIZER_BUSY
            // (stopListening alone does not reset the error state). Best
            // effort: a missing instance means nothing to reset.
            try {
                recognizer?.cancel()
            } catch (e: Exception) {
                Timber.w(e, "Voice: pre-start cancel failed")
            }
            var current = recognizer
            if (current == null) {
                current = SpeechRecognizer.createSpeechRecognizer(context)
                current.setRecognitionListener(listener)
                recognizer = current
            } else {
                current.setRecognitionListener(listener)
            }
            current.startListening(intent)
            return true
        } catch (e: Exception) {
            Timber.w(e, "Voice: startListening failed")
            onError(SpeechRecognizer.ERROR_CLIENT)
            return false
        }
    }

    /** Stop listening, keeping the recognizer for a later restart. */
    fun stop() {
        try {
            recognizer?.stopListening()
        } catch (e: Exception) {
            Timber.w(e, "Voice: stopListening failed")
        }
    }

    /**
     * Explicit teardown: stops listening and destroys the platform
     * recognizer. Must be called with the UI lifecycle (ViewModel
     * onCleared) so no recognizer leaks past the screen.
     */
    fun destroy() {
        try {
            recognizer?.stopListening()
        } catch (e: Exception) {
            Timber.w(e, "Voice: destroy stopListening failed")
        }
        try {
            recognizer?.destroy()
        } catch (e: Exception) {
            Timber.w(e, "Voice: destroy failed")
        } finally {
            recognizer = null
        }
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
}
