---
phase: quick
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt
  - app/src/test/java/com/warped/data/local/inference/LiteRTLmHistoryImagesTest.kt
autonomous: true
requirements: [QUICK-IMAGE-HISTORY-CARRY]
user_setup: []

must_haves:
  truths:
    - "A follow-up question in a conversation that contained images gets an answer grounded in the earlier images (no 'provide the image' when the image is in history)"
    - "Text-only history behavior is unchanged (no images carried, same Message shapes as before)"
    - "A turn with an undecodable history image still completes (failure skipped silently, never crashes the turn)"
  artifacts:
    - path: "app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt"
      provides: "History builder carries recent history images as Content.ImageBytes"
      contains: "HISTORY_IMAGE_CARRY_MAX"
    - path: "app/src/test/java/com/warped/data/local/inference/LiteRTLmHistoryImagesTest.kt"
      provides: "History-mapping tests: carry, K cap, decode-failure skip, dedupe, text-only unchanged"
  key_links:
    - from: "app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt"
      to: "ChatMessage.imageUris"
      via: "history builder reads imageUris from sanitized history messages"
      pattern: "imageUris"
    - from: "app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt"
      to: "decodeImage"
      via: "carried history images reuse decodeImage"
      pattern: "decodeImage"
---

<objective>
Fix the image-history regression: follow-up turns are blind to earlier images because
history forwarded to the engine is text-only (LiteRTLmProvider.kt lines 214-225:
`Role.USER → Message.user(msg.content)`, imageUris dropped). Carry the last K user
messages' images into history explicitly so fresh-create AND reused-conversation turns
behave uniformly.

Purpose: Restore multi-turn image retention on-device (on-device evidence: follow-up
"describe the image" → model asks to "provide the image").
Output: Patched history builder + history-mapping unit tests, full suite green.
</objective>

<execution_context>
@/home/cotizcesar/Documents/warped/.opencode/gsd-core/workflows/execute-plan.md
@/home/cotizcesar/Documents/warped/.opencode/gsd-core/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt
@app/src/main/java/com/warped/domain/model/ChatRequest.kt
@app/src/main/java/com/warped/domain/model/ChatMessage.kt
@app/src/test/java/com/warped/data/local/inference/LiteRTLmLoopTest.kt
</context>

<tasks>

<task type="auto">
  <name>Task 1: Carry recent history images in LiteRTLmProvider history builder</name>
  <files>app/src/main/java/com/warped/data/local/inference/LiteRTLmProvider.kt</files>
  <action>At the history-build site (currently lines 214-225, comment says "text-only, no images"):
  add a companion/internal constant HISTORY_IMAGE_CARRY_MAX = 3, then build history
  from sanitizedMessages.dropLast(1) (the existing dropLast(1) already excludes the
  current message — keep that exclusion so the carried set NEVER includes the current
  message and current-turn attachments in Step 4 are never double-sent). For each
  history message with Role.USER and non-empty imageUris: take the K most recent such
  messages (newest-first; K = HISTORY_IMAGE_CARRY_MAX), dedupe identical data-URL
  strings within the carried set (same image attached twice counts once), decode each
  via the existing private decodeImage (reuse as-is, do not duplicate base64 logic),
  skip failures silently (decodeImage already returns null + Timber.w — treat null as
  skip, never throw, never emit an Error token for a bad history image), and build
  that history turn as Message.user(Contents.of(imageContents + Content.Text(text)))
  — Message.user(Contents) overload verified present in litertlm-android-0.17.1
  (javap: Message$Companion.user(Contents)). History USER turns with no images keep
  the exact current shape Message.user(msg.content); SYSTEM/ASSISTANT/TOOL mapping
  (lines 218/223, Phase 49 DEL-01) is untouched. Update the Step 3 comment to describe
  the carry (K, newest-first, dedupe, skip-on-failure). K=3 reasoning (document in a
  brief code comment): each carried image is a full ImageBytes payload in native
  context — unbounded carry risks context-window eviction of the actual conversation
  text and OOM on large photos; 3 covers the realistic multi-image-turn case (user
  attaches 1-2 images then follows up) while bounding the worst case; newest-first
  because recency predicts relevance for follow-ups. BOTH send paths
  (sendAgenticWithRetry and sendContentsWithRetry) consume the same conversationConfig
  built at this site, so the single-site fix applies to fresh-create AND
  reused-conversation (acquireConversation) turns uniformly — do NOT add path-specific
  logic. Current-turn attachment block (Step 4, lines 232-251) is untouched. SCOPE
  LOCK: local LiteRT-LM path only. Remote evidence (verified 2026-09-29): OpenAI /
  Anthropic / Ollama map request.messages to text-only provider messages and only
  ever send request.images (current-turn); LMStudio prepends request.images
  (current-turn) to text history — NO remote path forwards history images today, so
  no remote plumbing in this fix. Do not touch any file under data/remote/.</action>
  <verify><automated>./gradlew :app:assembleDebug 2>&1 | tail -5</automated></verify>
  <done>History builder carries ≤3 newest history images as Content.ImageBytes via Message.user(Contents); text-only turns byte-identical; current message excluded; build passes</done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: History-mapping unit tests + full suite green</name>
  <files>app/src/test/java/com/warped/data/local/inference/LiteRTLmHistoryImagesTest.kt</files>
  <behavior>
    - Test 1 (images carried): history USER message with imageUris → built history Message contains ImageBytes content plus the text part
    - Test 2 (K cap): 5 history USER messages each with 1 image → only the 3 newest carried (HISTORY_IMAGE_CARRY_MAX respected, oldest dropped)
    - Test 3 (decode-failure skip): history image with malformed base64 → turn still builds, image skipped, text preserved, no exception
    - Test 4 (dedupe): same data URL on two history turns → decoded/carried once
    - Test 5 (text-only unchanged): history with no imageUris → Message.user(String) shape identical to pre-fix behavior
    - Test 6 (no double-send): current message images (request.images) are NOT part of the carried history set
  </behavior>
  <action>Follow the existing LiteRTLmLoopTest.kt patterns for provider construction
  (constructor deps, MockK/relaxed mocks as needed) — read that file first and mirror
  its provider() helper style. NOTE: chatInternal/acquireConversation exercise the
  native engine; if the history builder is not directly reachable in a JVM unit test
  (private inside chatInternal, needs EngineManager/conversation), extract the
  history-mapping into an internal pure function (e.g. internal fun
  buildHistoryMessages(sanitized: List[ChatMessage]): List[Message]) in Task 1 style
  and unit-test THAT — small, behavior-preserving extract, same mapping logic moved
  verbatim plus the carry. Tests assert on Message.contents contents list
  (filterIsInstance Content.ImageBytes / Content.Text), not on string output. After
  tests pass, run the FULL unit suite green: ./gradlew :app:testDebugUnitTest.</action>
  <verify><automated>./gradlew :app:testDebugUnitTest 2>&1 | tail -5</automated></verify>
  <done>All 6 behaviors covered and passing; full :app:testDebugUnitTest suite green</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| DB/user-input → engine history | imageUris are base64 data URLs from user picks or persisted MessageEntity.images JSON; decoded bytes go to native engine |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-quick-01 | Tampering | decodeImage on history data URLs | mitigate | Reuse existing decodeImage (try/catch → null, Timber.w); null = skip, never throw, never Error the turn |
| T-quick-02 | Denial of service | unbounded history image payloads evicting context / OOM | mitigate | K=3 cap newest-first + in-set dedupe; current message still excluded via dropLast(1) |
| T-quick-SC | Tampering | npm/pip/cargo installs | accept | No package installs in this fix (Gradle deps only, no new dependencies added) |
</threat_model>

<verification>
./gradlew :app:assembleDebug passes; ./gradlew :app:testDebugUnitTest full suite green.
On-device (honest note — no adb in this environment, needs human confirmation):
multi-turn image retention ("describe the image" follow-up answered from history) and
whether the transient FETCH_FAILED banner recurs. The banner on the evidence turn
suggests the search leg also failed that turn; if it recurs, capture logcat
ModelDownloadWorker / Tavily / DDG lines and file as a separate grounding issue —
NOT part of this fix.
</verification>

<success_criteria>
- Follow-up turns see recent history images on the local LiteRT-LM path (fresh and reused conversations)
- Text-only history behavior unchanged; decode failures never break a turn; no double-send of current-turn images
- Full unit suite green
- Remote history-image forwarding explicitly NOT done — recorded as follow-up below
</success_criteria>

<output>
Create `.planning/quick/20260929-image-history-carry/SUMMARY.md` when done.
Follow-ups (do NOT implement here): (1) remote providers (OpenAI/Anthropic/Ollama/
LMStudio/Custom) forward only current-turn request.images, history is text-only —
same blindness exists there; needs per-provider content-block plumbing as its own
fix. (2) audio history: ChatRequest.audioBytes is current-turn only, same treatment
question — decide symmetric-carry vs follow-up in that fix. (3) on-device/log
confirmation of multi-turn retention + FETCH_FAILED banner recurrence (logcat
ModelDownloadWorker/Tavily/DDG).
</output>
