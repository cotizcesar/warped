package com.warped.data.local.inference.tools

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.toolPreferencesStore: DataStore<Preferences> by preferencesDataStore(name = "tool_preferences")

@Singleton
class ToolPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private fun key(toolId: String) = booleanPreferencesKey("tool_$toolId")

    val enabledTools: Flow<Set<String>> = context.toolPreferencesStore.data.map { prefs ->
        ToolDefinitions.all
            .filter { tool -> prefs[key(tool.id)] ?: tool.defaultEnabled }
            .map { it.id }
            .toSet()
    }

    suspend fun setEnabled(toolId: String, enabled: Boolean) {
        context.toolPreferencesStore.edit { prefs ->
            prefs[key(toolId)] = enabled
        }
    }

    suspend fun resetToDefaults() {
        context.toolPreferencesStore.edit { it.clear() }
    }
}
