# Quick-Task Plan: All-Sources Sheet ("View all sources" drawer)

## Locked decision (verbatim user directive — non-negotiable)

- "Sources" header row gains a trailing icon button (same row, right side; list/expand glyph, 48dp touch, English a11y **"View all sources"**), visible **ONLY when ≥2 sources exist** (single source needs no drawer; zero sources → no header at all, unchanged).
- Tapping it opens a bottom sheet (same `ModalBottomSheet` scaffold/pattern as `SourcePreviewSheet`: `skipPartiallyExpanded = true`, surface container) listing **ALL sources as full-width rows** reusing the 2-col card visual (favicon/thumb, title, URL, description, muted, ellipsis) with search-engine info well styled; **omitida rows included struck/disabled WITHOUT tap** (consistent with chat); text-only sources render without thumb (existing fallback via `gatedHttpImageUrl` + `faviconFallbackUrl` + silent collapse).
- Row tap → opens that URL **in browser directly** (guarded `openUrlInBrowser` intent, same as thumb rule — the drawer's purpose is choosing which to visit). **No nested detail navigation** inside the sheet (single level; per-source preview sheet stays reachable via card tap in chat, unchanged).
- Dismiss via swipe/scrim only, no state change. English copy. Zero purple. Dark neutrals + rounded like siblings.
- Out of scope: single-source sheet contents, carousel changes, download path, search/fetch logic.

## Wiring points verified in code

- `app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt` (lines 228–320): `fuenteList` via `fuenteItems(details, legacyUrls)`; header is a bare `Text(sources_title)` at lines 239–244 shown only when `fuenteList.any { it.clickable }`; carousel renders clickable ok items via `CompactSourceCard` (body tap → `previewSource`/`previewNumber`, thumb → `openUrlInBrowser`); omitida rows are struck `Text(fuente_skipped_fmt)` with no tap; sheet host at 305–320 uses `SourcePreviewSheet(source, number, onDismiss, onOpenBrowser)` with guarded dismiss-only-on-launch.
- `app/src/main/java/com/warped/ui/chat/components/SourcePreviewSheet.kt` (lines 49–63): scaffold to copy — `rememberModalBottomSheetState(skipPartiallyExpanded = true)` + `ModalBottomSheet(onDismissRequest, sheetState, containerColor = surface)`. Pure helpers `fuenteItems` / `previewForTap` / `browserTarget` live here (lines 188–225) — reuse, do not duplicate.
- `app/src/main/java/com/warped/ui/chat/components/OgSourceCard.kt`: 2-col visual to reuse — `OgThumb` (internal, 64dp Coil + shimmer, same file), `ogDisplayTitle` (og:title → host fallback), `ogDisplayDescription` (trim, 160-char cap), `gatedHttpImageUrl`, `faviconFallbackUrl`. Container color rule: `OgCardDark` in dark theme / `surfaceVariant` in light — zero new color constants, zero purple.
- `app/src/main/java/com/warped/ui/chat/components/BrowserIntents.kt`: `openUrlInBrowser(context, url): Boolean` — single guarded gate (http/https allowlist, `ActivityNotFoundException`/`SecurityException` → toast, returns false). Reuse for every row tap.
- Strings: `sources_title`, `sheet_source_fmt`, `fuente_skipped_fmt`, `cd_open_in_browser` in `app/src/main/res/values/strings.xml`; ES parity required (`app/src/main/res/values-es/strings.xml`, enforced by `StringResourceParityTest`).
- Test precedent: `app/src/test/java/com/warped/ui/chat/SourcePreviewMappingTest.kt` — pure-mapper JVM tests (JUnit5 + Truth) over `fuenteItems`/`previewForTap`; ACTION_VIEW intent itself is not JVM-assertable.

## Tasks

### Task 1 — AllSourcesSheet composable (new file)

**File:** `app/src/main/java/com/warped/ui/chat/components/AllSourcesSheet.kt` (create)

**Action:**
- New `@Composable AllSourcesSheet(sources: List<GroundedSource>, onDismiss: () -> Unit, onOpenBrowser: (url: String) -> Unit)` using the same `ModalBottomSheet` scaffold as `SourcePreviewSheet` (`skipPartiallyExpanded = true`, `containerColor = MaterialTheme.colorScheme.surface`). Header title from new string `all_sources_title` ("All sources"). Dismiss = `onDismiss` only (swipe/scrim/back), no state change.
- Body: full-width rows in fetch-block order (same order as `fuenteList`, ok + omitida interleaved — do NOT reorder). Ok rows reuse the 2-col card visual: left `OgThumb` (same `gatedHttpImageUrl(ogImageUrl) ?: faviconFallbackUrl(url)` chain, silent text-only collapse on null/fail), right `ogDisplayTitle` (Semibold, ≤2 lines, ellipsis) + URL (muted, 1 line, ellipsis) + `ogDisplayDescription` (muted, ≤2 lines, ellipsis). Same container (`OgCardDark` dark / `surfaceVariant` light), same rounded shape/padding language as `OgSourceCard`. Search-engine info (title/URL/desc from hydrated `GroundedSource`) styled per the card pieces — no new text styles beyond card sizes.
- Row tap (ok only): call `onOpenBrowser(browserTarget(source))` — guarded intent, same as thumb rule. Do NOT dismiss the sheet on tap unless the caller decides (caller mirrors the MessageBubble pattern: dismiss only when `openUrlInBrowser` returns true — decide in Task 3 wiring, keep the sheet itself dumb).
- Omitida rows: struck/disabled text row (same `fuente_skipped_fmt` copy + `LineThrough`, muted, no `clickable`, no tap) — consistent with the carousel's omitida rows.
- Pure helper in the same file: `fun shouldShowViewAll(fuenteList: List<FuenteItem>): Boolean = fuenteList.size >= 2` (visibility rule; `FuenteItem` already defined in `SourcePreviewSheet.kt` — import, do not redefine). Icon choice is the implementer's pick from `Icons.Filled`/`Icons.AutoMirrored.Filled` list/expand glyphs (e.g. `ViewList`/`UnfoldMore`) — 48dp touch target, `contentDescription` from new string `cd_view_all_sources` ("View all sources").
- Strings to add (EN + ES, parity test enforced): `all_sources_title` ("All sources" / "Todas las fuentes"), `cd_view_all_sources` ("View all sources" / "Ver todas las fuentes"). Zero purple anywhere; English copy for new visible strings per directive (ES translations are parity-only).
- Do NOT touch: `SourcePreviewSheet` contents, carousel layout, download path, search/fetch logic.

**Verify:** `./gradlew :app:assembleDebug` passes.

**Done:** `AllSourcesSheet.kt` exists, compiles, renders all sources full-width with omitida struck/disabled and no nested navigation.

### Task 2 — Header icon + sheet wiring in MessageBubble

**Files:** `app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values-es/strings.xml` (modify)

**Action:**
- Replace the bare `Text(sources_title)` header (lines 239–244) with a `Row(verticalAlignment = CenterVertically, modifier = fillMaxWidth)`: title `Text` with `Modifier.weight(1f)` + trailing `IconButton` (48dp touch via default `IconButton` sizing, `contentDescription = stringResource(cd_view_all_sources)`).
- Visibility: icon renders ONLY when `shouldShowViewAll(fuenteList)` is true (≥2 sources total — ok + omitida count, consistent with `fuenteList` covering all N). Single source → title only; zero ok sources → whole block hidden (existing `any { it.clickable }` gate untouched).
- Sheet host next to the existing `previewSource` host (after line 320): `var showAllSources by remember { mutableStateOf(false) }` (local ephemeral state only, same discipline as `previewSource` — never lifted to ViewModel, never the hydrated data). Icon tap sets `showAllSources = true`; `AllSourcesSheet(sources = sourceDetails-resolved list, onDismiss = { showAllSources = false }, onOpenBrowser = { url -> if (openUrlInBrowser(context, url)) showAllSources = false })` — same dismiss-only-on-launch pattern as the single-source sheet.
- Sources passed to the sheet: resolve ok items to `GroundedSource` via existing `previewForTap(sourceDetails, index)` / legacy `GroundedSource(url)` fallback (mirror the carousel's `cardSource` resolution so numbering matches fetch-block order); omitida items pass through as-is for struck rows. Reuse `fuenteItems` numbering — do not renumber.
- Per-source preview sheet (`previewSource` host) stays byte-identical behavior — card tap in chat still opens it.

**Verify:** `./gradlew :app:assembleDebug` passes; manual code check: icon absent for 0–1 sources, present for ≥2.

**Done:** Header row shows icon only when ≥2 sources; tap opens the all-sources sheet; row tap opens browser via guarded intent; swipe/scrim dismisses with no state change; single-source and zero-source behavior unchanged.

### Task 3 — JVM tests for visibility rule + row-tap routing + omitida disabled

**File:** `app/src/test/java/com/warped/ui/chat/AllSourcesSheetTest.kt` (create; mirror `SourcePreviewMappingTest` style: JUnit5 + Truth, pure-mapper level)

**Action:**
- `shouldShowViewAll`: 0 sources → false; 1 ok → false; 1 ok + 0 omitida → false; 2 ok → true; 1 ok + 1 omitida → true (omitida counts toward the ≥2 rule); legacy-urls-only list of 2 → true.
- Row-tap routing: for each ok `FuenteItem`, `previewForTap`-resolved source's `browserTarget(source)` equals the fetcher-resolved `url` and never pasted/extracted text (same assertion shape as `browserTarget` test in `SourcePreviewMappingTest`).
- Omitida disabled: `previewForTap(details, omitidaIndex)` is null (sheet never opens for omitida); `fuenteItems(...).first { !clickable }` rows assert `clickable == false` so the sheet renders them without tap.
- Fetch-block order: sheet row order (`fuenteItems` numbers) is `1..N` in order across ok + omitida interleaved.

**Verify:** `./gradlew :app:testDebugUnitTest --tests "com.warped.ui.chat.AllSourcesSheetTest"` green, then full `./gradlew :app:testDebugUnitTest` green (parity test included).

**Done:** All new tests pass; full unit-test suite green; `assembleDebug` green.

## Verification (all must pass)

1. `./gradlew :app:assembleDebug`
2. `./gradlew :app:testDebugUnitTest` (full suite green, incl. `StringResourceParityTest` EN/ES)
3. `grep -rn "purple\|0xFF.*[Pp]urple\|Color(0xFF8" app/src/main/java/com/warped/ui/chat/components/AllSourcesSheet.kt` returns nothing (zero-purple check)

## Honest note

Sheet visuals + tap flow (icon placement, row density, thumb fallback, browser launch, swipe/scrim dismiss) need **on-device confirmation — no adb available in this environment**, so visual polish and the guarded-intent launch path beyond the JVM-assertable mapper level are verified by code review + build/tests only.
