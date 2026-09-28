# Summary: Catalog Order — gemma-4 pair first

**Status:** COMPLETE
**Date:** 2026-09-28
**Plan:** `.planning/quick/20260928-catalog-order/PLAN.md`

## What changed

Reordered `app/src/main/assets/model_allowlist.json` to the locked sequence
`gemma-4-E2B-it → gemma-4-E4B-it → gemma-3n-E2B-it-int4 → gemma-3n-E4B-it-int4`
by moving the `gemma-4-E4B-it` block verbatim from position 4 to position 2
(trailing-comma placement swapped so JSON stays valid). No field values touched.
Added one order-pinning test; no existing test modified.

## Commits

- `f8964ef4` — chore(01-catalog-order): reorder allowlist to locked gemma-4 pair first sequence
- `cee92a81` — test(01-catalog-order): pin shipped catalog order with locked-sequence test

## Test results

- `./gradlew :app:assembleDebug :app:testDebugUnitTest` → **BUILD SUCCESSFUL**
- Full unit suite: **310 tests, 0 failures, 0 errors, 0 skipped**
- New test `shipped asset catalog order is locked()` passes; all 10 pre-existing
  `ModelAllowlistTest` tests still green (audit confirmed none were order-sensitive).

## Deviations

None — plan executed exactly as written.
