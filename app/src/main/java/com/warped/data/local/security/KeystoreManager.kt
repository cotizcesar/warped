@file:Suppress("DEPRECATION")
package com.warped.data.local.security

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeystoreManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    @Suppress("DEPRECATION")
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    @Suppress("DEPRECATION")
    private val encryptedPrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            "warped_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun put(key: String, value: String) {
        try {
            encryptedPrefs.edit { putString(key, value) }
            Timber.d("KeystoreManager: put succeeded")
        } catch (e: Exception) {
            Timber.e(e, "KeystoreManager: put failed")
        }
    }

    fun get(key: String): String? {
        return try {
            encryptedPrefs.getString(key, null)
        } catch (e: Exception) {
            Timber.e(e, "KeystoreManager: get failed")
            null
        }
    }

    fun remove(key: String) {
        try {
            encryptedPrefs.edit { remove(key) }
        } catch (e: Exception) {
            Timber.e(e, "KeystoreManager: remove failed")
        }
    }

    fun clearAll() {
        try {
            encryptedPrefs.edit { clear() }
        } catch (e: Exception) {
            Timber.e(e, "KeystoreManager: clearAll failed")
        }
    }

    companion object {
        /**
         * Phase 63: legacy search-provider alias orphaned by the DDG-only
         * migration. Single source of truth so writer/remover typo drift is
         * caught at compile time (see ApiKeyStore.deleteAllKeys and
         * WarpedApplication cleanup).
         */
        const val LEGACY_SEARCH_ALIAS = "tavily_api_key"
    }
}
