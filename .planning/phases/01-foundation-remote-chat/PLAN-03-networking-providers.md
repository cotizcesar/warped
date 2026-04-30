---
plan: 03-networking-providers
wave: 3
depends_on: [02-data-layer]
autonomous: true
requirements_addressed: [PROV-03, PROV-04, PROV-05, CHAT-01, SEC-01]
files_modified:
  - app/src/main/java/com/warped/data/remote/network/HttpClientFactory.kt
  - app/src/main/java/com/warped/data/remote/network/AuthInterceptor.kt
  - app/src/main/java/com/warped/data/remote/network/SseParser.kt
  - app/src/main/java/com/warped/data/remote/network/SseEvent.kt
  - app/src/main/java/com/warped/data/remote/network/SseExtensions.kt
  - app/src/main/java/com/warped/data/remote/api/OpenAiApi.kt
  - app/src/main/java/com/warped/data/remote/api/OllamaApi.kt
  - app/src/main/java/com/warped/data/remote/api/CustomApi.kt
  - app/src/main/java/com/warped/data/remote/dto/OpenAiChatRequest.kt
  - app/src/main/java/com/warped/data/remote/dto/OpenAiModelListResponse.kt
  - app/src/main/java/com/warped/data/remote/dto/OllamaChatRequest.kt
  - app/src/main/java/com/warped/data/remote/dto/OllamaModelListResponse.kt
  - app/src/main/java/com/warped/data/remote/dto/StreamChunks.kt
  - app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt
  - app/src/main/java/com/warped/data/remote/provider/OllamaProvider.kt
  - app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt
  - app/src/main/java/com/warped/data/remote/provider/CustomProvider.kt
  - app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt
---

# Plan 03: Networking Layer & Provider Implementations

## Objective
Build the entire remote networking stack: OkHttp client factory with auth interceptor, SSE parser with line-accumulator buffer (handles fragmented TCP chunks per PITFALLS.md §4.1), Retrofit API interfaces for OpenAI/Ollama/Custom endpoints, DTOs for all JSON shapes, and 4 provider implementations (`OpenAIProvider`, `OllamaProvider`, `LMStudioProvider`, `CustomProvider`) that implement `LlmProvider` with streaming chat, model listing, and connection testing — plus the `ProviderRouter` factory.

## must_haves
- `SseParser` feeds data through a `StringBuilder` buffer, splits only on `\n\n` boundaries — detects `[DONE]`, handles split JSON across chunks
- `ResponseBody.asSseFlow()` wraps SSE parsing in a `flow {}` builder with proper error handling and cleanup
- OpenAI-compatible streaming uses `POST /v1/chat/completions` with `stream: true`
- Ollama provider handles newline-delimited JSON (NOT double-newline SSE) per RESEARCH.md §4.7
- `LMStudioProvider` reuses OpenAI DTOs but defaults base URL to `http://localhost:1234`
- `ProviderRouter.resolve(endpoint)` maps `ProviderType` → configured `LlmProvider`
- `AuthInterceptor` injects `Authorization: Bearer` header from `ApiKeyStore` per request (never caches key)
- All networking runs on `Dispatchers.IO` via `flowOn(Dispatchers.IO)`

## Verification
1. `grep -q 'class SseParser' app/src/main/java/com/warped/data/remote/network/SseParser.kt`
2. `grep -q 'buffer.indexOf("\\n\\n")' app/src/main/java/com/warped/data/remote/network/SseParser.kt` — double-newline boundary
3. `grep -q '\[DONE\]' app/src/main/java/com/warped/data/remote/network/SseExtensions.kt` — terminal event handling
4. `grep -q 'fun asSseFlow' app/src/main/java/com/warped/data/remote/network/SseExtensions.kt`
5. `grep -q 'v1/chat/completions' app/src/main/java/com/warped/data/remote/api/OpenAiApi.kt`
6. `grep -q 'api/chat' app/src/main/java/com/warped/data/remote/api/OllamaApi.kt`
7. `grep -q 'class OpenAIProvider' app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt`
8. `grep -q ': LlmProvider' app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt`
9. `grep -q 'class ProviderRouter' app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt`
10. `grep -q 'fun resolve.*LlmProvider' app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt`
11. `grep -q 'asSseFlow\|asOllamaFlow' app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt` — uses SSE flow extension

## Tasks

### Task 1: OkHttp Client Factory + Auth Interceptor
<read_first>
- app/src/main/java/com/warped/data/local/security/ApiKeyStore.kt (the auth interceptor uses ApiKeyStore to resolve keys)
- .planning/phases/01-foundation-remote-chat/01-RESEARCH.md §4.1 and §4.2 (lines 703-768 for exact OkHttp + AuthInterceptor code)
</read_first>
<action>
1. **`app/src/main/java/com/warped/data/remote/network/AuthInterceptor.kt`** — Exact from RESEARCH.md §4.2 lines 742-768. An OkHttp `Interceptor` that:
   - Gets the `endpointId` from the request's `tag(Long::class.java)`
   - Calls `apiKeyStore.getKey(endpointId)` to retrieve the key
   - Adds `Authorization: Bearer {key}` header if key exists
   - `@Inject constructor(apiKeyStore: ApiKeyStore)`, annotated `@Singleton`
   - IMPORTANT: Do NOT store the key in a field — resolve on every request. Use `apiKeyStore.getKey(endpointId)` inline and zero-fill after the request proceeds.

2. **`app/src/main/java/com/warped/data/remote/network/HttpClientFactory.kt`** — Exact from RESEARCH.md §4.1 lines 706-737 with one change: the `create()` method is the base, `createForEndpoint()` is variant. Constructor takes `AuthInterceptor` and `HttpLoggingInterceptor`. Configuration:
   - `connectTimeout(30, TimeUnit.SECONDS)` 
   - `readTimeout(120, TimeUnit.SECONDS)` — long read for SSE streaming
   - `writeTimeout(30, TimeUnit.SECONDS)`
   - `callTimeout(0, TimeUnit.MILLISECONDS)` — no global call timeout for streaming
   - `addInterceptor(authInterceptor)`
   - `addInterceptor(loggingInterceptor)`
   - `retryOnConnectionFailure(true)`
   - `connectionPool(ConnectionPool(5, 1, TimeUnit.MINUTES))`
</action>
<acceptance_criteria>
- `grep -q 'class HttpClientFactory' app/src/main/java/com/warped/data/remote/network/HttpClientFactory.kt`
- `grep -q 'fun create(): OkHttpClient' app/src/main/java/com/warped/data/remote/network/HttpClientFactory.kt`
- `grep -q 'connectTimeout.*30' app/src/main/java/com/warped/data/remote/network/HttpClientFactory.kt`
- `grep -q 'readTimeout.*120' app/src/main/java/com/warped/data/remote/network/HttpClientFactory.kt`
- `grep -q 'class AuthInterceptor' app/src/main/java/com/warped/data/remote/network/AuthInterceptor.kt`
- `grep -q ': Interceptor' app/src/main/java/com/warped/data/remote/network/AuthInterceptor.kt`
- `grep -q 'apiKeyStore' app/src/main/java/com/warped/data/remote/network/AuthInterceptor.kt`
- `grep -q 'Bearer' app/src/main/java/com/warped/data/remote/network/AuthInterceptor.kt`
- `grep -q 'tag(Long::class.java)' app/src/main/java/com/warped/data/remote/network/AuthInterceptor.kt`
</acceptance_criteria>

### Task 2: SSE Parser (Critical — Handles Fragmented TCP Chunks)
<read_first>
- .planning/research/PITFALLS.md §4.1 (lines 127-135 — "SSE parsing that doesn't handle multi-chunk splits")
- .planning/research/PITFALLS.md §4.2 (lines 137-143 — "[DONE] event handling")
- .planning/phases/01-foundation-remote-chat/01-RESEARCH.md §4.4 (lines 868-922 for exact SseParser + SseEvent code)
</read_first>
<action>
Create the SSE parsing subsystem. This is the most critical code in Phase 1 — a naive parser will silently drop tokens on mobile connections.

1. **`app/src/main/java/com/warped/data/remote/network/SseEvent.kt`** — Simple data class:
```kotlin
package com.warped.data.remote.network

data class SseEvent(val data: String, val event: String? = null)
```

2. **`app/src/main/java/com/warped/data/remote/network/SseParser.kt`** — Exact code from RESEARCH.md §4.4 lines 877-922. Key design:
   - `private val buffer = StringBuilder()` — accumulates partial lines
   - `fun feed(chunk: String): List<SseEvent>` — receives raw bytes, splits on `\n\n` boundaries only
   - If no double-newline found → buffer accumulates and returns empty list
   - On `\n\n` found → parse the accumulated segment, emit `SseEvent`
   - `private fun parse(raw: String): SseEvent?` — splits on `\n`, extracts `data:` and `event:` lines, skips comment lines (starting with `:`)
   - `fun reset()` — clears buffer for reuse

3. **`app/src/main/java/com/warped/data/remote/network/SseExtensions.kt`** — Exact code from RESEARCH.md §4.5 lines 936-973. Extension function `ResponseBody.asSseFlow(json: Json, onToken: (SseEvent) -> StreamToken): Flow<StreamToken>`:
   - Creates `SseParser` instance
   - Reads lines from `source.readUtf8Line()` in a `while (!source.exhausted())` loop
   - Feeds each line + `"\n"` to parser
   - For each event: checks `[DONE]`, JSON, empty data → emits appropriate `StreamToken`
   - `[DONE]` → `emit(StreamToken.Done)` then `return@flow`
   - `IOException` caught → `emit(StreamToken.Error("Connection lost: ..."))`
   - `finally` block resets parser and closes body
   - Uses `flowOn(Dispatchers.IO)`
</action>
<acceptance_criteria>
- `grep -q 'data class SseEvent' app/src/main/java/com/warped/data/remote/network/SseEvent.kt`
- `grep -q 'class SseParser' app/src/main/java/com/warped/data/remote/network/SseParser.kt`
- `grep -q 'private val buffer' app/src/main/java/com/warped/data/remote/network/SseParser.kt`
- `grep -q 'fun feed(chunk: String): List<SseEvent>' app/src/main/java/com/warped/data/remote/network/SseParser.kt`
- `grep -q 'indexOf.*\\n\\n' app/src/main/java/com/warped/data/remote/network/SseParser.kt`
- `grep -q 'data:' app/src/main/java/com/warped/data/remote/network/SseParser.kt`
- `grep -q 'fun reset()' app/src/main/java/com/warped/data/remote/network/SseParser.kt`
- `grep -q 'fun ResponseBody.asSseFlow' app/src/main/java/com/warped/data/remote/network/SseExtensions.kt`
- `grep -q '\[DONE\]' app/src/main/java/com/warped/data/remote/network/SseExtensions.kt`
- `grep -q 'readUtf8Line()' app/src/main/java/com/warped/data/remote/network/SseExtensions.kt`
- `grep -q 'flowOn(Dispatchers.IO)' app/src/main/java/com/warped/data/remote/network/SseExtensions.kt`
- `grep -q 'Connection lost' app/src/main/java/com/warped/data/remote/network/SseExtensions.kt`
</acceptance_criteria>

### Task 3: Retrofit API Interfaces + DTOs
<read_first>
- .planning/phases/01-foundation-remote-chat/01-RESEARCH.md §4.3, §4.6, §4.7 (lines 770-864 for all API contracts and DTOs)
- app/src/main/java/com/warped/domain/model/ChatMessage.kt (for message structure mapping)
- app/src/main/java/com/warped/domain/model/ChatRequest.kt (for request structure)
</read_first>
<action>
Create all Retrofit API interfaces and their corresponding DTOs. DTO package: `app/src/main/java/com/warped/data/remote/dto/`. API package: `app/src/main/java/com/warped/data/remote/api/`.

1. **`app/src/main/java/com/warped/data/remote/api/OpenAiApi.kt`** — Exact from RESEARCH.md §4.3 lines 772-783:
```kotlin
package com.warped.data.remote.api

import com.warped.data.remote.dto.OpenAiChatRequest
import com.warped.data.remote.dto.OpenAiModelListResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST

interface OpenAiApi {
    @POST("v1/chat/completions")
    @Headers("Content-Type: application/json")
    suspend fun chatCompletions(
        @Body request: OpenAiChatRequest
    ): Response<ResponseBody>

    @GET("v1/models")
    suspend fun listModels(): Response<OpenAiModelListResponse>
}
```

2. **`app/src/main/java/com/warped/data/remote/api/OllamaApi.kt`** — Exact from RESEARCH.md §4.3 lines 819-829:
```kotlin
package com.warped.data.remote.api

import com.warped.data.remote.dto.OllamaChatRequest
import com.warped.data.remote.dto.OllamaModelListResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST

interface OllamaApi {
    @POST("api/chat")
    @Headers("Content-Type: application/json")
    suspend fun chat(
        @Body request: OllamaChatRequest
    ): Response<ResponseBody>

    @GET("api/tags")
    suspend fun listModels(): Response<OllamaModelListResponse>
}
```

3. **`app/src/main/java/com/warped/data/remote/api/CustomApi.kt`** — Generic Retrofit interface for custom endpoints (OpenAI-compatible):
```kotlin
package com.warped.data.remote.api

import com.warped.data.remote.dto.OpenAiChatRequest
import com.warped.data.remote.dto.OpenAiModelListResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Url

interface CustomApi {
    @POST
    @Headers("Content-Type: application/json")
    suspend fun chatCompletions(
        @Url chatPath: String,
        @Body request: OpenAiChatRequest
    ): Response<ResponseBody>

    @GET
    suspend fun listModels(
        @Url modelsPath: String
    ): Response<OpenAiModelListResponse>
}
```

4. **`app/src/main/java/com/warped/data/remote/dto/OpenAiChatRequest.kt`** — Exact from RESEARCH.md §4.3 lines 787-817:
```kotlin
package com.warped.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val stream: Boolean = true,
    val temperature: Float? = null,
    @SerialName("top_p") val topP: Float? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val stop: List<String>? = null
)

@Serializable
data class OpenAiMessage(
    val role: String,
    val content: String
)

@Serializable
data class OpenAiModelListResponse(
    val `object`: String = "list",
    val data: List<OpenAiModelData> = emptyList()
)

@Serializable
data class OpenAiModelData(
    val id: String,
    val `object`: String = "model",
    val ownedBy: String = ""
)
```

5. **`app/src/main/java/com/warped/data/remote/dto/OllamaChatRequest.kt`** — Exact from RESEARCH.md §4.3 lines 832-864:
```kotlin
package com.warped.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = true,
    val options: OllamaOptions? = null
)

@Serializable
data class OllamaMessage(
    val role: String,
    val content: String
)

@Serializable
data class OllamaOptions(
    val temperature: Float? = null,
    @SerialName("top_p") val topP: Float? = null,
    @SerialName("top_k") val topK: Int? = null,
    @SerialName("num_predict") val numPredict: Int? = null
)

@Serializable
data class OllamaModelListResponse(
    val models: List<OllamaModelData> = emptyList()
)

@Serializable
data class OllamaModelData(
    val name: String,
    @SerialName("modified_at") val modifiedAt: String = "",
    val size: Long = 0
)
```

6. **`app/src/main/java/com/warped/data/remote/dto/StreamChunks.kt`** — Token parsing DTOs (combining OpenAI and Ollama stream response shapes):
```kotlin
package com.warped.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// OpenAI streaming chunk
@Serializable
data class OpenAiStreamChunk(
    val choices: List<OpenAiStreamChoice> = emptyList()
)

@Serializable
data class OpenAiStreamChoice(
    val delta: OpenAiStreamDelta? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class OpenAiStreamDelta(
    val content: String? = null,
    val role: String? = null
)

// Ollama streaming chunk
@Serializable
data class OllamaStreamChunk(
    val model: String? = null,
    val message: OllamaStreamMessage? = null,
    val done: Boolean = false
)

@Serializable
data class OllamaStreamMessage(
    val content: String? = null,
    val role: String? = null
)
```
</action>
<acceptance_criteria>
- `grep -q 'interface OpenAiApi' app/src/main/java/com/warped/data/remote/api/OpenAiApi.kt`
- `grep -q 'v1/chat/completions' app/src/main/java/com/warped/data/remote/api/OpenAiApi.kt`
- `grep -q 'v1/models' app/src/main/java/com/warped/data/remote/api/OpenAiApi.kt`
- `grep -q 'interface OllamaApi' app/src/main/java/com/warped/data/remote/api/OllamaApi.kt`
- `grep -q 'api/chat' app/src/main/java/com/warped/data/remote/api/OllamaApi.kt`
- `grep -q 'api/tags' app/src/main/java/com/warped/data/remote/api/OllamaApi.kt`
- `grep -q 'interface CustomApi' app/src/main/java/com/warped/data/remote/api/CustomApi.kt`
- `grep -q '@Serializable.*OpenAiChatRequest' app/src/main/java/com/warped/data/remote/dto/OpenAiChatRequest.kt`
- `grep -q 'stream.*true' app/src/main/java/com/warped/data/remote/dto/OpenAiChatRequest.kt`
- `grep -q 'max_tokens' app/src/main/java/com/warped/data/remote/dto/OpenAiChatRequest.kt`
- `grep -q '@Serializable.*OpenAiModelListResponse' app/src/main/java/com/warped/data/remote/dto/OpenAiChatRequest.kt`
- `grep -q '@Serializable.*OllamaChatRequest' app/src/main/java/com/warped/data/remote/dto/OllamaChatRequest.kt`
- `grep -q '@Serializable.*OllamaStreamChunk' app/src/main/java/com/warped/data/remote/dto/StreamChunks.kt`
- `grep -q 'done.*Boolean' app/src/main/java/com/warped/data/remote/dto/StreamChunks.kt`
</acceptance_criteria>

### Task 4: OpenAI Provider Implementation
<read_first>
- app/src/main/java/com/warped/domain/provider/LlmProvider.kt (interface contract)
- app/src/main/java/com/warped/domain/model/ChatRequest.kt
- app/src/main/java/com/warped/domain/model/StreamToken.kt
- app/src/main/java/com/warped/data/remote/network/SseExtensions.kt (asSseFlow)
- app/src/main/java/com/warped/data/remote/api/OpenAiApi.kt
- app/src/main/java/com/warped/data/remote/dto/OpenAiChatRequest.kt
- app/src/main/java/com/warped/data/remote/dto/StreamChunks.kt
- app/src/main/java/com/warped/data/remote/network/HttpClientFactory.kt
- .planning/phases/01-foundation-remote-chat/01-RESEARCH.md §4.6 (lines 978-1012 for OpenAI token parsing)
</read_first>
<action>
Create **`app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt`** following the exact provider pattern from RESEARCH.md §4.6 and §9.4:

```kotlin
package com.warped.data.remote.provider

import com.warped.data.remote.api.OpenAiApi
import com.warped.data.remote.dto.OpenAiChatRequest
import com.warped.data.remote.dto.OpenAiMessage
import com.warped.data.remote.dto.OpenAiStreamChunk
import com.warped.data.remote.dto.OpenAiStreamDelta
import com.warped.data.remote.network.SseEvent
import com.warped.data.remote.network.asSseFlow
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.ChatMessage
import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.Endpoint
import com.warped.domain.model.ModelInfo
import com.warped.domain.model.ProviderType
import com.warped.domain.model.Role
import com.warped.domain.model.StreamToken
import com.warped.domain.provider.LlmProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenAIProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : LlmProvider {

    override val type = ProviderType.OPENAI

    private var baseUrl: String = ""
    private var api: OpenAiApi? = null

    fun configure(endpoint: Endpoint): OpenAIProvider {
        baseUrl = endpoint.url.trimEnd('/')
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl + "/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        api = retrofit.create(OpenAiApi::class.java)
        return this
    }

    override fun chat(request: ChatRequest): Flow<StreamToken> = flow {
        val messages = request.messages.map { msg ->
            OpenAiMessage(role = msg.role.name.lowercase(), content = msg.content)
        }
        val apiRequest = OpenAiChatRequest(
            model = "gpt-3.5-turbo", // placeholder — set by ViewModel via endpoint config
            messages = messages,
            stream = true,
            temperature = request.parameters.temperature,
            topP = request.parameters.topP,
            maxTokens = request.parameters.maxTokens
        )
        val response = api!!.chatCompletions(apiRequest)
        if (!response.isSuccessful) {
            emit(StreamToken.Error("HTTP ${response.code()}: ${response.message()}"))
            return@flow
        }
        val body = response.body() ?: run {
            emit(StreamToken.Error("Empty response body"))
            return@flow
        }
        emitAll(body.asSseFlow(json) { event -> parseOpenAiToken(event) })
    }.flowOn(Dispatchers.IO)

    override suspend fun listModels(): Result<List<ModelInfo>> {
        return try {
            val response = api!!.listModels()
            if (response.isSuccessful) {
                val body = response.body()
                val models = body?.data?.map {
                    ModelInfo(id = it.id, name = it.id, providerType = ProviderType.OPENAI)
                } ?: emptyList()
                Result.success(models)
            } else {
                Result.failure(IOException("HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun testConnection(): Result<ConnectionStatus> {
        return try {
            val response = api!!.listModels()
            if (response.isSuccessful) {
                Result.success(ConnectionStatus.Connected)
            } else if (response.code() == 401 || response.code() == 403) {
                Result.success(ConnectionStatus.Disconnected) // auth error = disconnected
            } else {
                Result.success(ConnectionStatus.Disconnected)
            }
        } catch (e: Exception) {
            Result.success(ConnectionStatus.Disconnected)
        }
    }

    private fun parseOpenAiToken(event: SseEvent): StreamToken {
        return try {
            val chunk = json.decodeFromString<OpenAiStreamChunk>(event.data)
            val content = chunk.choices.firstOrNull()?.delta?.content
            if (content != null && content.isNotEmpty()) {
                StreamToken.Delta(content)
            } else if (chunk.choices.firstOrNull()?.finishReason != null) {
                StreamToken.Done
            } else {
                StreamToken.Delta("")
            }
        } catch (e: Exception) {
            StreamToken.Error("Failed to parse response token")
        }
    }
}
```
</action>
<acceptance_criteria>
- `grep -q 'class OpenAIProvider' app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt`
- `grep -q ': LlmProvider' app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt`
- `grep -q 'override val type = ProviderType.OPENAI' app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt`
- `grep -q 'fun configure.*Endpoint' app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt`
- `grep -q 'override fun chat.*Flow.*StreamToken' app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt`
- `grep -q 'override suspend fun listModels' app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt`
- `grep -q 'override suspend fun testConnection' app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt`
- `grep -q 'asSseFlow' app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt`
- `grep -q 'parseOpenAiToken' app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt`
- `grep -q 'OpenAiStreamChunk' app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt`
</acceptance_criteria>

### Task 5: Ollama Provider Implementation
<read_first>
- app/src/main/java/com/warped/domain/provider/LlmProvider.kt
- app/src/main/java/com/warped/data/remote/api/OllamaApi.kt
- app/src/main/java/com/warped/data/remote/dto/OllamaChatRequest.kt
- app/src/main/java/com/warped/data/remote/dto/StreamChunks.kt (OllamaStreamChunk)
- .planning/phases/01-foundation-remote-chat/01-RESEARCH.md §4.7 (lines 1019-1069 for Ollama token parsing and streaming pattern)
</read_first>
<action>
Create **`app/src/main/java/com/warped/data/remote/provider/OllamaProvider.kt`** following the pattern from RESEARCH.md §4.7. Critical difference from OpenAI: Ollama uses **newline-delimited JSON** (not SSE double-newline), so it needs its own `flow {}` builder reading `readUtf8Line()` line-by-line, NOT `asSseFlow()`.

```kotlin
package com.warped.data.remote.provider

import com.warped.data.remote.api.OllamaApi
import com.warped.data.remote.dto.OllamaChatRequest
import com.warped.data.remote.dto.OllamaMessage
import com.warped.data.remote.dto.OllamaOptions
import com.warped.data.remote.dto.OllamaStreamChunk
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.Endpoint
import com.warped.domain.model.ModelInfo
import com.warped.domain.model.ProviderType
import com.warped.domain.model.StreamToken
import com.warped.domain.provider.LlmProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OllamaProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : LlmProvider {

    override val type = ProviderType.OLLAMA

    private var api: OllamaApi? = null

    fun configure(endpoint: Endpoint): OllamaProvider {
        val baseUrl = endpoint.url.trimEnd('/')
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl + "/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        api = retrofit.create(OllamaApi::class.java)
        return this
    }

    override fun chat(request: ChatRequest): Flow<StreamToken> = flow {
        val messages = request.messages.map { msg ->
            OllamaMessage(role = msg.role.name.lowercase(), content = msg.content)
        }
        val apiRequest = OllamaChatRequest(
            model = "", // Set by ViewModel
            messages = messages,
            stream = true,
            options = OllamaOptions(
                temperature = request.parameters.temperature,
                topP = request.parameters.topP,
                topK = request.parameters.topK,
                numPredict = request.parameters.maxTokens
            )
        )
        val response = api!!.chat(apiRequest)
        if (!response.isSuccessful) {
            emit(StreamToken.Error("HTTP ${response.code()}: ${response.message()}"))
            return@flow
        }
        val body = response.body() ?: run {
            emit(StreamToken.Error("Empty response body"))
            return@flow
        }
        val source = body.source()
        try {
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank()) continue
                val chunk = json.decodeFromString<OllamaStreamChunk>(line)
                if (chunk.done) {
                    emit(StreamToken.Done)
                    return@flow
                }
                val content = chunk.message?.content
                if (!content.isNullOrEmpty()) emit(StreamToken.Delta(content))
            }
        } catch (e: IOException) {
            emit(StreamToken.Error("Connection lost: ${e.message}"))
        } finally {
            body.close()
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun listModels(): Result<List<ModelInfo>> {
        return try {
            val response = api!!.listModels()
            if (response.isSuccessful) {
                val body = response.body()
                val models = body?.models?.map {
                    ModelInfo(id = it.name, name = it.name, providerType = ProviderType.OLLAMA)
                } ?: emptyList()
                Result.success(models)
            } else {
                Result.failure(IOException("HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun testConnection(): Result<ConnectionStatus> {
        return try {
            val response = api!!.listModels()
            if (response.isSuccessful) Result.success(ConnectionStatus.Connected)
            else Result.success(ConnectionStatus.Disconnected)
        } catch (e: Exception) {
            Result.success(ConnectionStatus.Disconnected)
        }
    }
}
```
</action>
<acceptance_criteria>
- `grep -q 'class OllamaProvider' app/src/main/java/com/warped/data/remote/provider/OllamaProvider.kt`
- `grep -q ': LlmProvider' app/src/main/java/com/warped/data/remote/provider/OllamaProvider.kt`
- `grep -q 'override val type = ProviderType.OLLAMA' app/src/main/java/com/warped/data/remote/provider/OllamaProvider.kt`
- `grep -q 'OllamaStreamChunk' app/src/main/java/com/warped/data/remote/provider/OllamaProvider.kt`
- `grep -q 'source.readUtf8Line()' app/src/main/java/com/warped/data/remote/provider/OllamaProvider.kt` — line-by-line JSON parsing
- `grep -q 'chunk.done' app/src/main/java/com/warped/data/remote/provider/OllamaProvider.kt`
- `grep -q 'api/tags' app/src/main/java/com/warped/data/remote/api/OllamaApi.kt` — verify API endpoint exists
</acceptance_criteria>

### Task 6: LM Studio + Custom Providers + ProviderRouter
<read_first>
- app/src/main/java/com/warped/domain/provider/LlmProvider.kt
- app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt (LMStudioProvider reuses same protocol)
- app/src/main/java/com/warped/data/remote/api/CustomApi.kt
- app/src/main/java/com/warped/domain/model/Endpoint.kt
- app/src/main/java/com/warped/domain/model/ProviderType.kt
- .planning/phases/01-foundation-remote-chat/01-RESEARCH.md §4.8 and §9.4 (lines 1072-1077 for LM Studio/Custom details, lines 1857-1918 for ProviderRouter + configure pattern)
</read_first>
<action>
1. **`app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt`** — LM Studio is OpenAI-compatible. Reuses the same `/v1/chat/completions` + SSE protocol. Defaults to `http://localhost:1234`:
```kotlin
package com.warped.data.remote.provider

import com.warped.data.remote.api.OpenAiApi
import com.warped.data.remote.dto.OpenAiChatRequest
import com.warped.data.remote.dto.OpenAiMessage
import com.warped.data.remote.dto.OpenAiStreamChunk
import com.warped.data.remote.network.SseEvent
import com.warped.data.remote.network.asSseFlow
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.Endpoint
import com.warped.domain.model.ModelInfo
import com.warped.domain.model.ProviderType
import com.warped.domain.model.StreamToken
import com.warped.domain.provider.LlmProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LMStudioProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : LlmProvider {

    override val type = ProviderType.LM_STUDIO

    private var api: OpenAiApi? = null

    fun configure(endpoint: Endpoint): LMStudioProvider {
        val baseUrl = endpoint.url.trimEnd('/')
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl + "/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        api = retrofit.create(OpenAiApi::class.java)
        return this
    }

    override fun chat(request: ChatRequest): Flow<StreamToken> = flow {
        val messages = request.messages.map { msg ->
            OpenAiMessage(role = msg.role.name.lowercase(), content = msg.content)
        }
        val apiRequest = OpenAiChatRequest(
            model = "",
            messages = messages,
            stream = true
        )
        val response = api!!.chatCompletions(apiRequest)
        if (!response.isSuccessful) {
            emit(StreamToken.Error("HTTP ${response.code()}: ${response.message()}"))
            return@flow
        }
        val body = response.body() ?: run {
            emit(StreamToken.Error("Empty response body"))
            return@flow
        }
        emitAll(body.asSseFlow(json) { event ->
            try {
                val chunk = json.decodeFromString<OpenAiStreamChunk>(event.data)
                val content = chunk.choices.firstOrNull()?.delta?.content
                if (content != null && content.isNotEmpty()) StreamToken.Delta(content)
                else if (chunk.choices.firstOrNull()?.finishReason != null) StreamToken.Done
                else StreamToken.Delta("")
            } catch (e: Exception) {
                StreamToken.Error("Failed to parse response token")
            }
        })
    }.flowOn(Dispatchers.IO)

    override suspend fun listModels(): Result<List<ModelInfo>> {
        return try {
            val response = api!!.listModels()
            if (response.isSuccessful) {
                val models = response.body()?.data?.map {
                    ModelInfo(id = it.id, name = it.id, providerType = ProviderType.LM_STUDIO)
                } ?: emptyList()
                Result.success(models)
            } else {
                Result.failure(IOException("HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun testConnection(): Result<ConnectionStatus> {
        return try {
            val response = api!!.listModels()
            if (response.isSuccessful) Result.success(ConnectionStatus.Connected)
            else Result.success(ConnectionStatus.Disconnected)
        } catch (e: Exception) {
            Result.success(ConnectionStatus.Disconnected)
        }
    }
}
```

2. **`app/src/main/java/com/warped/data/remote/provider/CustomProvider.kt`** — Uses `CustomApi` for user-configured base URLs. OpenAI-compatible protocol with configurable chat path and models path:
```kotlin
package com.warped.data.remote.provider

import com.warped.data.remote.api.CustomApi
import com.warped.data.remote.dto.OpenAiChatRequest
import com.warped.data.remote.dto.OpenAiMessage
import com.warped.data.remote.dto.OpenAiStreamChunk
import com.warped.data.remote.network.SseEvent
import com.warped.data.remote.network.asSseFlow
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.Endpoint
import com.warped.domain.model.ModelInfo
import com.warped.domain.model.ProviderType
import com.warped.domain.model.StreamToken
import com.warped.domain.provider.LlmProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : LlmProvider {

    override val type = ProviderType.CUSTOM

    private var baseUrl: String = ""
    private var api: CustomApi? = null

    fun configure(endpoint: Endpoint): CustomProvider {
        baseUrl = endpoint.url.trimEnd('/')
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl + "/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        api = retrofit.create(CustomApi::class.java)
        return this
    }

    override fun chat(request: ChatRequest): Flow<StreamToken> = flow {
        val messages = request.messages.map { msg ->
            OpenAiMessage(role = msg.role.name.lowercase(), content = msg.content)
        }
        val apiRequest = OpenAiChatRequest(
            model = "",
            messages = messages,
            stream = true
        )
        val response = api!!.chatCompletions("v1/chat/completions", apiRequest)
        if (!response.isSuccessful) {
            emit(StreamToken.Error("HTTP ${response.code()}: ${response.message()}"))
            return@flow
        }
        val body = response.body() ?: run {
            emit(StreamToken.Error("Empty response body"))
            return@flow
        }
        emitAll(body.asSseFlow(json) { event ->
            try {
                val chunk = json.decodeFromString<OpenAiStreamChunk>(event.data)
                val content = chunk.choices.firstOrNull()?.delta?.content
                if (content != null && content.isNotEmpty()) StreamToken.Delta(content)
                else if (chunk.choices.firstOrNull()?.finishReason != null) StreamToken.Done
                else StreamToken.Delta("")
            } catch (e: Exception) {
                StreamToken.Error("Failed to parse response token")
            }
        })
    }.flowOn(Dispatchers.IO)

    override suspend fun listModels(): Result<List<ModelInfo>> {
        return try {
            val response = api!!.listModels("v1/models")
            if (response.isSuccessful) {
                val models = response.body()?.data?.map {
                    ModelInfo(id = it.id, name = it.id, providerType = ProviderType.CUSTOM)
                } ?: emptyList()
                Result.success(models)
            } else {
                Result.failure(IOException("HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun testConnection(): Result<ConnectionStatus> {
        return try {
            val response = api!!.listModels("v1/models")
            if (response.isSuccessful) Result.success(ConnectionStatus.Connected)
            else Result.success(ConnectionStatus.Disconnected)
        } catch (e: Exception) {
            Result.success(ConnectionStatus.Disconnected)
        }
    }
}
```

3. **`app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt`** — Exact from RESEARCH.md §9.4 lines 1858-1874. Maps `ProviderType` → configured `LlmProvider`:
```kotlin
package com.warped.data.remote.provider

import com.warped.domain.model.Endpoint
import com.warped.domain.model.ProviderType
import com.warped.domain.provider.LlmProvider
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class ProviderRouter @Inject constructor(
    private val openAIProvider: Provider<OpenAIProvider>,
    private val ollamaProvider: Provider<OllamaProvider>,
    private val lmStudioProvider: Provider<LMStudioProvider>,
    private val customProvider: Provider<CustomProvider>
) {
    fun resolve(endpoint: Endpoint): LlmProvider = when (endpoint.apiType) {
        ProviderType.OPENAI -> openAIProvider.get().configure(endpoint)
        ProviderType.OLLAMA -> ollamaProvider.get().configure(endpoint)
        ProviderType.LM_STUDIO -> lmStudioProvider.get().configure(endpoint)
        ProviderType.CUSTOM -> customProvider.get().configure(endpoint)
        ProviderType.LOCAL -> error("Local provider not available in Phase 1")
    }
}
```
</action>
<acceptance_criteria>
- `grep -q 'class LMStudioProvider' app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt`
- `grep -q ': LlmProvider' app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt`
- `grep -q 'override val type = ProviderType.LM_STUDIO' app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt`
- `grep -q 'class CustomProvider' app/src/main/java/com/warped/data/remote/provider/CustomProvider.kt`
- `grep -q ': LlmProvider' app/src/main/java/com/warped/data/remote/provider/CustomProvider.kt`
- `grep -q 'override val type = ProviderType.CUSTOM' app/src/main/java/com/warped/data/remote/provider/CustomProvider.kt`
- `grep -q 'class ProviderRouter' app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt`
- `grep -q 'fun resolve.*Endpoint.*LlmProvider' app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt`
- `grep -q 'ProviderType.OPENAI ->' app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt`
- `grep -q 'ProviderType.OLLAMA ->' app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt`
- `grep -q 'ProviderType.LM_STUDIO ->' app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt`
- `grep -q 'ProviderType.CUSTOM ->' app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt`
- `grep -q 'ProviderType.LOCAL -> error' app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt`
</acceptance_criteria>
