# SECURITY.md — Phase 56 Local Agentic Loop

**Phase:** 56 — local-agentic-loop (plans 01 + 02)
**Date:** 2026-09-29
**Verdict:** SECURED — 13/13 closed, 0 open
**ASVS Level:** 1 (no `asvs_level` in config; conservative default)
**Method:** grep-match proof per threat. Starting hypothesis was OPEN; every
mitigation below cites file:line evidence in implemented code. No documentation
or intent accepted as evidence. Implementation files were not modified.

## Threat Verification

### Plan 01 register (tracer backbone)

| Threat ID | Category | Disposition | Evidence (CLOSED) |
|-----------|----------|-------------|-------------------|
| T-56-01 | Tampering (tool-result strings) | mitigate | `data/agentic/LocalToolLoop.kt:148` — `Grounded -> outcome.fused.block` verbatim; `LocalToolLoop.kt:25-27,142-143` — fused-block-only doc contract; never raw JSON. Pinned by `LocalToolLoopTest` outcome-mapping cases |
| T-56-02 | Tampering (web_fetch URL arg) | mitigate | `LocalToolLoop.kt:179-195` — `validateArgs` rejects non-http(s) URLs pre-fetch; provider re-validates at `LiteRTLmProvider.kt:457` before any socket |
| T-56-03 | Info disclosure (tool args / keys) | mitigate | Arg-only inputs (query/url), no history serialization into calls; keys never sent to api.tavily.com — `TavilySearchRepository.kt:95` `api.search("Bearer $key")` with key from Keystore (`:81`), zeroed after use (`:83,87`), logs carry status only (`:118-119`) |
| T-56-04 | Denial, wallet (credit burn) | mitigate | `LocalToolLoop.kt:54` `MAX_TOOL_CALLS=5` counting CALLS (`:427` — validation short-circuits included); blank-query short-circuit pre-socket (`LocalToolLoop.kt:179+`, `TavilySearchRepository.kt:76-80`); VM skips Tavily pre-search when armed so worst case stays 5/turn |
| T-56-05 | Denial (R8 strips @Tool reflection) | mitigate | `app/proguard-rules.pro:84-88` — `com.warped.data.agentic.**` keep + keepclassmembers; `ToolSetSchemaTest` fails closed on drift |
| T-56-06 | Elevation (prompt injection via snippet/page) | mitigate | Fused-block pipeline (`LocalToolLoop.kt:148` verbatim passthrough of already-sanitized `fused.block`); tool results re-enter only as `Message.tool(ToolResponse)` (`LiteRTLmProvider.kt:443`) and `Content.Text`-only Delta filter (`:730-740`) keeps `ToolResponse` out of Deltas |
| T-56-SC | Tampering (supply chain) | mitigate | Zero new dependencies this phase — nothing to audit (both SUMMARYs confirm; no new `[libraries]` entries) |

### Plan 02 register (provider loop + transient rows)

| Threat ID | Category | Disposition | Evidence (CLOSED) |
|-----------|----------|-------------|-------------------|
| T-56-07 | Tampering (ToolResponse content) | mitigate | Only `LocalToolLoop`-mapped strings fed as `ToolResponse` (`LiteRTLmProvider.kt:418,430,436`); `extractTextContent` `filterIsInstance<Content.Text>()` kept verbatim (`:730-734`); `LiteRTLmLoopTest` pins ToolResponse exclusion from Deltas |
| T-56-08 | Tampering (thought-channel leak) | mitigate | Layer 1: `LiteRTLmEngine.kt:105` `ExperimentalFlags.filterChannelContentFromKvCache = true`. Layer 2: `channels[thought]` routed exclusively via `extractThoughtContent` (`LiteRTLmProvider.kt:751-758`) to `Done.reasoning` (`:406,411`), accumulated in transport (`:542-544`); never emitted as Delta |
| T-56-09 | Info disclosure (status rows) | mitigate | `ChatViewModel.kt:754-755` — `ToolStatus` → in-memory `toolCallActive` only; cleared on null/`ToolCompleted`/Done/Error/Stop/new-send (`:802,837,849,865,1043`); `toolCallActive` lives in `ChatUiState` (`ChatUiState.kt:76`) and renders as a transient chip (`ChatScreen.kt:327`) — never written to `ChatMessage`/Room/transcript |
| T-56-10 | Elevation (stale tool config) | mitigate | `LoopArmSnapshot` vs `activeLoopArm`: `acquireConversation` reuses only on equality (`LiteRTLmProvider.kt:335`), resets + rebuilds on any arming-input change (`:340-346`); armed config carries `automaticToolCalling=false` + fresh ToolSet instances (`:249-255`); error paths null the snapshot (`:583,688`) |
| T-56-11 | Denial (Stop ignored mid-loop) | mitigate | `coroutineContext.ensureActive()` per round (`:388`) and per call (`:416`); `CancellationException` always rethrows — executors (`:472,492`), agentic retry (`:569-572`), plain retry (`:667-670`); `stopGeneration` reaches `generationJob.cancel()` + `fetcher.cancel()` + `cancelActiveGeneration` (`ChatViewModel.kt:1015-1043`); `coroutineScope` (not supervisor) so cancellation is observed (`:674-678`) |
| T-56-12 | Denial (history replay re-triggers loop) | mitigate | Legacy `Role.TOOL` replays as `Message.model` read-only (`LiteRTLmProvider.kt:205`); `Message.tool` constructed only for the live loop resume (`:443`) |

### Auditor's 9-point cross-check (from assignment)

| # | Claim | Verdict | Evidence |
|---|-------|---------|----------|
| 1 | Exactly 2 tools; unknown names rejected; no file/system/shell | CLOSED | Only `WebSearchToolSet.web_search(query)` (`WebSearchToolSet.kt:42`) and `WebFetchToolSet.web_fetch(url)` (`WebFetchToolSet.kt:41`); `TOOL_WEB_SEARCH`/`TOOL_WEB_FETCH` constants + exact-match dispatch (`LocalToolLoop.kt:44-47,130-132`); unknown → error string, never executed (`:136-138,180,199`, `LiteRTLmProvider.kt:499`) |
| 2 | NEVER `runBlocking` on engine path | CLOSED | Grep finds `runBlocking` only inside comments (`WebSearchToolSet.kt:18`, `WebFetchToolSet.kt:19`, `LiteRTLmProvider.kt:354`); zero call sites in `data/agentic/` or provider loop path |
| 3 | Stop/cancel propagation | CLOSED | See T-56-11 row above |
| 4 | Offline/key gates inside tool execution (no socket, no credit burn) | CLOSED | `validateArgs` → `hasValidatedInternet()` gate before each repo call (`LiteRTLmProvider.kt:457-460,480`); `MissingKey`/`InvalidKey`/`UsageLimit` map to actionable strings (`LocalToolLoop.kt:153+`); `LiteRTLmLoopTest` MockK-verifies zero socket calls on offline/key-missing branches |
| 5 | KV-channel filtering on | CLOSED | `LiteRTLmEngine.kt:105` — set unconditionally after `ensureNativeLoaded()`, backend-independent (deviation documented in 56-01-SUMMARY) |
| 6 | Thinking never interleaved into answers | CLOSED | See T-56-08 row above |
| 7 | Tool outputs sanitized fused blocks only | CLOSED | See T-56-01/T-56-06/T-56-07 rows above |
| 8 | Capability gate (loop only when flag true) | CLOSED | `LocalToolLoop.isLoopArmed` = `groundingOn && supportsFunctionCalling && hasValidatedInternet` (`LocalToolLoop.kt:96-100`); allowlist lookup at `LiteRTLmProvider.kt:302`; unarmed turns byte-identical plain path (`:267-270`); exactly 2 `true` flags in allowlist JSON (`model_allowlist.json:20,41`, 3n `false` at `:62,83`) |
| 9 | Tavily key hygiene (Bearer per-call, no logs) | CLOSED | See T-56-03 row above |

## Unregistered Flags

None. `56-01-SUMMARY.md ## Threat Flags` and `56-02-SUMMARY.md ## Threat Flags`
both report no new attack surface beyond the plan registers; auditor found no
additional trust-boundary crossings (no new endpoints, auth paths, or persisted
state beyond the reviewed `toolCallActive` transient).

## Accepted / Transferred Risks

None declared in either plan's threat model (all dispositions are `mitigate`).
No accepted-risk log entries required.

## Notes

- Device smoke checkpoint APPROVED 2026-09-29 (user hardware run on Gemma 4
  E2B: real `ToolCall` emission observed, thinking confined to panel) — the
  `supportsFunctionCalling` flags STAND per the verified-only rule, no revert.
- `HOST_EXECUTED` stub bodies are schema-only markers by design (manual loop
  with `automaticToolCalling=false` never invokes them); executors never output
  the marker (pinned by test).
- Residual accepted behavior (not threats): engine-error retry replays the
  whole agentic turn (may re-burn ≤5 credits on rare engine death) — documented
  decision in 56-02-SUMMARY, strictly better than stranding the engine dead.
