# Image History Carry — Execution Summary

**Status:** complete
**Date:** 2026-09-30
**Plan:** `.planning/quick/20260929-image-history-carry/PLAN.md`
**Scope lock honored:** LOCAL LiteRT-LM path only — no file under `data/remote/` touched.

## What was built

Follow-up turns on the local LiteRT-LM path are no longer blind to earlier images.
The Step 3 history builder in `LiteRTLmProvider` was text-only (`Message.user(msg.content)`,
imageUris dropped); it now carries the last K image-bearing history USER turns as
`Content.ImageBytes` via the `Message.user(Contents)` overload, so fresh-create AND
reused-conversation (`acquireConversation`) turns behave uniformly — both send paths
consume the same `conversationConfig` built at this single site.

- `HISTORY_IMAGE_CARRY_MAX = 3` (companion constant, with K=3 reasoning comment:
  each carried image is a full ImageBytes payload in native context; unbounded carry
  risks context-window eviction of conversation text and OOM on large photos; 3 covers
  the realistic 1–2-images-then-follow-up case; newest-first because recency predicts
  relevance).
- `internal fun buildHistoryMessages(sanitized: List<ChatMessage>): List<Message>` —
  pure function extracted from the Step 3 inline mapping plus the carry (JVM-testable):
  `dropLast(1)` excludes the current message (carried set NEVER includes it, Step 4
  attachments never double-sent); K newest-first selection; in-set dedupe of identical
  data-URL strings (newest occurrence wins); each URL decoded via the existing private
  `decodeImage` (reuse, no duplicated base64 logic), null = skip silently (never throws,
  never Errors the turn); turns with no (carried) images keep the exact pre-fix
  `Message.user(String)` shape; SYSTEM/ASSISTANT/TOOL mapping untouched (Phase 49 DEL-01).
- Step 4 current-turn attachment block untouched. English copy only (code comments).

## Test results

- New `LiteRTLmHistoryImagesTest` — 6/6 passing (Base64.decode static-mocked, echo-bytes):
  1. images carried (ImageBytes + text part), 2. K cap (5 image turns → 3 newest carried,
  oldest two text-only), 3. decode-failure skip (malformed base64 → text preserved, no
  exception), 4. dedupe (shared URL on two turns → carried once, on newest), 5. text-only
  unchanged (single-Text shape), 6. no double-send (current-turn images excluded).
- Full suite: `./gradlew :app:testDebugUnitTest` — **683 tests, 0 failures, 0 errors,
  0 skipped (62 suites)**. `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL.

## Deviations from plan

**[Rule 3 — blocking] MockK overload ambiguity on `Base64.decode(any(), any())`:**
`android.util.Base64.decode` is overloaded, so bare `any()` matchers failed compilation
("Cannot infer type for type parameter 'T'"). Fixed inline by pinning the String overload
(`any<String>()`). Test-only change, no production impact.

## On-device notes (needs human confirmation — no adb in this environment)

- Verify multi-turn retention: attach image, then follow-up "describe the image" → answer
  should be grounded in the earlier image (no "provide the image").
- Watch whether the transient FETCH_FAILED banner recurs on that turn. The evidence turn's
  banner suggests the search leg also failed; if it recurs, capture logcat
  ModelDownloadWorker / Tavily / DDG lines and file as a separate grounding issue — NOT
  part of this fix.

## Follow-ups (explicitly NOT done, per plan)

1. Remote providers (OpenAI/Anthropic/Ollama/LMStudio/Custom) forward only current-turn
   `request.images`; history is text-only — same blindness, needs per-provider
   content-block plumbing as its own fix.
2. Audio history (`ChatRequest.audioBytes` is current-turn only) — symmetric-carry vs
   follow-up decision belongs in that fix.
3. On-device/log confirmation above.

## Commits

- `914ff244` — feat(quick-01): carry recent history images in LiteRT-LM history builder
- `27f22827` — test(quick-01): history image carry mapping tests
