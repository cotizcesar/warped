# Phase 9: UI Integration - Context

**Gathered:** 2026-05-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Make LiteRT-LM fully user-facing with separate ecosystem tabs, format badges, backend status indicators, cached loading, and lifecycle-aware memory management. Extends HuggingFaceScreen with TabRow, adds format badges to model cards, shows active backend during chat, integrates LiteRT-LM models into the model selector, and implements caching + lifecycle memory release.

**Depends on:** Phase 6 (engines), Phase 7 (provider), Phase 8 (acquisition + activeFormat in UiState)
**Requirements:** UI-01, UI-02, UI-03, UI-04, UI-05, POL-03, POL-05
</domain>

<decisions>
## Implementation Decisions

### Model Tabs & Badges
- Extend HuggingFaceScreen with Material3 `TabRow` — "GGUF" and "LiteRT-LM" tabs
- Driven by `activeFormat` from HuggingFaceUiState (already exists in code)
- Format badges as `Surface` with colored text: "GGUF" blue, "LiteRT-LM" green
- `activeFormat` persists within session (ViewModel state), resets on ViewModel recreation
- Each tab filters model list by format

### Backend Status & Model Selector
- Bottom chip in chat input area: "LiteRT-LM · CPU" or "LiteRT-LM · GPU"
- Read from `EngineManager.getActiveEngine()?.backend` (ActiveEngine already tracks this)
- Add LiteRT-LM models to existing model list with format badge next to name
- Filter model selector dropdown by engine compatibility (LITE_RT_LM type → .litertlm formats)

### Caching & Lifecycle
- Copy `.litertlm` model to `cacheDir` on first EngineManager init
- Subsequent loads use cached copy; fall back to original path if cache missing
- Release cache on `onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL)` in Application
- EngineManager.handleTrimMemory() for lifecycle-aware release
- EngineManager.unloadCurrent() handles model switch (already done)
- Cache does NOT survive app restart (cacheDir semantics) — auto-recopy on next init

### the agent's Discretion
- TabRow styling, badge colors, exact composable layout
- Model deletion confirmation dialog wording
- Cache directory naming under cacheDir
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `HuggingFaceScreen` — existing composable with search, model list, downloads. Extend with TabRow
- `HuggingFaceViewModel` — already has `activeFormat`, `setActiveFormat()`, format-aware search
- `HuggingFaceUiState` — already has `activeFormat: String = "gguf"`
- `EngineManager` — provides `getActiveEngine()?.backend` for backend status display
- `ChatScreen` / `ChatViewModel` — integrate backend status chip + model selector
- `LocalModelDao` / `LocalModelRepository` — model delete/view operations

### Integration Points
- HuggingFaceScreen: add TabRow composable
- Model cards: add format badge composable
- ChatScreen: add backend status chip
- Model list dropdown: filter by format compatibility
- EngineManager: add lifecycle handling (onTrimMemory)
- Application class: register ComponentCallbacks2
</code_context>

<specifics>
No specific requirements beyond ROADMAP success criteria — follow Material 3 patterns.
</specifics>

<deferred>
None — discussion stayed within phase scope.
</deferred>
