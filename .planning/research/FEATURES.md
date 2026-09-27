# Feature Research

**Domain:** On-device LLM tool-calling (Skills) + high-volume chat UI — Warped Android app, v2.1 milestone
**Researched:** 2026-09-27
**Confidence:** HIGH (LiteRT-LM tool API + LM Studio tool docs verified against official sources; Compose lazy-list guidance from official Android docs; Gallery Skills behavior from repo + docs)

## Feature Landscape

### Table Stakes (Users Expect These)

Features users assume exist. Missing these = product feels incomplete.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Real tool execution on local backend (LiteRT-LM `@Tool` registration) | Skills Lite v2.0 ships Tool-category skills that currently only log / inject prompt text; a "Calculator" chip that doesn't calculate is broken UX. LiteRT-LM Kotlin API supports `@Tool`/`@ToolParam` on `ToolSet` + `ConversationConfig(tools=…)` with automatic execution loop (up to 25 recurring calls) | MEDIUM | `tool(SampleToolSet())` reflection generates OpenAPI-style schema. Requires tool-capable model (FunctionGemma family); non-capable models silently ignore tools. Verify LiteRT-LM version in tree supports `ToolProvider`/`ToolSet` before planning |
| Real tool execution on remote backend (LM Studio `tools[]` mapping + multi-turn loop) | `LmStudioChatRequest.tools` DTO exists but is never populated; `applySkills` only injects PromptTemplate text into system prompt. LM Studio `/v1/chat/completions` follows OpenAI function-calling format: send `tools[]`, receive `choices[0].message.tool_calls`, execute locally, re-POST with `role: tool` message, loop until final content | MEDIUM | Streaming variant sends tool calls in chunks (`delta.tool_calls.function.name/arguments`) — accumulator needed. Small/non-tool models emit malformed calls LM Studio can't parse (falls back to `content`); app must handle `finish_reason: tool_calls` AND silent-malformed fallback |
| Tool-call progress UX ("Using calculator…") | Partially present (`ChatUiState.toolCallActive: String?` + "Using X…" row in ChatScreen). Users expect to see which tool runs and its result; silent multi-second stalls read as a hang | LOW | Keep: status row + spinner while tool executes; append tool result as visible follow-up or collapse. Gallery uses a collapsible progress panel (`MessageBodyCollapsableProgressPanel`) — same pattern |
| Skill enable/disable chips (SkillChipsRow) | v2.0 planning ships `SkillPreferences` DataStore (all-on defaults) + chips above input. Users expect per-skill toggles because each registered tool costs context tokens (tool schemas are injected into system prompt / billed as input tokens) | LOW | Keep chips as the tool-selection surface: chip ON = tool registered in `ConversationConfig.tools` / `tools[]`; chip OFF = omitted. Long-press/tooltip showing what the tool does (v2.0 PITFALLS note) |
| Keyed LazyColumn chat list with stable message IDs | Current `ChatScreen` renders messages in a scrolling `Column` (`rememberScrollState` + `animateScrollTo(maxValue)` on every token) — O(n) composition per frame during streaming, no item reuse. Official Android guidance: `items(messages, key = { it.id })` so Compose moves state with the item instead of recomposing the whole list on insert | MEDIUM | Keys must be `Bundle`-saveable (Long/String UUID ok). Room `ChatMessage` needs a stable id — verify `domain/model/ChatMessage` has one; if not, add before PERF-06. Reverse-layout (`reverseLayout = true`) vs normal layout decision affects scroll-anchor behavior (see below) |
| Scroll preservation (don't yank user on new message) | Current code auto-scrolls to bottom on every streaming token and every message insert. Standard chat behavior: stick to bottom only if already at bottom; preserve position when user scrolled up reading history | LOW | Pattern: `rememberLazyListState()` + `derivedStateOf { listState.firstVisibleItemIndex == 0 }` (reverse layout) gating `animateScrollToItem(0)` in `LaunchedEffect(messages.size)`. Without keys, position anchors to index and jumps; keys fix this (StackOverflow + Jetchat issue #696 evidence) |
| Split chat UI state (PERF-01 sub-state split) | Single `ChatUiState` data class (~30 fields: messages, input, streaming, models, endpoints, dialogs…) means every keystroke/streaming token recomposes every collector. Standard fix: split into `MessagesUiState` / `InputUiState` / `ConnectionUiState` (or separate StateFlows) so streaming recomposes only the list | MEDIUM | `ChatScreen` currently does `val uiState by viewModel.uiState.collectAsStateWithLifecycle()` once and reads everything — the exact anti-pattern. `derivedStateOf` for traffic light (PERF-04) is a band-aid; the split is the real fix. Keep one ViewModel, multiple StateFlows |
| Cancellable streaming calls (OkHttp `Call.cancel()` + `Conversation.cancelProcess()`) | Stop button exists (`onStop → viewModel.stopGeneration()`), but v2.1 scope notes true `Call.cancel()` plumbing is missing; per-call Flow with internal drain job risks double-collect. Users expect Stop to halt tokens + tool loop immediately | MEDIUM | Two cancel paths: local `conversation.cancelProcess()`, remote OkHttp `Call.cancel()`. Tool loop must check cancellation between rounds (RECURRING limit 25 could otherwise run away). Depends on runInference `shareIn` refactor (single shared flow, no double-collect) |

### Differentiators (Competitive Advantage)

Features that set the product apart. Not required, but valuable.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Offline tool skills (Calculator, CurrentTime, JsonFormatter, Summarize as real Kotlin functions) | Works airplane-mode; no other mobile LM app does local function-calling well. The 4 hand-curated skills are pure functions — zero permissions, deterministic, testable | LOW | Direct `@Tool` mapping, one Kotlin function each. `calculate` needs a safe expression parser (no `eval`); `getCurrentTime` needs timezone-aware formatting; `JsonFormatter` needs error-tolerant parse with friendly message |
| Unified skill surface across local + remote | Same 4 chips drive both LiteRT-LM `@Tool` registration and LM Studio `tools[]` JSON-schema mapping. LM Studio-grade parity story: "skills work wherever the model runs" | MEDIUM | Requires a single `Skill → (ToolSet | LmStudioTool)` mapper. `LmStudioToolFunction.parameters` is a `JsonObject` — build from the same description metadata as `@ToolParam` |
| Manual tool-calling mode (confirmation before execute) | LiteRT-LM supports `automaticToolCalling = false` → app receives `Message.toolCalls` and executes explicitly. Gallery auto-executes; a confirm step for future side-effecting skills (email, intents) is a trust differentiator | LOW now / HIGH later | For v2.1's pure-function skills, auto-execute is fine. Design the executor behind an interface so a confirmation gate can be inserted later without rewiring the loop |
| Tool-result rendering in chat (result cards, not raw JSON) | Gallery returns JS-skill results as chat text + optional image/webview embeds. Warped can render tool results as compact cards (e.g. calculator expression → result line) instead of dumping JSON into the transcript | MEDIUM | Persist tool interaction as messages with `role: tool` (OpenAI convention) or app-internal event rows; decide transcript model early — affects Room schema |
| `tool_choice` control (auto / specific / none) | OpenAI + LM Studio support forcing a tool or disabling tools per request. Power-user feature: "answer with calculator only" or "no tools this turn" for debugging misfires | LOW | LM Studio honors OpenAI-compatible params; expose as per-message override or debug setting, not mainline UI |

### Anti-Features (Commonly Requested, Often Problematic)

Features that seem good but create problems.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| JS/WebView skill runtime (Gallery-style `run_js` skills) | "Gallery does it, lets users write custom skills" | 200–500ms WebView init per skill, XSS/script-injection surface, `WebViewAssetLoader` sandbox complexity, MIME-type hosting pitfalls for imports. v2.0 research explicitly scoped Skills Lite to Kotlin-only (A12) | Stay Kotlin-only for v2.1; revisit only with a dedicated security phase |
| Autonomous multi-step agents (unbounded tool loops) | "Let the model chain 10 tools itself" | LiteRT-LM caps at 25 recurring calls; unbounded loops burn battery, context, and user patience on a phone; no approval gate = side-effect risk | Cap rounds (e.g. 5), show round progress, require explicit user turn between chains |
| Auto-executing skills on every message (no chips, always-on) | "Fewer taps, magic UX" | Every registered tool schema consumes context tokens on every request; irrelevant tools increase misfire rate and latency. OpenAI docs explicitly advise limiting loaded functions / using tool search | Chips default all-on for the 4 tiny skills (cheap), but keep the toggle — the mechanism matters more than the default |
| MCP server integrations in v2.1 | "LM Studio supports MCP via API, wire it through" | MCP needs JSON-RPC-over-HTTP client or MCP Kotlin SDK, server lifecycle, auth headers, `allowed_tools` filtering — a full phase by itself (v2.0 research: separate phase). Stuffing into the leftovers milestone re-creates the v2.0 partial problem | Defer to its own phase; v2.1's `tools[]` mapper should be shaped so MCP tools can reuse it later |
| Parallel tool calls in one turn | "Faster: run calculator + time together" | LM Studio Python SDK defaults `max_parallel_tool_calls = 1` for thread-safety; Warped's skills are synchronous pure functions with shared conversation state — parallelism buys ~0ms and risks ordering bugs | Sequential execution (model's array order); revisit only for slow I/O tools |
| Prompt-injection as "tool execution" (status quo) | "It already answers math questions, ship it" | PromptTemplate injection teaches the model to *talk about* the skill, not *invoke* it — no argument validation, no deterministic result, hallucinates calculations. The v2.0 audit explicitly marks this as the carry-over gap | Real registration on both backends (SKILLS-02/03) — the entire point of v2.1 |

## Feature Dependencies

```
[SKILLS-02 LiteRT @Tool registration]
    └──requires──> [Skill model (sealed Skill + Tool vs PromptTemplate category)]
                       └──requires──> [SkillPreferences DataStore (which skills enabled)]
    └──requires──> [Tool-capable local model] (FunctionGemma; graceful degrade otherwise)
    └──requires──> [Cancellable runInference] (stop must break the auto tool loop)

[SKILLS-03 LM Studio tools[] mapping]
    └──requires──> [Skill → JSON-schema mapper] (shared with @ToolParam descriptions)
    └──requires──> [Multi-turn tool loop in LmStudioHelper/Provider]
                       └──requires──> [SSE tool_call chunk accumulator] (streaming)
                       └──requires──> [Cancellable OkHttp Call] (stop mid-loop)
    └──requires──> [Transcript model for tool messages] (role=tool rows in Room?)

[PERF-06 LazyColumn keys]
    └──requires──> [Stable message IDs] (ChatMessage.id — verify exists)
    └──enhances──> [Scroll preservation] (keys are what make position anchoring work)

[PERF-01 sub-state split]
    └──enhances──> [PERF-06] (split first or together; both touch ChatScreen/ViewModel)
    └──enhances──> [Tool progress UX] (toolCallActive in its own state slice → no full recompose)

[shareIn runInference refactor]
    └──requires──> [nothing new] (internal to LlmModelHelper/Provider layer)
    └──conflicts──> [parallel work on the same streaming path] (do before/with tool-loop work,
                        not after — the tool loop appends emissions to the same Flow)
```

### Dependency Notes

- **SKILLS-02 requires the Skill model + preferences:** The v2.0 audit describes sealed `Skill`, `SkillCategory { Tool, PromptTemplate }`, `SkillRepository`, `SkillPreferences` — but **no `*Skill*.kt` or `SkillChipsRow.kt` files exist in the working tree at research time** (glob over `ui/chat/` + repo-wide grep confirm absence; only `PromptTemplateConfigs`, `toolCallActive`, and the `tools` DTO stub are present). Roadmap must include a "locate or rebuild Skills Lite surface" step before SKILLS-02/03.
- **SKILLS-03 streaming accumulator is the hidden complexity:** Non-streaming tool flow is a simple `finish_reason == tool_calls` check; streaming requires accumulating `delta.tool_calls[i].function.arguments` fragments across SSE chunks keyed by index, then JSON-parsing the joined string. Plan the accumulator as its own unit with tests.
- **Tool loop conflicts with double-collect fix:** Both change what `runInference`'s Flow emits (tokens + tool-driven follow-up turns). Sequence: fix the sharing (`shareIn`) first, then add the tool loop on top — otherwise two writers interleave.
- **PERF-01 + PERF-06 are one surgical area:** Both rewrite `ChatScreen` message rendering + `ChatViewModel` state exposure. Do them in the same phase (or adjacent plans with explicit ordering) to avoid merge conflicts and double verification.
- **Transcript model decision blocks Room work:** If tool calls/results persist as messages, Room schema + `MessageBubble` rendering need a `tool` role/type. Decide upfront: persist tool traffic (auditable, resumable) vs ephemeral status row only (simpler). Recommendation: persist minimal tool rows (name + summarized result) — users distrust invisible tool use.

## MVP Definition

v2.1 is a **completion milestone, not a discovery milestone** — MVP = "no partials left." Scope below is the minimum that closes SKILLS-02/03, PERF-01/06, and the runtime hardening items.

### Launch With (v2.1)

- [ ] **SKILLS-02: 4 Tool skills registered as LiteRT-LM `@Tool`s** — Calculator, CurrentTime, JsonFormatter (+ Summarize stays PromptTemplate — it's a persona, not a function; forcing it into `@Tool` is a category error). `ConversationConfig(tools=…)` wired from enabled chips, `automaticToolCalling = true`, graceful message when model lacks tool support
- [ ] **SKILLS-03: same skills mapped to LM Studio `tools[]` + executed multi-turn loop** — schema mapper, `tool_calls` detection (streaming + non-streaming), local execution, `role: tool` re-POST, loop cap (~5), malformed-call fallback to plain content
- [ ] **PERF-06: chat list on keyed LazyColumn** — `items(messages, key = { it.id })`, streaming message as keyed trailing item, scroll-stick-only-if-at-bottom
- [ ] **PERF-01: ChatUiState sub-state split** — separate StateFlows (messages/streaming vs input vs connection/models) so streaming tokens don't recompose the input bar and keystrokes don't recompose the list
- [ ] **Cancellable streaming on both backends** — `Call.cancel()` (remote) + `cancelProcess()` (local) wired to Stop; tool loop aborts between rounds; single shared Flow (no double-collect)
- [ ] **Tool progress UX** — keep/extend `toolCallActive` status row ("Using calculator…") through the real execution path, including error display ("Calculator failed: …") with fallback to plain answer

### Add After Validation (v1.x / next milestone)

- [ ] **Tool-result cards in transcript** — rich rendering once the persist-vs-ephemeral decision is validated with real use
- [ ] **`tool_choice` override / per-message tool control** — trigger: users report tool misfires on ambiguous prompts
- [ ] **Manual-confirmation gate interface** — trigger: first side-effecting skill (anything beyond pure functions) is proposed
- [ ] **Summarize-as-tool reconsideration** — trigger: evidence that prompt-injection Summarize underperforms vs a chunked-summarize function tool

### Future Consideration (v2+)

- [ ] **MCP bridge (LM Studio `integrations` / ephemeral MCP)** — why defer: separate SDK, lifecycle, auth surface; own phase per v2.0 research
- [ ] **JS/WebView custom skills + URL/local import** — why defer: security phase + execution sandbox required (Gallery parity is a product decision, not leftovers)
- [ ] **Native intent skills (email/SMS/maps via `run_intent`)** — why defer: new permissions + confirmation UX + per-intent testing
- [ ] **Benchmark history viewer, speculative decoding, Vulkan/NPU backends, deep links** — why defer: already tracked as deferred v2 items, unrelated to tool/chat completion

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| LiteRT-LM `@Tool` registration (SKILLS-02) | HIGH (chips do nothing real today) | MEDIUM | P1 |
| LM Studio `tools[]` mapping + loop (SKILLS-03) | HIGH (remote parity) | MEDIUM | P1 |
| Keyed LazyColumn + scroll preservation (PERF-06) | HIGH (jank + jump on every long chat) | MEDIUM | P1 |
| ChatUiState sub-state split (PERF-01) | MEDIUM (perf, invisible until scale) | MEDIUM | P1 (milestone goal is zero partials) |
| Cancellable streaming + shareIn refactor | HIGH (Stop button trust) | MEDIUM | P1 |
| Tool progress/error UX | MEDIUM | LOW | P1 (small, completes the loop) |
| Skill→schema shared mapper | MEDIUM (dev quality, prevents drift) | LOW | P2 (do inside SKILLS-02/03, don't split) |
| Tool transcript persistence model | MEDIUM | MEDIUM | P2 (decide early, implement minimally) |
| `tool_choice` override | LOW | LOW | P3 |
| MCP bridge | HIGH long-term | HIGH | P3 (own milestone) |
| JS/WebView skills | MEDIUM | HIGH | P3 |

**Priority key:**
- P1: Must have for launch
- P2: Should have, add when possible
- P3: Nice to have, future consideration

## Competitor Feature Analysis

| Feature | Google AI Edge Gallery | LM Studio (desktop + server) | Warped (v2.1 plan) |
|---------|------------------------|------------------------------|--------------------|
| Local tool execution | `AgentTools` (`load_skill`, `run_js` via hidden WebView, `run_intent` native) + MCP tools; skills defined by `SKILL.md` manifests, auto-invoked via function calling | `.act()` multi-round API (Python/TS SDKs); server parses model text into `tool_calls` | Kotlin `@Tool` functions, auto-execution loop, 4 curated pure-function skills — simpler, offline, no WebView |
| Remote tool execution | N/A (on-device only) | `/v1/chat/completions` `tools[]` (OpenAI-compatible) + MCP `integrations` (ephemeral / mcp.json) | `tools[]` mapping + app-side multi-turn loop; MCP deferred |
| Skill discovery UX | Skills Manager bottom sheet + tryout chips + URL/local import + secret dialog | Model-dependent; MCP server toggles in server settings | SkillChipsRow toggles above input (per v2.0 plan) — lighter than Gallery's manager, right-sized for 4 skills |
| Chat list performance | `ChatList` on LazyColumn, `key = item.id` (Uuid), `snapshotFlow` scroll monitoring, IME auto-scroller | Desktop (not comparable) | Target: same keyed-LazyColumn pattern Gallery already uses |
| State management | Per-screen ViewModels, `StateFlow` collection in `ChatPanel` | N/A | Split sub-states (Gallery keeps per-concern state; Warped's single `ChatUiState` is the outlier to fix) |
| Tool progress display | `MessageBodyCollapsableProgressPanel` (expandable execution detail + console logs) | `on_round_start/end`, `on_prediction_completed` callbacks per round | `toolCallActive` status row — keep minimal; expandable detail is P2+ |

## Sources

- LiteRT-LM Kotlin tools guide (official): `ToolSet`/`@Tool`/`@ToolParam`, `ConversationConfig(tools=…)`, `automaticToolCalling`, manual `Message.toolCalls` flow — https://developers.google.com/edge/litert-lm/android + getting_started.md in google-ai-edge/LiteRT-LM (HIGH)
- LiteRT-LM `Conversation.kt` source: auto tool loop, `handleToolCalls`, `RECURRING_TOOL_CALL_LIMIT = 25`, async `JniMessageCallbackImpl` pending-tool-response re-send — https://github.com/google-ai-edge/LiteRT-LM/blob/main/kotlin/java/com/google/ai/edge/litertlm/Conversation.kt (HIGH)
- OpenAI function-calling guide: 5-step loop (tools → tool_calls → execute → tool output → final), `role: tool` + `tool_call_id` message shape, parallel calls, streaming `response.function_call_arguments.delta` accumulation — https://developers.openai.com/api/docs/guides/function-calling (HIGH)
- LM Studio tool-use docs: OpenAI-compatible `tools[]` on `/v1/chat/completions`, server-side text→`tool_calls` parsing, malformed-call fallback to `content`, streaming chunk shape, default tool format for non-native models — https://lmstudio.ai/docs/developer/openai-compat/tools (HIGH)
- LM Studio MCP-via-API: `integrations` ephemeral vs mcp.json, `allowed_tools` — https://lmstudio.ai/docs/developer/core/mcp (MEDIUM — deferred, context only)
- Gallery skills README: `SKILL.md` manifest, `run_js` WebView bridge (`ai_edge_gallery_get_result`), `run_intent` native, secret handling — https://github.com/google-ai-edge/gallery/blob/main/skills/README.md (HIGH)
- Gallery `LlmChatModelHelper` / agent-chat diff (1.0.14→1.0.15): `ConversationConfig(tools=…)`, skills+MCP prompt injection (`injectSkillsAndMcpTools`), `load_skill`→`runMcpTool` routing prompt — GitHub compare (MEDIUM)
- Android Compose docs: lazy-layout keys (`items(keys)`), `rememberLazyListState` scroll preservation, `derivedStateOf` for scroll-derived UI — https://developer.android.com/develop/ui/compose/lists + /performance/bestpractices (HIGH)
- Jetchat issue #696 + StackOverflow reverse-layout threads: keys fix scroll jump; `firstVisibleItemIndex == 0` gate for auto-scroll-to-bottom in reverse layout (MEDIUM — community, consistent with official docs)
- Warped tree (verified 2026-09-27): `ChatUiState.kt` (single 30-field state, `toolCallActive`), `ChatScreen.kt` (scrolling `Column`, unkeyed `forEach`, always-autoscroll), `LmStudioDtos.kt` (`tools` DTO stub + SSE `tool_call.*` events already modeled), `LiteRTLmProvider.kt` (`tools = emptyList(), automaticToolCalling = false`), absent `*Skill*.kt`/`SkillChipsRow.kt` vs v2.0 planning claims (HIGH — local verification)
- Warped planning: `.planning/MILESTONES.md`, `STATE.md`, `v2.0-MILESTONE-AUDIT.md` (SKILLS-02/03 carry-over definitions), `milestones/v2.0-research/PITFALLS.md` (skill-chip tooltip note) (HIGH)

---
*Feature research for: v2.1 tool execution + chat performance completion*
*Researched: 2026-09-27*
