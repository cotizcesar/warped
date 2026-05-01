package com.warped.domain.repository

import com.warped.domain.model.ChatMessage
import com.warped.domain.model.Conversation
import com.warped.domain.model.ProviderType
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeConversations(): Flow<List<Conversation>>
    suspend fun loadConversation(conversationId: Long): Pair<Conversation, List<ChatMessage>>?
    suspend fun createConversation(title: String, providerType: ProviderType, modelId: String?, endpointId: Long): Long
    suspend fun saveMessage(conversationId: Long, message: ChatMessage)
    suspend fun updateConversationTitle(conversationId: Long, title: String)
    suspend fun deleteConversation(conversationId: Long)
    suspend fun deleteAllConversations()
}
