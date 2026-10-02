package com.warped.domain.review

import android.app.Activity
import com.google.common.truth.Truth.assertThat
import com.warped.data.local.preferences.ReviewPreferences
import com.warped.data.local.preferences.ReviewState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Phase 66 review-fix: behavioral gates for the hardened
 * [ReviewHelper.maybePrompt].
 *
 * The Play flow is faked behind [ReviewFlowLauncher] and the store is a
 * mockk [ReviewPreferences] over a [MutableStateFlow] snapshot, so these
 * run on the JVM without Play services:
 * - cooldown/cap atomicity under concurrency (WR-02),
 * - `CancellationException` propagation (WR-01),
 * - fully silent failures (RATE-01 guarantee),
 * - prompt timestamp recorded after the flow (IN-01),
 * - single-snapshot read per prompt (WR-02).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewHelperTest {

    private val activity = mockk<Activity>()

    /** In-memory [ReviewPreferences] double recording every write. */
    private class FakeStore(initial: ReviewState) {
        val state = MutableStateFlow(initial)
        val recorded = mutableListOf<Long>()
        var increments = 0

        val mock: ReviewPreferences = mockk {
            every { reviewState } returns state
            coEvery { incrementCompletedTurns() } coAnswers {
                synchronized(this@FakeStore) {
                    increments++
                    state.value = state.value.copy(
                        completedTurns = state.value.completedTurns + 1
                    )
                }
            }
            coEvery { recordPrompt(any()) } coAnswers {
                val now = firstArg<Long>()
                synchronized(this@FakeStore) {
                    recorded.add(now)
                    state.value = state.value.copy(
                        lastPromptMillis = now,
                        promptCount = state.value.promptCount + 1
                    )
                }
            }
        }
    }

    @Test
    fun `ineligible turn increments but never prompts`() = runTest {
        val store = FakeStore(ReviewState(completedTurns = 3))
        var launches = 0
        val helper = ReviewHelper(store.mock, ReviewFlowLauncher { launches++ })

        helper.maybePrompt(activity)

        assertThat(store.increments).isEqualTo(1)
        assertThat(launches).isEqualTo(0)
        assertThat(store.recorded).isEmpty()
    }

    @Test
    fun `eligible turn launches once and records a post-flow timestamp`() = runTest {
        val store = FakeStore(ReviewState(completedTurns = 4))
        var launchMillis = 0L
        val helper = ReviewHelper(
            store.mock,
            ReviewFlowLauncher { launchMillis = System.currentTimeMillis() }
        )

        helper.maybePrompt(activity)

        assertThat(store.increments).isEqualTo(1)
        assertThat(store.recorded).hasSize(1)
        // IN-01: the recorded timestamp comes from AFTER the flow ran.
        assertThat(store.recorded.single()).isAtLeast(launchMillis)
    }

    @Test
    fun `launcher failure is fully silent`() = runTest {
        val store = FakeStore(ReviewState(completedTurns = 4))
        val helper = ReviewHelper(
            store.mock,
            ReviewFlowLauncher { throw RuntimeException("Play quota suppressed") }
        )

        // Must return normally — never a toast/snackbar, never a throw.
        helper.maybePrompt(activity)

        assertThat(store.recorded).isEmpty()
    }

    @Test
    fun `cancellation exception is never swallowed`() = runTest {
        val store = FakeStore(ReviewState(completedTurns = 4))
        val helper = ReviewHelper(
            store.mock,
            ReviewFlowLauncher { throw CancellationException("scope cleared") }
        )

        val thrown = try {
            helper.maybePrompt(activity)
            null
        } catch (e: CancellationException) {
            e
        }

        assertThat(thrown).isNotNull()
        assertThat(store.recorded).isEmpty()
    }

    @Test
    fun `scope cancellation while in flight aborts without recording`() = runTest {
        val store = FakeStore(ReviewState(completedTurns = 4))
        val helper = ReviewHelper(
            store.mock,
            ReviewFlowLauncher { awaitCancellation() }
        )

        val job = launch { helper.maybePrompt(activity) }
        runCurrent()
        job.cancel()
        advanceUntilIdle()

        assertThat(job.isCancelled).isTrue()
        assertThat(store.recorded).isEmpty()
    }

    @Test
    fun `concurrent prompts serialize and fire only once`() = runTest {
        val store = FakeStore(ReviewState(completedTurns = 4))
        var launches = 0
        val helper = ReviewHelper(store.mock, ReviewFlowLauncher { launches++ })

        // WR-02: both coroutines increment, but the Mutex serializes the
        // whole check-and-record — the second sees the first prompt's
        // cooldown and stands down. Without the lock both would fire.
        awaitAll(
            async { helper.maybePrompt(activity) },
            async { helper.maybePrompt(activity) }
        )

        assertThat(store.increments).isEqualTo(2)
        assertThat(launches).isEqualTo(1)
        assertThat(store.recorded).hasSize(1)
        // Single snapshot read per prompt — never three separate flows.
        verify(exactly = 2) { store.mock.reviewState }
    }
}
