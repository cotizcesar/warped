package com.warped.data.local.preferences

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

private val Context.voicePreferencesStore: DataStore<Preferences> by preferencesDataStore(name = "voice_preferences")

/**
 * Phase 69 Plan 03 (VMSG-03): per-install voice UI flags. Mirrors
 * [WizardPreferences] exactly (Singleton/Inject/store/boolean-key/edit).
 *
 * The coachmark flag is behavioral, not security state: worst case on
 * corruption is one re-show. No gate or send-path decision reads it.
 */
@Singleton
class VoicePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        val KEY_VOICE_COACHMARK_SEEN = booleanPreferencesKey("voice_coachmark_seen")
    }

    val voiceCoachmarkSeen: Flow<Boolean> = context.voicePreferencesStore.data.map { prefs ->
        prefs[KEY_VOICE_COACHMARK_SEEN] ?: false
    }

    suspend fun markVoiceCoachmarkSeen() {
        context.voicePreferencesStore.edit { prefs ->
            prefs[KEY_VOICE_COACHMARK_SEEN] = true
        }
    }
}
