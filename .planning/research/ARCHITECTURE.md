# Architecture: v2.1 Tool Execution + Perf Completion

**Domain:** Warped Android app — completing deferred v2.0 PARTIALs/carry-overs
**Researched:** 2026-09-27
**Overall confidence:** HIGH (all claims verified by direct source read of shipped v2.0 code)

**Ground truth:** `LlmModelHelper` keystone interface exists at `domain/llm/LlmModelHelper.kt`
(`initialize` / `runInference(request, enableThinking)` / `resetConversation` / `stopResponse` / `cleanUp`).
Two `@Singleton @Binds` impls in `di/LlmHelperModule.kt` with `@Named` qualifiers
(`liteRtLmHelper` → `data/local/inference/LiteRtLlmHelper.kt`,
`lmStudioHelper` → `data/remote/provider/LmStudioHelper.kt`),
both injected as `dagger.Lazy` into `data/remote/provider/ProviderRouter.kt`
(`resolveHelper` / `resolveLocalHelper`).
`ChatViewModel` (879 lines) resolves the helper per `sendMessage` and collects
`runInference` directly. `ChatUiState` is a single 30+ field data class.
`ChatScreen` renders messages with `Column + verticalScroll + forEach`
(`LazyColumn` is imported but unused — dead imports, lines 15–17).

---

## 1. Recommended Architecture (v2.1 target)

No new layers. All six completions are **surgical modifications inside the existing
seams** — the v2.0 keystone design already anticipated them (several carry explicit
`// v2.1` / `// 44-02` / `// PERF-08` comments at the exact insertion points).

```
ui/chat/
  ChatUiState.kt            MODIFY — split into 3 @Immutable sub-states (PERF-01)
  ChatScreen.kt             MODIFY — Column→LazyColumn, read sub-state slices (PERF-06)
  ChatViewModel.kt          MODIFY — collect shared inference flow (shareIn refactor)
  components/
    MessageBubble.kt        UNCHANGED signature (already slice-stable) ✓
    ChatInputBar.kt         UNCHANGED signature (already slice-stable) ✓
    InlineModelSelectorBar  MODIFY — hoist out of ChatScreen.kt (private today)

domain/llm/
  LlmModelHelper.kt         UNCHANGED — interface is sufficient, no new methods needed
domain/skills/ (NEW package)
  SkillTool.kt              NEW — Skill → Tool descriptor interface
  SkillRegistry.kt          NEW — @Singleton, exposes StateFlow<Set<SkillId>>

data/local/inference/
  LiteRTLmProvider.kt       MODIFY — ConversationConfig.tools wiring (SKILLS-02)
  LiteRtLlmHelper.kt        MODIFY — shareIn + real stopResponse (runtime hardening)
data/remote/provider/
  LMStudioProvider.kt       MODIFY — Call-returning chat + cancelActiveCall() (Call.cancel)
  LmStudioHelper.kt         MODIFY — tools[] mapping + Call-cancel stopResponse (SKILLS-03)
data/skills/ (NEW package)
  BuiltinSkills.kt          NEW — 3–5 Kotlin skills with JSON-schema descriptors
  SkillRegistryImpl.kt      NEW — @Binds to domain interface

data/remote/dto/
  LmStudioDtos.kt           UNCHANGED — tools[] field already shipped (44-02)
data/remote/api/
  LmStudioApi.kt            MODIFY — add Call-returning chat overload
```

### Component Boundaries

| Component | Responsibility (unchanged) | v2.1 change |
|-----------|---------------------------|-------------|
| `LlmModelHelper` (domain) | Unified inference surface | **Unchanged** — all work fits existing 5 methods |
| `LiteRtLlmHelper` | Local runtime: engine lifecycle + inference fan-out | MODIFIED — shared flow, real cancellation, skill-supplied tools |
| `LmStudioHelper` | Remote runtime: endpoint binding, instanceId, per-call provider | MODIFIED — tools[] mapping, Call-cancel, suspend cleanUp |
| `ProviderRouter` | `dagger.Lazy` helper dispatch | **Unchanged** — lazy injection already breaks the `@Singleton` cycle |
| `LiteRTLmProvider` | Per-call streaming / conversation factory | MODIFIED — `tools` param flows into `ConversationConfig` |
| `LMStudioProvider` | Per-endpoint Retrofit client + SSE parse | MODIFIED — expose cancellable `Call` |
| `ChatViewModel` | Orchestration: helper resolve → initialize → collect → persist | MODIFIED — collect shared flow; sub-state updates |
| `ChatScreen` + subcomposables | Render | MODIFIED — LazyColumn; sub-state slices; zero signature churn on MessageBubble/ChatInputBar |

### Data Flow (after v2.1)

```
sendMessage()
  → ProviderRouter.resolveHelper() → LlmModelHelper (unchanged)
  → helper.initialize(modelId)      (unchanged, idempotent)
  → helper.runInference(request)    CHANGED: returns a flow backed by ONE shared
                                     upstream (shareIn in helper scope). VM collects;
                                     late collectors (benchmark, skills) share it.
  → StreamToken.Delta / Done / Error (unchanged protocol)
  → ChatViewModel updates SUB-STATE slice only
       (ChatListState for tokens, ChatStreamingState for spinner)
  → LazyColumn(items, key={id}) recomposes ONLY the streaming row (was: whole Column)

stopGeneration()
  → generationJob.cancel()          (unchanged, VM side)
  → helper.stopResponse()           CHANGED: cancels shared upstream scope AND
                                     provider.cancelActiveCall() (LM Studio) /
                                     conversation collection (LiteRT)
```

---

## 2. Integration Points (one per completion)

### (1) `@Tool` registration for LiteRtLlmHelper — SKILLS-02

**Registration lives in exactly one place:**
`LiteRTLmProvider.chat()` lines 132–138 builds the `ConversationConfig`:

```kotlin
val conversationConfig = ConversationConfig(
    initialMessages = historyMessages,
    samplerConfig = samplerConfig,
    extraContext = emptyMap(),
    tools = emptyList(),            // ← SKILLS-02 insertion point
    automaticToolCalling = false    // ← flips to true when skills enabled
)
```

**What to build:**
- NEW `domain/skills/SkillTool.kt` — interface exposing `{ id, description, jsonSchema, execute(args): String }`.
- NEW `data/skills/` — `BuiltinSkills.kt` (3–5 skills) + `SkillRegistryImpl @Singleton`
  exposing `enabledSkills: StateFlow<List<SkillTool>>` (replaces the old v2.0-era
  `runBlocking { toolRegistry.enabledToolIds.first() }` pattern — do NOT reintroduce it;
  constructor-inject the registry and collect/read `StateFlow.value`).
- MODIFY `LiteRTLmProvider` — add constructor param `skillRegistry: SkillRegistry`
  (or pass `List<ToolProvider>` through `ChatRequest` as `extraContext` — prefer
  constructor injection, keeps the domain model clean). Map each enabled `SkillTool`
  to a LiteRT `ToolProvider` and set `automaticToolCalling = enabled.isNotEmpty()`.
- MODIFY `LiteRtLlmHelper.runInference` — no signature change; tools ride the existing
  provider delegation. The helper's `stripThinkTags` mapping stays as-is.

**Constraint honored:** `LiteRtLlmHelper`/`LiteRTLmProvider` are `@Singleton` stateful
runtimes — owning the tool list is consistent with owning the `Conversation`.
Repositories stay out of it (no DB in the inference path; skill enablement flags can
live in DataStore via the registry, not Room).

### (2) LM Studio `tools[]` mapping — SKILLS-03

**Mapping lives in `LmStudioHelper.runInference()` (lines 95–121).** The DTO work is
DONE: `LmStudioChatRequest.tools: List<LmStudioTool>` + `LmStudioTool` /
`LmStudioToolFunction(name, description, parameters: JsonObject)` already shipped
(`LmStudioDtos.kt` lines 23–39, marked `44-02`). The SSE parse side is also DONE:
`tool_call.start/arguments/success/failure` events already emit `[tool:NAME]` /
`(args-json)` / output Deltas (`LMStudioProvider.kt` lines 205–218), and
`ChatViewModel` already detects `[tool:NAME]` to set `toolCallActive` (lines 273–277).

**The single missing link:** `LMStudioProvider.chat(request, integrations)` builds the
request body with `integrations = integrations` but `tools` always defaults to
`emptyList()` — nobody passes tools. And `LmStudioHelper.runInference` calls
`provider.chat(effectiveRequest)` (single-arg overload), never the two-arg one.

**What to build:**
- MODIFY `LmStudioHelper.runInference` — read `skillRegistry.enabledSkills.value`,
  map each `SkillTool` → `LmStudioTool(type="function", function=...(name, description, parameters))`,
  and pass into the request body (new `chat(request, tools)` overload on the provider,
  or extend the existing `integrations` overload — prefer a dedicated `tools` param;
  don't overload `integrations`, it models MCP-bridge servers, a different concept).
- MODIFY `LMStudioProvider.chat` — accept `tools: List<LmStudioTool> = emptyList()`
  and set `body.tools = tools`. One-line body change + signature.
- Result-accretion loop (tool result → follow-up request) stays in `ChatViewModel`'s
  existing `[tool:NAME]` detection path — no new component.

### (3) `ChatUiState` sub-state split — PERF-01

**Current shape:** single `data class ChatUiState` (~30 fields, 2 `@Deprecated`), plus
`toolCallActive` added by skills-lite. `ChatScreen` reads the whole object
(`val uiState by viewModel.uiState.collectAsStateWithLifecycle()`), so every token
`copy()` recomposes every reader.

**Split that breaks nothing** — the ~10 subcomposables already take narrow slices,
so the split is VM + screen only:

```kotlin
@Immutable data class ChatListState(
    val conversationId: Long? = null,
    val messages: List<ChatMessage> = emptyList(),
    val conversations: List<Conversation> = emptyList(),  // drawer
    val conversationModelId: String? = null,
    val conversationProviderType: ProviderType? = null,
    val modelUnavailable: Boolean = false,
)
@Immutable data class ChatInputState(
    val inputText: String = "",
    val generationParameters: GenerationParameters = GenerationParameters(),
    val reasoningEnabled: Boolean = true,
    val enableThinking: Boolean = false,
    val supportsThinking: Boolean = false,
)
@Immutable data class ChatStreamingState(
    val isStreaming: Boolean = false,   // merge isGenerating (dead field, never written true)
    val streamingContent: String = "",
    val streamingReasoning: String = "",
    val toolCallActive: String? = null,
)
@Immutable data class ChatSelectionState(  // everything else: models/endpoints/loading/errors/dialogs
    val selectedLocalModelId: String? = null, ... , val pendingModelSwitch: ModelSwitchRequest? = null,
)
```

**Why safe:** verified signatures — `MessageBubble(message, isStreaming, codeTheme, codeFontScale)`
and `ChatInputBar(text, isGenerating, canSend, …15 scalars/lambdas)` take NO `UiState`
whole-object param (grep confirms zero `uiState.` references inside `components/`).
Only `ChatScreen` + `ModelSelectorSheet` call-site + `NavGraph` drawer read wide state.
Migration: VM exposes `listState / inputState / streamingState / selectionState`
(`StateFlow`s derived from one private `MutableStateFlow<ChatUiState>` master, or four
flows — prefer master + `map + stateIn` to avoid 4-way update skew); `ChatScreen`
collects each slice with `collectAsStateWithLifecycle`. Delete the two `@Deprecated`
fields (`selectedProvider`, `selectedModelId`) — grep shows only `ActiveModelSelection`
collector writes them, nothing reads.
Also merge `isGenerating` (write-never, only read in `ChatInputBar(isGenerating = uiState.isStreaming)`
call — dead) into `isStreaming`.

### (4) `Column → LazyColumn` migration — PERF-06

**Current shape** (`ChatScreen.kt` lines 264–307): `Box(weight 1f)` →
`Column(verticalScroll(scrollState))` → `uiState.messages.forEach { MessageBubble }` +
streaming bubble. Two `LaunchedEffect` auto-scroll blocks drive `scrollState.animateScrollTo(maxValue)`.

**Migration path (mechanical, 1 file):**
1. Replace inner `Column` with `LazyColumn(state = listState, contentPadding, verticalArrangement = spacedBy(10.dp))`.
   Items: `items(messages, key = { it.id ?: it.hashCode() }) { MessageBubble(...) }` —
   `ChatMessage` needs a stable key; verify `id` field exists on the domain model
   (VM's `deleteMessage` compares `it.id != messageId.toString()`, so `id: String?`
   exists — default null for unsent; fall back to index-independent hash, never index).
   Streaming bubble becomes a trailing `item(key = "streaming") { … }`.
2. Replace `rememberScrollState` with `rememberLazyListState`; auto-scroll effects become
   `listState.animateScrollToItem(messages.size)` (guard `!isStreaming` variant stays).
3. Keep the top/bottom fade-gradient overlay `Box`es unchanged — they align to the
   parent `Box`, orthogonal to the scrollable.
4. Delete the now-duplicated dead `LazyColumn`/`items` imports (lines 15–17 import
   `LazyColumn`, `LazyRow`, `items` twice) as part of the change.
5. `ConversationList.kt` (drawer) and `ChatInputBar` image-preview `LazyRow` already
   lazy — no touch.

**Perf contract:** with `key()` + sub-state split, a streaming token updates only
`streamingState` → only the trailing `item("streaming")` recomposes. Without the split,
LazyColumn alone still recomposes all visible rows on each token (state object identity
changes) — which is why PERF-01 must land **before or with** PERF-06, not after.

### (5) `shareIn` / `MutableSharedFlow` refactor of double-collect

**Actual defect (worse than "double-collect"):** both helpers launch a **sentinel no-op job**
and track it as the cancellation handle —
`LiteRtLlmHelper` line 87: `.also { activeJob.set(scope.launch { /* sentinel */ }) }`;
`LmStudioHelper` line 107: same pattern. `stopResponse()` cancels a job that collects
nothing. The real collection lives in `ChatViewModel.sendMessage`'s
`helper.runInference(…).collect {}` under `generationJob`. So today:
Stop button → `generationJob.cancel()` (works, VM side) + `helper.stopResponse()` (no-op).
Provider-side streams (LiteRT `sendMessageAsync` Flow, OkHttp SSE body) are NOT
cancelled at the source — they complete/drain silently.

**Second defect:** double `flowOn` hop — `LiteRTLmProvider.chat` ends `flowOn(Default)`
(line 142), `LiteRtLlmHelper` re-applies `flowOn(Default)` (line 86); LM Studio side is
`flowOn(IO)` over `flowOn(IO)`. Harmless but evidence the helper adds no operator value.

**Refactor (in helpers, interface unchanged):**
```kotlin
// LiteRtLlmHelper / LmStudioHelper
private var sharedJob: Job? = null
fun runInference(request, enableThinking): Flow<StreamToken> {
    sharedJob?.cancel()  // single-flight: new send supersedes previous
    val upstream = buildUpstream(request, enableThinking)  // today's body
    val shared = upstream.shareIn(scope, SharingStarted.Lazily, replay = 0)
    sharedJob = shared as? Job ?: scope.launch { shared.collect() }
    return shared
}
fun stopResponse() { sharedJob?.cancel(); sharedJob = null; providerCancel() }
```
- `shareIn(scope)` with the helper's existing `scope` (already `SupervisorJob + Default/IO`).
- Single-flight guard (`sharedJob?.cancel()` on entry) matches chat semantics
  (one active generation per helper — both helpers are `@Singleton`).
- VM keeps `generationJob` for its own collect lifecycle; `stopGeneration()` additionally
  calls `helper.stopResponse()` (wire-up change in VM, 3 lines).
- Consumers that ride `runInference` later (benchmark, skills accreditation) collect the
  same shared flow with no extra inference cost.

### (6) OkHttp `Call` plumbing through `LMStudioProvider`

**Current shape:** `LMStudioProvider` owns a `private val client: OkHttpClient` (lines 42–58)
but exposes only Retrofit `suspend fun chat(body): Response<ResponseBody>` —
Retrofit's suspend adapter owns the internal `Call` and cancels it only on coroutine
cancellation of the `chat()` suspend itself. `LmStudioHelper.stopResponse` cancels the
sentinel job (no-op, see §5), so **nothing reaches the socket**: the SSE `while
(!source.exhausted())` loop (lines 111–150) keeps draining after Stop. The code admits
it (`LmStudioHelper` lines 57–60: *"deeper Call.cancel() requires threading the Call
through the provider. Tracked for v2.1"*).

**Plumbing path (2 files, no DI change):**
1. `LmStudioApi` — add overload returning the raw Call:
   `fun chatCall(@Body request: LmStudioChatRequest): Call<ResponseBody>` (same POST path).
2. `LMStudioProvider` — add `private val activeCall = AtomicReference<Call<*>?>()` ;
   new `chatCancellable(request, tools)` implementation uses
   `withContext(IO) { api.chatCall(body).also { activeCall.set(it) }.execute() }`
   then the EXISTING SSE parse loop verbatim; `finally { activeCall.set(null) }`.
   Add `fun cancelActiveCall() { activeCall.getAndSet(null)?.cancel() }`.
   Keep the current `suspend`-based `chat()` for `listModels/testConnection`-style
   non-streaming calls or migrate chat fully — prefer migrating streaming chat only,
   leaving `loadModel/unloadModel/listModels` on suspend Retrofit.
3. `LmStudioHelper.stopResponse` — `cancelActiveCall()` + shared-job cancel (§5).
   `runInference` entry clears any stale call (`cancelActiveCall()` before new `execute()`).
4. Bonus fix in the same edit: `LmStudioHelper.cleanUp` line 142 uses `runBlocking`
   inside a singleton — convert `cleanUp` body to launch on `scope` or make the
   unload fire-and-forget; never block the caller on network IO.

**OkHttp client ownership:** the provider constructs its own client per instance
(`LMStudioProvider` is constructed per-call in `createProvider`). For true
`Call.cancel()` this is sufficient (Call reference is enough). Do NOT lift the client
into Hilt in v2.1 — bigger refactor (auth interceptor per-endpoint), no cancellation
benefit. Optionally set `readTimeout` — 120s already fine for streaming.

---

## 3. Patterns to Follow

### Sentinel-job → shared-upstream-job (fixes §5 properly)
**What:** every `runInference` builds the upstream once, multicasts via `shareIn(helperScope)`,
tracks the multicast `Job` as the cancellation handle.
**When:** both helpers; single-flight semantics (new inference cancels previous).
```kotlin
private var inferenceJob: Job? = null
override fun runInference(request: ChatRequest, enableThinking: Boolean): Flow<StreamToken> {
    inferenceJob?.cancel()
    val shared = buildFlow(request, enableThinking).shareIn(scope, SharingStarted.Lazily)
    inferenceJob = scope.launch { shared.collect() }  // keeps upstream alive + cancellable
    return shared
}
override fun stopResponse() {
    inferenceJob?.cancel(); inferenceJob = null
    cancellableProviderCancel()  // Call.cancel() or conversation close
}
```

### Helper-specific config stays OFF the interface (existing pattern, keep)
`LmStudioHelper.setEndpoint()` / `getInstanceId()` are deliberately not on
`LlmModelHelper` (documented lines 31–35). New skill wiring follows the same rule:
registry injected into impl constructors, never added to the interface.

### `dagger.Lazy` for helper injection (existing pattern, keep)
`ProviderRouter` injects both helpers as `dagger.Lazy` — required because both helpers
are `@Singleton` and (after §1) will take `SkillRegistry @Singleton`; eager injection
risks a dependency cycle. Any new `@Singleton` collaborating with helpers
(registry, benchmark) must also be consumed via `Lazy` from the router/VM.

### Slice-stable composable signatures (already achieved, preserve)
`MessageBubble` / `ChatInputBar` take scalars + lambdas, never whole `UiState`.
PERF-01 must not "convenience" these into sub-state params — that would re-couple them.
`ChatScreen` is the only slice-fan-out point.

---

## 4. Anti-Patterns to Avoid

### runBlocking in the inference path
`LmStudioHelper.cleanUp` line 142 (`runBlocking { provider.unloadModel }`) blocks
whichever thread calls cleanUp (VM scope today, helper scope after §5). Fire-and-forget
on `scope` instead. Never add another `runBlocking` (the v2.0-era toolRegistry read
used one — the new registry MUST expose `StateFlow.value`, read synchronously).

### Repositories owning runtime state
`SkillRegistry` is runtime state (enabled flags + Tool instances), NOT a repository.
Do not put it behind `ChatRepository`/`EndpointRepository` or add skill tables to Room
in v2.1 — persistence (if needed) is a DataStore boolean set inside the registry impl.
Keystone constraint: repositories are stateless-or-own-DB-only.

### Index keys in LazyColumn
`items(messages.size) { i -> … }` or `key = { index }` breaks animations and causes
wrong-bubble reuse when history loads/deletes interleave with streaming. Always
`key = { it.id ?: "transient-${it.hashCode()}" }`.

### Splitting ChatViewModel
Tempting (879 lines) but OUT of v2.1 scope. The sub-state split (§3) already de-risks
recomposition; a VM split risks breaking the 10-collector `init` wiring and the
`generationJob` lifecycle. Defer.

### Adding methods to LlmModelHelper
None of the six completions needs it. `runInference(request, enableThinking)` already
carries thinking; tools ride constructor-injected registries; cancellation rides
`stopResponse`. Keep the 5-method surface stable so Benchmark/PromptLab call sites
don't churn.

---

## 5. Scalability Considerations

| Concern | v2.1 (this milestone) | Beyond |
|---------|----------------------|--------|
| Chat history length | LazyColumn keys + slice recomposition handle 1k+ messages | Paging (`PagingSource` on Room) if 10k+ |
| Concurrent inference | Single-flight per helper (matches one chat screen) | Scoped helpers per conversation if multi-chat |
| Tool count | 3–5 builtin skills, mapped per-call (no caching needed) | Cache `LmStudioTool` descriptors in registry when >20 |
| SSE cancel latency | `Call.cancel()` closes socket immediately | — |
| LiteRT conversation | Reused long-lived `Conversation` (LRT-02, keep) | Reset on skill-set change (config captures tools at creation) |

**Note on LRT-02 interaction:** `LiteRTLmProvider` reuses `activeConversation` across
`chat()` calls, but `ConversationConfig` (which will now carry `tools`) is only applied
at conversation CREATION. If the enabled-skill set changes mid-session, the helper must
call `resetConversation()` so the next `chat()` picks up the new tool list. Add this
invalidation to `SkillRegistry` (expose `enabledSkills` flow; helper collects once and
resets on change) — 5 lines, easy to miss.

---

## 6. Suggested Build Order

```
Phase 45 — Runtime hardening FIRST (shareIn + Call.cancel + runBlocking removal)
  ├─ LiteRtLlmHelper: shareIn single-flight + real stopResponse
  ├─ LMStudioProvider: chatCall(Call) + cancelActiveCall()
  ├─ LmStudioHelper: stopResponse → cancelActiveCall; cleanUp off runBlocking
  ├─ ChatViewModel.stopGeneration: helper.stopResponse() wire-up
  └─ No UI change; covered by existing chat regression (send/stop/send)
  DEPENDS ON: nothing. ENABLES: Phase 46 (skills ride the fixed runInference).

Phase 46 — Real tool execution (SKILLS-02 + SKILLS-03)
  ├─ domain/skills + data/skills (registry, builtin skills)
  ├─ LiteRTLmProvider: ConversationConfig.tools + automaticToolCalling
  ├─ LmStudioHelper/Provider: tools[] mapping into request body
  ├─ Conversation-reset-on-skill-change invalidation
  └─ ChatViewModel [tool:NAME] path already exists — accretion only
  DEPENDS ON: Phase 45 (tools execute THROUGH runInference; building on the
  sentinel-job cancellation would bake the no-op-stop bug into tool calls).

Phase 47 — Compose perf (PERF-01 + PERF-06 together, one phase)
  ├─ ChatUiState → 4 @Immutable sub-states; drop 2 @Deprecated + merge isGenerating
  ├─ ChatScreen Column→LazyColumn keyed; InlineModelSelectorBar hoist
  └─ VM exposes slice StateFlows; subcomposable signatures UNCHANGED
  DEPENDS ON: nothing (orthogonal to 45/46 — can parallelize).
  NOTE: PERF-01 and PERF-06 must ship TOGETHER (LazyColumn without the split
  still recomposes all rows per token; split without LazyColumn keeps O(n) layout).
```

**Why this order:**
- **Runtime before skills** — skills execute through `runInference`; the sentinel-job
  no-op and socket-drain bugs would become tool-execution bugs (unstoppable tool calls).
  Fix the pipe before pushing new traffic through it.
- **Skills before/independent of perf** — no shared files except `ChatViewModel`
  (different functions: `sendMessage` collect vs state `copy` sites). Low merge risk
  either way; perf can run parallel as Phase 47.
- **PERF-01+06 atomic** — either alone gives ~zero measurable win; together they give
  the whole win. Do not split across phases.

---

## 7. Open Questions (flagged, none blocking)

1. LiteRT `ToolProvider` Kotlin API shape — which `com.google.ai.edge.litertlm`
   class backs `ConversationConfig.tools`? Confirm constructor + JSON-schema format
   at Phase 46 start (AAR inspection, 30 min).
2. `ChatMessage.id` nullability for LazyColumn keys — confirm type/default before
   Phase 47 (domain model read, 5 min).
3. Whether `automaticToolCalling=true` executes locally without a network round-trip
   on the pinned LiteRT version — verify with one-skill smoke test in Phase 46.

---

## Sources

**HIGH confidence — direct source reads (2026-09-27):**
- `app/src/main/java/com/warped/domain/llm/LlmModelHelper.kt` (71 lines) — 5-method seam, unchanged-needs verdict
- `app/src/main/java/com/warped/data/local/inference/LiteRtLlmHelper.kt` (120 lines) — sentinel-job defect, line 87
- `app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt` (306 lines) — `tools = emptyList()` line 136, `flowOn(Default)` line 142, conversation reuse LRT-02
- `app/src/main/java/com/warped/data/remote/provider/LmStudioHelper.kt` (168 lines) — sentinel-job line 107, `runBlocking` cleanUp line 142, v2.1 Call.cancel note lines 57–60
- `app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt` (352 lines) — private OkHttpClient, suspend-only chat, SSE tool_call parse DONE
- `app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt` (113 lines) — `dagger.Lazy` helpers, `resolveHelper` dispatch
- `app/src/main/java/com/warped/data/remote/dto/LmStudioDtos.kt` — `tools[]` DTO DONE (44-02)
- `app/src/main/java/com/warped/data/remote/api/LmStudioApi.kt` (48 lines) — suspend-only, needs Call overload
- `app/src/main/java/com/warped/di/LlmHelperModule.kt` (47 lines) — @Binds @Singleton @Named bindings
- `app/src/main/java/com/warped/ui/chat/ChatUiState.kt` (107 lines) — single data class + traffic-light derivations
- `app/src/main/java/com/warped/ui/chat/ChatScreen.kt` (539 lines) — Column+forEach, dead LazyColumn imports, private InlineModelSelectorBar
- `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` (879 lines) — single collect site, `[tool:NAME]` detection, `stopGeneration`
- `app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt` + `ChatInputBar.kt` — slice-stable signatures verified
- `.planning/PROJECT.md` — v2.1 milestone scope, keystone constraint (stateful helpers / stateless repos)
- `.planning/research/ARCHITECTURE.md` (v2.0, 2026-06-05) — superseded Phase 40–44 plan; keystone + Drawer + LazyColumn history

---
*Architecture research for: Warped v2.1 Finish v2.0 Leftovers (tool execution + perf completion)*
*Researched: 2026-09-27*
*Confidence: HIGH (integration points) / HIGH (build order — dependency-driven)*
