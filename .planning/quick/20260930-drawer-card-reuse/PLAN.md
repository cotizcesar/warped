# Quick Plan — Drawer Card Reuse (same CompactSourceCard in chat + drawer)

**Locked decision (user):** the all-sources drawer rows become the SAME
`CompactSourceCard` composable used in the chat carousel (shared code, not
copy-paste). Drawer presents them full-width stacked vertically vs the
carousel's fixed-width horizontal items — the composable supports both width
modes via a width param. Zero visual drift in chat.

**Verified in code before planning:**
- `OgSourceCard.kt` — `CompactSourceCard` (lines 184–279): fixed
  `.width(272.dp)`, whole-card tap → `onPreview` (sheet), nested thumb
  `Box` with its own tap → `onOpenBrowser`, title maxLines 1 + desc (≤2) +
  URL-last ordering, `Alignment.Top` / `Arrangement.Top`, explicit
  `lineHeight 14.sp/12.sp`, `cd_preview_source` a11y. Text-only fallback via
  silent thumb collapse (`showThumb` gate). Omitida never reaches it.
- `AllSourcesSheet.kt` — private `AllSourcesRow` (lines 136–216, sole call
  site line 103) is a near-duplicate with real drift: whole-row tap →
  `onOpenBrowser(browserTarget(source))` directly (NO preview sheet),
  `cd_open_in_browser` a11y, `CenterVertically`/`Center` alignment, title
  maxLines 2 + URL-then-desc ordering, plain `OgThumb` (no separate thumb
  tap), no `number` param, no lineHeight. Omitida branch (lines 107–121) is
  struck `Text` with no tap — matches chat honesty rules, KEEP byte-identical.
- `MessageBubble.kt` — carousel host (line 338: `CompactSourceCard` in
  `LazyRow`, body → `previewSource`/`previewNumber` state, thumb → guarded
  `openUrlInBrowser`) and drawer host (lines 396–411: `AllSourcesSheet`
  with `onOpenBrowser` dismiss-only-on-launch). Preview-sheet state already
  lives in `MessageBubble` and is shared by both paths.
- NOTE: `OgSourceCard` (the older full-width composable, lines 76–168) is
  prod-unused — only referenced by `OgSourceCardTapTest`. Out of scope:
  leave it untouched.
- Tests: `AllSourcesSheetTest` (JVM mappers — unaffected), `OgSourceCardTapTest`
  (androidTest, covers `OgSourceCard` taps only, NOT `CompactSourceCard`).

**Out of scope:** data/fetch/OG logic, sheet open/close mechanics, download
path, colors/typography changes.

## Task 1 — Parameterize CompactSourceCard for both width modes

**Files:** `app/src/main/java/com/warped/ui/chat/components/OgSourceCard.kt`

1. Add a width param to `CompactSourceCard`, e.g.
   `cardWidth: Dp? = 272.dp` — non-null applies `Modifier.width(cardWidth)`,
   null applies `Modifier.fillMaxWidth()`. Default keeps the chat call site
   byte-identical with zero edits to `MessageBubble.kt`'s carousel block.
2. Restructure the `Surface` modifier chain so the caller `modifier` is
   honored first, then the width directive, then the existing
   clip/clickable/semantics (unchanged order otherwise).
3. Change NOTHING else in the composable: same tap rules (body → sheet,
   thumb → browser), same title/desc/URL ordering and line caps, same
   container colors, same text-only collapse, same a11y strings.

**Done:** `CompactSourceCard` compiles with a width param defaulting to
272.dp; carousel call site untouched and visually unchanged.
**Verify:** `grep -n "cardWidth" OgSourceCard.kt` shows the param + both
branches; `./gradlew :app:assembleDebug` passes.

## Task 2 — Drawer reuses CompactSourceCard; delete AllSourcesRow

**Files:** `app/src/main/java/com/warped/ui/chat/components/AllSourcesSheet.kt`,
`app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt`

1. Delete the private `AllSourcesRow` entirely (no dead code left — the
   function, its KDoc, and any now-unused imports such as
   `IntrinsicSize`/`fillMaxHeight` if unreferenced elsewhere in the file).
2. Replace the OK branch of the drawer loop with `CompactSourceCard(
   source = source, number = index + 1, cardWidth = null,
   modifier = Modifier.fillMaxWidth(), onPreview = { ... },
   onOpenBrowser = { ... })`. Fetch-block order is preserved (loop already
   iterates in order), so `index + 1` matches chat `[N]` numbering.
3. **Tap-behavior change (intended, per locked decision — taps identical
   across hosts):** drawer OK rows switch from direct-browser-tap to
   card-body → preview sheet + thumb → browser. Add
   `onPreview: (source: GroundedSource, number: Int) -> Unit` to
   `AllSourcesSheet` params; thumb path keeps the existing guarded
   `onOpenBrowser` (dismiss-only-on-launch, unchanged).
4. In `MessageBubble.kt`'s `showAllSources` host: pass
   `onPreview = { src, n -> previewSource = src; previewNumber = n }`
   reusing the existing preview-sheet state. Preferred: dismiss the drawer
   (`showAllSources = false`) when a preview opens to avoid stacked
   `ModalBottomSheet`s; if stacking proves problematic in review, keep the
   drawer open beneath — executor's call, document the choice.
5. KEEP byte-identical: omitida struck-`Text` branch, drawer title/header,
   spacing (`Arrangement.spacedBy(8.dp)`), legacy `GroundedSource(url=...)`
   fallback construction, `shouldShowViewAll` rule, text-only fallback
   (free via the shared composable).

**Done:** `AllSourcesRow` no longer exists; drawer OK rows are
`CompactSourceCard` full-width with card→sheet / thumb→browser taps;
omitida rows struck and untapped; no unused imports.
**Verify:** `grep -c "AllSourcesRow" AllSourcesSheet.kt` returns 0 (use
`grep -v '^#'` hygiene — file has no `#` comments, plain `grep -c` is fine);
`grep -c "CompactSourceCard" AllSourcesSheet.kt` is ≥ 1;
`./gradlew :app:assembleDebug` passes.

## Task 3 — Tests + full verification

**Files:** `app/src/androidTest/java/com/warped/ui/chat/components/OgSourceCardTapTest.kt`
(or new `CompactSourceCardWidthTest.kt`), existing suites untouched otherwise.

1. Keep `AllSourcesSheetTest` green (mapper-level; behavior it pins —
   view-all rule, omitida non-clickable, fetch-block order — is unchanged).
2. Add cheap coverage for the shared composable in both width modes IF cheap
   in androidTest (FakeImageLoaderEngine pattern from `OgSourceCardTapTest`
   applies directly): full-width card body → preview callback, thumb →
   browser callback, no-preview-on-thumb. If the androidTest harness proves
   expensive here, skip the new test and rely on the grep pin below +
   layout-parity reasoning (single shared composable = parity by
   construction), documented in the summary.
3. Pin shared-composable usage with a source gate (cheap, runs everywhere):
   `AllSourcesRow` absent AND `CompactSourceCard` present in
   `AllSourcesSheet.kt` (see Task 2 verify commands).
4. Run `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest`
   (compiles both unit + androidTest sources) then the full
   `./gradlew :app:testDebugUnitTest` — all green.

**Done:** unit suite fully green; drawer-reuse pinned by source gate (+ new
tap test if it was cheap); androidTest sources compile.
**Verify:** `./gradlew :app:testDebugUnitTest` exits 0; the two `grep`
gates from Task 2 pass.

## Honest note

Visual parity (drawer cards pixel-matching chat cards) needs an on-device
screenshot comparison — no `adb` in this environment, so parity is argued
by construction (one shared composable, only the width modifier differs)
plus the green suite, not by pixels.
