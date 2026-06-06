package com.warped.domain.repository

import com.warped.domain.model.BenchmarkResult
import kotlinx.coroutines.flow.Flow

interface BenchmarkRepository {
    suspend fun insert(result: BenchmarkResult): Long
    fun observeAll(): Flow<List<BenchmarkResult>>
    fun observeByModel(modelId: String): Flow<List<BenchmarkResult>>
}
