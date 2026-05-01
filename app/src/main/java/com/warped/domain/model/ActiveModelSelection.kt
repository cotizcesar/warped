package com.warped.domain.model

import com.warped.data.local.security.KeystoreManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

data class ActiveModel(
    val modelId: String,
    val providerType: ProviderType,
    val instanceId: String? = null
)

@Singleton
class ActiveModelSelection @Inject constructor(
    private val keystoreManager: KeystoreManager
) {
    private val _activeModel = MutableStateFlow<ActiveModel?>(null)
    val activeModel: StateFlow<ActiveModel?> = _activeModel.asStateFlow()
    private val json = Json

    init {
        try {
            keystoreManager.get(LAST_MODEL_KEY)?.let { raw ->
                val saved = json.decodeFromString<SavedModel>(raw)
                _activeModel.value = ActiveModel(
                    modelId = saved.modelId, 
                    providerType = ProviderType.valueOf(saved.providerType),
                    instanceId = saved.instanceId
                )
            }
        } catch (_: Exception) {}
    }

    fun select(modelId: String, providerType: ProviderType, instanceId: String? = null) {
        _activeModel.value = ActiveModel(modelId = modelId, providerType = providerType, instanceId = instanceId)
        try { persist() } catch (_: Exception) {}
    }

    fun clear() {
        _activeModel.value = null
        try { keystoreManager.remove(LAST_MODEL_KEY) } catch (_: Exception) {}
    }

    private fun persist() {
        _activeModel.value?.let { model ->
            keystoreManager.put(LAST_MODEL_KEY, json.encodeToString(
                SavedModel(model.modelId, model.providerType.name, model.instanceId)
            ))
        }
    }

    @Serializable
    private data class SavedModel(
        val modelId: String,
        val providerType: String,
        val instanceId: String? = null
    )

    companion object {
        private const val LAST_MODEL_KEY = "last_active_model"
        private const val LAST_CONVERSATION_KEY = "last_conversation_id"
    }

    fun saveLastConversation(conversationId: Long) {
        try { keystoreManager.put(LAST_CONVERSATION_KEY, conversationId.toString()) } catch (_: Exception) {}
    }

    fun getLastConversation(): Long {
        return try {
            keystoreManager.get(LAST_CONVERSATION_KEY)?.toLongOrNull() ?: 0L
        } catch (_: Exception) { 0L }
    }
}
