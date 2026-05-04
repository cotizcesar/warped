package com.warped.data.local.security

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiKeyStore @Inject constructor(
    private val keystoreManager: KeystoreManager
) {
    private companion object {
        const val HF_TOKEN_KEY = "huggingface_token"
    }

    fun storeKey(endpointId: Long, apiKey: CharArray) {
        val alias = "api_key_$endpointId"
        keystoreManager.put(alias, String(apiKey))
        apiKey.fill('0')
    }

    fun getKey(endpointId: Long): CharArray? {
        val alias = "api_key_$endpointId"
        return keystoreManager.get(alias)?.toCharArray()
    }

    fun deleteKey(endpointId: Long) {
        val alias = "api_key_$endpointId"
        keystoreManager.remove(alias)
    }

    fun deleteAllKeys(endpointIds: List<Long>) {
        endpointIds.forEach { deleteKey(it) }
    }

    fun storeHuggingFaceToken(token: CharArray) {
        keystoreManager.put(HF_TOKEN_KEY, String(token))
        token.fill('0')
    }

    fun getHuggingFaceToken(): CharArray? {
        return keystoreManager.get(HF_TOKEN_KEY)?.toCharArray()
    }

    fun deleteHuggingFaceToken() {
        keystoreManager.remove(HF_TOKEN_KEY)
    }
}
