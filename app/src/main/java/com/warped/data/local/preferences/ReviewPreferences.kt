package com.warped.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.reviewPreferencesStore: DataStore<Preferences> by preferencesDataStore(name = "review_preferences")

/**
 * Phase 66 (RATE-01): DataStore-backed ambient review trigger state.
 * Persists the completed-turn counter, the last prompt timestamp, and the
 * total prompt count driving the eligibility predicate in
 * [com.warped.domain.review.ReviewEligibility].
 *
 * Self-registering `@Singleton @Inject` with `@ApplicationContext` — no
 * Hilt module entry (same convention as [WizardPreferences]).
 */
@Singleton
class ReviewPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        val KEY_COMPLETED_TURNS = intPreferencesKey("completed_turns")
        val KEY_LAST_PROMPT_MILLIS = longPreferencesKey("last_prompt_millis")
        val KEY_PROMPT_COUNT = intPreferencesKey("prompt_count")
    }

    val completedTurns: Flow<Int> = context.reviewPreferencesStore.data.map { prefs ->
        prefs[KEY_COMPLETED_TURNS] ?: 0
    }

    val lastPromptMillis: Flow<Long> = context.reviewPreferencesStore.data.map { prefs ->
        prefs[KEY_LAST_PROMPT_MILLIS] ?: 0L
    }

    val promptCount: Flow<Int> = context.reviewPreferencesStore.data.map { prefs ->
        prefs[KEY_PROMPT_COUNT] ?: 0
    }

    suspend fun incrementCompletedTurns() {
        context.reviewPreferencesStore.edit { prefs ->
            prefs[KEY_COMPLETED_TURNS] = (prefs[KEY_COMPLETED_TURNS] ?: 0) + 1
        }
    }

    suspend fun recordPrompt(nowMillis: Long) {
        context.reviewPreferencesStore.edit { prefs ->
            prefs[KEY_LAST_PROMPT_MILLIS] = nowMillis
            prefs[KEY_PROMPT_COUNT] = (prefs[KEY_PROMPT_COUNT] ?: 0) + 1
        }
    }
}
