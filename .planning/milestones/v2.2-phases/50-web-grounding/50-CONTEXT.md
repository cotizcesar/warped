# Phase 50: Web Grounding - Context

**Gathered:** 2026-09-28
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous, user accepted all recommended)

<domain>
## Phase Boundary

User pastes a URL and gets a grounded answer with cited sources — or a clearly-marked model-only answer when offline. Heuristic zero-dependency grounding (existing OkHttp + ConnectivityManager, hand-rolled HTML→text). Builds on post-removal transcript shape from Phase 49. Covers WEB-01..WEB-06. LlmModelHelper unchanged.
</domain>

<decisions>
## Implementation Decisions

### Fetch Policy (WEB-01, WEB-02)
- Deterministic URL heuristic: first http(s) URL in message triggers a single fetch; 2nd+ URLs ignored (no multi-fetch agent loop — WEBF-02 deferred)
- Caps: 64KB byte cap, 3 redirects max, 8s connect / 10s read timeouts, browser User-Agent
- Cancelable: OkHttp Call.cancel() wired to Stop button; fetch on Dispatchers.IO, never blocks UI
- Pre-check via ConnectivityManager; offline short-circuits to model-only path

### Context Injection & Trust Boundary (WEB-03, WEB-04, WEB-05)
- [WEB CONTEXT] delimited block, hand-rolled HTML→text (strip tags/scripts/styles, decode entities), truncated to ~4KB budget (fits smallest allowlisted model window)
- Sanitization: strip instruction-like lines (imperative hijack patterns), escape delimiter collisions so fetched content cannot break out or hijack the model
- Failures never injected as context — fetch errors map to model-only path with UI notice
- System grounding prompt: consume block, cite numbered sources [1]/[2], ask user to paste a link when freshness needed, never invent URLs

### UX & Settings (WEB-06)
- "Leyendo página…" status chip during fetch (cancelable via Stop)
- Numbered Fuentes list rendered under grounded answers (non-model UI where possible)
- Offline/failure notice: visible model-only banner in Spanish, rendered by UI (never model-generated, never error text as context)
- Settings toggle for web grounding, default ON

### the agent's Discretion
- Exact heuristic regex, sanitizer patterns, and budget split at planner discretion within caps above; Jsoup escalation only if hand-rolled quality bar fails (out of scope unless triggered)
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- OkHttp via NetworkModule/HttpClientFactory (reuse client, add per-call timeouts/Call handle)
- ConnectivityManager (platform pre-check)
- ChatViewModel send pipeline (hook single fetch before inference, both local + remote paths)
- LlmModelHelper interface unchanged — grounding is pre-inference message augmentation + system prompt
- PromptTemplate (grounding system text lives here or adjacent)

### Established Patterns
- callbackFlow/Flow token streaming to Compose; Stop → cancel inference Job (extend to fetch Call)
- DataStore Preferences for settings toggle
- Room transcript: user message keeps original URL text; [WEB CONTEXT] is ephemeral augmentation, not persisted as separate message

### Integration Points
- ChatViewModel.sendMessage (fetch hook + status state)
- Local providers (LiteRTLmProvider/LocalLlmProvider) + remote providers (single-turn post-49) consume augmented prompt
- Settings screen + SettingsViewModel (toggle); ChatScreen status chip + Fuentes + offline banner
</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches. Research flags from STATE: HTML→text quality bar + Jsoup-escalation trigger; fetch-budget vs smallest allowlist window; delimiter robustness on small local models.
</specifics>

<deferred>
## Deferred Ideas

- WEBF-01 search API (Brave/Tavily), WEBF-02 multi-fetch agentic loop, WEBF-03 entailment verification — all Future, out of v2.2
- None else — discussion stayed within phase scope
</deferred>
