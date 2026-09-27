# Stack Research

**Domain:** Warped v2.1 — real tool execution (LiteRT-LM + LM Studio) + Compose perf completion
**Researched:** 2026-09-27
**Confidence:** HIGH

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| litertlm-android | 0.13.1 (KEEP — do not bump) | Local tool execution via `ToolSet`/`@Tool`/`@ToolParam` | Tool API (`ToolSet`, `@Tool`, `@ToolParam`, `ConversationConfig(tools=…, automaticToolCalling=…)`) ships inside the already-pinned artifact — SKILLS-02 needs zero new dependencies. 0.13.1 verified on Maven Central (Jun 04 2026); 0.14.0 exists but is unverified against the Gallery reference, and the catalog pins 0.13.1 deliberately. |
| Retrofit + OkHttp DTOs (kotlinx-serialization) | Retrofit 3.0.0 / OkHttp 4.12.0 / kotlinx-serialization-json 1.7.3 (all KEEP) | LM Studio `tools[]` request/response mapping | LM Studio tool use is plain OpenAI function-calling JSON over the existing `/v1/chat/completions` endpoint — new `@Serializable` DTOs (`ToolDefinition`, `ToolCall`, `ToolMessage`) on the existing stack, no new library. |
| kotlinx-coroutines-core/android | 1.9.0 (KEEP) | `shareIn` inference-stream refactor + `Call.cancel()` coroutine plumbing | `shareIn`/`SharingStarted` and `suspendCancellableCoroutine`/`invokeOnCancellation` are stdlib coroutines — the double-collect fix and cancel plumbing are pure code changes, no new artifacts. |
| kotlinx-collections-immutable | 0.4.0 (KEEP; 0.5.0 stable exists — optional bump) | Stable `ImmutableList<Message>` for keyed LazyColumn + sub-state split | Compose compiler treats `ImmutableList`/`PersistentList` as stable, making message-list params skippable. 0.4.0 already in catalog (RUNTIME-09); upstream changelog shows 0.5.0 promoted to stable — bump only after verifying in catalog, not required for v2.1. |
| Turbine | 1.1.0 (KEEP) | Testing the shared inference flow and tool-call emissions | `flow.test { awaitItem() }` covers `shareIn` replay semantics and tool-call parsing without new test deps. |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| (none new) | — | — | Every v2.1 work item resolves to code patterns on existing artifacts. This is deliberate: RUNTIME-12 audit budget stays intact. |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| Compose compiler metrics (`composeCompilerReports`) | Verify sub-state split actually yields `skippable` composables | Enable `stabilityConfigurationFile` + metrics on release build; target: message-bubble composables report `restartable skippable` with `stable` params. Debug builds enable Live Literals — always measure on release. |
| `lms log stream` | Inspect rendered prompt/tool-call parsing on LM Studio side | LM Studio docs recommend this to debug models that emit malformed tool calls (default tool-use fallback format is visible in logs). |

## Installation

```kotlin
// gradle/libs.versions.toml — NO CHANGES REQUIRED for v2.1
// litertlm = "0.13.1"                        ✅ covers SKILLS-02 ToolSet API
// kotlinx-collections-immutable = "0.4.0"     ✅ covers PERF-01/PERF-06 stability
// okhttp = "4.12.0", retrofit = "3.0.0"       ✅ covers SKILLS-03 + Call.cancel()
// coroutines = "1.9.0", turbine = "1.1.0"     ✅ covers shareIn refactor + tests

// Optional (verify in catalog first):
// kotlinx-collections-immutable = "0.5.0"  // upstream stable per CHANGELOG; 0.4.0 suffices
```

## Integration Points

### SKILLS-02 — LiteRT-LM `@Tool` registration (no new dep)

API surface in `litertlm-android:0.13.1` (verified via Google AI Edge docs, published 2026-09-04):

```kotlin
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam

class WarpedToolSet @Inject constructor(
  private val repo: SomeRepository,
) : ToolSet {
  @Tool(description = "…what it does, when to call it…")
  fun doSomething(
    @ToolParam(description = "…") arg: String,
    @ToolParam(description = "…") opt: String? = null,
  ): Map<String, Any> { … }
}

val conversation = engine.createConversation(
  ConversationConfig(
    tools = listOf(tool(WarpedToolSet(…))),  // `tool(…)` wrapper, not raw instance
    automaticToolCalling = true,             // default: engine executes + feeds back
  )
)
```

- **Prefer `automaticToolCalling = true`.** Manual mode (`false` + `sendMessage(Message.tool(…))` with `Content.ToolResponse`) is the escape hatch for custom execution/sandboxing only.
- **Supported param types:** primitives, `String`, nullable/defaulted params, `List<Double>`; return `Map<String, Any>` / `Double` / JSON-serializable. Schema is generated from annotations + signature (OpenAPI-style) — descriptions are load-bearing, write them carefully.
- **ProGuard:** already covered — `app/proguard-rules.pro` keeps `com.google.ai.edge.litertlm.**` + `ToolProvider` (RUNTIME-11). New `@Tool` classes need no extra rules, but verify annotated methods survive R8 fullMode (`enableR8.fullMode=true`) — annotation retention is the risk point.
- **Model caveat (HIGH importance):** tool-response serialization is model-processor-dependent. Upstream issue #1027 documents empty `<tool_response>` + template-mismatch crash on Qwen3 native tool calling. **Validate SKILLS-02 against Gemma tool-capable models first** (FunctionGemma / Gemma function-calling path is the reference implementation); treat Qwen3 tool use as device-test-gated.

### SKILLS-03 — LM Studio `tools[]` DTO mapping (no new dep)

Endpoint choice is the critical decision (verified via LM Studio docs):

- **USE `POST /v1/chat/completions` (OpenAI-compatible).** Accepts standard `tools: [{type: "function", function: {name, description, parameters}}]`, returns `choices[0].message.tool_calls` with `finish_reason: "tool_calls"`. Streaming sends `delta.tool_calls[i].function.{name,arguments}` chunks — **accumulate by `index`** (`id`/`name`/`arguments` arrive piecemeal).
- **DO NOT use `POST /api/v1/chat` (native v1) for custom tools.** Its `integrations` field supports MCP servers/plugins only — no custom function tools. (This also respects the RUNTIME-12 `mcp` ban: stay on the OpenAI-compat surface.)

```kotlin
@Serializable
data class ToolFunctionDef(
  val name: String,
  val description: String,
  val parameters: JsonObject,          // JSON Schema; strict = additionalProperties false
  val strict: Boolean = true,
)
@Serializable
data class ToolDefinition(val type: String = "function", val function: ToolFunctionDef)

@Serializable
data class ToolCallRequest(
  val id: String,
  val type: String = "function",
  val function: ToolCallFunction,       // { name, arguments: String (raw JSON) }
)
// Follow-up turn appends: assistant message (with tool_calls) + {"role":"tool","tool_call_id":…,"content":…}
```

- Tool-execution loop is app-side (model only *requests*): parse `tool_calls` → dispatch to the same executors as SKILLS-02 → append results → re-POST. Cap loop iterations (e.g. 5) to bound runaway calls.
- Reuse the existing `ResponseBody.readUtf8Line()` SSE reader; extend the `data:` line parser for `delta.tool_calls` chunks rather than adding a new streaming path.

### PERF-01 — ChatUiState sub-state split (no new dep)

Pattern (per Chanzmao 2026-08 + official stability docs):

```kotlin
@Immutable
data class ChatUiState(
  val header: ChatHeaderState,   // model/endpoint picker, connection status
  val list: ChatListState,       // messages: ImmutableList<MessageUi>, scroll effects
  val input: ChatInputState,     // draft text, send enabled, attached skill
)
```

- Split rule: group by recomposition frequency (streaming tokens ≠ input keystrokes ≠ header status). Each section composable collects **only its sub-state** (`collectAsStateWithLifecycle()` per section) so token streaming never recomposes the input bar.
- Keep `@Immutable` on every sub-state; message list must be `ImmutableList` (see below). UI-local state (LazyListState, focus, text field) stays in composition, not in the ViewModel.
- Selective `StateFlow` split is allowed if sections are genuinely independent — but default to one `StateFlow<ChatUiState>` with sub-state field selection; per-section collection already scopes recomposition.

### PERF-06 — LazyColumn keyed lists (no new dep; 0.4.0 suffices)

```kotlin
LazyColumn(state = listState, reverseLayout = true) {
  items(
    items = state.list.messages,          // ImmutableList<MessageUi>
    key = { it.id },                      // stable DB/UUID id — NEVER index
    contentType = { it.contentType },     // "sent_text" | "received_text" | "code" | "tool_call" …
  ) { msg ->
    MessageBubble(msg = msg, modifier = Modifier.animateItem())
  }
}
// Scroll-derived UI via derivedStateOf — never read firstVisibleItemIndex directly:
val showJumpToBottom by remember { derivedStateOf { listState.firstVisibleItemIndex > 5 } }
```

- `key` enables item reuse across streaming appends (without it, every token re-creates visible compositions). `contentType` lets Compose recycle per bubble type. `animateItem()` for insert animation.
- Typing indicator and input bar live **outside** LazyColumn as sibling composables.

### runInference double-collect → `shareIn` (no new dep)

```kotlin
private val inferenceStream: SharedFlow<InferenceEvent> =
  buildInferenceFlow()
    .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)
```

- `WhileSubscribed(5000)` keeps upstream alive across rotation (~1–2 s) but stops when the screen is left; `replay = 1` gives late collectors the latest token batch. Multiple collectors (UI + logger/persistence) then share one upstream — the double-collect fix.
- Test with Turbine: two `test {}` collectors on the shared flow, assert single upstream execution.

### OkHttp `Call.cancel()` plumbing (no new dep)

- Hold the **raw `okhttp3.Call`** (from `client.newCall(request)` or Retrofit's underlying call), store in a cancellable holder (e.g. `AtomicReference<Call>` / `var activeCall`), and wire coroutine cancellation: `invokeOnCancellation { activeCall?.cancel() }` (or `awaitClose { call.cancel() }` in `callbackFlow`).
- Read-loop must catch `IOException`/`SocketException` from `cancel()` — per OkHttp maintainers, **always handle IOException on streaming reads**; cancel manifests as a socket exception, which is the *expected* stop path, not an error to surface.
- Close `ResponseBody` in `finally`. Do not shut down the shared `OkHttpClient.dispatcher()` — the client is shared with non-streaming calls.

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| Manual `BufferedSource` SSE parse + raw `Call` handle (existing) | `com.squareup.okhttp3:okhttp-sse` artifact | Never in v2.1 — new artifact for capability already implemented; `RealEventSource` cancel semantics have known shutdown quirks (okhttp#5544); audit budget prefers zero additions. |
| `automaticToolCalling = true` | Manual `automaticToolCalling = false` + `Message.tool()` | When tool execution needs sandboxing/confirmation UI (e.g. destructive ops) or custom result shaping per skill. |
| OpenAI-compat `/v1/chat/completions` for tools | Native `/api/v1/chat` + MCP integrations | Only if Warped later adopts MCP servers — currently banned by RUNTIME-12; revisit if audit changes. |
| `shareIn(WhileSubscribed(5000), replay=1)` | `stateIn` / `SharingStarted.Lazily` / `Eagerly` | `Lazily` if inference must survive screen exit (background continuation); `Eagerly` never — leaks inference without collectors. |
| kotlinx-collections-immutable 0.4.0 (pinned) | 0.5.0 stable | After catalog verification + full-list scroll regression check; cosmetic for v2.1. |
| litertlm-android 0.13.1 (pinned) | 0.14.0 (Maven Central, Jul 2026) | Only after Gallery-reference validation + regression pass; explicitly out of v2.1 scope. |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `com.squareup.okhttp3:okhttp-sse` | New dep for solved problem; cancel/shutdown quirks; audit cost | Existing manual SSE reader + raw `Call.cancel()` |
| MCP client SDK / `integrations` tool path | Banned by RUNTIME-12 (`mcp` in audit script); native endpoint lacks custom tools anyway | OpenAI-compat `tools[]` DTOs |
| Bumping `litertlm` to 0.14.0 | Unverified vs Gallery reference; catalog comment requires verification before release | Stay on 0.13.1; tool API is identical surface |
| `kapt`, `moshi`, `gson`, `kotlin-reflect`, `ktor`, `firebase` | Banned by RUNTIME-12 audit (`audit-dependencies.sh` fails build) | KSP + kotlinx-serialization (already in place) |
| `List<Message>` in UiState / `items(index)` without `key` | Compose marks `List` unstable → message list never skippable; index keys destroy item reuse on streaming append | `ImmutableList` + `key = { it.id }` + `contentType` |
| `POST /api/v1/chat` for SKILLS-03 | No custom-function-tools support (MCP integrations only) | `POST /v1/chat/completions` with `tools[]` |
| Closing shared `OkHttpClient.dispatcher()` on cancel | Kills unrelated in-flight calls (downloads, HF browsing) | Cancel the single `Call`; close only its `ResponseBody` |

## Stack Patterns by Variant

**If the skill executes a read-only/local operation (prompt templates, converters):**
- Use `automaticToolCalling = true` with direct `@Tool` methods — simplest path, engine handles the loop.

**If the skill executes a destructive or external operation (file write, network call):**
- Use `automaticToolCalling = false`, surface a confirmation UI, execute manually, return via `Message.tool(Contents.of(toolResponses))`.

**If the LM Studio model lacks native tool-use support:**
- Still send `tools[]` — LM Studio applies default tool-use formatting fallback. Verify parseability via `lms log stream`; if `tool_calls` never populates, degrade to PromptTemplate injection (Skills Lite path) for that model.

**If inference must continue with screen in background:**
- Switch `shareIn` started-param to `SharingStarted.Lazily` and scope to a longer-lived scope (repository/service scope, not `viewModelScope`), with explicit stop handle.

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| litertlm-android 0.13.1 | Kotlin 2.3.20, AGP 9.2.1, R8 fullMode | ProGuard keeps already in place (RUNTIME-11); verify `@Tool` annotation retention after any R8 rule change |
| retrofit 3.0.0 | converter-kotlinx-serialization 1.0.0, kotlinx-serialization-json 1.7.3 | Already paired in catalog; new tool DTOs ride this pair — no converter change |
| kotlinx-collections-immutable 0.4.0 | Compose BOM 2026.05.01 (compiler stability inference) | Compiler recognizes `ImmutableList` as stable; 0.5.0 stable upstream is an optional, verify-first bump |
| coroutines 1.9.0 | Turbine 1.1.0, coroutines-test 1.9.0 | `shareIn` replay semantics testable with existing Turbine version |
| OkHttp 4.12.0 | Retrofit 3.0.0, `Call.cancel()` contract | `cancel()` closes socket in-flight; read loop must expect `SocketException` as normal stop |

## Sources

- Google AI Edge — LiteRT-LM Android tool-use docs (developers.google.com/edge/litert-lm/android, 2026-09-04) — `ToolSet`/`@Tool`/`@ToolParam`, `ConversationConfig(tools, automaticToolCalling)`, manual `Message.tool` flow — HIGH
- mvnrepository.com `com.google.ai.edge.litertlm:litertlm-android` — 0.13.1 (Jun 04 2026), 0.14.0 (Jul 08 2026) — HIGH
- LiteRT-LM GitHub releases — v0.13.0/v0.13.1 notes (agent skills, OpenAI-compat CLI server) — HIGH
- LiteRT-LM issue #1027 — Qwen3 `<tool_response>` empty serialization + template-mismatch crash — MEDIUM (single upstream issue; treat as device-test gate, not fact)
- LM Studio docs — Tool Use (`/v1/chat/completions` `tools[]`, `tool_calls`, streaming accumulation by index), REST API comparison table (native `/api/v1/chat` = MCP integrations only, no custom tools) — HIGH
- developer.android.com — Stability in Compose + Fix stability issues (Immutable collections, `@Immutable`/`@Stable`, `derivedStateOf`, metrics) — HIGH
- Chanzmao Bear Blog — Taming the Monolithic UiState (sub-state split decision tree, 2026-08-12) — MEDIUM
- Ramadan Sayed / Medium — Laggy chat screen fix (keyed items + contentType + animateItem + single StateFlow + WhileSubscribed(5000), 2026-02-28) — MEDIUM (community; patterns align with official docs)
- OkHttp GitHub #2964 / #5544 + OkHttpCall source — `cancel()` → `SocketException` on read is expected; always handle `IOException`; Retrofit `Call.cancel` delegates to raw call — HIGH
- `gradle/libs.versions.toml` + `app/proguard-rules.pro` + `.planning/v2.0-MILESTONE-AUDIT.md` (repo ground truth: 0.13.1 pinned, immutable 0.4.0 present, ToolProvider keeps, RUNTIME-09/11/12) — HIGH

---
*Stack research for: Warped v2.1 tool execution + perf completion*
*Researched: 2026-09-27*
