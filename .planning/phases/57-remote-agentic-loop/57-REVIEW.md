---
phase: 57-remote-agentic-loop
reviewed: 2026-09-29T00:00:00Z
depth: standard
files_reviewed: 15
files_reviewed_list:
  - app/src/main/java/com/warped/data/agentic/ToolCallAccumulator.kt
  - app/src/main/java/com/warped/data/agentic/ToolCapabilityMatrix.kt
  - app/src/main/java/com/warped/data/remote/dto/OpenAiChatRequest.kt
  - app/src/main/java/com/warped/data/remote/dto/StreamChunks.kt
  - app/src/main/java/com/warped/data/remote/dto/AnthropicDtos.kt
  - app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt
  - app/src/main/java/com/warped/data/remote/provider/AnthropicProvider.kt
  - app/src/main/java/com/warped/data/remote/provider/CompatToolLoop.kt
  - app/src/main/java/com/warped/data/remote/provider/OllamaProvider.kt
  - app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt
  - app/src/main/java/com/warped/data/remote/provider/CustomProvider.kt
  - app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt
  - app/src/main/java/com/warped/data/remote/provider/LmStudioHelper.kt
  - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
  - app/src/main/java/com/warped/domain/model/ChatMessage.kt
  - app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt
findings:
  critical: 2
  warning: 5
  info: 4
  total: 11
status: fixed
fixed_at: 2026-09-29T00:00:00Z
fix_commits:
  - 9a36ab71 CR-01
  - ba8227b9 WR-01
  - eebd81d7 CR-02
  - efb22cd1 WR-02
  - fb9d6001 WR-03
  - c39f791a WR-04
  - ac5de03e WR-05
  - 55e9cad7 IN-01/IN-02
fix_tests: 463 tests, 0 failures, 0 errors (full :app:testDebugUnitTest)
---

# Phase 57: Code Review Report

**Reviewed:** 2026-09-29T00:00:00Z
**Depth:** standard
**Files Reviewed:** 15 (+ ProviderRouter.kt / LmStudioHelper.kt as wiring context)
**Status:** issues_found

## Summary

Reviewed the full Phase 57 remote agentic loop (57-01 OpenAI tracer + 57-02 dialect
expansion) with emphasis on production-wiring completeness, dialect correctness, SSE
reassembly, retry bounds, secret isolation, cap enforcement, and Stop propagation.

The loop internals are carefully built: exactly-one retry holds in all three drivers
(`fallbackDone` per-turn flags), the 5-call cap is call-counted including validation
short-circuits (bounded termination — at most ~6 tool rounds before `capFed` forces
`Done`), no envelope mixing (compat driver uses `tool_calls`/`tool_call_id` throughout;
native paths untouched when unarmed), and secret isolation is clean (loop executors
reference only the Tavily/fetch singletons; endpoint keys stay on per-provider
interceptors, including the manual-OkHttp POSTs which correctly ride the
intercepted `client`).

**But the loop never executes in production.** Every provider constructor takes its
tool-loop collaborators as nullable default-null params, and no production call site
passes them: `ProviderRouter.resolve()` constructs all five providers unarmed, and
`LmStudioHelper.createProvider()` — the only path the live chat flow actually uses —
also constructs `LMStudioProvider` without collaborators. `isLoopArmed()` returns
`false` on the first null collaborator, so every production turn takes the plain path.
Worse, the one half of the phase that IS live — the `ChatViewModel` VM pre-search skip —
mirrors arming without checking collaborators, so grounded LM Studio turns now skip VM
grounding while the provider loop stays unarmed: those turns lose all web grounding
silently. Phase goal AGENT-03 is therefore not achieved at runtime, plus a silent
grounding regression on the working remote path.

## Critical Issues

### CR-01: Remote tool loop is dead code in production — no collaborator injection anywhere

**File:** `app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt:31-66`, `app/src/main/java/com/warped/data/remote/provider/LmStudioHelper.kt:158-168`
**Issue:** All five tooled providers arm only when nullable collaborators
(`tavily`, `multiUrlFetcher`, `webPageFetcher`, `advancedPreferences`) are injected,
and every `isLoopArmed()` returns `false` when any is null (e.g.
`OpenAIProvider.isLoopArmed`: `val prefs = advancedPreferences ?: return false`).
No production call site injects them:

- `ProviderRouter.resolve()` (the only constructor of OpenAI/Anthropic/Ollama/Custom
  providers) passes only `baseUrl/modelId/apiKey/inputSanitizer` — collaborators stay
  null, so `isLoopArmed()` is unconditionally false.
- The live chat flow (`ChatViewModel.sendMessage` → `resolveHelper` →
  `LmStudioHelper.runInference` → `createProvider()`) constructs `LMStudioProvider`
  with only `baseUrl/modelId/apiKey/inputSanitizer` — the LM Studio compat loop is
  likewise unarmed on every production turn.
- `resolve()` is documented as legacy listModels/testConnection-only; chat goes through
  the helper surface, where `resolveHelper` supports only LM_STUDIO/LOCAL and throws
  for OPENAI/ANTHROPIC/OLLAMA/CUSTOM — so four of the five tooled `chat()` loops are
  unreachable in production chat twice over (no helper route AND no collaborators).

The executor summaries acknowledge this ("ProviderRouter.resolve() still constructs
unarmed (production helper/DI wiring deferred as intended)"), but deferring the wiring
means the phase success criteria ("An armed OpenAI endpoint turn performs at least one
web_search/web_fetch round-trip…", "Remote endpoint users get agentic search/fetch…")
cannot be true at runtime. The ~1800 lines of loop/driver code are verified only by
unit tests, never exercised by the app.
**Fix:**
```kotlin
// ProviderRouter: inject the singletons it already can reach via Hilt and pass them.
class ProviderRouter @Inject constructor(
    private val apiKeyStore: ApiKeyStore,
    private val inputSanitizer: InputSanitizer,
    private val tavily: TavilySearchRepository,
    private val multiUrlFetcher: MultiUrlFetcher,
    private val webPageFetcher: WebPageFetcher,
    private val advancedPreferences: AdvancedPreferences,
    ...
) {
    fun resolve(endpoint: Endpoint, modelId: String): LlmProvider {
        ...
        ProviderType.OPENAI -> OpenAIProvider(
            ..., inputSanitizer = inputSanitizer,
            tavily = tavily, multiUrlFetcher = multiUrlFetcher,
            webPageFetcher = webPageFetcher, advancedPreferences = advancedPreferences,
        )
        // ... same for Anthropic/Ollama/LM_STUDIO/Custom
    }
}
// LmStudioHelper: same four collaborators via @Inject constructor, forwarded in
// createProvider(). Until then, gate the VM skip (CR-02) on the same condition.
```

### CR-02: VM pre-search skip is live while provider loops are dead — grounded remote turns silently lose all web grounding

**File:** `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt:535-543`
**Issue:** `remoteArmed` mirrors the provider decision with grounding + matrix mode +
internet, but NOT collaborator presence (the VM cannot see provider collaborators, and
nothing passes that signal). Since CR-01 guarantees providers are always unarmed in
production, every grounded + online remote turn (ATTEMPT/ATTEMPT_FALLBACK/
NATIVE_ANTHROPIC — i.e. all of OpenAI/Ollama/LM Studio/Custom/Anthropic) now skips the
Phase-55 VM Tavily pre-search (`requestUserText = augment(..., null, ...)`) while the
provider runs its plain unarmed path. Net effect vs pre-57: the turn gets neither VM
grounding nor loop grounding — a model-only answer with no notice and no banner (the
TOOLS_UNSUPPORTED notice only fires on an armed 400-rejection, which never happens).
This is directly user-visible on LM Studio, the one remote path whose chat flow works.
**Fix:**
```kotlin
// Option A (correct): arm the providers (CR-01 fix) so the skip is justified.
// Option B (stopgap until wiring lands): do not skip VM pre-search for remote
// providers until provider arming is proven — e.g. remove `|| remoteArmed` from
// `loopArmed`, or expose a real armed-signal from the serving layer.
val loopArmed = localArmed // remote skip re-enabled once CR-01 wiring lands
```

## Warnings

### WR-01: Stop cannot reach the armed LM Studio compat loop — helper handle never retained on the armed branch

**File:** `app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt:124-141`, `app/src/main/java/com/warped/data/remote/provider/LmStudioHelper.kt:105-107`
**Issue:** `ChatViewModel.stopGeneration()` only calls `helper?.stopResponse()`, which
cancels `LmStudioHelper.activeCall` retained via the 3-arg `chat(request, integrations,
onCallCreated)` hook. But the armed branch of the 1-arg `chat(request)` override calls
`CompatToolLoop.runTurn(... onCallCreated = { currentCall = it } ...)` — retaining the
socket on the *provider* field, never invoking the helper hook. The unarmed branch
(`chat(request, emptyList()).collect { emit(it) }`) drops the hook too (default `{}`).
So on an armed turn, `stopResponse()` cancels nothing; `LMStudioProvider.cancelChat()`
exists but has zero callers. `generationJob?.cancel()` still cancels the collector
coroutine, but the blocking `readUtf8Line()` socket is not torn down promptly (the
native-path `teardown` watcher only exists in the 3-arg overload). Latent today
(CR-01), breaks Stop the moment arming lands.
**Fix:** In the armed branch, forward the compat-loop handle to the helper hook:
thread the 3-arg `onCallCreated` param through the 1-arg override into
`runTurn(onCallCreated = { currentCall = it; onCallCreated(it) })`.

### WR-02: CustomProvider armed path sends unsanitized user content (no InputSanitizer at all)

**File:** `app/src/main/java/com/warped/data/remote/provider/CustomProvider.kt:37-54,99-110`
**Issue:** `CustomProvider` takes no `InputSanitizer` (pre-existing gap — verified no
`sanitize` in the pre-57 file), and the NEW 57-02 armed branch maps
`OpenAiMessage(role=..., content=it.content)` raw, while every sibling armed branch
(OpenAI, Anthropic, Ollama, LM Studio) sanitizes USER messages. New code entrenches the
gap on the exact path that will execute model-directed tool calls (T-57-01-adjacent:
untrusted user text reaches the wire unsanitized).
**Fix:** Add `private val inputSanitizer: InputSanitizer` to the constructor, pass it
in `ProviderRouter.resolve()`, and sanitize USER content in both the armed mapping and
`postPlainTurn`, matching the Ollama/LM Studio pattern.

### WR-03: Anthropic SSE "error" event emits a mid-round Error token, then the driver emits a second terminal Error

**File:** `app/src/main/java/com/warped/data/remote/provider/AnthropicProvider.kt:525-530`
**Issue:** `feedEvent` on `"error"` does `emit(StreamToken.Error(errorText))` directly
from inside `postRound`, then returns true → the round ends with `hadTokens=false`,
empty toolCalls → `runTooledLoop` emits `Error("No content in response")`. Two Error
tokens for one failure; worse, the mid-round emit drives `ChatViewModel` into the error
terminal state (`isStreaming=false`, `isGenerating=false`) while the driver keeps
running the turn. Errors must be returned as data (`AnthropicRoundResult.error`), never
emitted mid-round.
**Fix:**
```kotlin
"error" -> {
    pendingError = event.delta?.text ?: event.delta?.thinking ?: "Anthropic error"
    return true
}
// ... after the read loop:
if (pendingError != null) return AnthropicRoundResult(error = pendingError)
```

### WR-04: Compat SSE reassembly is not finish-gated; transport truncation executes partial accumulations

**File:** `app/src/main/java/com/warped/data/remote/provider/CompatToolLoop.kt:380-420`, `app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt:474,502,527-530`
**Issue:** The compat driver never tracks `finish_reason` — any accumulated
`tool_calls` fragments are returned as complete when `[DONE]` arrives (per plan), but
ALSO when the read loop dies early: the `catch (e: IOException)` around the SSE loop
only logs (`Timber.e(... "SSE stream read failed"`) and falls through to
`accumulator.complete()`. A truncated stream's partial-arguments call is then parsed
and, if the truncated prefix happens to be valid JSON with a `query`/`url` key,
executed as a real Tavily/fetch call (wallet + wrong-result risk). `parseToolArgs`
degradation catches garbage, but not valid-prefix truncation. Related: `OpenAIProvider`
tracks `toolFinishSeen` (lines 474/502) but never reads it — dead variable proving the
gate was intended and dropped.
**Fix:** Track `finish_reason:"tool_calls"` (as OpenAI does) and treat
`IOException`-truncated rounds as transport errors (`CompatRoundResult(error=...)`)
unless a finish signal was seen; delete or use `toolFinishSeen`.

### WR-05: Anthropic echo sends synthesized `<think>` markers as plain text; thinking-shape 400s bypass the graceful fallback

**File:** `app/src/main/java/com/warped/data/remote/provider/AnthropicProvider.kt:275,318-319`
**Issue:** `textParts` accumulates provider-synthesized `"<think>"`/`"</think>"`
markers plus thinking text, and `toolUseEcho` replays them as assistant `text` blocks.
With `thinking` enabled in `buildBody`, Anthropic expects thinking blocks (with
signatures) echoed back, not flattened text — a strict endpoint can 400 the follow-up
round, and that 400 body mentions "thinking"/"signature", NOT "tool"/"function", so
`isToolsRejection` returns false and the turn dies with a hard `HTTP 400` error instead
of the designed one-retry-with-notice. The classifier's substring scope makes the
graceful-fallback guarantee narrower than claimed for the Anthropic dialect.
**Fix:** Exclude thinking content/markers from `textParts` used in the echo (or replay
as proper `thinking` blocks); and/or extend the retry classifier to the Anthropic
thinking-signature rejection shape.

## Info

### IN-01: Dead variables — unread assignments in SSE readers

**File:** `app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt:451,466-468`, `app/src/main/java/com/warped/data/remote/provider/CompatToolLoop.kt:352-355,384`
**Issue:** `toolFinishSeen` is assigned on both SSE paths but never read (WR-04);
`currentEvent` is assigned from `event:` lines but never read in either driver (Anthropic
correctly notes the type rides inside data JSON — the OpenAI-compat readers kept the
dead variable).
**Fix:** Delete `currentEvent`; either consume `toolFinishSeen` (WR-04 fix) or delete it.

### IN-02: Assistant echo encodes explicit `"content":null` on tool_calls messages

**File:** `app/src/main/java/com/warped/data/remote/dto/OpenAiChatRequest.kt:121-136`
**Issue:** The assistant echo `OpenAiMessage(role="assistant", toolCalls=...)` leaves
`content=null`, which kotlinx (explicitNulls default true) serializes as
`"content":null`. OpenAI accepts this; strict compat servers (the ATTEMPT_FALLBACK
targets) may 400 it. The fallback absorbs the failure, but it wastes the attempt round
and shows the unsupported notice for what is actually a well-supported endpoint.
**Fix:** Add `@EncodeDefault(NEVER)`-style omission for null content or send `""`.

### IN-03: Banner copy duplicates the notice constant instead of referencing it

**File:** `app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt:402-407`
**Issue:** The `TOOLS_UNSUPPORTED` banner branch hard-codes a copy of
`ToolCapabilityMatrix.TOOLS_UNSUPPORTED_NOTICE` ("mirrors" per the comment). The VM
routes on constant equality while the UI renders a duplicate string — future copy edits
will drift apart silently.
**Fix:** Render the constant (pass it through the notice model) or add a test asserting
equality.

### IN-04: Anthropic fragment feed has no delta-type discrimination

**File:** `app/src/main/java/com/warped/data/remote/provider/AnthropicProvider.kt:512-519`
**Issue:** Any `content_block_delta` carrying `partial_json` feeds the accumulator at
`event.index`, regardless of block type. Wire-correct today (only `input_json_delta`
carries `partial_json`), but a text-block index colliding with a tool-use index would
poison reassembly. Tolerant-by-design; noting for future strictness.
**Fix:** Gate the feed on `delta.type == "input_json_delta"` (requires adding the
`type` field to `AnthropicDelta`) or document the tolerance assumption.

## Verified (no finding)

- **Exactly-one retry:** all three drivers (`OpenAIProvider`, `AnthropicProvider`,
  `CompatToolLoop`) gate on `attachedTools != null && !fallbackDone` with per-turn
  state; the plain replay sends `tools=null`, and the classifier gate (`body.tools !=
  null`) makes a second rejection impossible. No retry storm, no infinite loop.
- **5-call cap in all paths:** call-counted (`callsUsed++` per call including validation
  short-circuits), parallel Anthropic `tool_use` blocks each count, `capFed` drops tools
  for the answer round and finishes on a post-cap tool request. Termination bounded.
- **Secret isolation:** loop code references only Tavily/fetch singletons; manual-OkHttp
  POSTs correctly ride the intercepted endpoint client; `grep apikey` scope is clean.
- **Dialect correctness:** Ollama rides compat `/v1` (never the native `tool_name`
  envelope), LM Studio/Custom attempt-then-fallback, Anthropic native
  `tool_use`/`tool_result`; unarmed paths byte-identical (NEVER-encoded additive DTOs).
- **Cancel contract inside drivers:** `ensureActive` per round + per call, CE rethrown
  first, `Canceled`-IOException silent — correct wherever the driver actually runs.

---

_Reviewed: 2026-09-29T00:00:00Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
