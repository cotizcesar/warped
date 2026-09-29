package com.warped.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    val role: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    /**
     * Phase 57 (57-01): streaming `tool_calls` fragments, accumulated per
     * `index` by `ToolCallAccumulator` (arguments arrive as JSON string
     * fragments, possibly split mid-escape). Additive under the provider's
     * `ignoreUnknownKeys` — text-only turns parse byte-identically.
     */
    @SerialName("tool_calls") val toolCalls: List<OpenAiToolCallDelta>? = null
)

/**
 * Phase 57 (57-01): one streaming `tool_calls` entry. `id`/`name` typically
 * arrive on the first fragment per index only; continuation chunks carry
 * `arguments` fragments alone (`index` defaults to 0 for servers that omit
 * it on single-call turns).
 */
@Serializable
data class OpenAiToolCallDelta(
    val index: Int = 0,
    val id: String? = null,
    val type: String? = null,
    val function: OpenAiFunctionDelta? = null
)

/** Phase 57 (57-01): the `function` half of an [OpenAiToolCallDelta]. */
@Serializable
data class OpenAiFunctionDelta(
    val name: String? = null,
    /** JSON string fragment (not a complete object until reassembled). */
    val arguments: String? = null
)

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

@Serializable
data class OllamaGenerateChunk(
    val model: String? = null,
    val response: String = "",
    val done: Boolean = false
)

@Serializable
data class OllamaPullChunk(
    val status: String = "",
    val digest: String? = null,
    val total: Long? = null,
    val completed: Long? = null
)
