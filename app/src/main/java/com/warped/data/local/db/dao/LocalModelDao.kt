package com.warped.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.warped.data.local.db.entity.LocalModelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalModelDao {
    @Query("SELECT * FROM local_models ORDER BY imported_at DESC")
    fun observeAll(): Flow<List<LocalModelEntity>>

    @Query("SELECT * FROM local_models WHERE id = :id")
    suspend fun getById(id: Long): LocalModelEntity?

    @Query("SELECT * FROM local_models WHERE file_path = :filePath")
    suspend fun getByFilePath(filePath: String): LocalModelEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM local_models WHERE file_path = :filePath)")
    suspend fun existsByFilePath(filePath: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(model: LocalModelEntity): Long

    @Query("""UPDATE local_models SET
        param_temperature = :temperature,
        param_top_p = :topP,
        param_top_k = :topK,
        param_repeat_penalty = :repeatPenalty,
        param_max_tokens = :maxTokens,
        param_context_size = :contextSize,
        param_seed = :seed
        WHERE id = :id""")
    suspend fun updateParameters(
        id: Long,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        maxTokens: Int,
        contextSize: Int,
        seed: Int
    )

    @Query("DELETE FROM local_models WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM local_models WHERE file_path = :filePath")
    suspend fun deleteByFilePath(filePath: String): Int
}
