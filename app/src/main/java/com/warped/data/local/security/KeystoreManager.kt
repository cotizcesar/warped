package com.warped.data.local.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeystoreManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val masterKeyAlias = "_warped_master_key_"

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyGenParameterSpec(
                KeyGenParameterSpec.Builder(
                    masterKeyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            .build()
    }

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
        encryptedPrefs.edit().putString(key, value).apply()
    }

    fun get(key: String): String? {
        return encryptedPrefs.getString(key, null)
    }

    fun remove(key: String) {
        encryptedPrefs.edit().remove(key).apply()
    }

    fun clearAll() {
        encryptedPrefs.edit().clear().apply()
    }
}
