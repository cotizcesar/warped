# Phase 47: Real Tool Execution - Context

**Gathered:** 2026-09-28
**Status:** Ready for planning

<domain>
## Phase Boundary

Enabled skills actually execute — locally via LiteRT-LM `@Tool`s, remotely via the LM Studio `tools[]` loop — with visible progress, graceful errors, validated inputs.
Requirements: SKILLS-07..12, LRT-08, HARD-02. Depends on Phase 46 (cancellable single-flight runInference).
Plan 1 MUST be "locate or rebuild Skills Lite surface" (verified 2026-09-28: NO `*Skill*.kt` in tree — only LmStudioDtos tools[] remnants + StreamChunks/OpenAiChatRequest tool_calls DTOs + comments; full Kotlin-only rebuild required).
</domain>

<decisions>
## Implementation Decisions

### Skills Surface (user accepted)
- SkillChipsRow above chat input; SkillRepository (+Hilt binding) + SkillPreferences DataStore with all-on defaults
- 3 real tools: Calculator, CurrentTime, JsonFormatter. Summarize stays a PromptTemplate skill (persona, not a function)
- Sealed `Skill`, `SkillCategory { Tool, PromptTemplate }`; `skills/` package one file per skill

### Tool Loop + UX (user accepted)
- Single shared Skill→schema mapper feeds `@ToolParam` descriptions (local) and `LmStudioToolFunction.parameters` JSON schema (remote) — no drift
- Local: `@Tool`s via `ToolSet` + `ConversationConfig(tools=…)` from enabled chips, `automaticToolCalling = true`; adopt 0.14–0.17 tool-calling fixes (LRT-08)
- Remote: multi-turn loop over POST /v1/chat/completions (never /api/v1/chat); tools[], finish_reason tool_calls detection in streaming (index-keyed SSE accumulator) + non-streaming; execute locally; re-POST role:tool; cap ~5; malformed-call fallback to content
- Loop runs through Phase 46 runInference with cancellation checks between rounds (25-round cap can never run away — cap here is ~5)
- Progress: "Using calculator…" status row (toolCallActive already exists in ChatScreen); errors: "Calculator failed: …" + plain-text fallback; never hang/empty bubble
- Transcript: minimal rows (tool name + summarized result, role:tool) persisted via Room, resumable
- Gating: per-model allowlist gating + prompt-injection fallback; Qwen3/Gemma template bugs stay on fallback until re-tested on-device (LRT-08); model without tool support → clear message + normal answer

### Trust Boundary (user accepted via HARD-02)
- Every tool argument validated/sanitized before execution (safe expression parser, JSON size caps, timezone-safe formatting)
- Tool bodies pure sync functions, never throw out (error-mapped returns); results treated as untrusted text in next turn
- Standalone unit tests per tool; tool executor behind interface (confirmation gate later without rewiring)

### the agent's Discretion
- Exact ToolSet/ConversationConfig wiring order, SSE accumulator shape, transcript row schema details
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- LmStudioDtos.kt:23 OpenAI-compatible tools[] field; StreamChunks.kt OpenAiStreamToolCall; OpenAiChatRequest.kt OpenAiNonStreamingToolCall (DTO layer exists, executor absent)
- ChatScreen toolCallActive status row ("Using …" pattern exists)
- Phase 46 runInference: shareIn per-turn, Call.cancel/cancelProcess, CancellationException rethrow, ensureActive hooks (tool-round boundaries must call ensureActive)
- ModelAllowlistRepository pattern (supportsFunctionCalling already false-everywhere; per-model gating extends it)
- LiteRTLmEngine.createConversation(config, thinkingConfig, maxOutputToken) — ToolSet attach point (NOT yet wired; 45-02 comment reserves tools/automaticToolCalling for Phase 47)

### Established Patterns
- Hilt modules per feature; Room entities + manual migrations (v14); DataStore preferences; callbackFlow token streaming
- parseThinkBlocks tagged extraction (tool result text flows through same path)

### Integration Points
- ChatViewModel.runInference collection; ChatInputBar (chips row above); MessageBubble (tool status/transcript rows); LMStudioProvider (raw OkHttp Call path from 46-01); LiteRTLmProvider.sendContentsWithRetry
</code_context>

<specifics>
## Specific Ideas

- Calculator must compute, not guess (airplane-mode test); identical visible behavior local vs remote
</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope
</deferred>
