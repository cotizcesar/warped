package com.warped.ui.wizard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warped.data.local.preferences.WizardPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WizardViewModel @Inject constructor(
    private val wizardPreferences: WizardPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(WizardUiState())
    val uiState: StateFlow<WizardUiState> = _uiState.asStateFlow()

    private val stepKeys = listOf(
        "welcome", "engines", "gguf", "litertlm",
        "chat_local", "remote_providers", "chat_remote",
        "presets", "history"
    )

    val stepLabels = listOf(
        "Bienvenida",
        "Motores locales",
        "Modelos GGUF",
        "Modelos LiteRT-LM",
        "Chat local",
        "Proveedores remotos",
        "Chat remoto",
        "Presets",
        "Historial"
    )

    val stepDescriptions = listOf(
        "Conoce Warped: ejecuta LLMs locales y conéctate a proveedores remotos desde un solo lugar.",
        "Aprende sobre GGUF (llama.cpp) y LiteRT-LM, los dos motores de inferencia local.",
        "Descarga modelos GGUF desde Hugging Face y adminístralos en tu dispositivo.",
        "Importa y usa modelos .litertlm con LiteRT-LM, el motor de Google para Android.",
        "Carga un modelo local, configura los parámetros y chatea con streaming en tiempo real.",
        "Conecta Warped a OpenAI, Anthropic, Ollama, LM Studio y servidores personalizados.",
        "Selecciona un modelo remoto y chatea con streaming usando tus propias API keys.",
        "Guarda y reutiliza configuraciones de parámetros de generación como presets.",
        "Explora tu historial de conversaciones y retoma chats donde los dejaste."
    )

    val pageCount: Int get() = stepLabels.size

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
        val currentKey = stepKeys[_uiState.value.currentPage]
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
        val keys = stepKeys.toSet()
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
