package com.warped.ui.chat

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Quick-task (scroll-hardening): truth table for [clampPinTarget], the pure
 * pin-target math behind `LazyListState.pinLastItemEnd`.
 *
 * The follow `LaunchedEffect` clears its latch flags before pinning, so a
 * target beyond the laid-out count (layout lag on newly arrived items) must
 * pin the current end instead of throwing — and an empty list must stay a
 * no-op. Negative indices never reach the clamp (the caller guards first);
 * the clamp itself floors them to 0.
 */
class ChatPinTargetTest {

    @Test
    fun `in-range target pins verbatim`() {
        assertThat(clampPinTarget(4, 5)).isEqualTo(4)
        assertThat(clampPinTarget(0, 5)).isEqualTo(0)
    }

    @Test
    fun `target beyond laid-out count pins the current end`() {
        // Layout lag: five items requested, three laid out — pin item 2.
        assertThat(clampPinTarget(4, 3)).isEqualTo(2)
        assertThat(clampPinTarget(9, 5)).isEqualTo(4)
    }

    @Test
    fun `empty list is a no-op`() {
        assertThat(clampPinTarget(0, 0)).isEqualTo(-1)
        assertThat(clampPinTarget(3, 0)).isEqualTo(-1)
        assertThat(clampPinTarget(0, -1)).isEqualTo(-1)
    }

    @Test
    fun `single item always pins zero`() {
        assertThat(clampPinTarget(0, 1)).isEqualTo(0)
        assertThat(clampPinTarget(7, 1)).isEqualTo(0)
    }

    @Test
    fun `negative index floors to zero`() {
        assertThat(clampPinTarget(-3, 5)).isEqualTo(0)
    }
}
