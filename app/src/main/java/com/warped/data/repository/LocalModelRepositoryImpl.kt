package com.warped.data.repository

import com.warped.data.local.db.dao.LocalModelDao
import com.warped.data.local.db.entity.toDomain
import com.warped.data.local.db.entity.toEntity
import com.warped.domain.model.GenerationParameters
import com.warped.domain.model.LocalModel
import com.warped.domain.repository.LocalModelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalModelRepositoryImpl @Inject constructor(
    private val localModelDao: LocalModelDao
) : LocalModelRepository {

    override fun observeModels(): Flow<List<LocalModel>> =
        localModelDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: Long): LocalModel? =
        localModelDao.getById(id)?.toDomain()

    override suspend fun getByFilePath(filePath: String): LocalModel? =
        localModelDao.getByFilePath(filePath)?.toDomain()

    override suspend fun existsByFilePath(filePath: String): Boolean =
        localModelDao.existsByFilePath(filePath)

    override suspend fun saveModel(model: LocalModel): Long =
        localModelDao.upsert(model.toEntity())

    override suspend fun updateParameters(modelId: Long, parameters: GenerationParameters) {
        localModelDao.updateParameters(
            id = modelId,
            temperature = parameters.temperature,
            topP = parameters.topP,
            topK = parameters.topK,
            repeatPenalty = parameters.repeatPenalty,
            maxTokens = parameters.maxTokens,
            contextSize = parameters.contextSize,
            seed = parameters.seed
        )
    }

    override suspend fun deleteModel(id: Long) {
        localModelDao.deleteById(id)
    }

    override suspend fun deleteByFilePath(filePath: String): Int =
        localModelDao.deleteByFilePath(filePath)
}
