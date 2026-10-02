# Phase 64: Drawer + Settings + Help + Funnel Polish - Pattern Map

**Mapped:** 2026-10-02
**Files analyzed:** 13 (11 modified + 2 resource files)
**Analogs found:** 11 / 13 (2 need RESEARCH-free new-copy only: Help EN wording, new CTA strings — both follow existing string-pair convention)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `ui/chat/components/ModelSelector.kt` | component (bottom sheet) | request-response (selection callbacks) | itself (`ModelSelectorSheet` empty-state Box lines 164-177 + `ModelRow` selected-card lines 303-310) | exact (self) |
| `ui/chat/ChatScreen.kt` | screen | request-response | existing `ModelSelectorSheet(...)` call site lines 551-565 + `onNavigateToSelector` prop line 77 | exact (self) |
| `ui/navigation/NavGraph.kt` | navigation (drawer + graph) | request-response | itself: per-row delete dialog lines 173-201, footer items lines 250-298, chat destinations lines 312-350 | exact (self) |
| `ui/settings/SettingsScreen.kt` | screen | CRUD (read-display + delete triggers) | Phase 63 Tavily-card removal diff `deed2513` (102 lines deleted, same file) | exact (precedent diff) |
| `ui/settings/SettingsViewModel.kt` | viewmodel | CRUD | Phase 63 same-commit removal (149 lines deleted: Tavily state+fns, same file) | exact (precedent diff) |
| `ui/settings/SettingsUiState.kt` | model (UI state) | CRUD (state) | Phase 63 same-commit removal (8 lines: 5 Tavily fields, same file) | exact (precedent diff) |
| `ui/help/HelpScreen.kt` | screen (static) | request-response (static render) | itself: `HelpSection` lines 190-236 (KEEP verbatim); Phase 63 `help_s7_step5` rewrite precedent | exact (self) |
| `ui/huggingface/HuggingFaceScreen.kt` | screen | CRUD (download-state machine) | `ui/models/ModelsScreen.kt` `ModelCard` button row lines 399-415 (Use-in-Chat + delete pattern to copy) | role-match |
| `ui/huggingface/CatalogViewModel.kt` | viewmodel | CRUD (activation) | `ui/models/ModelsViewModel.kt` `useLocalModel` lines 129-138 + `openBoundChat` lines 321-339 + `pendingChatId` lines 69-74 | role-match |
| `ui/models/ModelsScreen.kt` | screen | CRUD (list + empty state) | itself: empty-state branch lines 247-262 + add-wizard dialog lines 149-193 (CTA targets live here) | exact (self) |
| `ui/selector/UnifiedSelectorScreen.kt` | screen | CRUD (list, live route) | `ui/models/ModelsScreen.kt` empty-state gate line 247 + `UnifiedSelectorViewModel.showEndpointForm()` line 258 | role-match |
| `res/values/strings.xml` + `res/values-es/strings.xml` | config (resources) | static | Phase 63 same-commit string deletion (24 lines EN+ES each, same files) | exact (precedent diff) |
| `ui/endpoints/EndpointsScreen.kt` | — LEGACY, DO NOT TOUCH | — | n/a (zero NavGraph references; `Screen.Endpoints` has no composable destination) | no-analog (out of scope) |

## Resolved Questions (on-disk evidence)

### Sheet vs nav-drawer: the "chat model drawer" is `ModelSelectorSheet`, NOT the nav drawer
- `ui/chat/components/ModelSelector.kt` lines 164-177: the bare centered `selector_no_models` empty state lives in `ModelSelectorSheet` (a `ModalBottomSheet`, line 91). This is the DRAWER-01 target.
- `ui/navigation/NavGraph.kt` lines 119-301: the nav drawer (`ModalNavigationDrawer` + full-size `Surface`, lines 119-136) renders **conversations** (`LazyColumn` of `conv.title`, lines 173-248) — no models. This is the DRAWER-03/04 target (footer + delete-all row).
- `ui/chat/components/ConversationList.kt` is **legacy/unused**: it renders its own `ModalDrawerSheet` with `new_chat_plus` button (lines 24-32) and is NOT referenced by `NavGraph.kt` (drawer content is inline). Do not copy from it; do not modify it.

### EndpointsScreen vs UnifiedSelectorScreen: live route is Selector → UnifiedSelectorScreen
- `NavGraph.kt` lines 352-367: `composable<Screen.Selector>` hosts `UnifiedSelectorScreen` with `onNavigateToChat` → `Screen.NewChat`, `onOpenHuggingFace` → `Screen.HuggingFace`. This is the live "Models & Endpoints" surface.
- `NavGraph.kt` lines 368-378: `composable<Screen.Models>` hosting `ModelsScreen` still exists (reached via deep link/legacy entry) — apply FUN-02/03 here as the primary empty-state edit.
- `Screen.Endpoints` (Screen.kt line 32) has **no `composable<Screen.Endpoints>` destination** anywhere in NavGraph (grep for `EndpointsScreen|Screen.Endpoints` in NavGraph.kt returns zero matches), and `EndpointsScreen.kt` (standalone, `no_endpoints` empty text line 57, `showAddForm` FAB line 32) is unmounted legacy. **Do not touch `EndpointsScreen.kt`.** Mirror the two empty-state CTAs in `UnifiedSelectorScreen` (which has only per-section `selector_no_local` line 137-141, no combined empty gate — new combined gate needed, modeled on ModelsScreen line 247).

## Pattern Assignments

### `ui/chat/components/ModelSelector.kt` (component, request-response)

**Analog:** self — empty-state Box (lines 164-177), selected-card style (lines 303-310), BackHandler + dismiss (lines 78-89)

**Empty-state pattern to extend** (lines 164-177):
```kotlin
if (localModels.isEmpty() && endpoints.isEmpty()) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            stringResource(R.string.selector_no_models),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```
Contract: keep the `Text`, wrap in a centered `Column` with 16dp gap, add filled accent `Button` below (`containerColor = Color(0xFFD97757)`, white text, `RoundedCornerShape(8.dp)` — copy button style from `ModelsScreen.kt` lines 400-405). Tap = `onDismiss()` then new `onNavigateToCatalog()` callback.

**Selected-card accent pattern to reuse for CTA color** (lines 303-310):
```kotlin
Surface(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    color = if (isSelected) Color(0xFF2B2B29) else Color.Transparent,
    border = if (isSelected) BorderStroke(1.dp, Color(0xFFD97757)) else null
)
```

**BackHandler + dismiss pattern, already correct** (lines 78-89):
```kotlin
val backScope = rememberCoroutineScope()
BackHandler {
    backScope.launch {
        try { sheetState.hide() } finally { onDismiss() }
    }
}
```

**Web Options block to DELETE** (lines 179-185):
```kotlin
HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
SectionHeader(stringResource(R.string.cd_web_options))
WebOverrideSheetRow(
    webOverride = webOverride,
    globalWebEnabled = globalWebEnabled,
    onWebOverrideSelected = onWebOverrideSelected
)
```
Also delete `WebOverrideSheetRow` + `WebOverrideOption` (lines 208-289) and params `webOverride`, `globalWebEnabled`, `onWebOverrideSelected` (lines 67-69) + imports `clickable`, `Circle`, `Check`, `WebOverrideIndicator`/`webOverrideIndicator` IF no other caller needs them — `ChatScreen.kt` lines 551-565 is the sole caller (verified), so simplification is safe. Keep `SectionHeader` (line 191) — still used by local/network headers.

---

### `ui/chat/ChatScreen.kt` (screen, request-response)

**Analog:** self — `ModelSelectorSheet` call site (lines 551-565), `onNavigateToSelector` prop (line 77)

**Call-site pattern** (lines 551-565):
```kotlin
ModelSelectorSheet(
    visible = showModelPicker,
    selectedModelId = connection.selectedLocalModelId ?: connection.selectedRemoteModelId,
    selectedProvider = if (connection.selectedLocalModelId != null) ProviderType.LITE_RT_LM else connection.selectedRemoteProvider,
    localModels = connection.localModels,
    endpoints = connection.endpoints,
    endpointModels = connection.endpointModels,
    onDismiss = { showModelPicker = false },
    onModelSelected = { modelId, providerType, endpointId ->
        viewModel.launchModelSelection(modelId, providerType, endpointId)
    },
    webOverride = connection.webOverride,
    globalWebEnabled = connection.webGroundingEnabled,
    onWebOverrideSelected = { viewModel.setWebOverride(it) }
)
```
Contract: add `onNavigateToCatalog: () -> Unit = {}` param next to `onNavigateToSelector` (line 77 pattern: `onNavigateToSelector: () -> Unit = {}`), thread into the sheet call as `onNavigateToCatalog = { showModelPicker = false; onNavigateToCatalog() }`, and drop the three web params. All three chat destinations in NavGraph (lines 312-350) wire `onNavigateToSelector` → `Screen.Selector` today; add the parallel `onNavigateToCatalog = { navController.navigate(Screen.HuggingFace) }` at each.

---

### `ui/navigation/NavGraph.kt` (navigation, request-response)

**Analog:** self — per-row delete dialog (lines 173-201), footer row (lines 250-298), New-Chat label style (line 158)

**Per-row delete dialog = template for delete-all dialog** (lines 173-201):
```kotlin
var showDeleteConfirm by remember { mutableStateOf(false) }
if (showDeleteConfirm) {
    WarpedAlertDialog(
        onDismissRequest = { showDeleteConfirm = false },
        title = { Text(stringResource(R.string.delete_chat_title)) },
        text = { Text(stringResource(R.string.delete_chat_message, conv.title)) },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    val wasActive = conv.id == activeConversationId
                    chatRepository.deleteConversation(conv.id)
                    showDeleteConfirm = false
                    if (wasActive) {
                        activeConversationId = null
                        activeModelSelection.clearLastConversation()
                        engineManager.scheduleUnload()
                        navController.navigate(Screen.NewChat(newChat = true)) {
                            popUpTo(Screen.Chat) { inclusive = true }
                        }
                    }
                }
            }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) } }
    )
}
```
Delete-all row reuses this shape with `settings_delete_chats_title` + plural `settings_delete_chats_msg`, confirm calls `chatRepository.deleteAllConversations()` (same API `SettingsViewModel.deleteAllChats` uses, SettingsViewModel.kt line 117), then unconditionally clears `activeConversationId` + `clearLastConversation()` + `scheduleUnload()` + navigates `Screen.NewChat`. Dialog visibility via local `remember { mutableStateOf(false) }` in drawer content. Gate row on `conversations.isNotEmpty()`.

**Footer items to restyle** (lines 253-298): three `NavigationDrawerItem`s with `Modifier.weight(1f)` in a `SpaceEvenly` Row. Bump labels from `fontSize = 12.sp` to `16.sp` + `fontWeight = Semibold` (match New-Chat label, line 158):
```kotlin
label = { Text(stringResource(R.string.new_chat), color = DrawerAccent, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) },
```
Tint/selection logic unchanged (`DrawerAccent` when selected else `DrawerTextSecondary`, 20dp icons, `DrawerSelectedBg` container). Order locked: New Chat (line 156) → Recents (line 170) → **new Delete-all row** → divider (line 250 pattern: `HorizontalDivider(color = Color(0xFF333333), thickness = 0.5.dp)`) → footer [Models · Help · Settings]. No Web Options item exists in the drawer — nothing to remove there (DRAWER-02 target is the sheet, not the drawer).

**Drawer color tokens** (lines 53-57): `DrawerBg = 0xFF1F1F1E`, `DrawerAccent = 0xFFD97757`, `DrawerTextPrimary = 0xFFECECEC`, `DrawerTextSecondary = 0xFF9CA3AF`, `DrawerSelectedBg = 0xFF121212`.

---

### `ui/settings/SettingsScreen.kt` (screen, CRUD) — follow Phase 63 diff `deed2513`

**Analog:** commit `deed2513` Tavily-card removal (same file, -102 lines). The exact replication recipe:
1. Remove the section header + card `item {}` blocks (same as deleted `settings_section_websearch` header + `TavilyKeyCard()` call site).
2. Remove the now-private composable (same as deleted `TavilyKeyCard`, ~86 lines).
3. Remove orphaned imports (precedent: `Search` icon, `KeyboardType`, `PasswordVisualTransformation`, `KeyboardOptions` imports deleted in the same diff).
4. Remove orphaned strings EN+ES (precedent: 24 lines each file in the same commit).

**Remove A — Data section** (lines 133-175): header `item` (lines 134-139, `Icons.Filled.Storage` + `settings_section_data`) + card `item` (lines 140-175, chats/endpoints/models/presets rows + `viewModel.showDeleteChatsDialog()` Delete button). Also delete the `showDeleteChatsDialog` `WarpedAlertDialog` block (lines 63-80):
```kotlin
if (uiState.showDeleteChatsDialog) {
    WarpedAlertDialog(
        onDismissRequest = { viewModel.dismissDeleteChatsDialog() },
        title = { Text(stringResource(R.string.settings_delete_chats_title)) },
        text = { Text(pluralStringResource(R.plurals.settings_delete_chats_msg, uiState.chatCount, uiState.chatCount)) },
        confirmButton = {
            TextButton(
                onClick = { viewModel.deleteAllChats() },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text(stringResource(R.string.settings_delete_all)) }
        },
        dismissButton = { TextButton(onClick = { viewModel.dismissDeleteChatsDialog() }) { Text(stringResource(R.string.cancel)) } }
    )
}
```
NOTE: this dialog copy (`settings_delete_chats_title` + plural msg, error confirm, Cancel dismiss) is the exact template the drawer delete-all row reuses — keep the strings, only delete this call site. After removal, `pluralStringResource` import (line 35) and `Icons.Filled.Storage` (line 28) become orphaned — delete per precedent.

**Remove B — Security key-deletion** (lines 348-373): header `item` (lines 349-354, `Icons.Filled.Lock` + `settings_section_security`) + card `item` (lines 355-373, `settings_security_desc` + `settings_delete_all_keys_btn` → `viewModel.showDeleteKeysDialog()`). Also delete the `showDeleteKeysDialog` block (lines 82-95, same `WarpedAlertDialog` shape with `settings_delete_keys_title`/`settings_delete_keys_msg`). After removal, `Icons.Filled.Lock` import (line 24) becomes orphaned — delete per precedent. `deleteEndpointKey`/`apiKeyStore.deleteKey` in the ViewModel stays (programmatic endpoint-deletion flow, no UI).

**Untouched sections, in order:** Web grounding switch card (lines 177-215), Display code-theme + font-scale cards (lines 217-317), App wizard card (lines 319-346). Card language: `CardDefaults.cardColors(containerColor = Color(0xFF2B2B29))`, `RoundedCornerShape(12.dp)`, `Modifier.padding(16.dp)`, `Arrangement.spacedBy(12.dp)`, `SettingsSectionHeader` (lines 408-427: 18dp `0xFFD97757` icon + `titleMedium Bold`).

---

### `ui/settings/SettingsViewModel.kt` (viewmodel, CRUD) — follow Phase 63 diff `deed2513`

**Analog:** same-commit deletion of 149 lines (Tavily state, input/test/save/clear fns) from this file; `ApiKeyStore.kt` Tavily-accessor deletion (-26 lines) as the keep-vs-delete boundary precedent.

Delete: `showDeleteChatsDialog()` / `dismissDeleteChatsDialog()` / `deleteAllChats()` (lines 105-130), `showDeleteKeysDialog()` / `dismissDeleteKeysDialog()` / `deleteAllApiKeys()` (lines 132-157). Keep: `deleteEndpointKey()` (line 159+, `apiKeyStore.deleteKey(endpointId)` programmatic path) — mirrors how the Tavily commit kept non-Tavily Keystore accessors. The bulk-delete repository call the drawer needs already exists here as the copy source:
```kotlin
chatRepository.deleteAllConversations()
```

---

### `ui/settings/SettingsUiState.kt` (model, CRUD state) — follow Phase 63 diff `deed2513`

**Analog:** same-commit deletion of 8 lines (5 Tavily fields) from this file.

```kotlin
data class SettingsUiState(
    val isDeletingChats: Boolean = false,
    val isDeletingKeys: Boolean = false,
    val chatCount: Int = 0,
    val endpointCount: Int = 0,
    val modelCount: Int = 0,
    val presetCount: Int = 0,
    val showDeleteChatsDialog: Boolean = false,
    val showDeleteKeysDialog: Boolean = false,
    ...
)
```
Delete `showDeleteChatsDialog`, `showDeleteKeysDialog`, `isDeletingKeys`; delete `isDeletingChats` + `chatCount` only if no other reader remains (check `SettingsViewModel` line 48 `chatCount` collector + any other usage before deleting — precedent deleted Tavily fields outright because the card was their sole reader). Keep `endpointCount`/`modelCount`/`presetCount` ONLY if referenced elsewhere after the Data card removal, else delete as orphaned.

---

### `ui/help/HelpScreen.kt` (screen, static) — KEEP `HelpSection`, rewrite strings

**Analog:** self, lines 190-236 (frozen composable) + Phase 63 `help_s7_step5` minimal-rewrite precedent (`values/strings.xml` line 478).

```kotlin
@Composable
private fun HelpSection(icon: ImageVector, title: String, steps: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B29)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = Color(0xFFD97757), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            steps.forEachIndexed { i, step ->
                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                    Text("${i + 1}. ", style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold, color = Color(0xFFD97757))
                    Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
```
Sections list (lines 64-183): 8 sections s1..s8, each `HelpSection(icon, title = help_sN_title, steps = help_sN_stepK)`. Scope: rewrite step strings short/minimal, hard requirement NO Tavily steps + NO API-key steps anywhere. Section count may shrink (fewer shorter sections preferred). Every touched `help_s*` key updated in BOTH `values/strings.xml` and `values-es/strings.xml` (EN+ES parity is the established pattern — precedent: commit `1205b861` bilingual sweep). Screen chrome: `Scaffold` + `TopAppBar` with back (lines 30-40), `LazyColumn` `spacedBy(16.dp)` + `horizontal 16.dp` (lines 42-48), headline `help_how_to`/`help_intro` header (lines 49-62).

---

### `ui/huggingface/HuggingFaceScreen.kt` (screen, download-state) — add "Use in Chat" to downloaded cards

**Analog:** `ui/models/ModelsScreen.kt` `ModelCard` button row (lines 399-405) — the exact button to replicate.

**Copy-this button** (ModelsScreen.kt lines 399-405):
```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Button(
        onClick = onLoad,
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97757)),
        shape = RoundedCornerShape(8.dp)
    ) { Text(stringResource(R.string.use_in_chat), color = Color.White) }
    ...
}
```
**Insertion point** — `CatalogModelCard` `trailingActions` (HuggingFaceScreen.kt lines 317-335):
```kotlin
trailingActions = {
    if (downloaded) {
        IconButton(onClick = { showDeleteConfirm = true }) {
            Icon(imageVector = Icons.Filled.Delete, contentDescription = stringResource(R.string.delete),
                tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
        }
    } else { CatalogDownloadActions(...) }
},
```
Contract: in the `downloaded` branch, render a `Row` (8dp gap, same as ModelsScreen): compact filled accent `Button` (`use_in_chat` string — EN+ES already exist at `values/strings.xml` line 22, no new key needed) with `weight(1f)` + keep the existing 22dp error-tint delete `IconButton`. Card shell is the shared `com.warped.ui.components.ModelCard` (ModelCard.kt line 68: `trailingActions: @Composable RowScope.() -> Unit` slot, plus `downloadContent`/`errorText`/`detailsContent` slots) — no card-structure change. Delete-confirm dialog (lines 268-288, `WarpedAlertDialog` + `delete_model_title`/`delete_model_message` + error confirm) stays untouched. `downloaded` state rule: `isEffectivelyDownloaded()` (lines 234-241, JVM-testable pure fn) — reuse as the branch condition, unchanged.

---

### `ui/huggingface/CatalogViewModel.kt` (viewmodel, activation) — add `useDownloadedModel` mirroring `ModelsViewModel`

**Analog:** `ui/models/ModelsViewModel.kt` — `pendingChatId` plumbing (lines 69-74), `useLocalModel` (lines 129-138), `openBoundChat` (lines 321-339).

```kotlin
// ModelsViewModel lines 69-74: one-shot navigation state
private val _pendingChatId = MutableStateFlow<Long?>(null)
val pendingChatId: StateFlow<Long?> = _pendingChatId.asStateFlow()
fun consumePendingChat() { _pendingChatId.value = null }

// ModelsViewModel lines 129-138: activation entry
fun useLocalModel(model: LocalModel) {
    activeModelSelection.connectLocal(model.filePath, ProviderType.LITE_RT_LM)
    viewModelScope.launch(coroutineExceptionHandler) {
        openBoundChat(providerType = ProviderType.LITE_RT_LM, modelId = model.filePath, endpointId = 0L)
    }
}

// ModelsViewModel lines 321-339: bound-chat creation (append-only, titled "New Chat")
val id = chatRepository.createConversation(
    title = context.getString(R.string.new_chat),
    providerType = providerType, modelId = modelId, endpointId = endpointId,
)
activeModelSelection.saveLastConversation(id)
_pendingChatId.value = id
```
`CatalogViewModel` already injects `activeModelSelection` (line 42), `localModelRepository`, `engineManager`, `modelImportManager` — the delete path (lines 98-121, incl. `disconnectLocal()` when deleting the active model) proves the wiring pattern. New `useDownloadedModel(entry)` resolves the `LocalModel` via `localModelRepository` by `entry.modelFile`, then mirrors `useLocalModel` + `openBoundChat` + `pendingChatId`/`consumePendingChat`. `HuggingFaceScreen` collects `pendingChatId` via `LaunchedEffect` exactly like `ModelsScreen` lines 59-64:
```kotlin
val pendingChatId by viewModel.pendingChatId.collectAsStateWithLifecycle()
LaunchedEffect(pendingChatId) {
    pendingChatId?.let { id -> onUseInChat(id); viewModel.consumePendingChat() }
}
```
and NavGraph `Screen.HuggingFace` destination (lines 379-385, currently only `onNavigateToModels`) gains `onUseInChat = { id -> navigate(Screen.ChatDetail(id)) }` copying the `Screen.Models` destination (lines 368-378). Navigation target decision: `Screen.ChatDetail(id)` (the ModelsScreen-proven chain), not `Screen.NewChat`.

---

### `ui/models/ModelsScreen.kt` (screen, empty state) — two CTAs replacing single wizard button

**Analog:** self — empty-state branch (lines 247-262) + add-wizard dialog rows (lines 149-193, CTA targets already live here).

```kotlin
} else if (uiState.models.isEmpty() && uiState.endpoints.isEmpty() && !uiState.isImporting && uiState.activeDownloads.isEmpty()) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.no_models_endpoints_yet), style = MaterialTheme.typography.bodyLarge, color = Color(0xFF9CA3AF))
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.models_empty_hint), style = MaterialTheme.typography.bodySmall, color = Color(0xFF9CA3AF))
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { showAddWizard = true }, shape = RoundedCornerShape(8.dp)) {
                Text(stringResource(R.string.add_model))
            }
        }
    }
}
```
Contract: keep heading + hint + gate (unchanged); replace the single `OutlinedButton` with a centered column, 8dp spacing: (1) filled accent `Button` ("Download a local model", `#D97757`, white text, 8dp — copy lines 400-405) → `onOpenHuggingFace()` (already a screen param, line 46; NavGraph wires it to `Screen.HuggingFace`, line 375); (2) `OutlinedButton` 8dp ("Add a new Endpoint") → `viewModel.showEndpointForm()` (ModelsViewModel line 143, resets form state + sets `isEndpointFormVisible = true`; the endpoint-form branch at lines ~200-246 already handles display). FAB + add-wizard stay for non-empty states. New strings: `models_empty_download_cta` + `models_empty_add_endpoint_cta` EN+ES (existing `hf_download_model` "Download model" ≠ "Download a local model", so new keys required).

---

### `ui/selector/UnifiedSelectorScreen.kt` (screen, live route) — mirror empty-state CTAs

**Analog:** `ModelsScreen.kt` line 247 gate + `UnifiedSelectorViewModel.showEndpointForm()` (line 258) + existing `onOpenHuggingFace` param (line 40).

Current state: NO combined empty gate — only per-section `selector_no_local` text (lines 137-141) and an add-`IconButton` in the section header (lines 131-133, `cd_add_model`). Remote header has `showEndpointForm()` `IconButton` (lines 164-166). Endpoint cards already have the activate-and-navigate pattern (lines 170-181):
```kotlin
items(uiState.endpoints, key = { "endpoint-${it.id}" }) { endpoint ->
    EndpointSelectorCard(
        endpoint = endpoint,
        isSelected = uiState.selectedRemoteEndpointId == endpoint.id,
        onUseInChat = { viewModel.selectRemote(endpoint); onNavigateToChat() },
        ...
```
(`selectRemote` = `activeModelSelection.selectRemote(modelId, apiType, id)`, UnifiedSelectorViewModel line 120-122.) Contract: add a combined `localModels.isEmpty() && endpoints.isEmpty() && activeDownloads.isEmpty()` branch rendering the same two CTAs as ModelsScreen (filled → `onOpenHuggingFace`, outlined → `viewModel.showEndpointForm()`). Keep per-section headers/rows for non-empty states.

---

### `res/values/strings.xml` + `res/values-es/strings.xml` (resources)

**Analog:** Phase 63 same-commit deletions (24 lines per file) + `help_s7_step5` rewrite (line 478 EN).

Existing keys (EN line nos.): `use_in_chat` (22, reuse for catalog — no new key), `hf_download_model` (282, "Download model" — reuse only if exact wording fits drawer CTA, else new key), `no_models_endpoints_yet` (46), `models_empty_hint` (292), `selector_no_models` (294), `settings_delete_chats_title` (318) + plural `settings_delete_chats_msg` (319), `settings_delete_all` (323), `settings_delete_keys_title` (324) + `settings_delete_keys_msg` (325), `settings_section_data` (302), `settings_section_security` (315), `settings_delete_all_keys_btn` (317), `add_model` (28), `help_s7_step5` (478). New keys (names at implementer discretion, EN+ES mandatory): drawer/sheet `drawer_empty_download_cta`, models-empty `models_empty_download_cta` + `models_empty_add_endpoint_cta`. Delete-if-orphaned after code removal: `settings_section_data`, `settings_section_security`, `settings_delete_all_keys_btn`, `settings_delete_keys_title`, `settings_delete_keys_msg` — but ONLY if grep confirms zero remaining references (drawer reuses the chats strings, so those stay). Precedent: the Tavily commit deleted `settings_tavily_*` + `settings_section_websearch` EN+ES together with the code.

## Shared Patterns

### WarpedAlertDialog (confirm dialogs)
**Source:** `ui/components/WarpedAlertDialog.kt` lines 39-50 (slots) + 94-104 (right-aligned buttons)
**Apply to:** drawer delete-all row (NavGraph), catalog delete (unchanged), all existing call sites
```kotlin
WarpedAlertDialog(
    onDismissRequest = { showDialog = false },
    title = { Text(stringResource(R.string...._title)) },
    text = { Text(...) },  // plural via pluralStringResource for count-based copy
    confirmButton = {
        TextButton(onClick = { /* action */ },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error))
        { Text(stringResource(R.string.delete_or_delete_all)) }
    },
    dismissButton = { TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.cancel)) } }
)
```
Dialog shell: 12dp card, `#2B2B29`, title `titleMedium Semibold #ECECEC`, body `bodyMedium #9CA3AF`, `usePlatformDefaultWidth = false`, max 400dp. Confirm is error-colored for destructive actions, dismiss is always Cancel.

### Accent filled CTA button
**Source:** `ui/models/ModelsScreen.kt` lines 400-405
**Apply to:** sheet empty-state CTA, catalog "Use in Chat", models-empty "Download a local model"
```kotlin
Button(
    onClick = { /* dismiss-then-navigate or activate-then-navigate */ },
    modifier = Modifier.weight(1f),  // inside Row; omit in centered Column
    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97757)),
    shape = RoundedCornerShape(8.dp)
) { Text(stringResource(R.string.use_in_chat), color = Color.White) }
```

### Activation → chat navigation chain
**Source:** `ModelsViewModel.useLocalModel` (129-138) → `openBoundChat` (321-339) → `pendingChatId` (69-74) → `ModelsScreen` `LaunchedEffect` (59-64) → NavGraph `Screen.Models` destination (370-374)
**Apply to:** `CatalogViewModel` + `HuggingFaceScreen` + NavGraph `Screen.HuggingFace` destination (FUN-01). `UnifiedSelectorScreen` endpoint cards use the lighter synchronous variant (`selectRemote` + `onNavigateToChat()` → `Screen.NewChat`, lines 174-177) — acceptable for endpoints, but catalog local-model activation MUST use the bound-chat chain so the new conversation carries the model binding.

### EN+ES string parity
**Source:** precedent commits `deed2513` (paired deletion) + `1205b861` (parity sweep); ES file `res/values-es/strings.xml`
**Apply to:** every new/rewritten user-facing string (drawer CTA, models-empty CTAs, all rewritten `help_s*` steps). Reuse `use_in_chat` (already paired) where wording matches exactly.

### Settings state via ViewModel + UiState
**Source:** `SettingsViewModel` (`_uiState.update { it.copy(...) }`, `viewModelScope.launch(coroutineExceptionHandler)`) + `SettingsUiState` data class; `collectAsStateWithLifecycle()` in composables
**Apply to:** any remaining Settings edits. Removal-only phase: delete state fields + fns + call sites together (the Tavily commit touched Screen + UiState + ViewModel + Keystore + strings atomically).

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| (none — all covered) | — | — | Help EN copy wording and new CTA key names are at implementer discretion per CONTEXT; the structural patterns (HelpSection, EN+ES pairing, button styles) all have exact analogs above. |

## Metadata

**Analog search scope:** `app/src/main/java/com/warped/ui/{chat,navigation,settings,help,huggingface,models,selector,endpoints,components}/`, `app/src/main/res/values*/strings.xml`, git history (`deed2513`, `c7b0eb79`, `1205b861`)
**Files scanned:** ~25 (13 read fully or in targeted ranges, remainder via grep)
**Pattern extraction date:** 2026-10-02
**Key diffs used:** `deed2513` (Phase 63 Tavily removal — the SET-01/SET-02 replication template: Screen -102 / ViewModel -149 / UiState -8 / strings 24+24)
