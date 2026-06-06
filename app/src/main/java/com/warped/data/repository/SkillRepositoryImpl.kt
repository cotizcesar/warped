package com.warped.data.repository

import com.warped.data.local.preferences.SkillPreferences
import com.warped.domain.repository.SkillRepository
import com.warped.domain.skill.Skill
import com.warped.domain.skill.SkillRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkillRepositoryImpl @Inject constructor(
    private val preferences: SkillPreferences,
) : SkillRepository {
    override val enabledSkills: Flow<List<Skill>> = preferences.enabledSkillIds.map { ids ->
        SkillRegistry.all
            .filter { it.id in ids }
            .sortedBy { it.name }
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) {
        preferences.setEnabled(id, enabled)
    }
}
