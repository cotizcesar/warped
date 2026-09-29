# Phase 57: Remote Agentic Loop - Research

**Researched:** 2026-09-29
**Domain:** OpenAI-compatible function calling (tools[] loop) on Android — Kotlin + OkHttp SSE + kotlinx.serialization
**Confidence:** MEDIUM (wire formats verified against official docs; per-server quirks need device confirmation)

## Summary

Phase 57 gives remote models the same two web tools (`web_search` / `web_fetch`) the local Gemma loop got in Phase 56, but through each provider's **native** function-calling dialect instead of LiteRT-LM `@Tool` schemas. The app already speaks five remote dialects (`data/remote/provider/`); only the OpenAI Chat Completions shape (`/v1/chat/completions`) needs the full loop, plus a smaller native-dialect adapter for Anthropic. Ollama can ride the OpenAI-compat shape (its `/v1` endpoint supports `tools[]` [CITED: docs.ollama.com/api/openai-compatibility]); LM Studio and Custom default to attempt-then-fallback per the locked decision.

The loop driver mirrors the Phase 56 manual loop exactly: same 5-call cap counted in calls, same `LocalToolLoop` pure policy reused verbatim (dispatch, arg validation, outcome mapping, `statusDisplay`, cap string), same `StreamToken.ToolStatus` / `ToolCompleted` events into the existing transient rows, same Stop/new-send cancel path. The genuinely new code is small and well-bounded: (1) `tools[]` request DTOs + `tool_calls` SSE delta reassembly, (2) transient `role:tool` message construction inside the loop, (3) a static per-`ProviderType` capability matrix + one-retry-without-tools fallback with visible notice, (4) secret-isolation wiring (endpoint keys never touch the Tavily/fetch clients).

**Primary recommendation:** Build one shared `RemoteToolLoop` driver parameterized by a two-method dialect interface (`attachTools(requestBuilder)` + `parseEvent`), with the OpenAI Chat Completions dialect first-class, Anthropic as the second dialect, Ollama routed through the OpenAI-compat dialect, and LM Studio/Custom on attempt-then-fallback. Send **loose** (non-strict) schemas. Reuse `LocalToolLoop` for everything provider-neutral — do not fork the policy.

## User Constraints (from CONTEXT.md)

### Locked Decisions
- Tools[] attempted per a static matrix by apiType (OpenAI + known-compatibles first; Ollama/LM Studio/custom per matrix)
- Provider rejection of tools[] → exactly one retry without tools + visible user notice (never silent fallback, never hard error)
- Matrix lives in code (not remote config); unknown/custom types default to attempt-then-fallback
- Same 5-call cap (calls, not rounds — credit-bounded identically)
- Same transient Using/Searching…/Reading… rows, same maxLines/ellipsis, same disappearance rules
- Same Stop-cancels-all + new-send pre-cancel
- Remote tool outputs sanitized identically (provider output is untrusted input: hijack filter, delimiter escaping, URL allowlist)
- web_search/web_fetch with schemas identical to local (provider-neutral descriptions already in place)
- Same Tavily dedicated client + Bearer-per-call; endpoint Authorization keys never attached to Tavily/fetch requests (existing strip reused and re-verified)
- Same fused-block → citations/Fuentes/rows pipeline downstream

### the agent's Discretion
- tools[] wire format details per provider dialect (strict vs loose function schemas, tool_calls parsing, tool-role message shape)
- Capability matrix exact contents per apiType (research provider docs knowledge)
- Retry-without-tools notice copy (English, actionable)

### Deferred Ideas (OUT OF SCOPE)
- OG thumbnails (Phase 58)
- Non-web tools (globally out of scope)
- Auto capability probing per endpoint (static matrix is the v2.4 call; probing is a follow-up)

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| AGENT-03 | Remote OpenAI-compatible models invoke the same tools via native `tools[]` loop (function schemas, tool_calls parsing, tool-role messages, loop cap, per-provider capability check) | OpenAI Chat Completions tools[] wire format + SSE `tool_calls` reassembly pattern; Anthropic `tools`/`tool_use`/`tool_result` dialect; capability matrix below; `LocalToolLoop` cap/policy reuse; rejection-classifier + one-retry fallback design |

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| tools[] request building + tool_calls reassembly | API / Backend (provider layer, `data/remote/`) | — | Wire format is per-provider; lives next to existing provider DTOs/parsers |
| Tool execution (Tavily search, page fetch) | API / Backend (existing repos) | — | Reuse `TavilySearchRepository` + `MultiUrlFetcher` unchanged; no new network code |
| Loop policy (cap, dispatch, mapping, status copy) | API / Backend (shared `data/agentic/`) | — | Provider-neutral pure policy already exists as `LocalToolLoop`; remote driver consumes it |
| Loop orchestration (rounds, cancel, retry) | API / Backend (provider `chat()` flow) | — | Mirrors `LiteRTLmProvider.runToolLoop`; per-round `ensureActive`, CE rethrow |
| Transient status rows + Stop + notice copy | Browser / Client (Compose UI: `ChatViewModel`, `ChatScreen`) | — | Existing `toolCallActive` slot + `Using …` chip reused; no new UI state shape |
| Secret isolation | API / Backend (OkHttp client scoping) | — | Endpoint keys live only on per-provider interceptors; Tavily/fetch clients untouched |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| OkHttp (existing clients) | 4.12.0 [CITED: AGENTS.md stack] | Per-round `POST /v1/chat/completions` with tools[]; raw `source().readUtf8Line()` SSE read | `OpenAIProvider.chat()` already does manual OkHttp SSE (not Retrofit) — the loop needs per-round body rebuilds and a retained cancellable `Call`, which the Retrofit `api.chatCompletions` path cannot do |
| kotlinx.serialization (existing) | 1.7.x [CITED: AGENTS.md stack] | `tools[]`/`tool_calls`/`tool` DTOs with `ignoreUnknownKeys = true` | All provider DTOs already use it; additive `tool_calls` fields on `OpenAiStreamDelta` are parse-safe |
| `LocalToolLoop` object (Phase 56) | current tree | Cap, dispatch, `validateArgs`, `mapSearchOutcome`, `mapFetchResult`, `statusDisplay`, copy twins | Locked: identical policy; JVM-tested (16 + 23 cases). Remote driver consumes, never forks |
| `TavilySearchRepository` + `MultiUrlFetcher` | Phase 55 / 52 | Tool executors on `Dispatchers.IO` | Locked: same dedicated client + Bearer-per-call, same fused-block outputs |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `StreamToken.ToolStatus` / `ToolCompleted` | current tree (`domain/model/StreamToken.kt`) | Transient row events | Remote loop emits the identical sequence local emits (`ToolStatus(display)` → work → `ToolCompleted` → `ToolStatus(null)` in `finally`) |
| `SseParser` | current tree | `data:`-frame splitting | Optional; `OpenAIProvider` currently hand-rolls line parsing — either is fine, do not introduce a second frame-splitting behavior |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Manual OkHttp loop in `OpenAIProvider` | Retrofit `@Streaming` + `asSseFlow` (CustomProvider path) | Retrofit path cannot rebuild the request body per round with appended tool messages nor retain a `Call` for Stop; manual OkHttp (LMStudioProvider precedent) is required |
| Chat Completions `tools[]` | Responses API (`/v1/responses`, `previous_response_id`) | App's OpenAI path is Chat Completions (`/v1/chat/completions` in `OpenAIProvider`); Ollama explicitly does NOT support stateful Responses (`previous_response_id` unsupported [CITED: docs.ollama.com]). Stay on Chat Completions |
| Strict schemas (`strict:true`) | Loose schemas (no `strict` flag) | **Use loose.** Strict requires `additionalProperties:false` + all-fields-required [CITED: platform.openai.com/docs/guides/function-calling] and unknown `strict` fields risk rejection by Ollama/LM Studio/custom servers (wasted fallback). Our schemas are single-required-string params — strict buys nothing |

**Installation:** None — zero new dependencies (locked, T-56-SC holds).

### Package Legitimacy Audit
Not applicable — this phase installs no external packages. (Zero-new-deps is a locked constraint.)

## Architecture Patterns

### System Architecture Diagram

```
User turn (ChatViewModel remote branch)
  │  grounding ON? + matrix allows tools? + online?  (else: existing plain path, byte-identical)
  ▼
Round driver (RemoteToolLoop, in provider chat() flow)
  │  1. Build messages: history + transient assistant/tool echoes (in-memory only)
  │  2. POST /v1/chat/completions {messages, tools[]} via manual OkHttp (cancellable Call)
  ▼
SSE stream ──► text deltas ──► StreamToken.Delta (existing path, <think> handling kept)
  │
  └──► tool_calls deltas ──► reassemble by index ──► finish_reason:"tool_calls"
        │  cap reached? ──► feed CAP_REACHED_STRING, finish text turn
        ▼  else (≤5 calls total)
     emit ToolStatus(statusDisplay) ──► existing transient row
        │
     validateArgs → offline gate → TavilySearchRepository / MultiUrlFetcher (Dispatchers.IO)
        │  (endpoint key NEVER on these clients — dedicated Tavily client + keyless fetcher)
        ▼
     LocalToolLoop.mapSearchOutcome / mapFetchResult (sanitized fused block verbatim)
        │
     emit ToolCompleted(toolId, summary) ──► transcript TOOL row on Done (existing)
     append assistant tool_calls echo + role:tool messages ──► next round (POST again)
        │
     provider 400 mentions tools? ──► EXACTLY ONE retry without tools[] + visible notice
  │
Stop / new send ──► coroutine cancel (CE rethrow) + Call.cancel() (LMStudio precedent)
```

A reader traces the primary use case: user asks fresh-info question → round 1 streams `tool_calls:[web_search]` → status row → Tavily executes → fused block fed back as `role:tool` → round 2 streams final text with citations → Done → Fuentes rows (untouched downstream).

### Recommended Project Structure
```
data/agentic/
├── LocalToolLoop.kt          # UNCHANGED — shared pure policy (cap, mapping, status copy)
├── WebSearchToolSet.kt       # UNCHANGED — description constants reused for tools[] entries
├── WebFetchToolSet.kt        # UNCHANGED — same
├── RemoteToolLoop.kt         # NEW — round driver: cap counting, executor fan-out, retry classifier
├── ToolCapabilityMatrix.kt   # NEW — static map ProviderType → ATTEMPT / ATTEMPT_FALLBACK / NATIVE_ANTHROPIC
└── ToolCallAccumulator.kt    # NEW — pure index-keyed SSE arguments reassembly (JVM-tested)
data/remote/dto/
├── OpenAiChatRequest.kt      # EXTEND — tools[] field; OpenAiMessage: tool_calls + tool_call_id roles
├── StreamChunks.kt           # EXTEND — OpenAiStreamDelta.tool_calls; OpenAiStreamChoice.finishReason already exists
└── AnthropicDtos.kt          # EXTEND — tools[], tool_use/tool_result blocks, input_json_delta
data/remote/provider/
├── OpenAIProvider.kt         # EXTEND — tools[] loop entry (manual OkHttp, cancellable Call)
├── AnthropicProvider.kt      # EXTEND — native dialect loop (or NATIVE adapter via RemoteToolLoop interface)
├── OllamaProvider.kt         # EXTEND — route through OpenAI-compat /v1 (or native tools; see matrix)
├── LMStudioProvider.kt       # EXTEND — attempt-then-fallback (no new transport)
└── CustomProvider.kt         # EXTEND — attempt-then-fallback (no new transport)
```

### Pattern 1: Chat Completions tools[] request (loose, OpenAI dialect)
**What:** `tools` array with `type:"function"` + `function:{name, description, parameters}` (JSON Schema, `additionalProperties:false`, single required string — NO `strict` flag). Descriptions copied verbatim from `WEB_SEARCH_TOOL_DESCRIPTION` / `WEB_FETCH_TOOL_DESCRIPTION` constants (locked: identical schemas).
**When to use:** Every tooled round for OPENAI / CUSTOM / OLLAMA-compat / LM_STUDIO-attempt providers.
**Example:**
```kotlin
// Source: https://platform.openai.com/docs/guides/function-calling (Responses shape adapted
// to Chat Completions envelope) + https://docs.ollama.com/api/openai-compatibility (tools[] supported)
@Serializable
data class OpenAiTool(
    val type: String = "function",
    val function: OpenAiFunctionDef,
)
@Serializable
data class OpenAiFunctionDef(
    val name: String,                 // "web_search" | "web_fetch" — exact, snake_case
    val description: String,          // WEB_SEARCH_TOOL_DESCRIPTION verbatim
    val parameters: JsonObject,       // {type:object, properties:{query:{type:string,…}}, required:[query], additionalProperties:false}
)
// Request: OpenAiChatRequest copy + val tools: List<OpenAiTool>? = null
// (null = retry-without-tools path reuses the same DTO)
```

### Pattern 2: tool_calls SSE delta reassembly (index-keyed accumulator)
**What:** Streaming chunks carry `choices[0].delta.tool_calls: [{index, id?, type?, function:{name?, arguments?}}]` where `arguments` is a JSON **string fragment**. Accumulate fragments per `index` in a `StringBuilder`; `id`+`name` typically arrive on the first fragment for that index; `finish_reason:"tool_calls"` (not `[DONE]` alone) signals completeness. Parse the concatenated string with `Json.parseToJsonElement` once per completed call, then feed the arg map through `LocalToolLoop.validateArgs`. [ASSUMED — standard Chat Completions streaming behavior, training knowledge; defensive parsing required, see Pitfall 1]
**When to use:** Every streamed tooled round on the OpenAI dialect.
**Example:**
```kotlin
// Pure accumulator — JVM-testable with zero network imports (47 ToolGating / 56-01 precedent)
class ToolCallAccumulator {
    private val names = mutableMapOf<Int, String>()
    private val ids = mutableMapOf<Int, String>()
    private val argBuffers = mutableMapOf<Int, StringBuilder>()
    fun feed(index: Int, id: String?, name: String?, argumentsFragment: String?) { … }
    fun complete(): List<PendingToolCall> // id (or generated "call_<index>"), canonical name?, args JsonObject?
}
// DTO addition (additive-safe under existing ignoreUnknownKeys=true):
// OpenAiStreamDelta += val tool_calls: List<OpenAiToolCallDelta>? = null
// OpenAiToolCallDelta(index: Int = 0, id: String? = null, type: String? = null,
//                     function: OpenAiFunctionDelta? = null)
// OpenAiFunctionDelta(name: String? = null, arguments: String? = null)
```

### Pattern 3: tool-role message round-trip (transient, in-loop only)
**What:** After executing, append TWO message types to the **in-memory** round list: (a) assistant echo `{"role":"assistant","content":null,"tool_calls":[{id,type:"function",function:{name,arguments}}]}` with the *complete* arguments string, (b) one `{"role":"tool","tool_call_id":id,"content":fusedBlock}` per call. Strict servers reject unpaired `role:tool` — the loop always pairs (echo first, then results). These never touch Room/`ChatMessage`; persistence stays the existing `<toolId>\n<summary>` encoding consumed via `ToolCompleted` on Done (DEL-01 replay paths untouched). [CITED: platform.openai.com/docs/guides/function-calling — tool calling flow, steps 4–5]
**When to use:** Rounds 2..N of any OpenAI-dialect loop turn.

### Pattern 4: Anthropic native dialect (second dialect)
**What:** Request `tools:[{name, description, input_schema}]` (same descriptions/schemas, `input_schema` key instead of `parameters`); response `stop_reason:"tool_use"` + `content:[…,{type:"tool_use",id,name,input}]`; result fed back as user-role `{"type":"tool_result","tool_use_id":id,"content":fusedBlock}` preceded by the assistant content echo; streaming assembles `input` from `content_block_delta/input_json_delta/partial_json` fragments. Disable parallel use is optional — accept parallel `tool_use` blocks and count each toward the 5-call cap (cap counts CALLS, locked). [CITED: platform.claude.com/docs/en/agents-and-tools/tool-use/overview + handle-tool-calls]
**When to use:** ANTHROPIC endpoints only, behind the matrix's NATIVE_ANTHROPIC entry.

### Pattern 5: Capability matrix + one-retry fallback (static, in code)
**What:** `object ToolCapabilityMatrix { fun modeFor(type: ProviderType): ToolMode }` with `ATTEMPT` / `ATTEMPT_FALLBACK` / `NATIVE_ANTHROPIC`. Rejection detection is a pure classifier over (HTTP code, error-body snippet): `code==400 && body mentions tools|tool_calls|function|tool_use (case-insensitive)` → exactly one retry of the same turn with `tools=null` + visible notice; any other error → existing error path unchanged. The classifier is JVM-testable with table tests (ToolGating precedent). Never silent, never more than one retry (locked).
**Recommended matrix contents** (planner's discretion area — recommendation with confidence):
| apiType | Mode | Rationale |
|---------|------|-----------|
| OPENAI | ATTEMPT | Native Chat Completions tools; retry only on 400-rejection |
| CUSTOM | ATTEMPT_FALLBACK (default) | Locked default: unknown servers get one attempt, graceful fallback |
| OLLAMA | ATTEMPT (OpenAI-compat `/v1`) | Ollama `/v1/chat/completions` lists Tools:✓ + streaming:✓ [CITED: docs.ollama.com/api/openai-compatibility]. NOTE: current `OllamaProvider` speaks native `/api/chat` (which also supports tools [CITED: docs.ollama.com tool-calling], different envelope). Planner chooses: (a) add compat-path client to OllamaProvider, or (b) native `/api/chat` tools envelope. (a) reuses the OpenAI dialect verbatim — preferred |
| LM_STUDIO | ATTEMPT_FALLBACK | LM Studio serves OpenAI-compat `/v1` with model-dependent tools support (LOW confidence — verify on device; fallback covers failure) [ASSUMED] |
| ANTHROPIC | NATIVE_ANTHROPIC | First-class provider with its own dialect (Pattern 4) |
**Retry notice copy (draft, English, actionable):** `"This endpoint doesn't support tool calling. Model-only answer — no web sources this turn."` Shown as the transient/banner notice; the turn otherwise completes normally.

### Anti-Patterns to Avoid
- **Forking the loop policy per provider:** `LocalToolLoop` is the single policy. A remote copy of cap/mapping/status strings drifts (the 56-02 credit-stacking lesson). Reuse, don't duplicate.
- **Persisting assistant/tool echoes:** round-list messages are in-memory. Writing `role:tool` rows to Room breaks the DEL-01 replay contract (`toProviderText` encodings) and leaks provider wire shapes into the transcript.
- **Blocking `execute()` without a retained Call:** `OpenAIProvider.chat()` currently blocks in `client.newCall().execute()` with no handle — Stop cannot reach it. Follow the `LMStudioProvider` precedent: retain `currentCall`, `onCallCreated` hook, `CancellationException` rethrown first, `IOException("Canceled")` silent.
- **Serializing conversation history into tool args:** args are model-authored `query`/`url` only (T-56-03 holds remotely too — endpoint operators see tool args in logs).
- **Sending `strict:true` or Responses-API envelopes on Chat Completions:** maximizes rejection surface on third-party servers. Loose Chat Completions shape everywhere.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Web search execution | New search client | `TavilySearchRepository.search()` (dedicated `@Named("tavily")` Retrofit, per-call `Bearer` header — verified in `TavilyApi.kt` + `TavilySearchRepository.kt:95`) | Auth scoping + outcome union + fused pipeline already exist; a second client risks key-scope mistakes |
| Page fetch | New fetcher/OkHttp client | `MultiUrlFetcher.fetchAll` / `WebPageFetcher` (keyless, UA-pinned, allowlisted — verified `WebPageFetcher.kt:72,111-112`) | Trust boundary (allowlist, no-auth-to-arbitrary-hosts) already audited; reuse inherits it |
| Tool-output sanitization | New filters | `LocalToolLoop.mapSearchOutcome/mapFetchResult` (fused blocks verbatim — already sanitized + budgeted) | AGENT-02 trust falls out for free (T-56-01 precedent) |
| SSE frame splitting | New parser | Existing `SseParser` or the hand-rolled `readUtf8Line` loop already in providers | Two framing behaviors double the edge cases (`[DONE]`, blank lines, `event:` prefixes) |
| Status copy | New strings | `LocalToolLoop.statusDisplay` (`Searching for "…"` / `Reading <host>…`, 80-char cap, host-only) | Locked parity incl. maxLines/ellipsis/disappearance |
| Tool-call JSON parsing | String surgery on `arguments` | `Json.parseToJsonElement(concatenated)` + `validateArgs` | Fragment boundaries are arbitrary; only the reassembled string is valid JSON |

**Key insight:** The remote loop is ~80% reuse (policy, executors, UI, persistence) and ~20% new (dialect DTOs + accumulator + matrix + retry). Every hand-rolled replacement of the 80% reintroduces a Phase 56 lesson (never-throw, credit-counting, secret scoping, transient-only rows).

## Common Pitfalls

### Pitfall 1: Fragmented / split tool_calls deltas
**What goes wrong:** `name` arrives in chunk 1, `arguments` fragments split mid-unicode-escape across chunks; naive per-chunk `Json.decodeFromString` throws, loop stalls or emits garbage.
**Why it happens:** SSE chunk boundaries are transport-level, unrelated to JSON structure; `id` may repeat or be absent on continuation chunks.
**How to avoid:** Index-keyed `StringBuilder` accumulation; parse ONLY after `finish_reason=="tool_calls"`; tolerate missing `id` (synthesize `call_<index>`); tolerate missing `name` on continuation (keep first-seen); wrap final parse in try/catch → `toolFailureMessage` degradation, never throw.
**Warning signs:** Tests only covering whole-object `tool_calls`; no test with arguments split across 3+ chunks.

### Pitfall 2: Strict-server rejection of the assistant echo
**What goes wrong:** Round 2 with `role:tool` but a malformed/missing assistant `tool_calls` echo → HTTP 400 on every subsequent round → infinite retry loop.
**Why it happens:** OpenAI-compat servers validate pairing; echo must carry the EXACT `id` and complete `arguments` string returned in round 1.
**How to avoid:** Echo uses the completed (reassembled) arguments string, original ids; the retry classifier fires once per turn max (counter in loop state, reset per user message); retry drops `tools` AND the partial echo (clean plain-message replay).
**Warning signs:** Retry counter living outside per-turn state; echo built from parsed-then-reserialized args (key order/whitespace changes are tolerated, but exact-string is safest).

### Pitfall 3: Non-streaming servers (200 + full JSON, no SSE)
**What goes wrong:** Some compat servers ignore `stream:true` and return one JSON body; the SSE reader sees no `data:` lines and emits "No content in response".
**Why it happens:** `OpenAIProvider` already handles this (`isSse` sniff on first line) for text — the tooled path must handle it too.
**How to avoid:** Mirror the existing sniff: if first line isn't SSE framing, decode `OpenAiNonStreamingResponse` and read `choices[0].message.tool_calls` (requires extending `OpenAiNonStreamingMessage` with `tool_calls`) — same loop entry, no separate code path beyond the first-turn parse.
**Warning signs:** "works against OpenAI, empty response against LM Studio/custom" bug reports.

### Pitfall 4: Endpoint key leaking to Tavily/fetched hosts
**What goes wrong:** Reusing the endpoint's OkHttp client (with its `Authorization` interceptor) for tool execution sends the user's endpoint key to `api.tavily.com` or an arbitrary page.
**Why it happens:** Convenience — the provider already has an authenticated client in scope.
**How to avoid:** Executors are the Phase 55/52 singletons ONLY (`TavilySearchRepository` with its dedicated client + per-call Tavily Bearer; keyless `WebPageFetcher`). Grep gate: remote-loop files must not reference endpoint `apiKey`; test with distinct fake keys asserting Tavily/fetch requests carry only the Tavily key (or none).
**Warning signs:** `chain.request()` interceptors added in loop code; tool execution taking an `OkHttpClient` parameter.

### Pitfall 5: Credit stacking (VM pre-search + remote loop)
**What goes wrong:** VM runs its Tavily pre-search AND the remote loop burns up to 5 more credits — same 1+5 stacking bug fixed for local in 56-02.
**Why it happens:** The 56-02 `loopArmed` skip covers the LiteRT-LM predicate only.
**How to avoid:** Extend the VM skip to remote turns where the matrix mode != plain (grounding ON + tools attempted + online). Same predicate-sharing shape: provider owns the decision, VM mirrors it for pre-search skip. Worst case stays 5 credits/message.
**Warning signs:** Pre-search results present in turns that also show `Searching…` rows.

### Pitfall 6: Ollama envelope confusion (native vs compat)
**What goes wrong:** Sending Chat Completions `tools[]` to Ollama's native `/api/chat` (which expects the same JSON shape actually — `tools:[{type:function,function:{…}}]` — but `tool_calls` in message and `tool_name` on tool role, NOT `tool_call_id`) [CITED: docs.ollama.com tool-calling] causes silent non-calling or 400s.
**Why it happens:** The two envelopes look almost identical but role/result keys differ (`tool_call_id` vs `tool_name`).
**How to avoid:** Planner picks ONE Ollama route (recommended: compat `/v1` reusing the OpenAI dialect verbatim). If native is chosen, the dialect adapter must translate result keys. Either way, attempt-then-fallback absorbs a wrong pick gracefully.
**Warning signs:** Ollama never tool-calls while OpenAI does with identical schemas.

## Code Examples

### OpenAI-dialect tool_calls delta (shape the parser must accept)
```kotlin
// Shape per Chat Completions streaming convention [ASSUMED — see A1]:
// data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_abc","type":"function",
//   "function":{"name":"web_search","arguments":""}}]},"finish_reason":null}]}
// data: {"choices":[{"delta":{"tool_calls":[{"index":0,
//   "function":{"arguments":"{\"query\":\"latest"}}]},"finish_reason":null}]}
// data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}
// data: [DONE]
// Rule: accumulate arguments per index; finish_reason "tool_calls" (or [DONE] after
// partial tool_calls) completes; content deltas and tool_calls deltas may interleave.
```

### Round driver skeleton (mirrors LiteRTLmProvider.runToolLoop)
```kotlin
// Source pattern: 56-02-SUMMARY (LiteRTLmProvider.runToolLoop) adapted to HTTP rounds
var callsUsed = 0
var tools: List<OpenAiTool>? = buildTools() // null after fallback
var roundMessages = history.toMutableList()
var toolsRejectedFallbackDone = false
while (true) {
    coroutineContext.ensureActive() // Stop-cancels-all; CE always rethrown
    val (text, toolCalls) = postStreamingRound(roundMessages, tools) // suspend, cancellable Call
    toolCalls.takeIf { it.isNotEmpty() } ?: return emitTextAndDone(text)
    if (LocalToolLoop.isCapReached(callsUsed)) {
        roundMessages += toolResultEcho(toolCalls, LocalToolLoop.CAP_REACHED_STRING)
        tools = null // final answer round needs no tools[]
        continue
    }
    for (call in toolCalls) {
        coroutineContext.ensureActive()
        emit(ToolStatus(LocalToolLoop.statusDisplay(call.name, call.args) ?: call.name))
        try {
            val result = executeRemoteTool(call) // validateArgs → online gate → repo → map
            emit(ToolCompleted(call.id, summarizeForTranscript(call, result)))
            roundMessages += echo(call, result)
        } finally { emit(ToolStatus(null)) }
        callsUsed++ // counts CALLS, not rounds (locked credit bound)
    }
}
```

### Rejection classifier (pure, JVM-tested)
```kotlin
// 400 + body naming the feature = tools unsupported → one retry without tools[] + notice.
// Anything else → existing error path. Case-insensitive; null-body safe; never throws.
fun isToolsRejection(httpCode: Int, errorBody: String?): Boolean =
    httpCode == 400 && errorBody?.let {
        val b = it.lowercase()
        "tool" in b || "function" in b
    } == true
// Notice copy (draft): "This endpoint doesn't support tool calling. Model-only answer —
// no web sources this turn."
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Phase 49 DEL-01: TOOL rows replay as plain user text on all remote providers | Native `role:tool` + assistant `tool_calls` echo inside the loop (transient only) | This phase | Remote models get real tool context instead of flattened summaries; DEL-01 replay paths for *persisted* rows stay untouched |
| `OpenAiStreamDelta` = content/reasoning only | `+ tool_calls` deltas, accumulator to complete calls | This phase | Additive under `ignoreUnknownKeys`; text-only turns byte-identical |
| LiteRT-LM `@Tool` schemas (local only) | Same descriptions as OpenAI `tools[]` entries | This phase | One schema source of truth (`WEB_*_DESCRIPTION` constants) across both loops |
| OpenAI platform pushing Responses API for tools | Chat Completions `tools[]` retained for third-party compat | Ongoing [CITED: platform.openai.com function-calling guide] | Stay on Chat Completions — Responses statefulness is unsupported by Ollama and absent on compat servers |

**Deprecated/outdated:**
- `tool_choice` on Ollama compat endpoint: listed as unsupported field [CITED: docs.ollama.com/api/openai-compatibility] — never send `tool_choice`; rely on default auto behavior.
- Ollama older docs "Tools (streaming support coming soon)": current docs list Tools ✓ under supported features — treat streaming tool_calls as supported but verify on device (matrix fallback covers it).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Chat Completions streams `delta.tool_calls[{index,id,function:{name,arguments-fragment}}]` with `finish_reason:"tool_calls"` | Patterns 2, Examples | Parser misses calls on some servers → loop never fires there; mitigated by defensive accumulator + fallback + device verification per provider |
| A2 | LM Studio's OpenAI-compat `/v1/chat/completions` accepts `tools[]` model-dependently | Matrix (LM_STUDIO row) | Wrong → every LM Studio turn burns one 400 + retry (latency, no data loss); fallback makes it graceful; device check confirms |
| A3 | Strict-server pairing rule (assistant echo required, exact ids) holds across compat servers | Pattern 3, Pitfall 2 | A lenient server may not need echoes (harmless to send); a stricter one may need more (retry covers) |
| A4 | ` AnthropicProvider` message shape can carry `tool_use`/`tool_result` content blocks (currently `AnthropicMessage{role,content:String}`) — needs block-list content upgrade | Pattern 4 | If the API interface is string-content-only, the Anthropic dialect needs a parallel request DTO; planner sizes this |
| A5 | Non-streaming `message.tool_calls` shape mirrors streaming (complete object, arguments as string) | Pitfall 3 | Parser adjustment after first device observation; covered by extending `OpenAiNonStreamingMessage` |

## Open Questions

1. **Ollama route: compat `/v1` vs native `/api/chat`?**
   - What we know: Both support tools; compat reuses the OpenAI dialect verbatim; current `OllamaProvider` speaks native `/api/chat` with `asOllamaFlow` (newline-delimited JSON, no SSE framing).
   - What's unclear: Which transport the planner prefers; native streaming tool_calls accumulation differs (whole `tool_calls` objects per chunk per Ollama docs, not fragments).
   - Recommendation: Compat `/v1` route (one dialect, one accumulator). Fallback absorbs failure either way.

2. **Where does the retry notice render?**
   - What we know: Must be visible, English, actionable; transient rows disappear by rule.
   - What's unclear: Banner vs model-only chip vs message-attached note (UI-SPEC territory).
   - Recommendation: Reuse the grounding fallback banner pattern (Phase 55 copy twins precedent); planner + UI review decide exact slot.

3. **Remote arming vs VM pre-search skip (Pitfall 5): exact predicate?**
   - What we know: 56-02 pattern (`isLoopArmed` shared, provider-authoritative, VM mirrors).
   - What's unclear: Remote adds matrix-mode + per-endpoint variance.
   - Recommendation: `isRemoteLoopArmed(groundingOn, matrixAttemptsTools, hasValidatedInternet)` in `RemoteToolLoop`/`ToolCapabilityMatrix`, VM mirrors for Tavily-branch skip only.

## Environment Availability

No new build/toolchain dependencies (Kotlin + OkHttp + kotlinx.serialization already in tree). Per-turn costs are runtime user setup, not build environment:

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Reachable endpoint (OpenAI/Ollama/LM Studio/custom URL) | Tool-attempt rounds | User-configured | — | One-retry-without-tools → plain turn (automatic) |
| Tavily key (Settings, test-connection green) | `web_search` execution | User-configured | — | `MISSING_KEY_STRING` model-only path (existing) |
| Validated internet | Any tool execution | Runtime check | — | `OFFLINE_STRING`, no socket (existing gate) |
| On-device verification per provider | Matrix confidence (esp. LM Studio, Ollama route) | Pending | — | Attempt-then-fallback is the safe default |

**Missing dependencies with no fallback:** None (every failure degrades to a plain or model-only turn by construction).

## Security Domain

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | Yes | Endpoint keys stay on per-provider OkHttp interceptors; Tavily Bearer-per-call on dedicated client; web_fetch keyless. Loop code takes NO key material |
| V3 Session Management | No | Stateless per-turn loop; no session tokens introduced |
| V4 Access Control | Yes | Exact-name dispatch via `LocalToolLoop.mapToolCallName` (fixed 2-tool allowlist, AGENT-04); unknown names never execute |
| V5 Input Validation | Yes | `validateArgs` pre-socket (blank-query short-circuit, http(s)-only URLs); reassembled-JSON parse in try/catch; error-body snippet never executed |
| V6 Cryptography | No | No new crypto; Keystore paths untouched |

### Known Threat Patterns for remote-tool-loop

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Tool-result prompt injection (remote model authors args; pages/Tavily return attacker text) | Tampering | Fused blocks verbatim (already sanitized + budgeted); hijack filter + delimiter escaping + URL allowlist identical to local (locked) |
| Endpoint key exfiltration to third parties | Info disclosure | Dedicated Tavily client + keyless fetcher; grep gate (no `apiKey` in loop files); distinct-fake-keys test |
| Wallet exhaustion (5 Tavily credits/turn + VM pre-search stacking) | Denial | Call-counted cap (`MAX_TOOL_CALLS=5`); VM pre-search skip when remote-armed (Pitfall 5); blank-query short-circuit pre-socket |
| Stop ignored during long SSE/tool execution | Denial | `ensureActive` per round + per call; retained `Call.cancel()`; CE rethrow first (LMStudio precedent) |
| Transcript poisoning (wire shapes persisted) | Tampering | Round echoes in-memory only; persistence via existing `<toolId>\n<summary>` + DEL-01 replay (untouched) |

## Sources

### Primary (HIGH confidence)
- https://platform.openai.com/docs/guides/function-calling — tools[] schema (type/function/name/description/parameters/strict), strict-mode requirements (additionalProperties:false, all-required), 5-step tool loop (request → call → execute → output with call_id → final), streaming argument-delta accumulation pattern (fetched 2026-09-29)
- https://platform.claude.com/docs/en/agents-and-tools/tool-use/overview — Anthropic tools/input_schema, tool_use blocks, tool_result with tool_use_id, stop_reason tool_use, client-vs-server tools (fetched 2026-09-29)
- https://docs.ollama.com/api/openai-compatibility (+ github ollama main mdx, via websearch) — Ollama `/v1/chat/completions`: Tools ✓, streaming ✓, reasoning effort ✓; `tool_choice` ✗; Responses API non-stateful only
- Codebase (read directly): `OpenAIProvider.kt` (manual OkHttp SSE + `isSse` sniff + DEL-01 replay), `AnthropicProvider.kt` (SSE event types, no tool events), `OllamaProvider.kt` (native `/api/chat`), `LMStudioProvider.kt` (cancellable-Call precedent, `cancelChat`, CE-first), `CustomProvider.kt` (Retrofit `asSseFlow`), `StreamChunks.kt` / `OpenAiChatRequest.kt` / `AnthropicDtos.kt` (DTO extension points), `SseParser.kt`, `SseExtensions.kt`, `LocalToolLoop.kt` (full policy), `WebSearchToolSet.kt` / `WebFetchToolSet.kt` (provider-neutral descriptions), `StreamToken.kt` (ToolStatus/ToolCompleted contract), `ProviderRouter.kt` (5 apiTypes), `ProviderType.kt`, `TavilyApi.kt` + `TavilySearchRepository.kt:95` (dedicated client, per-call Bearer), `WebPageFetcher.kt:72,111-112` (keyless, no-auth-to-arbitrary-hosts), 56-01/56-02 SUMMARies (policy core, status copies, VM skip, device checkpoint APPROVED)

### Secondary (MEDIUM confidence)
- https://docs.ollama.com tool-calling guide (via websearch snippet) — native `/api/chat` tools envelope (`tool_calls` in message, `role:tool` + `tool_name`), streaming accumulation guidance
- https://platform.openai.com/docs/api-reference/chat/streaming (fetch returned API overview, not the streaming-tool_calls schema — Chat Completions fragment shapes remain training-knowledge, hence A1 [ASSUMED])

### Tertiary (LOW confidence)
- LM Studio OpenAI-compat tools[] support details (model-dependent, version-dependent) — flagged A2, device verification required
- Exact per-server 400 error-body wording for the rejection classifier — classifier uses broad substring match to absorb variance

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — zero new deps; all reuse targets verified in tree
- Architecture: HIGH — mirrors the device-confirmed 56-02 loop; dialect shapes verified against official docs
- Pitfalls: MEDIUM — transport-level edge cases (fragments, non-streaming servers, envelope confusion) are real but each has a concrete mitigation + test
- Capability matrix: MEDIUM — OpenAI/Custom/Anthropic/Ollama-compat grounded in docs; LM Studio row needs device confirmation (fallback makes it safe)

**Research date:** 2026-09-29
**Valid until:** 30 days (stable domain: Chat Completions tools[] unchanged for ~2 years; re-check if LM Studio ships a tools-related release)
