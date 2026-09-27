package com.warped.data.local.inference

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * 45-02 LRT-09: version-namespaced mmap cache upgrade-install invariant.
 *
 * Bumping `litertlm` changes BuildConfig.LITERTLM_VERSION, which auto-namespaces the
 * cache (`litertlm/<version>`). These JVM tests pin the namespace math: the 0.13.1 and
 * 0.17.1 namespaces must coexist (old dir left for LRU eviction, never force-deleted).
 */
class LiteRtLmCacheTest {

    @Test
    fun `upgrade namespaces coexist 0-13-1 and 0-17-1`() {
        val old = LiteRtLmCache.namespaceFor("0.13.1")
        val new = LiteRtLmCache.namespaceFor("0.17.1")

        assertThat(old).isEqualTo("litertlm/0.13.1")
        assertThat(new).isEqualTo("litertlm/0.17.1")
        assertThat(new).isNotEqualTo(old)
        assertThat(LiteRtLmCache.isIsolated(currentVersion = "0.17.1", staleVersion = "0.13.1")).isTrue()
    }

    @Test
    fun `same version is not isolated from itself`() {
        assertThat(LiteRtLmCache.isIsolated(currentVersion = "0.17.1", staleVersion = "0.17.1")).isFalse()
    }

    @Test
    fun `lru cap is 500MB`() {
        assertThat(LiteRtLmCache.DEFAULT_CAP_BYTES).isEqualTo(500L * 1024L * 1024L)
    }
}
