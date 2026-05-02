# LiteRT-LM Stack Additions — Technology Stack

**Project:** Warped v1.1 — LiteRT-LM Integration
**Researched:** 2026-05-02
**Status:** Research complete — LiteRT-LM additions for milestone v1.1

> **Note:** This document covers ONLY changes/additions to the existing v1.0 stack.
> The full existing stack (Kotlin, Compose, Hilt, Room, llama.cpp, etc.) remains valid.
> See the [v1.0 research STACK.md](STACK.md) for the foundational stack.

---

## 1. LiteRT-LM Core: Single Maven Dependency (No JNI/NDK Build Required)

Unlike llama.cpp which requires CMake + NDK + custom JNI bridge, LiteRT-LM ships
as a pre-built Android AAR via Google Maven. No C++ compilation needed.

| Layer | Choice | Version | Rationale |
|-------|--------|---------|-----------|
| LiteRT-LM Android SDK | `com.google.ai.edge.litertlm:litertlm-android` | **0.10.2** | Latest stable release (April 14, 2026). v0.11.0-rc1 exists but is pre-release. The library bundles pre-compiled native `.so` libraries for `arm64-v8a` and `x86_64`, shipped as JNI inside the AAR. No NDK or CMake setup required. |
| GPU native lib deps | `libOpenCL.so`, `libvndksupport.so` (platform) | Android system libs | Declared via `<uses-native-library>` in AndroidManifest. These are platform libraries, not bundled. |
| NPU driver path | Native library dir path | Per-device | NPU libraries are device-specific. On Android, point to `context.applicationInfo.nativeLibraryDir`. |

**Version pinning decision:** Pin to `0.10.2` (not `latest.release` and not `0.11.0-rc1`).
RC versions of LiteRT-LM have introduced breaking API changes in the past.
Pinning prevents surprise breakage on CI.

**Maven metadata confirmed** at `https://dl.google.com/dl/android/maven2/com/google/ai/edge/litertlm/litertlm-android/maven-metadata.xml`:
Available versions include `0.10.2` (stable), `0.11.0-rc1` (pre-release), and older `0.9.x` releases.

### Gradle dependency addition

```kotlin
// In libs.versions.toml
[versions]
litertlm = "0.10.2"

[libraries]
litertlm-android = { group = "com.google.ai.edge.litertlm", name = "litertlm-android", version.ref = "litertlm" }

// In app/build.gradle.kts (add to dependencies block)
implementation(libs.litertlm.android)
```

### AndroidManifest.xml additions (GPU backend)

```xml
<application>
    <!-- Required for GPU backend (OpenCL) -->
    <uses-native-library android:name="libvndksupport.so" android:required="false"/>
    <uses-native-library android:name="libOpenCL.so" android:required="false"/>
</application>
```

**`android:required="false"` is critical:** Not all devices have OpenCL drivers.
Setting `required="false"` allows installation on all devices with graceful
fallback to CPU when GPU is unavailable.

---

## 2. LiteRT-LM API Surface

The Kotlin API is a pure Kotlin wrapper over JNI (pre-built inside the AAR).
All public types live under `com.google.ai.edge.litertlm`.

### Core Classes

| Class | Purpose | Lifecycle |
|-------|---------|-----------|
| `Engine` | Entry point. Loads model, manages backends. | `AutoCloseable` — must call `close()` |
| `EngineConfig` | Model path, backend selection, max tokens, cache dir. | Value object, passed to `Engine()` |
| `Conversation` | Chat session with message history. | `AutoCloseable` — created via `engine.createConversation()` |
| `ConversationConfig` | System prompt, initial messages, sampler, tools. | Value object, passed at conversation creation |
| `Session` | Lower-level session for inference without chat templating. | `AutoCloseable` — created via `engine.createSession()` |
| `SamplerConfig` | topK, topP, temperature, seed. | Value object, embedded in `ConversationConfig` |
| `Backend` | Sealed class: `Backend.CPU(numOfThreads)`, `Backend.GPU()`, `Backend.NPU(nativeLibraryDir)` | Value object |
| `Message` | Conversation message with role, content, tool calls, channels. | Immutable data class |
| `Content` | Union type: `Content.Text`, `Content.ImageBytes`, `Content.ImageFile`, `Content.AudioBytes`, `Content.AudioFile` | Sealed interface |
| `Contents` | Helper: `Contents.of(...)` to build content lists. | Utility |
| `ToolSet` | Interface for defining tool functions with `@Tool` annotations. | Custom implementation |
| `OpenApiTool` | Interface for defining tools via OpenAPI JSON spec. | Custom implementation |
| `MessageCallback` | Callback interface: `onMessage()`, `onDone()`, `onError()`. | Anonymous object |
| `LiteRtLmJniException` | Exception type for native-layer errors. | Standard exception |
| `LogSeverity` | Enum: VERBOSE, DEBUG, INFO, WARNING, ERROR, FATAL. | Passed to `Engine.setNativeMinLogSeverity()` |
| `BenchmarkInfo` | Performance metrics from inference (experimental). | From `conversation.getBenchmarkInfo()` |

### Backend Selection

```kotlin
// CPU (default) — works everywhere
Backend.CPU(numOfThreads = 4)  // null/0 = auto

// GPU — needs manifest entries, fails gracefully to CPU
Backend.GPU()

// NPU — needs device-specific libs path
Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
```

**Auto-detection strategy recommended:**
1. Try GPU first → if engine init fails with GPU, fall back to CPU
2. NPU optional → user toggle, since NPU drivers are device-specific
3. CPU always available as base

### Streaming Chat (Flow-based)

```kotlin
// This is the recommended pattern for Compose integration:
engine.createConversation(config).use { conversation ->
    conversation.sendMessageAsync("Hello!")
        .catch { error -> /* handle */ }
        .collect { message -> /* emit each Message chunk */ }
}
```

**Key difference from llama.cpp streaming:**
- llama.cpp: Custom JNI callback → `callbackFlow<String>` (raw tokens, manual decoding)
- LiteRT-LM: Built-in `callbackFlow<Message>` (structured messages, auto-decoded, tool calls included)

Both produce `Flow<T>` which integrates identically with `collectAsStateWithLifecycle()` in Compose ViewModels.

### SamplerConfig Parameters

```kotlin
SamplerConfig(
    topK = 40,        // Top-K sampling filter
    topP = 0.95,      // Nucleus sampling threshold
    temperature = 0.7, // Creativity (0=deterministic, >1=more random)
    seed = 42,        // Reproducibility (0 = random)
)
```

**Note:** LiteRT-LM's `SamplerConfig` covers the same parameters as the existing llama.cpp
configuration but uses **different parameter names and ranges**. The existing `Presets` table
may need a `backend_type` column or separate `litertlm_presets` to avoid parameter mismatch.

---

## 3. Hugging Face Integration — litert-community Org

| Layer | Choice | Rationale |
|-------|--------|-----------|
| Model source | `huggingface.co/litert-community` org | Official Google-maintained org with 93+ `.litertlm` models. Covers Gemma, Llama, Phi, Qwen, FunctionGemma, and speech/vision models. Same HF Hub API — no new client needed. |
| Model search | Existing Retrofit HF API client | Identical to GGUF search, different filter: search `litert-community` org and filter for `.litertlm` file extension. |
| File filter | `.litertlm` extension | Single binary file per model (no sharding like GGUF's `.gguf-split-a`). Each `.litertlm` is self-contained. |
| Download | Existing OkHttp + WorkManager | Same pattern as GGUF downloads: `WorkManager` + foreground notification + `Range` header resume. Stored in `filesDir/models/litertlm/` directory. |

**HF API endpoints (reused, no changes):**
- `GET /api/models?search=gemma&author=litert-community&sort=downloads` → model listing
- `GET /api/models/litert-community/{model_id}` → model details + siblings list
- `GET /litert-community/{model_id}/resolve/main/{filename}.litertlm` → direct download

**Key HG org details:**
- 93+ models as of May 2026
- Models include: Gemma 4 (E2B, E4B), Gemma3 (1B, 4B, 12B), Llama 3.2 (1B, 3B), Phi-4, Qwen 2.5, FunctionGemma, FastVLM, EmbeddingGemma
- `.litertlm` files typically 500MB–6GB depending on model size
- All models public (no HuggingFace token needed for downloads)

**No new HTTP client needed.** The existing Retrofit + OkHttp setup handles all HF API calls.

---

## 4. Room Database Schema Changes

| Change | Details |
|--------|---------|
| `models` table: add `model_format` column | `TEXT`, values: `"gguf"` or `"litertlm"`. Enables filtering in UI tabs. |
| `models` table: add `backend_type` column | `TEXT`, values: `"cpu"`, `"gpu"`, `"npu"`. Stores the backend used for this model. |
| `conversations` table: add `engine_type` column | `TEXT`, values: `"llama_cpp"`, `"litertlm"`, `"remote"`. Links conversation to the inference engine that created it. |
| `presets` table | **No schema change needed if parameters overlap.** LiteRT-LM's `SamplerConfig` maps 1:1 to existing `top_k`, `top_p`, `temperature`, `seed` fields. Thread count is CPU-backend-specific. May need separate `litertlm_presets` table if parameter semantics diverge, but YAGNI for now. |

**Migration:** Use Room's `Migration(1, 2)` with `ALTER TABLE ... ADD COLUMN ... DEFAULT ...`.

---

## 5. Memory & Performance Considerations

| Concern | llama.cpp (existing) | LiteRT-LM (new) |
|---------|---------------------|------------------|
| Model format | GGUF (with quantization) | `.litertlm` (LiteRT flatbuffer) |
| Loading time | 2–10 seconds (JNI) | 2–10 seconds (JNI inside AAR), `engine.initialize()` blocks |
| Memory (7B model) | ~4–5 GB RAM | ~4–5 GB RAM (similar footprint) |
| CPU inference | Native C++, OpenMP threads | Native C++ via LiteRT runtime, thread config |
| GPU inference | Vulkan (opt-in, separate CMake build) | OpenCL (built-in, one manifest entry) |
| NPU inference | None (experimental Hexagon) | NPU backend (Snapdragon 8 Gen 3+, Google Tensor) |
| Streaming speed | Token-by-token via JNI callback | `Flow<Message>` chunks via `callbackFlow` |
| Benchmark metrics | Manual token counting | Built-in `BenchmarkInfo` (experimental) |
| Cache warmup | None | Optional `cacheDir` speeds up subsequent loads |

**Memory management (shared between engines):**
- Only ONE engine should be active at a time. Loading both llama.cpp and LiteRT-LM models
  simultaneously will cause OOM on most devices (< 12GB RAM).
- Use `android:largeHeap="true"` (already set for llama.cpp).
- Check `ActivityManager.MemoryInfo` before loading any model.
- `Engine.close()` frees native resources — call before loading a different model.

---

## 6. Architecture Integration Points

### Where LiteRT-LM fits in Clean Architecture

```
UI Layer
  └── chat.ui.LiteRtLmChatScreen.kt          (new — separate tab)
  └── modelpicker.ui.LiteRtLmModelPicker.kt   (new — litert-community browser)

Domain Layer
  └── chat.domain.LiteRtLmChatUseCase.kt      (new — wraps Engine/Conversation)
  └── model.domain.ModelRepository.kt         (modified — handles both formats)
  └── model.domain.LiteRtLmModelRepository.kt (new — litertlm-specific queries)

Data Layer
  └── inference.data.LiteRtLmEngine.kt        (new — Engine lifecycle wrapper)
  └── modelsearch.data.LiteRtLmModelSearch.kt (new — litert-community Retrofit)
  └── download.data.LiteRtLmDownloader.kt     (modified — .litertlm file handling)
  └── modelstore.data.LiteRtLmModelDao.kt     (modified Room queries)
```

### Hilt Module (new)

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object LiteRtLmModule {
    @Provides @Singleton
    fun provideLiteRtLmEngineFactory(): LiteRtLmEngineFactory =
        LiteRtLmEngineFactory()
}
```

### ViewModel Pattern (unchanged)

```kotlin
@HiltViewModel
class LitertLmChatViewModel @Inject constructor(
    private val litertLmEngineFactory: LiteRtLmEngineFactory,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LitertLmChatUiState>(LitertLmChatUiState.Loading)
    val uiState: StateFlow<LitertLmChatUiState> = _uiState.asStateFlow()

    fun sendMessage(contents: String) {
        viewModelScope.launch(Dispatchers.Default) {
            conversation.sendMessageAsync(contents)
                .catch { _uiState.value = LitertLmChatUiState.Error(it) }
                .collect { message ->
                    _uiState.update { state ->
                        state.appendMessage(message)
                    }
                }
        }
    }
}
```

**Key:** `sendMessageAsync()` blocks internally on a native thread (managed by the JNI layer),
so it should be called from `Dispatchers.Default`, not `Dispatchers.Main`. The `Flow`
collection itself can happen on `Dispatchers.Main` since it's just emitting pre-computed chunks.

---

## 7. What NOT to Add

| Technology | Why NOT | What to Use Instead |
|-----------|---------|---------------------|
| **Separate NDK/CMake build** | LiteRT-LM ships pre-built native libs in the AAR. Adding CMake would duplicate native code. | Single Maven dependency (`litertlm-android`) |
| **Custom JNI bridge** | The library already provides a Kotlin wrapper over JNI. Writing custom JNI would fight the API. | Use `Engine`/`Conversation` directly |
| **`.gguf` to `.litertlm` converter** | Models from `litert-community` are pre-converted. No runtime conversion needed. If users need custom conversion, point them to desktop tools or the LiteRT CLI. | Models from litert-community only |
| **`litertlm-jvm` dependency** | JVM artifact is for desktop (Linux/macOS/Windows). Not needed on Android. Adds size to APK with no benefit. | `litertlm-android` only |
| **Bazel build system** | LiteRT-LM is developed with Bazel but Maven artifacts are pre-built. Gradle users only need the Maven coordinate. | Standard Gradle + version catalog |
| **Gson as app-wide JSON library** | LiteRT-LM uses Gson internally (`com.google.gson`) but the app already uses `kotlinx.serialization`. LiteRT-LM's Gson is a transitive dependency — accept it but don't use it in app code. | Keep kotlinx.serialization for app code |
| **New HTTP client** | HF litert-community uses the same REST API. Reuse existing Retrofit + OkHttp. | Existing networking stack |
| **New DI framework** | Hilt already provides `@Singleton` scoping for engine lifecycle. | Existing Hilt |
| **New database** | Room already handles model metadata. Add columns/tables. | Existing Room |
| **`latest.release` version pinning** | Unpinned versions break CI deterministically. Pinned versions with Renovate/Dependabot are safer. | Pin to `0.10.2` explicitly |
| **FusedLocationProvider or other irrelevant Google Play Services** | LiteRT-LM has no dependency on Play Services. Don't conflate. | N/A |

---

## 8. Transitive Dependencies (brought in by `litertlm-android`)

| Dependency | Version (approx) | Impact |
|-----------|-----------------|--------|
| `com.google.gson:gson` | 2.10.x+ | Already a very common transitive. No conflict with kotlinx.serialization (different use). Accept. |
| LiteRT-LM native `.so` | Bundled in AAR | Adds ~5-15 MB to APK per ABI (model-dependent). Accept as cost of built-in inference. |
| Coroutine libraries | Bundled | LiteRT-LM's Kotlin API uses `kotlinx.coroutines` internally. Should share same version as app's coroutines dep. Verify via `./gradlew app:dependencies`. |

**APK size impact estimate:** The `litertlm-android` AAR is larger than a thin JNI wrapper because it includes the full LiteRT runtime. Expect ~10-20 MB additional APK size per ABI. This is comparable to llama.cpp's `.so` size.

---

## 9. Integration Checklist (Build System Changes)

### `libs.versions.toml` additions

```toml
[versions]
litertlm = "0.10.2"

[libraries]
litertlm-android = { group = "com.google.ai.edge.litertlm", name = "litertlm-android", version.ref = "litertlm" }
```

### `app/build.gradle.kts` — no `externalNativeBuild` needed

Unlike llama.cpp which requires:
```kotlin
externalNativeBuild {
    cmake { path("src/main/cpp/CMakeLists.txt") }
}
```

LiteRT-LM needs **none of this**. Just the dependency.

### `AndroidManifest.xml` — GPU native lib declaration

```xml
<application>
    <uses-native-library android:name="libvndksupport.so" android:required="false"/>
    <uses-native-library android:name="libOpenCL.so" android:required="false"/>
</application>
```

### ProGuard/R8 — No special keep rules needed

LiteRT-LM's JNI entry points are inside the AAR and packaged with their own ProGuard rules.
No app-level keep rules needed. Standard `minifyEnabled = true` works.

### Min SDK: No change needed

LiteRT-LM's minimum Android version is API 24+. Current minSdk = 28, already above requirement.

---

## 10. Confidence Levels

| Area | Confidence | Notes |
|------|-----------|-------|
| Core dependency (`litertlm-android` v0.10.2) | **HIGH** | Verified via Google Maven metadata XML. v0.10.2 is latest stable. |
| API surface (Engine, Conversation, Config, Backend) | **HIGH** | Verified via official source code in `kotlin/java/com/google/ai/edge/litertlm/`. |
| Streaming pattern (`Flow<Message>`) | **HIGH** | Verified via `Conversation.kt` source — uses `callbackFlow {}` internally. |
| GPU backend (OpenCL) | **HIGH** | Verified via official docs and `Config.kt` source. |
| NPU backend | **MEDIUM** | API is stable but NPU driver availability is device-specific. Auto-detection strategy needs runtime testing. |
| Hugging Face litert-community org | **HIGH** | Verified via `huggingface.co/litert-community` — 93+ models, active maintainers from Google. |
| `.litertlm` download (file sizes, resume) | **HIGH** | Same HF CDN as GGUF — existing OkHttp `Range` header approach works identically. |
| Memory footprint comparison | **MEDIUM** | `.litertlm` uses LiteRT flatbuffer format — based on official docs descriptions. Exact memory delta vs GGUF needs benchmarking. |
| APK size impact | **MEDIUM** | Estimated 10-20 MB based on prebuilt AAR structure. Exact impact needs build measurement. |
| Gson transitive dependency | **HIGH** | Confirmed from source code imports. No conflict with kotlinx.serialization. |
| ProGuard compatibility | **HIGH** | LiteRT-LM AAR bundles its own consumer rules. |
| SamplerConfig parameter mapping to presets | **HIGH** | `SamplerConfig(topK, topP, temperature, seed)` maps cleanly to existing preset fields. |
| Tool use / function calling API | **HIGH** | Verified in `Tool.kt` source — `@Tool` annotation, `ToolSet`, `OpenApiTool`. (Out of scope for v1.1 but API is stable.) |
| BenchmarkInfo API | **MEDIUM** | Marked `@ExperimentalApi`. API shape may change in future releases. |

---

## Sources

| Source | URL | Confidence |
|--------|-----|------------|
| LiteRT-LM GitHub (README, releases) | `https://github.com/google-ai-edge/LiteRT-LM` | HIGH |
| Kotlin API Getting Started Guide | `https://raw.githubusercontent.com/google-ai-edge/LiteRT-LM/main/docs/api/kotlin/getting_started.md` | HIGH |
| Engine.kt source | `https://raw.githubusercontent.com/google-ai-edge/LiteRT-LM/main/kotlin/java/com/google/ai/edge/litertlm/Engine.kt` | HIGH |
| Config.kt source | `https://raw.githubusercontent.com/google-ai-edge/LiteRT-LM/main/kotlin/java/com/google/ai/edge/litertlm/Config.kt` | HIGH |
| Conversation.kt source | `https://raw.githubusercontent.com/google-ai-edge/LiteRT-LM/main/kotlin/java/com/google/ai/edge/litertlm/Conversation.kt` | HIGH |
| Google Maven metadata XML | `https://dl.google.com/dl/android/maven2/com/google/ai/edge/litertlm/litertlm-android/maven-metadata.xml` | HIGH |
| Hugging Face litert-community org | `https://huggingface.co/litert-community` | HIGH |
| Context7 LiteRT-LM library | `/google-ai-edge/litert-lm` | HIGH |

---

*Stack research for: LiteRT-LM integration into Warped (v1.1)*
*Researched: 2026-05-02*
