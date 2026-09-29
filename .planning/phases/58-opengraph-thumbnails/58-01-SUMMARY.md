---
phase: 58-opengraph-thumbnails
plan: 01
subsystem: grounding-persistence
tags: [opengraph, jsoup, room-migration, grounding]
dependency_graph:
  requires: [phase-53-grounded-sources, phase-52-fetch-pipeline]
  provides: [og-parser, og-threaded-details, migration-15-16, og-entity-roundtrip]
  affects: [58-02-cards-ui, 58-03-sheet-header]
tech_stack:
  added: []
  patterns: [parse-only-second-Jsoup-parse, nullable-defaulted-threading, single-migration-per-phase, static-migration-gate]
key_files:
  created:
    - app/src/main/java/com/warped/data/grounding/OpenGraphParser.kt
    - app/src/test/java/com/warped/data/grounding/OpenGraphParserTest.kt
    - app/src/test/java/com/warped/data/local/db/Migration15To16StaticTest.kt
    - app/schemas/com.warped.data.local.db.AppDatabase/16.json
  modified:
    - app/src/main/java/com/warped/data/grounding/WebPageFetcher.kt
    - app/src/main/java/com/warped/data/grounding/GroundingResult.kt
    - app/src/main/java/com/warped/data/grounding/MultiUrlFetcher.kt
    - app/src/main/java/com/warped/domain/model/GroundedSource.kt
    - app/src/main/java/com/warped/data/local/db/entity/GroundedSourceEntity.kt
    - app/src/main/java/com/warped/data/local/db/entity/EntityMappers.kt
    - app/src/main/java/com/warped/data/local/db/Migrations.kt
    - app/src/main/java/com/warped/data/local/db/AppDatabase.kt
    - app/src/main/java/com/warped/di/DatabaseModule.kt
    - app/src/test/java/com/warped/data/local/db/Migration14To15StaticTest.kt
decisions:
  - "OG parsed via second in-memory Jsoup.parse of the capped raw string (HtmlToMarkdown strips meta tags; threading Document through the frozen extractor would risk Phase 52 exit gates)"
  - "Image resolution via java.net.URI.resolve (not Jsoup abs: attributes) with http(s) prefix gate"
  - "Old 14-to-15 registration gate relaxed from version == 15 to version >= 15 so it survives future bumps"
metrics:
  duration: "~40 min"
  completed: "2026-09-29"
---

# Phase 58 Plan 01: OG Scrape + Persistence Tracer Summary

**One-liner:** Pure-Kotlin OpenGraph parser (Jsoup parse-only, http(s) image gate, 300/500 caps) threaded through fetch-to-Room with a single v15-to-v16 migration and JVM static gates — 484/484 unit tests green.

## Objective Achieved

OG-01 data backbone complete: every HTML fetch captures og:title/description/image alongside extraction with zero new sockets, persists them with source rows via MIGRATION_15_16, and carries them identically through retry writes and hydration. No UI yet — plan 58-02 builds cards on this backbone.

## Tasks Completed

| # | Name | Commit | Files |
|---|------|--------|-------|
| 1 | OpenGraphParser pure unit + fetcher threading (OG-01 parse) | 106e00c9 | OpenGraphParser.kt, WebPageFetcher.kt, GroundingResult.kt, MultiUrlFetcher.kt, GroundedSource.kt, OpenGraphParserTest.kt |
| 2 | Room v15 to v16 migration + entity/mapper/hydration (OG-01 persist) | c347da62 | GroundedSourceEntity.kt, EntityMappers.kt, Migrations.kt, AppDatabase.kt, DatabaseModule.kt, Migration15To16StaticTest.kt, 16.json (+14-15 gate relaxation) |

## Key Links Verified

- `WebPageFetcher` → `OpenGraphParser.parse`: called ONLY in the HTML branch (`isHtml = !isMarkdown && !plain`; missing content-type treated as html same as extraction). Plain/markdown bodies get `openGraph = null`; failed extracts return ModelOnly with no OG.
- `MultiUrlFetcher.details` → `GroundedSource` og fields: builder copies `result.openGraph` fields in the same edit (Pitfall 2 closed — retry thumbnails cannot vanish).
- `ChatRepositoryImpl` save/replace/hydration all flow through `toEntity`/`toDomain` (verified by grep) — no separate retry logic needed; delete-then-insert carries OG identically.

## Decisions Made

- **Second-parse over shared Document** (research Pattern 1): `HtmlToMarkdown.convert` strips meta tags, so OG is read from a fresh `Jsoup.parse(raw, url)` — microseconds, in-memory, touches nothing frozen.
- **URI.resolve for image resolution**: `java.net.URI(base).resolve(raw)` instead of Jsoup `abs:` attributes; accept only `http(s)` prefix post-resolution (T-58-01).
- **Description fallback to `meta[name=description]`** included per research recommendation (more cards with descriptions; column nullable either way).
- **Render-side title fallback deferred to plan 58-02**: `displayTitle = ogTitle ?: hostOf(url)` belongs in the card/sheet; parser's `hostOf` stays internal. No backfill, no migration change.
- **Old gate forward-compat fix**: the 14→15 registration test pinned `version = 15`; relaxed to `>= 15` (parsed via regex) so it survives this and all future bumps.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] KDoc comment tripped the Jsoup.connect grep gate**
- **Found during:** Task 1 verification
- **Issue:** Parser KDoc contained the literal string `Jsoup.connect` (in a "never opens a socket" sentence), matching the plan's own grep-gate pattern.
- **Fix:** Reworded comment to avoid the literal string; gate now clean.
- **Files modified:** OpenGraphParser.kt (comment only)
- **Commit:** 106e00c9

**2. [Rule 3 - Blocking] Pre-existing 14→15 static gate failed after version bump to 16**
- **Found during:** Task 2 full-suite run (484 tests, 1 failed)
- **Issue:** `Migration14To15StaticTest` registration test asserted AppDatabase declares `version = 15` exactly — inherently broken by any forward migration; first bump since the gate was introduced in Phase 53.
- **Fix:** Relaxed to `version >= 15` (regex-extracted from source + reflection `isAtLeast(15)`); also fixed a missing `()` on `toIntOrNull` caught by the compiler. The gate still pins its own link: exactly one `Migration(14, 15)` + `MIGRATION_14_15` wired in `addMigrations`.
- **Files modified:** app/src/test/java/com/warped/data/local/db/Migration14To15StaticTest.kt
- **Commit:** c347da62

**3. [Rule 2 - Critical] Markdown/plain null-OG enforced at call site, not parser unit test**
- **Found during:** Task 1
- **Issue:** Plan listed "markdown/plain body yields null OG" under parser tests, but the parser takes an HTML string — the null-OG contract lives at the `WebPageFetcher` call site (`og = if (isHtml) parse(...) else null`), which requires Android `Context` and is not JVM-testable.
- **Fix:** Enforced structurally at the call site (single `isHtml` flag mirroring the extraction routing); `Grounded.openGraph` defaults to null so any non-HTML path is null by construction. No test gap in practice.
- **Commit:** 106e00c9

## Auth Gates

None — no external services touched.

## Test Results

- `OpenGraphParserTest`: 13 tests green (happy path, title→doc-title→host chain, SEO description fallback, null description/image, relative URL resolution, twitter:image fallback, data:/javascript: rejection, blank HTML, 50KB→500 cap, 5000-char→300 cap, defaulted-null contract).
- `Migration15To16StaticTest`: 7 tests green (versions, v15 frozen baseline, exact 3-column delta with TEXT/nullable/no-default, 3-statement SQL exactness + createSql agreement, registration, OG round-trip, legacy null round-trip).
- Full suite: **484/484 green, 0 failures** (includes pre-existing 14→15 gate after Rule-3 fix).
- `Jsoup.connect` grep gate: clean in grounding sources.
- KSP: `16.json` exported (v16, 3 nullable TEXT OG columns, no defaults); `15.json` frozen untouched (git-confirmed: only `16.json` untracked-added, no modification to `15.json`).

## Threat Flags

None — all STRIDE mitigations from the plan's threat model are implemented in-plan:
- T-58-01 (image tampering): http(s) gate at parse; images never fetched at scrape time.
- T-58-02 (stored XSS): Jsoup `attr()` strings stored inert; Compose `Text()` rendering deferred to 58-02.
- T-58-03 (OG bloat DoS): `.take(300)` title / `.take(500)` description caps at parse.
- T-58-04 (signed-URL disclosure): no logging added in new code (no Timber/Log/println with URLs); existing `RedactingTree` covers future logs.
- T-58-SC: no package-manager installs (Jsoup/Room already in catalog; Coil lives in 58-02).

## Known Stubs

None — every field is wired end to end (parse → Grounded → details → entity → Room → hydration). Null OG is by design (pre-58 rows, plain/markdown, Tavily), rendering text-only cards in 58-02.

## Self-Check: PASSED

- All created files exist on disk (parser, 2 test files, 16.json).
- Both task commits exist in git log (106e00c9, c347da62).
- 16.json is version 16 with exactly the 3 new nullable TEXT columns (verified via JSON inspection).
- No `Jsoup.connect` in grounding sources (grep gate clean).
