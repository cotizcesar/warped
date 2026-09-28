package com.warped.domain.skills

import kotlinx.coroutines.flow.StateFlow

/**
 * 47-01: repository over the enabled tool skills.
 *
 * Backed by [com.warped.data.skills.SkillPreferences] (DataStore, all-on
 * defaults) projected through the shared descriptors. ViewModel-collected,
 * so toggle state survives rotation; DataStore persistence survives
 * process death.
 */
interface SkillRepository {
    /** Currently enabled tool skills (order = [SkillIds.TOOL_IDS]). */
    val enabledSkills: StateFlow<List<Skill.Tool>>

    /** Raw per-skill toggle map (id → enabled), includes all known tool ids. */
    val enabledMap: StateFlow<Map<String, Boolean>>

    suspend fun setEnabled(id: String, enabled: Boolean)
}
