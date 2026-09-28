# Quick Plan: Kill Purple Theme Remnants (color-only)

## Goal
Zero purple in the app UI. All 5 locked purple sources replaced with the app's
established language (dark neutrals `1F1F1E`/`2B2B29` + coral `D97757`).
No layout, behavior, or theme-definition changes.

## Locked audit (all 5 purple sources — nothing else to touch)
| # | File:line | Current | Target |
|---|-----------|---------|--------|
| 1 | `HuggingFaceScreen.kt:206` catalog card bg | `primaryContainer.copy(alpha = 0.4f)` | `Color(0xFF2B2B29)`, drop the alpha hack — identical to ModelsScreen cards |
| 2a | `HuggingFaceScreen.kt:372` vision icon | `Color(0xFF9C27B0)` | `Color(0xFF64B5F6)` light blue (distinct from audio green 4CAF50, thinking orange FF9800, tools blue 2196F3; never purple again) |
| 2b | `CapabilityBadges.kt:36` vision icon | `Color(0xFF9C27B0)` | `Color(0xFF64B5F6)` — same value as 2a, keep both in sync |
| 3 | `PresetsScreen.kt:353-357` PresetItem selected | `primaryContainer` fill when selected | Mirror ModelsScreen selected language (see note) |
| 4 | `ModelSelector.kt:170-176` selected row | `primaryContainer` bg when selected | Mirror ModelsScreen selected language (see note), keep existing icon tint line untouched |

**Selected-state note (verified 2026-09-28):** `ModelsScreen.kt` has NO
`isSelected`/border/active-marker concept — its cards are unconditionally
`Color(0xFF2B2B29)` with coral `Color(0xFFD97757)` action buttons
(lines 358, 396, 456, 550, 572). So "mirror ModelsScreen" means: **never a
filled tinted container for selected state.** Selected = same `2B2B29`
container + a coral marker (e.g. `BorderStroke(1.dp, Color(0xFFD97757))` on
the PresetItem Card; a coral indicator/tint on the ModelSelector row),
unselected = plain container with no marker. Read ModelsScreen lines 356-398
first and match its card shape/colors exactly. Do NOT invent a new
selected-fill color. Do NOT touch `Theme.kt`/`Color.kt`.

## must_haves (goal-backward)
- truths:
  - "No purple surface, icon, or selected-state is visible anywhere in the app"
  - "Catalog cards look identical to ModelsScreen cards"
  - "Vision badge is light blue and distinguishable from audio/thinking/tools badges"
  - "Selected preset / selected model row is identifiable without any purple"
  - "Existing unit/UI test suite is green"
- artifacts:
  - `HuggingFaceScreen.kt` contains `Color(0xFF2B2B29)` card bg, no `primaryContainer`, no `9C27B0`
  - `CapabilityBadges.kt` vision color is `Color(0xFF64B5F6)`
  - `PresetsScreen.kt` has no `primaryContainer`
  - `ModelSelector.kt` has no `primaryContainer`
- key_links:
  - `CatalogCapabilityIcons` → shared `CapabilityIconBadge` iconography (vision/audio/thinking) stays consistent with `CapabilityIconRow`
  - Selected-state markers → coral `0xFFD97757` only, no new theme colors introduced

## Tasks

### Task 1: Replace the 5 purple sources (color-only edits)
Files:
- `app/src/main/java/com/warped/ui/huggingface/HuggingFaceScreen.kt`
- `app/src/main/java/com/warped/ui/components/CapabilityBadges.kt`
- `app/src/main/java/com/warped/ui/presets/PresetsScreen.kt`
- `app/src/main/java/com/warped/ui/chat/components/ModelSelector.kt`

Actions:
1. Read `ModelsScreen.kt` lines 356-398 first; note exact card `containerColor`,
   shape, and coral button usage.
2. Apply edits #1, #2a, #2b, #3, #4 from the locked audit table above.
3. Change NOTHING else: no layout/spacing, no imports beyond what the color
   literals need (`androidx.compose.ui.graphics.Color`, `BorderStroke`/`border`
   if used for the coral marker — remove `MaterialTheme` import only if it
   becomes unused in that file, otherwise leave it), no `Theme.kt`/`Color.kt`.
4. Grep tests for old values first: search test sources for
   `9C27B0|primaryContainer|64B5F6` — update assertions only if they assert the
   old colors. Note in SUMMARY if a screenshot/paparazzi test exists.
5. Post-edit grep gate (must return ZERO hits under `app/src/main`):
   `primaryContainer|secondaryContainer|tertiaryContainer|9C27B0|6750A4|7B1FA2|CE93D8|E1BEE7`
   (add any other purple hex encountered to the gate). `colorScheme.primary`
   usages (e.g. ModelSelector icon tint) are out of scope — Theme.kt untouched.

Verify:
- Automated: `grep -rnE 'primaryContainer|secondaryContainer|tertiaryContainer|9C27B0|6750A4|7B1FA2|CE93D8|E1BEE7' app/src/main || echo CLEAN`
- Automated: `./gradlew :app:assembleDebug`
- Automated: `./gradlew :app:testDebugUnitTest` (full suite green)

Done: all 5 edits applied, grep gate clean, assemble + full unit-test suite green,
no non-color diff in `git diff`.

## Out of scope
Layout/spacing changes, dark/light theme definitions (`Theme.kt`/`Color.kt`
untouched), any functional/behavioral change.

## Honest verification note
True color judgment needs an on-device screenshot — no `adb` in this
environment, so correctness is established by hex-level grep gate + green build,
not visual inspection.
