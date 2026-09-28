package com.warped.data.repository

import com.warped.data.local.db.dao.ConversationDao
import com.warped.data.local.db.dao.GroundedSourceDao
import com.warped.data.local.db.dao.MessageDao
import com.warped.data.local.db.entity.ConversationEntity
import com.warped.data.local.db.entity.toDomain
import com.warped.data.local.db.entity.toEntity
import com.warped.domain.model.ChatMessage
import com.warped.domain.model.Conversation
import com.warped.domain.model.GroundedSource
import com.warped.domain.model.ProviderType
import com.warped.domain.model.Role
import com.warped.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val groundedSourceDao: GroundedSourceDao,
) : ChatRepository {

    override fun observeConversations(): Flow<List<Conversation>> =
        conversationDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun loadConversation(conversationId: Long): Pair<Conversation, List<ChatMessage>>? {
        val conv = conversationDao.getById(conversationId) ?: return null
        val entities = messageDao.getByConversation(conversationId)
        // Phase 53 (SRC-02): hydrate assistant messages with groundedSources
        // urls (ok AND omitida — fixing the okUrls-only drop) plus
        // groundedSourceDetails in source_index order. Only assistant rows
        // carry source rows, so user rows skip the extra query.
        val msgs = entities.map { entity ->
            val base = entity.toDomain()
            if (base.role != Role.ASSISTANT) {
                base
            } else {
                val details = groundedSourceDao.getByMessage(entity.id).map { it.toDomain() }
                base.copy(
                    groundedSources = details.map { it.url },
                    groundedSourceDetails = details,
                )
            }
        }
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

    // Phase 53 (SRC-02/TOGGLE-01): row-id save plus override read/write.
    //
    // WARNING for Phase 54 retry: MessageDao.insert uses REPLACE — re-saving
    // an already-persisted assistant message CASCADE-wipes its source rows.
    // Retry must READ rows via getSourcesByMessage, never re-save the message.
    //
    // Source rows key on the MessageDao.insert Long return — never on
    // ChatMessage.id, which is a UUID string unrelated to the DB row id
    // (toEntity drops it). Rows insert strictly AFTER the assistant insert.
    // Source-insert failure is Timber-logged and rethrown so the ViewModel
    // hook can surface the UI-SPEC persistence-failure Snackbar; the send
    // itself continues (transcript already updated).
    override suspend fun saveMessageWithSources(
        conversationId: Long,
        message: ChatMessage,
        sources: List<GroundedSource>,
    ): Long {
        val rowId = messageDao.insert(message.toEntity(conversationId))
        if (sources.isNotEmpty()) {
            try {
                // WR-03: delete-then-insert per message — REPLACE is a no-op
                // on autoGenerate ids, so a re-save would otherwise stack
                // duplicate (message_id, source_index) rows.
                groundedSourceDao.deleteByMessage(rowId)
                groundedSourceDao.insertAll(
                    sources.mapIndexed { index, source -> source.toEntity(rowId, index) },
                )
            } catch (e: Exception) {
                Timber.e(e, "ChatRepository: failed to persist %d grounded sources for message %d", sources.size, rowId)
                throw e
            }
        }
        conversationDao.updateTimestamp(conversationId, System.currentTimeMillis())
        return rowId
    }

    override suspend fun getSourcesByMessage(messageId: Long): List<GroundedSource> =
        groundedSourceDao.getByMessage(messageId).map { it.toDomain() }

    // Phase 54 (RETRY-01): row-reuse write — delete-then-insert on the
    // EXISTING assistant row, never a re-save (MessageDao.insert REPLACE
    // would CASCADE-wipe source rows per the warning above). Null row id
    // (message deleted after the turn) is a silent no-op: no delete, no
    // insert, no timestamp touch — orphan rows are never created.
    override suspend fun replaceSources(
        conversationId: Long,
        assistantCreatedAt: Instant,
        sources: List<GroundedSource>,
    ) {
        val rowId = messageDao.findAssistantRowId(
            conversationId,
            assistantCreatedAt.toEpochMilli(),
        ) ?: return
        groundedSourceDao.deleteByMessage(rowId)
        if (sources.isNotEmpty()) {
            groundedSourceDao.insertAll(
                sources.mapIndexed { index, source -> source.toEntity(rowId, index) },
            )
        }
        conversationDao.updateTimestamp(conversationId, System.currentTimeMillis())
    }

    override suspend fun getWebOverride(conversationId: Long): Boolean? =
        conversationDao.getWebOverride(conversationId)

    override suspend fun setWebOverride(conversationId: Long, override: Boolean?) {
        conversationDao.setWebOverride(conversationId, override)
    }
}
