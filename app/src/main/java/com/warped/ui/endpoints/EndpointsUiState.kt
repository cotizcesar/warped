package com.warped.ui.endpoints

import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.Endpoint

data class EndpointsUiState(
    val endpoints: List<Endpoint> = emptyList(),
    val isLoading: Boolean = false,
    val isFormVisible: Boolean = false,
    val editingEndpoint: Endpoint? = null,
    val formName: String = "",
    val formUrl: String = "",
    val formApiType: String = "OPENAI",
    val formLmStudioMode: String = "native",
    val formModelId: String = "",
    val formApiKey: String = "",
    val hasSavedApiKey: Boolean = false,
    val testStatus: Map<Long, ConnectionStatus> = emptyMap(),
    val error: String? = null
)
