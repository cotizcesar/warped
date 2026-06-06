package com.warped.domain.repository

import com.warped.domain.model.AllowlistEntry
import kotlinx.coroutines.flow.Flow

interface ModelAllowlistRepository {
    suspend fun getAll(): List<AllowlistEntry>
    fun observeAll(): Flow<List<AllowlistEntry>>
    fun findById(modelId: String): AllowlistEntry?
    fun supportsThinking(modelId: String): Boolean
    fun supportsSpeculativeDecoding(modelId: String): Boolean
    fun supportsVision(modelId: String): Boolean
}
