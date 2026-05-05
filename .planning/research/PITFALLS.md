# Pitfalls Research: GGUF Native Inference with llama.cpp

**Domain:** Android local LLM inference via llama.cpp JNI/NDK
**Researched:** 2026-05-05
**Confidence:** HIGH

## Critical Pitfalls

Mistakes that cause rewrites, native crashes (SIGSEGV), or permanently broken builds.

---

### Pitfall 1: Native Thread Calls JNI Callbacks Without Thread Attachment

**What goes wrong:**
The native `generate()` function runs on the calling thread (`Dispatchers.Default` worker). It calls `TokenCallback.onToken()` via `env->CallVoidMethod()` on the same thread. This works initially, but if the native inference loop migrates to a different native thread (Vulkan compute, internal worker pool, GPU completion callback), the `JNIEnv*` pointer from the original thread is **invalid** on the new thread. Calling JNI functions with a stale `env` crashes immediately with `JNI DETECTED ERROR IN APPLICATION: use of deleted local reference`.

**Why it happens:**
`JNIEnv*` is thread-local. The `env` captured in the C++ lambda `[env, callback, onTokenMethod]` is only valid on the thread that entered the JNI function. If `libllama` internally spawns threads for GPU work or batch processing, any JNI call from those threads will dereference invalid pointers. This is a silent crash on release builds (no JNI check mode).

**How to avoid:**
1. **Never capture `JNIEnv*` in callbacks that might execute on different threads.**
2. Store `JavaVM*` (obtained via `env->GetJavaVM(&jvm)`) in the C++ singleton at init time. Thread-safe, survives across threads.
3. In any callback, call `jvm->AttachCurrentThread(&env, nullptr)` before JNI calls, then `jvm->DetachCurrentThread()` after.
4. Create **global references** to the `callback` jobject (`env->NewGlobalRef(callback)`) — local references expire when the JNI call returns. Store the global ref in the engine, delete it on unload.
5. **Alternative (recommended):** Don't do JNI callbacks during generation at all. Use a `std::atomic<bool> shouldStop` flag that the native loop polls. Return generated tokens in batches via a thread-safe queue. Kotlin side polls via `callbackFlow {}` on its own thread.

**Warning signs:**
- Crashes in release builds but not in debug (JNI check mode captures stale refs in debug)
- Intermittent `SIGSEGV` in `libart.so` or `libwarped_llama.so` under high GPU load
- Crash reports with `use of invalid jobject` in logcat

**Phase to address:** Phase 1 (JNI bridge build + load) — design the threading model before writing the inference loop. Fix the capture pattern in `jni_bridge.cpp` line 112.

---

### Pitfall 2: `callbackFlow` Buffer Overrun Kills Streaming Silently

**What goes wrong:**
`LlamaEngine.generate()` (Kotlin) uses `callbackFlow {}` with `trySend(token)`. The default channel capacity is `Channel.RENDEZVOUS` (0 buffer). If the native code emits tokens faster than the downstream consumer (Compose UI on Main thread) can collect them, `trySend` returns `ChannelResult.closed` or `ChannelResult.failure`. The `callbackFlow` docs state: "A failure or exception from trySend does not close the channel." But repeated failures with a rendezvous channel mean tokens are **silently dropped**. The user sees gaps in the response.

**Why it happens:**
Native token generation on modern devices is fast (50-100 tokens/sec). Compose recomposition with Markdown rendering is slower (10-20ms per update). The native thread produces tokens faster than the UI thread consumes them. With a rendezvous channel, every token emission blocks until the UI collects it — but `trySend` is non-blocking by design, so it returns failure instead of waiting.

**How to avoid:**
```kotlin
fun generate(prompt: String): Flow<String> = callbackFlow {
    val callback = object : TokenCallback {
        override fun onToken(token: String, done: Boolean) {
            if (done) {
                close()
            } else if (token.isNotEmpty()) {
                trySendBlocking(token)  // swap: trySend → trySendBlocking
                // OR specify capacity:
                // callbackFlow { ... } with Channel(Channel.BUFFERED)
            }
        }
    }
    nativeGenerate(prompt, callback)
    awaitClose { nativeStop() }
}
```
Use `trySendBlocking()` — it suspends (blocks the native thread) until the channel has space. Since the native thread is owned by the JNI bridge and dispatched on `Dispatchers.Default` (a thread pool), this is acceptable. The native thread will simply wait until Compose catches up.

**Warning signs:**
- Streaming responses appear truncated (missing the last few words)
- No error in logcat — tokens silently discarded
- Performance appears "good" but content is incomplete

**Phase to address:** Phase 2 (inference loop + streaming) — this is the streaming integration point.

---

### Pitfall 3: Vulkan GPU on Android — Driver Fragmentation Causes Hard Crashes

**What goes wrong:**
Enabling `GGML_VULKAN=ON` at compile time produces a binary that works on some Android devices but crashes with `SIGSEGV` or `VK_ERROR_DEVICE_LOST` on others. The root cause is Android GPU driver fragmentation:

| GPU Vendor | Driver Family | Known Issues |
|------------|---------------|--------------|
| Qualcomm Adreno 6xx/7xx | Turnip (open-source Mesa) + proprietary | 16-bit storage buffer issues on 7xx; `VK_FORMAT_R16_SFLOAT` not universally supported |
| ARM Mali G-series | Proprietary (Panfrost incomplete on Android) | SPIR-V to Bifrost/Valhall compiler crashes on certain shader patterns; `subgroupSizeControl` missing |
| Samsung Xclipse (AMD RDNA2) | Proprietary | `VK_KHR_8bit_storage` present but buggy; correct on newer driver versions |
| Imagination PowerVR | Proprietary | Limited Vulkan 1.1 support; missing `VK_KHR_shader_float16_int8` |

**Why it happens:**
llama.cpp's Vulkan backend uses compute shaders that depend on Vulkan 1.1+ features (`shaderFloat16`, `8bit_storage`, `16bit_storage`). Device drivers advertise support for these features but have buggy implementations. The Vulkan validation layers catch some issues, but not driver-specific bugs.

**How to avoid:**
1. **Build CPU-only first** (`GGML_VULKAN=OFF`). Ship and validate.
2. **Add Vulkan as a separate `.so`** or compile-time option, not the default.
3. **Runtime capability probing before using Vulkan:**
   - Query `VkPhysicalDeviceFeatures2` for `shaderFloat16`, `shaderInt8`, `16BitStorage`
   - Check Vulkan API version ≥ 1.1 (not just driver-reported version)
   - Try a "canary" shader dispatch (small compute that exercises the problematic features) before committing to Vulkan for inference
4. **Automatic fallback:** If Vulkan initialization fails for any reason, silently fall back to CPU. Log the reason. Do NOT crash the app.
5. **Test on real devices across vendors** — emulator Vulkan is Swiftshader (software), not representative of real drivers.
6. **OpenCL note:** Qualcomm removed OpenCL from their Android drivers starting with Android 12. `GGML_OPENCL` on modern Qualcomm devices will fail. Vulkan is the only cross-vendor GPU API on Android.

**Warning signs:**
- Crashes only on specific device models (check crash report device distribution)
- `VK_ERROR_DEVICE_LOST` in logcat → driver bug
- Works on emulator but crashes on physical device (Swiftshader masks driver bugs)

**Phase to address:** Phase 1 (CMake build) — design for Vulkan as optional. Phase 3 (GPU backend) — implement runtime probing and fallback.

---

### Pitfall 4: mmap + Android LMK = SIGBUS Under Memory Pressure

**What goes wrong:**
llama.cpp uses `mmap` by default (`use_mmap=true`) to memory-map the GGUF file. The model weights are mapped into virtual memory, and pages are loaded on demand. Under memory pressure, the kernel evicts clean file-backed pages (no swap needed — they can be re-read from disk). This seems ideal. **However**, Android's Low Memory Killer (LMK) assesses process memory pressure using `oom_score_adj`. A large `mmap` mapping increases `VmRSS` (and `PSS` partially), inflating the process's perceived memory footprint. LMK may kill the process even though most mapped pages are clean and evictable. Worse: if the GGUF file is on external storage (SD card) and the card is removed or goes to sleep, a page fault on an evicted mapped page triggers `SIGBUS` — an uncatchable native crash.

**Why it happens:**
Android LMK uses `oom_score_adj` calculated partly from `RSS`, not just `RSS - file-backed`. Memory-mapped files contribute to RSS metrics. External SD cards can be unmounted or put into power-save mode independently.

**How to avoid:**
1. **Store models exclusively in internal storage** (`context.filesDir/models/`). Never on external/SD card.
2. **Set `use_mmap=true`** (default, good for performance) BUT add `ActivityManager.MemoryInfo` check **after** model load to detect if RSS spiked above safe threshold. If so, trigger the memory warning dialog.
3. **Alternative for devices with <8GB RAM:** Set `use_mmap=false` (loads weights into heap). This uses more virtual memory but gives more predictable RSS behavior with Android LMK. Trade-off: slower load, no benefit from page cache.
4. **Add `android:largeHeap="true"`** in `AndroidManifest.xml` — already recommended in STACK.md but verify it's there.
5. **Check available memory BEFORE loading**, not just file size. A 4.3GB Q4_K_M GGUF requires ~5-6GB at inference (weights + KV cache + scratch buffers). `MemoryChecker.canLoadModel()` currently only checks file size — need `fileSize * 1.3` multiplier for inference overhead.

**Warning signs:**
- Process killed by LMK during inference, no crash log (system-level kill)
- `logcat -b system` shows `Kill com.warped ... (adj 900): low on memory`
- Model loads successfully but app disappears during first prompt

**Phase to address:** Phase 1 (memory sizing) — update `MemoryChecker` with inference overhead multiplier. Phase 4 (stability) — add post-load memory verification.

---

### Pitfall 5: `shouldStop` Flag Without Atomics = Undetectable Race Condition

**What goes wrong:**
The current JNI bridge declares `bool shouldStop = false;` as a plain member variable. `nativeStop()` writes `shouldStop = true` from Kotlin's coroutine thread (`viewModelScope`, likely `Dispatchers.Main` or `Default`). The native `generate()` loop reads `shouldStop` from the native inference thread. Without `std::atomic<bool>` or explicit synchronization, this is a **data race** (C++ undefined behavior). The compiler is free to hoist the read of `shouldStop` into a register at loop entry — effectively ignoring the `stop()` signal forever. Even without compiler optimization, cache coherence on ARM big.LITTLE architectures means a write on one core may not be visible on another for an arbitrarily long time.

**Why it happens:**
The JNI bridge was written as a stub and never stress-tested for thread safety. The `shouldStop` flag pattern is correct in concept but missing the synchronization mechanism.

**How to avoid:**
```cpp
// In jni_bridge.h:
#include <atomic>
std::atomic<bool> shouldStop{false};

// In jni_bridge.cpp::generate():
while (!shouldStop.load(std::memory_order_relaxed)) {
    // decode loop
}
```
`memory_order_relaxed` is sufficient for a stop flag — we don't need ordering with other operations, just visibility.

**Warning signs:**
- User taps "stop" but generation continues for seconds/minutes
- "Generation never stops" bug reports
- Only reproduces on certain devices (big.LITTLE scheduling differences)

**Phase to address:** Phase 2 (inference loop) — first thing to fix when implementing real generation.

---

### Pitfall 6: Unloading Engine While Generation Is Active = SIGSEGV on Freed Memory

**What goes wrong:**
`EngineManager.unloadCurrent()` calls `llamaEngine.stop()` then `llamaEngine.unload()` on the same `@Synchronized` block. But `stop()` only sets `shouldStop = true` — it does not WAIT for the native generation thread to exit. `unload()` immediately calls `llama_free(ctx)` and `llama_free_model(model)`. If the generation thread is still executing `llama_decode()` on the freed context, the process crashes with SIGSEGV in freed heap memory.

**Why it happens:**
The `stop()` → `unload()` sequence assumes `stop()` is synchronous (blocks until generation exits). But it's fire-and-forget — sets a flag and returns immediately. `EngineManager` doesn't know the native thread is still running.

**How to avoid:**
```cpp
// In jni_bridge.cpp:
void LlamaEngine::stop() {
    shouldStop.store(true, std::memory_order_relaxed);
}

void LlamaEngine::unload() {
    stop();  // signal stop first
    if (generationThread.joinable()) {
        generationThread.join();  // WAIT for generation to exit
    }
    // Now safe to free
    if (llama_context) { llama_free(llama_context); llama_context = nullptr; }
    if (llama_model) { llama_free_model(llama_model); llama_model = nullptr; }
    loaded = false;
}
```
Or on the Kotlin side: `nativeStop()` → poll `nativeIsLoaded()` or a new `nativeIsGenerating()` → only call `nativeUnload()` when safe. The C++ `generate()` should set `isGenerating = false` when it exits the loop.

**Warning signs:**
- Crash on conversation switch (user switches model mid-generation)
- Crash on app backgrounding (system calls `onTrimMemory` → `unloadCurrent()`)
- SIGSEGV in `llama_decode` or `ggml_compute_forward` in crash reports

**Phase to address:** Phase 2 (inference loop) — design stop/unload as a two-phase sequence. Phase 4 (EngineManager integration) — add generation-state awareness to lifecycle.

---

### Pitfall 7: ProGuard/R8 Strips JNI Callback Methods

**What goes wrong:**
Release builds crash with `java.lang.NoSuchMethodError: no non-static method "Lcom/warped/data/local/inference/LlamaEngine$TokenCallback;.onToken(Ljava/lang/String;Z)V"`. This happens because R8 renames `LlamaEngine$TokenCallback.onToken()` (it's called from native code via JNI, which looks up the method by name). If the name changes, native code calls a non-existent method.

**Why it happens:**
The existing `proguard-rules.pro` has no keep rules for `LlamaEngine`, its native methods, or its `TokenCallback` inner interface. R8 treats `TokenCallback` as unused (it's only referenced from native code, which R8 can't see) and either renames or strips it.

**How to avoid:**
Add to `proguard-rules.pro`:
```
# llama.cpp JNI bridge
-keepclasseswithmembernames class com.warped.data.local.inference.LlamaEngine {
    native <methods>;
}
-keep class com.warped.data.local.inference.LlamaEngine$TokenCallback {
    void onToken(java.lang.String, boolean);
}
-keep,allowshrinking class com.warped.data.local.inference.LlamaEngine {
    *;
}
-keepattributes Signature,Exceptions,InnerClasses,EnclosingMethod
```
**Critical:** `-keepattributes Signature` is already needed for Retrofit type tokens but verify it's present. `Exceptions` is needed because JNI calls can throw exceptions that the Java side needs to handle.

**Warning signs:**
- Debug build works, release build crashes immediately on model load
- `NoSuchMethodError` or `UnsatisfiedLinkError` in crash reports
- Only on release builds (minifyEnabled=true)

**Phase to address:** Phase 1 (build system) — add before first release build with real JNI. Fix this BEFORE testing the JNI bridge in release mode.

---

### Pitfall 8: `libc++_shared` STL Conflict with Other Native Libraries

**What goes wrong:**
If the app uses `c++_shared` (the default for most Android NDK projects), and another library (e.g., LiteRT-LM's native libs) also links against `c++_shared`, both libraries share a single instance of `libc++_shared.so` in the process. If they were compiled with **different NDK versions**, the shared library ABI may be incompatible — causing mysterious crashes in `std::string` operations, `std::vector` destruction, or exception handling. The classic symptom: `__cxa_throw` or `std::__1::basic_string` in the crash stack.

**Why it happens:**
Android NDK revisions change the `libc++_shared.so` ABI. NDK 26's libc++ is not binary-compatible with NDK 27's libc++. If `libwarped_llama.so` was compiled with NDK 27 and another library was compiled with NDK 26, they'll both try to use the same `libc++_shared.so` and one will get the wrong ABI.

**How to avoid:**
1. **Use `c++_static` for `libwarped_llama.so`** — links libc++ into the .so directly. Eliminates the shared STL dependency entirely. Trade-off: larger .so (adds ~1MB) but zero ABI conflict risk.
   ```cmake
   # In CMakeLists.txt:
   set(CMAKE_ANDROID_STL_TYPE c++_static)
   ```
2. **Verify all native dependencies use the same NDK version.** Check LiteRT-LM's native `.so` files — they're likely pre-compiled. Find which NDK version they were built with.
3. **If you must use `c++_shared`:** Package the exact `libc++_shared.so` from your NDK in the APK. Don't rely on the system's version (Android's `libc++.so` is NOT the same as NDK's `libc++_shared.so`).

**Warning signs:**
- `SIGSEGV` / `SIGABRT` in `libc++.so` or `libc++_shared.so` during model load or generation
- Crash on first `std::string` allocation from JNI
- Works on some API levels but not others (system libc++ version varies)

**Phase to address:** Phase 1 (CMake build) — decide STL strategy before compiling llama.cpp. Test with all .so files (LiteRT-LM + custom) loaded in the same process.

---

### Pitfall 9: `@Synchronized` on EngineManager Doesn't Protect Native State

**What goes wrong:**
`EngineManager` methods are annotated `@Synchronized`, protecting the JVM-managed state (`activeEngine` field). But the actual model is loaded in **native memory** managed by `libllama`. The native singleton (`LlamaEngine::getInstance()`) has its own state (`loaded`, `modelPath`, `llama_model`, `llama_context`). Two JVM threads can't simultaneously enter `switchToLlama()` (thanks to `@Synchronized`), but a **native thread** (running `generate()`) and the **JVM thread** (calling `unloadCurrent()`) can race on the native state without any synchronization.

**Why it happens:**
Java monitors (`@Synchronized`) only synchronize Java threads. The native inference thread is started from Java but runs independently — it doesn't acquire Java monitors. The `stop()` → `unload()` race (Pitfall 6) is a specific case of this broader problem.

**How to avoid:**
1. Add `std::mutex` in the C++ `LlamaEngine` class for all state mutations.
2. `generate()` acquires the mutex (shared/read lock), `stop()` and `unload()` acquire it exclusively (write lock).
3. Or: make `generate()` run on a dedicated Java-managed thread (via `Dispatchers.Default` with `newSingleThreadContext`), and use `@Synchronized` methods called from JNI callbacks to coordinate with `EngineManager`.

**Warning signs:**
- Intermittent crashes during engine switches
- "Already generating" but engine state says "not loaded"
- Thread sanitizer warnings (if using ASAN/TSAN)

**Phase to address:** Phase 4 (EngineManager integration) — add native mutex to C++ engine.

---

### Pitfall 10: Missing GGUF Validation — SIGSEGV on Malformed/Partial Files

**What goes wrong:**
If a user imports a non-GGUF file (wrong format, truncated download, corrupted header), calling `llama_model_load_from_file()` can dereference invalid pointers in the header parsing code, causing a native SIGSEGV crash. There's no graceful failure — the entire process dies.

**Why it happens:**
The current `GgufMetadataParser` exists in the codebase (mentioned in CONCERNS.md as a critical untested path) but `LlamaEngine.loadModel()` (both Kotlin and native) pass the file path directly to llama.cpp without pre-validation. llama.cpp does validate the GGUF magic number (`GGUF` at byte 0), but corrupted files with a valid magic number and invalid tensor offsets can still crash deep in the loader.

**How to avoid:**
1. **Pre-validate before passing to native code:**
   ```kotlin
   fun isValidGguf(file: File): Boolean {
       if (file.length() < 32) return false // minimum header size
       return file.inputStream().use { stream ->
           val magic = ByteArray(4)
           stream.read(magic)
           magic.contentEquals("GGUF".toByteArray())
       }
   }
   ```
2. **Parse GGUF header in Kotlin before native load** — extract metadata (architecture, context length, quantization) and validate tensor offsets don't exceed file size.
3. **Check file size against expected size** (from Hugging Face API `siblings` response). If mismatch, flag as corrupted.
4. **Add SHA256 verification:** Hugging Face provides SHA256 for each file. Download the `.gguf` → download the `model.safetensors.index.json` or use `X-File-SHA256` header response if available. Validate after download completes.

**Warning signs:**
- Crash on model selection for certain imported files
- `SIGSEGV` with `#00 pc ... llama_model_load` in crash stacks
- User reports of "app crashes when I try to load this model" — likely corrupted GGUF

**Phase to address:** Phase 1 (model loading) — implement pre-validation in Kotlin before native call.

---

## Technical Debt Patterns

Shortcuts that seem reasonable but create long-term problems.

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Hardcoding `-march=armv8.7a` in CMake | Best perf on current flagships (Snapdragon 8 Gen 3) | Crash on older ARMv8.0 devices; llama.cpp has runtime dispatch, overriding it with `-march` disables backward-compatible code paths | **Never** — use `-march=armv8-a` (baseline) + runtime dispatch |
| Using pre-built `.so` from llama.cpp releases | Fast start, no CMake complexity | Different NDK version, STL, or page alignment than your app; linker errors on some devices | Only for initial proof-of-concept; build from source for production |
| Single `CMakeLists.txt` with all backends (CPU+Vulkan+OpenCL) | One binary for all devices | Multi-hundred-MB APK; each backend adds significant .so size; OpenCL broken on Qualcomm Android 12+ | **Never** — build CPU-only as default, Vulkan as separate build flavor |
| `n_threads = 0` (auto-detect threads) | Simple, works on most devices | llama.cpp auto-detection uses `std::thread::hardware_concurrency()` which returns ALL cores (8 on flagship). Using all 8 + UI thread + system = CPU oversaturation, thermal throttle | **Never** — use `physical_cores - 1` or `4` maximum; expose via `GenerationParameters.threads` |
| `context_size = 32768` for all models | Works for any model | KV cache memory is proportional to context_size. 32K context for a 7B Q4_K_M model = ~1GB KV cache ALONE. Can cause OOM on 8GB devices. | Only for models that need it; use actual `llama.context_length` from GGUF header plus user override |
| Skipping `n_batch` configuration (use default 512) | No decision needed | Large batch = high memory for prompt processing. 512 tokens × model dimensions = significant scratch memory. On 8GB devices, this can push past OOM threshold during prompt evaluation. | For 8GB devices, set `n_batch = 256` or even `128` |
| Loading model on UI thread with progress dialog | Simple UX, one less thread to manage | Model loading is 5-30 seconds. ANR threshold is 5 seconds. ANR dialog kills user trust. | **Never** — always load on `Dispatchers.Default` with progress state |
| `vp8_skip` / encoding assumptions in prompt template | Hardcoding ChatML template for all models | Wrong template = garbage output. Mistral, Llama 3, Gemma, Phi all use different templates. | **Never** — extract `tokenizer.chat_template` from GGUF metadata; fall back to architecture-based defaults |

---

## Integration Gotchas

Common mistakes when connecting llama.cpp to the existing Warped architecture.

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| **ChatViewModel ↔ LocalLlmProvider** | Calling `provider.chat()` without checking `LlamaEngine.isLoaded()`. Model could have been unloaded by `onTrimMemory` between selection and chat. | `LocalLlmProvider.chat()` should check `isLoaded()` and auto-reload if needed (it does this now at line 32-37 — verify it survives engine switches) |
| **EngineManager mutual exclusion** | `switchToLlama()` succeeds but `switchToLiteRT()` is called immediately after — both engines briefly loaded simultaneously. `@Synchronized` prevents Java threads but a delayed unload of native memory could overlap. | After `unloadCurrent()`, verify `llamaEngine.isLoaded() == false` AND `liteRTLmEngine.isInitialized() == false` before loading new engine. Add a small yield (`delay(50)`) after unload to let native cleanup complete. |
| **ProviderRouter ↔ Lazy<LocalLlmProvider>** | `ProviderRouter` injects `LocalLlmProvider` as `dagger.Lazy<>`. Calling `.get()` after `EngineManager` switched engines could return stale provider referencing old engine state. | `LocalLlmProvider` is stateless beyond the `LlamaEngine` reference (which is the same singleton). This is safe — but verify `configure()` is called with correct path after engine switch. |
| **ModelDownloadWorker ↔ GGUF metadata** | Download completes, but `ModelDownloadWorker.onCompletion()` tries to parse GGUF metadata from an incomplete file (download resumed from checkpoint that was corrupted). | After download completes, verify file size matches expected from Hugging Face API. Validate GGUF magic number. Parse metadata only after validation. Delete corrupted file and retry on mismatch. |
| **BackendDetector ↔ Vulkan runtime** | `BackendDetector` currently detects EGL + OpenCL (for LiteRT-LM). Adding Vulkan detection logic without coordinating with llama.cpp's own Vulkan probing. | llama.cpp has its own Vulkan device enumeration via `ggml_vulkan_init()`. Use llama.cpp's detection for GGUF, not the existing `BackendDetector`. Add a separate `VulkanCapability` check that queries `VkPhysicalDeviceFeatures2` before passing `-ngl` parameter. |
| **ParameterStore ↔ llama.cpp context params** | `GenerationParameters` includes `threads`, `contextSize`, `seed` — but not `n_gpu_layers`, `n_batch`, `flash_attn_type`, `kv_cache_type`. These are critical for llama.cpp but missing from the parameter model. | Add `nGpuLayers: Int = 0`, `nBatch: Int = 256`, `kvCacheQuant: KVCacheQuant = KVCacheQuant.F16` to `GenerationParameters`. Update `ParameterStore`, `Preset`, Room entity. |
| **StreamToken.Error ↔ Native crashes** | If `generate()` crashes the native thread (SIGSEGV), `callbackFlow` never closes. `awaitClose { nativeStop() }` never runs. The flow hangs forever, UI shows perpetual "thinking" state. | Wrap `nativeGenerate()` call in a try/catch at the JNI level. If the native generate function returns abnormally (uncaught exception or signal), the JNI wrapper should call `callback("Error: inference failed", true)` before returning. Add a timeout on the Kotlin side — if no token received for 60 seconds, emit `StreamToken.Error`. |

---

## Performance Traps

Patterns that work at small scale but fail as usage grows.

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Collecting ALL tokens in a `StringBuilder` before emitting | 30-second pause then entire response appears at once | Emit tokens individually via `callbackFlow`; UI batches with 50ms debounce (already implemented in `ChatViewModel`) | Any model slower than 50 tok/s |
| Loading model on every `chat()` call | 5-30 second delay before first response | `LocalLlmProvider.chat()` checks `isLoaded()` and skips load if already loaded. Model stays loaded between messages in same conversation. | Already handled in current code (line 32-37) |
| `n_threads = Runtime.availableProcessors()` | Thermal throttle after 30 seconds, device gets hot | Cap threads at `physical_cores - 1`, max 4. Expose as user setting. Use `GenerationParameters.threads` which is user-configurable. | Sustained generation >1 minute |
| Using `contextSize = n_ctx` from GGUF header blindly | OOM on devices with <12GB RAM | Default to `min(header_context, 4096)`. User can increase in settings. Show memory warning when increasing above 4096. | Models with 128K+ context headers (Llama 3.1) on 8GB devices |
| Flushing KV cache on every conversation switch | 5-second delay to re-process system prompt | Keep model loaded, only flush KV cache when model or system prompt changes. Single conversation = single KV cache lifetime. | Multi-conversation apps with frequent switching |
| `n_batch = 512` for prompt processing | OOM during prompt evaluation (long conversation history) | Use `n_batch = 128` or `256` for 8GB devices. Dynamically reduce if prompt token count exceeds threshold. | Conversations with >2000 token history |

---

## Security Mistakes

Domain-specific security issues beyond general web security.

| Mistake | Risk | Prevention |
|---------|------|------------|
| Loading GGUF from external/shared storage | Malicious app replaces model with backdoored version; user's chat data goes to attacker | Only load from `context.filesDir/models/` (app-private). Import copies file to private storage before loading. Never load from `content://` URIs directly. |
| Skipping GGUF metadata validation | Malformed GGUF can exploit buffer overflow in `llama_model_load_from_file`; remote code execution in native context | Validate magic number, version, KV count, tensor count against file size before native load. Reject files where tensor offsets exceed file size. |
| Logging model path in Timber | Model path reveals user's file system structure; combined with other leaks, facilitates targeted attacks | Redact file paths in logs. Only log model name, not full path. Use `RedactingTree` pattern (already exists for API keys). |
| Not verifying Hugging Face download integrity | MITM on network replaces GGUF with malicious file; user loads and executes compromised model | Verify SHA256 from Hugging Face API response against downloaded file. Use HTTPS with certificate pinning for `huggingface.co`. |
| Storing GGUF metadata in plain Room DB | Attacker with physical access reads model names/quantizations to fingerprint user's interests | Room DB is in app-private storage (protected by Linux UID). Acceptable for model metadata (not secrets). API keys already encrypted via Keystore. |

---

## UX Pitfalls

Common user experience mistakes with local LLM inference.

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| "Model loaded but no response" for 30+ seconds | User thinks app is broken, force-quits | Show inference progress: "Processing prompt..." (tokenizing + prefill phase) vs "Thinking..." (first token latency). First token can take 10-60 seconds depending on prompt length + model size. Show elapsed time. |
| Download fails at 99% with no way to resume | User wasted 20 minutes and 5GB of bandwidth | `ModelDownloadWorker` already supports pause/resume via checkpointing. Ensure this works for GGUF downloads too (same mechanism as LiteRT-LM downloads). |
| Loading a model silently consumes all RAM, OS kills app | App disappears, user blames the app | `MemoryChecker.shouldWarn()` triggers warning dialog BEFORE load. Show "This model requires X GB, you have Y GB available." Offer to cancel or proceed at user's risk. |
| No indication of which quantization a model uses | User downloads Q8_0 (8GB) instead of Q4_K_M (4GB) for their 8GB device | Show quantization badge on model tiles. Color-code: green (fits), yellow (tight), red (won't fit). Filter Hugging Face results to compatible quantizations. |
| "Why is it so slow?" — user expects GPU speed but gets CPU | User abandons app, leaves bad review | Show active backend clearly in UI: "Running on CPU (4 threads)" vs "Running on Vulkan GPU (17 layers offloaded)". If Vulkan fails, show toast explaining fallback. |

---

## "Looks Done But Isn't" Checklist

Things that appear complete but are missing critical pieces.

- [ ] **Model loads successfully:** Verify inference actually produces coherent text (not garbage). Wrong chat template = coherent-looking tokens but nonsensical assistant. Test with known prompts: "What is 2+2?" should always produce "4" or equivalent.
- [ ] **Streaming works:** Verify tokens arrive incrementally, not all at once. Test with `maxTokens=500` — should see gradual output, not a single burst.
- [ ] **Stop generation:** Tap stop during generation → generation stops within 1 second. Not 5 seconds later. Not "stops after finishing the current sentence."
- [ ] **Engine switching:** Switch from GGUF model → LiteRT-LM model → back to GGUF → chat works on both without reboot. Memory is properly released between switches.
- [ ] **Process death recovery:** Start generation → background the app → wait 30 seconds → return. Model should remain loaded or recover gracefully.
- [ ] **Memory pressure:** Load a 4GB model on an 8GB device → open 5 other apps → return to Warped. Model should either still be loaded or user gets a clear "model was unloaded due to memory pressure" message.
- [ ] **Corrupted GGUF:** Try to load a truncated/invalid file → app shows error toast "This model file appears to be corrupted. Try downloading it again." Not a crash.
- [ ] **Release build:** Test JNI bridge in release build (minifyEnabled=true, shrinkResources=true). Verify no `NoSuchMethodError`, no `UnsatisfiedLinkError`. This is the #1 thing that breaks between debug and release.
- [ ] **Download GGUF + load + chat:** End-to-end flow on a fresh install. Download a real GGUF from Hugging Face, load it, send a message. This is the actual user journey — not just testing components in isolation.
- [ ] **Vulkan fallback:** Test on a device without Vulkan support (or force CPU in settings). App should silently use CPU, not crash or show errors.

---

## Recovery Strategies

When pitfalls occur despite prevention, how to recover.

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| JNI threading crash (Pitfall 1) | HIGH (rewrite JNI callback architecture) | Replace lambda capture with JavaVM+GlobalRef pattern. Move token emission to a thread-safe queue. Pre-built `java-llama.cpp` library uses this pattern — reference their implementation. |
| Vulkan driver crash (Pitfall 3) | MEDIUM (add runtime fallback, retest) | Disable Vulkan by default. Add opt-in setting. Build Vulkan as a separate compile path. Add device denylist for known-broken GPU/driver combos (query from crash reports). |
| `callbackFlow` silent drops (Pitfall 2) | LOW (one-line fix) | Replace `trySend` with `trySendBlocking` or use `Channel(Channel.BUFFERED)`. Test with a fast model (TinyLlama 1.1B on CPU = 80+ tok/s). |
| mmap + LMK kills (Pitfall 4) | MEDIUM (change loading strategy) | Set `use_mmap=false` for devices with <8GB total RAM. Add `ActivityManager.MemoryInfo` check post-load. If RSS > 85% of total RAM, show warning and unload. |
| ProGuard strips JNI (Pitfall 7) | LOW (add keep rules, rebuild) | Add keep rules. Test release build. Add pre-launch checklist item: "Run release build → load model → verify no NoSuchMethodError." |
| STL conflict (Pitfall 8) | MEDIUM (recompile with c++_static) | Switch to `c++_static`. Verify no duplicate symbols with `readelf -s` on all .so files. If conflict exists, rebuild llama.cpp with matching NDK version. |
| `shouldStop` data race (Pitfall 5) | LOW (one-line fix) | Change `bool shouldStop` to `std::atomic<bool> shouldStop`. Add generation-state tracking (`isGenerating` flag set/cleared at generate() entry/exit). |
| Unload during generation crash (Pitfall 6) | MEDIUM (add generation-state protocol) | Add `LlamaEngine.isGenerating()` native method. `EngineManager.unloadCurrent()` checks this before unloading. If generating, call `stop()` and wait up to 5 seconds for generation to exit before forcing unload. |
| GGUF validation missing (Pitfall 10) | LOW (add header parse in Kotlin) | Implement GGUF header parser in Kotlin (magic number, version, field count validation). Add to `GgufMetadataParser` (already referenced in codebase). Call before every native load. |

---

## Pitfall-to-Phase Mapping

How roadmap phases should address these pitfalls.

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| JNI threading (P1) | Phase 1: JNI bridge build + model load | Design doc review: threading model must include JavaVM+GlobalRef pattern. Code review: no `env` capture in lambdas. |
| callbackFlow drops (P2) | Phase 2: Inference loop + streaming | Integration test: send long prompt (500 tokens generated), verify no gaps in output. Thread dump during generation shows no blocked threads. |
| Vulkan crash (P3) | Phase 3: GPU backend | Runtime capability test on 5+ physical devices across GPU vendors. Automatic fallback verified: force GPU failure → CPU works. |
| mmap + LMK (P4) | Phase 1: Memory sizing | Load 4GB model on 6GB device → verify warning dialog appears. Load on 8GB device → verify no LMK kill within 5 minutes of inference. |
| shouldStop race (P5) | Phase 2: Inference loop | Unit test: start generation on Thread A, call stop() from Thread B after 100ms, verify generation exits within 1 second. |
| Unload during generation (P6) | Phase 4: EngineManager integration | Integration test: start generation, switch model mid-stream, verify old model unloads only after generation stops, new model loads successfully. |
| ProGuard strips JNI (P7) | Phase 1: Build system | Release build smoke test: load model → generate → verify no JNI errors. Must pass before Phase 1 is complete. |
| STL conflict (P8) | Phase 1: CMake build | Load all .so files in same process (llama + LiteRT-LM). Verify no `UnsatisfiedLinkError` on `std::*` symbols. Check with `readelf -d *.so | grep NEEDED`. |
| EngineManager native sync (P9) | Phase 4: EngineManager integration | Concurrency test: rapid engine switches (10 switches in 5 seconds). No crash, no leaked native memory (check with `dumpsys meminfo`). |
| GGUF validation (P10) | Phase 1: Model loading | Unit test: load truncated file (first 1KB of valid GGUF), verify error message, no crash. Load file with valid magic but corrupted tensor data, verify graceful error. |

---

## Sources

- llama.cpp official docs `docs/android.md` — https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md (HIGH confidence — official source, 2026-05)
- llama.cpp build docs `docs/build.md` — Vulkan, CMake, backend configuration (HIGH confidence)
- llama.cpp C API via Context7 — model loading, context creation, inference loop (HIGH confidence)
- GGUF format spec via Context7 — header structure, magic number, KV pairs (HIGH confidence)
- llama.cpp quantized model sizes — Q4_K_M ~4.3GB for 7B, Q8_0 ~7.5GB for 7B (HIGH confidence, verified via Context7 `llama_model_quantize` docs)
- Android NDK JNI threading — `JNIEnv*` is thread-local, must use `JavaVM*` + `AttachCurrentThread` (HIGH confidence, official Android NDK documentation)
- Vulkan Android driver fragmentation — Qualcomm Adreno 7xx bugs with 16-bit storage, Mali SPIR-V compiler crashes (MEDIUM confidence — community reports, Khronos Vulkan Hardware Database)
- Qualcomm OpenCL deprecation on Android 12+ — official Qualcomm developer announcement (HIGH confidence)
- Android LMK behavior with mmap — file-backed pages counted in RSS/PSS metrics (HIGH confidence — AOSP memory management docs)
- `callbackFlow` channel capacity defaults — Kotlin coroutines official documentation (HIGH confidence)

---

*Pitfalls research for: GGUF native inference integration with existing Android app*
*Researched: 2026-05-05*
