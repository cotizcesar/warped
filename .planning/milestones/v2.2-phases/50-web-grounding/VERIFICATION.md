---
status: gaps_found
score: "all automated gates pass; on-device visual smoke deferred"
---
# Phase 50 (Web Grounding) — Verification

**Date:** 2026-09-28
**Plans:** 50-01, 50-02 (waves 1–2, sequential)
**Commits:** `86f3bcf` (50-01), `6ec5b2e` (50-02)
**Transition:** skipped (`--no-transition`; autonomous mode owns STATE/ROADMAP updates)

## Plan gates (all PASS)

### 50-01 — Grounding pipeline (detect → bounded fetch → inject → model-only fallback)
- [x] `:app:testDebugUnitTest --tests "com.warped.data.grounding.*"` — 20/20 pass (UrlDetector 6, HtmlToTextExtractor 5, WebContextSanitizer 5, GroundingPrompt 4)
- [x] Jsoup-leak gate (`grep -rn "jsoup\|Jsoup" app/src/main/java`): zero matches (exit 1)
- [x] Fetcher-bounds gate (`65536`, `connectTimeout(8`, `followRedirects(false)`, `fun cancel()`, Chrome/120.0 Mobile UA): all present in WebPageFetcher.kt
- [x] Unbounded-read gate (`grep body.string()`): zero matches (one self-inflicted KDoc hit fixed by rewording)
- [x] `bash scripts/audit-dependencies.sh`: green (no banned direct deps, no pre-release artifacts)
- [x] `EntityMappers.kt`: zero diff (no Room migration)
- [x] `fetcher.cancel()` + `isFetchingWeb` wiring in ChatViewModel: present (stopGeneration, CR-02 pre-cancel, turn finally)
- [x] `./gradlew :app:assembleDebug`: BUILD SUCCESSFUL

### 50-02 — Grounding surfaces (chip, Fuentes, banner, toggle)
- [x] `:app:testDebugUnitTest --tests "com.warped.ui.settings.SettingsGroundingToggleTest"` — 2/2 pass (write-through + default ON)
- [x] Settings copy gate (`Text("Web"` between Data card and Display header + `Grounding web`): present
- [x] Chat surfaces gate (`Leyendo página` in ChatScreen.kt, `Fuentes` + offline/failure copies in MessageBubble.kt): present with exact copy
- [x] `./gradlew :app:assembleDebug`: BUILD SUCCESSFUL

### Regression gate
- [x] `:app:testDebugUnitTest` full suite with `--rerun-tasks`: **224 tests, 0 failures, 0 errors, 0 skipped**

## Must-haves review (50-01)
- First-URL triggers exactly one fetch; 2nd+ ignored — verified by unit test (`first URL wins, second ignored`)
- Bounded fetch (64KB cap, 3 redirects, 8/10/20s timeouts, browser UA, html/plain only) — verified by grep gate + code review
- Cancelable via Stop/new-turn pre-cancel on Dispatchers.IO — verified by code (fetcher.cancel() in stopGeneration + CR-02 block; CancellationException rethrown)
- Offline short-circuits without a socket — verified by code (connectivity check precedes newCall)
- 4000-char line-boundary truncation with `[truncado]` marker — verified by unit test
- Hijack sanitization + delimiter escaping — verified by unit test (ES/EN patterns, benign imperative kept)
- Zero-byte failure injection; 64KB-cap abort still grounds — verified by code (FETCH_FAILED vs Grounded paths)
- Current-turn prefix augmentation for local + remote; LlmModelHelper unchanged; Room keeps originals — verified by code + EntityMappers zero-diff
- Citation/system-prompt contract (`Nunca inventes URLs`) — verified by unit test
- Toggle persisted, default ON; OFF skips detection — verified by code (webGroundingEnabled snapshot gates UrlDetector call) + toggle test
- Zero new dependencies — verified by audit script + Jsoup gate

## Must-haves review (50-02)
- Paste URL → chip → grounded answer with sources — pipeline proven; end-to-end needs device (gap 1)
- Toggle OFF = pre-50 behavior, effective next message — verified by code (snapshot read at turn start, no retroactive path)
- Chip transient above input, spinner + copy + cancel announcement, no error variant — verified by code; visual/transient behavior needs device (gap 1)
- Single chip, fixed short copy — verified by code (one fetch per message, `isFetchingWeb` single flag)
- Fuentes under answer, `[1] {url}`, wrap-no-truncate, zero-sources-renders-nothing — verified by code; visual needs device (gap 1)
- Body markers plain text; only fuente URLs accent — verified by code (primary color only on fuente items)
- Banner above message, Info + muted text, offline vs failure copy exact — verified by grep; visual needs device (gap 1)
- Banner UI-rendered, not Snackbar/dismissible, scrolls with message — verified by code (inside MessageBubble Column)
- Settings order Data → Web → Display → App → Security, 12dp/16dp, default ON — verified by code
- No new component library; muted banner, accent only on spinner/links/toggle — verified by code
- 4-size/2-weight type envelope, 4-multiple spacing — verified by code (12/14sp, 16/8/4dp)

## Gaps / human needed
1. **On-device grounding smoke (human_needed):** paste URL → see `Leyendo página…` → grounded answer with Fuentes; airplane-mode turn → offline banner; bad URL → failure banner; toggle OFF → pre-50 behavior. Not runnable in this environment (no device/emulator). Same deferral precedent as Phase 49 device smoke; required before regarding WEB-06 fully closed.
2. **Live-fetch behaviors (accepted):** redirect chains, 64KB-cap truncation marker on real pages, timeout values, and AuthInterceptor-strip leak-proofing are code-reviewed and gate-checked but never exercised against a live server here. Covered by the same device smoke (item 1) at release UAT.

## Verdict
**gaps_found** — all automated gates pass (224/224 unit, assembleDebug, dependency audit, all plan grep gates); on-device visual + live-fetch smoke (items 1–2) needs a human with a device. No code gaps.
