# PLAN — Source card description line (Title + URL + description)

## Objective

Add a description line to the source card so the user can decide to click
WITHOUT exploring: card reads favicon | Title + URL + description.
Description = `og:description` (already enriched onto `GroundedSource`);
max 2 lines + ellipsis, muted color; hidden when blank (card falls back to
current Title+URL exactly). Tap behaviors, badges-removed state, colors,
spacing rhythm otherwise byte-identical.

## Data-availability finding (verified in code, not assumed)

- `GroundedSource.ogDescription` EXISTS (`domain/model/GroundedSource.kt:22`),
  populated by `SearchOgEnricher` (og:description scraped at fetch) and
  `MultiUrlFetcher` (OG copy-through), persisted via `GroundedSourceEntity`
  + migration. **No data threading needed — the card already receives it
  and ignores it.**
- There is NO separate snippet field reaching the card: DDG
  (`DuckDuckGoSearchRepository.fuse`) and Tavily fuse the result snippet
  into `extractedText` as `"title\nsnippet"` (sanitized, per-page
  truncated), and fetched-URL rows carry full page text there instead.
  Deriving a "snippet" from `extractedText` second-line would show page
  body on fetched rows — unreliable. So this plan implements the
  **og:description → hidden** chain only; a pure-snippet fallback would
  need a new field + repo changes + DB migration (out of scope, noted
  below). Unenriched rows keep the current Title+URL exactly.
- Usages: only `CompactSourceCard` is called (`MessageBubble.kt:271`,
  Fuentes carousel); full `OgSourceCard` is currently unreferenced but
  shares the locked layout — update both (same file, duplicated block).

## Tasks

<task type="auto">
  <name>Task 1: Description line + helper + unit tests</name>
  <files>app/src/main/java/com/warped/ui/chat/components/OgSourceCard.kt, app/src/test/java/com/warped/ui/chat/components/OgSourceCardHelpersTest.kt</files>
  <action>Render-side only, no enrichment/policy/DB changes. Add pure helper ogDisplayDescription(ogDescription: String?): String? next to ogDisplayTitle: trim, null-if-blank, cap at ~160 chars (reuse OpenGraphParser.MAX_DESCRIPTION_CHARS when it is at most 160, else take(160)) with no mid-word guarantee needed beyond take. In BOTH OgSourceCard and CompactSourceCard, resolve val desc = ogDisplayDescription(source.ogDescription) alongside displayTitle and, only when non-null, render after the URL Text a 2.dp Spacer plus Text(desc, 12.sp, Normal, onSurfaceVariant, maxLines 2, Ellipsis). When null render nothing — no spacer, no placeholder — so the card is byte-identical to today. Do not touch tap handlers, semantics, thumb logic, container colors, title/URL styling, or the preview sheet/grid/modal/download path. Extend OgSourceCardHelpersTest with: og-desc returned trimmed and capped, blank/whitespace-only returns null, null returns null, short desc passes through untouched.</action>
  <verify>
    <automated>./gradlew :app:testDebugUnitTest --tests "com.warped.ui.chat.components.OgSourceCardHelpersTest" && ./gradlew assembleDebug && ./gradlew :app:testDebugUnitTest</automated>
  </verify>
  <done>Carousel cards with enriched og:description show a muted 2-line ellipsis description under the URL; blank/missing shows Title+URL exactly as before; helper unit tests green; assembleDebug + full testDebugUnitTest green.</done>
</task>

## Out of scope

Enrichment policy changes, pure-snippet fallback field (see note),
image grid / modal / preview-sheet changes, download path.

## Honest notes

- Visual density (3-line cards in a 272dp carousel) needs an on-device
  screenshot — no adb in this environment, so ship with the layout guard
  (hidden-when-blank, 2-line cap) and eyeball on device.
- If unenriched rows (Tavily/DDG rows pre-OG-scrape) showing no
  description proves too sparse in practice, the follow-up is a dedicated
  `snippet` field on GroundedSource threaded from both search repos +
  entity/migration — a separate quick-task, not smuggled in here.
