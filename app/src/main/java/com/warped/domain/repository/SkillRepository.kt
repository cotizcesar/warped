package com.warped.domain.repository

import com.warped.domain.skill.Skill
import kotlinx.coroutines.flow.Flow

interface SkillRepository {
    val enabledSkills: Flow<List<Skill>>
    suspend fun setEnabled(id: String, enabled: Boolean)
}
