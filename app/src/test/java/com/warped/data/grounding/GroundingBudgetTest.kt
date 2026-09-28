package com.warped.data.grounding

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Phase 52 (EXTRACT-02): global budget split across N pages.
 *
 * Constants are LOW-confidence estimates kept behind
 * [GroundingBudget.globalBudget] / [GroundingBudget.perPageBudget] so
 * TUNE-01 can refine them after on-device validation.
 */
class GroundingBudgetTest {

    @Test
    fun `single page gets the whole global budget`() {
        assertThat(GroundingBudget.perPageBudget(4096, 1))
            .isEqualTo(GroundingBudget.globalBudget(4096))
        assertThat(GroundingBudget.globalBudget(4096)).isEqualTo(6000)
    }

    @Test
    fun `budget splits evenly across N pages`() {
        assertThat(GroundingBudget.perPageBudget(4096, 5)).isEqualTo(1500)
        assertThat(GroundingBudget.perPageBudget(4096, 2)).isEqualTo(3000)
    }

    @Test
    fun `per-page never drops below the floor`() {
        assertThat(GroundingBudget.perPageBudget(4096, 10))
            .isEqualTo(GroundingBudget.MIN_PER_PAGE)
        assertThat(GroundingBudget.perPageBudget(4096, 100))
            .isEqualTo(GroundingBudget.MIN_PER_PAGE)
    }

    @Test
    fun `small windows shrink below the default tier`() {
        assertThat(GroundingBudget.globalBudget(2048))
            .isLessThan(GroundingBudget.globalBudget(4096))
    }

    @Test
    fun `large windows scale up modestly`() {
        assertThat(GroundingBudget.globalBudget(8192))
            .isGreaterThan(GroundingBudget.globalBudget(4096))
    }
}
