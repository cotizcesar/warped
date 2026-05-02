# Architecture: Warped v1.1 — LiteRT-LM Integration

**Domain:** Android LLM client with dual local engines
**Researched:** 2026-05-02
**Confidence:** HIGH (verified against LiteRT-LM source code at v0.10.2, existing Warped codebase)

---

## 1. Executive Integration Summary

LiteRT-LM integrates as a **second local inference provider** alongside llama.cpp, reusing the existing `LlmProvider` domain abstraction without modification. The integration is a **parallel pattern** — not a replacement or refactor of the existing local inference path. Both engines coexist under the UI's model selection system, differentiated by model format (GGUF vs .litertlm) and `ProviderType`.

**The central `LlmProvider` interface does not change.** LiteRT-LM gets its own provider implementation (`LiteRTLmProvider`) that conforms to the same contract. The key architectural challenge is mapping LiteRT-LM's stateful `Engine → Conversation` lifecycle onto the existing stateless `LlmProvider.chat()` call pattern.

---

## 2. High-Level Architecture (Updated)

```
┌─────────────────────────────────────────────────────────────────┐
│  UI LAYER (Jetpack Compose + ViewModels)                         │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐           │
│  │ChatScreen│ │ModelsScr │ │Endpoints │ │SettingsSc│           │
│  │(unified) │ │(GGUF tab │ │Screen    │ │reen      │           │
│  │          │ │ +LRTLM   │ │(remote    │ │(presets) │           │
│  │          │ │ tab)     │ │ only)     │ │          │           │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘           │
│       │            │            │             │                 │
├───────┴────────────┴────────────┴─────────────┴─────────────────┤
│  DOMAIN LAYER (pure Kotlin, zero Android deps)                   │
│  ┌───────────────────────────────┐  ┌──────────────────────────┐│
│  │ LlmProvider (interface)       │  │ ProviderType enum         ││
│  │   chat(ChatRequest): Flow     │  │   OPENAI, ANTHROPIC,      ││
│  │   listModels(): Result<>      │  │   OLLAMA, LM_STUDIO,      ││
│  │   testConnection(): Result<>  │  │   CUSTOM, LOCAL,          ││
│  └───────────────────────────────┘  │   LITERT_LM  ← NEW      ││
│  ┌────────────────────────────────┐ └──────────────────────────┘│
│  │ StreamToken, ChatRequest,      │                             │
│  │ GenerationParameters,          │                             │
│  │ ModelInfo, ConnectionStatus    │                             │
│  └────────────────────────────────┘                             │
├──────────────────────┬──────────────────────────────────────────┤
│  DATA LAYER (all)    │  NATIVE LAYER (two engines)              │
│  ┌────────────────┐  │  ┌────────────────────────────────────┐ │
│  │ LocalLlmProvider│  │  │ :library:llama-native              │ │
│  │ (wraps Llama   │  │  │  JNI bridge, .so, GGUF loader       │ │
│  │  Engine)       │──┼──│  LlamaEngine (JNI-based)            │ │
│  └────────────────┘  │  │  Vulkan/NNAPI delegates             │ │
│  ┌────────────────┐  │  └────────────────────────────────────┘ │
│  │ LiteRTLmProvider│  │  ┌────────────────────────────────────┐ │
│  │ (wraps LiteRT  │  │  │ :library:litertlm              NEW  │ │
│  │  Engine)       │──┼──│  LiteRTLmEngine (wraps Engine API) │ │
│  └────────────────┘  │  │  Backend detection service           │ │
│  ┌────────────────┐  │  │  lrtlm-android Maven dependency     │ │
│  │ ProviderRouter │  │  └────────────────────────────────────┘ │
│  │ (resolves to   │  │                                         │
│  │  correct impl) │  │                                         │
│  └────────────────┘  │                                         │
├──────────────────────┴──────────────────────────────────────────┤
│  INFRASTRUCTURE: Room, DataStore, OkHttp, WorkManager, Keystore │
└─────────────────────────────────────────────────────────────────┘
```

**Key principle:** The UI layer never knows which engine is running. It only sees `ProviderType`, `ModelInfo`, and the token stream.

---

## 3. Integration Layers — What Changes per Layer

### 3.1 Domain Layer (minimal change, 1 new enum value)

| What | Action | File |
|------|--------|------|
| `ProviderType` enum | Add `LITERT_LM` | `domain/model/ProviderType.kt` |
| `GenerationParameters` | No change. Map fields to `SamplerConfig` in data layer | — |
| `LlmProvider` interface | **No change.** LiteRT-LM fits the contract | — |
| `StreamToken`, `ChatRequest`, `ModelInfo` | No change | — |

**Why `LITERT_LM` not reuse `LOCAL`:** The UI requires separate model tabs (GGUF vs .litertlm), different download sources (HF main vs litert-community), different import UX, and different generation parameter sets. A single `LOCAL` type would force conditional branching throughout the system. Explicit typing is cheaper than runtime type checks.

### 3.2 Data Layer (2 new components, 2 modified)

| Component | Action | Layer |
|-----------|--------|-------|
| `LiteRTLmEngine` | **NEW** — wrapper around LiteRT-LM `Engine` lifecycle | `data/local/inference/` |
| `LiteRTLmProvider` | **NEW** — implements `LlmProvider`, wraps `LiteRTLmEngine` | `data/local/inference/` |
| `ProviderRouter` | **MODIFIED** — add `ProviderType.LITERT_LM` case | `data/remote/provider/` |
| `ProviderModule` (DI) | **MODIFIED** — provide `LiteRTLmEngine`, `LiteRTLmProvider` bindings | `di/` |
| `ModelRepository` | **NO CHANGE** — same `LocalModel` entity, distinguished by `filePath` extension | — |
| `DownloadManager` | **MODIFIED** — add `.litertlm` extension to whitelist, litert-community HF endpoint | `data/download/` |

### 3.3 UI Layer (1 new screen section, 1 modified)

| Component | Action |
|-----------|--------|
| `ModelsScreen` | Add second tab: "GGUF" and "LiteRT-LM" |
| `ChatScreen` | No change — provider-agnostic by design |
| `SettingsScreen` | Add backend preference (GPU/CPU/NPU selection for LiteRT-LM) |
| Navigation | Add route for LiteRT-LM model detail (or reuse existing) |

### 3.4 Module Structure (1 new module)

```
app/
├── :core:common          # + ProviderType.LITERT_LM
├── :core:network
├── :core:database
├── :core:security
├── :core:ui
├── :feature:chat
├── :feature:models       # + LiteRT-LM tab, litert-community HF API
├── :feature:endpoints
├── :feature:settings     # + LiteRT-LM backend config
├── :library:llama-native # JNI bridge (unchanged)
└── :library:litertlm     # NEW: LiteRTLmEngine + backend detection
```

**Dependency rule for `:library:litertlm`:**
- Depends on: `:core:common` (domain models only, zero Android deps beyond `android.content.Context`)
- Never depends on: `:core:network`, `:core:database`, `:feature:*`
- Exposes only: `LiteRTLmEngine` (data layer class) and `BackendDetector` (utility)

---

## 4. Core Integration Pattern: Stateless chat() over Stateful Conversation

### 4.1 The Problem

The existing architecture treats each `LlmProvider.chat()` call as stateless — the provider receives a full message history and returns a token stream. The engine (LlamaEngine) manages a single model context that gets prompted anew each time.

LiteRT-LM's API is stateful: `Engine` holds a loaded model, and `Conversation` accumulates message history internally. Each `sendMessage()` appends to that history. A fresh `Conversation` is needed per `chat()` call.

### 4.2 The Solution: Per-Call Conversation Factory

```
chat(request: ChatRequest) → create fresh Conversation(initialMessages=request.messages)
                              → conversation.sendMessageAsync()
                                → map LiteRT-LM Message.text segments → StreamToken.Delta
                                → close conversation on completion/error
                              → return Flow<StreamToken>
```

**Key insight:** LiteRT-LM's `ConversationConfig.initialMessages` accepts a `List<Message>` exactly matching our `ChatRequest.messages`. No prompt rewriting needed — the engine handles internal templating.

```kotlin
// LiteRTLmProvider.kt — pseudocode
override fun chat(request: ChatRequest): Flow<StreamToken> = flow {
    checkInitialized()

    val lrtlmMessages = request.messages.map { msg ->
        when (msg.role) {
            Role.SYSTEM -> Message.system(msg.content)
            Role.USER -> Message.user(msg.content)
            Role.ASSISTANT -> Message.model(msg.content)
        }
    }

    val samplerConfig = SamplerConfig(
        topK = request.parameters.topK,
        topP = request.parameters.topP.toDouble(),
        temperature = request.parameters.temperature.toDouble(),
        seed = if (request.parameters.seed >= 0) request.parameters.seed else 0
    )

    val convConfig = ConversationConfig(
        initialMessages = lrtlmMessages,
        samplerConfig = samplerConfig,
    )

    val conversation = engine.createConversation(convConfig)
    var lastContent = ""
    try {
        conversation.sendMessageAsync(Message.user(""))  // final user message
            .collect { message ->
                val delta = message.text.removePrefix(lastContent)
                if (delta.isNotEmpty()) {
                    lastContent = message.text
                    emit(StreamToken.Delta(delta))
                }
            }
        emit(StreamToken.Done())
    } catch (e: Exception) {
        emit(StreamToken.Error(e.message ?: "Inference error"))
    } finally {
        conversation.close()
    }
}.flowOn(Dispatchers.Default)
```

### 4.3 Why Not Reuse One Conversation?

A single long-lived `Conversation` accumulating all messages across `chat()` calls would:
- Grow the KV cache indefinitely, degrading performance
- Leak context from previous conversations (user switches chat → old messages still in model context)
- Not support switching models mid-session cleanly

Per-call `Conversation` gives clean isolation, predictable memory usage, and matches the existing stateless contract.

---

## 5. Engine Lifecycle Management

### 5.1 Lifecycle Comparison

| Phase | LlamaEngine | LiteRTLmEngine |
|-------|------------|----------------|
| **Load/Init** | `loadModel(path)` — ~2-5s, loads GGUF into memory | `engine.initialize()` — ~5-10s, loads .litertlm, compiles for backend |
| **Ready state** | `isLoaded() = true`, model in RAM (~4GB) | `isInitialized() = true`, model in RAM (~similar) |
| **Generate** | `generate(prompt)` — JNI call on native thread | `createConversation().sendMessageAsync()` — JNI call on native thread |
| **Unload/Close** | `unload()` — frees RAM | `engine.close()` — frees RAM + cached files |
| **Concurrent use** | One context, serial access (Mutex) | Multiple Conversations, internally serialized by engine |

### 5.2 LiteRTLmEngine Wrapper

```kotlin
// data/local/inference/LiteRTLmEngine.kt
@Singleton
class LiteRTLmEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backendDetector: BackendDetector,
) {
    private var engine: Engine? = null
    private var currentModelPath: String = ""
    private val lock = Mutex()

    val isInitialized: Boolean get() = engine?.isInitialized() ?: false

    suspend fun initialize(modelPath: String, preferredBackend: Backend? = null): Result<Unit> =
        lock.withLock {
            if (isInitialized && modelPath == currentModelPath) return Result.success(Unit)

            // Close existing engine if switching models
            engine?.close()
            engine = null

            val backend = preferredBackend ?: backendDetector.detectBestBackend(context)

            val config = EngineConfig(
                modelPath = modelPath,
                backend = backend,
                cacheDir = context.cacheDir.path,
            )

            return try {
                withContext(Dispatchers.Default) {
                    Engine(config).also { newEngine ->
                        newEngine.initialize()
                        engine = newEngine
                        currentModelPath = modelPath
                    }
                }
                Result.success(Unit)
            } catch (e: LiteRtLmJniException) {
                // Try fallback: GPU → CPU
                if (backend !is Backend.CPU) {
                    initialize(modelPath, Backend.CPU())
                } else {
                    Result.failure(e)
                }
            } catch (e: Exception) {
                engine = null
                Result.failure(e)
            }
        }

    fun createConversation(config: ConversationConfig = ConversationConfig()): Conversation {
        check(isInitialized) { "Engine not initialized" }
        return engine!!.createConversation(config)
    }

    suspend fun close() = lock.withLock {
        engine?.close()
        engine = null
        currentModelPath = ""
    }
}
```

### 5.3 Backend Detection Service

```kotlin
// library/litertlm/BackendDetector.kt
class BackendDetector @Inject constructor() {
    fun detectBestBackend(context: Context): Backend {
        // Priority: GPU > CPU (NPU requires native libraries not bundled by default)

        // GPU detection: check for Vulkan/OpenCL support
        if (hasGpuSupport(context)) {
            return Backend.GPU()
        }

        // CPU with optimal thread count
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
        return Backend.CPU(numOfThreads = threads)
    }

    private fun hasGpuSupport(context: Context): Boolean {
        return try {
            // Check if OpenCL lib is loadable (LiteRT-LM uses OpenCL for GPU on Android)
            System.loadLibrary("OpenCL")
            true
        } catch (e: UnsatisfiedLinkError) {
            false
        }
    }
}
```

---

## 6. Parameter Mapping

### 6.1 GenerationParameters → LiteRT-LM SamplerConfig

| Warped `GenerationParameters` | LiteRT-LM `SamplerConfig` | Mapping |
|--------|----------|---------|
| `temperature: Float` | `temperature: Double` | Direct cast |
| `topP: Float` | `topP: Double` | Direct cast |
| `topK: Int` | `topK: Int` | Direct pass |
| `seed: Int` | `seed: Int` | Direct pass (default 0 if -1) |
| `maxTokens: Int` | `EngineConfig.maxNumTokens` | Set at engine init, not per-request |
| `contextSize: Int` | N/A | Built into model, not configurable |
| `repeatPenalty: Float` | N/A | Not supported by current API |
| `threads: Int` | `Backend.CPU(numOfThreads)` | Set at engine init, not per-request |
| `reasoningEnabled: Boolean` | `channels` in ConversationConfig | Enable thinking channel if true |

### 6.2 Parameter Hierarchy

```
Engine-scoped (set once on init, model-specific):
  - maxNumTokens (maxTokens) → EngineConfig.maxNumTokens
  - threads → Backend.CPU(numOfThreads)
  - contextSize → model-inherent (not configurable)

Conversation-scoped (set per chat() call):
  - temperature, topP, topK, seed → ConversationConfig.samplerConfig
  - reasoningEnabled → ConversationConfig.channels
```

---

## 7. Hugging Face Integration for LiteRT-LM Models

### 7.1 Endpoint Differences

| Aspect | GGUF (llama.cpp) | LiteRT-LM (.litertlm) |
|--------|-----------------|----------------------|
| HF Organization | All Hugging Face (TheBloke, bartowski, etc.) | `litert-community` org |
| API filter | `?filter=gguf` | `?search=litertlm&author=litert-community` |
| File extension | `.gguf` | `.litertlm` |
| API endpoint | `GET /api/models` | Same (HF Hub API) |
| Download URL | `/{repo}/resolve/main/{file}` | Same pattern |

### 7.2 New Retrofit Interface (extend existing)

```kotlin
// Same HF API, different query parameters
interface HuggingFaceApi {
    // Existing GGUF search
    @GET("api/models")
    suspend fun searchModels(
        @Query("search") query: String,
        @Query("filter") filter: String = "gguf",
        @Query("sort") sort: String = "downloads",
        @Query("limit") limit: Int = 20,
    ): List<HFModel>

    // NEW: LiteRT-LM model search
    @GET("api/models")
    suspend fun searchLiteRTLmModels(
        @Query("search") query: String,
        @Query("author") author: String = "litert-community",
        @Query("sort") sort: String = "downloads",
        @Query("limit") limit: Int = 20,
    ): List<HFModel>
}
```

---

## 8. ProviderRouter Changes

```kotlin
// Modified ProviderRouter.kt
@Singleton
class ProviderRouter @Inject constructor(
    private val apiKeyStore: ApiKeyStore,
    private val localLlmProvider: dagger.Lazy<LocalLlmProvider>,
    private val liteRTLmProvider: dagger.Lazy<LiteRTLmProvider>,  // NEW
) {
    fun resolve(endpoint: Endpoint, modelId: String): LlmProvider {
        return when (endpoint.apiType) {
            // ... existing cases unchanged ...
            ProviderType.LOCAL -> localLlmProvider.get().configure(modelId)
            ProviderType.LITERT_LM -> liteRTLmProvider.get().configure(modelId)  // NEW
        }
    }
}
```

---

## 9. DI Module Changes

```kotlin
// ProviderModule.kt — added LiteRT-LM bindings
@Module
@InstallIn(SingletonComponent::class)
abstract class ProviderModule {
    @Binds
    abstract fun bindLiteRTLmEngine(impl: LiteRTLmEngine): LiteRTLmEngine  // NEW

    companion object {
        @Provides
        @Singleton
        fun provideLiteRTLmProvider(
            engine: LiteRTLmEngine,
        ): LiteRTLmProvider = LiteRTLmProvider(engine)  // NEW
    }
}
```

---

## 10. Data Flow: LiteRT-LM Chat (full path)

```
User types message in ChatScreen
  → ChatViewModel.sendMessage(text)
    → SendMessageUseCase.execute(conversationId, text)
      → ChatRepository.sendMessage(...)
        → EndpointRepository.getActive() → Endpoint(apiType = LITERT_LM)
        → ProviderRouter.resolve(endpoint) → LiteRTLmProvider
          → LiteRTLmEngine.initialize(modelPath)        [if not already]
          → LiteRTLmEngine.createConversation(config)
            → config includes: initialMessages from ChatRequest + samplerConfig
          → conversation.sendMessageAsync() → Flow<Message>
            → each Message has .text with accumulated content
            → compute delta = currentText - previousText
            → emit StreamToken.Delta(delta)
          → on complete: emit StreamToken.Done()
          → always: conversation.close()
        ← ProviderRouter returns Flow<StreamToken>
      ← ChatRepository persists message via Room
    ← UseCase returns Flow<StreamState>
  ← ViewModel collects Flow, updates StateFlow<ChatUiState>
← ChatScreen recomposes with new token
```

**Comparison with existing local path:** Identical from ChatViewModel down through ProviderRouter. Only the ProviderRouter branch changes. The ViewModel and UI never know it's LiteRT-LM vs llama.cpp vs remote.

---

## 11. Architecture Patterns

### Pattern 1: Provider Polymorphism (existing, unchanged)

All providers implement `LlmProvider`. UI is completely decoupled from engine specifics. LiteRT-LM is just another implementation — no architectural change needed.

**Why this works without change:** Even though LiteRT-LM has a fundamentally different API (stateful Engine→Conversation vs stateless prompt-based), the `chat()` method signature abstracts this away. The wrapping in `LiteRTLmProvider` is the adapter.

### Pattern 2: Per-Call Conversation (new)

LiteRT-LM's stateful `Conversation` is mapped to a stateless interface by creating and closing a `Conversation` per `chat()` call.

**When to use:** Any time a stateful SDK must be used through a stateless interface.

**Trade-offs:**
- **Pro:** Clean isolation between chat instances, no stale KV cache accumulation
- **Pro:** Matches existing architecture without interface changes
- **Con:** Slightly slower per-call startup (creating Conversation is fast, ~10ms — only Engine initialization takes seconds)
- **Con:** Cannot support true multi-turn stateful chats through the interface (but this matches current design — each `chat()` always sends full history)

### Pattern 3: Backend Fallback Chain (new)

```kotlin
// Try backends in order: NPU → GPU → CPU
fun resolveBackend(selected: Preference): Backend {
    return when (selected) {
        AUTO -> {
            if (canUseNPU(context)) Backend.NPU(nativeLibraryDir)
            else if (canUseGPU()) Backend.GPU()
            else Backend.CPU(getOptimalThreads())
        }
        GPU -> Backend.GPU()
        CPU -> Backend.CPU(getOptimalThreads())
        NPU -> Backend.NPU(nativeLibraryDir)
    }
}
```

**When to use:** Engine initialization. If GPU init fails, auto-retry with CPU before surfacing error to user.

---

## 12. Anti-Patterns to Avoid

### Anti-Pattern 1: Long-Lived Conversation Reuse

**What people do:** Create one `Conversation` at model load, reuse it for all `chat()` calls.

**Why it's wrong:**
- KV cache accumulates all messages across all conversations
- Memory grows unbounded
- Switching between chats carries old context into new chats
- `maxNumTokens` exhaustion causes truncation at unpredictable points

**Do this instead:** Create fresh `Conversation` per `chat()` call. Engine initialization is the expensive part (~5-10s) — `createConversation()` is cheap (~10ms). Keep the Engine alive, not the Conversation.

### Anti-Pattern 2: Hardcoding Backend

**What people do:** Always use `Backend.GPU()`.

**Why it's wrong:** Many Android devices lack GPU libraries, have incompatible drivers, or crash on GPU inference. `LiteRtLmJniException` during init causes unhandled failures.

**Do this instead:** Runtime backend detection. Try GPU, catch failure, fall back to CPU. Surface the actual backend used in UI. Allow user override.

### Anti-Pattern 3: Blocking UI Thread During Engine Initialize

**What people do:** Call `engine.initialize()` on the main thread.

**Why it's wrong:** `initialize()` can take 5-10 seconds. This guarantees ANR.

**Do this instead:** Always call on `Dispatchers.Default` (CPU-bound, not IO-bound). The existing `LiteRTLmEngine.initialize()` already wraps in `withContext(Dispatchers.Default)`. Show loading indicator in UI while model initializes.

---

## 13. Modified vs New Components Summary

### New (created from scratch)

| Component | Purpose | Module |
|-----------|---------|--------|
| `LiteRTLmEngine` | Wraps LiteRT-LM `Engine` lifecycle | `:library:litertlm` |
| `LiteRTLmProvider` | Implements `LlmProvider` for LiteRT-LM | `data/local/inference/` |
| `BackendDetector` | Runtime GPU/NPU/CPU capability detection | `:library:litertlm` |
| `LiteRTLmModelRepository` | litert-community HF search API methods | `data/repository/` (or extend existing) |
| `LiteRTLmEngineConfig` | Domain model for engine-scoped params | `data/local/inference/` |

### Modified (additions to existing)

| Component | Change | Why |
|-----------|--------|-----|
| `ProviderType` enum | Add `LITERT_LM` | UI needs separate tab/filter |
| `ProviderRouter` | Add `LITERT_LM → liteRTLmProvider` case | Routing to correct provider |
| `ProviderModule` (DI) | Add `LiteRTLmEngine` and `LiteRTLmProvider` bindings | Hilt dependency graph |
| `HuggingFaceApi` (Retrofit) | Add `searchLiteRTLmModels()` method | litert-community endpoint |
| `DownloadManager` | Add `.litertlm` extension support | Download .litertlm files |
| `ModelsScreen` | Add second tab "GGUF \| LiteRT-LM" | Separate model ecosystems |
| `SettingsScreen` | Add backend preference (Auto/GPU/CPU/NPU) | User control over acceleration |
| `build.gradle.kts` | Add `litertlm-android` Maven dependency | Library dependency |
| `AndroidManifest.xml` | Add `<uses-native-library>` for GPU | Required by LiteRT-LM GPU |

### Unchanged (no modifications)

| Component | Reason unchanged |
|-----------|-----------------|
| `LlmProvider` interface | Same contract works for LiteRT-LM |
| `ChatRequest`, `StreamToken`, `ChatMessage` | Same domain models |
| `GenerationParameters` | Map fields in data layer, no domain change |
| `ChatViewModel`, `ChatScreen` | Provider-agnostic by design |
| `Room` schema (conversations, messages) | Same chat data model |
| `Endpoint` entity | Already has `apiType: ProviderType` field |
| `DataStore`, `Keystore` | No change needed |
| `:library:llama-native` | Unchanged — coexistence, not replacement |
| `Remote providers` (OpenAI, Ollama, etc.) | Unchanged |
| `WorkManager`, `Coroutine` infrastructure | Unchanged |

---

## 14. Suggested Build Order for LiteRT-LM Integration

This is the build order within the v1.1 milestone, assuming the v1.0 codebase (with working llama.cpp) exists:

### Phase 1: Engine Integration (deepest dependency, no UI)

```
:library:litertlm module
  ├── BackendDetector (GPU detection logic)
  ├── LiteRTLmEngine (lifecycle wrapper)
  └── LiteRTLmEngineConfig (engine-scoped params)

DI bindings (ProviderModule)
  └── Provide LiteRTLmEngine as @Singleton

Gradle dependency
  └── com.google.ai.edge.litertlm:litertlm-android:0.10.2
```

**Why first:** Everything else depends on being able to load a `.litertlm` model and get tokens. This is the foundation. Testable with a tiny model (Gemma-3N-1B or FunctionGemma-270M) without any UI.

**Deliverable:** Unit test proving `LiteRTLmEngine.initialize(path)` → `createConversation().sendMessageAsync()` returns tokens.

### Phase 2: Provider Integration (domain → data wiring)

```
Domain layer
  └── ProviderType.LITERT_LM added

Data layer
  ├── LiteRTLmProvider (implements LlmProvider)
  └── ProviderRouter.resolve() → LITERT_LM case

Parameter mapping
  └── GenerationParameters → SamplerConfig converter
```

**Why second:** Wires the engine into the existing architecture. Enables switching provider type in Endpoint to `LITERT_LM` and routing chat through the new provider.

**Deliverable:** Integration test proving chat pipeline works end-to-end (Endpoint with LITERT_LM type → chat → tokens stream back).

### Phase 3: Model Acquisition (download + management)

```
Hugging Face integration
  └── HuggingFaceApi.searchLiteRTLmModels() (litert-community)
  └── ModelRepository: .litertlm model metadata in Room

DownloadManager
  └── .litertlm extension support
  └── litert-community download URLs

Import
  └── SAF file picker filters for .litertlm
```

**Why third:** Users need models on device to use the engine. Reuses existing download infrastructure — only different HF endpoint and file extension.

**Deliverable:** User can search litert-community, download `.litertlm`, see model in library.

### Phase 4: UI Integration (tabs, settings, UX)

```
ModelsScreen
  └── Tabs: "GGUF" | "LiteRT-LM"
  └── Each tab filters by format extension

SettingsScreen
  └── Backend preference: Auto | GPU | CPU | NPU
  └── Per-model backend override in model detail

ChatScreen
  └── No changes needed (provider-agnostic)
  └── Optional: backend badge in chat header
```

**Why fourth:** UI is the thin layer on top. By this point, the engine works, the provider works, and models can be acquired. This phase makes it user-facing.

**Deliverable:** Full user flow: discover model → download → chat → with correct backend badge.

### Phase 5: Polish & Edge Cases

```
Backend fallback UX
  └── Show "GPU failed, falling back to CPU" toast
  └── Persist backend preference per model

Error recovery
  └── Engine init failure → retry with different backend
  └── Conversation error → clear state, re-init

Memory management
  └── Close engine when all LiteRT-LM conversations idle
  └── Unload on app background
```

**Why last:** Polish after core path works.

---

## 15. Cross-Cutting Concerns

### 15.1 Memory: Two Engines Loaded Simultaneously

**Problem:** llama.cpp model (~4GB) + LiteRT-LM model (~4GB) = 8GB+ RAM. Many devices have 8GB total.

**Mitigation:**
- Only one engine is loaded at a time. When user switches from GGUF tab to LiteRT-LM tab, unload the llama.cpp model first.
- `LiteRTLmEngine.close()` + `LlamaEngine.unload()` are called on tab switch.
- Show confirmation dialog: "Switching engine will unload current model. Continue?"
- Memory check before load: `model_size * 1.2 < available_memory - 500MB_headroom`

### 15.2 Thread Safety

**Existing:** `LlamaEngine` uses callback-based JNI with `callbackFlow{}`. `LocalLlmProvider` uses `flowOn(Dispatchers.Default)`.

**LiteRT-LM:** The `Engine` and `Conversation` internally synchronize with `synchronized(lock)` blocks (verified in source). `sendMessageAsync` runs on a native thread that calls back into Kotlin. Kotlin-side `callbackFlow {}` bridges to coroutines.

**Recommendation:** Same pattern — `LiteRTLmProvider.chat()` runs on `Dispatchers.Default`. The `LiteRTLmEngine` uses a `Mutex` for initialization and closing. Conversations are inherently serial per-engine (the native engine serializes).

### 15.3 Process Death

**Existing pattern:** llama.cpp runs in app process. Native crash takes down entire app. Planned mitigation: separate `:inference` process.

**LiteRT-LM:** Also runs in-process. Same risk profile. LiteRT-LM's JNI layer handles native errors via `LiteRtLmJniException`. No SIGSEGV recovery yet — same mitigation (separate process) applies to both engines.

### 15.4 Thermal Throttling

**Existing:** `PowerManager.getCurrentThermalStatus()` for llama.cpp.

**LiteRT-LM:** Same approach. Monitor thermal status during streaming. Pause on `THERMAL_STATUS_SEVERE`. LiteRT-LM's `Conversation` cannot be paused mid-inference — must either let it complete or close it and re-create.

### 15.5 Coexistence with Remote Providers

Zero impact. Remote providers use `ProviderType.OPENAI/OLLAMA/...` completely independent code paths. ProviderRouter dispatches to correct implementation without awareness of other providers.

---

## 16. Testing Strategy

### 16.1 Unit Tests (JVM, no device)

| Test | What it verifies |
|------|-----------------|
| `LiteRTLmEngine` lifecycle mock | Init → initialized → create conversation → close works |
| `LiteRTLmProvider.chat()` | Maps ChatRequest → ConversationConfig correctly |
| `BackendDetector.detectBestBackend()` | Returns GPU > CPU based on capability flags |
| Parameter mapping | GenerationParameters → SamplerConfig is correct for all edge values |
| `ProviderRouter.resolve(LITERT_LM)` | Returns `LiteRTLmProvider` instance |

### 16.2 Integration Tests (instrumented, real device)

| Test | What it verifies |
|------|-----------------|
| Engine init with tiny model (<100MB) | `initialize()` succeeds, `isInitialized() = true` |
| Streaming chat round-trip | Send text → receive stream of tokens → final Done |
| Engine close and re-init | Close → re-initialize with same/different model |
| Backend fallback | GPU unavailable → CPU fallback succeeds |
| Conversation isolation | Two sequential `chat()` calls don't leak context |

### 16.3 UI Tests (Compose Test)

| Test | What it verifies |
|------|-----------------|
| ModelsScreen tabs | Tap "LiteRT-LM" tab → shows litert-community models |
| Backend preference | Select GPU in settings → preference persisted in DataStore |
| Chat with LiteRT-LM | Full UI flow: open chat, send message, see streaming tokens |

---

## 17. Risk Assessment

| Risk | Severity | Probability | Mitigation |
|------|----------|-------------|------------|
| LiteRT-LM Maven artifact incompatible with project AGP/NDK version | Medium | Low | LiteRT-LM publishes to Google Maven with Gradle metadata. Test early in Phase 1. |
| GPU backend crashes on specific SoCs (Mali, PowerVR) | Medium | Medium | Backend fallback chain. CPU is always available. Log GPU failures per device model for blocklist. |
| Engine init time (5-10s) UX friction | Medium | High | Show loading progress. Cache engine in memory while app is in foreground. Clear "initializing model..." UI state. |
| Two engines loaded simultaneously exhaust RAM | High | Low (preventable) | Enforce single-engine-at-a-time. Memory guard before initialization. |
| LiteRT-LM model format API breaks in future release | Low | Low | Pin to known version in Gradle. Test before upgrading. |

---

## Appendix A: Complete LiteRT-LM API Surface (for Integration)

### Engine initialization
```kotlin
val config = EngineConfig(
    modelPath = "/path/to/model.litertlm",
    backend = Backend.GPU(),              // CPU(), NPU(nativeLibraryDir)
    maxNumTokens = 8192,                  // max input+output tokens (null = model default)
    cacheDir = context.cacheDir.path,     // speeds up 2nd load time
)
val engine = Engine(config)
engine.initialize()  // BLOCKING — 5-10s, run on background thread
engine.close()       // release resources
engine.isInitialized() // check state
```

### Conversation creation & messaging
```kotlin
val convConfig = ConversationConfig(
    systemInstruction = Contents.of("You are a helpful assistant."),
    initialMessages = listOf(Message.user("Hello")),
    samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.8, seed = 0),
)
val conv = engine.createConversation(convConfig)

// Streaming with Flow
conv.sendMessageAsync("What is AI?").collect { message ->
    println(message.text)  // accumulated text per chunk (not delta)
}

// Sync (full response)
val response = conv.sendMessage("What is AI?")

conv.close()
```

### Message types
```kotlin
Message.user("text")                   // User message
Message.user(Contents.of(...))         // User with multi-modal content
Message.model("response text")         // Model response (for initialMessages)
Message.system("system prompt")        // System message (for initialMessages)
Message.tool(Contents.of(...))         // Tool response (manual tool calling)
```

### Backend details
```kotlin
Backend.CPU(numOfThreads = 4)          // null/0 = auto
Backend.GPU()                          // Requires <uses-native-library> in manifest
Backend.NPU(nativeLibraryDir = "...")  // Requires NPU libs bundled or downloaded
```

---

## Appendix B: Open Questions

1. **LiteRT-LM Maven version:** Confirm latest stable version on [Google Maven](https://maven.google.com/web/index.html#com.google.ai.edge.litertlm:litertlm-android). Current release is v0.10.2 (Apr 14, 2026).

2. **Min SDK compatibility:** Verify `litertlm-android` minSdk matches our minSdk 28. The API uses `android.content.Context` and `System.loadLibrary` — likely compatible with API 21+.

3. **APK size impact:** `litertlm-android` includes native .so libraries. Estimate size impact before release.

4. **Model format stability:** `.litertlm` is a new model format. Confirm backward compatibility guarantees. GGUF has years of stability — .litertlm may evolve more quickly.

5. **Context window:** LiteRT-LM's `maxNumTokens` is total input+output (KV cache size), not just output. Our `maxTokens` parameter is output-only. How to map? Use `maxNumTokens = messages_token_count + maxTokens` or set a generous default and let the model handle truncation.

6. **Repeat penalty:** LiteRT-LM doesn't expose repeat_penalty in SamplerConfig. Is it handled internally by the model's generation defaults, or is this a missing feature?

7. **Multi-turn performance:** Does per-call Conversation creation and message replay have a noticeable performance cost for long (>50 messages) conversations?

8. **NPU library bundling:** To use NPU, the app must bundle NPU native libraries or download them. Is the NPU driver available on Google Play Services or must we bundle it? This adds significant APK size.

9. **Offline model availability:** LiteRT-LM models from litert-community are publicly downloadable. Verify they don't require authentication for any models (similar to GGUF from TheBloke).

10. **Model metadata extraction:** Can we extract parameter count, quantization, and architecture from a `.litertlm` file header before full load? Similar to GGUF header parsing.

---

*Architecture research for: Warped v1.1 LiteRT-LM Integration*
*Researched: 2026-05-02*
*Sources: LiteRT-LM GitHub (v0.10.2 source code), existing Warped codebase, Hugging Face litert-community*
