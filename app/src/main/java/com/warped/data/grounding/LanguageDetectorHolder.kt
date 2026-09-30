package com.warped.data.grounding

import com.optimaize.langdetect.LanguageDetector
import com.optimaize.langdetect.LanguageDetectorBuilder
import com.optimaize.langdetect.ngram.NgramExtractors
import com.optimaize.langdetect.profiles.LanguageProfileReader
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Quick-task (langdetect-library): offline language detection over es+en only.
 *
 * THREAD FINDING (verified via grep on ChatViewModel): every
 * [GroundingPrompt.augment] call site runs inside
 * `generationJob = viewModelScope.launch(...)` — i.e. on
 * Dispatchers.Main.immediate. The only `withContext` in ChatViewModel is the
 * LiteRT model-switch path, NOT the send path. Profile JSON parsing must
 * therefore NEVER happen on the caller thread: the detector pre-warms on a
 * daemon background thread on first use, and [detectSpanish] returns NULL
 * (→ caller falls back to the regex) until the detector is ready. Send is
 * never blocked, even on a cold start.
 *
 * Contract: TRUE (confident Spanish) / FALSE (confident non-Spanish) / NULL
 * (blank input, detector not ready yet, empty probabilities, low confidence
 * either way, init failure, or ANY thrown library exception). NULL always
 * means "let the caller's regex fallback decide". Pure JVM — no Android
 * imports, unit-testable.
 */
internal object LanguageDetectorHolder {

    /**
     * A language wins only above this probability. With just es+en profiles
     * loaded the winner usually scores ~0.99; anything at/below 0.5 is a
     * coin-flip the regex fallback resolves better (fail-open preserved).
     */
    internal const val SPANISH_CONFIDENCE_THRESHOLD = 0.5

    /** Short-text profiles: trained for chat-length input, not documents. */
    private const val PROFILE_DIR = "languages.shorttext"
    private const val SPANISH = "es"

    /**
     * Test seam: production loads es+en from the jar; tests override this to
     * return a fake or to throw (fallback path). See [resetForTest].
     */
    internal var detectorFactory: () -> LanguageDetector? = { loadEsEnDetector() }

    @Volatile
    private var detector: LanguageDetector? = null
    private val warmStarted = AtomicBoolean(false)

    fun detectSpanish(text: String): Boolean? {
        if (text.isBlank()) return null
        ensureWarmed()
        val active = detector ?: return null
        return try {
            decide(active.getProbabilities(text))
        } catch (t: Throwable) {
            // Library must never crash or block send: any failure → NULL.
            null
        }
    }

    private fun decide(
        probabilities: List<com.optimaize.langdetect.DetectedLanguage>,
    ): Boolean? {
        if (probabilities.isEmpty()) return null
        val spanishBest = probabilities
            .firstOrNull { it.locale.language == SPANISH }
            ?.probability ?: 0.0
        if (spanishBest > SPANISH_CONFIDENCE_THRESHOLD) return true
        // Spanish is weak/absent: trust the winner only if IT is confident,
        // otherwise stay uncertain and let the regex fallback decide.
        return if (probabilities[0].probability > SPANISH_CONFIDENCE_THRESHOLD) false else null
    }

    private fun ensureWarmed() {
        if (warmStarted.compareAndSet(false, true)) {
            thread(isDaemon = true, name = "langdetect-warm") {
                detector = safeLoad()
            }
        }
    }

    private fun safeLoad(): LanguageDetector? = try {
        detectorFactory()
    } catch (t: Throwable) {
        null
    }

    private fun loadEsEnDetector(): LanguageDetector? {
        val classLoader = LanguageDetectorHolder::class.java.classLoader ?: return null
        val profiles = LanguageProfileReader().read(classLoader, PROFILE_DIR, listOf("es", "en"))
        return LanguageDetectorBuilder.create(NgramExtractors.standard())
            .withProfiles(profiles)
            .build()
    }

    /** Test-only: load synchronously so JVM tests pin behavior deterministically. */
    internal fun ensureLoadedBlocking() {
        detector = safeLoad()
    }

    /** Test-only: restore production state between tests. */
    internal fun resetForTest() {
        detector = null
        warmStarted.set(false)
        detectorFactory = { loadEsEnDetector() }
    }
}
