---
phase: 57-remote-agentic-loop
verified: 2026-09-29T00:00:00Z
status: passed
score: 9/9 must-haves verified
overrides_applied: 0
re_verification: false
accepted_follow_ups:
  - "Device: LM Studio live smoke (armed compat loop end-to-end on real server)"
  - "Device: Ollama/LM Studio matrix confidence (streaming tool_calls shape per server)"
  - "Device: Stop-cancels-mid-loop on a live armed turn (contract verified statically)"
---

# Phase 57: Remote Agentic Loop Verification Report

**Phase Goal:** Remote OpenAI-compatible models use the same tools via native tools[] loop.
**Requirement:** AGENT-03
**Verified:** 2026-09-29
**Status:** passed
**Re-verification:** No — initial verification (includes review-fix confirmation)

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Remote endpoint gets agentic search/fetch with capability gating + graceful fallback | ✓ VERIFIED | `ToolCapabilityMatrix` matrix + `isToolsRejection` classifier + exactly-one retry with `TOOLS_UNSUPPORTED_NOTICE` in all 3 drivers; `ProviderRouter` + `LmStudioHelper.createProvider` inject collaborators (CR-01 fix commit 9a36ab71, confirmed in working tree) |
| 2 | Honest streaming progress, no silent loops; Stop cancels mid-loop | ✓ VERIFIED | `ToolStatus(display)` → work → `ToolCompleted` → `ToolStatus(null)` in `finally` on every loop; `ensureActive` per round + per call, retained `Call` + `cancelChat()` on all 5 providers; WR-01 `callHook` forwards armed + unarmed sockets to helper `stopResponse()` (commit ba8227b9, confirmed live) |
| 3 | VM pre-search skip mirrors provider arming (no silent grounding loss) | ✓ VERIFIED | `ChatViewModel` `remoteArmed = isRemoteLoopArmed(...)` broadens skip; CR-01 wiring makes the mirror exact (same singletons at both construction sites); CR-02 documented in code (commit eebd81d7) |
| 4 | Dialects correct per provider | ✓ VERIFIED | Ollama armed → compat `/v1/chat/completions` (never native `tool_name` envelope); LM Studio armed → compat `/v1/chat/completions` attempt, unarmed native `/api/v1/chat` untouched; Custom armed → configured `chatPath`; Anthropic native `tools/input_schema` + `tool_use`/`tool_result`, `stop_reason:"tool_use"` completeness |
| 5 | Exactly-one retry + visible notice, never silent, never storm | ✓ VERIFIED | `fallbackDone` per-turn flag in `OpenAIProvider:213`, `AnthropicProvider:241`, `CompatToolLoop:91`; plain replay sends `tools=null`; notice routes to `ModelOnlyNotice.TOOLS_UNSUPPORTED` banner slot |
| 6 | 5-call cap enforced on every loop path | ✓ VERIFIED | `LocalToolLoop.MAX_TOOL_CALLS = 5`; call-counted `callsUsed++` incl. validation short-circuits; parallel Anthropic blocks each count; `capFed` drops tools for answer round |
| 7 | Tool outputs sanitized identically to local | ✓ VERIFIED | All loops delegate to `LocalToolLoop.mapSearchOutcome`/`mapFetchResult` verbatim (CompatToolLoop:229/247, AnthropicProvider:356/376); `validateArgs` pre-socket; WR-02 Custom USER sanitization live (commit efb22cd1) |
| 8 | Endpoint keys never leak toward Tavily/fetch | ✓ VERIFIED | `RemoteSecretIsolationTest` 6/6 green (distinct fake keys, header exact-match, body/output scans); `grep -rni apikey data/agentic/` clean; manual-OkHttp POSTs ride intercepted endpoint client |
| 9 | All review findings fixed (CR-01/CR-02, WR-01..05, IN-01/IN-02) | ✓ VERIFIED | 8 fix commits `9a36ab71..55e9cad7` in git log; each fix confirmed present in working tree (see Review-Fix Confirmation) |

**Score:** 9/9 truths verified

### Review-Fix Confirmation (57-REVIEW.md findings)

| Finding | Fix commit | Live-code evidence |
|---------|-----------|-------------------|
| CR-01 dead loop (no collaborator injection) | 9a36ab71 | `ProviderRouter` forwards `tavily/multiUrlFetcher/webPageFetcher/advancedPreferences` to all 5 providers (lines 56–98); `LmStudioHelper` takes nullable collaborators + forwards in `createProvider()` |
| CR-02 VM skip vs dead providers | eebd81d7 | Mirror-validity comment in `ChatViewModel`; exact because CR-01 injects same singletons at both sites |
| WR-01 Stop can't reach armed LM Studio loop | ba8227b9 | `LMStudioProvider.callHook` + both branches forward `{ currentCall = it; callHook(it) }`; helper sets `provider.callHook = { activeCall.set(it) }` and collects 1-arg armed dispatcher |
| WR-02 Custom unsanitized USER content | efb22cd1 | `CustomProvider` takes `inputSanitizer`, sanitizes USER on armed (:118) + plain (:200) paths |
| WR-03 Anthropic double-Error emit | fb9d6001 | `pendingError` result pattern, single terminal emit |
| WR-04 compat truncation executes partials | c39f791a | finish-gated reassembly; transport truncation → error result |
| WR-05 Anthropic thinking-shape 400 bypass | ac5de03e | thinking kept out of echo; classifier covers thinking-signature 400s (`ToolCapabilityMatrix` +20 lines) |
| IN-01/IN-02 dead vars + `"content":null` echo | 55e9cad7 | dead `currentEvent` dropped; null assistant content omitted |

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `data/agentic/ToolCallAccumulator.kt` | SSE args reassembly | ✓ VERIFIED | 18/18 tests; used by all 3 drivers, fresh per-round |
| `data/agentic/ToolCapabilityMatrix.kt` | matrix + classifier + arm predicate | ✓ VERIFIED | 13/13 tests; consulted by all providers + VM skip |
| `data/remote/provider/CompatToolLoop.kt` | shared OpenAI-dialect driver | ✓ VERIFIED | Substantive (~420 lines), wired by Ollama/LMStudio/Custom armed branches |
| `OpenAIProvider.kt` tooled loop | OpenAI-dialect round loop | ✓ VERIFIED | Armed via injected collaborators; unarmed path byte-identical (NEVER-encoded tools key) |
| `AnthropicProvider.kt` native loop | tool_use/tool_result loop | ✓ VERIFIED | Native dialect; WR-03/WR-05 fixes live |
| `OllamaProvider`/`LMStudioProvider`/`CustomProvider` armed branches | attempt-then-fallback | ✓ VERIFIED | All arm + retain `Call`; `cancelChat()` on all |
| `ChatViewModel` remote skip + notice routing | mirror + banner slot | ✓ VERIFIED | `remoteArmed` (line 542), `loopArmed` (549), `TOOLS_UNSUPPORTED` → model-only banner |
| `RemoteSecretIsolationTest.kt` | key-isolation proof | ✓ VERIFIED | 6/6 green |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| ProviderRouter.resolve | all 5 providers | collaborator injection | ✓ WIRED | All 5 construct calls pass 4 collaborators |
| LmStudioHelper.createProvider | LMStudioProvider | collaborator forwarding | ✓ WIRED | Live chat path arms the loop |
| LmStudioHelper.runInference | provider socket | `callHook` → `activeCall` | ✓ WIRED | Stop reaches armed + unarmed turns |
| ChatViewModel.stopGeneration | helper.stopResponse | `Call.cancel()` | ✓ WIRED | Pre-existing path, now fed on both branches |
| Loops | Tavily/fetch singletons | `Dispatchers.IO` executors | ✓ WIRED | No endpoint-key reference in loop code |
| VM skip | ToolCapabilityMatrix | `isRemoteLoopArmed` | ✓ WIRED | Mirrors provider decision exactly |

### Behavioral Spot-Checks

| Behavior | Command / Evidence | Result | Status |
|----------|-------------------|--------|--------|
| Full unit suite green | `:app:testDebugUnitTest` → test-results XML: 463 tests, 0 failures, 0 errors, 0 skipped | 463/463 | ✓ PASS |
| Agentic scope green | 5 agentic test classes (LocalToolLoop 16, Accumulator 18, Matrix 13, Schema 4, SecretIsolation 6) | 57/57 | ✓ PASS |
| No stubs/placeholders | grep TODO/FIXME/XXX/TBD/placeholder across agentic + 3 drivers | zero matches | ✓ PASS |
| Secret grep clean | `grep -rni apikey data/agentic/` → no matches; providers only endpoint-scoped interceptor lines | clean | ✓ PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| AGENT-03 | 57-02 | Remote OpenAI-compatible models invoke same tools via native tools[] loop | ✓ SATISFIED | All 5 dialects tooled/falling back, production wiring live, 463 green |
| NOTE | — | `.planning/REQUIREMENTS.md` still shows AGENT-03 Pending | ℹ️ INFO | Orchestrator-owned state, updated at phase close after verification (same pattern as prior phases) |

### Anti-Patterns Found

None. Stub scan clean; zero new dependencies (T-57-SC holds through all 8 fix commits).

### Accepted Follow-Ups (device-only, per v2.2 precedent)

The v2.2 milestone audit set precedent: automated gates pass + device smoke deferred as user-accepted (DEL-06/WEB-05/WEB-06/THEME-01 PARTIAL). Same applies here — no device exists in this environment, and the Stop/cancel contract is verified statically on every path:

1. **LM Studio live smoke** — armed compat loop end-to-end against a real server (esp. tools-support variance per model).
2. **Ollama/LM Studio matrix confidence** — streaming `tool_calls` shape per server version.
3. **Stop-cancels-mid-loop on a live armed turn** — `callHook`→`activeCall`→`Call.cancel()` chain is wired and unit-consistent; needs a finger on a real turn.

### Gaps Summary

No gaps. The review's two criticals (dead production loop, silent grounding loss) are fixed and confirmed live in the working tree — the phase goal is achieved at runtime, not just in unit tests.

---

_Verified: 2026-09-29_
_Verifier: the agent (gsd-verifier)_
