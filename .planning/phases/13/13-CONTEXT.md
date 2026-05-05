# Phase 13: Inference Core & Thread Safety - Context

**Gathered:** 2026-05-05
**Status:** Ready for planning

<domain>
## Phase Boundary

Implement the full llama.cpp inference loop: tokenization → decode → sample → detokenize, wrapped in a `callbackFlow` on Kotlin side for streaming token-by-token responses. Support stop via `std::atomic<bool>`, real-time TPS display, and mapping of all 8 generation parameters to llama.cpp's sampler chain. The entire pipeline is thread-safe via `@Synchronized` Kotlin guards and `std::atomic` native guards.

In-scope: INFR-01 (streaming chat), INFR-02 (stop with partial response preserved), INFR-03 (TPS display), INFR-04 (8 generation parameters), MEMS-03 (OOM warning during generation), MEMS-05 (concurrent call prevention), MEMS-06 (safe unload protocol).

Out of scope: Vulkan GPU backend (Phase 14), cross-engine UX parity (Phase 15).

</domain>

<decisions>
## Implementation Decisions

### Inference Loop Architecture
- **C++ inference loop with JNI callback per token** — existing `TokenCallback` pattern in `jni_bridge.cpp`, generates one token at a time via `llama_decode` + `llama_sample_token_greedy`, calls back to Kotlin with token text + done flag
- **Greedy sampling** as default with temperature-driven sampler chain using `llama_sampler_chain_init`, `llama_sampler_init_top_k`, `llama_sampler_init_top_p`, `llama_sampler_init_temp`, `llama_sampler_init_dist`
- **`@Synchronized` guard on `LlamaEngine.generate()`** in Kotlin to serialize concurrent calls, matching existing pattern in `EngineManager`
- **`std::atomic<bool>`** for `shouldStop` — checked after each `llama_decode` call

### Thread Safety
- **Kotlin-side `@Synchronized`** on `generate()`, `stop()`, `unload()` — prevents concurrent generation attempts and stop-during-unload races
- **Native-side atomic stop** — `shouldStop.load()` checked after each decode step in the generation loop
- **Safe unload sequence**: `stop() → wait for generate thread → unload()` — stop sets atomic flag, thread exits cleanly, then model freed

### Generation Parameters
- **Map Kotlin `GenerationParameters` to llama.cpp sampler chain**:
  - `temperature` → `llama_sampler_init_temp`
  - `top_p` → `llama_sampler_init_top_p`
  - `top_k` → `llama_sampler_init_top_k`
  - `repeat_penalty` → `llama_sampler_init_penalties`
  - `max_tokens` → max loop iterations in C++ (default 512)
  - `context_size` → passed at load time (Phase 12 `nCtx`)
  - `seed` → `llama_sampler_init_dist` with seed
  - `threads` → passed at load time (Phase 12 `nThreads`)
- **Parameters passed via JSON string** from Kotlin to C++ through JNI for simplicity (avoids individual JNI fields)

### TPS Display & OOM Warnings
- **TPS computed in Kotlin** — track token count and elapsed time in `flow {}` builder; emit TPS via existing `StreamToken` or UI state
- **OOM monitoring during generation** — `MemoryChecker.checkGgufRam()` polled every 30 seconds via coroutine; warn if approaching threshold
- **Thread safety for unload during generation**: `@Volatile` for thread-safe state transitions in Kotlin

### the agent's Discretion
All decisions are based on existing codebase patterns, llama.cpp API conventions, and the ROADMAP success criteria. The agent has latitude on implementation details.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`LlamaEngine.generate()`** — already has `callbackFlow` wrapping `nativeGenerate` with `TokenCallback`; replace placeholder response with real inference loop
- **`LlamaEngine.stop()` / `LlamaEngine.unload()`** — already wired to native via JNI; works with atomic stop flag
- **`jni_bridge.cpp`** — `llama_model_ptr` and `llama_context_ptr` already stored after Phase 12 load
- **`LocalLlmProvider.chat()`** — already calls `llamaEngine.generate()` and streams tokens; no changes needed if generate() works correctly
- **`GenerationParameters`** (`domain/model/GenerationParameters.kt`) — existing data class with all 8 fields
- **`ParameterStore`** — `@Singleton StateFlow<GenerationParameters>`, already consumed by providers
- **`EngineManager`** — mutual exclusion via `@Synchronized`, `isEngineLoaded()` check
- **`MemoryChecker`** — `checkGgufRam()` from Phase 12, `getMemoryInfo()` for runtime monitoring
- **`LiteRTLmProvider`** — existing TPS counter and parameter mapping pattern to follow

### Established Patterns
- **`callbackFlow + awaitClose`** — existing pattern in `LlamaEngine.generate()` and `LiteRTLmProvider.chat()`
- **`@Synchronized` guards** — `EngineManager.kt` uses for all public methods; `LiteRTLmEngine` uses for lifecycle
- **`std::atomic<bool>`** — Phase 12 `jni_bridge.h` already uses `std::atomic<bool>` for `loaded` and `shouldStop`
- **StreamToken sealed interface** — `Delta`, `Done`, `Error`; used by all providers

### Integration Points
- **`ParameterStore.parameters`** — `StateFlow<GenerationParameters>` provides current params to `LocalLlmProvider.chat()`
- **`LlamaEngine.generate()`** → if `llamaEngine.generate()` works with real inference, `LocalLlmProvider.chat()` needs no changes
- **Chat screen stop button** → triggers `LlamaEngine.stop()` → `shouldStop = true` → C++ loop exits → `callbackFlow` closes
- **TPS counter** → integrate into `LocalLlmProvider.chat()` flow alongside token streaming, similar to LiteRT-LM path
</code_context>

<specifics>
## Specific Ideas

- Tokenize prompt using `llama_tokenize()` with BOS token addition; format prompt with model's chat template tokens
- Sampling chain should be: `llama_sampler_chain_init` → add `top_k` → add `top_p` → add `temp` → add `dist` with seed → add `penalties` for repeat_penalty
- TPS counter should use a 2-second sliding window to smooth out jitter: track tokens generated in last 2 seconds
- Concurrent generation prevention: `@Synchronized` on `generate()` rejects a new call while generation is in progress with `StreamToken.Error("Generation already in progress")`
- The `nativeGenerate` JNI call should serialize `GenerationParameters` as a JSON string passed alongside the prompt

</specifics>

<deferred>
## Deferred Ideas

- Chat template auto-detection from GGUF metadata (Phase 15 — cross-engine UX parity)
- KV cache management / context window strategies (simple context right now, full history via `llama_state_set_size` later)
- Batch decoding for prompt processing (single-token decoding for now, batch prompt eval in optimizations phase)
- Performance benchmarks and reporting (Phase 15)
</deferred>
