# Android LLM Client — Technology Stack

**Project**: Warped (LM Studio equivalent for Android)
**Date**: 2026-04-30
**Status**: Research complete — ready for roadmap

---

## 1. Core Platform

| Layer | Choice | Version | Rationale |
|-------|--------|---------|-----------|
| Language | Kotlin | 2.1.10 | Latest stable in 2.1.x line. Kotlin 2.2.0 exists (referenced in Dagger 2.57) but 2.1.10 has the widest tooling support. K2 compiler is default since 2.0.0, providing faster builds and better type inference. |
| Build SDK | compileSdk | 35 (Android 15) | Latest stable API level. API 36 (Android 16) is in preview but not required for any feature we need. |
| Min SDK | minSdk | 28 (Android 9) | Balances llama.cpp NDK compatibility (Android 9+ has stable NDK ABI) with market reach (~95% of devices). Llama.cpp needs arm64-v8a, which starts at API 21, but API 28 ensures EncryptedSharedPreferences and modern crypto. |
| UI Toolkit | Jetpack Compose | BOM 2025.04.00 | Declarative UI, first-class Android support, Material 3 integration. Latest stable BOM. Compose compiler is bundled in Kotlin 2.0+ as a compiler plugin — no separate Compose Compiler artifact needed. |
| Material Design | Material 3 | via Compose BOM | Current Android design standard. Material 2 is deprecated. |

**Key decisions**:
- `compileSdk = 35` with `targetSdk = 35` per Google Play requirements (2026).
- `minSdk = 28` — could go to 26, but 28 simplifies Keystore and WorkManager usage.
- Single-module initially, migrate to multi-module as complexity grows (YAGNI).

---

## 2. DI & Architecture

| Layer | Choice | Version | Rationale |
|-------|--------|---------|-----------|
| DI framework | Hilt (Dagger) | 2.59.2 | Industry-standard compile-time DI for Android. Integrates with ViewModel, WorkManager, Navigation. Dagger 2.59.x requires AGP 9+. |
| ViewModel | `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.8.x | Compose-native ViewModel integration. Lifecycle-aware, survives config changes. |
| Navigation | `androidx.navigation:navigation-compose` | 2.8.x | Type-safe navigation with Compose. Supports navigation graphs and argument passing. |
| Lifecycle | `androidx.lifecycle:lifecycle-runtime-compose` | 2.8.x | `collectAsStateWithLifecycle()` for lifecycle-aware Flow collection in Compose. |

**Architecture: Clean Architecture (3-layer)**

```
UI Layer (Compose screens + ViewModels)
    ↓ depends on
Domain Layer (use cases, repository interfaces, domain models)
    ↓ depends on
Data Layer (Room DAOs, Retrofit services, file system, llama.cpp JNI bridge)
```

- **MVVM** within UI layer. ViewModels expose `StateFlow<UiState>` to Compose.
- **Repository pattern** at domain/data boundary. Each data source gets a repository implementation.
- **Hilt modules** per feature, not per layer (avoids anemic module explosion).

**Why NOT Koin**: Koin is runtime DI, no compile-time validation. With Hilt, missing bindings fail at compile time. For a production app, this matters.

---

## 3. Local Storage

| Layer | Choice | Version | Rationale |
|-------|--------|---------|-----------|
| Structured data | Room | 2.7.x | Official Android recommended ORM. Compile-time SQL verification, Flow-based observation, Kotlin coroutines support. KSP-based annotation processing (replaced kapt in Room 2.6+). |
| Key-value preferences | DataStore | 1.1.x | Replaces SharedPreferences. Coroutine-based, type-safe, supports both Preferences and Proto DataStore. Use Preferences flavor (proto is overkill for simple settings). |
| GGUF model files | File system | `context.filesDir/models/` | Models are large binary blobs (1–20 GB). Store in app-private internal storage to prevent other apps from accessing. Use `context.getExternalFilesDir()` optionally for user-accessible storage. |
| Chat history | Room | (same) | Messages, conversations, and metadata stored in Room tables. Full-text search via Room FTS4 if needed later. |

**Schema**:
- `conversations` — id, title, created_at, updated_at, model_id, provider_type (local/remote)
- `messages` — id, conversation_id, role (user/assistant/system), content, token_count, created_at
- `models` — id, name, file_path, size_bytes, hugging_face_id, download_status, created_at
- `endpoints` — id, name, url, api_type (openai/ollama/lmstudio/custom), auth_type, created_at
- `presets` — id, name, temperature, top_p, top_k, repeat_penalty, max_tokens, context_size, seed, threads, created_at

---

## 4. Networking

| Layer | Choice | Version | Rationale |
|-------|--------|---------|-----------|
| HTTP client | OkHttp | 4.12.0 | Battle-tested HTTP client. Connection pooling, interceptors for auth/logging, HTTP/2 support. Custom DNS for local network discovery is easier with OkHttp. |
| REST client | Retrofit | 2.11.1 | Type-safe HTTP client on top of OkHttp. Declarative API with annotations. Kotlin suspend functions return `Response<T>` directly. |
| SSE streaming | OkHttp SSE + manual parsing | N/A | OpenAI-compatible streaming uses Server-Sent Events. Retrofit's `@Streaming` + OkHttp SSE support handles this. Alternative: manual `BufferedSource` parsing for finer control over token streaming. |
| JSON | Kotlinx Serialization | 1.7.x | Kotlin-native, no reflection, compile-time code generation. Lighter than Moshi and first-class Kotlin support. Retrofit converter available. |
| Local network | OkHttp + custom DNS | — | For discovering Ollama/LM Studio on LAN. Use mDNS (JmDNS library) or manual IP entry. OkHttp's `Dns` interface allows custom resolution. |

**SSE streaming approach**:
- Retrofit `@Streaming` with `ResponseBody` return type.
- Read chunks with `source.readUtf8Line()` in a `flow {}` builder.
- Parse `data: {"choices":[{"delta":{"content":"Hello"}}]}` lines.
- Emit tokens via `Flow<String>` to ViewModel → Compose UI.

**Why NOT Ktor**: Ktor is multiplatform-first but OkHttp + Retrofit is the Android standard with deeper platform integration (certificate pinning, proxy support, connection specs). This is an Android-only app — no need for multiplatform networking.

---

## 5. Background Work

| Layer | Choice | Version | Rationale |
|-------|--------|---------|-----------|
| Deferrable work | WorkManager | 2.10.x | Handles model downloads with pause/resume across process death. Supports constraints (WiFi-only, charging), chaining, and observation. |
| Async | Kotlin Coroutines | 1.9.x | Structured concurrency. `Dispatchers.IO` for network/file, `Dispatchers.Default` for CPU-bound work, `Dispatchers.Main` for UI. |
| Flows | Kotlinx Coroutines Flow | (included) | Cold streams for reactive data. `StateFlow` for UI state, `SharedFlow` for one-shot events. `flow {}` for SSE token streaming. |

**Coroutine dispatchers mapping**:
- `Dispatchers.Main` — Compose UI updates
- `Dispatchers.IO` — Network calls, file I/O, Room queries, GGUF downloads
- `Dispatchers.Default` — JSON parsing, token counting

**GGUF download strategy**:
- WorkManager `Worker` with `setForeground()` for download notifications.
- Use OkHttp interceptors to track byte progress.
- Support pause/resume via `Range` header (if server supports it) or chunked download with checkpointing.
- WorkManager handles retry with exponential backoff.

---

## 6. Local LLM Inference

| Layer | Choice | Version | Rationale |
|-------|--------|---------|-----------|
| Inference engine | llama.cpp | b8987+ (build tag) | Most mature GGUF inference engine. Releases Android arm64 CPU binaries on every commit. Supports Samsung Hexagon NPU acceleration. 108k GitHub stars, very active community. |
| NDK integration | Custom JNI bridge | NDK 27+ | Write a thin C++ JNI wrapper around `libllama`. Expose functions: `llama_load_model`, `llama_generate`, `llama_tokenize`, `llama_free`. Compile via CMake in `app/src/main/cpp/`. |
| Build system for native | CMake + Gradle | — | `externalNativeBuild { cmake { path "src/main/cpp/CMakeLists.txt" } }` in `build.gradle.kts`. Cross-compile llama.cpp for `arm64-v8a` and `x86_64` (emulator). |
| Token streaming | Native callback → Kotlin Flow | — | JNI calls back into Kotlin via a listener interface on each generated token. Kotlin side wraps this in a `callbackFlow {}` for structured concurrency. |

**ABI targets**:
- `arm64-v8a` — Required. All modern Android phones are arm64.
- `x86_64` — Optional. Only needed for emulator testing. Skip for release builds.

**JNI bridge design**:
```
Kotlin (LlamaEngine.kt)
  → external fun nativeLoad(path: String): Long  // returns model pointer
  → external fun nativeGenerate(modelPtr: Long, prompt: String, params: LlamaParams): Unit
  → external fun nativeFree(modelPtr: Long): Unit

JNI (llama_jni.cpp)
  → llama_load_model_from_file()
  → llama_generate() with token callback
  → llama_free_model()

CMakeLists.txt
  → FetchContent or git submodule to pull llama.cpp
  → add_library(llama SHARED ...)
  → target_link_libraries(llama_jni llama)
```

**Backend selection** (llama.cpp supports multiple GPU backends):
- **CPU** — Default. Works on all devices. With `-p thread_count` config.
- **Vulkan** — Android's GPU compute API. Faster on flagship phones with good GPUs. Requires `GGML_VULKAN=ON` in CMake build. Check device Vulkan support at runtime.
- **Hexagon NPU** — Snapdragon 8 Gen 3+ devices. Experimental but emerging. Defer until stable.

**Initial recommendation: CPU-only**. Vulkan adds build complexity and runtime compatibility checks. Add as opt-in feature later.

**Why NOT MLC-LLM**: Good project but smaller ecosystem and fewer GGUF models available. llama.cpp has the widest model support and is the reference implementation.
**Why NOT ExecuTorch**: Android support is nascent (2025-2026). Good for on-device training, not mature enough for inference-focused app.
**Why NOT pre-built .so**: Pre-built binaries from llama.cpp releases may not match your NDK version/ABI. Building from source with the same NDK guarantees ABI compatibility.

---

## 7. Hugging Face Integration

| Layer | Choice | Version | Rationale |
|-------|--------|---------|-----------|
| API client | Retrofit (custom) | — | Hugging Face Hub API is a REST API at `https://huggingface.co/api/`. No official Android SDK. Custom Retrofit interface. |
| Model search | Hugging Face Hub API | v1 | `GET /api/models?search=llama&filter=gguf&sort=downloads` returns JSON with model metadata. |
| File listing | Hugging Face Hub API | v1 | `GET /api/models/{model_id}` → get `siblings` field for file list. Filter by `.gguf` extension. |
| GGUF download | OkHttp | — | Direct download from `https://huggingface.co/{model_id}/resolve/main/{filename}`. Requires `Authorization: Bearer {token}` for gated models (though most GGUF models are public). |
| Download management | Custom DownloadManager | — | Android's `DownloadManager` doesn't support custom headers well. Build a custom downloader with OkHttp + WorkManager for reliability. |

**Hugging Face API endpoints used**:
- `GET /api/models` — Search/list models
- `GET /api/models/{model_id}` — Model details, file list
- `GET /{model_id}/resolve/main/{filename}` — Download file (redirect)

**Authentication**: HF tokens are needed only for gated models. Store in EncryptedSharedPreferences. Most GGUF models (TheBloke, bartowski, etc.) are public.

**Why NOT Hugging Face Inference API**: We want local inference, not hosted. The Hub API is for model discovery and download only.

---

## 8. Security

| Layer | Choice | Version | Rationale |
|-------|--------|---------|-----------|
| API key storage | EncryptedSharedPreferences | via `androidx.security:security-crypto:1.1.0` | AES-256 encryption backed by Android Keystore. Survives app restart. Keys never leave Keystore hardware (TEE/StrongBox). |
| Keystore | Android Keystore | (platform) | Hardware-backed key storage. `KeyGenParameterSpec` with `PURPOSE_ENCRYPT \| PURPOSE_DECRYPT`. |
| Network security | Certificate pinning + cleartext blocking | — | OkHttp `CertificatePinner` for remote endpoints. `network_security_config.xml` blocks cleartext in production. Allow cleartext for local network (Ollama/LM Studio on LAN often use HTTP). |
| No secrets in code | BuildConfig fields | — | No hardcoded keys. Everything comes from user input or EncryptedSharedPreferences. |
| ProGuard/R8 | Enabled for release | — | Obfuscation, shrinking, optimization. Protects JNI symbols with keep rules. |

**Why NOT Tink**: EncryptedSharedPreferences covers our use case (key-value secret storage). Tink adds complexity without benefit here.

---

## 9. Testing

| Layer | Choice | Version | Rationale |
|-------|--------|---------|-----------|
| Unit testing | JUnit 5 | 5.11.x | Latest JUnit. `@ParameterizedTest`, `@Nested` test classes for organized suites. |
| Assertions | Truth | 1.4.x | Google's fluent assertion library. Better failure messages than JUnit assertions. `assertThat(result).isEqualTo(expected)`. |
| Mocking | MockK | 1.13.x | Kotlin-first mocking. Supports coroutines (`coEvery`), `mockkStatic`, relaxed mocks. |
| Coroutine testing | `kotlinx-coroutines-test` | 1.9.x | `runTest {}` for deterministic coroutine testing. `TestDispatcher` for controlling time. |
| Flow testing | Turbine | 1.1.x | Testing library for Kotlin Flows. `flow.test { awaitItem() }` pattern. |
| Compose UI testing | `compose.ui:ui-test-junit4` | via Compose BOM | `ComposeTestRule`, `onNodeWithText()`, `performClick()`. Screenshot testing not needed initially. |
| Room testing | Room in-memory | via Room | `Room.inMemoryDatabaseBuilder()` for fast DAO tests without a real database. |
| Test runner | AndroidJUnitRunner | — | Standard Android instrumentation test runner. |

**Testing pyramid**:
1. Unit tests — ViewModels, use cases, repositories (mocked data sources), JSON parsing, tokenization
2. Integration tests — Room DAOs, Retrofit with MockWebServer, JNI bridge with a tiny GGUF
3. UI tests — Critical flows (chat send/receive, model download, endpoint setup)

**Why NOT Mockito**: MockK supports `suspend` functions natively, Kotlin objects, and extension functions. Mockito has awkward Kotlin syntax.

---

## 10. Build Tooling

| Layer | Choice | Version | Rationale |
|-------|--------|---------|-----------|
| Build system | Gradle | 8.13+ (with AGP 9) | Kotlin DSL (`build.gradle.kts`). AGP 9 requires Gradle 9.1+ per Dagger 2.59 release notes. |
| Android Gradle Plugin | AGP | 9.0.x | Latest AGP major version. Required by Hilt 2.59.x. Brings configuration cache improvements and build optimizations. |
| Dependency management | Version catalog | `libs.versions.toml` | Centralized version management. Gradle's native `[versions]`, `[libraries]`, `[plugins]` sections. Auto-suggested by Android Studio. |
| NDK | Android NDK | 27.x | For cross-compiling llama.cpp C++ code. Required for JNI bridge. |
| CMake | CMake | 3.22+ (NDK bundled) | Build system for native C++ code (llama.cpp + JNI wrapper). |
| KSP | Kotlin Symbol Processing | 2.1.10-1.0.x | Replaces kapt for Room, Hilt, Kotlinx Serialization annotation processing. Faster incremental builds than kapt. |
| Linting | Detekt | 1.23.x | Static analysis for Kotlin. Configuration via `detekt.yml`. Run on CI. |
| Code formatting | ktlint | 1.5.x | Kotlin linter and formatter. `ktlintFormat` Gradle task. |

**gradle/libs.versions.toml structure**:
```toml
[versions]
kotlin = "2.1.10"
agp = "9.0.0"
compose-bom = "2025.04.00"
hilt = "2.59.2"
room = "2.7.1"
okhttp = "4.12.0"
retrofit = "2.11.1"
coroutines = "1.9.0"
lifecycle = "2.8.7"
navigation = "2.8.8"
workmanager = "2.10.1"
datastore = "1.1.3"
kotlinx-serialization = "1.7.3"
ksp = "2.1.10-1.0.31"
mockk = "1.13.16"
turbine = "1.1.0"
truth = "1.4.4"
detekt = "1.23.8"
ktlint = "1.5.0"

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version = "1.2.0" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-kotlinx-serialization = { group = "com.squareup.retrofit2", name = "retrofit-converter-kotlinx-serialization", version.ref = "retrofit" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
workmanager = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workmanager" }
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
security-crypto = { group = "androidx.security", name = "security-crypto", version = "1.1.0" }
# Testing
junit5 = { group = "org.junit.jupiter", name = "junit-jupiter", version = "5.11.4" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
truth = { group = "com.google.truth", name = "truth", version.ref = "truth" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
compose-ui-test = { group = "androidx.compose.ui", name = "ui-test-junit4" }
room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }

[plugins]
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

**Single-module Gradle setup** (`app/build.gradle.kts`):
```kotlin
plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.warped"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        targetSdk = 35
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    externalNativeBuild {
        cmake { path("src/main/cpp/CMakeLists.txt") }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    // ... all deps from version catalog
}
```

---

## 11. What NOT to Use and Why

| Technology | Why NOT | What to Use Instead |
|-----------|---------|-------------------|
| **Flutter / React Native** | Cross-platform frameworks add a JNI bridge layer that complicates llama.cpp integration. NDK/C++ interop is straightforward in native Android but fragile through Flutter's FFI or RN's native modules. | Kotlin + Compose (native) |
| **Java** (language) | Kotlin is the official Android language since 2019. Java lacks coroutines, null safety, data classes, and extension functions — all critical for clean architecture on Android. | Kotlin 2.1.10 |
| **Koin** (DI) | Runtime DI. Missing bindings fail at runtime, not compile time. Good for small projects; risky for production apps with complex dependency graphs. | Hilt (Dagger) |
| **Ktor** (HTTP client) | Multiplatform-first, but OkHttp is the Android standard with deeper OS integration (platform trust store, proxy detection, DNS caching). | OkHttp + Retrofit |
| **MLC-LLM** | Smaller model ecosystem, fewer GGUF models. llama.cpp is the reference implementation with widest model support. | llama.cpp |
| **ExecuTorch** | Android support is nascent. Good for on-device training, but inference maturity lags llama.cpp by years. | llama.cpp |
| **Firebase / cloud sync** | Adds vendor lock-in. Chat history is local-only per requirements. No cloud sync in scope. | Room + local file system |
| **Google ML Kit** | Focused on vision/text recognition, not LLM inference. Not designed for GGUF or transformer models. | llama.cpp |
| **libtorch / PyTorch Mobile** | Bulky (>50MB just for the framework), slower inference for LLMs vs purpose-built llama.cpp. | llama.cpp |
| **Moshi** (JSON) | Requires reflection or kapt code generation. Kotlinx Serialization is Kotlin-native with KSP support, no reflection. | Kotlinx Serialization |
| **SharedPreferences** | Synchronous API, no type safety, no coroutine support. Deprecated by Google in favor of DataStore. | DataStore (Preferences) |
| **LiveData** | Java-era observable. No support for Kotlin Flows, operators (`map`, `filter`) harder to compose. Google has moved to Flow for new architecture guidance. | StateFlow + Flow |
| **KAPT** (annotation processing) | Slower than KSP. Room, Hilt, and Kotlinx Serialization all support KSP. kapt is in maintenance mode. | KSP |
| **Groovy Gradle** | Not type-safe, no IDE completion. Kotlin DSL is the Gradle standard since Gradle 5.0. | Gradle Kotlin DSL |
| **Pre-built llama.cpp .so** | Pre-built binaries may be compiled with a different NDK version, STL, or ABI than your project. Causes cryptic linker errors at runtime. | Build from source with CMake + NDK |
| **ChatGPT app / closed-source LLM wrappers** | Not relevant — Warped is an LM Studio equivalent: user brings their own models and API keys. No vendor lock-in. | N/A |

---

## 12. Confidence Levels

| Section | Confidence | Notes |
|---------|-----------|-------|
| Core Platform (Kotlin, Compose, SDK) | **HIGH** | Kotlin 2.1.10 confirmed via Dagger 2.59 release notes. Compose BOM version estimated from release cadence — verify against [Google Maven](https://maven.google.com) before freezing dependencies. |
| DI & Architecture (Hilt) | **HIGH** | Dagger 2.59.2 confirmed via GitHub releases (Feb 2026). Hilt integration with Compose and ViewModel is mature. |
| Local Storage (Room, DataStore) | **HIGH** | Room and DataStore are stable AndroidX libraries. Exact patch versions will resolve via BOM or latest stable. |
| Networking (OkHttp, Retrofit) | **HIGH** | OkHttp 4.12.0 and Retrofit 2.11.x are long-term stable releases. No breaking changes expected. |
| Background Work (WorkManager, Coroutines) | **HIGH** | WorkManager is mature AndroidX library. Coroutines 1.9.x confirmed via Kotlin release cycle. |
| Local LLM Inference (llama.cpp, JNI) | **HIGH** | llama.cpp Android arm64 builds confirmed active (every release includes them). JNI bridge approach is proven (ChatGPT Android app by llama.cpp community uses same pattern). Build from source with CMake is standard. |
| Hugging Face Integration | **HIGH** | HF Hub API is stable REST. Custom Retrofit client is standard. No official SDK needed. |
| Security (Keystore, EncryptedSharedPreferences) | **HIGH** | `security-crypto:1.1.0` is stable. Keystore is platform API. |
| Testing (JUnit 5, MockK, Turbine, Truth) | **HIGH** | All are mature, stable libraries with active maintenance. |
| Build Tooling (Gradle, AGP, version catalog) | **MEDIUM** | AGP 9 is required by Hilt 2.59.x — confirmed. Gradle version 9.1+ inferred from AGP 9 requirements. Verify exact Gradle version against [AGP compatibility table](https://developer.android.com/build/releases/gradle-plugin#compatibility). |
| KSP version | **MEDIUM** | Version `2.1.10-1.0.31` inferred from Dagger 2.56 release notes. Verify against [KSP releases](https://github.com/google/ksp/releases). |
| Material 3 / Compose BOM | **MEDIUM** | BOM version estimated. Verify latest stable at [Compose BOM mapping](https://developer.android.com/jetpack/compose/bom/bom-mapping). |

---

## Appendix: llama.cpp Android Integration Quick Reference

### CMake integration structure:
```
app/src/main/cpp/
├── CMakeLists.txt          # CMake build for llama + JNI wrapper
├── llama_jni.cpp           # JNI bridge functions
├── llama/                  # llama.cpp source (git submodule or FetchContent)
│   ├── llama.h
│   ├── llama.cpp
│   ├── ggml/
│   └── ...
└── utils/
    └── callback.h          # Token callback infrastructure
```

### Key JNI functions to expose:
```cpp
// C++ side
extern "C" JNIEXPORT jlong JNICALL
Java_com_warped_llama_LlamaEngine_nativeLoadModel(
    JNIEnv* env, jobject, jstring path, jobject params);

extern "C" JNIEXPORT void JNICALL
Java_com_warped_llama_LlamaEngine_nativeGenerate(
    JNIEnv* env, jobject, jlong modelPtr, jstring prompt, jobject callback);

extern "C" JNIEXPORT void JNICALL
Java_com_warped_llama_LlamaEngine_nativeFree(
    JNIEnv* env, jobject, jlong modelPtr);
```

### Android.mk alternatives:
- Google recommends CMake for new projects. `Android.mk` (ndk-build) is legacy.
- CMake integrates better with Android Studio (native debugging, symbol resolution).

### llama.cpp build flags for Android:
```cmake
set(CMAKE_CXX_STANDARD 17)
set(GGML_OPENMP OFF)      # Not available on Android
set(GGML_VULKAN OFF)       # Enable later for GPU acceleration
set(GGML_NATIVE OFF)       # Don't use -march=native (cross-compiling)
set(GGML_CPU_ARM_ARCH "armv8-a")
```

### Runtime considerations:
- Model loading is memory-intensive. A 7B Q4_K_M GGUF needs ~4-5 GB RAM.
- Use `android:largeHeap="true"` in AndroidManifest.
- Check available memory before loading: `ActivityManager.MemoryInfo`.
- Handle `OutOfMemoryError` gracefully — suggest a smaller model quantization.
- Use `android:extractNativeLibs="false"` in AndroidManifest to save APK size (libs stay compressed in APK, extracted at install time).

---

*Stack document ready. Proceed to roadmap creation with this as input.*
