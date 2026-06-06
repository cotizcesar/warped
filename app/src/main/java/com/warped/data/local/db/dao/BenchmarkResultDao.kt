package com.warped.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.warped.data.local.db.entity.BenchmarkResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BenchmarkResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(result: BenchmarkResultEntity): Long

    @Query("SELECT * FROM benchmark_results ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BenchmarkResultEntity>>

    @Query("SELECT * FROM benchmark_results WHERE modelId = :modelId ORDER BY createdAt DESC LIMIT 10")
    fun observeByModel(modelId: String): Flow<List<BenchmarkResultEntity>>

    @Query("SELECT COUNT(*) FROM benchmark_results")
    suspend fun count(): Int
}
