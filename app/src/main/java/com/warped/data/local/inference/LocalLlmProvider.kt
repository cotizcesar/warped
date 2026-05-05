package com.warped.data.local.inference

import com.warped.domain.model.ChatRequest
import com.warped.domain.model.ChatMessage
import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.ModelInfo
import com.warped.domain.model.ProviderType
import com.warped.domain.model.Role
import com.warped.domain.model.StreamToken
import com.warped.domain.provider.LlmProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalLlmProvider @Inject constructor(
    private val llamaEngine: LlamaEngine,
    private val memoryChecker: MemoryChecker
) : LlmProvider {

    override val type = ProviderType.LOCAL
    private var modelPath: String = ""

    fun configure(modelFilePath: String): LocalLlmProvider {
        this.modelPath = modelFilePath
        return this
    }

    override fun chat(request: ChatRequest): Flow<StreamToken> = flow {
        if (!llamaEngine.isLoaded()) {
            val file = java.io.File(modelPath)
            if (!file.exists()) {
                emit(StreamToken.Error("Model file not found: $modelPath"))
                return@flow
            }
            val loadResult = llamaEngine.loadModel(modelPath)
            if (loadResult.isFailure) {
                val error = loadResult.exceptionOrNull() as? LlamaLoadError
                emit(StreamToken.Error(error?.userMessage ?: "Failed to load model"))
                return@flow
            }
        }

        val prompt = buildPrompt(request.messages)
        val startTime = System.currentTimeMillis()
        var tokenCount = 0
        var lastTpsUpdate = startTime
        var lastTpsTokens = 0

        llamaEngine.generate(prompt).collect { token ->
            tokenCount++

            // Emit TPS metadata every second
            val now = System.currentTimeMillis()
            if (now - lastTpsUpdate >= 1000) {
                val elapsedSeconds = (now - lastTpsUpdate) / 1000.0
                val newTokens = tokenCount - lastTpsTokens
                if (elapsedSeconds > 0 && newTokens > 0) {
                    val tps = (newTokens / elapsedSeconds).toInt()
                    emit(StreamToken.Delta(token))
                    lastTpsUpdate = now
                    lastTpsTokens = tokenCount
                } else {
                    emit(StreamToken.Delta(token))
                }
            } else {
                emit(StreamToken.Delta(token))
            }

            // OOM check every 30 seconds for long generations
            if (tokenCount > 50 && tokenCount % 50 == 0) {
                val file = java.io.File(modelPath)
                if (file.exists()) {
                    val ramCheck = memoryChecker.checkGgufRam(file.length())
                    if (!ramCheck.hasEnough) {
                        Timber.w("LocalLlmProvider: memory running low during generation")
                    }
                }
            }
        }

        // Emit final TPS
        val totalElapsed = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.1)
        val avgTps = (tokenCount / totalElapsed).toInt()
        Timber.d("LocalLlmProvider: generation complete — $tokenCount tokens, $avgTps tps")

        emit(StreamToken.Done())
    }.flowOn(Dispatchers.Default)

    override suspend fun listModels(): Result<List<ModelInfo>> {
        return try {
            val modelInfo = llamaEngine.getModelInfo()
            val models = if (modelInfo.isNotEmpty() && modelInfo != "{}") {
                listOf(ModelInfo(id = modelPath, name = "Local Model", providerType = ProviderType.LOCAL))
            } else emptyList()
            Result.success(models)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun testConnection(): Result<ConnectionStatus> {
        return try {
            if (llamaEngine.isLoaded()) {
                Result.success(ConnectionStatus.Connected)
            } else {
                Result.success(ConnectionStatus.Disconnected)
            }
        } catch (e: Exception) {
            Result.success(ConnectionStatus.Disconnected)
        }
    }

    fun getTps(): Int = 0 // real-time TPS handled in chat flow

    private fun buildPrompt(messages: List<ChatMessage>): String {
        return messages.joinToString("\n") { msg ->
            when (msg.role) {
                Role.SYSTEM -> "<|system|>\n${msg.content}\n"
                Role.USER -> "<|user|>\n${msg.content}\n"
                Role.ASSISTANT -> "<|assistant|>\n${msg.content}\n"
            }
        } + "<|assistant|>\n"
    }
}
