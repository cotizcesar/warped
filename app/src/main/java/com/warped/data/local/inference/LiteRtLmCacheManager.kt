package com.warped.data.local.inference

import android.content.Context
import com.warped.BuildConfig
import com.warped.data.local.preferences.AdvancedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiteRtLmCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val advancedPreferences: AdvancedPreferences,
) {
    val cacheRoot: File = File(context.cacheDir, LiteRtLmCache.namespaceFor(BuildConfig.LITERTLM_VERSION)).also {
        if (!it.exists() && !it.mkdirs()) throw java.io.IOException("Cannot create cache dir: $it")
    }

    /**
     * Per-model compiled-cache dir. Keyed by full-path hash + filename so two
     * distinct models sharing a bare filename (e.g. old vs new
     * `gemma-3n-E2B-it-int4.litertlm` in different dirs) never share a slot.
     * String.hashCode() is specified (stable across processes) — no runtime
     * registration needed.
     */
    fun cacheDirForModel(modelPath: String): File =
        File(cacheRoot, "${modelPath.hashCode()}-${File(modelPath).name}")

    suspend fun currentSizeBytes(): Long = withContext(Dispatchers.IO) {
        if (!cacheRoot.exists()) return@withContext 0L
        cacheRoot.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    suspend fun ensureWithinCap() = withContext(Dispatchers.IO) {
        val cap = advancedPreferences.cacheMaxSizeBytes.firstOrNull() ?: LiteRtLmCache.DEFAULT_CAP_BYTES
        var current = currentSizeBytesInternal()
        if (current <= cap) return@withContext
        val files = cacheRoot.walkTopDown()
            .filter { it.isFile }
            .toList()
            .sortedBy { it.lastModified() }
        for (file in files) {
            if (current <= cap) break
            val size = file.length()
            if (file.delete()) {
                current -= size
                Timber.d("LiteRtLmCacheManager: evicted ${file.name} (${size}B) — current=${current}B cap=${cap}B")
            }
        }
    }

    suspend fun evictAll() = withContext(Dispatchers.IO) {
        if (cacheRoot.exists()) {
            cacheRoot.deleteRecursively()
            Timber.d("LiteRtLmCacheManager: cache directory cleared")
        }
        cacheRoot.mkdirs()
    }

    suspend fun touchAccess(modelPath: String) = withContext(Dispatchers.IO) {
        val target = cacheDirForModel(modelPath)
        if (target.exists()) {
            target.setLastModified(System.currentTimeMillis())
        }
    }

    private fun currentSizeBytesInternal(): Long {
        if (!cacheRoot.exists()) return 0L
        return cacheRoot.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }
}
