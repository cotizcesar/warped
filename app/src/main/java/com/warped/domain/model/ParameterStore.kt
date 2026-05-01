package com.warped.domain.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ParameterStore @Inject constructor() {
    private val _parameters = MutableStateFlow(GenerationParameters())
    val parameters: StateFlow<GenerationParameters> = _parameters.asStateFlow()

    fun update(parameters: GenerationParameters) {
        _parameters.value = parameters
    }
}
