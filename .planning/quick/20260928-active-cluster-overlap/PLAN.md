# Quick Plan — Active download cluster overlap (2026-09-28)

## Cause (locked, verified in source)

`app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt`:

- `CatalogModelCard` header overlay: outer `Box(fillMaxWidth)` with title `Text(... Modifier.padding(end = 52.dp))` + inner `Box(Modifier.align(Alignment.CenterEnd))` hosting `CatalogDownloadActions` (lines ~213-235).
- `CatalogDownloadActions` active branch (lines ~313-342) emits `CircularProgressIndicator + Spacer + IconButton(pause/resume) + IconButton(cancel)` **directly with no Row wrapper**. Inside a `Box`, children stack on top of each other → overlapped/staggered ring + pause + cancel icons on-device.
- Idle/downloaded/failed branches are single children, so unaffected.
- Secondary: active cluster width ≈ 24 (ring) + 4 (spacer) + 48 (pause IconButton) + 48 (cancel IconButton) ≈ 124dp exceeds the fixed 52dp title end-padding → title text would underlap the cluster even after the Row fix.

## Scope

In scope: `CatalogModelCard` title end-padding + `CatalogDownloadActions` active-branch layout + progress ring colors.
Out of scope: progress text row (`CatalogInlineProgress`), error text, expanded section, download engine, catalog data.

## Tasks

### Task 1 — Wrap active cluster in Row + dynamic title padding + ring colors

File: `app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt`

1. Extract a pure helper (preferred, cheap + testable):
   `fun titleEndPaddingDp(active: Boolean): Int = if (active) 128 else 52`
   (dp value as Int, or Dp — keep the existing test style; document which).
2. In `CatalogModelCard`, replace fixed `Modifier.padding(end = 52.dp)` with
   `Modifier.padding(end = titleEndPaddingDp(active).dp)` so only the active card reserves the wide slot; idle/downloaded/failed keep 52dp exactly.
3. In `CatalogDownloadActions` active branch, wrap ALL active children in:
   `Row(verticalAlignment = Alignment.CenterVertically)` inside the existing overlay `Box`. Keep exact visuals/order for both sub-states:
   - downloading: ring + 4dp spacer + pause IconButton + cancel IconButton
   - paused: resume IconButton + cancel IconButton
   Idle/downloaded/failed branches: move inside the Row unchanged (or keep as-is if single child — no visual change either way).
4. Explicit ring colors on the active `CircularProgressIndicator`:
   `indicatorColor = MaterialTheme.colorScheme.primary`, `trackColor = Color(0xFF333333)` (matches app dividers; kills any purple-grey track impression).
5. Imports already present: `Row`, `Alignment`, `Color`, `MaterialTheme`, `dp` — add nothing except what the helper needs.

Verify:
- `./gradlew :app:assembleDebug` passes.
- Visual: active card shows ring | pause | cancel in one horizontal centered row, no overlap; long titles ellipsize before the cluster. Honest note: final visual confirmation needs an on-device screenshot (no adb in this environment).

Done:
- No Box-stacking in the active branch (single Row child in the overlay Box).
- Title padding 52dp idle / 128dp active; other branches pixel-identical.
- Ring uses primary indicator + 0xFF333333 track.

### Task 2 — Unit test (only if helper extracted) + full suite green

Files:
- `app/src/test/java/com/warped/ui/huggingface/CatalogCardTextTest.kt` (extend) OR new `CatalogActiveClusterTest.kt` — either is fine, don't do both.
- No other test changes expected (no behavior change).

1. Add 2 assertions: `titleEndPaddingDp(false) == 52`, `titleEndPaddingDp(true) == 128`. If the helper returns Dp, assert against `52.dp` / `128.dp`.
2. Run full unit suite: `./gradlew :app:testDebugUnitTest` — all green (existing `expandedText`, `isEffectivelyDownloaded`, ViewModel tests untouched).
3. If the helper is NOT extracted (inline ternary instead), skip new tests and just report the suite green.

Verify: `./gradlew :app:testDebugUnitTest` green.

Done:
- New helper covered (or documented reason for inline), suite green, no regressions.

## Files

- Modify: `app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt`
- Test: `app/src/test/java/com/warped/ui/huggingface/CatalogCardTextTest.kt` or new `CatalogActiveClusterTest.kt`
