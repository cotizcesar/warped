# Phase 12: Model Loading & Memory Foundation — Plan

**Created:** 2026-05-05
**Status:** Ready to execute
**Requirements:** NTVL-02, NTVL-03, NTVL-04, MEMS-01, MEMS-02

## Plan Overview

| Plan | Task | Requirements | Type |
|------|------|-------------|------|
| 12.1 | JNI Bridge — Real llama.cpp Model Loading | NTVL-02 | Native/Kotlin |
| 12.2 | Load Progress & Metadata Callbacks | NTVL-03, NTVL-04 | Native/Kotlin |
| 12.3 | Pre-Load RAM Check & Warning Dialog | MEMS-01 | UI/Kotlin |
| 12.4 | EngineManager LLAMA_CPP Integration & Error Mapping | NTVL-02, NTVL-04 | Kotlin |

Note: MEMS-02 (onTrimMemory auto-unload) is already implemented in `WarpedApplication.kt:71-74` and `EngineManager.kt:140-156`.

---

### Plan 12.1: JNI Bridge — Real llama.cpp Model Loading

**Goal:** Replace TODO stubs in `jni_bridge.cpp` with real llama.cpp API calls. `nativeLoadModel` loads GGUF models and returns success/failure with descriptive error codes.

**Files to modify:**
1. `app/src/main/cpp/jni_bridge.cpp` — Replace stub implementations with real llama.cpp calls
2. `app/src/main/cpp/jni_bridge.h` — Add progress callback and error struct

**Implementation steps:**
1. In `jni_bridge.cpp`, implement `nativeLoadModel`:
   - Call `llama_model_default_params()` and `llama_load_model_from_file()`
   - Call `llama_context_default_params()` and `llama_new_context_with_model()`
   - Store model + context pointers in `LlamaEngine` singleton
   - Return error codes via JNI `jstring` for descriptive messages
2. Error mapping in C++:
   - OOM: catch `std::bad_alloc`, return "Out of memory"
   - Corrupt file: check `llama_model` is null, return "Corrupted model file"
   - Return error strings via JNI `NewStringUTF`
3. Update JNI native method signatures in `LlamaEngine.kt` to return `String?` instead of `Boolean`
4. Implement `nativeIsLoaded()`, `nativeUnload()`, `nativeStop()` with real llama.cpp calls

**Acceptance criteria:**
- `nativeLoadModel` calls real llama.cpp functions and returns model/context pointers
- `nativeUnload` frees all llama.cpp resources cleanly
- Error conditions return descriptive strings, not generic "failed"

---

### Plan 12.2: Load Progress & Metadata Callbacks

**Goal:** Native loading reports progress percentage to Kotlin. Model metadata is returned after successful load.

**Files to modify:**
1. `app/src/main/cpp/jni_bridge.h` — Add `ProgressCallback` struct/interface
2. `app/src/main/cpp/jni_bridge.cpp` — Call progress callback during load
3. `app/src/main/java/com/warped/data/local/inference/LlamaEngine.kt` — Add `LoadProgressCallback`, change `loadModel` to accept callback and return metadata

**Implementation steps:**
1. Add `LoadProgressCallback` interface to `LlamaEngine.kt`:
   ```kotlin
   interface LoadProgressCallback {
       fun onProgress(percent: Int, message: String)
   }
   ```
2. Add `nativeLoadModelWithProgress` — JNI call that takes path + callback, returns JSON metadata string
3. In C++, call `env->CallVoidMethod(callback, onProgressMethodId, percent, message)` during:
   - "Validating file..." (0%)
   - "Mapping to memory..." (25%)
   - "Initializing context..." (75%)
   - "Ready" (100%)
4. After load, populate metadata from `llama_model_desc()` and `llama_model_meta()`:
   - Architecture: `llama_model_desc(model).name`
   - Parameter count: from metadata KV "general.architecture" + block count
   - Context size: `llama_n_ctx(ctx)`
   - Quantization: from `llama_model_ftype(model)`
5. Return metadata as a JSON string via JNI; parse in Kotlin into `GgufMetadata` data class

**Acceptance criteria:**
- Loading progress flows from C++ -> JNI callback -> Kotlin `callbackFlow` -> ViewModel StateFlow -> Compose UI
- `LinearProgressIndicator` shows real percentage during load
- Metadata is accurate (architecture, params, context, quantization)

---

### Plan 12.3: Pre-Load RAM Check & Warning Dialog

**Goal:** Before loading a GGUF model, the app checks available RAM and warns the user with specific numbers if the model may not fit.

**Files to modify:**
1. `app/src/main/java/com/warped/data/local/inference/MemoryChecker.kt` — Add `checkRamAvailability()` with specific threshold for GGUF
2. `app/src/main/java/com/warped/data/local/inference/EngineManager.kt` — Update `switchToLlama()` to accept progress callback and return `Result`
3. UI: Add AlertDialog in Models screen before load call

**Implementation steps:**
1. Add `RamCheckResult` to `MemoryChecker.kt`:
   ```kotlin
   data class RamCheckResult(
       val hasEnough: Boolean,
       val availableBytes: Long,
       val requiredBytes: Long
   )
   fun checkGgufRam(fileSizeBytes: Long): RamCheckResult {
       val required = (fileSizeBytes * 1.3).toLong() // KV cache overhead
       val memInfo = getMemoryInfo()
       return RamCheckResult(
           hasEnough = required <= memInfo.availableBytes * 0.85,
           availableBytes = memInfo.availableBytes,
           requiredBytes = required
       )
   }
   ```
2. Update `EngineManager.switchToLlama()` → `switchToLlamaCpp()`:
   - Accept `LoadProgressCallback` parameter
   - Return `Result<GgufMetadata>` instead of `Unit`
   - Call `memoryChecker.checkGgufRam()` before loading
   - Throw descriptive error on failure
3. Add RAM warning dialog in the calling ViewModel/Composable (reuse existing `warningDialog` patterns if present, or create simple AlertDialog)

**Acceptance criteria:**
- Dialog shows: "This model needs ~5.8 GB, your device has 4.2 GB available. Loading may cause instability."
- "Continue anyway" button proceeds to load
- "Cancel" button dismisses

---

### Plan 12.4: Error Mapping & Metadata Display

**Goal:** Native load errors are mapped to Kotlin sealed class with user-facing messages. Loaded model metadata is displayed in the UI.

**Files to create/modify:**
1. `app/src/main/java/com/warped/data/local/inference/LlamaLoadError.kt` — NEW: Sealed class
2. `app/src/main/java/com/warped/data/local/inference/LlamaEngine.kt` — Update `loadModel` signature
3. UI composable — Metadata display card in Models screen

**Implementation steps:**
1. Create `LlamaLoadError` sealed class:
   ```kotlin
   sealed class LlamaLoadError(val userMessage: String) {
       class OutOfMemory : LlamaLoadError("Out of memory — try a smaller quantization")
       class CorruptedFile : LlamaLoadError("Corrupted model file — please re-download")
       class UnsupportedArchitecture : LlamaLoadError("Unsupported architecture — this model requires ARM64")
       class Unknown(message: String) : LlamaLoadError("Failed to load model: $message")
   }
   ```
2. Map native error strings to sealed class in `EngineManager.switchToLlamaCpp()`
3. Create metadata display card composable showing: architecture, parameter count, context size, quantization, file size
4. Integrate into the existing Models screen / model detail flow

**Acceptance criteria:**
- Native errors produce user-facing text matching ROADMAP examples
- Metadata displays correctly for loaded GGUF models
- Metadata card follows existing Material 3 patterns (`AssistInfoChip` for each field)
