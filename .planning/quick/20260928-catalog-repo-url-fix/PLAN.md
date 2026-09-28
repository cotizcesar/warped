# Quick Plan: Catalog repo-slug fix + Gemma 4 E4B entry (SUPERSEDES 20260928-add-gemma4-e4b)

> **Supersedes:** `.planning/quick/20260928-add-gemma4-e4b/PLAN.md` — DO NOT execute the old plan. It assumed the download URL scheme `warped-community/${entry.name}`, which is BROKEN: real mirror repos carry a `-litert-lm` suffix (short names 404). This plan replaces the URL mechanism with an explicit per-entry `repo` field and then adds the E4B entry on top. Still-valid parts reused from the old plan: E4B entry data (size, flags), download-scheme test approach, fixture-collision fix.

## Goal

Every catalog download URL resolves to its real `warped-community/*-litert-lm` mirror repo, `gemma-4-E4B-it` appears in the catalog with verified capabilities, and the full unit test suite is green.

## Locked evidence (verified 2026-09-28, do not re-research)

- Real mirror repos: `warped-community/gemma-4-E2B-it-litert-lm`, `warped-community/gemma-4-E4B-it-litert-lm` (file `gemma-4-E4B-it.litertlm`), `warped-community/gemma-4-12B-it-litert-lm`. Short names without the suffix 404.
- 3n mirrors `warped-community/gemma-3n-E2B-it-litert-lm` / `warped-community/gemma-3n-E4B-it-litert-lm` are being created + filled in a background job RIGHT NOW — DO NOT touch 3n `modelFile`/`sizeInBytes` (deferred follow-up once transfer sizes are known). The repo-slug mechanism must cover them by construction.
- E4B file size (CDN-verified): `3659530240` bytes. E4B flags: text/vision/audio/supportsThinking `true`, supportsFunctionCalling `false` (APP POLICY: tool execution removed in v2.2 Phase 49 DEL-01, badge would mislead), speculativeDecoding `true`, extendedContext `false`, mtpSupport `false`.
- Broken code: `CatalogViewModel.kt:42-56` — `downloadId` + `startDownload` build `warped-community/${entry.name}`; `REPO_PREFIX` constant + the KDoc claim "the allowlist `name` is the repo slug within it" (`:23-24`) are wrong for every entry.
- `downloadId` callers checked 2026-09-28: only `HuggingFaceScreen.kt:98-105` (session-scoped map key for `downloadStates` + pause/resume/cancel) and internal `startDownload`. No tests reference `downloadId` or `REPO_PREFIX`; `modelId` is session-scoped only (in-memory map + WorkManager tag in `ModelDownloadManager.kt`, not persisted in Room) — safe to derive from `repo`.
- No `CatalogViewModel` tests exist (add URL test). `ModelAllowlistTest.kt:163` uses `local("gemma-4-E4B-it", "gemma-4-E4B-it.litertlm")` as the "unlisted model" fixture — collides once E4B is allowlisted (must replace).

## Out of scope

3n `modelFile`/`sizeInBytes` swap (follow-up), 12B catalog entry (rejected), engine/inference/Room changes, ThinkingConfig wiring.

## Tasks

### Task 1: Add explicit `repo` field to AllowlistedModel with legacy fallback

**Files:**
- `app/src/main/java/com/warped/data/repository/ModelAllowlistRepository.kt`

**Action:**
- Add `val repo: String? = null` to `AllowlistedModel` (nullable + default so unknown-key-tolerant parsing never breaks; full slug incl. org, e.g. `warped-community/gemma-4-E4B-it-litert-lm`).
- Add a computed helper on `AllowlistedModel`, e.g. `val repoSlug: String get() = repo?.takeIf { it.isNotBlank() } ?: "warped-community/$name"`, with KDoc documenting the back-compat rule: explicit `repo` wins; legacy `warped-community/${name}` applies ONLY when the field is absent/blank (old assets, tests constructing the model by hand).
- Extend the `gemma-4-E2B-it exception` KDoc to cover `gemma-4-E4B-it` identically (docs-verified vision/audio/thinking; function-calling false per Phase 49 DEL-01). No other logic changes.

**Verify:** `./gradlew :app:assembleDebug` still compiles (full test proof in Task 4).
**Done:** `AllowlistedModel` parses a `repo` field; missing `repo` falls back to the legacy slug; documented in KDoc.

### Task 2: Allowlist asset — repo slugs on all entries + E4B entry

**Files:**
- `app/src/main/assets/model_allowlist.json`

**Action:**
- Add `"repo"` to each existing entry (full slug incl. org; DO NOT change 3n `modelFile`/`sizeInBytes`):
  - `gemma-4-E2B-it` → `warped-community/gemma-4-E2B-it-litert-lm`
  - `gemma-3n-E2B-it-int4` → `warped-community/gemma-3n-E2B-it-litert-lm`
  - `gemma-3n-E4B-it-int4` → `warped-community/gemma-3n-E4B-it-litert-lm`
- Append a 4th entry mirroring the E2B shape exactly: `name` `gemma-4-E4B-it`, `displayName` `Gemma 4 E4B IT`, `repo` `warped-community/gemma-4-E4B-it-litert-lm`, `modelFile` `gemma-4-E4B-it.litertlm`, `sizeInBytes` `3659530240`, capabilities text/vision/audio/supportsThinking/speculativeDecoding `true`, supportsFunctionCalling/extendedContext/mtpSupport `false`, `llmPromptTemplates` `{}`, `taskTypes` `["chat"]`. Keep `name` short-stable (it is the display/lookup key, not the URL source).
- Update `meta.note` to stay accurate: keep verified-only wording, extend the docs-verified clause to cover E2B and E4B (device confirmation pending; E4B size verified via CDN HEAD 2026-09-28).
- Keep JSON valid. Do NOT touch 12B, engine code, or ThinkingConfig.

**Verify:** `python3 -c "import json; d=json.load(open('app/src/main/assets/model_allowlist.json')); print(len(d['models']), [(m['name'], m.get('repo')) for m in d['models']])"` prints `4` with all four `repo` slugs ending in `-litert-lm`.
**Done:** Asset parses with 4 entries; every entry carries its real repo slug; E4B field values match the locked data byte-for-byte.

### Task 3: CatalogViewModel builds URLs from entry.repo

**Files:**
- `app/src/main/java/com/warped/ui/huggingface/CatalogViewModel.kt`

**Action:**
- `downloadId(entry)` returns `"${entry.repoSlug}/${entry.modelFile}"` and `startDownload()` builds `fileUrl = "https://huggingface.co/${entry.repoSlug}/resolve/main/$encodedFile"` (keep the existing per-segment URL-encoding). Both go through the Task 1 helper — single source of truth.
- Keep `REPO_PREFIX = "warped-community"` ONLY if still referenced (it now lives in the fallback helper); otherwise delete it. Fix the class KDoc (`:23-24` "the allowlist `name` is the repo slug within it") to describe the explicit `repo` field + legacy fallback.
- `modelId` format changes from `warped-community/<name>/<file>` to `warped-community/<name>-litert-lm/<file>` — accepted per the locked caller analysis (session-scoped key only, no persistence, no tests). `HuggingFaceScreen` needs no change (it uses `viewModel.downloadId(entry)` consistently).

**Verify:** `grep -rn 'REPO_PREFIX/${' app/src/main/java/com/warped/ui/huggingface/ ; grep -rn 'entry.name}/resolve' app/src/main/java/` — both return nothing (no remaining name-based URL construction).
**Done:** Download URLs and IDs derive from `entry.repo`; no `warped-community/${name}` URL construction remains.

### Task 4: Tests — repo parsing, URL construction, counts/flags, fixture-collision fix

**Files:**
- `app/src/test/java/com/warped/data/repository/ModelAllowlistTest.kt`
- NEW `app/src/test/java/com/warped/ui/huggingface/CatalogDownloadUrlTest.kt` (create parent dirs as needed)

**Action:**
- `ModelAllowlistTest`: `hasSize(3)` → `hasSize(4)` in both `shipped asset parses` (`:37`) and `repository exposes capability queries` (`:99`).
- Add E4B assertions to `shipped asset parses`: name `gemma-4-E4B-it`, displayName `Gemma 4 E4B IT`, modelFile `gemma-4-E4B-it.litertlm`, sizeInBytes `3659530240L`, repo `warped-community/gemma-4-E4B-it-litert-lm`, taskTypes contains `chat`.
- Assert every shipped entry's `repoSlug` ends with `-litert-lm` and contains no `${name}`-derived fallback (i.e. `repo` non-null on all shipped entries).
- Add a fallback unit test: hand-constructed `AllowlistedModel` without `repo` yields `repoSlug == "warped-community/<name>"` (back-compat proof).
- Extend flag maps in `shipped asset flags are verified-only` with `gemma-4-E4B-it`: text/vision/audio `true`, speculativeDecoding `true`, thinking `true` (function-calling/extendedContext/mtpSupport stay `false` via existing loop assertions).
- `repository exposes capability queries`: add `findByModelFile("gemma-4-E4B-it.litertlm")?.name == "gemma-4-E4B-it"`, `supportsThinking("gemma-4-E4B-it")` true, `supportsFunctionCalling` false, `supportsSpeculativeDecoding` true.
- Fixture-collision fix (`:163`): the "unlisted model" fixture `local("gemma-4-E4B-it", "gemma-4-E4B-it.litertlm")` is now allowlisted — replace with a genuinely unlisted fixture (e.g. `local("some-future-model", "some-future-model.litertlm")` with default stored caps; keep asserting `reasoning` false) AND add an allowlisted-E4B block (`reasoning` true, `vision` true, `audio` true, `tools` false). Check the `LocalModel` constructor in `app/src/main/java/com/warped/domain/model/` — mirror the existing named-args construction.
- NEW `CatalogDownloadUrlTest` (no such tests exist today): mock `ModelAllowlistRepository` (return `parseModelAllowlist` of the shipped asset for `models`) and `ModelDownloadManager` with mockk (`every { downloadManager.downloadStates } returns MutableStateFlow(emptyMap())`, `every { downloadManager.startDownload(...) } returns Unit` with `slot<String>()` capturing `fileUrl`/`modelId` and `slot<Long>()` for `fileSizeBytes`). Cases: (a) E4B entry → `fileUrl == "https://huggingface.co/warped-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm"`, `fileSizeBytes == 3659530240L`, `modelId == "warped-community/gemma-4-E4B-it-litert-lm/gemma-4-E4B-it.litertlm"`, `isGated == false`; (b) E2B entry → `-litert-lm` URL (regression: old short-name URL gone); (c) repo-less entry → legacy `warped-community/<name>` fallback URL. `startDownload` signature: `ModelDownloadManager.kt:67-73` (`modelId, fileName, fileUrl, fileSizeBytes, isGated=false`). Follow existing test deps (JUnit5 `@Test`, Truth, mockk — see `ModelAllowlistTest.kt:1-9`).

**Verify:** `./gradlew :app:testDebugUnitTest --tests "com.warped.data.repository.ModelAllowlistTest" --tests "com.warped.ui.huggingface.CatalogDownloadUrlTest"` green; `grep -rn "hasSize(3)" app/src/test/java/com/warped/data/repository/` returns nothing.
**Done:** Both test classes pass; no test asserts a 3-entry catalog; URL tests pin repo-based URLs + fallback.

## Verification (full)

- `./gradlew :app:assembleDebug` succeeds.
- `./gradlew :app:testDebugUnitTest` — full suite green.

## must_haves

- truths:
  - "Tapping download on any catalog entry hits its real -litert-lm mirror repo, not a 404 short-name URL"
  - "Gemma 4 E4B IT appears in the app model catalog alongside the existing 3 entries"
  - "E4B download resolves with the verified 3.41 GiB size"
  - "E4B shows Thinking/Vision/Audio capability, and no Tools badge"
  - "Full unit test suite passes with the 4-entry repo-based catalog"
- artifacts:
  - path: "app/src/main/assets/model_allowlist.json"
    provides: "4-entry catalog with real repo slugs incl. E4B"
    contains: "warped-community/gemma-4-E4B-it-litert-lm"
  - path: "app/src/main/java/com/warped/data/repository/ModelAllowlistRepository.kt"
    provides: "repo field parsing + legacy fallback"
    contains: "repoSlug"
  - path: "app/src/main/java/com/warped/ui/huggingface/CatalogViewModel.kt"
    provides: "repo-based downloadId + fileUrl"
    contains: "repoSlug"
  - path: "app/src/test/java/com/warped/data/repository/ModelAllowlistTest.kt"
    provides: "Updated 4-entry catalog assertions"
    contains: "hasSize(4)"
  - path: "app/src/test/java/com/warped/ui/huggingface/CatalogDownloadUrlTest.kt"
    provides: "Download URL + size + fallback proof"
    contains: "litert-lm/resolve/main"
- key_links:
  - from: "app/src/main/assets/model_allowlist.json"
    to: "ModelAllowlistRepository.models"
    via: "asset parse at runtime"
    pattern: "model_allowlist\\.json"
  - from: "CatalogViewModel.startDownload"
    to: "huggingface.co/warped-community"
    via: "repoSlug-based URL construction"
    pattern: "repoSlug.*resolve/main"
