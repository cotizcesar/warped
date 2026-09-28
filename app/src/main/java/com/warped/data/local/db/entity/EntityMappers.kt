package com.warped.data.local.db.entity

import com.warped.domain.model.ChatMessage
import com.warped.domain.model.Conversation
import com.warped.domain.model.Endpoint
import com.warped.domain.model.GroundedSource
import com.warped.domain.model.GroundedSourceStatus
import com.warped.domain.model.ProviderType
import com.warped.domain.model.Role
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

private val mapperJson = Json

/**
 * 47-01 (threat T-47-02): the role column is untrusted stored text hitting
 * [Role.valueOf]. Unknown values (old DBs, future roles) map to a safe
 * default instead of throwing into history load. `tool` (any case) maps to
 * [Role.TOOL]; anything else unrecognized degrades to ASSISTANT-adjacent
 * rendering, never a crash.
 */
fun String.toRoleSafe(): Role = try {
    Role.valueOf(this)
} catch (_: IllegalArgumentException) {
    if (equals("tool", ignoreCase = true)) Role.TOOL else Role.ASSISTANT
}

fun MessageEntity.toDomain(): ChatMessage = ChatMessage(
    id = id.toString(),
    role = role.toRoleSafe(),
    content = content,
    tokenCount = tokenCount,
    createdAt = Instant.ofEpochMilli(createdAt),
    stats = stats,
    reasoning = reasoning,
    imageUris = images?.let { 
        try { mapperJson.decodeFromString<List<String>>(it) } catch (_: Exception) { emptyList() }
    } ?: emptyList()
)

fun ChatMessage.toEntity(conversationId: Long): MessageEntity = MessageEntity(
    conversationId = conversationId,
    role = role.name,
    content = content,
    tokenCount = tokenCount,
    createdAt = createdAt.toEpochMilli(),
    stats = stats,
    reasoning = reasoning,
    images = if (imageUris.isNotEmpty()) mapperJson.encodeToString(imageUris) else null
)

fun ConversationEntity.toDomain(): Conversation = Conversation(
    id = id,
    title = title,
    providerType = ProviderType.valueOf(providerType),
    endpointId = endpointId,
    modelId = modelId,
    systemPrompt = systemPrompt,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    webOverride = webOverride,
)

fun Conversation.toEntity(): ConversationEntity = ConversationEntity(
    id = id,
    title = title,
    providerType = providerType.name,
    endpointId = endpointId,
    modelId = modelId,
    systemPrompt = systemPrompt,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    webOverride = webOverride,
)

/**
 * Phase 53 (threat T-53-01): the status column is untrusted stored text.
 * Unknown values (old DBs, future statuses) map to OMITIDA — struck rendering,
 * never a crash. Same drop-unknown precedent as [String.toRoleSafe].
 */
fun String.toGroundedSourceStatusSafe(): GroundedSourceStatus {
    if (equals("ok", ignoreCase = true)) return GroundedSourceStatus.OK
    return GroundedSourceStatus.OMITIDA
}

fun GroundedSourceStatus.toStorage(): String = when (this) {
    GroundedSourceStatus.OK -> "ok"
    GroundedSourceStatus.OMITIDA -> "omitida"
}

fun GroundedSourceEntity.toDomain(): GroundedSource = GroundedSource(
    url = resolvedUrl,
    extractedText = extractedText,
    status = status.toGroundedSourceStatusSafe(),
)

fun GroundedSource.toEntity(messageId: Long, sourceIndex: Int): GroundedSourceEntity =
    GroundedSourceEntity(
        messageId = messageId,
        sourceIndex = sourceIndex,
        resolvedUrl = url,
        extractedText = extractedText,
        status = status.toStorage(),
    )

fun RemoteEndpointEntity.toDomain(): Endpoint = Endpoint(
    id = id,
    name = name,
    url = url,
    apiType = ProviderType.valueOf(apiType),
    modelId = modelId,
    isActive = isActive,
    createdAt = Instant.ofEpochMilli(createdAt)
)

fun Endpoint.toEntity(encryptedApiKeyRef: String? = null): RemoteEndpointEntity = RemoteEndpointEntity(
    id = id,
    name = name,
    url = url,
    apiType = apiType.name,
    modelId = modelId,
    encryptedApiKeyRef = encryptedApiKeyRef,
    createdAt = createdAt.toEpochMilli(),
    isActive = isActive
)
