---
phase: 57-remote-agentic-loop
plan: 2
subsystem: remote-agentic-loop
tags: [anthropic-tools, tool-use, compat-loop, ollama-v1, attempt-fallback, vm-skip, secret-isolation, jvm-tests]
status: complete

# Dependency graph
requires:
  - phase: 57-remote-agentic-loop plan 01
    provides: ToolCallAccumulator + parseToolArgs, ToolCapabilityMatrix (matrix, classifier, notice, arm predicate), OpenAI tools[] DTOs, tooled OpenAIProvider loop consumed verbatim as the semantic template
  - phase: 56-local-agentic-loop plan 02
    provides: LocalToolLoop pure policy (cap, validateArgs, mapSearchOutcome/mapFetchResult, statusDisplay) reused identically by every 57-02 loop
  - phase: 55-tavily-search
    provides: TavilySearchRepository.search consumed as the web_search executor
  - phase: 52-grounding
    provides: MultiUrlFetcher.fetchAll, GroundingPrecedence.shouldGround, WebPageFetcher.hasValidatedInternet consumed by every loop and the VM skip
provides:
  - Anthropic native dialect loop (tools/input_schema + tool_use/tool_result, input_json_delta accumulation, in-memory echoes)
  - CompatToolLoop shared OpenAI-dialect round driver (Ollama /v1, LM Studio /v1 attempt, Custom chatPath attempt)
  - Remote-armed VM pre-search skip (ToolCapabilityMatrix.isRemoteLoopArmed mirrored in ChatViewModel)
  - TOOLS_UNSUPPORTED model-only banner slot (notice copy routed off the error path)
  - RemoteSecretIsolationTest (distinct-fake-keys endpoint-key isolation proof)
affects: [phase-57 verification (all five dialects live), future helper/DI wiring (providers arm via nullable collaborators; ProviderRouter.resolve still constructs unarmed)]

# Tech tracking
tech-stack:
  added: []
  patterns: ["Shared compat driver over per-provider loop triplication (verbatim 57-01 semantics, provider-owned URL/messages/arming)", "JsonElement message content with JsonPrimitive plain path (Anthropic blocks without breaking the string wire shape)", "Exact-copy notice routing (StreamToken.Error matched on TOOLS_UNSUPPORTED_NOTICE constant, genuine errors untouched)"]

key-files:
  created: [app/src/main/java/com/warped/data/remote/provider/CompatToolLoop.kt, app/src/test/java/com/warped/data/agentic/RemoteSecretIsolationTest.kt]
  modified: [app/src/main/java/com/warped/data/remote/dto/AnthropicDtos.kt, app/src/main/java/com/warped/data/remote/provider/AnthropicProvider.kt, app/src/main/java/com/warped/data/remote/provider/OllamaProvider.kt, app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt, app/src/main/java/com/warped/data/remote/provider/CustomProvider.kt, app/src/main/java/com/warped/ui/chat/ChatViewModel.kt, app/src/main/java/com/warped/domain/model/ChatMessage.kt, app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt]

key-decisions:
  - "CompatToolLoop shared driver (new file) instead of triplicating the ~250-line round loop into Ollama/LMStudio/Custom — verbatim 57-01 semantics, provider call sites own only URL, base-message mapping, arming, and Call retention"
  - "AnthropicMessage.content String→JsonElement with JsonPrimitive plain path — tool_use/tool_result blocks without breaking the pre-57 string wire shape (unarmed turns byte-identical)"
  - "Anthropic arming feeds matrixAttemptsTools=true literally (mode checked explicitly for NATIVE_ANTHROPIC first) — attemptsTools() is OpenAI-dialect-scoped by 57-01 design and returns false for the native dialect"
  - "Tools-unsupported notice routes to the ModelOnlyBanner slot (new ephemeral TOOLS_UNSUPPORTED enum, no migration) instead of the error banner — informational retry, never a hard error; also upgrades the 57-01 OpenAI notice path"
  - "Providers keep nullable collaborators defaulting null (57-01 pattern) — loops arm only with collaborators present; production helper/DI wiring stays deferred (ProviderRouter.resolve constructs unarmed)"

requirements-completed: [AGENT-03]
requirements-pending: []

# Metrics
duration: ~35min
completed: 2026-09-29
---

# Phase 57 Plan 02: Dialect Expansion Summary

**All five remote dialects tooled or honestly falling back: native Anthropic loop (tools/tool_use/tool_result, 5-call cap, Stop, one-retry-with-notice), shared compat driver arming Ollama /v1 + LM Studio /v1 + Custom attempt-then-fallback, VM pre-search skipped whenever any remote loop is armed, endpoint keys provably isolated (distinct-fake-keys test), full suite 463/463 green, secret grep clean**

## Performance

- **Duration:** ~35 min (implementation + verification, no device work)
- **Completed:** 2026-09-29
- **Tasks:** 2/2 auto complete
- **Files modified:** 10 (2 created, 8 modified)

## Tasks Completed

### Task 1: Anthropic native dialect loop (cccf93fb)

- `AnthropicDtos`: request gains `NEVER`-encoded `tools[]` (`AnthropicTool{name, description, input_schema}` via `defaultAnthropicTools()` — same single-required-string schemas and verbatim descriptions as the 57-01 OpenAI parameters, no `strict`, never any `tool_choice` equivalent); `AnthropicMessage.content` upgrades `String`→`JsonElement` with `text()` factory (`JsonPrimitive` keeps unarmed turns byte-identical) plus `toolUseEcho`/`toolResults` in-memory echo builders (input coerced to object, never throws); new `AnthropicNonStreamingResponse` for Pitfall-3 parity. Existing SSE DTOs already carried `index`/`partial_json`/`stop_reason`/`content_block` — no changes needed.
- `AnthropicProvider`: armed loop mirrors the 57-01 OpenAI driver over the native dialect — `defaultAnthropicTools()` built once, call-counted 5-call cap, `validateArgs` pre-socket (no status row on short-circuit, IN-02 parity), offline gate, Tavily/fetch singletons on `Dispatchers.IO`, `mapSearchOutcome`/`mapFetchResult` verbatim, `ToolStatus(display)` → work → `ToolCompleted(id, ≤200-char summary)` → `ToolStatus(null)` in `finally`. SSE feeds `content_block_start` ids/names + `input_json_delta`/`partial_json` fragments into a fresh per-round `ToolCallAccumulator` (missing ids tolerated identically); completeness is `stop_reason:"tool_use"`; parallel `tool_use` blocks each count toward the cap; echoes are assistant `tool_use` blocks + `user`/`tool_result` rounds, in-memory only. Rejection: HTTP 400 + classifier hit → exactly one retry with tools null + clean replay + `TOOLS_UNSUPPORTED_NOTICE`. Retained `currentCall` + `cancelChat()`, `ensureActive` per round and per call, CE rethrown first, `IOException("Canceled")` silent. Unarmed turns keep the exact Retrofit plain path; `listModels`/`testConnection` untouched.

### Task 2: Ollama / LM Studio / Custom attempt-then-fallback + VM skip + notice + secret proof (f0dda2af)

- `CompatToolLoop` (new, internal): the 57-01 OpenAI round loop reused verbatim as a shared driver — same cap, validation, executors, mapping, status rows, in-memory pairing (`tool_calls` echo + `role:"tool"`/`tool_call_id` results, Pitfall 2), retained-`Call` cancel contract, non-streaming `tool_calls` handling (Pitfall 3), exactly-one retry without tools plus `TOOLS_UNSUPPORTED_NOTICE`. Provider call sites own only POST URL, `modelId`, base-message mapping, arming, and `Call` retention.
- `OllamaProvider`: armed turns ride OpenAI-compat `/v1/chat/completions` through the driver (RESEARCH route-a — never the native `tool_name` envelope, Pitfall 6); unarmed turns keep the exact native `/api/chat` path. `cancelChat()` added.
- `LMStudioProvider`: armed turns attempt compat `/v1/chat/completions` (model-dependent support — the fallback absorbs a wrong pick); unarmed turns keep the exact native `/api/v1/chat` path (images, integrations, reasoning, stats untouched). `chat()` override now gates; the `(request, integrations, onCallCreated)` overload is unchanged.
- `CustomProvider`: armed turns run the driver against the configured `chatPath`; unarmed turns keep the exact Retrofit path. `cancelChat()` added.
- All three arm provider-authoritatively (matrix `attemptsTools(modeFor(type))` + grounding precedence + validated internet + collaborators present); all constructors keep null-default collaborators so `resolve()` behavior is unchanged.
- `ChatViewModel`: VM Tavily pre-search skip broadens to `localArmed || remoteArmed`, where `remoteArmed = isRemoteLoopArmed(doGround, mode in {ATTEMPT, ATTEMPT_FALLBACK, NATIVE_ANTHROPIC}, online)` — worst case stays 5 credits/message; provider stays authoritative, VM mirrors for the skip only. Mid-turn `StreamToken.Error` carrying exactly `TOOLS_UNSUPPORTED_NOTICE` routes to the model-only banner slot instead of the error banner (genuine errors untouched); transient tool rows keep existing disappearance rules.
- `ModelOnlyNotice.TOOLS_UNSUPPORTED` (ephemeral, no migration) + `MessageBubble` banner branch rendering the notice copy (mirrors the constant; OFFLINE-only retry gate untouched).
- `RemoteSecretIsolationTest` (6 tests): real `ApiKeyStore` holding DISTINCT fake endpoint + Tavily keys — captured search `Authorization` equals `Bearer <tavily>` exactly and never contains the endpoint key; search body serializes with zero key material; keyless fetch entry verified `(url, budget)` with fused outputs clean; both tool-schema shapes serialize with exact `web_search`/`web_fetch` names, zero secrets, no `strict`/`tool_choice`; 56-02 never-throw expectation extended to the exact pure functions every remote executor delegates to.

## Verification

- `./gradlew :app:testDebugUnitTest --tests "com.warped.data.agentic.*"` → green (LocalToolLoop 16/16, Accumulator 18/18, Matrix 13/13, Schema 4/4, **SecretIsolation 6/6** — 57 total).
- Full suite `./gradlew :app:testDebugUnitTest` → **463 tests, 0 failures, 0 errors** (457 baseline + 6 new).
- Secret grep gate: `grep -rni "apikey" data/agentic/` → clean (no matches); `CompatToolLoop.kt` + `AnthropicDtos.kt` → clean; all five providers → only constructor params + endpoint-scoped interceptor header lines (pre-existing pattern, zero loop-code references).
- Unarmed-turn preservation: Anthropic plain path keeps the Retrofit request/response code (only `String`→`JsonPrimitive` at construction); Ollama native, LM Studio native, Custom Retrofit paths are extracted-not-rewritten (compile-verified + full suite green).
- Stop/cancel parity by construction on every loop: `ensureActive` per round + per call, retained `Call` + `cancelChat()` (Anthropic/Ollama/Custom new; LM Studio pre-existing), CE rethrown first, `Canceled`-IOException silent. Device-level Stop verification pending (no device in this environment) — same contract as the 57-01/56-02 device-approved precedent.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing critical] Shared `CompatToolLoop` driver (new file, not in plan `files_modified`)**
- **Found during:** Task 2 design
- **Issue:** Plan asked all three compat dialects to "reuse the 57-01 round semantics" — triplicating the ~250-line round driver per provider would triple the bug surface for the wallet/cancel/retry guarantees.
- **Fix:** One internal shared driver with verbatim 57-01 semantics; providers own only URL/messages/arming/Call retention. OpenAI's private loop left untouched (no refactor risk to green code).
- **Commit:** f0dda2af

**2. [Rule 2 - Missing critical] `ModelOnlyNotice.TOOLS_UNSUPPORTED` + banner branch (`ChatMessage.kt`, `MessageBubble.kt`, not in plan `files_modified`)**
- **Found during:** Task 2 notice rendering
- **Issue:** Plan mandates rendering the retry in "the existing model-only notice/banner slot" — no enum value fit the tools-rejected case, so the slot needed one.
- **Fix:** Ephemeral enum value (no Room migration — field is never persisted) + banner copy mirroring `TOOLS_UNSUPPORTED_NOTICE`; retry button stays OFFLINE-only; also upgrades the 57-01 OpenAI notice off the error banner.
- **Commit:** f0dda2af

None of the fixes changed plan behavior; no architectural changes needed (no Rule 4 stops). Zero new dependencies (T-57-SC).

## Threat Coverage (plan threat model)

- T-57-06 (tampering, Anthropic `tool_use` input + compat args): `validateArgs` pre-socket on every loop, fragment reassembly in try/catch with `toolFailureMessage` degradation, exact-name dispatch via `mapToolCallName`, fused-block-verbatim mapping — all in the Anthropic loop and shared driver.
- T-57-07 (info disclosure, endpoint key on attempt paths): executors are the Tavily/fetch singletons only; `apiKey` grep clean across `data/agentic/`, driver, DTOs; distinct-fake-keys test green (header exact-match + body/output scans).
- T-57-08 (denial, wallet — VM + remote stacking): `isRemoteLoopArmed` VM skip mirrors the provider decision; call-counted 5 cap on every loop; blank-query short-circuit pre-socket.
- T-57-09 (denial, cancel): `ensureActive` per round + per call, retained `Call.cancel()` + `cancelChat()` on Anthropic/Ollama/Custom (LM Studio pre-existing), CE rethrow first on every provider.
- T-57-SC (supply chain): zero new dependencies, no package-manager installs.

## Known Stubs

None — every path is implemented; no TODOs/placeholders in touched files. Deliberately deferred (not stubs, orchestrator-owned follow-ups): production helper/DI wiring so `ProviderRouter.resolve()` constructs armed providers (loops arm today only when collaborators are injected — VM skip already mirrors the armed decision); device-level Stop + matrix-confidence checks per provider (esp. LM Studio tools support, Ollama streaming tool_calls).

## Self-Check: PASSED

- All created/modified source/test files verified present on disk (10 files).
- Both task commits verified in git log (`cccf93fb`, `f0dda2af`); post-commit working tree clean under `app/src` (no deletions, no stray untracked source).
- Stub scan clean across all touched files; secret grep clean; full suite 463/463 green.
