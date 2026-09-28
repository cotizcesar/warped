# Phase 46: Runtime Hardening - Research

**Researched:** 2026-09-27
**Domain:** Kotlin coroutines Flow sharing + per-backend inference cancellation (LiteRT-LM 0.17.1, OkHttp 4.12.0 / Retrofit 3.0.0)
**Confidence:** HIGH

## Summary

Phase 46 makes Stop mean stop on both backends and collapses inference streaming onto one shared Flow per turn. Investigation of the actual tree shows the problem is precisely scoped and smaller than the requirement titles suggest: there is no literal "two `.collect{}` calls on the same flow" in `ChatViewModel` — there is one collection site (`ChatViewModel.kt:266`). The "double-collect" defect is structural: each helper mints a **sentinel no-op `Job`** (`LmStudioHelper.kt:107`, `LiteRtLlmHelper.kt:87`) that `stopResponse()` cancels instead of the real stream, while `ChatViewModel.stopGeneration()` (`ChatViewModel.kt:350`) cancels only its own `generationJob` and never calls `helper.stopResponse()` at all. Net effect: cancelling the UI-side job leaves the underlying work running — the OkHttp response body stays open (remote) and native generation continues (local) — and the sentinel job gives a false sense of cancellation.

Two further verified defects make Stop leak tokens even after the job fix: (1) `LMStudioProvider.chat` catches `Exception` around the SSE read loop (`LMStudioProvider.kt:181`) and emits `StreamToken.Error`, which **swallows `CancellationException`** — coroutine cancellation of the stream is converted into an Error token instead of terminating; (2) the provider goes through Retrofit `suspend fun chat()` (`LmStudioApi.kt:24`), so no `Call` handle exists to call `Call.cancel()` on — the requirement's explicit demand.

Good news verified by tool: `Conversation.cancelProcess()` **exists** in the pinned litertlm-android 0.17.1 AAR (confirmed via `javap` on the Gradle-cached AAR this session), so the local half of RUNTIME-14 is a pure plumbing task. Test stack is pinned and present (turbine 1.2.1, coroutines-test 1.11.0, MockK 1.14.11, JUnit 5.14.4) [CITED: gradle/libs.versions.toml].

**Primary recommendation:** Per-turn `shareIn` scope owned by `ChatViewModel.generationJob`; helpers drop the sentinel and instead retain the real cancellable handle (`volatile Call` via raw OkHttp on the remote path; `activeConversation.cancelProcess()` on the local path); `stopGeneration()` cancels the job AND calls `helper.stopResponse()`; fix the `CancellationException` swallow; add `ensureActive()` hook contract for the Phase 47 tool loop.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Single shared Flow via shareIn replay=1, per-turn scope; runInference double-collect gone (user accepted)
- Sentinel no-op cancellation job removed
- Rotation mid-stream: no duplicated or dropped tokens (replay covers config-change re-collect)
- Stop button wires true OkHttp Call.cancel() (remote) + cancelProcess() (local), fixing stopResponse() no-op (user accepted)
- Tokens halt immediately; no trailing tokens after Stop
- Stop then immediate follow-up: no hang, wedge, or stale generating spinner

### the agent's Discretion
- Exact shareIn scope holder (ViewModel vs Router) and coroutine scope choice at planner/executor discretion
- Tool-loop cancellation checks land here as hooks only (full loop is Phase 47); 25-round-cap guard noted

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| RUNTIME-13 | runInference double-collect gone — single shared Flow via shareIn (replay=1, per-turn scope); sentinel no-op cancellation job removed | Current sentinel/collect locations mapped below; shareIn design with scope owner recommendation + rotation analysis |
| RUNTIME-14 | Stop actually stops — true OkHttp Call.cancel() (remote) + cancelProcess() (local) wired to Stop, fixing stopResponse() no-op; tool loop checks cancellation between rounds | cancelProcess() verified in 0.17.1 AAR; raw-OkHttp Call-retention design; CancellationException-swallow defect found; Phase 47 hook contract |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Inference Flow sharing (shareIn, per-turn scope) | API / Backend (helper + ViewModel) | — | Flow lifecycle is owned by the data/domain layer; UI only collects |
| Stop / cancellation plumbing (Call.cancel, cancelProcess) | API / Backend (helpers, providers) | — | Cancellable handles live where the transport lives |
| Stop button wiring + generating-state reset | Browser / Client equivalent: Compose UI + ViewModel | — | `ChatScreen` onStop → `ChatViewModel.stopGeneration()` already exists; behavior fix only, no UI change |
| Tool-loop cancellation hooks | API / Backend (Phase 47 loop) | — | This phase defines the hook contract only |

## Standard Stack

No new libraries. Zero-new-dependency constraint is locked (v2.1 milestone rule, RUNTIME-12 audit stays green).

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| kotlinx-coroutines-core / android | 1.11.0 [CITED: gradle/libs.versions.toml] | `shareIn`, `ensureActive`, structured cancellation | Already the project's async foundation; `shareIn` is the stdlib operator for exactly this problem |
| OkHttp | 4.12.0 (HOLD) [CITED: gradle/libs.versions.toml] | Raw `Call` for SSE POST + `Call.cancel()` | Pinned; `Call.cancel()` is the documented immediate-stop API [CITED: https://square.github.io/okhttp/4.x/okhttp/okhttp3/-call/cancel/] |
| litertlm-android | 0.17.1 [CITED: gradle/libs.versions.toml + 45-02-SUMMARY] | `Conversation.cancelProcess()` for local stop | Verified present in AAR bytecode this session (see below) |
| Turbine | 1.2.1 [CITED: gradle/libs.versions.toml] | Flow cancellation/replay tests | Already pinned test dependency |
| MockK / coroutines-test / Truth / JUnit5 | 1.14.11 / 1.11.0 / 1.4.5 / 5.14.4 [CITED: gradle/libs.versions.toml] | Fake helpers, `runTest`, assertions | Already pinned |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Raw OkHttp `Call` in `LMStudioProvider.chat` | Keep Retrofit `suspend` + rely on coroutine-cancel → Retrofit cancels the underlying call automatically | Less code, BUT no explicit `Call` handle exists to satisfy "true `Call.cancel()`", and the SSE body is parsed manually anyway (Retrofit's converter adds nothing on this path). Requirement explicitly demands `Call.cancel()` — use raw OkHttp. |
| `shareIn` in helper (Router scope) | `shareIn` in `ChatViewModel` per-turn scope | Helper is `@Singleton` — a singleton-held SharedFlow leaks across turns/conversations and both collectors (Chat + PromptLab + BenchmarkWorker). Per-turn scope in the ViewModel matches the locked "per-turn scope" decision. |
| `stateIn` | `shareIn` | `stateIn` requires an initial value and conflates latest-token state with event-stream semantics; token streams are events with a terminal Done/Error — `shareIn` + explicit terminal handling is correct. |

**Installation:** none — all artifacts already in catalog.

## Current-State Audit (what the planner must change)

### Double-collect / sentinel locations (RUNTIME-13)

| # | File:line | What | Verdict |
|---|-----------|------|---------|
| 1 | `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:266-269` | The ONE real collection: `helper.runInference(...).collect {}` inside `generationJob` (`:182`) | Keep as the single collection point; wrap upstream in `shareIn` |
| 2 | `app/src/main/java/com/warped/data/remote/provider/LmStudioHelper.kt:107` | `activeJob.set(scope.launch { /* sentinel */ })` — empty coroutine whose only purpose is to give `stopResponse()` something to cancel | **DELETE** — the sentinel no-op |
| 3 | `app/src/main/java/com/warped/data/local/inference/LiteRtLlmHelper.kt:87` | Same sentinel pattern (`.also { activeJob.set(scope.launch { /* sentinel */ }) }`) | **DELETE** — the sentinel no-op |
| 4 | `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:350-359` | `stopGeneration()` cancels `generationJob` but **never calls `helper.stopResponse()`** — the helper (and its transport) is never told to stop | **FIX** — call `helper.stopResponse()`; needs the active-helper reference retained per turn |
| 5 | `app/src/main/java/com/warped/ui/promptlab/PromptLabViewModel.kt:101`, `app/src/main/java/com/warped/data/local/benchmark/ModelBenchmarkWorker.kt:86` | Independent cold-flow collectors — each starts its own inference; fine today, but proves helpers are `@Singleton` cold factories that must NOT hold shared state | Do not touch; constrains shareIn scope to per-turn ViewModel ownership (not helper/Router singleton scope) |

Note on "double-collect": there is no second `.collect` on the same Flow instance in the tree. The defect to remove is the **split cancellation domain** (UI job vs sentinel job vs live transport, three things that should be one). Planner should phrase tasks as "collapse to single shared Flow + single cancellation path", not "delete a duplicate collect call" — the latter does not exist and a task written that way will confuse the executor.

### Cancellation defects (RUNTIME-14)

| # | File:line | What | Verdict |
|---|-----------|------|---------|
| 6 | `app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt:181-183` | `catch (e: Exception) { emit(StreamToken.Error(...)) }` wraps the SSE read loop — **swallows `CancellationException`**, converting coroutine cancel into an Error token; upstream `IOException` catch at `:150` has the same shape but only catches `IOException` (CancellationException is not an IOException, so `:150` is safe) | **FIX** — `catch (e: CancellationException) { throw e }` first (or `catch (e: Exception) { currentCoroutineContext().ensureActive(); emit(...) }`) |
| 7 | `app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt:42-58,97` | Transport is Retrofit `suspend fun chat()` (`LmStudioApi.kt:24` `@Streaming @POST("api/v1/chat")`); the `OkHttpClient` (`:42`) is used only to build Retrofit — **no `Call` reference exists** to cancel | **FIX** — build the SSE POST with raw `client.newCall(request)` and retain `volatile Call`; parse the already-manual SSE loop unchanged |
| 8 | `app/src/main/java/com/warped/data/remote/provider/LmStudioHelper.kt:127-133` | `stopResponse()` cancels only the sentinel; comment at `:40-42` and `:57-60` openly admits `Call.cancel()` is future work | **FIX** — retain the provider/active-`Call` and call `Call.cancel()`; delete sentinel + helper-internal `scope` if nothing else uses it (check `cleanUp`'s `runBlocking` — out of scope, leave) |
| 9 | `app/src/main/java/com/warped/data/local/inference/LiteRtLlmHelper.kt:94-100` | `stopResponse()` cancels only the sentinel; native `sendMessageAsync` Flow keeps generating | **FIX** — call `cancelProcess()` on the active `Conversation` (verified to exist, see below) |
| 10 | `app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt:44-45,203-216` | `activeConversation` is `@Volatile private` with no cancellation accessor; `sendContentsWithRetry` has a retry loop (`:237-244`) that could re-emit after a stop if the error looks like an engine error | **FIX** — add `cancelActiveGeneration()` (calls `activeConversation?.cancelProcess()`); guard the retry path with `ensureActive()` so a cancelled turn never retries |

### Verified API: `Conversation.cancelProcess()` in 0.17.1 [VERIFIED: AAR javap this session]

```
$ javap -classpath litertlm-aar/classes.jar com.google.ai.edge.litertlm.Conversation
...
public final void cancelProcess();
```

Source: Gradle-cached `litertlm-android-0.17.1.aar` (`~/.gradle/caches/modules-2/.../litertlm-android/0.17.1/`), inspected 2026-09-27. The `Flow`-returning `sendMessageAsync(Contents, ...)` overload used by `LiteRTLmProvider.kt:218` is also confirmed present. Thread-safety note: `activeConversation` is already `@Volatile`; `cancelProcess()` is a plain `final void` method — safe to call from `stopResponse()` on any dispatcher [ASSUMED: thread-safety of concurrent cancelProcess vs collect — no docs found in AAR; treat as fire-and-forget best-effort + coroutine cancel, which is sufficient].

## Architecture Patterns

### Data flow after hardening

```
ChatScreen Stop button
  → ChatViewModel.stopGeneration()
      → generationJob.cancel()            (cancels shareIn scope → upstream collection stops)
      → helper.stopResponse()
          → REMOTE: activeCall?.cancel()  (OkHttp Call.cancel: socket closed, readUtf8Line throws IOException → loop exits, no trailing tokens)
          → LOCAL:  activeConversation?.cancelProcess() + coroutine cancel (native generation halts)
      → _uiState: isStreaming=false, streamingContent="", streamingReasoning="", toolCallActive=null
  → immediate follow-up sendMessage(): fresh per-turn scope, helper still initialized, conversation alive → no hang
```

### Recommended Project Structure

No new files required except tests. All changes are in-place edits:

```
app/src/main/java/com/warped/
├── ui/chat/ChatViewModel.kt                 # per-turn shareIn + stopGeneration fix (RUNTIME-13/14)
├── data/remote/provider/
│   ├── LMStudioProvider.kt                  # raw OkHttp Call retention + CancellationException fix
│   └── LmStudioHelper.kt                    # sentinel removal + stopResponse → Call.cancel()
├── data/local/inference/
│   ├── LiteRtLlmHelper.kt                   # sentinel removal + stopResponse → cancelProcess()
│   └── LiteRTLmProvider.kt                  # cancelActiveGeneration() accessor + ensureActive in retry
app/src/test/java/com/warped/
├── ui/chat/ChatCancellationTest.kt          # NEW (turbine + fake helper)
└── data/remote/provider/LmStudioCancelTest.kt # NEW (MockWebServer-less: fake Call / chunked body)
```

### Pattern 1: Per-turn shareIn owned by the ViewModel generation job
**What:** `sendMessage` creates one child scope per turn; the helper's cold flow is shared in that scope with `replay = 1`; the UI-state accumulator is the single collector. `stopGeneration` cancels the turn scope.
**When to use:** Every inference turn in `ChatViewModel.sendMessage`.
**Example:**
```kotlin
// Source: kotlinx.coroutines Flow SharingStarted/shareIn official docs
// https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/share-in.html
generationJob = viewModelScope.launch(handler) {
    val turnScope = this  // per-turn scope == the generation job itself
    val shared: SharedFlow<StreamToken> = helper.runInference(request, enableThinking)
        .shareIn(turnScope, SharingStarted.Eagerly, replay = 1)
    shared.collect { token -> /* existing accumulator, unchanged */ }
}

fun stopGeneration() {
    activeHelper?.stopResponse()   // transport-level stop FIRST (socket/native)
    generationJob?.cancel()        // then coroutine scope (shareIn upstream)
    generationJob = null
    activeHelper = null            // follow-up turn resolves fresh; no stale handle
    _uiState.update { it.copy(isStreaming = false, streamingContent = "",
        streamingReasoning = "", toolCallActive = null) }
}
```
Why `SharingStarted.Eagerly`: the upstream must start even if the accumulator collector suspends briefly on a slow emit; with a single collector `Lazily` also works — planner's discretion. `replay = 1` is locked. Retaining `activeHelper` per turn (field set at `:228-243` resolution time) is the minimal change that lets `stopGeneration` reach `stopResponse()`.

### Pattern 2: Transport-handle retention in helpers (no sentinel)
**What:** Replace `AtomicReference<Job?>` sentinel with the real cancellable handle. Remote: `AtomicReference<Call?>`. Local: delegate to provider's active conversation.
**When to use:** Both helpers' `runInference`/`stopResponse`.
```kotlin
// Remote (LmStudioHelper) — Source: OkHttp Call.cancel docs
// https://square.github.io/okhttp/4.x/okhttp/okhttp3/-call/cancel/
private val activeCall = AtomicReference<Call?>(null)
override fun runInference(...): Flow<StreamToken> =
    provider.chatWithCallTracking(effectiveRequest) { call -> activeCall.set(call) }
        .onCompletion { activeCall.set(null) }   // no stale handle → follow-up safe
        .flowOn(Dispatchers.IO)

override fun stopResponse() {
    activeCall.getAndSet(null)?.cancel()  // immediate socket teardown; safe if already complete
}
```
```kotlin
// Local (LiteRtLlmHelper) — cancelProcess() VERIFIED in 0.17.1 AAR (javap, this session)
override fun stopResponse() {
    try { liteRTLmProvider.cancelActiveGeneration() } catch (e: Exception) {
        Timber.w(e, "LiteRtLlmHelper: cancelProcess failed")
    }
}
```

### Pattern 3: CancellationException transparency in manually-parsed SSE loops
**What:** Never let `catch (e: Exception)` around a cancellable read loop swallow coroutine cancellation.
**When to use:** `LMStudioProvider.chat` SSE loop + non-streaming fallback.
```kotlin
// Source: Kotlin coroutines cancellation docs — CancellationException must be rethrown
// https://kotlinlang.org/docs/cancellation-and-timeouts.html
} catch (e: CancellationException) {
    throw e  // Stop means stop: no Error token, no Done token, just terminate
} catch (e: IOException) {
    Timber.e(e, "LMStudio: SSE stream read failed")
    // activeCall.cancel() surfaces here as IOException("Canceled") — expected on Stop, stay silent
}
```
Note: `Call.cancel()` while blocked in `readUtf8Line()` raises `IOException("Canceled")` — the existing `:150` handler already treats it as a silent stream end. After the fix, Stop on remote = `IOException("Canceled")` → loop exits → `flow` completes with **no terminal token**; the ViewModel's `onCompletion` path must then clear `isStreaming` (it already does via `stopGeneration`'s state reset, but the normal `collect` terminal handling must tolerate a completion with no Done — check `:295-326`: if neither Done nor Error arrived, content stays in `streamingContent` which `stopGeneration` clears. For a *natural* early completion without Done, add an `onCompletion` guard that persists the partial buffer as an assistant message or discards — planner's call; flag it).

### Anti-Patterns to Avoid
- **Singleton-held SharedFlow for inference:** helpers and `ProviderRouter` are `@Singleton` — a `shareIn` there leaks across turns, conversations, and the Chat/PromptLab/Benchmark collectors. Scope must be per-turn in the collecting ViewModel.
- **Sentinel-job cancellation theater:** any `Job` that does no work and exists only to be cancelled. If `stopResponse()` doesn't touch a socket, a native handle, or the collecting coroutine, it is a no-op.
- **Catching `Exception` around cancellable loops without rethrowing `CancellationException`:** converts Stop into a spurious Error bubble ("Connection failed: ...") — user-visible lie.
- **Closing the `Conversation` on Stop:** `close()` invalidates the handle and forces full re-creation on follow-up (slow + retry-path interaction). Use `cancelProcess()` (halts generation, keeps conversation usable) [ASSUMED: conversation reusable after cancelProcess — consistent with cancel-vs-close naming, but no official doc found; verify on-device; fallback = resetConversation + lazy recreate, which the provider already does].
- **Retrying after cancellation:** `sendContentsWithRetry` must `ensureActive()` before any retry — a Stop that surfaces as an engine-flavored error must not trigger recovery + re-emit.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Hot-sharing a cold inference flow | Custom `BroadcastChannel` / manual token cache for rotation | `shareIn(scope, Eagerly, replay = 1)` (coroutines 1.11.0, in catalog) | Rotation survival + replay + single-upstream are exactly shareIn's semantics; hand-rolled caches duplicate/drop tokens at the terminal boundary |
| HTTP cancellation | Socket close / `response.close()` plumbing / interrupt flags | `OkHttp Call.cancel()` (OkHttp 4.12.0, in catalog) | Documented thread-safe immediate cancel; unblocks `readUtf8Line()` with `IOException("Canceled")` [CITED: OkHttp Call.cancel docs] |
| Native generation halt | Prompt-injection "stop" / conversation close-recreate | `Conversation.cancelProcess()` (0.17.1, verified in AAR) | Purpose-built API; close-recreate is orders of magnitude slower and races the retry loop |
| Test virtual time | `Thread.sleep` in cancellation tests | `runTest` + Turbine (`turbine` 1.2.1, `coroutines-test` 1.11.0, in catalog) | Deterministic; `runTest` skew detection catches real-delay bugs |

**Key insight:** Every mechanism this phase needs already exists in the pinned catalog — the work is deletion (sentinels) + handle plumbing (Call, Conversation) + one transparency fix (CancellationException), not new machinery.

## Common Pitfalls

### Pitfall 1: Trailing tokens after Stop (remote)
**What goes wrong:** Stop pressed, spinner clears, then 1-3 more tokens appear or an Error bubble pops.
**Why it happens:** (a) Only `generationJob` cancelled — socket still open, buffered SSE lines already in `BufferedSource` get emitted before the coroutine notices; (b) `CancellationException` converted to `StreamToken.Error("Connection failed...")`.
**How to avoid:** `Call.cancel()` first (socket teardown makes the source throw immediately), then job cancel; rethrow `CancellationException`.
**Warning signs:** Error bubbles containing "Canceled" or "Connection failed" right after Stop in manual testing.

### Pitfall 2: Stop → immediate follow-up hangs or wedges spinner
**What goes wrong:** Next `sendMessage` never streams, or `isStreaming` stuck true.
**Why it happens:** Stale `activeCall`/`activeJob` handle cancelled the NEW turn's stream; or terminal `Done` from the old turn arrived late and cleared the new turn's state; or `stopResponse()` closed something the next turn needs.
**How to avoid:** `onCompletion { activeCall.set(null) }` + null `activeHelper` in `stopGeneration`; never `close()` the conversation on Stop; per-turn (not shared) scope so generations are disjoint.
**Warning signs:** Second message after Stop shows spinner forever; needs app restart.

### Pitfall 3: Rotation duplicates or drops tokens
**What goes wrong:** Rotating mid-stream restarts inference (duplicate tokens billed twice, conversation corrupted) or loses the last token.
**Why it happens:** Collecting the cold helper flow from a Composable (`collectAsStateWithLifecycle`) instead of the ViewModel, so recomposition/re-subscription restarts upstream.
**How to avoid:** Collection stays in `ChatViewModel` (survives config change — already the case); `replay = 1` covers any UI-side re-subscription to the *shared* flow. Verify `ChatScreen.kt:166-197` collects only `uiState` (StateFlow), never `runInference` — confirmed: it collects `uiState.isStreaming`/`streamingContent` only. No Composable change needed.
**Warning signs:** Token duplication in rotation manual test; double billing on metered endpoints.

### Pitfall 4: Retry loop resurrects a stopped turn (local)
**What goes wrong:** Stop on local backend briefly halts, then tokens resume.
**Why it happens:** `cancelProcess()` surfaces as an exception inside `sendContentsWithRetry`, which matches the `isEngineError` branch and retries.
**How to avoid:** `ensureActive()` as the first statement of the `catch` block (throws CancellationException before retry logic); also check `conversation.isAlive` semantics post-cancel on-device.
**Warning signs:** Tokens resuming ~1s after Stop on local models.

### Pitfall 5: Phase 47 tool loop runs away (25-round cap)
**What goes wrong:** Future multi-turn loop ignores Stop between rounds.
**Why it happens:** Loop checks only `finish_reason`, never coroutine cancellation.
**How to avoid (hooks only, this phase):** Contract — every tool-round boundary calls `currentCoroutineContext().ensureActive()`; the loop collects the SAME shared per-turn flow (so turn-scope cancel propagates); cap constant defined once and checked with `>=` not `==`. Full loop is Phase 47.
**Warning signs:** N/A this phase — verify hook presence in review, not behavior.

## Code Examples

### Retaining the OkHttp Call in LMStudioProvider.chat
```kotlin
// Source pattern: OkHttp newCall + Call.cancel docs
// https://square.github.io/okhttp/4.x/okhttp/okhttp3/-ok-http-client/new-call/
@Volatile private var currentCall: Call? = null

fun chat(request: ChatRequest, onCallCreated: (Call) -> Unit = {}): Flow<StreamToken> = callbackFlow {
    val call = client.newCall(
        Request.Builder().url("$baseUrl/api/v1/chat")
            .post(Json.encodeToString(LmStudioChatRequest.serializer(), body).toRequestBody(JSON))
            .build()
    )
    currentCall = call
    onCallCreated(call)
    try {
        call.execute().use { response ->
            // existing manual SSE loop over response.body.source(), unchanged,
            // with CancellationException rethrow added
        }
    } finally {
        currentCall = null
    }
    awaitClose { currentCall = null }
}.flowOn(Dispatchers.IO)

fun cancelChat() = currentCall?.cancel()
```
Note: `callbackFlow` + `awaitClose` also gives structured-concurrency cancellation for free (collector cancel → block cancelled → `finally` clears). Either `flow {}` with explicit handle or `callbackFlow` satisfies the requirement; the `Call` handle is the non-negotiable part. Request/URL/body construction must match `LmStudioApi.chat` exactly (`api/v1/chat`, JSON body) — the DTOs (`LmStudioChatRequest`) are reused unchanged.

### LiteRT cancel accessor
```kotlin
// LiteRTLmProvider — cancelProcess() VERIFIED in 0.17.1 AAR via javap (this session)
fun cancelActiveGeneration() {
    try { activeConversation?.cancelProcess() }
    catch (e: Exception) { Timber.w(e, "LiteRTLm: cancelProcess failed") }
}
```

### Tool-loop hook contract (for Phase 47, defined here)
```kotlin
// At every round boundary of the future tool loop:
currentCoroutineContext().ensureActive()   // throws CancellationException on Stop — never swallow
check(round < MAX_TOOL_ROUNDS) { "Tool loop cap exceeded" }  // 25-round cap, >= semantics
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Sentinel `Job` as cancellation proxy | Direct transport-handle cancellation (`Call.cancel` / `cancelProcess`) | This phase (sentinel admitted as placeholder in code comments) | Stop actually stops; no phantom jobs |
| Retrofit `suspend` hiding the `Call` | Raw OkHttp `Call` retained for the SSE path | This phase | Explicit cancel handle per RUNTIME-14 |
| `catch (Exception) → Error token` | `CancellationException` rethrown | This phase | Stop no longer surfaces as a fake network error |
| litertlm 0.13.1 API surface | 0.17.1 `cancelProcess()`, `channels`, `Capabilities` | Phase 45-02 (verified) | Local stop has a first-class API; no JNI work needed |

**Deprecated/outdated:**
- AGENTS.md STACK.md §6 still describes "llama.cpp via JNI/NDK, GGUF exclusively" — superseded: local inference is LiteRT-LM `.litertlm`/`.task` (Phases 45+, v1.8 removed GGUF/llama.cpp per REQUIREMENTS.md Out of Scope). Planner: follow the tree + Phase 45 summaries, not AGENTS.md §6, for anything inference-shaped.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `Conversation.cancelProcess()` leaves the conversation reusable for the next turn (cancel ≠ close) | Architecture Patterns / Anti-Patterns | MEDIUM — if it invalidates the handle, follow-up turns must `resetConversation()` first; add on-device verification step and keep the lazy-recreate fallback |
| A2 | `Call.cancel()` surfaces in the SSE reader as `IOException("Canceled")`, handled silently by the existing read-loop catch | Code Examples / Pitfall 1 | LOW — standard OkHttp behavior, but verify no Error token leaks in manual test |
| A3 | Retrofit `suspend` cancellation would also cancel the call, but raw OkHttp is still required to meet the explicit "true Call.cancel()" requirement text | Standard Stack | LOW — belt-and-braces; raw Call satisfies both readings |
| A4 | No Composable collects `runInference` directly (rotation safety holds without UI changes) — verified for ChatScreen; PromptLab/Benchmark paths untouched | Pitfall 3 | LOW — grep showed only ViewModel/Worker collectors; executor should re-grep to confirm |

## Open Questions

1. **Does `cancelProcess()` mid-`sendMessageAsync`-Flow terminate the Flow promptly, or does the collector hang until timeout?**
   - What we know: method exists (AAR-verified); Warped collects the Flow on `Dispatchers.Default`.
   - What's unclear: promptness + terminal behavior (exception vs clean completion) — no public docs found in this session.
   - Recommendation: on-device manual test (Stop on local, measure token-halt latency, note any terminal Error); code defensively — belt-and-braces coroutine cancel alongside `cancelProcess()`, `ensureActive()` in retry path.
2. **Should a Stop with a non-empty partial buffer persist a truncated assistant message or discard it?**
   - What we know: current `stopGeneration()` discards (`streamingContent = ""`); natural completion persists.
   - What's unclear: desired UX — LM Studio desktop keeps partial text on stop.
   - Recommendation: planner's discretion; flag as a discuss-or-defer item (no CONTEXT.md guidance). Default = keep current discard behavior (smallest change).

## Environment Availability

Phase is code-only (no new services/CLIs). Build environment already proven by Phase 45 gates.

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Android SDK + Gradle wrapper | compile/test | ✓ (Phase 45 ran assembleDebug/Release + unit suite green) | AGP 9.3.0 / Gradle 9.5.0 | — |
| Real arm64 device | Stop-latency + rotation manual verification | ✗ (not in this environment) | — | Human verification step owned by plan gate (same pattern as 45-02 device smoke) |
| JVM unit tests | Turbine/MockK cancellation tests | ✓ | JUnit5 5.14.4, turbine 1.2.1 | — |

**Missing dependencies with no fallback:** none for implementation.
**Missing dependencies with fallback:** arm64 device for manual Stop/rotation verification → `checkpoint:human-verify` gate (precedent: 45-02 device smoke).

## Security Domain

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | No auth change (API key path untouched) |
| V3 Session Management | No | — |
| V4 Access Control | No | — |
| V5 Input Validation | Yes (unchanged surface) | `InputSanitizer` already applied at provider entry; raw-OkHttp rewrite must keep sending the sanitized body — do not rebuild the request from unsanitized fields |
| V6 Cryptography | No | — |
| V9 Communications | Partial | `Call.cancel()` tears down TLS cleanly; no secret logging — keep `HttpLoggingInterceptor.Level.BASIC` (never BODY/H EADERS on the chat path; API keys ride `x-api-key`) |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Unsanitized prompt forwarded after transport rewrite | Tampering | Reuse `LmStudioChatRequest` DTO + sanitized content; regression test asserting sanitize-before-send still holds |
| API key in logs on the new raw-OkHttp path | Information disclosure | No BODY-level logging; existing `x-api-key` interceptor reused, not duplicated |

## Test Strategy (for planner)

No new test-infra needed; `nyquist_validation: false` in `.planning/config.json` (Validation Architecture section omitted per protocol). Existing suite: 180 tests green post-45-02; **zero** existing ChatViewModel/helper tests — all new tests are greenfield JVM unit tests (LiteRT native and OkHttp socket behavior stay behind interfaces / manual device test).

| Test | Technique | Covers |
|------|-----------|--------|
| Stop cancels shared upstream; no trailing tokens | Fake `LlmModelHelper` emitting numbered Deltas via `flow { delay(); emit() }`; `shareIn(Eagerly, 1)` in `runTest`; cancel turn scope; `test { expectNoEvents() }` (turbine 1.2.1) | RUNTIME-13, RUNTIME-14 (no-trailing-tokens) |
| `replay=1` re-collect gets last token, upstream not restarted | Same fake; two collectors; assert upstream `onStart` ran once | RUNTIME-13 rotation clause |
| Stop-then-follow-up: second turn streams cleanly | Fake helper; stop turn 1 mid-stream; start turn 2; assert full sequence + `isStreaming` transitions | Locked "no hang/wedge/stale spinner" |
| `CancellationException` not swallowed | Fake `Flow` that throws `CancellationException`; assert it propagates (not mapped to `StreamToken.Error`) | Defect #6 regression |
| Retry loop does not retry when cancelled | `LiteRTLmProvider` with mocked engine throwing engine-flavored error under a cancelled scope; assert single attempt (`ensureActive` guard) | Pitfall 4 |
| `stopResponse` calls `Call.cancel` / `cancelProcess` | MockK verify on retained handle (remote: mock `Call`; local: mock conversation accessor) | RUNTIME-14 wiring |
| Rotation manual test + stop-latency manual test | `checkpoint:human-verify` on real device (precedent: 45-02 smoke) | Rotation clause, "halt immediately" |

## Sources

### Primary (HIGH confidence)
- Tree source: `ChatViewModel.kt` (sendMessage/stopGeneration), `LlmModelHelper.kt`, `LmStudioHelper.kt`, `LiteRtLlmHelper.kt`, `LMStudioProvider.kt`, `LiteRTLmProvider.kt`, `LmStudioApi.kt`, `ProviderRouter.kt`, `ChatScreen.kt` — read verbatim this session
- `javap` on Gradle-cached `litertlm-android-0.17.1.aar` — `Conversation.cancelProcess()` + Flow-returning `sendMessageAsync` confirmed present (this session)
- `gradle/libs.versions.toml` — coroutines 1.11.0, OkHttp 4.12.0, turbine 1.2.1, coroutines-test 1.11.0, litertlm 0.17.1
- Phase 45 summaries: `45-01-SUMMARY.md` (catalog pins), `45-02-SUMMARY.md` (0.17.1 AAR verification, R8, cache, allowlist)
- OkHttp `Call.cancel()` docs — https://square.github.io/okhttp/4.x/okhttp/okhttp3/-call/cancel/
- Kotlin `shareIn` docs — https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/share-in.html
- Kotlin cancellation semantics (`CancellationException` rethrow) — https://kotlinlang.org/docs/cancellation-and-timeouts.html

### Secondary (MEDIUM confidence)
- CONTEXT.md 46 + REQUIREMENTS.md RUNTIME-13/14 + STATE.md (phase boundary, locked decisions)

### Tertiary (LOW confidence)
- None — every load-bearing claim above is tree- or AAR-verified. Assumption-tagged items (A1–A4) need on-device confirmation.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all versions from the tree's own catalog + Phase 45 verification record; zero new deps.
- Architecture: HIGH — every defect location cited to file:line read this session; cancelProcess verified in AAR bytecode.
- Pitfalls: HIGH — derived directly from the cited defects, not from general training knowledge.

**Research date:** 2026-09-27
**Valid until:** ~30 days (stable domain: pinned catalog + verified AAR surface)
