# Quick Task — Attachments skip heuristic no-URL search

## Goal
Fix the v2.4 Phase 55 regression where the heuristic no-URL pre-search fires on
attachment-carrying turns and injects junk text context about the QUESTION WORDS
while the question is about the ATTACHED image, causing the small model to answer
from the injected text and ignore the image. Skip the heuristic no-URL SEARCH
branch when the turn carries images or audio bytes. (Before v2.4 no search
existed, so recognition worked.)

## Locked cause (verified in code)
- `ChatViewModel.kt` `sendMessage` (~line 442 `if (doGround)`): URL branch
  (`groundedUrls.isNotEmpty()` → `multiUrlFetcher.fetchAll`) vs no-URL branch
  (`else` ~line 529 → `ddgSearchRepository.search`). The no-URL branch reads only
  `userMessage.content` — it never checks `images`/`audioBytes`/`imageDataUrls`.
- Attach path intact: `ChatRequest(messages, images = imageDataUrls,
  audioBytes = audioBytes)` (~lines 807-818) is built unconditionally after the
  grounding hook, and `LiteRTLmProvider` `currentContents` maps them to
  `Content.ImageBytes`/`Content.AudioBytes`. Capability media gate (~lines
  701-728) sits AFTER the grounding hook and is unaffected.
- Fix placement: the skip must sit INSIDE the `if (doGround)` block, at the
  no-URL `else` branch — i.e. skip condition = `doGround && no URLs &&
  hasAttachments`. The per-chat/global precedence (`doGround`) is unchanged.

## Scope
- IN: skip heuristic no-URL search on attachment turns; attachment-passthrough
  pin test; skip-matrix + combo + loop-interaction tests; `assembleDebug` +
  full `testDebugUnitTest` green.
- OUT: engine vision backends, OG/cards, loop mechanics, budgets, download paths.
- UNCHANGED (do not touch): pasted-URL fetch branch (explicit URLs always fetch,
  even with attachments); agentic loop entry/arming (model may still call
  `web_search`/`web_fetch` via tools on attachment turns — its choice with full
  multimodal context; arming snapshot computation untouched); capability media
  gate; provider attach path; per-chat/global precedence.

## Tasks

### Task 1 — Skip heuristic no-URL search on attachment turns (code fix)
File: `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
- In `sendMessage`, in the `if (doGround)` hook, keep the URL branch
  (`groundedUrls.isNotEmpty()` → `fetchAll`) EXACTLY as-is.
- In the no-URL `else` branch (~line 529): if the turn carries attachments —
  `images.isNotEmpty() || audioBytes != null` (equivalently
  `imageDataUrls.isNotEmpty() || audioBytes != null`; pick one and note which) —
  SKIP the `ddgSearchRepository.search` call entirely: leave `requestUserText`
  as the original user text, `groundedSources`/`groundedSourceDetails`/
  `groundedImages` empty, `modelOnlyNotice` null. The SYSTEM_PROMPT augment
  behavior on skipped turns: do NOT augment (no grounding happened — the turn
  is effectively model-only with attachments); do not emit any model-only
  notice for the skip (a notice would be noise on a deliberate non-search).
- What must NOT change: URL branch (fetch runs even with attachments);
  `ChatRequest` construction (`images = imageDataUrls, audioBytes = audioBytes`
  still passed on skipped turns); capability gate; loop arming
  (`computeArmSnapshot` / `ConversationConfig.tools` inputs untouched);
  `loopSourceDetails`/`loopImages` accumulators and Done-union (loop rows still
  merge — normally empty on skipped turns unless the loop ran).
- Add a short comment at the skip citing the regression (v2.4 Phase 55,
  blind text search on image-question turns injects junk context).
- Verify: `./gradlew :app:assembleDebug` green.
- Done: image/audio turns with `doGround=true` and no pasted URLs never call
  `ddgSearchRepository.search`; URL+attachment turns still call `fetchAll`;
  text-only no-URL turns behave exactly as before.

### Task 2 — Tests: skip-matrix + combos + loop interaction + passthrough pin
Files: extend `app/src/test/java/com/warped/ui/chat/ChatAlwaysSearchTest.kt`
(or a new `ChatAttachmentSearchSkipTest.kt` reusing its harness — note the
harness builds the VM with mocked `fetcher`/`ddgSearchRepository`/
`providerRouter`; reuse that pattern, do not rebuild from scratch).
- Skip-matrix (mock `ddgSearchRepository.search`, `sendMessage`, advance
  scheduler, `coVerify(exactly = …)` on `search`):
  1. image-only turn (text blank-but-valid? note `sendMessage` early-returns
     only when text blank AND no images AND no audio — image-only passes) →
     search NOT called.
  2. audio-only turn → search NOT called (pass `audioBytes = byteArrayOf(...)`;
     note the `audioBytes` param, not a Uri list).
  3. image + question text, no URLs → search NOT called.
  4. text-only, no URLs → search called (regression guard for existing behavior).
- Combos: text + pasted URL + image → `multiUrlFetcher.fetchAll` called
  (fetch branch unaffected by skip); URL-only (no attachments) → fetch called
  (existing behavior intact).
- Loop-armed interaction: build VM with `supportsFunctionCalling = true`
  (armed turn per existing harness) + image attachments → heuristic `search`
  NOT called AND provider arming inputs untouched (assert via captured
  `ChatRequest` on `helper.runInference`: `webOverride` still passed; do not
  assert on provider internals).
- Attachment-passthrough pin (the verify-don't-break check): on a skipped
  turn, capture the `ChatRequest` passed to `helper.runInference` and assert
  `images` is non-empty (image data URLs present) / `audioBytes` non-null —
  i.e. attachments still flow into the request when search is skipped.
- Verify: `./gradlew :app:testDebugUnitTest --tests "com.warped.ui.chat.*"`
  green, then FULL `./gradlew :app:testDebugUnitTest` green (no regressions).
- Done: all matrix/combo/loop/passthrough tests pass; full unit suite green.

## Verification (overall)
- `./gradlew :app:assembleDebug` green.
- FULL `./gradlew :app:testDebugUnitTest` green.
- Honest note: image-recognition E2E is on-device only (no adb available here)
  — state this in the summary; do NOT claim device verification.

## Output
- Code change in `ChatViewModel.kt` + tests, committed if possible.
- Summary at `.planning/quick/20260929-attachments-skip-search/SUMMARY.md`
  (follow the shape of `.planning/quick/20260928-remove-sin-web/SUMMARY.md`).
