package com.warped.domain.model

import com.warped.data.local.security.KeystoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    val instanceId: String? = null,
    /**
     * Quick-task lazy-model-load: true only while the engine is mounting
     * (load START signal). Selection alone is pending ([isLoading] false) —
     * the engine loads on the first send, then generates.
     */
    val isLoading: Boolean = false
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

    /**
     * 2026-10-04 ANR guard: Keystore writes (EncryptedSharedPreferences,
     * first-touch MasterKey unlock) must never run on Main — picks happen
     * mid-interaction. In-memory flows update synchronously (UI + logic
     * observe instantly); persistence is best-effort background. Worst
     * case on process death before the write lands: the user re-picks.
     */
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun persistAsync(block: () -> Unit) {
        persistScope.launch {
            try {
                block()
            } catch (e: Exception) {
                Timber.e(e, "ActiveModel: persist failed")
            }
        }
    }

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
        _localSelection.value = LocalSelection(modelId = modelId, isConnected = true, instanceId = instanceId, isLoading = false)
        persistAsync { persistLocal() }
        deriveActiveModel()
    }

    fun disconnectLocal() {
        _localSelection.value = LocalSelection()
        persistAsync { keystoreManager.remove(LAST_LOCAL_KEY) }
        deriveActiveModel()
    }

    fun markLocalLoading(modelId: String, instanceId: String? = null) {
        _localSelection.value = LocalSelection(modelId = modelId, isConnected = false, instanceId = instanceId, isLoading = true)
        persistAsync { persistLocal() }
        deriveActiveModel()
    }

    /**
     * Quick-task lazy-model-load: mark a model selected but NOT loading.
     * Selection never touches the engine — the load happens on the first
     * send ([ChatViewModel.sendMessage] mounts via `preloadLocalModel`
     * when the engine path mismatches the selection).
     */
    fun selectLocalPending(modelId: String, instanceId: String? = null) {
        _localSelection.value = LocalSelection(modelId = modelId, isConnected = false, instanceId = instanceId, isLoading = false)
        persistAsync { persistLocal() }
        deriveActiveModel()
    }

    fun markLocalDisconnected() {
        _localSelection.value = _localSelection.value.copy(isConnected = false, isLoading = false)
    }

    fun selectRemote(modelId: String, providerType: ProviderType, endpointId: Long) {
        _remoteSelection.value = RemoteSelection(modelId = modelId, providerType = providerType, endpointId = endpointId)
        persistAsync { persistRemote() }
        deriveActiveModel()
    }

    fun clearRemote() {
        _remoteSelection.value = RemoteSelection()
        persistAsync { keystoreManager.remove(LAST_REMOTE_KEY) }
        deriveActiveModel()
    }

    @Deprecated("Use connectLocal() or selectRemote() instead", ReplaceWith("connectLocal(modelId, providerType, instanceId)"))
    fun select(modelId: String, providerType: ProviderType, instanceId: String? = null) {
        if (providerType == ProviderType.LITE_RT_LM) {
            connectLocal(modelId, providerType, instanceId)
        } else {
            _activeModel.value = ActiveModel(modelId = modelId, providerType = providerType, instanceId = instanceId)
            persistAsync { persistLegacy() }
        }
    }

    @Deprecated("Use disconnectLocal() or clearRemote() instead")
    fun clear() {
        _localSelection.value = LocalSelection()
        _remoteSelection.value = RemoteSelection()
        _activeModel.value = null
        persistAsync { keystoreManager.remove(LAST_LOCAL_KEY) }
        persistAsync { keystoreManager.remove(LAST_REMOTE_KEY) }
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
        persistAsync { keystoreManager.remove(LAST_CONVERSATION_KEY) }
    }
}
