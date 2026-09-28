# Quick Plan: Add Gemma 4 E4B IT to model catalog

## Goal
`gemma-4-E4B-it` appears in the app model catalog with docs-verified capabilities and a working download URL, with all unit tests green.

## Locked evidence (verified 2026-09-28, do not re-research)
- Upstream repo `litert-community/gemma-4-E4B-it-litert-lm`: public, not gated; main file `gemma-4-E4B-it.litertlm` (+ `-gpu`/`-web` variants, not used).
- Size via CDN HEAD: `content-length 3659530240` bytes (~3.41 GiB; matches Google spec 3.65 GB).
- Capabilities (Gemma 4 model card + E4B `chat_template.jinja` with `<|think|>` + `<|tool|>` blocks): thinking YES, vision YES, audio YES, tools YES-at-model-level.
- APP POLICY (locked): `supportsFunctionCalling` stays FALSE (tool execution removed in v2.2 Phase 49 DEL-01; badge would mislead). LiteRT-LM supports E2B+E4B on Android today. Gemma 4 12B explicitly OUT (no mobile support, GPU-only, fails on device).

## Out of scope
12B model, engine changes, ThinkingConfig wiring, tools execution.

## Download-URL mechanism (read before implementing)
- `CatalogViewModel.startDownload()` (`app/src/main/java/com/warped/ui/huggingface/CatalogViewModel.kt:45-56`) builds the URL as `https://huggingface.co/$REPO_PREFIX/${entry.name}/resolve/main/$encodedFile` with `REPO_PREFIX = "warped-community"`, and `downloadId = "$REPO_PREFIX/${entry.name}/${entry.modelFile}"`.
- The allowlist `name` is the repo slug within the `warped-community` org (NOT the upstream `litert-community/...` slug). Mirrors the E2B pattern: `warped-community/gemma-4-E2B-it` → `gemma-4-E2B-it.litertlm`.
- New entry therefore resolves to `https://huggingface.co/warped-community/gemma-4-E4B-it/resolve/main/gemma-4-E4B-it.litertlm`. The `warped-community/gemma-4-E4B-it` mirror must exist for the download to succeed — Task 2 includes a HEAD check; if it 404s, STOP and report (mirror upload is a human-action item, do not invent a different URL scheme).

## Tasks

### Task 1: Add E4B entry to allowlist asset + repository docs
**Files:**
- `app/src/main/assets/model_allowlist.json`
- `app/src/main/java/com/warped/data/repository/ModelAllowlistRepository.kt` (KDoc only)

**Action:**
- Append a 4th entry to `models` in `model_allowlist.json`, mirroring the E2B entry shape exactly:
  - `name`: `gemma-4-E4B-it`, `displayName`: `Gemma 4 E4B IT`, `modelFile`: `gemma-4-E4B-it.litertlm`, `sizeInBytes`: `3659530240`
  - `capabilities`: `text true, vision true, audio true, supportsThinking true, supportsFunctionCalling false, speculativeDecoding true` (MTP documented for E4B — follows the 3n precedent of speculativeDecoding true with mtpSupport false), `extendedContext false, mtpSupport false`
  - `llmPromptTemplates`: `{}`, `taskTypes`: `["chat"]`
- Update the `meta.note` to stay accurate: keep the existing verified-only wording, extend the docs-verified clause to cover both E2B and E4B (E4B: Google official Gemma 4 docs + chat_template, device confirmation pending; size verified via CDN HEAD 2026-09-28).
- In `ModelAllowlistRepository.kt`, extend the KDoc `gemma-4-E2B-it exception` comment to cover `gemma-4-E4B-it` identically (docs-verified vision/audio/thinking, function-calling false per Phase 49 DEL-01). Code change only if a comment references "E2B" exclusively — no logic changes.
- Keep JSON valid (trailing commas, quoting). Do NOT touch the 12B (out of scope), engine code, or ThinkingConfig.

**Verify:** `python3 -c "import json; d=json.load(open('app/src/main/assets/model_allowlist.json')); print(len(d['models']), [m['name'] for m in d['models']])"` prints `4` with `gemma-4-E4B-it` present.
**Done:** Asset parses as valid JSON with 4 entries; E4B entry matches the locked field values byte-for-byte.

### Task 2: Verify download URL resolution for the new entry
**Files:** none (verification only; new test file lands in Task 3)

**Action:**
- Confirm by code inspection that the new entry flows through the unchanged `CatalogViewModel.startDownload()` name→URL pattern (`warped-community/${entry.name}/resolve/main/${entry.modelFile}`, URL-encoded) — no ViewModel change needed since the pattern is entry-driven.
- Run `curl -sI "https://huggingface.co/warped-community/gemma-4-E4B-it/resolve/main/gemma-4-E4B-it.litertlm" | head -20` and confirm HTTP 200/302 with a `content-length` near `3659530240`. If it 404s (mirror not yet uploaded), STOP and report to the user — mirror upload is a human-action item; do not repoint the entry at `litert-community` (upstream slug scheme differs from the app's `warped-community` convention, and gated/public headers differ).
- The automated proof of URL resolution is the new test in Task 3 (captures the exact `fileUrl`/`fileSizeBytes` passed to `ModelDownloadManager`).

**Verify:** HEAD request returns 200/302 with matching content-length (or a stop-and-report message if the mirror is missing).
**Done:** Resolved URL `https://huggingface.co/warped-community/gemma-4-E4B-it/resolve/main/gemma-4-E4B-it.litertlm` proven reachable with the expected size.

### Task 3: Update allowlist tests (counts, flags, fixtures) + add download-URL test
**Files:**
- `app/src/test/java/com/warped/data/repository/ModelAllowlistTest.kt`
- NEW `app/src/test/java/com/warped/ui/huggingface/CatalogDownloadUrlTest.kt` (create parent dirs as needed)

**Action:**
- `ModelAllowlistTest`: update `hasSize(3)` → `hasSize(4)` in both `shipped asset parses` and `repository exposes capability queries` tests.
- Add E4B entry assertions to `shipped asset parses`: name `gemma-4-E4B-it`, displayName `Gemma 4 E4B IT`, modelFile `gemma-4-E4B-it.litertlm`, sizeInBytes `3659530240L`, taskTypes contains `chat`.
- Extend the flag maps in `shipped asset flags are verified-only` with `gemma-4-E4B-it`: text/vision/audio `true`, speculativeDecoding `true`, thinking `true` (function-calling/extendedContext/mtpSupport stay `false` via the existing loop assertions — no change needed there).
- `repository exposes capability queries`: add `findByModelFile("gemma-4-E4B-it.litertlm")?.name == "gemma-4-E4B-it"`, `supportsThinking("gemma-4-E4B-it")` true, `supportsFunctionCalling` false, `supportsSpeculativeDecoding` true.
- `effectiveCapabilities prefers allowlist and gates thinking`: the current "unlisted model" fixture uses `local("gemma-4-E4B-it", "gemma-4-E4B-it.litertlm")` — it is now allowlisted, so replace it with a genuinely unlisted fixture (e.g. `local("some-future-model", "some-future-model.litertlm")` with default stored caps; keep asserting `reasoning` false) AND add an allowlisted-E4B assertion block (`reasoning` true, `vision` true, `audio` true, `tools` false). Check the `LocalModel` constructor signature in `app/src/main/java/com/warped/domain/model/` before writing — the existing test builds it with named args (`capabilities` defaults apply); mirror that.
- NEW `CatalogDownloadUrlTest`: mock `ModelAllowlistRepository` (return `parseModelAllowlist` of the shipped asset for `models`) and `ModelDownloadManager` with mockk (`every { downloadManager.downloadStates } returns MutableStateFlow(emptyMap())`, `every { downloadManager.startDownload(...) } returns Unit` with a `slot<String>()` capturing `fileUrl` and `slot<Long>()` for `fileSizeBytes`). Call `CatalogViewModel(repo, downloadManager).startDownload(e4bEntry)` and assert captured `fileUrl == "https://huggingface.co/warped-community/gemma-4-E4B-it/resolve/main/gemma-4-E4B-it.litertlm"`, `fileSizeBytes == 3659530240L`, `modelId == "warped-community/gemma-4-E4B-it/gemma-4-E4B-it.litertlm"`, `isGated == false`. `startDownload` signature: `ModelDownloadManager.kt:67-73` (`modelId, fileName, fileUrl, fileSizeBytes, isGated=false`). Follow existing test deps (JUnit5 `@Test`, Truth, mockk — see `ModelAllowlistTest.kt:1-9`).

**Verify:** `./gradlew :app:testDebugUnitTest --tests "com.warped.data.repository.ModelAllowlistTest" --tests "com.warped.ui.huggingface.CatalogDownloadUrlTest"`
**Done:** Both test classes pass; no test in the module still asserts a 3-entry catalog (`grep -rn "hasSize(3)" app/src/test` returns nothing).

## Verification (full)
- `./gradlew :app:assembleDebug` succeeds.
- `./gradlew :app:testDebugUnitTest` — full suite green.

## must_haves
- truths:
  - "Gemma 4 E4B IT appears in the app model catalog alongside the existing 3 entries"
  - "E4B download resolves to the warped-community mirror URL with the verified 3.41 GiB size"
  - "E4B shows Thinking/Vision/Audio capability, and no Tools badge"
  - "Full unit test suite passes with the 4-entry catalog"
- artifacts:
  - path: "app/src/main/assets/model_allowlist.json"
    provides: "4-entry catalog with gemma-4-E4B-it"
    contains: "gemma-4-E4B-it.litertlm"
  - path: "app/src/test/java/com/warped/data/repository/ModelAllowlistTest.kt"
    provides: "Updated 4-entry catalog assertions"
    contains: "hasSize(4)"
  - path: "app/src/test/java/com/warped/ui/huggingface/CatalogDownloadUrlTest.kt"
    provides: "Download URL + size proof for E4B entry"
    contains: "warped-community/gemma-4-E4B-it"
- key_links:
  - from: "app/src/main/assets/model_allowlist.json"
    to: "ModelAllowlistRepository.models"
    via: "asset parse at runtime"
    pattern: "model_allowlist\.json"
  - from: "CatalogViewModel.startDownload"
    to: "huggingface.co/warped-community"
    via: "entry-driven URL construction"
    pattern: "resolve/main/"
