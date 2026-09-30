# PLAN — search-result titles + OG enrichment for source cards

## Objective

Search-grounded source cards show favicon + domain but no title and no og:image
(on-device evidence). Two locked fixes:

1. **Thread search-result titles into details** — DDG/Tavily `fuse()` build
   `GroundedSource(url, extractedText, status)` with `ogTitle = null` today
   (title only enters the fused prompt text). Verified dropped in code:
   `DuckDuckGoSearchRepository.kt:309-343`, `TavilySearchRepository.kt:149-183`.
   Card title line reads `ogDisplayTitle(source.ogTitle, source.url)` → host-only.
2. **Best-effort parallel OG enrichment post-fusion** — scrape
   `og:title`/`og:image`/`og:description` per OK-source URL. Fetched-page OG
   (`WebPageFetcher` HTML branch → `MultiUrlFetcher.details` builder) untouched;
   favicon stays the final render-side fallback (`OgSourceCard.kt:85-86` chain untouched).

**Hook placement (verified, covers all callers):** enrich inside the two
repository `search()` methods immediately after their own `fuse()` returns
`Grounded` — NOT in `ChatViewModel` (VM branch at `ChatViewModel.kt:599-607`
just consumes `fused.details`) and NOT in `fuse()` itself (keep it pure/sync).
This covers the VM pre-search branch AND tool-loop/`LocalToolLoop` search
callers for free. DDG→Tavily delegate passthroughs (fallback leg + keyed
image-direct leg) are already enriched by the Tavily leg — no double work.
`MultiUrlFetcher` (fetched-page path) untouched.

**Title precedence (locked):** scraped `og:title` wins when enrichment succeeds
(page-authoritative; note `OpenGraphParser.parse` never returns blank — falls
back `og:title → doc.title → host`), threaded search title is the fallback when
enrichment fails/is skipped, host remains the render fallback via the unchanged
`ogDisplayTitle`. `ogDescription`/`ogImageUrl` set only when scraped non-null.
No schema change: `og_title`/`og_description`/`og_image_url` columns and the
`EntityMappers` round-trip already exist (verified in
`GroundedSourceEntity.kt:41+`).

**Light-path decision (planner justification):** reuse of `WebPageFetcher.fetch`
rejected — it does a 256 KB full-body read + markdown extraction + sanitization
+ prompt-block build per URL, ~10-30 s budgets, all wasted for head-meta only.
New `SearchOgEnricher` does a capped head-fetch (OG tags live in `<head>`,
first bytes) + `Jsoup.parse` (never `Jsoup.connect`) + existing
`OpenGraphParser.parse`. Zero new dependencies.

## Locked budgets (planner numbers — extra latency on EVERY search turn)

- **Total: `withTimeout(3000)`** around the whole enrichment fan-out.
- Per-call client: connect 2000 ms / read 2000 ms.
- Body cap: **65536 bytes** streamed in 8 KB chunks (same pattern as `WebPageFetcher`).
- Max 5 OK URLs enriched per turn (fuse default 5; hard-take 5 even if fuse yields up to 10).
- Max **1 manual redirect**, `followRedirects(false)`, http(s) gate pre- AND post-redirect.
- Content-type: missing → treat as HTML (same as `WebPageFetcher`); present non-`text/html` → skip row.
- Stripped client: `baseClient.newBuilder()`, remove `AuthInterceptor`, desktop UA
  (`WebPageFetcher.USER_AGENT`), never log URL contents (status-only).
- Failures → keep original row (never fail the turn). `CancellationException`
  rethrows (Stop/new-turn cancel must propagate); only `TimeoutCancellationException`
  from the outer `withTimeout` → return originals. Per-URL child:
  `catch (e: CancellationException) { throw e }` then `catch (_: Exception) { original }`.

## Tasks

### Task 1 — Thread search titles into fuse() details (DDG + Tavily)

**Files:**
- `app/src/main/java/com/warped/data/grounding/DuckDuckGoSearchRepository.kt`
- `app/src/main/java/com/warped/data/grounding/TavilySearchRepository.kt`

**Action (per locked req 1):**
- In DDG `fuse()` OK branch: set `ogTitle = result.title.trim().take(OpenGraphParser.MAX_TITLE_CHARS).takeIf { it.isNotEmpty() }`.
  OMITIDA branches untouched (`ogTitle` stays null).
- Same in Tavily `fuse()` OK branch with `result.title`.
- No other field changes; fused prompt text construction byte-identical.
- Do NOT touch `ogDisplayTitle`/`ogHostOf`/render code.

**Verify:** new unit tests in Task 3 (title-threading cases) pass.

**Done:** OK rows in `fused.details` carry the search-result title; blank titles → null → host fallback at render, unchanged.

### Task 2 — SearchOgEnricher + repo hook wiring

**Files:**
- `app/src/main/java/com/warped/data/grounding/SearchOgEnricher.kt` (new)
- `app/src/main/java/com/warped/data/grounding/DuckDuckGoSearchRepository.kt` (hook + ctor param)
- `app/src/main/java/com/warped/data/grounding/TavilySearchRepository.kt` (hook + ctor param)

**Action (per locked req 2 + budgets above):**
- New `@Singleton SearchOgEnricher @Inject constructor(baseClient: OkHttpClient)`:
  - `suspend fun enrich(sources: List<GroundedSource>): List<GroundedSource>` —
    filters OK-status + http(s)-scheme rows, takes 5, `withTimeout(3000)` +
    `coroutineScope` + `async(ioDispatcher)` + `awaitAll`, zipped back by index
    (order-preserving). Non-OK / non-http(s) rows pass through untouched.
  - Per-URL head-fetch: stripped derived client (remove `AuthInterceptor`,
    desktop UA, `followRedirects(false)`), GET, ≤1 manual redirect with
    http(s) re-gate, content-type gate (missing = HTML, non-html = skip →
    return original), stream ≤65536 bytes in 8 KB chunks via okio `Buffer`
    (mirror `WebPageFetcher` lines 148-156), never an unbounded read.
  - Parse with `OpenGraphParser.parse(raw, url)` (`Jsoup.parse` only — grep must
    show zero `Jsoup.connect` in this file).
  - Merge: `ogTitle = scraped.ogTitle` (parser never blank — page-authoritative
    win), `ogDescription = scraped.ogDescription ?: original.ogDescription`,
    `ogImageUrl = scraped.ogImageUrl ?: original.ogImageUrl`. (Fused search title
    sits in `ogTitle` already from Task 1; a successful scrape overwrites it,
    a failed/skipped scrape keeps it.)
  - Cancellation: per-child `catch (e: CancellationException) { throw e }`
    before the generic catch; outer `catch (e: TimeoutCancellationException) →
    return originals`. `internal var ioDispatcher = Dispatchers.IO` and
    `internal var headSupplier: (suspend (url: String) -> Pair<String, String?>?)? = null`
    test seam (`Pair(rawHtml, contentType)`; null return = skip row). Production leaves it null (real fetch).
  - Status-only logging via Timber (never URL contents, never key material).
- Hooks: DDG `search()` — after own `fuse(pairs, contextSize)` returns
  `Grounded`, `copy(details = enricher.enrich(fused.details))` before returning
  (delegate-passthrough returns untouched). Tavily `search()` — same after its
  `fuse(...)` returns `Grounded`. `fuse()` signatures stay sync/pure.
- Ctor: add `enricher: SearchOgEnricher` to both `@Inject` constructors (Hilt
  needs no module). Grep main source for other construction sites and update.
- Explicit non-goals: no `ChatViewModel` change, no `MultiUrlFetcher` change,
  no `WebPageFetcher` change, no entity/mapper/DB change, no full markdown
  extraction.

**Verify:** `./gradlew :app:assembleDebug` green; `grep -rn "Jsoup.connect"
app/src/main/java/com/warped/data/grounding/SearchOgEnricher.kt` empty.

**Done:** Search turns enrich ≤5 OK rows within a 3 s total bound; any failure
keeps the original row and the turn still grounds.

### Task 3 — Tests (threading, enrichment, fallback chain, suite green)

**Files:**
- `app/src/test/java/com/warped/data/grounding/DuckDuckGoSearchRepositoryTest.kt` (update setUp + new cases)
- `app/src/test/java/com/warped/data/grounding/TavilySearchRepositoryTest.kt` (update setUp + new cases)
- `app/src/test/java/com/warped/data/grounding/SearchOgEnricherTest.kt` (new)
- Possibly `app/src/test/java/com/warped/ui/chat/components/OgSourceCardHelpersTest.kt` (fallback-chain additions)

**Action (per locked req 3):**
- Update both existing test `setUp`s for the new ctor param:
  `SearchOgEnricher(OkHttpClient()).apply { headSupplier = { null } }`
  (null-returning seam = enrichment no-op). **Every search() test must run with
  the seam set — grep-verify no test leaves `headSupplier` null, otherwise unit
  tests open real sockets.**
- Title threading: DDG via `htmlSupplier` fake with titled anchors → OK rows
  `ogTitle == anchor text`; Tavily via MockK `api` fake → `ogTitle == result.title`;
  blank-title result → `ogTitle == null`; fused prompt block assertions unchanged.
- Enrichment (`SearchOgEnricherTest`, `headSupplier` returning canned HTML):
  success → `ogTitle`/`ogImageUrl`/`ogDescription` set from meta tags;
  partial failure (one URL throws) → that row keeps threaded title, others enriched,
  result still full-size and order-preserving; timeout (supplier delays past the
  3 s bound — inject a shorter timeout via seam if needed, or delay > 3000 ms)
  → all rows original, no exception; non-HTML content-type → row skipped;
  non-http(s) URL → untouched; OMITIDA row → untouched.
- Fallback chain: `ogDisplayTitle(scrapedTitle, url) == scrapedTitle`;
  `ogDisplayTitle(null-threaded…, url)` covered by existing helper tests —
  add only missing links: enriched-og wins over threaded title at merge level
  (assert in enricher test: threaded `ogTitle` replaced by scraped value),
  favicon fallback (`faviconFallbackUrl` non-null when `ogImageUrl` null) and
  text-only (both null) already pinned — extend only if gaps found.
- Full run: `./gradlew :app:testDebugUnitTest` green + `./gradlew :app:assembleDebug` green.

**Verify:** `./gradlew :app:testDebugUnitTest` fully green; targeted new tests pass.

**Done:** Threading, enrichment success/partial/timeout, and the
og → threaded-title → favicon → text-only fallback chain are pinned; existing suite green.

## Out of scope (locked)

Image grid/modal, sheet changes, download path, providers/loop mechanics, budgets.
No render-code changes (`OgSourceCard`, `CompactSourceCard`, sheet untouched).

## Honest notes

- Enrichment adds bounded extra latency (≤3 s worst case, typically far less —
  head-fetch of ≤64 KB × up to 5 parallel) to **every** search turn, including
  tool-loop search turns via the repo-level hook.
- Live og:image quality (hotlink protection, oversized payloads at Coil load
  time) needs on-device confirmation — no adb in this environment; Coil +
  existing `onError → text-only collapse` is the safety net.
