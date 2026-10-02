package com.warped.domain.repository

import com.warped.domain.model.ChatMessage
import com.warped.domain.model.Conversation
import com.warped.domain.model.GroundedSource
import com.warped.domain.model.ProviderType
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface ChatRepository {
    fun observeConversations(): Flow<List<Conversation>>
    suspend fun loadConversation(conversationId: Long): Pair<Conversation, List<ChatMessage>>?
    suspend fun createConversation(title: String, providerType: ProviderType, modelId: String?, endpointId: Long): Long
    suspend fun saveMessage(conversationId: Long, message: ChatMessage)
    suspend fun updateConversationTitle(conversationId: Long, title: String)
    suspend fun deleteConversation(conversationId: Long)
    suspend fun deleteAllConversations()
    suspend fun deleteMessage(messageId: Long)
    /**
     * Phase 68 (VMSG-06, WR-05): source-of-truth audio path for one row.
     * Delete cleanup resolves here first so unloaded-row deletes still
     * remove their clips; null when the row is gone or carries no audio.
     */
    suspend fun getMessageAudioPath(messageId: Long): String?
    // Phase 53 (SRC-01/TOGGLE-01): source + override contracts. Implementation in 53-02.
    /** Inserts the message plus its source rows; returns the assistant row id. */
    suspend fun saveMessageWithSources(
        conversationId: Long,
        message: ChatMessage,
        sources: List<GroundedSource>,
    ): Long
    /** Source rows for one assistant message, in source_index order. */
    suspend fun getSourcesByMessage(messageId: Long): List<GroundedSource>
    /** Tri-state override: null = inherit global default-ON. */
    suspend fun getWebOverride(conversationId: Long): Boolean?
    suspend fun setWebOverride(conversationId: Long, override: Boolean?)
    /**
     * Phase 54 (RETRY-01): row-reuse write for offline retry. Resolves the
     * assistant row by (conversationId, assistantCreatedAt), then
     * delete-then-inserts the source rows keyed by that row id. NEVER
     * re-saves the message — MessageDao.insert uses REPLACE and would
     * CASCADE-wipe the rows just written. Null row id (message deleted) is
     * a silent no-op that never inserts orphan rows. No schema change.
     */
    suspend fun replaceSources(
        conversationId: Long,
        assistantCreatedAt: Instant,
        sources: List<GroundedSource>,
    )
}
