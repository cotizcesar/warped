---
phase: 47-real-tool-execution
plan: "03"
subsystem: skills-remote-loop
tags: [skills, lm-studio, tool-calling, openai-compatible, sse, transcript, compose-status]
dependency_graph:
  requires:
    - phase: 47-01
      provides: [ToolExecutor-interface, shared-descriptor-mapper, Role-TOOL, tool-copy-deck, ToolCopy-contract]
    - phase: 47-02
      provides: [LocalToolExecutor, StreamToken-ToolStatus, ToolGating-decisions]
  provides: [completions-tool-loop, chatCompletionsWithTools-entry, ToolCompleted-token, remote-gating, role-tool-persistence]
  affects: [phase-48-or-later-confirmation-gate]
tech_stack:
  added: []
  patterns: [parallel-completions-path, index-keyed-sse-accumulator, interceptor-fake-tests, loop-owned-fallback]
key_files:
  created:
    - app/src/main/java/com/warped/data/remote/provider/LmStudioToolLoop.kt
    - app/src/test/java/com/warped/data/remote/LmStudioToolLoopTest.kt
    - app/src/test/java/com/warped/ui/chat/ChatViewModelToolTest.kt
  modified:
    - app/src/main/java/com/warped/data/remote/dto/OpenAiChatRequest.kt
    - app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt
    - app/src/main/java/com/warped/data/remote/provider/LmStudioHelper.kt
    - app/src/main/java/com/warped/data/skills/ToolGating.kt
    - app/src/main/java/com/warped/domain/model/StreamToken.kt
    - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
    - app/src/main/java/com/warped/data/local/benchmark/ModelBenchmarkWorker.kt
    - app/src/main/java/com/warped/data/local/inference/LiteRtLlmHelper.kt
    - app/src/main/java/com/warped/ui/promptlab/PromptLabViewModel.kt
    - app/src/test/java/com/warped/data/remote/provider/LmStudioCancelTest.kt
    - app/src/test/java/com/warped/ui/chat/ChatCancellationTest.kt
decisions:
  - "Transcript rows persist in the 47-01 ToolCopy encoding ('<toolId>\\n<summary>'), not the plan text's 'Used {Display}: ...' literal — the header renders, never stored (avoids lossy Display→id reverse-mapping)"
  - "One new token (StreamToken.ToolCompleted) carries the persistable record + optional error reason; fallback re-POST lives in the loop, not the ViewModel"
  - "Interceptor-fake tests instead of MockWebServer — zero new deps, release audit untouched"
metrics:
  duration: "~120 min"
  completed: "2026-09-28"
---

# Phase 47 Plan 03: Remote Tool Loop Summary

**One-liner:** Parallel raw-OkHttp `POST /v1/chat/completions` tool loop (index-keyed SSE accumulator, local execution, `role:tool` re-POST, `>=` 5-round cap, malformed fallback, per-round cancel) routed by shared gating in `LmStudioHelper`, with `ToolCompleted`-driven status/error/persistence in `ChatViewModel` — 16 new tests, full suite 253/253 green, native `/api/v1/chat` path byte-identical for plain chat, zero new dependencies.

## What Was Built

**Task 1 — DTO extension + `LmStudioToolLoop` + provider entry + routing (`f7a5dc1`):**
- `OpenAiChatRequest.kt`: `OpenAiMessage` gains nullable defaulted `toolCallId` (`tool_call_id`) + `toolCalls` (`tool_calls: List<OpenAiNonStreamingToolCall>`) — existing `OpenAiMessage(role, content)` call sites unaffected.
- `LmStudioToolLoop.kt` (new): `MAX_TOOL_ROUNDS = 5`; `buildCompletionsTools()` from shared `SkillDescriptors` (`tool_choice` omitted = server `auto`); `run(request, tools, onCallCreated)` as `callbackFlow` on `Dispatchers.IO` replicating the 46-01 contract (per-round `newCall` + `onCallCreated`, teardown watcher bridging collector-cancel to the socket, `IOException("Canceled")` silent, `CancellationException` rethrown first, BASIC logging via the shared client).
  - Index-keyed `PendingToolCall` accumulator (id/name/args append by `delta.tool_calls[].index`, sorted materialization); parsed ONCE at terminal `finish_reason == "tool_calls"`; non-streaming `{choices:[{message:{content,tool_calls}}]}` body handled when no SSE framing seen.
  - Blank name or non-JSON-object args → malformed content fallback, never throw; unknown names pass to `ToolExecutor` (allowlist rejects with error-string `role:tool` reply, T-47-10).
  - `ensureActive()` at loop top + after each execute; cap enforced with `>=` (6th tool round never fires); `ToolStatus(name)` progress directly (no text markers); `ToolCompleted(toolId, summary, errorReason)` per execution; assistant echo + `role:tool` (truncated sanitized reply) appended per round.
  - Silent-model fallback: tools ran but zero text → one final POST **without** `tools[]` (never synthesized text), so an error row never sits above an empty bubble.
- `LMStudioProvider.chatCompletionsWithTools(request, tools, executor, onCallCreated)` — additive entry only; native `chat()` SSE parser untouched (`git diff` shows entry + a `Role.TOOL` history branch that replays persisted rows as `Used {Display}: {summary}` plain text — plain chat byte-identical).
- `LmStudioHelper` routing (new injected deps `SkillRepository`, `ModelAllowlistRepository`, `ToolExecutor`): `UseTools` → loop; `NoSupportFallback` → native + merged injection fallback system prompt; `PlainChat` → native. `ToolStatus` + `ToolCompleted` pass through the thinking filter.
- `ToolGating.supportsRemoteTools()`: allowlist OR `LmStudioModelCache.trainedForToolUse` — shared by helper and ViewModel.
- `StreamToken.ToolCompleted(toolId, summary, errorReason?)`; ripple branches added (ignore in benchmark/PromptLab, passthrough in `LiteRtLlmHelper`).

**Task 2 — ViewModel integration (`5a50b04`):**
- `ToolCompleted` collected per turn: failures raise `ActiveToolError(toolId, reason)` (renders `{Display} failed: …` via existing `formatToolError`); all records persist on `Done` as `Role.TOOL` rows in the 47-01 encoding (`toolResultContent`), causal order (tool rows, then assistant), alongside the assistant `saveMessage`; silent turns still persist rows for auditability.
- Remote no-support notice via the same gating truth (exact `NO_TOOL_SUPPORT_NOTICE` string, once per turn, zero-enabled silent).
- `activeToolError` cleared on send/stop/new-conversation; Phase-46 single-shared-Flow / rethrow / Stop wiring untouched.

**Task 3 — Tests (`64572ef`):**
- `LmStudioToolLoopTest` (10, interceptor fake — **no MockWebServer dep**): 3-chunk reassembly + `role:tool`/`tool_call_id` re-POST; parallel index-keying (no arg mixing); cap (6 requests / 5 execs, 6th suppressed); cancel-mid-round (1 request, 1 Call); blank-name + non-object-args fallback; non-streaming DTO path; error-then-silence plain fallback (3rd request carries no `tools[]`); unknown-tool error reply; resumed `role:tool` rows re-sent as tool context.
- `ChatViewModelToolTest` (6): status set/clear sequence; `ToolStatus(null)` hygiene; failure → error row + non-blank fallback + persisted row + `saveMessage` verification; exact notice string once; zero-enabled silence; success row without error.
- Full suite **253/253 green** (237 prior + 16 new); `audit-dependencies.sh` clean; no catalog/build-file changes.

## Verification

- `:app:compileDebugKotlin` clean; targeted suites green (`LmStudioToolLoopTest` 10/10, `ChatViewModelToolTest` 6/6); full `:app:testDebugUnitTest` 253/253, 0 failures.
- `grep -c "chatCompletionsWithTools\|MAX_TOOL_ROUNDS\|ensureActive"` on `LmStudioToolLoop.kt` = 12; cap uses `>=`; every round reports `onCallCreated` (asserted in tests via created-Call counts).
- `git diff` on `LMStudioProvider` native path: additive entry + TOOL-history branch only; SSE parser untouched.
- Copy strings verbatim (`NO_TOOL_SUPPORT_NOTICE`, `{Display} failed:`, `Using {display}…`, `Used {Display}` all live in `ToolCopy.kt` + `Skill.kt`).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Finite `callbackFlow` never completed — collection hung forever**
- **Found during:** Task 3 (first `LmStudioToolLoopTest` run hung; `jstack` showed the test thread parked in `runBlocking join` with no producer alive)
- **Issue:** The loop ended with `awaitClose {}`, which suspends until close/cancel. A finite flow that sends `Done` and then awaits close never terminates, so `collect` never returns. (The native provider gets away with it because production and its tests only ever cancel collection.)
- **Fix:** Explicit `close()` after the `Done` send; `awaitClose` retained afterward purely for the cancellation path (returns at once when already closed).
- **Files modified:** `LmStudioToolLoop.kt`
- **Commit:** 64572ef

**2. [Rule 3 - Blocking] Sealed-interface ripple in the cancellation-test harness**
- **Found during:** Task 3 test compile (`TurnHarness` exhaustive `when`)
- **Issue:** New `ToolCompleted` token broke `ChatCancellationTest` compilation.
- **Fix:** `is StreamToken.ToolCompleted -> Unit` (records carry no text).
- **Files modified:** `ChatCancellationTest.kt`
- **Commit:** 64572ef

**3. [Rule 1 - Test bug] Turbine wall-clock timeout vs `StandardTestDispatcher`**
- **Found during:** Task 3 (`ToolStatus tokens drive the status row…` — "No value produced in 3s" on the first `awaitItem`)
- **Issue:** Turbine's real-time timeout deadlocks collecting a foreign-scope `StateFlow` under the test dispatcher in this harness.
- **Fix:** Rewrote that one test with the deterministic gate + pump + direct-assertion pattern (same behavior asserted: set → clear → persisted answer). No Turbine import remains; plan's Turbine mention was means, not contract.
- **Files modified:** `ChatViewModelToolTest.kt`
- **Commit:** 64572ef

### Plan-File Stale Text (AGENTS.md precedence applied)

- **Persistence encoding:** Task 2's action text says rows store `"Used {Display}: {summary ≤200 chars}"`, but 47-01's SUMMARY locked the decision to `"<toolId>\n<summary>"` (ToolCopy contract — header renders, never stored). Followed 47-01; the visible behavior is identical (`Used {Display}` header + ~200-char summary).
- **Cap-count arithmetic:** plan text asserts "6 total" requests; the silent-model plain fallback legitimately adds a 7th **only** when tools ran with zero text. The cap test uses content-bearing rounds to prove exactly 6-with-tools/5-execs; a dedicated fallback test proves the 7th is tool-free.

### Plan-File Additions (inline necessities, not scope change)

- `ToolGating.supportsRemoteTools()` (new): the OR-gate both helper and ViewModel share — implied by "helper owns routing" + "ViewModel notice" but not named in `<files>`.
- `StreamToken.ToolCompleted` (new): the only channel for failure reasons + persistable summaries from loop to ViewModel — implied by the error-row and persistence contracts.
- Native-path `Role.TOOL` history branch + helper fallback-merge function: required so resumed rows don't leak raw `<toolId>\n…` encoding into model context (Rule 2 correctness).

## Known Stubs

None introduced. One intentional non-stub note: local-path tool *result* rows remain unpersisted (47-02 deferral — automatic-mode results are engine-internal); remote rows persist fully.

## Threat Flags

None — no new surface beyond the plan's threat model. T-47-10…T-47-14 mitigations landed as specified (executor allowlist + schema validation; append-only index-keyed accumulator parsed once; `>=` cap + `ensureActive` + per-round `Call.cancel`; loop-only `/v1/chat/completions` via DTOs; BASIC logging, name-only logs, sanitized short reasons); T-47-SC holds (zero installs — interceptor fake, no MockWebServer).

## Live-Server Probe (open question A1, recorded per plan)

Target LM Studio `/v1/chat/completions` `tool_calls` support is unverifiable in this environment (no server). First run against a real server should: `curl` the endpoint with `tools[]` + `stream:true`, confirm OpenAI-shaped `delta.tool_calls[]` streaming + `finish_reason: "tool_calls"` + `role:tool` acceptance. Native `[tool:NAME]` markers remain as-is regardless of probe outcome.

## Self-Check: PASSED

- All 7 key files verified present on disk.
- All 3 task commits (`f7a5dc1`, `5a50b04`, `64572ef`) verified in `git log`.
- Full unit suite re-verified after final commit: 253/253 green; dependency audit clean.

---
*Phase: 47-real-tool-execution*
*Completed: 2026-09-28*
