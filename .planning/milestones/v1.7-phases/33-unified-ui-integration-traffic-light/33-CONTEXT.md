# Phase 33: Unified UI Integration & Traffic Light — Context

**Gathered:** 2026-05-25
**Status:** Ready for planning

<domain>
## Phase Boundary

Integrate the unified selector into the app navigation (done in Phase 32), and implement the full traffic light status indicator for both local and remote. The traffic light circle is always visible in ChatScreen TopAppBar showing combined provider health.

</domain>

<decisions>
## Implementation Decisions

### Traffic Light States (Auto-Decided)

- **Local states:** Green (loaded+ready), Red (error), Gray (not loaded), Yellow (streaming)
- **Remote states:** Green (connected — last request succeeded), Red (error/disconnected), Gray (idle — no recent request)
- **Combined display:** Always show one light reflecting the active provider. If remote selected, show remote state. If local, show local state.
- **Click behavior:** Show snackbar with detailed status text instead of unloading (unload is via selector screen now)
- **Streaming detection:** Use existing `uiState.isStreaming` for Yellow state

### Navigation Cleanup (Auto-Decided)

- Keep `EndpointsScreen` route for backward compat, but it's not in primary navigation
- ModelsScreen route still exists but unused — kept for deep link compat
</decisions>

<code_context>
## Existing Code Insights

- Traffic light already exists in ChatScreen TopAppBar actions slot (lines 187-206), but only for local
- ChatUiState has `isLocalModelLoaded`, `isStreaming`, `connectionStatus`, `memoryWarningModel`
- Selector screen already integrated via NavGraph
</code_context>

<specifics>
## Specific Ideas

Per ROADMAP success criteria:
1. ChatScreen TopAppBar shows unified Models & Endpoints button (done in Phase 32)
2. EndpointsScreen route not in primary nav (done — never wired to NavHost)
3. Traffic light always visible for both local and remote
4. Local: Green/Red/Gray/Yellow. Remote: Green/Red/Gray.
5. Click reveals snackbar with detailed status
</specifics>

<deferred>
## Deferred Ideas

None
</deferred>
