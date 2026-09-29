# Phase 58: OpenGraph Thumbnails - Research

**Researched:** 2026-09-29
**Domain:** OpenGraph parsing (Jsoup) + Room migration + Coil 3 image loading + Compose cards
**Confidence:** HIGH

## Summary

Phase 58 adds rich OpenGraph thumbnail cards to every grounded source. The work splits into three independent layers that converge in the UI: (1) a pure-Kotlin OG parser reading `og:*` meta tags from the already-fetched HTML document (zero new sockets, same stripped-client policy), (2) three nullable columns on `grounded_sources` via a single `MIGRATION_15_16` following the exact `Migration14To15StaticTest` precedent, and (3) Coil 3 (new dependency, first image loader) with a disk cache for the offline thumbnail story.

Coil 3.6.3 is verified current on Maven Central (Sept 2026) and its official docs prescribe the exact `coil-compose` + `coil-network-okhttp` artifacts, the `SingletonImageLoader.Factory`-on-Application singleton pattern, and the `MemoryCache`/`DiskCache` builder API used below. `WarpedApplication` already exists as the `@HiltAndroidApp` entry point, so singleton wiring is a small additive change. The OG parse point is unambiguous: a second `Jsoup.parse` of the already-capped raw HTML string inside `WebPageFetcher.fetch` (or a helper it calls) — `HtmlToMarkdown.convert` strips `meta` tags internally, so OG must be read from a fresh parse, not threaded through the markdown pipeline.

**Primary recommendation:** Add `OpenGraphData` + `OpenGraphParser` (pure Kotlin, JVM-testable) called from `WebPageFetcher.fetch` on the HTML path only; thread OG through `GroundingResult.Grounded` → `MultiUrlFetcher.details` → `GroundedSource` (defaulted params, existing callers green) → entity/mappers → `MIGRATION_15_16`; add Coil 3.6.3 (`coil-compose` + `coil-network-okhttp`), configure the singleton in `WarpedApplication` via `SingletonImageLoader.Factory` with a conservative fixed disk cap; build `OgSourceCard.kt` + sheet OG header per the UI-SPEC verbatim.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Capture og:title, og:description, og:image during fetch (Jsoup parse-only, same stripped-client policy; no extra socket — same document)
- Fallback chain: og:title → <title> → host; description/image nullable (text-only card when absent)
- Only http(s) image URLs accepted (same allowlist as link targets); images NEVER fetched at scrape time (Coil loads on demand)
- Horizontal card: thumbnail left (rounded, ~64-72dp) | middle title (1 line) + description (2 lines, ellipsis) | right [N] badge + open-in-browser icon shortcut
- Card language: 2B2B29 container, rounded-12dp, neutral text, coral accents only (badge/open icon) — zero purple
- Tap card → preview sheet (existing); open icon = guarded ACTION_VIEW shortcut (same allowlist + dual catch as sheet button)
- Omitida sources: no card (struck row convention stands); failed image load → text-only card (no error state)
- Single migration MIGRATION_15_16: og_title/og_description/og_image_url nullable columns on grounded_sources
- Coil dependency (new — first image loader; justified: thumbnails are the feature) with disk cache so preview works offline; memory + disk sizing conservative for low-RAM devices
- Hydration extends existing load path (rows → details → cards); retry writes (replaceSources) carry OG columns identically

### the agent's Discretion
- Coil version + cache sizing numbers (repo conventions + low-RAM caution)
- Exact thumbnail dp within 64-72dp range, placeholder/shimmer choice while loading
- Sheet OG header arrangement reusing card pieces where sensible

### Deferred Ideas (OUT OF SCOPE)
- Video/audio OG tags (og:video) — text/image only in v2.4
- Non-web tools (globally out of scope)
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| OG-01 | Fetch captures OpenGraph tags (title, description, image URL) alongside extraction; persisted with source rows (Room migration v16) | OpenGraphParser pattern + GroundingResult/GroundedSource threading + MIGRATION_15_16 shape + static gate |
| OG-02 | Each grounded source renders a thumbnail card in chat (Coil-loaded image, title, description, tap → preview sheet; graceful text-only fallback when no image) | Coil 3 AsyncImage pattern + OgSourceCard wiring into MessageBubble Fuentes + existing preview state reuse |
| OG-03 | Preview sheet shows the OG header (image + title + description) above the extracted text | Sheet header extension pattern reusing card pieces; insertion point above existing HorizontalDivider |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| OG meta parsing | Data layer (grounding) | — | Pure function over fetched HTML; same trust boundary as extraction |
| OG persistence | Data layer (Room) | — | Columns on grounded_sources; entity/mapper/migration only |
| Thumbnail fetching + caching | UI layer (Coil singleton) | — | On-demand image loads with disk cache; no repository involvement |
| Card + sheet rendering | UI layer (Compose) | — | Pure render over hydrated GroundedSource; zero I/O per Phase 53 precedent |
| Hydration (rows → cards) | Data layer (repository) | — | Extends existing loadConversation/delete-then-insert paths |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `io.coil-kt.coil3:coil-compose` [VERIFIED: Maven Central metadata + official docs] | 3.6.3 | `AsyncImage` composable for thumbnails | Official Coil docs prescribe exactly this artifact for Compose UI; Coil 2.x line frozen at 2.7.0 since July 2024 |
| `io.coil-kt.coil3:coil-network-okhttp` [VERIFIED: Maven Central metadata + official docs] | 3.6.3 | OkHttp-backed network fetching for Coil | Official getting-started page lists this alongside coil-compose as the standard pair |
| Jsoup (existing) [VERIFIED: repo + Maven Central] | 1.23.2 (no change) | `Jsoup.parse` for OG meta extraction | Already the parse-only engine; Maven Central confirms 1.23.2 is latest — no bump needed |
| Room (existing) | 2.8.5 | v15→v16 migration | Unchanged; single ALTER-based migration per phase convention |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| None | — | — | No supporting libraries needed; Coil ships its own DiskCache/MemoryCache |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Coil 3.6.3 | Coil 2.7.0 (`io.coil-kt:coil-compose`) | 2.x is frozen (no updates since 2024-07); 3.x is the maintained line with identical AsyncImage ergonomics. No reason to take the dead line. |
| Coil | Glide / manual OkHttp + BitmapFactory | Glide is View-centric with heavier Compose interop; hand-rolled caching reimplements disk LRU, memory pressure handling, and request dedup — classic Don't Hand-Roll. |
| Fresh `Jsoup.parse` for OG | Thread `Document` through HtmlToMarkdown | Refactoring the frozen extractor pipeline risks Phase 52 exit gates (adversarial suite + budget assertions); a second in-memory parse of the capped string costs microseconds and touches nothing frozen. |

**Installation:**
```kotlin
// gradle/libs.versions.toml
coil = "3.6.3"
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }
coil-network-okhttp = { group = "io.coil-kt.coil3", name = "coil-network-okhttp", version.ref = "coil" }
```
```kotlin
// app/build.gradle.kts
implementation(libs.coil.compose)
implementation(libs.coil.network.okhttp)
```

**Version verification:** `coil-compose` 3.6.3 / `coil-network-okhttp` 3.6.3 confirmed via Maven Central `maven-metadata.xml` (`<latest>3.6.3</latest>`, `lastUpdated 20260918`) on 2026-09-29 [VERIFIED: Maven Central]. Coil 2 line latest is 2.7.0 (2024-07) — frozen. Jsoup 1.23.2 confirmed latest on Maven Central — matches repo, no bump.

## Package Legitimacy Audit

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| `io.coil-kt.coil3:coil-compose` 3.6.3 | Maven Central | ~10 yrs (Coil since 2020; coil3 group since 2024) | Mass-adoption (default Android image loader) | github.com/coil-kt/coil | N/A (see note) | Approved |
| `io.coil-kt.coil3:coil-network-okhttp` 3.6.3 | Maven Central | same | same | github.com/coil-kt/coil | N/A (see note) | Approved |

**Note on slopcheck:** `slopcheck install io.coil-kt.coil3:coil-compose` returned `[SLOP]` — this is a **false positive from scope mismatch**: slopcheck queries the **npm** registry, where a Maven coordinate trivially does not exist. Legitimacy for JVM artifacts was instead established by (1) official docs at `coil-kt.github.io/coil` prescribing these exact coordinates, and (2) Maven Central metadata confirming the artifacts + versions exist. No postinstall-script risk applies (Gradle/AAR artifacts, no npm lifecycle scripts).

**Packages removed due to slopcheck [SLOP] verdict:** none (verdict inapplicable — wrong ecosystem registry)
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
paste URLs ──► UrlDetector ──► MultiUrlFetcher.fetchAll ──► N × WebPageFetcher.fetch(url)
                                                                     │
                                              ┌──────────────────────┼──────────────────────┐
                                              │ raw HTML (capped)    │                      │
                                              ▼                      ▼                      ▼
                                     HtmlToMarkdown.convert  OpenGraphParser.parse   (plain/md: skip OG)
                                              │                      │
                                              ▼                      ▼
                                     extracted text ──► GroundingResult.Grounded(block, url, text, openGraph?)
                                                                             │
                                                                             ▼
                                                              MultiUrlFetcher.details: GroundedSource(+ogTitle/ogDesc/ogImageUrl)
                                                                             │
                                              ┌──────────────────────────────┼──────────────────────────────┐
                                              ▼                              ▼                              ▼
                                   saveMessageWithSources          replaceSources (retry)          loadConversation hydrate
                                   (delete-then-insert)            (delete-then-insert,             (rows → details → cards)
                                                                   same row, OG identical)
                                                                              │
                                                                              ▼
                                                              MessageBubble Fuentes → OgSourceCard ──tap──► previewSource state
                                                                                                              │
                                                                                                              ▼
                                                                                                   SourcePreviewSheet + OG header
OgSourceCard thumbnail ──► Coil AsyncImage (singleton ImageLoader, disk cache) ──► on-demand HTTP, offline-safe
```

Reader trace: URL paste → fetch returns text + OG in one pass → OG rides `details` into Room rows → hydration rebuilds cards → Coil loads images lazily with disk cache → tap opens sheet with OG header.

### Recommended Project Structure

```
data/grounding/
├── OpenGraphParser.kt      # NEW — pure object: parse(html, baseUrl) → OpenGraphData
├── OpenGraphData.kt        # NEW (or inside parser file) — data class(ogTitle?, ogDescription?, ogImageUrl?)
├── WebPageFetcher.kt       # MODIFY — call parser on HTML path, attach to Grounded
├── GroundingResult.kt      # MODIFY — Grounded gains openGraph: OpenGraphData? = null
├── MultiUrlFetcher.kt      # MODIFY — details builder copies OG into GroundedSource
domain/model/
├── GroundedSource.kt       # MODIFY — ogTitle/ogDescription/ogImageUrl nullable defaults
data/local/db/
├── Migrations.kt           # MODIFY — add MIGRATION_15_16 (3× ALTER, nullable TEXT)
├── AppDatabase.kt          # MODIFY — version 15 → 16
├── entity/GroundedSourceEntity.kt  # MODIFY — 3 nullable columns
├── entity/EntityMappers.kt # MODIFY — toDomain/toEntity carry OG
di/
├── DatabaseModule.kt       # MODIFY — wire MIGRATION_15_16 into addMigrations
ui/chat/components/
├── OgSourceCard.kt         # NEW — horizontal card per UI-SPEC
├── MessageBubble.kt        # MODIFY — Fuentes clickable rows → OgSourceCard list; keep omitida struck rows
├── SourcePreviewSheet.kt   # MODIFY — OG header above HorizontalDivider
WarpedApplication.kt        # MODIFY — implement SingletonImageLoader.Factory (Coil singleton + cache config)
```

### Pattern 1: Same-document OG parse (no new socket)

**What:** `OpenGraphParser` does its own `Jsoup.parse(html, baseUrl)` over the already-downloaded, already-capped raw string, inside `WebPageFetcher.fetch` right after the body is read. It never touches the network.
**When to use:** Always for this phase — `HtmlToMarkdown.convert` strips `meta`/`link` tags at its first step, so OG tags are unrecoverable from its output; a second in-memory parse is the only non-invasive read point.
**Example:**
```kotlin
// Source: repo convention (HtmlToTextExtractor Jsoup.parse parse-only) + OG standard meta names
object OpenGraphParser {
    fun parse(html: String, baseUrl: String): OpenGraphData {
        if (html.isBlank()) return OpenGraphData()
        val doc = try { Jsoup.parse(html, baseUrl) } catch (_: Exception) { return OpenGraphData() }
        val title = doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim().orEmpty()
            .ifEmpty { doc.title().trim() }
            .ifEmpty { hostOf(baseUrl) }          // locked chain: og:title → <title> → host, never empty
        val description = doc.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: doc.selectFirst("meta[name=description]")?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }
        val image = doc.selectFirst("meta[property=og:image]")?.attr("content")?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: doc.selectFirst("meta[property=og:image:secure_url]")?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }
            ?: doc.selectFirst("meta[name=twitter:image]")?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }
        return OpenGraphData(
            ogTitle = title.take(300),
            ogDescription = description?.take(500),
            ogImageUrl = image?.let { resolveHttpUrl(doc, it) },   // null unless http(s)
        )
    }
}
```
Selector names (`meta[property=og:title]` etc.) are the OpenGraph protocol standard [ASSUMED — protocol knowledge, not re-verified against spec in-session; risk if wrong: cards show fallback title/host instead of OG title — visible in unit tests, trivially fixable]. `twitter:image` / `og:image:secure_url` secondaries are discretionary fallbacks (cheap, standard practice) — planner may drop them to the locked minimum (og:image only) with zero contract breakage since the column is nullable.

### Pattern 2: Relative-URL resolution + http(s) gate

**What:** Resolve the raw `content` against the document base URI and accept only `http`/`https` — mirroring the fetcher redirect gate (`WebPageFetcher.kt:124`) and the sheet browser gate (`MessageBubble.kt:306`).
**When to use:** Every image URL, before persisting. Non-http(s) (`data:`, `javascript:`, protocol-relative edge cases) → null, text-only card.
**Example:**
```kotlin
// Source: Jsoup abs:URL resolution convention + repo allowlist precedent
private fun resolveHttpUrl(doc: Document, raw: String): String? {
    val abs = try { doc.selectFirst("meta[property=og:image]")?.attr("abs:content")?.trim() } catch (_: Exception) { null }
        ?.takeIf { it.isNotEmpty() } ?: raw.trim()
    if (!abs.startsWith("http://", ignoreCase = true) && !abs.startsWith("https://", ignoreCase = true)) return null
    return abs
}
```
(`attr("abs:content")` resolves relative/srcset-agnostic URLs against the base URI passed to `Jsoup.parse(html, baseUrl)` [ASSUMED — standard Jsoup API from training; verify by unit test with a relative-URL fixture — if it misbehaves, fall back to manual `java.net.URI.resolve`, same gate].)

### Pattern 3: Coil 3 singleton via Application (official Android route)

**What:** `WarpedApplication` implements `coil3.SingletonImageLoader.Factory`; Coil picks it up automatically as the singleton backing every `AsyncImage`. One `ImageLoader` for the whole app = one memory cache + one disk cache (per official docs: "Coil performs best when you create a single ImageLoader and share it").
**When to use:** Exactly this — do NOT create per-card ImageLoaders, do NOT pass a Hilt-provided loader into composables (AsyncImage defaults to the singleton).
**Example:**
```kotlin
// Source: https://coil-kt.github.io/coil/image_loaders/ (singleton + caching sections)
@HiltAndroidApp
class WarpedApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)   // Coil default; explicit for low-RAM auditability
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("og_thumbnails"))
                    .maxSizeBytes(50L * 1024 * 1024) // fixed cap, NOT percent: matches 50MB SSE precedent, bounded on 32GB low-end devices
                    .build()
            }
            .crossfade(true)
            .build()
}
```
Exact `PlatformContext` vs `Context` parameter type and `maxSizeBytes` builder name are [ASSUMED — from training on Coil 3 API; verify against IDE/compiler at implementation; the docs-verified parts are the Factory-on-Application pattern, `MemoryCache.Builder().maxSizePercent`, and `DiskCache.Builder().directory().maxSizePercent()`]. Prefer `maxSizeBytes` fixed cap over the docs' `maxSizePercent(0.02)` example: 2% of a 128GB device is ~2.5GB — unacceptable for a thumbnail cache on a local-LLM app where storage is already pressured by GGUF files. 50MB holds ~500–1000 64dp thumbnails. Coil's own OkHttp instance (from `coil-network-okhttp`) is used deliberately — do NOT inject the app's base OkHttp client (carries `AuthInterceptor` + body logging; image hosts must never see endpoint auth or verbose logging).

### Pattern 4: AsyncImage with silent text-only fallback

**What:** `AsyncImage(model = ogImageUrl, ...)` rendered only when URL non-null; load failure collapses the thumbnail slot (no error painter, no retry) per locked "no error state".
**When to use:** Both `OgSourceCard` and the sheet OG header, identically.
**Example:**
```kotlin
// Source: https://coil-kt.github.io/coil/compose/ (AsyncImage section)
if (source.ogImageUrl != null) {
    var imageOk by remember(source.ogImageUrl) { mutableStateOf(true) }
    if (imageOk) {
        AsyncImage(
            model = source.ogImageUrl,
            contentDescription = null,   // card already has contentDescription; avoid double-announce
            contentScale = ContentScale.Crop,
            onError = { imageOk = false },   // collapse slot → text-only card, silently
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
        )
    }
}
// Loading placeholder: shimmer (locked by UI-SPEC) via `placeholder` + animateFloat pulse —
// AsyncImage has no built-in shimmer; implement as parent-box background behind AsyncImage,
// or SubcomposeAsyncImage `loading` slot. Prefer parent-box shimmer (avoids subcomposition cost in lists).
```
`onError` callback on `AsyncImage` is docs-verified ("additionally, it supports setting placeholder/error/fallback painters and onLoading/onSuccess/onError callbacks"). Shimmer implementation is agent's discretion per UI-SPEC.

### Pattern 5: Additive threading with defaulted params (zero-breakage)

**What:** Every shape change adds nullable/defaulted fields so the 300+ existing tests and Phase 54/57 callers compile untouched: `Grounded(openGraph: OpenGraphData? = null)`, `GroundedSource(ogTitle?/ogDescription?/ogImageUrl? = null)`, entity columns nullable with defaults.
**When to use:** All domain/entity/result changes in this phase. Same precedent as `MultiUrlResult.Fused.pageTexts/details` defaulted params.

### Anti-Patterns to Avoid
- **Fetching images at scrape time:** Never open a socket for `og:image` during fetch (no HEAD validation, no prefetch). Locked decision; Coil loads on demand. Violating this doubles per-source network cost and breaks the offline/timeout budget.
- **Threading Document through HtmlToMarkdown:** Do not refactor the frozen extractor to share one parse. Phase 52 exit gates (adversarial suite, 5× budget assertion) guard that pipeline — touch it and you own re-validation.
- **Per-card ImageLoader instances:** Each loader owns its own caches; N loaders = N disk caches + memory pressure on low-RAM devices. Singleton only.
- **Sharing the app OkHttp client with Coil:** Base client carries `AuthInterceptor` and body-level logging. Image CDN hosts must never receive endpoint Authorization headers. Let `coil-network-okhttp` build its own client.
- **Non-nullable OG columns or backfill defaults:** NULL = "no OG captured" (pre-58 rows, plain-text/markdown sources, Tavily rows). A non-null default would conflate "absent" with "empty" and break the text-only fallback branch.
- **`SubcomposeAsyncImage` in card lists:** Docs warn subcomposition is slower and unsuitable for performance-critical UI like lazy lists. Use `AsyncImage` + parent-box shimmer.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Image loading/caching/decoding | OkHttp + BitmapFactory + manual LRU | Coil 3 (`AsyncImage` + singleton loader) | Memory pressure + disk LRU + request dedup + downsampling-to-view-size + lifecycle cancellation; hand-rolled versions OOM on low-RAM devices |
| OG meta parsing quirks | Regex over raw HTML | Jsoup `selectFirst("meta[property=...]")` | Case-insensitive attrs, entity decoding, single/double-quoted values, malformed markup — regex misses all of them |
| URL resolution | String concatenation of base + relative | Jsoup `abs:` attributes / `java.net.URI.resolve` | `../`, protocol-relative `//host`, `<base href>` overrides — all handled |
| Migration verification | Manual "it worked on my device" | `Migration15To16StaticTest` mirroring the v14→v15 static gate (schema JSON + captured execSQL + registration) | On-device `MigrationTest` is hardware-gated (no adb in CI); the static gate is the JVM-side proof |

**Key insight:** The only genuinely new capability is image loading — everything else (parse, persist, render) rides pipes built in Phases 50–54. Keep the diff additive and the new code pure-Kotlin where possible so it stays JVM-testable.

## Common Pitfalls

### Pitfall 1: OG parse silently disabled by content-type routing
**What goes wrong:** `WebPageFetcher.fetch` routes `text/plain` and `text/markdown` bodies away from HTML entirely — calling the parser there yields garbage or wasted cycles; worse, attaching OG unconditionally could stamp markdown bodies with a `<title>`-less empty object.
**Why it happens:** The OG call site sits next to the `HtmlToMarkdown.convert` branch but the other two branches share the function scope.
**How to avoid:** Call `OpenGraphParser.parse` ONLY in the HTML branch (missing content-type treated as html, same as extraction). Plain/markdown → `openGraph = null`.
**Warning signs:** Unit test with a markdown body asserting `openGraph == null` fails.

### Pitfall 2: Stale OG on retry / redirect mismatch
**What goes wrong:** Retry (`replaceSources`) writes rows from a fresh fetch — if the new `details` omit OG fields, old thumbnails linger (delete-then-insert deletes rows, so this is actually safe) — the real risk is `MultiUrlFetcher.details` building `GroundedSource` without copying the new OG through, silently nulling thumbnails after every retry.
**Why it happens:** `details` construction (`MultiUrlFetcher.kt:116-131`) maps `GroundingResult.Grounded → GroundedSource(url, text, OK)` positionally; a new field on `Grounded` doesn't flow unless the mapper is updated.
**How to avoid:** Update the `details` builder to copy `result.openGraph` fields in the same edit that adds them to `Grounded`. Add a JVM test: `fetchAll` with a fake fetcher returning OG → assert `details[i].ogImageUrl` set.
**Warning signs:** Thumbnails appear on first fetch, vanish after Stop→retry or after history reload.

### Pitfall 3: Tavily-sourced rows have no OG (null is normal)
**What goes wrong:** Phase 55 Tavily results flow through the same `GroundedSource` pipeline but never touch `WebPageFetcher.fetch` HTML parsing — their OG columns are always NULL. Treating null-OG as a bug triggers wasted "fix" work.
**Why it happens:** Two producers (web fetch vs Tavily API) share one consumer (cards).
**How to avoid:** Null OG = text-only card, by design. Do NOT add Tavily-side OG scraping in this phase (out of scope; Tavily API returns its own images only on higher tiers — unverified, ignore).
**Warning signs:** Planner writes a task to "fix missing OG for search results" — delete that task.

### Pitfall 4: Coil + R8/ProGuard + SQLCipher release build
**What goes wrong:** Release enables R8 (`isMinifyEnabled = true`); Coil 3 uses OkHttp + okio which are R8-safe via bundled rules, but verify the release build assembles — a missing keep rule surfaces as runtime image-load failure, not compile error.
**Why it happens:** New dependency + shrinking enabled.
**How to avoid:** Plan includes `./gradlew assembleRelease` (or at least `minifyReleaseWithR8` path) as a verification step. Coil ships consumer ProGuard rules [ASSUMED — standard for Square-family libraries; verify by successful release assemble].
**Warning signs:** Debug thumbnails work, release APK shows text-only cards.

### Pitfall 5: StrictMode disk-read violations from Coil in debug
**What goes wrong:** `WarpedApplication` enables `detectCustomSlowCalls` + VM leak detection in debug; Coil disk-cache reads on the main thread would log-penalty (penaltyLog only — no crash). Harmless but noisy.
**Why it happens:** Image loading touches disk cache; StrictMode watches.
**How to avoid:** Nothing — `AsyncImage` loads off-main by design; `penaltyLog` (not `penaltyDeath`) means worst case is logcat noise. Do not add `permitDiskReads` hacks.
**Warning signs:** Logcat StrictMode spam when scrolling Fuentes — expected, ignore.

### Pitfall 6: Description/title length unbounded from hostile pages
**What goes wrong:** A page ships a 50KB `og:description`; stored verbatim in Room TEXT, re-read on every history load, inflating DB + memory.
**Why it happens:** No cap at parse time; UI ellipsis only affects display.
**How to avoid:** Cap at parse: title 300 chars, description 500 chars (matches UI-SPEC backstop numbers: 200-char title / 500-char desc). Cheap `.take(n)`.
**Warning signs:** None visible — silent bloat. The caps are one line; just include them.

### Pitfall 7: `og:image` with query-string credentials
**What goes wrong:** Image URL contains `?hf_token=…` or signed params; Coil logs full URLs at debug via its own logging only if enabled (it isn't by default) — but the URL persists in Room and could surface in logs elsewhere.
**Why it happens:** Signed image URLs are common (CDN signatures).
**How to avoid:** The repo's `RedactingTree` already strips `hf_token/access_token/token/api_key` query params from Timber logs (WR-07) — image URLs benefit automatically. No extra work; just never log raw URLs in new code (use Timber with the redacting tree, never `println`).
**Warning signs:** New code calling `Log.d`/`println` with a URL — forbid in review.

## Code Examples

### OG parse call site in WebPageFetcher.fetch (HTML branch only)
```kotlin
// Pattern: parse same capped string, attach to Grounded; plain/markdown skip
} else {
    val markdown = HtmlToMarkdown.convert(raw, currentUrl, safeBudget)
    // Phase 58 (OG-01): OG reads the SAME document bytes — parse-only, zero new sockets.
    // HtmlToMarkdown strips meta tags internally, so parse raw afresh (microseconds, in-memory).
    val og = OpenGraphParser.parse(raw, currentUrl)
    val text = if (markdown.isNotBlank()) markdown
        else HtmlToTextExtractor.extract(raw, currentUrl, safeBudget)
    ...
    return@withContext GroundingResult.Grounded(block, currentUrl, sanitized, openGraph = og)
}
```
Note: `extracted.isBlank()` check stays — OG attaches only to successful grounding (failed extract → ModelOnly, no OG row). Omitida rows keep null OG by construction.

### MIGRATION_15_16 (exact shape)
```kotlin
// Source: MIGRATION_14_15 precedent (Migrations.kt:116-135) — ALTER-only, nullable, no defaults
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE grounded_sources ADD COLUMN og_title TEXT")
        db.execSQL("ALTER TABLE grounded_sources ADD COLUMN og_description TEXT")
        db.execSQL("ALTER TABLE grounded_sources ADD COLUMN og_image_url TEXT")
    }
}
```
Exactly 3 statements (mirrors the "migration SQL exactness" static test style: assert `statements.hasSize(3)`). No index (no query filters on OG columns), no FK change, no backfill (NULL = absent). Plus: `AppDatabase` version 16, `DatabaseModule.addMigrations(..., MIGRATION_15_16)`, KSP regenerates `16.json` on build (schemaLocation already wired), freeze `15.json` untouched.

### Entity + mapper extension
```kotlin
// GroundedSourceEntity: add 3 nullable columns with defaults (old-code reads stay green)
@ColumnInfo(name = "og_title") val ogTitle: String? = null,
@ColumnInfo(name = "og_description") val ogDescription: String? = null,
@ColumnInfo(name = "og_image_url") val ogImageUrl: String? = null,

// EntityMappers: extend both directions (drop-unknown precedent stands; OG needs no validation —
// URL gate already applied at parse; defense-in-depth: sheet/Coil re-check http(s) at render)
fun GroundedSourceEntity.toDomain(): GroundedSource = GroundedSource(
    url = resolvedUrl, extractedText = extractedText, status = status.toGroundedSourceStatusSafe(),
    ogTitle = ogTitle, ogDescription = ogDescription, ogImageUrl = ogImageUrl,
)
fun GroundedSource.toEntity(messageId: Long, sourceIndex: Int): GroundedSourceEntity =
    GroundedSourceEntity(..., status = status.toStorage(),
        ogTitle = ogTitle, ogDescription = ogDescription, ogImageUrl = ogImageUrl)
```

### OgSourceCard skeleton (UI-SPEC values verbatim)
```kotlin
// ui/chat/components/OgSourceCard.kt — Row: 64dp thumb | weight(1f) title+desc | badge+open column
// container 2B2B29 → MaterialTheme.colorScheme.surfaceContainer? NO — spec locks CARD #FF2B2B29 dark /
// #FFF3F4F6 light. Check Color.kt for existing tokens first; hardcode ONLY if no token matches
// (kill-purple precedent: grep-gate for purple). Title 14sp/600/1-line, desc 14sp/400/2-line,
// badge = existing Surface(primary 15% tint) construction copied from SourcePreviewSheet:73-85,
// open icon = Icons.Outlined.OpenInNew 20dp glyph / 48dp hit area / coral tint.
// Whole card clip(medium).clickable(role=Button) → onOpenSheet(); icon consumes tap → onOpenBrowser(url)
// with the SAME allowlist + dual-catch block as MessageBubble.kt:305-329 (extract to shared helper!).
```
Strong recommendation: extract the guarded `openUrlInBrowser(context, url)` handler from `MessageBubble` into a shared `ui/chat/components/BrowserIntents.kt` (or similar) so card icon + sheet button + future callers share one allowlist/catch/toast block — three copies of a security gate is a drift bug waiting to happen.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Coil 2 (`io.coil-kt:coil-compose`) | Coil 3 (`io.coil-kt.coil3:coil-compose`) | 2024–2025 (Coil 3 stable) | 2.x frozen at 2.7.0; new work targets 3.x group IDs — using 2.x coordinates today is tech debt on arrival |
| `rememberCoilImageLoader` / manual loader passing | `SingletonImageLoader.Factory` on Application | Coil 3 docs (current) | Official Android-best-practice route; Hilt module providing ImageLoader is unnecessary indirection |
| `Jsoup.connect()` fetching | `Jsoup.parse(string)` only | Project policy since v2.2 (STACK anti-pattern table) | OG parsing must never introduce a connect() call — grep-gate it like the existing policy |
| Multi-migration phases | Single migration per phase | Phase 53 precedent | v15→v16 is exactly one `Migration(15,16)` with 3 ALTERs |

**Deprecated/outdated:**
- Coil 2.x artifacts (`io.coil-kt:coil-compose:2.7.0`): frozen line, do not adopt.
- `SubcomposeAsyncImage` for list thumbnails: docs-flagged slow path; use `AsyncImage`.
- Coil 3 `coil-bom`: exists but unnecessary for two artifacts — pin `coil = "3.6.3"` in the version catalog instead (repo convention is per-artifact pins).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | OG `meta[property=...]` selector names/attribute casing follow the OpenGraph protocol standard | Pattern 1 | LOW — fallback chain (title→host) covers misses; unit tests with real-world HTML fixtures will catch |
| A2 | `twitter:image` / `og:image:secure_url` as secondary image fallbacks is acceptable scope | Pattern 1 | LOW — planner may cut to og:image-only; columns nullable so either choice is compatible |
| A3 | Jsoup `attr("abs:content")` resolves relative OG URLs against parse base URI | Pattern 2 | LOW — fallback to `URI.resolve` is one line; covered by a relative-URL unit test |
| A4 | Coil 3 `ImageLoader.Builder` param type (`PlatformContext` vs `Context`), `maxSizeBytes` name, `SingletonImageLoader.Factory` interface shape | Pattern 3 | LOW — compiler catches immediately; docs-verified parts (Factory-on-Application, Memory/DiskCache builders) are stable |
| A5 | Coil ships R8 consumer rules (release build safe) | Pitfall 4 | MEDIUM — mitigated by planned `assembleRelease` verification step; failure mode is visible (text-only cards) not silent corruption |
| A6 | 50MB fixed disk cap + 25% memory is "conservative for low-RAM" | Pattern 3 | LOW — numbers are discretionary per CONTEXT; any reasonable cap satisfies the requirement |

## Open Questions

1. **Should `og:description` fall back to `meta[name=description]`?**
   - What we know: Locked chain specifies `og:description` nullable; classic `<meta name="description">` is near-universal for SEO and costs one selector.
   - What's unclear: Whether the user considers non-OG description "OpenGraph data" (strict reading = og: only).
   - Recommendation: Include the fallback (more cards with descriptions = better feature); it never violates a locked decision since the column is nullable either way. Flag in planning for visibility.

2. **Title fallback for legacy/pre-58 rows?**
   - What we know: Pre-58 rows have NULL `og_title`; card needs a title — host-of-URL is the locked terminal fallback and is computable at render from `GroundedSource.url` with zero migration.
   - What's unclear: Nothing — this resolves cleanly.
   - Recommendation: `displayTitle = ogTitle ?: hostOf(url)` computed in the card/sheet (pure function, JVM-testable). No backfill, no migration change.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Maven Central (coil artifacts) | Coil dependency | ✓ (curl 200) | 3.6.3 | — |
| Jsoup 1.23.2 (repo) | OG parsing | ✓ (in catalog) | 1.23.2 = latest | — |
| Room KSP schema export | 16.json generation | ✓ (`room.schemaLocation` wired) | — | — |
| Java 17 toolchain | Build | ✓ (17.0.20.1) | — | — |

**Missing dependencies with no fallback:** none.
**Missing dependencies with fallback:** none.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | — |
| V3 Session Management | No | — |
| V4 Access Control | No | — |
| V5 Input Validation | Yes | http(s) allowlist on `og:image` at parse + re-check at render; `og:title/description` rendered as plain `Text` (never HTML/WebView); unknown status strings drop to OMITIDA (existing) |
| V6 Cryptography | No | — (SQLCipher passphrase handling untouched) |

### Known Threat Patterns for grounding + Coil

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| SSRF via `og:image` URL | Tampering / Info disclosure | Images NEVER fetched at scrape time (locked); Coil fetches on demand — Coil must use its OWN OkHttp instance, never the authed base client (AuthInterceptor must not reach image CDNs) |
| Malicious `og:image` scheme (`javascript:`, `data:` exfil) | Spoofing | Parse-time http(s) gate + render-time re-gate; Coil `model` only ever receives gated URLs |
| Stored XSS via `og:title` HTML entities | Tampering | Compose `Text()` renders plain text (no HTML interpretation); Jsoup `attr()` returns decoded-but-inert strings — never pass through `Html.fromHtml` or WebView |
| Endpoint API-key leak to image hosts | Info disclosure | Coil-owned OkHttp client (no AuthInterceptor); no shared cache dir with SSE cache (separate `og_thumbnails` dir) |
| DB bloat via hostile OG tags | Denial of service | 300/500-char caps at parse (Pitfall 6) |

## Sources

### Primary (HIGH confidence)
- https://coil-kt.github.io/coil/getting_started/ — artifacts (`io.coil-kt.coil3:coil-compose` + `coil-network-okhttp:3.6.3`), AsyncImage usage, singleton configuration routes
- https://coil-kt.github.io/coil/image_loaders/ — single-shared-ImageLoader guidance, `MemoryCache.Builder().maxSizePercent`, `DiskCache.Builder().directory().maxSizePercent()`
- https://coil-kt.github.io/coil/compose/ — AsyncImage args (placeholder/error/fallback, onLoading/onSuccess/onError), SubcomposeAsyncImage perf warning, `AsyncImagePainter.state` observation
- Maven Central `maven-metadata.xml` for `io.coil-kt.coil3:{coil-compose,coil-network-okhttp,coil-core,coil-svg}` (3.6.3, 2026-09-18) and `io.coil-kt:coil-compose` (2.7.0 frozen) and `org.jsoup:jsoup` (1.23.2) — fetched 2026-09-29
- Codebase: `WebPageFetcher.kt`, `HtmlToMarkdown.kt` (meta-strip line 35-37), `HtmlToTextExtractor.kt`, `MultiUrlFetcher.kt`, `GroundingResult.kt`, `GroundedSource.kt`, `GroundedSourceEntity.kt`, `EntityMappers.kt`, `ChatRepositoryImpl.kt` (save/replace/hydrate), `Migrations.kt` + `Migration14To15StaticTest.kt`, `AppDatabase.kt`, `DatabaseModule.kt`, `NetworkModule.kt` (50MB SSE cache precedent), `MessageBubble.kt` (browser gate 305-329), `SourcePreviewSheet.kt`, `WarpedApplication.kt`, `libs.versions.toml`, `app/build.gradle.kts`

### Secondary (MEDIUM confidence)
- None — all critical claims verified against primary sources above.

### Tertiary (LOW confidence)
- Coil 3 exact builder parameter names (`PlatformContext`, `maxSizeBytes`) — training knowledge, compiler-verified at implementation (A4)
- Jsoup `abs:` attribute resolution behavior — training knowledge, unit-test-verified at implementation (A3)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Coil 3.6.3 verified on Maven Central + official docs prescribe exact artifacts; Jsoup/Room versions confirmed in-repo
- Architecture: HIGH - every touchpoint read in the live codebase; parse point, threading path, migration shape, and UI wiring all mapped to exact files/lines
- Pitfalls: HIGH - grounded in Phase 52–54 precedents and locked CONTEXT decisions; security gates mirror existing allowlist code

**Research date:** 2026-09-29
**Valid until:** ~30 days (Coil/Jsoup/Room are slow-moving; re-check Coil version if implementation slips past Oct 2026)
