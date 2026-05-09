package com.warped.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.wizardPreferencesStore: DataStore<Preferences> by preferencesDataStore(name = "wizard_preferences")

@Singleton
class WizardPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        val KEY_WIZARD_COMPLETED = booleanPreferencesKey("wizard_completed")
        val KEY_SKIPPED_STEPS = stringSetPreferencesKey("skipped_steps")
    }

    val isWizardComplete: Flow<Boolean> = context.wizardPreferencesStore.data.map { prefs ->
        prefs[KEY_WIZARD_COMPLETED] ?: false
    }

    val skippedSteps: Flow<Set<String>> = context.wizardPreferencesStore.data.map { prefs ->
        prefs[KEY_SKIPPED_STEPS] ?: emptySet()
    }

    suspend fun markWizardComplete() {
        context.wizardPreferencesStore.edit { prefs ->
            prefs[KEY_WIZARD_COMPLETED] = true
        }
    }

    suspend fun markStepsSkipped(steps: Set<String>) {
        context.wizardPreferencesStore.edit { prefs ->
            val existing = prefs[KEY_SKIPPED_STEPS] ?: emptySet()
            prefs[KEY_SKIPPED_STEPS] = existing + steps
        }
    }

    suspend fun resetWizard() {
        context.wizardPreferencesStore.edit { prefs ->
            prefs.remove(KEY_WIZARD_COMPLETED)
            prefs.remove(KEY_SKIPPED_STEPS)
        }
    }
}
