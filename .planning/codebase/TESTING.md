# Testing Patterns

**Analysis Date:** 2026-05-02

## Test Infrastructure

**WARNING: The test infrastructure is configured but contains no actual tests.** Test directories exist but are empty.

### Test Frameworks and Versions

| Dependency | Version | Purpose |
|-----------|---------|---------|
| JUnit Jupiter | 5.11.4 | Unit test framework |
| MockK | 1.13.16 | Kotlin-native mocking (supports coroutines, static mocks) |
| Turbine | 1.1.0 | Flow testing library (`flow.test { awaitItem() }` pattern) |
| Truth | 1.4.4 | Google's fluent assertion library |
| kotlinx-coroutines-test | 1.9.0 | Coroutine testing (`runTest {}`, `TestDispatcher`) |
| room-testing | 2.7.1 | Room in-memory database testing |
| compose-ui-test-junit4 | via BOM 2026.04.01 | Compose UI testing (`ComposeTestRule`, `onNodeWithText()`, `performClick()`) |

### Test Runner

- **Unit tests:** JUnit 5 (via `junit-jupiter`)
- **Instrumentation tests:** `AndroidJUnitRunner` (`androidx.test.runner.AndroidJUnitRunner`)

### Test Directory Structure

```
app/
├── src/
│   ├── test/              # Unit tests (empty — no test files)
│   ├── androidTest/       # Instrumentation tests (empty — no test files)
│   └── main/              # Application source code
```

### Build Configuration for Tests

From `app/build.gradle.kts`:
```kotlin
// Unit test dependencies
testImplementation(libs.junit5)
testImplementation(libs.mockk)
testImplementation(libs.turbine)
testImplementation(libs.truth)
testImplementation(libs.kotlinx.coroutines.test)
testImplementation(libs.room.testing)

// Instrumentation test dependencies
androidTestImplementation(composeBom)
androidTestImplementation(libs.compose.ui.test)

// Test runner
defaultConfig {
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
}
```

### Run Commands

```bash
# Run all unit tests
./gradlew test

# Run all instrumentation tests (on device/emulator)
./gradlew connectedAndroidTest

# Run specific test class
./gradlew test --tests "com.warped.ExampleTest"

# No coverage tooling configured
```

## Existing Tests

### Unit Tests

**Location:** `app/src/test/`

**Status:** **EMPTY** — No test files exist. The directory is present but contains no `.kt` files.

### Instrumentation Tests

**Location:** `app/src/androidTest/`

**Status:** **EMPTY** — No test files exist. The directory is present but contains no `.kt` files.

## Test Patterns

The following patterns SHOULD be used when writing tests, based on the configured dependencies and the project's architecture conventions:

### ViewModel Testing

```kotlin
// Pattern to use (based on MockK + Turbine + coroutines-test):
class ChatViewModelTest {
    @get:Rule
    val coroutineRule = TestCoroutineRule()

    private val chatRepository: ChatRepository = mockk()
    private val viewModel = ChatViewModel(chatRepository, ...)

    @Test
    fun `sendMessage with blank text does nothing`() = coroutineRule.runTest {
        viewModel.uiState.test {
            skipItems(1) // skip initial state
            viewModel.sendMessage("")
            expectNoEvents()
        }
    }

    @Test
    fun `sendMessage emits streaming tokens`() = coroutineRule.runTest {
        coEvery { chatRepository.createConversation(...) } returns 1L
        // ...
    }
}
```

**Key testing targets:**
- State transitions: verify `uiState` values change correctly
- Error handling: verify error states on exceptions
- Cancellation: verify `stopGeneration()` cancels the job
- Model selection: verify state updates when model changes
- Form validation: verify validation guards

### Repository Testing

**For Room-backed repositories (unit test with in-memory DB):**
```kotlin
// Pattern to use (based on room-testing):
class ChatRepositoryImplTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: ChatRepositoryImpl

    @BeforeEach
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        repository = ChatRepositoryImpl(db.conversationDao(), db.messageDao())
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    @Test
    fun `createConversation returns valid id`() = runTest {
        val id = repository.createConversation("Test", ProviderType.LOCAL, null, 0)
        assertThat(id).isGreaterThan(0)
    }
}
```

**For network-backed repositories:**
```kotlin
// Pattern to use (MockK for API, in-memory DB for cache):
class HuggingFaceRepositoryImplTest {
    private val api: HuggingFaceApi = mockk()
    private val okHttpClient: OkHttpClient = mockk()
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `searchModels returns success on valid response`() = runTest {
        coEvery { api.searchModels(...) } returns Response.success(listOf(...))
        val result = repository.searchModels("llama")
        assertThat(result.isSuccess).isTrue()
    }
}
```

### DAO Testing

```kotlin
class ConversationDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ConversationDao

    @BeforeEach
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(...).build()
        dao = db.conversationDao()
    }

    @Test
    fun `observeAll emits inserted conversations`() = runTest {
        dao.observeAll().test {
            dao.upsert(ConversationEntity(title = "Test", ...))
            awaitItem()?.let { assertThat(it).isNotEmpty() }
        }
    }
}
```

### Coroutine Testing

```kotlin
// Standard pattern using kotlinx-coroutines-test:
class SomeTest {
    @Test
    fun `async operation`() = runTest {
        // runTest provides TestScope with TestDispatcher
        val result = someSuspendFunction()
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `flow collection with time control`() = runTest {
        someFlow.test {
            assertThat(awaitItem()).isEqualTo(firstValue)
            assertThat(awaitItem()).isEqualTo(secondValue)
            awaitComplete()
        }
    }
}
```

### Mock Patterns

**Standard MockK usage:**
```kotlin
// Relaxed mock (returns default values for unstubbed calls)
private val repository: ChatRepository = mockk(relaxed = true)

// Stub suspend function
coEvery { repository.createConversation(any(), any(), any(), any()) } returns 42L

// Stub Flow
every { repository.observeConversations() } returns flowOf(listOf(...))

// Verify call
coVerify { repository.saveMessage(eq(42L), any()) }

// Capture argument
val slot = slot<ChatMessage>()
coEvery { repository.saveMessage(any(), capture(slot)) } just Runs
```

### Compose UI Testing

```kotlin
// Pattern to use (based on compose-ui-test):
class ChatScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `displays loading indicator when model loading`() {
        composeTestRule.setContent {
            // Set up ViewModel with loading state
            ChatScreen(viewModel = mockViewModel)
        }
        composeTestRule.onNodeWithText("Loading...").assertIsDisplayed()
    }
}
```

## Coverage

### What's Well Tested

**Nothing.** There are zero test files. The entire codebase (~90+ source files) has no test coverage.

### What's Poorly Tested

**Everything.** All layers, all feature screens, all repositories, all providers, all ViewModels are untested.

### What's Untested

The complete list of untested areas:

| Layer | Files | What Needs Testing |
|-------|-------|-------------------|
| **ViewModels** | `ChatViewModel.kt` (550 lines), `HuggingFaceViewModel.kt` (196 lines), `ModelsViewModel.kt` (292 lines), `EndpointsViewModel.kt` (154 lines), `PresetsViewModel.kt` (209 lines), `SettingsViewModel.kt` (122 lines) | State transitions, error handling, event flows, model loading, streaming token handling |
| **Repositories** | 5 `*RepositoryImpl.kt` files | CRUD operations, entity↔domain mapping, Room queries, network-to-cache flows |
| **DAOs** | 6 `*Dao.kt` files | Room queries, Flow emissions on insert/delete, cascade deletes |
| **Providers** | `OpenAIProvider.kt`, `AnthropicProvider.kt`, `OllamaProvider.kt`, `LMStudioProvider.kt`, `CustomProvider.kt`, `LocalLlmProvider.kt`, `LiteRTLmProvider.kt` | Chat streaming, SSE parsing, error handling, timeout behavior, retry logic |
| **SSE Parsing** | `SseParser.kt`, `SseExtensions.kt` | Buffer parsing, event extraction, `[DONE]` detection, malformed input handling |
| **Inference Engine** | `EngineManager.kt`, `LlamaEngine.kt`, `LiteRTLmEngine.kt` | Model loading/unloading, memory management, engine switching |
| **Download Manager** | `ModelDownloadManager.kt` (316 lines), `ModelDownloadWorker.kt` (363 lines) | WorkManager flow, checkpoint/resume, progress tracking, error recovery |
| **Compose UI** | All `*Screen.kt` files | Component rendering, user interaction flows, state rendering |
| **Network** | `AuthInterceptor.kt`, `HttpClientFactory.kt` | Token injection, header construction, timeout handling |
| **Security** | `ApiKeyStore.kt`, `KeystoreManager.kt` | Key encryption/decryption, secure storage, zero-out |
| **Entity Mappers** | `EntityMappers.kt`, `LocalModelMappers.kt`, `PresetMappers.kt` | Domain↔entity roundtrip, JSON serialization of nested fields |

### Priority Areas for Test Coverage

1. **ChatViewModel.sendMessage()** — Most complex logic: provider resolution, streaming, token parsing, error handling (550 lines)
2. **SSE parsing** — SseParser and ResponseBody extensions — core to all remote providers
3. **EngineManager** — Model loading/unloading lifecycle with synchronization
4. **ModelDownloadWorker** — Download with resume/checkpoint, foreground notification
5. **Provider implementations** — Each provider's chat/error handling behavior
6. **Repository implementations** — Ensure DAO mapping correctness

## Test Dependencies from Version Catalog

From `gradle/libs.versions.toml`:
```toml
[versions]
junit5 = "5.11.4"
mockk = "1.13.16"
turbine = "1.1.0"
truth = "1.4.4"
coroutines-test = "1.9.0"
errorprone = "2.28.0"

[libraries]
junit5 = { group = "org.junit.jupiter", name = "junit-jupiter", version.ref = "junit5" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
truth = { group = "com.google.truth", name = "truth", version.ref = "truth" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines-test" }
room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
errorprone-annotations = { group = "com.google.errorprone", name = "error_prone_annotations", version.ref = "errorprone" }
```

## Notes

- The project has well-configured test infrastructure (JUnit 5, MockK, Turbine, Truth, coroutines-test, room-testing, compose-ui-test) but **zero test files**
- All test dependencies are declared but unused
- The project follows Clean Architecture which is inherently testable: domain layer has no Android dependencies, repositories are behind interfaces, ViewModels follow the `StateFlow<UiState>` pattern
- ViewModels at 100–550 lines with many conditional branches are the highest-priority testing targets
- The `Room.inMemoryDatabaseBuilder()` + `room-testing` artifact pattern is available for DAO/repository unit tests
- `kotlinx-coroutines-test` with `runTest {}` provides deterministic coroutine testing with virtual time

---

*Testing analysis: 2026-05-02*
