---
phase: quick-260430-ryn
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt
  - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
  - app/src/main/java/com/warped/ui/navigation/NavGraph.kt
autonomous: true
requirements:
  - CHAT-REDESIGN-01

must_haves:
  truths:
    - "Chat input is a rounded pill with transparent underline — ChatGPT/Claude style"
    - "Send button shows Icons.AutoMirrored.Filled.Send, stop button shows Icons.Filled.Stop"
    - "Model selector appears inline above the chat messages (not in TopAppBar)"
    - "Bottom navbar shows icons only, no text labels"
    - "Chat screen has no TopAppBar — model picker is inline, drawer works via swipe"
  artifacts:
    - path: "app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt"
      provides: "ChatGPT-style pill input with Material send/stop icons"
      min_lines: 30
    - path: "app/src/main/java/com/warped/ui/chat/ChatScreen.kt"
      provides: "Chat layout without TopAppBar; ModelSelector inline above LazyColumn"
      min_lines: 80
    - path: "app/src/main/java/com/warped/ui/navigation/NavGraph.kt"
      provides: "Icon-only compact bottom navigation"
      min_lines: 50
  key_links:
    - from: "ChatInputBar.kt → IconButton(onClick = onSend)"
      to: "Icon(Icons.AutoMirrored.Filled.Send)"
      via: "material-icons-extended already in libs.versions.toml"
    - from: "ChatInputBar.kt → IconButton(onClick = onStop)"
      to: "Icon(Icons.Filled.Stop)"
      via: "material-icons-extended"
    - from: "ChatScreen.kt → Column body"
      to: "ModelSelector composable (inline, no longer in TopAppBar title)"
      via: "direct composable call above LazyColumn"
    - from: "NavGraph.kt → NavigationBarItem"
      to: "label = null (removed)"
      via: "parameter set to null; contentDescription on icon retained"
---

<objective>
Redesign the chat screen UI: remove the TopAppBar, make the model selector an inline dropdown above messages, redesign the input bar to a ChatGPT-style pill with Material icons for send/stop, and compact the bottom navbar to icons-only.

Purpose: Modernize the chat interface to feel like ChatGPT/Claude — cleaner, more spacious, with interactive model selection and proper Material iconography.
Output: Three modified files — ChatInputBar.kt (complete redesign), ChatScreen.kt (TopAppBar removed + ModelSelector inline), NavGraph.kt (icon-only navbar).
</objective>

<execution_context>
@$HOME/.config/opencode/get-shit-done/workflows/execute-plan.md
@$HOME/.config/opencode/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/STATE.md
@.planning/ROADMAP.md

## Interfaces

### From ChatScreen.kt (current structure — after TopAppBar removal, these remain)
```kotlin
// ChatScreen uses ModalNavigationDrawer wrapping a Scaffold
// ViewModel exposes: uiState (ChatUiState) with:
//   - selectedModelId: String?, selectedProvider: ProviderType?, localModels: List<LocalModel>
//   - inputText: String, isStreaming: Boolean
//   - messages: List<ChatMessage>, streamingContent: String

// ChatInputBar signature (will be kept):
fun ChatInputBar(
    text: String,
    isGenerating: Boolean,
    canSend: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
)

// ModelSelector signature:
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelector(
    selectedModelId: String?,
    selectedProvider: ProviderType?,
    localModels: List<LocalModel>,
    onModelSelected: (String, ProviderType) -> Unit
)
// Uses ExposedDropdownMenuBox internally — works inline above LazyColumn
```

### From NavGraph.kt
```kotlin
sealed class Screen(val route: String, val label: String, val icon: ImageVector)
// Chat → Icons.AutoMirrored.Filled.Chat
// Models → Icons.Filled.Memory
// Presets → Icons.Filled.Settings
// Only Chat/Models/Presets are in the tab bar (tabs list)
```
</context>

<tasks>

<task type="auto">
  <name>Task 1: Redesign ChatInputBar — pill input + Material icons</name>
  <files>app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt</files>
  <action>
Replace the entire ChatInputBar composable with a ChatGPT/Claude-style design:

1. **Remove** the `Surface(tonalElevation, shadowElevation)` wrapper — use a flat `Row` instead.
2. **Layout:** `Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom)`
3. **TextField:** `OutlinedTextField` with:
   - `modifier = Modifier.weight(1f)`
   - `shape = MaterialTheme.shapes.extraLarge` (rounded pill)
   - `colors = TextFieldDefaults.colors(unfocusedIndicatorColor = Color.Transparent, focusedIndicatorColor = Color.Transparent)` — hides the underline so it looks like a standalone pill
   - `placeholder = { Text(stringResource(R.string.type_message)) }`
   - `enabled = !isGenerating && canSend`
   - `maxLines = 4`
   - Remove `singleLine = false` (not needed when maxLines is set)
4. **Send button (when NOT generating):** `IconButton(onClick = onSend, enabled = canSend && text.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message") }` — replaces `FilledIconButton` + `Text("→")`
5. **Stop button (when generating):** `IconButton(onClick = onStop) { Icon(Icons.Filled.Stop, contentDescription = "Stop generating") }` — replaces `FilledIconButton` + `Text("■")`
6. **Spacer:** `Spacer(Modifier.width(8.dp))` between TextField and icon button (keep as-is)
7. **Imports:** Add `import androidx.compose.material.icons.automirrored.filled.Send`, `import androidx.compose.material.icons.filled.Stop`, `import androidx.compose.ui.Alignment`, `import androidx.compose.ui.graphics.Color`

**Exact replacement code:**

```kotlin
package com.warped.ui.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.warped.R

@Composable
fun ChatInputBar(
    text: String,
    isGenerating: Boolean,
    canSend: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.type_message)) },
            enabled = !isGenerating && canSend,
            maxLines = 4,
            shape = MaterialTheme.shapes.extraLarge,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedIndicatorColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent
            )
        )
        Spacer(Modifier.width(8.dp))
        if (isGenerating) {
            IconButton(onClick = onStop) {
                Icon(Icons.Filled.Stop, contentDescription = "Stop generating")
            }
        } else {
            IconButton(onClick = onSend, enabled = canSend && text.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message")
            }
        }
    }
}
```

Note: Use `OutlinedTextFieldDefaults.colors(...)` (not `TextFieldDefaults.colors(...)`) — Compose Material 3 uses `OutlinedTextFieldDefaults` for `OutlinedTextField` color customization.
  </action>
  <verify>
    <automated>grep -n "Text(\"■\")" app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt; grep -n "Text(\"→\")" app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt; test $? -ne 0 || echo "OLD ICONS FOUND — remove them"</automated>
  </verify>
  <done>
Text("■") and Text("→") are replaced with Icon(Icons.Filled.Stop) and Icon(Icons.AutoMirrored.Filled.Send) respectively. Surface wrapper removed. TextField uses shape = extraLarge with transparent indicators. Row layout with Alignment.Bottom.
  </done>
</task>

<task type="auto">
  <name>Task 2: Remove TopAppBar + AssistChip, inline ModelSelector, compact navbar</name>
  <files>
    app/src/main/java/com/warped/ui/chat/ChatScreen.kt,
    app/src/main/java/com/warped/ui/navigation/NavGraph.kt
  </files>
  <action>

### ChatScreen.kt changes:

1. **Remove the entire `topBar` parameter** from `Scaffold(…)`:
   - Delete lines 55-74 (the `topBar = { TopAppBar(…) }` block).
   - The `Scaffold` will still have `bottomBar = { ChatInputBar(…) }` as parameter.

2. **Remove the AssistChip block** (lines 116-131):
   - Delete the entire `if (uiState.selectedModelId == null) { AssistChip(…) }` block.
   - The model selection is now handled by the inline ModelSelector (see step 3).

3. **Add ModelSelector inline above the LazyColumn:**
   - Inside the `Column(modifier = Modifier.fillMaxSize().padding(padding))` block, insert the ModelSelector **before** the LazyColumn.
   - Wrap with a `Box` for padding:
   ```kotlin
   Box(
       modifier = Modifier
           .fillMaxWidth()
           .padding(horizontal = 16.dp, vertical = 10.dp)
   ) {
       ModelSelector(
           selectedModelId = uiState.selectedModelId,
           selectedProvider = uiState.selectedProvider,
           localModels = uiState.localModels,
           onModelSelected = { modelId, provider ->
               viewModel.setSelectedModel(modelId, provider)
           }
       )
   }
   ```
   - Import `androidx.compose.foundation.layout.Box` if not already imported (it should be via `import androidx.compose.foundation.layout.*`).

4. **After the change, the Column body** (skipping unchanged error Snackbar) should look like:
   ```kotlin
   Column(modifier = Modifier.fillMaxSize().padding(padding)) {
       Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
           ModelSelector(...)
       }
       LazyColumn(...) { ... }
       // Error snackbar unchanged
   }
   ```

### NavGraph.kt changes:

1. **Remove text labels from NavigationBarItem:**
   - Change `label = { Text(screen.label) }` to `label = null` on line 38.
   - The `icon` parameter remains unchanged: `icon = { Icon(screen.icon, contentDescription = screen.label) }`

2. **Optionally remove the now-unused `Text` import:**
   - `Text` was only used in the `label` lambda. Remove `import androidx.compose.material3.Text` from line 8.
   - (The wildcard import `import androidx.compose.material3.*` on line 5 already covers `Text`, so the explicit import on line 8 is redundant. Remove line 8.)

3. **The final NavigationBarItem should read:**
   ```kotlin
   NavigationBarItem(
       icon = { Icon(screen.icon, contentDescription = screen.label) },
       label = null,
       selected = currentRoute == screen.route,
       onClick = { ... }
   )
   ```
  </action>
  <verify>
    <automated>
# Verify TopAppBar removed
grep -c "TopAppBar" app/src/main/java/com/warped/ui/chat/ChatScreen.kt | xargs -I{} sh -c 'test {} -eq 0 || echo "TopAppBar still present"'

# Verify AssistChip removed
grep -c "AssistChip" app/src/main/java/com/warped/ui/chat/ChatScreen.kt | xargs -I{} sh -c 'test {} -eq 0 || echo "AssistChip still present"'

# Verify ModelSelector is called inside Column (not inside TopAppBar)
grep -v '^#' app/src/main/java/com/warped/ui/chat/ChatScreen.kt | grep -c "ModelSelector" | xargs -I{} sh -c 'test {} -gt 0 || echo "ModelSelector missing from ChatScreen"'

# Verify label = null in NavGraph
grep -c 'label = null' app/src/main/java/com/warped/ui/navigation/NavGraph.kt | xargs -I{} sh -c 'test {} -gt 0 || echo "label = null not found in NavGraph"'

# Verify explicit Text import removed from NavGraph
grep -v '^#' app/src/main/java/com/warped/ui/navigation/NavGraph.kt | grep -c '^import androidx.compose.material3.Text$' | xargs -I{} sh -c 'test {} -eq 0 || echo "Explicit Text import should be removed (covered by wildcard)"'
    </automated>
  </verify>
  <done>
ChatScreen has no TopAppBar, no AssistChip. ModelSelector appears inline above LazyColumn as an ExposedDropdownMenuBox. NavGraph NavigationBarItem has `label = null` (icons only). Redundant `Text` import removed from NavGraph.
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| None new | UI-only changes, no new data flows or trust boundaries created |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-quick-01 | Spoofing | N/A | accept | Pure UI refactor — no new input surfaces, no authentication changes, no data handling. Existing security posture unchanged. |
</threat_model>

<verification>
**Build verification:** `./gradlew assembleDebug` compiles without errors — the Pill Input, Material send/stop icons, inline ModelSelector, and icon-only navbar all resolve correctly.

**Manual spot checks (optional):**
1. Open chat screen — verify no TopAppBar is present
2. Tap the inline model selector — verify dropdown opens and model selection works
3. Type a message — verify the pill-shaped input accepts text and the send icon appears
4. Send a message — verify the stop icon appears during generation
5. Check bottom navbar — verify only icons show (no text labels)
</verification>

<success_criteria>
- [ ] ChatInputBar uses `OutlinedTextField` with `shape = MaterialTheme.shapes.extraLarge` and transparent indicators
- [ ] Send button displays `Icons.AutoMirrored.Filled.Send`; stop button displays `Icons.Filled.Stop`
- [ ] ChatScreen `Scaffold` has no `topBar` parameter
- [ ] `AssistChip` block removed from ChatScreen
- [ ] `ModelSelector` composable called directly in the Column body above `LazyColumn`, wrapped in `Box` with padding
- [ ] NavGraph `NavigationBarItem` uses `label = null`
- [ ] `./gradlew assembleDebug` succeeds
</success_criteria>

<output>
After completion, create `.planning/quick/260430-ryn-rediseno-chat-ocultar-topbar-selector-mo/260430-ryn-SUMMARY.md`
</output>
