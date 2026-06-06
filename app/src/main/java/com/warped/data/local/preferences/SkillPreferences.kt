package com.warped.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.warped.domain.skill.SkillRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.skillsDataStore: DataStore<Preferences> by preferencesDataStore(name = "skills")

@Singleton
class SkillPreferences @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    private val store get() = context.skillsDataStore

    val enabledSkillIds: Flow<Set<String>> = store.data.map { prefs ->
        prefs[KEY_ENABLED_SKILLS] ?: SkillRegistry.all.map { it.id }.toSet()
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        store.edit { prefs ->
            val current = prefs[KEY_ENABLED_SKILLS] ?: SkillRegistry.all.map { it.id }.toSet()
            prefs[KEY_ENABLED_SKILLS] = if (enabled) current + id else current - id
        }
    }

    companion object {
        val KEY_ENABLED_SKILLS = stringSetPreferencesKey("enabled_skills")
    }
}
