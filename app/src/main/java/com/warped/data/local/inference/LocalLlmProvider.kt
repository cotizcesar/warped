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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalLlmProvider @Inject constructor(
    private val llamaEngine: LlamaEngine
) : LlmProvider {

    override val type = ProviderType.LOCAL
    private var modelPath: String = ""

    fun configure(modelFilePath: String): LocalLlmProvider {
        this.modelPath = modelFilePath
        return this
    }

    override fun chat(request: ChatRequest): Flow<StreamToken> = flow {
        if (!llamaEngine.isLoaded()) {
            val loadResult = llamaEngine.loadModel(modelPath)
            if (loadResult.isFailure) {
                val error = loadResult.exceptionOrNull() as? LlamaLoadError
                emit(StreamToken.Error(error?.userMessage ?: "Failed to load model"))
                return@flow
            }
        }

        val prompt = buildPrompt(request.messages)
        llamaEngine.generate(prompt).collect { token ->
            emit(StreamToken.Delta(token))
        }
        emit(StreamToken.Done())
    }.flowOn(Dispatchers.Default)

    override suspend fun listModels(): Result<List<ModelInfo>> {
        return try {
            val modelInfo = llamaEngine.getModelInfo()
            val models = if (modelInfo.isNotEmpty()) {
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
