# Architecture

**Analysis Date:** 2026-05-02

## High-Level Architecture

Clean Architecture with 3 strict layers: **UI** → **Domain** → **Data**. Dependencies point inward. The UI knows about Domain models and repository interfaces. The Data layer implements repository interfaces and depends on Domain models. DI wires everything via Hilt at compile time.

```text
┌──────────────────────────────────────────────────────────────────┐
│                        UI Layer                                   │
│  Compose Screens + ViewModels (MVVM)                             │
│  StateFlow<UiState>, collectAsStateWithLifecycle()               │
│  `app/src/main/java/com/warped/ui/`                               │
├──────────────────┬──────────────────┬────────────────────────────┤
│  ChatScreen      │  ModelsScreen    │  Settings/Endpoints/       │
│  ChatViewModel   │  ModelsViewModel │  HFPresets ViewModels       │
│  ChatUiState     │  ModelsUiState   │                             │
└────────┬─────────┴────────┬─────────┴──────────┬─────────────────┘
         │                  │                     │
         ▼                  ▼                     ▼
┌──────────────────────────────────────────────────────────────────┐
│                      Domain Layer                                 │
│  Pure Kotlin (no Android deps except KeystoreManager in           │
│  ActiveModelSelection)                                            │
│  `app/src/main/java/com/warped/domain/`                           │
├──────────────────┬──────────────────┬────────────────────────────┤
│  Models (14)     │  Repository      │  LlmProvider interface      │
│  ChatMessage,    │  interfaces (6)  │  (core abstraction over     │
│  Conversation,   │  ChatRepository, │  local + remote)            │
│  Endpoint,       │  ModelRepository,│                               │
│  LocalModel,     │  EndpointRepo,   │  ProviderType enum (7)      │
│  StreamToken,... │  LocalModelRepo, │                               │
│                  │  HuggingFaceRepo,│                               │
│                  │  PresetRepository│                               │
└────────┬─────────┴────────┬─────────┴──────────┬─────────────────┘
         │                  │                     │
         ▼                  ▼                     ▼
┌──────────────────────────────────────────────────────────────────┐
│                       Data Layer                                  │
│  `app/src/main/java/com/warped/data/`                             │
├───────────────┬────────────────────┬─────────────────────────────┤
│  local/       │  remote/           │  repository/ (6 impls)      │
│  ├─ db/       │  ├─ api/ (6)       │  ChatRepositoryImpl,        │
│  │  Room DB   │  │  Retrofit ifs.  │  EndpointRepositoryImpl,    │
│  │  6 DAOs    │  ├─ dto/ (6)       │  LocalModelRepoImpl,        │
│  │  8 entities│  │  Data classes   │  ModelRepositoryImpl,       │
│  ├─ download/ │  ├─ network/ (5)   │  HuggingFaceRepoImpl,       │
│  │  Worker    │  │  OkHttp,SSE     │  PresetRepositoryImpl       │
│  ├─ inference/│  └─ provider/ (6)  │                             │
│  │  engines   │     ProviderRouter │                             │
│  └─ security/ │     + 5 providers  │                             │
│     Keystore  │                    │                             │
└───────────────┴────────────────────┴─────────────────────────────┘
```

## Layer Boundaries

### UI Layer (`app/src/main/java/com/warped/ui/`)

**Pattern:** MVVM. Each screen has a `ViewModel` that exposes `StateFlow<UiState>`. Composables observe via `collectAsStateWithLifecycle()`.

**Key characteristics:**
- Screens: `ChatScreen`, `ModelsScreen`, `HuggingFaceScreen`, `PresetsScreen`, `SettingsScreen`, `EndpointsScreen`
- ViewModels annotated `@HiltViewModel`, injected repositories via constructor
- UiState data classes are package-private per screen (e.g., `ChatUiState`, `ModelsUiState`)
- Sealed `ChatError` hierarchy for typed error handling
- `WarpedNavGraph` composable owns `NavController`, `ModalNavigationDrawer`, and all navigation logic
- `ModelSelector` composable lives in `ui/chat/components/` — shared UI across chat

**State management:**
- `MutableStateFlow<ChatUiState>` in `ChatViewModel` — single source of truth
- `ActiveModelSelection` (domain, `@Singleton`) persists selected model via Keystore
- `ParameterStore` (domain, `@Singleton`) holds generation parameters in a `StateFlow`
- ViewModels collect repositories' `Flow<List<T>>` and merge into UiState

### Domain Layer (`app/src/main/java/com/warped/domain/`)

**Pattern:** Pure Kotlin. No Android framework dependencies (exception: `ActiveModelSelection` uses `KeystoreManager`).

**Models (14 files):**
| Model | File | Purpose |
|-------|------|---------|
| `ChatMessage` | `domain/model/ChatMessage.kt` | Single message (role, content, tokenCount, reasoning, imageUris) |
| `Conversation` | `domain/model/Conversation.kt` | Conversation metadata (title, providerType, endpointId, modelId) |
| `Endpoint` | `domain/model/Endpoint.kt` | Remote endpoint config (name, url, apiType, modelId, isActive) |
| `LocalModel` | `domain/model/LocalModel.kt` | Downloaded model (name, filePath, sizeBytes, quantization, format, capabilities) |
| `ModelInfo` | `domain/model/ModelInfo.kt` | Lightweight model descriptor from remote APIs |
| `ChatRequest` | `domain/model/ChatRequest.kt` | Messages + GenerationParameters + images |
| `StreamToken` | `domain/model/StreamToken.kt` | Sealed interface: Delta(content), Done(stats, reasoning), Error(message) |
| `GenerationParameters` | `domain/model/GenerationParameters.kt` | temperature, topP, topK, repeatPenalty, maxTokens, contextSize, seed, threads |
| `Preset` | `domain/model/Preset.kt` | Named parameter presets with `toGenerationParameters()` |
| `ProviderType` | `domain/model/ProviderType.kt` | Enum: OPENAI, ANTHROPIC, OLLAMA, LM_STUDIO, CUSTOM, LOCAL, LITE_RT_LM |
| `Role` | `domain/model/Role.kt` | Enum: SYSTEM, USER, ASSISTANT |
| `ConnectionStatus` | `domain/model/ConnectionStatus.kt` | Enum: Unknown, Connected, Connecting, Disconnected |
| `ActiveModelSelection` | `domain/model/ActiveModelSelection.kt` | `@Singleton` — persists selected model to Keystore, exposes `StateFlow<ActiveModel?>` |
| `ParameterStore` | `domain/model/ParameterStore.kt` | `@Singleton` — holds `StateFlow<GenerationParameters>` for the current session |

**Repository interfaces (6 files):**
| Interface | File | Purpose |
|-----------|------|---------|
| `ChatRepository` | `domain/repository/ChatRepository.kt` | Conversations + messages CRUD, observe all conversations |
| `EndpointRepository` | `domain/repository/EndpointRepository.kt` | Save/delete/activate remote endpoints |
| `LocalModelRepository` | `domain/repository/LocalModelRepository.kt` | Save/delete/observe downloaded models |
| `ModelRepository` | `domain/repository/ModelRepository.kt` | Observe/refresh remote model lists (on-demand, not persisted) |
| `HuggingFaceRepository` | `domain/repository/HuggingFaceRepository.kt` | Search HF models, get model details |
| `PresetRepository` | `domain/repository/PresetRepository.kt` | CRUD for parameter presets |

**Core Abstraction (`LlmProvider`):**
```kotlin
// domain/provider/LlmProvider.kt
interface LlmProvider {
    val type: ProviderType
    fun chat(request: ChatRequest): Flow<StreamToken>
    suspend fun listModels(): Result<List<ModelInfo>>
    suspend fun testConnection(): Result<ConnectionStatus>
}
```
Every local engine and remote provider implements this interface. The UI layer never calls engines/APIs directly — it goes through `ProviderRouter.resolve(endpoint, modelId)` → `LlmProvider`.

### Data Layer (`app/src/main/java/com/warped/data/`)

**Repository implementations (6 files in `data/repository/`):**
| Implementation | Interfaces | Data Sources |
|----------------|-----------|-------------|
| `ChatRepositoryImpl` | `ChatRepository` | Room (`ConversationDao`, `MessageDao`) |
| `EndpointRepositoryImpl` | `EndpointRepository` | Room (`RemoteEndpointDao`), `ApiKeyStore` |
| `LocalModelRepositoryImpl` | `LocalModelRepository` | Room (`LocalModelDao`) |
| `ModelRepositoryImpl` | `ModelRepository` | In-memory `MutableStateFlow` (on-demand fetching) |
| `HuggingFaceRepositoryImpl` | `HuggingFaceRepository` | Retrofit (`HuggingFaceApi`) |
| `PresetRepositoryImpl` | `PresetRepository` | Room (`PresetDao`) |

**Data sources:**
- **Room DB** (`data/local/db/`): `AppDatabase` v9, 6 entities, 6 DAOs, 5 migrations, `Converters` for instants
- **File system**: Models stored in `context.filesDir/models/`, LiteRT-LM cached to `context.cacheDir/litertlm_cache/`
- **DataStore**: Not used — `ActiveModelSelection` uses Keystore, `ParameterStore` is in-memory
- **WorkManager**: `ModelDownloadWorker` (`@HiltWorker`) for background download with foreground notification, checkpoint persistence
- **llama.cpp JNI**: `LlamaEngine` → native `jni_bridge.cpp` (stub — TODO: integrate real llama.cpp)
- **LiteRT-LM**: `LiteRTLmEngine` wraps Google AI Edge `Engine` API, `BackendDetector` probes GPU/CPU
- **Retrofit APIs**: `OpenAiApi`, `AnthropicApi`, `OllamaApi`, `LmStudioApi`, `CustomApi`, `HuggingFaceApi`
- **Security**: `ApiKeyStore` wraps `EncryptedSharedPreferences`, `KeystoreManager` wraps Android Keystore

## Dependency Injection

**Hilt modules (7 files in `di/`):**

| Module | File | Provides |
|--------|------|----------|
| `DatabaseModule` | `di/DatabaseModule.kt` | `AppDatabase`, all 6 DAOs, `WorkManager` |
| `NetworkModule` | `di/NetworkModule.kt` | `OkHttpClient` (with `AuthInterceptor` + logging), `Json` instance |
| `SecurityModule` | `di/SecurityModule.kt` | `KeystoreManager`, `ApiKeyStore` |
| `InferenceModule` | `di/InferenceModule.kt` | `LlamaEngine`, `LiteRTLmEngine`, `EngineManager`, `BackendDetector`, `MemoryChecker`, `LocalLlmProvider`, `LiteRTLmProvider`, `InputSanitizer` + binds `LocalModelRepository` |
| `RepositoryModule` | `di/RepositoryModule.kt` | Binds `ChatRepository`, `EndpointRepository`, `ModelRepository`, `PresetRepository` |
| `HuggingFaceModule` | `di/HuggingFaceModule.kt` | Binds `HuggingFaceRepository` |
| `ProviderModule` | `di/ProviderModule.kt` | Empty module — `ProviderRouter` uses `@Inject` constructor; remote providers created per-endpoint by `ProviderRouter.resolve()` |

**Scoping:**
- All modules install in `SingletonComponent`
- `@Singleton` DAOs, engines, repositories, `ProviderRouter`, `EngineManager`
- Remote providers (`OpenAIProvider`, `AnthropicProvider`, etc.) are **not** singleton — created fresh per-endpoint with runtime parameters via `ProviderRouter.resolve()`
- Local providers (`LocalLlmProvider`, `LiteRTLmProvider`) are `@Singleton` with `dagger.Lazy<>` injection in `ProviderRouter`

**Entry point:** `ChatRepoEntryPoint` — a Compose-side `@EntryPoint` in `NavGraph.kt` to lazily inject `ChatRepository` for the drawer conversation list.

## Data Flow

### Primary Chat Message Flow

```
User types message → ChatInputBar
  → ChatViewModel.sendMessage(text, images)
    1. Validate model selected (ChatError.NoModelSelected if not)
    2. Create user ChatMessage, append to _uiState.messages
    3. ensureConversation() → ChatRepository.createConversation() → Room insert
    4. ChatRepository.saveMessage() → Room insert (user message)
    5. Resolve provider:
       - Local: ProviderRouter.resolveLocal() → LocalLlmProvider.configure(modelId)
       - LiteRT: ProviderRouter.resolveLocal() → LiteRTLmProvider
       - Remote: find active Endpoint → ProviderRouter.resolve(endpoint, modelId)
         → OpenAIProvider / AnthropicProvider / OllamaProvider / LMStudioProvider / CustomProvider
    6. Build ChatRequest(messages, generationParameters, images)
    7. provider.chat(request).collect { token ->
         StreamToken.Delta → accumulate in tokenBuffer, update UI every 50ms
         StreamToken.Done → create assistant ChatMessage, save to Room, clear streaming
         StreamToken.Error → update _uiState.error
       }
    8. _uiState collected by ChatScreen via collectAsStateWithLifecycle()
       → ChatScreen renders messages via LazyColumn
       → Streaming content renders via MarkdownText with typing animation
```

### Token Streaming Flow

```
Remote (OpenAI/Ollama/Custom):
  Retrofit @Streaming ResponseBody → SseExtensions.asSseFlow() / asOllamaFlow()
    → SseParser (buffer-based SSE protocol parser)
    → Parse JSON chunks (OpenAiStreamChunk / OllamaStreamChunk)
    → emit StreamToken.Delta(token) per chunk
    → emit StreamToken.Done() on [DONE] or stream end

Remote (Anthropic):
  Retrofit ResponseBody → AnthropicProvider.parseAnthropicSse()
    → Custom SSE-in-JSON protocol (event: content_block_delta, message_stop, error)
    → emit StreamToken.Delta() or StreamToken.Done()

Remote (LM Studio):
  Retrofit ResponseBody → LMStudioProvider (manual parsing)
    → SSE line-by-line or full JSON fallback
    → LmStudioSseEvent parsing (message.delta, reasoning.delta, chat.end)
    → emit StreamToken.Delta() / StreamToken.Done(stats, reasoning)

Local (llama.cpp):
  LocalLlmProvider → llamaEngine.generate(prompt) via callbackFlow {}
    → JNI callback: TokenCallback.onToken(token, done)
    → Channel → Flow<String>
    → emit StreamToken.Delta(token)

Local (LiteRT-LM):
  LiteRTLmProvider → sendContentsWithRetry()
    → engineManager.createLiteRTConversation(config)
    → conversation.sendMessageAsync(contents).collect { responseMsg -> ... }
    → extract Text content from Message
    → emit StreamToken.Delta()
    → On error: recoverEngine() + retry (max 2 retries)
    → stream via Flow with ConversationFactory pattern
```

### ChatViewModel → UI streaming mechanism

```
Token flow: provider.chat(request).collect { token ->
  StreamToken.Delta → tokenBuffer.add(token.content)
      50ms debounce → join buffer → parseThinkBlocks() (extract <think> tags)
      → update _uiState.streamingContent / streamingReasoning
  StreamToken.Done → flush buffer → parseThinkBlocks()
      → create ChatMessage(role=ASSISTANT, content, reasoning, stats)
      → append to _uiState.messages
      → saveMessage() to Room
      → clear streamingContent, isStreaming=false
  StreamToken.Error → _uiState.error = ChatError.Network(message)
}
```

### Model Download Flow

```
User searches HF → HuggingFaceScreen → HuggingFaceViewModel
  → HuggingFaceRepository.searchModels()
    → HuggingFaceApi (Retrofit GET /api/models)
  → HuggingFaceRepository.getModelDetail(id)
    → HuggingFaceApi (GET /api/models/{id})

User taps download → ModelsViewModel
  → ModelDownloadManager.startDownload(modelId, fileName, fileUrl, fileSizeBytes)
    1. Storage check (StatFs)
    2. Clear stale checkpoint
    3. Enqueue ModelDownloadWorker via WorkManager
    4. observeWorkProgress() → observeForever on WorkInfo LiveData
       → Update _downloadStates (MutableStateFlow<Map<String,DownloadState>>)

ModelDownloadWorker.doWork():
  1. Restore checkpoint from Room (DownloadCheckpointDao)
  2. OkHttp GET with Range: bytes={resumeOffset}-
  3. Write to context.filesDir/models/{filename} via RandomAccessFile
  4. Update WorkManager progress (PROGRESS, DOWNLOADED_BYTES, TOTAL_BYTES, SPEED)
  5. Foreground notification with cancel action
  6. Persist checkpoint every ~1MB
  7. On completion: parse GGUF metadata → save LocalModel to Room
  8. On isStopped: persist checkpoint → Result.success() (don't retry)
  9. On error: persist checkpoint → Result.retry()
```

### Model Loading Flow (Local)

```
User selects local model → ChatViewModel.launchModelSelection() / setSelectedModel()
  1. Memory check: memoryChecker.shouldWarn(sizeBytes) → show warning dialog
  2. User confirms → preloadLocalModel(filePath):
     - .litertlm: engineManager.switchToLiteRT(filePath)
         → LiteRTLmEngine.init() on Dispatchers.Default (blocking)
         → BackendDetector.probeBackend() (EGL + OpenCL probe, cached)
     - .gguf: llamaEngine.loadModel(filePath, threads=4, contextSize=4096)
         → JNI → nativeLoadModel (stub — TODO: real llama.cpp)
  3. refreshActiveBackend() → update _uiState.activeBackend, isLocalModelLoaded
  4. Persist: activeModelSelection.select(modelId, providerType)
```

### Model Load/Unload Flow (LM Studio — remote)

```
User selects LM Studio endpoint → ChatViewModel.setSelectedModel()
  1. Unload previous LM Studio model (if any): LMStudioProvider.unloadModel(instanceId)
  2. Load new model: LMStudioProvider.loadModel(modelId)
     → POST /api/v1/models/load { model: modelId }
     → Response: { instanceId: "..." }
  3. Store instanceId via activeModelSelection.select(modelId, LM_STUDIO, instanceId)
```

## Navigation Graph

**File:** `app/src/main/java/com/warped/ui/navigation/NavGraph.kt`

**Screen definitions** (`Screen.kt` — sealed class):
| Screen | Route | Label | Icon |
|--------|-------|-------|------|
| `Chat` | `"chat"` | Chat | `Icons.AutoMirrored.Filled.Chat` |
| `Chat` (detail) | `"chat/{conversationId}"` | — | — (Long argument) |
| `Endpoints` | `"endpoints"` | Endpoints | `Icons.Filled.Dns` |
| `Models` | `"models"` | Models | `Icons.Filled.Memory` |
| `HuggingFace` | `"huggingface"` | HF | `Icons.Filled.Search` |
| `Presets` | `"presets"` | Presets | `Icons.Filled.Settings` |
| `Settings` | `"settings"` | Settings | `Icons.Filled.Settings` |

**Navigation structure:**
```
WarpedNavGraph()
├── ModalNavigationDrawer
│   ├── Drawer: Logo, "New Chat" button, conversation list (from ChatRepository), Models/Settings bottom tabs
│   └── NavHost(startDestination = "chat")
│       ├── composable("chat") → ChatScreen()
│       ├── composable("chat/{conversationId}") → ChatScreen(conversationId)
│       ├── composable("models") → ModelsScreen(onUseInChat, onOpenHuggingFace)
│       ├── composable("huggingface") → HuggingFaceScreen(onNavigateToModels)
│       ├── composable("presets") → PresetsScreen()
│       └── composable("settings") → SettingsScreen()
```

**Navigation behavior:**
- "New Chat" clears conversation and navigates to `chat` with `popUpTo(chat, inclusive=true)`
- Model selection navigates to `chat` with `popUpTo(startDestination, saveState=true)`
- HuggingFace navigates back via `popBackStack()`
- Conversation click navigates to `chat/{id}` with `launchSingleTop=true`
- Process death recovery: `rememberSaveable` persists route + conversation ID; `LaunchedEffect` restores on recreation
- Endpoints screen not yet wired into NavHost but defined in `Screen.kt`

## Key Architectural Decisions

### 1. Per-endpoint Provider Instantiation
Remote providers (`OpenAIProvider`, `AnthropicProvider`, etc.) are **not** injected via Hilt. They're created per-endpoint by `ProviderRouter.resolve()` with runtime parameters (`baseUrl`, `modelId`, optional `apiKey`). Each provider creates its own `Retrofit` instance. This isolates per-provider configuration (timeout, auth headers, base URL).

**Trade-off:** Duplicate Retrofit instances, but clean separation of concerns. The global `OkHttpClient` from `NetworkModule` is not shared with most remote providers (exception: `ModelDownloadWorker` uses it for downloads).

### 2. Per-call Conversation Factory Pattern (LiteRT-LM)
`LiteRTLmProvider` creates a **new** `Conversation` per chat request (via `EngineManager.createLiteRTConversation(config)`). This ensures clean state isolation between requests and allows recovery from engine errors by reinitializing the engine and creating a fresh conversation. Previous conversation is closed before creating a new one.

### 3. EngineManager Mutual Exclusion
Only one inference engine can be active at a time (llama.cpp **or** LiteRT-LM). All `EngineManager` methods are `@Synchronized`. Switching engines unloads the current one first. This prevents memory exhaustion (models are multi-GB). The `ActiveEngine` data class tracks which engine is loaded.

### 4. liteRTLm Model Caching
LiteRT-LM requires models in `cacheDir` (not `filesDir/models/`). On `switchToLiteRT()`, the model is automatically copied from source to `context.cacheDir/litertlm_cache/`. Cache is invalidated when source file size differs. On critical memory pressure (`onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL)`), the entire cache is cleared.

### 5. Download Pause/Resume via WorkManager + Room Checkpoints
Downloads use a dual-tracking system: `WorkManager` tracks progress for UI observation, `DownloadCheckpointDao` (Room) persists byte-level progress. On pause, checkpoint is written to Room. On resume, the Worker reads the checkpoint and sets `Range: bytes={offset}-` header. On cancellation/stoppage during download, the Worker persists the checkpoint and returns `Result.success()` (don't retry — pause handled by Manager).

### 6. Active Model Persistence via Android Keystore
`ActiveModelSelection` stores the selected model ID, provider type, and instance ID as a serialized JSON in Android Keystore (via `KeystoreManager`). This survives app restart and process death. Also persists last conversation ID.

### 7. Reasoning/Think Block Extraction
`ChatViewModel.parseThinkBlocks()` extracts `<think>...</think>` tags from streaming content using regex. Operates case-insensitively (handles DeepSeek variants). Separates reasoning text from display content in `ChatUiState.streamingReasoning` vs `streamingContent`. Works during streaming (handles incomplete `<think>` tags).

### 8. Image Handling
Images are read from `content://` URIs via `ContentResolver`, converted to base64 data URLs (`data:image/png;base64,...`). Stored in `ChatMessage.imageUris`. Passed through `ChatRequest.images`. Supported by `LiteRTLmProvider` (via `Content.ImageBytes`), `LMStudioProvider` (via `LmStudioInputItem(type="image")`), and remote OpenAI-compatible providers. Local llama.cpp provider does **not** support images. Model capability detection: `LocalModel.capabilities.vision` lazily computed from model name heuristics.

## Component Diagram (Relationships)

```text
┌───────────────────────────────────────────────────────────────────┐
│ WarpedApplication (@HiltAndroidApp)                               │
│  - Provides WorkerFactory (HiltWorkerFactory)                     │
│  - Creates notification channels                                  │
│  - StrictMode in debug                                            │
│  - Memory trimming → EngineManager.handleTrimMemory()             │
└──────────────┬────────────────────────────────────────────────────┘
               │
┌──────────────▼────────────────────────────────────────────────────┐
│ MainActivity (@AndroidEntryPoint)                                  │
│  - Edge-to-edge, notification permission                          │
│  - setContent { WarpedTheme { WarpedNavGraph() } }                │
└──────────────┬────────────────────────────────────────────────────┘
               │
┌──────────────▼────────────────────────────────────────────────────┐
│ WarpedNavGraph (Composable)                                        │
│  - ModalNavigationDrawer + NavHost                                │
│  - Screen routing: Chat, Models, HuggingFace, Presets, Settings   │
│  - ChatRepoEntryPoint (Hilt EntryPoint for conversation list)     │
└──────────┬────────────────────────────────────────────────────────┘
           │
┌──────────▼────────────────────────────────────────────────────────┐
│ ChatViewModel (@HiltViewModel)            (per ChatScreen)         │
│  Orchestrator — depends on:                                       │
│  ├─ ChatRepository (Room conversations/messages)                  │
│  ├─ EndpointRepository (remote endpoints)                         │
│  ├─ LocalModelRepository (downloaded models)                      │
│  ├─ ActiveModelSelection (persisted model selection)              │
│  ├─ ParameterStore (generation parameters)                        │
│  ├─ ProviderRouter (resolve provider from endpoint)               │
│  ├─ LlamaEngine (check loaded state, preload)                     │
│  ├─ EngineManager (switch engines, memory pressure)               │
│  └─ MemoryChecker (warn on large model load)                      │
└──────────┬────────────────────────────────────────────────────────┘
           │
     ┌─────▼─────┐
     │ProviderRouter (@Singleton)                                   │
     │ resolve(endpoint, modelId) → LlmProvider                     │
     │ resolveLocal(providerType, modelId) → LlmProvider            │
     │                                                                 │
     │ Remote providers (created per call):                          │
     │   OpenAIProvider(baseUrl, modelId, endpointId)                │
     │   AnthropicProvider(baseUrl, modelId, apiKey)                 │
     │   OllamaProvider(baseUrl, modelId)                            │
     │   LMStudioProvider(baseUrl, modelId)                          │
     │   CustomProvider(baseUrl, modelId)                            │
     │                                                                │
     │ Local providers (singleton, lazy):                             │
     │   LocalLlmProvider(llamaEngine) → LlmProvider                │
     │   LiteRTLmProvider(engineManager, inputSanitizer) → LlmProvider │
     └─────┬─────┘
           │
     ┌─────▼──────────────┐     ┌──────────────────────────────┐
     │ LlamaEngine        │     │ EngineManager (@Singleton)   │
     │ (JNI → jni_bridge) │     │ ├─ LlamaEngine (wrapper)     │
     │ nativeLoadModel()  │◄────┤ ├─ LiteRTLmEngine (wrapper)  │
     │ nativeGenerate()   │     │ ├─ BackendDetector           │
     │ nativeStop()       │     │ ├─ switchToLlama(path)       │
     │ nativeUnload()     │     │ ├─ switchToLiteRT(path)      │
     └────────────────────┘     │ ├─ unloadCurrent()           │
                                │ └─ createLiteRTConversation()│
     ┌────────────────────┐     └──────┬───────────────────────┘
     │ LiteRTLmEngine     │            │
     │ (Google AI Edge)   │◄───────────┘
     │ init(path,backend) │
     │ createConversation │
     │ close()            │
     └────────────────────┘
```

## Error Handling

**Strategy:** Fail gracefully. Every external call is wrapped in try/catch. Errors are surfaced as typed error states in UiState, not thrown exceptions.

**Patterns:**
- `StreamToken.Error(message)` — surfaced during token streaming in `ChatViewModel.sendMessage()`
- `ChatError` sealed class — `Network`, `Server`, `Auth`, `NoModelSelected`, `DownloadModelFirst`, `ConnectionLost`, `Unknown`
- `testConnection()` returns `Result.success(ConnectionStatus.Disconnected)` on any exception (never throws)
- `listModels()` returns `Result.failure(Exception)` on error — caller decides how to handle
- `ModelDownloadWorker` returns `Result.failure(workDataOf("error" to msg))` with user-friendly messages
- Network logging in debug via `HttpLoggingInterceptor` at `HEADERS` level
- API key redaction in Timber via `RedactingTree` (regex-based redaction of `api_key=`, `Bearer`, `Authorization` patterns)

## Cross-Cutting Concerns

**Logging:** Timber library. `RedactingTree` wraps `DebugTree` in debug builds to redact secrets. Production builds have no Timber tree planted (only via `BuildConfig.DEBUG` check).

**Security (api keys):**
- `ApiKeyStore` → `EncryptedSharedPreferences` ("warped_keystore") for API keys
- `KeystoreManager` → Android Keystore (`KeyGenParameterSpec` with `PURPOSE_ENCRYPT | PURPOSE_DECRYPT`) for model selection
- `AuthInterceptor` — OkHttp interceptor that reads keys from `ApiKeyStore` and injects `Authorization: Bearer` header
- `network_security_config.xml` — blocks cleartext in production, allows for local network (Ollama/LM Studio)
- `CertificatePinner` in OkHttp — applied via `HttpClientFactory` (not in global `OkHttpClient`)

**Threading:**
- `Dispatchers.Main` — Compose UI updates (implicit via ViewModel + StateFlow)
- `Dispatchers.IO` — Network calls, file I/O, Room queries, GGUF downloads, Retrofit streaming
- `Dispatchers.Default` — Local inference (llama.cpp generate via `flowOn(Dispatchers.Default)`), LiteRT-LM engine init (blocking call in `withContext(Dispatchers.Default)`), JSON parsing

**Global state (singletons):**
- `ProviderRouter` (`@Singleton`) — maps endpoint + modelId to provider instances
- `EngineManager` (`@Singleton`) — tracks which engine is loaded, mutual exclusion
- `LlamaEngine` (`@Singleton`) — JNI native library wrapper (singleton in native code too)
- `LiteRTLmEngine` (`@Singleton`) — wraps Google AI Edge Engine
- `ActiveModelSelection` (`@Singleton`) — persists model selection via Keystore
- `ParameterStore` (`@Singleton`) — in-memory generation parameters
- `ModelDownloadManager` (`@Singleton`) — download state + WorkManager orchestration

## Architectural Constraints

- **Threading:** All local inference runs on `Dispatchers.Default`. Never block UI thread during model loading or inference.
- **Global state:** `EngineManager` enforces mutual exclusion — only one engine loaded at a time (memory constraint).
- **Native loading:** `LlamaEngine` loads `libwarped_llama.so` in companion object init (JNI). Must not be called before native lib is ready.
- **Memory:** Local models are multi-GB. `MemoryChecker.shouldWarn()` triggers before loading. `EngineManager.handleTrimMemory()` unloads on critical pressure. `ActivityManager.MemoryInfo` check not yet integrated.
- **Circular imports:** Not detected at this stage — single-module project, no circular dependencies between packages.
- **Process death:** `NavGraph` uses `rememberSaveable` for route + conversation ID. `ActiveModelSelection` persists to Keystore. WorkManager restarts downloads after crash.

## Anti-Patterns

### Provider self-managed OkHttpClient

**What happens:** Remote providers (`OpenAIProvider`, `OllamaProvider`, etc.) create their own Retrofit/OkHttpClient instances instead of using the global `OkHttpClient` from `NetworkModule`.

**Why it's wrong:** Auth headers are managed inconsistently. `OpenAIProvider` doesn't use `AuthInterceptor` at all (no `apiKey` is passed — see NOTE in `ProviderRouter.kt`). `AnthropicProvider` injects `x-api-key` header manually. This leads to duplicated configuration and potential auth gaps.

**Do this instead:** Pass the global `OkHttpClient` (with `AuthInterceptor`) to remote providers, or use a factory pattern in DI to create per-endpoint clients with centralized auth configuration.

### Domain model depends on data layer (ActiveModelSelection)

**What happens:** `ActiveModelSelection` (in `domain/model/`) depends on `KeystoreManager` (in `data/local/security/`).

**Why it's wrong:** Domain layer should not depend on Data layer. This creates a dependency cycle (domain → data, data → domain).

**Do this instead:** Move `ActiveModelSelection` to `data/repository/` or extract a domain-level interface for persistence that `KeystoreManager` implements.

---

*Architecture analysis: 2026-05-02*
