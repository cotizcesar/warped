# Summary: OG thumbnail tap opens browser directly

**Status:** COMPLETE (implementation + compile verification; on-device tap run pending — no adb in this environment)
**Date:** 2026-09-29
**Plan:** `.planning/quick/20260929-og-thumb-opens-browser/PLAN.md`

## What changed

Tapping the **thumbnail** on an `OgSourceCard` now fires the existing `onOpenBrowser(source.url)`
callback (guarded `ACTION_VIEW` via `BrowserIntents`), never the preview sheet. Tapping the
**rest of the card** still fires `onPreview` (sheet). Open-icon behavior unchanged.

- `OgSourceCard.kt` (card `OgThumb` call site only): wrapped `OgThumb` in a `Box` carrying
  `.clickable(role = Role.Button, onClick = { onOpenBrowser(source.url) })` plus
  `.semantics { contentDescription = "Open in browser" }`. Reuses the exact
  `onOpenBrowser(source.url)` expression from the open icon — no new intent code, no new
  callback parameter. `OgThumb` itself untouched, so the preview-sheet header reuse
  (`SourcePreviewSheet.kt`) gains no clickability. Card-level `onPreview` clickable,
  card description, icon, `MessageBubble.kt` wiring, and all visuals unchanged.
- New `OgSourceCardTapTest.kt` (androidTest): thumb→browser (not sheet), body/title→sheet
  (not browser), open-icon→browser regression guard.
- `gradle/libs.versions.toml` + `app/build.gradle.kts`: added `coil3-test` 3.4.0 as
  `androidTestImplementation` (test-only dependency, no production impact).

## Coil flake handling

`AsyncImage` would load the fake `ogImageUrl` over the network and `onError` would collapse
the thumb slot before `performClick` runs. The test installs an `ImageLoader` with Coil3's
`FakeImageLoaderEngine` (`.default(ColorImage(RED))`) via `SingletonImageLoader.setUnsafe()`
in `@Before` and `reset()` in `@After`, so every load succeeds deterministically and the
thumb stays mounted. `FakeImage()` was avoided (deprecated in 3.4.0); `setUnsafe`/`reset`
carry `@OptIn(DelicateCoilApi::class)`. Production code untouched for testability.

## Test results

- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL.
- `./gradlew :app:testDebugUnitTest` — BUILD SUCCESSFUL, fully green.
- `./gradlew :app:compileDebugAndroidTestKotlin` — BUILD SUCCESSFUL (includes new tap test;
  only warning is the pre-existing `createComposeRule` v1 deprecation shared with
  `MarkdownTextTest.kt`, kept deliberately to match the established pattern).
- `connectedAndroidTest` (new tap tests on device) — NOT RUN: no adb/device in this
  environment (`which adb` → absent).

## On-device tap note (required follow-up)

On a real device or emulator, run:
`./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.warped.ui.chat.components.OgSourceCardTapTest`
plus a manual check: thumb tap → browser opens, body tap → preview sheet opens.

## Commits

- `525ceb83` feat(thumb-opens-browser): thumbnail tap routes to onOpenBrowser
- `03567971` test(thumb-opens-browser): tap-routing tests with Coil fake engine

## Deviations

- None from plan scope. Two in-plan adaptations, both within Task 2's brief: used the
  Coil3 `coil-test` fake (plan's first choice) instead of the `mainClock` fallback, and
  used `ColorImage` over deprecated `FakeImage()` plus a `DelicateCoilApi` opt-in.
