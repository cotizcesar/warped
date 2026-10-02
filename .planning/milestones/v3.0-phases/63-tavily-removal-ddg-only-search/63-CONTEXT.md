# Phase 63: Tavily Removal → DDG-only Search - Context

**Gathered:** 2026-10-02
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous, 3 areas accepted by user)

<domain>
## Phase Boundary

Remove the Tavily web-search integration end-to-end (client, producer, DI, Settings UI, Keystore paths, EN+ES strings, tests — grep-clean per SEARCH-01) and leave DuckDuckGo as the single search producer behind the existing producer interface. Upgrading users lose no functionality and keep no orphaned Tavily Keystore entry; legacy chats with Tavily citations still open read-only.

</domain>

<decisions>
## Implementation Decisions

### Removal scope
- Delete `TavilyApi.kt`, `TavilyDtos.kt`, and `TavilySearchRepository.kt` entirely — no stubs, grep-clean.
- Remove the Tavily settings card plus its EN+ES strings.
- Tavily-referencing tests: rewrite to DDG where the coverage still matters, delete Tavily-only tests.

### DDG-only wiring
- Single DuckDuckGo producer (`DuckDuckGoSearchRepository`) behind the existing search producer interface; no fallback chain.
- Preserve existing search budget + cancellation semantics as-is.
- Reuse existing DDG error/empty-result copy; no new user-facing strings.

### Migration + legacy
- Delete the orphaned Tavily Keystore alias via lightweight startup cleanup, reusing the existing `ApiKeyStore.deleteTavilyKey()` path (dedicated string alias, not per-endpoint id).
- Keep `GroundedSourceEntity` + the citation render path so legacy Tavily-cited chats open read-only without crashes.
- Render legacy Tavily rows with a generic source label — no Tavily branding.

### the agent's Discretion
- Exact DI rebind shape in `NetworkModule` and any producer-selection branches in `ProviderRouter`/`CompatToolLoop` call sites.
- Whether the startup Keystore cleanup lives in an existing migration helper or application init — pick the codebase-idiomatic spot.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `data/grounding/DuckDuckGoSearchRepository.kt` (400 lines) — surviving producer; its budget/cancel behavior is the contract to preserve.
- `data/grounding/TavilySearchRepository.kt` (254 lines) — to delete; check its call sites first (provider files, `WebSearchToolSet`, `LocalToolLoop`).
- `data/local/security/ApiKeyStore.kt` — has `storeTavilyKey`/`getTavilyKey`/`deleteTavilyKey` under dedicated alias `TAVILY_ALIAS`; delete accessors, keep one-shot cleanup.
- `data/remote/api/TavilyApi.kt` (27 lines) + `data/remote/dto/TavilyDtos.kt` (64 lines) — delete with DI bindings in `di/NetworkModule.kt`.
- `data/grounding/SearchOgEnricher.kt`, `MultiUrlFetcher.kt`, `OpenGraphParser.kt` — shared enrichment, stays (verify no Tavily coupling).

### Established Patterns
- Settings state via `ui/settings/SettingsViewModel.kt` + `SettingsUiState.kt` + `SettingsScreen.kt` — remove Tavily card there.
- Grounding precedences/prompts (`GroundingPrecedence`, `GroundingPrompt`, `NeedsWeb`) are producer-agnostic — keep.
- House precedent (v2.2): orphaned `huggingface_token` Keystore entry accepted as harmless — but SEARCH-03 explicitly requires Tavily alias deletion, so active cleanup is in scope.

### Integration Points
- UI references to Tavily: `MessageBubble.kt`, `ChatViewModel.kt`, `TurnStatus.kt` (citation rendering — keep working read-only), `SettingsScreen.kt` (key card — remove).
- DI: `di/NetworkModule.kt` Tavily bindings.
- DB: `GroundedSourceEntity.kt`, `Migrations.kt` — entity stays for legacy rows; no schema change expected.

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches. Success criteria are the contract: zero API-key setup for DDG answers, no Tavily UI surface, no orphaned Keystore entry, legacy citations render.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>
