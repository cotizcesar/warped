---
phase: 56-local-agentic-loop
verified: 2026-09-29T00:00:00Z
status: passed
score: 3/3 must-haves verified
overrides_applied: 0
---

# Phase 56: Local Agentic Loop Verification Report

**Phase Goal:** Local models invoke web_search/web_fetch autonomously via LiteRT-LM function calling
**Verified:** 2026-09-29
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Model searches/fetches on its own (multi-turn loop, 5-call cap, Stop cancels) | ✓ VERIFIED | `runToolLoop` in LiteRTLmProvider.kt:375-443 (automaticToolCalling=false:255, per-round ensureActive:388/416, cap→CAP_REACHED_STRING:418, Message.tool reply:443); cap constant MAX_TOOL_CALLS=5 LocalToolLoop.kt:54; 23/23 LiteRTLmLoopTest incl. cap + CE propagation; transient Using-chip (ChatScreen.kt:327,345) + Stop clears (VM:849,1043); device checkpoint APPROVED f39d7173 (real ToolCall emission on Gemma 4 E2B) |
| 2 | Tool outputs pass trust boundary (sanitized, no hijack/breakout) | ✓ VERIFIED | executeToolCall maps only via LocalToolLoop.mapSearchOutcome/mapFetchResult over fused blocks (Provider:455-508); validateArgs short-circuit before any socket (428); per-call offline gate hasValidatedInternet (462/481 → OFFLINE_STRING); key-missing/zero-socket pinned in LoopTest; Content.Text-only extraction (730-733), thought→Done.reasoning only (406/411); KV filter set unconditionally at engine init (LiteRTLmEngine.kt:105) |
| 3 | Only web_search/web_fetch exposed; no leaks | ✓ VERIFIED | Exactly 2 ToolSet classes (WebSearchToolSet.web_search, WebFetchToolSet.web_fetch, single-String params); exact-name dispatch mapToolCallName (LocalToolLoop) + unknown→unknownToolMessage fail-closed; ToolSetSchemaTest 4/4 no-drift pins; allowlist exactly 2 supportsFunctionCalling=true (gemma-4 pair, JSON lines 20/41; 3n pair false); arming = groundingOn AND supportsFunctionCalling AND internet (isLoopArmed:96-100), shared by provider + VM (VM:519) |

**Score:** 3/3 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `data/agentic/WebSearchToolSet.kt` | web_search @Tool schema | ✓ VERIFIED | Exists, substantive, wired (fresh instances in provider tooled config) |
| `data/agentic/WebFetchToolSet.kt` | web_fetch @Tool schema | ✓ VERIFIED | Exists, substantive, wired |
| `data/agentic/LocalToolLoop.kt` | Pure policy: cap, dispatch, mapping, arming | ✓ VERIFIED | Exists, substantive, wired (provider + VM share isLoopArmed/statusDisplay) |
| `data/local/inference/LiteRTLmProvider.kt` | Manual loop driver + executors | ✓ VERIFIED | runToolLoop + executeToolCall + ConversationTurnTransport live |
| `data/local/inference/LiteRTLmEngine.kt` | KV-cache hygiene | ✓ VERIFIED | filterChannelContentFromKvCache=true at init:105 |
| `model_allowlist.json` | gemma-4 flags | ✓ VERIFIED | Exactly 2 true; evidence note in meta |
| `ui/chat/ChatViewModel.kt` + `ChatUiState.kt` + `ChatScreen.kt` | Transient ToolStatus rows | ✓ VERIFIED | toolCallActive lifecycle complete incl. WR-01 fix (VM:865) |
| `LiteRTLmLoopTest.kt` (23) + `LocalToolLoopTest.kt` (16) + `ToolSetSchemaTest.kt` (4) | Loop invariants | ✓ VERIFIED | All green |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| Provider loop | TavilySearchRepository / MultiUrlFetcher | executeToolCall | WIRED | search/fetch executors with offline gate + failure mapping |
| Provider | VM UI state | StreamToken.ToolStatus/ToolCompleted → toolCallActive → chip | WIRED | Emits on start + null in finally; IN-02 fixed (no flash on validation short-circuit) |
| Loop arming | Allowlist + grounding + internet | isLoopArmed shared predicate | WIRED | Provider snapshot + VM Tavily-skip use same predicate; webOverride on ChatRequest |
| Thought channel | Done.reasoning → Thinking panel | extractThoughtContent / Done(reasoning) | WIRED | Text-only Deltas; thinking never interleaved |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|-------------------|--------|
| runToolLoop | toolCalls from terminal Message | sendMessageAsync collect-to-terminal | ✓ FLOWING | Real model ToolCalls confirmed on device (E2B) |
| executeToolCall | fused-block strings | TavilySearchRepository.search / fetchAll | ✓ FLOWING | Mapped via LocalToolLoop, never raw JSON |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Focused suites green | `:app:testDebugUnitTest --tests agentic/loop/allowlist` | BUILD SUCCESSFUL; 16+4+23+12 green | ✓ PASS |
| Full suite 425 green | `:app:testDebugUnitTest` | tests=425 skipped=0 failures=0 errors=0 | ✓ PASS |
| No runBlocking on agentic path | grep runBlocking in agentic + provider | Comments/docs only, no call sites | ✓ PASS |
| Exactly 2 function-calling flags | grep count supportsFunctionCalling:true | 2 | ✓ PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| AGENT-01 | 56-02 | Local model invokes tools autonomously (loop, cap, Stop, thinking routing) | ✓ SATISFIED | runToolLoop + 23 loop tests + device APPROVED |
| AGENT-02 | 56-01/56-02 | Tool outputs sanitized through trust boundary | ✓ SATISFIED | Fused-block-only mapping + offline/key gates + Text-only Deltas |
| AGENT-04 | 56-01 | Only web tools exposed | ✓ SATISFIED | 2 schemas + exact-name dispatch + allowlist gating |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | None | — | No TODO/FIXME/placeholder/empty-return/console-only in phase files; HOST_EXECUTED is an intentional never-invoked schema marker (manual mode), consumed-never-forwarded |

### Human Verification Required

None — device checkpoint already performed and recorded as APPROVED (56-02-SUMMARY.md:295-297, commit f39d7173): real ToolCall emission on Gemma 4 E2B with Using rows, cited answer + Fuentes, thinking confined to panel. Review findings WR-01 + IN-02 fixed and committed (be55faa8, e1d81463, review marked fixed c95e25fa).

### Gaps Summary

No gaps. All three roadmap success criteria hold in code, 425/425 unit tests green, review clean, device checkpoint approved.

---
_Verified: 2026-09-29_
_Verifier: the agent (gsd-verifier)_
