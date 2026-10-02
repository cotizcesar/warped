package com.warped.data.local.security

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiKeyStore @Inject constructor(
    private val keystoreManager: KeystoreManager
) {
    fun storeKey(endpointId: Long, apiKey: CharArray) {
        val alias = "api_key_$endpointId"
        val bytes = apiKey.concatToString().toByteArray(Charsets.UTF_8)
        apiKey.fill('0')
        keystoreManager.put(alias, bytes.toString(Charsets.UTF_8))
        bytes.fill(0)
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
}
