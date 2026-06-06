package com.warped.ui.huggingface

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warped.data.local.download.ModelDownloadManager
import com.warped.data.local.security.ApiKeyStore
import com.warped.data.remote.dto.HuggingFaceModel
import com.warped.data.remote.dto.HuggingFaceSibling
import com.warped.domain.model.AllowlistEntry
import com.warped.domain.repository.HuggingFaceRepository
import com.warped.domain.repository.ModelAllowlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch

import javax.inject.Inject
import timber.log.Timber

@HiltViewModel
class HuggingFaceViewModel @Inject constructor(
    private val huggingFaceRepository: HuggingFaceRepository,
    private val modelAllowlistRepository: ModelAllowlistRepository,
    private val downloadManager: ModelDownloadManager,
    private val apiKeyStore: ApiKeyStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(HuggingFaceUiState())
    val uiState: StateFlow<HuggingFaceUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        timber.log.Timber.e(throwable, "Unhandled coroutine exception")
    }

    init {
        loadRecommended()
        viewModelScope.launch(coroutineExceptionHandler) {
            downloadManager.downloadStates.collect { states ->
                val activeId = _uiState.value.activeDownloadId
                if (activeId != null) {
                    val state = states[activeId]
                    if (state != null) {
                        _uiState.update {
                            it.copy(
                                isDownloading = state.isDownloading,
                                isDownloadPaused = state.isPaused,
                                downloadProgress = state.progress,
                                downloadedBytes = state.downloadedBytes,
                                totalDownloadBytes = state.totalBytes,
                                downloadSpeedBytesPerSecond = state.speedBytesPerSecond,
                                downloadError = state.error
                            )
                        }
                        if (!state.isDownloading && state.error == null && state.progress >= 1f) {
                            _uiState.update { it.copy(downloadSuccess = true, isDownloadPaused = false) }
                        }
                    }
                }
            }
        }
    }

    fun search(query: String) {
        val trimmedQuery = query.trim()

        if (trimmedQuery.length < MIN_SEARCH_LENGTH) return
        searchJob?.cancel()
        _uiState.update { it.copy(searchQuery = trimmedQuery, isLoading = true, error = null) }
        searchJob = viewModelScope.launch(coroutineExceptionHandler) {
            val author: String? = "litert-community"
            val result = huggingFaceRepository.searchModels(
                query = trimmedQuery.ifBlank { null },
                author = author
            )
            result.onSuccess { models ->
                val sortedModels = models.sortedByDescending { it.downloads }
                _uiState.update {
                    it.copy(
                        searchResults = sortedModels,
                        isLoading = false
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun downloadFile(modelId: String, fileName: String, fileSize: Long, isGated: Boolean = false) {
        if (isGated) {
            val hasToken = try { apiKeyStore.getHuggingFaceToken() != null } catch (_: Exception) { false }
            if (!hasToken) {
                _uiState.update {
                    it.copy(
                        downloadError = "Gated model — get an Access Token at huggingface.co/settings/tokens then add it in Settings -> Hugging Face.",
                        isDownloading = false
                    )
                }
                return
            }
        }
        val encodedPath = fileName.split("/").joinToString("/") {
            java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
        val fileUrl = "https://huggingface.co/$modelId/resolve/main/$encodedPath"
        val downloadId = "$modelId/$fileName"
        _uiState.update {
            it.copy(
                isDownloading = true,
                isDownloadPaused = false,
                downloadingFileName = fileName,
                downloadProgress = 0f,
                downloadedBytes = 0,
                totalDownloadBytes = fileSize,
                downloadSpeedBytesPerSecond = 0,
                downloadError = null,
                activeDownloadId = downloadId
            )
        }
        downloadManager.startDownload(
            modelId = downloadId,
            fileName = fileName,
            fileUrl = fileUrl,
            fileSizeBytes = fileSize,
            isGated = isGated
        )
    }

    fun pauseDownload() {
        val activeId = _uiState.value.activeDownloadId ?: return
        downloadManager.pauseDownload(activeId)
        _uiState.update { it.copy(isDownloading = false, isDownloadPaused = true) }
    }

    fun cancelDownload() {
        val activeId = _uiState.value.activeDownloadId ?: return
        downloadManager.cancelDownload(activeId)
        _uiState.update {
            it.copy(
                isDownloading = false,
                isDownloadPaused = true,
                downloadError = "Cancelled",
                activeDownloadId = null,
                downloadingFileName = ""
            )
        }
    }

    fun resumeDownload() {
        val activeId = _uiState.value.activeDownloadId ?: return
        downloadManager.resumeDownload(activeId)
        _uiState.update { it.copy(isDownloading = true, isDownloadPaused = false, downloadError = null) }
    }

    fun onSearchTextChanged(text: String) {
        _uiState.update { it.copy(searchQuery = text) }
    }

    fun selectTab(tab: HuggingFaceTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        if (tab == HuggingFaceTab.Recommended && _uiState.value.recommendedResults.isEmpty() && !_uiState.value.isLoadingRecommended) {
            loadRecommended()
        }
    }

    /**
     * Populate the Recommended tab from the curated `assets/model_allowlist.json`
     * asset. The allowlist is the source of truth for which models are
     * recommended — it removes the runtime HF API call and guarantees offline
     * availability of the Recommended tab on first launch.
     */
    fun loadRecommended() {
        if (_uiState.value.isLoadingRecommended) return
        _uiState.update { it.copy(isLoadingRecommended = true, error = null) }
        viewModelScope.launch(coroutineExceptionHandler) {
            val entries = modelAllowlistRepository.getAll()
            val mapped = entries.map { it.toHuggingFaceModel() }
            _uiState.update {
                it.copy(
                    recommendedResults = mapped,
                    isLoadingRecommended = false
                )
            }
        }
    }

    private fun AllowlistEntry.toHuggingFaceModel(): HuggingFaceModel {
        val author = name.substringBefore('/')
        val capList = capabilities
        val tags = buildList {
            add("litertlm")
            if ("llm_chat" in capList) add("conversational")
            if ("llm_vision" in capList) add("image-text-to-text")
        }
        return HuggingFaceModel(
            id = name,
            modelIdAlias = name,
            author = author,
            tags = tags,
            downloads = 0,
            likes = 0,
            description = displayName,
            pipelineTag = if ("llm_vision" in capList) "image-text-to-text" else "text-generation",
            gated = "false",
            lastModified = "",
            siblings = listOf(
                HuggingFaceSibling(
                    rfilename = modelFile,
                    size = sizeInBytes,
                    blobId = null,
                    lfs = null
                )
            )
        )
    }

    /**
     * Start a download directly from a card's dropdown — no detail sheet.
     * The card itself shows progress inline once the download starts.
     */
    fun startDirectDownload(model: HuggingFaceModel, fileName: String, fileSize: Long) {
        downloadFile(model.id, fileName, fileSize, isGated = model.gated != "false")
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "", searchResults = emptyList(), error = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null, downloadError = null) }
    }

    fun clearDownloadSuccess() {
        _uiState.update { it.copy(downloadSuccess = false) }
    }

    private companion object {
        const val MIN_SEARCH_LENGTH = 0
    }
}
