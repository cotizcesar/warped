---
phase: quick-260430-vsl
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/com/warped/data/remote/api/LmStudioApi.kt (NEW)
  - app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt
  - app/src/main/java/com/warped/ui/endpoints/components/EndpointForm.kt
  - app/src/main/java/com/warped/ui/models/ModelsUiState.kt
  - app/src/main/java/com/warped/ui/models/ModelsViewModel.kt
  - app/src/main/java/com/warped/ui/endpoints/EndpointsViewModel.kt
autonomous: true
requirements: []

must_haves:
  truths:
    - "LM Studio native v1 REST API at /api/v1/chat is used instead of OpenAI-compatible endpoint"
    - "Endpoint form only offers LM_STUDIO as provider type (removed OpenAI/Anthropic/Ollama/Custom)"
    - "Existing LMStudioProvider rewritten to use native LmStudioApi"
  artifacts:
    - path: "app/src/main/java/com/warped/data/remote/api/LmStudioApi.kt"
      provides: "Native LM Studio v1 REST API endpoints"
    - path: "app/src/main/java/com/warped/ui/endpoints/components/EndpointForm.kt"
      provides: "Simplified provider type dropdown with only LM_STUDIO"
---

<objective>
Focus network endpoints exclusively on LM Studio. Rewrite LMStudioProvider to use native v1 REST API (/api/v1/chat, /api/v1/models). Remove OpenAI, Anthropic, Ollama, and Custom from the endpoint form UI. Simplify the codebase scope.
</objective>

<context>
<interfaces>
LM Studio native v1 REST API (same SSE chunk format as OpenAI):
- POST /api/v1/chat — {"model":"...", "messages":[...], "stream":true, "temperature":0.7, "max_tokens":-1}
- GET /api/v1/models — returns {"data":[{"id":"model-name",...}]}
- SSE chunks identical format to OpenAI: data: {"choices":[{"delta":{"content":"text"}}]}

Current LMStudioProvider uses OpenAiApi pointing to /v1/chat/completions (OpenAI-compat). Need new LmStudioApi with native v1 paths.
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Create native LmStudioApi and rewrite LMStudioProvider</name>
  <files>
    app/src/main/java/com/warped/data/remote/api/LmStudioApi.kt (NEW),
    app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt
  </files>
  <action>

**New file — LmStudioApi.kt:**
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

interface LmStudioApi {
    @POST("api/v1/chat")
    @Headers("Content-Type: application/json")
    suspend fun chat(@Body request: OpenAiChatRequest): Response<ResponseBody>

    @GET("api/v1/models")
    suspend fun listModels(): Response<OpenAiModelListResponse>
}
```
Reuses `OpenAiChatRequest` and `OpenAiModelListResponse` DTOs since LM Studio's native v1 format uses the same JSON structure.

**Rewrite — LMStudioProvider.kt:**

Replace the entire file. Use `LmStudioApi` instead of `OpenAiApi`. Same SSE parsing via `asSseFlow()`.

```kotlin
package com.warped.data.remote.provider

import com.warped.data.remote.api.LmStudioApi
import com.warped.data.remote.dto.OpenAiChatRequest
import com.warped.data.remote.dto.OpenAiMessage
import com.warped.data.remote.network.asSseFlow
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.ModelInfo
import com.warped.domain.model.ProviderType
import com.warped.domain.model.StreamToken
import com.warped.domain.provider.LlmProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class LMStudioProvider(
    private val baseUrl: String = "http://localhost:1234",
    private val modelId: String
) : LlmProvider {
    override val type = ProviderType.LM_STUDIO
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val api = retrofit.create(LmStudioApi::class.java)

    override fun chat(request: ChatRequest): Flow<StreamToken> = flow {
        val messages = request.messages.map {
            OpenAiMessage(role = it.role.name.lowercase(), content = it.content)
        }
        val body = OpenAiChatRequest(
            model = modelId,
            messages = messages,
            stream = true,
            temperature = request.parameters.temperature,
            topP = request.parameters.topP,
            maxTokens = request.parameters.maxTokens.takeIf { it > 0 } ?: -1
        )
        try {
            val response = api.chat(body)
            if (response.isSuccessful) {
                response.body()?.asSseFlow(json)?.collect { emit(it) }
            } else {
                val errorBody = response.errorBody()?.string() ?: response.message()
                emit(StreamToken.Error("HTTP ${response.code()}: $errorBody"))
            }
        } catch (e: Exception) {
            emit(StreamToken.Error("Connection failed: ${e.message}"))
        }
    }

    override suspend fun listModels(): Result<List<ModelInfo>> {
        return try {
            val response = api.listModels()
            if (response.isSuccessful) {
                val models = response.body()?.data?.map {
                    ModelInfo(id = it.id, name = it.id, providerType = ProviderType.LM_STUDIO)
                } ?: emptyList()
                Result.success(models)
            } else {
                Result.failure(Exception("HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun testConnection(): Result<ConnectionStatus> {
        return try {
            val response = api.listModels()
            if (response.isSuccessful) Result.success(ConnectionStatus.Connected)
            else Result.success(ConnectionStatus.Disconnected)
        } catch (e: Exception) {
            Result.success(ConnectionStatus.Disconnected)
        }
    }
}
```

Key changes from old LMStudioProvider:
- Uses `LmStudioApi` (native `/api/v1/chat` and `/api/v1/models`) instead of `OpenAiApi`
- Adds proper `OkHttpClient` with timeouts (was missing before)
- Better error handling with `errorBody().string()`
- Uses `maxTokens.takeIf { it > 0 } ?: -1` (LM Studio uses -1 for unlimited)
</action>
<verify>
<automated>grep -n "LmStudioApi\|api/v1/chat\|api/v1/models" app/src/main/java/com/warped/data/remote/api/LmStudioApi.kt app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt</automated>
</verify>
</task>

<task type="auto">
  <name>Task 2: Simplify UI — only LM_STUDIO in endpoint forms</name>
  <files>
    app/src/main/java/com/warped/ui/endpoints/components/EndpointForm.kt,
    app/src/main/java/com/warped/ui/models/ModelsUiState.kt,
    app/src/main/java/com/warped/ui/models/ModelsViewModel.kt,
    app/src/main/java/com/warped/ui/endpoints/EndpointsViewModel.kt
  </files>
  <action>

**EndpointForm.kt** — Change provider types list from `["OPENAI", "ANTHROPIC", "OLLAMA", "LM_STUDIO", "CUSTOM"]` to just:
```kotlin
val providerTypes = listOf("LM_STUDIO")
```

Also update the `showAddForm()` default in EndpointsViewModel:
```kotlin
formApiType = "LM_STUDIO",
```

Also update `showAddForm()` default in ModelsViewModel (if the method exists with similar defaults).

**ModelsUiState.kt** — Change default for endpoint form:
```kotlin
val formApiType: String = "LM_STUDIO",
```

**ModelsViewModel.kt** — If `showEndpointForm()` or similar exists, change default:
```kotlin
formApiType = "LM_STUDIO",
```

**EndpointsViewModel.kt** — Line 46: Change:
```kotlin
formApiType = "OPENAI",
```
to:
```kotlin
formApiType = "LM_STUDIO",
```

Also update `showAddWizard` in ModelsScreen or wherever the endpoint form is triggered.
</action>
<verify>
<automated>grep -n "LM_STUDIO\|providerTypes\|formApiType" app/src/main/java/com/warped/ui/endpoints/components/EndpointForm.kt app/src/main/java/com/warped/ui/endpoints/EndpointsViewModel.kt app/src/main/java/com/warped/ui/models/ModelsUiState.kt app/src/main/java/com/warped/ui/models/ModelsViewModel.kt</automated>
</verify>
</task>

</tasks>

<success_criteria>
- LMStudioProvider uses native LmStudioApi with /api/v1/chat and /api/v1/models
- Endpoint form only offers LM_STUDIO (no OpenAI, Anthropic, Ollama, Custom)
- All form defaults set to LM_STUDIO
- Build compiles
</success_criteria>
