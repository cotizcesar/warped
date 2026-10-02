package com.warped.domain.review

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Phase 66 (RATE-01): truth table for the ambient review eligibility
 * predicate. Policy: >= 5 completed turns, 21-day cooldown after a
 * prompt, max 3 prompts ever.
 */
class ReviewEligibilityTest {

    private val day = 24L * 60L * 60L * 1000L
    private val now = 1_800_000_000_000L

    @Test
    fun `below turn threshold is not eligible`() {
        assertThat(
            ReviewEligibility.isEligible(
                completedTurns = 4,
                lastPromptMillis = 0L,
                promptCount = 0,
                nowMillis = now
            )
        ).isFalse()
    }

    @Test
    fun `at turn threshold with no prior prompt is eligible`() {
        assertThat(
            ReviewEligibility.isEligible(
                completedTurns = 5,
                lastPromptMillis = 0L,
                promptCount = 0,
                nowMillis = now
            )
        ).isTrue()
    }

    @Test
    fun `cooldown suppresses a recent prompt`() {
        assertThat(
            ReviewEligibility.isEligible(
                completedTurns = 20,
                lastPromptMillis = now - 7 * day,
                promptCount = 1,
                nowMillis = now
            )
        ).isFalse()
    }

    @Test
    fun `expired cooldown restores eligibility`() {
        assertThat(
            ReviewEligibility.isEligible(
                completedTurns = 20,
                lastPromptMillis = now - 22 * day,
                promptCount = 1,
                nowMillis = now
            )
        ).isTrue()
    }

    @Test
    fun `prompt cap suppresses regardless of turns and cooldown`() {
        assertThat(
            ReviewEligibility.isEligible(
                completedTurns = 100,
                lastPromptMillis = now - 365 * day,
                promptCount = 3,
                nowMillis = now
            )
        ).isFalse()
    }

    @Test
    fun `below cap with expired cooldown stays eligible`() {
        assertThat(
            ReviewEligibility.isEligible(
                completedTurns = 100,
                lastPromptMillis = now - 365 * day,
                promptCount = 2,
                nowMillis = now
            )
        ).isTrue()
    }
}
