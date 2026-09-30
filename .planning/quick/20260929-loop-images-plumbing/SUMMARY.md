# Quick Task Summary: Loop web_search image plumbing

**Status:** COMPLETE — all 3 tasks executed, committed, full suite green.
**Date:** 2026-09-29
**Plan:** `.planning/quick/20260929-loop-images-plumbing/PLAN.md`

## What was built

Image-intent questions answered via the agentic loop now render the image
grid. Two defects fixed:

1. All four loop `web_search` executors pass `includeImages = true`, so the
   keyed Tavily fallback leg sends `include_images=true` (DDG leg ignores
   it; keyless turns byte-identical).
2. `outcome.images` threads through `ToolCallOutcome` → `ToolCompleted` →
   VM `loopImages` accumulator → `ChatMessage.groundedImages` on Done
   (`(groundedImages + loopImages).distinct()`, pre-search keeps precedence).
   `groundedImages` stays ephemeral — no EntityMappers/toEntity/MessageEntity
   change, no migration.

## Commits

- `8b18b9c0` feat(loop-images): includeImages=true at all loop web_search
  sites (LiteRTLmProvider, CompatToolLoop, OpenAIProvider, AnthropicProvider)
- `a11b9fad` feat(loop-images): thread outcome.images to
  ChatMessage.groundedImages (LocalToolLoop.searchImages + ToolCallOutcome /
  ToolCompleted `images` fields + VM accumulator + Done merge)
- `1215028e` test(loop-images): cover includeImages flag + images threading

## Test results

- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL
- `./gradlew :app:testDebugUnitTest` — **656 tests, 0 failures, 0 errors,
  0 skipped** (60 classes)
- New tests (6): `searchImages` verbatim/empty mapping (LocalToolLoopTest),
  flag assert + ToolCompleted images incl. sources-only-empty (LiteRTLmLoopTest),
  compat emission images + shared-helper pin (CompatToolLoopSourcesTest),
  VM union into `groundedImages` first-seen/distinct (ChatLoopSourcesTest)
- Mechanical fix: 19 existing 3-arg `ddg.search(any(), any(), any())` MockK
  stubs/verifies in the two loop test files widened to 4-arg — required,
  since the production call sites now pass `includeImages` explicitly
  (3-arg stubs record `includeImages=false` and would miss `true` calls).

## Deviations from plan

- **OpenAI/Anthropic executor coverage via shared helpers, not dedicated
  harnesses.** No OpenAI/Anthropic loop unit-test harness exists in the repo
  (private `executeRemoteTool`, full provider construction required). Their
  three-line change is byte-identical to the compat site and flows through
  the same `searchImages` helper pinned in `CompatToolLoopSourcesTest`
  ("shared mapping helpers" test, which already documents this contract for
  all three remote drivers) plus grep-verified identical edit sites
  (4× `includeImages = true`, 4× `searchImages(outcome)`,
  4× `images = outcome.images`).
- **No new MessageBubble/GroundedImageGrid compose test.** The repo has no
  compose-rule test infra (component tests are pure-logic). The render
  condition (`!isUser && groundedImages.isNotEmpty()` →
  `GroundedImageGrid`, MessageBubble ~L353) is covered at its input: the VM
  test asserts the assistant message carries non-empty `groundedImages`.

## On-device notes (not verifiable here — no adb in this environment)

- Keyed device, ask e.g. "show me pictures of X" via a loop-armed turn
  (capable local model or remote endpoint, grounding on, internet): expect
  the image grid under the answer.
- Keyless loop turns: no grid (Tavily MissingKey path) — same as pre-search;
  the IMAGES_NEED_KEY notice is pre-search-only and unchanged.
- Extra Tavily payload (`include_images=true`) on every keyed loop search is
  the accepted cost; credit caps already bound it.

## Self-Check: PASSED

- All files from the plan edited and present; grep confirms 4×
  `includeImages = true`, 1 `searchImages` definition + 4 call sites,
  4× `images = outcome.images`, VM `loopImages` accumulator + Done merge.
- All 3 commits exist on the current branch (`git log` verified above).
- No persistence changes: `git show --stat` touches no Entity/DAO/mapper.
