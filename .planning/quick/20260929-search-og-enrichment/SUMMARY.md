---
audit_acknowledged:
  milestone: v2.5
  at: 2026-10-01
  status: unknown
---

# SUMMARY — search-result titles + OG enrichment + two-column cards

**Status:** complete — all PLAN.md tasks executed, plus the user-locked layout amendment.
**Date:** 2026-09-29 (UTC).

## What was built

1. **Title threading (PLAN Task 1):** DDG + Tavily `fuse()` OK rows now carry
   `ogTitle` from the search-result title (trimmed, capped at
   `OpenGraphParser.MAX_TITLE_CHARS`, blank → null → host fallback at render).
   OMITIDA branches untouched; fused prompt text byte-identical.
2. **SearchOgEnricher (PLAN Task 2):** new `@Singleton` best-effort parallel
   OG scrape (`og:title`/`og:image`/`og:description`) hooked into both
   repository `search()` methods right after their own `fuse()` returns
   `Grounded`. All locked budgets honored: 3 s total `withTimeout`, 2 s
   connect/read per-call, 64 KB cap in 8 KB chunks, max 5 OK URLs, max 1
   manual redirect, `followRedirects(false)`, http(s) gate pre- and
   post-redirect, missing content-type = HTML / present non-`text/html` =
   skip, stripped client (AuthInterceptor removed, desktop UA, status-only
   Timber), `Jsoup.parse` only (zero `Jsoup.connect` in the file),
   `CancellationException` rethrows, outer timeout → originals. Scraped
   `og:title` wins on success; threaded title survives failed/skipped
   scrapes; `ogDescription`/`ogImageUrl` set only when scraped non-null.
   Test seams: `headSupplier` (null = skip) and `totalTimeoutMs`.
3. **User-locked layout amendment (overrides PLAN "no render-code changes"):** 
   `OgSourceCard` + `CompactSourceCard` are now TWO columns — left
   favicon/thumb, right Title (max 2 lines, ellipsis) + URL (1 line, muted,
   ellipsis). `[N]` badges and the open/external-link icon removed from card
   chrome. Taps unchanged (thumb → guarded browser intent, card → preview
   sheet). Kept: 2B2B29 rounded-12dp container, neutral text, shimmer,
   text-only fallback, struck omitida rows, English "Open in browser" thumb
   a11y, zero purple. Citations `[1]`/`[2]` in answer text and model prompt
   untouched. Stale `MessageBubble` comments updated to match.

## Test results

- `./gradlew :app:assembleDebug` — green.
- `./gradlew :app:testDebugUnitTest` — **614 tests, 0 failures, 0 errors, 0 skipped.**
  - `SearchOgEnricherTest` (new): 11/11 — success + merge-wins, missing-type-as-HTML,
    partial failure (order-preserving), blank-page keeps threaded title, timeout →
    originals, non-HTML skip, null-supplier skip, non-http(s) untouched (supplier
    uncalled), OMITIDA untouched, cancel propagation, 5-row cap.
  - `DuckDuckGoSearchRepositoryTest`: 25/25 (incl. 2 new threading cases).
  - `TavilySearchRepositoryTest`: 21/21 (incl. 2 new threading cases).
  - `RemoteSecretIsolationTest`: 6/6 (ctor updated for the new param).
- No-socket guarantee grep-verified: every repo construction in tests uses the
  null-returning `headSupplier` seam; `Jsoup.connect` absent from the enricher.

## Commits (branch `beta`)

- `34358689` feat(quick-og): thread search titles into fuse() details
- `1ae21f09` feat(quick-og): parallel OG enrichment post-fusion with repo hooks
- `632afe15` feat(quick-og): two-column source cards, no badges or open icon
- `b67dfa1f` test(quick-og): threading, enrichment, and no-socket seams

## Deviations from PLAN

### Auto-fixed issues

1. **[Rule 3 — blocking] `RemoteSecretIsolationTest` ctor:** third construction
   site of `TavilySearchRepository` missed by the plan's grep scope; added the
   null-seam enricher there too. No main-source construction sites exist
   (Hilt `@Inject` only) — verified by grep.
2. **[Rule 1 — correctness] blank-parse merge:** plan wrote
   `ogTitle = scraped.ogTitle`, but `OpenGraphParser.parse` returns all-null on
   blank/unparseable HTML, which would have wiped the threaded title on a
   "successful" empty scrape. Implemented `scraped.ogTitle ?: original.ogTitle`
   (a blank page is a failed scrape per the plan's own intent: "a failed/skipped
   scrape keeps it"). Pinned by the blank-page test.
3. **[Rule 2 — test determinism] `ioDispatcher = Unconfined` in test seams** and
   an internal `totalTimeoutMs` seam so the timeout case runs on virtual time
   with zero real waiting.

No architectural changes (no Rule 4). No auth gates. No stubs introduced.

## Fallback chain (pinned)

scraped og:title → threaded search title → host (`ogDisplayTitle`, unchanged) →
favicon S2 when `ogImageUrl` null → text-only card (title + URL only).

## On-device notes (not verifiable here — no adb)

- Enrichment adds ≤3 s worst-case latency to every search turn (typically far
  less: ≤64 KB × ≤5 parallel head-fetches).
- Live og:image quality (hotlink protection, oversized payloads at Coil load
  time) needs on-device confirmation; Coil + existing `onError → text-only
  collapse` is the safety net.
- Confirm two-column cards + titles on a real search-grounded turn; confirm
  `cd_open_in_browser` ("Open in browser") thumb a11y with TalkBack.

## Self-Check: PASSED

- Created files exist: `SearchOgEnricher.kt`, `SearchOgEnricherTest.kt`.
- All four commits present on `beta`; no unintended deletions in any of them.
- Suite: 614 tests green; assemble green.
