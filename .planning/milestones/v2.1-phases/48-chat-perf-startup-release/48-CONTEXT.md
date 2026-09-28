# Phase 48: Chat Perf + Startup + Release - Context

**Gathered:** 2026-09-28
**Status:** Ready for planning

<domain>
## Phase Boundary

Chat stays smooth at scale, cold start under 1 second, release build hardened end-to-end. ATOMIC: PERF-14 + PERF-15 never split (same files).
Requirements: PERF-14, PERF-15, PERF-16, HARD-01. Depends on Phase 47 (tool-turn UI states exist before the split accommodates them).
Deferred verifications landing here: Phase 45 device smoke, Phase 46 stop/rotation, Phase 47 airplane-calculator + LM Studio loop.
</domain>

<decisions>
## Implementation Decisions

### Chat Perf (user accepted)
- ChatUiState split into single-owner sub-states (messages/streaming vs input vs connection/models, @Immutable); streaming tokens never recompose input bar, keystrokes never recompose list
- Chat list on LazyColumn items(messages, key = { id }) with stable ChatMessage.id; streaming message as keyed trailing item (incl. Phase 47 tool-status/transcript rows as keyed items)
- Auto-scroll sticks to bottom only when already at bottom + "Jump to latest" affordance; no scroll jumps on 100+ message + code blocks + streaming

### Startup (user accepted)
- Baseline Profiles (Macrobenchmark rules + profileinstaller) in release; no eager engine/helper init on startup path (lazy ProviderRouter verified); splash cross-fade ≤200ms
- Measure cold start on available hardware, record in BENCHMARKS.md next to PERF-12 targets (Pixel 7 reference hardware not available — emulator measurement + note)

### Release (user accepted)
- assembleRelease installs on real device; tool skills work in release; logcat shows no secrets/PII/raw tool arguments (tool args redacted)
- R8 full mode re-verified with 0.17.x + ToolSet/skill/mapper classes; dependency audit extended to new transitives; network_security_config + Keystore re-audited

### the agent's Discretion
- Exact sub-state boundaries and LazyColumn key strategy details; benchmark approach on emulator
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- ChatScreen (Column + verticalScroll today — the PERF-15 target), MessageBubble (Thinking/tool transcript panels), ChatInputBar + SkillChipsRow, ChatUiState/ChatViewModel (shareIn per-turn, toolCallActive, streamingContent/Reasoning)
- Splash via core-splashscreen (PERF-11 done); ProviderRouter (lazy per PERF-07); R8 rules incl. ToolSet keeps (45-02)
- BENCHMARKS.md (PERF-12 targets); audit-dependencies.sh (beta/RC-aware since 45 fix)

### Established Patterns
- @Immutable sub-states; keyed LazyColumn; Baseline Profiles via Macrobenchmark; release smoke on device
</code_with>

<specifics>
## Specific Ideas

None — standard approaches per existing patterns
</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope
</deferred>
