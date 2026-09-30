package com.warped.data.remote.provider

import com.warped.data.agentic.ToolCapabilityMatrix
import com.warped.data.grounding.GroundingPrecedence
import com.warped.data.grounding.MultiUrlFetcher
import com.warped.data.grounding.DuckDuckGoSearchRepository
import com.warped.data.grounding.WebPageFetcher
import com.warped.data.local.inference.InputSanitizer
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.remote.dto.OllamaChatRequest
import timber.log.Timber
import com.warped.data.remote.api.OllamaApi
import com.warped.data.remote.dto.OllamaCreateRequest
import com.warped.data.remote.dto.OllamaDeleteRequest
import com.warped.data.remote.dto.OllamaEmbedRequest
import com.warped.data.remote.dto.OllamaGenerateRequest
import com.warped.data.remote.dto.OllamaMessage
import com.warped.data.remote.dto.OllamaPullRequest
import com.warped.data.remote.dto.OllamaShowRequest
import com.warped.data.remote.network.asOllamaFlow
import com.warped.data.remote.network.asOllamaGenerateFlow
import com.warped.data.remote.network.asOllamaPullFlow
import com.warped.domain.model.ChatMessage
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.ModelInfo
import com.warped.domain.model.ProviderType
import com.warped.domain.model.Role
import com.warped.domain.model.StreamToken
import com.warped.domain.model.toProviderText
import com.warped.domain.provider.LlmProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class OllamaProvider(
    private val baseUrl: String,
    private val modelId: String,
    private val inputSanitizer: InputSanitizer,
    /**
     * Phase 57 (57-02): tool-loop collaborators (Phase 55/52 singletons).
     * All-null by default so the legacy `resolve()` path behaves exactly
     * as before — the compat loop only arms when every collaborator is
     * present (see [isLoopArmed]). Ollama takes no endpoint key; loop
     * code only ever touches the search/fetch singletons (T-57-07).
     *
     * Quick-task (DDG-default): the search collaborator is the DDG-primary
     * / Tavily-fallback repository.
     */
    private val ddg: DuckDuckGoSearchRepository? = null,
    private val multiUrlFetcher: MultiUrlFetcher? = null,
    private val webPageFetcher: WebPageFetcher? = null,
    private val advancedPreferences: AdvancedPreferences? = null,
) : LlmProvider {
    override val type = ProviderType.OLLAMA
    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val api = retrofit.create(OllamaApi::class.java)

    /**
     * Phase 57 (57-02): retained cancellable compat-round Call
     * (LMStudioProvider precedent, same as the 57-01 OpenAI loop).
     * `cancelChat()` tears down the in-flight socket; safe when idle.
     */
    @Volatile
    private var currentCall: Call? = null

    /** Belt-and-braces teardown of the in-flight round, if any. */
    fun cancelChat() {
        currentCall?.cancel()
    }

    override fun chat(request: ChatRequest): Flow<StreamToken> = flow {
        // Phase 57 (57-02): armed turns ride the OpenAI-compat `/v1`
        // chat-completions path with the shared compat loop (RESEARCH
        // route-a); unarmed turns keep the exact native `/api/chat` path.
        // Compat rounds use the Chat Completions envelope throughout —
        // never the native `tool_name` envelope (Pitfall 6).
        if (isLoopArmed(request.webOverride)) {
            // Quick-task (remote-image-carry): compat rounds carry history
            // images as `image_url` parts (shared K=3 rule).
            val baseMessages = mapOpenAiHistory(
                request.messages,
                includeSystem = true,
                sanitizeUser = inputSanitizer::sanitize,
            )
            val ddgRepo = ddg
            val fetchAll = multiUrlFetcher
            val net = webPageFetcher
            if (ddgRepo != null && fetchAll != null && net != null) {
                with(CompatToolLoop) {
                    runTurn(
                        client = client,
                        json = json,
                        postUrl = baseUrl.trimEnd('/') + "/v1/chat/completions",
                        modelId = modelId,
                        baseMessages = baseMessages,
                        request = request,
                        ddg = ddgRepo,
                        multiUrlFetcher = fetchAll,
                        webPageFetcher = net,
                        logTag = "Ollama",
                        onCallCreated = { currentCall = it },
                        onCallCleared = { currentCall = null },
                    )
                }
                return@flow
            }
        }
        postNativeTurn(request)
    }.flowOn(Dispatchers.IO)

    /**
     * Phase 57 (57-02): provider-authoritative loop-arming. The matrix
     * must attempt the OpenAI dialect for Ollama; grounding precedence
     * resolves the per-chat override against the global default; internet
     * must be validated. Missing collaborators or any gate failure reads
     * as unarmed (native turn), never a crash.
     */
    private suspend fun isLoopArmed(perChat: Boolean?): Boolean {
        val prefs = advancedPreferences ?: return false
        val net = webPageFetcher ?: return false
        if (ddg == null || multiUrlFetcher == null) return false
        if (!ToolCapabilityMatrix.attemptsTools(
                ToolCapabilityMatrix.modeFor(ProviderType.OLLAMA),
            )
        ) return false
        return try {
            val global = try {
                prefs.webGroundingEnabled.first()
            } catch (e: Exception) {
                Timber.w(e, "Ollama: global grounding read failed, treating as off")
                false
            }
            val online = try {
                net.hasValidatedInternet()
            } catch (e: Exception) {
                Timber.w(e, "Ollama: connectivity check failed, treating as offline")
                false
            }
            ToolCapabilityMatrix.isRemoteLoopArmed(
                groundingOn = GroundingPrecedence.shouldGround(perChat, global),
                matrixAttemptsTools = true,
                hasValidatedInternet = online,
            )
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Quick-task (remote-image-carry): native `/api/chat` history mapping
     * with the shared K=3 carry. Reproduces the pre-carry filter/sanitize
     * exactly (blank rows dropped, TOOL replays as plain text, USER text
     * sanitized); history USER turns selected by [HistoryImageCarry]
     * ride as native `images[]` (raw base64 — the data-URL prefix is
     * stripped per the Ollama wire format); every other row stays
     * text-only and byte-identical on the wire.
     */
    internal fun mapNativeMessages(messages: List<ChatMessage>): List<OllamaMessage> {
        val kept = HistoryImageCarry.selectKeptUrls(messages)
        return messages.mapIndexedNotNull { index, msg ->
            if (msg.content.isBlank()) return@mapIndexedNotNull null
            if (msg.role == Role.TOOL) {
                val (role, text) = msg.toProviderText()
                OllamaMessage(role = role, content = text)
            } else {
                val content = if (msg.role == Role.USER) inputSanitizer.sanitize(msg.content) else msg.content
                OllamaMessage(
                    role = msg.role.name.lowercase(),
                    content = content,
                    images = kept[index]?.mapNotNull(::ollamaRawImage)?.takeIf { it.isNotEmpty() },
                )
            }
        }
    }

    /** Phase 57 (57-02): the exact native `/api/chat` path, unchanged. */
    private suspend fun FlowCollector<StreamToken>.postNativeTurn(
        request: ChatRequest,
    ) {
        val messages = mapNativeMessages(request.messages)
        val body = OllamaChatRequest(
            model = modelId,
            messages = messages,
            stream = true,
            options = null
        )
        try {
            val response = api.chat(body)
            if (response.isSuccessful) {
                response.body()?.asOllamaFlow(json)?.collect { emit(it) }
            } else {
                emit(StreamToken.Error("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(StreamToken.Error("Connection failed: ${e.message}"))
        }
    }

    fun generate(prompt: String): Flow<StreamToken> = flow {
        val sanitizedPrompt = inputSanitizer.sanitize(prompt)
        val body = OllamaGenerateRequest(model = modelId, prompt = sanitizedPrompt, stream = true)
        try {
            val response = api.generate(body)
            if (response.isSuccessful) {
                response.body()?.asOllamaGenerateFlow(json)?.collect { emit(it) }
            } else {
                emit(StreamToken.Error("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(StreamToken.Error("Connection failed: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun embed(input: List<String>): Result<List<List<Float>>> {
        return try {
            val body = OllamaEmbedRequest(model = modelId, input = input)
            val response = api.embed(body)
            if (response.isSuccessful) {
                val embeddings = response.body()?.embeddings ?: emptyList()
                Result.success(embeddings)
            } else {
                Result.failure(Exception("HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listRunning(): Result<List<ModelInfo>> {
        return try {
            val response = api.ps()
            if (response.isSuccessful) {
                val models = response.body()?.models?.map {
                    ModelInfo(id = it.name, name = it.name, providerType = ProviderType.OLLAMA)
                } ?: emptyList()
                Result.success(models)
            } else {
                Result.failure(Exception("HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun showModel(modelName: String): Result<String> {
        return try {
            val response = api.show(OllamaShowRequest(modelName))
            if (response.isSuccessful) {
                val modelfile = response.body()?.modelfile ?: ""
                Result.success(modelfile)
            } else {
                Result.failure(Exception("HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createModel(modelName: String, modelfile: String): Flow<StreamToken> = flow {
        val body = OllamaCreateRequest(model = modelName, modelfile = modelfile, stream = true)
        try {
            val response = api.create(body)
            if (response.isSuccessful) {
                response.body()?.asOllamaGenerateFlow(json)?.collect { emit(it) }
            } else {
                emit(StreamToken.Error("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(StreamToken.Error("Connection failed: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun deleteModel(modelName: String): Result<Unit> {
        return try {
            val response = api.delete(OllamaDeleteRequest(modelName))
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun pullModel(modelName: String): Flow<StreamToken> = flow {
        val body = OllamaPullRequest(model = modelName, stream = true)
        try {
            val response = api.pull(body)
            if (response.isSuccessful) {
                response.body()?.asOllamaPullFlow(json)?.collect { emit(it) }
            } else {
                emit(StreamToken.Error("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(StreamToken.Error("Connection failed: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun listModels(): Result<List<ModelInfo>> {
        return try {
            val response = api.listModels()
            if (response.isSuccessful) {
                val models = response.body()?.models?.map {
                    ModelInfo(id = it.name, name = it.name, providerType = ProviderType.OLLAMA)
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
            if (response.isSuccessful) {
                Result.success(ConnectionStatus.Connected)
            } else {
                Result.success(ConnectionStatus.Disconnected)
            }
        } catch (e: Exception) {
            Result.success(ConnectionStatus.Disconnected)
        }
    }
}
