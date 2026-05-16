package com.warped.data.local.inference

import android.content.Context
import com.google.ai.edge.litertlm.ConversationConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Identifies which local inference engine type. */
enum class EngineType { LITE_RT_LM }

/** Tracks which engine (if any) is currently loaded and which model. */
data class ActiveEngine(
    val type: EngineType,
    val modelPath: String,
    val backend: BackendType? = null
)

@Singleton
class EngineManager @Inject constructor(
    private val liteRTLmEngine: LiteRTLmEngine,
    private val backendDetector: BackendDetector,
    @param:ApplicationContext private val context: Context
) {
    private var activeEngine: ActiveEngine? = null

    companion object {
        private const val CACHE_SUBDIR = "litertlm_cache"
    }

    /** Returns the currently active engine info, or null if nothing is loaded. */
    @Synchronized
    fun getActiveEngine(): ActiveEngine? = activeEngine

    /**
     * Switch to the LiteRT-LM engine with the given model.
     * Unloads any currently loaded engine first, then initializes LiteRT-LM.
     * The backend is auto-detected via BackendDetector.
     *
     * @param modelPath Absolute path to the .litertlm model file
     */
    @Synchronized
    fun switchToLiteRT(modelPath: String) {
        val cachedFile = getCachedModelPath(modelPath)

        // If cache missing or size mismatch (corrupt/incomplete), recopy from source
        val sourceFile = File(modelPath)
        if (!cachedFile.exists() || cachedFile.length() != sourceFile.length()) {
            Timber.d("EngineManager: caching .litertlm model to ${cachedFile.absolutePath}")
            try {
                sourceFile.copyTo(cachedFile, overwrite = true)
                Timber.d("EngineManager: model cached successfully (${cachedFile.length()} bytes)")
            } catch (e: Exception) {
                Timber.w(e, "EngineManager: failed to cache model, using original path")
            }
        }

        val resolvedPath = if (cachedFile.exists()) cachedFile.absolutePath else modelPath
        val target = ActiveEngine(EngineType.LITE_RT_LM, resolvedPath, backendDetector.probeBackend())
        if (activeEngine == target) {
            Timber.d("EngineManager: $target already loaded, skipping switch")
            return
        }
        unloadCurrent()

        Timber.d("EngineManager: initializing LiteRT-LM with backend=${target.backend} path=$resolvedPath")
        liteRTLmEngine.init(
            modelPath = resolvedPath,
            backend = target.backend!!,
            visionBackend = null,
            audioBackend = null
        )
        activeEngine = target
        Timber.d("EngineManager: LiteRT-LM engine now active")
    }

    /**
     * Unload the current engine (if any). Releases all native resources.
     * Safe to call even if nothing is loaded.
     */
    @Synchronized
    fun unloadCurrent() {
        val current = activeEngine ?: return
        Timber.d("EngineManager: unloading current engine: $current")

        try {
            liteRTLmEngine.close()
        } catch (e: Exception) {
            Timber.w(e, "EngineManager: error during unload of $current")
        } finally {
            activeEngine = null
        }
    }

    /**
     * Schedule unload of the current engine. If the engine is generating text,
     * waits for generation to finish, then unloads. If not generating, unloads immediately.
     * Safe to call from any thread.
     */
    fun scheduleUnload() {
        val current = activeEngine ?: return
        try {
            liteRTLmEngine.close()
        } catch (e: Exception) { Timber.e(e, "EngineManager: scheduleUnload failed") }
        synchronized(this) { activeEngine = null }
    }

    /** Returns true if any engine is currently loaded. */
    @Synchronized
    fun isEngineLoaded(): Boolean = activeEngine != null

    /**
     * Handle system memory pressure. Called from Application.onTrimMemory.
     * Releases engine resources on critical memory pressure.
     */
    fun handleTrimMemory(level: Int) {
        @Suppress("DEPRECATION")
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            Timber.d("EngineManager: TRIM_MEMORY_RUNNING_CRITICAL — unloading engine")
            try {
                unloadCurrent()
                // Also clear cache to free disk space
                val cacheDir = File(context.cacheDir, CACHE_SUBDIR)
                if (cacheDir.exists()) {
                    cacheDir.deleteRecursively()
                    Timber.d("EngineManager: cache directory cleared")
                }
            } catch (e: Exception) {
                Timber.w(e, "EngineManager: error during trim memory")
            }
        }
    }

    private fun getCachedModelPath(originalPath: String): File {
        val cacheDir = File(context.cacheDir, CACHE_SUBDIR)
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val fileName = File(originalPath).name
        return File(cacheDir, fileName)
    }

    /**
     * Create a LiteRT-LM conversation. Convenience method that delegates to the engine.
     * @throws IllegalStateException if LiteRT-LM is not the active engine
     */
    @Synchronized
    fun createLiteRTConversation(config: ConversationConfig = ConversationConfig()) =
        liteRTLmEngine.createConversation(config)

    /** Returns the LiteRT-LM engine directly for advanced usage. */
    fun getLiteRTLmEngine(): LiteRTLmEngine = liteRTLmEngine
}
