# SUMMARY — Source card description line (Title + URL + description)

**Status:** COMPLETE
**Commit:** 492fedf4 — `feat(quick-card-description-line): render og:description line in source cards`
**Date:** 2026-09-29

## What was built

Carousel and full source cards now read `favicon | Title + URL + description`:
- New pure helper `ogDisplayDescription(ogDescription: String?)` in `OgSourceCard.kt` — trims, returns null when blank, caps at 160 chars (plan's prescribed cap since `OpenGraphParser.MAX_DESCRIPTION_CHARS` is 500 > 160, so no cross-layer import needed).
- Both `OgSourceCard` and `CompactSourceCard` resolve `desc` alongside `displayTitle` and, only when non-null, render a 2dp `Spacer` + `Text(desc, 12sp, Normal, onSurfaceVariant, maxLines 2, Ellipsis)` after the URL line. When null they render nothing — no spacer, no placeholder — so unenriched rows are byte-identical Title+URL.
- Untouched: tap handlers, semantics, thumb/favicon logic, container colors, title/URL styling, preview sheet/grid/modal/download path.

## Test results

- Targeted: `OgSourceCardHelpersTest` — 14/14 PASS (10 existing + 4 new: trimmed+capped, blank→null, null→null, short passthrough).
- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL.
- Full `./gradlew :app:testDebugUnitTest` — 618 tests, 0 failures, 0 errors, 0 skipped.

## On-device note

No adb in this environment, so no screenshot was taken. Shipped with the layout guard (hidden-when-blank, 2-line cap, muted 12sp tone matching the URL line). Eyeball on device: check 3-line cards inside the 272dp Fuentes carousel for visual density. If unenriched rows look too sparse, the follow-up is a dedicated `snippet` field threaded from both search repos + entity/migration (separate quick-task per PLAN).

## Deviations

None — plan executed exactly as written. No enrichment, policy, DB, or i18n changes.
