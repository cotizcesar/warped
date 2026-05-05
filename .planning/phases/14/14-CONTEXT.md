# Phase 14: Vulkan GPU Backend - Context

**Gathered:** 2026-05-05
**Status:** Ready for planning

<domain>
## Phase Boundary

Enable `GGML_VULKAN=ON` compilation for llama.cpp, add runtime Vulkan availability detection with automatic CPU fallback, and display the active backend label during GGUF chat consistent with the existing LiteRT-LM backend indicator pattern.

In-scope: BACK-01 (CMake Vulkan compilation), BACK-02 (runtime detection + CPU fallback), BACK-03 (backend display).
</domain>

<decisions>
## Implementation Decisions

- **Vulkan enabled in CMake**: Set `GGML_VULKAN=ON` in CMakeLists.txt (conditional on arm64-v8a ABI). x86_64 emulator stays CPU-only.
- **Runtime Vulkan detection**: Try creating Vulkan instance + physical device check; if fails, `llama_model_params.n_gpu_layers = 0` (CPU only); if succeeds, `n_gpu_layers = 99` (offload all).
- **Silent fallback**: Failure to init Vulkan backend logs warning via Timber, continues with CPU. No user-facing error.
- **Backend label**: Read `llama_model_params.n_gpu_layers > 0` to indicate Vulkan vs CPU; emit via `EngineManager.getActiveEngine().backend`.
- **Run backend probe in a coroutine** with timeout to prevent ANR on driver stalls.
</decisions>

<code_context>
## Existing Code Insights
- **`BackendDetector`** already probes GPU backends for LiteRT-LM via EGL14 + OpenCL
- **`ActiveEngine`** data class has `backend: BackendType?` field
- **`EngineManager.switchToLlama()`** already returns metadata; add backend info
- **Chat UI** already shows backend label for LiteRT-LM; reuse the same widget
</code_context>
</deferred>
</deferred>
