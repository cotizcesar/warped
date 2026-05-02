package com.warped.data.local.inference

import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.GenerationParameters
import com.warped.domain.model.ModelInfo
import com.warped.domain.model.ProviderType
import com.warped.domain.model.Role
import com.warped.domain.model.StreamToken
import com.warped.domain.provider.LlmProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiteRTLmProvider @Inject constructor(
    private val engineManager: EngineManager,
    private val inputSanitizer: InputSanitizer
) : LlmProvider {

    override val type = ProviderType.LITE_RT_LM

    override fun chat(request: ChatRequest): Flow<StreamToken> = flow {
        // Step 1: Sanitize all messages
        val sanitizedMessages = request.messages.map { msg ->
            msg.copy(content = inputSanitizer.sanitize(msg.content))
        }

        // Step 2: Pre-condition check
        val activeEngine = engineManager.getActiveEngine()
        if (activeEngine == null || activeEngine.type != EngineType.LITE_RT_LM) {
            emit(StreamToken.Error("No LiteRT-LM engine is loaded. Select a .litertlm model first."))
            return@flow
        }

        // Step 3: Build LiteRT-LM Messages from sanitized Warped ChatMessages
        val litertlmMessages = sanitizedMessages.map { msg ->
            when (msg.role) {
                Role.SYSTEM -> Message.system(msg.content)
                Role.USER -> Message.user(msg.content)
                Role.ASSISTANT -> Message.model(msg.content)
            }
        }

        // Last message is the current user message; previous are history
        val currentMessage = litertlmMessages.last()
        val historyMessages = litertlmMessages.dropLast(1)

        // Step 4: Map GenerationParameters → SamplerConfig
        val params = request.parameters
        val samplerConfig = SamplerConfig(
            topK = clamp(params.topK, 1, 100, "topK"),
            topP = clamp(params.topP.toDouble(), 0.0, 1.0, "topP"),
            temperature = clamp(params.temperature.toDouble(), 0.0, 2.0, "temperature"),
            seed = if (params.seed != -1) params.seed else 0
        )

        // Map maxTokens via extraContext
        val extraContext = mapOf<String, Any>(
            "max_output_tokens" to params.maxTokens
        )

        // Step 5: Create conversation config
        val conversationConfig = ConversationConfig(
            initialMessages = historyMessages,
            samplerConfig = samplerConfig,
            extraContext = extraContext
        )

        // Step 6: Send message with retry loop
        sendMessageWithRetry(currentMessage, conversationConfig, 0)
    }.flowOn(Dispatchers.Default)

    override suspend fun listModels(): Result<List<ModelInfo>> {
        return try {
            val active = engineManager.getActiveEngine()
            if (active != null && active.type == EngineType.LITE_RT_LM) {
                val modelInfo = ModelInfo(
                    id = active.modelPath,
                    name = active.modelPath.substringAfterLast("/"),
                    providerType = ProviderType.LITE_RT_LM
                )
                Result.success(listOf(modelInfo))
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun testConnection(): Result<ConnectionStatus> {
        return try {
            val engine = engineManager.getLiteRTLmEngine()
            if (engine.isInitialized()) {
                Result.success(ConnectionStatus.Connected)
            } else {
                Result.success(ConnectionStatus.Disconnected)
            }
        } catch (e: Exception) {
            Result.success(ConnectionStatus.Disconnected)
        }
    }

    // --- Private helpers ---

    private suspend fun FlowCollector<StreamToken>.sendMessageWithRetry(
        message: Message,
        conversationConfig: ConversationConfig,
        attempt: Int
    ) {
        val maxRetries = 2 // 3 total attempts

        try {
            // Pre-condition: engine is initialized
            val engine = engineManager.getLiteRTLmEngine()
            if (!engine.isInitialized()) {
                throw IllegalStateException("Engine not initialized")
            }

            // Create conversation for this request
            val conversation = engineManager.createLiteRTConversation(conversationConfig)

            // Stream tokens via sendMessageAsync(Message): Flow<Message>
            conversation.sendMessageAsync(message).collect { responseMsg ->
                val content = extractTextContent(responseMsg)
                if (content.isNotEmpty()) {
                    emit(StreamToken.Delta(content))
                }
            }

            // Post-condition: conversation should still be alive
            if (!conversation.isAlive) {
                throw IllegalStateException("Conversation not alive after streaming")
            }

            emit(StreamToken.Done())

        } catch (e: Exception) {
            val isEngineError = e is IllegalStateException ||
                e.message?.contains("not alive", ignoreCase = true) == true ||
                e.message?.contains("not initialized", ignoreCase = true) == true

            if (isEngineError && attempt < maxRetries) {
                Timber.w(e, "LiteRTLmProvider: engine error (attempt ${attempt + 1}/3), recovering...")

                // Reinitialize engine with same model path
                recoverEngine()

                Timber.w("LiteRTLmProvider: Engine recovered, retrying... (attempt ${attempt + 1})")

                // Retry
                sendMessageWithRetry(message, conversationConfig, attempt + 1)
            } else if (isEngineError) {
                // Exhausted retries
                Timber.e(e, "LiteRTLmProvider: engine failed to recover after $maxRetries retries")
                emit(StreamToken.Error("Engine failed to recover. Please reload the model manually."))
            } else {
                // Non-engine error — don't retry
                Timber.e(e, "LiteRTLmProvider: unexpected error")
                emit(StreamToken.Error("Chat error: ${e.message ?: "Unknown error"}"))
            }
        }
    }

    private fun recoverEngine() {
        val active = engineManager.getActiveEngine()
        if (active != null && active.type == EngineType.LITE_RT_LM) {
            val modelPath = active.modelPath
            try {
                engineManager.switchToLiteRT(modelPath)
                Timber.d("LiteRTLmProvider: engine recovered successfully")
            } catch (e: Exception) {
                Timber.e(e, "LiteRTLmProvider: engine recovery failed")
                throw e
            }
        }
    }

    private fun extractTextContent(message: Message): String {
        return try {
            message.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
        } catch (e: Exception) {
            Timber.w(e, "LiteRTLmProvider: failed to extract text from message")
            ""
        }
    }

    private fun clamp(value: Int, min: Int, max: Int, paramName: String): Int {
        return if (value < min) {
            Timber.w("LiteRTLmProvider: $paramName=$value clamped to min=$min")
            min
        } else if (value > max) {
            Timber.w("LiteRTLmProvider: $paramName=$value clamped to max=$max")
            max
        } else value
    }

    private fun clamp(value: Double, min: Double, max: Double, paramName: String): Double {
        return if (value < min) {
            Timber.w("LiteRTLmProvider: $paramName=$value clamped to min=$min")
            min
        } else if (value > max) {
            Timber.w("LiteRTLmProvider: $paramName=$value clamped to max=$max")
            max
        } else value
    }
}
