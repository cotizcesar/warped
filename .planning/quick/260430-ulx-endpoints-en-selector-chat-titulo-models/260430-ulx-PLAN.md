---
phase: quick-260430-ulx
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
  - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
  - app/src/main/java/com/warped/ui/chat/ChatUiState.kt
  - app/src/main/java/com/warped/ui/chat/components/ModelSelector.kt
  - app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt
  - app/src/main/java/com/warped/ui/models/ModelsScreen.kt
  - app/src/main/java/com/warped/ui/endpoints/components/EndpointForm.kt (if needed)
autonomous: true
requirements: []

must_haves:
  truths:
    - "Network endpoints appear alongside local models in chat model picker"
    - "Models & Endpoints title has minimal top waste (compact TopAppBar or no TopAppBar)"
    - "Model selection is an icon button near the chat input, opens bottom sheet"
  artifacts:
    - path: "app/src/main/java/com/warped/ui/chat/components/ModelSelector.kt"
      provides: "Unified model picker supporting both local models and endpoints via bottom sheet"
    - path: "app/src/main/java/com/warped/ui/chat/ChatScreen.kt"
      provides: "Model picker icon near input, no model selector at top"
  key_links:
    - from: "ChatViewModel.observeEndpoints"
      to: "ModelSelector endpoints parameter"
      via: "ChatUiState.endpoints"
    - from: "ChatInputBar model icon button"
      to: "ModalBottomSheet with ModelSelector"
      via: "showModelPicker state"
---

<objective>
Three fixes: (1) Show network endpoints in chat model selector alongside local models, (2) Reduce wasted top space in Models & Endpoints title, (3) Move model selector from top of chat to an icon button near the input area.
</objective>

<tasks>

<task type="auto">
  <name>Task 1: Add network endpoints to ChatUiState + ChatViewModel + ModelSelector</name>
  <files>
    app/src/main/java/com/warped/ui/chat/ChatUiState.kt,
    app/src/main/java/com/warped/ui/chat/ChatViewModel.kt,
    app/src/main/java/com/warped/ui/chat/components/ModelSelector.kt,
    app/src/main/java/com/warped/ui/chat/ChatScreen.kt
  </files>
  <action>

**Part A — ChatUiState.kt:** Add `endpoints: List<Endpoint> = emptyList()` field (requires import `com.warped.domain.model.Endpoint`).

**Part B — ChatViewModel.kt:** Add endpoint observation in `init`:
```kotlin
viewModelScope.launch {
    endpointRepository.observeEndpoints().collect { endpoints ->
        _uiState.update { it.copy(endpoints = endpoints) }
    }
}
```

**Part C — ModelSelector.kt:** Add `endpoints: List<Endpoint>` parameter. Update the dropdown logic to show both models and endpoints:

Add import for `com.warped.domain.model.Endpoint`.

Update label logic: when a model or endpoint is selected, show its name. For local models, look up by `filePath == selectedModelId`. For endpoints, look up by `modelId == selectedModelId && apiType == selectedProvider`.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelector(
    selectedModelId: String?,
    selectedProvider: ProviderType?,
    localModels: List<LocalModel>,
    endpoints: List<Endpoint>,
    onModelSelected: (String, ProviderType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLocal = localModels.firstOrNull { it.filePath == selectedModelId }
    val selectedEndpoint = endpoints.firstOrNull { 
        it.modelId == selectedModelId && it.apiType == selectedProvider 
    }
    val hasItems = localModels.isNotEmpty() || endpoints.isNotEmpty()
    val label = when {
        selectedLocal != null -> "${selectedLocal.name} (${selectedProvider?.name ?: "LOCAL"})"
        selectedEndpoint != null -> "${selectedEndpoint.name} (${selectedProvider?.name ?: ""})"
        !hasItems -> stringResource(R.string.no_models_select_model)
        else -> stringResource(R.string.select_model)
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (hasItems) expanded = !expanded }
    ) {
        TextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            enabled = hasItems,
            ...
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // Local models section
            if (localModels.isNotEmpty()) {
                localModels.forEach { model ->
                    DropdownMenuItem(
                        text = { Text("${model.name} (local)") },
                        onClick = {
                            onModelSelected(model.filePath, ProviderType.LOCAL)
                            expanded = false
                        }
                    )
                }
            }
            // Network endpoints section (with divider if both exist)
            if (localModels.isNotEmpty() && endpoints.isNotEmpty()) {
                HorizontalDivider()
            }
            if (endpoints.isNotEmpty()) {
                endpoints.forEach { endpoint ->
                    val modelId = endpoint.modelId
                    if (modelId != null) {
                        DropdownMenuItem(
                            text = { Text("${endpoint.name} · ${endpoint.apiType.name}") },
                            onClick = {
                                onModelSelected(modelId, endpoint.apiType)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
```

Import `HorizontalDivider` from `androidx.compose.material3`. Remove old `DropdownMenuItem` with the `ProviderType.LOCAL` hardcode.

**Part D — ChatScreen.kt:** Pass `uiState.endpoints` to `ModelSelector`:
```kotlin
ModelSelector(
    selectedModelId = uiState.selectedModelId,
    selectedProvider = uiState.selectedProvider,
    localModels = uiState.localModels,
    endpoints = uiState.endpoints,       // <-- NEW
    onModelSelected = { modelId, provider ->
        viewModel.setSelectedModel(modelId, provider)
    }
)
```
</action>
<verify>
<automated>grep -n "endpoints\|observeEndpoints\|HorizontalDivider" app/src/main/java/com/warped/ui/chat/components/ModelSelector.kt app/src/main/java/com/warped/ui/chat/ChatViewModel.kt</automated>
</verify>
</task>

<task type="auto">
  <name>Task 2: Move model selector to icon at bottom + remove top selector</name>
  <files>
    app/src/main/java/com/warped/ui/chat/ChatScreen.kt,
    app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt
  </files>
  <action>

**Part A — ChatScreen.kt:** Remove the `Box` with `ModelSelector` from the top (lines 71-84 in current file). Replace with a `ModalBottomSheet` approach:

1. Add state: `var showModelPicker by remember { mutableStateOf(false) }`

2. Remove the `Box(Modifier.fillMaxWidth().padding(...)) { ModelSelector(...) }` block entirely.

3. Add `ModalBottomSheet` that opens when `showModelPicker` is true:
```kotlin
if (showModelPicker) {
    ModalBottomSheet(
        onDismissRequest = { showModelPicker = false }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Select Model", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            ModelSelector(
                selectedModelId = uiState.selectedModelId,
                selectedProvider = uiState.selectedProvider,
                localModels = uiState.localModels,
                endpoints = uiState.endpoints,
                onModelSelected = { modelId, provider ->
                    viewModel.setSelectedModel(modelId, provider)
                    showModelPicker = false
                }
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}
```

4. Add `showModelPicker` toggle to `ChatInputBar` via a new `onModelPickerClick: () -> Unit` parameter.

**Part B — ChatInputBar.kt:** Add model picker icon button on the LEFT of the text field:

Update signature:
```kotlin
@Composable
fun ChatInputBar(
    text: String,
    isGenerating: Boolean,
    canSend: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    selectedModelName: String? = null,
    onModelPickerClick: () -> Unit = {}
)
```

Add import for `Icons.Filled.Psychology` or `Icons.Filled.SmartToy` or similar model icon.

In the Row layout, before the OutlinedTextField, add:
```kotlin
IconButton(onClick = onModelPickerClick) {
    Icon(Icons.Filled.Psychology, contentDescription = "Select model")
}
```

Remove `Spacer(Modifier.width(8.dp))` between the icon and the text field — use `Spacer(Modifier.width(4.dp))` to reduce space.

**Part C — ChatScreen.kt:** Wire the new callbacks to ChatInputBar:
```kotlin
ChatInputBar(
    text = uiState.inputText,
    isGenerating = uiState.isStreaming,
    canSend = uiState.selectedModelId != null,
    onTextChange = { viewModel.updateInput(it) },
    onSend = { viewModel.sendMessage(uiState.inputText) },
    onStop = { viewModel.stopGeneration() },
    selectedModelName = /* compute display name */,
    onModelPickerClick = { showModelPicker = true }
)
```

Add import for `Icons.Filled.Psychology` to ChatScreen.kt.
Add import for `ModalBottomSheet` to ChatScreen.kt.
Add `import androidx.compose.material3.ModalBottomSheet` and `import androidx.compose.material3.rememberModalBottomSheetState`.

Note: `ModalBottomSheet` requires `@OptIn(ExperimentalMaterial3Api::class)` which is already present on ChatScreen.
</action>
<verify>
<automated>grep -n "showModelPicker\|ModalBottomSheet\|onModelPickerClick\|Psychology" app/src/main/java/com/warped/ui/chat/ChatScreen.kt app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt</automated>
</verify>
</task>

<task type="auto">
  <name>Task 3: Reduce top space in Models & Endpoints title</name>
  <files>app/src/main/java/com/warped/ui/models/ModelsScreen.kt</files>
  <action>
The ModelsScreen uses a standard `TopAppBar` inside `Scaffold`. The default behavior adds status bar padding + TopAppBar height. To reduce wasted space:

**Option A (simplest):** Remove the `TopAppBar` and use a `Text` directly in the Column with minimal padding:

Replace the `Scaffold(topBar = { TopAppBar(title = { Text("Models & Endpoints") }) })` with:

```kotlin
Scaffold(
    floatingActionButton = {
        FloatingActionButton(onClick = { showAddWizard = true }) { Text("+") }
    }
) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .statusBarsPadding()      // Only status bar, no TopAppBar height
    ) {
        Text(
            "Models & Endpoints",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        // ... rest of the content ...
    }
}
```

Add import: `import androidx.compose.foundation.layout.statusBarsPadding`
Remove import of `TopAppBar` parameters if no longer used (TopAppBar might still be imported via material3.*).

This cuts the top space approximately in half (from ~64dp TopAppBar to just status bar + 8dp padding).
</action>
<verify>
<automated>grep -n "statusBarsPadding\|TopAppBar" app/src/main/java/com/warped/ui/models/ModelsScreen.kt</automated>
</verify>
</task>

</tasks>

<success_criteria>
- Network endpoints appear in chat model selector dropdown alongside local models
- Model selector moved to brain/Psychology icon in ChatInputBar, opens ModalBottomSheet
- Models & Endpoints title uses minimal top padding (no TopAppBar, only status bar)
- All files compile without errors
</success_criteria>
