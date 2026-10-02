package com.warped.ui.huggingface

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warped.R
import com.warped.data.local.download.DownloadState
import com.warped.data.local.download.ModelDownloadManager
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.ModelImportManager
import com.warped.data.repository.AllowlistedModel
import com.warped.data.repository.ModelAllowlistRepository
import com.warped.domain.model.ActiveModelSelection
import com.warped.domain.model.LocalModel
import com.warped.domain.model.ProviderType
import com.warped.domain.repository.ChatRepository
import com.warped.domain.repository.LocalModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.URLEncoder
import javax.inject.Inject

/**
 * Phase 49 (DEL-05): static model-catalog backing store.
 *
 * Model discovery is the bundled `model_allowlist.json` only — no search, no
 * auth, no gated-model filtering. Downloads go direct (no Authorization
 * header) through [ModelDownloadManager]/[ModelDownloadWorker].
 *
 * All models ship under the `warped-community` Hugging Face org; each
 * allowlist entry carries its explicit `repo` slug (full org/repo path).
 * Download URLs and IDs derive from `entry.repoSlug` — the legacy
 * `warped-community/${name}` slug applies only when `repo` is absent
 * (see [AllowlistedModel.repoSlug]).
 */
@HiltViewModel
class CatalogViewModel @Inject constructor(
    allowlistRepository: ModelAllowlistRepository,
    private val downloadManager: ModelDownloadManager,
    private val localModelRepository: LocalModelRepository,
    private val activeModelSelection: ActiveModelSelection,
    private val engineManager: EngineManager,
    private val modelImportManager: ModelImportManager,
    private val chatRepository: ChatRepository,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    /**
     * Synchronous asset parse — first paint shows the populated list, no skeleton.
     * Display order is smallest-first by file size (user decision 2026-10-01);
     * the bundled asset keeps its locked sequence for non-display uses.
     */
    val models: List<AllowlistedModel> =
        allowlistRepository.models.sortedBy { it.sizeInBytes }

    val downloadStates: StateFlow<Map<String, DownloadState>> =
        downloadManager.downloadStates.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            emptyMap(),
        )

    /** On-device file names (entry.modelFile match key) from the models table. */
    val downloadedFileNames: StateFlow<Set<String>> =
        localModelRepository.observeModels()
            .map { models -> models.map { it.filePath.substringAfterLast('/') }.toSet() }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    fun downloadId(entry: AllowlistedModel): String =
        "${entry.repoSlug}/${entry.modelFile}"

    /**
     * One-shot navigation id for catalog activation (FUN-01, T-64-03).
     * Mirrors ModelsViewModel.pendingChatId: set once openBoundChat lands,
     * consumed once by the screen — never re-navigates on recomposition.
     */
    private val _pendingChatId = MutableStateFlow<Long?>(null)
    val pendingChatId: StateFlow<Long?> = _pendingChatId.asStateFlow()

    /** Consume a delivered activation navigation (single-shot). */
    fun consumePendingChat() {
        _pendingChatId.value = null
    }

    /**
     * Activation failure channel (WR-01). Mirrors the ModelsViewModel
     * `_uiState.error` pattern: a missing file or a failed chat creation
     * lands here so the screen can show a Snackbar instead of silently
     * doing nothing.
     */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Clear a delivered activation error after the screen shows it. */
    fun clearError() {
        _error.value = null
    }

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable, "Unhandled coroutine exception")
    }

    fun startDownload(entry: AllowlistedModel) {
        val encodedFile = entry.modelFile.split("/").joinToString("/") {
            URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
        downloadManager.startDownload(
            modelId = downloadId(entry),
            fileName = entry.modelFile,
            fileUrl = "https://huggingface.co/${entry.repoSlug}/resolve/main/$encodedFile",
            fileSizeBytes = entry.sizeInBytes,
            isGated = false,
        )
    }

    // No UI entry point after unified download look (Cancel only) — kept for re-wire
    fun pauseDownload(downloadId: String) = downloadManager.pauseDownload(downloadId)

    // No UI entry point after unified download look (Cancel only) — kept for re-wire
    fun resumeDownload(downloadId: String) = downloadManager.resumeDownload(downloadId)

    fun cancelDownload(downloadId: String) = downloadManager.cancelDownload(downloadId)

    /**
     * Delete an on-device model from the catalog's red trash icon.
     * Mirrors the selector delete path: unload + disconnect when it is the
     * active model, then delete file + library row. Failures are logged —
     * the confirm dialog already gates the action and the screen has no
     * error channel.
     */
    fun deleteDownloaded(entry: AllowlistedModel) {
        viewModelScope.launch {
            try {
                val models = localModelRepository.observeModels().first()
                val model = findLocalModelForEntry(models, entry) ?: return@launch Timber.w(
                    "CatalogVM: delete requested for missing file %s",
                    entry.modelFile
                )
                if (activeModelSelection.localSelection.value.modelId == model.filePath) {
                    try {
                        engineManager.unloadCurrent()
                    } catch (e: Exception) {
                        Timber.w(e, "CatalogVM: unloadCurrent failed")
                    }
                    activeModelSelection.disconnectLocal()
                }
                modelImportManager.deleteModel(model)
            } catch (e: Exception) {
                Timber.e(e, "CatalogVM: deleteDownloaded failed")
            }
        }
    }

    /**
     * Activate a downloaded catalog model and open a bound chat (FUN-01,
     * T-64-03). Resolves the LocalModel via localModelRepository by
     * entry modelFile, then mirrors the ModelsViewModel bound-chat chain
     * so the new conversation carries the verified model binding.
     */
    fun useDownloadedModel(entry: AllowlistedModel) {
        viewModelScope.launch {
            try {
                val models = localModelRepository.observeModels().first()
                val model = findLocalModelForEntry(models, entry) ?: run {
                    Timber.w(
                        "CatalogVM: use requested for missing file %s",
                        entry.modelFile
                    )
                    _error.value =
                        context.getString(R.string.catalog_activation_failed)
                    return@launch
                }
                activeModelSelection.connectLocal(model.filePath, ProviderType.LITE_RT_LM)
                openBoundChat(
                    providerType = ProviderType.LITE_RT_LM,
                    modelId = model.filePath,
                    endpointId = 0L,
                )
            } catch (e: Exception) {
                Timber.e(e, "CatalogVM: useDownloadedModel failed")
            }
        }
    }

    /**
     * Bound-chat creation mirroring ModelsViewModel.openBoundChat: appends
     * a fresh conversation row carrying the activated binding, titled
     * "New Chat" until the first send retitles it.
     */
    private suspend fun openBoundChat(
        providerType: ProviderType,
        modelId: String,
        endpointId: Long,
    ) {
        try {
            val id = chatRepository.createConversation(
                title = context.getString(R.string.new_chat),
                providerType = providerType,
                modelId = modelId,
                endpointId = endpointId,
            )
            activeModelSelection.saveLastConversation(id)
            _pendingChatId.value = id
        } catch (e: Exception) {
            Timber.e(e, "CatalogVM: activation chat creation failed")
            _error.value = e.message
        }
    }
}

/**
 * Shared catalog-to-library resolver (WR-03). The allowlist entry carries
 * only the bare [AllowlistedModel.modelFile] basename while the DB row
 * stores the absolute path, so both the delete and activation paths funnel
 * through this single match instead of duplicating `firstOrNull`
 * basename logic.
 *
 * NOTE: a repo-substring refinement
 * (`filePath.contains(repoSlug.substringAfter("/"))`) was deliberately
 * NOT applied — on-device rows live under `filesDir/models/<basename>`
 * and never contain the repo slug, so that check would fail every lookup
 * and break activation entirely. Full repo-qualified resolution needs the
 * repo slug persisted alongside the row (schema change, out of scope).
 * When several rows share a basename the ambiguity is logged loudly so a
 * wrong-file activation is diagnosable; all 6 current allowlist basenames
 * are unique, so the collision path is latent-only today. Pure and
 * JVM-testable.
 */
internal fun findLocalModelForEntry(
    models: List<LocalModel>,
    entry: AllowlistedModel,
): LocalModel? {
    val matches = models.filter {
        it.filePath.substringAfterLast("/") == entry.modelFile
    }
    if (matches.size > 1) {
        Timber.w(
            "CatalogVM: %d rows share basename %s — activating the first; " +
                "persist the repo slug per row for qualified resolution",
            matches.size,
            entry.modelFile,
        )
    }
    return matches.firstOrNull()
}
