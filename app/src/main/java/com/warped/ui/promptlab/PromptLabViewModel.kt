package com.warped.ui.promptlab

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warped.R
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.data.remote.provider.ProviderRouter
import com.warped.domain.model.ActiveModelSelection
import com.warped.domain.model.ChatMessage
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.GenerationParameters
import com.warped.domain.model.Role
import com.warped.domain.model.StreamToken
import com.warped.domain.model.SyntaxTheme
import com.warped.domain.prompt.PromptTemplate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class PromptLabViewModel @Inject constructor(
    @Named("promptTemplate")
    private val templates: Set<@JvmSuppressWildcards PromptTemplate>,
    private val providerRouter: ProviderRouter,
    private val activeModelSelection: ActiveModelSelection,
    private val advancedPreferences: AdvancedPreferences,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    private val _ui = MutableStateFlow(
        PromptLabUiState(
            templates = templates.toList().sortedBy { it.name },
        )
    )

    val codeTheme: StateFlow<SyntaxTheme> = advancedPreferences.syntaxTheme
        .stateIn(viewModelScope, SharingStarted.Eagerly, SyntaxTheme.MONOKAI)

    val codeFontScale: StateFlow<Float> = advancedPreferences.codeFontScale
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)

    val ui: StateFlow<PromptLabUiState> = combine(
        _ui,
        activeModelSelection.activeModel,
    ) { state, active ->
        state.copy(activeModelId = active?.modelId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _ui.value)

    fun selectTemplate(id: String) {
        _ui.update { it.copy(selectedTemplateId = id, output = "", error = null) }
    }

    fun setInput(s: String) {
        _ui.update { it.copy(input = s) }
    }

    fun setLanguage(lang: String) {
        _ui.update { it.copy(targetLanguage = lang.ifBlank { "English" }) }
    }

    fun clearError() {
        _ui.update { it.copy(error = null) }
    }

    fun run() {
        val s = _ui.value
        val template = s.templates.firstOrNull { it.id == s.selectedTemplateId } ?: return
        if (s.input.isBlank() || s.isRunning) return

        val active = activeModelSelection.activeModel.value
        if (active == null) {
            _ui.update { it.copy(error = context.getString(R.string.lab_error_no_model)) }
            return
        }
        val helper = try {
            providerRouter.resolveLocalHelper(active.providerType, active.modelId)
        } catch (e: Exception) {
            _ui.update { it.copy(error = e.message ?: context.getString(R.string.lab_error_resolve)) }
            return
        }
        val userPrompt = template.userPromptTemplate(
            mapOf("input" to s.input, "language" to s.targetLanguage)
        )
        val request = ChatRequest(
            messages = listOf(
                ChatMessage(role = Role.SYSTEM, content = template.systemPrompt),
                ChatMessage(role = Role.USER, content = userPrompt),
            ),
            parameters = GenerationParameters(temperature = 0.3f, maxTokens = 1024),
        )

        viewModelScope.launch {
            _ui.update { it.copy(isRunning = true, output = "", error = null) }
            val buffer = StringBuilder()
            try {
                helper.runInference(request, enableThinking = false).collect { token ->
                    when (token) {
                        is StreamToken.Delta -> {
                            buffer.append(token.content)
                            _ui.update { it.copy(output = buffer.toString()) }
                        }
                        is StreamToken.Done -> _ui.update { it.copy(isRunning = false) }
                        is StreamToken.Error -> _ui.update {
                            it.copy(isRunning = false, error = token.message)
                        }
                        // 47-02: tool status is not text — ignored by PromptLab.
                        is StreamToken.ToolStatus -> Unit
                        // 47-03: remote-loop completion records are not text either.
                        is StreamToken.ToolCompleted -> Unit
                        // Phase 57 UI-review: typed tools-unsupported
                        // notice carries no text — ignored by PromptLab.
                        is StreamToken.ToolsUnsupported -> Unit
                        // Quick-task (live-thinking): native thought carries
                        // no answer text — ignored by PromptLab.
                        is StreamToken.Thinking -> Unit
                    }
                }
            } catch (e: Exception) {
                _ui.update { it.copy(isRunning = false, error = e.message ?: context.getString(R.string.lab_error_inference)) }
            }
        }
    }
}
