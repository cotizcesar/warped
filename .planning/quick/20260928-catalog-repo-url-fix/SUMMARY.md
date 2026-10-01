---
audit_acknowledged:
  milestone: v2.5
  at: 2026-10-01
  status: unknown
---

# EXECUTION SUMMARY — Catalog repo-slug fix + Gemma 4 E4B entry

**Plan:** `.planning/quick/20260928-catalog-repo-url-fix/PLAN.md` (supersedes `20260928-add-gemma4-e4b`)
**Status:** COMPLETE — all 4 tasks executed, committed, verified
**Date:** 2026-09-28

## What was built

Every catalog download URL now resolves to its real `warped-community/*-litert-lm`
mirror repo (short-name 404s eliminated), and `gemma-4-E4B-it` appears in the
catalog with verified capabilities. Mechanism: explicit per-entry `repo` field
with a legacy `warped-community/${name}` fallback for old assets and
hand-constructed models.

## Tasks + commits

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 | `repo` field + `repoSlug` fallback on `AllowlistedModel` | `c3324d2` | `data/repository/ModelAllowlistRepository.kt` |
| 2 | Allowlist asset — repo slugs + E4B entry | `6fef30a` | `app/src/main/assets/model_allowlist.json` |
| 3 | `CatalogViewModel` builds URLs from `entry.repoSlug` | `67dfdbd` | `ui/huggingface/CatalogViewModel.kt` |
| 4 | Tests — counts/flags/URLs/fixture-collision fix | `bcae03f` | `ModelAllowlistTest.kt`, NEW `CatalogDownloadUrlTest.kt` |

## Key details

- **E4B locked data applied byte-for-byte:** repo
  `warped-community/gemma-4-E4B-it-litert-lm`, file
  `gemma-4-E4B-it.litertlm`, size `3659530240`, text/vision/audio/
  supportsThinking/speculativeDecoding `true`, supportsFunctionCalling
  (APP POLICY, Phase 49 DEL-01)/extendedContext/mtpSupport `false`.
- **3n untouched:** `modelFile`/`sizeInBytes` for both 3n entries unchanged
  (background transfer job owns them); they only gained `repo` slugs.
- **`modelId` format change accepted:** now
  `warped-community/<name>-litert-lm/<file>` (session-scoped key only, no
  persistence, no tests referenced it). `HuggingFaceScreen` needed no change.
- **`REPO_PREFIX` deleted** — no remaining `warped-community/${name}` URL
  construction (both greps return nothing).
- **Fixture-collision fix:** the "unlisted model" fixture
  `local("gemma-4-E4B-it", …)` renamed to `local("some-future-model", …)`;
  added an allowlisted-E4B `effectiveCapabilities` block (reasoning/vision/
  audio true, tools false).
- **Deviations:** none — plan executed exactly as written. No auth gates.

## Test results

- Targeted: `ModelAllowlistTest` + `CatalogDownloadUrlTest` — green.
- Full: `./gradlew :app:testDebugUnitTest` — **297 tests, 0 failures,
  0 errors, 0 skipped** — green.
- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL.
- `grep -rn "hasSize(3)"` in repository tests — nothing (no 3-entry
  assertions remain).

## Must-haves check

- Tapping download on any catalog entry hits its real `-litert-lm` mirror
  repo — pinned by `CatalogDownloadUrlTest` for E4B + E2B.
- Gemma 4 E4B IT in the catalog alongside the existing 3 entries — 4-entry
  asset asserted.
- E4B resolves with verified 3.41 GiB size (`3659530240L`) — asserted in
  asset parse + URL test.
- E4B shows Thinking/Vision/Audio, no Tools badge — flag map +
  `effectiveCapabilities` assertions.
- Full suite green with 4-entry repo-based catalog — 297/297.
