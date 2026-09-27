package com.warped.data.local.inference

/**
 * Pure-Kotlin version-namespace math for the LiteRT-LM mmap cache.
 *
 * The on-device compiled-model cache lives at `<cacheDir>/litertlm/<engineVersion>/`.
 * Bumping `litertlm` in the version catalog changes [com.warped.BuildConfig.LITERTLM_VERSION],
 * which auto-namespaces the cache: the new engine never reads the old engine's opaque mmap
 * state, so no schema migration is needed and silent drift is impossible by construction.
 *
 * Eviction policy (enforced by [LiteRtLmCacheManager], LRU 500 MB cap):
 * - Eviction is scoped to the CURRENT namespace root only.
 * - A stale namespace (e.g. `litertlm/0.13.1` after upgrading to `0.17.1`) is NEVER
 *   force-deleted: a second profile may still reference it, and deleting compiled cache
 *   silently destroys offline capability. Stale dirs age out only if the user clears data,
 *   or may be lazily reclaimed by a future explicit "clear stale caches" action.
 *
 * 45-02 LRT-09: extracted as a pure (Android-free) object so the upgrade-install
 * invariant (old+new namespaces coexist, stale never force-deleted) is unit-testable
 * on the JVM without Robolectric.
 */
object LiteRtLmCache {

    /** Parent dir name under [android.content.Context.getCacheDir]. */
    const val PARENT_DIR = "litertlm"

    /** LRU cap for the current namespace (500 MB). Mirrors AdvancedPreferences default. */
    const val DEFAULT_CAP_BYTES: Long = 500L * 1024L * 1024L

    /** Relative namespace path for an engine version, e.g. `litertlm/0.17.1`. */
    fun namespaceFor(engineVersion: String): String = "$PARENT_DIR/$engineVersion"

    /**
     * Returns true if [staleVersion]'s namespace is isolated from [currentVersion]'s,
     * i.e. an upgrade-install leaves both dirs coexisting without cross-reads.
     */
    fun isIsolated(currentVersion: String, staleVersion: String): Boolean =
        staleVersion != currentVersion &&
            namespaceFor(staleVersion) != namespaceFor(currentVersion)
}
