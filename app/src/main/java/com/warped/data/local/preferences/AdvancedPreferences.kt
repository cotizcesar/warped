package com.warped.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.warped.domain.model.SyntaxTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private val Context.advancedPreferencesStore: DataStore<Preferences> by preferencesDataStore(name = "advanced_preferences")

@Singleton
class AdvancedPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        val KEY_CODE_THEME = stringPreferencesKey("code_theme")
        val KEY_CODE_FONT_SCALE = floatPreferencesKey("code_font_scale")
        val KEY_CACHE_MAX_SIZE_BYTES = longPreferencesKey("cache_max_size_bytes")
        val KEY_THINKING_ENABLED = booleanPreferencesKey("thinking_enabled")
        const val DEFAULT_CACHE_MAX_SIZE_BYTES: Long = 500L * 1024L * 1024L
        const val MIN_CACHE_MAX_SIZE_BYTES: Long = 50L * 1024L * 1024L
    }

    /**
     * Perform a one-time migration from legacy CodeTheme enum names
     * to SyntaxTheme keys. Safe to call multiple times — only writes
     * when a legacy value is detected in the DataStore.
     */
    suspend fun migrateCodeThemeIfNeeded() {
        context.advancedPreferencesStore.edit { prefs ->
            val stored = prefs[KEY_CODE_THEME] ?: return@edit
            val migrated = migrateCodeTheme(stored)
            if (migrated.key != stored) {
                prefs[KEY_CODE_THEME] = migrated.key
            }
        }
    }

    private fun migrateCodeTheme(oldName: String): SyntaxTheme = when (oldName) {
        "MONOKAI" -> SyntaxTheme.MONOKAI
        "DRACULA" -> SyntaxTheme.DRACULA
        "NORD" -> SyntaxTheme.ONE_DARK
        "ONE_DARK" -> SyntaxTheme.ONE_DARK
        "GITHUB" -> SyntaxTheme.GITHUB
        "SOLARIZED_DARK" -> SyntaxTheme.MONOKAI
        else -> {
            Timber.w("AdvancedPreferences: unrecognized legacy CodeTheme name '%s' — defaulting to Monokai", oldName)
            SyntaxTheme.MONOKAI
        }
    }

    val syntaxTheme: Flow<SyntaxTheme> = context.advancedPreferencesStore.data
        .onStart { migrateCodeThemeIfNeeded() }
        .map { prefs ->
            val storedKey = prefs[KEY_CODE_THEME]
            when (storedKey) {
                null -> SyntaxTheme.MONOKAI
                "monokai", "one_dark", "github", "dracula" -> SyntaxTheme.fromKey(storedKey)
                "MONOKAI", "DRACULA", "NORD", "ONE_DARK", "GITHUB", "SOLARIZED_DARK" -> {
                    val migrated = migrateCodeTheme(storedKey)
                    Timber.w("AdvancedPreferences: resolved legacy CodeTheme '%s' -> SyntaxTheme '%s' (migrated in onStart)", storedKey, migrated.key)
                    migrated
                }
                else -> SyntaxTheme.MONOKAI
            }
        }
        .distinctUntilChanged()

    suspend fun setSyntaxTheme(theme: SyntaxTheme) {
        context.advancedPreferencesStore.edit { prefs ->
            prefs[KEY_CODE_THEME] = theme.key
        }
    }

    val codeFontScale: Flow<Float> = context.advancedPreferencesStore.data.map { prefs ->
        prefs[KEY_CODE_FONT_SCALE] ?: 1.0f
    }

    suspend fun setCodeFontScale(scale: Float) {
        context.advancedPreferencesStore.edit { prefs ->
            prefs[KEY_CODE_FONT_SCALE] = scale.coerceIn(0.8f, 1.5f)
        }
    }

    val cacheMaxSizeBytes: Flow<Long> = context.advancedPreferencesStore.data.map { prefs ->
        prefs[KEY_CACHE_MAX_SIZE_BYTES] ?: DEFAULT_CACHE_MAX_SIZE_BYTES
    }.distinctUntilChanged()

    suspend fun setCacheMaxSizeBytes(bytes: Long) {
        context.advancedPreferencesStore.edit { prefs ->
            prefs[KEY_CACHE_MAX_SIZE_BYTES] = bytes.coerceAtLeast(MIN_CACHE_MAX_SIZE_BYTES)
        }
    }

    val thinkingEnabled: Flow<Boolean> = context.advancedPreferencesStore.data.map { prefs ->
        prefs[KEY_THINKING_ENABLED] ?: true
    }.distinctUntilChanged()

    suspend fun setThinkingEnabled(enabled: Boolean) {
        context.advancedPreferencesStore.edit { prefs ->
            prefs[KEY_THINKING_ENABLED] = enabled
        }
    }
}
