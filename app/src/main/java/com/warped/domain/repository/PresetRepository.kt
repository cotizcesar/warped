package com.warped.domain.repository

import com.warped.domain.model.Preset
import kotlinx.coroutines.flow.Flow

interface PresetRepository {
    fun observePresets(): Flow<List<Preset>>
    suspend fun getById(id: Long): Preset?
    suspend fun save(preset: Preset): Long
    suspend fun delete(id: Long)
}
