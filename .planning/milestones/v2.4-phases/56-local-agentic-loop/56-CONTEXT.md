# Phase 56: Local Agentic Loop - Context

**Gathered:** 2026-09-29
**Status:** Ready for planning
**Mode:** Inline discuss (3 areas accepted, no open items)

<domain>
## Phase Boundary

Local models invoke `web_search` / `web_fetch` autonomously via LiteRT-LM function calling. Multi-turn loop with step cap and Stop-cancels-all; thinking-channel tokens never leak into answers; tool outputs pass the existing trust boundary; exactly two tools exposed. Builds on Phase 55 (Tavily producer becomes the `web_search` implementation; `fetchAll` becomes `web_fetch`). Local only — remote is Phase 57.

</domain>

<decisions>
## Implementation Decisions

### Loop Visibility
- Every tool call renders a transient status row (search: query text; fetch: URL) reusing the ToolStatus UX pattern; rows never persist to history
- Stop cancels the entire loop (in-flight tool + generation); new send pre-cancels like the fetch path
- Completion flows into the normal grounded answer path (citations, Fuentes, rows)

### Limits + Failure Policy
- Max 5 tool rounds per message; on reaching the cap the model answers with what was gathered (no hard error)
- Tool failure feeds a concise English error to the model (it continues); failures never crash the turn
- Offline/key-missing at tool time → same actionable messages as Phase 55 (no socket, no credit burn)

### Tool Surface + Thinking
- Exactly `web_search` (Tavily producer) + `web_fetch` (existing fetchAll); no file/system/shell tools; no local-context leaks to tools
- Thinking-channel content filtered from KV cache if the SDK exposes it; otherwise thinking tokens route to the existing Thinking panel (never interleaved into answer Deltas)
- Automatic vs manual loop mechanics: planner's call per Kotlin SDK capabilities (automaticToolCalling if sound, else app-driven iteration) — same observable contract either way

### the agent's Discretion
- Tool JSON schemas/annotations (@Tool/@ToolParam shapes), ConversationConfig wiring point, status-row copy details
- Step-cap counting unit (tool calls vs rounds) — user-observable behavior identical

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `data/local/inference/LiteRTLmProvider.kt` — ConversationConfig flows to engine (line ~169); channel/thinking notes (~337-340); StreamToken.ToolStatus/ToolCompleted types flow (consumers ignore)
- `data/local/inference/LiteRTLmEngine.kt` — createConversation(config) accepts ToolSet-capable configs (~182-192); ThinkingConfig overload precedent (~172-192)
- `data/local/inference/EngineManager.kt` — createLiteRTConversation(config) passthrough (~274-275)
- Phase 55 `TavilySearchRepository.search()` — the `web_search` implementation (outcome union incl. MissingKey/InvalidKey)
- `data/grounding/MultiUrlFetcher.fetchAll` — the `web_fetch` implementation (partial grounding, OFFLINE collapse)
- `domain/model/StreamToken.kt` — ToolStatus(toolName)/ToolCompleted records (local-path passthrough today)
- `ui/chat/ChatViewModel.kt:713-714` — ToolStatus/ToolCompleted currently Unit (wire-up point for status rows)

### Established Patterns
- Hilt Singletons; Dispatchers.IO network; StateFlow UI; JVM-testable pure logic; English copy; zero new deps
- Trust boundary: sanitize-before-model (WebContextSanitizer), Bearer-per-call, no sockets offline

### Integration Points
- ChatViewModel.sendMessage() — loop entry (same hook position as grounding), Stop path shared
- Grounding toggle precedence + validated internet gate the whole loop (no tools when grounding off/offline)
- Phase 57 consumes: same two tools via remote tools[] (signatures should be provider-neutral)

</code>

<specifics>
## Specific Ideas

- SDK precedent (Python docs): ThinkingConfig(enable_thinking, thinking_token_budget), filter_channel_content_from_kv_cache, automatic_tool_calling flag, Tool approval handlers — mirror in Kotlin where the 0.17.1 Android API exposes them
- Tavily credit guard: blank-query short-circuit + basic-depth default already in producer; loop cap bounds worst-case credits per message

</specifics>

<deferred>
## Deferred Ideas

- Remote tools[] loop (Phase 57), OG thumbnails (Phase 58)
- Non-web tools (globally out of scope), ThinkingConfig engine enablement beyond channel hygiene

</deferred>
