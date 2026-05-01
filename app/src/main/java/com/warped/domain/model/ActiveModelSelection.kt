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
    val providerType: ProviderType
)

@Singleton
class ActiveModelSelection @Inject constructor(
    private val keystoreManager: KeystoreManager
) {
    private val _activeModel = MutableStateFlow<ActiveModel?>(null)
    val activeModel: StateFlow<ActiveModel?> = _activeModel.asStateFlow()
    private val json = Json

    init {
        // Restore last selection
        keystoreManager.get(LAST_MODEL_KEY)?.let { raw ->
            try {
                val saved = json.decodeFromString<SavedModel>(raw)
                _activeModel.value = ActiveModel(modelId = saved.modelId, providerType = ProviderType.valueOf(saved.providerType))
            } catch (_: Exception) {}
        }
    }

    fun select(modelId: String, providerType: ProviderType) {
        _activeModel.value = ActiveModel(modelId = modelId, providerType = providerType)
        persist()
    }

    fun clear() {
        _activeModel.value = null
        keystoreManager.remove(LAST_MODEL_KEY)
    }

    private fun persist() {
        _activeModel.value?.let { model ->
            val saved = SavedModel(modelId = model.modelId, providerType = model.providerType.name)
            keystoreManager.put(LAST_MODEL_KEY, json.encodeToString(saved))
        }
    }

    @Serializable
    private data class SavedModel(val modelId: String, val providerType: String)

    companion object {
        private const val LAST_MODEL_KEY = "last_active_model"
    }
}
