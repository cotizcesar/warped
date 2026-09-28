# Phase 46: Runtime Hardening - Context

**Gathered:** 2026-09-27
**Status:** Ready for planning

<domain>
## Phase Boundary

Streaming is truly cancellable on both backends through one shared inference Flow — Stop means stop.
Requirements: RUNTIME-13 (single shared Flow via shareIn, sentinel removed), RUNTIME-14 (true Call.cancel()/cancelProcess(), tool loop checks cancellation).
Depends on Phase 45 (final engine + OkHttp APIs).
</domain>

<decisions>
## Implementation Decisions

### Shared Inference Flow
- Single shared Flow via shareIn replay=1, per-turn scope; runInference double-collect gone (user accepted)
- Sentinel no-op cancellation job removed
- Rotation mid-stream: no duplicated or dropped tokens (replay covers config-change re-collect)

### Stop Semantics
- Stop button wires true OkHttp Call.cancel() (remote) + cancelProcess() (local), fixing stopResponse() no-op (user accepted)
- Tokens halt immediately; no trailing tokens after Stop
- Stop then immediate follow-up: no hang, wedge, or stale generating spinner

### the agent's Discretion
- Exact shareIn scope holder (ViewModel vs Router) and coroutine scope choice at planner/executor discretion
- Tool-loop cancellation checks land here as hooks only (full loop is Phase 47); 25-round-cap guard noted
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- LlmModelHelper.runInference / stopResponse interface; LiteRtLlmHelper + LmStudioHelper implementations
- ProviderRouter factory; ChatViewModel inference collection
- OkHttp Call references in LMStudioProvider; LiteRT engine cancelProcess entry point (Phase 45 re-verified)

### Established Patterns
- callbackFlow for token streaming; StateFlow UiState to Compose; Dispatchers.IO for inference
- SSE streaming via OkHttp + BufferedSource lines

### Integration Points
- ChatViewModel.runInference collection; Stop button onClick; LMStudioProvider Call handle; LiteRT helper process handle
</code_context>

<specifics>
## Specific Ideas

No specific UI changes — Stop button already exists; behavior fix only.
</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope
</deferred>
