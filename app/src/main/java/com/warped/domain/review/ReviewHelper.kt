package com.warped.domain.review

import android.app.Activity
import android.content.Context
import com.google.android.play.core.review.ReviewManagerFactory
import com.warped.data.local.preferences.ReviewPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

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
 * Phase 66 (RATE-01): seam around the Play review flow, so
 * [ReviewHelper] stays JVM-testable ([ReviewHelperTest]) without Play
 * services on the classpath. The production binding
 * ([DefaultReviewFlowLauncher], via `ReviewModule`) owns the
 * `ReviewManagerFactory` Task bridges.
 */
fun interface ReviewFlowLauncher {
    suspend fun launch(activity: Activity)
}

/**
 * Phase 66 (RATE-01): production [ReviewFlowLauncher] owning the Play
 * `Task` → coroutine bridges.
 *
 * WR-03: both bridges guard the resume with `tryResume` (a Play
 * callback landing after cancellation completes the continuation token
 * as consumed — never an `IllegalStateException` on the callback
 * thread) and register `invokeOnCancellation` so a cancelled parent
 * job is observed instead of leaking the listener resumption.
 */
@Singleton
class DefaultReviewFlowLauncher @Inject constructor(
    @ApplicationContext private val appContext: Context
) : ReviewFlowLauncher {
    override suspend fun launch(activity: Activity) {
        val manager = ReviewManagerFactory.create(appContext)
        val reviewInfo = suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation {
                Timber.d("Review: review request cancelled")
            }
            manager.requestReviewFlow()
                .addOnSuccessListener { result ->
                    val token = cont.tryResume(result)
                    if (token == null) {
                        Timber.d("Review: request completed after cancellation")
                    } else {
                        cont.completeResume(token)
                    }
                }
                .addOnFailureListener { e ->
                    val token = cont.tryResumeWithException(e)
                    if (token == null) {
                        Timber.w(e, "Review: request failed after cancellation")
                    } else {
                        cont.completeResume(token)
                    }
                }
        }
        suspendCancellableCoroutine<Unit> { cont ->
            cont.invokeOnCancellation {
                Timber.d("Review: review launch cancelled")
            }
            manager.launchReviewFlow(activity, reviewInfo)
                .addOnSuccessListener {
                    val token = cont.tryResume(Unit)
                    if (token == null) {
                        Timber.d("Review: launch completed after cancellation")
                    } else {
                        cont.completeResume(token)
                    }
                }
                .addOnFailureListener { e ->
                    val token = cont.tryResumeWithException(e)
                    if (token == null) {
                        Timber.w(e, "Review: launch failed after cancellation")
                    } else {
                        cont.completeResume(token)
                    }
                }
        }
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
 *
 * Review-fix hardening:
 * - WR-01: `CancellationException` is rethrown, never swallowed, so a
 *   cleared `ViewModelScope` completes normally.
 * - WR-02: increment + single-snapshot read + eligibility + prompt
 *   record run under [mutex], so overlapping prompts serialize —
 *   no double-prompt, no cooldown/cap bypass.
 * - IN-01: the prompt timestamp is captured AFTER the flow completes
 *   (cooldown starts at show time, not check time).
 */
@Singleton
class ReviewHelper @Inject constructor(
    private val prefs: ReviewPreferences,
    private val launcher: ReviewFlowLauncher
) {
    private val mutex = Mutex()

    suspend fun maybePrompt(activity: Activity) {
        try {
            mutex.withLock {
                prefs.incrementCompletedTurns()
                val snapshot = prefs.reviewState.first()
                val nowMillis = System.currentTimeMillis()
                if (!ReviewEligibility.isEligible(
                        snapshot.completedTurns,
                        snapshot.lastPromptMillis,
                        snapshot.promptCount,
                        nowMillis
                    )
                ) {
                    return
                }
                launcher.launch(activity)
                prefs.recordPrompt(System.currentTimeMillis())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Review: prompt flow failed silently")
        }
    }
}
