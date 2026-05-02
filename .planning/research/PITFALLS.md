# LiteRT-LM Integration Pitfalls

**Domain:** Adding LiteRT-LM as a second local inference engine to an existing Android LLM app (Warped)
**Researched:** 2026-05-02
**Confidence:** HIGH

Pitfalls specific to integrating Google's LiteRT-LM as a co-existing engine alongside llama.cpp/GGUF, remote providers, and Hugging Face downloads. Based on official docs (Context7), GitHub issues (97 open), and the litert-community ecosystem on Hugging Face.

---

## Critical Pitfalls

### Pitfall 1: GPU Backend Choosing `Backend.GPU()` Which Silently Fails on Devices Without OpenCL

**What goes wrong:**
`Backend.GPU()` constructs successfully and `engine.initialize()` completes without error, but inference crashes with `Can not find OpenCL library` at message-send time. This specifically affects Tensor G3 devices (Pixel 8 Pro) and any device where the manufacturer stripped OpenCL from the system image. The crash is a native `UnsatisfiedLinkError` or SIGSEGV that kills the process with no Kotlin-level recovery.

**Why it happens:**
LiteRT-LM's GPU backend depends on `libOpenCL.so` being available on the device. Tensor G3 doesn't expose OpenCL (Google uses ANGLE/Vulkan for GPU compute). The `Backend.GPU()` constructor and early engine init don't probe for the library — they set up delegation handles that fail later at first inference. There is no public SDK API like `Backend.isGpuAvailable()` or `Engine.checkBackendSupport()`.

- **Real issue:** [github.com/google-ai-edge/LiteRT-LM/issues/1860](https://github.com/google-ai-edge/LiteRT-LM/issues/1860) — Pixel 8 Pro, `Backend.GPU()` constructs then crashes at inference with "Can not find OpenCL library"
- **Also:** [Issue #1681](https://github.com/google-ai-edge/LiteRT-LM/issues/1681) — GPU misidentified as PowerVR on Tensor G5 due to missing EGL context

**How to avoid:**
Build a **backend probe** that runs before any `Backend.GPU()` construction. Attempt to open a lightweight GPU context within a `try/catch` and time-bound it (5 seconds). Fall back to `Backend.CPU()` if:

1. `System.loadLibrary("OpenCL")` throws
2. A minimal `Engine` with a tiny test model fails to `initialize()` with GPU backend
3. The device is on a known-incompatible list (Tensor G3, Tensor G5 — updateable via remote config or DataStore)

The probe result should be cached per device session. Never let the crash reach the user.

**Warning signs:**
- `Backend.GPU()` constructor never throws
- Inference crashes only on specific chipmakers (Tensor, some Exynos)
- Crash stacktrace mentions `libOpenCL.so` or `Can not find OpenCL library`
- Users report "crashes immediately when I type a message" on Pixel 8/9

**Phase to address:** First phase — backend auto-detection and engine initialization must be built with fallback from day one.

---

### Pitfall 2: Conversation State Corruption — SIGSEGV on Second `sendMessage()` (MediaTek Dimensity SoCs)

**What goes wrong:**
On MediaTek Dimensity devices (OnePlus CPH2609, iQOO I2407), calling `conversation.sendMessage()` or `sendMessageAsync()` a second time on the same `Conversation` object crashes with `SIGSEGV (SEGV_MAPERR)` in `liblitertlm_jni.so` at `nativeSendMessage`. The first call succeeds; the second call crashes with a null pointer dereference at address `0xa8` inside native code. The `Conversation` object becomes invalid after one use.

**Why it happens:**
The native conversation state in LiteRT-LM's CPU path on MediaTek Dimensity SoCs appears to free or invalidate internal pointers after the first `sendMessage()` completes (after `RunPrefillAsync status: OK`). The Kotlin-side `Conversation` object still reports `isAlive() == true`, but the native handle is dangling. This is chipset-specific — same code works on Tensor G3 and Snapdragon.

- **Real issue:** [github.com/google-ai-edge/LiteRT-LM/issues/1849](https://github.com/google-ai-edge/LiteRT-LM/issues/1849) — "SIGSEGV in nativeSendMessage on second sequential call (MediaTek Dimensity)"
- **Also:** [Issue #2028](https://github.com/google-ai-edge/LiteRT-LM/issues/2028) — SIGSEGV on second `createConversation()` in separate process (also Mediatek)
- **Also:** [Issue #1859](https://github.com/google-ai-edge/LiteRT-LM/issues/1859) — FunctionGemma SIGSEGV on Pixel 8 Pro CPU (same symptom pattern)

**How to avoid:**
Always create a **fresh `Conversation` per user message exchange**, never reuse:
```kotlin
// DO NOT DO THIS:
val conv = engine.createConversation(config)
conv.sendMessageAsync("msg1").collect { ... } // Works
conv.sendMessageAsync("msg2").collect { ... } // SIGSEGV on Dimensity

// DO THIS INSTEAD:
engine.createConversation(config).use { c -> c.sendMessageAsync("msg1").collect { ... } }
engine.createConversation(config).use { c -> c.sendMessageAsync("msg2").collect { ... } }
```

Additionally, wrap every `sendMessageAsync` call in a `try/catch` that catches `RuntimeException` and checks `conversation.isAlive()`. On failure, create a new conversation. This also matches LiteRT-LM's documented patterns (the official getting-started guide uses `.use {}` blocks, not reused conversations).

**Warning signs:**
- First message always works, second always crashes on specific devices
- Stacktrace in `liblitertlm_jni.so` with fault address near `0xa8` or `0x0`
- Crash happens at prefill→decode boundary (`RunPrefillAsync status: OK` followed by SIGSEGV)
- Issue only on MediaTek Dimensity, not on Tensor or Snapdragon

**Phase to address:** Engine initialization and chat integration phase — architect from the start for single-use conversations.

---

### Pitfall 3: Unicode/LaTeX Input Crashes via ICU RegexMatcher

**What goes wrong:**
Any input containing LaTeX notation (`$`, `\frac`, `\sqrt`, `^`, `_`), Unicode math symbols (`∫`, `∑`, `π`, `∞`), or non-Latin scripts (Devanagari, CJK, Arabic) causes a native SIGSEGV in `libicui18n.so` at `RegexMatcher::find()`. The crash is 100% reproducible and affects all Kotlin API surfaces (`sendMessageAsync`, system instructions, initial messages). Plain ASCII works fine.

**Why it happens:**
`liblitertlm_jni.so` uses ICU's `RegexMatcher` internally for Jinja-style chat template tokenization. When input contains regex-metacharacters (`$`, `\`, `{`, `}`, `^`) or multi-byte UTF-8 sequences, the native tokenizer constructs an invalid `UText` buffer that ICU dereferences at a null pointer. This is a confirmed bug in the SDK's native text preprocessing pipeline — not API misuse.

- **Real issue:** [github.com/google-ai-edge/LiteRT-LM/issues/1616](https://github.com/google-ai-edge/LiteRT-LM/issues/1616) — SIGSEGV in `liblitertlm_jni.so` via ICU `RegexMatcher` with LaTeX/Unicode on Android
- Version affected: `0.9.0-alpha06` through at least `0.10.0` (multiple confirmations)

**How to avoid:**
1. **Input sanitization layer** between the chat UI and `sendMessageAsync`: strip or escape regex-metacharacters from user input before passing to LiteRT-LM. Use `android.icu.text.Transliterator` with careful character allowlisting.
2. **Pre-flight input check**: detect LaTeX/Unicode math symbols and offer a fallback engine (llama.cpp) for those conversations. The existing `LlamaEngine` handles arbitrary Unicode — route LaTeX-heavy conversations there.
3. **Crash containment**: since this is a native crash, run LiteRT-LM inference in a **separate process** (`android:process=":litert"`) so the crash doesn't kill the UI.
4. **Version lock & test**: before integrating any LiteRT-LM version, run a test with LaTeX input. Check if the issue is fixed in `v0.10.2` or newer.

**Warning signs:**
- App crashes whenever math/STEM content is typed
- Crash in `libicui18n.so` at `RegexMatcher::find`
- Fault address near `0x3` or `0xd` (null pointer offsets)
- Multilingual users experience crashes; English-only users don't

**Phase to address:** Chat integration phase — sanitization must be built before chat goes live. Plan for engine fallback routing.

---

### Pitfall 4: Dual-Engine Memory Contention — Model Loading While Other Engine is Active

**What goes wrong:**
Loading a `.litertlm` model via `engine.initialize()` while llama.cpp still has a GGUF model loaded in memory (or vice versa) can exceed available device RAM, triggering Android's low-memory killer. The app process is killed silently without saving conversation state. Even if both fit, loading a large model (7B Q4 = ~4GB) while another is resident hits the 4-16GB physical RAM ceiling on most Android phones.

**Why it happens:**
Android doesn't page model weights to disk — they must stay in physical RAM. Both llama.cpp and LiteRT-LM keep model weights in process memory. The LLM engines have no coordination mechanism — each allocates independently. A 7B Q4_K_M GGUF (~4.3GB) + a Gemma 4 E4B `.litertlm` (~4GB INT4) = 8.3GB, exceeding the ~6GB usable RAM on 8GB devices (system needs ~2GB).

**How to avoid:**
1. **Engine lifecycle manager**: A domain-level `EngineManager` that tracks which engine is active and enforces mutual exclusion — only one local model loaded at a time. When switching engines, call `engine.close()` on the old one first.
2. **Pre-load memory check**: Before `initialize()`, call `ActivityManager.getMemoryInfo()` and compare `availMem` against the model file size. Warn users if available RAM < model_size × 1.5.
3. **Automatic unload on switch**: When the user selects a model from a different engine type, the UI triggers unload of the current model before loading the new one. The ViewModel enforces this.
4. **`onTrimMemory()` handling**: Override in Activity/Application to eagerly unload models when `TRIM_MEMORY_RUNNING_CRITICAL` fires.

**Warning signs:**
- "App keeps closing when I switch models"
- logcat shows `Process XXX has been killed (lowmemorykiller)`
- No crash report — the process is killed, not crashed
- `dumpsys meminfo` shows near-100% RAM usage before kill

**Phase to address:** Engine initialization phase — lifecycle management must be designed before both engines are integrated.

---

### Pitfall 5: Thread Safety — LiteRT-LM's Engine is Not Thread-Safe for Concurrent Conversations

**What goes wrong:**
If multiple coroutines call `engine.createConversation()` or `conversation.sendMessageAsync()` on the same `Engine` instance simultaneously, the native layer can enter a corrupt state. This manifests as garbled output, duplicated tokens, or an `IllegalStateException("Engine is not alive")` from the Kotlin layer. The engine internally uses a single native session pointer — concurrent access races on it.

**Why it happens:**
The LiteRT-LM Kotlin API wraps a C++ engine with a single native handle. The `Engine` class is documented as not thread-safe for simultaneous operations. Unlike some inference frameworks that queue concurrent requests, LiteRT-LM expects sequential access: create one conversation → send one message → close conversation → optionally create another. This is different from llama.cpp where you can at least have multiple contexts from one model.

**How to avoid:**
1. **Single conversation scope**: Use a `Mutex` or single-threaded `CoroutineDispatcher` to serialize all LiteRT-LM operations. Each chat session gets a dedicated coroutine scope.
2. **One conversation at a time per engine**: The `EngineManager` should track active conversations and reject attempts to create a second one while one is in-flight.
3. **Process model**: For applications that genuinely need concurrent LiteRT-LM inference, run separate `Engine` instances in separate processes. But chat apps typically only need one active conversation.
4. The official pattern from the getting-started guide already enforces this: `engine.createConversation().use { ... }` — the `.use {}` block ensures sequential access.

**Warning signs:**
- "Engine is not alive" errors under load
- Corrupted/garbled output when multiple messages are sent rapidly
- `sendMessageAsync` returns empty flow or throws from `nativeSendMessage`
- Flaky behavior that's hard to reproduce on fast devices but fails on slower ones

**Phase to address:** Chat integration phase — the conversation lifecycle must be designed as single-threaded from the start.

---

### Pitfall 6: Parameter Mapping Mismatch — SamplerConfig vs Existing GenerationParams System

**What goes wrong:**
The existing app has a unified `GenerationParams` data class (temperature, top_p, top_k, repeat_penalty, max_tokens, context_size, seed, threads) that feeds both llama.cpp and remote providers. LiteRT-LM's `SamplerConfig` has a different parameter set: `topK`, `topP`, `temperature`, `maxNumTokens`, `stopTokens`, `seed`. Mapping between them is not 1:1:

- `repeat_penalty` doesn't exist in `SamplerConfig` — LiteRT-LM may not support it yet
- `threads` (CPU thread count) is set at `EngineConfig` level, not per-conversation
- `context_size` is determined by the model's native context window, not configurable via SDK
- `stopTokens` is new — the existing system doesn't have stop-token configuration
- `SamplerConfig` lacks `top_k` in some API versions — it was added later

If you blindly pass all generation params, some will be silently ignored; others won't exist and cause compilation errors. More critically, a user who relies on `repeat_penalty` for coherent output will get worse quality with LiteRT-LM — silently.

**How to avoid:**
1. **Create a `GenerationParams.toLiteRTSamplerConfig()` extension**: Explicit mapping function that translates what's possible and logs warnings for unsupported parameters.
2. **Version-gate parameters**: Check the LiteRT-LM SDK version at runtime. Some parameters (`topK`, `stopTokens`) may only exist in newer versions.
3. **UI differentiation**: When a LiteRT-LM model is selected, grey out or hide unsupported parameters (repeat_penalty, context_size slider) rather than letting users think they're being applied.
4. **Document the mapping table** in code comments:
```
Warped param     → LiteRT-LM SamplerConfig       Status
temperature      → temperature                    ✓ Same
topP             → topP                           ✓ Same
topK             → topK                           ✓ (v0.9.0+)
maxTokens        → maxNumTokens                   ✓ Same
seed             → seed                           ✓ Same
repeatPenalty    → N/A                            ✗ Not supported
threads          → EngineConfig.numThreads        ✓ Diff location
contextSize      → N/A (model-determined)         ✗ Not configurable
stopTokens       → stopTokens                     ✓ New feature
```

**Warning signs:**
- Users report "repeat_penalty doesn't work with Gemma models"
- Parameter UI shows all sliders active, but some have no effect
- "I set context to 4096 but it only uses 2048"
- Compilation errors when updating LiteRT-LM SDK — new/deprecated SamplerConfig fields

**Phase to address:** Parameters and presets extension phase — must redesign the generation params system to be engine-aware.

---

### Pitfall 7: Conversation Lifecycle Mismanagement — Engine Killed After Conversation Close

**What goes wrong:**
On certain devices (MediaTek Dimensity) and in separate process configurations, calling `conversation.close()` on one conversation invalidates the underlying `Engine` state. The next `engine.createConversation()` throws `IllegalStateException("Engine is not alive")` or crashes with SIGSEGV. This means you can't do the natural pattern of "create conversation → send → close → create new conversation".

**Why it happens:**
The native `Engine` object appears to maintain weak internal state references to active conversations. On path completion (close + GC), some internal cleanup in the C++ layer may accidentally free shared engine resources. This is essentially a use-after-free in the native layer, triggered by conversation close. The exact trigger is "close conversation + create new conversation" on specific SoCs.

- **Real issue:** [github.com/google-ai-edge/LiteRT-LM/issues/2028](https://github.com/google-ai-edge/LiteRT-LM/issues/2028) — SIGSEGV on second `createConversation()` when `Engine` runs in `android:process=":ai"` (iQOO I2407, CPU, Gemma 4 E2B)
- Workaround discovered by reporter: removing `android:process=":ai"` fixes it on the affected device
- Same code on OPPO CPH2717 (Dimensity 7050) works fine — SoC-specific

**How to avoid:**
1. **Avoid separate processes for inference initially**: Run LiteRT-LM inference in the main app process, not a dedicated `:ai` process. The process isolation benefit (crash containment) is outweighed by stability issues on Mediatek devices.
2. **Engine singleton with single lifetime**: Instead of create→use→close→recreate, keep one `Engine` instance alive for the app session lifetime. Create conversations as needed and close them, but never close the engine until the app process is terminating.
3. **Defensive recreate**: If `createConversation()` throws or the engine reports not-alive, catch the exception, `engine.close()`, create a fresh `Engine`, re-initialize, and retry. Log the event for diagnostics.
4. **Test on MediaTek devices**: If you can't access real Mediatek hardware, use Firebase Test Lab with a MediaTek device configuration.

**Warning signs:**
- First conversation works, second `createConversation()` throws or crashes
- Error: "Engine is not alive" after closing a conversation
- Only on MediaTek devices; Tensor and Snapdragon unaffected
- Crash at `nativeSendMessage` after conversation lifecycle operations

**Phase to address:** Engine integration phase — the Engine lifecycle strategy must be defined before chat integration.

---

### Pitfall 8: Hugging Face API Divergence — litert-community Uses Different Search/Filter Patterns

**What goes wrong:**
The existing `HuggingFaceApi` was designed for GGUF model search (`GET /api/models?search=llama&filter=gguf&sort=downloads`). LiteRT-LM models live in the `litert-community` organization and use a different filtering mechanism. The `.litertlm` extension filter doesn't work the same way. The HF Hub API returns `.litertlm` models interspersed with `.tflite`, `.task`, and other LiteRT artifacts. Naively extending the GGUF search to `.litertlm` filtering will return unrelated TFLite models.

**Why it happens:**
The `litert-community` org hosts all LiteRT model types — not just LLMs. Models include vision, speech, and classification models in addition to text generation. The HF API `filter` parameter for file extensions may not work identically for `.litertlm` as it does for `.gguf`. Additionally, `.litertlm` models are larger single-file bundles (no quantization variants like Q2-Q8) — the file listing UI designed for GGUF quantization variants needs adaptation.

**How to avoid:**
1. **Separate HF API client or search path for litert-community**: `GET /api/models?author=litert-community&search=gemma&sort=downloads` — filter by organization, not file extension.
2. **Post-filter results client-side**: Fetch model list from litert-community, then for each model check `siblings` for `.litertlm` file extension. Exclude models that only contain `.tflite` or `.task` files.
3. **Model type metadata**: Check if the HF model card contains a `pipeline_tag: text-generation` or similar tag to identify LLM-capable models vs. vision/audio models.
4. **Update the model detail UI**: GGUF models show quantization variants (Q2, Q4, Q8, etc.). LiteRT-LM models are single files with a fixed quantization (usually INT4). The download UI must adapt to not show a quantization picker for LiteRT-LM models.

**Warning signs:**
- Search returns speech/vision models mixed with LLMs
- "Filter by .litertlm" returns empty results or wrong models
- Users download a `.litertlm` file only to discover it's an ASR model, not a chat model
- Model file list shows no quantization variants but the UI expects them

**Phase to address:** Model acquisition phase — the HF search/download system must be refactored to handle two model ecosystems.

---

### Pitfall 9: Room Schema Migration — Adding Model Type Without Breaking Existing Data

**What goes wrong:**
The existing `models` table has columns specific to GGUF (`quantization`, `file_path` pointing to `.gguf`). Adding a `model_type` discriminator (e.g., `GGUF` vs `LITERTLM`) requires a schema migration. A naive `ALTER TABLE ADD COLUMN` with a default value works, but the existing download tracking, import workflows, and model listing queries need to handle the new type. If any query assumes GGUF-specific columns (e.g., filtering by quantization level), it will break for LiteRT-LM models.

**Why it happens:**
Room schema evolution in Android is notoriously fragile. The existing `ModelEntity` likely has a `quantization: String` field that's populated for GGUF models but meaningless for `.litertlm` files. Queries like `SELECT * FROM models WHERE quantization = 'Q4_K_M'` will silently exclude all LiteRT-LM models. Similarly, the `DownloadWorker` tracks progress by `modelId` — if the same model has both GGUF and LiteRT-LM variants, the tracking gets confused.

**How to avoid:**
1. **Migration strategy**: 
   - Add `model_format TEXT NOT NULL DEFAULT 'GGUF'` column (Room migration)
   - Add `file_extension TEXT NOT NULL DEFAULT '.gguf'` column
   - Make `quantization` nullable (it's meaningless for LiteRT-LM)
2. **Create a `ModelFormat` enum**: `GGUF`, `LITERTLM`, `SAFETENSORS` (future). Use it as a sealed class in domain layer.
3. **Separate model ID namespace**: Use a composite key or prefix: `gguf:org/model:Q4_K_M` vs `litertlm:org/model`. This prevents download tracking collisions.
4. **Query adaptation**: All model listing queries should be reviewed — none should filter by GGUF-specific fields without checking `model_format`.
5. **Test the migration**: Run the migration on a Room database populated with real GGUF download records. Verify that old data survives and new LiteRT-LM records store correctly.

**Warning signs:**
- Existing GGUF models disappear from the model list after migration
- Download progress tracking gets confused between GGUF and LiteRT-LM downloads of the same base model
- "quantization must not be null" errors for LiteRT-LM model insertions
- Room `Migration` test fails with "table models has no column named model_format"

**Phase to address:** Foundation phase for LiteRT-LM — schema migration must be the very first step before any feature work.

---

### Pitfall 10: Engine Initialization Blocking — `engine.initialize()` Takes Seconds and Must Not Run on UI Thread

**What goes wrong:**
`engine.initialize()` can take 5-30 seconds depending on model size, backend, and device. If called on the main thread (or a coroutine that dispatches to Main), the UI freezes entirely. On Android, a 5-second main-thread block triggers an ANR dialog. Even if it doesn't ANR, the user sees a frozen screen with no progress indication.

**Why it happens:**
LiteRT-LM model initialization involves: parsing the `.litertlm` bundle → loading model weights into RAM → compiling GPU shaders (if GPU backend) → warming up the tokenizer → allocating KV cache buffers. This is inherently CPU/IO-bound work. The Kotlin `Engine.initialize()` is a `suspend` function, which means it *can* be called from a coroutine, but Coroutines don't automatically move work off the main thread — they suspend cooperatively. If the native initialization code doesn't yield, the main thread blocks regardless of coroutine usage.

The official docs state: "Engine initialization can take time and should be run on a background thread." But many developers miss the distinction between "suspend function" and "automatically on background".

**How to avoid:**
1. **Explicitly wrap in `withContext(Dispatchers.IO)`**: 
   ```kotlin
   val engine = withContext(Dispatchers.IO) {
       Engine(engineConfig).also { it.initialize() }
   }
   ```
2. **Never call in `viewModelScope.launch` without explicit dispatcher**: `viewModelScope` defaults to `Dispatchers.Main`. Always use `viewModelScope.launch(Dispatchers.IO)` for engine operations.
3. **Show progress UI during initialization**: Since LiteRT-LM doesn't expose a progress callback for initialization, show an indeterminate loading indicator with model name while initializing.
4. **Handle process death during initialization**: If Android kills the process during a long initialization, the app must recover gracefully. Use `SavedStateHandle` or DataStore to track "initialization in progress" state.

**Warning signs:**
- "App freezes when I tap Load Model"
- ANR dialog "Warped isn't responding"
- Choreographer "Skipped XXX frames" warnings in logcat during model load
- Initialization works on flagship devices (fast) but ANRs on mid-range devices (slow)

**Phase to address:** Engine initialization phase — must be designed with background dispatch from the start.

---

## Technical Debt Patterns

Shortcuts that seem reasonable but create long-term problems.

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Copy-paste `LlamaEngine` to create `LitertLmEngine` | Fast to get working | Two parallel codebases diverge; shared engine lifecycle logic duplicated; bugs fixed in one not fixed in other | Never — extract `LocalEngine` interface and shared lifecycle manager |
| Hardcode `Backend.CPU()` to avoid GPU fallback complexity | No crash on Tensor G3 | Loses 2-4x performance on OpenCL-capable devices; users complain LiteRT-LM is "slower than llama.cpp" | Only for initial development; GPU auto-detection must ship to production |
| Skip `.litertlm` file validation on import | User can import any file quickly | Invalid/corrupt files cause native crashes with no error message; hard to diagnose | Never — always validate `.litertlm` magic bytes before passing to engine |
| Add `model_format` column with `NOT NULL DEFAULT 'GGUF'` without migration testing | Quick migration | Crashes on existing installations with GGUF data if migration has subtle issues | Never — always test migration on database with real production-shaped data |
| Use `Contents.of(Content.Text(input))` without input sanitization | Simple code | App crashes for any user typing math/Unicode; terrible first impression for LiteRT-LM | Only if you've confirmed the ICU bug is fixed in your SDK version |
| Share `DownloadWorker` between GGUF and LiteRT-LM without refactoring | Reuse existing download infrastructure | Wrong file extension detection, wrong storage path, wrong post-download processing | Only if download logic is fully parameterized by model type |
| Run LiteRT-LM in same process as UI without crash containment | Simpler architecture | Native crash kills the entire app; user loses conversation and sees a hard crash | Acceptable if version 0.10.2+ and you've tested all input types; still risky |

---

## Integration Gotchas

Common mistakes when connecting LiteRT-LM to existing systems.

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| **Hugging Face API** | Using same search filter `?filter=gguf` for `.litertlm` models | Use `?author=litert-community` for model search, then filter `siblings` for `.litertlm` files |
| **Hugging Face API** | Searching all of HF Hub for `.litertlm` files returns too many unrelated results | Scope searches to the `litert-community` organization |
| **DownloadManager** | Reusing GGUF-specific download paths for `.litertlm` | Use separate storage directory: `models/gguf/` and `models/litertlm/` |
| **File extension detection** | Checking for `.gguf` extension to determine model type | Parse the file header/magic bytes; `.litertlm` has a distinct binary container format |
| **Room DAO queries** | `@Query("SELECT * FROM models WHERE quantization = :q")` silently excludes LiteRT-LM models | Add `AND model_format = :format` or make quantization nullable and handle null |
| **Engine interface** | `LocalEngine` interface leaking GGUF-specific concepts (`quantization`, `contextSize`) | Redesign `LocalEngine` with provider-agnostic contract: `load()`, `generate(prompt, params)`, `unload()` |
| **Parameter application** | Passing `repeat_penalty` to `SamplerConfig` (doesn't exist) | Maintain `GenerationParams.toSamplerConfig()` mapping with supported params only |
| **AndroidManifest** | Missing `<uses-native-library android:name="libOpenCL.so" android:required="false"/>` | Add both `<uses-native-library>` entries for GPU backend support; mark as `required="false"` so app installs on devices without OpenCL |
| **Backend selection UI** | Exposing "CPU / GPU / NPU" radio buttons with no availability pre-check | Pre-probe backend availability; hide GPU option if OpenCL/Vulkan not detected; disable NPU unless device confirmed compatible |
| **Cache directory** | Not setting `cacheDir` in `EngineConfig` | Always set `cacheDir` to `context.cacheDir.absolutePath` — it significantly improves subsequent load times for the same model |

---

## Performance Traps

Patterns that work at small scale but fail as usage grows.

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| **Engine creation on every chat message** | 5-30s delay before first token appears | Create Engine once at model load time; reuse for all conversations with that model | Immediately noticeable to user — initialization is too slow to hide |
| **Loading both engines simultaneously** | LowMemoryKiller kills app process | Enforce mutual exclusion via `EngineManager` — only one local engine active | When total model weights + system RAM exceed device RAM (typical with 7B+8B models on 8GB devices) |
| **GPU backend with bad shader compilation** | 10-30s initialization, followed by crash | Probe GPU availability before engine creation; fall back to CPU if GPU init fails | On first model load after app install on GPU-incompatible devices |
| **Token streaming without backpressure** | UI freezes, dropped tokens, ANR | Use `Flow.buffer()` with `onBufferOverflow = DROP_OLDEST` and aggressive UI composable recomposition scoping | Under rapid token generation (>30 tokens/sec) on mid-range devices |
| **Not setting `maxNumTokens` in EngineConfig** | Generation runs until model exhausts context or produces garbage | Always set `maxNumTokens` at engine level as a safety bound | User sends "Write a novel about..." and generation runs for 5+ minutes with degrading quality |
| **Large `.litertlm` model download without storage check** | Download fails at 95% with "insufficient storage" | Pre-check `freeSpace >= fileSize * 1.2` before download; surface clear error early | When device has <10GB free and user downloads Gemma 4 E4B (4GB+) |

---

## Security Mistakes

Domain-specific security issues.

| Mistake | Risk | Prevention |
|---------|------|------------|
| **Downloading `.litertlm` files over HTTP from unofficial mirrors** | Malicious model file could contain code execution payload in the native inference pipeline | Only download from `huggingface.co` official CDN; enforce HTTPS via `network_security_config.xml` |
| **No signature verification on downloaded `.litertlm` files** | Tampered model could produce malicious outputs or exploit engine bugs | Verify SHA256 against Hugging Face metadata after download. HF API returns `sha256` per file sibling |
| **Exposing native crash details to users** | SIGSEGV crash dialogs reveal native stack traces — potential information leak | Wrap all engine interactions in error handlers that log to file (not UI) and show generic "Inference error" message |
| **`.litertlm` model files world-readable on shared storage** | Other apps could steal proprietary/custom models | Store models in `context.filesDir/models/litertlm/` (app-private); only use `getExternalFilesDir()` for user-requested exports |

---

## UX Pitfalls

Common user experience mistakes.

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| **Mixing GGUF and LiteRT-LM models in a single list without format indicator** | User loads wrong model format, gets error, doesn't understand why | Separate tabs/sections: "GGUF Models" and "LiteRT-LM Models" with format badges |
| **Not showing engine type in model selector during chat** | User doesn't know which engine will be used; confused when features differ | Show engine badge on each model: "llama.cpp" or "LiteRT-LM" |
| **Not explaining why some parameters are unavailable** | User sets repeat_penalty, it doesn't work with LiteRT-LM model, thinks it's a bug | Grey/gray out unavailable params with tooltip: "Not supported by LiteRT-LM models" |
| **Auto-selecting GPU backend without user awareness** | GPU crashes silently, user thinks the app is broken | Show backend selection as "Auto (recommended)" that explains: "GPU for speed, CPU as fallback" |
| **Download progress showing 0% for the first 30 seconds of a large file** | User thinks download is stuck, cancels and retries | Fetch `Content-Length` header first, show file size immediately, update progress incrementally |
| **No indication that `.litertlm` models are different from `.gguf`** | User downloads same base model in both formats, wastes storage and bandwidth | Model detail page shows format comparison: "GGUF: 4.2GB (Q4_K_M, more quantization options) | LiteRT-LM: 3.8GB (INT4, GPU accelerated)" |

---

## "Looks Done But Isn't" Checklist

Things that appear complete but are missing critical pieces.

- [ ] **Backend auto-detection:** Often missing the fallback path — verify that a Tensor G3 device gracefully falls back to CPU
- [ ] **Input sanitization:** Often missing — verify that typing `$x^2$` doesn't crash the app with LiteRT-LM
- [ ] **Engine lifecycle:** Often implemented as create-once-reuse, but that crashes on certain SoCs — verify that conversation-per-message pattern works
- [ ] **Room migration:** Often tested on empty DB — verify on DB with 10+ real GGUF download records
- [ ] **Download format detection:** Often assumes single model format — verify that `.litertlm` and `.gguf` downloads go to correct directories
- [ ] **Parameter UI:** Often shows all parameters active — verify that LiteRT-LM-incompatible params are hidden/disabled
- [ ] **Memory management:** Often loads model without checking RAM — verify that loading >80% available RAM shows warning
- [ ] **`AndroidManifest` entries:** Often missing `<uses-native-library>` for GPU backend — verify on OpenCL-capable devices
- [ ] **Cache directory:** Often not set — verify that second model load is faster than first (caching works)
- [ ] **Native crash containment:** Often no `try/catch` around engine calls — verify that a native crash shows error UI, not process death

---

## Recovery Strategies

When pitfalls occur despite prevention, how to recover.

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| GPU backend crash on unsupported device | MEDIUM | Catch error → switch to `Backend.CPU()` → re-initialize engine → persist backend preference per device |
| Unicode crash on user input | MEDIUM | Catch SIGSEGV via signal handler or process monitor → restart inference process with sanitized input → show user "Some characters were removed from your message for compatibility" |
| Conversation state corruption (second sendMessage) | LOW | Always use fresh conversation per message — if reuse fails, recover by creating new conversation; no user impact |
| Dual-engine memory exhaustion | HIGH | App process killed by OS → on restart, detect which model was loaded → re-load only the most recently used model → restore conversation from Room |
| Room migration failure | HIGH | App won't launch for existing users → Recovery: implement `fallbackToDestructiveMigration()` as safety net; accept data loss for this corner case |
| Download of wrong model format | LOW | Post-download validation checks file header → if invalid, move to "corrupt" folder and show error → user can re-download |

---

## Pitfall-to-Phase Mapping

How roadmap phases should address these pitfalls.

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| GPU backend silent failure (#1) | Phase 1: Engine foundation | Test on Tensor G3 device: `Backend.GPU()` falls back to CPU without crash |
| Conversation SIGSEGV on reuse (#2) | Phase 2: Chat integration | Test on MediaTek Dimensity: send two messages without crash |
| Unicode/LaTeX input crash (#3) | Phase 2/3: Chat integration + polish | Test input `$x^2$` — no crash; verify sanitization or engine routing |
| Dual-engine memory contention (#4) | Phase 1: Engine foundation | Load 7B GGUF + 4B LiteRT-LM on 8GB device — only one active, no OOM kill |
| Engine thread safety (#5) | Phase 2: Chat integration | Concurrent sendMessageAsync calls — second call rejected or queued, no crash |
| Parameter mapping mismatch (#6) | Phase 3: Parameters & presets | All supported params apply; unsupported params greyed out in UI |
| Conversation lifecycle mismanagement (#7) | Phase 1: Engine foundation | Five conversation create/close cycles on same engine — no "Engine not alive" errors |
| HF API divergence (#8) | Phase 2: Model acquisition | Search "gemma" in litert-community tab → returns only LLM .litertlm models |
| Room schema migration (#9) | Phase 1: Foundation | Migrate DB with 10+ GGUF records → all survive, LiteRT-LM models insert correctly |
| Engine init blocking main thread (#10) | Phase 1: Engine foundation | `StrictMode` detects no main-thread I/O during `engine.initialize()` |

---

## Sources

- **Context7:** LiteRT-LM official docs (`/google-ai-edge/litert-lm`) — Android Kotlin API, `EngineConfig`, `SamplerConfig`, `ConversationConfig`, GPU backend configuration
- **GitHub Issues:** [google-ai-edge/LiteRT-LM/issues](https://github.com/google-ai-edge/LiteRT-LM/issues) — 97 open issues as of 2026-05-02
  - [#1860](https://github.com/google-ai-edge/LiteRT-LM/issues/1860): Backend.GPU() silently fails on Pixel 8 Pro (HIGH confidence — detailed repro steps, environment confirmed)
  - [#2028](https://github.com/google-ai-edge/LiteRT-LM/issues/2028): SIGSEGV on second createConversation in separate process (HIGH confidence — thorough investigation by reporter)
  - [#1849](https://github.com/google-ai-edge/LiteRT-LM/issues/1849): SIGSEGV on second sendMessage on MediaTek Dimensity (HIGH confidence — multiple models, backends tested)
  - [#1616](https://github.com/google-ai-edge/LiteRT-LM/issues/1616): ICU RegexMatcher crash on LaTeX/Unicode input (HIGH confidence — all API surfaces tested)
  - [#1864](https://github.com/google-ai-edge/LiteRT-LM/issues/1864): Engine init failure on Samsung Exynos 2600 (MEDIUM confidence — single report but detailed)
  - [#1859](https://github.com/google-ai-edge/LiteRT-LM/issues/1859): FunctionGemma SIGSEGV on Pixel 8 Pro CPU (HIGH confidence — confirmed crash with stacktrace)
  - [#1850](https://github.com/google-ai-edge/LiteRT-LM/issues/1850): GPU decode failure on Pixel 8 with v0.10.0, self-built patch fixes (MEDIUM confidence — version-specific)
  - [#1681](https://github.com/google-ai-edge/LiteRT-LM/issues/1681): GPU misidentification on Tensor G5 (MEDIUM confidence)
  - [#2056](https://github.com/google-ai-edge/LiteRT-LM/issues/2056): Gemma 4 vision path SIGSEGV on second image (MEDIUM confidence — multimodal-specific, not text chat)
- **Hugging Face:** [litert-community](https://huggingface.co/litert-community) — model hosting org, 93 models, Gemma 4 E2B/E4B most popular (182k downloads)
- **Official docs:** Google AI Edge [LiteRT-LM overview](https://ai.google.dev/edge/litert-lm)
- **Existing app context:** `.planning/PROJECT.md`, `.planning/research/ARCHITECTURE.md`, `.planning/research/PITFALLS.md` (v1.0 llama.cpp pitfalls)

---

*Pitfalls research for: LiteRT-LM integration as second local inference engine*
*Researched: 2026-05-02*
*Confidence: HIGH — findings backed by official docs (Context7) and confirmed GitHub issues with reproduction steps*
