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
    // WR-03: rows use autoGenerate ids, so REPLACE never conflicts and no
    // unique index backs (message_id, source_index) on v15. Re-saving a
    // message (retry/double-persist) must not stack duplicate rows — the
    // repository deletes per message before inserting (delete-then-insert),
    // keeping ORDER BY source_index a true ordering invariant without a
    // schema migration.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<GroundedSourceEntity>)

    @Query("DELETE FROM grounded_sources WHERE message_id = :messageId")
    suspend fun deleteByMessage(messageId: Long)

    @Query("SELECT * FROM grounded_sources WHERE message_id = :messageId ORDER BY source_index ASC")
    suspend fun getByMessage(messageId: Long): List<GroundedSourceEntity>
}
