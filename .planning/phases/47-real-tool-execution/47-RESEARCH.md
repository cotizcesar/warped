# Phase 47: Real Tool Execution — Research

**Researched:** 2026-09-28
**Domain:** LiteRT-LM 0.17.1 on-device function calling + LM Studio OpenAI-compatible `tools[]` loop (Kotlin/Android)
**Confidence:** HIGH (AAR bytecode via `javap`, official LiteRT-LM Android docs fetched 2026-09-28, tree ground truth; model-family bug reports are version-sensitive → MEDIUM)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- SkillChipsRow above chat input; SkillRepository (+Hilt binding) + SkillPreferences DataStore with all-on defaults
- 3 real tools: Calculator, CurrentTime, JsonFormatter. Summarize stays a PromptTemplate skill (persona, not a function)
- Sealed `Skill`, `SkillCategory { Tool, PromptTemplate }`; `skills/` package one file per skill
- Single shared Skill→schema mapper feeds `@ToolParam` descriptions (local) and `LmStudioToolFunction.parameters` JSON schema (remote) — no drift
- Local: `@Tool`s via `ToolSet` + `ConversationConfig(tools=…)` from enabled chips, `automaticToolCalling = true`; adopt 0.14–0.17 tool-calling fixes (LRT-08)
- Remote: multi-turn loop over POST /v1/chat/completions (never /api/v1/chat); tools[], finish_reason tool_calls detection in streaming (index-keyed SSE accumulator) + non-streaming; execute locally; re-POST role:tool; cap ~5; malformed-call fallback to content
- Loop runs through Phase 46 runInference with cancellation checks between rounds (25-round cap can never run away — cap here is ~5)
- Progress: "Using calculator…" status row (toolCallActive already exists in ChatScreen); errors: "Calculator failed: …" + plain-text fallback; never hang/empty bubble
- Transcript: minimal rows (tool name + summarized result, role:tool) persisted via Room, resumable
- Gating: per-model allowlist gating + prompt-injection fallback; Qwen3/Gemma template bugs stay on fallback until re-tested on-device (LRT-08); model without tool support → clear message + normal answer
- Every tool argument validated/sanitized before execution (safe expression parser, JSON size caps, timezone-safe formatting)
- Tool bodies pure sync functions, never throw out (error-mapped returns); results treated as untrusted text in next turn
- Standalone unit tests per tool; tool executor behind interface (confirmation gate later without rewiring)

### the agent's Discretion
- Exact ToolSet/ConversationConfig wiring order, SSE accumulator shape, transcript row schema details

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SKILLS-07 | Skills Lite surface located or rebuilt Kotlin-only (sealed Skill, SkillCategory, SkillRepository+Hilt, SkillPreferences DataStore all-on, SkillChipsRow) | §Architecture Patterns Plan 1 decomposition; DataStore pattern from AdvancedPreferences; verified zero `*Skill*.kt` in tree |
| SKILLS-08 | Calculator/CurrentTime/JsonFormatter as `@Tool`s via ToolSet + ConversationConfig(tools=…), automaticToolCalling=true; graceful no-support message | §Local Tool Registration (bytecode + official docs verified); §Per-Model Gating |
| SKILLS-09 | Single shared Skill→schema mapper feeding @ToolParam descriptions and LmStudioToolFunction.parameters | §Shared Mapper Design |
| SKILLS-10 | Multi-turn tool loop over POST /v1/chat/completions: tools[], streaming + non-streaming tool_calls detection, local exec, role:tool re-POST, cap ~5, malformed fallback | §Remote Loop Design; §Critical Dialect Finding (native vs OpenAI-compatible path) |
| SKILLS-11 | Minimal transcript rows (tool name + summarized result, role:tool), persist-vs-ephemeral decided | §Transcript Persistence (§Role.valueOf crash finding) |
| SKILLS-12 | Progress ("Using calculator…") + errors ("Calculator failed: …" + fallback); executor behind interface | §Progress/Error Wiring (existing toolCallActive row); §Executor Interface |
| LRT-08 | Adopt 0.14–0.17 tool-calling fixes; re-test Qwen3/Gemma template bugs on-device; gating + fallback | §0.13.1→0.17.1 Delta; §Per-Model Gating |
| HARD-02 | Trust boundary: validate/sanitize args, pure sync bodies, untrusted results, unit tests per tool | §Trust Boundary |
</phase_requirements>

## Summary

Phase 47 rebuilds a Skills surface that does not exist (verified: no `*Skill*.kt` in tree; the only "Skill" mention is a comment in `LmStudioDtos.kt:23-24`) and wires real execution on both backends. The LiteRT-LM 0.17.1 AAR bytecode confirms the exact registration shape the locked decisions assume: a `ToolSet` interface whose `@Tool`/`@ToolParam`-annotated methods are adapted via the top-level `tool(ToolSet): ToolProvider` function into `ConversationConfig(tools, automaticToolCalling)`. The official Android docs (fetched 2026-09-28, last updated 2026-09-04) confirm the same pattern plus the manual-mode escape hatch (`automaticToolCalling=false` + `responseMessage.toolCalls` + `Message.tool(Contents.of(...))`).

The single most important research finding for the planner: **the existing `LMStudioProvider.chat()` speaks LM Studio's NATIVE `/api/v1/chat` protocol** (`LmStudioChatRequest` with `input: [{type, content}]` items, native SSE `message.delta`/`tool_call.*` events). SKILLS-10 mandates the tool loop over the **OpenAI-compatible `POST /v1/chat/completions`** instead. The OpenAI-compatible DTOs already exist (`OpenAiChatRequest` with `tools`/`tool_choice`, `OpenAiStreamChunk`/`OpenAiStreamToolCall` with `index`, `OpenAiNonStreamingMessage.toolCalls`) but nothing sends them and `LmStudioApi` has no `/v1/chat/completions` endpoint. So the remote loop is a parallel request path, not a modification of the existing SSE parser — though the 46-01 raw-`Call` + `onCallCreated` + `AtomicReference<Call>` cancellation pattern must be replicated, and `OpenAiMessage(role, content)` must gain `tool_call_id`/`tool_calls` fields for the `role:tool` re-POST.

Second critical finding: **`EntityMappers.toDomain()` calls `Role.valueOf(role)`**, and the domain `Role` enum is `{SYSTEM, USER, ASSISTANT}`. Persisting `role="tool"` rows verbatim will crash history load. The plan must either add `Role.TOOL` (enum change ripples to every `when(role)` — provider mapping, UI) or encode tool rows as `ASSISTANT` with a marker prefix parsed at render time. Recommendation below.

**Primary recommendation:** Build `domain/skills/` (sealed Skill + SkillCategory + SkillRepository interface + ToolExecutor interface) and `data/skills/` (3 tool ToolSets, shared schema mapper, SkillPreferences DataStore, repository impl + Hilt module) first; wire local via fresh `tool(WarpedToolSet(enabledOnly))` per conversation with `automaticToolCalling=true`; wire remote via a new OpenAI-compatible `chatCompletionsWithTools()` path on `LMStudioProvider` using raw OkHttp `Call` + index-keyed `tool_calls` accumulator + `role:tool` re-POST loop capped at 5 with `ensureActive()` between rounds; persist tool rows as `role="tool"` behind a `Role.TOOL` enum addition with exhaustive-`when` audit.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Tool registration (`@Tool` ToolSet) | API/Backend (LiteRT engine, on-device) | — | Engine owns execution when automaticToolCalling=true; app only supplies pure functions |
| Tool schema mapping (Skill→JSON schema) | API/Backend (app data layer) | — | Single mapper in `data/skills/` feeds both backends; no UI involvement |
| Remote tool loop (POST/re-POST) | API/Backend (LMStudioProvider + helpers) | — | Network + loop control live in provider/helper; ViewModel only collects tokens |
| Skill toggles + progress/error display | Browser/Client (Compose UI) | — | SkillChipsRow, status row, error row are pure UI over ViewModel state |
| Tool gating (allowlist) | API/Backend (ModelAllowlistRepository) | — | Capability decision is data-layer; UI only renders the notice string |
| Transcript persistence | Database/Storage (Room) | — | role:tool rows in `messages` table via existing DAO |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| litertlm-android | 0.17.1 (pinned in `libs.versions.toml`) | Local `@Tool` execution: ToolSet, Tool/ToolParam, ToolKt.tool(), ConversationConfig(tools, automaticToolCalling) | Only engine that executes GGUF-class models on-device; API shape verified in AAR bytecode [VERIFIED: AAR javap] |
| OkHttp (existing) | 4.12.0 (catalog) | Raw `Call` for `/v1/chat/completions` streaming + cancel | 46-01 established the raw-Call + onCallCreated + AtomicReference pattern; Retrofit `@Streaming` cannot surface the live Call for mid-loop cancel |
| kotlinx-serialization (existing) | 1.7.x (catalog) | `OpenAiChatRequest`/`OpenAiStreamChunk` encode/decode; shared mapper builds `JsonObject` schemas | Already the DTO layer; `buildJsonObject` for parameter schemas needs no new dep |
| DataStore Preferences (existing) | 1.1.x (catalog) | `SkillPreferences` per-skill boolean keys, all-on defaults | Same pattern as `AdvancedPreferences` (`booleanPreferencesKey` + `.data.map { ?: true }`) |
| Room (existing) | 2.7.x (catalog) | `role:tool` transcript rows in `messages` table | No schema change needed (role/content columns suffice); manual migration only if columns added — avoid adding columns |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| kotlinx-datetime / java.time (stdlib) | platform | CurrentTime tool: timezone-safe formatting | `java.time.ZonedDateTime` + explicit `ZoneId`; never default-locale `DateFormat` in tool body |
| JUnit5 + Truth + MockK + Turbine (existing) | catalog | Per-tool unit tests, golden-JSON schema test, mock-server SSE chunk-split test | Test strategy in §Test Strategy |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| automaticToolCalling=true (locked) | manual mode (`automaticToolCalling=false` + `Message.tool()`) | Manual gives app-side dispatcher control and per-call validation hooks, but requires owning the whole round-trip loop locally too; locked decision is automatic — keep manual only as the documented fallback if a model family wedges under automatic mode |
| Raw OkHttp Call for completions loop | Retrofit `@Streaming` endpoint | Retrofit hides the `Call` handle; 46-01 proved raw Call is required for Stop-between-rounds; use raw OkHttp |
| `Role.TOOL` enum addition | Marker-prefix on ASSISTANT rows | Enum is cleaner for queries but forces exhaustive-`when` audit everywhere Role is matched; prefix avoids enum ripple but pollutes content — prefer enum, audit is small (grep shows ~6 match sites) |

**Installation:** None — zero new dependencies (RUNTIME-12 audit stays green). All code rides the pinned catalog.

**Version verification:** `litertlm-android 0.17.1` pinned in `gradle/libs.versions.toml:4` [VERIFIED: tree]. AAR present in Gradle cache (`com.google.ai.edge.litertlm/litertlm-android/0.17.1/…`, plus co-existing 0.13.1 AAR) [VERIFIED: filesystem].

## Package Legitimacy Audit

No external packages are installed by this phase. All tool bodies use Kotlin stdlib + `java.time` (platform) + existing `kotlinx-serialization-json` (already in catalog). The expression evaluator for Calculator must be **hand-rolled in-tree** (shunting-yard/recursive-descent, ~100 lines) because the zero-new-dependencies constraint forbids `exp4j`/`mXparser`-style libraries.

| Package | Registry | Disposition |
|---------|----------|-------------|
| (none) | — | No installs; audit N/A |

**Packages removed due to slopcheck [SLOP] verdict:** none.
**Packages flagged as suspicious [SUS]:** none.

## Architecture Patterns

### System Architecture Diagram

```
ChatInputBar (SkillChipsRow) ── toggles ──▶ SkillPreferences (DataStore)
        │                                          │
        │ Map<String,Boolean>                      │ enabled skills
        ▼                                          ▼
ChatViewModel.sendMessage ── ChatRequest ──▶ helper.runInference()
        │                                          │
        ├── LOCAL ──▶ LiteRtLlmHelper ──▶ LiteRTLmProvider.chat()
        │                  │  builds ConversationConfig(
        │                  │    tools = enabled.map { tool(WarpedToolSet(it)) },
        │                  │    automaticToolCalling = enabled.isNotEmpty())
        │                  ▼
        │            Conversation.sendMessageAsync(Contents): Flow<Message>
        │                  │  engine executes @Tool inline, streams text Deltas
        │                  ▼
        │            StreamToken.Delta / Done  (toolCallActive set via
        │                  │   [tool:NAME] marker emitted by provider shim*)
        │                  ▼
        ├── REMOTE ─▶ LmStudioHelper ──▶ NEW LMStudioProvider.chatCompletionsWithTools()
        │                  │  raw OkHttp Call → POST {base}/v1/chat/completions
        │                  │  body = OpenAiChatRequest(model, messages, tools[], stream=true)
        │                  ▼
        │            SSE `data:` lines → OpenAiStreamChunk
        │                  ├── content deltas ──▶ StreamToken.Delta
        │                  └── finish_reason=tool_calls ──▶ index-keyed accumulator
        │                        │ reconstructs [{name, arguments-json}]
        │                        ▼
        │                   ensureActive() + round++ (cap 5, >= semantics)
        │                        ▼
        │                   ToolExecutor.execute(name, argsJson) locally
        │                        ▼
        │                   append {assistant tool_calls} + {role:tool} messages
        │                        ▼ re-POST (new Call, retained for cancel)
        │
        ▼
ChatViewModel accumulator ──▶ toolCallActive / streamingContent / messages
        │── "Using {display}…" status row (existing ChatScreen:287-306)
        │── "{Display} failed: {reason}" + fallback text on ToolResult.Error
        └── persist: assistant msg + role:tool rows via ChatRepository.saveMessage
```

\* Local path detail: with `automaticToolCalling=true` the engine executes tools internally and the app never sees a tool event on the `Flow<Message>`. The existing `[tool:NAME]` → `toolCallActive` UI contract (ChatViewModel:310-313, ChatScreen:287) therefore needs a local source of "tool is running" signal. Options, in preference order: (1) wrap each `@Tool` body in a companion that posts to a `SharedFlow<ToolEvent>` before/after execution (tool body runs on engine thread — post is non-blocking, safe); (2) emit status from `ToolExecutor` in manual mode only. Recommend (1): a `ToolEventBus` (or callback lambda captured by the ToolSet instance) shared between `data/skills/` and the provider; ViewModel collects it alongside tokens to set/clear `toolCallActive` and to persist transcript rows. Keep the `[tool:NAME]` regex path for the remote/native-protocol `[tool:NAME]` markers already emitted by `handleSseEvent`, but the NEW completions loop should emit `toolCallActive` directly (not via text markers) to avoid leaking markers into persisted content.

### Recommended Project Structure

```
app/src/main/java/com/warped/
├── domain/skills/               # NEW — pure interfaces + value types (JVM-testable)
│   ├── Skill.kt                 # sealed Skill { Tool(id, displayName, description), PromptTemplate }; SkillCategory
│   ├── SkillRepository.kt       # interface: enabledSkills: StateFlow<List<Skill.Tool>>, setEnabled()
│   └── ToolExecutor.kt          # interface: suspend fun execute(name, argsJson: String): ToolResult
│                                # sealed ToolResult { Success(summary, transcript), Failure(reason) }
├── data/skills/                 # NEW — implementations
│   ├── CalculatorSkill.kt       # ToolSet with @Tool + pure eval + companion schema descriptor
│   ├── CurrentTimeSkill.kt      # ToolSet with @Tool
│   ├── JsonFormatterSkill.kt    # ToolSet with @Tool
│   ├── SkillDescriptors.kt      # single shared schema source: per-skill param specs →
│                                #   (a) @ToolParam descriptions (used by ToolSet),
│                                #   (b) JsonObject OpenAI parameters (remote)
│   ├── SkillPreferences.kt      # DataStore booleans, default true (mirror AdvancedPreferences)
│   ├── SkillRepositoryImpl.kt   # combines descriptors + preferences → enabledSkills
│   └── LocalToolExecutor.kt     # validates args → dispatches to pure tool fns → error-mapped results
├── di/SkillsModule.kt           # NEW Hilt module: binds SkillRepository, ToolExecutor
├── ui/chat/components/
│   ├── SkillChipsRow.kt         # NEW (in ChatInputBar Column Row 0 per UI-SPEC §2)
│   ├── ChatInputBar.kt          # MODIFY — add skillMap + onToggleSkill params
│   └── MessageBubble.kt         # MODIFY — "Used {Display}" transcript rows (§5 pattern)
└── data/remote|local            # MODIFY — provider wiring (see below)
```

### Pattern 1: Local @Tool Registration (verified shape)

**What:** One `ToolSet` implementation per skill (or one `WarpedToolSet` holding enabled skills — prefer one-file-per-skill ToolSets, aggregated at conversation creation). Adapt with `tool(...)`, attach at `ConversationConfig` creation, set `automaticToolCalling=true` iff ≥1 tool enabled.
**When to use:** Local path, every turn where `ModelAllowlistRepository.supportsFunctionCalling(model)` is true AND ≥1 skill enabled.
**API facts [VERIFIED: 0.17.1 AAR javap + official docs https://developers.google.com/edge/litert-lm/android]:**
- `interface ToolSet` (marker); methods annotated `@Tool(description=…)`; params `@ToolParam(description=…)`; supported param types `String, Int, Boolean, Float, Double, List thereof`; nullable = optional; defaults allowed (document default in description).
- `ToolKt.tool(ToolSet): ToolProvider` and `tool(OpenApiTool): ToolProvider` (top-level `com.google.ai.edge.litertlm.tool`).
- `ConversationConfig(tools: List<ToolProvider>, automaticToolCalling: Boolean, …)` — named args from Kotlin.
- Manual fallback: `responseMessage.toolCalls: List<ToolCall(name, arguments: Map)>`, execute app-side, reply `Message.tool(Contents.of(Content.ToolResponse(name, responseJson)))`.
- Thought channel: `response.channels["thought"]` — never interleave into answer Deltas (already handled by parseThinkBlocks discipline).

```kotlin
// Source: https://developers.google.com/edge/litert-lm/android (Defining Tools with Kotlin Functions)
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import com.google.ai.edge.litertlm.tool

class CalculatorToolSet(
    private val events: ToolEventSink,   // posts start/finish for toolCallActive UI
    private val evaluate: (String) -> ToolResult, // pure fn, injected for JVM tests
) : ToolSet {
    @Tool(description = "Evaluate an arithmetic expression and return the result as a string.")
    fun calculator(
        @ToolParam(description = "Arithmetic expression using + - * / ( ) and decimals, e.g. (2+3)*4. Max 200 chars.") expression: String,
    ): String {
        events.onStart("calculator")
        return try {
            when (val r = evaluate(expression)) {
                is ToolResult.Success -> r.text
                is ToolResult.Failure -> "Error: ${r.reason}" // never throw across JNI
            }
        } catch (e: Exception) {
            "Error: ${e.message ?: "evaluation failed"}"     // belt-and-braces
        } finally {
            events.onFinish("calculator")
        }
    }
}

// At conversation creation (LiteRTLmProvider Step 6 site, currently tools=emptyList()):
val conversationConfig = ConversationConfig(
    initialMessages = historyMessages,
    samplerConfig = samplerConfig,
    extraContext = emptyMap(),
    tools = enabledToolSets.map { tool(it) },          // fresh instances per conversation
    automaticToolCalling = enabledToolSets.isNotEmpty()
)
```

**Wiring-order rules (agent's discretion, decided here):**
1. Resolve enabled skills + gating BEFORE building `ConversationConfig` (config applies only at creation — SUMMARY.md confirmed).
2. **Fresh ToolSet instances per conversation creation** (never reuse across conversations — mutable `events` sink + engine-wedge residue; PITFALLS tradeoffs table).
3. If enabled set changes mid-chat → `resetConversation()` so the next `chat()` rebuilds config (LRT-02 reuse interaction documented in SUMMARY).
4. If gating says no-support → build config with `tools=emptyList(), automaticToolCalling=false` AND inject the prompt-injection fallback (skill one-liners in system message — v2.0 behavior preserved).

### Pattern 2: Shared Skill→Schema Mapper (SKILLS-09)

**What:** One source of truth per skill: a `SkillDescriptor(id, name, description, params: List<ParamSpec>)` where `ParamSpec(name, type: JsonType, description, required, maxLength?)`. Two pure projections: (a) the `@ToolParam(description)` strings are written to MATCH the descriptor (verified by a unit test that reflects over the ToolSet methods and asserts descriptions equal the descriptor — prevents drift by construction); (b) `toOpenAiParameters(): JsonObject` builds `{"type":"object","properties":{…},"required":[…]}`.
**Why:** LiteRT generates schema by reflection while LM Studio needs explicit JSON — the descriptor + reflection-assert test is the only no-drift guarantee.

```kotlin
// Projection (b) — kotlinx.serialization, no new deps:
fun SkillDescriptor.toOpenAiParameters(): JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        params.forEach { p ->
            putJsonObject(p.name) {
                put("type", p.jsonType)          // "string" | "number" | "boolean" | "array"
                put("description", p.description)
                p.itemsType?.let { putJsonObject("items") { put("type", it) } }
            }
        }
    }
    putJsonArray("required") { params.filter { it.required }.forEach { add(it.name) } }
}
// → LmStudioToolFunction(name, description, parameters) — reuses LmStudioTool/OpenAiTool DTOs
```

### Pattern 3: Remote Tool Loop over `/v1/chat/completions` (SKILLS-10)

**What:** New provider method `chatCompletionsWithTools(request, tools, onCallCreated): Flow<StreamToken>` on `LMStudioProvider`, driven by `LmStudioHelper.runInference` when skills are enabled (helper owns the loop so phase-46 cancel/transport contracts stay in one place; or a dedicated `LmStudioToolLoop` class constructed per turn — prefer a small dedicated class `data/remote/provider/LmStudioToolLoop.kt` taking provider-factory + executor, to keep `LMStudioProvider` from bloating).

**Request path (must be verified against LM Studio server at plan time — endpoint shape is OpenAI standard):**
- `POST {baseUrl}/v1/chat/completions`, body `OpenAiChatRequest(model, messages, stream=true, tools=[{type:function,…}], tool_choice="auto")`.
- NOTE: `OpenAiMessage` currently `(role, content)` only — plan must add `tool_call_id: String? = null` and `tool_calls: List<OpenAiNonStreamingToolCall>? = null` fields (both nullable, `encodeDefaults=false`-safe) for the assistant-tool-call echo and `role:tool` re-POST.
- `LmStudioApi` (Retrofit) has no completions endpoint — use the 46-01 raw-OkHttp `client.newCall(Request…)` pattern with the same `x-api-key` interceptor client. Expose the shared `OkHttpClient` (currently `private val client`) via internal accessor or build the loop inside `LMStudioProvider` where `client` is visible — prefer loop-inside-provider private method + public entry, mirroring existing structure.

**Streaming accumulator (agent's discretion, decided here):**
```kotlin
// Per round: keyed by OpenAiStreamToolCall.index
data class PendingToolCall(
    var id: String? = null,
    var name: StringBuilder = StringBuilder(),
    var args: StringBuilder = StringBuilder(),
)
val pending = mutableMapOf<Int, PendingToolCall>()
// On each OpenAiStreamChunk: delta.toolCalls?.forEach {
//   val p = pending.getOrPut(it.index) { PendingToolCall() }
//   it.id?.let { id -> p.id = id }; it.function?.name?.let(p.name::append)
//   it.function?.arguments?.let(p.args::append) }
// Terminal: choice.finishReason == "tool_calls" → materialize pending (sorted by index).
// ALSO handle non-streaming: OpenAiNonStreamingResponse.choices[].message.toolCalls (DTOs exist).
// Malformed (blank name / args not JSON object) → treat as plain content (fallback), never crash.
```

**Loop pseudocode:**
```
messages = [system?, ...history(user/assistant text only), current user]
round = 0
loop {
  ensureActive()                                   // 46-01 hook contract
  check(round >= MAX_TOOL_ROUNDS /* 5 */) { break with fallback }
  call = newCall(POST /v1/chat/completions {messages, tools})
  onCallCreated(call)                              // helper retains for stopResponse
  accumulate SSE until finish_reason or [DONE]
  if finish_reason == "tool_calls" && valid calls.nonEmpty {
     emit toolCallActive(name) via StreamToken? — see below
     round++
     results = calls.map { executor.execute(validated) }   // Dispatchers.Default, pure fns
     messages += assistant(tool_calls echo) + results.map { role=tool, tool_call_id, content=summary }
     persist minimal rows (VM-side on Done — see transcript section)
     continue                                       // re-POST
  } else break (stream content Deltas as usual)
}
```

**UI signaling choice:** The existing contract sets `toolCallActive` from `[tool:NAME]` text markers. For the new loop, do NOT inject fake text markers into content (they'd persist). Instead extend `StreamToken` with `data class ToolStatus(val toolName: String?) : StreamToken` (null = clear) — small additive change, ViewModel maps to `toolCallActive`. Keep the old regex path untouched for the native-protocol provider (still used for non-tool chat).

**Cancellation:** `ensureActive()` at loop top + after each `execute` + `>=` cap check; each round creates a new `Call` and re-registers `onCallCreated` (helper's `activeCall` always points at the live round); `stopResponse()` → `Call.cancel()` → in-flight `readUtf8Line()` throws `IOException("Canceled")` → silent. Partial text already emitted stays (UI-SPEC §3).

### Pattern 4: Transcript Persistence (SKILLS-11)

**Decision (persist-vs-ephemeral, decided here): persist minimal rows.** Each executed tool persists one `messages` row: `role="tool"`, `content="Used {Display}: {summary ≤200 chars}"`. This satisfies "auditable and resumable": history reload shows the collapsed rows per UI-SPEC §5, and a resumed conversation can re-send tool summaries as context.

**Blocker resolved:** `EntityMappers.toDomain()` does `Role.valueOf(role)` → `role="tool"` crashes today. Plan must add `Role.TOOL` to `domain/model/Role.kt` and audit every `when(role)` / `role ==` site (provider history mapping in `LiteRTLmProvider:113-119` — LiteRT `Message.tool()` vs `Message.model()` mapping needed for resumed history; `LMStudioProvider:127-133` native items; `ChatRepositoryImpl`; UI alignment `isUser` checks). Alternatively persist as ASSISTANT with `"[tool:calculator] …"` prefix — rejected (prefix leaks into model context on resume and collides with the status-marker regex).

- Resumed-history mapping: local → `Message.tool(Contents.of(Content.ToolResponse(name, summary)))` is available [VERIFIED: AAR]; remote native path has no tool history concept — send summaries as plain text items; remote completions path re-sends `role:tool` messages (the DTO extension above).
- Summarization: `ToolResult.Success(summary)` truncated to ~200 chars + "…" at the executor boundary (UI-SPEC §5), never raw JSON dumps.

### Pattern 5: Per-Model Gating + Fallback (LRT-08)

- Gate: `ModelAllowlistRepository.supportsFunctionCalling(modelName)` — currently false-everywhere by the verified-only rule [VERIFIED: ModelAllowlistRepository.kt:93-95 + asset]. Remote: `LmStudioCapabilities.trainedForToolUse` from `/api/v1/models` list response [VERIFIED: LmStudioDtos.kt:82-85] OR-ed with allowlist.
- No-support UX: exact string `"This model doesn't support tools — answering directly."` (UI-SPEC §6), once per turn, then normal answer. Zero-skills-enabled → no notice, no tools[] sent.
- Qwen3/Gemma: stay on prompt-injection fallback until on-device re-test on 0.17.1 passes (cannot verify from this environment — no device). Plan must include a manual smoke-test checklist (airplane-mode calculator test per CONTEXT specifics) and must NOT flip any `supportsFunctionCalling` flag without device evidence (verified-only rule).
- Official docs note tool calling "only works with models with tool support, e.g. FunctionGemma" [CITED: developers.google.com/edge/litert-lm/android] — gating is engine-sanctioned, not just app caution.

### Pattern 6: Progress / Error Wiring (SKILLS-12)

- Status: existing `ChatScreen:287-306` row reused; text mapping centralized in `toolDisplayName()` (`calculator→"calculator"`, `current_time→"current time"`, `json_formatter→"JSON formatter"`, format `"Using {display}…"` with U+2026) [per UI-SPEC §3].
- Error: `ToolResult.Failure(reason)` → error row `"{Display} failed: {reason}"` + assistant fallback text continues (ViewModel guarantees non-blank — if model goes silent after tool error, synthesize `"I couldn't complete that calculation, but …"`? NO — planner: fallback means the loop's final content; if empty, re-POST once without tools to get a plain answer rather than synthesizing fake text).
- Executor behind `ToolExecutor` interface (confirmation gate later = decorator, no rewiring).

### Anti-Patterns to Avoid
- **Reusing one ToolSet instance across conversations:** leaks event-sink state; fresh instances per `createConversation` [per PITFALLS tradeoffs].
- **Throwing from @Tool bodies:** crosses JNI as `LiteRtLmJniException`, kills conversation — always error-map [CITED: official docs return-type contract + PITFALLS Critical 1].
- **Blocking I/O in @Tool bodies:** engine executes inline on callback thread — tools are pure CPU-trivial only (calculator/time/json all qualify).
- **`@ToolParam` types beyond String/Int/Boolean/Float/Double/List:** silently broken schema [CITED: official docs Parameter Types].
- **Sending tools[] in the wrong dialect** (native `/api/v1/chat` `tools` silently ignored): remote loop MUST use `/v1/chat/completions` [per SKILLS-10 + PITFALLS Critical 3].
- **Rebuilding request bodies from unsanitized fields** in the loop: reuse the sanitized content path (threat T-46-01 comment on `LMStudioProvider.chat`).
- **`check(round > MAX)` instead of `>=`:** off-by-one lets a 6th round run — 46-01 hook contract mandates `>=` semantics.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Tool JSON schemas | String-templated JSON | `kotlinx.serialization.buildJsonObject` in shared mapper + golden test | Typos in `type:function` shape are silently ignored by server (PITFALLS Critical 3) |
| SSE `tool_calls` reassembly | Ad-hoc string concat | Index-keyed `PendingToolCall` accumulator (Pattern 3) | Arguments stream split across chunks; naive concat mixes parallel calls |
| Arithmetic evaluation | `eval()`/ScriptEngine (unavailable + unsafe) | In-tree shunting-yard (~100 lines, no deps) | Zero-new-dep constraint; must cap input length, reject non-math chars, guard div-by-zero/overflow — all unit-tested |
| Timezone formatting | `DateFormat.getInstance()` default locale | `java.time` with explicit `ZoneId.systemDefault()` + fixed pattern | Default-locale formats break tool-result determinism across devices |
| Cancellation | Custom flags/Job sentinels | `ensureActive()` + `Call.cancel()`/`cancelProcess()` (Phase 46) | 46-01 removed the sentinel pattern; reuse the transport handles |

**Key insight:** The engine owns local execution; the app owns validation, mapping, and the remote loop. Every custom line should live at those seams — never reimplement engine or protocol behavior.

## Common Pitfalls

### Pitfall 1: `ConversationConfig.tools` applies only at creation + long-lived conversation reuse
**What goes wrong:** Enabling/disabling a chip mid-chat has no effect (or worse, stale tools fire).
**Why it happens:** `LiteRTLmProvider` reuses one `activeConversation` across turns (LRT-02); config is read at `createConversation` only.
**How to avoid:** Chip toggle → `SkillRepository` update → if conversation alive, `resetConversation()` (cheap, keeps engine loaded). Document in plan.
**Warning signs:** Tool fires after its chip was turned off.

### Pitfall 2: `Role.valueOf("tool")` crash on history load
**What goes wrong:** `IllegalArgumentException` when opening any conversation containing tool rows.
**Why it happens:** `EntityMappers.toDomain()` does `Role.valueOf(role)`; `Role` lacks `TOOL`.
**How to avoid:** Add `Role.TOOL` + audit all `when(role)` sites in the same plan (compiler enforces exhaustiveness — use it).
**Warning signs:** Crash on `selectConversation` after a tool turn.

### Pitfall 3: SSE `arguments` split across chunks parsed prematurely
**What goes wrong:** `Json.parse` on partial args → exception → tool silently skipped or turn fails.
**Why it happens:** `function.arguments` deltas are fragments; only complete at `finish_reason`.
**How to avoid:** Append-only accumulator; parse ONCE at terminal reason; malformed → content fallback.
**Warning signs:** Intermittent tool failures under slow networks (chunk splits vary).

### Pitfall 4: Local tool execution invisible to UI (automatic mode emits no events)
**What goes wrong:** Status row never shows; transcript rows never persist; "Using calculator…" requirement fails on local path.
**Why it happens:** `automaticToolCalling=true` executes inside the engine; `Flow<Message>` carries only text.
**How to avoid:** `ToolEventSink` callbacks inside `@Tool` bodies (non-blocking post) → provider/VM collects → `toolCallActive` + transcript capture.
**Warning signs:** Remote shows progress, local doesn't.

### Pitfall 5: Qwen/Gemma template wedge treated as ordinary error
**What goes wrong:** Retry loop hammers a wedged engine; every subsequent turn hangs with zero callbacks until process kill (PITFALLS Critical 2).
**Why it happens:** Qwen 2.5 post-tool wedge survives `createConversation`; only gating prevents it.
**How to avoid:** Gating defaults closed; on `LiteRtLmJniException` mentioning `tool_response`/template → degrade turn to no-tools retry ONCE, then prompt-injection fallback for the session; never infinite retry.
**Warning signs:** Zero callbacks after a tool turn on Qwen-family models.

### Pitfall 6: Tool results fed back as trusted content (prompt injection via tool output)
**What goes wrong:** Malicious/garbage tool text steers the next turn (HARD-02 violation).
**Why it happens:** Results concatenated raw into `role:tool` messages without caps.
**How to avoid:** Truncate (~200 chars transcript; cap re-POST content, e.g. 2000 chars), treat as untrusted text, sanitize control chars; log redaction (no raw args in release logs — HARD-01 adjacent).

## Code Examples

### Shared descriptor → OpenAI parameters (SKILLS-09)
```kotlin
// Source: kotlinx.serialization docs pattern; shape per OpenAI function-calling
// (type/function/name/description/parameters) + existing OpenAiTool/LmStudioTool DTOs
val calcDescriptor = SkillDescriptor(
    id = "calculator", name = "calculator",
    description = "Evaluate an arithmetic expression.",
    params = listOf(ParamSpec("expression", "string",
        "Arithmetic expression using + - * / ( ) and decimals. Max 200 chars.",
        required = true, maxLength = 200))
)
// Remote: OpenAiTool(type = "function",
//   function = OpenAiFunctionDef(name, description, calcDescriptor.toOpenAiParameters()))
```

### Remote SSE tool_calls detection (streaming)
```kotlin
// Source: OpenAI chat-completions chunk protocol; DTOs OpenAiStreamChunk/OpenAiStreamToolCall (tree)
val chunk = json.decodeFromString<OpenAiStreamChunk>(dataLine)
chunk.choices.firstOrNull()?.let { choice ->
    choice.delta?.toolCalls?.forEach { tc ->
        val p = pending.getOrPut(tc.index) { PendingToolCall() }
        tc.id?.let { p.id = it }
        tc.function?.name?.let(p.name::append)
        tc.function?.arguments?.let(p.args::append)
    }
    choice.delta?.content?.let { emit(StreamToken.Delta(it)) }
    if (choice.finishReason == "tool_calls") materializeAndExecute(pending)
}
```

### role:tool re-POST message (requires OpenAiMessage extension)
```kotlin
// Plan must extend: OpenAiMessage(role, content, toolCallId? = null, toolCalls? = null)
OpenAiMessage(role = "assistant", content = "", toolCalls = roundCalls) // echo
OpenAiMessage(role = "tool", content = resultSummary, toolCallId = callId)
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| litertlm 0.13.1 tool API | 0.17.1: same Tool/ToolSet/automaticToolCalling/Message.tool surface + ThinkingConfig, maxOutputToken, ResponseFormat, repetition configs, Embedding API | 0.14–0.17 (pinned 0.17.1, 2026-09-16) | LRT-08 = adopt fixes; tool registration code is version-stable, thinking/output-length knobs are new |
| Prompt-injection skills (v2.0) | Real `@Tool` + tools[] loop with injection kept as fallback | This phase | Capable models compute; Qwen3/Gemma stay on fallback until device-verified |
| Native `/api/v1/chat` for everything remote | Tool loop on OpenAI-compatible `/v1/chat/completions` | This phase (SKILLS-10 locked) | Parallel path; native provider untouched for plain chat |

**0.13.1→0.17.1 delta [VERIFIED: javap diff of both AARs]:** Tool surface (`Tool`, `ToolParam`, `ToolSet`, `ToolKt.tool×2`, `ReflectionTool`, `Content.ToolResponse`, `Message.tool`, `ConversationConfig.getTools/getAutomaticToolCalling`) exists in BOTH versions. New in 0.17.1: `ThinkingConfig`, `ResponseFormat`, `RepetitionPenaltyConfig`, `NoRepeatNgramConfig`, `SuppressTokensConfig`, `SamplerParameters`, `SupportedModalities`, `Backend$GOOGLE_TENSOR`, `Embedding*`. LRT-08 "adopt 0.14–0.17 tool-calling fixes" therefore means behavior fixes (int-type tool-call args, streaming tool-call tokens — per REQUIREMENTS.md, sourced from release notes/issues), not API migration; no registration-code rewrite needed for the bump.

**Qwen3/Gemma bugs:** version-sensitive upstream reports (empty `<tool_response>`, missing `type: tool_response`, Qwen 2.5 post-tool wedge — PITFALLS Critical 2, MEDIUM confidence). Currency against 0.17.1 unverifiable here — on-device re-test required, gating stays closed until then.

**Deprecated/outdated:**
- `applySkills` prompt-injection-only path (v2.0 `LmStudioHelper.applySkills` referenced in PITFALLS) — kept ONLY as non-capable-model fallback, never the primary path.
- `integrations` overload for passing tools — wrong field (MCP-only); use dedicated `tools` param.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | LM Studio server exposes OpenAI-compatible `POST /v1/chat/completions` with `tools[]` + streaming `tool_calls` deltas + `role:tool` messages | Remote Loop | HIGH — if the target LM Studio version lacks it, remote tools impossible; plan must verify with a live-server probe first and keep native-protocol graceful degradation |
| A2 | `tool_choice="auto"` default acceptable (omit field; OpenAiChatRequest.toolChoice null) | Remote Loop | LOW — server default is auto; explicit `"auto"` can be added if probe shows tools ignored |
| A3 | 0.14–0.17 fixed the int-arg/streaming tool-call bugs on the pinned build (release-note claim in REQUIREMENTS LRT-08) | State of Art | MEDIUM — device smoke test per skill gates this; fallback covers failure |
| A4 | Engine invokes `@Tool` on a thread where non-blocking `SharedFlow.tryEmit`/callback post is safe | Pitfall 4 | MEDIUM — if engine thread forbids callbacks, fall back to manual mode for status; tool execution itself unaffected |
| A5 | `supportsFunctionCalling` stays false for all allowlist models until device test (no flag flips in this phase's code) | Gating | LOW — plan writes the gating plumbing + probe checklist, not the verdict |

## Open Questions

1. **Does the target LM Studio server version support `/v1/chat/completions` tool_calls?**
   - What we know: DTOs assume OpenAI protocol; SKILLS-10 locks the endpoint. `LmStudioCapabilities.trainedForToolUse` exists on the native models API.
   - What's unclear: server version in the field; exact `tool_calls` streaming shape (OpenAI `delta.tool_calls[]` vs native `tool_call.*` events).
   - Recommendation: Plan 1 includes a live-probe task (curl `/v1/chat/completions` with tools[]); loop code parses OpenAI shape primary, keeps native `[tool:NAME]` markers as-is.

2. **Which local models pass the tool smoke test on 0.17.1?**
   - What we know: allowlist is all-false by verified-only rule; FunctionGemma-class models are the docs-sanctioned tool models.
   - What's unclear: everything until a device runs it.
   - Recommendation: ship gating + fallback; device checklist in plan; no flag flips without evidence.

## Environment Availability

No external services, tools, or runtimes beyond the existing Android/Gradle toolchain are required (code + in-tree tests only). Toolchain presence is assumed from Phases 45–46 completion (same machine, same checkout).

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Android SDK + Gradle | build/test | ✓ (assumed — 45/46 built) | — | — |
| Device/emulator + .litertlm model | tool smoke test | ✗ (not in this env) | — | Manual checklist for human; gating defaults closed |
| LM Studio server | loop probe | ✗ (not in this env) | — | MockWebServer chunk-split tests in JVM suite |

## Test Strategy

Existing suite shape: JVM unit tests under `app/src/test/java/com/warped/` (dto, provider, repository, inference, ui/chat), JUnit5 + Truth + MockK + Turbine in catalog [VERIFIED: tree]. No new test deps.

| Requirement | Test | Type |
|-------------|------|------|
| HARD-02 | Each tool fn: valid compute + every invalid input class (empty, oversize, malformed, div-zero, bad-tz) → error-mapped, never throws | JVM unit (direct fn calls, no engine) |
| SKILLS-09 | Golden JSON for `toOpenAiParameters()` per skill + reflection assert that `@ToolParam` descriptions == descriptor | JVM unit |
| SKILLS-08 | `tool(CalculatorToolSet())` wiring smoke is device-only; JVM covers config-builder (tools list ↔ enabled chips, auto flag) with fakes | JVM unit + device checklist |
| SKILLS-10 | MockWebServer: stream `tool_calls` split across chunks → assert accumulator materializes + follow-up re-POST contains `role:tool`; malformed-call → content fallback; 5-round cap + cancel between rounds | JVM unit (OkHttp MockWebServer is already a test dep? — VERIFY at plan time; else hand-rolled `MockWebServer` via `okhttp3.mockwebserver` catalog check, still zero-new-prod-dep) |
| SKILLS-12 | ViewModel-level: ToolStatus tokens → toolCallActive set/cleared; Failure → error row state + non-blank fallback | Turbine flow test |
| LRT-08 | Per-model smoke checklist (calculator airplane-mode, time, json) on-device | Manual, recorded in plan UAT |

## Security Domain

`security_enforcement` is not disabled in `.planning/config.json` (absent → enabled); Validation Architecture section skipped (`workflow.nyquist_validation: false` explicit).

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | API keys unchanged (ApiKeyStore + x-api-key interceptor reused as-is) |
| V3 Session Management | No | — |
| V4 Access Control | Partial | Unknown tool names from model are never executed — allowlisted skill IDs only; error-string `role:tool` reply |
| V5 Input Validation | **Yes** | Arg validation per tool (length caps, charset allowlist for calculator, JSON size cap ~64KB, tz allowlist); results truncated + control-char stripped before re-POST |
| V6 Cryptography | No | — |
| V14 Configuration | Partial | R8 keep rules already cover `ToolSet`/`OpenApiTool`/`@Tool`/`@ToolParam`/`ReflectionTool`/`ToolKt`/`Capabilities` (proguard-rules.pro:30-36) [VERIFIED: tree] — plan must add keeps for new `data/skills/*` + `domain/skills/*` classes |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Prompt injection via tool results | Tampering / Elevation | Truncate + sanitize results; treat as untrusted text; never log raw args at release (BASIC logging only, per T-46-02) |
| Malicious `tool_calls` name/args from remote model | Tampering | Name allowlist check; args schema-validate before execute; unknown → error-string reply, no crash |
| Calculator expression DoS (pathological input) | Denial of service | 200-char cap, recursion/iteration bound in evaluator, pure-CPU budget; runs on Dispatchers.Default, never Main |
| Tool-error text leaking PII/paths | Information disclosure | Sanitized short reasons only (`"invalid expression"`, not echoes of input); no stack traces to UI or logs |

## Sources

### Primary (HIGH confidence)
- litertlm-android-0.17.1 AAR bytecode (`javap`: Tool, ToolParam, ToolSet, ToolKt, ToolProvider, ReflectionTool, ConversationConfig, Conversation, Message+Companion, ToolCall, Content.ToolResponse, ToolManager, ThinkingConfig, Channel, Role, Engine) — extracted from Gradle cache AAR
- litertlm-android-0.13.1 AAR bytecode diff (tool surface present in both; 0.17.x additions enumerated)
- https://developers.google.com/edge/litert-lm/android (fetched 2026-09-28; page updated 2026-09-04) — @Tool/@ToolParam/ToolSet/tool() registration, param/return-type contracts, automatic vs manual tool calling, Message.tool + Content.ToolResponse pattern, thought channel, FunctionGemma tool-support note
- Warped tree ground truth: LiteRTLmProvider.kt (Step 6 tools=emptyList() insertion point, sendContentsWithRetry, extractThoughtContent), LiteRTLmEngine.kt (45-02 comment), LMStudioProvider.kt (46-01 raw-Call contract + Phase-47 hook comments), LmStudioHelper.kt, LiteRtLlmHelper.kt, ChatViewModel.kt (toolCallActive, parseThinkBlocks, shareIn collection), ChatScreen.kt:287-306 (status row), ChatInputBar.kt, MessageBubble.kt:88-146 (Thinking panel pattern), MessageEntity.kt + EntityMappers.kt (`Role.valueOf` finding), ModelAllowlistRepository.kt + model_allowlist.json (all-false gating), LmStudioDtos.kt / StreamChunks.kt / OpenAiChatRequest.kt (DTO inventory), AdvancedPreferences.kt (DataStore pattern), proguard-rules.pro (tool keeps present)

### Secondary (MEDIUM confidence)
- .planning/research/PITFALLS.md (Critical 1–3, tradeoffs table — corroborated by tree + docs where cited, version-sensitive bug reports not independently re-verified)
- .planning/research/ARCHITECTURE.md §§1–2 (insertion points — verified against current tree line numbers where cited)
- .planning/research/SUMMARY.md (LRT-02 conversation-reuse interaction, RECURRING_TOOL_CALL_LIMIT=25 reference)

### Tertiary (LOW confidence)
- None — no web search needed; AAR + official docs + tree closed every question except device/server behavior (flagged in Open Questions).

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — AAR bytecode + pinned catalog + tree.
- Architecture: HIGH — insertion points verified line-by-line; one structural decision (new completions path vs native reuse) forced by protocol evidence.
- Pitfalls: HIGH for code-level (Role.valueOf, accumulator, config-at-creation), MEDIUM for model-family bugs (version-sensitive, device-gated).

**Research date:** 2026-09-28
**Valid until:** 30 days (stable APIs; re-verify only if litertlm bumps or LM Studio server version changes)
