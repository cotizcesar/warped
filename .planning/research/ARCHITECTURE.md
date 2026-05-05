# Architecture Research: GGUF Native Inference Integration

**Domain:** Android local LLM inference via llama.cpp JNI/NDK
**Researched:** 2026-05-05
**Confidence:** HIGH

## Executive Summary

The Warped app's existing Clean Architecture (UI → Domain → Data) already has stubs and integration points for GGUF/llama.cpp: `LlamaEngine` wraps a JNI bridge to native C++, `EngineManager` coordinates engine lifecycle, `LocalLlmProvider` implements `LlmProvider` for the chat pipeline, `ProviderRouter` dispatches `ProviderType.LOCAL` to llama.cpp, and `ModelDownloadWorker`/`ModelImportManager` parse GGUF metadata on download/import. The architecture is sound and the integration points are correctly placed — what's missing is the real native implementation (llama.cpp compiled from source) and production-hardening of memory management, Vulkan probing, token streaming, and thread safety.

This document describes the NEW patterns needed for the GGUF pipeline and how they integrate with existing code.

## Integration Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────────┐
│  EXISTING (unchanged)                                                     │
│  ┌─────────────────┐    ┌──────────────────┐    ┌──────────────────┐     │
│  │ ChatViewModel    │    │ ProviderRouter    │    │ EngineManager     │     │
│  │ preloadLocalModel│───▶│ resolveLocal(     │───▶│ switchToLlama()   │     │
│  │ sendMessage()    │    │ LOCAL, modelId)   │    │ getLlamaEngine()  │     │
│  └─────────────────┘    └──────────────────┘    └────────┬─────────┘     │
│                                                          │               │
│  MODIFIED (production-hardened)                          │               │
│  ┌─────────────────┐    ┌──────────────────┐    ┌───────▼──────────┐    │
│  │ LocalLlmProvider │───▶│ LlamaEngine       │    │ BackendDetector  │    │
│  │ (chat streaming) │    │ (JNI wrapper)     │    │ (+ Vulkan probe) │    │
│  └─────────────────┘    └────────┬─────────┘    └──────────────────┘    │
│                                  │                                       │
│  NEW (native layer)              │                                       │
│                         ┌───────▼──────────────────────────┐            │
│                         │ libwarped_llama.so (NDK)          │            │
│                         │  ├─ jni_bridge.cpp (JNI glue)    │            │
│                         │  ├─ LlamaEngine (C++ singleton)  │            │
│                         │  ├─ llama.cpp (compiled from src)│            │
│                         │  ├─ GGML Vulkan backend (.so)    │            │
│                         │  └─ GGML CPU backend (always)    │            │
│                         └──────────────────────────────────┘            │
└──────────────────────────────────────────────────────────────────────────┘
```

## Component Boundaries

### 1. JNI Bridge Layer — `jni_bridge.cpp` / `LlamaEngine.kt`

**What exists:**
- `jni_bridge.cpp` has stub implementations (`loadModel` returns `true` always, `generate` emits placeholder text)
- `LlamaEngine.kt` has correct JNI declarations, `callbackFlow` wrapping native callbacks
- `CMakeLists.txt` compiles only `jni_bridge.cpp` — no llama.cpp source linked yet
- Declares compile definitions `GGML_USE_CPU=1`, `GGML_USE_CPU_AARCH64=1` but doesn't use them

**What must change:**

| File | Change | Why |
|------|--------|-----|
| `CMakeLists.txt` | Add llama.cpp source files, GGML subdirectories, Vulkan backend | Must compile real inference engine |
| `jni_bridge.cpp` | Implement real `loadModel`, `generate`, `stop`, `unload` using llama.cpp API | Core inference loop |
| `jni_bridge.h` | Add Vulkan backend params, model metadata struct | Need GPU backend and metadata |
| `LlamaEngine.kt` | Add `loadModel(path, backend, nGpuLayers)`, add `nativeGetVulkanInfo()`, add `nativeGetContextSize()` | Vulkan backend selection, metadata extraction |
| `LlamaEngine.kt` | Add `generate(prompt, params: GenerationParameters)` — thread count, temp, topK, topP, seed | Per-request parameter pass-through |

**Key JNI design decisions:**

```kotlin
// LlamaEngine.kt — NEW API surface
class LlamaEngine @Inject constructor() {
    // EXISTING — unchanged
    fun generate(prompt: String): Flow<String>

    // MODIFIED — new params
    fun loadModel(
        path: String,
        nThreads: Int = 4,
        nCtx: Int = 4096,
        nGpuLayers: Int = 0,  // NEW: 0=CPU-only, >0=offload layers to GPU
        useVulkan: Boolean = false  // NEW: Vulkan backend toggle
    ): Boolean

    // NEW
    fun generate(prompt: String, params: GenerationParameters): Flow<String>
    fun getVulkanInfo(): VulkanInfo  // adapter name, VRAM, compute queue count
    fun getContextSize(): Int
    fun getModelArchitecture(): String  // from GGUF header via native
}
```

**The `callbackFlow` pattern (already correct, needs hardening):**

```kotlin
// The callback is invoked from the JNI thread — NOT the Kotlin coroutine thread.
// callbackFlow handles this correctly: trySend() is thread-safe and buffered.
fun generate(prompt: String): Flow<String> = callbackFlow {
    val callback = object : TokenCallback {
        override fun onToken(token: String, done: Boolean) {
            if (done) close()
            else if (token.isNotEmpty()) trySend(token)
        }
    }
    nativeGenerate(prompt, callback)
    awaitClose { nativeStop() }
}.flowOn(Dispatchers.Default)  // CPU-bound inference moves off Main
```

**Critical JNI concern: Global Reference lifecycle**

The JNI `nativeGenerate` receives a `jobject callback` parameter. This is a **local reference** valid only for the duration of the JNI call. Since `nativeGenerate` is blocking (runs the entire generation loop), the local reference outlives the method scope — this is technically valid but fragile. **Mitigation:**
```cpp
// In nativeGenerate JNI function
jobject globalCallback = env->NewGlobalRef(callback);
// ... generate loop uses globalCallback ...
env->DeleteGlobalRef(globalCallback);
```

The current stub doesn't convert to global ref — the real implementation must. This is the #1 cause of JNI crashes ("JNI local reference table overflow" or "use of deleted local reference").

### 2. EngineManager Integration

**What exists:**
- `switchToLlama(modelPath)` calls `llamaEngine.loadModel(modelPath)` synchronously
- `unloadCurrent()` calls `llamaEngine.stop()` then `llamaEngine.unload()`
- `@Synchronized` on all public methods for mutual exclusion
- `ActiveEngine` tracks `type`, `modelPath`, `backend`

**What must change:**

```kotlin
// EngineManager.kt — MODIFIED switchToLlama
@Synchronized
fun switchToLlama(modelPath: String, nGpuLayers: Int = 0) {
    val backend = backendDetector.probeBackend()  // now includes Vulkan
    val useVulkan = backend == BackendType.GPU
    val target = ActiveEngine(
        type = EngineType.LLAMA_CPP,
        modelPath = modelPath,
        backend = backend  // NEW: was null, now populated
    )
    if (activeEngine == target) return
    unloadCurrent()

    // Load is a blocking call — caller (ChatViewModel) dispatches on Dispatchers.Default
    val loaded = llamaEngine.loadModel(
        path = modelPath,
        nThreads = Runtime.getRuntime().availableProcessors(),
        nCtx = 4096,
        nGpuLayers = if (useVulkan) nGpuLayers else 0,
        useVulkan = useVulkan
    )
    if (!loaded) throw IllegalStateException("Failed to load GGUF model: $modelPath")
    activeEngine = target
}
```

**Lifecycle state machine:**
```
UNLOADED ──loadModel()──▶ LOADED ──generate()──▶ GENERATING
   ▲                         │                       │
   │                         │                       │ stop()
   │                         ◀───────────────────────┘
   │                         │
   └───unload()◀────────────┘
         (or process death)
```

**Key constraint:** Only one engine loaded at a time (unchanged). EngineManager enforces this via `@Synchronized`. The native C++ `LlamaEngine` singleton also enforces it — `loadModel` unloads previous model first. This dual-layer enforcement prevents accidental double-load.

### 3. Memory Management Across Layers

**The critical numbers:**
| Model Size | GGUF File | RAM Needed (inference) | Example |
|-----------|-----------|----------------------|---------|
| 1B Q4_K_M | ~600 MB | ~1.2 GB | TinyLlama |
| 3B Q4_K_M | ~2 GB | ~3.5 GB | Phi-3-mini |
| 7B Q4_K_M | ~4.5 GB | ~6 GB | Mistral-7B, Llama-3.2-3B |
| 8B Q4_K_M | ~5 GB | ~7 GB | Llama-3.1-8B |
| 13B Q4_K_M | ~8 GB | ~10 GB | (top-end flagships only) |

**RAM needed ≈ GGUF file size × 1.3** (model weights + KV cache + context buffers). A 4.5 GB GGUF needs ~6 GB free RAM.

**What exists:**
- `MemoryChecker` with `canLoadModel()`, `shouldWarn()`, `getMemoryInfo()`
- 80% available RAM threshold for `canLoadModel`
- 60% threshold for warning
- `ChatViewModel.preloadLocalModel()` checks `canLoadModel()` before loading
- `ChatViewModel.launchModelSelection()` shows warning dialog via `shouldWarn()`

**What must change:**

1. **Tighter memory check in `preloadLocalModel`** — currently checks `canLoadModel` but doesn't use `ActivityManager.MemoryInfo` for the actual memory state. Must switch to `ActivityManager.getMemoryInfo()` which reflects the *current process* memory situation, not just the system-wide available RAM.

2. **OOM recovery** — `llamaEngine.loadModel()` can throw `OutOfMemoryError` for large models on constrained devices. Must catch in `preloadLocalModel()` and surface a user-friendly error ("Model needs X MB but only Y MB available" — already partially done).

3. **`android:largeHeap="true"`** — Not yet in AndroidManifest. Required for models >256MB. Without it, Android enforces a lower heap limit (~256-512MB depending on device).

4. **No artificial size limit** — Per PROJECT.md requirements, must not hard-cap model size. Some flagship phones (16GB RAM) can run 13B models. Let the memory check (80% of available) be the only gate.

```kotlin
// ChatViewModel.kt — MODIFIED preloadLocalModel
private suspend fun preloadLocalModel(filePath: String) {
    val model = _uiState.value.localModels.firstOrNull { it.filePath == filePath }
    if (model != null) {
        // NEW: Check ActivityManager actual process memory, not just system-wide
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val modelSizeBytes = model.sizeBytes
        val estimatedRamNeeded = (modelSizeBytes * 1.3).toLong()  // 30% overhead for KV cache + context
        val availableForProcess = memInfo.availMem

        if (estimatedRamNeeded > availableForProcess * 0.8) {
            val modelMB = modelSizeBytes / (1024 * 1024)
            val availMB = availableForProcess / (1024 * 1024)
            _uiState.update {
                it.copy(modelLoadError = "Not enough memory: model needs ~${modelMB} MB RAM but only ${availMB} MB available.")
            }
            return
        }
    }

    // ... existing load logic (unchanged) ...
    try {
        withContext(Dispatchers.Default) {
            if (isLitertlm) {
                engineManager.switchToLiteRT(filePath)
            } else {
                val loaded = llamaEngine.loadModel(filePath, ...)
                if (!loaded) throw IllegalStateException("Failed to load GGUF model")
            }
        }
    } catch (e: OutOfMemoryError) {
        // NEW: explicit OOM handling
        _uiState.update {
            it.copy(modelLoadError = "Out of memory. Close other apps or use a smaller quantization.")
        }
    } catch (e: Exception) {
        _uiState.update { it.copy(modelLoadError = e.message) }
    }
}
```

**Memory cleanup lifecycle:**
```
EngineManager.handleTrimMemory(level)
  level >= TRIM_MEMORY_RUNNING_CRITICAL
    → unloadCurrent()  (calls llamaEngine.unload())
    → clear LiteRT-LM cache dir
```

Already implemented correctly. No changes needed for GGUF path.

### 4. GGUF Metadata Extraction (Header Parsing)

**What exists:**
- `GgufMetadataParser` — reads GGUF header (magic "GGUF", version, tensor count, KV count, then iterates KV pairs)
- Parses: `general.name`, `general.architecture`, `general.file_type` → quantization, `*.block_count` → param count, `llama.context_length`
- `mapQuantization()` maps integer file_type to human-readable name (Q4_K_M, Q8_0, etc.)
- Used in: `ModelDownloadWorker` (post-download), `ModelImportManager` (post-import)

**What must change:**

1. **Complete the quantization map** — Current map is missing many GGUF v3 quantizations: Q4_K_M (15), IQ quantizations (IQ2_XXS=26, IQ2_XS=27, IQ3_XXS=28, IQ3_S=29, IQ4_XS=31, IQ4_NL=33), and newer types. The `file_type` mapping has changed across GGUF versions — the parser must handle GGUF v2 and v3.

2. **Pass metadata through the load pipeline** — Currently `LlamaEngine.getModelInfo()` returns a stub JSON string. The JNI layer should extract metadata from the loaded model's GGUF header (or from `llama_model_desc()` / `llama_model_meta()` which is more reliable than raw header parsing). This gives: actual context size, architecture string, quantization used, model size in parameters.

3. **Use metadata at download time for the ModelsScreen** — When a GGUF file downloads, `ModelDownloadWorker` parses metadata via `GgufMetadataParser` and saves it to `LocalModel` in Room. The ModelsScreen already displays `quantization`, `parameterCount`, `architecture` from Room — this works end-to-end already. Validation point: ensure the parser handles all GGUF variants (v1, v2, v3) correctly.

4. **Native metadata extraction (NEW)** — After model is loaded into llama.cpp, extract richer metadata:

```cpp
// jni_bridge.cpp — NEW native functions
JNIEXPORT jstring JNICALL Java_..._nativeGetModelArchitecture(JNIEnv* env, jobject) {
    auto& engine = LlamaEngine::getInstance();
    if (!engine.isLoaded()) return env->NewStringUTF("");
    // llama.cpp API (version-dependent):
    // const char* arch = llama_model_desc(engine.getModel());
    // or from metadata: llama_model_meta_val_str(model, "general.architecture", buf, sizeof(buf));
    return env->NewStringUTF(engine.getArchitecture().c_str());
}

JNIEXPORT jint JNICALL Java_..._nativeGetContextSize(JNIEnv* env, jobject) {
    auto& engine = LlamaEngine::getInstance();
    if (!engine.isLoaded()) return 0;
    // return llama_n_ctx(engine.getContext());
    return engine.getContextSize();
}
```

### 5. Download-to-Load Pipeline

**Current flow (already correct, needs file listing fix):**

```
User searches HF → HuggingFaceScreen
  → huggingFaceRepository.searchModels(query, library="gguf")
  → HuggingFaceApi GET /api/models?search=...&library=gguf&sort=downloads
  → Returns List<HuggingFaceModel>

User selects model → selectModel(model)
  → huggingFaceRepository.getModelDetail(modelId)
  → HuggingFaceApi GET /api/models/{modelId}
  → Returns HuggingFaceModelDetail with siblings list
  → HuggingFaceViewModel filters siblings to .gguf files only
  → Shows sorted file list (by size)

User downloads → downloadFile(modelId, fileName, fileSize)
  → ModelDownloadManager.startDownload(downloadId, fileName, fileUrl, fileSizeBytes)
  → WorkManager enqueues ModelDownloadWorker

ModelDownloadWorker.doWork():
  1. Restore checkpoint from Room (DownloadCheckpointDao)
  2. OkHttp GET with Range header for resume
  3. Write to context.filesDir/models/{filename}
  4. Foreground notification with progress
  5. On completion: GgufMetadataParser.parse(destFile)
     → Save LocalModel to Room (name, filePath, sizeBytes, quantization, paramCount, architecture)
  6. Delete checkpoint on success
```

**GGUF file listing gap (already working but subtle):**

The `HuggingFaceApi.searchModels()` passes `library=gguf` which filters to GGUF-tagged models at the API level. The `HuggingFaceViewModel.selectModel()` filters siblings by `.gguf` extension. This is correct. The issue mentioned in PROJECT.md ("la búsqueda en Hugging Face lista modelos pero no muestra archivos .gguf") may be an API version mismatch — some HF API responses embed `siblings` in the search result (not just the detail endpoint). The DTO already has `HuggingFaceModel.siblings` field, so search results should carry sibling data. If not, a separate detail call per search result may be needed.

**Validation pipeline (NEW — should add):**

After download, before saving as a usable model, validate:
1. File exists and size > 0
2. `GgufMetadataParser.parse()` succeeds (not corrupt)
3. Magic bytes = "GGUF"
4. Architecture is a known type (llama, falcon, mistral, phi, etc.)
5. Optional: test-load a minimal context (load model, create tiny context, run 1 token) — defer

If validation fails, surface error to user, don't save invalid model to Room, delete corrupt file.

### 6. Vulkan Backend Probing and Selection

**What exists:**
- `BackendDetector` probes `EGL14` (GPU presence) and `System.loadLibrary("OpenCL")` (OpenCL availability)
- `probeBackend()` returns `BackendType.CPU` or `BackendType.GPU`
- Result cached `@Volatile` for process lifetime
- Used by `EngineManager.switchToLiteRT()` for LiteRT-LM backend selection
- Not currently used for llama.cpp path (llama.cpp `ActiveEngine.backend` was always `null`)

**What must change for Vulkan:**

llama.cpp's GPU backend on Android uses **Vulkan** (not OpenCL, not OpenGL/EGL). Vulkan provides universal GPU compute on Android 7+ (API 24+) across all GPU vendors (Adreno, Mali, PowerVR, Xclipse). OpenCL is deprecated on Android and unreliable across vendors — many modern devices don't ship `libOpenCL.so` at all.

**New Vulkan probing:**

```kotlin
// BackendDetector.kt — MODIFIED
@Synchronized
fun probeBackend(): BackendType {
    cachedBackend?.let { return it }

    cachedBackend = try {
        // Priority 1: Vulkan (for llama.cpp GPU acceleration)
        if (isVulkanAvailable()) {
            Timber.d("BackendDetector: Vulkan backend available")
            BackendType.GPU
        }
        // Priority 2: OpenCL + EGL (for LiteRT-LM path, existing)
        else if (isOpenCLAvailable() && isEGLAvailable()) {
            Timber.d("BackendDetector: OpenCL+EGL GPU backend available")
            BackendType.GPU
        }
        // Fallback: CPU only
        else {
            Timber.d("BackendDetector: no GPU backend available, falling back to CPU")
            BackendType.CPU
        }
    } catch (e: Exception) {
        Timber.w(e, "BackendDetector: probe failed, falling back to CPU")
        BackendType.CPU
    }
    return cachedBackend!!
}

// NEW
fun probeVulkanBackend(): BackendType {
    return if (isVulkanAvailable()) BackendType.GPU else BackendType.CPU
}

// NEW — checks for Vulkan runtime availability
private fun isVulkanAvailable(): Boolean {
    return try {
        // Android ships libvulkan.so on API 24+. Try loading it.
        System.loadLibrary("vulkan")
        Timber.d("BackendDetector: libvulkan.so loaded — Vulkan runtime present")
        true
    } catch (e: UnsatisfiedLinkError) {
        Timber.d("BackendDetector: libvulkan.so not found on this device")
        false
    }
}
```

**Vulkan capability info (NEW):**

After `System.loadLibrary("vulkan")` succeeds, we need more details for the user:

```kotlin
// BackendDetector.kt — NEW
data class VulkanInfo(
    val deviceName: String,       // e.g., "Adreno 750"
    val apiVersion: String,       // e.g., "1.3"
    val maxComputeSharedMemorySize: Long,  // bytes
    val hasFloat16Support: Boolean,
    val hasInt8Support: Boolean
)

// This requires native JNI — can't query Vulkan capabilities from pure Kotlin.
// Add to jni_bridge.cpp:
// JNIEXPORT jobject JNICALL Java_..._BackendDetector_nativeGetVulkanInfo(JNIEnv* env, jclass)
```

**Runtime backend selection logic:**

When user loads a GGUF model:
1. `BackendDetector.probeBackend()` returns `GPU` or `CPU`
2. If `GPU`: pass `nGpuLayers = 99` (offload all layers to GPU) to `llamaEngine.loadModel()`
3. If `CPU`: pass `nGpuLayers = 0` (pure CPU inference)
4. `llama.cpp` internally selects Vulkan or CPU backend based on compile-time flags and runtime params

**Compile-time configuration:**

```cmake
# CMakeLists.txt — MODIFIED
option(GGML_VULKAN "Vulkan GPU backend" ON)
option(GGML_VULKAN_CHECK_RESULTS "Check Vulkan results" ON)

if(GGML_VULKAN)
    add_subdirectory(${LLAMA_CPP_DIR}/ggml/src/ggml-vulkan ${CMAKE_BINARY_DIR}/ggml-vulkan)
    target_link_libraries(warped_llama ggml-vulkan)
    target_compile_definitions(warped_llama PRIVATE GGML_USE_VULKAN=1)
endif()
```

**GPU layer offloading strategy:**
- `nGpuLayers = 0` → pure CPU (always works, slower)
- `nGpuLayers = 99` → all layers to GPU (fastest, requires enough VRAM)
- `nGpuLayers = N` → partial offload (balanced, requires tuning per-device)

Initial implementation should use `nGpuLayers = 0` for safety, then enable Vulkan offloading as a user toggle once stable.

### 7. Streaming Token Flow (Native → Kotlin → ViewModel)

**Data flow chain:**
```
llama.cpp inference loop (C++, jni_bridge.cpp)
  │ on each token
  ▼
JNI callback: TokenCallback.onToken(token: String, done: Boolean)
  │ called on JNI thread (not Kotlin coroutine thread)
  ▼
Kotlin callbackFlow { trySend(token) }
  │ structured concurrency, thread-safe channel
  ▼
LocalLlmProvider.chat(request): Flow<StreamToken>
  │ wraps generate() → maps String tokens to StreamToken.Delta
  │ flowOn(Dispatchers.Default)
  ▼
ChatViewModel.sendMessage()
  │ provider.chat(request).collect { token → ... }
  │ 50ms debounce → update UI state
  ▼
ChatScreen composable (collectAsStateWithLifecycle)
```

**The critical thread transition:**
```
JNI thread (C++)           Coroutine context (Kotlin)
      │                           │
      │──onToken("Hello")────────▶│ trySend("Hello") → Channel
      │                           │
      │──onToken(" world")───────▶│ trySend(" world") → Channel
      │                           │
      │──onToken("!", done=true)─▶│ trySend("!") → close()
                                  │
                                  │─flowOn(Dispatchers.Default)─▶
                                  │   emit StreamToken.Delta(token)
                                  │
                                  │─collect on Main──▶
                                  │   update MutableStateFlow<ChatUiState>
```

**Thread safety in `callbackFlow`:**

`trySend()` is thread-safe and non-blocking — it's designed for exactly this JNI callback pattern. `awaitClose { nativeStop() }` ensures proper cleanup when the Flow collector cancels (e.g., user taps stop, or ViewModel scope ends).

**Potential issue: JNI thread overload**

If the native `generate()` loop runs on the calling thread (which it does — `nativeGenerate` is blocking), then the coroutine thread that calls `flowOn(Dispatchers.Default)` is *waiting* on the native call. The JNI callbacks fire from **the same thread** that called `nativeGenerate` (llama.cpp runs inference on the calling thread). This means:
- The coroutine worker thread IS the inference thread
- `trySend()` from within the callback might block if the Channel buffer is full
- Default `callbackFlow` buffer is `Channel.RENDEZVOUS` (0) — this causes `trySend` to fail if no collector is ready

**Fix: Use BUFFERED channel**

```kotlin
// LlamaEngine.kt — MODIFIED
fun generate(prompt: String): Flow<String> = callbackFlow {
    val callback = object : TokenCallback {
        override fun onToken(token: String, done: Boolean) {
            if (done) close()
            else if (token.isNotEmpty()) trySend(token)
        }
    }
    nativeGenerate(prompt, callback)
    awaitClose { nativeStop() }
}.buffer(Channel.BUFFERED)  // NEW: prevent trySend failure when Channel is full
 .flowOn(Dispatchers.Default)
```

`buffer(Channel.BUFFERED)` = 64 element buffer by default. Tokens are small strings — this is safe and prevents dropped tokens.

**Prompt templating (NEW — currently hardcoded ChatML):**

`LocalLlmProvider.buildPrompt()` uses a hardcoded ChatML template (`<|system|>`, `<|user|>`, `<|assistant|>`). Different models expect different templates:
- Llama-3: `<|begin_of_text|><|start_header_id|>system<|end_header_id|>...`
- Mistral: `[INST] ... [/INST]`
- DeepSeek: `User: ...\n\nAssistant:`
- Gemma: `<start_of_turn>user\n...<end_of_turn>\n<start_of_turn>model\n`

**Defer:** Initial implementation uses llama.cpp's built-in chat template via `llama_chat_apply_template()` which reads the template from the GGUF metadata. The JNI wrapper calls this to format the prompt correctly per-model. In the future, expose template selection in UI.

### 8. Thread Safety for JNI Calls from Coroutines

**Problem:** Multiple coroutines could call `LlamaEngine.generate()` concurrently (e.g., user rapidly taps send, or a bug). The native C++ `LlamaEngine` is a singleton — concurrent `generate()` calls would corrupt model state.

**Current state:** `EngineManager` uses `@Synchronized` on public methods, but `LocalLlmProvider.chat()` calls `llamaEngine.generate()` directly (bypasses EngineManager). `ChatViewModel.sendMessage()` uses a `generationJob` that prevents concurrent sends from the UI, but doesn't protect against programmatic calls.

**Solution: Layered protection**

1. **UI layer:** `ChatViewModel.sendMessage()` already uses `generationJob` to prevent duplicate sends (existing, correct)

2. **Provider layer:** `LocalLlmProvider.chat()` checks `llamaEngine.isLoaded()` before calling `generate()`, but doesn't guard against concurrent calls

3. **Engine layer:** `LlamaEngine.generate()` should be `@Synchronized` at the Kotlin level (NEW)

4. **Native layer:** `LlamaEngine::generate()` in C++ already has `shouldStop` flag for cancellation, but no mutex (NEW — add `std::mutex`)

```kotlin
// LlamaEngine.kt — MODIFIED
@Synchronized  // NEW
fun generate(prompt: String): Flow<String> = callbackFlow {
    // ... (existing code)
}.buffer(Channel.BUFFERED)
 .flowOn(Dispatchers.Default)
```

```cpp
// jni_bridge.h — MODIFIED
class LlamaEngine {
private:
    std::mutex generateMutex;
    // ...
};
```

**Threading summary table:**

| Operation | Kotlin Dispatcher | Native Thread | Synchronization |
|-----------|-------------------|---------------|-----------------|
| `loadModel` | `Dispatchers.Default` | Calling thread (blocking) | `@Synchronized` + native mutex |
| `generate` | `Dispatchers.Default` | Calling thread (blocking) | `@Synchronized` + native mutex |
| `stop` | Any | Any | Native mutex + `shouldStop` flag |
| `unload` | `Dispatchers.Default` | Calling thread | `@Synchronized` + native mutex |
| `isLoaded` | Any | Calling thread | Native atomic flag |
| Token callback | JNI thread | Same as generate thread | `callbackFlow` Channel |

## New vs Modified Components

### NEW Components

| Component | Package | Purpose |
|-----------|---------|---------|
| `VulkanInfo` data class | `data/local/inference/` | Vulkan device capabilities (adapter name, VRAM, compute support) |
| `GgufValidator` | `data/local/inference/` | Post-download GGUF file validation (magic bytes, architecture check) |
| `PromptTemplate` enum + resolver | `domain/model/` | Per-model chat template selection (deferred to later phase) |
| `llama.cpp` source (vendored) | `app/src/main/cpp/llama.cpp/` | Compiled llama.cpp library (ggml, llama, common) |
| `ggml-vulkan` source (vendored) | `app/src/main/cpp/ggml-vulkan/` | Vulkan backend for GGML |

### MODIFIED Components

| Component | Change Summary |
|-----------|---------------|
| `LlamaEngine.kt` | `@Synchronized` on `generate()`, add `.buffer(Channel.BUFFERED)`, add Vulkan params to `loadModel()`, add new native methods for Vulkan info and metadata |
| `jni_bridge.cpp` | Implement real llama.cpp calls (model load, generate loop, tokenization, sampling), add global JNI ref management, add Vulkan info queries |
| `jni_bridge.h` | Add Vulkan params to `loadModel`, add mutex for `generate`, add metadata extraction |
| `CMakeLists.txt` | Add llama.cpp source files, GGML Vulkan backend, cross-compilation config |
| `EngineManager.kt` | `switchToLlama()` passes Vulkan params from `BackendDetector`, `ActiveEngine.backend` populated for llama.cpp |
| `BackendDetector.kt` | Add `isVulkanAvailable()`, prioritize Vulkan over OpenCL, add `probeVulkanBackend()` |
| `LocalLlmProvider.kt` | Pass `GenerationParameters` to `llamaEngine.generate()`, use native chat template instead of hardcoded ChatML |
| `ChatViewModel.kt` | Tighter memory check using `ActivityManager.MemoryInfo`, OOM catch in `preloadLocalModel()` |
| `GgufMetadataParser.kt` | Complete the `mapQuantization` table for all GGUF v3 quantizations, fix `parameterCount` extraction |
| `ModelDownloadWorker.kt` | Add GGUF validation step after download (call `GgufValidator`) |
| `AndroidManifest.xml` | Add `android:largeHeap="true"` |
| `InferenceModule.kt` | No structural changes, but ensure `LlamaEngine` provider accounts for new dependencies |

### UNCHANGED Components

| Component | Why Unchanged |
|-----------|--------------|
| `ProviderRouter` | Already dispatches `LOCAL` to `LocalLlmProvider` — no routing change needed |
| `ChatRepository` / `ChatRepositoryImpl` | Chat storage unchanged — messages flow the same regardless of provider |
| `ChatScreen` (Compose) | UI unchanged — streams tokens from `ChatUiState.streamingContent` which works for any provider |
| `ModelsScreen` / `ModelsViewModel` | Already displays `LocalModel.quantization`, `parameterCount`, `architecture` from Room — feeds from GGUF metadata parser |
| `HuggingFaceScreen` / `HuggingFaceViewModel` | Already filters `.gguf` siblings — works; may need API detail call fix |
| `HuggingFaceApi` / `HuggingFaceRepository` | API unchanged — endpoints already support GGUF model listing |
| `LiteRTLmEngine` / `LiteRTLmProvider` | No changes — GGUF path is independent |
| `Domain models` (ChatMessage, Conversation, etc.) | Unchanged — all models are provider-agnostic |
| `Room entities / DAOs` | Unchanged — `LocalModel` table already has GGUF fields |
| `ModelImportManager` | Already handles `.gguf` imports via `GgufMetadataParser` — unchanged |

## Data Flow: GGUF Chat (End-to-End)

```
User taps send on ChatScreen
  │
  ▼
ChatViewModel.sendMessage(text, images=[])
  │ 1. Validate model selected
  │ 2. Create user ChatMessage, save to Room
  │ 3. Resolve provider: ProviderRouter.resolveLocal(LOCAL, modelId)
  │    → LocalLlmProvider.configure(modelFilePath)
  │ 4. Check engine loaded → if not, preloadLocalModel(modelId)
  │    → Memory check (ActivityManager.MemoryInfo, 80% threshold)
  │    → withContext(Dispatchers.Default) {
  │        engineManager.switchToLlama(modelPath, nGpuLayers=0|99)
  │          → backendDetector.probeBackend() → GPU (Vulkan) or CPU
  │          → llamaEngine.loadModel(path, threads=N, ctx=4096, nGpuLayers, useVulkan)
  │            → JNI → nativeLoadModel → llama_load_model_from_file()
  │            → llama_new_context_with_model()
  │      }
  │ 5. Build ChatRequest(messages, parameters, images=[])
  │ 6. provider.chat(request).collect { token → ... }
  │
  ▼
LocalLlmProvider.chat(request): Flow<StreamToken>
  │ 1. Check llamaEngine.isLoaded()
  │ 2. if not loaded: auto-load (same as preloadLocalModel)
  │ 3. format prompt via llama_chat_apply_template() [deferred: use built-in]
  │ 4. llamaEngine.generate(prompt, params).collect { token → ... }
  │    .flowOn(Dispatchers.Default)
  │
  ▼
LlamaEngine.generate(prompt): Flow<String>
  │ callbackFlow { callback → nativeGenerate(prompt, callback) }
  │ .buffer(Channel.BUFFERED)
  │
  ▼
jni_bridge.cpp: LlamaEngine::generate(prompt, callback)
  │ 1. Tokenize prompt → llama_tokenize()
  │ 2. Create llama_batch
  │ 3. Inference loop:
  │    while (!shouldStop) {
  │      llama_decode(ctx, batch)
  │      llama_sample_*() → next token
  │      token_str = llama_token_to_piece()
  │      callback(token_str, done=false)
  │      if (next_token == EOS) break
  │    }
  │ 4. callback("", done=true)
  │
  ▼ (back in Kotlin)
ChatViewModel.sendMessage() collector:
  │ StreamToken.Delta → 50ms buffer → update streamingContent
  │ StreamToken.Done → save assistant message to Room, clear streaming
  │ StreamToken.Error → surface ChatError
  │
  ▼
ChatScreen composable:
  │ collectAsStateWithLifecycle(uiState)
  │ LazyColumn with streamingContent updated every 50ms
```

## Integration Points Summary

| Integration Point | Existing Hook | Status | Action |
|------------------|---------------|--------|--------|
| ChatViewModel → llama.cpp | `ProviderRouter.resolveLocal(LOCAL)` → `LocalLlmProvider` | Works | Harden memory check, add OOM catch |
| Model download → load pipeline | `ModelDownloadWorker` → `GgufMetadataParser` → Room → `ChatViewModel.preloadLocalModel()` | Works | Add GGUF validation step |
| Engine lifecycle | `EngineManager.switchToLlama()` | Works | Pass Vulkan backend params through |
| Token streaming | `callbackFlow` in `LlamaEngine.generate()` | Works | Add `.buffer(Channel.BUFFERED)`, `@Synchronized` |
| GGUF metadata | `GgufMetadataParser` standalone, `LlamaEngine.getModelInfo()` stub | Partial | Complete quantization map, add native metadata extraction |
| Vulkan backend | `BackendDetector` probes EGL+OpenCL (not Vulkan) | Needs Vulkan | Add `isVulkanAvailable()`, prioritize over OpenCL |
| Memory management | `MemoryChecker` + `preloadLocalModel()` | Partial | Add `ActivityManager.MemoryInfo`, use 1.3× size estimate |
| Thread safety | EngineManager `@Synchronized` | Partial | Add `@Synchronized` to `LlamaEngine.generate()`, native mutex |
| JNI global refs | `nativeGenerate` uses callback object directly | Risky | Add `NewGlobalRef`/`DeleteGlobalRef` in JNI |

## Build Order Dependencies

```
Phase 1: CMake + llama.cpp compilation
  ├── CMakeLists.txt update (add llama.cpp source)
  ├── NDK cross-compilation config (arm64-v8a, x86_64)
  └── Verify libwarped_llama.so builds with real llama.cpp symbols

Phase 2: JNI bridge — model loading
  ├── Implement real nativeLoadModel (llama_load_model_from_file)
  ├── Implement nativeUnload (llama_free)
  ├── Implement nativeIsLoaded (check model pointer)
  ├── Implement nativeGetModelInfo (llama_model_desc)
  ├── Fix JNI global ref management
  └── Test: load a tiny GGUF (TinyLlama 1B), verify loaded state

Phase 3: JNI bridge — token generation
  ├── Implement nativeGenerate (llama_tokenize → decode loop → callback)
  ├── Pass GenerationParameters to native (temperature, topK, topP, seed, threads)
  ├── Implement nativeStop (shouldStop flag)
  ├── Add @Synchronized + native mutex for thread safety
  └── Test: generate a single response end-to-end

Phase 4: Vulkan backend
  ├── Add isVulkanAvailable() to BackendDetector
  ├── Add GGML_VULKAN to CMake compilation
  ├── Pass nGpuLayers to loadModel
  ├── Add VulkanInfo JNI query
  └── Test: GPU offloading on supported devices, CPU fallback on others

Phase 5: Production hardening
  ├── .buffer(Channel.BUFFERED) on callbackFlow
  ├── ActivityManager.MemoryInfo + 1.3× RAM estimate
  ├── OOM catch in preloadLocalModel
  ├── GGUF validation post-download
  ├── Complete quantization mapping table
  ├── android:largeHeap="true" in manifest
  └── Test: memory pressure, device rotation, process death
```

## Anti-Patterns to Avoid

### Anti-Pattern 1: Loading model on Main thread
**What people do:** Call `llamaEngine.loadModel()` from `ChatViewModel` without `withContext(Dispatchers.Default)`.
**Why wrong:** Model loading is a blocking I/O + compute operation that can take 10-30 seconds. Freezes the UI.
**Already correct:** `preloadLocalModel()` wraps load in `withContext(Dispatchers.Default)`. Maintain this.

### Anti-Pattern 2: Not converting JNI local refs to global refs
**What happens:** Passing a `jobject callback` to a long-running native function without `NewGlobalRef`. When the JNI call returns (could be minutes later during generation), the local reference table may have been garbage collected, causing a crash.
**Prevention:** Always `NewGlobalRef` for objects passed to long-running native functions; `DeleteGlobalRef` on cleanup.

### Anti-Pattern 3: Tokens dropped due to unbuffered Channel
**What happens:** `callbackFlow` with default `Channel.RENDEZVOUS` (zero buffer). If the collector is slightly behind (e.g., UI thread busy), `trySend()` fails silently and tokens are lost.
**Fix:** Add `.buffer(Channel.BUFFERED)` after `callbackFlow`.

### Anti-Pattern 4: Unloading model while generating
**What happens:** User switches models mid-generation → `unloadCurrent()` → `nativeUnload()` called while `nativeGenerate` is running on another thread. Memory freed under active use → crash.
**Prevention:** `EngineManager.unloadCurrent()` already calls `llamaEngine.stop()` before `llamaEngine.unload()`. The `stop()` sets `shouldStop = true` which causes the native generate loop to exit gracefully. This is correct — ensure `unload()` waits for generate to finish (or add a timeout).

### Anti-Pattern 5: Compiling llama.cpp pre-built .so from different NDK version
**What happens:** Using pre-built `libllama.so` from GitHub releases compiled with a different NDK, STL, or ABI. Causes `UnsatisfiedLinkError` or segfaults at runtime.
**Prevention:** Always build llama.cpp from source with the project's NDK version via CMake. The existing CMakeLists.txt already takes this approach (build from source).

## Sources

- **Warped codebase** (2026-05-05): `LlamaEngine.kt`, `jni_bridge.cpp/h`, `EngineManager.kt`, `BackendDetector.kt`, `LocalLlmProvider.kt`, `ChatViewModel.kt`, `CMakeLists.txt`, `GgufMetadataParser.kt`, `ModelDownloadWorker.kt`, `ModelImportManager.kt` — HIGH confidence (direct code analysis)
- **llama.cpp official repository**: `github.com/ggerganov/llama.cpp` — Android build documentation, GGUF format spec, Vulkan backend documentation — HIGH confidence (authoritative source)
- **Android NDK documentation**: JNI tips (global vs local references), `callbackFlow` thread safety — HIGH confidence (official Android docs)
- **GGUF format specification**: `github.com/ggerganov/ggml/blob/master/docs/gguf.md` — HIGH confidence (format spec)

---

*Architecture research for: GGUF native inference integration with Clean Architecture*
*Researched: 2026-05-05*
*Confidence: HIGH (based on direct codebase analysis + authoritative documentation)*
