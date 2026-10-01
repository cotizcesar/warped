package com.warped.ui.huggingface

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warped.data.local.download.DownloadState
import com.warped.data.local.download.ModelDownloadManager
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.ModelImportManager
import com.warped.data.repository.AllowlistedModel
import com.warped.data.repository.ModelAllowlistRepository
import com.warped.domain.model.ActiveModelSelection
import com.warped.domain.repository.LocalModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
                // Match by file name: the entry carries only the bare name
                // while the DB row stores the absolute path.
                val models = localModelRepository.observeModels().first()
                val model = models.firstOrNull {
                    it.filePath.substringAfterLast("/") == entry.modelFile
                } ?: return@launch Timber.w(
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
}
