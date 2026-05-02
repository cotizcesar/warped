# Feature Research: LiteRT-LM Integration for Warped v1.1

**Domain:** Second local LLM inference engine (LiteRT-LM) into existing Android LLM chat app
**Researched:** 2026-05-02
**Confidence:** HIGH

## Feature Landscape

### Table Stakes (Users Expect These)

Features users assume exist when told "this app supports LiteRT-LM." Missing these = the integration feels broken.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| **Search/filter `.litertlm` models from Hugging Face** | User discovers models to download. Without search, there's no model acquisition path. | MEDIUM | Requires extending `HuggingFaceApi` with `filter=litert-lm&author=litert-community` query mode. The HF Hub API returns `library_name:"litert-lm"` and tag `litert-lm` on community models. Existing search endpoint can be parameterized — add a `SearchMode` enum (GGUF vs LITERT). litert-community org has ~93 models (includes non-LLM like ASR/vision). Need additional filter for `pipeline_tag:text-generation` to surface only chat models. |
| **Download `.litertlm` models with progress and pause/resume** | Users expect large-model downloads (2–15 GB) to show progress, survive network interruptions, and not block the UI. | MEDIUM | `.litertlm` files are single blobs (not sharded like GGUF). Existing `ModelDownloadManager` already handles OkHttp streaming, `Range` header resume, and progress tracking. **Reuse entire pipeline** — only change: detect `.litertlm` extension, skip GGUF metadata parsing (no GGML magic bytes), store in same `models/` directory or separate `models/litertlm/` subdirectory. No architecture change needed. |
| **Import local `.litertlm` files from device storage** | Users may have downloaded `.litertlm` files on desktop or from other sources. Import is the alternative acquisition path. | LOW | Reuses existing `ActivityResultContracts.OpenDocument` file picker. Same `ModelImportManager` pattern: validate file extension (`.litertlm`) and size, copy to app-private storage. No GGUF metadata parsing needed — LiteRT-LM validates file via `Capabilities` class (JNI). Existing import flow is format-agnostic at the import level; only validation logic differs. |
| **Load `.litertlm` model and chat with streaming** | Core value proposition. Must produce tokens streaming to UI within seconds of sending a message. | HIGH | **Biggest implementation item.** Requires a new `LiteRtLmProvider` implementing `LlmProvider` (same `chat(): Flow<StreamToken>` contract). Wraps `Engine(EngineConfig(...))` → `engine.initialize()` (blocking, ~2-15 seconds, must be on `Dispatchers.Default`) → `engine.createConversation(config)` → `conversation.sendMessageAsync(text).collect { message -> emit(StreamToken.Delta(message.text)) }`. LiteRT-LM's `sendMessageAsync` returns `Flow<Message>` natively — no callbackFlow wrapper needed (unlike llama.cpp JNI). Conversation handles chat template automatically (no manual `<|system|>` prompt construction). |
| **Generation parameters for LiteRT-LM** | Users expect to configure temperature, top_k, top_p for any LLM. Missing params = feels unpolished. | MEDIUM | LiteRT-LM uses `SamplerConfig(topK: Int, topP: Double, temperature: Double, seed: Int)`. This is a **different parameter surface** than llama.cpp's `GenerationParameters` (which has `repeatPenalty`, `threads`, `contextSize`, `reasoningEnabled` — params LiteRT-LM doesn't expose). Need a **separate parameter model** (`LiteRtParameters`) with mapping to `SamplerConfig`. The existing `GenerationParameters` class is llama.cpp-specific — do not force-fit. Presets infrastructure needs extending to handle dual parameter models. |
| **Model management: view, delete LiteRT-LM models** | Users need to see what's downloaded, how large it is, and free up space. | LOW | Existing `LocalModel` entity and `LocalModelRepository` already handle list/view/delete. Need a `format` field (e.g., `ModelFormat.GGUF` vs `ModelFormat.LITERT`) to distinguish types. Delete is identical (delete file + Room row). View details: LiteRT-LM models don't expose quantization/architecture metadata via simple header parsing like GGUF — use `Capabilities` class for model inspection. |
| **Backend selection with auto-detection** | User expects best performance automatically. Explicit selection is power-user feature. | MEDIUM | LiteRT-LM backends: `Backend.CPU()`, `Backend.GPU()`, `Backend.NPU(nativeLibraryDir)`. Auto-detection sequence: try `Backend.GPU()` first → catch initialization failure → fall back to `Backend.CPU()`. GPU requires `<uses-native-library>` entries in manifest for `libvndksupport.so` and `libOpenCL.so` (both `required="false"` to allow CPU fallback). NPU is chip-specific (Qualcomm Snapdragon) — **defer NPU auto-detection** to a later phase; expose as explicit power-user option. |
| **Separate UI tabs for GGUF vs LiteRT-LM models** | Users must not confuse which format a model is. Mixing them in one list = "which one do I download?" confusion. | LOW | Add `ModelFormatTab` enum in `ModelsScreen`: `GGUF` / `LITERT_LM`. Each tab filters `LocalModel` by format. `HuggingFaceScreen` already has search — add format selector (GGUF / LiteRT-LM) that switches the HF API query filter and target directory. Low complexity: Compose `TabRow` + filtered lists. |

### Differentiators (Competitive Advantage)

Features that set Warped apart from other Android LLM apps. Not required for basic functionality, but drive the "best local LLM Android app" positioning.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **Dual-engine local inference (llama.cpp + LiteRT-LM)** | No other Android app offers both GGUF and `.litertlm` inference in one interface. User has maximum model choice: GGUF's massive ecosystem (50K+ models) + LiteRT-LM's Google-optimized performance for Gemma, Llama, Phi, Qwen. | Already inherent in architecture | This is the architectural differentiator. LiteRT-LM models run faster on Android (Google's production engine powers Chrome/Chromebook/Pixel). GGUF models offer wider selection and community tuning. Each engine has its own `LlmProvider` implementation — provider interface was designed for this. |
| **GPU acceleration with automatic CPU fallback** | llama.cpp on Android is CPU-only (Vulkan support is experimental/device-dependent). LiteRT-LM's GPU backend (`Backend.GPU()`) provides **significantly** faster inference on devices with Adreno/Mali GPUs. Automatic fallback means users don't need to understand backends. | MEDIUM | Implement `BackendDetector` utility that attempts `Engine(EngineConfig(backend = Backend.GPU()))` initialization. On `LiteRtLmJniException`, retry with `Backend.CPU()`. Surface detected backend in UI (e.g., "Running on GPU" badge). GPU requires OpenCL — most Android devices with API 28+ have it, but some budget devices don't. |
| **Auto-handled chat templates** | llama.cpp requires manual prompt formatting (the app currently builds `<\|system\|>/<\|user\|>/<\|assistant\|>` tags manually). LiteRT-LM's `Conversation` API applies the model's Jinja chat template **automatically** based on the `.litertlm` file's embedded template. No prompt engineering needed — the app sends structured `Message` objects and the engine renders them correctly. | Already provided by LiteRT-LM API | This is a UX differentiator: Gemma models use different prompt formats than Llama models. With LiteRT-LM, the app doesn't need to know. With llama.cpp, the app currently uses a hardcoded ChatML-style format that may not match all GGUF models. |
| **Cached model loading** | Second launch of the same `.litertlm` model is significantly faster (seconds vs 10+ seconds) when `cacheDir` is configured. llama.cpp always loads from scratch. | Already in `EngineConfig.cacheDir` | Set `cacheDir = context.cacheDir.path` in `EngineConfig`. LiteRT-LM compiles/caches the model graph to this directory on first load. This is an automatic performance win — no extra implementation needed beyond passing the parameter. |

### Anti-Features (Commonly Requested, Often Problematic)

Features that seem good but create problems. Documenting these prevents scope creep.

| Anti-Feature | Why Requested | Why Problematic | Alternative |
|--------------|---------------|-----------------|-------------|
| **Multi-modality (vision/audio) in v1.1** | "LiteRT-LM supports images and audio, why not add it?" | `EngineConfig` requires separate `visionBackend` and `audioBackend` configurations. Multi-modal models (Gemma3n, Qwen-VL) are larger and have different memory profiles. Mixing text chat + vision in the same v1.1 milestone explodes scope. The existing app supports text-only chat — adding multi-modality requires UI changes (image attachment, camera capture), new permission handling, and audio recording. | **Defer to v1.2+**. Ship text chat with LiteRT-LM first. Vision/audio can be a separate milestone with its own research phase. |
| **Tool use / function calling** | "LiteRT-LM supports tools, let's add agent capabilities." | LiteRT-LM's `ToolSet` API (annotated Kotlin functions, OpenAPI spec tools) is impressive but adds a completely new interaction paradigm. Tool calling requires: tool registration UI, execution sandboxing, error handling for tool failures, and a different conversation flow (tool_call → tool_response loops). This is an agent feature, not a chat feature. | **Defer to v2.0**. Focus v1.1 on chat parity with existing llama.cpp remote providers. |
| **NPU auto-detection in v1.1** | "Snapdragon 8 Gen 3+ has Hexagon NPU, let's auto-detect it." | NPU support requires chip-specific `.litertlm` files (e.g., `_qualcomm_sm8750.litertlm`), bundling NPU native libraries, and device-SoC detection. The NPU ecosystem is fragmented: Qualcomm, MediaTek, and Samsung each have different NPU stacks. Auto-detection would be unreliable without per-SoC testing. | **Expose NPU as manual power-user option**. Add a "Use NPU" toggle (off by default) with device compatibility notes. Auto-detection comes in v1.3+ after real-world NPU testing. |
| **Converting GGUF → .litertlm on-device** | "I have GGUF models, can I convert them to get LiteRT-LM performance?" | GGUF and `.litertlm` are fundamentally different formats. GGUF uses llama.cpp's custom serialization; `.litertlm` is a TFLite-based format. On-device conversion would require running a full model conversion pipeline (PyTorch → TFLite → .litertlm) on a phone. Computationally infeasible and not supported by LiteRT-LM tooling. | **User downloads `.litertlm` models from Hugging Face**. The litert-community hosts pre-converted models for all major architectures. |
| **Running both engines simultaneously** | "Load a GGUF model and a LiteRT-LM model at the same time so I can switch faster." | Android phones have 8–16 GB RAM. A single 7B model uses 4-5 GB. Loading two models simultaneously would consume 8-10 GB, causing OOM kills and system instability. | **Unload current model when switching engines**. The existing `LocalLlmProvider` already calls `llamaEngine.unload()` — add symmetric `engine.close()` for LiteRT-LM. |
| **Session API (raw token control) for chat** | "The Session API gives more control over token generation." | LiteRT-LM's `Session` API provides `runPrefill`/`runDecode`/`generateContentStream` at a lower level than `Conversation`. It bypasses chat templates, tool calling, and message formatting. For chat, this requires reimplementing all the template logic that `Conversation` provides for free. | **Use `Conversation` API exclusively for chat**. The `Session` API is for advanced use cases (benchmarking, constrained generation) that aren't in scope. |
| **Auto-switching engine based on model format** | "Just pick the right engine automatically — the user shouldn't have to choose." | Users need to make informed decisions: GGUF models often have different quantization levels (Q4_K_M vs Q8_0), different parameter counts, and different prompt formats than LiteRT-LM equivalents. Auto-switching hides this complexity and leads to "why is this slower?" confusion. | **Explicit user choice via separate tabs and a clear engine indicator**. This is what LM Studio does on desktop (separate sections for local vs remote). |

## Feature Dependencies

```
[LiteRT-LM Search/Filters on HF]
    └──requires──> [HuggingFaceApi mode parameter (GGUF/LITERT)]

[LiteRT-LM Download with Progress/Pause]
    └──requires──> [LiteRT-LM Search/Filters on HF]
    └──requires──> [ModelDownloadManager .litertlm extension support]

[LiteRT-LM Import from Storage]
    └──enhances──> [LiteRT-LM Download]  (alternative acquisition path)
    └──requires──> [LocalModel format field (GGUF/LITERT)]

[LiteRT-LM Chat with Streaming]
    └──requires──> [LiteRT-LM Download OR Import]  (model must exist locally)
    └──requires──> [LiteRtLmProvider implementing LlmProvider]
    └──requires──> [Backend auto-detection logic]
    └──requires──> [SamplerConfig parameter model]
    └──requires──> [Gradle dependency: litertlm-android]

[Separate UI Tabs GGUF vs LiteRT-LM]
    └──requires──> [LocalModel format field (GGUF/LITERT)]
    └──enhances──> [LiteRT-LM Search/Filters on HF]  (tab switches search mode)

[LiteRT-LM Generation Parameters]
    └──requires──> [LiteRtParameters data class]
    └──enhances──> [LiteRT-LM Chat with Streaming]
    └──enhances──> [Presets infrastructure]  (dual parameter models)

[Backend Auto-Detection (GPU → CPU)]
    └──requires──> [LiteRT-LM Chat with Streaming]
    └──requires──> [AndroidManifest native library entries]
    └──conflicts──> [NPU auto-detection in v1.1]  (defer NPU)

[Model Management (view/delete LiteRT-LM)]
    └──requires──> [LiteRT-LM Download OR Import]  (models must exist to manage)
    └──requires──> [LocalModel format field (GGUF/LITERT)]
```

### Dependency Notes

- **LiteRT-LM Chat requires Engine to be initialized:** `Engine.initialize()` is blocking and takes 2-15 seconds. Must be called on `Dispatchers.Default` with loading UI state. Engine is single-threaded — only one `Conversation` active per engine at a time.
- **Conversation is single-turn:** Each `conversation.sendMessageAsync()` call handles one assistant response. For multi-turn chat, the same `Conversation` instance persists across turns (it maintains history internally via the engine). Do NOT create a new Conversation per message — this loses context.
- **SamplerConfig is per-conversation-creation, not per-message:** Defined in `ConversationConfig` when calling `engine.createConversation(config)`. To change params mid-session, close the conversation and create a new one.
- **GPU backend needs manifest changes:** Without `<uses-native-library android:name="libOpenCL.so" android:required="false"/>`, GPU backend fails silently or crashes. This is a build-time requirement, not runtime.
- **Separate tabs require `ModelFormat` enum on `LocalModel`:** This is a database schema change. Requires a Room migration adding a `format` column with default `GGUF` for existing models.
- **Backend detection is try/catch based:** There is no `Backend.isAvailable()` API. Detection requires attempting `Engine.initialize()` with the desired backend and catching failures.

## MVP Definition

### Launch With (v1.1)

Minimum viable LiteRT-LM integration — what validates that users want this second engine.

- [ ] **Search `.litertlm` models on Hugging Face** — User types a model name, sees litert-community models with `.litertlm` files. Requires `HuggingFaceApi` mode parameter.
- [ ] **Download `.litertlm` models** — Progress bar, pause/resume, survives app backgrounding. Reuses existing `ModelDownloadManager` with `.litertlm` extension handling.
- [ ] **Load and chat with streaming** — User selects a downloaded `.litertlm` model, types a message, sees tokens streaming in real time. Core value proposition.
- [ ] **Auto-detect GPU with CPU fallback** — No user configuration needed. App tries GPU, falls back to CPU. Shows which backend is active.
- [ ] **Separate GGUF / LiteRT-LM tabs in Models screen** — User clearly sees two ecosystems. Cannot accidentally load a GGUF model with LiteRT-LM or vice versa.

### Add After Validation (v1.1.x)

Features to add once core chat flow works and is stable.

- [ ] **Import local `.litertlm` files** — Trigger: users asking "I have a .litertlm file on my phone, how do I use it?" Low risk, simple addition.
- [ ] **LiteRT-LM generation parameters UI** — Trigger: users wanting to adjust temperature/top_k/top_p for LiteRT-LM models. Separate parameter model; sliders/pickers in chat screen.
- [ ] **Delete LiteRT-LM models** — Trigger: users running out of storage. Reuses existing delete flow.
- [ ] **Cached model loading** — Trigger: users complaining about slow model load times. Already supported by `EngineConfig.cacheDir`, just needs to be enabled.

### Future Consideration (v2.0+)

Features to defer until the dual-engine chat experience is battle-tested.

- [ ] **LiteRT-LM presets** — Separate preset model for `SamplerConfig`. Requires preset infrastructure redesign to handle heterogeneous parameter types.
- [ ] **NPU acceleration** — Manual toggle first, auto-detection later. Requires per-SoC testing.
- [ ] **Multi-modality (vision/audio)** — Requires UI changes (image attachment), new models, permission handling.
- [ ] **Tool use / function calling** — Different interaction paradigm. Requires agent architecture.
- [ ] **LiteRT-LM Session API for advanced users** — Raw token control for power users.
- [ ] **Structured output / constrained generation** — JSON mode, grammar constraints.

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Load and chat with .litertlm (streaming) | HIGH | HIGH | P1 |
| Search .litertlm models on HF | HIGH | MEDIUM | P1 |
| Download .litertlm with progress | HIGH | MEDIUM | P1 |
| Auto-detect GPU + CPU fallback | HIGH | MEDIUM | P1 |
| Separate GGUF/LiteRT-LM UI tabs | HIGH | LOW | P1 |
| Generation parameters for LiteRT-LM | MEDIUM | MEDIUM | P2 |
| Import local .litertlm files | MEDIUM | LOW | P2 |
| Model management (view/delete) | MEDIUM | LOW | P2 |
| Cached model loading | MEDIUM | LOW (free with API) | P2 |
| NPU backend support | LOW | HIGH | P3 |
| Multi-modality support | MEDIUM | HIGH | P3 |
| Tool use / function calling | MEDIUM | HIGH | P3 |
| LiteRT-LM presets | LOW | MEDIUM | P3 |

**Priority key:**
- P1: Must have for v1.1 launch — validates the second engine
- P2: Should have in v1.1.x — polish without delaying launch
- P3: Nice to have, v2.0+ consideration

## Competitor Feature Analysis

| Feature | Google AI Edge Gallery | LM Studio (Desktop) | Other Android LLM Apps (ChatterUI, Maid) | Our Approach (Warped) |
|---------|------------------------|---------------------|------------------------------------------|----------------------|
| Local inference engine | LiteRT-LM only | llama.cpp only | Usually one engine (llama.cpp or MLC) | **Both** llama.cpp + LiteRT-LM |
| Model format | .litertlm only | GGUF only | GGUF (or MLX on iOS) | **Both** GGUF + .litertlm |
| GPU acceleration | Yes (LiteRT-LM native) | Yes (Metal/CUDA/Vulkan) | Varies (CPU mostly) | GPU + CPU fallback for LiteRT-LM |
| Model download UX | Built-in (Google Play app) | Built-in (HF integration) | Manual import or limited HF browsing | HF search + download + import for both formats |
| Remote providers | None (local only) | OpenAI-compatible + Ollama | Varies | OpenAI, Anthropic, Ollama, LM Studio, custom |
| Chat templates | Automatic (Conversation API) | Automatic (Jinja) | Manual or hardcoded | Automatic for LiteRT-LM, hardcoded for llama.cpp |
| Streaming UX | Yes (Flow-based) | Yes (SSE-style) | Varies | Yes (Flow-based for both engines) |
| Backend detection | ? (likely auto) | Manual | N/A | Auto GPU → CPU fallback |
| Separate engine tabs | Single engine | Single source (local vs remote tabs, not format tabs) | Single engine | Per-format tabs (GGUF / LiteRT-LM) |

## Sources

- **LiteRT-LM GitHub Repository (google-ai-edge/LiteRT-LM):** Official source. README, Kotlin API docs (`docs/api/kotlin/getting_started.md`), source files (`Engine.kt`, `Config.kt`, `Conversation.kt`, `Session.kt`, `Capabilities.kt`). 4.6k stars, v0.10.2 (Apr 2026). **HIGH confidence.**
- **Hugging Face litert-community Org:** https://huggingface.co/litert-community — 93 models, 6,277 followers. Official model distribution hub. HF Hub API response tested directly. **HIGH confidence.**
- **Google AI Edge Gallery App:** Production reference for Android LiteRT-LM integration. Available on Google Play. **HIGH confidence.**
- **Existing Warped Codebase:** `LlmProvider`, `LocalModel`, `ModelDownloadManager`, `HuggingFaceApi`, `ModelsScreen`, `HuggingFaceViewModel` — mapped integration points directly from source. **HIGH confidence.**
- **Maven dependency:** `com.google.ai.edge.litertlm:litertlm-android` on Google Maven. **HIGH confidence.**

---

*Feature research for: LiteRT-LM integration (second local inference engine)*
*Researched: 2026-05-02*
