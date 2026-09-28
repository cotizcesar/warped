# Quick Plan: Catalog Order — gemma-4 pair first

## Goal
Reorder `model_allowlist.json` catalog to the locked sequence
`gemma-4-E2B-it → gemma-4-E4B-it → gemma-3n-E2B-it-int4 → gemma-3n-E4B-it-int4`.
UI already renders asset order (verified: `CatalogViewModel.models`
is a direct `allowlistRepository.models` passthrough, line 39, no sort;
repo-wide grep for `sortedBy|sortedWith|.sorted(|sortBy|orderBy` hits only
unrelated files — PromptLabViewModel, LiteRtLmCacheManager, TypeMapper).
No sorting logic to add or remove.

## Context
- Asset: `app/src/main/assets/model_allowlist.json` (current order:
  4-E2B, 3n-E2B, 3n-E4B, 4-E4B)
- Tests: `app/src/test/java/com/warped/data/repository/ModelAllowlistTest.kt`
- Parser/repo: `app/src/main/java/com/warped/data/repository/ModelAllowlistRepository.kt`
  (`parseModelAllowlist` returns decoded list as-is, asset order preserved)
- Consumer: `app/src/main/java/com/warped/ui/huggingface/CatalogViewModel.kt:39`

## Tasks

### Task 1: Reorder JSON entries (E4B block moves to position 2)
**Files:** `app/src/main/assets/model_allowlist.json`
**Action:**
- Move the entire `gemma-4-E4B-it` JSON object block (lines 72–92,
  from `{ "name": "gemma-4-E4B-it"` through its closing `},`) to
  immediately after the `gemma-4-E2B-it` block (after line 29's `},`),
  so entry order becomes: gemma-4-E2B-it, gemma-4-E4B-it,
  gemma-3n-E2B-it-int4, gemma-3n-E4B-it-int4.
- Move the block verbatim — no field value changes, no comma errors
  (E4B block keeps trailing comma in middle position; 3n-E4B block
  becomes last and loses its trailing comma... actually 3n-E4B currently
  has trailing comma as middle entry and E4B currently last with no
  trailing comma — swap comma placement accordingly).
- Validate JSON parses (e.g. `python3 -c "import json; json.load(open(...))"`).
**Verify:** JSON valid; `python3 -c` prints names in locked order.
**Done:** Asset lists exactly 4 entries in the locked sequence, all
  fields byte-identical to before.

### Task 2: Add explicit order test + audit existing assertions
**Files:** `app/src/test/java/com/warped/data/repository/ModelAllowlistTest.kt`
**Action:**
- Audit result (pre-computed): NO existing catalog assertion is
  order-sensitive. All shipped-asset tests use name lookup
  (`first { it.name == ... }`, `associateBy`, per-name maps, `findByModelFile`)
  or iterate. The only `models[0]` uses (lines 82–83, 203) operate on
  single-element synthetic JSON, unaffected by catalog order — leave them.
- Add one new test `shipped asset catalog order is locked` asserting
  `models.map { it.name }` equals exactly
  `["gemma-4-E2B-it", "gemma-4-E4B-it", "gemma-3n-E2B-it-int4", "gemma-3n-E4B-it-int4"]`.
**Verify:** New test passes; no existing test modified (none needed).
**Done:** Order is pinned by a failing-if-reordered test; all prior
  assertions untouched and green.

### Task 3: Full build + unit tests green
**Files:** (none — verification only)
**Action:**
- Run `./gradlew :app:assembleDebug` then full `:app:testDebugUnitTest`.
- If any unrelated failure appears, report it verbatim; do NOT fix
  out-of-scope failures, do NOT expand scope.
**Verify:** `./gradlew :app:assembleDebug :app:testDebugUnitTest` exits 0.
**Done:** Debug APK assembles; entire unit-test suite green.

## Out of scope
Everything else — no UI changes, no parser changes, no sorting logic,
no field/capability/size edits, no new models.
