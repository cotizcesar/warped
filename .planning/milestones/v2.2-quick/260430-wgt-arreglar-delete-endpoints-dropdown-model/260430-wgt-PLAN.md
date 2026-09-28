---
phase: quick-260430-wgt
plan: 01
type: execute
wave: 1
files_modified:
  - app/src/main/java/com/warped/ui/endpoints/components/EndpointForm.kt
  - app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt
  - app/src/main/java/com/warped/ui/models/ModelsViewModel.kt
  - app/src/main/java/com/warped/ui/models/ModelsUiState.kt
  - app/src/main/java/com/warped/data/repository/EndpointRepositoryImpl.kt
autonomous: true
---

<objective>
Fix three bugs: (1) endpoint deletion silently failing, (2) no model dropdown in endpoint form, (3) LM Studio returning 400 "input required" in chat.
</objective>

<tasks>

<task type="auto">
  <name>Task 1: Fix endpoint deletion + ensure confirmation dialog works</name>
  <files>app/src/main/java/com/warped/data/repository/EndpointRepositoryImpl.kt</files>
  <action>
The `deleteEndpoint()` method silently returns if the endpoint ID isn't found in DB:
```kotlin
val entity = endpointDao.getById(endpointId) ?: return
```

If the endpoint list has stale data or `endpoint.id == 0`, the deletion silently does nothing. Fix: try the delete by ID directly and catch the error:

```kotlin
override suspend fun deleteEndpoint(endpointId: Long) {
    if (endpointId == 0L) throw IllegalArgumentException("Invalid endpoint ID")
    apiKeyStore.deleteKey(endpointId)
    endpointDao.deleteById(endpointId)
}
```

Remove the `getById` check — Room's `deleteById` with `@Query("DELETE FROM endpoints WHERE id = :id")` handles non-existent IDs gracefully (no-op). The `apiKeyStore.deleteKey()` is also safe to call with any ID. This ensures the delete ALWAYS runs even if the cached entity is stale. The UI will update reactively via the Flow after the DELETE query completes.
</action>
<verify>
<automated>grep -n "deleteEndpoint\|deleteById\|throw.*Invalid" app/src/main/java/com/warped/data/repository/EndpointRepositoryImpl.kt</automated>
</verify>
</task>

<task type="auto">
  <name>Task 2: Add model dropdown to EndpointForm (fetch from LM Studio)</name>
  <files>
    app/src/main/java/com/warped/ui/endpoints/components/EndpointForm.kt,
    app/src/main/java/com/warped/ui/models/ModelsViewModel.kt,
    app/src/main/java/com/warped/ui/models/ModelsUiState.kt
  </files>
  <action>

**Part A — EndpointForm.kt:** Add parameters for available models list and a fetch callback:

Add to the function signature:
```kotlin
availableModels: List<String> = emptyList(),
isFetchingModels: Boolean = false,
onFetchModels: () -> Unit = {},
```

Replace the current `OutlinedTextField` for `modelId` with an `ExposedDropdownMenuBox` that shows fetched models. If models haven't been fetched yet, show a "Fetch Models" button that calls `onFetchModels`. If models are available, show them in a dropdown:

```kotlin
var modelDropdownExpanded by remember { mutableStateOf(false) }
val modelOptions = if (availableModels.isNotEmpty()) availableModels else listOf(modelId).filter { it.isNotBlank() }

ExposedDropdownMenuBox(
    expanded = modelDropdownExpanded,
    onExpandedChange = { if (availableModels.isNotEmpty()) modelDropdownExpanded = !modelDropdownExpanded }
) {
    OutlinedTextField(
        value = modelId,
        onValueChange = { onFieldChange("modelId", it) },
        label = { Text("Model ID") },
        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        singleLine = true,
        placeholder = { Text("Select or type model ID") },
        trailingIcon = {
            if (isFetchingModels) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else if (availableModels.isEmpty()) {
                TextButton(onClick = onFetchModels) { Text("Fetch") }
            } else {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded)
            }
        },
        enabled = true
    )
    if (availableModels.isNotEmpty()) {
        ExposedDropdownMenu(
            expanded = modelDropdownExpanded,
            onDismissRequest = { modelDropdownExpanded = false }
        ) {
            availableModels.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model) },
                    onClick = {
                        onFieldChange("modelId", model)
                        modelDropdownExpanded = false
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Custom (type manually)", color = MaterialTheme.colorScheme.primary) },
                onClick = { modelDropdownExpanded = false }
            )
        }
    }
}
```

Import `CircularProgressIndicator`.

**Part B — ModelsUiState.kt:** Add field for available endpoint models (string list):
```kotlin
val availableEndpointModels: List<String> = emptyList(),
val isFetchingEndpointModels: Boolean = false,
```

**Part C — ModelsViewModel.kt:** Add `fetchEndpointModels()` that builds an LMStudioProvider from the current form URL and calls `listModels()`:

```kotlin
fun fetchEndpointModels() {
    val state = _uiState.value
    val url = state.formUrl.ifBlank { return }
    viewModelScope.launch {
        _uiState.update { it.copy(isFetchingEndpointModels = true, availableEndpointModels = emptyList()) }
        try {
            val provider = LMStudioProvider(baseUrl = url, modelId = "fetch")
            val result = provider.listModels()
            result.onSuccess { models ->
                _uiState.update { it.copy(availableEndpointModels = models.map { m -> m.id }, isFetchingEndpointModels = false) }
            }.onFailure { e ->
                _uiState.update { it.copy(isFetchingEndpointModels = false, error = "Failed to fetch: ${e.message}") }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isFetchingEndpointModels = false, error = e.message) }
        }
    }
}
```

Inject `import com.warped.data.remote.provider.LMStudioProvider`.

Update the `EndpointForm` calls in ModelsScreen to pass `availableModels`, `isFetchingModels`, `onFetchModels`.
</action>
<verify>
<automated>grep -n "fetchEndpointModels\|availableEndpointModels\|isFetchingEndpointModels\|onFetchModels" app/src/main/java/com/warped/ui/models/ModelsViewModel.kt app/src/main/java/com/warped/ui/models/ModelsUiState.kt app/src/main/java/com/warped/ui/endpoints/components/EndpointForm.kt</automated>
</verify>
</task>

<task type="auto">
  <name>Task 3: Fix LM Studio 400 "input required" error</name>
  <files>app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt</files>
  <action>
The LM Studio native v1 API may reject `max_tokens: -1`. Change to send `null` when the parameter is not set:

In `chat()`:
```kotlin
val body = OpenAiChatRequest(
    model = modelId,
    messages = messages,
    stream = true,
    temperature = request.parameters.temperature,
    topP = request.parameters.topP,
    maxTokens = request.parameters.maxTokens.takeIf { it > 0 },
    stop = null
)
```

Changed: `maxTokens.takeIf { it > 0 } ?: -1` → `maxTokens.takeIf { it > 0 }`. When `maxTokens <= 0`, `takeIf` returns `null`, and Retrofit serializes `null` as omitting the field (since `maxTokens: Int?` is nullable with default `null`). LM Studio handles missing `max_tokens` by using its default (unlimited or model default).

Also, add `stop = null` explicitly to prevent sending empty stop sequences.
</action>
<verify>
<automated>grep -n "maxTokens\|stop.*null\|-1" app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt</automated>
</verify>
</task>

</tasks>

<success_criteria>
- Endpoint deletion works (no silent return on missing entity)
- Endpoint form has "Fetch" button that populates model dropdown from LM Studio
- Chat no longer returns 400 "input required" from LM Studio
- Build compiles
</success_criteria>
