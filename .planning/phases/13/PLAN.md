# Phase 13: Inference Core & Thread Safety — Plan

**Created:** 2026-05-05
**Status:** Ready to execute
**Requirements:** INFR-01, INFR-02, INFR-03, INFR-04, MEMS-03, MEMS-05, MEMS-06

## Plan Overview

| Plan | Task | Requirements | Type |
|------|------|-------------|------|
| 13.1 | C++ Inference Loop — Tokenize, Decode, Sample | INFR-01, INFR-02 | Native |
| 13.2 | Generation Parameters & Sampler Chain | INFR-04 | Native + Kotlin |
| 13.3 | TPS Counter & OOM Monitoring | INFR-03, MEMS-03 | Kotlin |
| 13.4 | Thread Safety — Concurrent Guard, Safe Unload | MEMS-05, MEMS-06 | Kotlin + Native |

---

### Plan 13.1: C++ Inference Loop

**Files:** `jni_bridge.cpp`, `jni_bridge.h`

Replace `generate()` stub with real inference:
1. Tokenize prompt via `llama_tokenize()`
2. Eval prompt tokens in a batch via `llama_decode()`
3. Sample next token via `llama_sample_token_greedy()` (default) or sampler chain
4. Detokenize via `llama_token_to_piece()`
5. Callback to Kotlin via `TokenCallback`
6. Continue until EOS, max tokens, or `shouldStop`

### Plan 13.2: Generation Parameters

**Files:** `jni_bridge.cpp`, `LlamaEngine.kt`

Pass `GenerationParameters` as JSON from Kotlin → C++. C++ parses and configures sampler chain. Also pass `max_tokens` and `seed` via JSON.

### Plan 13.3: TPS Counter & OOM Monitoring

**Files:** `LocalLlmProvider.kt`

Add TPS tracking in the flow: count tokens / elapsed time. Poll memory every 30s during long generations.

### Plan 13.4: Thread Safety

**Files:** `LlamaEngine.kt`

Add `@Synchronized` to `generate()`, `stop()`, `unload()`. Add concurrent call prevention.

---

**Execution order: 13.1 → 13.2 → 13.4 (parallel with 13.3)**
