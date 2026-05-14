package com.warped.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.warped.domain.model.GenerationParameters
import com.warped.domain.model.SyntaxTheme
import com.warped.ui.chat.components.CodeTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private val Context.advancedPreferencesStore: DataStore<Preferences> by preferencesDataStore(name = "advanced_preferences")

@Singleton
class AdvancedPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        val KEY_TEMPERATURE = floatPreferencesKey("temperature")
        val KEY_TOP_P = floatPreferencesKey("top_p")
        val KEY_TOP_K = intPreferencesKey("top_k")
        val KEY_REPEAT_PENALTY = floatPreferencesKey("repeat_penalty")
        val KEY_MAX_TOKENS = intPreferencesKey("max_tokens")
        val KEY_CONTEXT_SIZE = intPreferencesKey("context_size")
        val KEY_SEED = intPreferencesKey("seed")
        val KEY_CODE_THEME = stringPreferencesKey("code_theme")
    }

    val defaultParameters: Flow<GenerationParameters> = context.advancedPreferencesStore.data.map { prefs ->
        GenerationParameters(
            temperature = prefs[KEY_TEMPERATURE] ?: 0.7f,
            topP = prefs[KEY_TOP_P] ?: 0.9f,
            topK = prefs[KEY_TOP_K] ?: 40,
            repeatPenalty = prefs[KEY_REPEAT_PENALTY] ?: 1.1f,
            maxTokens = prefs[KEY_MAX_TOKENS] ?: 512,
            contextSize = prefs[KEY_CONTEXT_SIZE] ?: 1024,
            seed = prefs[KEY_SEED] ?: -1
        )
    }

    suspend fun save(params: GenerationParameters) {
        context.advancedPreferencesStore.edit { prefs ->
            prefs[KEY_TEMPERATURE] = params.temperature
            prefs[KEY_TOP_P] = params.topP
            prefs[KEY_TOP_K] = params.topK
            prefs[KEY_REPEAT_PENALTY] = params.repeatPenalty
            prefs[KEY_MAX_TOKENS] = params.maxTokens
            prefs[KEY_CONTEXT_SIZE] = params.contextSize
            prefs[KEY_SEED] = params.seed
        }
    }

    val codeTheme: Flow<CodeTheme> = context.advancedPreferencesStore.data.map { prefs ->
        val name = prefs[KEY_CODE_THEME] ?: return@map CodeTheme.MONOKAI
        // Try direct enum match first (legacy uppercase format)
        try {
            CodeTheme.valueOf(name)
        } catch (_: IllegalArgumentException) {
            // Handle SyntaxTheme keys (lowercase) written by migration
            when (name.lowercase()) {
                "monokai" -> CodeTheme.MONOKAI
                "one_dark" -> CodeTheme.ONE_DARK
                "github" -> CodeTheme.GITHUB
                "dracula" -> CodeTheme.DRACULA
                else -> CodeTheme.MONOKAI
            }
        }
    }

    suspend fun setCodeTheme(theme: CodeTheme) {
        context.advancedPreferencesStore.edit { prefs ->
            prefs[KEY_CODE_THEME] = theme.name
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

    val syntaxTheme: Flow<SyntaxTheme> = context.advancedPreferencesStore.data.map { prefs ->
        val storedKey = prefs[KEY_CODE_THEME]
        when (storedKey) {
            null -> SyntaxTheme.MONOKAI
            "monokai", "one_dark", "github", "dracula" -> SyntaxTheme.fromKey(storedKey)
            "MONOKAI", "DRACULA", "NORD", "ONE_DARK", "GITHUB", "SOLARIZED_DARK" -> {
                val migrated = migrateCodeTheme(storedKey)
                Timber.w("AdvancedPreferences: migrating legacy CodeTheme '%s' -> SyntaxTheme '%s'", storedKey, migrated.key)
                context.advancedPreferencesStore.edit { it[KEY_CODE_THEME] = migrated.key }
                migrated
            }
            else -> SyntaxTheme.MONOKAI
        }
    }.distinctUntilChanged()

    suspend fun setSyntaxTheme(theme: SyntaxTheme) {
        context.advancedPreferencesStore.edit { prefs ->
            prefs[KEY_CODE_THEME] = theme.key
        }
    }
}
