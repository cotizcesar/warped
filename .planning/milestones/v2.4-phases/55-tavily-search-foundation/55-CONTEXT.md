# Phase 55: Tavily Search Foundation - Context

**Gathered:** 2026-09-28
**Status:** Ready for planning
**Mode:** Inline discuss (5 locked user decisions, no open areas)

<domain>
## Phase Boundary

Users ground answers in Tavily search results with a stored API key. Settings holds a Keystore-encrypted Tavily key with test-connection; search queries return top-N results fused into the existing grounding pipeline (numbered citations, Fuentes, preview rows); search honors grounding enablement (global + per-chat toggle) and the offline gate. No tool loop in this phase — direct search→ground path that Phase 56's agentic loop will call as the `web_search` implementation.

</domain>

<decisions>
## Implementation Decisions

### Provider (locked: Tavily)
- Tavily Search API (https://api.tavily.com/search), user-provided key — same quality as the reference OpenCode flow
- Key stored via existing ApiKeyStore/KeystoreManager pattern (never plaintext, never logged); add a dedicated key id for Tavily (not per-endpoint)
- Settings row: key field + Test connection button + clear missing/invalid-key errors in English

### Search → Ground fusion (locked: existing pipeline)
- Top-N results (default 5, max 10) map to the fused-block pipeline: each result = title + URL + content snippet as one numbered source
- Use Tavily `include_answer`/`extract`? Planner decides payload shape (search_depth, chunks) within latency budget; must reuse buildFusedBlock/GroundingBudget/per-source ok-skipped semantics
- Citations, Fuentes list, preview rows, persisted rows: identical to URL grounding (same rows the sheet reads)

### Enablement + gates (locked: TAV-03)
- Search runs only when grounding resolves enabled (global default-ON + per-chat tri-state, same precedence as fetch) AND connectivity validated AND key present
- Missing key → actionable message (where to get it + where to paste it); invalid key (401) → distinct invalid-key message; offline → existing offline path, no search attempt

### the agent's Discretion
- Tavily request/response DTO shapes, Retrofit vs OkHttp-direct client, timeouts/retries, result-count default
- Settings placement detail within existing Settings surfaces
- Test-connection implementation (cheap `search` with garbage query vs `/extract` ping — cheapest truthful option)

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `data/local/security/ApiKeyStore.kt` — storeKey/getKey/deleteKey per endpoint id; extend with a Tavily key id (check how endpoint ids are namespaced first)
- `data/grounding/` — MultiUrlFetcher.fetchAll entry, GroundingPrompt.buildFusedBlock, GroundingBudget, GroundingResult, WebContextSanitizer (search snippets are untrusted input — sanitize like fetched pages)
- `data/remote/` — Retrofit/OkHttp patterns, DTO conventions (kotlinx.serialization)
- `ui/settings/SettingsScreen.kt` + SettingsViewModel — settings row patterns; `ui/chat/ChatViewModel.kt` grounding hook (fetch-bypass point when URLs absent)
- `di/NetworkModule.kt` — OkHttp client factory (new client WITHOUT endpoint AuthInterceptor for Tavily; Tavily uses its own `Authorization: Bearer` or `x-api-key` — verify header scheme)

### Established Patterns
- Hilt Singleton providers; Dispatchers.IO network; StateFlow UI state; JVM-testable pure logic; English copy only
- Zero-dependency ethos broken only with justification (Tavily = HTTPS API, no new dep)

### Integration Points
- ChatViewModel grounding hook — search branch when grounding enabled + zero URLs detected
- Settings nav — Tavily key row
- Phase 56 consumes: search function becomes the `web_search` tool implementation (same signature)

</code>

<specifics>
## Specific Ideas

- Reference quality bar: OpenCode Tavily flow (screenshot 2026-09-28) — "Web Search via Tavily" with titled results + snippets + URLs
- Tavily API: POST https://api.tavily.com/search {api_key|Authorization, query, search_depth, max_results, include_answer}

</specifics>

<deferred>
## Deferred Ideas

- Agentic invocation (Phase 56), remote tools[] (Phase 57), OG thumbnails (Phase 58)
- Non-Tavily providers (Brave/DuckDuckGo rejected by user)

</deferred>
