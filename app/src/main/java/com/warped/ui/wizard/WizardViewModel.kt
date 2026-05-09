package com.warped.ui.wizard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warped.data.local.preferences.WizardPreferences
import com.warped.domain.repository.ChatRepository
import com.warped.domain.repository.EndpointRepository
import com.warped.domain.repository.LocalModelRepository
import com.warped.domain.repository.PresetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WizardViewModel @Inject constructor(
    private val wizardPreferences: WizardPreferences,
    private val localModelRepository: LocalModelRepository,
    private val endpointRepository: EndpointRepository,
    private val chatRepository: ChatRepository,
    private val presetRepository: PresetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WizardUiState())
    val uiState: StateFlow<WizardUiState> = _uiState.asStateFlow()

    val steps = WizardStep.entries
    val pageCount: Int get() = steps.size

    init {
        viewModelScope.launch {
            wizardPreferences.isWizardComplete.collect { complete ->
                _uiState.update { it.copy(isWizardComplete = complete) }
            }
        }
        viewModelScope.launch {
            wizardPreferences.skippedSteps.collect { steps ->
                _uiState.update { it.copy(skippedSteps = steps) }
            }
        }
        viewModelScope.launch {
            snapshotContext()
        }
    }

    private suspend fun snapshotContext() {
        val models = localModelRepository.observeModels().first()
        val endpoints = endpointRepository.observeEndpoints().first()
        val chats = chatRepository.observeConversations().first()
        val presets = presetRepository.observePresets().first()

        val ggufCount = models.count { it.modelFormat == "GGUF" || it.modelFormat == "gguf" }
        val litertlmCount = models.count {
            it.modelFormat.equals("LITERTLM", ignoreCase = true) ||
                    it.modelFormat.equals("litertlm", ignoreCase = true)
        }

        _uiState.update {
            it.copy(
                contextData = WizardContextData(
                    ggufModelCount = ggufCount,
                    litertlmModelCount = litertlmCount,
                    endpointCount = endpoints.size,
                    chatCount = chats.size,
                    presetCount = presets.size
                )
            )
        }
    }

    fun goToPage(page: Int) {
        val clamped = page.coerceIn(0, pageCount - 1)
        _uiState.update { it.copy(currentPage = clamped) }
    }

    fun goToNextPage() {
        val next = _uiState.value.currentPage + 1
        if (next < pageCount) {
            _uiState.update { it.copy(currentPage = next) }
        }
    }

    fun goToPreviousPage() {
        val prev = _uiState.value.currentPage - 1
        if (prev >= 0) {
            _uiState.update { it.copy(currentPage = prev) }
        }
    }

    fun skipCurrentStep() {
        val currentKey = steps[_uiState.value.currentPage].name.lowercase()
        viewModelScope.launch {
            wizardPreferences.markStepsSkipped(setOf(currentKey))
        }
        goToNextPage()
    }

    fun showSkipAllConfirm() {
        _uiState.update { it.copy(showSkipAllConfirm = true) }
    }

    fun dismissSkipAllConfirm() {
        _uiState.update { it.copy(showSkipAllConfirm = false) }
    }

    fun confirmSkipAll() {
        val keys = steps.map { it.name.lowercase() }.toSet()
        viewModelScope.launch {
            wizardPreferences.markStepsSkipped(keys)
            wizardPreferences.markWizardComplete()
        }
    }

    fun completeWizard() {
        viewModelScope.launch {
            wizardPreferences.markWizardComplete()
        }
    }
}
