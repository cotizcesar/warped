# Quick Plan: Card Title Top Spacing (overlay header fix)

## Goal
Catalog card title sits at exactly 14dp from card top = sides = bottom (no extra ~10dp float above title).

## Locked cause
Header Row height is driven by 48dp action touch targets (IconButton
minimumInteractiveComponentSize), so the title text floats vertically centered
with ~10dp extra above it on top of the 14dp card padding — while
sides/bottom are exactly 14dp.

## Approach (single, no alternatives)
Header becomes overlay construction:
- `Box(Modifier.fillMaxWidth())` where the title `Text` ALONE defines the row height.
- Title `Text` keeps `maxLines = 1`, `Ellipsis`, adds end padding ~52dp so text
  never underlaps the actions.
- Actions cluster (`CatalogDownloadActions`) wrapped in a `Box` with
  `align(Alignment.CenterEnd)`, composed AFTER the title (on top in z-order)
  so overlapping touch resolves to the button. Free to bleed symmetrically
  into card padding (no clip by default).
- Downloaded `CheckCircle` branch: same overlay treatment — fixed 48dp `Box`
  centered-end, remove the `padding(12.dp)` trick
  (`Modifier.padding(12.dp).size(24.dp)` → e.g. `Box(Modifier.size(48.dp),
  contentAlignment = Alignment.Center)` with inner `Icon(Modifier.size(24.dp))`).
- Active-download branch (progress + pause/close IconButtons) KEEPS its
  current row — do not touch (separate progress row below icons, out of scope).
- Card `Column` padding stays 14dp all sides; inter-row 4dp spacers untouched;
  expandable/error/progress sections untouched.
- Card-expand tap still works everywhere except on the action buttons.

## Out of scope
Colors, icons, download logic, expanded section, capability row, progress/error
sections. Everything else untouched.

## Files
- `app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt`
  - Header `Row` lines ~211-233 → overlay `Box`
  - `CatalogDownloadActions` downloaded branch lines ~341-346 → fixed 48dp Box

## Tasks

### Task 1: Overlay header + downloaded-badge fix
- In `CatalogModelCard`, replace the collapsed-row-1 `Row(weight(1f) + 4dp
  Spacer + CatalogDownloadActions)` with `Box(Modifier.fillMaxWidth())`:
  title `Text` first (with `Modifier.padding(end = 52.dp)`, keep
  `maxLines = 1` + `Ellipsis`), then `Box(Modifier.align(Alignment.CenterEnd))`
  wrapping the existing `CatalogDownloadActions(...)` call unchanged
  (active branch untouched).
- In `CatalogDownloadActions` downloaded branch, replace
  `Modifier.padding(12.dp).size(24.dp)` with a fixed 48dp centered `Box`
  + 24dp inner icon (same overlay treatment, no padding trick).
- Remove now-unused `Spacer(Modifier.width(4.dp))` in header only; keep all
  other spacers/padding identical. Remove `Row` import only if unused
  elsewhere (row 2 still uses `Row` — keep import).
- Do NOT touch: active branch internals, `CatalogInlineProgress`, error text,
  expanded section, capability row, card padding.

### Task 2: Verify (no behavior change)
- `./gradlew :app:assembleDebug` must succeed.
- `./gradlew :app:testDebugUnitTest` fully green.
- If a UI/paparazzi/screenshot test asserts the header layout, update it to
  the overlay structure; otherwise no test changes.
- Honest note: pixel confirmation needs an on-device screenshot (no adb in
  this environment) — verify visually on device: title-to-top == title-to-side.

## must_haves
- truths:
  - "Title text top edge sits exactly at card padding (14dp), equal on all sides"
  - "Title never underlaps action icons (ellipsizes before them)"
  - "Download / pause / resume / cancel / downloaded affordances all still work"
  - "Card-expand tap still works everywhere except on action buttons"
  - "Unit test suite stays green"
- artifacts:
  - "app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt contains overlay Box header with align(Alignment.CenterEnd) actions"
- key_links:
  - "Header title end-padding (52dp) prevents underlap with actions Box"
  - "Actions Box composed after title → touch resolves to button"

## Verify commands
```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```
