package com.warped.data.local.inference

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Mmap-aware load rule: estimated need = 60% of file bytes + 512 MiB
 * KV/overhead reserve, fitting in 90% of available RAM.
 */
class MemoryCheckerFitTest {

    private val MiB = 1024L * 1024L

    @Test
    fun `device case 2468MB model on 2396MB available loads`() {
        // Real block 2026-10-01 (Pixel 8 + emulator): the old file-size
        // rule rejected a mmap'd load that fits in practice.
        assertThat(MemoryChecker.fitsInMemory(2468 * MiB, 2396 * MiB)).isTrue()
    }

    @Test
    fun `tiny model on ample RAM loads`() {
        assertThat(MemoryChecker.fitsInMemory(304 * MiB, 4000 * MiB)).isTrue()
    }

    @Test
    fun `absurd case still blocked`() {
        // 4.9 GB model with 2 GB free must stay blocked (OOM protection).
        assertThat(MemoryChecker.fitsInMemory(4900 * MiB, 2000 * MiB)).isFalse()
    }

    @Test
    fun `empty device edge loads nothing`() {
        assertThat(MemoryChecker.fitsInMemory(100 * MiB, 0)).isFalse()
    }
}
