---
phase: 57-remote-agentic-loop
plan: 1
subsystem: remote-agentic-loop
tags: [openai-tools, tool-calls, sse-reassembly, capability-matrix, rejection-classifier, jvm-tests, tracer]
status: complete

# Dependency graph
requires:
  - phase: 56-local-agentic-loop plan 02
    provides: LocalToolLoop pure policy (cap, validateArgs, mapSearchOutcome/mapFetchResult, statusDisplay) consumed verbatim by the remote driver
  - phase: 55-tavily-search
    provides: TavilySearchRepository.search consumed as the web_search executor
  - phase: 52-grounding
    provides: MultiUrlFetcher.fetchAll, GroundingPrecedence.shouldGround, WebPageFetcher.hasValidatedInternet consumed by the loop
provides:
  - ToolCallAccumulator + parseToolArgs (pure index-keyed SSE arguments reassembly, JVM-tested)
  - ToolCapabilityMatrix (static ProviderType matrix, 400-rejection classifier, retry notice, remote-arm predicate)
  - OpenAI Chat Completions tools[] DTOs (loose schemas, additive, byte-identical plain path)
  - Tooled OpenAIProvider.chat() round loop (cap, honest progress, Stop, one-retry fallback)
affects: [57-remote-agentic-loop plan 02 (reuses this core verbatim for Anthropic/Ollama/LM Studio/Custom + VM skip + helper wiring)]

# Tech tracking
tech-stack:
  added: []
  patterns: ["Nullable collaborator injection with null-means-unarmed default (direct-constructed provider, no Hilt)", "EncodeDefault NEVER on additive request fields (unarmed bodies byte-identical on the wire)", "Per-round fresh accumulator + in-memory echoes (never persisted, DEL-01 replay untouched)", "CE-rethrow-first + Canceled-silent cancel contract (LMStudio precedent)"]

key-files:
  created: [app/src/main/java/com/warped/data/agentic/ToolCallAccumulator.kt, app/src/main/java/com/warped/data/agentic/ToolCapabilityMatrix.kt, app/src/test/java/com/warped/data/agentic/ToolCallAccumulatorTest.kt, app/src/test/java/com/warped/data/agentic/ToolCapabilityMatrixTest.kt]
  modified: [app/src/main/java/com/warped/data/remote/dto/OpenAiChatRequest.kt, app/src/main/java/com/warped/data/remote/dto/StreamChunks.kt, app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt]

key-decisions:
  - "Tool executors reach OpenAIProvider as nullable constructor params (default null = unarmed) — the provider is directly constructed in ProviderRouter, not Hilt-managed; 57-02 owns helper/DI wiring"
  - "Retry notice travels on StreamToken.Error (the only visible notice channel) but the turn continues to a plain retry + Done — informational, never a hard error"
  - "Validation short-circuits emit no ToolStatus row (56-02 IN-02 parity: no flash for calls that never execute)"
  - "Single shared Json instance kept (no explicitNulls change) so responses()/completions()/embed() bodies are untouched"

requirements-completed: []
requirements-pending: [AGENT-03]
# AGENT-03 needs plan 57-02 (Anthropic/Ollama/LM Studio/Custom dialects + VM pre-search
# skip + helper wiring). This plan proves the full loop on the native OpenAI dialect.

# Metrics
duration: ~6min
completed: 2026-09-29
---

# Phase 57 Plan 01: Tracer Backbone Summary

**One complete OpenAI-dialect tool round-trip live: pure accumulator + capability matrix + rejection classifier with 31 new JVM tests, additive tools[] DTOs, and the tooled OpenAIProvider loop (5-call cap, per-call status rows, Stop, single retry-with-notice); full suite 457/457 green, secret grep clean**

## Performance

- **Duration:** ~6 min (pure implementation, no device work)
- **Started:** 2026-09-29T03:16:06Z
- **Completed:** 2026-09-29T03:22:20Z
- **Tasks:** 2/2 auto complete
- **Files modified:** 7 (4 created, 3 modified)

## Tasks Completed

### Task 1: Pure shared core — accumulator, matrix, classifier, tools DTOs (c1435d3f)

- `ToolCallAccumulator` (+ `PendingToolCall`, + `parseToolArgs`): index-keyed `StringBuilder`
  reassembly, first-seen id/name win, missing id synthesizes `call_<index>`, `complete()` in
  index order; `parseToolArgs` total (blank → empty map for `validateArgs` short-circuit,
  garbage/non-object → null for `toolFailureMessage` degradation). Zero network imports.
- `ToolCapabilityMatrix` (+ `ToolMode`): OPENAI→ATTEMPT, CUSTOM→ATTEMPT_FALLBACK,
  OLLAMA→ATTEMPT, LM_STUDIO→ATTEMPT_FALLBACK, ANTHROPIC→NATIVE_ANTHROPIC,
  LITE_RT_LM/LOCAL→NO_REMOTE_TOOLS sentinel; `isToolsRejection` (400 + tool/function
  substring, case-insensitive, null-safe, never throws); `TOOLS_UNSUPPORTED_NOTICE` English
  actionable copy; `isRemoteLoopArmed` AND-predicate (plan-02 VM skip mirrors it).
- DTOs additive: `OpenAiChatRequest.tools` (`@EncodeDefault(NEVER)` → unarmed bodies omit the
  key entirely), `OpenAiMessage.tool_calls`/`tool_call_id` (NEVER-encoded), completed-call
  shape, stream `tool_calls` deltas (`index` default 0), non-streaming `message.tool_calls`.
  `defaultRemoteTools()` builds loose schemas (no `strict`, no `tool_choice`) with
  descriptions verbatim from `WEB_SEARCH/WEB_FETCH_TOOL_DESCRIPTION`.
- Tests: `ToolCallAccumulatorTest` (18: whole-object, 3-chunk + mid-`\\u0041`-escape splits,
  id synthesis, name continuation, interleaved indices, parse degradation, DTO wire shape incl.
  tools-omission and `strict`/`tool_choice` absence) + `ToolCapabilityMatrixTest` (13: full
  matrix, classifier table, notice copy, arm truth-table).

### Task 2: OpenAIProvider tools[] round loop (2946ec47)

- Gate: `modeFor(OPENAI)` + `GroundingPrecedence.shouldGround(webOverride, global)` +
  `hasValidatedInternet()`; collaborators arrive as nullable constructor params (default null
  → unarmed → exact pre-57 plain path; only `ProviderRouter.resolve` constructs it today).
- Armed loop mirrors `LiteRTLmProvider.runToolLoop`: `defaultRemoteTools()` built once,
  `MAX_TOOL_CALLS` counted in CALLS, per-round POST via manual OkHttp with retained
  cancellable `currentCall` + `cancelChat()`, `ensureActive` per round and per call,
  CE rethrown first, `IOException("Canceled")` silent.
- SSE feeds `delta.tool_calls` into a fresh per-round accumulator (content/`reasoning_content`
  + `<think>` handling preserved); completeness = `finish_reason:"tool_calls"` (or DONE after
  partials); non-SSE reads `message.tool_calls` directly (Pitfall 3).
- Per call: `validateArgs` pre-socket (short-circuit fed back with no status row, IN-02
  parity) → offline gate → `TavilySearchRepository` / `MultiUrlFetcher` singletons on
  `Dispatchers.IO` → `mapSearchOutcome`/`mapFetchResult` verbatim; `ToolStatus(display)` →
  work → `ToolCompleted(id, ≤200-char summary)` → `ToolStatus(null)` in `finally`.
- Cap: `CAP_REACHED_STRING` fed as the call's result, tools dropped for the answer round,
  second tool-request after cap finishes with gathered context. Echoes (assistant `tool_calls`
  with exact ids + complete arguments strings, then `role:"tool"` results) in-memory only.
- Rejection: HTTP 400 + classifier hit → exactly one retry with tools null + clean
  plain-message replay (partial echoes dropped) + `TOOLS_UNSUPPORTED_NOTICE` on the
  error/notice token path; counter is per-turn state. Never sends `tool_choice`; DEL-01
  TOOL-row replay untouched.

## Verification

- `./gradlew :app:testDebugUnitTest --tests "com.warped.data.agentic.*"` → green
  (ToolCallAccumulatorTest 18/18, ToolCapabilityMatrixTest 13/13, LocalToolLoopTest 16/16,
  ToolSetSchemaTest green).
- Full suite `./gradlew :app:testDebugUnitTest` → **457 tests, 0 failures, 0 errors**.
- Secret grep gate: `grep -rn "apiKey" data/agentic/ OpenAIProvider.kt` matches ONLY the 3
  pre-existing endpoint-scoped interceptor lines (constructor param, null check,
  `Authorization` header) — zero `apiKey` references in loop code; new code references only
  the Tavily/fetch singletons.
- Unarmed turns: request body byte-identical (NEVER-encoded `tools` omitted; verified by the
  `plain request omits tools key entirely` test); response parsing additive under
  `ignoreUnknownKeys`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `JsonArrayBuilder.add(String)` does not exist (kotlinx 1.7.x)**
- **Found during:** Task 1 compile
- **Issue:** `putJsonArray("required") { add("query") }` failed with argument-type mismatch —
  only `add(JsonElement)` exists.
- **Fix:** Wrapped in `JsonPrimitive(...)` in both tool schema builders.
- **Commit:** c1435d3f

**2. [Rule 1 - Bug] Test JSON fixture had one `}` too many**
- **Found during:** Task 1 test run (46/47 green)
- **Issue:** Hand-written stream-delta JSON in `stream delta parses tool_calls fragments
  additively` failed to decode (`Expected ']' but had '}'`).
- **Fix:** Removed the extra brace; 47/47 green.
- **Commit:** c1435d3f

None of the fixes changed plan behavior; no architectural changes needed (no Rule 4 stops).

## Threat Coverage (plan threat model)

- T-57-01 (tampering, tool-result strings): `mapSearchOutcome`/`mapFetchResult` verbatim +
  `validateArgs` pre-socket + reassembled-JSON parse in try/catch with `toolFailureMessage`
  degradation — all in the loop as specified.
- T-57-02 (info disclosure, endpoint key): executors are the Tavily/fetch singletons only;
  secret grep gate clean (see Verification).
- T-57-03 (denial, wallet): call-counted cap (5 max) + blank-query short-circuit pre-socket;
  VM pre-search skip lands in 57-02 via `isRemoteLoopArmed`.
- T-57-04 (denial, cancel): `ensureActive` per round + per call, retained `Call.cancel()`
  via `cancelChat()`, CE rethrow first.
- T-57-05 (tampering, transcript poisoning): echoes in-memory only; persistence untouched
  (DEL-01 replay paths unchanged).
- T-57-SC (supply chain): zero new dependencies, no package-manager installs.

## Known Stubs

None — every path is implemented; no TODOs/placeholders in touched files. Deliberately
deferred to 57-02 (not stubs): Anthropic/Ollama/LM Studio/Custom dialect wiring, VM
pre-search skip, helper `stopResponse` → `cancelChat` wiring, transcript TOOL-row
persistence on `ToolCompleted` (VM currently clears the row only).

## Self-Check: PASSED

- All 5 created/modified source/test files verified present on disk.
- Both task commits verified in git log (`c1435d3f`, `2946ec47`); post-commit deletion check
  clean (no tracked-file deletions).
- `.planning/REQUIREMENTS.md` + `.planning/state.json` show concurrent orchestrator
  modifications — intentionally left unstaged (orchestrator-owned state).
