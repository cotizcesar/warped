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

@Deprecated("Use LiteRTLmProvider instead", ReplaceWith("LiteRTLmProvider"))
@Singleton
class LocalLlmProvider @Inject constructor() : LlmProvider {

    override val type = ProviderType.LITE_RT_LM

    override fun chat(request: ChatRequest): Flow<StreamToken> = flow {
        emit(StreamToken.Error("LocalLlmProvider is deprecated — use LiteRTLmProvider"))
    }.flowOn(Dispatchers.Default)

    override suspend fun listModels(): Result<List<ModelInfo>> = Result.success(emptyList())

    override suspend fun testConnection(): Result<ConnectionStatus> = Result.success(ConnectionStatus.Disconnected)

    private fun buildPrompt(messages: List<ChatMessage>): String {
        return messages.joinToString("\n") { msg ->
            when (msg.role) {
                Role.SYSTEM -> "<|system|>\n${msg.content}\n"
                Role.USER -> "<|user|>\n${msg.content}\n"
                Role.ASSISTANT -> "<|assistant|>\n${msg.content}\n"
                // 47-01: tool rows render assistant-adjacent, never crash.
                Role.TOOL -> "<|assistant|>\n${msg.content}\n"
            }
        } + "<|assistant|>\n"
    }
}
