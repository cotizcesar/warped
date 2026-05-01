package com.warped.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.warped.data.local.db.entity.RemoteEndpointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RemoteEndpointDao {
    @Query("SELECT * FROM endpoints ORDER BY created_at DESC")
    fun observeAll(): Flow<List<RemoteEndpointEntity>>

    @Query("SELECT * FROM endpoints WHERE id = :id")
    suspend fun getById(id: Long): RemoteEndpointEntity?

    @Query("SELECT * FROM endpoints WHERE is_active = 1 LIMIT 1")
    suspend fun getActive(): RemoteEndpointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(endpoint: RemoteEndpointEntity): Long

    @Query("UPDATE endpoints SET is_active = 0")
    suspend fun deactivateAll()

    @Query("UPDATE endpoints SET is_active = 1 WHERE id = :id")
    suspend fun activate(id: Long)

    @Query("DELETE FROM endpoints WHERE id = :id")
    suspend fun deleteById(id: Long)
}
