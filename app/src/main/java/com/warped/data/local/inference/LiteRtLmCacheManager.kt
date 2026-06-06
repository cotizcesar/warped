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
    val cacheRoot: File = File(context.cacheDir, "litertlm/${BuildConfig.LITERTLM_VERSION}").also { it.mkdirs() }

    fun cacheDirForModel(modelPath: String): File = File(cacheRoot, File(modelPath).name)

    suspend fun currentSizeBytes(): Long = withContext(Dispatchers.IO) {
        if (!cacheRoot.exists()) return@withContext 0L
        cacheRoot.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    suspend fun ensureWithinCap() = withContext(Dispatchers.IO) {
        val cap = advancedPreferences.cacheMaxSizeBytes.firstOrNull() ?: DEFAULT_CAP_BYTES
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

    private companion object {
        const val DEFAULT_CAP_BYTES: Long = 500L * 1024L * 1024L
    }
}
