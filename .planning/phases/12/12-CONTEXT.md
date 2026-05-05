# Phase 12: Model Loading & Memory Foundation - Context

**Gathered:** 2026-05-05
**Status:** Ready for planning

<domain>
## Phase Boundary

This phase implements the JNI bridge for loading GGUF models into memory via llama.cpp, provides real-time loading progress feedback, displays rich model metadata after successful load, enforces pre-load RAM safety checks, and handles Android memory pressure by automatically unloading models.

In-scope: NTVL-02 (JNI nativeLoadModel with descriptive errors), NTVL-03 (loading progress), NTVL-04 (metadata display), MEMS-01 (pre-load RAM check with warning), MEMS-02 (onTrimMemory auto-unload).

Out of scope: streaming inference/token generation (Phase 13), Vulkan GPU (Phase 14), cross-engine UX parity (Phase 15).

</domain>

<decisions>
## Implementation Decisions

### JNI Bridge Architecture
- **C++ singleton facade** (`LlamaEngine::getInstance()`) wrapped by Kotlin `LlamaEngine` class — existing pattern in `jni_bridge.cpp`, isolates native state, handles thread safety in C++
- **JNI callback to Kotlin** for progress — `nativeLoadModel` accepts a `ProgressCallback` interface, C++ calls back periodically during mmap/init, Kotlin wraps in `callbackFlow` for coroutine compat
- **Pre-validate in Kotlin** before native call — reuse `GgufMetadataParser.validateHeader()` from Phase 11; native assumes valid input, avoids duplicating file I/O logic in C++
- **Static load in companion**: `System.loadLibrary("warped_llama")` in `companion object { init { ... } }` — one-time load, fails fast on class init

### Loading Progress UX
- **Model detail screen** — same screen that shows metadata after load, replace with progress during loading
- **LinearProgressIndicator with percentage** + status text ("Loading model... 45%") — matches existing download progress pattern
- **ViewModel coroutine** with `viewModelScope`, dispatches to `Dispatchers.IO` for native call
- **Continue loading on navigate away** — signal completion via StateFlow; ViewModel+StateFlow pattern used throughout app

### Memory Management & RAM Checks
- **Warning dialog with "Continue anyway"** option — shows specific numbers ("Needs ~5.8 GB, device has 4.2 GB")
- **Central `MemoryChecker` utility** — `checkRamAvailability(fileSizeBytes): RamCheckResult`, reusable across GGUF and LiteRT-LM load paths
- **Application-level `onTrimMemory`** in `WarpedApplication` calls `EngineManager.unloadAll()` on `TRIM_MEMORY_RUNNING_CRITICAL`
- **Silent unload + log** — no notification; when user returns, chat shows "Model was unloaded to free memory"

### Metadata Display & Error Handling
- **Model detail card in Models screen** — collapsible card with architecture, parameter count, context size, quantization, file size
- **`LlamaLoadError` sealed class in Kotlin** — `OutOfMemory`, `CorruptedFile`, `UnsupportedArchitecture`, `Unknown` with `userMessage` property
- **Parse fresh from GGUF header** each load via existing `GgufMetadataParser.parse()` — fast (<1ms), always accurate
- **New `switchToLlamaCpp()` on `EngineManager`** — follows existing `switchToLiteRT()` pattern, handles mutual exclusion

### the agent's Discretion
All decisions above were accepted by the user. Implementation details within the accepted direction are at the agent's discretion.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`EngineManager`** (`domain/model/ActiveEngine.kt` or `di/InferenceModule.kt` area) — `@Singleton`, `switchToLiteRT()`, `unloadCurrent()`, `ActiveEngine` data class tracking `type + modelPath + backend`. Extend with `switchToLlamaCpp()` method
- **`GgufMetadataParser`** — `parse()` and `validateHeader()` from Phase 11; reuse for pre-load validation and post-load metadata parsing
- **`GgufQuantizationParser`** — RAM estimate utilities from Phase 11; reuse in `MemoryChecker`
- **`ModelDownloadWorker`** — foreground notification pattern; reuse notification style for loading progress
- **`WarpedApplication`** — has `onTrimMemory` override point; add `EngineManager.unloadAll()` call
- **`LocalLlmProvider`** — existing provider implementing `LlmProvider`; extend for GGUF path
- **`InferenceModule.kt`** — Hilt DI for `LlamaEngine`, `EngineManager`, providers; add `MemoryChecker` and new `LlamaEngine` bindings

### Established Patterns
- **MVVM + StateFlow**: ViewModels expose `StateFlow<UiState>` to Compose via `collectAsStateWithLifecycle()`
- **Hilt @Singleton**: `EngineManager`, all providers are singletons; `LlamaEngine` follows same pattern
- **@Synchronized guards**: `EngineManager` uses `@Synchronized` for mutual exclusion; `LiteRTLmEngine` uses `@Synchronized` for lifecycle
- **callbackFlow + JNI**: App uses `callbackFlow {}` for streaming; use same pattern for loading progress callbacks
- **Mutual exclusion**: Only one local engine loaded at a time via `EngineManager`; `unloadCurrent()` before `switchToLlamaCpp()`

### Integration Points
- **`EngineManager.switchToLiteRT()`** → pattern to follow for `switchToLlamaCpp()`
- **`GgufMetadataParser.validateHeader()`** → call before native load
- **`WarpedApplication.onCreate()` / `onTrimMemory()`** → add memory pressure handler
- **Models screen → Model detail** → add loaded model metadata display component
- **`InferenceModule.kt`** → wire new `LlamaEngine` Kotlin wrapper, `MemoryChecker`
</code_context>

<specifics>
## Specific Ideas

- Loading progress callback should report phases: "Validating file...", "Mapping to memory...", "Initializing context..." for multi-GB models that take seconds to mmap
- The pre-load RAM warning dialog should use the exact format from the ROADMAP: "This model needs ~5.8 GB, your device has 4.2 GB available. Loading may cause instability."
- Error mapping should use `GgufMetadataParser.validateHeader()` result to differentiate "Corrupted file" from "Unsupported architecture" errors before native load
- The model metadata card should use the same `AssistInfoChip` pattern from `HuggingFaceScreen.kt` for each metadata field

</specifics>

<deferred>
## Deferred Ideas

- Model unloading progress indicator (Phase 13 — unload is fast with llama.cpp, no progress needed)
- GPU backend display on metadata card (Phase 14 — Vulkan support not yet integrated)
- Token count and generation speed metadata (Phase 13 — requires actual inference to measure)
- Cached model info for faster subsequent loads (Phase 15 — cross-engine parity feature)
</deferred>
