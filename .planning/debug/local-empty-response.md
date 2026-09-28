---
status: resolved
trigger: |
  Local model (gemma-4-E2B-it) gives no response: user sends "hola", UI shows
  "Thinking >" with empty area, no assistant bubble ever appears. Screenshot
  from real device 2026-09-28.
symptoms:
  expected: |
    Local model replies to "hola" with a greeting response bubble.
  actual: |
    No response bubble. UI stuck showing "Thinking >" expander with empty
    content area. Input bar idle ("Type a message...", Thinking chip).
  error_messages: |
    None visible — no error bubble, no crash. Silent empty stream.
  timeline: |
    Reported 2026-09-28 on device, after Phase 45 (LiteRT-LM 0.13.1 -> 0.17.1)
    + Phase 46 (shareIn, Call.cancel/cancelProcess, CancellationException
    rethrow, generationSeq) changes. Unknown if worked before on this build.
  reproduction: |
    Load local gemma-4-E2B-it model, send "hola" (or any message). Observe
    empty Thinking row, no answer.
  related_history: |
    .planning/debug/crash-2nd-msg-reasoning.md (resolved, 0.12.0 era): native
    SIGSEGV on 2nd message via stale conversation handle; fixed by always
    recreating conversation per message. Current symptom differs (1st message,
    silent empty, no crash) but same provider file (LiteRTLmProvider.kt).
    Phase 45 RESEARCH noted NPU backend now real + ThinkingConfig/reasoning
    channels changed in 0.14-0.17; LRT-08 tool-calling adoption deferred to
    Phase 47.
created: 2026-09-28
updated: 2026-09-28
---

# Debug Session: local-empty-response

## Current Focus
- hypothesis: parseThinkBlocks fallback routes the entire plain-text local answer into `reasoning` (content="")
- test: trace "hola" turn through ChatViewModel.parseThinkBlocks with reasoningEnabled=true (default) + capabilities.reasoning=true (hardcoded for all local models)
- expecting: no <think>/<channel|> tags in gemma-3n plain reply → fallback fires → assistant bubble saved with empty content + reasoning=answer
- next_action: user fix-choice, then apply fix
- reasoning_checkpoint: null
- tdd_checkpoint: null

## Evidence
- timestamp: 2026-09-28T00:00:00Z
  observation: |
    Screenshot shows persisted "Thinking >" (collapsed expander) + empty content area
    with input bar idle. MessageBubble.kt renders exactly that for a non-user message
    with content="" and non-blank reasoning (collapsed by default after Done).
  implication: answer text WAS received, but classified as reasoning, not content
- timestamp: 2026-09-28T00:00:00Z
  observation: |
    LocalModel.capabilities hardcodes reasoning=true for ALL local models
    (LocalModel.kt lazy block); ChatUiState.reasoningEnabled defaults true.
    parseThinkBlocks fallback (f7ba8c2, June 2026): if no tags found and
    modelMayThink, moves ENTIRE raw output to reasoning, clean="".
  implication: every untagged local reply becomes thinking-with-empty-answer
- timestamp: 2026-09-28T00:00:00Z
  observation: |
    Done path (ChatViewModel.kt:331-361): blank content + non-blank reasoning still
    creates+saves assistant message with content="" — no error emitted, isStreaming=false.
  implication: matches silent symptom precisely (no bubble body, no error, no crash, idle input)

## Eliminated
- 0.17.1 extractTextContent/channel breakage (empty native stream): would leave NO
  message and NO "Thinking >" row (Done else-branch creates nothing, streaming cleared).
  Screenshot's persisted expander contradicts this.
- transport cancel/shareIn regression (Phase 46): turn completed normally (input idle,
  isStreaming=false); cancel path rethrows and would surface differently.

## Resolution
- root_cause: parseThinkBlocks "no tags → everything is reasoning" fallback (f7ba8c2) fired on every untagged local reply because LocalModel hardcodes capabilities.reasoning=true and reasoningEnabled defaults true; answer landed in collapsed Thinking panel, content empty
- fix: removed the fallback in ChatViewModel.parseThinkBlocks — untagged output stays the visible answer; tagged <think>/<channel|> extraction unchanged
- verification: ./gradlew :app:compileDebugKotlin --offline passed; device confirmation pending (Phase 48 smoke)
- files_changed: app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
- follow_up_2026_09_28: user screenshot showed the answer rendered INSIDE the expanded Thinking panel — a row persisted while the bug was live (reasoning=reply, content=""). e99ca5d adds MIGRATION_13_14 (DB v14) repairing those rows: untagged reasoning moves back to content for content-empty assistant rows; genuine tagged reasoning untouched. SQL simulated-verified, compile green, 14.json exported, unit suite 192/192. Upgrade-install runs the repair; user to confirm on device.
