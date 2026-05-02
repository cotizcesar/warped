package com.warped.data.local.inference

import com.google.ai.edge.litertlm.ConversationConfig
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Identifies which local inference engine type. */
enum class EngineType { LLAMA_CPP, LITE_RT_LM }

/** Tracks which engine (if any) is currently loaded and which model. */
data class ActiveEngine(
    val type: EngineType,
    val modelPath: String,
    val backend: BackendType? = null  // null for llama.cpp (no backend concept)
)

@Singleton
class EngineManager @Inject constructor(
    private val llamaEngine: LlamaEngine,
    private val liteRTLmEngine: LiteRTLmEngine,
    private val backendDetector: BackendDetector
) {
    private var activeEngine: ActiveEngine? = null

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
        val target = ActiveEngine(EngineType.LITE_RT_LM, modelPath, backendDetector.probeBackend())
        if (activeEngine == target) {
            Timber.d("EngineManager: $target already loaded, skipping switch")
            return
        }
        unloadCurrent()

        Timber.d("EngineManager: initializing LiteRT-LM with backend=${target.backend}")
        liteRTLmEngine.init(modelPath, target.backend!!)
        activeEngine = target
        Timber.d("EngineManager: LiteRT-LM engine now active")
    }

    /**
     * Switch to the llama.cpp engine with the given model.
     * Unloads any currently loaded engine first, then loads llama.cpp.
     * Actual model loading is handled by LlamaEngine.loadModel() — this just coordinates.
     *
     * @param modelPath Absolute path to the GGUF model file
     */
    @Synchronized
    fun switchToLlama(modelPath: String) {
        val target = ActiveEngine(EngineType.LLAMA_CPP, modelPath)
        if (activeEngine == target) {
            Timber.d("EngineManager: $target already loaded, skipping switch")
            return
        }
        unloadCurrent()

        Timber.d("EngineManager: loading llama.cpp model")
        val loaded = llamaEngine.loadModel(modelPath)
        if (!loaded) {
            Timber.e("EngineManager: llama.cpp failed to load model")
            throw IllegalStateException("Failed to load llama.cpp model: $modelPath")
        }
        activeEngine = target
        Timber.d("EngineManager: llama.cpp engine now active")
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
            when (current.type) {
                EngineType.LLAMA_CPP -> {
                    llamaEngine.stop()
                    llamaEngine.unload()
                }
                EngineType.LITE_RT_LM -> {
                    liteRTLmEngine.close()
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "EngineManager: error during unload of $current")
        } finally {
            activeEngine = null
        }
    }

    /** Returns true if any engine is currently loaded. */
    @Synchronized
    fun isEngineLoaded(): Boolean = activeEngine != null

    /**
     * Create a LiteRT-LM conversation. Convenience method that delegates to the engine.
     * @throws IllegalStateException if LiteRT-LM is not the active engine
     */
    @Synchronized
    fun createLiteRTConversation(config: ConversationConfig = ConversationConfig()) =
        liteRTLmEngine.createConversation(config)

    /** Returns the LiteRT-LM engine directly for advanced usage. */
    fun getLiteRTLmEngine(): LiteRTLmEngine = liteRTLmEngine

    /** Returns the llama.cpp engine directly for advanced usage. */
    fun getLlamaEngine(): LlamaEngine = llamaEngine
}
