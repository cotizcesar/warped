---
audit_acknowledged:
  milestone: v2.5
  at: 2026-10-01
  status: unknown
---

# SUMMARY — Source Card Density Pass (OgSourceCard + CompactSourceCard)

**Date:** 2026-09-30
**Plan:** `.planning/quick/20260930-source-card-density/PLAN.md`
**Scope:** Single production file, 5 user-locked density edits, no behavior/tap/data changes.

## What changed

File: `app/src/main/java/com/warped/ui/chat/components/OgSourceCard.kt` (only production file touched)

Both `OgSourceCard` and `CompactSourceCard` text columns reordered from title → URL → desc to **title → desc → URL**, with tight lineHeights and top alignment:

1. **lineHeight = fontSize** on all 6 Text composables — title `14.sp`, URL/desc `12.sp` (tightest safe values; below fontSize clips glyphs).
2. **URL last + dimmer** — URL Text block moved below the `if (desc != null)` block; color `onSurfaceVariant.copy(alpha = 0.7f)` (alpha-reduction branch; no dimmer gray exists in palette and no new hexes allowed).
3. **Title maxLines 2 → 1**, ellipsis kept (both cards).
4. **Description untouched** — maxLines 2 + ellipsis, only gained `lineHeight = 12.sp`.
5. **Thumb 64dp → 48dp** (`OgThumb`, one shared edit covering both cards + preview-sheet header — accepted collateral) **+ top alignment** — both Rows `CenterVertically → Top`, both text Columns `Arrangement.Center → Top` (companion required so title pins to thumb top).
6. Both card KDocs updated ("max 2 lines" → "max 1 line", URL-last order); `OgThumb` KDoc 64dp → 48dp.

Untouched: taps/preview/browser intents, data/fetch/fallback helpers, badges, container colors, spacers (2.dp), sheet, carousel grid.

## Verification

- `./gradlew :app:assembleDebug` — **BUILD SUCCESSFUL**
- `./gradlew :app:testDebugUnitTest` — **BUILD SUCCESSFUL**, zero failures/errors across all suites
- `OgSourceCardHelpersTest` — **14/14 pass** (pure helpers `ogDisplayTitle`, `ogDisplayDescription`, `ogHostOf`, `gatedHttpImageUrl`, `faviconFallbackUrl` untouched, covered as-is)
- No new test file: Compose layout params (lineHeight/maxLines/alignment) are not JVM-unit-testable; `OgSourceCardTapTest` (androidTest) unaffected — taps unchanged.

## Commit

- `9e91af60` — `feat(quick-source-card-density): apply 5 locked density edits to source cards` (only `OgSourceCard.kt` staged; build/IDE artifacts left uncommitted)

## Deviations

None — plan executed exactly as written.

## Known limitation (from plan)

Density *feel* cannot be verified here — no adb/device attached. Confirm visually on-device after flashing; if `lineHeight = fontSize` clips descenders on any OEM font, bump desc/URL to `13.sp`.
