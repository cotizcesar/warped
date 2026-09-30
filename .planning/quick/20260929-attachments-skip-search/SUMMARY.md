# Attachments skip heuristic no-URL search — Summary

**Status:** Complete — both tasks executed, `./gradlew :app:assembleDebug` + FULL `./gradlew :app:testDebugUnitTest` green (665 tests, 0 failures).
**Commits:** `d0d433e6` (Task 1), `2fefe111` (Task 2)

## Task 1 — Skip heuristic no-URL search on attachment turns (code fix)

- `ChatViewModel.kt` (`sendMessage`, grounding hook): the no-URL `else` branch is now `else if (images.isEmpty() && audioBytes == null)`. Attachment turns skip the entire `ddgSearchRepository.search` path — `requestUserText` stays the original text, `groundedSources`/`groundedSourceDetails`/`groundedImages` stay empty, `modelOnlyNotice` stays null (no augment, no banner: the skip is deliberate, not a failure).
- Skip-condition choice: raw `images` (`List<Uri>`) / `audioBytes` params — same inputs as the send early-return and media checks — so the skip holds even if a Uri fails to decode. Noted in code comment citing the v2.4 Phase 55 regression.
- Untouched as required: pasted-URL fetch branch (fetches even with attachments); loop arming (`computeArmSnapshot` / `ConversationConfig.tools` inputs, `webOverride` still travels on the request); capability media gate (sits after the hook); provider attach path (`ChatRequest(images = imageDataUrls, audioBytes = audioBytes)` built unconditionally); per-chat/global precedence (`doGround`).

## Task 2 — Tests: skip-matrix + combos + loop interaction + passthrough pin

- New `ChatAttachmentSearchSkipTest.kt` (9 tests, reuses the `ChatAlwaysSearchTest` harness pattern with `context`/`multiUrlFetcher` fields + `webOverride` param):
  - Skip matrix: image-only → search 0; audio-only → search 0; image + question text → search 0 and outgoing text byte-identical (no `SYSTEM_PROMPT`, no fused block); text-only no-URL → search 1 (regression guard).
  - Combos: text + pasted URL + image → `fetchAll` 1 + search 0, sources land on the message; URL-only → `fetchAll` 1.
  - Loop-armed interaction: armed + image + per-chat override → search 0 AND captured `ChatRequest.webOverride == true` (arming input travels; no provider-internal assertions).
  - Passthrough pins: audio bytes captured non-null and content-equal on the request; decoded image data URLs captured (`data:image/png;base64,…`, 1 entry) via mocked `ContentResolver` + static-mocked `android.util.Base64` (JVM stub otherwise).
- Skipped turns render the model reply with `modelOnlyNotice == null` and empty sources (no noise banner).

## Test results

- `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL
- `./gradlew :app:testDebugUnitTest` → BUILD SUCCESSFUL, 665 tests, 0 failures/errors/skipped suite-wide
  - ChatAttachmentSearchSkipTest: 9 tests, 0 failures (incl. the `mockkStatic(Base64)` image-passthrough test)

## Deviations from plan

None — plan executed exactly as written. No stubs introduced. English copy only (code comment + test names); no user-facing strings touched.

## On-device note (honest)

Image-recognition E2E is on-device only — no `adb` available in this environment, so device verification was NOT performed. The unit tests pin the mechanism (no blind-text context injected on attachment turns + attachments still reach the provider request); confirming the small model now answers from the image needs an on-device pass.
