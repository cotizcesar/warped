package com.warped.domain.review

import android.app.Activity
import android.content.Context
import com.google.android.play.core.review.ReviewManagerFactory
import com.warped.data.local.preferences.ReviewPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resumeWithException

/**
 * Phase 66 (RATE-01): pure eligibility predicate for the ambient Play
 * In-App Review prompt. Kept free of Android/Play dependencies so it is
 * unit-testable on the JVM ([ReviewEligibilityTest]).
 *
 * Policy (CONTEXT: single-digit turns, weeks-scale cooldown, capped):
 * - at least [MIN_COMPLETED_TURNS] completed chat turns,
 * - at least [COOLDOWN_MILLIS] since the last prompt (first prompt has no
 *   cooldown — [lastPromptMillis] of 0 means "never prompted"),
 * - fewer than [MAX_PROMPTS] total prompts ever shown.
 */
object ReviewEligibility {
    const val MIN_COMPLETED_TURNS = 5
    const val COOLDOWN_MILLIS = 21L * 24L * 60L * 60L * 1000L
    const val MAX_PROMPTS = 3

    fun isEligible(
        completedTurns: Int,
        lastPromptMillis: Long,
        promptCount: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (completedTurns < MIN_COMPLETED_TURNS) return false
        if (promptCount >= MAX_PROMPTS) return false
        if (lastPromptMillis != 0L && nowMillis - lastPromptMillis < COOLDOWN_MILLIS) return false
        return true
    }
}

/**
 * Phase 66 (RATE-01): silent Play In-App Review flow owner.
 *
 * [maybePrompt] increments the completed-turn counter, checks
 * [ReviewEligibility], and — only when eligible — requests and launches
 * the Play review flow. EVERY failure path (quota suppression, API
 * errors, missing Play services) is fully silent: `Timber.w` and return,
 * never a toast/snackbar. Quota suppression is just another silent catch
 * branch.
 *
 * The [Activity] is a method parameter, never stored, because
 * `launchReviewFlow` needs an Activity at call time.
 */
@Singleton
class ReviewHelper @Inject constructor(
    private val prefs: ReviewPreferences,
    @ApplicationContext private val appContext: Context
) {
    suspend fun maybePrompt(activity: Activity) {
        try {
            prefs.incrementCompletedTurns()
            val completedTurns = prefs.completedTurns.first()
            val lastPromptMillis = prefs.lastPromptMillis.first()
            val promptCount = prefs.promptCount.first()
            val nowMillis = System.currentTimeMillis()
            if (!ReviewEligibility.isEligible(completedTurns, lastPromptMillis, promptCount, nowMillis)) {
                return
            }
            val manager = ReviewManagerFactory.create(appContext)
            val reviewInfo = suspendCancellableCoroutine { cont ->
                manager.requestReviewFlow()
                    .addOnSuccessListener { cont.resumeWith(Result.success(it)) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
            suspendCancellableCoroutine<Unit> { cont ->
                manager.launchReviewFlow(activity, reviewInfo)
                    .addOnSuccessListener { cont.resumeWith(Result.success(Unit)) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
            prefs.recordPrompt(nowMillis)
        } catch (e: Exception) {
            Timber.w(e, "Review: prompt flow failed silently")
        }
    }
}
