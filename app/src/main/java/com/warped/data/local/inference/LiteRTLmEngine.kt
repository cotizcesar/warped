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
        /** Set once the native library has been loaded (lazy — see [ensureNativeLoaded]). */
        @Volatile
        private var nativeLoaded = false

        /**
         * Load the native library on first real use, never at class-load.
         *
         * PERF-16: this used to live in a `companion object init {}` block, which
         * ran `System.loadLibrary` the moment Hilt constructed the engine graph
         * node at Application creation (WarpedApplication eagerly injects
         * EngineManager). Class construction is now pure-Java (context ref only);
         * the dlopen happens here, on the first [init] call — i.e. first model
         * load, never on the cold-start path.
         */
        @Synchronized
        private fun ensureNativeLoaded() {
            if (nativeLoaded) return
            try {
                System.loadLibrary("litertlm_jni")
                Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
                nativeLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                Timber.e(e, "LiteRTLmEngine: native lib not found")
                throw IllegalStateException("Native litertlm_jni library unavailable", e)
            }
        }
    }

    @Volatile
    private var engine: Engine? = null

    @Volatile
    private var loadedModelPath: String? = null

    /**
     * Sessions handed out by [createConversation], tracked so [close] can release
     * them before destroying the engine. The native layer errors
     * ("EngineAdvancedImpl destructed with N living sessions", "Execution manager
     * is not available") when the engine dies with live sessions — and a stale
     * session handle is the same family as the 0.12.0 SIGSEGV (see
     * .planning/debug/crash-2nd-msg-reasoning.md). Guarded by the same monitor
     * as createConversation/close (both @Synchronized); stale entries are harmless
     * (skipped via isAlive, set cleared on every close).
     */
    private val openSessions = mutableSetOf<Conversation>()

    /** Returns true if the engine is initialized and ready. */
    @Synchronized
    fun isInitialized(): Boolean = engine?.isInitialized() == true

    /**
     * Initialize the LiteRT-LM engine with a .litertlm model and backends.
     * This is a blocking call (can take seconds) — caller must dispatch on Dispatchers.Default.
     *
     * @param modelPath Absolute path to the .litertlm or .task model file
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
        audioBackend: BackendType? = null,
        enableSpeculativeDecoding: Boolean = true
    ) {
        require(!isInitialized()) { "LiteRTLmEngine is already initialized. Call close() first." }

        ensureNativeLoaded()

        val litertlmBackend = when (backend) {
            BackendType.CPU -> Backend.CPU()
            BackendType.GPU -> {
                // Speculative decoding demands a TF_LITE_MTP_DRAFTER in the model
                // file — GPU-only models without one (e.g. gemma-4-12B-it) fail
                // engine creation when the flag is forced on. Caller (EngineManager)
                // passes the allowlist-verified value; default preserves legacy.
                @OptIn(ExperimentalApi::class)
                ExperimentalFlags.enableSpeculativeDecoding = enableSpeculativeDecoding
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
        ).also {
            if (!it.exists() && !it.mkdirs()) throw java.io.IOException("Cannot create cache dir: $it")
        }

        val config = EngineConfig(
            modelPath = modelPath,
            backend = litertlmBackend,
            visionBackend = visionBackend?.let {
                when (it) {
                    BackendType.GPU -> Backend.GPU()
                    BackendType.NPU -> Backend.NPU(
                        nativeLibraryDir = context.applicationInfo.nativeLibraryDir
                    )
                    else -> Backend.CPU()
                }
            },
            audioBackend = audioBackend?.let {
                when (it) {
                    BackendType.GPU -> Backend.GPU()
                    BackendType.NPU -> Backend.NPU(
                        nativeLibraryDir = context.applicationInfo.nativeLibraryDir
                    )
                    else -> Backend.CPU()
                }
            },
            cacheDir = cacheDir.absolutePath
        )

        val created = Engine(config)
        Timber.d("LiteRTLmEngine: initializing backend=$backend vision=$visionBackend audio=$audioBackend cache=${cacheDir.absolutePath}")
        try {
            created.initialize()
            Timber.d("LiteRTLmEngine: initialization complete")
        } catch (jniEx: LiteRtLmJniException) {
            Timber.e(jniEx, "LiteRTLmEngine: JNI init failed — ${jniEx.message}")
            try { created.close() } catch (_: Exception) { /* best effort: release native handle */ }
            throw jniEx
        } catch (ex: Exception) {
            Timber.e(ex, "LiteRTLmEngine: init failed — ${ex.message}")
            try { created.close() } catch (_: Exception) { /* best effort: release native handle */ }
            throw ex
        }
        engine = created
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
        val conversation = if (thinkingConfig == null && maxOutputToken == null) {
            e.createConversation(config)
        } else {
            e.createConversation(
                config.copy(thinkingConfig = thinkingConfig, maxOutputToken = maxOutputToken)
            )
        }
        openSessions.add(conversation)
        return conversation
    }

    /**
     * Close the engine and release all native resources.
     * Safe to call even if not initialized (no-op).
     */
    @Synchronized
    fun close() {
        // Release live sessions BEFORE destroying the engine — otherwise the native
        // layer logs "destructed with N living sessions" and later session calls fail
        // with "Execution manager is not available" (device log 2026-09-27).
        val sessions = openSessions.toList()
        openSessions.clear()
        for (session in sessions) {
            try {
                if (session.isAlive) session.close()
            } catch (e: Exception) {
                Timber.w(e, "LiteRTLmEngine: error closing conversation during engine close")
            }
        }
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
