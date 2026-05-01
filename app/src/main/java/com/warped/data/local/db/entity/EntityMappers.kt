package com.warped.data.local.db.entity

import com.warped.domain.model.ChatMessage
import com.warped.domain.model.Conversation
import com.warped.domain.model.Endpoint
import com.warped.domain.model.ProviderType
import com.warped.domain.model.Role
import java.time.Instant

fun MessageEntity.toDomain(): ChatMessage = ChatMessage(
    id = id.toString(),
    role = Role.valueOf(role),
    content = content,
    tokenCount = tokenCount,
    createdAt = Instant.ofEpochMilli(createdAt)
)

fun ChatMessage.toEntity(conversationId: Long): MessageEntity = MessageEntity(
    conversationId = conversationId,
    role = role.name,
    content = content,
    tokenCount = tokenCount,
    createdAt = createdAt.toEpochMilli()
)

fun ConversationEntity.toDomain(): Conversation = Conversation(
    id = id,
    title = title,
    providerType = ProviderType.valueOf(providerType),
    endpointId = endpointId,
    modelId = modelId,
    systemPrompt = systemPrompt,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt)
)

fun Conversation.toEntity(): ConversationEntity = ConversationEntity(
    id = id,
    title = title,
    providerType = providerType.name,
    endpointId = endpointId,
    modelId = modelId,
    systemPrompt = systemPrompt,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli()
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
