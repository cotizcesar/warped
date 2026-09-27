package com.warped.data.local.inference

import android.content.Context
import com.google.ai.edge.litertlm.ConversationConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
    @param:ApplicationContext private val context: Context,
    private val cacheManager: LiteRtLmCacheManager,
) {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeEngine: ActiveEngine? = null

    /** Returns the currently active engine info, or null if nothing is loaded. */
    @Synchronized
    fun getActiveEngine(): ActiveEngine? = activeEngine

    /**
     * Switch to the LiteRT-LM engine with the given model.
     * Unloads any currently loaded engine first, then initializes LiteRT-LM
     * by mmap-ing the source model file directly (no copy). The internal
     * cache directory used by EngineConfig is namespaced by the LiteRT-LM
     * version and capped via [LiteRtLmCacheManager].
     *
     * @param modelPath Absolute path to the .litertlm or .task model file
     */
    @Synchronized
    fun switchToLiteRT(modelPath: String) {
        cacheManager.cacheRoot
        val sourceFile = File(modelPath)
        val target = ActiveEngine(EngineType.LITE_RT_LM, modelPath, backendDetector.probeBackend())
        if (activeEngine == target) {
            Timber.d("EngineManager: $target already loaded, skipping switch")
            return
        }
        unloadCurrent()

        if (!sourceFile.exists()) {
            error("EngineManager: model file does not exist at $modelPath")
        }

        Timber.d("EngineManager: initializing LiteRT-LM with backend=${target.backend} path=$modelPath (mmap, no copy)")
        liteRTLmEngine.init(
            modelPath = modelPath,
            backend = target.backend!!,
            visionBackend = BackendType.CPU,
            audioBackend = BackendType.CPU
        )
        activeEngine = target
        ioScope.launch {
            runCatching { cacheManager.touchAccess(modelPath) }
                .onFailure { Timber.w(it, "EngineManager: touchAccess failed") }
        }
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
     * - level >= 10 (TRIM_MEMORY_RUNNING_LOW): soft pre-evict to cap.
     * - level >= 15 (TRIM_MEMORY_RUNNING_CRITICAL): unload engine and evict
     *   the entire cache directory.
     */
    fun handleTrimMemory(level: Int) {
        if (level >= 15) {
            Timber.d("EngineManager: TRIM_MEMORY_RUNNING_CRITICAL — unloading engine and clearing cache")
            try {
                unloadCurrent()
                ioScope.launch {
                    runCatching { cacheManager.evictAll() }
                        .onFailure { Timber.w(it, "EngineManager: evictAll failed") }
                }
            } catch (e: Exception) {
                Timber.w(e, "EngineManager: error during trim memory (level=$level)")
            }
        } else if (level >= 10) {
            Timber.d("EngineManager: TRIM_MEMORY_RUNNING_LOW — soft cap pre-eviction")
            ioScope.launch {
                runCatching { cacheManager.ensureWithinCap() }
                    .onFailure { Timber.w(it, "EngineManager: ensureWithinCap failed") }
            }
        }
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
