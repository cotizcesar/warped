# Phase 58 — OpenGraph Thumbnails: Security Assessment

**Phase:** 58-opengraph-thumbnails (plans 01 + 02)
**Date:** 2026-09-29
**ASVS Level:** 1
**Verdict:** SECURED — 10/10 closed, 0 open
**Threat flags:** none declared in either SUMMARY.md

## Threat Verification

| Threat ID | Category | Disposition | Evidence |
|-----------|----------|-------------|----------|
| T-58-01 | Tampering (og:image URL) | mitigate | CLOSED — parse gate `OpenGraphParser.kt:71-72` (`startsWith("http://"/"https://", ignoreCase=true)` post-`URI.resolve`); render re-gate `OgSourceCard.kt:241-242` strict `http(s)://` prefix (WR-01 fix); applied at BOTH call sites — card `OgSourceCard.kt:81` and sheet `SourcePreviewSheet.kt:83`; only gated strings reach Coil (`OgThumb model = imageUrl` at `OgSourceCard.kt:209`, called with `gatedImage` only) |
| T-58-02 | Tampering (stored OG XSS) | mitigate | CLOSED — title/description/URL render via plain Compose `Text()` only (`OgSourceCard.kt`, `SourcePreviewSheet.kt:69,96,111,121,143`); no `Html.fromHtml`, no `WebView` (grep clean) |
| T-58-03 | DoS (OG tag bloat) | mitigate | CLOSED — `OpenGraphParser.kt:26-27` constants `MAX_TITLE_CHARS=300`/`MAX_DESCRIPTION_CHARS=500`, enforced `take()` at `OpenGraphParser.kt:53-54` |
| T-58-04 | Info disclosure (signed image URLs) | mitigate | CLOSED — no `Log`/`Timber`/`println` in `OpenGraphParser.kt` (grep clean); no URL logging added in 58-02 UI files |
| T-58-SC (01) | Tampering (supply chain, plan 01) | accept | CLOSED — accepted risk documented: no package-manager installs in plan 01 (Jsoup/Room already in catalog); nothing to verify beyond absence, confirmed |
| T-58-05 | Info disclosure (Coil endpoint-auth leak) | mitigate | CLOSED — `WarpedApplication.kt:51-52` builds Coil's OWN bare `OkHttpClient` inside `newImageLoader` (only `followRedirects(true)`); zero references to app authed client/`AuthInterceptor`/interceptors (grep: no `addInterceptor`, no `AuthInterceptor` in file; comment at `:45` documents the invariant) |
| T-58-06 | Spoofing (og:image scheme at render) | mitigate | CLOSED — `gatedHttpImageUrl` strict `http(s)://` prefix `OgSourceCard.kt:238-244`; wired in card `:81` (`showThumb = gatedImage != null`) and sheet `:83` (`if (gatedImage != null && !headerImageFailed)`); regression-pinned by `OgSourceCardHelpersTest` (`https:foo`, `https:javascript:alert(1)` → null) |
| T-58-07 | Tampering (OG XSS at render) | mitigate | CLOSED — same evidence as T-58-02 at render layer; plain `AsyncImage` (`OgSourceCard.kt:209`, never `SubcomposeAsyncImage`) + plain `Text()` |
| T-58-08 | Spoofing (open-in-browser) | mitigate | CLOSED — `BrowserIntents.kt:27-28` case-insensitive allowlist (`equals(..., ignoreCase = true)`, WR-02 fix); guarded `ACTION_VIEW` `:37` with dual catch `ActivityNotFoundException` `:39` + `SecurityException` `:46` and user toasts; single shared gate for all three call sites |
| T-58-SC (02) | Tampering (Coil artifacts) | mitigate | CLOSED — Coil 3.4.0 AARs from Maven Central (group/artifact identity per RESEARCH.md audit, version lowered for compileSdk 35 ceiling); no npm/postinstall surface |

## Cross-cutting checks (prompt-specific)

| Check | Result | Evidence |
|-------|--------|----------|
| Jsoup parse-only, never connect | PASS | Only `Jsoup.parse(html, baseUrl)` at `OpenGraphParser.kt:32` (+ pre-existing parse calls in `HtmlToTextExtractor`/`HtmlToMarkdown`); `Jsoup.connect` grep clean in grounding sources (comment-literal deviation fixed in-commit `106e00c9`) |
| HTML-branch-only threading | PASS | `WebPageFetcher.kt:161-162` (`og = if (isHtml) parse(raw, currentUrl) else null`); plain/markdown → `openGraph = null` by construction; failures → `ModelOnly` carry no OG |
| Single v16 migration correctness | PASS | `Migrations.kt:141-145` exactly 3 nullable `ALTER TABLE grounded_sources ADD COLUMN` (no default, no index); `AppDatabase.kt:33` version=16; `DatabaseModule.kt:54` wires `MIGRATION_15_16`; `fallbackToDestructiveMigration(false)` at `:55` |
| OG columns through retry/replace path | PASS | `MultiUrlFetcher.kt:125-127` copies `openGraph` fields into details; `EntityMappers.kt:96-98,108-110` round-trip both directions; delete-then-insert carries OG identically |
| Disk cache sizing sane | PASS | `WarpedApplication.kt:63-64` fixed `50L*1024*1024` cap in `cacheDir/og_thumbnails` (not percent-based); memory `maxSizePercent 0.25` at `:57` |
| No SSRF-ish local-file image loads | PASS | `file://` grep across `app/src/main/java/com/warped` clean; both gates reject anything not `http(s)://`, so `file:`/`content:`/`data:`/`javascript:` from network data resolve to null → text-only fallback |

## Unregistered Flags

None — both plans declare `## Threat Flags: None`, and no new attack surface was introduced outside the registered threats.

## Accepted risks log

- T-58-SC (plan 01 scope): no installs — accepted by design, no residual risk.
- Visual-only follow-ups (on-device Coil fetch, long-text ellipsis pixels): graceful-degradation by construction (silent text-only fallback), non-blocking per VERIFICATION.md.
