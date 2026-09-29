# Phase 56: Local Agentic Loop - Research

**Researched:** 2026-09-29
**Domain:** LiteRT-LM function calling (on-device agentic tool loop, web_search/web_fetch)
**Confidence:** HIGH (SDK verified from 0.17.1 AAR bytecode + in-repo Phase 47 precedent)

## Summary

Phase 56 arms the local LiteRT-LM chat path with exactly two tools — `web_search` (Phase 55 `TavilySearchRepository.search()`) and `web_fetch` (`MultiUrlFetcher.fetchAll`) — executed in an app-driven multi-turn loop capped at 5 tool calls. The LiteRT-LM 0.17.1 SDK (verified by `javap` against the Gradle-cached `litertlm-android-0.17.1-api.jar`) exposes both loop mechanics: `ConversationConfig(tools, automaticToolCalling)` for SDK-driven execution and `Message.getToolCalls()` for app-driven iteration.

**Primary recommendation:** Use the **app-driven manual loop (`automaticToolCalling = false`)**, not SDK automatic execution. The phase's three hard requirements — step cap with graceful continuation, Stop-cancels-everything including in-flight tool network calls, and offline/key gates with zero socket and zero credit burn — are all trivially satisfied with structured concurrency in Kotlin suspend-land, and awkward or impossible inside SDK automatic mode (whose `ReflectionTool.execute(JsonObject): Any` is synchronous, so network tools would need `runBlocking` on engine threads with no coroutine-cancellation propagation for Stop). Phase 47 already proved the SDK surface works on-device with 0.17.1; Phase 56 reuses the schema/tool-history half of that pattern (`tool(ToolSet)`, `Message.tool(Contents)`, `ToolEventSink` → `StreamToken.ToolStatus`) while moving execution into the provider's suspend loop. The deleted Phase 47 implementation is recoverable from git history (commit `8f9b9640`) as a schema/wiring reference — not to be resurrected wholesale.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Every tool call renders a transient status row (search: query text; fetch: URL) reusing the ToolStatus UX pattern; rows never persist to history
- Stop cancels the entire loop (in-flight tool + generation); new send pre-cancels like the fetch path
- Completion flows into the normal grounded answer path (citations, Fuentes, rows)
- Max 5 tool rounds per message; on reaching the cap the model answers with what was gathered (no hard error)
- Tool failure feeds a concise English error to the model (it continues); failures never crash the turn
- Offline/key-missing at tool time → same actionable messages as Phase 55 (no socket, no credit burn)
- Exactly `web_search` (Tavily producer) + `web_fetch` (existing fetchAll); no file/system/shell tools; no local-context leaks to tools
- Thinking-channel content filtered from KV cache if the SDK exposes it; otherwise thinking tokens route to the existing Thinking panel (never interleaved into answer Deltas)
- Automatic vs manual loop mechanics: planner's call per Kotlin SDK capabilities (automaticToolCalling if sound, else app-driven iteration) — same observable contract either way

### the agent's Discretion
- Tool JSON schemas/annotations (@Tool/@ToolParam shapes), ConversationConfig wiring point, status-row copy details
- Step-cap counting unit (tool calls vs rounds) — user-observable behavior identical

### Deferred Ideas (OUT OF SCOPE)
- Remote tools[] loop (Phase 57), OG thumbnails (Phase 58)
- Non-web tools (globally out of scope), ThinkingConfig engine enablement beyond channel hygiene
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| AGENT-01 | Local model invokes `web_search`/`web_fetch` autonomously via LiteRT-LM function calling (multi-turn loop, step cap, Stop cancels, thinking never leaks) | Manual loop design + SDK verification + Phase 47 precedent (see Architecture Patterns) |
| AGENT-02 | Tool outputs sanitized before reaching the model (URL allowlist, hijack filter, delimiter escaping — same trust boundary as fetched pages) | Trust mapping: reuse fused-block/sanitizer pipeline as tool-result strings (see Security Domain) |
| AGENT-04 | Only web tools exposed (fixed allowlist, no file/system/shell; no local-only context to tools) | Fixed two-ToolSet surface, arg-only tool inputs, provider-neutral signatures for Phase 57 |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Tool loop orchestration (rounds, cap, Stop) | API / Backend (app data layer: `LiteRTLmProvider`) | — | Structured concurrency + cancellation live here; engine threads cannot be cancelled |
| Tool execution (search/fetch network I/O) | API / Backend (repositories on `Dispatchers.IO`) | — | Existing `TavilySearchRepository`/`MultiUrlFetcher` already IO-bound and cancellation-safe |
| Tool schema declaration (`@Tool` ToolSets) | API / Backend (data layer) | — | SDK reflection reads them at conversation creation |
| Status rows / Thinking panel / citations | Browser / Client (Compose UI: `ChatViewModel` + chat screen) | — | Transient UI state only; rows never persist |
| Trust boundary (sanitize-before-model) | API / Backend (`WebContextSanitizer`, fused pipeline) | — | Must sit between tool output and model input, same as Phase 52/55 |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `com.google.ai.edge.litertlm:litertlm-android` | 0.17.1 (pinned, `gradle/libs.versions.toml`) | `ConversationConfig(tools, automaticToolCalling)`, `tool(ToolSet)`, `Message.tool()`, `Content.ToolResponse`, `ExperimentalFlags` | Only on-device function-calling runtime; already integrated, R8-kept, release-smoked [VERIFIED: AAR bytecode via javap] |
| `TavilySearchRepository.search()` | in-repo (Phase 55) | `web_search` implementation; returns `TavilySearchOutcome` union | Producer already fuses through sanitizer + budget + `buildFusedBlock` [VERIFIED: codebase] |
| `MultiUrlFetcher.fetchAll()` | in-repo (Phase 52) | `web_fetch` implementation; returns `MultiUrlResult` | Partial grounding, OFFLINE collapse, per-page budget [VERIFIED: codebase] |
| `WebContextSanitizer.sanitize()` | in-repo | Markdown-link neutralization, delimiter escaping | Trust boundary both tools already pass through [VERIFIED: codebase] |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `Capabilities.supportsFunctionCalling()` | SDK 0.17.1 | Runtime per-model function-calling probe | Capability gate alternative/complement to allowlist flags (see Pitfall 1) |
| `ExperimentalFlags.filterChannelContentFromKvCache` | SDK 0.17.1 (nullable Boolean, `@OptIn ExperimentalApi`) | Exclude thinking-channel content from KV cache | Set once at engine init — CONTEXT's "if the SDK exposes it" is YES [VERIFIED: AAR bytecode] |
| `StreamToken.ToolStatus/ToolCompleted` | in-repo (`domain/model/StreamToken.kt`) | Status-row signal + persistable record | Reuse verbatim; VM currently maps both to `Unit` (`ChatViewModel.kt:713-714`) — the wire-up point |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Manual loop (`automaticToolCalling=false`) | SDK automatic (`automaticToolCalling=true`) | Automatic is less code and Phase-47-proven, but cap must be hacked into sync `@Tool` bodies via shared counter, and Stop cannot cancel a `runBlocking` network call on an engine thread (coroutine cancellation does not propagate; worst-case lag = OkHttp timeouts). Manual gives `for (round in 1..5)` + `ensureActive()` + native suspend cancellation. **Manual wins — the phase's hard requirements are exactly what automatic mode handles worst.** |
| Fresh ToolSets per conversation | Singleton ToolSets | 47 precedent: fresh instances per conversation creation (event-sink residue); conversation reuse means config applies at creation only |

**Installation:**
```bash
# Zero new dependencies (locked). No installs.
```

**Version verification:** `litertlm = "0.17.1"` pinned in `gradle/libs.versions.toml`; AAR present in Gradle cache and inspected via `javap` this session. No registry lookup needed — no new packages.

## Package Legitimacy Audit

No external packages installed in this phase (zero-new-deps lock from CONTEXT + AGENTS.md conventions). Audit not applicable — nothing to verify.

## Architecture Patterns

### System Architecture Diagram

```
User message
    │
    ▼
ChatViewModel.sendMessage()
  ├─ GroundingPrecedence.shouldGround? ──No──▶ plain local turn (unchanged)
  ├─ fetcher.hasValidatedInternet? ──No──▶ loop ARMED-OFF: tools attached but
  │                                          offline short-circuit inside loop
  ▼                                          (same OFFLINE copy as Phase 55)
LiteRTLmProvider.chat()  [loop armed ⟺ LITE_RT_LM + doGround + capable model]
    │
    ├─ ConversationConfig(tools=[web_search, web_fetch], automaticToolCalling=false)
    │   (fresh ToolSets per creation; long-lived conversation reuse unchanged)
    │
    ▼
┌─ Manual round loop (max 5 TOOL CALLS) ──────────────────────┐
│  sendMessageAsync(Contents) ──▶ collect Message deltas       │
│       ├─ text deltas ──▶ StreamToken.Delta (answer)          │
│       ├─ channels["thought"] ──▶ Thinking panel (never Delta)│
│       └─ message.toolCalls ──▶ for each ToolCall:            │
│            ├─ emit ToolStatus("web_search: <query>" /         │
│            │            "web_fetch: <url>")                   │
│            ├─ gate: offline/key? ──▶ concise English string  │
│            │                       (no socket, no credit)     │
│            ├─ execute suspend tool ──▶ sanitized result string│
│            ├─ emit ToolStatus(null) [clear]                   │
│            └─ feed Message.tool(ToolResponse) back            │
│  cap reached ──▶ "budget exhausted, answer with gathered     │
│                  context" string as final tool result         │
└──────────────────────────────────────────────────────────────┘
    │
    ▼
Final answer ──▶ normal grounded path (citations, Fuentes, rows)
    │                (tool-result strings ARE fused Source[N] blocks,
    │                 so downstream persist/citation code is untouched)
    ▼
StreamToken.Done()
```

Step-cap counting unit (agent's discretion): **count tool CALLS, not rounds** (recommendation). Each `web_search` call burns exactly 1 Tavily credit, so call-counting bounds worst-case credits per message 1:1 (5 calls = ≤5 credits). User-observable behavior is identical to round-counting for the common single-call-per-round case; multi-call rounds (parallel `ToolCall`s in one `Message`) are bounded rather than multiplied.

### Recommended Project Structure

```
app/src/main/java/com/warped/data/agentic/      # NEW package (or data/skills/ successor)
├── WebSearchToolSet.kt      # @Tool web_search schema; body = thin sync adapter (see Pitfall 3)
├── WebFetchToolSet.kt       # @Tool web_fetch schema; body = thin sync adapter
├── LocalToolLoop.kt         # Pure loop policy: cap counting, result-string mapping,
│                            #   offline/key short-circuits — JVM-testable, no engine types
└── AgenticGates.kt          # doGround + validated-internet + capability gate composition
    (alternatively fold gates into LocalToolLoop; planner's call)
```

Provider wiring stays in `LiteRTLmProvider` (Step 6 config + round loop in `sendContentsWithRetry` or a sibling helper); status forwarding reuses the 47 `channelFlow` + `MutableStateFlow<String?>` sink pattern from git history.

### Pattern 1: Manual tool round (SDK-verified shapes)
**What:** `automaticToolCalling=false`; after each `sendMessageAsync` collection, read `Message.getToolCalls(): List<ToolCall>`; dispatch by `ToolCall.getName()` with `getArguments(): Map<String, Any?>`; feed results back as `Message.tool(Contents.of(Content.ToolResponse(name, response)))`.
**When to use:** Every agentic turn. This is the entire AGENT-01 loop.
**Example:**
```kotlin
// Shapes VERIFIED via javap on litertlm-android-0.17.1-api.jar this session:
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.ToolCall
import com.google.ai.edge.litertlm.tool  // ToolKt.tool(ToolSet): ToolProvider

val config = ConversationConfig(
    initialMessages = historyMessages,
    samplerConfig = samplerConfig,
    extraContext = emptyMap(),
    tools = listOf(tool(WebSearchToolSet(...)), tool(WebFetchToolSet(...))),
    automaticToolCalling = false,   // engine returns ToolCalls; app executes
)
// Per round: collect final Message, inspect message.toolCalls,
// then: Message.tool(Contents.of(Content.ToolResponse(call.name, resultString)))
```
**Tool schema shape (47 precedent, recoverable from `git show 8f9b9640:.../CalculatorSkill.kt`):**
```kotlin
class WebSearchToolSet(...) : ToolSet {
    @Tool(description = "...what it does, when the model should call it...")
    fun web_search(
        @ToolParam(description = "...") query: String,
    ): String = "<never executed in manual mode; schema only>"
}
```
Note: `@Tool`/`@ToolParam` each expose exactly one `description()` element [VERIFIED: AAR bytecode]. Param names map camel→snake unless `ExperimentalFlags.convertCamelToSnakeCaseInToolDescription` is touched — use single-word lowercase names (`query`, `url`) to dodge the mapping entirely. Keep params to `String`/`Int`/`Double`/`Boolean` primitives (0.17.1 fixed int-type tool-call parsing per 45-02 notes; avoid nullable/optional params — 47-REVIEW showed `null` optionals fail closed unexpectedly).

### Pattern 2: Tool-result-as-fused-block (trust + downstream reuse)
**What:** Tool bodies/executors return the *already-sanitized, already-budgeted* fused `Source [N]` block text, not raw JSON. `web_search` returns `TavilySearchOutcome.Grounded.fused.block` (or the concise English mapping for `ModelOnly`/`MissingKey`/`InvalidKey`/`UsageLimit`); `web_fetch` returns the sanitized page text in the same `Source [N]` shape.
**When to use:** Always — this is what makes "completion flows into the normal grounded answer path" a no-op: the final answer's citations/Fuentes/rows pipeline already knows this shape, and AGENT-02's sanitize-before-model falls out for free because both producers sanitize before fusion.
**Why not raw JSON:** raw snippets/pages would need a second sanitize pass at the loop boundary and would not be citation-compatible.

### Pattern 3: Status sink (47-proven, resurrect)
**What:** `MutableStateFlow<String?>` in the provider; tool executor posts display string on start (`"web_search: <query>"`, `"web_fetch: <url>"`), `null` on finish; `chat()` wraps body in `channelFlow` merging sink → `StreamToken.ToolStatus`. VM maps non-null → transient status row, null → clear, `Done` → clear.
**When to use:** Every tool call. Engine emits no status events in manual mode, so the sink is the only signal (same reason as 47 automatic mode).
**Precedent:** exact code in `git show 8f9b9640 -- LiteRTLmProvider.kt` (channelFlow merge) and `ChatViewModel.kt` (`toolCallActive` update). Status rows never persist (CONTEXT-locked): VM must not write them to `ChatMessage` history or Room.

### Pattern 4: Channel hygiene (two layers)
**What:** (1) Set `ExperimentalFlags.filterChannelContentFromKvCache = true` once (opt-in `ExperimentalApi`, precedent in `LiteRTLmEngine.init` for speculative decoding). (2) Per-message, read `message.channels["thought"]` via the existing `extractThoughtContent()` and route to the Thinking panel; only `Content.Text` joins answer Deltas (current `extractTextContent` already filters `isInstance<Content.Text>` — keep that exact filter; `ToolResponse` contents must never reach `Delta`).
**When to use:** Always on the local path. CONTEXT's "if the SDK exposes it" resolves to YES for layer 1 [VERIFIED: `getFilterChannelContentFromKvCache`/`setFilterChannelContentFromKvCache` exist in AAR bytecode].

### Anti-Patterns to Avoid
- **`runBlocking` network I/O inside `@Tool` bodies:** engine-thread blocking with no cancellation propagation; Stop lags to OkHttp timeouts; JNI-throw kills the conversation (47-REVIEW StackOverflowError lesson). Manual loop keeps tools as suspend functions — never do this.
- **Reusing ToolSet instances across conversations:** event-sink/status residue; 47 precedent is fresh-instances-per-creation. Config applies at creation only — toggles/gates must `resetConversation()` (47 `setSkillEnabled` precedent).
- **Persisting status rows or tool-result strings as chat history:** rows are transient; tool results live in conversation context only. Legacy `Role.TOOL` rows stay read-only replay (`Message.model`, current behavior) — do not switch replay to `Message.tool` (would re-trigger loops on history re-send; 47 used `Message.tool` resume only while the loop was live).
- **Feeding raw tool JSON to the model:** bypasses AGENT-02 boundary and breaks citations. Always fused-block strings.
- **Counting rounds instead of calls for the cap:** parallel multi-call messages multiply Tavily credit burn under round-counting. Count calls.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Function-call schema/reflection | Custom JSON-schema builder or prompt-injection tool descriptions | `@Tool`/`@ToolParam` + `tool(ToolSet)` SDK reflection | Engine parses args into `ToolCall(name, arguments)`; R8 keeps already cover `ToolSet`/`ReflectionTool`/`ToolKt`/`*Annotation*` (`proguard-rules.pro:26-35`) |
| Tool-result message encoding | Custom resume format | `Content.ToolResponse(name, response)` + `Message.tool(Contents)` | Engine-native round-trip; 47-verified shape |
| Thinking-channel filtering | Regex stripping of think blocks on local path | `ExperimentalFlags.filterChannelContentFromKvCache` + `channels["thought"]` routing | SDK-level KV hygiene + existing `extractThoughtContent()`; (the `parseThinkBlocks` regex path in VM stays for remote models) |
| Retry/backoff for tool network calls | Custom retry in tool bodies | Single attempt; failure → concise English string → model continues | CONTEXT-locked: failures never crash the turn, no retry loop inside the cap budget |
| Credit accounting service | Server-side usage tracker | Call-count cap (5) × basic-depth default (1 credit/search, Phase 55) | Worst case 5 credits/message, no infrastructure |

**Key insight:** The loop's hardest problems (cancellation, gating, trust) are already solved by adjacent in-repo systems (`generationJob` cancel tree, `hasValidatedInternet`, `WebContextSanitizer` + fused pipeline). The phase is composition, not invention — the only new logic is the round driver + result-string mapping, both JVM-testable pure Kotlin.

## Common Pitfalls

### Pitfall 1: No allowlisted model advertises function calling
**What goes wrong:** Loop is armed but `ToolGating`-style capability check gates everything CLOSED — feature silently dead.
**Why it happens:** All 4 `model_allowlist.json` entries have `supportsFunctionCalling: false` [VERIFIED: codebase this session]. The 47 "verified-only" rule forbids flipping flags without on-device evidence.
**How to avoid:** Planner must include a capability-verification task: on-device, probe `Capabilities(modelPath).supportsFunctionCalling()` (SDK 0.17.1 class [VERIFIED: AAR bytecode]) and/or run a 2-tool smoke turn per allowlisted model; flip flags only for models that demonstrably emit `ToolCall`s. Fallback if no model supports it: prompt-injection fallback (47 `fallbackSystemPrompt` precedent) — but that changes the phase contract, so surface as a plan-checker flag, not a silent downgrade.
**Warning signs:** `message.toolCalls` always empty on-device; loop never fires.

### Pitfall 2: Long-lived conversation pins stale tool config
**What goes wrong:** Grounding toggled on/off or gates change mid-session, but `activeConversation` reuse keeps the creation-time `tools[]` — tools fire when grounding is off, or stay absent when on.
**Why it happens:** `ConversationConfig.tools` applies only at creation (47 Pitfall 1, documented in code comments).
**How to avoid:** Reset the conversation (`resetConversation()`) whenever loop-arming inputs change (grounding toggle, per-chat web override, model switch — model switch already resets via engine reload). Compute the armed decision per `sendMessage`, and if it differs from the decision baked into `activeConversationConfig`, reset before creating. Store the armed-input snapshot alongside `activeConversationConfig`.
**Warning signs:** Tool calls with grounding toggle off; stale `toolsDegraded`-style wedges.

### Pitfall 3: `@Tool` bodies must be sync — don't put suspend work in them
**What goes wrong:** Attempting `suspend fun` in a `ToolSet` fails (reflection `execute(JsonObject): Any` is non-suspend [VERIFIED]) or `runBlocking` deadlocks/stalls engine threads.
**Why it happens:** Manual loop still needs `ToolSet` classes for *schemas*; a developer may wire real logic into the bodies out of habit.
**How to avoid:** Bodies are schema-only stubs returning a constant ("executed by host loop") or throw `ToolException` if ever invoked; all real execution lives in suspend executor functions called by the provider loop. Add a JVM reflection test asserting both ToolSets expose exactly `web_search(query: String)` / `web_fetch(url: String)` with descriptions (47 "no-drift" precedent).
**Warning signs:** `runBlocking` anywhere under `data/agentic/`; JNI-thread network in profiler.

### Pitfall 4: Stop during in-flight tool network call
**What goes wrong:** `cancelProcess()` halts native generation but the suspend Retrofit/OkHttp call continues until timeouts; user perceives Stop as laggy.
**Why it happens:** Tool network calls are independent sockets, not engine work.
**How to avoid:** Manual loop fixes 90% of this: `generationJob.cancel()` cancels the collecting coroutine, and `TavilySearchRepository.search`/`fetchAll` propagate `CancellationException` (both rethrow it [VERIFIED]). Additionally cancel any tracked fetch `Call` in `stopGeneration()` (the `fetcher.cancel()` precedent at `ChatViewModel.kt:342,978` — extend the same position to the tool path). Tavily client timeouts (15s/30s) bound the residual worst case; do NOT add a logging interceptor to the zero-interceptor Tavily client to track calls (T-55-01) — coroutine cancellation suffices.
**Warning signs:** Stop latency >1s on-device during tool turns.

### Pitfall 5: Model never calls tools / calls with garbage args
**What goes wrong:** Small on-device models ignore tool descriptions or emit malformed args (empty query, non-URL string), burning rounds or stalling.
**Why it happens:** Weak instruction-following; blank-query would burn a Tavily credit.
**How to avoid:** (a) Short tool-use system hint when armed (provider-neutral wording — Phase 57 reuses it). (b) Validate args before execution: blank query → return the blank short-circuit string without socket (Phase 55 already short-circuits blank queries pre-socket — reuse by calling `search()`, which handles it); malformed URL → concise English error string, counts against the cap (prevents infinite garbage loops). (c) Cap-reached message instructs answering with gathered context (CONTEXT-locked graceful continuation).
**Warning signs:** Turns that burn 5 calls with zero citations; Tavily dashboard credit drain.

### Pitfall 6: Thought-channel text interleaved into answer
**What goes wrong:** Reasoning tokens stream into the answer bubble or into tool-result context.
**Why it happens:** Collecting `message.contents` wholesale instead of filtering `Content.Text`, or ignoring `channels` map.
**How to avoid:** Keep the `filterIsInstance<Content.Text>()` filter in `extractTextContent` (it already excludes `ToolResponse`); route `channels["thought"]` exclusively to the Thinking panel; set the KV-cache filter flag. Add a JVM test with a fabricated `Message` carrying both text + thought channel asserting Deltas contain only text.
**Warning signs:** "Pensando…" content inside answer bubbles; reasoning text inside citations.

## Code Examples

### ToolSet schemas (provider-neutral for Phase 57 reuse)
```kotlin
// @Tool/@ToolParam single-description() shapes VERIFIED via javap, 0.17.1 AAR.
class WebSearchToolSet : ToolSet {
    @Tool(description = "Search the web for current or external facts. Call when the user's question needs information beyond the model's knowledge. Returns numbered sources.")
    fun web_search(
        @ToolParam(description = "The search query. Be specific; include key entities.") query: String,
    ): String = HOST_EXECUTED  // schema only — manual loop executes; never called by engine
}

class WebFetchToolSet : ToolSet {
    @Tool(description = "Fetch a web page's readable text. Call with a full https URL from search results or the user. Returns the page content as a numbered source.")
    fun web_fetch(
        @ToolParam(description = "The full page URL, starting with http:// or https://.") url: String,
    ): String = HOST_EXECUTED
}
// Keep signatures provider-neutral: Phase 57 maps these 1:1 to OpenAI tools[]
// (name, description, {query: string} / {url: string} JSON schema).
```

### Round-loop skeleton (provider)
```kotlin
// Manual loop: automaticToolCalling=false; app executes; suspend throughout.
var toolCallsUsed = 0
var pendingToolMsg: Message? = null
while (toolCallsUsed < MAX_TOOL_CALLS) {   // 5; counts CALLS (see Architecture)
    coroutineScope { ensureActive() }       // Stop checkpoint per round (46-01 pattern)
    val finalMsg = collectTurn(pendingToolMsg)  // sendMessageAsync collect
    val calls = finalMsg.toolCalls
    if (calls.isEmpty()) { emitAnswerDeltas(); break }
    val results = calls.map { call ->
        if (toolCallsUsed >= MAX_TOOL_CALLS) return@map CAP_REACHED_STRING
        toolCallsUsed++
        sink.onStart(displayFor(call))      // "web_search: <query>" / "web_fetch: <url>"
        try { executeTool(call) }           // suspend: gates → repo → fused-block string
        finally { sink.onFinish(displayFor(call)) }
    }
    pendingToolMsg = Message.tool(Contents.of(results.mapIndexed { i, r ->
        Content.ToolResponse(calls[i].name, r)
    }))
}
// Tool failure → "Error: <concise English reason>" string (never throws —
// @Tool never-throw lesson from 47 applies to executors too).
```

### Gate composition (per tool call AND per turn)
```kotlin
// Order matters: cheapest, no-side-effect checks first.
fun webSearchResult(query: String): String {
    if (!groundingArmed) return "Web search is disabled for this chat."
    if (!hasValidatedInternet()) return OFFLINE_STRING      // no socket (Phase 55 copy)
    return when (val out = tavily.search(query, maxResults = 5)) {
        is TavilySearchOutcome.Grounded -> out.fused.block  // sanitized + cited
        is TavilySearchOutcome.ModelOnly -> "Search returned no usable results. Answer from model knowledge."
        TavilySearchOutcome.MissingKey -> MISSING_KEY_STRING // actionable Phase-55 copy
        TavilySearchOutcome.InvalidKey -> INVALID_KEY_STRING
        TavilySearchOutcome.UsageLimit -> USAGE_LIMIT_STRING
    }
}
// fetchAll: same gate shape; OFFLINE collapse + per-page budget already inside.
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Phase 47 skills `@Tool`s (Calculator/Time/JSON, chip-gated, automatic mode) | Deleted (Phase 49 DEL-01); Phase 56 rebuilds web-only tools, grounding-gated, manual loop | 2026-09-27/28 | Schema/wiring precedent reusable from git history; chip/gating/skill-repo surface must NOT be resurrected |
| Heuristic pasted-URL grounding only | Model-initiated `web_search`/`web_fetch` via function calling | This phase (v2.4) | Model decides when it needs the web; pasted-URL path stays as fallback (unchanged) |
| `parseThinkBlocks` regex for all thinking | SDK channel API (`channels["thought"]`) + KV-cache filter on local path | 0.17.1 SDK (verified) | No regex fragility for local reasoning; regex stays for remote models |

**Deprecated/outdated:**
- `ReflectionTool`-executed network bodies: never built (47 tools were pure CPU); do not introduce now — manual loop supersedes.
- Prompt-injection tool descriptions as primary mechanism: fallback-only (no-support models), never the armed path.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | With `automaticToolCalling=false`, `sendMessageAsync` surfaces model-requested calls in `Message.getToolCalls()` (standard SDK manual-tool-calling flow) | Architecture Patterns | HIGH — if the engine instead errors or never emits ToolCalls with automatic=false, the loop is dead; mitigate with an early on-device smoke task before building the full loop |
| A2 | At least one allowlisted model emits function calls on 0.17.1 (flag flips justified) | Pitfall 1 | HIGH — if none do, phase needs rescoping (fallback path); capability-verification task must run first |
| A3 | `Message.tool(Contents)` + `Content.ToolResponse` round-trips through `sendMessageAsync` without a wedge (Qwen-family template wedge from 47 T-47-09 does not recur for Gemma) | Architecture Patterns | MEDIUM — wedge-degrade precedent exists if observed; add wedge detection to loop error mapping |
| A4 | `ExperimentalFlags.filterChannelContentFromKvCache=true` is safe to set globally (nullable Boolean observed; semantics inferred from name + CONTEXT) | Pattern 4 | LOW — worst case it is a no-op; verify no init regression in engine tests |

## Open Questions

1. **Does `sendMessageAsync` Flow emit per-round final `Message` objects carrying `toolCalls` in manual mode, or must the loop use blocking `sendMessage` per round?**
   - What we know: Flow overloads exist; 47 used automatic mode so the Flow was never inspected for ToolCalls.
   - What's unclear: whether the streaming Flow's terminal `Message` reliably carries `toolCalls` when automatic=false.
   - Recommendation: planner adds a spike task (on-device or instrumented) to confirm; fallback is blocking `sendMessage` per round on `Dispatchers.Default` (loses token-streaming mid-loop but preserves status rows; final answer still streams).

2. **Should the loop-armed system hint live in `ConversationConfig.systemInstruction` or as a leading system `Message`?**
   - What we know: both shapes exist in SDK; 47 used leading-system-message injection for fallback.
   - What's unclear: which survives conversation reuse/prefill better on 0.17.1.
   - Recommendation: planner's call; prefer `systemInstruction` (config-level, survives reuse) with a JVM test pinning the copy.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| LiteRT-LM 0.17.1 AAR | Tool schemas, loop | ✓ (Gradle cache, bytecode-inspected) | 0.17.1 | — |
| Android device w/ allowlisted `.litertlm` | Capability verification, E2E | ✗ (this session) | — | Planner must gate loop-on-device tasks as hardware checkpoints |
| Tavily API key | `web_search` live path | ✗ (user-provided at runtime) | — | `MissingKey` copy path; unit tests use MockK fake (55 precedent) |
| Internet (validated) | Tool execution | ✗ (this session) | — | OFFLINE short-circuit copy; JVM tests cover the branch |

**Missing dependencies with no fallback:**
- On-device function-calling verification (Pitfall 1 / A1 / A2) — planner must schedule hardware tasks; cannot be resolved in JVM tests.

**Missing dependencies with fallback:**
- Tavily key / internet — covered by outcome-union branches + fakes.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|------------------|
| V2 Authentication | Partial | Tavily Bearer-per-call from Keystore alias (Phase 55); key bytes zeroed; never logged |
| V4 Access Control | Yes | Fixed two-tool allowlist; dispatch by exact name match; unknown `ToolCall.name` → error string, never executed (AGENT-04) |
| V5 Input Validation | Yes | Tool-arg validation (blank query short-circuit pre-socket; URL shape check pre-fetch); `WebContextSanitizer` on all tool output; `InputSanitizer` on user text (existing) |
| V6 Cryptography | No new | Keystore handling unchanged from Phase 55 |
| V14 Configuration | Yes | Loop armed ONLY when `GroundingPrecedence.shouldGround` AND validated internet AND capable model; R8 keeps for re-added `ToolSet` classes (`data.agentic.**` keep, mirroring deleted `data.skills.**` keeps) |

### Known Threat Patterns for agentic web loop

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Prompt injection via search snippet / page content (tool output → model context) | Tampering / Elevation | Fused-block pipeline: `WebContextSanitizer` (link neutralization, delimiter escaping) + per-page budget BEFORE model sees text; tool-result strings carry `Source [N]` delimiters so the model can cite but not obey embedded instructions |
| Malicious URL arg (file://, localhost, intranet) | Tampering / Info disclosure | `fetchAll` URL validation/allowlist (existing Phase 52 boundary — verify http(s) + public host before socket); `web_fetch` never fetches non-http(s) |
| Secret exfiltration (chat history / keys as tool args) | Info disclosure | Tool inputs are arg-only (`query`/`url` strings authored by the model, validated); conversation history is never serialized into tool calls; endpoint keys never sent to `api.tavily.com` (T-55-02 holds) |
| Credit burn (model spams `web_search`) | Denial (wallet) | Call-count cap 5 + blank-query pre-socket short-circuit + basic-depth default; worst case 5 credits/message |
| Tool-throw kills conversation (JNI wedge) | Denial | Executors never throw (error-string discipline); manual loop keeps throws in Kotlin where `try/catch` + retry path already exist |
| R8 strips new `@Tool` reflection | Tampering / Denial | Extend `proguard-rules.pro` with `data.agentic.**` keep (47 precedent: `:84-85` skills keeps); release smoke must invoke both tools |

## Project Constraints (from AGENTS.md)

- Kotlin only; Hilt DI (`@Singleton`, modules per feature); coroutines (`Dispatchers.IO` network, `Dispatchers.Default` inference/CPU, never block UI thread)
- Clean architecture (domain/data/ui), MVVM, repository pattern; `StateFlow` UI state
- API keys via Android Keystore (`EncryptedSharedPreferences`/`ApiKeyStore`); no plaintext secrets, no hardcoded keys
- Offline-first: local chat works without internet; tool path degrades to actionable copy, never crash
- Testing: JUnit 5 + MockK + Turbine + Truth; `runTest` for coroutines; Room in-memory for DAO (no Room change here)
- Zero new dependencies (locked by CONTEXT; consistent with v2.2 zero-dep discipline — Jsoup is the only recent addition and is unrelated)
- English UI copy; JVM-testable pure logic separated from engine types (47 `ToolGating` precedent: no engine imports in policy classes)

## Sources

### Primary (HIGH confidence)
- `litertlm-android-0.17.1-api.jar` (`javap`: `ConversationConfig`, `Tool`/`ToolParam`/`ToolSet`/`ToolKt.tool()`, `ToolCall`, `ToolProvider`, `ReflectionTool`/`InternalJsonTool.execute`, `ToolManager`, `Content.ToolResponse`, `Message`+Companion incl. `tool()`, `Channel`, `ThinkingConfig`, `ExperimentalFlags.filterChannelContentFromKvCache`, `Capabilities.supportsFunctionCalling`, `Conversation`) — inspected this session from Gradle cache
- In-repo: `LiteRTLmProvider.kt`, `LiteRTLmEngine.kt`, `EngineManager.kt`, `StreamToken.kt`, `ChatViewModel.kt` (send/grounding/Stop/collector), `TavilySearchRepository.kt`, `MultiUrlFetcher.fetchAll`, `WebContextSanitizer`, `GroundingPrecedence`, `WebPageFetcher.hasValidatedInternet`, `model_allowlist.json`, `proguard-rules.pro`
- Git history: commit `8f9b9640` (47-02 ToolSets + Config wiring + gating + ToolStatus) — full diff read this session

### Secondary (MEDIUM confidence)
- `.planning/milestones/v2.1-phases/47-real-tool-execution/47-VERIFICATION.md`, `47-REVIEW.md` (StackOverflowError throw-out, control-char hygiene, nullable-param fail-closed), `45-02-SUMMARY.md` (0.17.x surface incl. int-arg fix), `55-01-SUMMARY.md` (Tavily producer contract)

### Tertiary (LOW confidence)
- None — all load-bearing claims verified above; residual unknowns captured as A1–A4 with mitigations.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — SDK bytecode + pinned version + in-repo producers all inspected this session
- Architecture (manual-loop recommendation): HIGH — requirements-to-mechanics mapping is direct; Phase 47 precedent + SDK sync-`execute` signature force the conclusion
- Pitfalls: HIGH — Pitfall 1 verified against `model_allowlist.json`; 2–6 grounded in code/history
- Capability-flight risk (A1/A2): explicitly flagged as hardware-gated unknowns, not papered over

**Research date:** 2026-09-29
**Valid until:** 30 days (stable SDK pin; invalidated sooner if allowlist flags or SDK version change)
