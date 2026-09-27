# Pitfalls Research: Warped v2.1 Finish v2.0 Leftovers

**Domain:** Adding real on-device tool execution + perf refactors to a shipped Android LLM chat app (Kotlin + Compose + LiteRT-LM 0.13.1 + LM Studio v1)
**Researched:** 2026-09-27
**Confidence:** HIGH (LiteRT-LM `@Tool`/`ToolSet`/`ConversationConfig` API verified against official docs; double-collect, single-state, Column-not-LazyColumn, Job-cancellation facts verified against Warped's own audit docs) / MEDIUM (specific LiteRT-LM 0.13.x tool-calling bugs — corroborated by multiple GitHub issues but version-sensitive) / LOW (exact perf numbers — not measured on Warped hardware)

**Stack ground truth (from v2.0 research — do not re-litigate):** Kotlin 2.3.20, AGP 9.2.1, Compose BOM 2026.05.01 (strong skipping default), Hilt 2.59.2 (KSP-only), Room 2.8.4, LiteRT-LM 0.13.1, OkHttp 4.12.0, Retrofit 3.0.0, kotlinx-serialization 1.7.3, `minSdk = 28`, R8 full mode with keep rules, dependency audit script that fails the build on banned libs (kapt, Gson, Moshi, kotlin-reflect, Ktor). Only LM Studio v1 is supported for remote.

**v2.1 scope (from PROJECT.md + v2.0-MILESTONE-AUDIT.md):** PERF-01 (split `ChatUiState` into `ChatListState`/`ChatInputState`/`ChatStreamingState`, currently consumed by ~10 subcomposables), PERF-06 (`ChatScreen` uses `Column`, not `LazyColumn` — migrate with stable keys), SKILLS-02 (real tool execution: LiteRT-LM `@Tool` registration; currently skills are prompt-injection only — `LmStudioHelper.applySkills` injects `PromptTemplate` skills into the system prompt), SKILLS-03 (LM Studio `tools[]` mapping), `runInference` double-collect refactor (`LiteRtLlmHelper`/`LmStudioHelper` launch an internal "drain" job AND return a separate `chat()` Flow → `shareIn`/`MutableSharedFlow`), true OkHttp `Call.cancel()` plumbing (43-02 removed the dead `activeCall` field; cancellation still goes through the Flow/Job path). PERF-12/13 benchmark numbers stay CI-gated (Pixel 7 hardware required) — out of scope.

**Downstream consumer:** Each pitfall is tagged with the v2.1 phase that should prevent it (tool execution phase vs. perf/UI phase vs. runtime-hardening phase).

---

## Critical Pitfalls

### Critical 1: Registering `@Tool` Methods That Do Blocking I/O or Touch Android APIs on the Inference Thread

**What goes wrong:**
LiteRT-LM `automaticToolCalling=true` executes the annotated `@Tool` method inline on whatever thread the engine invokes it on, then feeds the return value back into the next generation turn. If the tool body does network I/O (`Calculator` calling a web API), disk access, or touches `Context`/`ContentResolver`, it blocks the inference callback path: streaming stalls with zero chunks, and on some runtimes the engine's silence watchdog treats the stall as a hang. Worse, a tool that throws (e.g., `JsonFormatter` on malformed model-generated JSON) propagates as a native `LiteRtLmJniException` mid-turn instead of a clean tool-error response, killing the whole conversation.

**Why it happens:**
The sample code in the official docs shows pure functions (`getCurrentWeather` returning a hardcoded map, `sum` over a list). Developers copy the shape but fill the body with real work — Room queries, `OkHttp` calls, `DateFormat` with default locale — without noticing the docs never promise a background dispatcher for tool execution.

**How to avoid:**
- Tool bodies must be pure, synchronous, CPU-trivial functions: string/date math, JSON validation/formatting, arithmetic. Anything involving I/O belongs behind `automaticToolCleaning=false` (manual tool calling) where the app executes the tool in its own coroutine and sends the result back via `Message.tool(...)`.
- Wrap every tool body in try/catch returning a structured error map (`mapOf("error" to "...")`) instead of throwing. A thrown exception must never cross the JNI boundary.
- Keep the 4 curated skills' tool surface minimal: `Calculator` (expression eval, no network), `CurrentTime` (pure `Clock`), `JsonFormatter` (validate + pretty-print, catch parse errors), `Summarize` (prompt-template only — NOT a `@Tool`, keep it as system-prompt injection).
- Constrain `@ToolParam` types to what the schema generator supports: `String`, `Int`, `Boolean`, `Float`, `Double`, or `List` thereof; nullable + default value = optional. Any other type (e.g., `Map`, custom data class) silently produces a broken schema the model can't call.

**Warning signs:**
- First tool call works, response stream is empty afterward (tool blocked the callback thread).
- `LiteRtLmJniException` whose stack points at the app's own tool method rather than engine code.
- Tool fires for Gemini-family models but never for Qwen (schema or template mismatch — see Critical 2).

**Phase to address:** Tool execution phase (SKILLS-02). Gate: a unit test invoking each `@Tool` method directly (no engine) asserting pure return + error-map-on-bad-input, plus an on-device smoke test of one tool turn per skill.

---

### Critical 2: Assuming Tool Calling Works Uniformly Across Models — Qwen/Gemma Template Bugs Wedge the Engine

**What goes wrong:**
LiteRT-LM's tool pipeline is per-model-family (`gemma3_data_processor.cc`, `qwen3_data_processor.cc`, …). Public issues document: Qwen3 native tool calling executing the tool but sending back an **empty `<tool_response>`**, causing a template prefix-mismatch crash (`The new rendered template string does not start with the previous rendered template string`); Gemma 3n crashing with `INVALID_ARGUMENT: Item must contain a type` because `Conversation.kt handleToolCalls` omitted the `type: tool_response` property; and Qwen 2.5 wedging the engine after 1–2 successful tool calls so every subsequent `sendMessageAsync()` delivers zero callbacks (no `onMessage`/`onDone`/`onError`) until process kill. If Warped registers tools unconditionally for every model, users on affected model families get crashes or silent hangs.

**Why it happens:**
`automaticToolCalling=true` is a single boolean, so it looks model-agnostic. The model-specific template fragility is invisible until a real tool fires on that exact family + runtime version.

**How to avoid:**
- Gate `@Tool` registration on the model allowlist: only register tools for thinking/tool-capable entries that have passed an on-device tool smoke test. The allowlist asset already carries `capabilities`/`taskTypes` — add a `toolCalling: true/false` flag per model.
- When `toolCalling=false`, keep the v2.0 prompt-injection behavior (skill description in system prompt). The model can still *describe* the skill; it just can't invoke it.
- Set a per-turn tool-call cap (`RECURRING_TOOL_CALL_LIMIT`, default 5 in LiteRT-LM) and a silence watchdog on the app side: if no callback arrives within N seconds after a tool fires, close the conversation, surface "Tool call timed out — try again", and never leave the UI in a permanent spinner.
- On `LiteRtLmJniException` mentioning `tool_response` / template mismatch, catch it at the helper boundary and degrade to a no-tools retry of the same turn rather than killing the conversation.

**Warning signs:**
- Tool works on one model, crashes/hangs on another with identical app code.
- Logcat shows `<tool_response></tool_response>` (empty) or `Item must contain a type`.
- After one successful tool turn, all subsequent turns hang with zero callbacks — engine-level wedge, `createConversation()` does not recover.

**Phase to address:** Tool execution phase (SKILLS-02). Gate: tool smoke test on EACH allowlisted tool-capable model, not just one reference model. Non-tool models keep prompt-injection — verify no regression.

---

### Critical 3: LM Studio `tools[]` Mapping Sent in the Wrong Schema Dialect

**What goes wrong:**
LM Studio's v1 API speaks OpenAI-style `tools: [{type: "function", function: {name, description, parameters}}]` chat-completions. If Warped hand-rolls the mapping from its `Skill` objects and gets the JSON shape subtly wrong (missing `type: "function"`, `parameters` without `"type": "object"`, non-JSON-Schema `properties`), the server silently ignores the tools array and answers as if no tools exist — "looks done but isn't" (see checklist). Alternatively the server returns `finish_reason: "tool_calls"` which the current streaming parser (built for `content` deltas only) drops, so the assistant message renders empty and the tool never executes.

**Why it happens:**
SKILLS-02/03 share the `Skill` value object, tempting a single shared serializer. But LiteRT-LM generates its schema from `@Tool`/`@ToolParam` reflection while LM Studio needs explicit OpenAI function-schema JSON — two different shapes from one source, and the app currently only exercises the prompt-injection path (`applySkills` → system prompt), so neither real path has ever been parsed end-to-end.

**How to avoid:**
- Derive both schemas from the single `Skill` definition via two tested mappers: `Skill.toLiteRtTool(): ToolSet` and `Skill.toOpenAiFunction(): JsonObject`. Unit-test the OpenAI JSON against the exact `type/function/name/description/parameters` shape with a golden JSON file.
- Handle `finish_reason == "tool_calls"` in the SSE parser: accumulate the `tool_calls[].function.arguments` delta fragments (they stream as partial JSON), parse once complete, execute the matching local skill (calculator/time/json — all pure functions, safe on `Dispatchers.Default`), then send a follow-up chat request with the `tool` role message. Cap at 5 rounds (mirror LiteRT-LM's limit).
- Never execute a model-requested tool that isn't in the allowlisted `Skill` set — unknown `name` → return a `tool` message with an error string, don't crash, don't ignore.

**Warning signs:**
- Remote tool turn returns an empty assistant bubble (tool_calls delta swallowed).
- Server behaves identically with skills on vs. off (schema ignored).
- Partial-JSON parse crash mid-stream (`JsonSyntaxException` inside the SSE collector).

**Phase to address:** Tool execution phase (SKILLS-03). Gate: golden-JSON unit test + a mock-server SSE test that streams a `tool_calls` response split across chunks and asserts the follow-up request contains the `tool` message.

---

### Critical 4: Splitting `ChatUiState` Into Sub-States That Duplicate Source of Truth

**What goes wrong:**
The obvious split (`ChatListState` + `ChatInputState` + `ChatStreamingState`) tempts duplicating fields that two sub-states both need — e.g., `isStreaming` in both list and input, `activeMessageId` in both list and streaming. The copies drift: the send button enables while the list shows a spinner, or the streaming indicator freezes because the collector updated the copy nobody renders. ~10 subcomposables each pick a different sub-state and the bug surface triples versus the single state.

**Why it happens:**
v2.0 audit says the split is "structural improvement for Phase 45+ scale" — it reads as a pure refactor, so it's done by moving fields without defining ownership. Every field that "both sides need" gets copied instead of hoisted or derived.

**How to avoid:**
- Rule: each piece of data lives in exactly ONE sub-state; cross needs are derived (`val canSend = !streamingState.isStreaming && inputState.text.isNotBlank()` computed at the screen level, not stored).
- Keep the ViewModel's single `StateFlow<ChatUiState>` as the source of truth internally if desired, but expose `StateFlow<ChatListState>`, `StateFlow<ChatInputState>`, `StateFlow<ChatStreamingState>` via `map { }` + `stateIn` — or split the data class into three `@Immutable` classes held in one holder. Either way, no field exists twice.
- Annotate all three `@Immutable` (strong-skipping-mode requirement from v2.0 Critical 11) and convert any `List<Message>` params to `ImmutableList` at the same time.
- Migrate subcomposables one at a time (input row first — smallest, streaming indicator second, list last) with the app compiling and green after each step. Big-bang rewiring of ~10 consumers in one commit is how this refactor breaks.

**Warning signs:**
- Same boolean/enum appears in two sub-state classes.
- A UI element (send button, stop button, spinner) disagrees with another element about whether streaming is active.
- PR touches all ~10 subcomposables at once with no intermediate green state.

**Phase to address:** Perf/UI phase (PERF-01). Gate: Layout Inspector recomposition audit — streaming token updates must skip (gray) the input row and list rows, recomposing only the streaming bubble; plus a "no duplicated field" review check (grep each field name, exactly one declaration).

---

### Critical 5: Migrating to `LazyColumn` Without Stable Keys Destroys Streaming State

**What goes wrong:**
`ChatScreen` currently uses a `Column`. The naive migration — `LazyColumn { items(messages) { MessageBubble(it) } }` without `key` — makes every streaming token update a positional rebind: the in-progress bubble loses its internal state (collapsed/expanded code block, syntax-highlight pass, thinking-panel open state), scroll position jumps as items resize, and with 100+ messages the whole list visibly flickers per token. Code blocks over 200 lines (v1.6 collapse behavior) snap open/closed on every token.

**Why it happens:**
Without `key`, Lazy reuses item slots by index. During streaming the last item mutates every ~20ms, and any list change (new message, thinking panel appearing) shifts indices — slot reuse then attaches the wrong remembered state to the wrong message.

**How to avoid:**
- `items(messages, key = { it.id })` with the Room message ID (stable across streaming updates — the streaming bubble must UPDATE the same row, never append-then-replace with a new ID).
- Keep per-message UI state (code-block expanded, thinking panel open) keyed by message ID: `remember(message.id) { ... }` or hoisted in the ViewModel map — never bare `remember { }` inside the item.
- `contentType = { if (it.isStreaming) "streaming" else "settled" }` so the streaming bubble gets its own slot type and settled rows are never recomposed by token flow.
- Preserve the v1.6 deferred-highlighting contract: flat monospace while `isStreaming`, full highlight pass on completion. The highlight pass must run ONCE per message (on close fence / done flag), not per token — gate it on the settled transition.
- Auto-scroll policy: stick to bottom only if already near bottom; never force-scroll while the user has scrolled up to read history. Use `listState.isScrollInProgress` + distance-to-end check.

**Warning signs:**
- Code blocks flicker between plain and highlighted during streaming.
- Scroll jumps to a random position when a new token arrives or a message completes.
- Expanding a code block, then watching it collapse on the next token.

**Phase to address:** Perf/UI phase (PERF-06). Gate: manual test with a 150-message conversation + an active streaming reply containing a 200+ line code block; assert no scroll jump, no collapse reset, Layout Inspector shows settled rows skipped during streaming.

---

### Critical 6: `shareIn` Refactor That Replays the Entire Reply (or Loses Late Collectors)

**What goes wrong:**
The double-collect fix replaces "internal drain job + returned `chat()` Flow" with a shared flow. Two symmetric failures: (a) `shareIn(scope, SharingStarted.Eagerly, replay = Int.MAX_VALUE)` replays the whole conversation turn to every new collector — rotate the device and the UI re-renders the full reply as "new" tokens, duplicating persistence writes; (b) `replay = 0` with `SharingStarted.Lazily` drops tokens for any collector that subscribes a frame late (the exact bug the drain job was papering over), reintroducing the v2.0 Critical 7 token-drop symptom.

**Why it happens:**
The current shape (drain job consumes for side effects like Room persistence, returned flow feeds the UI) exists because two consumers need the same token stream. `shareIn` looks like a drop-in fix, but replay + start-mode + scope lifetime must match the two consumers' needs, and the persistence consumer must not depend on UI collection being active.

**How to avoid:**
- Share with `SharingStarted.WhileSubscribed()` (or `Eagerly` scoped to the inference job, cancelled by `stopResponse`) and `replay = 1` — enough for a just-subscribed UI to pick up the latest token, small enough to never duplicate a turn.
- Scope the shared flow to the inference coroutine scope (a per-turn `Job` child of `viewModelScope`), NOT `viewModelScope` itself: cancelling the turn's job must terminate the share. `stopResponse()` cancels that job — verify both local (`Engine.close()` path) and remote paths.
- Persistence (Room append) subscribes as a collector of the shared flow from turn start, independent of UI subscription — rotating the device must not lose or duplicate stored tokens.
- Keep the `Channel.UNLIMITED` producer buffer from the v2.0 fix (Critical 7): `shareIn` does not add producer buffering by itself.

**Warning signs:**
- Device rotation duplicates the assistant message in Room (replay too large + persistence re-collects).
- First token(s) missing after rotation or after navigating away and back (replay too small / lazy start).
- "Stop" no longer stops promptly (shared flow outlives the turn scope).

**Phase to address:** Runtime-hardening phase (double-collect refactor). Gate: rotation-during-streaming test asserting exactly one Room row and no token gap; stop-latency test (coroutine completes < 200ms after `stopResponse`, repeat 50×).

---

### Critical 7: `Call.cancel()` Plumbed to the Wrong `Call` Instance (or None)

**What goes wrong:**
43-02 removed the dead `activeCall` field; cancellation currently goes through Flow/Job cancellation. If the v2.1 fix stores the OkHttp `Call` in a field but the SSE read loop creates its call from a *different* client/request path (e.g., Retrofit creates its own `Call` internally), cancelling the stored reference cancels nothing — the socket keeps streaming, tokens keep arriving into a dead collector, and repeated stop/start leaks pooled connections until the radio stalls ("no network" ~30 min after a chat session, per v2.0 Critical 10).

**Why it happens:**
`LMStudioProvider` likely uses Retrofit (`@Streaming`) for SSE, where the `Call` object is created and enqueued inside Retrofit — there is no app-visible `Call` to store unless the code drops to raw OkHttp or captures the call via an interceptor/event listener. Storing a field named `activeCall` and assigning it somewhere near-but-not-at the real execution site compiles fine and passes a casual review.

**How to avoid:**
- Capture the real call: either execute SSE via raw `OkHttpClient.newCall(request)` (full ownership of the `Call`), or capture Retrofit's underlying call with an OkHttp `Interceptor`/`EventListener` that records `callStart`. The stored reference must be the exact instance whose socket is open.
- Guard with an `AtomicReference<Call?>` (or `Mutex`): set on start, cleared in `finally` on completion/cancel. `stopResponse()` does `getAndSet(null)?.cancel()` — null-safe, idempotent, no double-cancel of the *next* turn's call (clear-then-new ordering under the mutex).
- Keep cooperative cancellation too: `coroutineContext.ensureActive()` between `readUtf8Line()` iterations. `Call.cancel()` unblocks the socket read with an `IOException`; the loop must translate that into clean termination, not an error toast ("Stopped" ≠ "Network error" — suppress the exception when the turn was user-cancelled).
- `retryOnConnectionFailure(false)` + `callTimeout(60s)` on the streaming client (carried over from v2.0 Critical 10).

**Warning signs:**
- "Stop" freezes the UI bubble but logcat shows tokens still arriving.
- `connectionPool.connectionCount()` grows across stop/start cycles.
- User-cancelled stops surface as red error snackbars instead of a quiet "Stopped".

**Phase to address:** Runtime-hardening phase (`Call.cancel()` plumbing). Gate: OkHttp `EventListener` test — `callEnd` → `connectionReleased` promptly on `stopResponse`; 50× stop/start soak with flat pool count; cancelled turn shows "Stopped", not an error.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Ship tool execution as prompt-injection for "all skills" and call SKILLS-02 done | Zero native risk, works on every model | Model hallucinates tool results (calculator returns wrong arithmetic with full confidence); no verification possible | **Only as the fallback for non-tool-capable models** (see Critical 2). Never for the 4 curated skills on capable models. |
| One shared `@Tool` ToolSet instance reused across conversations | Less allocation | Tool with any mutable field (counters, caches) leaks state between turns and users' chats; engine wedge residue (Critical 2) persists | **Never.** Fresh `tool(SkillToolSet())` per `createConversation`. |
| Split `ChatUiState` by copy-pasting fields into 3 classes | Compiles immediately | Drift bugs (Critical 4) that only manifest as send/stop/spinner disagreement | **Never.** One owner per field, derived cross-needs. |
| `LazyColumn` migration without keys ("works on my 5-message chat") | One-line diff | Streaming state loss + scroll jumps at 100+ messages (Critical 5) | **Never.** Keys + contentType from the first commit. |
| `shareIn(viewModelScope, Eagerly, replay = 0)` as the double-collect fix | Removes the drain job | Late collectors drop tokens; scope outlives the turn so Stop breaks (Critical 6) | **Never.** Per-turn scope, explicit replay=1, persistence as independent collector. |
| Storing the Retrofit `Call` wrapper instead of the OkHttp `Call` | Field exists, review passes | Cancel hits the wrong object; socket leaks (Critical 7) | **Never.** Verify with EventListener test. |
| Suppressing `IOException` from cancelled SSE reads globally | No error toasts | Real network failures also silenced; user sees infinite spinner on airplane mode | **Only suppress when the turn's cancelled flag is set.** Check `coroutineContext.isActive` / explicit `userCancelled` flag before swallowing. |
| Adding a tool-execution dependency (MCP Kotlin SDK, Ktor client) to match Gallery | Familiar API | Banned-lib build failure (audit script), +MBs APK, second HTTP stack | **Never.** LiteRT-LM `@Tool` + existing OkHttp only. |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|---------------|------------------|
| LiteRT-LM `tool()` registration | Registering the same `ToolSet` instance across conversations; tools list built once in `init` | Build `tools = listOf(tool(FreshSkillToolSet()))` inside `createConversation` per turn/session; gate on allowlist `toolCalling` flag |
| LiteRT-LM `automaticToolCalling` | Leaving `true` with tool bodies that do I/O or throw | Pure sync bodies + try/catch → error map; I/O tools require manual mode (`automaticToolCalling=false` + `Message.tool(...)` round-trip) |
| LiteRT-LM conversation lifecycle | Reusing one `Conversation` across model switches or after a tool exception | `close()` + fresh `createConversation` per model and after any tool-turn failure; never reuse a wedged conversation (Qwen wedge does not reset) |
| LM Studio `tools[]` JSON | Hand-shaped JSON missing `type: "function"` or non-object `parameters` | Golden-JSON unit test against OpenAI function-schema shape; validate with a strict `JsonObject` builder, not string templates |
| LM Studio `tool_calls` streaming | Parser only handles `content` deltas; `tool_calls` fragments crash or vanish | Accumulate `function.arguments` deltas → parse once → execute allowlisted skill → follow-up request with `tool` role message; cap 5 rounds |
| Compose sub-state split | Two sub-states each holding `isStreaming` / selection | Single owner per field; cross-needs derived at screen level; all sub-states `@Immutable`; lists as `ImmutableList` |
| `LazyColumn` streaming row | Streaming appends new rows per token batch | Update the SAME message ID in place; `key = { it.id }`; `contentType` separates streaming vs settled slots |
| `shareIn` scope | `viewModelScope` + `Eagerly` | Per-turn child `Job`; `stopResponse` cancels it; persistence collector subscribed from turn start |
| OkHttp `Call` capture | Storing a field near the call site and assuming identity | Raw `newCall` ownership or `EventListener.callStart` capture; `AtomicReference.getAndSet(null)?.cancel()`; clear in `finally` |
| R8 + new tool/skill classes | New `@Tool`/`@Serializable` classes stripped in release (`NoSuchMethod` / serializer-not-found only in release) | `@Tool` methods are reflection-read — add keeps for the ToolSet classes; keep existing kotlinx-serialization rules; smoke-test tools in `assembleRelease` (v2.0 Critical 5/6 still apply to every new class) |
| Hilt + new helpers | `SkillExecutor` injected into `LiteRtLlmHelper` while `SkillRepository` injects the helper (graph cycle, cf. v2.0 Critical 8) | Tools are stateless functions — no repository dependency inside the ToolSet; skill *config* (on/off) resolved before `createConversation`, passed as data |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Tool schema inflates the system prompt every turn | TTFT climbs 200–500ms once tools registered; small-context models truncate history sooner | Register only skills enabled in `SkillPreferences`; keep `@Tool(description)` strings one line; re-measure TTFT with tools on vs. off | As soon as 4 skills × descriptions + JSON schema ship on a 4k-context model |
| `LazyColumn` without `contentType` recomposes all settled rows per token | Frame overruns during streaming scale with history length | `contentType` streaming vs settled; settled rows skip (verify in Layout Inspector) | >50 messages in one conversation |
| `shareIn replay = ALL` re-emits full turns to new collectors | Rotation replays hundreds of tokens through Compose + Room at once; visible burst + duplicate-write risk | `replay = 1`, per-turn scope (Critical 6) | First rotation during a long reply |
| SSE `tool_calls` argument accumulation on Main | Jank while a long argument JSON streams in | Accumulate fragments on `Dispatchers.Default`, parse once, deliver result only | Long tool arguments (JSON formatter payloads) |
| Streaming bubble `key` instability forces full highlight pass per token | Syntax highlight (v1.6 engine) re-runs per token; streaming jank on code-heavy replies | Highlight once on settled transition (Critical 5); flat monospace while streaming (existing v1.6 contract) | Any reply with a 200+ line code block |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| `@Tool` that reads files/contacts/location at model direction | Prompt-injected tool abuse: a crafted reply tricks the model into calling the tool with attacker args ("send file X to…") — the 4 curated skills are safe only if they stay pure | Keep tool surface to pure functions (calc/time/format); any future I/O tool requires explicit user confirmation UI per invocation; never expose paths, tokens, or keys as tool params |
| Logging tool arguments/results at INFO in release | Tool args may contain user data echoed by the model; logcat is readable by other apps on rooted devices | Log tool invocations at DEBUG/VERBOSE only; never log full argument JSON in release |
| LM Studio `tools[]` follow-up request re-sends API key headers through a different client | Key leaks into a non-Keystore path or plaintext log | Reuse the single authenticated provider client for follow-up turns; keys only from EncryptedSharedPreferences (unchanged v1.0 contract) |
| New ToolSet/Skill classes added without R8 keep review | Release-only `NoSuchMethodError` on the reflection-read `@Tool` methods | Extend `proguard-rules.pro` with the new classes; `assembleRelease` + tool smoke test before merge |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Tool turn shows nothing until final answer | 5–15s of dead UI while tool executes + second generation runs; user taps Stop thinking it hung | Render a "Using Calculator…" / "Formatting JSON…" activity row the moment a tool call starts; replace with the final answer (same collapsible pattern as the Thinking panel) |
| Cancelled tool turn shows red error | User-initiated Stop looks like a crash | Suppress `IOException`/cancellation from user-cancelled turns; show quiet "Stopped" state (Critical 7) |
| `ChatUiState` split ships with send/stop disagreement | Send enabled during streaming → double-submit; or Stop missing while streaming → no way out | Single-owner fields + derived `canSend` (Critical 4); test matrix: idle/streaming/tool-running × send/stop visibility |
| LazyColumn auto-scroll yanks user away from history | Reading old messages while a reply streams; list jumps to bottom per token | Stick-to-bottom only when already near bottom (Critical 5); show a "Jump to latest" pill otherwise |
| Tools silently ignored on non-capable models | User enables Calculator, model does arithmetic wrong anyway, trust erodes | Per-model tool badge: capable models show skill chips active; non-capable show skills as prompt-hints (existing chips UI) with no execution claim |

---

## "Looks Done But Isn't" Checklist

- [ ] **SKILLS-02 (LiteRT tool execution):** Often missing per-model gating — verify tools registered ONLY for allowlisted tool-capable models, prompt-injection retained for the rest.
- [ ] **SKILLS-02:** Often missing tool-body purity — verify each `@Tool` method unit-tested standalone (pure return + error map on bad input, never throws).
- [ ] **SKILLS-02:** Often missing fresh-instance-per-conversation — verify no shared mutable `ToolSet` singleton.
- [ ] **SKILLS-03 (LM Studio tools[]):** Often missing `tool_calls` parse path — verify mock-server SSE test with chunk-split `arguments` JSON and follow-up `tool`-role request.
- [ ] **SKILLS-03:** Often missing unknown-tool guard — verify model-requested name outside the Skill set yields an error `tool` message, not a crash.
- [ ] **PERF-01 (sub-state split):** Often missing single-ownership — verify no field declared in two sub-states; `canSend`/derived values computed, not stored.
- [ ] **PERF-01:** Often missing stability annotations — verify all sub-states `@Immutable`, list params `ImmutableList`, Layout Inspector shows input/list skipped during streaming.
- [ ] **PERF-06 (LazyColumn):** Often missing stable keys — verify `key = { messageId }` + `contentType` streaming/settled; 150-message + 200-line-code-block scroll test passes.
- [ ] **Double-collect refactor:** Often missing per-turn scope — verify rotation yields exactly one Room row, no token gap, Stop latency < 200ms.
- [ ] **Call.cancel() plumbing:** Often missing call-identity proof — verify `EventListener` shows `connectionReleased` on `stopResponse` and 50× soak keeps pool flat.
- [ ] **All of the above:** Often missing release verification — verify tool + streaming paths smoke-tested in `assembleRelease` (R8 keeps for new ToolSet/`@Serializable` classes).

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Tool body blocks inference thread | LOW (if caught in smoke test) | Move I/O to manual tool-calling mode or make body pure; add try/catch → error map |
| Engine wedge on Qwen tool turns | MEDIUM | Gate tools off for the affected family in the allowlist; ship prompt-injection fallback; re-enable after LiteRT-LM bump with smoke test |
| LM Studio schema ignored / tool_calls dropped | LOW | Fix mapper against golden JSON; add `tool_calls` parse path; mock-server regression test |
| Sub-state field drift | MEDIUM | Re-merge duplicated field into single owner; convert cross-needs to derived; re-audit ~10 consumers |
| LazyColumn state loss / scroll jumps | LOW | Add `key` + `contentType` + `remember(id)`; one-commit fix, but requires the 150-message manual test to confirm |
| shareIn replay/late-collector bugs | MEDIUM | Fix replay/start-mode/scope; add rotation + stop-latency tests; backfill any duplicated Room rows via migration-safe dedupe |
| Wrong-Call cancellation / socket leak | MEDIUM | Capture real call via raw `newCall` or `EventListener`; soak-test pool flatness; users on leaked builds need app restart (no data loss) |
| R8 strips new Tool classes (release-only crash) | LOW | Add keep rules; `assembleRelease` smoke test; hotfix release |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| Critical 1 (tool-body purity) | Tool execution phase (SKILLS-02) | Standalone `@Tool` unit tests (pure + error-map); no I/O/Context in tool bodies (review) |
| Critical 2 (per-model tool gating) | Tool execution phase (SKILLS-02) | Allowlist `toolCalling` flag; on-device tool smoke test per capable model; watchdog + degrade-to-no-tools retry |
| Critical 3 (LM Studio tools[] dialect) | Tool execution phase (SKILLS-03) | Golden-JSON unit test; mock-server chunk-split `tool_calls` SSE test; unknown-name guard test |
| Critical 4 (sub-state single ownership) | Perf/UI phase (PERF-01) | No duplicated field (grep); `@Immutable` + `ImmutableList`; Layout Inspector skip audit |
| Critical 5 (LazyColumn keys) | Perf/UI phase (PERF-06) | `key` + `contentType`; 150-msg + 200-line-code-block scroll test; settled rows skipped |
| Critical 6 (shareIn replay/scope) | Runtime-hardening phase (double-collect) | Rotation → one Room row, no gap; Stop < 200ms × 50 |
| Critical 7 (Call.cancel identity) | Runtime-hardening phase (Call.cancel) | `EventListener` release-on-stop; 50× soak flat pool; "Stopped" not error |
| Security (tool surface / logging / R8) | Tool execution phase + release gate | Tool surface review (pure only); no INFO logging of args; `assembleRelease` tool smoke test |
| UX (activity row / scroll policy / badges) | Perf/UI phase (with tool phase input) | Tool-running activity row; stick-to-bottom-only-near-bottom; per-model tool badge |

**Suggested phase ordering rationale:** tool execution first (it defines what the streaming pipeline must carry: tool-activity rows, multi-round turns — the UI split and LazyColumn work must accommodate those states, not the other way around); perf/UI second (sub-state split + LazyColumn, tested with tool-turn UI present); runtime hardening last (shareIn + Call.cancel mechanize the now-settled turn lifecycle; verified against the final UI). R8/release smoke test closes the milestone.

---

## Sources

- LiteRT-LM official Android docs — tool definition (`ToolSet`, `@Tool`, `@ToolParam` types, `ConversationConfig(tools, automaticToolCalling)`, manual tool calling via `Message.tool(...)`): https://developers.google.com/edge/litert-lm/android (HIGH)
- LiteRT-LM Kotlin getting-started (same tool API, `RECURRING_TOOL_CALL_LIMIT`): https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md (HIGH)
- LiteRT-LM C++ tool-use doc (per-model `ModelDataProcessor` formats tools/calls/responses — basis for per-family fragility claim): https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/cpp/tool-use.md (MEDIUM)
- LiteRT-LM issue #1027 — Qwen3 empty `<tool_response>` + template prefix-mismatch crash (2025-11): https://github.com/google-ai-edge/LiteRT-LM/issues/1027 (MEDIUM)
- LiteRT-LM issue #2256 — Qwen 2.5 engine wedge after 1–2 tool calls, `createConversation()` doesn't reset (0.10.2 report; version-sensitive for 0.13.1): GitHub google-ai-edge/LiteRT-LM (MEDIUM)
- LiteRT-LM issue #1181 — Gemma 3n `Item must contain a type` tool-response crash, `Conversation.kt handleToolCalls` fix (MEDIUM)
- DeepWiki LiteRT-LM Kotlin/Android API — `automaticToolCalling` recursive loop via `ToolManager` + reflection (2026-05-21): https://deepwiki.com/google-ai-edge/LiteRT-LM/4.6-kotlin-and-android-api (LOW, secondary source)
- Warped v2.0-MILESTONE-AUDIT.md — double-collect, single-state-~10-consumers, Column-not-LazyColumn, Job-cancellation facts (HIGH, primary project source)
- Warped MILESTONES.md / STATE.md / REQUIREMENTS.md — v2.1 deferred item list, PERF-01 sub-state names, SKILLS-02/03 scope (HIGH)
- Warped v2.0 PITFALLS.md (`.planning/research/PITFALLS.md`) — Critical 7 (callbackFlow buffering), Critical 10 (SSE cancellation), Critical 11 (strong skipping/`ImmutableList`), R8 keeps; not re-argued here, only extended (HIGH)
- `callbackFlow`/`shareIn`/replay semantics, OkHttp `Call.cancel()` + `EventListener`, Compose `LazyColumn` keys/`contentType` — established Android/Kotlin API knowledge, no web verification available in this session (LOW — validate against official docs during planning if any claim is load-bearing)

---
*Pitfalls research for: Warped v2.1 Finish v2.0 Leftovers (tool execution + perf completion)*
*Researched: 2026-09-27*
