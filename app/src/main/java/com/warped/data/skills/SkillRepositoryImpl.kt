package com.warped.data.skills

import com.warped.domain.skills.Skill
import com.warped.domain.skills.SkillIds
import com.warped.domain.skills.SkillRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 47-01: combines shared descriptors + [SkillPreferences] into the
 * [SkillRepository] surface. Zero skills enabled is legal (plain chat,
 * no tools[] sent, no notice).
 */
@Singleton
class SkillRepositoryImpl @Inject constructor(
    private val preferences: SkillPreferences,
) : SkillRepository {

    // Singleton-owned scope (no @ApplicationScope binding exists in this
    // project; a dedicated SupervisorJob scope is the established fallback).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val enabledMap: StateFlow<Map<String, Boolean>> = preferences.enabledMap
        .map { stored ->
            // Guarantee every known tool id is present even if the descriptor
            // set ever grows ahead of stored prefs (default true = all-on).
            SkillIds.TOOL_IDS.associateWith { id -> stored[id] ?: true }
        }
        .stateIn(scope, SharingStarted.Eagerly, SkillIds.TOOL_IDS.associateWith { true })

    override val enabledSkills: StateFlow<List<Skill.Tool>> = enabledMap
        .map { map ->
            SKILL_DESCRIPTORS.filter { map[it.id] == true }.map { it.toTool() }
        }
        .stateIn(scope, SharingStarted.Eagerly, SKILL_DESCRIPTORS.map { it.toTool() })

    override suspend fun setEnabled(id: String, enabled: Boolean) {
        preferences.setEnabled(id, enabled)
    }
}
