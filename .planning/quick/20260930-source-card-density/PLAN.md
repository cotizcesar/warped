# PLAN — Source Card Density Pass (OgSourceCard + CompactSourceCard)

Single-task UI density pass. No research needed — spec is user-locked below.
Out of scope: taps, data/fetch, badges (already removed), preview sheet, carousel grid.

## Current values (verified in code 2026-09-30)

File: `app/src/main/java/com/warped/ui/chat/components/OgSourceCard.kt`

| Element | Current |
|---|---|
| Title (`OgSourceCard` L133-140, `CompactSourceCard` L241-248) | 14.sp SemiBold, onSurface, maxLines 2, ellipsis, **no lineHeight param** |
| URL (L142-149, L250-257) | 12.sp Normal, onSurfaceVariant, maxLines 1, ellipsis, **no lineHeight param**, positioned SECOND (title → URL → desc) |
| Description (L152-160, L258-268) | 12.sp Normal, onSurfaceVariant, maxLines 2, ellipsis, **no lineHeight param** |
| Thumb (`OgThumb` L288-292) | `.size(64.dp)` |
| Row (L107-112, L213-218) | `verticalAlignment = Alignment.CenterVertically` |
| Column (L127-132, L235-240) | `verticalArrangement = Arrangement.Center` |
| Palette (`ui/theme/Color.kt`) | Only `OgCardDark` 0xFF2B2B29, `OgShimmer` 0xFF353534 — **no dimmer gray constant exists** |

## Locked spec (user directive, per-element — implement exactly)

1. **lineHeight × 0.5.** No explicit lineHeight exists today (Compose auto default). Locked dense equivalents — tightest safe values (lineHeight = fontSize; below fontSize clips glyphs): title `lineHeight = 14.sp`, URL `lineHeight = 12.sp`, description `lineHeight = 12.sp`. Apply to all 6 Text composables (3 per card × 2 cards).
2. **URL LAST + GRAYER.** Reorder text column to title → description → URL in both cards (move the URL Text block + its Spacer below the `if (desc != null)` block; keep 2.dp spacers). URL color: `MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)` — alpha-reduction branch, since no dimmer gray exists in palette and **no new hexes** are allowed.
3. **Title maxLines 2 → 1** + keep ellipsis (both cards).
4. **Description maxLines 2 + ellipsis (keep)** — no change, do not touch.
5. **Icon 64dp → 48dp AND top-aligned.** `OgThumb`: `.size(64.dp)` → `.size(48.dp)`. Both card Rows: `Alignment.CenterVertically` → `Alignment.Top`. Both text Columns: `Arrangement.Center` → `Arrangement.Top` (required companion — centered column inside a top-aligned row leaves text vertically centered against the shorter thumb; top arrangement keeps title pinned to thumb top).

KDoc on both cards mentions "Title (max 2 lines…)" — update those two lines to max 1 line to match.

## Tasks

### Task 1 (only task): Apply 5 density edits + build + full unit suite

**Files:**
- `app/src/main/java/com/warped/ui/chat/components/OgSourceCard.kt` (only production file)
- Existing tests only — helpers (`ogDisplayTitle`, `ogDisplayDescription`, `ogHostOf`, `gatedHttpImageUrl`, `faviconFallbackUrl`) are pure functions untouched by this pass, so `OgSourceCardHelpersTest` covers them as-is. No new test file: there are no text/ellipsis unit-test helpers for composables (Compose layout params are not JVM-unit-testable; the androidTest tap test `OgSourceCardTapTest` is unaffected — taps unchanged).

**Actions (all in `OgSourceCard.kt`, both `OgSourceCard` and `CompactSourceCard` unless noted):**
1. Title Text: add `lineHeight = 14.sp`, change `maxLines = 2` → `maxLines = 1` (keep ellipsis, fontSize, weight, color).
2. Description Text: add `lineHeight = 12.sp`; leave maxLines/ellipsis/color alone.
3. URL Text: add `lineHeight = 12.sp`; color → `MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)`; move block after the `if (desc != null)` block so order is title → desc → URL.
4. `OgThumb`: `.size(64.dp)` → `.size(48.dp)` (single shared composable — one edit covers both cards + sheet header; sheet header thumb shrink is accepted collateral, same component).
5. Both Rows: `Alignment.CenterVertically` → `Alignment.Top`; both text Columns: `Arrangement.Center` → `Arrangement.Top`.
6. Update both KDoc lines describing "Title (max 2 lines…)" to max 1 line.
7. Run `./gradlew :app:assembleDebug` then `./gradlew :app:testDebugUnitTest` — both must be green.

**Verify (automated):**
- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL
- `./gradlew :app:testDebugUnitTest` — all tests pass (includes `OgSourceCardHelpersTest`)

**Done:**
- Both cards render title(1-line ellipsis) → desc(2-line ellipsis, when present) → URL(last, dimmer) with tight lineHeights, 48dp thumb, top-aligned row; build + full unit suite green.

## Honest note

Density *feel* (does the card actually look tighter/better) cannot be verified here — no adb/device attached. Confirm visually on-device with a screenshot after flashing; if lineHeight = fontSize clips descenders on any OEM font, bump desc/URL to 13.sp.
