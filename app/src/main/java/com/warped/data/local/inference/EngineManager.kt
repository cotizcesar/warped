package com.warped.data.local.inference

import android.content.Context
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import com.warped.data.repository.ModelAllowlistRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.withLock

/** Identifies which local inference engine type. */
enum class EngineType { LITE_RT_LM }

/** Which backend slot a native constraint error (or init resolution) names. */
enum class BackendSlot { MAIN, VISION, AUDIO }

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
    private val allowlist: ModelAllowlistRepository,
) {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeEngine: ActiveEngine? = null

    /**
     * 2026-10-04 async-load fix: the old method-level `@Synchronized`
     * held ONE monitor across the whole multi-second native init/close,
     * so every Main-thread [getActiveEngine] parked until the mount
     * finished — the full UI freeze on model load. Now split:
     * - [fieldLock] guards ONLY the [activeEngine] field (nanoseconds —
     *   Main-safe, this is all Main ever takes).
     * - [switchLock] serializes full unload→init sequences among
     *   BACKGROUND threads only (today's atomicity preserved: a switch
     *   can never interleave with another switch/close). Main never
     *   touches it.
     */
    private val fieldLock = Any()
    private val switchLock = java.util.concurrent.locks.ReentrantLock()

    /** Returns the currently active engine info, or null if nothing is loaded. */
    fun getActiveEngine(): ActiveEngine? = synchronized(fieldLock) { activeEngine }

    /**
     * Switch to the LiteRT-LM engine with the given model.
     * Unloads any currently loaded engine first, then initializes LiteRT-LM
     * by mmap-ing the source model file directly (no copy). The internal
     * cache directory used by EngineConfig is namespaced by the LiteRT-LM
     * version and capped via [LiteRtLmCacheManager].
     *
     * @param modelPath Absolute path to the .litertlm or .task model file
     *
     * Background-only contract (every call site is already off-main):
     * the full unload→init sequence runs under [switchLock]; Main
     * threads only ever take [fieldLock], so mounts never freeze the UI.
     */
    fun switchToLiteRT(modelPath: String) {
        switchLock.withLock {
            switchToLiteRTLocked(modelPath)
        }
    }

    /** [switchLock] held. Synchronous close + init; background only. */
    private fun switchToLiteRTLocked(modelPath: String) {
        cacheManager.cacheRoot
        val sourceFile = File(modelPath)
        val target = ActiveEngine(EngineType.LITE_RT_LM, modelPath, backendDetector.probeBackend())
        if (getActiveEngine() == target) {
            Timber.d("EngineManager: $target already loaded, skipping switch")
            return
        }
        closeNowLocked()

        if (!sourceFile.exists()) {
            error("EngineManager: model file does not exist at $modelPath")
        }

        Timber.d("EngineManager: initializing LiteRT-LM with backend=${target.backend} model=${modelPath.substringAfterLast("/")} (mmap, no copy)")
        try {
            initWith(target)
            synchronized(fieldLock) { activeEngine = target }
        } catch (e: Exception) {
            // GPU-constrained models (e.g. gemma-4-12B-it: "requires one of [gpu]")
            // fail on the probed CPU backend. The device probe can false-negative
            // (strict OpenCL+EGL AND), so the engine is ground truth: retry once
            // with the required backend before giving up.
            //
            // Slot-aware retry (2026-09-28): vision-capable .litertlm files carry
            // per-slot constraints ("Vision backend constraint mismatch ... requires
            // one of [gpu]"), so flip ONLY the named slot. Retrying the main
            // backend for a vision-slot error repeats the identical JNI failure.
            val required = parseRequiredBackend(e.message)
            if (required != null) {
                val slot = parseConstraintSlot(e.message) ?: BackendSlot.MAIN
                val current: BackendType? = when (slot) {
                    BackendSlot.MAIN -> target.backend
                    BackendSlot.VISION -> resolveVisionBackend(target)
                    BackendSlot.AUDIO -> resolveAudioBackend(target)
                }
                // Never retry when the required backend already equals the
                // current value of the named slot — it would fail identically.
                if (required != current) {
                    Timber.w(e, "EngineManager: $slot backend constraint mismatch, retrying with $required")
                    try {
                        when (slot) {
                            BackendSlot.MAIN -> {
                                val retryTarget = target.copy(backend = required)
                                initWith(retryTarget)
                                synchronized(fieldLock) { activeEngine = retryTarget }
                            }
                            BackendSlot.VISION -> {
                                initWith(target, visionOverride = required)
                                synchronized(fieldLock) { activeEngine = target }
                            }
                            BackendSlot.AUDIO -> {
                                initWith(target, audioOverride = required)
                                synchronized(fieldLock) { activeEngine = target }
                            }
                        }
                        return
                    } catch (retryEx: Exception) {
                        throw IllegalStateException(
                            "This model needs the ${required.name} backend, which failed on this device: ${retryEx.message}"
                        )
                    }
                }
            }
            throw e
        }
        ioScope.launch {
            runCatching { cacheManager.touchAccess(modelPath) }
                .onFailure { Timber.w(it, "EngineManager: touchAccess failed") }
        }
        Timber.d("EngineManager: LiteRT-LM engine now active")
    }

    /**
     * Unload the current engine (if any). Releases all native resources.
     * Safe to call even if nothing is loaded — and now safe from ANY
     * thread: the field flips synchronously (callers observe the unload
     * immediately) while the native close runs serialized with switches
     * on IO, so Main callers (memory trim, catalog/selector, chat exit)
     * never block on it.
     */
    fun unloadCurrent() {
        requestUnload()
    }

    /**
     * Schedule unload of the current engine. If the engine is generating text,
     * waits for generation to finish, then unloads. If not generating, unloads immediately.
     * Safe to call from any thread (same async-close contract as [unloadCurrent]).
     */
    fun scheduleUnload() {
        requestUnload()
    }

    /**
     * Grab-and-null under [fieldLock] (fast — Main-safe), then close the
     * grabbed engine on IO under [switchLock]. A pending close is DROPPED
     * when the field no longer holds what we grabbed: a concurrent switch
     * already closed it synchronously and owns the engine now, so closing
     * again would kill the fresh engine (or double-close the old one).
     */
    private fun requestUnload() {
        val grabbed: ActiveEngine? = synchronized(fieldLock) {
            val current = activeEngine
            activeEngine = null
            current
        } ?: return
        ioScope.launch {
            switchLock.withLock {
                val current = synchronized(fieldLock) { activeEngine }
                if (current == null) {
                    try {
                        liteRTLmEngine.close()
                    } catch (e: Exception) {
                        Timber.w(e, "EngineManager: error during unload of $grabbed")
                    }
                } else {
                    Timber.d("EngineManager: unload of $grabbed superseded by ${current.modelPath}, skipping close")
                }
            }
        }
    }

    /**
     * Synchronous close for use INSIDE [switchLock] only (the switch
     * sequence owns the old engine and must release it before init —
     * delegating to the async path would leak it: the pending close
     * would observe the fresh engine and stand down).
     */
    private fun closeNowLocked() {
        val current = synchronized(fieldLock) {
            val cur = activeEngine
            activeEngine = null
            cur
        } ?: return
        Timber.d("EngineManager: unloading current engine: $current")
        try {
            liteRTLmEngine.close()
        } catch (e: Exception) {
            Timber.w(e, "EngineManager: error during unload of $current")
        }
    }

    /** Single init attempt for a resolved target (no retry). */
    private fun initWith(
        target: ActiveEngine,
        visionOverride: BackendType? = null,
        audioOverride: BackendType? = null
    ) {
        // Speculative decoding is allowlist opt-in: the GPU flag demands a
        // TF_LITE_MTP_DRAFTER in the model file, and unlisted/unverified models
        // (e.g. gemma-4-12B-it) fail engine creation with it on.
        val entry = allowlist
            .findByModelFile(target.modelPath.substringAfterLast("/"))
        val specDecoding = entry?.capabilities?.speculativeDecoding == true
        liteRTLmEngine.init(
            modelPath = target.modelPath,
            backend = target.backend!!,
            visionBackend = visionOverride ?: resolveVisionBackend(target, entry?.capabilities?.vision),
            audioBackend = audioOverride ?: resolveAudioBackend(target),
            enableSpeculativeDecoding = specDecoding
        )
    }

    /**
     * Resolve the vision backend for an init attempt. Vision-capable models
     * (allowlist `capabilities.vision == true`, e.g. gemma-4-E2B-it) probe the
     * device vision backend (GPU when EGL is present); all other models get
     * null (vision slot unconfigured). Requesting any explicit vision backend
     * for a model without TF_LITE_VISION_ENCODER fails conversation creation
     * with NOT_FOUND (device log 2026-09-30, gemma-3-270m-it) — same
     * capability-gating precedent as audio and speculative decoding.
     */
    private fun resolveVisionBackend(
        target: ActiveEngine,
        visionCapable: Boolean? = allowlist
            .findByModelFile(target.modelPath.substringAfterLast("/"))
            ?.capabilities?.vision
    ): BackendType? =
        if (visionCapable == true) backendDetector.probeVisionBackend() else null

    /**
     * Resolve the audio backend for an init attempt. Audio-capable models
     * (allowlist `capabilities.audio == true`, e.g. gemma-4-E2B-it) probe the
     * device audio backend (CPU today, most compatible); all other models get
     * null (audio slot unconfigured). Requesting any explicit audio backend
     * for a model without TF_LITE_AUDIO_ENCODER_HW fails conversation
     * creation with NOT_FOUND (device log 2026-09-30, gemma-3-270m-it) —
     * same capability-gating precedent as vision and speculative decoding.
     */
    private fun resolveAudioBackend(target: ActiveEngine): BackendType? {
        val audioCapable = allowlist
            .findByModelFile(target.modelPath.substringAfterLast("/"))
            ?.capabilities?.audio
        return if (audioCapable == true) backendDetector.probeAudioBackend() else null
    }

    /**
     * Which backend slot a native constraint error names. Inspects the JNI
     * message prefix: "vision backend" → [VISION], "audio backend" → [AUDIO],
     * "main backend" → [MAIN]. Any other message carrying a
     * "requires one of [...]" constraint falls back to [MAIN] (preserves the
     * pre-existing main-only retry behavior). Null when the message carries
     * no constraint info. Pure function — unit-testable without the native engine.
     */
    fun parseConstraintSlot(message: String?): BackendSlot? {
        if (message == null) return null
        if (!message.contains("requires one of", ignoreCase = true)) return null
        return when {
            message.contains("vision backend", ignoreCase = true) -> BackendSlot.VISION
            message.contains("audio backend", ignoreCase = true) -> BackendSlot.AUDIO
            message.contains("main backend", ignoreCase = true) -> BackendSlot.MAIN
            else -> BackendSlot.MAIN
        }
    }

    /**
     * Parse a native backend-constraint error ("Model requires one of [gpu]" or
     * "Main backend constraint mismatch ... one of [gpu, cpu]") into the first
     * supported [BackendType]. Null when the message carries no constraint.
     * Pure function — unit-testable without the native engine.
     */
    fun parseRequiredBackend(message: String?): BackendType? {
        if (message == null) return null
        val match = Regex("""requires one of \[(.*?)\]""").find(message) ?: return null
        for (token in match.groupValues[1].split(",")) {
            when (token.trim().lowercase()) {
                "gpu" -> return BackendType.GPU
                "cpu" -> return BackendType.CPU
                "npu" -> return BackendType.NPU
            }
        }
        return null
    }

    /** Returns true if any engine is currently loaded. */
    @Synchronized
    /** True while an engine is mounted. Field-locked read — Main-safe (2026-10-04). */
    fun isEngineLoaded(): Boolean = getActiveEngine() != null

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
     *
     * Quick-task (thinking-config): optional [thinkingConfig] enables the
     * 0.17.x reasoning channel for capable models when the Thinking toggle
     * is on (the caller ANDs toggle + capability — null preserves engine
     * defaults for toggle-off or incapable models).
     *
     * @throws IllegalStateException if LiteRT-LM is not the active engine
     */
    @Synchronized
    fun createLiteRTConversation(
        config: ConversationConfig = ConversationConfig(),
        thinkingConfig: ThinkingConfig? = null,
        maxOutputToken: Int? = null,
    ) = liteRTLmEngine.createConversation(config, thinkingConfig, maxOutputToken)

    /** Returns the LiteRT-LM engine directly for advanced usage. */
    fun getLiteRTLmEngine(): LiteRTLmEngine = liteRTLmEngine
}
