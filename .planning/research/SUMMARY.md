# Project Research Summary

**Project:** Warped (v1.2 — GGUF Native Inference)
**Domain:** Android local LLM inference via llama.cpp JNI/NDK
**Researched:** 2026-05-05
**Confidence:** HIGH

## Executive Summary

Warped v1.2 adds real GGUF/llama.cpp inference to an existing Android app that already has a functional multi-engine architecture (LiteRT-LM + Remote providers). The existing codebase is **architecturally ready**: `LlamaEngine` wraps a JNI bridge to native C++, `EngineManager` coordinates engine lifecycle, `LocalLlmProvider` handles the chat pipeline, and `GgufMetadataParser` reads GGUF headers — but all native code is currently stubs. This milestone is about replacing stubs with real llama.cpp API calls compiled from source.

The integration requires **zero new Kotlin/Java Maven dependencies**. The entire native layer is added via CMake: source-include llama.cpp (pinned to tag `b9030`), compile it alongside the existing JNI bridge, and wire the real `llama.h` C API into `jni_bridge.cpp`. Optionally compile with `GGML_VULKAN=ON` for GPU acceleration on Snapdragon 8 Gen 2+ devices, with automatic CPU fallback on devices without Vulkan support.

**Key risks** center on memory and threading. A 7B Q4_K_M model needs ~6 GB RAM (1.3× file size for KV cache overhead), and Android's Low Memory Killer can kill the process during inference if `mmap` inflates RSS metrics. Thread safety between Java monitors (`@Synchronized`) and native code (`std::mutex`, `std::atomic<bool>`) requires careful design — particularly JNI global reference lifecycle, stop/unload sequencing to prevent SIGSEGV on freed memory, and Vulkan driver fragmentation across GPU vendors. Mitigations: pre-load memory checks with `ActivityManager.MemoryInfo`, dual-layer synchronization (Kotlin `@Synchronized` + C++ `std::mutex`), Vulkan runtime capability probing with automatic CPU fallback, and GGUF file validation before native load.

## Key Findings

### Stack Additions

From [STACK.md](./STACK.md) — **no new Kotlin/Java dependencies required.** The entire llama.cpp integration is native C++.

**Core technologies:**

| Technology | Purpose | Rationale |
|------------|---------|-----------|
| llama.cpp source (tag `b9030`) | GGUF inference engine | Build from source via CMake+NDK to ensure ABI consistency with the host project. Pre-built `.so` binaries risk NDK/STL version mismatches causing `UnsatisfiedLinkError`. |
| `GGML_VULKAN=ON` (CMake option) | GPU-accelerated inference on Android | 2-4× speedup on flagship phones. Separate build variant from CPU-only; runtime Vulkan probing with automatic fallback. |
| `c++_static` STL | Eliminate `libc++_shared` ABI conflicts | Prevents crashes when multiple native libraries (llama.cpp + LiteRT-LM) link against different NDK versions of the same shared STL. |
| `std::atomic<bool>` + `std::mutex` | Thread safety for native state | Replaces plain `bool shouldStop` in JNI bridge; prevents data races and SIGSEGV during stop/unload. |

**What was researched but rejected:**

- Pre-built `.so` from llama.cpp releases (ABI mismatch risk)
- `java-llama.cpp` wrapper library (duplicates existing JNI bridge architecture)
- Hexagon NPU backend (experimental, Snapdragon 8 Gen 3+ only)
- OpenCL backend (deprecated on Android 14+)
- Any new Kotlin/Java Maven dependency (none needed)

### Expected Features

From [FEATURES.md](./FEATURES.md) — feature landscape for GGUF inference across v1.2, v1.3, and v2+.

**Must have (v1.2 — table stakes):**

| Feature | Complexity | Status |
|---------|------------|--------|
| GGUF file browsing on HF (siblings with `.gguf` filtering) | MEDIUM | HF API integration exists; need siblings filtering |
| GGUF download with progress (WorkManager + OkHttp) | LOW | `ModelDownloadWorker` already handles downloads |
| GGUF model loading via JNI (real llama.cpp) | HIGH | Stubs exist; need native implementation |
| Streaming token generation (`callbackFlow` → Chat UI) | MEDIUM | Pattern exists; need real native callback |
| Basic generation parameters (temperature, threads, max_tokens) | LOW | Presets system exists; need JNI parameter passing |
| Memory check before loading (RAM vs model size + 30% overhead) | HIGH | `MemoryChecker` exists; needs `ActivityManager.MemoryInfo` integration |
| Model file management (view/delete) | LOW | `ModelsScreen` already handles this |
| CPU inference (works on all arm64 devices) | MEDIUM | Baseline — must work before GPU |

**Should have (v1.3 — differentiators):**

| Feature | Complexity | Differentiation |
|---------|------------|----------------|
| Vulkan GPU backend with auto CPU fallback | HIGH | PocketPal has limited GPU support |
| Rich GGUF metadata display (arch, params, context, license) | MEDIUM | `GgufMetadataParser` exists; needs enrichment |
| Quantization-aware recommendations ("Q4_K_M fits your device") | MEDIUM | No competitor does this on mobile |
| Stop generation button | LOW | `nativeStop` declared; needs UI |
| Token-per-second display | LOW | Already works for LiteRT-LM |
| Context size configuration | LOW | JNI `n_ctx` parameter |

**Defer (v2+):**

- Multimodal models (LLaVA, image pipelines)
- Speculative decoding (draft model acceleration)
- Cross-engine chat (switch GGUF ↔ Remote mid-conversation)
- Progressive download (chat before download completes)
- Model sharding (multi-file GGUF)
- LoRA adapter merging on-device

### Architecture Approach

From [ARCHITECTURE.md](./ARCHITECTURE.md) — the existing Clean Architecture is sound; integration points are stubs awaiting real native code.

**Integration architecture:**

```
ChatViewModel → ProviderRouter → LocalLlmProvider → LlamaEngine (Kotlin/JNI)
                                                         │
                                           jni_bridge.cpp (C++ glue)
                                                         │
                                              llama.cpp (native engine)
                                              ├─ GGML CPU backend (always)
                                              └─ GGML Vulkan backend (optional)
```

**Major components and their changes:**

1. **JNI Bridge (`jni_bridge.cpp` + `LlamaEngine.kt`)** — Replace stubs with real `llama_model_load_from_file()`, `llama_decode()`, `llama_sampler_sample()` calls. Add `@Synchronized` on `generate()`, `.buffer(Channel.BUFFERED)` on `callbackFlow`, Vulkan params to `loadModel()`, and JNI global reference management (`NewGlobalRef`/`DeleteGlobalRef`).

2. **EngineManager** — Pass Vulkan backend params (`nGpuLayers`, `useVulkan`) through `switchToLlama()`. Populate `ActiveEngine.backend` from `BackendDetector`. Enforce stop→wait→unload sequencing.

3. **Memory Management** — Replace file-size-only check with `ActivityManager.MemoryInfo` + 1.3× overhead multiplier. Add `android:largeHeap="true"` to manifest. Catch `OutOfMemoryError` in `preloadLocalModel()`.

4. **BackendDetector** — Add `isVulkanAvailable()` (probe `System.loadLibrary("vulkan")`). Prioritize Vulkan over OpenCL for llama.cpp path. Add `VulkanInfo` JNI query for device capabilities.

5. **GGUF Metadata Pipeline** — Complete `mapQuantization()` table for GGUF v3 quant types. Add post-download validation (magic bytes, architecture check). Use `llama_model_desc()` after load for accurate metadata.

6. **Chat Template Formatting** — Replace hardcoded ChatML template with `llama_chat_apply_template()` via JNI, reading the model's built-in template from GGUF metadata.

**Unchanged components:** `ProviderRouter` (routing already correct), `ChatRepository` (storage agnostic), `ChatScreen` (UI consumes `Flow<String>` regardless of provider), `HuggingFaceApi` (API unchanged), `LiteRTLmEngine` (independent path).

### Critical Pitfalls

From [PITFALLS.md](./PITFALLS.md) — top 5 of 10 documented pitfalls:

1. **JNI Thread Attachment Crash (SIGSEGV):** Capturing `JNIEnv*` in callbacks that may execute on different native threads (Vulkan compute, worker pools) causes stale-pointer crashes. **Mitigation:** Store `JavaVM*` at init; call `AttachCurrentThread`/`DetachCurrentThread` in every callback. Use `NewGlobalRef` for `jobject` callbacks.

2. **`callbackFlow` Buffer Overrun Kills Streaming Silently:** Default `Channel.RENDEZVOUS` (0 buffer) causes `trySend()` to silently drop tokens when the UI thread can't keep up with 50-100 tok/s output. **Mitigation:** Use `.buffer(Channel.BUFFERED)` or `trySendBlocking()`.

3. **Vulkan GPU Driver Fragmentation:** Qualcomm Adreno 7xx has buggy 16-bit storage; Mali SPIR-V compilers crash on certain shader patterns; Samsung Xclipse has unreliable `8bit_storage`. **Mitigation:** Build CPU-first, add Vulkan as optional separate variant, runtime capability probing, automatic CPU fallback on any Vulkan failure.

4. **mmap + Android LMK = SIGBUS:** Memory-mapped GGUF files inflate RSS, causing Android's Low Memory Killer to kill the process. If file is on removable storage and card disconnects, page fault triggers uncatchable SIGBUS. **Mitigation:** Store models exclusively in `context.filesDir/models/`. Add post-load memory verification. Consider `use_mmap=false` on <8GB devices.

5. **`shouldStop` Data Race:** Plain `bool shouldStop` without atomics is undefined behavior in C++. The compiler may hoist the read into a register, ignoring the `stop()` signal. **Mitigation:** Use `std::atomic<bool> shouldStop` with `memory_order_relaxed`.

**Additional critical pitfalls:** unloading engine during active generation (SIGSEGV on freed memory), ProGuard/R8 stripping JNI callback methods (release-only crash), `libc++_shared` STL conflict with LiteRT-LM native libs, `@Synchronized` not protecting native state, and missing GGUF validation causing crashes on corrupted files.

## Implications for Roadmap

Based on combined research across all four dimensions, here is the recommended phase structure:

### Phase 1: Native Foundation — CMake Build + Model Loading

**Rationale:** This is the prerequisite for everything. Without llama.cpp compiled and models loading successfully, no other phase can proceed. The CMake integration must be correct before any JNI code is written. Early detection of NDK/STL/ABI issues saves rewrites. ProGuard rules must be in place before release testing.

**Delivers:**
- llama.cpp compiled from source (tag `b9030`) via CMake+NDK for `arm64-v8a` and `x86_64`
- `libwarped_llama.so` with real llama.cpp symbols (not stubs)
- `jni_bridge.cpp` implements `nativeLoadModel`, `nativeUnload`, `nativeIsLoaded`, `nativeGetModelInfo`
- `LlamaEngine.kt` extended with `nGpuLayers`, `useVulkan` params
- `GgufMetadataParser` quantization map completed for GGUF v3
- `android:largeHeap="true"` in AndroidManifest
- ProGuard/R8 keep rules for all JNI callback methods
- `c++_static` STL strategy decided and verified

**Features from FEATURES.md:** GGUF model loading, model file management
**Avoids pitfalls:** #1 (JNI threading design decided before write), #7 (ProGuard rules), #8 (STL strategy), #10 (GGUF validation pre-load)

### Phase 2: Inference Core — Token Generation + Streaming

**Rationale:** Once models load, the next dependency is generating tokens and streaming them to the UI. This is the user-visible value proposition. The `callbackFlow` pattern and thread safety model must be proven here before adding GPU complexity.

**Delivers:**
- `nativeGenerate` with real `llama_tokenize` → `llama_decode` loop → `llama_sampler_sample`
- `callbackFlow` with `.buffer(Channel.BUFFERED)` for reliable token delivery
- `nativeStop` with `std::atomic<bool> shouldStop`
- `nativeSetGenerationParams` — temperature, topP, topK, repeatPenalty, maxTokens, seed
- First-token latency tracking, TPS counter
- `@Synchronized` on `LlamaEngine.generate()`, native `std::mutex` for generate state
- JNI global ref management (`NewGlobalRef`/`DeleteGlobalRef` on callback object)
- Stop → wait → unload sequencing in `EngineManager`

**Features from FEATURES.md:** Streaming token generation, generation parameters, stop button
**Avoids pitfalls:** #1 (JNI threading), #2 (callbackFlow buffer), #5 (shouldStop atomic), #6 (unload during generation), #9 (native mutex)

### Phase 3: Memory & Stability Hardening

**Rationale:** Memory management and validation are critical for production quality. Users will load models that are too large, import corrupted files, and background the app during inference. This phase prevents the #1 cause of bad reviews for mobile LLM apps: crashes under memory pressure.

**Delivers:**
- `ActivityManager.MemoryInfo` integration with 1.3× RAM overhead estimate
- Pre-load memory warning dialog ("model needs X GB, you have Y GB")
- `OutOfMemoryError` catch in `preloadLocalModel` with user-friendly message
- GGUF validation in `ModelDownloadWorker` + `ModelImportManager` (magic bytes, architecture check, size vs HF metadata)
- `handleTrimMemory` integration — unload on critical pressure, verify model state on return
- Post-load memory verification (if RSS > 85% of total RAM, warn user)
- `n_batch` adaptive sizing for 8GB vs 12GB+ devices

**Features from FEATURES.md:** Memory check before loading, offline chat stability
**Avoids pitfalls:** #4 (mmap + LMK), #10 (GGUF validation)

### Phase 4: GPU Acceleration — Vulkan Backend

**Rationale:** GPU acceleration is a differentiator but depends on the stability of CPU inference (Phase 1-3). Adding Vulkan before memory and threading are hardened multiplies debugging complexity. Vulkan driver fragmentation means this needs dedicated testing on physical devices.

**Delivers:**
- `GGML_VULKAN=ON` CMake build variant (separate `.so`, not default)
- `BackendDetector.isVulkanAvailable()` — `System.loadLibrary("vulkan")` probe
- `VulkanInfo` JNI query (device name, API version, compute memory, shading features)
- Runtime Vulkan capability probing (`VkPhysicalDeviceFeatures2` for `shaderFloat16`, `shaderInt8`, `16BitStorage`)
- Automatic CPU fallback on any Vulkan initialization failure
- `nGpuLayers` parameter pass-through (0 = CPU, 99 = all GPU, N = partial)
- User-facing backend indicator ("Running on Vulkan GPU" / "Running on CPU")
- Vulkan variant verified on 5+ physical devices across GPU vendors

**Features from FEATURES.md:** Vulkan GPU backend, backend selection UX
**Avoids pitfalls:** #3 (Vulkan driver fragmentation), #1 (Vulkan compute threads + JNI)

### Phase 5: UX Polish — Metadata, Recommendations & Engine Transparency

**Rationale:** With the core engine working end-to-end, this phase adds the competitive differentiators that make Warped feel like a premium product. Rich metadata display, quantization recommendations, and seamless engine switching are what set Warped apart from PocketPal AI.

**Delivers:**
- Full GGUF metadata display from header (architecture, param count, context length, tokenizer info, license)
- Quantization-aware recommendations: "Q4_K_M (4.6 GB) — recommended for your 8 GB device"
- Color-coded quant badges (green/yellow/red based on device RAM)
- `llama_chat_apply_template()` via JNI for correct per-model prompt formatting
- Parameter normalization layer (llama.cpp ↔ LiteRT-LM ↔ Remote param mapping)
- Enhanced model detail screen with pre-download metadata from HF API `config.json`
- Download integrity verification (SHA256 from HF API headers)

**Features from FEATURES.md:** Rich metadata display, quantization recommendations, transparent UX across engines, chat template formatting
**Implements from ARCHITECTURE.md:** Chat template formatting, metadata enrichment, parameter normalization

### Phase Ordering Rationale

The dependency chain uncovered by research is:

```
CMake build (Phase 1)
  └── Model loading (Phase 1)
       └── Token generation (Phase 2)
            ├── Memory hardening (Phase 3) — can parallelize with Phase 2 after basic generation works
            ├── GPU acceleration (Phase 4) — depends on stable CPU inference (Phase 2+3)
            └── UX polish (Phase 5) — depends on all engine features working
```

Phase 3 (memory hardening) can partially overlap with Phase 2 once basic token generation is verified — but should not be deferred past Phase 4 since Vulkan GPU debugging with untested memory management is exponentially harder.

### Research Flags

**Phases likely needing deeper research during planning:**
- **Phase 3 (Memory & Stability):** Android LMK behavior with mmap-backed models varies by device manufacturer (Samsung, Xiaomi, Pixel). May need device-specific tuning of `n_batch` and `use_mmap` settings. Research: `/gsd-research-phase` on Android memory management patterns for large native allocations.
- **Phase 4 (Vulkan Backend):** llama.cpp Vulkan backend documentation is sparse for Android. Need to verify shader compilation works for `arm64-v8a` target. May need to research specific GPU/driver combos for known crashes. Research: Vulkan capability probing strategy and device denylist architecture.
- **Phase 5 (Chat Templates):** `llama_chat_apply_template()` behavior varies across llama.cpp versions. Need to verify template extraction from GGUF metadata works for all target model architectures. Research: test with Llama 3, Mistral, Gemma, Phi, DeepSeek, Qwen GGUF files.

**Phases with well-documented patterns (skip research-phase):**
- **Phase 1 (CMake Build):** Well-documented by llama.cpp official `docs/android.md` and existing project STACK.md. Standard NDK cross-compilation.
- **Phase 2 (Inference Core):** llama.cpp C API is extensively documented. `callbackFlow` pattern is standard Kotlin coroutine practice. JNI global refs documented in Android NDK guides.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | **HIGH** | llama.cpp release tag b9030 verified (2026-05-05). Android arm64 binaries confirmed. CMake build flags confirmed from official `docs/android.md`. Zero new Maven dependencies confirmed by codebase analysis. |
| Features | **HIGH** | Feature landscape mapped against PocketPal AI and LM Studio. Dependency graph derived from existing codebase. Prioritization grounded in user value and implementation cost. Hugging Face API endpoints verified. |
| Architecture | **HIGH** | Based on direct codebase analysis of all 15+ relevant files (`LlamaEngine.kt`, `jni_bridge.cpp`, `EngineManager.kt`, etc.). Integration points and stubs identified with line-level precision. |
| Pitfalls | **HIGH** | Pitfalls identified from llama.cpp official docs, Android NDK JNI threading docs, Khronos Vulkan hardware database, Android LMK source documentation, and Kotlin coroutine docs. Recovery strategies mapped to each pitfall. |

**Overall confidence: HIGH** — all research dimensions verified against authoritative sources and direct codebase analysis. The Warped codebase is well-structured with clear integration points; the primary work is replacing stubs with real implementations, not redesigning the architecture.

### Gaps to Address

- **Hugging Face API siblings in search results:** The search endpoint may or may not include `siblings[]` per result. If it doesn't, loading the model detail screen requires a per-result detail API call. Handle during Phase 1 feature implementation.

- **`n_gpu_layers` optimal value per device:** llama.cpp's Vulkan backend on Android is relatively new. The ideal number of GPU-offloaded layers varies by device RAM, VRAM, and model size. Plan for a user-configurable setting with a "recommended" default derived from `VulkanInfo.maxComputeSharedMemorySize`.

- **Device testing coverage:** Vulkan GPU fragmentation requires testing on physical devices across GPU vendors (Adreno 6xx/7xx, Mali-G, Xclipse, PowerVR). Emulator Vulkan (Swiftshader) is not representative. Budget for device lab testing or beta program during Phase 4.

- **`llama_chat_apply_template()` API stability:** The function signature and behavior may change between llama.cpp versions. Our pinned tag (b9030) freezes the API, but future upgrades need regression testing against all supported model architectures.

- **LiteRT-LM + llama.cpp coexistence in same process:** Both engines load native `.so` files. Verify no symbol conflicts (both may link ggml). Check with `readelf -s` on all `.so` files before Phase 1 build finalization.

## Sources

### Primary (HIGH confidence)
- [llama.cpp official repository](https://github.com/ggml-org/llama.cpp) — Android build docs, C API, Vulkan backend, releases with Android arm64 binaries
- [llama.cpp Android build guide](https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md) — CMake flags, NDK cross-compilation, Vulkan configuration
- [GGUF format specification](https://github.com/ggml-org/ggml/blob/master/docs/gguf.md) — Header structure, magic number, KV metadata pairs, quantization types
- [Hugging Face Hub API](https://huggingface.co/docs/hub/en/api) — Model search, detail endpoint, siblings listing, SHA256 verification
- [Android NDK JNI documentation](https://developer.android.com/training/articles/perf-jni) — `JNIEnv*` thread-locality, `JavaVM*` + `AttachCurrentThread`, global vs local references
- **Warped codebase** (2026-05-05) — Direct analysis of `LlamaEngine.kt`, `jni_bridge.cpp/h`, `EngineManager.kt`, `BackendDetector.kt`, `LocalLlmProvider.kt`, `ChatViewModel.kt`, `CMakeLists.txt`, `GgufMetadataParser.kt`, `ModelDownloadWorker.kt`, `ModelImportManager.kt`, `gradle/libs.versions.toml`, `app/build.gradle.kts`

### Secondary (MEDIUM confidence)
- [PocketPal AI](https://github.com/a-ghorbani/pocketpal-ai) — Reference Android GGUF app using llama.rn; compared feature set, GPU support limitations
- [Khronos Vulkan Hardware Database](https://vulkan.gpuinfo.org/) — GPU capability matrix for Android devices; driver fragmentation patterns
- [Qualcomm OpenCL deprecation announcement](https://developer.qualcomm.com/) — Confirmed OpenCL removed from Android 12+ drivers

### Tertiary (LOW confidence — needs validation)
- Vulkan `VK_KHR_16bit_storage` reliability on Adreno 7xx — community reports of bugs; needs physical device testing
- Mali SPIR-V compiler crashes on specific shader patterns — reported in llama.cpp GitHub issues; needs verification on target devices

---

*Research completed: 2026-05-05*
*Ready for roadmap: yes*
