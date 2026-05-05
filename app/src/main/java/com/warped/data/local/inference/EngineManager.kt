package com.warped.data.local.inference

import android.content.Context
import com.google.ai.edge.litertlm.ConversationConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
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
            visionBackend = backendDetector.probeVisionBackend(),
            audioBackend = backendDetector.probeAudioBackend()
        )
        activeEngine = target
        Timber.d("EngineManager: LiteRT-LM engine now active")
    }

    /**
     * Switch to the llama.cpp engine with the given model.
     * Unloads any currently loaded engine first, then loads llama.cpp.
     * Validates the file and provides loading progress callbacks.
     *
     * @param modelPath Absolute path to the GGUF model file
     * @param onProgress Optional callback for loading progress (percent, message)
     * @return Result with GgufMetadata on success, LlamaLoadError on failure
     */
    @Synchronized
    fun switchToLlama(
        modelPath: String,
        onProgress: ((Int, String) -> Unit)? = null
    ): Result<GgufMetadata> {
        // Pre-validate GGUF header before attempting native load
        val validationResult = GgufMetadataParser.validateHeader(File(modelPath))
        if (validationResult.isFailure) {
            Timber.e("EngineManager: GGUF validation failed — $modelPath")
            return Result.failure(LlamaLoadError.CorruptedFile())
        }

        val target = ActiveEngine(EngineType.LLAMA_CPP, modelPath)
        if (activeEngine == target) {
            Timber.d("EngineManager: $target already loaded, skipping switch")
            val metadata = try {
                GgufMetadataParser.parse(File(modelPath)).getOrThrow()
            } catch (e: Exception) {
                GgufMetadata()
            }
            return Result.success(metadata)
        }
        unloadCurrent()

        Timber.d("EngineManager: loading llama.cpp model: $modelPath")
        val loadResult = llamaEngine.loadModel(
            path = modelPath,
            onProgress = onProgress
        )

        if (loadResult.isFailure) {
            val error = loadResult.exceptionOrNull() as? LlamaLoadError ?: LlamaLoadError.Unknown("Unknown error")
            Timber.e(error, "EngineManager: llama.cpp failed to load model")
            return Result.failure(error)
        }

        activeEngine = target
        Timber.d("EngineManager: llama.cpp engine now active")

        // Parse metadata after successful load
        val metadata = try {
            GgufMetadataParser.parse(File(modelPath)).getOrThrow()
        } catch (e: Exception) {
            Timber.w(e, "EngineManager: metadata parse warning, using native info")
            GgufMetadata()
        }

        return Result.success(metadata)
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

    /** Returns the llama.cpp engine directly for advanced usage. */
    fun getLlamaEngine(): LlamaEngine = llamaEngine
}
