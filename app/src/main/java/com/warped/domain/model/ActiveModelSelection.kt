package com.warped.domain.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class ActiveModel(
    val modelId: String,
    val providerType: ProviderType
)

@Singleton
class ActiveModelSelection @Inject constructor() {
    private val _activeModel = MutableStateFlow<ActiveModel?>(null)
    val activeModel: StateFlow<ActiveModel?> = _activeModel.asStateFlow()

    fun select(modelId: String, providerType: ProviderType) {
        _activeModel.value = ActiveModel(modelId = modelId, providerType = providerType)
    }
}
