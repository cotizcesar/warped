package com.warped.data.remote.provider

import com.warped.data.local.inference.InputSanitizer
import com.warped.data.remote.api.OllamaApi
import com.warped.data.remote.dto.OllamaChatRequest
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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class OllamaProvider(
    private val baseUrl: String,
    private val modelId: String,
    private val inputSanitizer: InputSanitizer
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

    override fun chat(request: ChatRequest): Flow<StreamToken> = flow {
        val messages = request.messages
            .filter { it.content.isNotBlank() }
            .map {
                // Phase 49 (DEL-01): TOOL rows replay as plain user text —
                // the raw "<toolId>\n<summary>" encoding must never hit the wire.
                if (it.role == Role.TOOL) {
                    val (role, text) = it.toProviderText()
                    OllamaMessage(role = role, content = text)
                } else {
                    val content = if (it.role == Role.USER) inputSanitizer.sanitize(it.content) else it.content
                    OllamaMessage(role = it.role.name.lowercase(), content = content)
                }
            }
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
    }.flowOn(Dispatchers.IO)

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
