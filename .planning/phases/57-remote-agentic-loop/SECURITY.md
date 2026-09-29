# SECURITY.md — Phase 57 Remote Agentic Loop

**Phase:** 57 — remote-agentic-loop (plans 57-01 + 57-02 + review-fix commits 9a36ab71..55e9cad7)
**Audited:** 2026-09-29
**ASVS Level:** 2
**Verdict:** SECURED — 11/11 threat rows CLOSED (10 unique IDs; T-57-SC appears in both plans)
**Threats Open:** 0

## Method

Adversarial verification: every `mitigate` row required a grep match in the cited
implementation file. Documentation/SUMMARY claims were not accepted as evidence.
Implementation files are READ-ONLY for this audit; no code was modified.

## Threat Verification (57-01)

| Threat ID | Category | Disposition | Evidence — CLOSED |
|-----------|----------|-------------|-------------------|
| T-57-01 | Tampering (tool-result strings) | mitigate | `OpenAIProvider.kt:264` `validateArgs` pre-socket; `:342/:362` `mapSearchOutcome`/`mapFetchResult` verbatim; `parseToolArgs` try/catch degradation via `ToolCallAccumulator.kt` |
| T-57-02 | Information disclosure (endpoint apiKey) | mitigate | `grep apiKey\|Authorization\|Bearer data/agentic/` → zero matches; `OpenAIProvider.kt` loop references only Tavily/fetch singletons; `RemoteSecretIsolationTest.kt:103-130` distinct-fake-keys header exact-match (`Bearer <tavily>`, never endpoint key) |
| T-57-03 | Denial, wallet (Tavily burn) | mitigate | `OpenAIProvider.kt:211-259` call-counted cap via `LocalToolLoop.isCapReached` (`MAX_TOOL_CALLS = 5`, `LocalToolLoop.kt:54`); blank-query short-circuit via `validateArgs` (`:326`); VM skip in `ChatViewModel.kt:542-549` |
| T-57-04 | Denial, cancel (Stop) | mitigate | `OpenAIProvider.kt:116-117` `cancelChat()` → `currentCall?.cancel()`; `ensureActive` per round (`:216`) + per call (`:250`, `:404`); CE-first/Canceled-silent contract |
| T-57-05 | Tampering (transcript poisoning) | mitigate | Round echoes in-memory only (`AnthropicProvider.kt:228` doc; `OpenAIProvider` echo + `role:"tool"` messages never persisted); DEL-01 replay untouched |
| T-57-SC (57-01) | Tampering (supply chain) | mitigate | Zero new dependencies; no package-manager installs in either plan's commits |

## Threat Verification (57-02)

| Threat ID | Category | Disposition | Evidence — CLOSED |
|-----------|----------|-------------|-------------------|
| T-57-06 | Tampering (Anthropic tool_use + compat args) | mitigate | `AnthropicProvider.kt:292` `validateArgs` pre-socket; `:497` tool_use-gated block start; `:551-584` `pendingError` single-emit (WR-03 fix); `:508-528` thinking excluded from echo (WR-05 fix); `CompatToolLoop.kt:145/:215/:229/:247` validate + verbatim mapping; `OpenAiChatRequest.kt` loose schemas, no `strict` on tools, never `tool_choice` |
| T-57-07 | Information disclosure (attempt-path keys) | mitigate | `CompatToolLoop.kt` has zero `apiKey`/`Authorization` code references (single comment at `:202` stating endpoint client is never in scope); `OllamaProvider`/`LMStudioProvider`/`CustomProvider` loop branches reference only injected singletons; distinct-fake-keys test green (6/6) |
| T-57-08 | Denial, wallet (VM + loop stacking) | mitigate | `ChatViewModel.kt:542-548` `remoteArmed = isRemoteLoopArmed(doGround, mode in {ATTEMPT, ATTEMPT_FALLBACK, NATIVE_ANTHROPIC}, online)`; worst case 5 credits/message; exactness justified by CR-01 fix (same singletons at both construction sites) |
| T-57-09 | Denial, cancel (all dialects) | mitigate | `cancelChat()` + retained `Call` on all 5 providers (OpenAI `:116`, Anthropic `:108`, Ollama `:88`, LM Studio `:238`, Custom `:97`); `LMStudioProvider.kt:139/:151` armed + unarmed branches forward `{ currentCall = it; callHook(it) }`; `LmStudioHelper.kt:127` `callHook → activeCall`, `:152-163` `stopResponse()` cancels socket; `ChatViewModel.kt:1053-1064` `stopGeneration()` reaches helper |
| T-57-SC (57-02) | Tampering (supply chain) | mitigate | Zero new dependencies across all 8 review-fix commits |

## Parent-Directed Spot Checks (all CLOSED)

| Check | Evidence |
|-------|----------|
| Dialect correctness (no envelope mixing) | Ollama armed → compat `/v1/chat/completions` (`OllamaProvider.kt:121`), never native `tool_name` (`:97` comment); LM Studio armed → compat `/v1/chat/completions` (`LMStudioProvider.kt:128`), unarmed native `/api/v1/chat` (`:287`) untouched; Custom armed → configured `chatPath` (`CustomProvider.kt:131`); Anthropic native `tools`/`tool_use`/`tool_result`, `stop_reason:"tool_use"` (`AnthropicProvider.kt:691`) |
| Exactly-one retry (no storms) | Per-turn `fallbackDone` flags: `OpenAIProvider.kt:213`, `AnthropicProvider.kt:241`, `CompatToolLoop.kt:91`; gated `attachedTools != null && !fallbackDone`; plain replay sends `tools=null`; notice via `TOOLS_UNSUPPORTED_NOTICE` |
| 5-call cap in ALL remote paths | Call-counted `callsUsed++` incl. validation short-circuits in all 3 drivers (OpenAI `:259`, Anthropic `:287`, Compat `:140`); parallel Anthropic `tool_use` blocks each count; `capFed` drops tools for answer round (OpenAI `:303`, Anthropic `:317`, Compat `:187`) |
| Secret isolation (distinct-keys proof) | `RemoteSecretIsolationTest.kt` 6/6: search `Authorization == Bearer <tavily>` exactly + `doesNotContain(endpointKey)` (`:128-129`); bodies/outputs carry zero key material (`:150-151`); `data/agentic/` grep clean |
| Sanitized remote outputs | All loops delegate to `LocalToolLoop.mapSearchOutcome`/`mapFetchResult` verbatim (Compat `:229/:247`, Anthropic `:356/:376`, OpenAI `:342/:362`); USER content sanitized on every armed branch incl. Custom WR-02 fix (`CustomProvider.kt:115-118`, `:198-200`) |
| Stop propagation | See T-57-09 row above; WR-01 `callHook` fix live |
| Capability matrix soundness | `ToolCapabilityMatrix.kt:55-63` OPENAI→ATTEMPT, CUSTOM→ATTEMPT_FALLBACK, OLLAMA→ATTEMPT, LM_STUDIO→ATTEMPT_FALLBACK, ANTHROPIC→NATIVE_ANTHROPIC, LOCAL/LITE_RT_LM→NO_REMOTE_TOOLS; `attemptsTools` OpenAI-dialect-scoped (`:70-71`); `isToolsRejection` 400 + tool/function substring, null-safe, never throws (`:81-90`); WR-05 `isAnthropicThinkingRejection` covers thinking/signature 400s (`:101-110`); `isRemoteLoopArmed` ANDs all three inputs (`:118-122`); 13/13 matrix tests green |
| CR-01 fix present (production injection) | `ProviderRouter.kt:36-39` nullable collaborator params; all 5 `resolve()` branches forward them (`:56-98`); `LmStudioHelper.kt:59-62` same params, forwarded in `createProvider()` (`:188-189`); live chat path (`runInference` → `createProvider`) therefore arms the loop — dead-code finding resolved in working tree |

## Review-Fix Confirmation (57-REVIEW.md findings)

All 8 fix commits confirmed live: CR-01 `9a36ab71` (injection above), CR-02 `eebd81d7`
(mirror-validity comment `ChatViewModel.kt:530-534`), WR-01 `ba8227b9` (callHook),
WR-02 `efb22cd1` (Custom sanitizer), WR-03 `fb9d6001` (pendingError), WR-04 `c39f791a`
(finish-gated reassembly — `toolFinishSeen` consumed at `CompatToolLoop.kt:361-433`
and `OpenAIProvider.kt:451-535`), WR-05 `ac5de03e` (thinking exclusion + classifier),
IN-01/IN-02 `55e9cad7` (dead vars dropped; `OpenAiMessage.content` NEVER-encoded,
`OpenAiChatRequest.kt:126-132`). Note: `strict: Boolean = true` at
`OpenAiChatRequest.kt:115` belongs to pre-existing `OpenAiJsonSchema`
(response_format), not to tool schemas — tool functions carry no `strict` flag.

## Unregistered Flags

None. Neither `57-01-SUMMARY.md` nor `57-02-SUMMARY.md` contains a
`## Threat Flags` section; no new attack surface was declared during execution.
`RemoteSecretIsolationTest` body/output scans and the clean `data/agentic/` secret
grep corroborate no unmapped surface.

## Accepted Risks Log

(none — every declared threat is mitigated in code; device-only follow-ups
LM-Studio live smoke / matrix confidence / live Stop are functional gaps, not
threat-disposition gaps, and are recorded in 57-VERIFICATION.md)
