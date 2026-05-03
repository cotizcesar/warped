# Concerns & Technical Debt

**Analysis Date:** 2026-05-02

## Critical Issues

### llama.cpp Native Engine Is a Stub (Core Feature Non-Functional)

**Issue:** The entire `jni_bridge.cpp` (`app/src/main/cpp/jni_bridge.cpp`) and `jni_bridge.h` (`app/src/main/cpp/jni_bridge.h`) contain only placeholder/stub implementations. All actual llama.cpp model loading, inference, and token generation code is commented out (lines 23-39 and 50-55). The `generate()` method returns a hardcoded placeholder message: "This is a placeholder response. Real llama.cpp inference requires the native library to be compiled from source (see build instructions)."

**Files:** `app/src/main/cpp/jni_bridge.cpp` (lines 23-39, 50-57), `app/src/main/cpp/CMakeLists.txt` (builds only the JNI wrapper, not llama.cpp itself)

**Impact:** The core value proposition — running LLMs locally via llama.cpp — is entirely non-functional. Users can only use remote providers. This is the highest-priority gap.

**Fix approach:** Integrate the actual `libllama` source into the CMake build. Clone `llama.cpp` as a submodule or dependency, compile the `libllama` static library, and link it against the JNI wrapper. Uncomment and wire the real `llama_load_model_from_file`, `llama_new_context_with_model`, `llama_decode`, `llama_sample_*` functions.

---

### API Key Not Transmitted to OpenAI/Ollama/Custom Providers (Auth Bypass)

**Issue:** `OpenAIProvider` (`app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt`), `OllamaProvider` (`app/src/main/java/com/warped/data/remote/provider/OllamaProvider.kt`), and `CustomProvider` (`app/src/main/java/com/warped/data/remote/provider/CustomProvider.kt`) each create their own Retrofit instance directly — they do NOT pass the global `OkHttpClient` from `NetworkModule` (`app/src/main/java/com/warped/di/NetworkModule.kt`) which contains the `AuthInterceptor` (`app/src/main/java/com/warped/data/remote/network/AuthInterceptor.kt`). The `AuthInterceptor` is the mechanism that attaches `Authorization: Bearer {apiKey}` to outgoing requests.

**Files:** `app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt` (lines 29-31), `app/src/main/java/com/warped/data/remote/provider/OllamaProvider.kt` (lines 28-31), `app/src/main/java/com/warped/data/remote/provider/CustomProvider.kt` (lines 30-33), `app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt` (lines 19-49 — a comment on line 26-29 acknowledges this problem for OpenAI)

**Impact:** API keys stored securely by the user are never sent with requests to OpenAI/Ollama/Custom endpoints. Chat silently fails or returns 401. The `ProviderRouter` comment explicitly notes this is a known issue.

**Fix approach:** Pass the global `OkHttpClient` (or a configured copy with `AuthInterceptor`) from `ProviderRouter` to each provider's constructor. Alternatively, have each provider accept an `OkHttpClient` via constructor injection rather than creating their own.

---

### Cleartext HTTP Permitted Globally

**Issue:** `network_security_config.xml` (`app/src/main/res/xml/network_security_config.xml`) has `<base-config cleartextTrafficPermitted="true">`. This allows cleartext HTTP to any domain, bypassing Android's TLS enforcement.

**Files:** `app/src/main/res/xml/network_security_config.xml` (line 3)

**Impact:** All network traffic can be intercepted unencrypted. While cleartext is necessary for local LAN endpoints (Ollama at `http://localhost:11434`, LM Studio at `http://localhost:1234`), the current config permits it globally rather than scoping to local addresses only.

**Fix approach:** Change `<base-config cleartextTrafficPermitted="false">` and add a `<domain-config cleartextTrafficPermitted="true">` block scoped only to `localhost`, `127.0.0.1`, and private LAN ranges (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`).

---

### No Certificate Pinning

**Issue:** Neither the `NetworkModule` nor any provider implements OkHttp `CertificatePinner` for remote API endpoints. ProGuard rules (`app/proguard-rules.pro`) contain no pinning configuration.

**Files:** `app/src/main/java/com/warped/di/NetworkModule.kt`, `app/proguard-rules.pro`

**Impact:** Susceptible to MITM attacks on untrusted networks. API keys and chat content could be intercepted.

**Fix approach:** Add `CertificatePinner` to the OkHttpClient builder in `NetworkModule` for known remote hosts (api.openai.com, api.anthropic.com, huggingface.co, ollama.com). Use backup pins.

---

### Zero Test Coverage — No Tests Exist

**Issue:** Not a single test file exists in the entire project — no `app/src/test/` or `app/src/androidTest/` directories with any `.kt` files.

**Files:** `app/src/test/` (empty), `app/src/androidTest/` (empty) — verified via glob

**Impact:** Every change risks regression. The chat flow, SSE parsing, API key handling, GGUF metadata parsing, model download checkpointing, and inference engine lifecycle — all untested. A crash in any of these paths could lose user data or expose secrets.

**Fix approach:** Prioritize tests for: (1) `AuthInterceptor` and API key flow, (2) `SseParser` edge cases, (3) `GgufMetadataParser` malformed file handling, (4) `ChatViewModel` state transitions, (5) `EngineManager` lifecycle (load/unload/switch), (6) `ModelDownloadWorker` checkpoint/resume logic.

---

## High Priority

### Silent Exception Swallowing (20+ Instances)

**Issue:** Many `catch (_: Exception) {}` blocks silently discard errors without logging or surfacing to the user. Critical paths affected:

- `ChatViewModel.kt` lines 373, 379, 384, 405, 459 — unloading models silently fails
- `ActiveModelSelection.kt` lines 37, 42, 47, 71, 77 — persistence failures silently swallowed
- `LMStudioProvider.kt` lines 119, 142 — malformed SSE events silently skipped
- `SseExtensions.kt` lines 35, 64 — malformed JSON silently skipped
- `EntityMappers.kt` line 22 — JSON deserialization failures silently return empty list

**Impact:** Bugs in error-recovery paths are invisible. Users may experience data loss (active model selection resetting) or silent feature degradation (chat tokens dropped) with no indicators.

**Fix approach:** Replace empty catch blocks with `Timber.w(e, "context message")` at minimum. For persistence failures in `ActiveModelSelection`, surface via a `SharedFlow<ErrorEvent>` that ViewModels can observe.

---

### Force-Unwrap (!!) on Potentially Null Values (11 Instances)

**Issue:** 11 instances of `!!` in production code, some on values that can legitimately be null:

- `EngineManager.kt` line 70: `target.backend!!` — If `probeBackend()` throws, `backend` IS null
- `BackendDetector.kt` line 35: `cachedBackend!!` — Can be null if probe fails with exception
- `ChatViewModel.kt` lines 130, 181: `state.selectedModelId!!` — Can be null if user hasn't selected
- `ChatScreen.kt` lines 71, 305: `uiState.memoryWarningModel!!`, `uiState.activeBackend!!.name`
- `ModelsScreen.kt` lines 57, 68: `showMemoryWarning!!`
- `HuggingFaceScreen.kt` lines 78, 89: `uiState.selectedModel!!`
- `PresetsScreen.kt` line 202: `uiState.formatWarningPreset!!`

**Impact:** Potential `NullPointerException` crashes in production, especially under race conditions or error states.

**Fix approach:** Replace with `?: return` or `?.let {}` patterns. For `EngineManager` line 70, specifically: `val backend = target.backend ?: BackendType.CPU` as fallback.

---

### LMStudioProvider Hardcoded Default URL

**Issue:** `LMStudioProvider` (`app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt`) hardcodes `baseUrl: String = "http://localhost:1234"` (line 26). While `ProviderRouter` (line 39-42) always passes the endpoint URL, this default is misleading and could mask configuration errors.

**Files:** `app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt` line 26

**Impact:** If a caller ever constructs `LMStudioProvider` without the base URL, it silently defaults to `localhost:1234` — this is a bug waiting to happen if constructor injection is added later.

**Fix approach:** Remove the default value. Make `baseUrl` a required parameter.

---

### ModelsPlaceholderScreen Still Exists

**Issue:** `ModelsPlaceholderScreen.kt` (`app/src/main/java/com/warped/ui/models/ModelsPlaceholderScreen.kt`) is a stub displaying "Models coming in Phase 2". Meanwhile, `ModelsScreen.kt` exists and appears functional. The placeholder screen should be removed or reference updated.

**Files:** `app/src/main/java/com/warped/ui/models/ModelsPlaceholderScreen.kt`, `app/src/main/java/com/warped/ui/navigation/NavGraph.kt` (check if still referenced)

**Impact:** Dead code. If any navigation path still routes to this screen, users see a placeholder instead of the real models interface.

---

## Medium Priority

### ChatViewModel Is a God Class (550 Lines)

**Issue:** `ChatViewModel.kt` is 550 lines and handles: model selection, model preloading, engine lifecycle (load/unload/reload), memory warning management, chat streaming/token buffering, conversation management, image encoding, reasoning-think-block parsing, endpoint resolution, and generation parameter management.

**Files:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` (550 lines)

**Impact:** High complexity, difficult to test (no tests exist), prone to state management bugs. The model loading logic (`setSelectedModel`, `preloadLocalModel`, `unloadLocalModels`) could be its own `ModelLoadManager` collaborator.

**Fix approach:** Extract: (1) `ModelLoadCoordinator` — handles engine lifecycle, (2) `TokenStreamProcessor` — handles buffering/throttling/think-block parsing, (3) `ImageEncoder` — handles uriToBase64 conversion.

---

### Duplicated `isLiteRtLm()` Check Logic

**Issue:** The same "is this model a LiteRT-LM model?" logic is duplicated across `ChatViewModel.kt` (lines 466, 488, 495-496), `ModelsViewModel.kt` (lines 92-93), and `ChatScreen.kt` (lines 178-179). Each reimplements `modelFormat.equals("LITERTLM", ignoreCase = true) || filePath.endsWith(".litertlm", ignoreCase = true)`.

**Files:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`, `app/src/main/java/com/warped/ui/models/ModelsViewModel.kt`, `app/src/main/java/com/warped/ui/chat/ChatScreen.kt`

**Impact:** If the LiteRT-LM detection logic changes (e.g., new file extension), it must be updated in 3+ places. Already a bug risk.

**Fix approach:** Move to `LocalModel.isLiteRtLm()` extension function in one place (domain layer) and reuse everywhere. This appears to already partially exist in `ChatViewModel.kt` line 495 — promote to `LocalModel.kt`.

---

### Remote Providers Create Own OkHttpClient Without Timeouts/Pooling

**Issue:** `OllamaProvider`, `OpenAIProvider`, `CustomProvider` create Retrofit instances without configuring an OkHttpClient at all — they use Retrofit's default client, which has default timeouts and no connection pooling. `AnthropicProvider` creates its own OkHttpClient but doesn't use connection pooling. `LMStudioProvider` creates its own but has timeouts configured.

**Files:** `app/src/main/java/com/warped/data/remote/provider/OllamaProvider.kt` (lines 28-30), `OpenAIProvider.kt` (lines 29-31), `CustomProvider.kt` (lines 30-33), `AnthropicProvider.kt` (lines 30-44), `LMStudioProvider.kt` (lines 32-35)

**Impact:** Slow connection setup on each request, no connection reuse, potential hangs on default timeouts. Memory inefficient for frequent streaming connections.

**Fix approach:** All providers should accept a pre-configured OkHttpClient via constructor (singleton from NetworkModule). This also fixes the AuthInterceptor gap (Critical Issue #2).

---

### SseParser Has No Maximum Buffer Size

**Issue:** `SseParser` (`app/src/main/java/com/warped/data/remote/network/SseParser.kt`) uses a `StringBuilder` buffer that grows without bound. If an SSE stream sends data without `\n\n` delimiters (malformed or slow stream), the buffer could consume arbitrary memory.

**Files:** `app/src/main/java/com/warped/data/remote/network/SseParser.kt` (lines 4, 7)

**Impact:** Potential OOM on malformed SSE streams, especially on memory-constrained devices (which is common given large models already consume most RAM).

**Fix approach:** Add a maximum buffer size (e.g., 1MB). If exceeded, flush the buffer and emit an error token rather than accumulating indefinitely.

---

### AnthropicProvider testConnection Sends a Real Request

**Issue:** `AnthropicProvider.testConnection()` (`app/src/main/java/com/warped/data/remote/provider/AnthropicProvider.kt` lines 91-109) sends a real chat completion request with `maxTokens = 1` and `"Hi"`. This consumes API quota on every connection test.

**Files:** `app/src/main/java/com/warped/data/remote/provider/AnthropicProvider.kt` (lines 93-99)

**Impact:** Small but real API cost. Other providers use `listModels()` for connection testing (which is cheaper/more appropriate).

**Fix approach:** Use a dedicated lightweight endpoint or a HEAD/GET request to the base URL. Anthropic doesn't have a `listModels` endpoint, so use `okhttp3` directly for a connectivity check.

---

### Missing ProGuard Rules for JNI/Native Functions

**Issue:** `proguard-rules.pro` (`app/proguard-rules.pro`) contains keep rules only for Kotlinx Serialization (lines 1-8), Room (lines 10-12), and LiteRT-LM (line 15). No rules for the llama.cpp JNI bridge native functions declared in `LlamaEngine.kt`.

**Files:** `app/proguard-rules.pro`, `app/src/main/java/com/warped/data/local/inference/LlamaEngine.kt` (lines 18-23)

**Impact:** R8 may rename or strip the native method declarations in `LlamaEngine`, causing `UnsatisfiedLinkError` at runtime in release builds.

**Fix approach:** Add keep rules:
```
-keepclasseswithmembernames class com.warped.data.local.inference.LlamaEngine {
    native <methods>;
}
-keep class com.warped.data.local.inference.LlamaEngine$TokenCallback { *; }
```

---

### HttpLoggingInterceptor at BODY Level in HttpClientFactory

**Issue:** `HttpClientFactory` (`app/src/main/java/com/warped/data/remote/network/HttpClientFactory.kt` line 16) uses `HttpLoggingInterceptor.Level.BODY` — this logs full request and response bodies, including API keys in `Authorization` headers. While `WarpedApplication.RedactingTree` (`app/src/main/java/com/warped/WarpedApplication.kt` lines 91-99) redacts Bearer tokens, it only does so in debug builds (line 35-36). The `HttpClientFactory` doesn't gate logging on `BuildConfig.DEBUG`.

**Files:** `app/src/main/java/com/warped/data/remote/network/HttpClientFactory.kt` line 16, `app/src/main/java/com/warped/WarpedApplication.kt` lines 91-99

**Impact:** If `HttpClientFactory` is ever used in a release build (currently it appears to be a standalone factory for non-Hilt providers), full request bodies are logged. If logcat output is captured by a malicious app with `READ_LOGS` permission, API keys leak.

**Fix approach:** Either gate on `BuildConfig.DEBUG` or use `Level.HEADERS` (which the main `NetworkModule` already uses at line 23) and ensure `RedactingTree` covers body logging too.

---

## Low Priority

### @Suppress("DEPRECATION") Silencing Instead of Fixing

**Issue:** Two instances of `@Suppress("DEPRECATION")` to silence compile warnings:
- `WarpedApplication.kt` line 81: `ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL` — deprecated on API 35+
- `EngineManager.kt` line 136: Same issue — `TRIM_MEMORY_RUNNING_CRITICAL`

**Files:** `app/src/main/java/com/warped/WarpedApplication.kt` line 81, `app/src/main/java/com/warped/data/local/inference/EngineManager.kt` line 136

**Impact:** Low — current API 35 behavior is identical. But these compile warnings exist for a reason and the deprecation-replacement (Android 15+ memory pressure model) should be adopted before these APIs are removed.

**Fix approach:** Use the new `ApplicationExitInfo` and `onTrimMemory` with `TRIM_MEMORY_COMPLETE` for modern API levels, keeping the old constant for `minSdk = 28` via `Build.VERSION.SDK_INT` checks.

---

### ModelDownloadManager Uses LiveData (Legacy Pattern)

**Issue:** `ModelDownloadManager` (`app/src/main/java/com/warped/data/local/download/ModelDownloadManager.kt` lines 59, 240-301) uses `LiveData` with `observeForever` for WorkManager progress observation, while the rest of the codebase uses Kotlin `Flow` and `StateFlow`. This requires manual observer cleanup via `cleanupObserver()`.

**Files:** `app/src/main/java/com/warped/data/local/download/ModelDownloadManager.kt`

**Impact:** Low — functionally correct, but inconsistent with codebase patterns and requires manual lifecycle management. Risk of observer leaks if `cleanupObserver` is missed.

**Fix approach:** Convert to `WorkManager.getWorkInfoByIdFlow(workId)` (available since WorkManager 2.8+) for a coroutine-native Flow-based API.

---

### Missing KDoc on Public API

**Issue:** Almost no KDoc documentation on any public class, function, or property. Notable exceptions: `EngineManager.kt` (has some KDoc on `switchToLiteRT`, `switchToLlama`, `unloadCurrent`, `createLiteRTConversation`), `MemoryChecker.kt` (has KDoc on `canLoadLitertlmModel`), `LiteRTLmEngine.kt` (has some KDoc). The remaining 120+ files have zero KDoc.

**Impact:** Higher onboarding cost. No documentation for `ChatViewModel`, `ModelDownloadWorker`, `AuthInterceptor`, `SseParser`, or any of the provider classes.

**Fix approach:** Add KDoc incrementally, starting with public API surfaces: `LlmProvider` interface, `ChatRepository`, `ModelDownloadManager`, and all API interfaces.

---

### Anemic Service Layer (Repository Pattern Bypassed for Providers)

**Issue:** `ProviderRouter` (`app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt`) constructs provider instances directly rather than going through a repository or service layer. The `ChatViewModel` then calls `provider.chat()` directly (line 198 in `ChatViewModel.kt`). This creates a tight coupling between the UI layer and network providers.

**Impact:** Harder to test. Provider construction logic lives in the "router" rather than being injected. Adding a new provider requires changes to `ProviderRouter`, not just DI modules.

**Fix approach:** All providers should be injectable (constructor-inject OkHttpClient, baseUrl, modelId, apiKey). Move provider resolution to a Hilt module with `@IntoMap` multi-binding keyed by `ProviderType`.

---

## Security Audit

### API Key Storage
**Status: GOOD.** `KeystoreManager` (`app/src/main/java/com/warped/data/local/security/KeystoreManager.kt`) uses `EncryptedSharedPreferences` with AES-256-GCM backed by Android Keystore hardware (TEE/StrongBox). `ApiKeyStore` (`app/src/main/java/com/warped/data/local/security/ApiKeyStore.kt`) uses `CharArray` and zeroes out the array after use (`apiKey.fill('0')` — lines 13, 24). The `AuthInterceptor` similarly zeroes `CharArrays` after use (line 24). **Concern:** the `storeKey` method converts `CharArray` to `String` for storage (line 12), which creates an immutable String in memory that can't be zeroed.

### Network Security Configuration
**Status: NEEDS FIX.** `network_security_config.xml` permits cleartext globally (see Critical Issue #3). Should be scoped to `localhost` and private LAN ranges only.

### Certificate Pinning
**Status: MISSING.** No `CertificatePinner` configured (see Critical Issue #4).

### ProGuard/R8 Rules
**Status: INCOMPLETE.** Missing keep rules for llama.cpp JNI native methods (see Medium Priority issue). Missing keep rules for `TokenCallback` interface (used for JNI callbacks). Missing `-keepattributes Signature` for Retrofit type tokens.

### Permissions Review
**Status: APPROPRIATE.** `AndroidManifest.xml` declares: `INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`. All justified: INTERNET for network/API calls, FOREGROUND_SERVICE for model downloads, POST_NOTIFICATIONS for download progress. No over-permissions detected. `android:allowBackup="false"` (line 11) — correct for an app storing sensitive API keys.

### Logging Security
**Status: PARTIALLY MITIGATED.** `WarpedApplication.RedactingTree` (lines 91-99) redacts API keys and Bearer tokens from log output, but only in debug builds. The `HttpClientFactory` uses BODY-level logging which could expose tokens before reaching `RedactingTree`. The main `NetworkModule` uses HEADERS-level logging only.

---

## Performance Concerns

### ChatViewModel at 550 Lines
The largest file in the codebase handles too many responsibilities (see High Priority). Load impact on UI thread is minimal since ViewModel operations are on `viewModelScope`, but cognitive complexity is high.

### SSE Parser Unbounded Buffer
`SseParser` can grow without bounds (see Medium Priority). On devices with limited RAM (where an LLM already consumes 4-5GB), this is a real OOM risk.

### MemoryChecker Division-by-Zero Risk
`MemoryChecker.getMemoryInfo()` (line 26) computes `usedPercent` as `((1.0 - memInfo.availMem.toDouble() / memInfo.totalMem) * 100).toInt()`. If `totalMem` is 0 (theoretical edge case on some emulator/configurations), this would produce `NaN` → negative percent.

### Native Code Synchronous Blocking
`LlamaEngine.generate()` in native code (`jni_bridge.cpp` line 41) blocks the calling thread synchronously until generation completes. The Kotlin layer wraps this in `callbackFlow` (`LlamaEngine.kt` line 33) and `LocalLlmProvider` dispatches on `Dispatchers.Default` (line 45). This is correct, but the native side must ensure `shouldStop` is checked periodically to prevent hung threads on `stop()`.

---

## Incomplete Features

### llama.cpp Inference (Core)
**Status: STUB.** The entire native inference pipeline is placeholder. See Critical Issue #1. The `CMakeLists.txt` (`app/src/main/cpp/CMakeLists.txt`) only compiles the JNI bridge — it does not include or link against llama.cpp source.

### ModelsPlaceholderScreen
**Status: DEAD CODE.** `app/src/main/java/com/warped/ui/models/ModelsPlaceholderScreen.kt` exists but appears superseded by `ModelsScreen.kt`.

### TODO Comments in Native Code
The `jni_bridge.cpp` contains 5 explicit `TODO` comments (lines 23, 50, 71-72, 85) marking all real llama.cpp integration as stubbed out.

### AnthropicProvider listModels Returns Empty
`AnthropicProvider.listModels()` (line 88) returns `Result.success(emptyList())` with the comment "Anthropic doesn't have a public models list API". This is true, but the app should handle this gracefully (show hardcoded Anthropic model names, or prompt user to enter model ID).

---

## Testing Gaps

### Complete Absence of Tests
**ZERO test files exist** in the project. No unit tests (`app/src/test/`), no Android instrumented tests (`app/src/androidTest/`). The build file (`app/build.gradle.kts`) has test dependencies configured (JUnit5, MockK, Turbine, Truth, Room testing, Compose UI test) but no test source files exist.

### Critical Untested Paths
| Path | Risk | Priority |
|------|------|----------|
| `AuthInterceptor` — API key extraction and header injection | API keys silently not sent; chat fails | HIGH |
| `SseParser` — edge cases, malformed input, buffer overflow | OOM, dropped tokens | HIGH |
| `GgufMetadataParser` — malformed GGUF files, truncated headers | Crash on model import | HIGH |
| `ChatViewModel.sendMessage()` — full chat flow state machine | Core user experience | HIGH |
| `EngineManager.switchToLiteRT()` / `switchToLlama()` | Engine corruption, double-load | MEDIUM |
| `ModelDownloadWorker` — checkpoint/resume, network failure | Data loss, partial downloads | MEDIUM |
| `KeystoreManager` / `ApiKeyStore` — encryption lifecycle | Secret exposure | HIGH |
| `BackendDetector` — GPU detection logic | Incorrect backend selection | MEDIUM |
| `MemoryChecker` — threshold calculations | False OOM warnings | LOW |
| `LMStudioProvider` — SSE parsing fallback paths | Dropped/unreadable chat | MEDIUM |
| `AnthropicProvider` — SSE event parsing | Dropped chat responses | MEDIUM |

---

## Recommendations

**Top 10 things to address first, in priority order:**

1. **Integrate real llama.cpp** — The app's core value proposition (local LLM inference) doesn't work. Compile and link llama.cpp in the CMake build, uncomment the native inference code in `jni_bridge.cpp`.

2. **Fix API key transmission** — Wire the global `OkHttpClient` with `AuthInterceptor` into `OpenAIProvider`, `OllamaProvider`, and `CustomProvider` so API keys are actually sent. This blocks all remote provider usage via the secure auth path.

3. **Scope cleartext traffic** — Change `network_security_config.xml` to only permit cleartext for `localhost` and private LAN ranges. Currently all traffic is vulnerable.

4. **Add certificate pinning** — Configure OkHttp `CertificatePinner` for all remote API hosts.

5. **Write critical path tests** — Start with `AuthInterceptor`, `SseParser`, `GgufMetadataParser`, and `ChatViewModel`. The test dependencies are already in `build.gradle.kts` — just add the test files.

6. **Add JNI ProGuard keep rules** — Prevent R8 from stripping native method declarations in release builds.

7. **Replace force-unwrap (!!) with safe access** — 11 instances in production code. Start with `EngineManager.kt` line 70 and `BackendDetector.kt` line 35 (highest crash risk).

8. **Replace silent catch blocks with logging** — At minimum, add `Timber.w(e, ...)` to all empty catch blocks so failures are visible in production.

9. **Gate HttpLoggingInterceptor on BuildConfig.DEBUG** — Ensure `HttpClientFactory` BODY-level logging is debug-only.

10. **Extract ChatViewModel into smaller collaborators** — At 550 lines, it's the most complex class and will be the hardest to test. Extract `ModelLoadCoordinator` and `TokenStreamProcessor` first.

---

*Concerns audit: 2026-05-02*
