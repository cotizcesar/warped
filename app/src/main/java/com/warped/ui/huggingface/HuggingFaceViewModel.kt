package com.warped.ui.huggingface

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warped.data.local.download.ModelDownloadManager
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.remote.dto.HuggingFaceModel
import com.warped.domain.repository.HuggingFaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HuggingFaceViewModel @Inject constructor(
    private val huggingFaceRepository: HuggingFaceRepository,
    private val downloadManager: ModelDownloadManager,
    private val memoryChecker: MemoryChecker
) : ViewModel() {

    private val _uiState = MutableStateFlow(HuggingFaceUiState())
    val uiState: StateFlow<HuggingFaceUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null

    init {
        refreshMemoryInfo()
        search("gguf")
        viewModelScope.launch {
            downloadManager.downloadStates.collect { states ->
                val activeId = _uiState.value.activeDownloadId
                if (activeId != null) {
                    val state = states[activeId]
                    if (state != null) {
                        _uiState.update {
                            it.copy(
                                isDownloading = state.isDownloading,
                                downloadProgress = state.progress,
                                downloadError = state.error
                            )
                        }
                        if (!state.isDownloading && state.error == null && state.progress >= 1f) {
                            _uiState.update { it.copy(downloadSuccess = true) }
                        }
                    }
                }
            }
        }
    }

    private fun refreshMemoryInfo() {
        val info = memoryChecker.getMemoryInfo()
        _uiState.update { it.copy(availableMemoryBytes = info.availableBytes) }
    }

    fun search(query: String) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.length < MIN_SEARCH_LENGTH) return
        searchJob?.cancel()
        _uiState.update { it.copy(searchQuery = trimmedQuery, isLoading = true, error = null) }
        searchJob = viewModelScope.launch {
            val activeFormat = _uiState.value.activeFormat
            val result = huggingFaceRepository.searchModels(
                query = trimmedQuery,
                format = activeFormat
            )
            result.onSuccess { models ->
                // Filter out vision/speech models — only show text-capable models
                val textModels = if (_uiState.value.activeFormat == "litertlm") {
                    models.filter { model ->
                        model.pipelineTag.isBlank() || model.pipelineTag !in EXCLUDED_PIPELINE_TAGS
                    }
                } else {
                    models // GGUF search: don't filter (GGUF format implies text model)
                }

                val compatibility = loadCompatibility(textModels)
                val sortedModels = textModels.sortedWith(
                    compareByDescending<HuggingFaceModel> { compatibility[it.id] == true }
                        .thenByDescending { it.downloads }
                )
                _uiState.update {
                    it.copy(
                        searchResults = sortedModels,
                        compatibilityByModelId = compatibility,
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
        viewModelScope.launch {
            val result = huggingFaceRepository.getModelDetail(model.id)
            result.onSuccess { detail ->
                val extension = ".${_uiState.value.activeFormat}" // ".gguf" or ".litertlm"
                val formatFiles = detail.siblings
                    .filter { it.rfilename.endsWith(extension, ignoreCase = true) }
                val compatibility = formatFiles.associate {
                    val effSize = it.size.takeIf { s -> s > 0 } ?: it.lfs?.size ?: 0L
                    val level = when {
                        memoryChecker.canLoadModel(effSize) -> 2
                        memoryChecker.shouldWarn(effSize) -> 1
                        else -> 0
                    }
                    it.rfilename to level
                }
                val sortedFiles = formatFiles.sortedBy { sibling ->
                    sibling.size.takeIf { it > 0 } ?: sibling.lfs?.size ?: 0L
                }
                _uiState.update {
                    it.copy(
                        selectedModel = detail,
                        modelSiblings = sortedFiles,
                        compatibilityByFileName = compatibility,
                        isLoading = false
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun downloadFile(modelId: String, fileName: String, fileSize: Long) {
        val fileUrl = "https://huggingface.co/$modelId/resolve/main/$fileName"
        val downloadId = "$modelId/$fileName"
        _uiState.update {
            it.copy(
                isDownloading = true,
                downloadingFileName = fileName,
                downloadProgress = 0f,
                downloadError = null,
                activeDownloadId = downloadId
            )
        }
        downloadManager.startDownload(
            modelId = downloadId,
            fileName = fileName,
            fileUrl = fileUrl,
            fileSizeBytes = fileSize
        )
    }

    fun pauseDownload() {
        val activeId = _uiState.value.activeDownloadId ?: return
        downloadManager.pauseDownload(activeId)
        _uiState.update { it.copy(isDownloading = false, downloadProgress = 0f, downloadingFileName = "") }
    }

    fun onSearchTextChanged(text: String) {
        _uiState.update { it.copy(searchQuery = text) }
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "", searchResults = emptyList(), error = null) }
    }

    fun setActiveFormat(format: String) {
        _uiState.update {
            it.copy(
                activeFormat = format,
                searchResults = emptyList(),
                selectedModel = null
            )
        }
        search(_uiState.value.searchQuery) // re-search with new format
    }

    fun clearDetail() {
        _uiState.update {
            it.copy(
                selectedModel = null,
                modelSiblings = emptyList(),
                compatibilityByFileName = emptyMap()
            )
        }
    }

    fun toggleCompatibleFilter() {
        _uiState.update { it.copy(showCompatibleOnly = !it.showCompatibleOnly) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null, downloadError = null) }
    }

    fun clearDownloadSuccess() {
        _uiState.update { it.copy(downloadSuccess = false) }
    }

    private suspend fun loadCompatibility(models: List<HuggingFaceModel>): Map<String, Boolean> {
        val extension = ".${_uiState.value.activeFormat}"
        return models.map { model ->
            viewModelScope.async {
                val formatFiles = model.siblings.ifEmpty {
                    huggingFaceRepository.getModelDetail(model.id)
                        .getOrNull()?.siblings ?: emptyList()
                }
                val isCompatible = formatFiles
                    .asSequence()
                    .filter { it.rfilename.endsWith(extension, ignoreCase = true) }
                    .any { memoryChecker.canLoadModel(it.size.takeIf { s -> s > 0 } ?: it.lfs?.size ?: 0L) }
                model.id to isCompatible
            }
        }.awaitAll().toMap()
    }

    private companion object {
        const val MIN_SEARCH_LENGTH = 3

        /**
         * Pipeline tags for vision/speech models that are not usable in Warped v1.1.
         * These model types require vision/audio backends deferred to v2.x.
         * Blacklist approach: exclude known non-text tags; include everything else
         * (handles models with blank pipelineTag, which is common in litert-community).
         */
        private val EXCLUDED_PIPELINE_TAGS = setOf(
            "image-to-text",
            "automatic-speech-recognition",
            "text-to-speech",
            "image-classification",
            "object-detection",
            "image-segmentation",
            "audio-classification",
            "image-text-to-text",
            "visual-question-answering",
            "text-to-image",
            "zero-shot-image-classification",
            "zero-shot-object-detection",
            "image-feature-extraction",
            "video-classification",
            "depth-estimation"
        )
    }
}
