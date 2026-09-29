# Phase 57: Remote Agentic Loop - Context

**Gathered:** 2026-09-29
**Status:** Ready for planning
**Mode:** Inline discuss (3 areas accepted, no open items)

<domain>
## Phase Boundary

Remote OpenAI-compatible models use the same two tools via the native `tools[]` loop. Per-provider capability gating with graceful fallback (one retry without tools + visible notice, never silent failure). Same 5-call cap, same transient status rows, same Stop, same trust boundary on tool outputs (remote providers are untrusted too). Endpoint API keys never leave toward Tavily or fetched pages. Local loop untouched.

</domain>

<decisions>
## Implementation Decisions

### Capability Gating
- Tools[] attempted per a static matrix by apiType (OpenAI + known-compatibles first; Ollama/LM Studio/custom per matrix)
- Provider rejection of tools[] → exactly one retry without tools + visible user notice (never silent fallback, never hard error)
- Matrix lives in code (not remote config); unknown/custom types default to attempt-then-fallback

### Parity with Local
- Same 5-call cap (calls, not rounds — credit-bounded identically)
- Same transient Using/Searching…/Reading… rows, same maxLines/ellipsis, same disappearance rules
- Same Stop-cancels-all + new-send pre-cancel
- Remote tool outputs sanitized identically (provider output is untrusted input: hijack filter, delimiter escaping, URL allowlist)

### Tool Surface + Secrets
- web_search/web_fetch with schemas identical to local (provider-neutral descriptions already in place)
- Same Tavily dedicated client + Bearer-per-call; endpoint Authorization keys never attached to Tavily/fetch requests (existing strip reused and re-verified)
- Same fused-block → citations/Fuentes/rows pipeline downstream

### the agent's Discretion
- tools[] wire format details per provider dialect (strict vs loose function schemas, tool_calls parsing, tool-role message shape)
- Capability matrix exact contents per apiType (research provider docs knowledge)
- Retry-without-tools notice copy (English, actionable)

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- Phase 55 Tavily producer + dedicated client (the `web_search` implementation, unchanged)
- Phase 56 policy core (cap counting, sanitized fused mapping, status-copy helpers Searching…/Reading… — reuse where provider-neutral)
- `data/remote/provider/` — OpenAI/Anthropic/Ollama/LMStudio/Custom providers + ProviderRouter (tools[] insertion point per provider)
- `domain/model/StreamToken.kt` — ToolStatus/ToolCompleted (remote loop emits the same events for the same rows)
- `ui/chat/ChatViewModel.kt` — transient toolCallActive rows, Stop path, grounding hook (remote branch adjacent to local loop entry)

### Established Patterns
- Hilt providers per remote type; SSE streaming parse; StateFlow UI; JVM-testable pure parsing; English copy; zero new deps
- Trust boundary: sanitize-before-model on everything crossing from network to prompt

### Integration Points
- ChatViewModel remote send path — loop entry mirrors local position
- ProviderRouter resolve — capability matrix consulted before attaching tools[]
- Same Fuentes/preview/persist/citations downstream (untouched)

</code>

<specifics>
## Specific Ideas

- OpenAI tools[] reference: functions[] schemas with strict flag, assistant tool_calls[] deltas over SSE, tool-role messages with tool_call_id, loop until no more calls or cap
- Anthropic dialect differs (tools + tool_use/tool_result blocks) — planner covers per provider in matrix scope

</specifics>

<deferred>
## Deferred Ideas

- OG thumbnails (Phase 58)
- Non-web tools (globally out of scope)
- Auto capability probing per endpoint (static matrix is the v2.4 call; probing is a follow-up)

</deferred>
