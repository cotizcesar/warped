# Code-Intent Pre-Search Gate — Execution Summary

**Dir:** `.planning/quick/20260930-code-intent-gate/`
**Date:** 2026-09-30
**Status:** COMPLETE — all 3 tasks executed, full suite + assembleDebug green.
**Requirements:** CODE-INTENT-01, CODE-INTENT-02, CODE-INTENT-03 (all met)

## What was built

Code-generation turns no longer trigger the heuristic pre-search. A pure
`CodeIntent.isCodeTurn` detector gates the same no-URL pre-search branch
`NeedsWeb` already gates (single combined skip: social OR code), and one
clause was appended to `TOOL_USE_SYSTEM_HINT` so code comes from knowledge
first. Loop arming, the URL-fetch branch, and `GroundingPrecedence` are
untouched — the model keeps its mid-turn `web_search` escape hatch.

## Commits (atomic, one per task)

| Task | Commit | Files |
|------|--------|-------|
| 1 — CodeIntent detector + unit tests | `e86d2930` | `data/grounding/CodeIntent.kt` (new), `data/grounding/CodeIntentTest.kt` (new) |
| 2 — Gate wiring + hint clause | `8d1d752a` | `ui/chat/ChatViewModel.kt`, `data/local/inference/LiteRTLmProvider.kt`, `data/local/inference/LiteRTLmLoopTest.kt` (pin update) |
| 3 — Gate-integration tests + hint pin | `9b684cf9` | `ui/chat/ChatCodeIntentGateTest.kt` (new), `LiteRTLmLoopTest.kt` (contains-pin) |

## Skip rule (as locked in plan)

`skip = (noun AND (verb OR language)) OR (verb AND language)`, with one
deliberate token-scoped typo exception: a single token containing
`avascript` / `ython` / `ypescript` / `otlin` counts as that language (catches
the verbatim evidence `jsavascript`). Fail-open: empty/blank returns FALSE
(searches as today). Whole-token matching everywhere else (`codigos` ≠
`codigo`, `explicame` ≠ `explica`, `holanda`-style safety preserved).

## Test results

`./gradlew :app:testDebugUnitTest :app:assembleDebug` — BUILD SUCCESSFUL.

| Suite | Tests | Failures |
|-------|-------|----------|
| `CodeIntentTest` (new) | 27 | 0 |
| `ChatCodeIntentGateTest` (new) | 3 | 0 |
| `LiteRTLmLoopTest` (pin updated + new contains-pin) | 37 | 0 |
| `NeedsWebTest` (untouched behavior) | all | 0 (green) |
| Full `:app:testDebugUnitTest` | all | 0 |

Truth-table coverage: evidence string verbatim, `write a quicksort in
python`, ES/EN noun+verb cases, diacritics (`código/función/método`),
case/punctuation variance skip; `Kotlin 2.3 new features` (language alone),
`write an email` (verb alone), factual, social, empty, `codigos` boundary
still search. Gate-integration: code turn = 0 search + 1 init-only
connectivity check + byte-identical request + null notice + empty sources;
factual fuses `--- Source [1]`; code+URL fetches via `fetchAll`, zero
searches.

## Deviations from plan

None — plan executed exactly as written. (One intra-task whitespace retry on
the `LiteRTLmProvider.kt` KDoc edit; no behavioral impact.)

## On-device notes (human, next session)

1. Send `Dame un ejemplo de codigo simple en jsavascript` — expect code from
   weights, no Fuentes row, no "based on Source" hedge.
2. Send `Kotlin 2.3 new features` — expect grounded answer (still searches).
3. Versioned-API spot check (e.g. a "latest Retrofit version" style query
   phrased as code) — the armed loop should still `web_search` mid-turn via
   the escape hatch.

## Self-check

- [x] `CodeIntent.kt`, `CodeIntentTest.kt`, `ChatCodeIntentGateTest.kt` exist
- [x] Commits `e86d2930`, `8d1d752a`, `9b684cf9` in log
- [x] No stubs; no new threat surface (no endpoints/auth/schema changes)
- [x] PASSED
