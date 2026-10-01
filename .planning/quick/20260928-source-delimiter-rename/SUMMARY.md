---
audit_acknowledged:
  milestone: v2.5
  at: 2026-10-01
  status: unknown
---

# SUMMARY: source-delimiter rename (block-label echo fix)

**Status:** COMPLETE — all 3 tasks executed, committed, verified.
**Date:** 2026-09-28
**Plan:** `.planning/quick/20260928-source-delimiter-rename/PLAN.md`

## What changed

Renamed web-grounding block delimiters so the `WEB CONTEXT N` label phrase no
longer exists to be echoed by the model. Citation-marker scheme (`[1]`/`[2]`)
unchanged.

- **New per-page header:** `--- Source [N]: <url> ---`
- **New footer:** `--- End of sources ---` (single shared footer per block)
- **`SYSTEM_PROMPT`:** only the block reference changed
  (`[WEB CONTEXT] block` → `the sources below`); cite-`[1]`/`[2]` and
  never-invent-URLs sentences byte-identical.
- **Sanitizer:** two new escapes added (`--- Source-[`, `--- End-of-sources ---`);
  all three old escapes KEPT (defense in depth for cached/stored old-format blocks).

## Commits (atomic, one per task)

| Task | Commit | Files |
|------|--------|-------|
| 1 — Rename builders + SYSTEM_PROMPT | `781f1041` | `GroundingPrompt.kt`, `GroundingBudget.kt` (KDoc), `GroundingResult.kt` (KDoc) |
| 2 — Sanitizer escapes | `abefebc4` | `WebContextSanitizer.kt` |
| 3 — Tests + echo-regression test | `b948a949` | `GroundingPromptTest.kt`, `MultiUrlFusionTest.kt`, `MultiUrlFetcherTest.kt`, `WebContextSanitizerTest.kt` |

## Test results

- `./gradlew :app:assembleDebug` — **BUILD SUCCESSFUL**
- `./gradlew :app:testDebugUnitTest` — **BUILD SUCCESSFUL** (full suite green)
- Grounding suites: `GroundingPromptTest` 6/6, `MultiUrlFusionTest` 3/3,
  `MultiUrlFetcherTest` 10/10, `WebContextSanitizerTest` 13/13,
  `ChatGroundingToggleTest` 5/5 — 0 failures/errors.
- New echo-regression test (`built blocks never contain the web context label
  phrase`) covers `buildBlock`, `buildFusedBlock` (2 pages), and `augment`.
- `ChatGroundingToggleTest` confirmed by inspection to use only the
  `SYSTEM_PROMPT` constant — no change needed.

## Grep gates (per PLAN.md Task 3)

- `WEB CONTEXT` in `GroundingPrompt.kt`: **zero matches** (stricter than the
  allowed historical phase-tag comments).
- Old-delimiter strings remaining in tests: only the documented exceptions —
  sanitizer old-escape assertions (`WebContextSanitizerTest.kt:51,58`) and the
  negative `doesNotContain("[END WEB CONTEXT")` guard in the new regression
  test (`GroundingPromptTest.kt:66`). No test asserts old delimiters as live output.

## Deviations

None — plan executed exactly as written. No bugs found, no scope changes,
no auth gates.
