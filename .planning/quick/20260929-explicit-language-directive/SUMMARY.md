---
audit_acknowledged:
  milestone: v2.5
  at: 2026-10-01
  status: unknown
---

# Summary: Explicit Language Directive (EN+ES)

Status: COMPLETE — both tasks executed, full unit suite green.

## What changed

- `GroundingPrompt.kt`: added `isSpanish()` (locked high-precision heuristic:
  precompiled case-insensitive `Regex("[¿¡áéíóúñü]")`, empty → false) and
  `languageDirective()` (`Responde en español.` / `Reply in English.`,
  byte-exact). Removed the generic ` Always reply in the same language the
  user wrote in.` sentence from `SYSTEM_PROMPT` (rest byte-identical).
  `augment()` appends the directive — computed from the ORIGINAL user text,
  never the block — as the last line on both grounded branches; the disabled
  branch returns `original` untouched.
- `LiteRTLmProvider.kt`: removed the identical generic sentence from
  `TOOL_USE_SYSTEM_HINT` (rest byte-identical). No directive added to
  `systemInstruction` — exactly-once rule.
- Tests: `GroundingPromptTest` extended (detection table, selection,
  exactly-once/last-line assembly, original-derives-directive, disabled
  untouched, no-generic-sentence guards; obsolete test now asserts ABSENCE);
  pinned expectations updated in `SettingsTavilyTest`, `ChatGroundingToggleTest`
  (exact equality + `\n\nReply in English.`), `LiteRTLmLoopTest` (verbatim pin
  minus generic sentence + `doesNotContain`). `ChatAlwaysSearchTest`,
  `ChatLoopSourcesTest`, `ChatAttachmentSearchSkipTest` needed no changes
  (prefix/doesNotContain assertions still hold).

## Invariant verification (loop-armed ⇒ grounded ⇒ augmented)

All 7 `augment()` call sites in `ChatViewModel.kt` sit inside `if (doGround)`;
the provider arms only when `GroundingPrecedence.shouldGround(perChat, global)`
holds, and the VM passes the same `perChatOverride` as `webOverride`
(`ChatViewModel.kt:832`) — no armed turn can reach the model unaugmented.
Search queries use pre-augmentation `userMessage.content` (no directive leak
into search); Room history persists the original text (covered by
`ChatLoopSourcesTest.history keeps originals`).

## Test results

- `./gradlew :app:assembleDebug` — SUCCESS, no detekt/ktlint regressions.
- `./gradlew :app:testDebugUnitTest --tests
  "com.warped.data.grounding.GroundingPromptTest"` — green.
- Full `./gradlew :app:testDebugUnitTest` — **677 tests, 0 failures,
  0 errors, 0 skipped**.

## On-device notes (manual, not covered by JVM tests)

No adb in this environment. Still needed on-device: send an ES question
(e.g. `¿Cuál es la capital de Francia?`) with grounding on and confirm the
answer is Spanish; send an EN question and confirm English. Residual drift
on the 2B model is expected — the directive raises compliance but is never
100%.

## Commits

- `51d5c8e1` feat(01-language-directive): explicit last-line language
  directive in augment()
- `b2bd8c5d` test(01-language-directive): detection table, directive
  selection, assembly guards

## Deviations

None — plan executed exactly as written.
