# Phase 32: Unified Models & Endpoints Selector — Plan

**Created:** 2026-05-25
**Phase:** 32 — Unified Models & Endpoints Selector

---

## Plan 1: Refactor ActiveModelSelection → Dual Selection Domain Model

**Goal:** Split `ActiveModelSelection` to independently track local and remote selections.

**Steps:**
1. Add `LocalSelection(localModelId: String? = null, isConnected: Boolean = false)` data class and `RemoteSelection(modelId: String?, providerType: ProviderType?, endpointId: Long?)` data class
2. Add `localSelection: StateFlow<LocalSelection>` and `remoteSelection: StateFlow<RemoteSelection>`
3. Add `connectLocal(modelId: String, providerType: ProviderType)` — persists + updates local state
4. Add `disconnectLocal()` — clears local selection, persists nil
5. Add `selectRemote(modelId: String, providerType: ProviderType, endpointId: Long)` — persists remote selection
6. Add `clearRemote()` — clears remote selection
7. Update Keystore persistence: persist both local and remote selections independently
8. Keep backward compat: `activeModel: StateFlow<ActiveModel?>` deprecated, computed from local/remote (for gradual migration)

**Files:**
- `app/src/main/java/com/warped/domain/model/ActiveModelSelection.kt` — refactor
- (`ChatViewModel.kt` — adapt in Plan 3)

---

## Plan 2: Build UnifiedSelectorScreen Composable

**Goal:** Create the new full-screen selector with local model toggles and remote endpoint listing.

**Steps:**
1. Create `app/src/main/java/com/warped/ui/selector/UnifiedSelectorScreen.kt` — full-screen Scaffold with TopAppBar "Models & Endpoints"
2. **Local Models section:**
   - "Local Models" section header with model count
   - Per model: ModelCard with name, size, quantization, capabilities badges
   - **Switch** toggle (Material3 Switch) on the right side of each card
   - Connected state: green dot indicator, model size shown
   - Disconnected state: gray dot, toggle off
   - Only one switch can be ON at a time (auto-unload previous on connect)
   - Delete button via IconButton (trash icon) with WarpedAlertDialog confirmation
3. **Network Endpoints section:**
   - "Network Endpoints" section header with endpoint count
   - Per endpoint: Endpoint card with name, URL, apiType badge
   - Expandable section: "Fetch models" button → loads model list inline
   - Per remote model: model name row + "Use in chat" button
   - Endpoint edit/delete via IconButtons
4. **FAB** for Add (download from HF / import file / add endpoint) — reuse existing wizard from ModelsScreen
5. **Empty states:** "No local models downloaded" + "Download from Hugging Face" button; "No endpoints configured" + "Add Endpoint" button
6. Create `app/src/main/java/com/warped/ui/selector/UnifiedSelectorViewModel.kt`:
   - Observes `localModelRepository.observeModels()`, `endpointRepository.observeEndpoints()`, `activeModelSelection.localSelection`, `activeModelSelection.remoteSelection`
   - `connectLocal(model)` → `activeModelSelection.connectLocal(model.filePath, providerType)` → `engineManager.switchToLiteRT(model.filePath)` (on Dispatchers.Default)
   - `disconnectLocal()` → `engineManager.unloadCurrent()` → `activeModelSelection.disconnectLocal()`
   - `selectRemote(endpoint, modelId)` → `activeModelSelection.selectRemote(modelId, endpoint.apiType, endpoint.id)`
   - `fetchEndpointModels(endpoint)` → `providerRouter.resolve(endpoint, "list").listModels()`
   - Endpoint CRUD: delegate to existing `endpointRepository` methods
   - Model delete: delegate to `modelImportManager.deleteModel()`
7. Expose `onNavigateToChat` callback — fires when user taps "Use in chat"

**Files created:**
- `app/src/main/java/com/warped/ui/selector/UnifiedSelectorScreen.kt`
- `app/src/main/java/com/warped/ui/selector/UnifiedSelectorViewModel.kt`
- `app/src/main/java/com/warped/ui/selector/UnifiedSelectorUiState.kt`

---

## Plan 3: Wire ChatViewModel to Dual Selection & Connect/Disconnect Lifecycle

**Goal:** Adapt ChatViewModel to consume dual selection model and handle local model connect/disconnect properly.

**Steps:**
1. In `ChatViewModel.init()`:
   - Remove single `activeModel` collection
   - Collect `activeModelSelection.localSelection` → update `_uiState.localModelConnected`, `_uiState.selectedLocalModelId`
   - Collect `activeModelSelection.remoteSelection` → update `_uiState.selectedRemoteModelId`, `_uiState.selectedRemoteProvider`
2. In `ChatUiState`: add `localModelConnected: Boolean`, `selectedLocalModelId: String?`, `selectedRemoteModelId: String?`, `selectedRemoteProvider: ProviderType?`
3. In `resolvedSelectedProvider()`: determine whether current conversation uses local or remote based on conversation metadata + selection state
4. In `sendMessage()`:
   - If remote selected: resolve provider via `providerRouter.resolve(endpoint, remoteModelId)` — local model stays loaded (unaffected)
   - If local selected: check `localModelConnected`, if not → auto-load engine on-demand (fallback), resolve via `providerRouter.resolveLocal(LITE_RT_LM, modelPath)`
   - Remove old `LOCAL` provider references (already deprecated)
5. Keep backward compat: existing single `selectedModelId`/`selectedProvider` still works for bare calls without dual selection
6. Add `disconnectCurrentLocalModel()` method — called when ModelsScreen toggle is turned off mid-chat

**Files:**
- `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` — adapt
- `app/src/main/java/com/warped/ui/chat/ChatUiState.kt` — add fields

---

## Plan 4: Add Navigation Route & Replace Old Selector

**Goal:** Wire UnifiedSelectorScreen into NavGraph. Replace old ModelSelector dropdown in ChatScreen.

**Steps:**
1. Add `Selector` route to `Screen.kt` sealed class: `data object Selector : Screen("selector")`
2. Add `composable("selector") → UnifiedSelectorScreen(onNavigateToChat = { navController.navigateToChat() })` in NavGraph
3. In ChatScreen TopAppBar: replace the inline ModelSelector dropdown with an IconButton that navigates to `"selector"` route
4. Keep the current selected model name shown as subtitle in TopAppBar (informational, not interactive)
5. Keep ModelsScreen route for backward compat (existing deep links, drawer item) but redirect to Selector screen
6. In nav drawer: update "Models" item to navigate to `"selector"` instead of `"models"`

**Files:**
- `app/src/main/java/com/warped/ui/navigation/NavGraph.kt` — add route
- `app/src/main/java/com/warped/ui/navigation/Screen.kt` — add Selector
- `app/src/main/java/com/warped/ui/chat/ChatScreen.kt` — replace ModelSelector with nav button
- `app/src/main/java/com/warped/ui/chat/components/ModelSelector.kt` — mark @Deprecated (keep for reference)

---

## Verification

| Plan | Verification | 
|------|-------------|
| Plan 1 | `ActiveModelSelection` exposes `localSelection` + `remoteSelection` StateFlows; old `activeModel` still works |
| Plan 2 | `UnifiedSelectorScreen` compiles and renders local model cards with Switch toggles + remote endpoint listing |
| Plan 3 | `ChatViewModel` collects dual selection; sendMessage works with both local and remote selections |
| Plan 4 | ChatScreen TopAppBar shows selector nav button; clicking navigates to unified selector; remote "Use in chat" navigates back to chat |

---

*Plan created: 2026-05-25*
