---
audit_acknowledged:
  milestone: v2.5
  at: 2026-10-01
  status: unknown
---

# Summary — Citation taps + YouTube oEmbed

Date: 2026-09-29
Scope: `20260929-citation-taps-youtube-oembed`
Status: COMPLETE — both tasks implemented, full suite green (651 tests, 0 failures).

## What was built

**Task 1 — Clickable citation markers → SourcePreviewSheet** (`432be23d`)

- `MarkdownText.kt`: `parseInlineMarkdownAsAnnotatedString` gained optional
  `onCitationClick` + `citationStyle` params (existing 3-arg callers compile
  unchanged). When the callback is non-null, `[(N)]` / `[1, 5]` markers produce
  one `"citation"` string annotation per number over exactly the digit range
  (brackets/commas/spaces untouched, visible text byte-identical) plus an
  underline + `MaterialTheme.colorScheme.primary` affordance. Verified
  `PrimaryLight/PrimaryDark = 0xFFD97757` in `theme/Color.kt`, so the theme
  token resolves to the app coral — no literal needed. Null callback renders
  byte-identical plain output. Code spans are skipped (backtick branch consumes
  whole spans first); fenced `CodeBlock`s never reach the parser.
- New `CitationAwareText` composable resolves single taps via `pointerInput` +
  `TextLayoutResult.getOffsetForPosition` → `getStringAnnotations`. Deliberately
  NOT `ClickableText`, so the parent `SelectionContainer` long-press-copy keeps
  working.
- `MessageBubble.kt`: hoisted `sourceDetails`/`fuenteList`/`previewSource`/
  `previewNumber` above the content render and wired assistant-answer
  `MarkdownText` with `onCitationClick` → 1-based N into `previewForTap`
  (out-of-range/omitida → null → ignored, no crash); legacy rows construct
  `GroundedSource(url)` exactly like the card path. Null callback on no-source
  turns (no annotations, no affordance); reasoning/user bubbles untouched.

**Task 2 — YouTube oEmbed title/thumbnail fallback** (`5adc07e5`)

- New `YoutubeOembed.kt`: locked 4-host allowlist
  (`youtube.com`, `www.youtube.com`, `youtu.be`, `m.youtube.com`, exact match —
  `music.youtube.com` / `youtube-nocookie.com` / `notyoutube.com` rejected,
  non-http(s) rejected), constant-base request-URL builder
  (`https://www.youtube.com/oembed?url=<encoded>&format=json`), JSON parser
  returning `(title, thumbnail_url)` with blanks → null and `author_name`
  deliberately unread.
- `SearchOgEnricher.fetchHead`: after the normal merge, when originals are null
  AND no real scraped title/image survived (a scraped title equal to the URL
  host counts as missing, since `OpenGraphParser` falls back title→host) AND
  the URL is http(s) YouTube, one oEmbed fetch runs on the same stripped client
  (≤16 KB cap, no redirects, same UA). Title gated to non-blank ≤
  `MAX_TITLE_CHARS`; thumbnail gated to http(s). Any failure → pre-oEmbed row
  kept; `CancellationException` rethrows. New `oembedSupplier` seam mirrors
  `headSupplier` — zero sockets in unit tests. Cards + sheet header + grid pick
  it up via the unchanged `og_*` columns.

## Deviations from plan

1. **oEmbed JSON via kotlinx.serialization instead of org.json** (Rule 2 —
   correctness). `org.json` is an Android-framework stub that throws
   `RuntimeException("Stub!")` in local JVM unit tests, making the helper
   untestable per the plan's own "fake-HTTP tests only" gate. kotlinx.serialization
   (`kotlinx.serialization.json`, already in the app's dependencies and STACK.md)
   provides the identical opt-string contract and is JVM-safe. Pure helper file,
   no new dependency.
2. **Trigger check compares scraped title against URL host** (clarification of
   locked trigger, not a change). `OpenGraphParser.parse` never returns a null
   title for non-blank HTML (falls back to host), so "still null" is evaluated
   as: scraped title null (blank/unparseable HTML) OR scraped title == host.
   Documented inline in `fetchHead`.

No other deviations. No new dependencies, no string resources, no schema changes.

## Test results

- Targeted: `MarkdownTextInlineParsingTest` (12: 4 existing + 8 new),
  `SearchOgEnricherTest` (19: 11 existing + 8 new), `YoutubeOembedTest` (8 new),
  plus `MarkdownParserTest`, `OpenGraphParserTest`, `SourcePreviewMappingTest`
  (out-of-range/omitida mapping already covered there) — all green.
- Global: `./gradlew :app:assembleDebug` green;
  `./gradlew :app:testDebugUnitTest` FULL suite green — **651 tests,
  0 failures, 0 errors, 0 skipped**.
- `MarkdownParserTest` and the `androidTest` `MarkdownTextTest` needed no
  changes (new params default; existing callers compile unchanged).

## Coverage of plan must-haves

Task 1: single `[2]` → same sheet+number badge as card tap (same state path);
`[1, 5]` independent targets (test-asserted, brackets/commas unannotated);
`[99]` ignored via `previewForTap` null (test-asserted at both layers);
null-callback turns render plain (test-asserted zero annotations/styles);
code citations stay plain (test-asserted); existing tests green.
Task 2: YouTube null-OG → title+thumb mapped (test-asserted); failure/timeout/
malformed → row unchanged (test-asserted); non-YouTube never triggers
(test-asserted uncalled fake); YouTube-with-OG never triggers (test-asserted);
non-family hosts rejected (test-asserted); no sockets in tests (both seams
faked); `author_name` never lands in any column (test-asserted).

## On-device confirmation needed (no adb in this env)

Per plan §Honest notes, still open:

- Tap feel: touch-target size of `[N]` spans, underline visibility in
  light/dark theme, single-tap vs long-press-copy interaction inside
  `SelectionContainer`.
- Real YouTube thumbnail rendering (Coil load of `i.ytimg.com` URLs in card +
  sheet header + grid) with network.
- Real oEmbed latency impact inside the 3 s fan-out bound; worst case the outer
  timeout keeps originals.

## Self-Check: PASSED

- Created files exist: `YoutubeOembed.kt`, `YoutubeOembedTest.kt` — FOUND.
- Modified files exist with new symbols (`CITATION_TAG`, `CitationAwareText`,
  `oembedSupplier`, `fetchOembed`) — FOUND via compile + tests.
- Commits exist: `432be23d`, `5adc07e5` — verified in `git log`.
- No accidental deletions (`git diff --diff-filter=D HEAD~2 HEAD` empty).
