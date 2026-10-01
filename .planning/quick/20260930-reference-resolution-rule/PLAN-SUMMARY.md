---
phase: quick-reference-resolution-rule
plan: 01
subsystem: local-grounding-prompts
tags: [prompt-engineering, reference-resolution, citation-hygiene, litertlm, grounding]
dependency_graph:
  requires: []
  provides: [reference-resolution-system-prompts]
  affects: [grounded-answers, agentic-loop]
tech_stack:
  added: []
  patterns: [prompt-only-fix, verbatim-test-pins]
key_files:
  created: []
  modified:
    - app/src/main/java/com/warped/data/grounding/GroundingPrompt.kt
    - app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt
    - app/src/test/java/com/warped/data/grounding/GroundingPromptTest.kt
    - app/src/test/java/com/warped/data/local/inference/LiteRTLmLoopTest.kt

decisions:

  - "Fixed plan's 'leave untouched' instruction for the re-search test (Rule 1): its first assertion pinned the deleted sentence and would have failed; swapped it to the resolve clause, kept the re-search substring assertion"

metrics:
  duration: "~25 min"
  completed: 2026-09-30
audit_acknowledged:
  milestone: v2.5
  at: 2026-10-01
  status: unknown
---

# Quick 01: Reference-Resolution Rule Summary

**One-liner:** Prompt-only swap in both grounding constants — resolve pronouns/references against history, cite only current-turn sources.

## Objective

On-device evidence: follow-up "Y quien es su hermanastro?" answered with a generic
dictionary definition citing recycled [2,5]. Root causes: (1) the
per-turn-independence rule never told the model to RESOLVE pronouns against history,
so "su" never became Hornet; (2) nothing forbade reusing prior turns' citation
numbers, so stale [2,5] got grafted onto new claims.

## Changes

**Task 1 — constant swap (replace, not append, rest byte-identical):**

- `GroundingPrompt.SYSTEM_PROMPT`: deleted "Treat each new question on its own: never
  answer from earlier sources alone when it needs facts the current sources do not
  cover." → inserted "Resolve pronouns and references (he/she/it/this/that,
  él/ella/su/eso/este, and names) against the conversation history first, and use
  the resolved names when searching and answering. Cite only sources fetched for
  the current answer; never reuse citation numbers from earlier turns." Same
  position (after the [1]/[2] chunk, before the "call web_search" chunk).
  The re-search trigger survives via "Answer with the provided sources; call
  web_search if you need more."
- `LiteRTLmProvider.TOOL_USE_SYSTEM_HINT`: deleted "Treat each new user message on
  its own: if it needs facts not covered by earlier tool results, call web_search
  again instead of answering from stale results." → inserted the resolve clause
  with "include the resolved names in web_search queries" plus the preserved
  "call web_search again instead of answering from stale results" substring plus
  the no-reuse sentence. Same position (after "Answer with the gathered
  context.", before the code-from-knowledge clause). KDoc above the hint updated
  to describe reference-resolution + citation-hygiene.

**Task 2 — test pins + new clause tests:**

- `GroundingPromptTest`: renamed `per-turn re-search rule` →
  `reference-resolution rule`, asserts the resolve + cite-only-current + no-reuse
  substrings. Added `system prompt resolves references and forbids citation reuse`.
- `LiteRTLmLoopTest`: verbatim `isEqualTo` pin updated to the exact new hint text
  (copy, not paraphrase); armed-composition assertion swapped to the resolve
  clause. Added `system hint resolves references into search queries`.
- Untouched: `WEB_SEARCH_TOOL_DESCRIPTION` / `ToolSetSchemaTest` (different
  constant, out of scope), code-from-knowledge / greetings / IDENTITY_LINE,
  loop/search code, history mechanics, UI.

## Verification

- `./gradlew :app:assembleDebug :app:testDebugUnitTest` — BUILD SUCCESSFUL.
- `GroundingPromptTest`: 32/32 pass. `LiteRTLmLoopTest`: 38/38 pass.
- Full `:app:testDebugUnitTest` suite: zero failures/errors across all classes.
- `grep "Treat each new" app/src/` — zero occurrences.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Plan's "LEAVE UNTOUCHED" instruction for `system hint carries the per-turn re-search rule` was wrong**

- **Found during:** Task 2
- **Issue:** The plan claimed that test "still passes" because the re-search
  substring is preserved — but its FIRST assertion pinned "Treat each new user
  message on its own", the exact sentence Task 1 deleted. Leaving it untouched
  would fail the suite and violate the plan's own done criteria ('No test
  references "Treat each new"').
- **Fix:** Swapped the first assertion to "Resolve pronouns and references";
  kept the re-search substring assertion (the test's actual purpose) verbatim.
- **Files modified:** `LiteRTLmLoopTest.kt`
- **Commit:** (this commit)

Or otherwise: none — plan executed as written.

## Honest Notes

- 2B-model compliance with the resolve/no-reuse rule is probabilistic (prompt-level
  instruction, no enforcement mechanism).
- Multi-turn anaphora ("su" → Hornet) needs on-device confirmation — no adb in
  this environment. **Human follow-up:** on-device check of the "hermanastro"
  follow-up flow.

## Self-Check: PASSED

- All 4 modified files exist; both constants verified by grep (1 occurrence each
  of the no-reuse sentence per file, 0 of "Treat each new").
- Full unit suite + assembleDebug green (verified from test-result XMLs).
