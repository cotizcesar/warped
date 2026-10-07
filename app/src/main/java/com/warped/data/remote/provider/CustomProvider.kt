package com.warped.data.remote.provider

import com.warped.data.remote.api.CustomApi
import com.warped.data.remote.dto.OpenAiChatRequest
import com.warped.data.remote.network.asSseFlow
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.Role
import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.ModelInfo
import com.warped.domain.model.ProviderType
import com.warped.domain.model.StreamToken
import com.warped.domain.provider.LlmProvider
import com.warped.data.agentic.ToolCapabilityMatrix
import com.warped.data.grounding.GroundingPrecedence
import com.warped.data.grounding.MultiUrlFetcher
import com.warped.data.grounding.DuckDuckGoSearchRepository
import com.warped.data.grounding.WebPageFetcher
import com.warped.data.local.inference.InputSanitizer
import com.warped.data.local.preferences.AdvancedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import timber.log.Timber
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class CustomProvider(
    private val baseUrl: String,
    private val modelId: String,
    private val chatPath: String = "v1/chat/completions",
    private val modelsPath: String = "v1/models",
    apiKey: String? = null,
    /**
     * Phase 57 (CR-01 fix): sanitizer wired from [ProviderRouter] like
     * every sibling provider. Stored here; applied to USER content on
     * both the armed mapping and the plain path (WR-02).
     */
    private val inputSanitizer: InputSanitizer? = null,
    /**
     * Phase 57 (57-02): tool-loop collaborators (Phase 55/52 singletons).
     * All-null by default so the legacy `resolve()` path behaves exactly
     * as before — the compat loop only arms when every collaborator is
     * present (see [isLoopArmed]). The endpoint key stays on this
     * client's interceptor ONLY; loop code never references it (T-57-07).
     *
     * Quick-task (DDG-default): the search collaborator is the DDG-only
     * repository.
     */
    private val ddg: DuckDuckGoSearchRepository? = null,
    private val multiUrlFetcher: MultiUrlFetcher? = null,
    private val webPageFetcher: WebPageFetcher? = null,
    private val advancedPreferences: AdvancedPreferences? = null,
) : LlmProvider {
    override val type = ProviderType.CUSTOM
    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .apply {
            if (!apiKey.isNullOrBlank()) {
                addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("Authorization", "Bearer $apiKey")
                        .build()
                    chain.proceed(request)
                }
            }
        }
        .build()

    private val retrofit = Retrofit.Builder()
        .client(client)
        .baseUrl(baseUrl)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val api = retrofit.create(CustomApi::class.java)

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
        // Phase 57 (57-02): armed turns run the shared compat loop
        // against the configured chat completions path (unknown/custom
        // servers default to attempt-then-fallback per the locked
        // decision); unarmed turns keep the exact Retrofit path.
        if (isLoopArmed(request.webOverride)) {
            // Quick-task (remote-image-carry): compat rounds carry history
            // images as `image_url` parts (shared K=3 rule).
            val baseMessages = mapOpenAiHistory(
                request.messages,
                includeSystem = true,
                sanitizeUser = { inputSanitizer?.sanitize(it) ?: it },
                currentImages = request.images,
            )
            val ddgRepo = ddg
            val fetchAll = multiUrlFetcher
            val net = webPageFetcher
            if (ddgRepo != null && fetchAll != null && net != null) {
                with(CompatToolLoop) {
                    runTurn(
                        client = client,
                        json = json,
                        postUrl = baseUrl.trimEnd('/') + "/" + chatPath.trimStart('/'),
                        modelId = modelId,
                        baseMessages = baseMessages,
                        request = request,
                        ddg = ddgRepo,
                        multiUrlFetcher = fetchAll,
                        webPageFetcher = net,
                        logTag = "Custom",
                        onCallCreated = { currentCall = it },
                        onCallCleared = { currentCall = null },
                    )
                }
                return@flow
            }
        }
        postPlainTurn(request)
    }.flowOn(Dispatchers.IO)

    /**
     * Phase 57 (57-02): provider-authoritative loop-arming. Unknown/custom
     * servers default to attempt-then-fallback: one `tools[]` attempt,
     * then exactly one retry without tools plus a visible notice.
     * Grounding precedence resolves the per-chat override against the
     * global default; internet must be validated. Missing collaborators
     * or any gate failure reads as unarmed (plain turn), never a crash.
     */
    private suspend fun isLoopArmed(perChat: Boolean?): Boolean {
        val prefs = advancedPreferences ?: return false
        val net = webPageFetcher ?: return false
        if (ddg == null || multiUrlFetcher == null) return false
        if (!ToolCapabilityMatrix.attemptsTools(
                ToolCapabilityMatrix.modeFor(ProviderType.CUSTOM),
            )
        ) return false
        return try {
            val global = try {
                prefs.webGroundingEnabled.first()
            } catch (e: Exception) {
                Timber.w(e, "Custom: global grounding read failed, treating as off")
                false
            }
            val online = try {
                net.hasValidatedInternet()
            } catch (e: Exception) {
                Timber.w(e, "Custom: connectivity check failed, treating as offline")
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

    /** Phase 57 (57-02): the exact pre-57 Retrofit path, unchanged. */
    private suspend fun FlowCollector<StreamToken>.postPlainTurn(request: ChatRequest) {
        // Quick-task (remote-image-carry): shared K=3 history carry
        // (`image_url` parts); text-only rows map exactly as before.
        val messages = mapOpenAiHistory(
            request.messages,
            includeSystem = true,
            sanitizeUser = { inputSanitizer?.sanitize(it) ?: it },
            currentImages = request.images,
        )
        val body = OpenAiChatRequest(
            model = modelId,
            messages = messages,
            stream = true,
            temperature = request.parameters.temperature,
            topP = request.parameters.topP,
            topK = request.parameters.topK,
            repeatPenalty = request.parameters.repeatPenalty,
            maxTokens = request.parameters.maxTokens.takeIf { it > 0 },
            seed = request.parameters.seed.takeIf { it != -1 },
        )
        try {
            val response = api.chatCompletions(chatPath, body)
            if (response.isSuccessful) {
                response.body()?.asSseFlow(json)?.collect { emit(it) }
            } else {
                emit(StreamToken.Error("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(StreamToken.Error("Connection failed: ${e.message}"))
        }
    }

    override suspend fun listModels(): Result<List<ModelInfo>> {
        return try {
            val response = api.listModels(modelsPath)
            if (response.isSuccessful) {
                val models = response.body()?.data?.map {
                    ModelInfo(id = it.id, name = it.id, providerType = ProviderType.CUSTOM)
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
            val response = api.listModels(modelsPath)
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
