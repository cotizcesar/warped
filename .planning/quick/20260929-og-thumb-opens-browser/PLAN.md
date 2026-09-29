# Quick Plan: OG thumbnail tap opens browser directly

## Objective

Tapping the **thumbnail** on an `OgSourceCard` opens the page in the browser
directly (guarded `ACTION_VIEW`), never the preview sheet. Tapping the **rest
of the card** still opens the preview sheet. Open-icon behavior unchanged.

**Locked user decision:** thumbnail → existing `onOpenBrowser` callback (reuse,
no duplicate intent code); card body → `onPreview` (sheet). No scope reduction,
no visual changes.

## Context

- Card: `app/src/main/java/com/warped/ui/chat/components/OgSourceCard.kt`
  (lines 90–113: `Surface` with card-level `.clickable(onClick = onPreview)`;
  lines 106–111: `OgThumb` call site with `contentDescription = null`;
  line 162: open `IconButton(onClick = { onOpenBrowser(source.url) })` — the
  callback to reuse).
- Shared thumb: `OgThumb` (same file, lines 182–216) is also used by the
  preview-sheet header (`SourcePreviewSheet.kt` line 87, passes
  `contentDescription = null`). Clickability must be added **at the card call
  site only** — never inside `OgThumb` itself — or the sheet header thumb would
  also fire the browser intent (out of scope).
- Wiring: `MessageBubble.kt` lines 252–259 passes
  `onOpenBrowser = { url -> openUrlInBrowser(context, url) }` (guarded gate in
  `BrowserIntents`, allowlist + dual-catch + toasts). Untouched by this change.
- Existing tests: JVM helpers in
  `app/src/test/java/com/warped/ui/chat/components/OgSourceCardHelpersTest.kt`
  (pure functions only — tap routing cannot be tested there). Compose UI test
  pattern: `app/src/androidTest/java/com/warped/ui/chat/components/MarkdownTextTest.kt`
  (`createComposeRule`, `onNodeWith*`).

## Tasks

### Task 1: Thumbnail gets its own clickable → onOpenBrowser

**Files:** `app/src/main/java/com/warped/ui/chat/components/OgSourceCard.kt` (only)

**Action:**
- At the card's `OgThumb` call site (currently lines 106–111), wrap `OgThumb`
  in a `Box` carrying `.clickable(role = Role.Button, onClick = { onOpenBrowser(source.url) })`
  plus `.semantics { contentDescription = "Open in browser" }` (English-only
  product). Reuse the exact `onOpenBrowser(source.url)` expression from the
  open icon — no new intent code, no new callback parameter.
- Keep passing `contentDescription = null` into `OgThumb`/`AsyncImage` so the
  "Open in browser" semantics live on exactly one node (no duplicate/conflicting
  descriptions).
- Do NOT touch `OgThumb` itself (sheet header reuse), the card-level
  `.clickable(onClick = onPreview)` + `"Source preview $number: …"`
  description, the open `IconButton`, `MessageBubble.kt`, `BrowserIntents`, or
  any visual (sizes, colors, padding, shimmer).
- Nested-clickable resolution (verify by code reasoning while implementing):
  the inner thumb clickable consumes the tap, so the outer card `onPreview`
  must NOT fire on thumb taps; taps anywhere else on the card still fire
  `onPreview`. Keep the default ripple/indication.

**Verify:** `./gradlew :app:assembleDebug` passes.

**Done:** Thumb tap routes to `onOpenBrowser(source.url)`; body tap routes to
`onPreview`; icon, sheet wiring, and card description unchanged; `OgThumb`
composable itself has no clickable modifier.

### Task 2: Tap-routing widget tests (thumb → browser, body → sheet)

**Files:**
- New: `app/src/androidTest/java/com/warped/ui/chat/components/OgSourceCardTapTest.kt`
- Touch nothing else.

**Action:**
- Model on `MarkdownTextTest.kt` (`createComposeRule`, `setContent` with a
  `GroundedSource(url = "https://example.com/x", ogImageUrl = "https://…")`).
- Deterministic thumb presence: `AsyncImage` (Coil) will try to load the fake
  URL and `onError` collapses the thumb slot — and `performClick` waits for
  idle, so the node may vanish before the tap. Check the version catalog for a
  Coil3 test fake first; install a test `ImageLoader` that deterministically
  succeeds (1×1 bitmap) or errors per-case. If no Coil3 test utility is
  available, control `mainClock.autoAdvance` so assertions/taps run before
  async load settles. Do NOT change production code to accommodate the test.
- Cases:
  1. `onNodeWithContentDescription("Open in browser").performClick()` →
     browser callback fired with `source.url`, preview callback NOT fired.
  2. Click on the title/body text node → preview callback fired, browser
     callback NOT fired.
  3. Open-icon node (`"Open source $number in browser"`) still routes to the
     browser callback (guard against regressions of existing behavior).
- Extend `OgSourceCardHelpersTest.kt` only if a pure helper changed (it
  shouldn't — no helper changes expected).

**Verify:** `./gradlew :app:testDebugUnitTest` fully green (JVM suite). The new
`androidTest` tap tests run under `connectedAndroidTest`, which needs a
device/emulator.

**Done:** Thumb-tap→browser (not sheet) and body-tap→sheet are covered;
guarded-intent behavior itself untouched (no `BrowserIntents` changes, so its
existing coverage still applies).

## Verification (honest)

- `./gradlew :app:assembleDebug` + full `./gradlew :app:testDebugUnitTest`
  green — required.
- Tap behavior needs **on-device confirmation** (no adb in this environment):
  `connectedAndroidTest` for the new tap test, plus a manual thumb-tap vs
  body-tap check on a real device, cannot run here. Say so in the summary.

## Out of scope

Sheet contents, `BrowserIntents` gate logic (allowlist/dual-catch/toasts),
card visuals, Coil config/cache, `MessageBubble.kt` wiring.
