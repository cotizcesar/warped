# Stack Research: GGUF Native Inference (llama.cpp JNI/NDK)

**Domain:** Android local LLM inference via llama.cpp C++ integration
**Researched:** 2026-05-05
**Confidence:** HIGH

## Executive Summary

Adding real GGUF/llama.cpp inference requires **zero new Kotlin/Java Maven dependencies**. The entire integration is native C++: source-include the llama.cpp project, compile it alongside our existing JNI bridge via CMake+NDK, and wire the real `llama.h` API calls into the existing `jni_bridge.cpp` stubs. The Kotlin side (`LlamaEngine.kt`, `EngineManager.kt`, `LocalLlmProvider.kt`, `MemoryChecker.kt`, `GgufMetadataParser.kt`) is architecturally ready — it needs the native layer to stop being stubs and start being real.

The primary stack additions are: (1) llama.cpp source in the CMake build tree, (2) Vulkan backend compilation via `GGML_VULKAN=ON`, and (3) runtime Vulkan device probing to complement the existing `BackendDetector` (which currently only probes EGL/OpenCL for LiteRT-LM).

## Stack Changes Required

### 1. Native Build: llama.cpp Source Integration

**What changes:** The existing `app/src/main/cpp/CMakeLists.txt` must source-include the llama.cpp project and compile it alongside our JNI bridge.

**Recommended approach:** Git submodule at `app/src/main/cpp/llama.cpp/`, pinned to a known-good release tag.

| Aspect | Current State | Required Change |
|--------|--------------|-----------------|
| CMake project | `warped_llama` SHARED library from `jni_bridge.cpp` | Add `add_subdirectory(llama.cpp)` before `add_library(warped_llama ...)` |
| Source files | Only `jni_bridge.cpp`, `jni_bridge.h` | Same files, but `#include "llama.h"` and call real llama.cpp functions |
| Compile defs | `GGML_USE_CPU=1`, `GGML_USE_CPU_AARCH64=1` | Add `GGML_VULKAN` for GPU variant, keep CPU defs for CPU variant |
| Link libs | `android`, `log` | Add `llama` (the llama.cpp library target), `Vulkan::Vulkan` (for GPU variant) |
| Include dirs | `.` (current source dir) | Add `llama.cpp/include`, `llama.cpp/ggml/include`, `llama.cpp/common` |
| C++ standard | C++17 (already set) | No change — llama.cpp requires C++17 minimum |

**Critical build flags for Android (from official llama.cpp docs):**
```cmake
# In our CMakeLists.txt, before add_subdirectory(llama.cpp):
set(GGML_OPENMP OFF CACHE BOOL "OpenMP not well supported on Android NDK")
set(GGML_LLAMAFILE OFF CACHE BOOL "llamafile does not support Android")
set(GGML_VULKAN ON CACHE BOOL "Enable Vulkan GPU backend")
set(GGML_CPU ON CACHE BOOL "Enable CPU backend (always needed as fallback)")
set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build static libs, we'll link into our .so")

# Tell llama.cpp we're cross-compiling for Android (auto-detected by CMake toolchain)
# The NDK's android.toolchain.cmake sets CMAKE_SYSTEM_NAME=Android automatically
```

**Why build from source:** Pre-built `.so` binaries may use a different NDK version, STL (`libc++_shared` vs `libc++_static`), or ABI configuration than the host project. Mismatches cause cryptic `UnsatisfiedLinkError` at runtime. Building from source in the same CMake invocation ensures ABI consistency. This is already documented in the project's STACK.md as the recommended approach.

**llama.cpp release tag to pin:**
- Latest release as of 2026-05-05: **b9030** (released same day)
- Android arm64 CPU binaries confirmed published with every release
- Pin to a specific tag (not `master`) for reproducible builds
- Recommended initial pin: `b9030` (verify Vulkan shader compilation works for Android before freezing)

### 2. No New Kotlin/Java Maven Dependencies

**This is the key finding:** The entire llama.cpp integration is native C++. No new entries in `gradle/libs.versions.toml` are required. The existing Kotlin infrastructure handles everything:

| Existing Component | Role in GGUF Integration | Status |
|--------------------|--------------------------|--------|
| `LlamaEngine.kt` | JNI wrapper with `callbackFlow` for token streaming | **Ready** — needs real native code, not stubs |
| `EngineManager.kt` | Engine lifecycle coordination, mutual exclusion with LiteRT-LM | **Ready** — `switchToLlama()` already implemented |
| `LocalLlmProvider.kt` | Implements `LlmProvider`, formats chat prompts, delegates to `LlamaEngine` | **Ready** — needs real tokens flowing |
| `GgufMetadataParser.kt` | Parses GGUF binary header (magic, version, KV metadata) | **Complete** — extracts name, architecture, quantization, parameter count, context length |
| `MemoryChecker.kt` | Checks RAM availability, warns at 60%, blocks at 80% threshold | **Ready** — needs integration before model load |
| `BackendDetector.kt` | Probes EGL/OpenCL for LiteRT-LM GPU backend | **Needs extension** — add Vulkan probing |

### 3. Vulkan GPU Backend

**What it is:** llama.cpp's Vulkan backend offloads matrix multiplication (`ggml_mul_mat`) and attention computation to the device GPU, providing 2-4x speedup on flagship Android phones with good Vulkan drivers (Snapdragon 8 Gen 2+, Mali-G715+, Tensor G3+).

**Compilation:** Set `GGML_VULKAN=ON` before `add_subdirectory(llama.cpp)`. The llama.cpp Vulkan CMake module (`ggml/src/ggml-vulkan/CMakeLists.txt`) calls `find_package(Vulkan COMPONENTS glslc REQUIRED)`. On Android NDK:
- `Vulkan::Vulkan` loader library is available via NDK (API 24+)
- `glslc` shader compiler must be available on the host build machine (comes with the Vulkan SDK or Android NDK in `shader-tools/`)
- The Vulkan backend compiles SPIR-V shaders at build time (not runtime)
- Cross-compilation of shaders is handled by llama.cpp's vulkan-shaders-gen external project

**Build variants needed:**
```
arm64-v8a + CPU only        → GGML_VULKAN=OFF  (baseline, works everywhere)
arm64-v8a + Vulkan + CPU    → GGML_VULKAN=ON   (GPU acceleration for capable devices)
x86_64   + CPU only          → GGML_VULKAN=OFF  (emulator only)
```

**How to handle dual-variant builds:** Use Gradle's `productFlavors` or CMake variables to produce two `.so` files:
- `libwarped_llama_cpu.so` — CPU only (smaller, universal)
- `libwarped_llama_vulkan.so` — Vulkan+CPU (larger, faster on capable devices)

This is a build-time decision, not runtime. The app can check Vulkan availability at runtime and load the appropriate variant.

**Runtime Vulkan probing** (new code needed):
```kotlin
// Extend BackendDetector or create new VulkanDetector
// Probe: try to create a Vulkan instance via vkCreateInstance
// If successful → Vulkan available, use vulkan variant
// If not → CPU only
```
The NDK provides Vulkan headers via `<vulkan/vulkan.h>` and the loader lib via `libvulkan.so`. A minimal Vulkan instance creation test confirms driver availability without needing a window surface.

**Vulkan detection integration with existing BackendDetector:**
The existing `BackendDetector.probeBackend()` returns `BackendType.GPU` or `BackendType.CPU` based on EGL+OpenCL detection (designed for LiteRT-LM). For llama.cpp, we need a separate Vulkan probe since:
- OpenCL != Vulkan (different APIs, different driver stacks)
- A device might have EGL but broken Vulkan (common on older Mali GPUs)
- A device might have Vulkan but no OpenCL (newer Adreno GPUs)

Recommendation: Add `BackendType.VULKAN_GPU` or a separate `VulkanDetector` class alongside `BackendDetector`. The `EngineManager` can decide which backend to prefer based on what each engine supports.

### 4. JNI Bridge API — Real Implementation

The existing JNI bridge (`jni_bridge.h`, `jni_bridge.cpp`) has the correct structure but contains stub implementations. The real implementation must use the llama.cpp C API:

**Key API call sequence for model loading (replaces stub `loadModel()`):**
```cpp
// 1. Initialize backend (once per process)
llama_backend_init();

// 2. Load model from GGUF file
llama_model_params model_params = llama_model_default_params();
model_params.n_gpu_layers = nGpuLayers; // 0 = CPU only, -1 = all layers to GPU
model_params.use_mmap = true;           // Memory-map the file (saves RAM)
model_params.progress_callback = progressCallback; // Optional: loading progress
llama_model* model = llama_model_load_from_file(path.c_str(), model_params);

// 3. Create context
llama_context_params ctx_params = llama_context_default_params();
ctx_params.n_ctx = nCtx;                // Context window size
ctx_params.n_batch = 512;               // Maximum batch size
ctx_params.n_ubatch = 512;              // Physical batch size
ctx_params.n_threads = nThreads;
ctx_params.n_threads_batch = nThreads;  // Threads for prompt processing
ctx_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_AUTO;
llama_context* ctx = llama_init_from_model(model, ctx_params);

// 4. Set up sampler chain (temperature, top_p, top_k, etc.)
llama_sampler* smpl = llama_sampler_chain_init(params);
llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
llama_sampler_chain_add(smpl, llama_sampler_init_top_p(topP, 1));
llama_sampler_chain_add(smpl, llama_sampler_init_top_k(topK));
llama_sampler_chain_add(smpl, llama_sampler_init_dist(seed));
```

**Key API call sequence for token generation (replaces stub `generate()`):**
```cpp
// 1. Tokenize input prompt
int n_tokens = -llama_tokenize(vocab, prompt.c_str(), prompt.size(),
                                nullptr, 0, true, true);
std::vector<llama_token> tokens(n_tokens);
llama_tokenize(vocab, prompt.c_str(), prompt.size(),
                tokens.data(), tokens.size(), true, true);

// 2. Process prompt in batches
for (size_t i = 0; i < tokens.size(); i += n_batch) {
    int n_eval = std::min(n_batch, (int)(tokens.size() - i));
    llama_batch batch = llama_batch_init(n_eval, 0, 1);
    for (int j = 0; j < n_eval; j++) {
        batch.token[j] = tokens[i + j];
        batch.pos[j] = i + j;
        batch.n_seq_id[j] = 1;
        batch.seq_id[j][0] = 0;
        batch.logits[j] = (j == n_eval - 1) ? 1 : 0; // Only get logits for last token
    }
    llama_decode(ctx, batch);
    llama_batch_free(batch);
}

// 3. Generate tokens one by one with sampling
while (!shouldStop) {
    llama_token new_token = llama_sampler_sample(smpl, ctx, -1);
    
    if (llama_vocab_is_eog(vocab, new_token)) break; // End of generation
    
    std::string token_text = llama_vocab_get_text(vocab, new_token);
    callback(token_text, false); // Stream token to Kotlin via JNI callback
    
    // Submit single token for next iteration
    llama_batch batch = llama_batch_init(1, 0, 1);
    batch.token[0] = new_token;
    batch.pos[0] = n_cur++;
    batch.n_seq_id[0] = 1;
    batch.seq_id[0][0] = 0;
    batch.logits[0] = 1;
    llama_decode(ctx, batch);
    llama_batch_free(batch);
}
callback("", true); // Done signal
```

**JNI functions to expose (existing signatures, real implementations):**

| Native Function | llama.cpp API Called | Notes |
|----------------|---------------------|-------|
| `nativeLoadModel(path, nThreads, nCtx)` | `llama_model_load_from_file()` + `llama_init_from_model()` | Need additional params: `nGpuLayers`, `temperature`, `topP`, `topK` — or configure separately |
| `nativeGenerate(prompt, callback)` | `llama_tokenize()` → `llama_decode()` → `llama_sampler_sample()` → `llama_vocab_get_text()` | Run on background thread; stream tokens via JNI callback |
| `nativeStop()` | Set `shouldStop = true` | Interrupts the generation loop |
| `nativeUnload()` | `llama_free(ctx)` → `llama_model_free(model)` | Also free sampler chain |
| `nativeIsLoaded()` | Check `model != nullptr && ctx != nullptr` | No change needed |
| `nativeGetModelInfo()` | `llama_model_desc()`, `llama_model_n_params()`, `llama_model_size()` | Real metadata, not stub JSON |

**New JNI functions needed:**
- `nativeSetGenerationParams(temperature, topP, topK, repeatPenalty, maxTokens, seed)` — configure sampler chain dynamically (avoids reloading model on parameter changes)
- `nativeGetContextSize()` — returns `llama_n_ctx()` for memory calculations
- `nativeTokenCount(text)` — token counting via `llama_tokenize()` for UI display

### 5. Chat Template Formatting

**Current state:** `LocalLlmProvider.buildPrompt()` uses hardcoded `<|system|>`, `<|user|>`, `<|assistant|>` format (ChatML-style). This works for Llama 3, Mistral, and Qwen models but not for Gemma, Phi, Command R, or DeepSeek.

**What llama.cpp provides:** `llama_model_chat_template(model, nullptr)` returns the model's built-in chat template (Jinja2 format for modern GGUF models). The C API also provides:
- `llama_chat_apply_template()` — applies the template to a list of messages, producing the formatted prompt string complete with BOS/EOS tokens

**Recommendation:** Use the model's built-in chat template via JNI:
```cpp
// New JNI function: nativeFormatChat(messagesJson) → formatted prompt
// Uses llama_chat_apply_template() internally
```

This ensures correct formatting for ALL model architectures without hardcoded templates. Fall back to the existing ChatML format for older GGUF files that don't include a template.

### 6. Stack Changes Summary Table

| Layer | Addition | Version / Source | Rationale |
|-------|----------|------------------|-----------|
| Native build | llama.cpp source | git tag `b9030` (2026-05-05) | GGUF inference engine. Build from source for NDK ABI consistency |
| Native build | Vulkan SDK (host) | Latest (for `glslc` shader compiler) | Required to compile SPIR-V shaders for GPU backend |
| Native build | `GGML_VULKAN=ON` | CMake option | Enables GPU-accelerated inference on Android |
| Native build | `GGML_OPENMP=OFF` | CMake option | OpenMP not well supported on Android NDK |
| Native build | `GGML_LLAMAFILE=OFF` | CMake option | llamafile does not support Android |
| Kotlin code | `VulkanDetector` or extend `BackendDetector` | New class | Runtime Vulkan device probing (instance creation test) |
| Kotlin code | Generation parameter setters on `LlamaEngine` | New methods | `setGenerationParams(temp, topP, ...)` — configure sampler without reloading model |
| Kotlin code | Chat template integration | JNI bridge extension | `nativeFormatChat()` using `llama_chat_apply_template()` |
| JNI bridge | Real llama.cpp API calls | Replace stubs | `llama_model_load_from_file()`, `llama_decode()`, `llama_sampler_sample()`, etc. |
| CMake | `add_subdirectory(llama.cpp)` | In existing CMakeLists.txt | Source-include llama.cpp to compile alongside JNI bridge |
| ProGuard/R8 | Keep rules for JNI | `proguard-rules.pro` | Keep `LlamaEngine` and its native methods (already covered by existing rules) |

## What NOT to Add

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| **java-llama.cpp** (kherud/java-llama.cpp) | Adds a Java wrapper layer on top of llama.cpp. We already have our own JNI bridge (`LlamaEngine.kt` + `jni_bridge.cpp`). The Java wrapper would duplicate our architecture and add maintenance burden. | Our existing JNI bridge |
| **Pre-built `.so` libraries** | Risk of NDK/STL/ABI mismatch causing UnsatisfiedLinkError. Already documented as an anti-pattern in project STACK.md. | Build from source via CMake+NDK |
| **Additional JSON library** (Gson, Moshi) | Kotlinx Serialization is already in the project for all JSON needs. GGUF metadata is binary, not JSON. | Kotlinx Serialization (already present) |
| **OpenCL backend for llama.cpp** | llama.cpp supports OpenCL (`GGML_OPENCL=ON`) but Vulkan has broader Android GPU support and is the actively maintained backend. OpenCL is deprecated on Android 14+. | Vulkan (`GGML_VULKAN=ON`) |
| **MLC-LLM, ExecuTorch, or other inference engines** | Project decision already made: llama.cpp is the GGUF engine. LiteRT-LM handles `.litertlm` format. Adding more engines adds complexity without value. | llama.cpp (already decided) |
| **Custom GGUF parser library** | `GgufMetadataParser.kt` already reads the GGUF binary header (magic, version, KV pairs) and extracts all needed metadata. llama.cpp's `llama_model_meta_*()` functions are an alternative but require the model to be loaded (slow for directory browsing). | Keep existing `GgufMetadataParser` for browsing; use `llama_model_desc()` after loading for accurate data |
| **Hexagon NPU backend** (`GGML_HEXAGON`) | Experimental, Snapdragon 8 Gen 3+ only, not yet stable. Defer to future milestone. | Vulkan GPU (broadly supported) + CPU fallback |

## Version Compatibility

| Component | Version | Compatible With | Notes |
|-----------|---------|-----------------|-------|
| llama.cpp source | b9030 (2026-05-05) | NDK 27+, CMake 3.22.1+, C++17 | Pinned tag ensures reproducible builds |
| Vulkan SDK (host) | 1.3.x | NDK 27+ (provides Vulkan headers + loader) | Only `glslc` compiler needed on build host |
| Kotlin | 2.1.10 | All existing project dependencies | No change required |
| AGP | 9.0.0 | NDK 27+, CMake 3.22.1 | No change required |
| CMake | 3.22.1 (existing) | llama.cpp requires 3.14+ | No version bump needed |
| NDK | 27+ (existing) | llama.cpp requires NDK r23+ | No version bump needed |
| minSdk | 28 (existing) | Vulkan requires API 24+ | No version bump needed |

## Installation / Build Configuration

**No new `gradle/libs.versions.toml` entries needed.** The llama.cpp integration is entirely in CMake and C++ source.

**Build steps for developer:**
```bash
# 1. Add llama.cpp as git submodule
cd app/src/main/cpp
git submodule add https://github.com/ggml-org/llama.cpp.git
cd llama.cpp
git checkout b9030
cd ../../../../..

# 2. Install Vulkan SDK on build host (for glslc shader compiler)
# Linux: apt install vulkan-sdk
# macOS: brew install vulkan-sdk  
# Windows: download from vulkan.lunarg.com

# 3. Build as normal
./gradlew assembleDebug
```

## Sources

| Source | Confidence | What Was Verified |
|--------|-----------|-------------------|
| [llama.cpp releases](https://github.com/ggml-org/llama.cpp/releases) | **HIGH** | Latest tag b9030 (2026-05-05), Android arm64 CPU binaries published every release |
| [llama.cpp Android build docs](https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md) | **HIGH** | Official CMake flags for Android NDK cross-compilation: `GGML_OPENMP=OFF`, `GGML_LLAMAFILE=OFF`, `-march=armv8.7a` |
| [llama.h C API](https://raw.githubusercontent.com/ggml-org/llama.cpp/master/include/llama.h) | **HIGH** | Full API surface: `llama_model_load_from_file()`, `llama_init_from_model()`, `llama_decode()`, `llama_sampler_sample()`, `llama_chat_apply_template()` |
| [llama.cpp CMakeLists.txt](https://raw.githubusercontent.com/ggml-org/llama.cpp/master/CMakeLists.txt) | **HIGH** | Project structure: `ggml/` subdirectory, `src/` for llama lib, `common/` for utilities. `GGML_VULKAN` option at ggml level |
| [ggml-vulkan CMakeLists.txt](https://raw.githubusercontent.com/ggml-org/llama.cpp/master/ggml/src/ggml-vulkan/CMakeLists.txt) | **HIGH** | Vulkan backend requires `find_package(Vulkan COMPONENTS glslc)`, compiles SPIR-V shaders at build time |
| [ggml CMakeLists.txt](https://raw.githubusercontent.com/ggml-org/llama.cpp/master/ggml/CMakeLists.txt) | **HIGH** | All backend options defined here. `GGML_VULKAN=OFF` by default, must be set ON before `add_subdirectory` |
| Existing codebase analysis | **HIGH** | `LlamaEngine.kt`, `EngineManager.kt`, `LocalLlmProvider.kt`, `GgufMetadataParser.kt`, `MemoryChecker.kt`, `BackendDetector.kt` all present and architecturally ready |
| Existing `gradle/libs.versions.toml` | **HIGH** | Verified no new Kotlin/Java dependencies needed for GGUF integration |
| Existing `app/build.gradle.kts` | **HIGH** | CMake 3.22.1, NDK 27+, `arm64-v8a`/`x86_64` ABIs already configured |

---

*Stack research for: GGUF native inference via llama.cpp JNI/NDK*
*Researched: 2026-05-05*
