package com.warped.data.repository

import com.warped.data.local.db.dao.ConversationDao
import com.warped.data.local.db.dao.MessageDao
import com.warped.data.local.db.entity.ConversationEntity
import com.warped.data.local.db.entity.toDomain
import com.warped.data.local.db.entity.toEntity
import com.warped.domain.model.ChatMessage
import com.warped.domain.model.Conversation
import com.warped.domain.model.GroundedSource
import com.warped.domain.model.ProviderType
import com.warped.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) : ChatRepository {

    override fun observeConversations(): Flow<List<Conversation>> =
        conversationDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun loadConversation(conversationId: Long): Pair<Conversation, List<ChatMessage>>? {
        val conv = conversationDao.getById(conversationId) ?: return null
        val msgs = messageDao.getByConversation(conversationId).map { it.toDomain() }
        return conv.toDomain() to msgs
    }

    override suspend fun createConversation(title: String, providerType: ProviderType, modelId: String?, endpointId: Long): Long {
        val now = System.currentTimeMillis()
        val entity = ConversationEntity(
            title = title,
            createdAt = now,
            updatedAt = now,
            providerType = providerType.name,
            endpointId = endpointId,
            modelId = modelId,
            systemPrompt = null
        )
        return conversationDao.upsert(entity)
    }

    override suspend fun saveMessage(conversationId: Long, message: ChatMessage) {
        messageDao.insert(message.toEntity(conversationId))
        conversationDao.updateTimestamp(conversationId, System.currentTimeMillis())
    }

    override suspend fun updateConversationTitle(conversationId: Long, title: String) {
        val entity = conversationDao.getById(conversationId) ?: return
        conversationDao.upsert(entity.copy(title = title, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun deleteConversation(conversationId: Long) {
        conversationDao.deleteById(conversationId)
    }

    override suspend fun deleteAllConversations() {
        messageDao.deleteAll()
        conversationDao.deleteAll()
    }

    override suspend fun deleteMessage(messageId: Long) {
        messageDao.deleteById(messageId)
    }

    // Phase 53 (53-01 scaffolding): full source/override implementation lands in
    // 53-02. These stubs keep the interface-first contracts compiling.
    override suspend fun saveMessageWithSources(
        conversationId: Long,
        message: ChatMessage,
        sources: List<GroundedSource>,
    ): Long = throw NotImplementedError("53-02 implements source persistence")

    override suspend fun getSourcesByMessage(messageId: Long): List<GroundedSource> =
        throw NotImplementedError("53-02 implements source hydration")

    override suspend fun getWebOverride(conversationId: Long): Boolean? =
        throw NotImplementedError("53-02 implements override read")

    override suspend fun setWebOverride(conversationId: Long, override: Boolean?) {
        throw NotImplementedError("53-02 implements override write")
    }
}
