---
phase: 58-opengraph-thumbnails
verified: 2026-09-29T12:35:00Z
status: passed
score: 3/3 must-haves verified
overrides_applied: 0
---

# Phase 58: OpenGraph Thumbnails Verification Report

**Phase Goal:** Every grounded source renders a rich thumbnail card with its OpenGraph data
**Verified:** 2026-09-29T12:35:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Fetch captures og:title/description/image persisted with source rows (single Room migration v16) | ✓ VERIFIED | `OpenGraphParser.kt` (Jsoup parse-only line 32, http(s) gate lines 71-72, 300/500 caps); `WebPageFetcher.kt:161-162` threads OG in HTML branch only (`openGraph=null` for plain/markdown); `MultiUrlFetcher.kt:125-127` copies OG into details (retry-safe); `Migrations.kt:141` single `MIGRATION_15_16` with exactly 3 nullable `ALTER TABLE` statements; `AppDatabase` version=16; `DatabaseModule.kt:54` registers full chain `...MIGRATION_14_15, MIGRATION_15_16`; `16.json` database.version=16 with 3 TEXT OG columns (ogTitle/og_title, ogDescription/og_description, ogImageUrl/og_image_url); entity + `EntityMappers.kt:96-98,108-110` round-trip both directions |
| 2 | User sees a Coil-loaded thumbnail card per source (tap → preview); text-only fallback without image | ✓ VERIFIED | Coil 3.4.0 pinned (`libs.versions.toml:18`, newest line compiling against compileSdk 35); `WarpedApplication.newImageLoader` singleton: own bare `OkHttpClient` (no AuthInterceptor reference), 25% memory, 50MB `og_thumbnails` disk cache, Okio path; `OgSourceCard` uses plain `AsyncImage` (singleton-resolved), 64dp thumb, 1-line title + 2-line description ellipsis, `[N]` badge + open icon; `MessageBubble.kt:240-263` ok items render cards in fetch-block order, tap sets `previewSource`/`previewNumber` opening sheet, omitida rows stay struck text with no card; `gatedHttpImageUrl` strict `http(s)://` render re-gate (WR-01 fixed); text-only fallback by construction (thumb slot collapses, no error state) |
| 3 | Preview sheet shows the OG header above extracted text | ✓ VERIFIED | `SourcePreviewSheet.kt:82-83` computes `ogTitle`/`gatedImage` via shared helpers; header renders 64dp thumb + 1-line title + `[N]` badge + 1-line URL, positioned above `HorizontalDivider` (line ~130) with extracted text unchanged below; text-only header when image absent/failed; `BrowserIntents.openUrlInBrowser` single gate serves card icon + sheet button + legacy path (case-insensitive scheme per WR-02 fix, dismisses sheet only on `true`) |

**Score:** 3/3 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `data/grounding/OpenGraphParser.kt` | Pure-Kotlin OG parser, parse-only | ✓ VERIFIED | `Jsoup.parse(html, baseUrl)` in-memory; no `Jsoup.connect` in grounding sources (only `call.execute()` in WebPageFetcher is the pre-existing fetch socket); http(s) gate, 300/500 caps, `meta[name=description]` + `twitter:image` fallbacks |
| `data/grounding/WebPageFetcher.kt` (threading) | OG captured in HTML branch | ✓ VERIFIED | `isHtml` flag mirrors extraction routing; `openGraph=null` for plain/markdown by construction |
| `data/local/db/Migrations.kt` MIGRATION_15_16 | Single v15→v16 migration | ✓ VERIFIED | Exactly 3 `ALTER TABLE grounded_sources ADD COLUMN` (og_title/og_description/og_image_url, TEXT, nullable, no default) |
| `AppDatabase.kt` + `DatabaseModule.kt` | v16 registered, no destructive fallback | ✓ VERIFIED | version=16; full chain wired including MIGRATION_15_16 |
| `schemas/.../AppDatabase/16.json` | v16 schema export | ✓ VERIFIED | database.version=16, 3 TEXT OG columns on grounded_sources |
| `WarpedApplication.kt` Coil singleton | Own-OkHttp ImageLoader, 50MB disk | ✓ VERIFIED | Single `newImageLoader` override; bare OkHttp (redirects only); 25% memory + 50MB `og_thumbnails` disk; T-58-05 clean |
| `ui/chat/components/OgSourceCard.kt` | Thumbnail card per UI-SPEC | ✓ VERIFIED | 64dp AsyncImage + shimmer, 1-line/2-line ellipsis, `[N]` badge + open icon, full-row tap → preview, silent text-only fallback |
| `ui/chat/components/BrowserIntents.kt` | Single browser gate | ✓ VERIFIED | http/https allowlist (case-insensitive post WR-02), dual catch, Boolean return; all 3 call sites routed |
| `ui/chat/components/MessageBubble.kt` (Fuentes) | Cards per ok source, omitida struck | ✓ VERIFIED | Ok → `OgSourceCard`; omitida → struck text, no card; order preserved |
| `ui/chat/components/SourcePreviewSheet.kt` (header) | OG header above divider | ✓ VERIFIED | Thumb + title + badge + URL above `HorizontalDivider`; body/action paths unchanged |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| WebPageFetcher | OpenGraphParser.parse | `og = if (isHtml) parse(raw, url) else null` | WIRED | HTML-only; zero new sockets |
| MultiUrlFetcher.details | GroundedSource og fields | `result.openGraph?.ogTitle/...` copy | WIRED | Retry thumbnails cannot vanish |
| ChatRepository save/replace/hydration | GroundedSourceEntity | `toEntity`/`toDomain` OG columns | WIRED | Grep-verified in 58-01 (no separate retry path) |
| OgSourceCard | Coil singleton | plain `AsyncImage` → default loader | WIRED | Single `newImageLoader`; no per-card loaders |
| MessageBubble Fuentes | OgSourceCard / previewSource | card tap sets preview state | WIRED | Tap → `SourcePreviewSheet` |
| SourcePreviewSheet | OG header | thumb + title + badge + URL above divider | WIRED | Text-only when image absent/failed |
| Card icon + sheet button + legacy | BrowserIntents gate | `openUrlInBrowser` | WIRED | One allowlist + dual catch, no drift |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| OgSourceCard | `source.ogTitle/ogDescription/ogImageUrl` | Room rows → hydration → GroundedSource | ✓ FLOWING | Parser → fetcher → entity → Room → domain → card; null OG by design renders text-only |
| SourcePreviewSheet header | `source.ogTitle/ogImageUrl` | Same rows via previewSource | ✓ FLOWING | Shared helpers `ogDisplayTitle`/`gatedHttpImageUrl` |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Full unit suite green | `:app:testDebugUnitTest --offline` | 494 tests, 0 failures, 0 errors (BUILD SUCCESSFUL) | ✓ PASS |
| Targeted OG tests | OpenGraphParserTest + Migration15To16StaticTest + OgSourceCardHelpersTest | exit 0 | ✓ PASS |
| WR-01 opaque-URI regression | `OgSourceCardHelpersTest: image gate rejects opaque non-hierarchical uris` (`https:foo`, `https:javascript:alert(1)` → null) | present, passing | ✓ PASS |
| Parse-side socket gate | grep `Jsoup.connect` in grounding sources → no match | CLEAN | ✓ PASS |

### Probe Execution

No probes declared for this phase (not a migration/tooling phase with probe scripts). Skipped.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| OG-01 | 58-01 | Fetch captures OG tags; persisted with source rows (migration v16) | ✓ SATISFIED | Parser + threading + MIGRATION_15_16 + 16.json + round-trip tests (13 parser + 7 migration tests) |
| OG-02 | 58-02 | Thumbnail card per source (Coil image, title, desc, tap → sheet; text-only fallback) | ✓ SATISFIED | Coil singleton + OgSourceCard + Fuentes wiring + 9 helper tests; omitida/failed-image fallbacks by construction |
| OG-03 | 58-02 | Preview sheet OG header above extracted text | ✓ SATISFIED | Sheet header (thumb + title + badge + URL) above divider; body unchanged |

### Review Findings Closure (58-REVIEW.md)

| Finding | Severity | Status | Evidence |
|---------|----------|--------|----------|
| WR-01 render-gate opaque URIs | warning | ✓ FIXED | `gatedHttpImageUrl` now strict `http(s)://` prefix (lines 241-242); regression test added (`095cb0a3`) pinning `https:foo`/`https:javascript:alert(1)` → null |
| WR-02 browser scheme case-sensitivity | warning | ✓ FIXED | `BrowserIntents.kt:27-28` uses `equals(..., ignoreCase = true)` (`26573835`) |
| IN-01 duplicated host fallback | info | accepted | `ogHostOf` (www-strip) vs parser `hostOf` (no strip) — divergent but harmless; deferred, no user impact |
| IN-02 dead-path spacer | info | accepted | Unreachable guard by construction; at most a stray 8dp gap in an impossible path |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | `return null` in `OpenGraphParser.resolveHttpUrl` / `gatedHttpImageUrl` | ℹ️ Info | Legitimate gate rejections, not stubs — verified in context |
| — | — | No TODO/FIXME/XXX/placeholders in Phase 58 files | — | CLEAN |

### Human Verification Required

None blocking. Two visual confirmations were anticipated by the UI-SPEC backstop and 58-02 SUMMARY as accepted non-blocking follow-ups per precedent (automatable evidence — 494/494 tests, strict gates, release assemble green per SUMMARY — all passes):

- **On-device image loading** (Coil thumb fetch over network on hardware): emulator/device check when convenient; failure mode is silent text-only fallback, so no crash risk.
- **Long-text ellipsis rendering** (200-char title / 500-char description visual ellipsis): `maxLines` 1/2 + `TextOverflow.Ellipsis` are set in code; pixel confirmation needs a screen.

These are recorded as accepted follow-ups, not gaps: the contracts are pinned in code and unit tests, and both degradations are graceful by construction.

### Gaps Summary

No gaps. All three success criteria are observably true in the codebase: OG data flows end-to-end from HTML parse to Room v16 to card/sheet rendering, the Coil singleton uses its own unauthenticated OkHttp instance with conservative caches, both review warnings are fixed with regression coverage, and the full suite is 494/494 green with zero failures.

---
_Verified: 2026-09-29T12:35:00Z_
_Verifier: the agent (gsd-verifier)_
