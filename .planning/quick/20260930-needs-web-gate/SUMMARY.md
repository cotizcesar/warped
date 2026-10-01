---
audit_acknowledged:
  milestone: v2.5
  at: 2026-10-01
  status: unknown
---

# Quick-Task SUMMARY — Needs-Web gate + identity/hint prompt lines

**Status:** complete (3/3 tasks)
**Date:** 2026-09-30
**Commits:** 4019bdde (Task 1), e0cdc05e (Task 2), 6824f709 (Task 3)

## What was built

Deterministic social/identity gate for the always-on pre-search plus a local
identity line, fixing the on-device regression where `"hola, quien Eres"`
web-searched SpanishDict and cited a translation answer.

1. **`NeedsWeb` pure helper** (`data/grounding/NeedsWeb.kt`) — mirrors
   `ImageIntent`: NFD-normalize + strip combining marks, lowercase, split on
   `[^\p{L}]+`; whole-token WORDS + token-subsequence PHRASES, never
   substrings. FALSE = social/identity (locked ES+EN lists), TRUE = fail-open
   for factual/short/empty. Zero Android imports.
2. **Gate in `ChatViewModel.doGround` no-URL branch** — first statement before
   `hasValidatedInternet()`: social turns skip the entire pre-search block
   (no augment call at all, no notice, no progress state, no socket, no
   credit). URL branch, attachment-skip, offline/model-only paths, and loop
   arming untouched.
3. **Local identity + hint** (`LiteRTLmProvider`) — new `IDENTITY_LINE`
   (`"You are Warped, a mobile AI assistant running locally."`);
   `TOOL_USE_SYSTEM_HINT` gains `"Do not call web_search/web_fetch for
   greetings, thanks, or questions about yourself."` Armed config =
   `Contents.of("$IDENTITY_LINE $TOOL_USE_SYSTEM_HINT")`; unarmed config (was
   bare) now carries `Contents.of(IDENTITY_LINE)`. PromptLab
   templates / `GroundingPrompt.SYSTEM_PROMPT` untouched (wrong layer).

## Test results

- `NeedsWebTest`: truth table green (social FALSE incl. the exact regression
  message `hola, quien Eres`, diacritics, mixed case; factual TRUE incl.
  `holanda` boundary, `""`, whitespace).
- `ChatNeedsWebGateTest` (new, 4 tests): social ES + EN → 0 search calls, 0
  turn-time connectivity checks, original-text transcript, null notice,
  assistant still answers; factual control searches + fuses; social+URL text
  still fetches via the URL branch.
- Full suite: **782 tests, 0 failures, 0 errors, 0 skipped** + 
  `./gradlew :app:assembleDebug` BUILD SUCCESSFUL.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Test] Replaced `hola`-bearing fixtures colliding with the locked social token**

- **Found during:** Task 2 verification (`com.warped.ui.chat.*` run)
- **Issue:** Two existing tests used `"hola sin urls"` as a neutral no-URL
  fixture while asserting search-time behavior; under the gate `hola` (locked
  WORD) skips the pre-search by design:
  - `ChatGroundingToggleTest`: `grounded turn with no urls still sends
    system-prompt-prefixed text` (expected SYSTEM_PROMPT prefix, got plain)
  - `SettingsTavilyTest`: `offline never calls search and yields offline
    notice` (expected OFFLINE notice + SYSTEM_PROMPT text, got plain + no
    notice — caught by fixture audit before the full run)
- **Fix:** Fixture → `"pregunta sin urls"` (verified non-social under the
  gate) in exactly those two tests; assertions unchanged, test intent
  preserved. Other `"hola sin urls"` uses (grounding-off / toggle-pending
  tests) pass unmodified — gate lives inside `if (doGround)` / they assert
  no content.
- **Also fixed in new tests:** `fetcher.hasValidatedInternet()` is called once
  at `ChatViewModel` init (`refreshConnectivity`), so the "no socket" assertion
  is `exactly = 1` (init only; the gated turn adds zero), not `exactly = 0`.
- **Files modified:** `ChatGroundingToggleTest.kt`, `SettingsTavilyTest.kt`,
  `ChatNeedsWebGateTest.kt`
- **Commit:** e0cdc05e

**2. [Rule 1 - Test] Updated verbatim `TOOL_USE_SYSTEM_HINT` pin (expected)**

- **Found during:** Task 3 (plan-flagged: the pin WILL fail)
- **Fix:** Extended the pinned string with the locked no-social-search
  sentence; added identity/hint/composition pins.
- **Commit:** 6824f709

Or otherwise: none — plan executed as written apart from the above.

## Known Stubs

None — no placeholder data, mock payloads, or unwired surfaces introduced.

## Threat Flags

None — no new network endpoints, auth paths, or schema changes. The gate only
*removes* pre-search calls on social turns; remote providers untouched.

## Follow-up (by design, not a regression)

**Remote-provider identity:** `IDENTITY_LINE` is local-only via
`LiteRTLmProvider` (planner-locked: no base-persona path reaches ungrounded
turns today). OpenAI / Anthropic / Ollama / Custom / LM-Studio system prompts
are untouched — a follow-up quick-task should add the equivalent identity line
(and optionally the no-social-search sentence) to each remote provider's
system prompt path.

## On-device notes

Not run on-device in this session (unit suite + `assembleDebug` only).
Suggested checkpoint on-device: send `hola, quien eres` with grounding on —
expect an identity answer with no Fuentes row and no search progress UI; send
`latest news` — expect sources fused as before.

## Self-Check: PASSED

- Files exist: `NeedsWeb.kt`, `NeedsWebTest.kt`, `ChatNeedsWebGateTest.kt`
  (all FOUND); `ChatViewModel.kt`, `LiteRTLmProvider.kt`,
  `LiteRTLmLoopTest.kt` modified as described.
- Commits exist: 4019bdde, e0cdc05e, 6824f709 (all in `git log`).
- Grep gate: `systemInstruction = Contents.of` appears 2× in
  `LiteRTLmProvider.kt` (armed + unarmed).
- Success criteria: NeedsWebTest green; social turn 0 search / 0
  connectivity / plain transcript; factual + URL unchanged (full suite
  green); identity + hint pinned; full suite + assembleDebug green;
  remote-identity follow-up recorded above.
