# Quick Task: Catalog 3n .task → .litertlm swap

## Goal
Swap the two Gemma 3n catalog entries from stale `.task` filenames/sizes to the
verified `.litertlm` files (mirrors verified OK 2026-09-28), and update every
test/assertion that pins the old values. No flags, order, blurbs, download
engine, or 12B changes.

## Locked data (do not re-derive, do not re-verify via network)
- `gemma-3n-E2B-it-int4`: modelFile `gemma-3n-E2B-it-int4.litertlm`,
  sizeInBytes `3655827456`, repo `warped-community/gemma-3n-E2B-it-litert-lm`
  (repo already correct in asset — leave untouched).
- `gemma-3n-E4B-it-int4`: modelFile `gemma-3n-E4B-it-int4.litertlm`,
  sizeInBytes `4919541760`, repo `warped-community/gemma-3n-E4B-it-litert-lm`
  (repo already correct in asset — leave untouched).
- Entry `name`, `displayName`, capabilities, ramNote, blurb, order: UNTOUCHED.

## Context
- `app/src/main/assets/model_allowlist.json` lines 51–92 (the two 3n entries).
- `app/src/test/java/com/warped/data/repository/ModelAllowlistTest.kt`
  (4 stale pins: lines ~40, ~41, ~178, ~241).

## Task 1 — Swap the 2 catalog entries
- File: `app/src/main/assets/model_allowlist.json`
- On the `gemma-3n-E2B-it-int4` entry ONLY: set
  `"modelFile": "gemma-3n-E2B-it-int4.litertlm"` and `"sizeInBytes": 3655827456`.
- On the `gemma-3n-E4B-it-int4` entry ONLY: set
  `"modelFile": "gemma-3n-E4B-it-int4.litertlm"` and `"sizeInBytes": 4919541760`.
- Change NOTHING else (no whitespace reformat beyond the edited lines, no flag/
  order/blurb/ramNote/repo/meta edits). File must remain valid JSON.
- Verify: `python3 -c "import json; d=json.load(open('app/src/main/assets/model_allowlist.json')); print([(m['name'],m['modelFile'],m['sizeInBytes']) for m in d['models'] if '3n' in m['name']])"`
  shows the two new `.litertlm` filenames with sizes 3655827456 / 4919541760.
- Done: asset parses; only the 4 intended scalar values differ
  (`git diff --stat` shows 1 file, `git diff` shows only modelFile/sizeInBytes lines).

## Task 2 — Update tests + pin new 3n mapping
- File: `app/src/test/java/com/warped/data/repository/ModelAllowlistTest.kt`
- Update the 4 stale references:
  - line ~40 → `gemma-3n-E2B-it-int4.litertlm`
  - line ~41 → `3655827456L`
  - line ~178 → `findByModelFile("gemma-3n-E4B-it-int4.litertlm")`
  - line ~241 → `local("gemma-3n-E2B-it-int4", "gemma-3n-E2B-it-int4.litertlm")`
- Add/extend assertions pinning the NEW 3n file mapping (repo + file + size)
  in the `shipped asset parses with expected entries` test (or a small new
  `@Test`): assert E2B repo == `warped-community/gemma-3n-E2B-it-litert-lm`,
  file == `gemma-3n-E2B-it-int4.litertlm`, size == 3655827456L; and E4B repo ==
  `warped-community/gemma-3n-E4B-it-litert-lm`, file ==
  `gemma-3n-E4B-it-int4.litertlm`, size == 4919541760L.
- Also update the doc-comment example in
  `app/src/main/java/com/warped/data/local/inference/LiteRtLmCacheManager.kt`
  line ~27 (`gemma-3n-E2B-it-int4.task` → `gemma-3n-E2B-it-int4.litertlm`) so the
  grep gate below passes. Comment-only change, no logic change.
- Do NOT touch flags/order/ramNote/blurb assertions (they still pass unchanged).
- Verify: `grep -rn "int4\.task\|3136226711\|4405655031" app/src || echo CLEAN`
  prints CLEAN.
- Done: no old `.task` name or old size literal remains under `app/src`; new
  mapping is pinned by an explicit test.

## Task 3 — Build + full unit tests green
- Run: `./gradlew :app:assembleDebug` then `./gradlew :app:testDebugUnitTest`
  (both must be green; if the environment lacks Android SDK, report the exact
  failure instead of claiming success).
- Re-run the grep gate from Task 2 after the build.
- Done: assemble + full unit-test suite green, grep gate CLEAN.

## Out of scope (do NOT do)
Flags, entry order, ramNote/blurb strings, download engine / cache logic,
12B catalog entries (excluded by decision), meta.note edits.

## Honest note (for the summary, not code)
End-to-end 3n download still needs on-device confirmation (no adb in this
environment) — unit tests pin the catalog mapping only.
