# Phase 58: OpenGraph Thumbnails - Context

**Gathered:** 2026-09-29
**Status:** Ready for planning
**Mode:** Inline discuss (3 areas + layout reference, all accepted)

<domain>
## Phase Boundary

Every grounded source renders a rich thumbnail card with its OpenGraph data. Fetch captures og:title/description/image (parse-only, same policy) persisted with source rows via Room migration v15→v16; Coil loads images with disk cache (offline-safe); cards render per source with tap → preview sheet; sheet shows the OG header. Reference layout: horizontal card (user-provided mock) adapted to app neutrals.

</domain>

<decisions>
## Implementation Decisions

### OG Scrape
- Capture og:title, og:description, og:image during fetch (Jsoup parse-only, same stripped-client policy; no extra socket — same document)
- Fallback chain: og:title → <title> → host; description/image nullable (text-only card when absent)
- Only http(s) image URLs accepted (same allowlist as link targets); images NEVER fetched at scrape time (Coil loads on demand)

### Card Layout (locked from user mock)
- Horizontal card: thumbnail left (rounded, ~64-72dp) | middle title (1 line) + description (2 lines, ellipsis) | right [N] badge + open-in-browser icon shortcut
- Card language: 2B2B29 container, rounded-12dp, neutral text, coral accents only (badge/open icon) — zero purple
- Tap card → preview sheet (existing); open icon = guarded ACTION_VIEW shortcut (same allowlist + dual catch as sheet button)
- Omitida sources: no card (struck row convention stands); failed image load → text-only card (no error state)

### Persistence + Image Loading
- Single migration MIGRATION_15_16: og_title/og_description/og_image_url nullable columns on grounded_sources
- Coil dependency (new — first image loader; justified: thumbnails are the feature) with disk cache so preview works offline; memory + disk sizing conservative for low-RAM devices
- Hydration extends existing load path (rows → details → cards); retry writes (replaceSources) carry OG columns identically

### the agent's Discretion
- Coil version + cache sizing numbers (repo conventions + low-RAM caution)
- Exact thumbnail dp within 64-72dp range, placeholder/shimmer choice while loading
- Sheet OG header arrangement reusing card pieces where sensible

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `data/grounding/HtmlToTextExtractor.kt` / new `HtmlToMarkdown.kt` — Jsoup document already parsed; OG meta tags read from the same document (zero new sockets)
- `data/local/db/` — AppDatabase v15, Migrations chain, GroundedSourceEntity/Dao, Migration14To15StaticTest pattern to mirror for v16
- `data/repository/ChatRepositoryImpl.kt` — saveMessageWithSources / replaceSources / loadConversation hydration (extend row shape)
- `ui/chat/components/MessageBubble.kt` — Fuentes list (cards replace/augment per-source rows; omitida struck stays), SourcePreviewSheet (OG header slot)
- `ui/chat/components/ModelSelector.kt` — ModalBottomSheet pattern (already used by sheet)
- Phase 53 WR-01 context: per-source snapshot retained for consumers — OG rides the same rows

### Established Patterns
- Room entity + DAO + mapper extensions; single-migration-per-phase; MigrationTest + static gate precedent
- Hilt modules per domain; Coil will need an ImageLoader singleton (check Singleton/SingletonComponent conventions)
- English copy; neutral/coral visuals; 4dp spacing scale

### Integration Points
- WebPageFetcher/HtmlToMarkdown — OG extraction point (same parse)
- grounded_sources rows — write (fetch + retry) / read (hydration) paths extended
- MessageBubble Fuentes → OgSourceCard list; SourcePreviewSheet header
- Phase 54 retry + Phase 57 remote loop read the same rows (contracts unchanged, columns additive)

</code>

<specifics>
## Specific Ideas

- User mock: horizontal product card (thumb left, title+desc center, price+button right) → mapped to thumb + title/desc + [N] badge + open icon; dark neutral + rounded
- Coil disk cache doubles as the offline story for already-seen thumbnails

</specifics>

<deferred>
## Deferred Ideas

- Video/audio OG tags (og:video) — text/image only in v2.4
- Non-web tools (globally out of scope)

</deferred>
