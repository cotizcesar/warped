---
audit_acknowledged:
  milestone: v2.5
  at: 2026-10-01
  status: unknown
---

# Summary — Drawer Card Reuse (same CompactSourceCard in chat + drawer)

**Dir:** `.planning/quick/20260930-drawer-card-reuse/`
**Date:** 2026-09-30
**Status:** Complete (3/3 tasks)

The all-sources drawer rows are now the SAME `CompactSourceCard`
composable used in the chat carousel — shared code, not copy-paste.
Drawer presents them full-width stacked vertically; the carousel keeps its
fixed 272dp horizontal items. Chat visuals are byte-identical (default
`cardWidth = 272.dp`, carousel call site untouched).

## Commits

- `664a28ab` — `feat(drawer-card-reuse): parameterize CompactSourceCard
  for both width modes` (Task 1, `OgSourceCard.kt`)
- `2636084c` — `feat(drawer-card-reuse): drawer reuses CompactSourceCard,
  delete AllSourcesRow` (Task 2, `AllSourcesSheet.kt` + `MessageBubble.kt`)

## What changed

**Task 1 — `OgSourceCard.kt`:** `CompactSourceCard` gained
`cardWidth: Dp? = 272.dp`. Non-null applies `Modifier.width(cardWidth)`,
null applies `Modifier.fillMaxWidth()` — via `modifier.then(...)` so the
caller modifier is honored first, then the width directive, then the
unchanged clip/clickable/semantics chain. Nothing else touched: same tap
rules (body → sheet, thumb → browser), same title/desc/URL ordering and
line caps, same container colors, same text-only collapse, same a11y.

**Task 2 — `AllSourcesSheet.kt` + `MessageBubble.kt`:**

- Deleted the private `AllSourcesRow` entirely (function + KDoc) and
  removed 16 now-unused imports (`clickable`, `isSystemInDarkTheme`,
  `IntrinsicSize`, `Row`, `fillMaxHeight`, `width`, `clip`, `Role`,
  `contentDescription`, `semantics`, `TextOverflow`, `OgCardDark`,
  `Surface`, `getValue`/`mutableStateOf`/`remember`/`setValue`,
  `Alignment`). This also resolved the real drift: old row tapped
  straight to browser with title maxLines 2 + URL-then-desc ordering and
  `CenterVertically` alignment.
- Drawer OK rows are now `CompactSourceCard(source, number = index + 1,
  cardWidth = null, modifier = fillMaxWidth(), ...)`. Fetch-block order
  preserved, so `index + 1` matches chat `[N]` numbering.
- Intended tap-behavior change: drawer card body → preview sheet via new
  `AllSourcesSheet.onPreview: (GroundedSource, Int) -> Unit`; thumb →
  guarded `onOpenBrowser` (dismiss-only-on-launch, unchanged).
- `MessageBubble` host passes
  `onPreview = { src, n -> showAllSources = false; previewSource = src;
  previewNumber = n }`, reusing the existing preview-sheet state. Choice:
  the drawer is dismissed when a preview opens — no stacked
  `ModalBottomSheet`s.
- Kept byte-identical: omitida struck-`Text` branch, drawer title/header,
  `spacedBy(8.dp)`, legacy `GroundedSource(url=...)` fallback,
  `shouldShowViewAll` rule, text-only fallback (free via the shared
  composable).

**Task 3 — tests + verification:** no new test file (see decision below).
Source gates pass: `AllSourcesRow` count = 0, `CompactSourceCard`
count = 2 in `AllSourcesSheet.kt`.

## Test results

- `./gradlew :app:assembleDebug` — **PASS** (BUILD SUCCESSFUL)
- `./gradlew :app:compileDebugAndroidTestKotlin` — **PASS**
  (androidTest sources compile against the changed composables)
- `./gradlew :app:testDebugUnitTest` — **PASS** (BUILD SUCCESSFUL,
  full unit suite green, includes `AllSourcesSheetTest`)
- `./gradlew :app:assembleDebugAndroidTest` — **FAIL at dex stage,
  pre-existing, out of scope** (see below)

## Decisions

1. **Skipped the new androidTest tap test** (plan-authorized fallback).
   No `adb`/emulator exists in this environment, so a new compose test
   could be compiled but never executed — adding an unexecuted test
   risks breaking CI blind. Pin is by source gate (0 × `AllSourcesRow`,
   ≥1 × `CompactSourceCard`) + parity by construction (one shared
   composable, only the width modifier differs).
2. **Drawer dismissed when a preview opens** (`showAllSources = false`
   before setting preview state) to avoid stacked `ModalBottomSheet`s.
3. **Left the older full-width `OgSourceCard` untouched** (prod-unused,
   out of scope per plan).

## Pre-existing issues found (out of scope, not fixed)

1. **`assembleDebugAndroidTest` dex failure:** D8 rejects
   `MarkdownTextTest.class` — backtick test names with spaces
   (`renders bold text with FontWeight Bold span`) are illegal before
   DEX 040. From commit `e038f996`, untouched by this change.
   Compilation of all androidTest sources succeeds; only the dex step
   fails.
2. **`OgSourceCardTapTest` third test targets a phantom node:**
   `open icon still routes to browser callback` looks up
   `cd_open_source_browser` ("Open source 1 in browser"), which no
   production code references — `OgSourceCard` has no open icon. That
   test would fail on-device independent of this change.

## Visual parity note

Per plan: no on-device screenshot comparison possible here — parity is
argued by construction (single shared composable, only the width
modifier differs) plus the green suite, not by pixels.
