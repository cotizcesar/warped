package com.warped.data.local.inference

import android.util.Base64
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.LiteRtLmJniException
import com.warped.domain.model.ActiveModelSelection
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
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiteRTLmProvider @Inject constructor(
    private val engineManager: EngineManager,
    private val inputSanitizer: InputSanitizer,
    private val activeModelSelection: ActiveModelSelection
) : LlmProvider {

    override val type = ProviderType.LITE_RT_LM

    companion object {
        /** 0.17.x reasoning channel name (see [extractThoughtContent]). */
        const val THOUGHT_CHANNEL = "thought"
    }

    @Volatile
    private var activeConversation: Conversation? = null

    @Volatile
    private var activeConversationConfig: ConversationConfig? = null

    /**
     * Reset the active conversation. Called when the engine is reloaded or the model changes.
     * The next chat() call will lazily create a new conversation.
     */
    fun resetConversation() {
        synchronized(this) {
            activeConversation?.let { prev ->
                try { prev.close() } catch (e: Exception) { Timber.w(e, "LiteRTLm: resetConversation.close() failed") }
            }
            activeConversation = null
            activeConversationConfig = null
        }
    }

    override fun chat(request: ChatRequest): Flow<StreamToken> = flow {
        // Step 1: Sanitize all messages
        val sanitizedMessages = request.messages.map { msg ->
            msg.copy(content = inputSanitizer.sanitize(msg.content))
        }

        // Step 2: Load engine on-demand if not loaded
        val activeEngine = engineManager.getActiveEngine()
        if (activeEngine == null || activeEngine.type != EngineType.LITE_RT_LM) {
            // Try to load the engine from the active model path
            val modelPath = activeModelSelection.activeModel.value?.modelId
            // Engine-supported containers: .litertlm and .task (allowlist ships .task files).
            if (modelPath != null && (modelPath.endsWith(".litertlm", ignoreCase = true) ||
                modelPath.endsWith(".task", ignoreCase = true))) {
                try {
                    engineManager.switchToLiteRT(modelPath)
        } catch (e: LiteRtLmJniException) {
            Timber.e(e, "LiteRTLmProvider: JNI native error — ${e.message}")
            emit(StreamToken.Error("LiteRT-LM native error: ${e.message ?: "Unknown JNI error"}"))
            return@flow
        } catch (e: Exception) {
                    emit(StreamToken.Error("Failed to load LiteRT-LM engine: ${e.message}"))
                    return@flow
                }
            } else {
                emit(StreamToken.Error("No LiteRT-LM engine is loaded. Select a .litertlm or .task model first."))
                return@flow
            }
        }

        // Step 3: Build history messages (text-only, no images)
        val hasImages = request.images.isNotEmpty()
        val historyMessages = sanitizedMessages.map { msg ->
            when (msg.role) {
                Role.SYSTEM -> Message.system(msg.content)
                Role.USER -> Message.user(msg.content)
                Role.ASSISTANT -> Message.model(msg.content)
            }
        }.dropLast(1) // exclude current message from history

        // Step 4: Build current message contents (text + images + audio)
        val currentUserText = sanitizedMessages.lastOrNull { it.role == Role.USER }?.content ?: ""
        val audioBytes = request.audioBytes
        val hasAudio = audioBytes != null && audioBytes.isNotEmpty()
        val hasText = currentUserText.isNotBlank()
        val currentContents = if (hasImages || hasAudio) {
            val contentList = mutableListOf<Content>()
            if (hasAudio) {
                contentList.add(Content.AudioBytes(audioBytes))
                Timber.d("LiteRTLmProvider: attaching audio (${audioBytes.size} bytes)")
            }
            request.images.forEach { dataUrl ->
                decodeImage(dataUrl)?.let { contentList.add(Content.ImageBytes(it)) }
            }
            // Only include text content if the user actually provided text.
            // An image-only / audio-only message should not have an empty text part,
            // which LiteRT-LM rejects.
            if (hasText) {
                contentList.add(Content.Text(currentUserText))
            }
            Timber.d("LiteRTLmProvider: sending ${request.images.size} image(s) + audio=${hasAudio} + text=${hasText}")
            Contents.of(contentList)
        } else {
            Contents.of(currentUserText)
        }

        // Step 5: Map GenerationParameters -> SamplerConfig
        val params = request.parameters
        val samplerConfig = SamplerConfig(
            topK = clamp(params.topK, 1, 100, "topK"),
            topP = clamp(params.topP.toDouble(), 0.0, 1.0, "topP"),
            temperature = clamp(params.temperature.toDouble(), 0.0, 2.0, "temperature"),
            seed = if (params.seed != -1) params.seed else 0
        )

        // Step 6: Create conversation config with history, tools, and auto tool calling
        val conversationConfig = ConversationConfig(
            initialMessages = historyMessages,
            samplerConfig = samplerConfig,
            extraContext = emptyMap(),
            tools = emptyList(),
            automaticToolCalling = false
        )

        // Step 7: Send content with retry loop
        sendContentsWithRetry(currentContents, conversationConfig, 0)
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

    private suspend fun FlowCollector<StreamToken>.sendContentsWithRetry(
        contents: Contents,
        conversationConfig: ConversationConfig,
        attempt: Int
    ) {
        val maxRetries = 2

        try {
            val engine = engineManager.getLiteRTLmEngine()
            if (!engine.isInitialized()) {
                throw IllegalStateException("Engine not initialized")
            }

            // LRT-02: Reuse a single long-lived Conversation across chat() calls.
            // The conversation is only recreated when the underlying engine is reloaded
            // (see resetConversation()) or when a fatal error invalidates the native handle
            // (see retry path below). Initial history is set on first creation; subsequent
            // calls append to the conversation in-place.
            val conversation = synchronized(this@LiteRTLmProvider) {
                val existing = activeConversation
                if (existing != null && existing.isAlive) {
                    existing
                } else {
                    existing?.let { prev ->
                        try { prev.close() } catch (e: Exception) { Timber.w(e, "LiteRTLm: stale.close() failed") }
                    }
                    engineManager.createLiteRTConversation(conversationConfig).also {
                        activeConversation = it
                        activeConversationConfig = conversationConfig
                    }
                }
            }

            conversation.sendMessageAsync(contents).collect { responseMsg ->
                val content = extractTextContent(responseMsg)
                if (content.isNotEmpty()) {
                    Timber.d("LiteRTLmProvider: delta (${content.length} chars): %s", content.takeLast(100))
                    emit(StreamToken.Delta(content))
                }
            }

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
                // Null out the conversation — its native handle may be invalid after the error
                activeConversation = null
                activeConversationConfig = null
                recoverEngine()
                Timber.w("LiteRTLmProvider: Engine recovered, retrying... (attempt ${attempt + 1})")
                sendContentsWithRetry(contents, conversationConfig, attempt + 1)
            } else if (isEngineError) {
                Timber.e(e, "LiteRTLmProvider: engine failed to recover after $maxRetries retries")
                emit(StreamToken.Error("Engine failed to recover. Please reload the model manually."))
            } else {
                Timber.e(e, "LiteRTLmProvider: unexpected error")
                emit(StreamToken.Error("Chat error: ${e.message ?: "Unknown error"}"))
            }
        }
    }

    private fun recoverEngine() {
        val active = engineManager.getActiveEngine()
        if (active != null && active.type == EngineType.LITE_RT_LM) {
            val modelPath = active.modelPath
            // LRT-02: Drop the dead conversation; a new one will be created lazily on the
            // next chat() call. This must happen BEFORE switchToLiteRT() so we don't leak
            // a handle into a now-stale engine instance.
            resetConversation()
            try {
                engineManager.switchToLiteRT(modelPath)
                Timber.d("LiteRTLmProvider: engine recovered successfully")
            } catch (e: Exception) {
                Timber.e(e, "LiteRTLmProvider: engine recovery failed")
                throw e
            }
        }
    }

    private fun decodeImage(dataUrl: String): ByteArray? {
        return try {
            val base64 = dataUrl.substringAfter("base64,")
            Base64.decode(base64, Base64.DEFAULT)
        } catch (e: Exception) {
            Timber.w(e, "LiteRTLmProvider: failed to decode image")
            null
        }
    }

    private fun extractTextContent(message: Message): String {
        val raw = try {
            message.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
        } catch (e: Exception) {
            Timber.w(e, "LiteRTLmProvider: failed to extract text from message")
            ""
        }
        return Normalizer.normalize(raw, Normalizer.Form.NFC)
    }

    /**
     * 45-02 LRT-09 (0.17.x re-verification): read the reasoning stream from
     * `response.channels["thought"]` (verified: `Message.getChannels()` returns
     * `Map<String, String>` in litertlm-android-0.17.1). Returns null when the active
     * conversation has no thinking enabled. NOT wired into the chat path here — thought
     * tokens must never be interleaved into answer Deltas; thinking UX (THINK-02) and
     * the ThinkingConfig enablement belong to a later phase. Safe by construction:
     * with thinking disabled the channel is absent and this returns null.
     */
    fun extractThoughtContent(message: Message): String? {
        return try {
            message.channels[THOUGHT_CHANNEL]?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Timber.w(e, "LiteRTLmProvider: failed to extract thought channel")
            null
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
