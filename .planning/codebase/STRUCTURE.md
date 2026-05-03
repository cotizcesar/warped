# Codebase Structure

**Analysis Date:** 2026-05-02

## Directory Tree

```
warped/
├── .planning/                                # GSD planning artifacts
│   ├── codebase/                             # Codebase intelligence docs (this file)
│   ├── milestones/                           # Milestone phase definitions
│   ├── phases/                               # Phase execution artifacts
│   ├── quick/                                # Quick-fix task artifacts
│   └── research/                             # Research documents
├── app/
│   ├── build.gradle.kts                      # App-level Gradle build script
│   ├── .cxx/                                 # CMake build intermediates (generated, not committed)
│   └── src/main/
│       ├── cpp/                              # Native C++ JNI bridge (3 files)
│       │   ├── CMakeLists.txt                # CMake build config for libwarped_llama.so
│       │   ├── jni_bridge.h                  # LlamaEngine C++ class + TokenCallback typedef
│       │   └── jni_bridge.cpp                # JNI functions + llama.cpp stubs (143 lines)
│       ├── java/com/warped/                  # Root Kotlin package (123 files)
│       │   ├── WarpedApplication.kt          # @HiltAndroidApp, WorkManager config, notification channels, StrictMode
│       │   ├── MainActivity.kt               # @AndroidEntryPoint, edge-to-edge, NavGraph host
│       │   ├── di/                           # Hilt DI modules (7 files)
│       │   │   ├── DatabaseModule.kt         # Room DB + DAOs + WorkManager
│       │   │   ├── NetworkModule.kt          # OkHttpClient + Json + LoggingInterceptor
│       │   │   ├── SecurityModule.kt         # KeystoreManager + ApiKeyStore
│       │   │   ├── InferenceModule.kt        # LlamaEngine, LiteRTLmEngine, EngineManager, LocalLlmProvider, LiteRTLmProvider + LocalModelRepository bind
│       │   │   ├── RepositoryModule.kt       # Binds ChatRepository, EndpointRepository, ModelRepository, PresetRepository
│       │   │   ├── HuggingFaceModule.kt      # Binds HuggingFaceRepository
│       │   │   └── ProviderModule.kt         # Empty (ProviderRouter self-injects)
│       │   ├── domain/                       # Domain layer (21 files)
│       │   │   ├── model/                    # Domain models (14 files)
│       │   │   │   ├── ActiveModelSelection.kt  # @Singleton - persisted model selection via Keystore
│       │   │   │   ├── ChatMessage.kt            # Message with role, content, reasoning, imageUris
│       │   │   │   ├── ChatRequest.kt            # Input to LlmProvider.chat()
│       │   │   │   ├── ConnectionStatus.kt       # Enum: Unknown/Connected/Connecting/Disconnected
│       │   │   │   ├── Conversation.kt           # Conversation metadata
│       │   │   │   ├── Endpoint.kt               # Remote endpoint config (name, url, apiType)
│       │   │   │   ├── GenerationParameters.kt   # temperature, topP, topK, maxTokens, etc.
│       │   │   │   ├── LocalModel.kt             # Downloaded model + capability detection
│       │   │   │   ├── ModelInfo.kt              # Lightweight model ID/name/type triplet
│       │   │   │   ├── ParameterStore.kt         # @Singleton - in-memory StateFlow<GenerationParameters>
│       │   │   │   ├── Preset.kt                 # Named parameter preset → toGenerationParameters()
│       │   │   │   ├── ProviderType.kt           # Enum: OPENAI/ANTHROPIC/OLLAMA/LM_STUDIO/CUSTOM/LOCAL/LITE_RT_LM
│       │   │   │   ├── Role.kt                   # Enum: SYSTEM/USER/ASSISTANT
│       │   │   │   └── StreamToken.kt            # Sealed interface: Delta/Done/Error
│       │   │   ├── provider/                 # Core abstraction (1 file)
│       │   │   │   └── LlmProvider.kt            # Interface: chat(), listModels(), testConnection()
│       │   │   └── repository/               # Repository interfaces (6 files)
│       │   │       ├── ChatRepository.kt         # Conversations + messages CRUD
│       │   │       ├── EndpointRepository.kt     # Remote endpoint management
│       │   │       ├── HuggingFaceRepository.kt  # HF model search + details
│       │   │       ├── LocalModelRepository.kt   # Downloaded model CRUD
│       │   │       ├── ModelRepository.kt        # Remote model list observation
│       │   │       └── PresetRepository.kt       # Parameter preset CRUD
│       │   ├── data/                         # Data layer (61 files)
│       │   │   ├── local/                    # Local data sources (32 files)
│       │   │   │   ├── db/                   # Room database (18 files)
│       │   │   │   │   ├── AppDatabase.kt        # @Database v9, 6 entities, 6 DAOs
│       │   │   │   │   ├── Migrations.kt         # MIGRATION_4_5 through MIGRATION_8_9
│       │   │   │   │   ├── dao/                  # Data Access Objects (6 files)
│       │   │   │   │   │   ├── ConversationDao.kt    # observeAll(), getById(), upsert(), deleteById(), updateTimestamp()
│       │   │   │   │   │   ├── MessageDao.kt         # getByConversation(), insert(), deleteAll()
│       │   │   │   │   │   ├── RemoteEndpointDao.kt  # observeAll(), getActive(), upsert(), activate(), deactivateAll()
│       │   │   │   │   │   ├── LocalModelDao.kt      # observeAll(), getById(), upsert(), deleteById()
│       │   │   │   │   │   ├── PresetDao.kt          # observeAll(), getById(), upsert(), delete()
│       │   │   │   │   │   └── DownloadCheckpointDao.kt # getCheckpoint(), upsertCheckpoint(), deleteCheckpoint()
│       │   │   │   │   ├── entity/              # Room entities + mappers (8 files)
│       │   │   │   │   │   ├── ConversationEntity.kt
│       │   │   │   │   │   ├── MessageEntity.kt
│       │   │   │   │   │   ├── RemoteEndpointEntity.kt
│       │   │   │   │   │   ├── LocalModelEntity.kt
│       │   │   │   │   │   ├── PresetEntity.kt
│       │   │   │   │   │   ├── DownloadCheckpointEntity.kt
│       │   │   │   │   │   ├── EntityMappers.kt           # Message, Conversation entity↔domain
│       │   │   │   │   │   ├── LocalModelMappers.kt       # LocalModel entity↔domain
│       │   │   │   │   │   └── PresetMappers.kt           # Preset entity↔domain
│       │   │   │   │   └── converter/            # Type converters (1 file)
│       │   │   │   │       └── Converters.kt            # Instant ↔ Long converters for Room
│       │   │   │   ├── download/              # Model download (2 files)
│       │   │   │   │   ├── ModelDownloadManager.kt   # @Singleton - orchestrates WorkManager downloads, pause/resume/cancel
│       │   │   │   │   └── ModelDownloadWorker.kt    # @HiltWorker - OkHttp download with Range support, checkpoints, foreground notification
│       │   │   │   ├── inference/             # Local inference engines (10 files)
│       │   │   │   │   ├── BackendDetector.kt      # @Singleton - probes GPU (EGL + OpenCL), caches result
│       │   │   │   │   ├── EngineManager.kt         # @Singleton - mutual exclusion, switchToLlama/switchToLiteRT, trim memory
│       │   │   │   │   ├── GgufMetadataParser.kt   # Parses GGUF header for quant, params, arch
│       │   │   │   │   ├── InputSanitizer.kt       # @Singleton - text normalization for LiteRT-LM
│       │   │   │   │   ├── LiteRTLmEngine.kt       # @Singleton - wraps Google AI Edge Engine (init, createConversation, close)
│       │   │   │   │   ├── LiteRTLmProvider.kt     # @Singleton - LlmProvider for LiteRT-LM, per-call ConversationFactory, retry logic
│       │   │   │   │   ├── LlamaEngine.kt          # @Singleton - JNI wrapper (loadModel, generate via callbackFlow, unload)
│       │   │   │   │   ├── LocalLlmProvider.kt     # @Singleton - LlmProvider for llama.cpp, prompt formatting
│       │   │   │   │   ├── MemoryChecker.kt        # @Singleton - checks free RAM vs model size
│       │   │   │   │   └── ModelImportManager.kt   # @Singleton - imports external model files
│       │   │   │   └── security/              # Encrypted storage (2 files)
│       │   │   │       ├── ApiKeyStore.kt          # EncryptedSharedPreferences wrapper for API keys
│       │   │   │       └── KeystoreManager.kt      # Android Keystore wrapper (encrypt/decrypt/persist)
│       │   │   ├── remote/                   # Remote data sources (23 files)
│       │   │   │   ├── api/                  # Retrofit API interfaces (6 files)
│       │   │   │   │   ├── AnthropicApi.kt        # POST v1/messages (chatCompletions)
│       │   │   │   │   ├── CustomApi.kt           # POST {chatPath}, GET {modelsPath} (path-injected)
│       │   │   │   │   ├── HuggingFaceApi.kt      # GET /api/models, GET /api/models/{id}, GET /api/models/{id}?config=true
│       │   │   │   │   ├── LmStudioApi.kt         # POST api/v1/chat, GET api/v1/models, POST api/v1/models/load, POST api/v1/models/unload
│       │   │   │   │   ├── OllamaApi.kt           # POST api/chat, GET api/tags
│       │   │   │   │   └── OpenAiApi.kt           # POST v1/chat/completions, GET v1/models
│       │   │   │   ├── dto/                  # Data Transfer Objects (6 files)
│       │   │   │   │   ├── AnthropicDtos.kt       # AnthropicChatRequest, AnthropicMessage, AnthropicSseEvent, etc.
│       │   │   │   │   ├── HuggingFaceDtos.kt     # HuggingFaceModel, HuggingFaceModelDetail, HuggingFaceSibling
│       │   │   │   │   ├── LmStudioDtos.kt        # LmStudioChatRequest, LmStudioInputItem, LmStudioSseEvent, LmStudioLoad/Unload, LmStudioModelList
│       │   │   │   │   ├── OllamaChatRequest.kt   # OllamaChatRequest, OllamaMessage, OllamaStreamChunk, OllamaModelList
│       │   │   │   │   ├── OpenAiChatRequest.kt   # OpenAiChatRequest, OpenAiMessage, OpenAiStreamChunk, OpenAiModelListResponse
│       │   │   │   │   └── StreamChunks.kt        # OpenAiModel (id field for list response)
│       │   │   │   ├── network/              # HTTP/SSE infrastructure (5 files)
│       │   │   │   │   ├── AuthInterceptor.kt     # OkHttp interceptor — injects Authorization header from ApiKeyStore
│       │   │   │   │   ├── HttpClientFactory.kt   # Creates OkHttpClient with CertificatePinner for HuggingFace
│       │   │   │   │   ├── SseEvent.kt            # Simple data class for SSE event: data + type
│       │   │   │   │   ├── SseExtensions.kt       # ResponseBody.asSseFlow() (OpenAI format), asOllamaFlow()
│       │   │   │   │   └── SseParser.kt           # Buffer-based SSE protocol parser (data:/event: lines)
│       │   │   │   └── provider/             # Remote provider implementations (6 files)
│       │   │   │       ├── AnthropicProvider.kt   # Anthropic API with x-api-key header, custom SSE parsing
│       │   │   │       ├── CustomProvider.kt      # OpenAI-compatible custom endpoint
│       │   │   │       ├── LMStudioProvider.kt    # LM Studio v1 API (SSE + fallback full JSON, load/unload models)
│       │   │   │       ├── OllamaProvider.kt      # Ollama chat + model listing
│       │   │   │       ├── OpenAIProvider.kt      # OpenAI-compatible chat completions
│       │   │   │       └── ProviderRouter.kt      # @Singleton - resolve(endpoint,modelId) → LlmProvider; resolveLocal(type,modelId) → LlmProvider
│       │   │   └── repository/              # Repository implementations (6 files)
│       │   │       ├── ChatRepositoryImpl.kt      # ConversationDao + MessageDao
│       │   │       ├── EndpointRepositoryImpl.kt  # RemoteEndpointDao + ApiKeyStore
│       │   │       ├── HuggingFaceRepositoryImpl.kt # HuggingFaceApi (search + details)
│       │   │       ├── LocalModelRepositoryImpl.kt # LocalModelDao
│       │   │       ├── ModelRepositoryImpl.kt     # In-memory MutableStateFlow (on-demand, not persisted)
│       │   │       └── PresetRepositoryImpl.kt    # PresetDao
│       │   └── ui/                          # UI layer (32 files)
│       │       ├── chat/                    # Chat screen (8 files)
│       │       │   ├── ChatScreen.kt             # Main chat composable (456 lines) — LazyColumn messages, error handling, loading states
│       │       │   ├── ChatViewModel.kt          # Core orchestrator (550 lines) — sendMessage, streaming, model loading, reasoning parsing
│       │       │   ├── ChatUiState.kt            # ChatUiState data class (36 fields), ChatError sealed class
│       │       │   └── components/               # Reusable chat composables (5 files)
│       │       │       ├── ChatInputBar.kt       # Text input + file/image attachment + send button
│       │       │       ├── ConversationList.kt   # Sidebar conversation list in drawer
│       │       │       ├── MarkdownText.kt       # Markdown rendering for assistant messages
│       │       │       ├── MessageBubble.kt      # Individual message bubble (user/assistant styling)
│       │       │       └── ModelSelector.kt      # ExposedDropdownMenu for model/provider selection
│       │       ├── endpoints/              # Endpoint management (5 files)
│       │       │   ├── EndpointsScreen.kt        # List of configured endpoints
│       │       │   ├── EndpointsViewModel.kt     # CRUD for endpoints
│       │       │   ├── EndpointsUiState.kt       # EndpointsUiState data class
│       │       │   └── components/               # Endpoint composables (2 files)
│       │       │       ├── EndpointCard.kt       # Single endpoint display card
│       │       │       └── EndpointForm.kt       # Add/edit endpoint form
│       │       ├── huggingface/            # HuggingFace model browser (3 files)
│       │       │   ├── HuggingFaceScreen.kt      # Search + model list UI
│       │       │   ├── HuggingFaceViewModel.kt   # Search, model detail fetching
│       │       │   └── HuggingFaceUiState.kt     # HuggingFaceUiState data class
│       │       ├── models/                 # Model management (4 files)
│       │       │   ├── ModelsScreen.kt           # Downloaded models list with download state
│       │       │   ├── ModelsViewModel.kt        # Observe/download/delete models
│       │       │   ├── ModelsUiState.kt          # ModelsUiState data class
│       │       │   └── ModelsPlaceholderScreen.kt # Empty state composable
│       │       ├── navigation/             # Navigation (2 files)
│       │       │   ├── NavGraph.kt               # WarpedNavGraph composable (282 lines) — NavHost + ModalDrawer + ChatRepoEntryPoint
│       │       │   └── Screen.kt                 # Sealed Screen class (6 routes: Chat, Chat/{id}, Endpoints, Models, HuggingFace, Presets, Settings)
│       │       ├── presets/                # Parameter presets (3 files)
│       │       │   ├── PresetsScreen.kt          # Preset list + CRUD UI
│       │       │   ├── PresetsViewModel.kt       # Preset CRUD operations
│       │       │   └── PresetsUiState.kt        # PresetsUiState data class
│       │       ├── settings/               # Settings (3 files)
│       │       │   ├── SettingsScreen.kt         # Settings UI (options toggle)
│       │       │   ├── SettingsViewModel.kt      # Settings logic
│       │       │   └── SettingsUiState.kt        # SettingsUiState data class
│       │       └── theme/                  # Material 3 theme (4 files)
│       │           ├── Color.kt                  # Color palette definitions
│       │           ├── Shape.kt                  # Shape definitions
│       │           ├── Theme.kt                  # WarpedTheme composable (dark/light)
│       │           └── Type.kt                   # Typography definitions
│       └── res/                            # Android resources
│           ├── drawable/                   # Logo + icons
│           ├── mipmap-anydpi-v26/          # Adaptive icon
│           ├── values/                     # Strings, colors, themes
│           ├── values-es/                  # Spanish translations
│           └── xml/                        # network_security_config.xml, backup_rules.xml
```

## Package Summary

| Package | Files | Purpose |
|---------|-------|---------|
| `com.warped` (root) | 2 | `WarpedApplication`, `MainActivity` — app entry points |
| `di/` | 7 | Hilt DI modules — `DatabaseModule`, `NetworkModule`, `SecurityModule`, `InferenceModule`, `RepositoryModule`, `HuggingFaceModule`, `ProviderModule` |
| `domain/model/` | 14 | Domain models — pure Kotlin data classes + enums + `ActiveModelSelection`, `ParameterStore` |
| `domain/provider/` | 1 | `LlmProvider` interface — core abstraction |
| `domain/repository/` | 6 | Repository interfaces — `ChatRepository`, `EndpointRepository`, `ModelRepository`, `LocalModelRepository`, `HuggingFaceRepository`, `PresetRepository` |
| `data/local/db/` | 18 | Room database — `AppDatabase`, 6 DAOs, 8 entities, `Migrations`, `Converters` |
| `data/local/download/` | 2 | Model download — `ModelDownloadManager`, `ModelDownloadWorker` |
| `data/local/inference/` | 10 | Local inference — `LlamaEngine`, `LiteRTLmEngine`, `EngineManager`, `BackendDetector`, `LocalLlmProvider`, `LiteRTLmProvider`, `MemoryChecker`, `ModelImportManager`, `GgufMetadataParser`, `InputSanitizer` |
| `data/local/security/` | 2 | Security — `KeystoreManager`, `ApiKeyStore` |
| `data/remote/api/` | 6 | Retrofit API interfaces — `OpenAiApi`, `AnthropicApi`, `OllamaApi`, `LmStudioApi`, `CustomApi`, `HuggingFaceApi` |
| `data/remote/dto/` | 6 | Data Transfer Objects — request/response models for each API |
| `data/remote/network/` | 5 | Network infrastructure — `AuthInterceptor`, `HttpClientFactory`, `SseParser`, `SseEvent`, `SseExtensions` |
| `data/remote/provider/` | 6 | Provider implementations — `ProviderRouter` + 5 remote providers |
| `data/repository/` | 6 | Repository implementations — 6 impls for the 6 domain interfaces |
| `ui/chat/` | 8 | Chat screen — `ChatScreen`, `ChatViewModel`, `ChatUiState` + 5 components |
| `ui/endpoints/` | 5 | Endpoint management — `EndpointsScreen`, `EndpointsViewModel`, `EndpointsUiState` + 2 components |
| `ui/huggingface/` | 3 | HuggingFace browser — `HuggingFaceScreen`, `HuggingFaceViewModel`, `HuggingFaceUiState` |
| `ui/models/` | 4 | Model management — `ModelsScreen`, `ModelsViewModel`, `ModelsUiState`, `ModelsPlaceholderScreen` |
| `ui/navigation/` | 2 | Navigation — `NavGraph`, `Screen` |
| `ui/presets/` | 3 | Parameter presets — `PresetsScreen`, `PresetsViewModel`, `PresetsUiState` |
| `ui/settings/` | 3 | Settings — `SettingsScreen`, `SettingsViewModel`, `SettingsUiState` |
| `ui/theme/` | 4 | Material 3 theme — `Color`, `Shape`, `Theme`, `Type` |
| `cpp/` | 3 | Native C++ JNI bridge — `CMakeLists.txt`, `jni_bridge.h`, `jni_bridge.cpp` |

**Total:** 123 Kotlin files + 3 C++ files = 126 source files

## File Count by Layer

| Layer | Files | % of total |
|-------|-------|-----------|
| Data | 61 | 49.6% |
| UI | 32 | 26.0% |
| Domain | 21 | 17.1% |
| DI | 7 | 5.7% |
| Root (app entry) | 2 | 1.6% |
| **Total Kotlin** | **123** | **100%** |

## Key File Locations

**Entry Points:**
- `app/src/main/java/com/warped/WarpedApplication.kt` — Application class, Hilt entry point, WorkManager config, notification channels
- `app/src/main/java/com/warped/MainActivity.kt` — Activity, edge-to-edge, NavGraph host
- `app/src/main/java/com/warped/ui/navigation/NavGraph.kt` — Composable navigation graph + ModalNavigationDrawer

**Configuration:**
- `app/build.gradle.kts` — Android Gradle plugin, dependencies, version catalog references
- `app/src/main/cpp/CMakeLists.txt` — Native build config for `warped_llama` library
- `app/src/main/res/xml/network_security_config.xml` — Network security policy
- `gradle/libs.versions.toml` — Version catalog (dependencies)

**Core Logic:**
- `app/src/main/java/com/warped/domain/provider/LlmProvider.kt` — Core abstraction over all inference providers
- `app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt` — Provider factory, maps endpoint + modelId to LlmProvider
- `app/src/main/java/com/warped/data/local/inference/EngineManager.kt` — Mutual exclusion for local inference engines
- `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` — Main orchestrator (550 lines, most complex file)

**Data:**
- `app/src/main/java/com/warped/data/local/db/AppDatabase.kt` — Room database (v9, 6 entities)
- `app/src/main/java/com/warped/data/local/download/ModelDownloadManager.kt` — Download orchestration (316 lines)
- `app/src/main/java/com/warped/data/local/download/ModelDownloadWorker.kt` — Download worker (363 lines)
- `app/src/main/java/com/warped/data/remote/network/SseExtensions.kt` — SSE streaming for OpenAI + Ollama

**Testing:**
- No test files detected in the codebase.

## Naming Conventions

**Files:**
- Kotlin files: `PascalCase.kt` (matching class name for single-class files)
- Composable files: `PascalCase.kt` matching composable function name
- UI files follow pattern: `{Feature}Screen.kt`, `{Feature}ViewModel.kt`, `{Feature}UiState.kt`
- Component files: `PascalCase.kt` (e.g., `MessageBubble.kt`, `ChatInputBar.kt`)

**Directories:**
- Feature-based: `ui/{feature}/` (chat, endpoints, models, huggingface, presets, settings)
- Layer-based: `domain/{subsystem}/` (model, provider, repository)
- Data source-based: `data/{local|remote}/{data_source_type}/`

**Packages:**
- Root: `com.warped`
- Follow directory structure exactly

## Where to Add New Code

**New Feature (e.g., new screen):**
- Screen composable: `ui/{feature}/{Feature}Screen.kt`
- ViewModel: `ui/{feature}/{Feature}ViewModel.kt`
- UiState: `ui/{feature}/{Feature}UiState.kt` (data class)
- Reusable components: `ui/{feature}/components/{ComponentName}.kt`

**New Remote Provider:**
- API interface: `data/remote/api/{ProviderName}Api.kt`
- DTOs: `data/remote/dto/{ProviderName}Dtos.kt`
- Provider implementation: `data/remote/provider/{ProviderName}Provider.kt`
- Register in `ProviderRouter.kt` `resolve()` method
- Add enum value to `ProviderType`

**New Repository:**
- Interface: `domain/repository/{Name}Repository.kt`
- Implementation: `data/repository/{Name}RepositoryImpl.kt`
- DI binding: `di/RepositoryModule.kt` (or new feature module)

**New Entity/Table:**
- Entity: `data/local/db/entity/{Name}Entity.kt`
- DAO: `data/local/db/dao/{Name}Dao.kt`
- Mapper: add to `data/local/db/entity/EntityMappers.kt` or create new
- Add to `AppDatabase.kt` entities list + abstract DAO method
- Add DAO provider to `DatabaseModule.kt`

**Utilities:**
- Shared infrastructure: `data/remote/network/` (for networking) or `data/local/inference/` (for inference)
- Domain helpers: `domain/model/` only if pure Kotlin utility

## Special Directories

**`app/.cxx/`:**
- Purpose: CMake build intermediates for native JNI code
- Generated: Yes (by Android Gradle Plugin)
- Committed: No (in `.gitignore`)

**`app/src/main/cpp/`:**
- Purpose: Native C++ JNI bridge between Kotlin and llama.cpp
- Generated: No (hand-written)
- Committed: Yes
- Contents: `jni_bridge.cpp` (JNI extern "C" functions), `jni_bridge.h` (LlamaEngine singleton class), `CMakeLists.txt`

**`app/src/main/res/values-es/`:**
- Purpose: Spanish (es) localized strings
- Generated: No (hand-written)
- Committed: Yes

---

*Structure analysis: 2026-05-02*
