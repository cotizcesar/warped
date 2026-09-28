package com.warped.ui.huggingface

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warped.data.local.download.DownloadState
import com.warped.data.local.download.ModelDownloadManager
import com.warped.data.repository.AllowlistedModel
import com.warped.data.repository.ModelAllowlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.net.URLEncoder
import javax.inject.Inject

/**
 * Phase 49 (DEL-05): static model-catalog backing store.
 *
 * Model discovery is the bundled `model_allowlist.json` only — no search, no
 * auth, no gated-model filtering. Downloads go direct (no Authorization
 * header) through [ModelDownloadManager]/[ModelDownloadWorker].
 *
 * All models ship under the `warped-community` Hugging Face org; the
 * allowlist `name` is the repo slug within it.
 */
@HiltViewModel
class CatalogViewModel @Inject constructor(
    allowlistRepository: ModelAllowlistRepository,
    private val downloadManager: ModelDownloadManager,
) : ViewModel() {

    /** Synchronous asset parse — first paint shows the populated list, no skeleton. */
    val models: List<AllowlistedModel> = allowlistRepository.models

    val downloadStates: StateFlow<Map<String, DownloadState>> =
        downloadManager.downloadStates.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            emptyMap(),
        )

    fun downloadId(entry: AllowlistedModel): String =
        "$REPO_PREFIX/${entry.name}/${entry.modelFile}"

    fun startDownload(entry: AllowlistedModel) {
        val encodedFile = entry.modelFile.split("/").joinToString("/") {
            URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
        downloadManager.startDownload(
            modelId = downloadId(entry),
            fileName = entry.modelFile,
            fileUrl = "https://huggingface.co/$REPO_PREFIX/${entry.name}/resolve/main/$encodedFile",
            fileSizeBytes = entry.sizeInBytes,
            isGated = false,
        )
    }

    fun pauseDownload(downloadId: String) = downloadManager.pauseDownload(downloadId)

    fun resumeDownload(downloadId: String) = downloadManager.resumeDownload(downloadId)

    fun cancelDownload(downloadId: String) = downloadManager.cancelDownload(downloadId)

    private companion object {
        const val REPO_PREFIX = "warped-community"
    }
}
