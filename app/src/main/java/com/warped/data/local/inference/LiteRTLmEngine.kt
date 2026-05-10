package com.warped.data.local.inference

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiteRTLmEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        init {
            try {
                System.loadLibrary("litertlm_jni")
            } catch (e: UnsatisfiedLinkError) { Timber.e(e, "LiteRTLmEngine: native lib not found") }
            Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
        }
    }

    @Volatile
    private var engine: Engine? = null

    @Volatile
    private var loadedModelPath: String? = null

    /** Returns true if the engine is initialized and ready. */
    @Synchronized
    fun isInitialized(): Boolean = engine?.isInitialized() == true

    /**
     * Initialize the LiteRT-LM engine with a .litertlm model and backends.
     * This is a blocking call (can take seconds) — caller must dispatch on Dispatchers.Default.
     *
     * @param modelPath Absolute path to the .litertlm model file
     * @param backend   Main backend (CPU or GPU)
     * @param visionBackend Backend for vision processing, or null to use main backend
     * @param audioBackend  Backend for audio processing, or null to use main backend
     * @throws IllegalStateException if engine is already initialized
     */
    @Synchronized
    fun init(
        modelPath: String,
        backend: BackendType,
        visionBackend: BackendType? = null,
        audioBackend: BackendType? = null
    ) {
        require(!isInitialized()) { "LiteRTLmEngine is already initialized. Call close() first." }

        val litertlmBackend = when (backend) {
            BackendType.CPU -> Backend.CPU()
            BackendType.GPU -> Backend.GPU()
            BackendType.NPU -> Backend.GPU() // fallback: NPU not yet supported by EngineConfig
        }

        val cacheDir = java.io.File(context.cacheDir, "litertlm_cache").also { it.mkdirs() }

        val config = EngineConfig(
            modelPath = modelPath,
            backend = litertlmBackend,
            cacheDir = cacheDir.absolutePath
        )

        engine = Engine(config).also { e ->
            Timber.d("LiteRTLmEngine: initializing backend=$backend vision=$visionBackend audio=$audioBackend cache=${cacheDir.absolutePath}")
            e.initialize()
            Timber.d("LiteRTLmEngine: initialization complete")
        }
        loadedModelPath = modelPath
    }

    /**
     * Create a new conversation session from the initialized engine.
     * @throws IllegalStateException if engine is not initialized
     */
    @Synchronized
    fun createConversation(config: ConversationConfig = ConversationConfig()): Conversation {
        val e = engine ?: error("LiteRTLmEngine is not initialized. Call init() first.")
        return e.createConversation(config)
    }

    /**
     * Close the engine and release all native resources.
     * Safe to call even if not initialized (no-op).
     */
    @Synchronized
    fun close() {
        try {
            engine?.close()
            Timber.d("LiteRTLmEngine: closed successfully")
        } catch (e: Exception) {
            Timber.w(e, "LiteRTLmEngine: error during close")
        } finally {
            engine = null
            loadedModelPath = null
        }
    }

    /** Returns the path of the currently loaded model, or null. */
    fun getModelPath(): String? = loadedModelPath
}
