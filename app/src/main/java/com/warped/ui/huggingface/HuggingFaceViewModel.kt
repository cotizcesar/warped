package com.warped.ui.huggingface

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warped.data.local.download.ModelDownloadManager
import com.warped.data.local.security.ApiKeyStore
import com.warped.data.remote.dto.HuggingFaceModel
import com.warped.domain.repository.HuggingFaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch

import javax.inject.Inject

@HiltViewModel
class HuggingFaceViewModel @Inject constructor(
    private val huggingFaceRepository: HuggingFaceRepository,
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
        search("")
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
            val author: String? = null
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

    fun selectModel(model: HuggingFaceModel) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch(coroutineExceptionHandler) {
            val result = huggingFaceRepository.getModelDetail(model.id)
            result.onSuccess { detail ->
                val filteredSiblings = detail.siblings.filter {
                    it.rfilename.endsWith(".litertlm", ignoreCase = true)
                }
                val sortedFiles = filteredSiblings.sortedBy { sibling ->
                    sibling.size.takeIf { it > 0 } ?: sibling.lfs?.size ?: 0L
                }
                _uiState.update {
                    it.copy(
                        selectedModel = detail,
                        modelSiblings = sortedFiles,
                        isLoading = false
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun downloadFile(modelId: String, fileName: String, fileSize: Long) {
        val gated = _uiState.value.selectedModel?.gated ?: "false"
        if (gated != "false") {
            val hasToken = try { apiKeyStore.getHuggingFaceToken() != null } catch (_: Exception) { false }
            if (!hasToken) {
                _uiState.update {
                    it.copy(
                        downloadError = "Gated model — get an Access Token at huggingface.co/settings/tokens then add it in Settings → Hugging Face.",
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
            isGated = gated != "false"
        )
    }

    fun pauseDownload() {
        val activeId = _uiState.value.activeDownloadId ?: return
        downloadManager.pauseDownload(activeId)
        _uiState.update { it.copy(isDownloading = false, isDownloadPaused = true) }
    }

    fun resumeDownload() {
        val activeId = _uiState.value.activeDownloadId ?: return
        downloadManager.resumeDownload(activeId)
        _uiState.update { it.copy(isDownloading = true, isDownloadPaused = false, downloadError = null) }
    }

    fun onSearchTextChanged(text: String) {
        _uiState.update { it.copy(searchQuery = text) }
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "", searchResults = emptyList(), error = null) }
    }

    fun clearDetail() {
        _uiState.update {
            it.copy(
                selectedModel = null,
                modelSiblings = emptyList()
            )
        }
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
