---
phase: 58-opengraph-thumbnails
plan: 02
subsystem: grounding-cards-ui
tags: [opengraph, coil, thumbnails, fuentes, preview-sheet]
dependency_graph:
  requires: [58-01-og-backbone]
  provides: [coil-singleton, og-source-card, browser-intents-gate, sheet-og-header]
  affects: [chat-ui, future-visual-polish]
tech_stack:
  added: [coil3-compose-3.4.0, coil3-network-okhttp-3.4.0]
  patterns: [singleton-imageloader-factory, shimmer-behind-thumb, silent-text-only-fallback, render-side-http-regate]
key_files:
  created:
    - app/src/main/java/com/warped/ui/chat/components/OgSourceCard.kt
    - app/src/main/java/com/warped/ui/chat/components/BrowserIntents.kt
    - app/src/test/java/com/warped/ui/chat/components/OgSourceCardHelpersTest.kt
  modified:
    - gradle/libs.versions.toml
    - app/build.gradle.kts
    - app/src/main/java/com/warped/WarpedApplication.kt
    - app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt
    - app/src/main/java/com/warped/ui/chat/components/SourcePreviewSheet.kt
    - app/src/main/java/com/warped/ui/theme/Color.kt
decisions:
  - "Coil pinned at 3.4.0 (not 3.6.3): newest line compiling against compileSdk 35 — 3.5.0 raised compile SDK to 36, 3.6.x to 37 (changelog-confirmed); repo holds 35"
  - "OG card/container hex tokens live in Color.kt (OgCardDark/OgShimmer) so the card and sheet files carry zero 0xFF literals for the color grep gate"
  - "openUrlInBrowser returns Boolean so the sheet dismisses only on launched intents — exact Phase 53 behavior preserved through the shared gate"
metrics:
  duration: "~45 min"
  completed: "2026-09-29"
---

# Phase 58 Plan 02: Coil Cards + Sheet Header Summary

**One-liner:** Coil 3.4.0 singleton (25% memory, 50MB og_thumbnails disk, own OkHttp) feeding OgSourceCard thumbnails in Fuentes and an OG header in the preview sheet — 493/493 unit tests green, debug + release green.

## Objective Achieved

OG-02/OG-03 visible feature complete: every grounded ok source renders a rich thumbnail card (64dp Coil thumb, 1-line title, 2-line description, [N] badge + open icon) per the locked layout and UI-SPEC 7/7 contract; tap opens the preview sheet, which now shows the OG header (thumb + title + badge + URL) above the extracted text. Sources without images render silent text-only cards; omitida rows stay struck text with no card.

## Tasks Completed

| # | Name | Commit | Files |
|---|------|--------|-------|
| 1 | Coil 3.4.0 dependency + singleton ImageLoader (OG-02 foundation) | 04616258 | libs.versions.toml, app/build.gradle.kts, WarpedApplication.kt |
| 2 | OgSourceCard + Fuentes wiring + sheet OG header (OG-02, OG-03) | a05bd8e2 | OgSourceCard.kt, BrowserIntents.kt, MessageBubble.kt, SourcePreviewSheet.kt, Color.kt, OgSourceCardHelpersTest.kt |

## Key Links Verified

- `OgSourceCard` → Coil singleton: plain `AsyncImage` (never SubcomposeAsyncImage) resolves the default singleton loader — no per-card loaders anywhere (grep-confirmed by construction: single `newImageLoader` override).
- `MessageBubble` Fuentes → `OgSourceCard`/`previewSource`: ok items render cards in fetch-block order under the existing Sources header; card tap sets `previewSource`/`previewNumber` opening `SourcePreviewSheet`; open icon fires `openUrlInBrowser` and consumes the tap.
- `SourcePreviewSheet` → OG header: thumb + 16sp Semibold 1-line title + [N] badge + 14sp primary 1-line URL above the existing divider; text-only header when image absent/failed; sheet body/action/empty-extract paths byte-identical.
- `BrowserIntents.openUrlInBrowser` ← all three callers: card icon, sheet button (via MessageBubble lambda, dismisses only on `true`), legacy path — one allowlist + dual catch, no drift.

## Decisions Made

- **Coil 3.4.0 ceiling over plan's 3.6.3**: 3.6.3 AAR metadata requires compileSdk 37; repo holds compileSdk 35 (android-37 platform unavailable stable). Changelog confirms 3.5.0 → compile SDK 36, 3.6.0 → 37, so 3.4.0 is the newest compatible line. Documented as a ceiling comment in the catalog.
- **`toOkioPath` for DiskCache directory**: Coil 3.4.0 `DiskCache.Builder.directory()` takes an Okio `Path`, not `java.io.File` — one-line conversion, no new dependency (Okio rides transitively with Coil).
- **Color tokens in Color.kt**: dark container 2B2B29 and shimmer gray live as `OgCardDark`/`OgShimmer` tokens (no matching token existed) so the gated UI files carry zero hex literals; light container reuses M3 `surfaceVariant` per UI-SPEC.
- **Boolean-returning browser gate**: preserves the exact Phase 53 semantic (sheet closes only when the browser intent launched; toasts keep the sheet open) while letting the card icon ignore the result.
- **9 JVM unit tests added** (beyond plan minimum): pure helpers `ogDisplayTitle`/`ogHostOf`/`gatedHttpImageUrl` are Android-free (`java.net.URI`), so the T-58-06 re-gate and title-fallback chain are pinned without instrumentation.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Coil 3.6.3 requires compileSdk 37, repo holds 35**
- **Found during:** Task 1 `assembleDebug` (`checkDebugAarMetadata`: 16 issues, coil-compose-android needs API 37)
- **Issue:** Plan-pinned 3.6.3 cannot compile against android-35; bumping compileSdk is out of scope (repo HOLDs confirm android-37 platform unavailable stable).
- **Fix:** Pinned Coil to 3.4.0 after changelog verification (3.5.0 → SDK 36, 3.6.0 → SDK 37); recorded the ceiling rationale in `libs.versions.toml`.
- **Files modified:** gradle/libs.versions.toml
- **Commit:** 04616258

**2. [Rule 3 - Blocking] DiskCache directory expects Okio Path in Coil 3.4.0**
- **Found during:** Task 1 `compileDebugKotlin` (argument type mismatch: File vs Path)
- **Issue:** `DiskCache.Builder().directory()` takes `okio.Path` in the Coil 3 line.
- **Fix:** `.toOkioPath()` conversion on the `cacheDir.resolve(...)` file; no new dependency.
- **Files modified:** WarpedApplication.kt
- **Commit:** 04616258

**3. [Rule 2 - Critical] Card/sheet hex literals would trip the color grep gate**
- **Found during:** Task 2 implementation (gate pattern matches any 0xFF literal containing 8/9/B — including the locked 2B2B29 itself)
- **Issue:** Hardcoding the locked container color inside `OgSourceCard.kt` (as the plan text suggests) fails the plan's own grep gate.
- **Fix:** Added `OgCardDark`/`OgShimmer` tokens to `Color.kt` (outside grep scope); card/sheet reference tokens and M3 scheme colors only — zero `0xFF` literals in either file, gate clean.
- **Files modified:** Color.kt, OgSourceCard.kt, SourcePreviewSheet.kt
- **Commit:** a05bd8e2

## Auth Gates

None — no external services touched (Coil artifacts resolved from Maven Central via the existing Gradle cache configuration).

## Test Results

- `OgSourceCardHelpersTest`: 9 tests green (host strip/www/unparseable, http(s) accept incl. uppercase scheme, data:/javascript:/ftp:/file: rejection, null/blank/schemeless rejection, title-prefer + host fallback never-empty).
- Full suite: **493/493 green, 0 failures** (484 pre-existing + 9 new).
- `assembleDebug` + `assembleRelease` green (R8/minify path with Coil consumer rules — no text-only-in-release failure mode).
- Color grep gate on `OgSourceCard.kt` + `SourcePreviewSheet.kt`: clean (exit 1, zero matches).
- On-device backstop (200-char title / 500-char description ellipsis): visual confirmation deferred — human-verify at execution per plan.

## Threat Flags

None — all STRIDE mitigations from the plan's threat model are implemented in-plan:
- T-58-05 (endpoint auth disclosure): Coil's bare `OkHttpClient` (redirects only, no interceptors) is constructed inside `newImageLoader`; the app authed client is never referenced.
- T-58-06 (image spoofing): `gatedHttpImageUrl` re-gates at render in both card and sheet; only http(s) strings reach `AsyncImage` (unit-pinned).
- T-58-07 (OG XSS): title/description/URL render via plain Compose `Text()` only — no `Html.fromHtml`, no WebView.
- T-58-08 (browser spoofing): single `openUrlInBrowser` gate (http/https allowlist + `ActivityNotFoundException`/`SecurityException` dual catch + existing toast copy) serving all three call sites.
- T-58-SC: Coil 3.4.0 AAR artifacts from Maven Central (same legitimacy basis as the RESEARCH.md 3.6.3 audit — group/artifact identity unchanged, version only lowered); no npm/postinstall surface.

## Known Stubs

None — every ok source renders a card (image or text-only by construction); omitida/zero-source states preserve Phase 50/53 behavior.

## Self-Check: PASSED

- All created files exist on disk (OgSourceCard.kt, BrowserIntents.kt, OgSourceCardHelpersTest.kt).
- Both task commits exist in git log (04616258, a05bd8e2).
- Full suite 493/493 green, release assemble green, color gate clean (verified above).
- No unintended file deletions in either task commit (source-only staging; build-output churn left unstaged).
