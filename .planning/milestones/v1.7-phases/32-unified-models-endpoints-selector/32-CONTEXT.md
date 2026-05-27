# Phase 32: Unified Models & Endpoints Selector - Context

**Gathered:** 2026-05-25
**Status:** Ready for planning
**Mode:** Auto-accepted (autonomous smart discuss)

<domain>
## Phase Boundary

Build a single unified selector screen where the user sees 1 local model (with connect/disconnect toggle) alongside infinite remote endpoint models. This replaces the current fragmented model selection: ModelSelector dropdown (in ChatScreen) and ModelsScreen (standalone). The new screen is accessed from ChatScreen's TopAppBar and from the nav drawer.

</domain>

<decisions>
## Implementation Decisions

### Grey Area 1/4: Domain Model — Dual Selection Architecture

- **Q1: How to model simultaneous local + remote selection?** Split `ActiveModelSelection` into two separate state flows: `localSelection: StateFlow<ActiveModel?>` and `remoteSelection: StateFlow<ActiveModel?>`. The existing single `activeModel` flow is replaced by these two — consumers (ChatViewModel) combine them to determine which provider to use per message based on conversation type. *Rationale: Clean separation — local and remote are independent concerns. ChatViewModel already branches on `selectedProvider` (local vs remote).*

- **Q2: Should ActiveModelSelection grow a `connectLocal(modelId)` / `disconnectLocal()` API?** Yes. Add `connectLocal(modelId)` which persists the selection AND triggers engine loading via EngineManager. Add `disconnectLocal()` which unloads the engine and clears the selection. This is the toggle action. *Rationale: The connect/disconnect toggle is the primary UX — the domain layer should own this lifecycle, not ChatViewModel.*

- **Q3: Remote endpoint selection — does it need a "connect"?** No. Remote selection is simple: pick an endpoint+model and it's the active remote. No persistent connection — the HTTP call happens on `sendMessage()`. No toggle needed. *Rationale: Remote providers are stateless (REST/SSE). The toggle is for local models that consume RAM.*

- **Q4: Should local and remote selections persist independently across app restarts?** Yes. Both should persist via KeystoreManager (like current ActiveModel does). On app restart: restore local selection (but don't auto-load engine — show disconnected gray badge) and restore remote selection (show as active). *Rationale: Survives process death without auto-loading multi-GB models on cold start.*

### Grey Area 2/4: Local Model Connect/Disconnect — Engine Lifecycle

- **Q1: When user toggles "Connect" on a local model, should engine initialization happen immediately?** Yes. Toggle ON → `EngineManager.switchToLiteRT(modelPath)` immediately. Show loading indicator during init. On success → green badge. On failure → red badge + error snackbar. *Rationale: Users expect immediate feedback. Delaying to first message adds "why isn't my model responding" confusion.*

- **Q2: What happens when connecting a second local model while one is already connected?** Gracefully unload the first (EngineManager.unloadCurrent → switchToLiteRT for the new one). Show a brief toast: "Switched to {modelName}". *Rationale: Only 1 local model in RAM — device memory constraint. This is explicit user intent (they toggled).*

- **Q3: Should disconnect keep the model selected (just not loaded)?** No. Disconnect = unload from RAM AND deselect. User must explicitly connect a model for it to be usable. Gray badge = not connected, no model selected. *Rationale: The concept of "selected but not loaded" adds complexity with no benefit. The toggle IS the selection.*

- **Q4: Should we show RAM consumption info next to the toggle?** Show model size in the card (already done) but don't add a RAM meter per toggle. Memory info will be shown in Phase 34 (Smart Presets). *Rationale: Keeps scope clean — memory display is Phase 34's job.*

### Grey Area 3/4: Screen Layout — Unified Selector Design

- **Q1: Should the unified selector be a full-screen navigation destination or an overlay/bottom sheet?** Full-screen navigation destination with its own route (`"selector"`). Accessible from ChatScreen TopAppBar and nav drawer. *Rationale: The list of endpoints + models can be long. Full screen allows proper scrolling, search, and CRUD operations.*

- **Q2: How to organize sections visually?** Two distinct sections with headers: "Local Models" (top, shows downloaded models with connect toggle) and "Network Endpoints" (below, shows saved endpoints with expandable model lists). Separated by a subtitle divider. *Rationale: Clear visual hierarchy — local first (what the user most frequently interacts with), remote second.*

- **Q3: Per local model card — what UI elements?** Model name, size badge, quantization, capabilities badges, and a Switch (connect/disconnect toggle) on the right. Connected state shows green indicator dot. Delete button in a dropdown menu (not primary action). *Rationale: The connect toggle is the primary action — it should be prominent and toggle-like, not buried behind a button.*

- **Q4: Per remote endpoint card — what UI elements?** Endpoint name, URL, apiType badge, expandable section showing fetched models. Each remote model row shows: model name, "Use in chat" button. Endpoint CRUD (edit/delete) accessible via long-press or card menu. *Rationale: Remote endpoints have 2-level hierarchy: endpoint → models. Expandable cards keep the list scannable.*

### Grey Area 4/4: Remote Model Discovery & UX

- **Q1: Should remote model lists auto-fetch on screen open, or require manual refresh?** Auto-fetch for endpoints that have a modelId already set (single model). For endpoints without a modelId (e.g., OpenAI with many models), show a "Fetch models" button per endpoint card. *Rationale: Avoids hitting every API on screen open — some endpoints have hundreds of models. Lazy fetch is better UX.*

- **Q2: How to handle fetch failures?** Show error badge on the endpoint card. Don't block screen rendering. Allow retry via pull-to-refresh or a retry button. *Rationale: Network calls can fail — the screen stays functional even if one endpoint is unreachable.*

- **Q3: Should fetched remote model lists persist across screen navigations?** Cache in memory (ViewModel-scoped). Refetch on explicit user action or when returning to the screen after >5 minutes. Don't persist to Room (too volatile). *Rationale: Remote model lists change frequently. In-memory cache reduces redundant API calls without adding stale data.*

- **Q4: Should we fetch model capabilities from remote endpoints?** Not in this phase. Remote endpoints don't expose capability info consistently. Show model name and ID only. *Rationale: Capability detection for remote models is provider-specific and unreliable.*

### the agent's Discretion
- Exact padding, spacing, and color values for the selector screen
- Whether to use LazyColumn or LazyVerticalStaggeredGrid for the model list
- Exact animation for connect/disconnect toggle transition
- Whether to show a BottomSheet or dialog for endpoint management CRUD vs inline expansion
- Error message wording for connection failures

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ModelSelector.kt` (ui/chat/components/) — current dropdown composable. Will be **replaced** by the new unified screen. Keep for reference during migration.
- `ModelsScreen.kt` — current standalone screen showing local models + endpoints. Will be **refactored** into the new `UnifiedSelectorScreen`. Keep CRUD logic but restructure layout.
- `ModelsViewModel.kt` — handles `useLocalModel()`, `useEndpoint()`, endpoint CRUD, downloads. Will be **adapted** to support connect/disconnect toggle API.
- `ChatViewModel.kt` — orchestrates model selection and provider resolution. Current logic: `selectedModelId` + `selectedProvider` determine which provider to call. Will need to handle `dual selection` (local selection ≠ remote selection).
- `ActiveModelSelection.kt` (domain/model/) — persists single active model to Keystore. Needs to **split into local + remote independently**.
- `EngineManager.kt` — `switchToLiteRT()`, `unloadCurrent()`, `isEngineLoaded()` — the API surface for connect/disconnect toggle.
- `EndpointRepository` — CRUD for remote endpoints. Used in the new screen for endpoint management.
- `LocalModelRepository.observeModels()` — reactive Flow of downloaded models.
- `EndpointRepository.observeEndpoints()` — reactive Flow of saved endpoints.

### Established Patterns
- MVVM: ViewModel exposes `StateFlow<UiState>`, Composables observe via `collectAsStateWithLifecycle()`
- `@HiltViewModel` + constructor injection for all ViewModels
- `ActiveModelSelection` is `@Singleton` — same instance shared across ChatViewModel and ModelsViewModel
- `ProviderRouter.resolve()` and `resolveLocal()` for provider instantiation
- `ChatUiState` tracks `selectedModelId`, `selectedProvider`, `isLocalModelLoaded`, `activeBackend`
- Navigation: `NavGraph` with sealed `Screen` class for routes
- Cards use `Color(0xFF2B2B29)` background, `RoundedCornerShape(12.dp)`, `MaterialTheme.typography.titleMedium`
- `WarpedAlertDialog` for confirmations (delete, memory warning)
- `HorizontalDivider()` for section separators in lists

### Integration Points
- **NavGraph.kt** — add new `Selector` route: `composable("selector") → UnifiedSelectorScreen()`
- **ChatScreen TopAppBar** — replace/integrate current ModelSelector dropdown button with navigation to the new selector screen
- **Nav drawer** — add "Models & Endpoints" item (currently goes to ModelsScreen)
- **ChatViewModel.kt** — split `selectedModelId`/`selectedProvider` into `localModelId`/`remoteModelId`/`remoteProvider`
- **ActiveModelSelection.kt** — split into localSelection + remoteSelection StateFlows

</code_context>

<specifics>
## Specific Ideas

From the ROADMAP success criteria:
1. User opens Models & Endpoints screen and sees local models section with a connect/disconnect switch per model
2. Only 1 local model can be connected at a time — connecting a second gracefully unloads the first
3. Remote endpoints and their models are listed below local models with clear visual separation
4. Local model shows green badge when connected (loaded in RAM), gray when disconnected
5. Tapping a remote endpoint model sets it as active without affecting the local model connection state

</specifics>

<deferred>
## Deferred Ideas

- Memory display per model (Phase 34: Smart Presets)
- Traffic light status indicator in TopAppBar (Phase 33)
- Auto-reconnect on app restart (user must explicitly connect — per grey area decision)
- Remote model capability detection (unreliable across providers)
- Endpoint model list persistence to Room (too volatile, per grey area decision)

</deferred>
