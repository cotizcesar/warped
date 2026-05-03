# External Integrations

**Analysis Date:** 2026-05-02

## Hugging Face Hub API

**Purpose:** Browse and download GGUF models for local inference.

**Base URL:** `https://huggingface.co/`

**API Interface:** `app/src/main/java/com/warped/data/remote/api/HuggingFaceApi.kt`

**Endpoints:**
| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `api/models` | Search models. Query params: `search`, `library` (default `"gguf"`), `author`, `sort` (default `"downloads"`), `direction`, `limit`, `full` |
| `GET` | `api/models/{modelId}` | Model detail including `siblings` file list, `cardData`, `config`, `safetensors`, gated status |

**DTOs:** `app/src/main/java/com/warped/data/remote/dto/HuggingFaceDtos.kt`
- `HuggingFaceModel` — search result (id, author, tags, downloads, likes, pipeline_tag, gated, siblings)
- `HuggingFaceModelDetail` — full model info (siblings with blobId/LFS, cardData, config, safeTensors, gated)
- `HuggingFaceSibling` — individual file (rfilename, size, blobId, lfs info)
- `HuggingFaceLfsInfo` — Git LFS pointer (size, sha256)
- `HuggingFaceCardData` — model card metadata (language, license, library_name)
- `HuggingFaceSafeTensors` — safe tensors parameters

**Auth:** No API key required for public models. For gated models, user must add a HuggingFace token (noted in error message: `"Model requires authentication (gated). Add a HuggingFace token."`)

**Repository:** `app/src/main/java/com/warped/data/repository/HuggingFaceRepositoryImpl.kt` — uses shared `OkHttpClient` with 15s connect / 30s read timeouts. DI binding via `HuggingFaceModule.kt`.

**UI:** `app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt`, `HuggingFaceViewModel.kt`, `HuggingFaceUiState.kt`

## Model Download Flow

**Worker:** `app/src/main/java/com/warped/data/local/download/ModelDownloadWorker.kt` (`@HiltWorker`, `CoroutineWorker`)

**Manager:** `app/src/main/java/com/warped/data/local/download/ModelDownloadManager.kt`

**Download URL pattern:** `https://huggingface.co/{model_id}/resolve/main/{filename}` (constructed externally, passed as `fileUrl` to the worker)

**Features:**
- HTTP `Range` header for resume support (`bytes={resumeOffset}-`)
- Checkpoint persistence to Room (`DownloadCheckpointEntity`) every ~1MB
- Foreground notification with progress, speed, cancel action
- WorkManager progress observation via `LiveData<WorkInfo>`
- Storage check before download (requires 110% of file size free)
- Post-download: GGUF metadata parsing (`GgufMetadataParser`) → Room save (`LocalModelRepository.saveModel()`)
- Error handling: 401/403 → "Model requires authentication", 404 → "File not found"
- Cancellation-friendly: checks `isStopped` flag, persists checkpoint before exiting

**DI:** `ModelDownloadManager` receives `Context`, `WorkManager`, `DownloadCheckpointDao` via Hilt.

## OpenAI API (and OpenAI-compatible)

**Purpose:** Connect to OpenAI API or any OpenAI-compatible API (e.g., vLLM, TGI, LocalAI, Groq, Together AI).

**API Interface:** `app/src/main/java/com/warped/data/remote/api/OpenAiApi.kt`

**Endpoints:**
| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `v1/chat/completions` | Streaming chat completions (`stream: true`) |
| `GET` | `v1/models` | List available models |

**Request DTO:** `app/src/main/java/com/warped/data/remote/dto/OpenAiChatRequest.kt`
- `OpenAiChatRequest` — model, messages, stream, temperature, top_p, max_tokens, stop
- `OpenAiMessage` — role, content
- `OpenAiModelListResponse` / `OpenAiModelData` — model listing

**Streaming DTO:** `app/src/main/java/com/warped/data/remote/dto/StreamChunks.kt`
- `OpenAiStreamChunk` → `OpenAiStreamChoice` → `OpenAiStreamDelta` (content, role, finish_reason)

**Auth:** Bearer token from `ApiKeyStore` per endpoint ID. Injected via `AuthInterceptor` (OkHttp interceptor) which reads `endpointId` from request tag.

**Provider:** `app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt` — implements `LlmProvider`. Creates its own Retrofit per-instance (not using the global OkHttpClient). Maps `ChatRequest` → `OpenAiChatRequest`, receives `ResponseBody` → `.asSseFlow(json)` → `Flow<StreamToken>`. `[DONE]` sentinel terminates stream.

**SSE Parsing:** `app/src/main/java/com/warped/data/remote/network/SseExtensions.kt` — `ResponseBody.asSseFlow(json)` parses `data: {...}` lines, decodes `OpenAiStreamChunk`, extracts `choices[0].delta.content`. Uses `SseParser` for buffer-based `\n\n` delimiter handling. Runs on `Dispatchers.IO`.

**Connection test:** Calls `listModels()` — 200 = Connected, otherwise = Disconnected.

## Anthropic API

**Purpose:** Connect to Anthropic Claude API.

**API Interface:** `app/src/main/java/com/warped/data/remote/api/AnthropicApi.kt`

**Endpoints:**
| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `v1/messages` | Chat completions (streaming) |

**Request DTO:** `app/src/main/java/com/warped/data/remote/dto/AnthropicDtos.kt`
- `AnthropicChatRequest` — model, max_tokens, messages, system (separate field), stream, temperature, top_p, top_k
- `AnthropicMessage` — role, content

**Streaming DTOs:**
- `AnthropicSseEvent` — type, delta, message, index
- `AnthropicDelta` — type, text, stop_reason
- `AnthropicSseMessage` — id, model, role

**Auth:** `x-api-key` header + `anthropic-version: 2023-06-01` header. API key retrieved from `ApiKeyStore` at provider construction time, passed as constructor parameter. Key material zeroed via `CharArray.fill('0')` after use.

**Provider:** `app/src/main/java/com/warped/data/remote/provider/AnthropicProvider.kt` — implements `LlmProvider`. Creates its own OkHttpClient with auth interceptor injecting `x-api-key` and `anthropic-version`. Custom SSE parsing via `parseAnthropicSse()` — reads `ResponseBody.source()` line-by-line, handles `event:` / `data:` lines, decodes `AnthropicSseEvent`, emits `StreamToken.Delta` for `content_block_delta`, `StreamToken.Done` on `message_stop`, `StreamToken.Error` on `error` type.

**System message handling:** Anthropic uses a separate `system` field — the provider extracts the first `Role.SYSTEM` message from the chat request.

**Connection test:** Sends a minimal chat request (`max_tokens=1`, `stream=false`). 200 = Connected, 401/403 = Disconnected (endpoint reachable but auth failed).

**No models list API:** Anthropic does not expose a public models listing endpoint — `listModels()` returns empty list.

## Ollama API

**Purpose:** Connect to Ollama running on local network or remote server.

**API Interface:** `app/src/main/java/com/warped/data/remote/api/OllamaApi.kt`

**Endpoints:**
| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `api/chat` | Streaming chat completions (`stream: true`) |
| `GET` | `api/tags` | List available models |

**Request DTO:** `app/src/main/java/com/warped/data/remote/dto/OllamaChatRequest.kt`
- `OllamaChatRequest` — model, messages, stream, options
- `OllamaMessage` — role, content
- `OllamaOptions` — temperature, top_p, top_k, num_predict
- `OllamaModelListResponse` / `OllamaModelData` — name, modified_at, size

**Streaming DTO:** `app/src/main/java/com/warped/data/remote/dto/StreamChunks.kt`
- `OllamaStreamChunk` — model, message (content, role), done

**Auth:** No authentication required (Ollama is typically local/trusted network).

**Provider:** `app/src/main/java/com/warped/data/remote/provider/OllamaProvider.kt` — implements `LlmProvider`. Creates own Retrofit per-instance. Maps `ChatRequest` → `OllamaChatRequest`, receives `ResponseBody` → `.asOllamaFlow(json)` → `Flow<StreamToken>`.

**Streaming:** Ollama uses JSON-line streaming (one JSON object per line, not SSE). `ResponseBody.asOllamaFlow()` in `SseExtensions.kt` reads line-by-line, decodes `OllamaStreamChunk`, emits `Delta(content)` or `Done` when `chunk.done == true`.

**Connection test:** Calls `listModels()` — 200 = Connected, otherwise = Disconnected.

## LM Studio API

**Purpose:** Connect to LM Studio running on local network or remote machine.

**API Interface:** `app/src/main/java/com/warped/data/remote/api/LmStudioApi.kt`

**Endpoints:**
| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `api/v1/chat` | Chat completions (streaming + non-streaming) |
| `GET` | `api/v1/models` | List available models (with display_name, architecture, quantization, size, capabilities) |
| `POST` | `api/v1/models/load` | Load a model instance |
| `POST` | `api/v1/models/unload` | Unload a model instance |

**Request DTO:** `app/src/main/java/com/warped/data/remote/dto/LmStudioDtos.kt`
- `LmStudioChatRequest` — model, input (multimodal: text + image items), system_prompt, stream, temperature, top_p, top_k, repeat_penalty, max_output_tokens, context_length, reasoning, store
- `LmStudioInputItem` — type (`"text"` or `"image"`), content, data_url
- `LmStudioModelListResponse` / `LmStudioModelData` — key, display_name, type, publisher, architecture, quantization, size_bytes, params_string, max_context_length, format, capabilities
- `LmStudioModelData.capabilities` — vision, trained_for_tool_use
- `LmStudioLoadRequest` / `LmStudioLoadResponse` — model load with instanceId
- `LmStudioUnloadRequest` / `LmStudioUnloadResponse` — unload by instanceId

**Streaming DTOs:**
- `LmStudioSseEvent` — content, token, type, done, error, output, result, stats
- `LmStudioChatResult` — model_instance_id, output items, stats
- `LmStudioStats` — input_tokens, total_output_tokens, reasoning_output_tokens, tokens_per_second, time_to_first_token_seconds, model_load_time_seconds
- `LmStudioOutputItem` — type (`"message"`, `"reasoning"`), content
- `LmStudioSseError` — message, type, code

**Auth:** No authentication required (LM Studio is typically local/trusted network).

**Provider:** `app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt` — implements `LlmProvider`. Default base URL: `http://localhost:1234`. Creates own OkHttpClient (15s connect / 120s read). Handles multimodal input (text + base64 image data URLs). Custom SSE parsing with fallback to full JSON response for non-streaming mode. Emits reasoning content separately (available in `StreamToken.Done` via `reasoning` field). Emits stats as part of `StreamToken.Done`. Handles `message.delta`, `reasoning.delta`, and `chat.end` SSE events.

**Connection test:** Calls `listModels()` — 200 = Connected, otherwise = Disconnected.

## Custom API Provider

**Purpose:** Connect to any OpenAI-compatible API with custom URL paths.

**API Interface:** `app/src/main/java/com/warped/data/remote/api/CustomApi.kt`

**Endpoints:**
| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `{chatPath}` (default `"v1/chat/completions"`) | Chat completions |
| `GET` | `{modelsPath}` (default `"v1/models"`) | List models |

**How it differs from OpenAI:** Uses Retrofit `@Url` parameters for dynamic paths. Same request/response format as OpenAI (`OpenAiChatRequest`, `OpenAiStreamChunk`).

**Provider:** `app/src/main/java/com/warped/data/remote/provider/CustomProvider.kt` — implements `LlmProvider`. Configurable `chatPath` and `modelsPath` via constructor. Uses same `asSseFlow()` for streaming as OpenAI.

**Connection test:** Calls `listModels(modelsPath)` — 200 = Connected.

## Provider Router

**File:** `app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt`

Centralizes provider instantiation based on `Endpoint.apiType`:
| `ProviderType` | Provider Class | Auth |
|----------------|---------------|------|
| `OPENAI` | `OpenAIProvider` | Bearer token via AuthInterceptor |
| `ANTHROPIC` | `AnthropicProvider` | x-api-key header (direct constructor injection) |
| `OLLAMA` | `OllamaProvider` | None |
| `LM_STUDIO` | `LMStudioProvider` | None |
| `CUSTOM` | `CustomProvider` | None (uses OpenAi format via `CustomApi`) |
| `LOCAL` | `LocalLlmProvider` | N/A (llama.cpp JNI) |
| `LITE_RT_LM` | `LiteRTLmProvider` | N/A (local SDK) |

## llama.cpp (JNI/NDK Integration)

**Bridge layer:** `app/src/main/cpp/jni_bridge.cpp` + `jni_bridge.h`

**Native functions exposed to Kotlin** (via JNI `extern "C"`):

| JNI Function | Kotlin `external fun` | Purpose |
|---|---|---|
| `Java_com_warped_data_local_inference_LlamaEngine_nativeLoadModel` | `nativeLoadModel(path, nThreads, nCtx): Boolean` | Load GGUF model into memory |
| `Java_com_warped_data_local_inference_LlamaEngine_nativeGenerate` | `nativeGenerate(prompt, callback)` | Run inference with token callback |
| `Java_com_warped_data_local_inference_LlamaEngine_nativeStop` | `nativeStop()` | Stop generation |
| `Java_com_warped_data_local_inference_LlamaEngine_nativeUnload` | `nativeUnload()` | Free model memory |
| `Java_com_warped_data_local_inference_LlamaEngine_nativeIsLoaded` | `nativeIsLoaded(): Boolean` | Check model loaded status |
| `Java_com_warped_data_local_inference_LlamaEngine_nativeGetModelInfo` | `nativeGetModelInfo(): String` | Get model metadata |

**Token streaming callback:** JNI calls `onToken(String token, boolean done)` on a `TokenCallback` Java interface. Kotlin side wraps this in `callbackFlow {}` to produce `Flow<String>`. On `done=true` the flow closes.

**Current status:** Stub implementations with TODO comments for real llama.cpp integration. The infrastructure (CMake, JNI bridge, Kotlin wrapper, `callbackFlow`, `LocalLlmProvider`) is fully built and ready for real native library integration.

**CMake setup** (`app/src/main/cpp/CMakeLists.txt`):
- C++17 standard
- Links `android` and `log` system libraries
- Page size alignment: `max-page-size=16384`
- Output to `${ANDROID_ABI}` subdirectory

## LiteRT-LM (Google AI Edge SDK)

**SDK:** `com.google.ai.edge.litertlm:litertlm-android:0.11.0-rc1`

**Integration points** (`app/src/main/java/com/warped/data/local/inference/`):

| Component | File | Purpose |
|-----------|------|---------|
| `LiteRTLmEngine` | `LiteRTLmEngine.kt` | Wraps LiteRT-LM `Engine` SDK. `init()` (blocking), `createConversation()`, `close()`. Configures `EngineConfig` with model path and `Backend.CPU()` or `Backend.GPU()` |
| `BackendDetector` | `BackendDetector.kt` | Runtime GPU detection. Probes EGL display (`EGL14.eglGetDisplay` + `eglChooseConfig` for OpenGL ES2 config) and attempts `System.loadLibrary("OpenCL")`. Result cached for process lifetime |
| `EngineManager` | `EngineManager.kt` | Singleton coordinator. Handles engine switching, model file caching (copies to `context.cacheDir/litertlm_cache/`), memory pressure unload (`onTrimMemory`) |
| `LiteRTLmProvider` | `LiteRTLmProvider.kt` | Implements `LlmProvider`. Builds `Contents` payload (text + base64-decoded images), maps `GenerationParameters` → `SamplerConfig` (topK, topP, temperature, seed), 2-retry recovery on engine errors, NFC normalization of response text |

**GPU backend requirements** (from `AndroidManifest.xml`):
- `<uses-native-library android:name="libvndksupport.so" android:required="false" />`
- `<uses-native-library android:name="libOpenCL.so" android:required="false" />`

**Model format:** `.litertlm` files (separate from GGUF). Auto-detected during download/import by file extension.

**ProGuard:** `-keep class com.google.ai.edge.litertlm.** { *; }`

## Android Keystore & Encrypted Storage

**Files:** `app/src/main/java/com/warped/data/local/security/KeystoreManager.kt`, `ApiKeyStore.kt`

**KeystoreManager:**
- Uses `androidx.security:security-crypto:1.1.0-alpha06` (Tink-integrated)
- Creates `MasterKey` with AES-256 GCM, hardware-backed via Android Keystore (`KeyGenParameterSpec` with `PURPOSE_ENCRYPT | PURPOSE_DECRYPT`)
- Configures `EncryptedSharedPreferences` with:
  - **Key encryption:** `AES256_SIV` (deterministic, supports lookup)
  - **Value encryption:** `AES256_GCM` (authenticated)
- File name: `"warped_secure_prefs"`
- Master key alias: `"_warped_master_key_"`

**ApiKeyStore:**
- Stores API keys as `CharArray` (mutable, zeroable)
- Key alias format: `"api_key_{endpointId}"`
- On store: saves char array, then zeroes it via `fill('0')`
- On get: returns as `CharArray?` (caller must zero after use)
- On delete: removes from EncryptedSharedPreferences
- Supports batch delete (`deleteAllKeys`)

**Auth flow** (`AuthInterceptor.kt`):
1. Request is tagged with `endpointId` (`Long`)
2. `AuthInterceptor.intercept()` reads tag, fetches key from `ApiKeyStore.getKey(endpointId)`
3. Converts `CharArray` to String, adds `Authorization: Bearer {key}` header
4. Zeroes the `CharArray` after use

## WorkManager & Background Downloads

**Integration:** `@HiltWorker` annotation + `HiltWorkerFactory`

**Application setup** (`WarpedApplication.kt`):
- Implements `Configuration.Provider`
- Provides `workManagerConfiguration` with custom `HiltWorkerFactory`
- Disables default `WorkManagerInitializer` in Manifest via `tools:node="remove"`
- Foreground service type: `dataSync` (API 34+)

**Download worker** (`ModelDownloadWorker.kt`):
- `@AssistedInject` for Hilt + WorkManager integration
- Receives injected `OkHttpClient`, `LocalModelRepository`, `DownloadCheckpointDao`
- Input data: modelId, fileName, fileUrl, fileSizeBytes
- Foreground notification via `setForeground()` with progress, speed, cancel action
- Checkpoint persistence to Room every ~1MB
- HTTP Range header for resume
- GGUF metadata parsing and Room model registration on completion

## Networking Infrastructure

**OkHttp Configuration** (from `NetworkModule.kt`):
- Connect timeout: 30s
- Read timeout: 120s
- Write timeout: 30s
- Connection pool: 5 connections, 1-minute keep-alive
- `retryOnConnectionFailure(true)`
- Interceptors: `AuthInterceptor` (Bearer token injection), `HttpLoggingInterceptor` (HEADERS level)

**JSON Configuration** (from `NetworkModule.kt`):
- `ignoreUnknownKeys = true`
- `isLenient = true`
- `encodeDefaults = true`
- `coerceInputValues = true`

**Retrofit setup:** Each provider creates its own Retrofit instance since each has a different `baseUrl`. The `kotlinx.serialization` converter factory is used throughout.

**Network security:** Cleartext HTTP permitted globally (needed for LAN Ollama/LM Studio). System trust anchors only for TLS.

## Data Extraction Rules

**File:** `app/src/main/res/xml/data_extraction_rules.xml`

Both `cloud-backup` and `device-transfer` have `<exclude domain="root" />` — no app data is backed up or transferred. This protects API keys in EncryptedSharedPreferences and locally stored chat history.

---

*Integration audit: 2026-05-02*
