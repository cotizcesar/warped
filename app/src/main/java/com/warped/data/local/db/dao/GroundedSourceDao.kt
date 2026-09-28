package com.warped.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.warped.data.local.db.entity.GroundedSourceEntity

/**
 * Phase 53 (SRC-01): source rows by assistant message. All I/O goes through
 * ChatRepository — never call from ViewModel/Composable layers.
 */
@Dao
interface GroundedSourceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<GroundedSourceEntity>)

    @Query("SELECT * FROM grounded_sources WHERE message_id = :messageId ORDER BY source_index ASC")
    suspend fun getByMessage(messageId: Long): List<GroundedSourceEntity>
}
