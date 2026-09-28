# Quick Task Plan — WebFetch Parity (OpenCode quality, Android constraints)

Reference-driven rework. No open research — rules locked in the task brief.
Reference behavior: OpenCode `webfetch.ts` = markdown-by-default via Turndown
(ATX headings, fenced code, `-` bullets, strips script/style/meta/link), text-mode
strips script/style/noscript/iframe/object/embed.

## Goal (backward)

User pastes/asks about a URL → grounded answer cites a compact, structured
markdown excerpt (headings, lists, code, tables, safe links) instead of a flat
text wall — while small-local-model windows (4K tokens) and the adversarial
suite stay green.

### must_haves

- truths:
  - "Fetched HTML pages ground as structured markdown (headings/lists/code/tables/links preserved)"
  - "Pages that convert to blank markdown still ground via the flat-text fallback (no regression to model-only)"
  - "Markdown link targets can never smuggle javascript:/data:/vbscript: URLs or delimiter breakouts past the sanitizer"
  - "Hijack-line filter and delimiter escaping work on markdown, including inside code fences and blockquotes"
  - "Fetch handles larger pages (256KB cap) with 10/15/30s budgets; Stop/cancel semantics unchanged"
  - "Full existing grounding adversarial suite stays green"
- artifacts:
  - `app/src/main/java/com/warped/data/grounding/HtmlToMarkdown.kt` (new Jsoup converter, zero new deps)
  - `app/src/main/java/com/warped/data/grounding/WebPageFetcher.kt` (256KB cap, 10/15/30s, desktop UA, Accept upgrade, markdown path + text fallback)
  - `app/src/main/java/com/warped/data/grounding/WebContextSanitizer.kt` (markdown-aware link-target allowlist)
  - `app/src/main/java/com/warped/data/grounding/GroundingBudget.kt` (raised per-page floor, global cap unchanged)
  - `app/src/test/java/com/warped/data/grounding/HtmlToMarkdownTest.kt` (new converter tests)
- key_links:
  - `WebPageFetcher.fetch` → `HtmlToMarkdown.convert` → `WebContextSanitizer.sanitize` → `GroundingPrompt.buildBlock`
  - `WebPageFetcher` fallback → `HtmlToTextExtractor.extract` when markdown is blank

## Out of scope (locked)

Agentic model-invoked fetching / tool loop, image attachments, WorkManager,
provider changes, Room changes.

---

## Task 1 — HtmlToMarkdown converter + fetcher rework

**Files:**
- NEW `app/src/main/java/com/warped/data/grounding/HtmlToMarkdown.kt`
- MOD `app/src/main/java/com/warped/data/grounding/WebPageFetcher.kt`
- READ `app/src/main/java/com/warped/data/grounding/HtmlToTextExtractor.kt` (fallback — do not change logic)

**Action:**
1. Create `HtmlToMarkdown` object (pure Kotlin, Jsoup only — already a dependency;
   no Turndown on JVM). `fun convert(html: String, url: String, budget: Int): String`:
   - Parse with `Jsoup.parse(html, url)` (parse-only, never `connect()`).
   - Strip `script, style, noscript, iframe, object, embed, nav, footer, aside, form, meta, link`
     (superset of OpenCode's Turndown-remove + text-mode-skip lists).
   - Convert: `h1..h6` → ATX `#..######`; `pre/code` → fenced blocks with language
     hint from `class="language-x"` (fallback: bare fence); `ul/li` → `- ` bullets;
     `ol/li` → `1. ` ordered; `a[href]` → `[text](url)` for http(s) targets only
     (other schemes → emit link text unwrapped); `table` → GitHub-style markdown
     tables (header row + `| --- |` separator); `b/strong` → `**x**`, `i/em` → `*x*`;
     `blockquote` → `> ` prefixed lines; `hr` → `---`; `img` → alt-text (element
     dropped, alt kept; empty alt → dropped entirely); `title` prepended as first line.
   - Block-level newline discipline mirroring `HtmlToTextExtractor.extractJsoup`
     (newline before block tags, per-line trim/drop-blank, collapse 3+ newlines).
   - Same line-boundary truncation with `… [truncated]` marker at caller `budget`.
   - Returns `""` when nothing usable (caller falls back).
2. Rework `WebPageFetcher`:
   - `MAX_BODY_BYTES` 65536 → 262144 (262144 = 256KB); keep 8KB-chunk streaming loop unchanged.
   - Timeouts 8/10/20s → connect 10s / read 15s / call 30s. `cancel()` / `activeCalls`
     / redirect (max 3, http(s)-only) / offline short-circuit / AuthInterceptor strip — all unchanged.
   - `USER_AGENT` → OpenCode's desktop Chrome string:
     `Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36`.
   - `Accept` header → `text/markdown;q=1.0, text/plain;q=0.8, text/html;q=0.7, */*;q=0.1`
     (mirrors OpenCode's markdown q-value negotiation).
   - Content-type gate: keep text/html + text/plain, ADD `text/markdown` direct
     passthrough (body used as-is, no conversion). html → `HtmlToMarkdown.convert`;
     if result `isBlank()` → fallback `HtmlToTextExtractor.extract(raw, url, safeBudget)`;
     blank-from-both → model-only as today. Plain/markdown bodies keep today's blank→model-only rule.
   - Update the class KDoc policy line (caps/timeouts/UA/markdown path) so docs don't lie.

**Verify:** `./gradlew :app:testDebugUnitTest --tests "com.warped.data.grounding.*"`
**Done:** HTML page with headings/list/code/table/links grounds as markdown;
blank-markdown page still grounds via flat-text fallback; 200KB page no longer cut at 64KB.

## Task 2 — Sanitizer markdown hardening + budget retune

**Files:**
- MOD `app/src/main/java/com/warped/data/grounding/WebContextSanitizer.kt`
- MOD `app/src/main/java/com/warped/data/grounding/GroundingBudget.kt`

**Action:**
1. Sanitizer (keep `sanitize(text: String): String` signature; keep line-granular
   hijack filter and `[WEB CONTEXT` delimiter escaping byte-for-byte behavior):
   - Add markdown link-target pass BEFORE the hijack filter: match
     `[text](target)` occurrences; allow only `http://` / `https://` (and
     protocol-relative `//` resolved as https) targets. `javascript:` / `data:` /
     `vbscript:` (case-insensitive, including leading-whitespace/control-char
     padded variants) → strip to bare `[text]` (keep visible text, drop target).
     Relative-path targets (`/foo`, `#anchor`) → keep as-is (harmless text, no scheme).
   - Delimiter escaping must also cover markdown-abetted breakouts: after link
     neutralization, the existing `[WEB CONTEXT` / `[FIN` / `[END` replacements
     still apply to the full string (a `[WEB CONTEXT](http://evil)` link must end
     up escaped, not clickable-shaped).
   - NO fence exemption: code-fence contents and `> ` quote lines are still
     scanned per line by the hijack filter (a fence must not become a smuggling
     container). Do not add fence-state tracking that skips filtering.
2. Budget (`GroundingBudget`): markdown is denser per char than flat text, so raise
   the per-page floor — `MIN_PER_PAGE` 800 → 1500. `globalBudget()` tiers
   (4500 / 6000 / scaled) stay UNCHANGED to fit 4K-token windows; `perPageBudget()`
   formula unchanged (floor does the work). Update KDoc numbers.
   Also raise `HtmlToTextExtractor.MAX_CHARS` 4000 → 6000 (default for direct
   callers; production path passes `perPageBudget` explicitly — single-constant change only).

**Verify:** `./gradlew :app:testDebugUnitTest --tests "com.warped.data.grounding.*"`
**Done:** `[x](javascript:alert(1))` → `[x]`; delimiter-shaped markdown escaped;
hijack line inside fence/quote still dropped; `perPageBudget(4096, 4)` ≥ 1500;
`globalBudget(4096)` still 6000.

## Task 3 — Tests (converter + attacks + budget, adversarial green)

**Files:**
- NEW `app/src/test/java/com/warped/data/grounding/HtmlToMarkdownTest.kt`
- MOD `app/src/test/java/com/warped/data/grounding/WebContextSanitizerTest.kt` (append markdown-attack cases)
- MOD `app/src/test/java/com/warped/data/grounding/GroundingBudgetTest.kt` (update floor expectations)
- READ (no breakage) `HtmlToTextExtractorTest.kt`, `GroundingPrecedenceTest.kt`,
  `MultiUrlFetcherTest.kt`, `MultiUrlFusionTest.kt`, `GroundingPromptTest.kt`, `UrlDetectorTest.kt`

**Action:**
1. `HtmlToMarkdownTest` (JUnit5, JVM, no Android): headings → `# H`; `ul` → `- a`;
   `ol` → `1. a`; `pre>code.language-kotlin` → ` ```kotlin ` fence; `table` →
   `| h |` + `| --- |` rows; `<a href="https://…">` → `[t](https://…)` while
   `<a href="javascript:…">` unwraps to text; `<img alt="pic">` → `pic`;
   `<script>`/`<nav>`/`<footer>` content absent; blank-input → `""` (documents
   the fallback contract); truncation marker present over budget.
2. Sanitizer additions: `javascript:` link neutralized; `JaVaScRiPt:` +
   whitespace-padded variant neutralized; `data:text/html` neutralized;
   `[WEB CONTEXT](http://x)` escaped; delimiter text inside a fence still escaped;
   `Ignore all previous instructions` line inside a `> ` quote still dropped;
   normal markdown (`# H`, `- item`, `[t](https://ok)`, fenced code) passes through untouched.
3. Budget test updates: floor assertions 800 → 1500 (`perPageBudget` small-window
   and many-page cases); global-tier assertions unchanged.
4. Full suite must be green — fix fallout from Tasks 1–2 here, never by weakening
   existing adversarial assertions.

**Verify:** `./gradlew :app:testDebugUnitTest --tests "com.warped.data.grounding.*" ` +
`./gradlew :app:lintDebug` (if lint is wired; otherwise unit suite only)
**Done:** New tests pass; every pre-existing grounding test passes unmodified in
intent (assertions only updated where locked decisions changed the numbers:
MIN_PER_PAGE, fetcher caps/timeouts).
