---
phase: quick-260430-tac
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/com/warped/data/local/download/ModelDownloadManager.kt
  - app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt
  - app/src/main/java/com/warped/ui/huggingface/HuggingFaceUiState.kt
  - app/src/main/java/com/warped/ui/models/ModelsViewModel.kt
  - app/src/main/java/com/warped/ui/models/ModelsUiState.kt
  - app/src/main/java/com/warped/ui/models/ModelsScreen.kt
autonomous: true
requirements: []

must_haves:
  truths:
    - "Downloads continue in background when user switches screens"
    - "Active/downloading models appear in Models & Endpoints list with progress"
    - "Incomplete/paused downloads can be cancelled and deleted"
  artifacts:
    - path: "app/src/main/java/com/warped/data/local/download/ModelDownloadManager.kt"
      provides: "Application-scoped download coroutine + incomplete download management"
    - path: "app/src/main/java/com/warped/ui/models/ModelsScreen.kt"
      provides: "Active download cards with progress + cancel/delete actions"
  key_links:
    - from: "ModelDownloadManager.downloadScope"
      to: "modelsScreen observes downloadStates"
      via: "ModelsViewModel downloadStates collection"
---

<objective>
Make model downloads survive screen navigation (background downloads). Show active/incomplete downloads in the Models & Endpoints list with progress bars. Allow cancelling and deleting incomplete downloads.
</objective>

<execution_context>
@$HOME/.config/opencode/get-shit-done/workflows/execute-plan.md
</execution_context>

<tasks>

<task type="auto">
  <name>Task 1: Make downloads survive screen changes with application-scoped coroutine</name>
  <files>app/src/main/java/com/warped/data/local/download/ModelDownloadManager.kt</files>
  <action>

**Add application-scoped coroutine to ModelDownloadManager:**

1. Add imports for `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.SupervisorJob`, `kotlinx.coroutines.launch`.

2. Add a download scope as a property:
```kotlin
private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
```

3. **Change `downloadModel()` from `suspend` to a non-suspend fire-and-forget `startDownload()`** that launches in `downloadScope`:

Replace the entire `downloadModel()` signature and body. The new method:
- Is non-suspend: `fun startDownload(modelId: String, fileName: String, fileUrl: String, fileSizeBytes: Long)`
- Updates `_downloadStates` immediately with downloading state
- Launches the actual download in `downloadScope.launch { ... }`
- The download body is the same as the current `downloadModel()` body, but:
  - Remove `withContext(Dispatchers.IO)` wrapper (the scope already uses IO)
  - Remove `onProgress` parameter — progress is reported via `_downloadStates` updates (which already happen in the current code via `updateState()`)
  - On completion: parse GGUF metadata, save to Room, update download state to completed
  - On failure: update download state with error

4. **Add `cancelDownload(modelId: String)`** method:
```kotlin
fun cancelDownload(modelId: String) {
    // Set isPaused flag to stop the download loop
    isPaused = true  
    updateState(modelId) {
        it.copy(isDownloading = false, isPaused = true, error = "Cancelled")
    }
}
```
Note: the existing `@Volatile isPaused` flag is checked in the download loop. This needs to be per-download, not global. Change `isPaused` to a `ConcurrentHashMap<String, Boolean>` or check the download state directly:
```kotlin
// In the download loop, replace:
// if (isPaused) { ... }
// With:
val currentState = _downloadStates.value[modelId]
if (currentState?.isPaused == true) { ... }
```
Remove the `@Volatile private var isPaused = false` field and use the download state instead.

5. **Add `deleteIncompleteDownload(modelId: String, fileName: String)`** method:
```kotlin
fun deleteIncompleteDownload(modelId: String, fileName: String) {
    val localName = fileName.substringAfterLast("/")
    val file = File(modelsDir, localName)
    if (file.exists()) file.delete()
    _downloadStates.update { it - modelId }
}
```

6. **Keep `pauseDownload()` and `resumeDownload()`** but update them to work per-download using the state map instead of the global flag:
```kotlin
fun pauseDownload(modelId: String) {
    updateState(modelId) { it.copy(isPaused = true, isDownloading = false) }
}

fun resumeDownload(modelId: String, fileUrl: String) {
    updateState(modelId) { it.copy(isPaused = false, isDownloading = true) }
    // Re-trigger the download if needed
}
```

7. **Update the `getDownloadState()`** to be useful for the caller to check if a download is active for a given modelId.

8. Remove the old `@Volatile private var isPaused = false` field — replace with state-map checks.
</action>
<verify>
<automated>grep -n "downloadScope\|startDownload\|cancelDownload\|deleteIncompleteDownload" app/src/main/java/com/warped/data/local/download/ModelDownloadManager.kt</automated>
</verify>
</task>

<task type="auto">
  <name>Task 2: Update HuggingFaceViewModel to use new download API + observe states</name>
  <files>
    app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt,
    app/src/main/java/com/warped/ui/huggingface/HuggingFaceUiState.kt
  </files>
  <action>

**Part A — HuggingFaceUiState.kt:** Add `activeDownloadId: String? = null` field so the HuggingFace screen knows which download to track:
```kotlin
val activeDownloadId: String? = null,
```

**Part B — HuggingFaceViewModel.kt:**

1. In `init`, add a collector for `downloadManager.downloadStates` to sync the HuggingFace UI with ongoing downloads:
```kotlin
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
```

2. **Rewrite `downloadFile()`** to call `downloadManager.startDownload()` and set `activeDownloadId`:
```kotlin
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
```

3. **Update `pauseDownload()`** to pass modelId:
```kotlin
fun pauseDownload() {
    val activeId = _uiState.value.activeDownloadId ?: return
    downloadManager.cancelDownload(activeId)
    _uiState.update { it.copy(isDownloading = false, downloadProgress = 0f, downloadingFileName = "") }
}
```
</action>
<verify>
<automated>grep -n "startDownload\|activeDownloadId\|downloadStates.collect" app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt</automated>
</verify>
</task>

<task type="auto">
  <name>Task 3: Show active/incomplete downloads in ModelsScreen with cancel/delete</name>
  <files>
    app/src/main/java/com/warped/ui/models/ModelsViewModel.kt,
    app/src/main/java/com/warped/ui/models/ModelsUiState.kt,
    app/src/main/java/com/warped/ui/models/ModelsScreen.kt
  </files>
  <action>

**Part A — ModelsUiState.kt:** Add `activeDownloads: List<com.warped.data.local.download.DownloadState> = emptyList()`.

**Part B — ModelsViewModel.kt:**

1. Inject `ModelDownloadManager`:
```kotlin
private val modelDownloadManager: ModelDownloadManager,
```

2. In `init`, add a collector for active downloads:
```kotlin
viewModelScope.launch {
    modelDownloadManager.downloadStates.collect { states ->
        val active = states.values.filter { it.isDownloading || it.isPaused || it.progress < 1f }
        _uiState.update { it.copy(activeDownloads = active) }
    }
}
```

3. Add methods:
```kotlin
fun cancelDownload(modelId: String) {
    modelDownloadManager.cancelDownload(modelId)
}

fun deleteIncompleteDownload(download: com.warped.data.local.download.DownloadState) {
    modelDownloadManager.deleteIncompleteDownload(download.modelId, download.fileName)
}
```

**Part C — ModelsScreen.kt:** Add an "Active Downloads" section before the "Local Models" section in the LazyColumn:

```kotlin
if (uiState.activeDownloads.isNotEmpty()) {
    item { Text("Active Downloads", style = MaterialTheme.typography.titleMedium) }
    items(uiState.activeDownloads, key = { "dl-${it.modelId}" }) { download ->
        DownloadCard(
            download = download,
            onCancel = { viewModel.cancelDownload(download.modelId) },
            onDeleteIncomplete = { viewModel.deleteIncompleteDownload(download) }
        )
    }
}
```

Add a `DownloadCard` composable:
```kotlin
@Composable
private fun DownloadCard(
    download: com.warped.data.local.download.DownloadState,
    onCancel: () -> Unit,
    onDeleteIncomplete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(download.fileName.substringAfterLast("/"), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            if (download.isDownloading) {
                LinearProgressIndicator(
                    progress = { download.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text("${(download.progress * 100).toInt()}% · ${formatFileSize(download.downloadedBytes)} / ${formatFileSize(download.totalBytes)}",
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
            } else if (download.isPaused) {
                Text("Paused · ${formatFileSize(download.downloadedBytes)} / ${formatFileSize(download.totalBytes)}",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF9800))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDeleteIncomplete) { Text("Delete") }
                }
            } else {
                Text("Interrupted · ${formatFileSize(download.downloadedBytes)} downloaded",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                if (download.error != null) {
                    Text(download.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onDeleteIncomplete) { Text("Delete partial file") }
            }
        }
    }
}
```

Add import for `androidx.compose.ui.graphics.Color` if not already present in ModelsScreen.kt.
</action>
<verify>
<automated>grep -n "DownloadCard\|activeDownloads\|cancelDownload\|deleteIncompleteDownload" app/src/main/java/com/warped/ui/models/ModelsScreen.kt</automated>
</verify>
</task>

</tasks>

<success_criteria>
- ModelDownloadManager owns download coroutine scope — downloads survive screen changes
- HuggingFaceViewModel observes downloadStates flow for progress updates
- ModelsScreen shows active/incomplete downloads with progress, cancel, and delete buttons
- Incomplete download files can be deleted from the Models list
- All files compile without errors
</success_criteria>

<output>
After completion, create `.planning/quick/260430-tac-descargas-en-segundo-plano-listar-modelo/260430-tac-SUMMARY.md`
</output>
