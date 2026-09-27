package com.warped.data.local.inference

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.LiteRtLmJniException
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.ThinkingConfig
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
            BackendType.GPU -> {
                @OptIn(ExperimentalApi::class)
                ExperimentalFlags.enableSpeculativeDecoding = true
                Backend.GPU()
            }
            // 45-02 LRT-09 (0.17.x re-verification): Backend.NPU(nativeLibraryDir) is a
            // real 0.17.x option (verified in litertlm-android-0.17.1 AAR bytecode), so the
            // old NPU->GPU fallback is replaced with a proper NPU backend. CHOICE RECORDED:
            // adopt-proper-NPU rather than keep-fallback, because the API exists and the
            // manifest keeps libcdsprpc.so (Hexagon DSP RPC) for this path. Dead path today
            // (BackendDetector.probeBackend() only ever emits CPU/GPU), so no behavior
            // change until NPU probing lands; libcdsprpc.so stays required=false.
            BackendType.NPU -> Backend.NPU(
                nativeLibraryDir = context.applicationInfo.nativeLibraryDir
            )
        }

        val cacheDir = java.io.File(
            context.cacheDir,
            LiteRtLmCache.namespaceFor(com.warped.BuildConfig.LITERTLM_VERSION)
        ).also { it.mkdirs() }

        val config = EngineConfig(
            modelPath = modelPath,
            backend = litertlmBackend,
            visionBackend = visionBackend?.let {
                when (it) { BackendType.GPU -> Backend.GPU(); else -> Backend.CPU() }
            },
            audioBackend = audioBackend?.let {
                when (it) { BackendType.GPU -> Backend.GPU(); else -> Backend.CPU() }
            },
            cacheDir = cacheDir.absolutePath
        )

        engine = Engine(config).also { e ->
            Timber.d("LiteRTLmEngine: initializing backend=$backend vision=$visionBackend audio=$audioBackend cache=${cacheDir.absolutePath}")
            try {
                e.initialize()
                Timber.d("LiteRTLmEngine: initialization complete")
            } catch (jniEx: LiteRtLmJniException) {
                Timber.e(jniEx, "LiteRTLmEngine: JNI init failed — ${jniEx.message}")
                throw jniEx
            } catch (ex: Exception) {
                Timber.e(ex, "LiteRTLmEngine: init failed — ${ex.message}")
                throw ex
            }
        }
        loadedModelPath = modelPath
    }

    /**
     * Create a new conversation session from the initialized engine.
     *
     * 45-02 LRT-09 (0.17.x re-verification): optional [thinkingConfig] and [maxOutputToken]
     * surface the 0.17.x `ConversationConfig(thinkingConfig, maxOutputToken)` delta
     * (verified in litertlm-android-0.17.1 AAR bytecode). Both default to null, which
     * preserves the pre-0.17 behavior (engine defaults apply). Tool wiring
     * (`tools`/`automaticToolCalling`) stays Phase-47 owned — this overload only exposes
     * thinking/output-length, it does not attach any ToolSet.
     *
     * @throws IllegalStateException if engine is not initialized
     */
    @Synchronized
    fun createConversation(
        config: ConversationConfig = ConversationConfig(),
        thinkingConfig: ThinkingConfig? = null,
        maxOutputToken: Int? = null
    ): Conversation {
        val e = engine ?: error("LiteRTLmEngine is not initialized. Call init() first.")
        if (thinkingConfig == null && maxOutputToken == null) return e.createConversation(config)
        return e.createConversation(
            config.copy(thinkingConfig = thinkingConfig, maxOutputToken = maxOutputToken)
        )
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
        } catch (e: LiteRtLmJniException) {
            Timber.w(e, "LiteRTLmEngine: JNI error during close")
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
