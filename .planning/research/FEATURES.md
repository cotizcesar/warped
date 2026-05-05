# Feature Research: GGUF Native Inference on Android

**Domain:** Mobile LLM inference via llama.cpp/GGUF on Android
**Researched:** 2026-05-05
**Confidence:** HIGH

---

## Feature Landscape

### Table Stakes (Users Expect These)

Features users assume exist in any GGUF-capable Android app. Missing any = product feels broken or incomplete.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| **Download GGUF from HF Hub** | Every GGUF app (LM Studio, PocketPal, Jan) downloads from HF. Users expect to paste a repo URL or browse models and download `.gguf` files. | MEDIUM | **Already partially built:** HuggingFaceApi exists, HuggingFaceScreen lists models, ModelDownloadWorker handles downloads. What's missing: listing `.gguf` siblings per model so users see quant files, not just model cards. The HF API returns `siblings[]` on `/api/models/{model_id}` — filter by `.gguf` extension. |
| **Browse GGUF quantizations (Q4_K_M, Q5_K_M, etc.)** | GGUF repos contain multiple quantization files (F16, Q8_0, Q6_K, Q5_K_M, Q4_K_M, IQ4_XS, etc.). Users must choose the right quant for their device's RAM. LM Studio, PocketPal, and Ollama all show quant options. | MEDIUM | When drilling into a model repo, list all `.gguf` siblings with their file size. Parse the quant type from filename (e.g., `Q4_K_M` from `model-Q4_K_M.gguf`) or from GGUF header metadata. Show estimated RAM requirement: `file_size * 1.2` for KV cache overhead. |
| **Show model metadata (arch, param count, context length)** | Users need to see architecture (llama, gemma, qwen), parameter count (1B, 7B, 8B), and context length before downloading/loading. Every GGUF tool displays this. | LOW | **Already partially built:** `GgufMetadataParser` reads `general.name`, `general.architecture`, `general.file_type`, and `llama.context_length` from GGUF headers. **Gap:** parameter count estimation is inaccurate (divides block_count by 1000). Fix: parse `general.size_label` ("8B") or estimate from tensor dimensions. Metadata can be parsed from downloaded file OR from HF API `config.json` if available. |
| **Load GGUF model into memory** | Core function: tap "Load" → llama.cpp loads model weights into RAM → ready for chat. Expected to work within seconds for small models. | HIGH | **Already built:** `LlamaEngine.loadModel()` calls `nativeLoadModel(path, nThreads, nCtx)` via JNI. `EngineManager.switchToLlama()` coordinates unloading current engine first. **Gap:** `nativeLoadModel` returns Boolean but no error details. Need richer failure info (OOM? corrupt file? unsupported arch?). |
| **Stream token generation to chat UI** | Tokens appear incrementally as they're generated. Same UX as remote providers and LiteRT-LM. Users expect this from any LLM chat. | MEDIUM | **Already built:** `LlamaEngine.generate()` wraps JNI callback in `callbackFlow<String>`. Chat UI collects tokens via Flow. **Gap:** Need to verify the JNI implementation actually works — `nativeGenerate` is declared as external but C++ implementation was noted as not fully built in the milestone description ("llama not implemented"). |
| **Generation parameter support (temp, top_p, etc.)** | Users expect to tweak generation params. The existing Presets system already provides this for remote and LiteRT-LM. | LOW | **Already built:** Presets screen, `ParameterStore`, `Preset` domain model. LlamaEngine's `nativeGenerate` signature currently takes only `prompt` and `callback` — need to extend JNI to pass sampler params (temperature, top_p, top_k, repeat_penalty, max_tokens, seed) to llama.cpp's `llama_sample_*` functions. |
| **Model file management (view, delete)** | Users accumulate multiple GGUF files (5-20 GB each). Need to see what's downloaded and delete to free space. | LOW | **Already built:** `ModelsScreen` shows downloaded models. Room `models` table tracks file paths. Delete should remove file + Room record + any associated conversations. |
| **Memory pressure handling (OOM graceful)** | Android can kill the app if RAM runs out. Users expect graceful degradation: warn if insufficient RAM before loading, unload on background, never crash-during-inference. | HIGH | **Already partially built:** `MemoryChecker` exists, `EngineManager.handleTrimMemory()` unloads engine on critical pressure. **Gaps:** No pre-load memory check ("this model needs 5GB, you have 3GB free — continue?"). No progressive memory warning during long generations. LLM inference can't be paused mid-token. |
| **Import GGUF from device storage** | Users may download GGUF files outside the app (browser, file transfer). Must be able to pick a `.gguf` file and register it. | LOW | **Already built:** `ModelImportManager` handles file picker + copy to app storage. |
| **Offline chat with loaded model** | Once model is loaded, chat works without internet. Core value proposition of local inference. | LOW | Already inherits from existing offline-first architecture. No remote calls needed once model is in memory. |

### Differentiators (Competitive Advantage)

Features that set Warped apart from PocketPal AI, LM Studio desktop, and other GGUF apps.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **Transparent UX across model formats (GGUF ↔ LiteRT-LM ↔ Remote)** | User doesn't care which engine is loaded — they just chat. The same chat UI, same conversation history, same presets work regardless of whether the model is GGUF, LiteRT-LM, or remote. No "this feature only works with engine X" surprises. LM Studio and PocketPal lack multi-engine transparency. | MEDIUM | **Already partially built:** `EngineManager` abstracts engine switching. Chat UI is format-agnostic (it just displays tokens). **Gap:** Need engine-aware preset application (llama.cpp needs `n_threads`, context_size, repeat_penalty; remote needs `max_tokens`; LiteRT-LM has `topK` not `top_k`). ParameterStore should normalize or the UI should show only relevant params per engine. |
| **CPU + GPU backend selection with runtime detection** | llama.cpp supports Vulkan GPU acceleration on Android. Users with Snapdragon 8 Gen 2+ get 2-4x faster inference. Seamless fallback to CPU if Vulkan unavailable. PocketPal uses llama.rn which has limited GPU support. | HIGH | **Already partially built:** `BackendDetector` probes device capabilities. **Gap:** llama.cpp's Android JNI wrapper (`LlamaEngine`) doesn't currently expose backend selection. Need to build llama.cpp with `GGML_VULKAN=ON` and detect Vulkan runtime availability. Add backend selector to UI (CPU / Vulkan auto / Vulkan force). **Warning:** Vulkan on Android is device-dependent — some GPUs crash with certain models. Need try-catch + automatic CPU fallback. |
| **Quantization-aware model browser with RAM estimation** | Show exactly which quantizations are available per model, file sizes, estimated RAM usage, and "best for your device" recommendations. Compares available RAM vs requirements. PocketPal shows quants but doesn't estimate RAM. | MEDIUM | Build on the existing HF model detail endpoint. Parse `.gguf` filenames for quant types. Cross-reference with `ActivityManager.MemoryInfo`. Recommend: "Q4_K_M (4.6 GB) — fits your 8 GB device" or "Q8_0 (8.0 GB) — too large for your device". |
| **GGUF metadata display without loading** | Show model architecture, parameter count, context length, tokenizer info, license — all from GGUF header without loading the full model into RAM. Fast (<1s). LM Studio does this well; PocketPal doesn't. | MEDIUM | **Already partially built:** `GgufMetadataParser` reads GGUF headers. **Gaps:** Parser misses many standardized metadata keys (`general.size_label`, `general.license`, `tokenizer.ggml.bos_token_id`, `[arch].attention.head_count`, `[arch].expert_count`). Fix the parser and display the info on model detail screen. For remote models (not yet downloaded), use HF API `config.json` and model card metadata as fallback. |
| **Chat across engines in same conversation** | Most apps lock you to one engine per chat. Warped already has `engine_type` on conversations — could allow switching a conversation mid-stream (e.g., start with fast local model, switch to powerful remote model for complex query). | HIGH | This is a stretch differentiator for v1.3+. Requires: conversation history serialization between engines, prompt template translation, context window management during engine swap. Out of scope for v1.2. |
| **Progressive download: chat earlier with partial model** | llama.cpp supports loading models as they download (mmap-based). User could start chatting before download completes. No competitor does this on mobile. | VERY HIGH | This is research-grade. Requires: mmap the download target, llama.cpp must tolerate incomplete files, tokenizer must be in early bytes of GGUF. Unclear if HF CDN supports byte-range serving for this pattern. Defer to v2+. |

### Anti-Features (Commonly Requested, Often Problematic)

Features that seem good but create maintenance nightmares or degrade UX.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| **Auto-download latest quant** | "Just pick the best quant for me" | "Best" depends on user priorities (speed vs quality vs RAM). Q4_K_M is the community default but some users want Q8_0 for quality or IQ3_M for smaller footprint. Auto-selection leads to "why did you download 8GB when I have 4GB RAM?" | Show a recommended quant (Q4_K_M for balance, IQ4_XS for tight RAM) with explanation, but let user choose. Highlight the recommended one. |
| **Quantize models on-device** | "I want to convert my FP16 model to Q4_K_M on my phone" | llama.cpp's `llama-quantize` tool requires 2x RAM of the model (reads FP16, writes quant). A 7B FP16 model is ~14 GB — impossible on most phones. Even if technically possible (swap to disk), it would take hours and drain battery. This is a desktop operation. | Users download pre-quantized GGUF files from Hugging Face. There are thousands available. |
| **Convert non-GGUF models to GGUF** | "I have a .safetensors model, convert it" | Same RAM problem as quantization (need full model in float32 memory). Conversion scripts are Python-based and don't run on Android. Even if ported to C++, the RAM limitation is fatal. | Download pre-converted GGUF from HF. Use HF's `GGUF-my-repo` space to convert models on the cloud via the web. |
| **Run multiple GGUF models simultaneously** | "Compare model A vs B responses" | Two 7B Q4_K_M models = ~10 GB RAM. Only flagship phones with 16 GB RAM could do this, and Android would likely kill the app. llama.cpp doesn't support multi-model contexts well. | Users switch between models via EngineManager (unload A → load B). For comparison, show previous responses from chat history. |
| **Auto-update models** | "Keep my models current" | GGUF models are static files — they don't "update" like apps. A new quant of the same model is a different file with a different hash. Auto-updating would re-download GBs of data unexpectedly, potentially on mobile data. | Notify users when the HF repo has a newer commit. Let them choose to re-download. Show model version in metadata. |
| **Model sharing between apps** | "Other apps should use my downloaded models" | Android's scoped storage makes this complex. Sharing via content:// URIs is possible but llama.cpp needs file paths for mmap. Security risk: other apps could read model weights. | Users can manually share GGUF files via Android's share sheet. Warped's download folder is app-private for security. |
| **Merge LoRA adapters on-device** | "Apply a LoRA to my model" | Same RAM problem: merging requires loading both model and adapter. LoRA merging tools are Python-based. On Android this is technically possible but the UX complexity (managing separate LoRA files, applying, verifying) outweighs the value for v1.2. | Defer to v2+. Until then, users download pre-merged models from HF. |
| **Prompt template auto-detection from filename** | "The app should know this is a ChatML model from the filename" | GGUF filenames are non-standardized. Some models include chat template in GGUF metadata (`tokenizer.chat_template`), others don't. Auto-detection from filename is brittle and leads to silent failures (wrong template → garbled output). | Read `tokenizer.chat_template` from GGUF metadata when available. For models without it, let user select template or use llama.cpp's built-in auto-detection (`llama_chat_apply_template`). |

---

## Feature Dependencies

```
GGUF Download from HF
    └──requires──> HuggingFaceApi.siblings parsing (already exists, needs .gguf filtering)
                        └──requires──> ModelDetail endpoint (already built)

Quantization Browser (show file sizes, quant types)
    └──requires──> GGUF Download from HF (above)
    └──enhances──> Model Metadata Display (shows quant info pre-download)

GGUF Model Loading (llama.cpp JNI)
    └──requires──> Native library compiled (llama.cpp CMake + NDK)
    └──requires──> EngineManager.unloadCurrent() (already built)
    └──requires──> MemoryChecker pre-load validation (needs enhancement)
    └──enables──> Streaming Token Generation

Streaming Token Generation
    └──requires──> GGUF Model Loading (above)
    └──requires──> JNI callback → callbackFlow wrapper (declared, needs C++ implementation)
    └──integrates──> Chat UI (already handles Flow<String> tokens)

CPU vs GPU Backend Selection
    └──requires──> Vulkan-compiled llama.cpp .so
    └──requires──> BackendDetector Vulkan probes (needs implementation)
    └──requires──> EngineManager backend parameter (currently only has type+path)

Memory Pressure Handling (OOM Prevention)
    └──requires──> MemoryChecker.preLoadCheck() (needs implementation)
    └──requires──> ActivityManager.MemoryInfo integration (platform API, straightforward)
    └──enhances──> All model loading paths

Model Metadata Display (architecture, params, quant)
    └──requires──> GgufMetadataParser (already exists, needs enrichment)
    └──can-consume──> Downloaded GGUF file (parse header, don't load full model)
    └──alternative──> HF API config.json + cardData (for remote browsing, pre-download)

Generation Parameters (temp, top_p, etc.)
    └──requires──> JNI extended to pass sampler params (needs C++ implementation)
    └──integrates──> Presets system (already built)
    └──notes──> Parameter names differ between engines; need normalization layer

Transparent UX Across Engines
    └──enhances──> All chat features
    └──requires──> Parameter normalization (mapping between llama.cpp ↔ LiteRT-LM ↔ remote params)
    └──notes──> Chat UI already format-agnostic; parameter normalization is the remaining work
```

---

## MVP Definition (v1.2 GGUF)

### Launch With (v1.2)

Minimum required to claim GGUF inference works end-to-end:

- [ ] **GGUF file browsing on HF** — List `.gguf` files per model, show file sizes, parse quant type from filename — *this unblocks everything else; models must be findable before they can be downloaded*
- [ ] **GGUF download with progress** — Download `.gguf` files via existing ModelDownloadWorker/WorkManager with progress notifications — *blocks model loading (need files on device)*
- [ ] **GGUF model loading via JNI** — llama.cpp loads model into RAM, returns success/failure with error details — *core feature; without this there's no GGUF inference*
- [ ] **Streaming token generation** — `callbackFlow<String>` from JNI → Chat UI renders tokens incrementally — *user-visible value; this is what they came for*
- [ ] **Basic generation parameters** — At minimum: temperature, max_tokens, threads. Passed from existing presets to JNI — *users expect to control output style*
- [ ] **Memory check before loading** — Warn if insufficient RAM; refuse to load instead of crashing — *OOM is the #1 cause of bad reviews for mobile LLM apps*
- [ ] **Model file management** — View downloaded models, delete to free space — *users will download multiple models; need to manage storage*
- [ ] **CPU inference** — Core path; works on all arm64 devices — *baseline that must work before GPU*

### Add After Validation (v1.3)

Features to add once CPU inference is stable:

- [ ] **Vulkan GPU backend** — Build llama.cpp with GGML_VULKAN=ON, detect Vulkan at runtime, fallback to CPU
- [ ] **Rich GGUF metadata display** — Parse full metadata from header: architecture, param count, context length, tokenizer info, license
- [ ] **Quantization-aware recommendations** — "Q4_K_M recommended for your 8 GB device" based on available RAM
- [ ] **Generation stop button** — Interrupt inference mid-generation (nativeStop already declared, needs UI button)
- [ ] **Token-per-second display** — Real-time generation speed metric (already implemented for LiteRT-LM; add for llama.cpp)
- [ ] **Context size configuration** — Allow users to increase/decrease context window (trade RAM for longer conversations)

### Future Consideration (v2+)

Features to defer until product-market fit is established:

- [ ] **Multimodal models (LLaVA, etc.)** — Add image understanding; requires image encoding pipeline, different model loading path
- [ ] **Speculative decoding** — Draft model acceleration; adds complexity for marginal gain on mobile
- [ ] **Split model across CPU+GPU** — Hybrid inference for large models; requires complex memory orchestration
- [ ] **Model sharding (multi-file GGUF)** — Handle `00001-of-00005.gguf` sharded models; uncommon on mobile-sized models
- [ ] **RPC backend (remote llama.cpp server)** — Treat a remote llama.cpp instance as a "local" engine; architecture overlap with remote providers

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority | Depends On |
|---------|------------|---------------------|----------|------------|
| GGUF file browsing on HF | HIGH | MEDIUM | P1 | HF API siblings parsing |
| GGUF download with progress | HIGH | LOW (mostly exists) | P1 | ModelDownloadWorker |
| GGUF model loading via JNI | CRITICAL | HIGH | P1 | llama.cpp NDK build |
| Streaming token generation | CRITICAL | MEDIUM | P1 | JNI callback implementation |
| Memory check before loading | HIGH | MEDIUM | P1 | MemoryChecker enhancement |
| Basic generation params (temp, threads) | HIGH | LOW | P1 | JNI parameter passing |
| Model file management (view/delete) | HIGH | LOW (exists) | P1 | ModelsScreen |
| CPU backend | CRITICAL | MEDIUM | P1 | C++ CMake build |
| Vulkan GPU backend | MEDIUM | HIGH | P2 | GGML_VULKAN build, detection |
| Quantization recommendations | MEDIUM | MEDIUM | P2 | RAM estimation logic |
| Rich GGUF metadata display | MEDIUM | MEDIUM | P2 | GgufMetadataParser enrichment |
| Stop generation button | MEDIUM | LOW | P2 | UI button + nativeStop |
| Token-per-second display | LOW | LOW | P2 | Token counting in callback |
| Context size configuration | MEDIUM | LOW | P2 | JNI n_ctx parameter |
| Transparent UX across engines | HIGH | MEDIUM | P3 | Parameter normalization |
| Multimodal models | MEDIUM | VERY HIGH | P3 | Image pipeline |
| Speculative decoding | LOW | VERY HIGH | P3 | Draft model management |

**Priority key:**
- P1: Must have for v1.2 launch (GGUF inference end-to-end)
- P2: Should have for v1.3 (polish + GPU)
- P3: Nice to have, deferred to v2+

---

## Competitor Feature Analysis

| Feature | LM Studio (Desktop) | PocketPal AI (Android) | Warped (Our Approach) |
|---------|---------------------|------------------------|----------------------|
| GGUF download from HF | Full HF integration, shows all quants, download queue | HF search + quant selection, background download | Same HF API, sibling filtering for `.gguf`, WorkManager foreground download |
| Quantization browser | Shows all quants with size, allows filtering | Lists quants per model, file size shown | Show quants + estimated RAM + "fits your device" recommendation |
| Model metadata | Architecture, params, context, quant type, license | Basic model name + quant file size | Full GGUF header parse + HF card fallback for pre-download browsing |
| CPU inference | Yes (default) | Yes (llama.rn) | Yes (JNI/NDK from source) |
| GPU inference | Metal (Apple), CUDA, Vulkan | Limited (llama.rn Vulkan support varies) | Vulkan with runtime detection + auto CPU fallback |
| Memory management | Shows VRAM/RAM usage | Auto offload on background | Pre-load RAM check + trimMemory handler + OOM graceful fail |
| Multi-engine | Only llama.cpp | Only llama.cpp | llama.cpp + LiteRT-LM + Remote, same chat UI |
| Generation params | Full sampler config | Temperature, BOS, system prompt | Full preset system with engine-aware normalization |
| Streaming UX | Token-by-token display | Token-by-token display | Same `Flow<String>` pattern across all engines |
| Offline chat | Yes | Yes | Yes (core value prop) |
| Model file management | Delete, rename, reveal in finder | Delete, import local files | Delete, import via file picker, Room-tracked metadata |

---

## Sources

| Source | URL | Confidence |
|--------|-----|------------|
| llama.cpp README (backends, features, build) | https://github.com/ggml-org/llama.cpp | HIGH |
| llama.cpp Android build guide | https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md | HIGH |
| GGUF format specification | https://github.com/ggml-org/ggml/blob/master/docs/gguf.md | HIGH |
| llama.cpp quantization guide | https://github.com/ggml-org/llama.cpp/blob/master/tools/quantize/README.md | HIGH |
| PocketPal AI (reference Android GGUF app) | https://github.com/a-ghorbani/pocketpal-ai | HIGH |
| HF Hub API (models, siblings, search) | https://huggingface.co/docs/hub/en/api | HIGH |
| HF GGUF model library (174k+ models) | https://huggingface.co/models?library=gguf&sort=downloads | HIGH |
| Existing Warped codebase | `app/src/main/java/com/warped/` (v1.1 shipped code) | HIGH |
| LlamaEngine.kt (JNI declarations) | `app/.../inference/LlamaEngine.kt` | HIGH |
| EngineManager.kt (engine switching) | `app/.../inference/EngineManager.kt` | HIGH |
| GgufMetadataParser.kt (header parser) | `app/.../inference/GgufMetadataParser.kt` | HIGH |
| HuggingFaceApi.kt (HF REST client) | `app/.../api/HuggingFaceApi.kt` | HIGH |

---

*Feature research for: GGUF native inference on Android (Warped v1.2)*
*Researched: 2026-05-05*
