package com.warped.data.skills

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.warped.domain.skills.SkillIds
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.skillPreferencesStore: DataStore<Preferences> by preferencesDataStore(name = "skill_preferences")

/**
 * 47-01 (D-01): per-skill enable toggles. All-on defaults — first launch
 * shows all chips ON. Mirrors the [com.warped.data.local.preferences.AdvancedPreferences]
 * DataStore pattern. Keys are fixed per-skill ids, never user text
 * (threat boundary chips→DataStore).
 */
@Singleton
class SkillPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        fun keyFor(id: String) = booleanPreferencesKey("skill_enabled_$id")
    }

    /** id → enabled for every known tool id; missing key reads as `true`. */
    val enabledMap: Flow<Map<String, Boolean>> = context.skillPreferencesStore.data
        .map { prefs ->
            SkillIds.TOOL_IDS.associateWith { id -> prefs[keyFor(id)] ?: true }
        }
        .distinctUntilChanged()

    fun isEnabled(id: String): Flow<Boolean> = context.skillPreferencesStore.data
        .map { prefs -> prefs[keyFor(id)] ?: true }
        .distinctUntilChanged()

    suspend fun setEnabled(id: String, enabled: Boolean) {
        require(id in SkillIds.TOOL_IDS) { "Unknown skill id: $id" }
        context.skillPreferencesStore.edit { prefs ->
            prefs[keyFor(id)] = enabled
        }
    }
}
