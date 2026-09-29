---
phase: 56-local-agentic-loop
plan: 2
subsystem: agentic-loop
tags: [litertlm, manual-tools, tool-loop, thinking-channel, transient-status, jvm-tests, checkpoint-pending]
status: checkpoint-pending

# Dependency graph
requires:
  - phase: 56-local-agentic-loop plan 01
    provides: LocalToolLoop pure policy + web_search/web_fetch schemas + gemma-4 capability flags + KV-cache hygiene
  - phase: 55-tavily-search
    provides: TavilySearchRepository.search + MultiUrlFetcher.fetchAll consumed as tool executors
  - phase: 52-grounding
    provides: GroundingPrecedence.shouldGround + SYSTEM_PROMPT persona reused by the loop
provides:
  - App-driven manual tool loop in LiteRTLmProvider (AGENT-01 observable behavior)
  - Transient ToolStatus row wiring end-to-end (provider token → VM state → chip)
  - LiteRTLmLoopTest (23 loop invariants) + VM transient-row regression test
  - Streaming-primary transport with documented A1 fallback trigger
affects: [56-local-agentic-loop device checkpoint (this plan task 3), 57-remote-agentic-loop (schema + fused-string reuse)]

# Tech tracking
tech-stack:
  added: []
  patterns: ["Manual loop over Conversation.sendMessageAsync collected to terminal (blocking sendMessage fallback documented, not implemented)", "Inline ToolStatus emits in sequential flow (no channelFlow sink — identical observable contract)", "Shared pure arming predicate consumed by provider (authoritative) and VM (pre-search skip)"]

key-files:
  created: [app/src/test/java/com/warped/data/local/inference/LiteRTLmLoopTest.kt]
  modified: [app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt, app/src/main/java/com/warped/data/agentic/LocalToolLoop.kt, app/src/main/java/com/warped/domain/model/ChatRequest.kt, app/src/main/java/com/warped/ui/chat/ChatViewModel.kt, app/src/main/java/com/warped/ui/chat/ChatUiState.kt, app/src/main/java/com/warped/ui/chat/ChatScreen.kt, app/src/test/java/com/warped/data/remote/provider/LmStudioCancelTest.kt, app/src/test/java/com/warped/ui/chat/ChatCancellationTest.kt, app/src/test/java/com/warped/ui/chat/ChatGroundingRetryTest.kt, app/src/test/java/com/warped/ui/chat/ChatSubStateTest.kt, app/src/test/java/com/warped/ui/chat/ChatGroundingToggleTest.kt, app/src/test/java/com/warped/ui/settings/SettingsTavilyTest.kt]

key-decisions:
  - "Streaming-primary transport (collect sendMessageAsync to terminal); A1 fallback to blocking sendMessage keyed on device step 7, not implemented blind"
  - "VM skips its Tavily pre-search when the loop is armed — otherwise the model never needs web_search and credits stack 1+5"
  - "Inline ToolStatus emits instead of a MutableStateFlow sink — sequential execution makes the sink redundant"
  - "Engine-error retry replays the whole agentic turn (matches plain-path wart; failing closed would strand the engine dead)"
  - "Loop thought rides Done.reasoning through the existing Thinking-toggle gate (helper nulls it when the toggle is off)"

requirements-completed: [AGENT-01]
requirements-pending: [AGENT-02]
# AGENT-02 (tool outputs pass the trust boundary end-to-end on device) needs the
# on-device smoke below; JVM side (mapped fused strings only, Text-only Deltas) is done.

# Metrics
duration: ~50min
completed: 2026-09-29
---

# Phase 56 Plan 02: Provider Loop + Transient Rows Summary

**Manual 5-call tool loop live in LiteRTLmProvider with status sink, thought routing, and arming gates; transient ToolStatus rows end-to-end; 425/425 unit green — device smoke checkpoint PENDING (no adb in this environment)**

## Performance

- **Duration:** ~50 min (incl. litertlm 0.17.1 bytecode verification, one stash-restore incident, HEAD-worktree proof)
- **Started:** 2026-09-29T01:55Z
- **Completed (autos):** 2026-09-29T02:15Z
- **Tasks:** 2/2 auto complete; 1/1 checkpoint pending
- **Files modified:** 13 (1 created, 12 modified)

## Accomplishments

- **Task 1 — Manual tool round loop in LiteRTLmProvider.** Per-turn arming
  snapshot (`LoopArmSnapshot`: per-chat override + global DataStore + loaded-engine
  allowlist lookup + validated internet) via the shared
  `LocalToolLoop.isLoopArmed` predicate. Armed turns create the conversation with
  fresh `WebSearchToolSet`/`WebFetchToolSet` instances,
  `automaticToolCalling=false`, and the pinned `TOOL_USE_SYSTEM_HINT`
  systemInstruction. Round driver: `coroutineContext.ensureActive()` per round,
  terminal = last `sendMessageAsync` emission, `Content.Text`-only Deltas
  (existing filter verbatim), thought channel accumulated to `Done.reasoning`,
  `ToolStatus(display)` on start + `ToolStatus(null)` in `finally`, results fed
  back as `Message.tool(ToolResponse)`, cap → `CAP_REACHED_STRING`, second
  post-cap tool request finishes instead of ping-ponging, executors never throw
  (`validateArgs` → offline gate → repo → map → `toolFailureMessage`),
  `CancellationException` always rethrows, engine-error recovery mirrors the
  plain path. Conversation rebuilds whenever the arming snapshot changes
  (T-56-10). Unarmed turns byte-identical incl. retry.
- **Task 2 — Transient ToolStatus rows.** `ChatInputState.toolCallActive`
  (nullable String, 47 shape): set on non-null `ToolStatus`, cleared on null /
  `ToolCompleted` / Done / Error / silent / Stop / new send. Rendered by a
  `Using <query|URL>` chip in the fetch-chip slot; thinking row yields while a
  tool runs. Rows never touch `ChatMessage`/Room/transcript.
- **VM/provider coherence.** VM skips its Tavily pre-search when
  `isLoopArmed` (same predicate, allowlist read, hoisted online check) but keeps
  the `SYSTEM_PROMPT` persona via null-block augment; pasted-URL prefetch
  unchanged. Per-chat override travels on new `ChatRequest.webOverride` so the
  provider owns its arming decision. Stop/new-send paths verified already
  complete (`generationJob.cancel()` + shared-singleton `fetcher.cancel()` +
  `stopResponse→cancelActiveGeneration`; in-flight tool calls die by CE, sockets
  by the singleton fetcher set) — no code change needed.
- **Tests.** `LiteRTLmLoopTest` 23/23 (channel routing, hint pin, arming truth
  table, status formats, unknown/blank/malformed/offline/key-missing zero-socket
  with MockK verify, search/fetch mapping, failure degradation, 3-round driver,
  6-call cap, post-cap finish, CE propagation) + VM transient-row lifecycle test.
  Full suite **425/425, 0 failures** (401 baseline + 24 new); `assembleDebug`
  clean; `runBlocking` gate clean (comments only); no `ToolStatus` writes outside
  UI state.

## Task Commits

Each task was committed atomically:

1. **Task 1: Manual tool round loop + invariant tests** - `09b8bce0` (feat)
2. **Task 2: Transient rows + loop-aware VM grounding** - `f4994281` (feat)

## Files Created/Modified

- `app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt` - arming snapshot, tooled config, `runToolLoop` driver, `executeToolCall`, `ConversationTurnTransport`, snapshot-aware acquire (modified)
- `app/src/main/java/com/warped/data/agentic/LocalToolLoop.kt` - `isLoopArmed` + `statusDisplay` pure helpers (modified)
- `app/src/main/java/com/warped/domain/model/ChatRequest.kt` - `webOverride` field (modified)
- `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` - ToolStatus wiring, Tavily-skip, allowlist dep, clears (modified)
- `app/src/main/java/com/warped/ui/chat/ChatUiState.kt` - `toolCallActive` + shim mirror (modified)
- `app/src/main/java/com/warped/ui/chat/ChatScreen.kt` - `Using …` chip, thinking-row yield (modified)
- `app/src/test/java/com/warped/data/local/inference/LiteRTLmLoopTest.kt` - 23 loop invariants (created)
- `app/src/test/java/com/warped/data/remote/provider/LmStudioCancelTest.kt` - 2 provider constructions updated (modified)
- `app/src/test/java/com/warped/ui/chat/ChatCancellationTest.kt` - VM ctor + transient-row test (modified)
- `app/src/test/java/com/warped/ui/chat/ChatGroundingRetryTest.kt` - VM ctor (modified)
- `app/src/test/java/com/warped/ui/chat/ChatGroundingToggleTest.kt` - VM ctor (modified)
- `app/src/test/java/com/warped/ui/chat/ChatSubStateTest.kt` - VM ctor (modified)
- `app/src/test/java/com/warped/ui/settings/SettingsTavilyTest.kt` - VM ctor (modified)

## Decisions Made

- Streaming-primary transport: the plan's primary path (collect each
  `sendMessageAsync` turn to its terminal) is implemented; the A1 blocking
  fallback is NOT pre-implemented — device step 7 is its empirical trigger
  (loop never fires on both Gemma 4s → swap transport, revert flags).
- VM Tavily-skip when armed (Rule 2): without it the checkpoint is
  unobservable (model pre-fed, never tool-calls) and worst-case credits go
  1 (pre-search) + 5 (loop) against the 56-01 "5/message" bound.
- Inline `ToolStatus` emits over the plan's `MutableStateFlow` sink: execution
  is sequential inside one flow collection, so a sink + merge adds machinery
  with zero observable difference. Same token sequence the tests pin.
- Engine-error retry replays the whole agentic turn (may re-burn ≤5 credits on
  rare engine death) rather than failing closed — a dead engine with no
  recovery strands all subsequent turns, which is worse.
- Thought rides `Done.reasoning` through the existing Thinking-toggle gate:
  with the toggle off the helper nulls it (user control wins). Smoke step 4
  therefore requires the Thinking toggle ON.
- `HOST_EXECUTED` stub bodies unchanged (never-throw, never invoked with
  automatic=false): "resolved" = the manual loop now executes tools for real
  and no executor output ever equals the marker.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Correctness] VM skips Tavily pre-search when the loop is armed**
- **Found during:** Task 2 (wiring the VM side; checkpoint observability analysis)
- **Issue:** Plan left the VM grounding branch untouched, but with pre-search
  always on the model is pre-fed and never tool-calls — the checkpoint step 3
  (status row appears) could never pass, and credits stack 1+5 vs the 5/call
  bound baked into 56-01.
- **Fix:** `loopArmed` check (same `isLoopArmed` predicate, allowlist + hoisted
  online read) gates only the Tavily branch; pasted-URL prefetch untouched;
  `SYSTEM_PROMPT` persona kept via null-block augment.
- **Files modified:** `ChatViewModel.kt`
- **Commit:** `f4994281`

**2. [Rule 2 - Correctness] Supporting surface the plan required but did not list**
- **Found during:** Tasks 1-2
- **Issue:** Provider-side `shouldGround(global + per-chat)` needs the per-chat
  value carried (provider has no conversationId); VM/provider arming must not
  diverge (shared predicate); status rows need a state slot + rendering.
- **Fix:** `ChatRequest.webOverride`, `LocalToolLoop.isLoopArmed` /
  `statusDisplay`, `ChatUiState.toolCallActive` (+ shim mirror), `ChatScreen`
  `Using …` chip + thinking-row yield.
- **Files modified:** `ChatRequest.kt`, `LocalToolLoop.kt`, `ChatUiState.kt`, `ChatScreen.kt`
- **Commit:** `09b8bce0` / `f4994281`

**3. [Rule 3 - Blocking] Constructor updates for new dependencies**
- **Found during:** Task 1 compile, Task 2 compile
- **Issue:** Provider gained 5 injected deps; VM gained the allowlist repo —
  2 + 5 existing test constructions stopped compiling.
- **Fix:** Relaxed mocks (provider) / strict mocks (VM — safe: capability read
  catches all into `false`) + one new VM transient-row test.
- **Files modified:** `LmStudioCancelTest.kt`, 5 VM test files
- **Commit:** `09b8bce0` / `f4994281`

**4. [Rule 3 - Blocking] Coroutine/suspend/MockK mechanics**
- **Found during:** Task 1 compile + test
- **Issue:** `ensureActive()` has no FlowCollector receiver; transport
  callbacks must be `suspend` to `emit`; Kotlin default-arg repo calls route
  via `$default` statics MockK cannot stub; member-extension driver needs an
  implicit dispatch receiver in tests.
- **Fix:** `coroutineContext.ensureActive()`; `suspend (String) -> Unit`
  callbacks; explicit `maxResults`/`onProgress` args in executors; `with(p)
  { runToolLoop(...) }` in tests.
- **Files modified:** `LiteRTLmProvider.kt`, `LiteRTLmLoopTest.kt`
- **Commit:** `09b8bce0`

---

**Total deviations:** 4 auto-fixed (2 correctness, 2 blocking mechanics)
**Impact on plan:** All required for the phase goal (observable loop, green
suite). No scope creep — no new tools, no new deps, no new surfaces.

## Issues Encountered

- **Stash-restore incident (process, no product impact).** A mid-execution
  `git stash -u` / `pop` pair (used to probe a suspect failure) left tracked
  edits in the stash when the pop failed silently (output swallowed). Recovered
  by `git checkout stash@{0} -- <files>` after verifying content, then dropped
  the stash. Lesson: never swallow git output; use worktrees — not stashes —
  for isolation probes.
- **Incremental-compile phantom.** A `CatalogDownloadedTest` "does not
  implement `deleteByFilePath`" error appeared in my tree while both files were
  byte-identical to HEAD. Proven phantom via an isolated HEAD worktree
  (`/tmp/warped-head`, since removed): clean `compileDebugUnitTestKotlin`
  SUCCESSFUL — HEAD content compiles. Both files untouched by this plan; no
  fix applied (out of scope, not a real break). Full suite green since.

## Threat Flags

None beyond the plan register. Disposition check per task file:
- T-56-07 (ToolResponse tampering): only `LocalToolLoop`-mapped fused strings
  fed as `ToolResponse`; `Content.Text`-only filter kept verbatim; test pins
  ToolResponse exclusion from Deltas. MITIGATED.
- T-56-08 (thought leak): `channels[thought]` → `Done.reasoning` only, never
  Deltas; KV flag from 56-01; test pins separation. MITIGATED.
- T-56-09 (status persistence): `toolCallActive` in-memory only; grep shows no
  writes outside UI state; VM test pins USER+ASSISTANT-only transcript. MITIGATED.
- T-56-10 (stale config): `LoopArmSnapshot` vs stored snapshot, reset on any
  change; nulled with config on reset/recovery. MITIGATED.
- T-56-11 (Stop ignored): `ensureActive` per round + per call, CE rethrows,
  shared-singleton `fetcher.cancel()` aborts sockets, `cancelProcess` halts
  native; single-cancel-path (no new scopes). MITIGATED (device step 5 to confirm).
- T-56-12 (replay re-trigger): history still `Message.model` read-only;
  `Message.tool` only live. MITIGATED.
- T-56-SC: zero new dependencies. HOLDS.

## Known Stubs

- `HOST_EXECUTED` bodies in both ToolSets remain schema-only markers by design
  (manual mode never invokes them; throwing across JNI would kill the
  conversation). Consumed — never forwarded — by the loop wired here. No
  follow-up plan needed.

## User Setup Required

- Device with 4GB+ free RAM + debug build installed.
- Tavily key stored in Settings with test-connection green (steps 2-3 burn
  real credits: ≤5/turn by construction).
- Thinking toggle ON for step 4 (loop thought obeys the toggle gate).

## Device Smoke (CHECKPOINT TASK — NOT RUN HERE)

No adb in this environment; on-device execution was not attempted. Resume agent
or user: install `app-debug.apk` from this tree (`09b8bce0` + `f4994281`) and run:

1. Load **gemma-4-E2B-it**, enable grounding, Tavily key green. Ask a
   fresh-info question (e.g. "latest Android Studio release notes").
2. **EXPECT:** transient `Using web_search: <query>` row, possibly
   `Using web_fetch: <url>`; final answer with numbered citations + Fuentes;
   rows gone after the turn and after restart (never persisted).
3. **EXPECT:** thinking (if any) only in the Thinking panel, never the answer.
4. Press **Stop** mid-tool-call: **EXPECT** halt in ~1s, no lagging text.
5. Airplane mode: **EXPECT** Phase-55 offline message, no socket, no credit burn.
6. Missing key (clear Tavily key): **EXPECT** model-only answer mentioning the
   Settings path (tool-time string, no socket).
7. **A1 CHECK:** if `toolCalls` never fires on BOTH Gemma 4 models, report
   which — its `supportsFunctionCalling` flag reverts to false per the
   verified-only rule and the transport swaps to blocking `sendMessage` on
   `Dispatchers.Default` (do NOT silently downgrade).

**Resume signal:** Type "approved" with per-step results, or describe which
step failed and what was observed.

## Next Phase Readiness

- 57-remote-agentic-loop consumes: provider-neutral tool names/schemas,
  `statusDisplay` copy shape, fused-string trust pattern. Untouched.
- If step 7 trips on a model: revert its flag (1-line allowlist edit +
  `ModelAllowlistTest` expectation), rescope note in the checkpoint reply.

---

*Phase: 56-local-agentic-loop*
*Plan: 2 (autos complete, device checkpoint pending)*
*Completed: 2026-09-29*

## Self-Check: PASSED

- All 13 files exist on disk with the expected changes (loop driver, arming,
  status chip, hint constant, 23-case + 1-case tests).
- Commits `09b8bce0` and `f4994281` verified in `git log`.
- Test evidence: `TEST-com.warped.data.local.inference.LiteRTLmLoopTest.xml`
  (23/23), agentic 20/20, `TEST-com.warped.data.remote.provider.LmStudioCancelTest.xml`
  (6/6), chat VM suites (incl. new transient test 7/7), settings Tavily 17/17,
  full suite 425/425 zero failures, `assembleDebug` SUCCESSFUL.
- Grep gates: no `runBlocking` in loop path (comments only); no `toolCallActive`
  writes outside UI state; `HOST_EXECUTED` never forwarded by executors.
