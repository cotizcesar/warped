# Coding Conventions

**Analysis Date:** 2026-05-02

## Language & Style

### Kotlin
- **Version:** Kotlin 2.1.10 with K2 compiler (default since 2.0.0)
- **JVM Target:** JVM 17
- **Build DSL:** Gradle Kotlin DSL (`build.gradle.kts`)
- **Dependency Management:** Version catalog (`gradle/libs.versions.toml`)
- **Annotation Processing:** KSP (`ksp` plugin), not kapt
- **Java Interop:** Not used — Kotlin-only codebase per project constraint

### Naming Conventions

**Classes/Types:** PascalCase — `ChatViewModel`, `ChatUiState`, `OpenAiApi`, `SseParser`
**Functions:** camelCase — `sendMessage()`, `observeConversations()`, `collectAsStateWithLifecycle()`
**Variables:** camelCase — `viewModel`, `uiState`, `isStreaming`
**Constants:** UPPER_SNAKE_CASE — `MIN_SEARCH_LENGTH`, `CHANNEL_DOWNLOADS`, `LAST_MODEL_KEY`
**Files:** PascalCase matching the primary class — `ChatViewModel.kt`, `ChatUiState.kt`

### File/Class Naming by Layer

| Layer | Pattern | Examples |
|-------|---------|----------|
| Domain models | `{Name}` | `ChatMessage`, `Conversation`, `LocalModel` |
| Repository interfaces (`domain/repository/`) | `{Name}Repository` | `ChatRepository`, `EndpointRepository` |
| Repository implementations (`data/repository/`) | `{Name}RepositoryImpl` | `ChatRepositoryImpl`, `EndpointRepositoryImpl` |
| ViewModels (`ui/{feature}/`) | `{Name}ViewModel` | `ChatViewModel`, `HuggingFaceViewModel` |
| UI State (`ui/{feature}/`) | `{Name}UiState` | `ChatUiState`, `HuggingFaceUiState` |
| Screens (`ui/{feature}/`) | `{Name}Screen` | `ChatScreen`, `ModelsScreen` |
| Components (`ui/{feature}/components/`) | `{Name}` describing the widget | `ChatInputBar`, `MessageBubble`, `ModelSelector` |
| Room Entities (`data/local/db/entity/`) | `{Name}Entity` | `ConversationEntity`, `MessageEntity` |
| Room DAOs (`data/local/db/dao/`) | `{Name}Dao` | `ConversationDao`, `MessageDao` |
| Retrofit APIs (`data/remote/api/`) | `{Name}Api` | `OpenAiApi`, `HuggingFaceApi` |
| DTOs (`data/remote/dto/`) | `{Service}{Purpose}` | `OpenAiChatRequest`, `HuggingFaceModelDetail` |
| Providers (`data/remote/provider/`) | `{Name}Provider` | `OpenAIProvider`, `AnthropicProvider` |
| DI Modules (`di/`) | `{Domain}Module` | `DatabaseModule`, `NetworkModule`, `RepositoryModule` |
| Mapper extension functions | `{Source}.toDomain()` / `{Source}.toEntity()` | `MessageEntity.toDomain()`, `ChatMessage.toEntity()` |

## Architecture Conventions

### Clean Architecture Layer Rules

```text
ui/  ─── depends on ───>  domain/  <─── depends on ────  data/
```

- **`domain/`** — Pure Kotlin. Contains models, repository interfaces, and `LlmProvider` interface. Never imports Android framework classes (except `java.time.Instant`). No Hilt annotations on domain classes (except `ActiveModelSelection` which is a cross-cutting singleton with `@Inject`).
- **`data/`** — Implements repository interfaces. Contains Room entities/DAOs, Retrofit APIs, DTOs, provider implementations, inference engine code. Depends on Android framework (`Context`, `WorkManager`, etc.).
- **`ui/`** — Compose screens, ViewModels, UiState data classes, navigation, theme. Depends on `domain/` for models. Never directly imports from `data/` (ViewModels get repositories via Hilt).

### Package Structure

```
com.warped/
├── domain/
│   ├── model/          # Pure data classes (ChatMessage, Conversation, etc.)
│   ├── repository/     # Repository interfaces
│   └── provider/       # LlmProvider interface
├── data/
│   ├── repository/     # Repository implementations
│   ├── local/
│   │   ├── db/         # Room database, DAOs, entities, migrations, entity mappers
│   │   ├── download/   # Model download manager (WorkManager)
│   │   ├── inference/  # llama.cpp engine, LiteRT-LM engine, providers, memory checker
│   │   └── security/   # ApiKeyStore, KeystoreManager
│   └── remote/
│       ├── api/        # Retrofit API interfaces
│       ├── dto/        # Serializable request/response DTOs
│       ├── network/    # SSE parser, auth interceptors, HTTP client factory
│       └── provider/   # LlmProvider implementations (OpenAI, Anthropic, etc.)
├── di/                 # Hilt DI modules (one file per domain)
├── ui/
│   ├── chat/           # ChatScreen, ChatViewModel, ChatUiState, components/
│   ├── endpoints/      # EndpointsScreen, EndpointsViewModel, EndpointsUiState
│   ├── huggingface/    # HuggingFaceScreen, HuggingFaceViewModel, HuggingFaceUiState
│   ├── models/         # ModelsScreen, ModelsViewModel, ModelsUiState
│   ├── presets/        # PresetsScreen, PresetsViewModel, PresetsUiState
│   ├── settings/       # SettingsScreen, SettingsViewModel, SettingsUiState
│   ├── navigation/     # NavGraph, Screen sealed class
│   └── theme/          # Color, Shape, Theme, Type
├── MainActivity.kt
└── WarpedApplication.kt
```

### Repository Pattern

**Interface** (in `domain/repository/`):
```kotlin
interface ChatRepository {
    fun observeConversations(): Flow<List<Conversation>>
    suspend fun loadConversation(conversationId: Long): Pair<Conversation, List<ChatMessage>>?
    suspend fun createConversation(title: String, providerType: ProviderType, modelId: String?, endpointId: Long): Long
    suspend fun saveMessage(conversationId: Long, message: ChatMessage)
    suspend fun deleteConversation(conversationId: Long)
    suspend fun deleteAllConversations()
}
```

**Implementation** (in `data/repository/`):
```kotlin
@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) : ChatRepository { ... }
```

Key conventions:
- Single-purpose: one repository per domain aggregate
- Observable data: `Flow<List<T>>` for lists, `suspend fun` for mutations
- Return values: use `Result<T>` for network operations, nullable for optional `getById`, `Long` for inserts
- `@Singleton` on all repository implementations

## ViewModel Pattern

**Canonical pattern** (from `ChatViewModel.kt`):
```kotlin
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val endpointRepository: EndpointRepository,
    // ... more dependencies
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null

    init {
        // Observe data sources
        viewModelScope.launch {
            chatRepository.observeConversations().collect { conversations ->
                _uiState.update { it.copy(conversations = conversations) }
            }
        }
    }

    fun sendMessage(text: String) {
        // Validate state
        val state = _uiState.value
        if (text.isBlank()) return

        // Mutate state
        _uiState.update { it.copy(messages = it.messages + message, isStreaming = true) }

        generationJob = viewModelScope.launch {
            try {
                // ... async work
            } catch (e: Exception) {
                _uiState.update { it.copy(error = ChatError.Network(e.message ?: "Unknown error")) }
            }
        }
    }
}
```

**Key conventions:**
- `@HiltViewModel` annotation for Hilt injection
- `MutableStateFlow` as private `_uiState`, exposed as `StateFlow` via `asStateFlow()`
- `_uiState.update { it.copy(...) }` for atomic state transitions
- `viewModelScope.launch` for all coroutine launches
- `Job` tracking for cancellable operations (e.g., `generationJob`)
- `Dispatchers.Default` for CPU-bound work (model loading, generation)
- `withContext(Dispatchers.Default)` to switch contexts inside `viewModelScope`
- Error state stored as nullable field in UiState (e.g., `error: ChatError?`)
- `clearError()` function to dismiss errors
- User actions as public `fun` methods
- `SavedStateHandle` injected when process-death persistence needed

### UI State Pattern

**All UiStates are `data class` with all defaults:**
```kotlin
data class ChatUiState(
    val conversationId: Long? = null,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isStreaming: Boolean = false,
    val error: ChatError? = null,
    // ...
)

// Error hierarchy when multiple error types exist:
sealed class ChatError {
    data class Network(val message: String) : ChatError()
    data class Server(val code: Int, val message: String) : ChatError()
    data class Auth(val message: String) : ChatError()
    data object NoModelSelected : ChatError()
    data object DownloadModelFirst : ChatError()
    data object ConnectionLost : ChatError()
    data class Unknown(val message: String) : ChatError()
}
```

- Simple ViewModels use `String?` for errors (no sealed class)
- Form fields stored directly in UiState: `formName`, `formUrl`, `formApiType`, `formModelId`, `formApiKey`
- Dialog visibility as boolean flags: `isFormVisible`, `showDeleteChatsDialog`

## Compose UI Conventions

### Screen → ViewModel → UiState Pattern

```kotlin
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(conversationId) {
        if (conversationId > 0) viewModel.selectConversation(conversationId)
    }
    LaunchedEffect(Unit) {
        if (conversationId == 0L) viewModel.loadLastConversation()
    }

    Scaffold(
        topBar = { TopAppBar { ... } },
        bottomBar = { ChatInputBar(...) }
    ) { padding ->
        // Content
    }
}
```

**Key conventions:**
- `hiltViewModel()` for ViewModel injection
- `collectAsStateWithLifecycle()` for lifecycle-aware Flow collection
- `LaunchedEffect` for coroutine-triggered side effects (key determines re-invocation)
- `Scaffold` for top bar / bottom bar / content layout
- Callbacks passed as function parameters (`onOpenDrawer`, `onNavigateToModels`)
- `remember { mutableStateOf(false) }` for local UI state (dropdown expansion, dialog visibility)
- `rememberSaveable` for state surviving process death
- Material 3 with `@OptIn(ExperimentalMaterial3Api::class)`
- `Modifier.imePadding()` on Scaffold for keyboard handling
- Snackbar or inline Card for error/info display

### Component Composition

- Components extracted into `components/` subdirectory per feature
- Components receive state and callbacks as parameters (stateless where possible)
- Example: `ChatInputBar(text, isGenerating, canSend, onTextChange, onSend, onStop, ...)`

### Theme

- Custom dark theme colors: background `#1F1F1E`, accent `#D97757`, text `#ECECEC`
- Material 3 with `MaterialTheme.colorScheme`
- Files: `Color.kt`, `Type.kt`, `Shape.kt`, `Theme.kt`

### Navigation

- `sealed class Screen(val route: String, val label: String, val icon: ImageVector)` in `ui/navigation/Screen.kt`
- `NavHost` with `composable()` for each route
- Arguments passed via route string: `"${Screen.Chat.route}/{conversationId}"` with `NavType.LongType`
- `navController.navigate` with `popUpTo`, `launchSingleTop`, `restoreState` flags
- `ModalNavigationDrawer` for the app drawer
- Process death: `rememberSaveable` for restoring last route + conversation

## Data Layer Conventions

### Room Entities vs Domain Models

**Entity** (in `data/local/db/entity/`):
```kotlin
@Entity(tableName = "messages", foreignKeys = [...], indices = [...])
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "conversation_id") val conversationId: Long,
    @ColumnInfo(name = "role") val role: String,
    @ColumnInfo(name = "content") val content: String,
    // Room types: Long, String, Int, Float — no Instant, no sealed class
)
```

**Mapper** (in same file or dedicated `EntityMappers.kt`):
```kotlin
fun MessageEntity.toDomain(): ChatMessage = ChatMessage(
    id = id.toString(),
    role = Role.valueOf(role),
    content = content,
    // ...
)
fun ChatMessage.toEntity(conversationId: Long): MessageEntity = MessageEntity(
    conversationId = conversationId,
    role = role.name,
    content = content,
    // ...
)
```

**Key conventions:**
- Enums stored as `String` (via `.name` / `.valueOf()`)
- Timestamps stored as `Long` (millis) in entity, mapped to `Instant` in domain
- JSON-serialized columns `String?` for nested data (e.g., `images: String?` storing `List<String>`)
- `mapperJson` instance for JSON encode/decode inside mappers
- Foreign keys with `CASCADE` delete, indices on foreign key columns
- Room database `@Database` in `AppDatabase.kt`, version 9, migrations in `Migrations.kt`

### DAO Patterns

```kotlin
@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: Long): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity): Long

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: Long)
}
```

**Key conventions:**
- Observable methods: `fun` returning `Flow` (NOT `suspend`)
- One-shot queries: `suspend fun` returning entity/nullable
- `@Insert(onConflict = REPLACE)` for upsert semantics
- `@Query` for everything that isn't a simple insert; explicit SQL
- `@Update` sparingly; prefer `@Query` for clarity

### Repository Implementations

**Standard pattern:**
```kotlin
@Singleton
class PresetRepositoryImpl @Inject constructor(
    private val presetDao: PresetDao
) : PresetRepository {
    override fun observePresets(): Flow<List<Preset>> =
        presetDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: Long): Preset? =
        presetDao.getById(id)?.toDomain()

    override suspend fun save(preset: Preset): Long =
        presetDao.upsert(preset.toEntity())

    override suspend fun delete(id: Long) {
        presetDao.deleteById(id)
    }
}
```

**Key conventions:**
- Map entities→domain in Flow using `.map { list -> list.map { it.toDomain() } }`
- Map domain→entity at the call site: `preset.toEntity()`
- `@Singleton` annotation on implementation
- Dependencies injected via `@Inject constructor`

### Network Repositories

```kotlin
@Singleton
class HuggingFaceRepositoryImpl @Inject constructor(
    okHttpClient: OkHttpClient,
    json: Json
) : HuggingFaceRepository {
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://huggingface.co/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
    private val api = retrofit.create(HuggingFaceApi::class.java)

    override suspend fun searchModels(...): Result<List<HuggingFaceModel>> {
        return try {
            val response = api.searchModels(...)
            if (response.isSuccessful) Result.success(response.body() ?: emptyList())
            else Result.failure(Exception("Search failed: HTTP ${response.code()}"))
        } catch (e: Exception) {
            Timber.e(e, "HF API search failed")
            Result.failure(e)
        }
    }
}
```

**Key conventions:**
- Use `Result<T>` for network operations
- Log failures with `Timber.e(e, ...)` before returning `Result.failure(e)`
- Create Retrofit instance per repository (not shared) for different base URLs/configs
- Accept shared `OkHttpClient` and `Json` via constructor for consistency

## Networking Conventions

### Retrofit API Interfaces

```kotlin
interface OpenAiApi {
    @POST("v1/chat/completions")
    @Headers("Content-Type: application/json")
    suspend fun chatCompletions(@Body request: OpenAiChatRequest): Response<ResponseBody>

    @GET("v1/models")
    suspend fun listModels(): Response<OpenAiModelListResponse>
}
```

**Key conventions:**
- `suspend fun` returning `Response<T>` (not just `T`)
- Streaming endpoints return `Response<ResponseBody>` for manual SSE parsing
- JSON endpoints return typed DTOs
- `@Headers("Content-Type: application/json")` on POST endpoints
- `@SerialName` for snake_case JSON fields in DTOs

### DTOs

```kotlin
@Serializable
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val stream: Boolean = true,
    val temperature: Float? = null,
    @SerialName("top_p") val topP: Float? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
)
```

**Key conventions:**
- `@Serializable` annotation on all DTOs
- `@SerialName` for field name mapping (snake_case → camelCase)
- Nullable fields for optional parameters
- Default values where API specifies defaults
- Reuse DTOs across providers when protocol-compatible (`OpenAiChatRequest` used by both OpenAI and Custom)

### SSE Streaming

**Extension pattern:**
```kotlin
fun ResponseBody.asSseFlow(json: Json): Flow<StreamToken> = flow {
    val parser = SseParser()
    try {
        val source = source()
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: continue
            val events = parser.feed(line + "\n")
            for (event in events) {
                when {
                    event.data == "[DONE]" -> { emit(StreamToken.Done()); return@flow }
                    event.data.isBlank() -> continue
                    else -> {
                        val chunk = json.decodeFromString<OpenAiStreamChunk>(event.data)
                        chunk.choices.firstOrNull()?.delta?.content?.let { emit(StreamToken.Delta(it)) }
                    }
                }
            }
        }
    } catch (e: IOException) {
        emit(StreamToken.Error("Connection lost: ${e.message}"))
    } finally {
        parser.reset()
        close()
    }
}.flowOn(Dispatchers.IO)
```

**Key conventions:**
- Extension functions on `ResponseBody` in `data/remote/network/SseExtensions.kt`
- Custom `SseParser` class with buffer-based feeding (not stream-based)
- `flowOn(Dispatchers.IO)` for the entire SSE flow
- `[DONE]` token signals stream completion
- Malformed JSON lines silently skipped via `catch (_: Exception) {}`
- IO exceptions emitted as `StreamToken.Error`

### HTTP Client Configuration

**Global client** (from `NetworkModule`):
```kotlin
OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)    // Long timeout for streaming
    .writeTimeout(30, TimeUnit.SECONDS)
    .addInterceptor(authInterceptor)
    .addInterceptor(loggingInterceptor)
    .retryOnConnectionFailure(true)
    .connectionPool(ConnectionPool(5, 1, TimeUnit.MINUTES))
    .build()
```

**Logging:**
- `HttpLoggingInterceptor.Level.HEADERS` for global client
- `HttpLoggingInterceptor.Level.BODY` in `HttpClientFactory`

**Auth:** `AuthInterceptor` reads API key from `ApiKeyStore` using request tag (`request.tag(Long::class.java)` as endpointId), adds `Authorization: Bearer` header, zeroes out char array after use.

### Provider Implementations

```kotlin
class OpenAIProvider(
    private val baseUrl: String,
    private val modelId: String,
    endpointId: Long
) : LlmProvider {
    override val type = ProviderType.OPENAI

    override fun chat(request: ChatRequest): Flow<StreamToken> = flow {
        val messages = request.messages.map {
            OpenAiMessage(role = it.role.name.lowercase(), content = it.content)
        }
        val body = OpenAiChatRequest(model = modelId, messages = messages, stream = true, ...)
        try {
            val response = api.chatCompletions(body)
            if (response.isSuccessful) {
                response.body()?.asSseFlow(json)?.collect { emit(it) }
            } else {
                emit(StreamToken.Error("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(StreamToken.Error("Connection failed: ${e.message}"))
        }
    }
}
```

**Key conventions:**
- Instantiated per-endpoint via `ProviderRouter.resolve()` with runtime parameters
- Each creates its own `Retrofit` instance (different base URLs)
- `chat()` returns `Flow<StreamToken>` — caller collects and emits tokens
- Generic catch wraps exceptions as `StreamToken.Error`
- `listModels()` and `testConnection()` return `Result<T>`

### StreamToken Model

```kotlin
sealed interface StreamToken {
    data class Delta(val content: String) : StreamToken
    data class Done(val stats: String? = null, val reasoning: String? = null) : StreamToken
    data class Error(val message: String) : StreamToken
}
```

Used as the common currency between all providers (local and remote). ViewModels consume the flow and update UiState accordingly.

## DI Conventions

### Module Organization

**`object` module with `@Provides`** — for constructing concrete types:
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun provideOkHttpClient(...): OkHttpClient = OkHttpClient.Builder()...build()

    @Provides @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true; isLenient = true; ... }
}
```

**`abstract class` module with `@Binds`** — for interface→implementation binding:
```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository
}
```

**Key conventions:**
- All modules installed in `SingletonComponent::class`
- One module file per domain concern: `DatabaseModule`, `NetworkModule`, `SecurityModule`, `InferenceModule`, `RepositoryModule`, `HuggingFaceModule`, `ProviderModule`
- `ProviderModule` is mostly empty (providers created dynamically by `ProviderRouter`)
- `@ApplicationContext` for `Context` injection in modules
- `@Singleton` on all provided instances that are stateless or shared
- DAOs provided as unscoped `@Provides` (scoped by database lifetime)

### Scoping Rules

| Scope | When Used |
|-------|-----------|
| `@Singleton` (default in `SingletonComponent`) | Database, OkHttpClient, Repositories, Engines (llama, LiteRT), ApiKeyStore, WorkManager, EngineManager, MemoryChecker |
| Unscoped | DAOs (bounded to database lifecycle) |
| Runtime parameters | Providers (OpenAI, Anthropic, etc.) — created via `ProviderRouter.resolve()` |

## Error Handling

### Error Propagation

```
Provider (StreamToken.Error) → ViewModel (ChatError.Network) → UiState.error → Compose Snackbar
```

**At provider level:** Exceptions caught, emitted as `StreamToken.Error(message)` for streaming operations and `Result.failure(e)` for RPC-style operations.

**At repository level:** Exceptions caught, logged via `Timber.e()`, returned as `Result.failure()`.

**At ViewModel level:** `try/catch` around provider flows, maps errors to typed `ChatError` sealed subclasses, stores in `_uiState.update { it.copy(error = ...) }`.

**At UI level:** `when (val error = uiState.error)` renders appropriate user-facing message, dismiss with `clearError()`.

### Exception Hierarchy

- `ChatError` sealed class — primary user-facing error model (in `ChatUiState.kt`)
- `StreamToken.Error` — generic streaming error used by all providers
- `Result.failure(Exception(...))` — domain-level error for non-streaming operations
- No custom exception hierarchy — uses standard `Exception` with descriptive messages

### Error Message Patterns

- User messages are simple strings: `"No model selected"`, `"Connection lost"`
- Network errors include context: `"HTTP ${code}: ${message}"`
- Local model errors: `"Failed to load model"`, specific messages for missing files, unsupported formats
- All errors logged via Timber before being surfaced

## Logging

### Framework

**Timber 5.0.1** — planted as `RedactingTree` (custom `DebugTree` that strips API keys/bearer tokens) in debug builds only.

### Tag Convention

Messages use a **ClassName prefix** pattern:
```kotlin
Timber.d("EngineManager: initializing LiteRT-LM with backend=${target.backend}")
Timber.d("ModelDownloadWorker: starting download — modelId=$modelId url=$fileUrl")
Timber.e(e, "HF API search failed: query=$query format=$format author=$author")
Timber.w(e, "EngineManager: error during unload of $current")
```

**Log levels:**
- `Timber.d()` — Debug info: state transitions, parameter values, initialization
- `Timber.e()` — Errors: network failures, load failures, API errors (with exception)
- `Timber.w()` — Warnings: non-critical failures, recoverable errors
- `Timber.i()` — Not observed in codebase

### Security

- `RedactingTree` strips `api_key=...`, `secret=...`, `token=...`, `Bearer ...` patterns
- Never logs full API keys, only endpoint IDs
- `charArray.fill('0')` after extracting API key for zero-out security

## State Management

### StateFlow Pattern

- `MutableStateFlow` as private backing field, publicly exposed as `StateFlow` via `asStateFlow()`
- All state updates use `_uiState.update { it.copy(...) }` — atomic read-modify-write
- No `SharedFlow` usage — no event bus pattern in the app

### One-Shot Events

Events that should fire only once (errors, success messages, navigation triggers) are handled as:
- **Nullable fields** in UiState: `error: ChatError?`, `modelLoadError: String?`
- Set on event occurrence, manually cleared via `clearError()` / `clearModelLoadError()`
- No `Channel` or `SharedFlow` for events

### Cross-ViewModel State

- **`ActiveModelSelection`** — `@Singleton` class with its own `MutableStateFlow<ActiveModel?>` — injected into both `ChatViewModel` and `ModelsViewModel` for shared active model state
- **`ParameterStore`** — `@Singleton` class with `MutableStateFlow<GenerationParameters>` — injected into `ChatViewModel` and `PresetsViewModel` for shared parameter state

## Function Design

### ViewModel Action Pattern

```kotlin
fun updateInput(text: String) {
    _uiState.update { it.copy(inputText = text) }
}

fun clearError() {
    _uiState.update { it.copy(error = null) }
}

fun sendMessage(text: String) {
    // 1. Validate: guard clause returns early
    if (text.isBlank()) return

    // 2. Mutate state synchronously
    _uiState.update { it.copy(isStreaming = true) }

    // 3. Launch async work
    generationJob = viewModelScope.launch {
        try {
            // ...
        } catch (e: Exception) {
            _uiState.update { it.copy(error = ...) }
        }
    }
}
```

### Parameter Ordering

- Required dependency injection arguments first
- `SavedStateHandle` and `@ApplicationContext Context` near the end
- `@param:ApplicationContext` for named qualifier annotations

### Return Value Patterns

| Context | Return Type |
|---------|------------|
| Repository `observe*` | `Flow<List<T>>` |
| Repository `getById` | `T?` (nullable) |
| Repository `save/create` | `Long` (ID) or `Unit` |
| Repository network calls | `Result<T>` |
| Provider `chat` | `Flow<StreamToken>` |
| Provider RPC calls | `Result<T>` |
| ViewModel actions | `Unit` (state updated via StateFlow) |

## Module Design

### Exports

- No `internal` visibility used — all classes are public
- No barrel/index files — imports are explicit per file
- No star imports except for packages with many items: `com.warped.domain.model.*` and Compose layout imports

### File Organization

- One primary class per file (exception: tightly-coupled small types like `StreamToken` subclasses)
- Mapper functions co-located in entity files or a dedicated `EntityMappers.kt`
- `companion object` placed at the bottom of the class (convention, not enforced)
- `init` blocks at the top of the class body, after property declarations

---

*Convention analysis: 2026-05-02*
