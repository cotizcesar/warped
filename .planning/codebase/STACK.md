# Technology Stack

**Analysis Date:** 2026-05-02

## Platform

- **Language:** Kotlin 2.1.10 — all code is Kotlin (no Java)
- **Build SDK:** `compileSdk = 35` (Android 15)
- **Min SDK:** `minSdk = 28` (Android 9) — enables EncryptedSharedPreferences, stable NDK ABI
- **Target SDK:** `targetSdk = 35`
- **Java Target:** JVM 17 (`sourceCompatibility`/`targetCompatibility` + Kotlin `jvmTarget = JVM_17`)
- **UI Toolkit:** Jetpack Compose, governed by BOM `2026.04.01`
- **Material Design:** Material 3 via Compose BOM, with Material Icons Extended included
- **Compose Compiler:** Built-in (Kotlin 2.0+ compiler plugin, no separate artifact)

## Build System

- **Gradle:** 9.4.1 (distribution URL in `gradle/wrapper/gradle-wrapper.properties`)
- **Android Gradle Plugin (AGP):** 9.0.0 (`gradle/libs.versions.toml`)
- **KSP:** 2.1.10-1.0.31 (Kotlin Symbol Processing — replaces kapt for Room, Hilt, Kotlinx Serialization)
- **CMake:** 3.22.1 (native build for llama.cpp JNI bridge)
- **NDK:** NDK 27+ (cross-compile for `arm64-v8a`, `x86_64`)
- **Dependency Management:** Gradle Version Catalog (`gradle/libs.versions.toml`) with `[versions]`, `[libraries]`, `[plugins]` sections
- **Gradle Plugin Toolchain:** `org.gradle.toolchains.foojay-resolver-convention` 1.0.0
- **Build Features:** Compose enabled, BuildConfig enabled

**Plugins declared (root):**
| Plugin | ID | Version |
|--------|-----|---------|
| Android Application | `com.android.application` | 9.0.0 |
| Kotlin Android | `org.jetbrains.kotlin.android` | 2.1.10 |
| Compose Compiler | `org.jetbrains.kotlin.plugin.compose` | 2.1.10 |
| Kotlin Serialization | `org.jetbrains.kotlin.plugin.serialization` | 2.1.10 |
| Hilt | `com.google.dagger.hilt.android` | 2.59.2 |
| KSP | `com.google.devtools.ksp` | 2.1.10-1.0.31 |

**Gradle properties:**
- `org.gradle.parallel=true` — parallel project execution
- `org.gradle.caching=true` — build cache enabled
- `org.gradle.configuration-cache=true` — configuration cache enabled
- `android.useAndroidX=true` — AndroidX libraries
- `kotlin.code.style=official` — official Kotlin code style
- JVM args: `-Xmx2048m --enable-native-access=ALL-UNNAMED`

## Core Dependencies

### UI (governed by Compose BOM 2026.04.01)
| Library | Group | Purpose |
|---------|-------|---------|
| compose-ui | `androidx.compose.ui:ui` | Core Compose UI |
| compose-ui-graphics | `androidx.compose.ui:ui-graphics` | Graphics primitives |
| compose-ui-tooling-preview | `androidx.compose.ui:ui-tooling-preview` | Preview support |
| compose-ui-tooling | `androidx.compose.ui:ui-tooling` | Debug tooling (debugImplementation) |
| compose-material3 | `androidx.compose.material3:material3` | Material 3 components |
| compose-material-icons | `androidx.compose.material:material-icons-extended` | Extended Material Icons |
| compose-ui-test | `androidx.compose.ui:ui-test-junit4` | Compose UI tests (androidTest) |
| compose-ui-test-manifest | `androidx.compose.ui:ui-test-manifest` | Test manifest (debugImplementation) |

### Lifecycle & ViewModel
| Library | Version | Purpose |
|---------|---------|---------|
| lifecycle-viewmodel-compose | 2.8.7 | ViewModel integration with Compose (`collectAsStateWithLifecycle()`) |
| lifecycle-runtime-compose | 2.8.7 | Lifecycle-aware Flow collection in Compose |
| lifecycle-runtime-ktx | 2.8.7 | Lifecycle coroutine scopes |

### Navigation
| Library | Version | Purpose |
|---------|---------|---------|
| navigation-compose | 2.8.8 | Type-safe Compose navigation, NavGraph, Screen routes |

### Dependency Injection (Hilt)
| Library | Version | Purpose |
|---------|---------|---------|
| hilt-android | 2.59.2 | Core Hilt DI (Dagger-based, compile-time) |
| hilt-compiler | 2.59.2 | Hilt annotation processor (KSP) |
| hilt-androidx-compiler | 1.2.0 | AndroidX Hilt extensions compiler |
| hilt-navigation-compose | 1.2.0 | Hilt ViewModel integration with Navigation |
| hilt-work | 1.2.0 | Hilt integration with WorkManager (`@HiltWorker`) |

**DI modules** (all at `app/src/main/java/com/warped/di/`):
- `DatabaseModule.kt` — Room database, DAOs, WorkManager
- `NetworkModule.kt` — OkHttpClient, logging interceptor, JSON config
- `SecurityModule.kt` — KeystoreManager, ApiKeyStore
- `HuggingFaceModule.kt` — HuggingFaceRepository bindings
- `InferenceModule.kt` — LlamaEngine, LiteRTLmEngine, EngineManager, BackendDetector, MemoryChecker, InputSanitizer, LiteRTLmProvider, LocalLlmProvider
- `ProviderModule.kt` — ProviderRouter (constructor injection)
- `RepositoryModule.kt` — ChatRepository, EndpointRepository, ModelRepository, PresetRepository bindings

### Local Storage
| Library | Version | Purpose |
|---------|---------|---------|
| room-runtime | 2.7.1 | Room ORM — SQLite with compile-time verification |
| room-ktx | 2.7.1 | Kotlin coroutines support for Room (`suspend` DAOs) |
| room-compiler | 2.7.1 | Room KSP annotation processor |
| datastore-preferences | 1.1.3 | Type-safe key-value preferences (replaces SharedPreferences) |

**Room entities** (`app/src/main/java/com/warped/data/local/db/entity/`):
- `ConversationEntity` — chat conversations
- `MessageEntity` — chat messages (role, content, token_count, timestamps)
- `RemoteEndpointEntity` — remote API endpoint configs
- `LocalModelEntity` — downloaded/imported GGUF/LiteRT-LM models
- `PresetEntity` — saved generation parameter presets
- `DownloadCheckpointEntity` — download resume checkpoints

**DAOs** (`app/src/main/java/com/warped/data/local/db/dao/`):
- `ConversationDao`, `MessageDao`, `RemoteEndpointDao`, `LocalModelDao`, `PresetDao`, `DownloadCheckpointDao`

**Database:** `AppDatabase` — version 9, single SQLite file `warped.db`, migrations `MIGRATION_4_5` through `MIGRATION_8_9`, fallback to destructive migration

**GGUF/Model file storage:** `context.filesDir/models/` (app-private internal storage)

### Networking
| Library | Version | Purpose |
|---------|---------|---------|
| okhttp | 4.12.0 | HTTP client — connection pooling, interceptors |
| okhttp-logging | 4.12.0 | HTTP request/response logging interceptor |
| retrofit | 3.0.0 | Type-safe REST client on top of OkHttp |
| retrofit-kotlinx-serialization | 3.0.0 | Retrofit converter for Kotlinx Serialization |
| kotlinx-serialization-json | 1.7.3 | JSON serialization/deserialization (no reflection) |

**Custom networking components** (`app/src/main/java/com/warped/data/remote/network/`):
- `HttpClientFactory.kt` — shared HttpClientFactory (used by ModelDownloadWorker)
- `AuthInterceptor.kt` — adds `Bearer {apiKey}` header from ApiKeyStore based on endpoint ID request tag
- `SseParser.kt` — custom SSE parser (buffer-based, `\n\n` delimiter, `data:`/`event:` lines)
- `SseEvent.kt` — data class `SseEvent(data: String, event: String?)`
- `SseExtensions.kt` — `ResponseBody.asSseFlow()` (OpenAI SSE), `ResponseBody.asOllamaFlow()` (Ollama JSON-line streaming)

### Concurrency
| Library | Version | Purpose |
|---------|---------|---------|
| kotlinx-coroutines-core | 1.9.0 | Structured concurrency, Flows, Channels |
| kotlinx-coroutines-android | 1.9.0 | Android-specific `Dispatchers.Main` |

### Background Work
| Library | Version | Purpose |
|---------|---------|---------|
| work-runtime-ktx | 2.10.0 | Deferrable background work (downloads), foreground service support |

**Key workers:** `ModelDownloadWorker` (`app/src/main/java/com/warped/data/local/download/ModelDownloadWorker.kt`) — `@HiltWorker`, `CoroutineWorker`, foreground notification with progress, checkpoint persistence to Room, HTTP Range header resume support

### Security
| Library | Version | Purpose |
|---------|---------|---------|
| security-crypto | 1.1.0-alpha06 | EncryptedSharedPreferences (AES-256 backed by Android Keystore, Tink integration) |

**Security components** (`app/src/main/java/com/warped/data/local/security/`):
- `KeystoreManager.kt` — `MasterKey` (AES-256 GCM, hardware-backed via Android Keystore), `EncryptedSharedPreferences` (AES256_SIV keys, AES256_GCM values)
- `ApiKeyStore.kt` — stores/retrieves API keys as `CharArray` (zeroed after use), keyed by endpoint ID alias

**ProGuard/R8 rules** (`app/proguard-rules.pro`):
- Keep Kotlinx Serialization generated serializers and companions
- Keep Room database classes
- Keep LiteRT-LM classes (`com.google.ai.edge.litertlm.**`)

**Network security** (`app/src/main/res/xml/network_security_config.xml`):
- `base-config` with `cleartextTrafficPermitted="true"` (needed for local network Ollama/LM Studio HTTP access)
- System trust anchors only

### Logging
| Library | Version | Purpose |
|---------|---------|---------|
| timber | 5.0.1 | Logging facade over Android Log (Jake Wharton) |
| errorprone-annotations | 2.28.0 | Compile-time error checking annotations |

**Custom Timber Tree:** `RedactingTree` in `WarpedApplication.kt` — redacts API keys, secrets, tokens, and Bearer tokens from log output using regex

## Local LLM Inference

### llama.cpp (JNI/NDK)
| Component | Details |
|-----------|---------|
| Language | C++17 (`app/src/main/cpp/`) |
| Build | CMake 3.22.1 via `app/build.gradle.kts` `externalNativeBuild` |
| Library Name | `warped_llama` (shared library, loaded via `System.loadLibrary`) |
| ABIs | `arm64-v8a`, `x86_64` |
| Compile defs | `GGML_USE_CPU=1`, `GGML_USE_CPU_AARCH64=1` |
| Linked libs | `android`, `log` (Android NDK system libs) |
| Linker flags | `-Wl,-z,max-page-size=16384` |

**Native source files:**
- `app/src/main/cpp/jni_bridge.h` — C++ `LlamaEngine` class (singleton, `loadModel`, `generate`, `stop`, `unload`, `isLoaded`, `getModelInfo`)
- `app/src/main/cpp/jni_bridge.cpp` — JNI functions (`nativeLoadModel`, `nativeGenerate`, `nativeStop`, `nativeUnload`, `nativeIsLoaded`, `nativeGetModelInfo`), stub implementations (real llama.cpp integration pending)

**Kotlin side** (`app/src/main/java/com/warped/data/local/inference/`):
- `LlamaEngine.kt` — JNI wrapper with `callbackFlow` for token streaming, `TokenCallback` interface
- `LocalLlmProvider.kt` — Implements `LlmProvider`, uses chat template `\<|system|\>`, `\<|user|\>`, `\<|assistant|\>` format

### LiteRT-LM (Google AI Edge)
| Component | Details |
|-----------|---------|
| SDK | `com.google.ai.edge.litertlm:litertlm-android:0.11.0-rc1` |
| Format | `.litertlm` model files (separate from GGUF) |
| Backends | CPU (default), GPU (auto-detected via `BackendDetector`) |
| ProGuard | Keep all `com.google.ai.edge.litertlm.**` classes |

**Key classes** (`app/src/main/java/com/warped/data/local/inference/`):
- `LiteRTLmEngine.kt` — wraps `Engine`, `EngineConfig`, `ConversationConfig`, `Conversation`. Blocking `init()`, `createConversation()`, `close()`
- `BackendDetector.kt` — probes EGL display + OpenCL library availability at runtime, caches result. Returns `BackendType.GPU` or `BackendType.CPU`
- `LiteRTLmProvider.kt` — Implements `LlmProvider`, sends `Contents` (text + base64-decoded images), maps `GenerationParameters` → `SamplerConfig`, 2-retry recovery loop on engine errors
- `EngineManager.kt` — coordinates engine switching (`switchToLlama`/`switchToLiteRT`), lifecycle-aware unloading on memory pressure, model file caching

### Engine Management
- `EngineManager` (`app/src/main/java/com/warped/data/local/inference/EngineManager.kt`) — singleton coordinator, tracks `ActiveEngine` (type + path + backend), handles engine switching with proper cleanup
- `ModelImportManager` (`app/src/main/java/com/warped/data/local/inference/ModelImportManager.kt`) — imports GGUF/LiteRT-LM files from content URIs
- `MemoryChecker` (`app/src/main/java/com/warped/data/local/inference/MemoryChecker.kt`) — checks available RAM via `ActivityManager.MemoryInfo`, warns at 60%, blocks at 80% threshold
- `GgufMetadataParser` (`app/src/main/java/com/warped/data/local/inference/GgufMetadataParser.kt`) — parses GGUF binary header (magic, version, key-value metadata) extracting name, architecture, quantization, parameter count, context length
- `InputSanitizer` (`app/src/main/java/com/warped/data/local/inference/InputSanitizer.kt`) — NFC normalization, strips LaTeX delimiters, Unicode math blocks, control/surrogate/zero-width characters

## Testing
| Library | Version | Purpose |
|---------|---------|---------|
| junit5 | 5.11.4 | JUnit Jupiter — `@Nested`, `@ParameterizedTest` |
| mockk | 1.13.16 | Kotlin-first mocking (`coEvery`, relaxed mocks) |
| turbine | 1.1.0 | Kotlin Flow testing (`flow.test { awaitItem() }`) |
| truth | 1.4.4 | Google's fluent assertions (`assertThat(result).isEqualTo(expected)`) |
| kotlinx-coroutines-test | 1.9.0 | `runTest {}` for deterministic coroutine testing |
| room-testing | 2.7.1 | Room in-memory database testing |
| compose-ui-test | BOM | `ComposeTestRule`, `onNodeWithText()`, `performClick()` |
| androidJUnitRunner | (default) | Standard instrumentation test runner |

## Architecture Pattern

**Clean Architecture** with three layers:
- **data/** — Room DAOs/entities, Retrofit APIs, DTOs, repository implementations, providers
- **domain/** — Repository interfaces, domain models, `LlmProvider` interface
- **ui/** — Compose screens, ViewModels, UiState data classes, navigation

**MVVM** within UI layer. ViewModels expose `StateFlow<UiState>` to Compose. Repository pattern at domain/data boundary.

**Provider pattern** for LLM backends — `LlmProvider` interface (`app/src/main/java/com/warped/domain/provider/LlmProvider.kt`) implemented by: `OpenAIProvider`, `AnthropicProvider`, `OllamaProvider`, `LMStudioProvider`, `CustomProvider`, `LocalLlmProvider`, `LiteRTLmProvider`. Routed via `ProviderRouter`.

## ProGuard / R8

Release builds have `isMinifyEnabled = true` and `isShrinkResources = true`. Keep rules in `app/proguard-rules.pro` protect:
- Kotlinx Serialization (serializers, companions, `@Serializable` classes under `com.warped.**`)
- Room (`RoomDatabase` subclasses)
- LiteRT-LM (`com.google.ai.edge.litertlm.**`)

## Manifest Permissions & Features

Permissions declared in `app/src/main/AndroidManifest.xml`:
- `android.permission.INTERNET` — network access
- `android.permission.ACCESS_NETWORK_STATE` — connectivity checks
- `android.permission.FOREGROUND_SERVICE` — download notification
- `android.permission.FOREGROUND_SERVICE_DATA_SYNC` — API 34+ foreground service type
- `android.permission.POST_NOTIFICATIONS` — notification permission

Other features:
- `android:largeHeap="true"` — larger heap for model loading
- `android:allowBackup="false"` — no cloud backup (local-only data)
- Custom `networkSecurityConfig` — cleartext permitted (local network)
- Data extraction rules block cloud backup and device transfer
- WorkManager default initializer disabled (replaced by `HiltWorkerFactory`)
- `uses-native-library` for `libvndksupport.so` and `libOpenCL.so` (LiteRT-LM GPU backend)

## Linting & Formatting

- **Detekt:** Not yet configured (no `detekt.yml` found)
- **ktlint:** Not yet configured (no `.editorconfig` found)
- **StrictMode:** Enabled in debug builds only — detects network on main thread, slow calls, activity leaks, leaked closables, leaked SQLite objects

---

*Stack analysis: 2026-05-02*
