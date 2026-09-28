# SUMMARY — Catalog 3n .task → .litertlm swap

**Status:** COMPLETE — all 3 tasks done, build + full unit suite green.
**Date:** 2026-09-28

## What changed

- `app/src/main/assets/model_allowlist.json` — the two Gemma 3n entries now
  point at the verified `.litertlm` files:
  - `gemma-3n-E2B-it-int4`: `gemma-3n-E2B-it-int4.litertlm`, size `3655827456`
  - `gemma-3n-E4B-it-int4`: `gemma-3n-E4B-it-int4.litertlm`, size `4919541760`
  - Repos, names, flags, order, ramNote/blurb untouched.
- `app/src/test/java/com/warped/data/repository/ModelAllowlistTest.kt`
  - Updated the 4 stale `.task`/old-size pins to the new values.
  - Added `shipped 3n entries pin litertlm file mapping` test asserting
    repo + file + size for both 3n entries.
- `app/src/main/java/com/warped/data/local/inference/LiteRtLmCacheManager.kt`
  - Comment-only KDoc fix (`.task` → `.litertlm` in the example filename).

## Commits

- `c0b857d7` — feat(quick-3n-swap): swap 3n catalog entries to .litertlm
- `58e0393c` — test(quick-3n-swap): pin new 3n litertlm mapping

## Test results

- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL.
- `./gradlew :app:testDebugUnitTest` — BUILD SUCCESSFUL,
  **311 tests, 0 failures, 0 errors**.
  - `ModelAllowlistTest`: 12/12 pass, including the new
    `shipped 3n entries pin litertlm file mapping` test.
- Grep gate `grep -rn "int4\.task\|3136226711\|4405655031" app/src` → **CLEAN**
  (re-verified after the build).

## Honest note

End-to-end 3n download still needs on-device confirmation (no adb in this
environment) — unit tests pin the catalog mapping only.
