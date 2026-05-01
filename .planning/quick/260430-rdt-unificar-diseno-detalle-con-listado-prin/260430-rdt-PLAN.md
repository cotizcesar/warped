---
phase: quick-260430-rdt
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt
  - app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt
autonomous: true
requirements: []

must_haves:
  truths:
    - "Detail file cards mirror main search list design (Storage icon, chips, border, primaryContainer colors)"
    - "User can cancel an in-progress download from the detail screen"
    - "Search text survives device rotation and back-navigation from detail"
  artifacts:
    - path: "app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt"
      provides: "Redesigned sibling file cards, cancel button, search text binding"
    - path: "app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt"
      provides: "pauseDownload state reset, onSearchTextChanged method"
  key_links:
    - from: "HuggingFaceScreen search TextField"
      to: "HuggingFaceViewModel.uiState.searchQuery"
      via: "onSearchTextChanged + remember initialization"
      pattern: "remember.*uiState\\.searchQuery"
    - from: "HuggingFaceScreen cancel button"
      to: "HuggingFaceViewModel.pauseDownload"
      via: "onCancelDownload callback"
      pattern: "onCancelDownload"
    - from: "HuggingFaceScreen sibling cards"
      to: "ModelSearchResultCard design"
      via: "Storage icon, BorderStroke, primaryContainer, AssistInfoChip"
      pattern: "SiblingFileCard"
---

<objective>
Fix three UX bugs in the Hugging Face model browser: (1) detail file cards lack visual consistency with the main search list, (2) no way to cancel an in-progress download, (3) search text is lost on configuration changes like screen rotation.

Purpose: Unify the visual language between the search results list and the model detail file list, add missing download cancellation, and preserve user input state across Android lifecycle events.
Output: Updated HuggingFaceScreen.kt with redesigned sibling cards, cancel button, and proper ViewModel-bound search text; updated HuggingFaceViewModel.kt with state-aware pauseDownload and onSearchTextChanged.
</objective>

<execution_context>
@$HOME/.config/opencode/get-shit-done/workflows/execute-plan.md
@$HOME/.config/opencode/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@.planning/PROJECT.md
@.planning/ROADMAP.md

<interfaces>
<!-- Key types and patterns the executor needs. Extracted from codebase. -->

From HuggingFaceScreen.kt — existing ModelSearchResultCard (the design target):
```kotlin
@Composable
private fun ModelSearchResultCard(
    model: com.warped.data.remote.dto.HuggingFaceModel,
    isCompatible: Boolean,
    onClick: () -> Unit
)
// Pattern: Storage icon left, modelName title, fullId subtitle,
// chips row (compatible, downloads, likes, tags),
// primaryContainer cardColors when compatible,
// BorderStroke + primary copy border when compatible
```

From HuggingFaceScreen.kt — existing AssistInfoChip (reuse):
```kotlin
@Composable
private fun AssistInfoChip(text: String)
```

From HuggingFaceScreen.kt — existing ModelDetailScreen signature (to modify):
```kotlin
@Composable
private fun ModelDetailScreen(
    model: HuggingFaceModelDetail,
    siblings: List<HuggingFaceSibling>,
    compatibilityByFileName: Map<String, Int>,
    isDownloading: Boolean,
    downloadProgress: Float,
    downloadingFileName: String,
    downloadError: String?,
    onDownload: (fileName: String, size: Long) -> Unit,
    onBack: () -> Unit
)
// Need to ADD: onCancelDownload: () -> Unit
```

From HuggingFaceUiState.kt:
```kotlin
data class HuggingFaceUiState(
    val searchQuery: String = "",
    val searchResults: List<HuggingFaceModel> = emptyList(),
    val compatibilityByModelId: Map<String, Boolean> = emptyMap(),
    val showCompatibleOnly: Boolean = false,
    val isLoading: Boolean = false,
    val availableMemoryBytes: Long = 0,
    val selectedModel: HuggingFaceModelDetail? = null,
    val modelSiblings: List<HuggingFaceSibling> = emptyList(),
    val compatibilityByFileName: Map<String, Int> = emptyMap(),
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadingFileName: String = "",
    val downloadError: String? = null,
    val error: String? = null
)
```

From HuggingFaceViewModel.kt — current pauseDownload (needs state reset):
```kotlin
fun pauseDownload() {
    downloadManager.pauseDownload()
    // MISSING: _uiState.update { it.copy(isDownloading = false) }
}
```

From HuggingFaceModelDetail DTO — has `downloads` field (Int):
```kotlin
// model.downloads used at HuggingFaceScreen.kt line 349: "${model.downloads} downloads"
```

From HuggingFaceSibling DTO:
```kotlin
// sibling.rfilename, sibling.size, sibling.lfs?.size
// compatibilityByFileName maps rfilename to Int (2=compatible, 1=warn, 0=incompatible)
```
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Redesign "Available Models" cards to match ModelSearchResultCard</name>
  <files>app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt</files>
  <action>
Replace the inline sibling file cards (current lines ~400-448 inside `items(siblings)`) with a new `SiblingFileCard` composable that mirrors `ModelSearchResultCard` (lines 217-286).

**New composable: `SiblingFileCard`**
Create a private composable `SiblingFileCard` after `AssistInfoChip` with these parameters:
- `sibling: HuggingFaceSibling`
- `compatibilityLevel: Int` (2=compatible, 1=warn, 0=incompatible)
- `modelDownloads: Int` — downloads count from the parent model for the chips row
- `isDownloading: Boolean`
- `onDownload: () -> Unit`

**Card layout** (match `ModelSearchResultCard` pattern):
1. **Card container:**
   - If `compatibilityLevel >= 1`: `primaryContainer` at alpha 0.25f container color, `BorderStroke(1.dp, primary.copy(alpha = 0.5f))`
   - Otherwise: `surfaceVariant` at alpha 0.3f, no border
2. **Row 1 — Header:**
   - `Icons.Filled.Storage` icon (tinted `primary`)
   - `Spacer(12.dp)`
   - `Text(sibling.rfilename.substringAfterLast("/"), style = titleMedium, maxLines = 2)` with `Modifier.weight(1f)`
   - If `compatibilityLevel >= 1`: `Icons.Filled.CheckCircle` icon tinted with the compatibility color (primary for level 2, orange for level 1)
3. **Subtitle:**
   - `Text("${formatFileSize(effectiveSize)} · $sizeLabel", style = bodySmall, maxLines = 1)` colored with the compatibility color
   - `effectiveSize = sibling.size.takeIf { it > 0 } ?: sibling.lfs?.size ?: 0L`
   - `sizeLabel` from string resources: `R.string.compatible_with_device` for level 2, `R.string.may_be_too_large` for level 1/0
4. **Row 2 — Chips** (horizontal, spacedBy 8.dp):
   - If compatible: `AssistInfoChip(text = stringResource(R.string.compatible_with_device))`
   - `AssistInfoChip(text = formatFileSize(effectiveSize))`
   - `AssistInfoChip(text = "$modelDownloads downloads")`
5. **Download button:** `OutlinedButton` aligned to `Alignment.End`, enabled when `!isDownloading`, onClick = `onDownload`

**Update `items(siblings)` call site** (inside ModelDetailScreen LazyColumn):
Replace the current inline `Card { Row { ... } }` block with:
```kotlin
val compatLevel = compatibilityByFileName[sibling.rfilename] ?: 0
SiblingFileCard(
    sibling = sibling,
    compatibilityLevel = compatLevel,
    modelDownloads = model.downloads,
    isDownloading = isDownloading,
    onDownload = { onDownload(sibling.rfilename, effectiveSize) }
)
```
where `effectiveSize` is computed the same way (in the item block before calling the composable).
</action>
<verify>
<automated>grep -n "SiblingFileCard" app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt</automated>
</verify>
<done>
Sibling file cards in ModelDetailScreen structurally match ModelSearchResultCard: Storage icon, filename title, chips row (compatible, size, downloads), primaryContainer colors + border when compatible, OutlinedButton for download. Old inline Card block removed.
</done>
</task>

<task type="auto">
  <name>Task 2: Add download cancel button and fix search text persistence</name>
  <files>
    app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt,
    app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt
  </files>
  <action>

**Part A — HuggingFaceViewModel.kt: State-aware cancel + search text binding**

1. **Update `pauseDownload()`** (line 149) to reset UI state:
   ```kotlin
   fun pauseDownload() {
       downloadManager.pauseDownload()
       _uiState.update { it.copy(isDownloading = false, downloadProgress = 0f, downloadingFileName = "") }
   }
   ```
   This turns `pauseDownload` into a cancel operation — the download is paused at the manager level and the UI resets.

2. **Add `onSearchTextChanged()` method** for ViewModel as source of truth:
   ```kotlin
   fun onSearchTextChanged(text: String) {
       _uiState.update { it.copy(searchQuery = text) }
   }
   ```

**Part B — HuggingFaceScreen.kt: Cancel button UI**

1. **Add `onCancelDownload` parameter to `ModelDetailScreen`** signature (after `onDownload`, before `onBack`):
   ```kotlin
   onCancelDownload: () -> Unit,
   ```

2. **Extract the download progress `item` block** (lines 384-392) into a Row with cancel:
   ```kotlin
   if (isDownloading) {
       item {
           Column {
               LinearProgressIndicator(
                   progress = { downloadProgress },
                   modifier = Modifier.fillMaxWidth()
               )
               Row(
                   modifier = Modifier.fillMaxWidth(),
                   horizontalArrangement = Arrangement.SpaceBetween,
                   verticalAlignment = Alignment.CenterVertically
               ) {
                   Text(
                       "Downloading: $downloadingFileName (${(downloadProgress * 100).toInt()}%)",
                       modifier = Modifier.weight(1f)
                   )
                   TextButton(onClick = onCancelDownload) {
                       Text("Cancel")
                   }
               }
           }
       }
   }
   ```

3. **Wire the callback** in `HuggingFaceScreen` (line 80 area):
   ```kotlin
   onCancelDownload = { viewModel.pauseDownload() },
   ```
   Add this line right after the `onDownload = { ... }` block.

**Part C — HuggingFaceScreen.kt: Bind search text to ViewModel**

1. **Initialize `searchText` from ViewModel state** (line 57):
   ```kotlin
   var searchText by remember { mutableStateOf(uiState.searchQuery) }
   ```
   This restores the text after rotation or back-navigation because the ViewModel survives.

2. **Update `onValueChange`** in `OutlinedTextField` (line 98) to sync with ViewModel:
   ```kotlin
   onValueChange = {
       searchText = it
       viewModel.onSearchTextChanged(it)
   },
   ```

This two-way binding (remember initializes from ViewModel, onValueChange pushes to ViewModel) ensures:
- After rotation: `searchText` re-initializes from `uiState.searchQuery` (which survived)
- During typing: debounced search triggers as before via `LaunchedEffect(searchText)`
- After back-navigation from detail: text is restored
</action>
<verify>
<automated>grep -n "onCancelDownload" app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt && grep -n "onSearchTextChanged" app/src/main/java/com/warped/ui/huggingface/HuggingFaceViewModel.kt && grep -n "remember.*uiState.searchQuery" app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt</automated>
</verify>
<done>
1. Cancel button appears next to download progress in ModelDetailScreen; pressing it calls `pauseDownload()` which resets `isDownloading`, `downloadProgress`, and `downloadingFileName`.
2. `searchText` initialized from `uiState.searchQuery` on composition; `onValueChange` pushes to ViewModel via `onSearchTextChanged`. Text survives rotation.
3. No compilation errors in either file.
</done>
</task>

</tasks>

<verification>
Build check: `./gradlew :app:compileDebugKotlin` passes with no errors.

Manual verification:
1. Navigate to Hugging Face screen — search text "gguf" is pre-filled from init.
2. Type a query, rotate device — search text persists and search results remain.
3. Tap a model to see detail — file cards have Storage icon, chips, BorderStroke, primaryContainer colors matching the main list design.
4. Tap Download on a file — progress shows with Cancel button; tap Cancel — download cancels, progress disappears.
5. Tap Back — search results still visible, search text still in field.
</verification>

<success_criteria>
- `SiblingFileCard` composable exists with structure matching `ModelSearchResultCard`
- Cancel button wired to `pauseDownload()` which resets `isDownloading`
- Search text survives device rotation (initialized from `uiState.searchQuery`)
- `onSearchTextChanged` exists in ViewModel
- Both files compile without errors
</success_criteria>

<output>
After completion, create `.planning/quick/260430-rdt-unificar-diseno-detalle-con-listado-prin/260430-rdt-SUMMARY.md`
</output>
