# Project Research Summary

**Project:** Warped v2.1 — Finish v2.0 Leftovers (real tool execution + Compose perf completion)
**Domain:** On-device LLM chat app (Android/Kotlin + LiteRT-LM 0.13.1 local + LM Studio v1 remote)
**Researched:** 2026-09-27
**Confidence:** HIGH

## Executive Summary

Warped v2.1 is a **completion milestone, not a discovery milestone**: v2.0 shipped a Skills Lite surface whose Tool-category skills are prompt-injection only (they log/inject text, never execute), a chat list rendered as an unkeyed scrolling `Column`, a single 30-field `ChatUiState` consumed whole by `ChatScreen`, and a `runInference` path with a sentinel no-op cancellation job plus no true OkHttp `Call.cancel()`. Experts build this class of product with real function-calling on both backends (LiteRT-LM `@Tool`/`ToolSet` + `ConversationConfig(tools, automaticToolCalling)` locally; OpenAI-compatible `tools[]` + multi-turn `tool_calls` loop remotely), keyed `LazyColumn` with split sub-states for chat perf, and a single shared inference flow with per-turn scope and socket-level cancellation.

The recommended approach is **zero new dependencies**: every v2.1 work item (SKILLS-02, SKILLS-03, PERF-01, PERF-06, `shareIn` refactor, `Call.cancel()` plumbing) resolves to code patterns on the already-pinned catalog (litertlm-android 0.13.1, Retrofit 3.0.0 / OkHttp 4.12.0, coroutines 1.9.0, kotlinx-collections-immutable 0.4.0). No new layers either — all six completions are surgical modifications inside the existing keystone seams (`LlmModelHelper` 5-method interface unchanged, helpers own runtime state, `ProviderRouter` `dagger.Lazy` dispatch unchanged), with the v2.0 code carrying explicit `// v2.1` insertion comments at the exact edit points.

The key risks are model-family fragility in LiteRT tool calling (Qwen3 empty `<tool_response>` + template-mismatch crash, Qwen 2.5 post-tool engine wedge, Gemma 3n `type: tool_response` omission — all version-sensitive upstream issues), silent LM Studio `tools[]` dialect mismatches (server ignores malformed schema; streaming `tool_calls` deltas need an index-keyed accumulator), and Compose refactor traps (duplicated sub-state fields drifting, unkeyed LazyColumn destroying streaming state, `shareIn` replay/scope misconfiguration duplicating or dropping tokens, `Call.cancel()` aimed at the wrong `Call` instance leaking sockets). Mitigation is per-model tool gating with prompt-injection fallback, golden-JSON schema tests + mock-server chunk-split SSE tests, single-owner sub-state fields with `@Immutable` + `ImmutableList`, and rotation/stop-latency/soak-test gates — detailed in PITFALLS.md's "Looks Done But Isn't" checklist.

## Key Findings

### Recommended Stack

Zero new dependencies for v2.1 — the pinned catalog already covers everything (see STACK.md). The headline decision is deliberate restraint: `litertlm-android` stays on **0.13.1** (the `ToolSet`/`@Tool`/`@ToolParam` + `ConversationConfig(tools, automaticToolCalling)` API ships inside it; 0.14.0 exists but is unverified against the Gallery reference), LM Studio tool use rides the existing Retrofit/OkHttp + kotlinx-serialization stack as new `@Serializable` DTOs over `POST /v1/chat/completions` (never the native `/api/v1/chat`, whose `integrations` field is MCP-only), and the `shareIn` + `Call.cancel()` + keyed-LazyColumn work is pure code patterns on coroutines 1.9.0 / immutable 0.4.0. Explicitly banned: `okhttp-sse` artifact, MCP client SDK, `litertlm` bump, kapt/Moshi/Gson/reflect/Ktor (RUNTIME-12 audit), closing the shared OkHttp dispatcher on cancel.

**Core technologies:**
- litertlm-android 0.13.1 (KEEP): local `@Tool` registration via `ToolSet` — tool API verified in-tree, no bump
- Retrofit 3.0.0 + OkHttp 4.12.0 + kotlinx-serialization-json 1.7.3 (KEEP): LM Studio `tools[]` DTOs + `Call.cancel()` on existing stack
- kotlinx-coroutines 1.9.0 (KEEP): `shareIn(WhileSubscribed(5000), replay=1)` double-collect fix + cancel plumbing
- kotlinx-collections-immutable 0.4.0 (KEEP): stable `ImmutableList<Message>` for keyed LazyColumn + sub-state split (0.5.0 stable exists, optional post-verification bump)
- Turbine 1.1.0 (KEEP): tests for shared-flow replay semantics and tool-call emissions

### Expected Features

v2.1 MVP = "no partials left": close SKILLS-02/03, PERF-01/06, and the runtime hardening items (see FEATURES.md). Note the scoping correction: **Summarize stays PromptTemplate** (it's a persona, not a function — forcing it into `@Tool` is a category error), so SKILLS-02 is 3 real `@Tool`s (Calculator, CurrentTime, JsonFormatter). Note also the tree gap: **no `*Skill*.kt` or `SkillChipsRow.kt` files exist in the working tree** despite v2.0 planning describing sealed `Skill` + `SkillPreferences` — the roadmap must include a "locate or rebuild Skills Lite surface" step before SKILLS-02/03.

**Must have (table stakes):**
- SKILLS-02: 3 Tool skills as LiteRT-LM `@Tool`s, `ConversationConfig(tools=…)` from enabled chips, `automaticToolCalling=true`, graceful degrade on non-capable models — chips do nothing real today
- SKILLS-03: same skills mapped to LM Studio `tools[]` + app-side multi-turn loop (streaming accumulator, `role: tool` re-POST, ~5-round cap, malformed-call fallback) — remote parity
- PERF-06: chat list on keyed LazyColumn (`key = { it.id }`, `contentType` streaming/settled) + stick-to-bottom-only-if-at-bottom — fixes O(n) per-token composition + scroll yanks
- PERF-01: `ChatUiState` sub-state split (messages/streaming vs input vs connection) so tokens don't recompose the input bar — milestone goal is zero partials
- Cancellable streaming on both backends (`Call.cancel()` remote + `cancelProcess()` local, tool loop aborts between rounds, single shared Flow)
- Tool progress/error UX ("Using calculator…" row through the real path, error display with plain-answer fallback)

**Should have (competitive):**
- Offline tool skills as pure Kotlin functions (airplane-mode differentiator; calculator needs safe expr parser, no eval)
- Unified skill surface: one `Skill → (ToolSet | LmStudioTool)` mapper so skills work wherever the model runs
- Tool-result rendering decision (persist minimal tool rows vs ephemeral status — recommend persist name + summarized result; affects Room schema, decide upfront)
- Manual-confirmation gate *interface* (auto-execute now for pure functions; shape the executor so a gate inserts later without rewiring)

**Defer (v2+):**
- MCP bridge, JS/WebView custom skills, native intent skills (`run_intent`), `tool_choice` override, parallel tool calls, tool-result cards (rich rendering), benchmark history / speculative decoding / Vulkan/NPU / deep links — see FEATURES.md anti-features (unbounded loops, always-on tools, prompt-injection-as-execution)

### Architecture Approach

No new layers; six surgical modifications inside existing seams (see ARCHITECTURE.md). The `LlmModelHelper` 5-method interface is sufficient and stays unchanged; `LiteRTLmProvider.chat()` lines 132–138 (`tools = emptyList()` → skill-mapped tools, `automaticToolCalling` flip) and `LmStudioHelper.runInference()` (map `SkillTool → LmStudioTool`, pass into request body via a new dedicated `tools` param — not the `integrations` overload) are the two skill insertion points; `LmStudioDtos.tools[]` + SSE `tool_call.*` parse + `ChatViewModel` `[tool:NAME]` detection are already DONE. PERF-01 splits `ChatUiState` into 4 `@Immutable` sub-states (list/input/streaming/selection, merging dead `isGenerating`, deleting 2 `@Deprecated` fields) with `MessageBubble`/`ChatInputBar` signatures unchanged; PERF-06 is a mechanical 1-file `Column → LazyColumn` migration; the runtime fix replaces sentinel no-op jobs with `shareIn` single-flight shared upstreams and threads a raw `Call` through `LMStudioProvider` (`chatCall()` overload + `AtomicReference<Call>` + `cancelActiveCall()`). New code lives in `domain/skills/` (`SkillTool`, `SkillRegistry`) + `data/skills/` (`BuiltinSkills`, `SkillRegistryImpl`); registry exposes `StateFlow.value` (never `runBlocking`), takes no repository dependency, is consumed via `dagger.Lazy`. Watch the LRT-02 interaction: `ConversationConfig.tools` applies only at conversation creation, so enabled-skill changes must trigger `resetConversation()`.

**Major components:**
1. `LiteRTLmProvider` / `LiteRtLlmHelper` (local runtime) — `ConversationConfig.tools` wiring, `shareIn` single-flight + real `stopResponse`
2. `LMStudioProvider` / `LmStudioHelper` (remote runtime) — `tools[]` body mapping, Call-returning chat + `cancelActiveCall()`, `cleanUp` off `runBlocking`
3. `ChatViewModel` + `ChatScreen` + `ChatUiState` (UI) — slice StateFlows, keyed LazyColumn, shared-flow collection, existing `[tool:NAME]` accretion path
4. `domain/skills` + `data/skills` (NEW, small) — `SkillTool` interface, `BuiltinSkills`, `SkillRegistry` with per-call-mapped descriptors

### Critical Pitfalls

Top items from PITFALLS.md (7 criticals; full prevention/verification matrix in the Pitfall-to-Phase Mapping table):

1. **Blocking I/O or throwing `@Tool` bodies on the inference thread** — engine executes tools inline; stalls read as hangs, throws cross JNI as `LiteRtLmJniException`. Avoid: pure sync CPU-trivial bodies, try/catch → error map, `@ToolParam` types restricted to String/Int/Boolean/Float/Double/List thereof.
2. **Assuming tool calling works uniformly across models** — Qwen3 empty-`<tool_response>` crash, Qwen 2.5 post-tool engine wedge (process-kill to recover), Gemma 3n `type` omission. Avoid: per-model `toolCalling` allowlist flag + on-device smoke test per capable model, prompt-injection fallback otherwise, tool-call cap + silence watchdog + degrade-to-no-tools retry.
3. **LM Studio `tools[]` in the wrong schema dialect** — server silently ignores malformed tools; `finish_reason: tool_calls` dropped by content-only parser → empty bubble. Avoid: two tested mappers from one `Skill` source, golden-JSON unit test, index-keyed `arguments` accumulator, allowlist-name guard, 5-round cap.
4. **Sub-state split duplicating source of truth** — `isStreaming` in two slices drifts (send/stop/spinner disagree). Avoid: one owner per field, cross-needs derived at screen level, all `@Immutable` + `ImmutableList`, migrate subcomposables one at a time.
5. **LazyColumn without stable keys** — positional rebind loses code-block collapse/highlight/thinking state, scroll jumps, per-token flicker at 100+ messages. Avoid: `key = { it.id }` (update same row, never append-then-replace), `contentType` streaming/settled, `remember(message.id)`, highlight-once-on-settle, conditional auto-scroll.
6. **`shareIn` replay/scope misconfiguration** — `replay=ALL` duplicates turns on rotation; `replay=0` + `Lazily` drops late-collector tokens; `viewModelScope` scope breaks Stop. Avoid: `replay=1`, per-turn child `Job`, persistence as independent from-turn-start collector, keep `Channel.UNLIMITED` producer buffer.
7. **Wrong-`Call` `cancel()`** — Retrofit owns its internal `Call`; storing a nearby-but-different reference cancels nothing → socket leaks, pool exhaustion. Avoid: raw `newCall` ownership or `EventListener.callStart` capture, `AtomicReference.getAndSet(null)?.cancel()`, cooperative `ensureActive()` + cancelled-flag-gated `IOException` suppression ("Stopped" ≠ error).

Plus cross-cutting: R8 strips new `@Tool`/`@Serializable` classes in release-only (keep rules + `assembleRelease` tool smoke test), Hilt cycles if a skill executor depends on a repository (tools stay stateless; config passed as data), tool-schema token inflation on small-context models (register only enabled skills, one-line descriptions, re-measure TTFT).

## Implications for Roadmap

Suggested phase structure below. **Phase-order disagreement recorded (no forced consensus)** — the two researchers argue opposite orders with genuine rationale; the roadmapper decides:

- **ARCHITECTURE.md position (runtime → skills → perf):** fix the pipe before pushing new traffic through it — skills execute *through* `runInference`, so building the tool loop on top of the sentinel-job no-op + socket-drain bugs bakes unstoppable-tool-call bugs into SKILLS-02/03. PERF-01+06 ship together atomically afterward (either alone gives ~zero measurable win) and are orthogonal enough to parallelize.
- **PITFALLS.md position (tools → perf → runtime):** tools-first because tool execution defines what the streaming pipeline must carry (tool-activity rows, multi-round turns) — the UI split and LazyColumn work must accommodate those states, not the other way around; runtime hardening *last* because `shareIn` + `Call.cancel` mechanize the now-settled turn lifecycle and are verified against the final UI.

Both agree PERF-01 and PERF-06 must ship together (never split), and both agree the runtime work and skills work touch the same `runInference` flow and must be explicitly sequenced (not parallelized) wherever they land.

### Phase A: Runtime hardening (shareIn + Call.cancel + runBlocking removal)

**Rationale:** ARCHITECTURE.md order — the sentinel-job defect means `stopResponse()` is currently a no-op on both helpers and the OkHttp socket keeps draining after Stop (code admits it: `LmStudioHelper` lines 57–60). Fix the pipe before tool traffic flows through it. (PITFALLS.md would place this last — see ordering note above.)
**Delivers:** `shareIn` single-flight shared upstream in both helpers with real `stopResponse()`; `LMStudioProvider.chatCall()` + `cancelActiveCall()` via raw `Call` ownership; `cleanUp` off `runBlocking`; `ChatViewModel.stopGeneration → helper.stopResponse()` wire-up. No UI change.
**Addresses:** Cancellable streaming (table stakes); double-collect + socket-drain defects.
**Avoids:** Critical 6 (replay/scope), Critical 7 (wrong-Call cancel), `runBlocking`-in-inference anti-pattern.
**Uses:** coroutines 1.9.0 `shareIn` pattern; existing OkHttp 4.12.0 client (no `okhttp-sse` artifact); `AtomicReference<Call>` guard.

### Phase B: Real tool execution (SKILLS-02 + SKILLS-03)

**Rationale:** The milestone's headline gap — Tool-category skills currently only log/inject prompt text. Builds on Phase A in ARCHITECTURE order (tools execute through the fixed `runInference`); in PITFALLS order this would come first to define the turn lifecycle the UI must carry. Either way, sequence explicitly against Phase A (shared `runInference` flow — do not parallelize blindly).
**Delivers:** `domain/skills` + `data/skills` (registry, 3 builtin pure-function `@Tool`s — Calculator, CurrentTime, JsonFormatter; Summarize stays PromptTemplate); `ConversationConfig.tools` + `automaticToolCalling` wiring with per-model `toolCalling` gating and conversation-reset-on-skill-change; LM Studio `tools[]` mapper + index-keyed streaming accumulator + 5-round app-side loop with `role: tool` re-POST; "locate or rebuild Skills Lite surface" step (chips + preferences missing from tree); tool-activity row through the real path.
**Addresses:** SKILLS-02, SKILLS-03, tool progress/error UX, unified skill surface.
**Avoids:** Critical 1 (tool purity), Critical 2 (per-model gating), Critical 3 (schema dialect), Security (pure-only surface, DEBUG-only tool logging, R8 keeps, single authenticated client for follow-ups).

### Phase C: Compose perf (PERF-01 + PERF-06 together, atomic)

**Rationale:** Single `ChatUiState` + unkeyed `Column` = O(n) composition per streaming token with scroll jumps. The two completions are one surgical area (`ChatScreen` + ViewModel state exposure) — ship together or get ~zero measurable win from either alone. Orthogonal to A/B (different `ChatViewModel` functions), so parallelizable if capacity allows — but verify against final tool-turn UI (activity rows, multi-round states) per PITFALLS.
**Delivers:** 4 `@Immutable` sub-states with single-field-ownership + slice StateFlows; `Column → LazyColumn` with `key = { it.id }` + `contentType` + `remember(id)`; `InlineModelSelectorBar` hoist; subcomposable signatures unchanged; conditional stick-to-bottom + "Jump to latest" pill; `isGenerating` merge + `@Deprecated` deletion.
**Addresses:** PERF-01, PERF-06, scroll preservation, tool progress UX accommodation.
**Avoids:** Critical 4 (field drift), Critical 5 (keyless state loss), perf traps (contentType-less recompose, highlight-per-token, main-thread argument accumulation).
**Uses:** kotlinx-collections-immutable 0.4.0 `ImmutableList`; compose compiler metrics to verify `skippable` bubbles on release builds.

### Phase Ordering Rationale

- **Dependency-driven core:** the tool loop and the sharing/cancellation refactor both change what `runInference`'s Flow emits — sequence them explicitly (ARCHITECTURE: runtime first; PITFALLS: tools first). The roadmapper picks the direction; what matters is they are not built concurrently on the same flow.
- **Atomic perf pair:** PERF-01 without PERF-06 still recomposes all rows per token (state identity changes); PERF-06 without PERF-01 keeps O(n) layout with keyed slots. One phase, one verification (Layout Inspector skip audit + 150-message/200-line-code-block scroll test).
- **Pitfall-gated exits:** every phase has a falsifiable gate (per-model smoke tests; golden-JSON + chunk-split SSE tests; rotation-single-Row + Stop-<200ms + pool-flat soak; release-build tool smoke for R8) — a phase is not done when code compiles but when its gate passes.
- **Release close:** `assembleRelease` + tool smoke test ends the milestone (new `@Tool`/`@Serializable` keep rules), with PERF-12/13 benchmark numbers staying CI-gated out of scope.

### Research Flags

Phases likely needing deeper research during planning (`/gsd-plan-phase --research-phase`):
- **Phase B (tool execution):** LiteRT `ToolProvider` Kotlin API shape — which `com.google.ai.edge.litertlm` class backs `ConversationConfig.tools` and its JSON-schema format (30-min AAR inspection at phase start); `automaticToolCalling=true` local-execution-without-roundtrip verification on the pinned 0.13.1; Qwen3/Gemma issue currency against 0.13.1 (upstream issues are version-sensitive — re-check before gating decisions).
- **Phase A (runtime hardening):** `shareIn` replay/start-mode/scope selection is load-bearing and version-sensitive (LOW-confidence API-semantics claims in PITFALLS) — validate against official coroutines docs during planning.

Phases with standard patterns (skip research-phase):
- **Phase C (Compose perf):** keyed LazyColumn + sub-state split + `derivedStateOf` scroll gating are well-documented official Android patterns (HIGH confidence); only open question is confirming `ChatMessage.id` type/nullability (5-min domain model read).

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | LiteRT-LM tool API verified against official docs (2026-09-04); versions cross-checked against Maven Central + repo ground truth (`libs.versions.toml`, proguard rules, audit doc); only MEDIUM items are exact Compose BOM patch + KSP version (resolve at plan time) |
| Features | HIGH | LiteRT-LM tool API + LM Studio tool docs verified against official sources; Compose guidance from official Android docs; Gallery behavior from repo + docs; scope grounded in Warped's own tree (verified 2026-09-27) + v2.0 audit |
| Architecture | HIGH | Every claim verified by direct source read of shipped v2.0 code (file + line citations for all 6 insertion points); build order is dependency-driven |
| Pitfalls | HIGH/MEDIUM/LOW (mixed) | HIGH: `@Tool`/`ToolSet`/`ConversationConfig` API + Warped's own defect facts (audit docs); MEDIUM: specific LiteRT-LM 0.13.x tool-calling bugs (multiple GitHub issues, version-sensitive); LOW: exact perf numbers, some coroutines/OkHttp/Compose API semantics without live doc verification |

**Overall confidence:** HIGH

### Gaps to Address

- **Skills Lite surface missing from tree:** no `*Skill*.kt` / `SkillChipsRow.kt` found vs v2.0 planning claims — roadmap Phase B must start with "locate or rebuild" before SKILLS-02/03 (FEATURES.md dependency notes).
- **`ChatMessage.id` existence/type:** FEATURES assumes verify-before-PERF-06; ARCHITECTURE found `id: String?` via `deleteMessage` usage — confirm nullability/default before Phase C key selection.
- **LiteRT `ToolProvider` constructor + schema format:** confirm via AAR inspection at Phase B start (ARCHITECTURE open question 1).
- **`automaticToolCalling=true` locality on 0.13.1:** verify one-skill smoke test executes without network round-trip (ARCHITECTURE open question 3).
- **Upstream tool-calling issue currency:** issues #1027 / #2256 / #1181 predate or straddle 0.13.1 — re-verify which still reproduce before finalizing the per-model allowlist (treat as device-test gates, not settled facts).
- **Perf numbers unmeasured:** no Warped-hardware baselines (TTFT with tools on/off, frame overruns at N messages) — measure during Phase C; PERF-12/13 CI gating stays out of scope.
- **Phase order unresolved by design:** runtime-first vs tools-first — roadmapper decides (see ordering note); flag the chosen direction's risk (unstoppable tool calls vs UI retrofit) in the roadmap.

## Sources

### Primary (HIGH confidence)
- Google AI Edge LiteRT-LM Android tool-use docs (developers.google.com/edge/litert-lm/android, 2026-09-04) — `ToolSet`/`@Tool`/`@ToolParam`, `ConversationConfig(tools, automaticToolCalling)`, manual `Message.tool` flow
- LM Studio tool-use docs (lmstudio.ai/docs/developer/openai-compat/tools) — `/v1/chat/completions` `tools[]`, `tool_calls`, streaming accumulation by index, malformed-call fallback
- Android Compose docs — lazy-layout keys, `rememberLazyListState` scroll preservation, stability/`Immutable` collections, `derivedStateOf`
- Warped repo ground truth — `LlmModelHelper`, both helpers, both providers, `ProviderRouter`, `LlmHelperModule`, `ChatUiState`/`ChatScreen`/`ChatViewModel`, `LmStudioDtos`/`LmStudioApi`, `libs.versions.toml`, `proguard-rules.pro`, `.planning/v2.0-MILESTONE-AUDIT.md` (HIGH — local verification 2026-09-27)
- LiteRT-LM `Conversation.kt` source — auto tool loop, `handleToolCalls`, `RECURRING_TOOL_CALL_LIMIT = 25`
- OpenAI function-calling guide — 5-step loop, `role: tool` shape, streaming argument-delta accumulation
- mvnrepository.com `litertlm-android` — 0.13.1 (Jun 04 2026) vs 0.14.0 (Jul 08 2026) version facts

### Secondary (MEDIUM confidence)
- LiteRT-LM issues #1027 (Qwen3 empty tool_response), #2256 (Qwen 2.5 wedge), #1181 (Gemma 3n type omission) — version-sensitive bug reports
- Gallery skills README + `LlmChatModelHelper` agent-chat diff — `SKILL.md` manifests, `ConversationConfig(tools=…)` precedent
- Chanzmao (2026-08) monolithic-UiState split decision tree; Ramadan Sayed (2026-02) laggy-chat fix; Jetchat #696 + StackOverflow reverse-layout threads — community, consistent with official docs
- LM Studio MCP-via-API docs — deferred context only

### Tertiary (LOW confidence)
- `callbackFlow`/`shareIn`/replay semantics, OkHttp `Call.cancel()` + `EventListener` specifics, exact perf numbers — established API knowledge without live doc verification in-session; validate load-bearing claims during planning
- DeepWiki LiteRT-LM Kotlin API summary (2026-05-21) — secondary source for reflection-loop internals

---
*Research completed: 2026-09-27*
*Ready for roadmap: yes*
