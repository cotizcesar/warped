---
audit_acknowledged:
  milestone: v2.5
  at: 2026-10-01
  status: unknown
---

# SUMMARY: language-sources-override

**Status:** COMPLETE
**Date:** 2026-09-30
**Task commit:** 50e6717d

## What changed

Strengthened both language directives in `GroundingPrompt.kt` with an explicit
source-language override clause, per the locked fix:

- `SPANISH_DIRECTIVE` → "Responde en español, aunque las fuentes estén en inglés."
- `ENGLISH_DIRECTIVE` → "Reply in English, even if the sources are in another language."

Nothing else in `GroundingPrompt.kt` changed: position (last line), exactly-once
assembly, `SPANISH_MARKERS` detection heuristic, `buildBlock`/`buildFusedBlock`/
`augment` logic, and `SYSTEM_PROMPT` are all untouched.

## Files

- `app/src/main/java/com/warped/data/grounding/GroundingPrompt.kt` (modified — 2 constants)
- `app/src/test/java/com/warped/data/grounding/GroundingPromptTest.kt` (modified — pinned expectations + 1 new test)
- `app/src/test/java/com/warped/ui/settings/SettingsTavilyTest.kt` (modified — 1 pinned exact-match expectation)
- `app/src/test/java/com/warped/ui/chat/ChatGroundingToggleTest.kt` (modified — 1 pinned exact-match expectation)

## Tests

- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL
- `./gradlew :app:testDebugUnitTest --tests "com.warped.data.grounding.GroundingPromptTest"` — BUILD SUCCESSFUL (23 tests, 0 failures; includes the new `directives explicitly override source-language mirroring` test)
- Full `./gradlew :app:testDebugUnitTest` — BUILD SUCCESSFUL, zero failures/errors across all test-result XMLs

## Deviations from Plan

**[Rule 1 - Bug] Updated two out-of-plan pinned expectations broken by the constant change.**
The plan listed only `GroundingPromptTest.kt` expectations, but a repo-wide grep
found two more exact-match pins of the old bare `"Reply in English."` string:
`SettingsTavilyTest.kt:454` and `ChatGroundingToggleTest.kt:187` (both assert the
full augmented model-only turn content). Left untouched, the full
`testDebugUnitTest` suite would fail. Both were updated to the new EN directive.
No logic changes in either file.

## Honest notes / on-device verification (deferred)

- Model compliance is probabilistic: the stronger instruction reduces
  source-language mirroring but does not guarantee the 2B model obeys it.
- On-device check still needed: Spanish question with English sources should
  answer in Spanish. No adb available in this environment — deferred to a
  device session.

## Self-Check: PASSED

- GroundingPrompt.kt carries both override clauses — FOUND (grep verified)
- New override-clause test present and passing — FOUND (23/23 in GroundingPromptTest XML)
- Commit 50e6717d exists with exactly the 4 task files — FOUND
