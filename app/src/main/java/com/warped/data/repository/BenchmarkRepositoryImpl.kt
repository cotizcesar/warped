package com.warped.data.repository

import com.warped.data.local.db.dao.BenchmarkResultDao
import com.warped.data.local.db.entity.toDomain
import com.warped.data.local.db.entity.toEntity
import com.warped.domain.model.BenchmarkResult
import com.warped.domain.repository.BenchmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BenchmarkRepositoryImpl @Inject constructor(
    private val dao: BenchmarkResultDao,
) : BenchmarkRepository {
    override suspend fun insert(result: BenchmarkResult): Long =
        dao.insert(result.toEntity())

    override fun observeAll(): Flow<List<BenchmarkResult>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeByModel(modelId: String): Flow<List<BenchmarkResult>> =
        dao.observeByModel(modelId).map { list -> list.map { it.toDomain() } }
}
