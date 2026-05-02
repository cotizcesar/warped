package com.warped.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.warped.data.local.db.entity.DownloadCheckpointEntity

@Dao
interface DownloadCheckpointDao {
    @Query("SELECT * FROM download_checkpoints WHERE model_id = :modelId")
    suspend fun getCheckpoint(modelId: String): DownloadCheckpointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCheckpoint(checkpoint: DownloadCheckpointEntity)

    @Query("DELETE FROM download_checkpoints WHERE model_id = :modelId")
    suspend fun deleteCheckpoint(modelId: String)
}
