# Quick Task Plan — Citation taps + YouTube oEmbed
Date: 2026-09-29
Scope: `20260929-citation-taps-youtube-oembed`
Out of scope: generic oEmbed providers (YouTube only), autoplay/embeds, download changes, loop/search mechanics.

## Locked decisions (NON-NEGOTIABLE)
- **A. Clickable citations:** `[N]` markers in assistant answers become taps → open the EXISTING single-source preview drawer (`SourcePreviewSheet`) for source N (individual drawer per source — consistent with card tap). Multi markers like `[1, 5]`: EACH number individually tappable. Out-of-range N (model hallucination) → tap ignored, no crash. Implementation via annotated strings in the markdown renderer; needs the turn's source list + a tap callback plumbed from `MessageBubble` (which already owns preview state). Styling: keep marker text identical, add subtle underline/accent affordance (coral underline, app language — `0xFFD97757` drawer accent / or `MaterialTheme.colorScheme.primary`; executor picks the theme-correct token, marker glyphs byte-identical). Must not break existing markdown tests; update them.
- **B. YouTube titles/thumbnails via oEmbed:** when host is `youtube.com` / `youtu.be` (locked set: `youtube.com`, `www.youtube.com`, `youtu.be`, `m.youtube.com`) AND `og:title`/`og:image` still null after normal enrichment, fetch `https://www.youtube.com/oembed?url=<encoded-video-url>&format=json` (same stripped client, tight timeout ~3s, small cap, http(s) only, failures → keep favicon fallback). Map `title`→`ogTitle`, `thumbnail_url`→`ogImageUrl` (gated http(s)), `author_name` IGNORED (title/image only). Same path benefits cards + sheet header + grid automatically (all read the same `og_*` columns).
- **C. Tests:** citation annotation mapping tests (incl. `[1,5]` split, out-of-range ignore, no-source turns render plain), oEmbed success/failure/timeout/non-YouTube-skip tests with fake HTTP (no real sockets in unit tests), existing markdown/sheet tests updated.

## Verified wiring points (read 2026-09-29, do NOT re-derive)
- `app/src/main/java/com/warped/ui/chat/components/MarkdownText.kt` — `MarkdownText()` renders `MarkdownBlock.TextBlock` / `ListItemBlock` via `parseInlineMarkdownAsAnnotatedString()` → `AnnotatedString.Builder.parseInlineStyles()` (handles `**bold**`, `*italic*`, `` `code` ``; `[N]` markers are plain text today). All rendering goes through plain `Text(annotated)` — no `ClickableText`, no string annotations. Citation work hooks HERE.
- `app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt` — assistant answer at L199-208 (`MarkdownText(text = message.content…)` inside `SelectionContainer`); preview state at L237-238 (`previewSource: GroundedSource?`, `previewNumber: Int`); source list at L231-233 (`fuenteItems(details, legacyUrls)`); sheet host at L330-344 (`SourcePreviewSheet(source, number, onDismiss, onOpenBrowser)` with dismiss-only-on-launch). Tap callback plumbs from HERE into `MarkdownText`.
- `app/src/main/java/com/warped/ui/chat/components/SourcePreviewSheet.kt` — `FuenteItem(number = i+1 …)` (1-based fetch-block order), `previewForTap(details, index)` (OK → details, omitida/out-of-range → null, sheet never opens). Citation N maps 1-based into `fuenteList`; resolve via `previewForTap(sourceDetails, n-1)`.
- `app/src/main/java/com/warped/data/grounding/SearchOgEnricher.kt` — fan-out enrichment with locked budgets (3 s total, 2 s connect/read per-call, 64 KB cap, max 5 URLs, 1 manual redirect, http(s) pre+post gate, stripped client minus `AuthInterceptor`, `headSupplier` test seam, `CancellationException` rethrows). oEmbed fallback hooks at the END of `fetchHead()` merge: only when scraped+original `ogTitle`/`ogImageUrl` still null AND host matches. Reuse `client` (already stripped) + `isHttpUrl` gate.
- `app/src/main/java/com/warped/data/grounding/OpenGraphParser.kt` — parse-only, never opens sockets; `resolveHttpUrl` http(s)-only terminal. oEmbed `thumbnail_url` must pass the same-class gate (reuse `gatedHttpImageUrl` semantics — http(s) only).
- `app/src/main/java/com/warped/ui/chat/components/BrowserIntents.kt` — `openUrlInBrowser` http(s) allowlist pattern to mirror for any new fetch URL construction. oEmbed request URL is always `https://www.youtube.com/oembed?…` (constant base) with the video URL as an encoded query param.
- `app/src/main/java/com/warped/ui/chat/components/OgSourceCard.kt` L317-376 — `ogDisplayTitle` / `gatedHttpImageUrl` / `faviconFallbackUrl`; no changes needed (oEmbed lands in `og_*` columns upstream, render falls out).
- Accent token: coral `0xFFD97757` (`NavGraph.kt` `DrawerAccent`); prefer `MaterialTheme.colorScheme.primary` if it resolves to the app coral in theme, else the literal — executor verifies against theme, marker text unchanged either way.

---

## Task 1 — Clickable citation markers → SourcePreviewSheet (Workstream A)

**Files:**
- Modify: `app/src/main/java/com/warped/ui/chat/components/MarkdownText.kt`
- Modify: `app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt`
- Modify (tests): `app/src/test/java/com/warped/ui/chat/components/MarkdownTextInlineParsingTest.kt`
- Touch only if needed: `app/src/androidTest/java/com/warped/ui/chat/components/MarkdownTextTest.kt`, `app/src/test/java/com/warped/ui/chat/components/MarkdownParserTest.kt` (update, do not delete coverage)

**Action:**
1. In `MarkdownText.kt`, extend the inline parser so citation markers `[N]` / `[1, 5]` / `[1,5]` produce per-number string annotations WITHOUT changing visible text: match regex `\[(\d+(?:\s*,\s*\d+)*)\]` in `parseInlineStyles` (skip matches inside `` `code` `` spans and fenced `CodeBlock`s — citations in code stay plain). For each number, `addStringAnnotation(tag = "citation", annotation = "<n>", start, end)` over exactly that number's range (multi-marker: one annotation per number, brackets/commas unannotated) plus a subtle `SpanStyle` (underline + accent color per Locked A; marker glyphs byte-identical). Add an optional param `onCitationClick: ((Int) -> Unit)? = null` to `MarkdownText` (+ `parseInlineMarkdownAsAnnotatedString` overload/default `null` so existing callers compile unchanged); when non-null, render the annotated content with tap-to-annotation-offset resolution (Compose `ClickableText` or `Text` + pointer tap resolving `getStringAnnotations("citation", offset, offset)` → `annotation.toIntOrNull() → onCitationClick(n)`). Out-of-range/non-numeric resolutions are the CALLER's job (ignore) — the renderer always fires the parsed int. No-source turns (`onCitationClick == null`) render byte-identical plain output (no annotations, no style change).
2. In `MessageBubble.kt`, wire the assistant-answer `MarkdownText` call (L199-208) with `onCitationClick = { n -> previewForTap(sourceDetails, n - 1)?.let { previewSource = it; previewNumber = n } }` — i.e. 1-based N into `fuenteList` order, omitida/out-of-range (`null`) → ignored, no crash. Legacy rows (no `sourceDetails`) resolve against `fuenteList` the same way (construct `GroundedSource(url)` as the card path does). Do NOT lift `previewSource`/`previewNumber` state; do NOT touch reasoning/user bubbles (citations only in assistant content; reasoning bubble keeps `onCitationClick = null`). Preserve long-press-copy (`combinedClickable` parent) and `SelectionContainer` behavior — if `ClickableText`-in-`SelectionContainer` breaks selection/copy, fall back to `Text` + `pointerInput` tap-offset resolution and note it in the summary. Reason: `MessageBubble` already owns the sheet + numbering; the renderer only reports ints.
3. Update existing markdown tests for the new param/annotations (they must stay green with the new affordance); add mapping tests per must_haves. No new deps, no string resources needed (no new visible copy).

**Must_haves:**
- [ ] Single `[2]` tap opens `SourcePreviewSheet` for source 2 (same sheet + number badge as card tap).
- [ ] `[1, 5]` renders each of `1` and `5` as an independent tap target (brackets/commas not tappable; visible text unchanged).
- [ ] Out-of-range tap (e.g. `[99]` with 3 sources) is ignored — no crash, no sheet.
- [ ] No-source / `onCitationClick == null` turns render plain (zero `citation` annotations).
- [ ] Citations inside inline/fenced code do NOT become taps.
- [ ] Existing `MarkdownText*` tests green (updated, not deleted).

**Verify:**
- `cd /var/home/cotizcesar/Documents/warped && ./gradlew :app:testDebugUnitTest --tests "com.warped.ui.chat.components.MarkdownTextInlineParsingTest" --tests "com.warped.ui.chat.components.MarkdownParserTest"` green.

---

## Task 2 — YouTube oEmbed title/thumbnail fallback (Workstream B)

**Files:**
- Modify: `app/src/main/java/com/warped/data/grounding/SearchOgEnricher.kt`
- Create: `app/src/main/java/com/warped/data/grounding/YoutubeOembed.kt` (pure helper: host allowlist, request-URL builder, JSON parser — no sockets, JVM-testable)
- Modify (tests): `app/src/test/java/com/warped/data/grounding/SearchOgEnricherTest.kt` (extend; add `YoutubeOembedTest.kt` if helper warrants its own file)

**Action:**
1. Create `YoutubeOembed.kt`: `YOUTUBE_HOSTS = setOf("youtube.com", "www.youtube.com", "youtu.be", "m.youtube.com")` with `isYouTubeUrl(url)` (parse `java.net.URI(url).host?.lowercase()`, exact set membership — subdomains other than `www`/`m` do NOT match; non-http(s) → false); `oembedRequestUrl(videoUrl)` returning `https://www.youtube.com/oembed?url=<URLEncoder.encode(videoUrl, UTF-8)>&format=json`; `parseOembed(json)` returning `(title: String?, thumbnailUrl: String?)` via `org.json.JSONObject` opt-strings (blank → null; `author_name` deliberately unread). Reason: keeps socket/JSON-edge logic out of the enricher and unit-testable with zero I/O.
2. In `SearchOgEnricher.fetchHead()`, AFTER the normal head-fetch+parse merge, add: if merged `ogTitle == null && ogImageUrl == null` (still null — note `OpenGraphParser` falls back title→host, so check the SCRAPED result nullness, not the merged display title; locked trigger is "still null after normal enrichment") AND `isYouTubeUrl(original.url)` AND `isHttpUrl(original.url)`, then fetch the oEmbed JSON with the SAME stripped `client` (per-call timeouts already 2 s; total fan-out bound already 3 s — do not add new timeout knobs), small body cap (≤ 16 KB, same chunked discipline as `fetchHeadHttp`), http(s)-only, same `User-Agent`. Map `title`→`ogTitle`, `thumbnail_url`→`ogImageUrl` ONLY when each passes its gate (`title` non-blank trimmed ≤ `OpenGraphParser.MAX_TITLE_CHARS`; `thumbnail_url` non-blank + http(s) per `gatedHttpImageUrl` semantics). `author_name` ignored. ANY failure (non-200, bad JSON, timeout, non-http thumb, `CancellationException` aside) → keep the pre-oEmbed row (favicon fallback path untouched). `CancellationException` still rethrows. Reason: same-path enrichment means cards + sheet header + grid pick it up with zero render changes; failures must never fail the turn.
3. Tests with fake HTTP only (extend the existing `headSupplier` seam + add an `oembedSupplier: (suspend (url: String) -> String?)?` seam mirroring it — production null/real-fetch; tests script it; NO real sockets in unit tests): success maps title+thumb; oEmbed failure/timeout → originals kept; non-YouTube URL never triggers oEmbed (supplier uncalled); YouTube URL WITH good OG never triggers oEmbed (supplier uncalled); non-http(s) thumbnail gated to null; `author_name`-only JSON leaves row unchanged. Update existing `SearchOgEnricherTest` expectations only if the merge contract changes (it must not).

**Must_haves:**
- [ ] YouTube URL (`youtube.com`/`www`/`youtu.be`/`m`) with null OG after normal enrichment gets `title`→`ogTitle`, `thumbnail_url`→`ogImageUrl` from fake oEmbed JSON.
- [ ] oEmbed transport failure / timeout / malformed JSON → row unchanged (turn still grounds, favicon fallback intact).
- [ ] Non-YouTube URL never calls the oEmbed path (verified via uncalled fake).
- [ ] YouTube URL with present OG never calls the oEmbed path.
- [ ] Non-`youtu.be`-family hosts (e.g. `music.youtube.com`, `youtube-nocookie.com`, `notyoutube.com`) do NOT match.
- [ ] No real sockets in unit tests (all oEmbed HTTP faked).
- [ ] `author_name` never lands in any column.

**Verify:**
- `cd /var/home/cotizcesar/Documents/warped && ./gradlew :app:testDebugUnitTest --tests "com.warped.data.grounding.SearchOgEnricherTest" --tests "com.warped.data.grounding.YoutubeOembedTest" --tests "com.warped.data.grounding.OpenGraphParserTest"` green.

---

## Global verify (both tasks)
- `cd /var/home/cotizcesar/Documents/warped && ./gradlew :app:assembleDebug` green.
- `cd /var/home/cotizcesar/Documents/warped && ./gradlew :app:testDebugUnitTest` FULL suite green.

## Honest notes / on-device confirmation needed (no adb in this env)
- Tap feel (touch target size on `[N]` spans, underline visibility in light/dark, interaction with text selection/long-press copy) needs on-device confirmation.
- Real YouTube thumbnail rendering (Coil load of `i.ytimg.com` URLs, card + sheet header + grid) needs on-device confirmation with network.
- Real oEmbed latency impact on search turns (extra fetch inside the 3 s fan-out bound) should be sanity-checked on-device; worst case the outer timeout keeps originals.
