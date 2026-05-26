package com.warped.domain.model

import com.warped.data.local.security.KeystoreManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class ActiveModel(
    val modelId: String,
    val providerType: ProviderType,
    val instanceId: String? = null
)

data class LocalSelection(
    val modelId: String? = null,
    val isConnected: Boolean = false,
    val instanceId: String? = null
)

data class RemoteSelection(
    val modelId: String? = null,
    val providerType: ProviderType? = null,
    val endpointId: Long? = null
)

@Singleton
class ActiveModelSelection @Inject constructor(
    private val keystoreManager: KeystoreManager
) {
    private val _activeModel = MutableStateFlow<ActiveModel?>(null)
    val activeModel: StateFlow<ActiveModel?> = _activeModel.asStateFlow()

    private val _localSelection = MutableStateFlow(LocalSelection())
    val localSelection: StateFlow<LocalSelection> = _localSelection.asStateFlow()

    private val _remoteSelection = MutableStateFlow(RemoteSelection())
    val remoteSelection: StateFlow<RemoteSelection> = _remoteSelection.asStateFlow()

    private val json = Json

    init {
        try {
            keystoreManager.get(LAST_LOCAL_KEY)?.let { raw ->
                val saved = json.decodeFromString<SavedLocal>(raw)
                _localSelection.value = LocalSelection(
                    modelId = saved.modelId,
                    isConnected = false,
                    instanceId = saved.instanceId
                )
            }
            keystoreManager.get(LAST_REMOTE_KEY)?.let { raw ->
                val saved = json.decodeFromString<SavedRemote>(raw)
                _remoteSelection.value = RemoteSelection(
                    modelId = saved.modelId,
                    providerType = saved.providerType?.let { ProviderType.valueOf(it) },
                    endpointId = saved.endpointId
                )
            }
        } catch (e: Exception) { Timber.e(e, "ActiveModel: init load failed") }
        deriveActiveModel()
    }

    fun connectLocal(modelId: String, providerType: ProviderType, instanceId: String? = null) {
        _localSelection.value = LocalSelection(modelId = modelId, isConnected = true, instanceId = instanceId)
        try { persistLocal() } catch (e: Exception) { Timber.e(e, "ActiveModel: persist local failed") }
        deriveActiveModel()
    }

    fun disconnectLocal() {
        _localSelection.value = LocalSelection()
        try { keystoreManager.remove(LAST_LOCAL_KEY) } catch (e: Exception) { Timber.e(e, "ActiveModel: remove local failed") }
        deriveActiveModel()
    }

    fun markLocalLoading(modelId: String, instanceId: String? = null) {
        _localSelection.value = LocalSelection(modelId = modelId, isConnected = false, instanceId = instanceId)
        try { persistLocal() } catch (e: Exception) { Timber.e(e, "ActiveModel: persist local loading failed") }
        deriveActiveModel()
    }

    fun markLocalDisconnected() {
        _localSelection.value = _localSelection.value.copy(isConnected = false)
    }

    fun selectRemote(modelId: String, providerType: ProviderType, endpointId: Long) {
        _remoteSelection.value = RemoteSelection(modelId = modelId, providerType = providerType, endpointId = endpointId)
        try { persistRemote() } catch (e: Exception) { Timber.e(e, "ActiveModel: persist remote failed") }
        deriveActiveModel()
    }

    fun clearRemote() {
        _remoteSelection.value = RemoteSelection()
        try { keystoreManager.remove(LAST_REMOTE_KEY) } catch (e: Exception) { Timber.e(e, "ActiveModel: remove remote failed") }
        deriveActiveModel()
    }

    @Deprecated("Use connectLocal() or selectRemote() instead", ReplaceWith("connectLocal(modelId, providerType, instanceId)"))
    fun select(modelId: String, providerType: ProviderType, instanceId: String? = null) {
        if (providerType == ProviderType.LITE_RT_LM || providerType == ProviderType.LOCAL) {
            connectLocal(modelId, providerType, instanceId)
        } else {
            _activeModel.value = ActiveModel(modelId = modelId, providerType = providerType, instanceId = instanceId)
            try { persistLegacy() } catch (e: Exception) { Timber.e(e, "ActiveModel: persist failed") }
        }
    }

    @Deprecated("Use disconnectLocal() or clearRemote() instead")
    fun clear() {
        _localSelection.value = LocalSelection()
        _remoteSelection.value = RemoteSelection()
        _activeModel.value = null
        try { keystoreManager.remove(LAST_LOCAL_KEY) } catch (e: Exception) { Timber.e(e, "ActiveModel: remove model failed") }
        try { keystoreManager.remove(LAST_REMOTE_KEY) } catch (e: Exception) { Timber.e(e, "ActiveModel: remove model failed") }
    }

    private fun deriveActiveModel() {
        val local = _localSelection.value
        val remote = _remoteSelection.value
        _activeModel.value = if (local.modelId != null && local.isConnected) {
            ActiveModel(modelId = local.modelId, providerType = ProviderType.LITE_RT_LM, instanceId = local.instanceId)
        } else if (remote.modelId != null && remote.providerType != null) {
            ActiveModel(modelId = remote.modelId, providerType = remote.providerType, instanceId = null)
        } else null
    }

    private fun persistLocal() {
        _localSelection.value.let { sel ->
            keystoreManager.put(LAST_LOCAL_KEY, json.encodeToString(
                SavedLocal(sel.modelId, sel.instanceId)
            ))
        }
    }

    private fun persistRemote() {
        _remoteSelection.value.let { sel ->
            keystoreManager.put(LAST_REMOTE_KEY, json.encodeToString(
                SavedRemote(sel.modelId, sel.providerType?.name, sel.endpointId)
            ))
        }
    }

    private fun persistLegacy() {
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

    @Serializable
    private data class SavedLocal(
        val modelId: String? = null,
        val instanceId: String? = null
    )

    @Serializable
    private data class SavedRemote(
        val modelId: String? = null,
        val providerType: String? = null,
        val endpointId: Long? = null
    )

    companion object {
        private const val LAST_MODEL_KEY = "last_active_model"
        private const val LAST_LOCAL_KEY = "last_active_local"
        private const val LAST_REMOTE_KEY = "last_active_remote"
        private const val LAST_CONVERSATION_KEY = "last_conversation_id"
    }

    fun saveLastConversation(conversationId: Long) {
        try { keystoreManager.put(LAST_CONVERSATION_KEY, conversationId.toString()) } catch (e: Exception) { Timber.e(e, "ActiveModel: persist conversation failed") }
    }

    fun getLastConversation(): Long {
        return try {
            keystoreManager.get(LAST_CONVERSATION_KEY)?.toLongOrNull() ?: 0L
        } catch (_: Exception) { 0L }
    }

    fun clearLastConversation() {
        try { keystoreManager.remove(LAST_CONVERSATION_KEY) } catch (e: Exception) { Timber.e(e, "ActiveModel: remove conversation failed") }
    }
}
