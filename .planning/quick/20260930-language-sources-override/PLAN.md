# Quick-Task Plan: language-sources-override

## Cause (locked)
On-device evidence: Spanish question + English sources → fully English answer.
The 2B model mirrors the CONTEXT language (fused English blocks) over the bare
"Responde en español." directive. The last-line directive alone does not
override source-language mirroring.

## Fix (locked)
Strengthen both directives to explicitly override source-language mirroring:
- Spanish: "Responde en español, aunque las fuentes estén en inglés."
- English: "Reply in English, even if the sources are in another language."
Nothing else changes: position (last line), exactly-once, detection heuristic
(SPANISH_MARKERS regex), both constants' other sentences (there are none —
each constant is a single sentence; the "other sentences" constraint means do
not add/remove any additional sentences beyond the override clause).

## Out of scope
Source-language filtering, translation, UI, loop mechanics.

## Tasks

### Task 1: Strengthen directives + update and add tests
Files:
- `app/src/main/java/com/warped/data/grounding/GroundingPrompt.kt`
- `app/src/test/java/com/warped/data/grounding/GroundingPromptTest.kt`

Actions:
1. In `GroundingPrompt.kt`, edit exactly the two constants (~lines 23-24):
   - `SPANISH_DIRECTIVE` → "Responde en español, aunque las fuentes estén en inglés."
   - `ENGLISH_DIRECTIVE` → "Reply in English, even if the sources are in another language."
   - Do NOT touch `SPANISH_MARKERS`, `isSpanish()`, `languageDirective()`
     selection logic, `buildBlock()`, `buildFusedBlock()`, or `augment()`
     assembly/position. Do NOT add a generic same-language sentence to
     `SYSTEM_PROMPT` (pinned by test `system prompt carries no generic
     same-language sentence`).
2. In `GroundingPromptTest.kt`, update every pinned expectation of the old
   bare directives to the new strings:
   - `languageDirective selects the exact directive` (ES + EN + empty→EN)
   - `augment appends the directive exactly once as the last line - block path`
     (endsWith / countOccurrences / doesNotContain counterparts)
   - `augment appends the directive exactly once as the last line - no-block path`
   - `augment derives the directive from the original text not the block`
     (both endsWith assertions)
   - `augment disabled returns original untouched with no directive`
     (doesNotContain new strings)
   - `null block with grounding enabled prepends system prompt` (exact
     `isEqualTo` string embeds "Reply in English." — update to new EN directive)
3. Add one new test asserting the override clause presence per language, e.g.
   `directives explicitly override source-language mirroring`:
   - `assertThat(GroundingPrompt.SPANISH_DIRECTIVE).contains("aunque las fuentes estén en inglés")`
   - `assertThat(GroundingPrompt.ENGLISH_DIRECTIVE).contains("even if the sources are in another language")`

Verify:
- `./gradlew :app:assembleDebug` green
- `./gradlew :app:testDebugUnitTest --tests "com.warped.data.grounding.GroundingPromptTest"` green (then full `testDebugUnitTest` green if time permits)

Done:
- Both constants carry the override clause; all pinned expectations updated;
  new override-clause test passes; assembleDebug + full testDebugUnitTest green.

## Honest notes
- Model compliance is still probabilistic: a stronger instruction reduces
  mirroring but does not guarantee the 2B model obeys it every turn.
- On-device verification still needed: Spanish question with English sources
  should answer in Spanish. No adb available in this environment, so this
  check is deferred to a device session.
