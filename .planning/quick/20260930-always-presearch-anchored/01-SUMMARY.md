---
phase: quick-20260930-always-presearch-anchored
plan: 01
subsystem: grounding-pre-search
tags: [anaphora, pre-search, ddg-primary, chatviewmodel, grounding]
requires: [always-on-pre-search, ddg-primary-search]
provides: [anaphora-anchored-presearch-query]
affects: [chat-grounding, search-quality]
tech-stack:
  added: []
  patterns: [pure-builder-object, vm-query-anchoring, mockk-vm-harness]
key-files:
  created:
    - app/src/main/java/com/warped/data/grounding/AnaphoraAnchor.kt
    - app/src/test/java/com/warped/data/grounding/AnaphoraAnchorTest.kt
    - app/src/test/java/com/warped/ui/chat/ChatAnaphoraAnchorTest.kt
  modified:
    - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
decisions:
  - "Anchor = most-recent prior USER turn, assistant fallback (user topic wins)"
  - "Fail-open over-fire on el/lo/la/le accepted; under-anchoring refused"
  - "Query-text-only change: no gate/call-count/cap/prompt-copy changes"
metrics:
  duration: "~25 min"
  completed: "2026-09-30"
---

# Phase quick-20260930-always-presearch-anchored Plan 01: Summary

Armed-turn follow-ups like "Quien es su hermanastro?" now pre-search the
anchored query "<message> <prior-turn topic>" instead of the raw message, so
keyword DDG gets topical signal instead of pushing the model back onto stale
history sources with recycled citation numbers.

## Tasks Completed

| # | Name | Commit | Files |
|---|------|--------|-------|
| 1 | AnaphoraAnchor builder + pure unit tests | 2bbf2fe8 | AnaphoraAnchor.kt, AnaphoraAnchorTest.kt |
| 2 | Wire anchored query into VM no-URL pre-search hook | dd279bd2 | ChatViewModel.kt |
| 3 | VM anchoring tests + gates/wallet regression + full gate | 049ddfb6 | ChatAnaphoraAnchorTest.kt |

## Key Decisions

- **Anchor selection:** most-recent non-blank prior USER turn wins; last
  assistant text is fallback only. User's own topic words are the freshest
  intent signal.
- **Fail-open over-fire accepted:** `el` collides with the Spanish article
  after diacritic-strip and lo/la/le fire on articles — worst case is a
  harmless topic suffix DDG tolerates. Under-anchoring (a miss) is the failure
  refused. Documented in AnaphoraAnchor KDoc.
- **Query-text-only contract:** no gate, call-count, cap, budget, or
  GroundingPrompt change. Downstream `take(MAX_QUERY_CHARS)` still caps the
  anchored query; Tavily fallback keeps keyed + DDG-empty-only caps.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed wrong test expectation for attached clitics**
- **Found during:** Task 1 verification
- **Issue:** `Dale más detalles` expected to anchor on `le`, but `dale` is a
  single letter-token after split — whole-token matching (per locked spec)
  never fires on it. Test expectation contradicted the spec, not the builder.
- **Fix:** Replaced with `¿Qué le dijo ayer?` (whole-token `le`) in
  AnaphoraAnchorTest ES spot checks. Builder untouched.
- **Files modified:** app/src/test/java/com/warped/data/grounding/AnaphoraAnchorTest.kt
- **Commit:** 2bbf2fe8 (amended before commit — fixed pre-commit)

## Test Results

- `AnaphoraAnchorTest` + `NeedsWebTest` + `CodeIntentTest`: green (16 + 91
  total in scope, 1 pre-commit expectation fix above)
- All 5 existing gate suites green with anchored query wired:
  ChatAlwaysSearchTest, ChatNeedsWebGateTest, ChatCodeIntentGateTest,
  ChatAttachmentSearchSkipTest, ChatLoopSourcesTest
- New `ChatAnaphoraAnchorTest` (10 tests): green — anchored follow-up
  (`"Quien es su hermanastro? Háblame de la familia real"` asserted via
  coVerify), raw-passthrough matrix, social/code/URL/offline gates,
  exactly-N invocations, no-Tavily-client reflection gate
- **Full suite: 885 tests, 0 skipped, 0 failures, 0 errors**
- **assembleDebug: success**
- Grep gates: `loopArmed` 0 refs in ChatViewModel.kt; `TavilySearchRepository`
  only pre-existing import + `DEFAULT_MAX_RESULTS` constant; GroundingPrompt
  diff empty

## Known Stubs

None.

## Self-Check: PASSED

- AnaphoraAnchor.kt, AnaphoraAnchorTest.kt, ChatAnaphoraAnchorTest.kt exist;
  ChatViewModel.kt hook contains `AnaphoraAnchor.buildQuery`.
- Commits 2bbf2fe8, dd279bd2, 049ddfb6 verified in `git log`.
