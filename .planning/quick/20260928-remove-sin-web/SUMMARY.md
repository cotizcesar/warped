---
audit_acknowledged:
  milestone: v2.5
  at: 2026-10-01
  status: unknown
---

# Remove "Sin web" + Always-on SYSTEM_PROMPT — Summary

**Status:** Complete — both tasks executed, grep gate clean, `assembleDebug` + full `testDebugUnitTest` green.
**Commits:** `d6ef104` (Task 1), `20a24d5` (Task 2)

## Task 1 — Remove Sin web chip + skipWebOnce state + skipOnce precedence (D-01)

- `ChatInputBar.kt`: deleted `skipWebOnce`/`onToggleSkipWeb` params (+ TOGGLE-03 comment) and the "Sin web" Button block with its preceding Spacer. Thinking chip untouched.
- `ChatScreen.kt`: deleted the two wiring lines (`skipWebOnce = input.skipWebOnce`, `onToggleSkipWeb = ...`).
- `ChatUiState.kt`: deleted the `skipWebOnce` field + TOGGLE-03 comment; `isFetchingWeb` keeps its WEB-06 comment only.
- `ChatViewModel.kt`: deleted `toggleSkipWebOnce()`; deleted the consume/reset lines in `sendMessage`; hook now calls `shouldGround(perChat, global)` with rewritten comment.
- `GroundingPrecedence.kt`: simplified to `shouldGround(perChat: Boolean?, global: Boolean)` — perChat non-null wins, else global.
- Gate: `grep -rn 'skipWebOnce|toggleSkipWebOnce|SkipWeb|skipOnce|Sin web' app/src/main` → CLEAN (also clean in `app/src/test`).

Per-chat tri-state menu, global toggle, preview sheet, and retry path untouched.

## Task 2 — Always-on SYSTEM_PROMPT via augment() enabled flag (D-02)

- `GroundingPrompt.augment(original, block, groundingEnabled: Boolean = true)`: block non-null → `SYSTEM_PROMPT + block + original` (unchanged); block null + enabled → `SYSTEM_PROMPT + original`; block null + disabled → original untouched.
- `ChatViewModel` hook: passes resolved `doGround` (not a fresh DataStore read) at the Fused call site; added no-URL branch so grounded turns without pasted URLs still send SYSTEM_PROMPT.
- **Auto-fix note [Rule 2 — correctness]:** the outgoing-request gate used `groundedSources.isNotEmpty()`, which would have silently dropped the URL-less SYSTEM_PROMPT (request would fall back to history originals). Changed gate to `doGround && historyMessages.isNotEmpty()`. Ungrounded turns send byte-identical text, so behavior there is unchanged.
- Tests:
  - `GroundingPrecedenceTest`: rewritten to 6-row `(perChat, global)` table; skipOnce-wins test replaced with per-chat-Yes-forces-grounding test.
  - `GroundingPromptTest`: block-order test passes `enabled=true`; null-block test split into disabled→untouched and enabled→`SYSTEM_PROMPT + original` (exact equality).
  - `ChatGroundingToggleTest`: deleted `skipWebOnce skips fetch and resets after send`; added `grounded turn with no urls still sends system-prompt-prefixed text` (captures `ChatRequest` at `runInference`, asserts exact prefixed content, fetch never called) and `disabled turn sends original text untouched`. Other toggle tests unchanged and green.

## Test results

- `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL
- `./gradlew :app:testDebugUnitTest` → BUILD SUCCESSFUL, zero failures/errors suite-wide
  - GroundingPrecedenceTest: 3 tests, 0 failures
  - GroundingPromptTest: 5 tests, 0 failures
  - ChatGroundingToggleTest: 5 tests, 0 failures

## Deviations from plan

1. [Rule 2 — correctness] `requestMessages` gate changed from `groundedSources.isNotEmpty()` to `doGround` (required for D-02 URL-less prompt to actually ship). Filed in `ChatViewModel.kt`, covered by the new ViewModel-level capture tests.

No Room/provider/inference/Spanish-copy changes. No stubs introduced.
