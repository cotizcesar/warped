# SUMMARY: Catalog Card Redesign (dense, expandable)

**Status:** Complete — build + full unit-test suite green.
**Commits:** `8e9cbe6` (Task 1: JSON + parsing + tests), `82b2533` (Task 2: card rewrite + expandedText tests)

## What was built

- `AllowlistedModel` gains nullable `ramNote` + `blurb` (back-compat: absent → null → card not expandable, no crash).
- `model_allowlist.json`: 4 locked Spanish ramNote/blurb pairs + honesty note appended to `meta.note` (mobile-footprint basis; 3n values marked aproximate).
- `CatalogModelCard` rewritten exactly per user-locked layout: collapsed Row 1 = title + download-state IconButton cluster; Row 2 = vision/audio/thinking icons (reused `CapabilityIconBadge`, Spanish cds Visión/Audio/Razonamiento) + size; tap toggles expanded Spanish RAM + blurb + modelFile line.
- All 7 prior download states preserved in icon form: idle Download icon, downloading mini-progress + Pause + Cancel, paused Resume + Cancel, progress % + bytes/total · speed parity line, cancel-confirm dialog verbatim, downloaded CheckCircle status, error-tinted retry icon + retained error text.
- Pure `expandedText(entry): String?` helper (null when both fields blank) drives expandability.
- Dead code deleted: `CatalogCapabilityBadge` text chips, wide `CatalogDownloadProgress` layout, Storage leading icon, `OutlinedButton("Download")`.

## Deviations from Plan

None structural. Two minor notes:
1. The `expandedText` blank-string contract test (planned under Task 1) lives in new `CatalogCardTextTest.kt` committed with Task 2, since the helper itself is defined in Task 2's file.
2. Progress labels rendered in Spanish ("Descargando…/En pausa…") under the plan's "user-facing strings in Spanish" rule; cancel-confirm dialog strings untouched per plan.

## Test results

- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL
- `./gradlew :app:testDebugUnitTest` (full) — BUILD SUCCESSFUL, zero failures/errors across all suites
- `CatalogCardTextTest`: 4/4 pass · `ModelAllowlistTest`: 10/10 pass · `CatalogDownloadUrlTest`: 4/4 pass (no shape changes needed)

## On-device confirmation note (honest)

No adb/hardware in this environment: visual polish (density, icon alignment, dark-purple card look, expand affordance/animation) needs screenshot review on a real device before closing. Logic + build + unit tests verified here.
