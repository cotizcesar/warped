package com.warped.ui.endpoints

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warped.ui.endpoints.components.EndpointCard
import com.warped.ui.endpoints.components.EndpointForm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EndpointsScreen(
    viewModel: EndpointsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Endpoints") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showAddForm() }) {
                Text("+")
            }
        }
    ) { padding ->
        if (uiState.isFormVisible) {
            EndpointForm(
                name = uiState.formName,
                url = uiState.formUrl,
                apiType = uiState.formApiType,
                lmStudioMode = uiState.formLmStudioMode,
                modelId = uiState.formModelId,
                apiKey = uiState.formApiKey,
                hasSavedKey = uiState.hasSavedApiKey,
                onFieldChange = { field, value -> viewModel.updateFormField(field, value) },
                onSave = { viewModel.saveEndpoint() },
                onDismiss = { viewModel.dismissForm() }
            )
        } else if (uiState.endpoints.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text("No endpoints configured", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(uiState.endpoints, key = { it.id }) { endpoint ->
                    EndpointCard(
                        endpoint = endpoint,
                        connectionStatus = uiState.testStatus[endpoint.id],
                        onTest = { viewModel.testConnection(endpoint.id) },
                        onEdit = { viewModel.showEditForm(endpoint) },
                        onDelete = { viewModel.deleteEndpoint(endpoint.id) },
                        onActivate = { viewModel.activateEndpoint(endpoint.id) }
                    )
                }
            }
        }

        if (uiState.error != null) {
            Snackbar(
                modifier = Modifier.padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.dismissForm() }) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text(uiState.error ?: "")
            }
        }
    }
}
