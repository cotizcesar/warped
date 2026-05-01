package com.warped.data.repository

import com.warped.data.local.db.dao.PresetDao
import com.warped.data.local.db.entity.toDomain
import com.warped.data.local.db.entity.toEntity
import com.warped.domain.model.Preset
import com.warped.domain.repository.PresetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PresetRepositoryImpl @Inject constructor(
    private val presetDao: PresetDao
) : PresetRepository {
    override fun observePresets(): Flow<List<Preset>> =
        presetDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: Long): Preset? =
        presetDao.getById(id)?.toDomain()

    override suspend fun save(preset: Preset): Long =
        presetDao.upsert(preset.toEntity())

    override suspend fun delete(id: Long) {
        presetDao.deleteById(id)
    }
}
