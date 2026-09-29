# PLAN — DDG Default Search + Sources Carousel

## Goal
`web_search` works with no API key (DuckDuckGo HTML primary, Tavily fallback only when keyed), and grounded-answer sources render as a horizontal carousel of compact cards.

## Locked decisions (NON-NEGOTIABLE)
- **A. DDG default, Tavily fallback:** DDG HTML endpoint `https://html.duckduckgo.com/html/?q=...`, desktop UA (mirror `WebPageFetcher.USER_AGENT`), same stripped-client policy as `WebPageFetcher` (remove `AuthInterceptor` so endpoint keys never leak). Parse-only. Tavily used ONLY when (a) DDG returns zero usable results or throws, AND (b) a Tavily key is stored. Unwrap `//duckduckgo.com/l/?uddg=<real>` → real URL; validate `http(s)` scheme; drop wrapper.
- **B. Identical producer shape:** DDG title + snippet → `(url, text)` pairs → `GroundingPrompt.buildFusedBlock` with OK/OMITIDA semantics (mirror `TavilySearchRepository.fuse()`); snippets through `WebContextSanitizer`. `MissingKey` disappears as a blocker: no-key + DDG-OK = silent success; no-key + DDG-fail = FETCH_FAILED path (no key nag). Key present-but-bad (401) keeps the invalid-key message.
- **C. Carousel:** Fuentes OG cards under a grounded answer become a horizontal `LazyRow` of compact cards reusing `OgSourceCard` pieces (thumbnail, title max 2 lines, `[N]` badge); tap thumb → browser (existing `openUrlInBrowser` rule), tap card → preview sheet; text-only sources render compact text cards (same fallback); omitida struck rows stay as-is; zero-ok → no block (unchanged). Fixed card width ~260–280dp so the row scrolls; vertical list removed.
- **D. Schema unchanged:** `web_search` ToolSet stays query-only; DDG→Tavily selection is internal to the search executor, invisible to the model.

## Out of scope
- Tavily removal (stays as fallback + Settings key UI remains), OG scrape changes, sheet contents, engine/provider loop mechanics.

## Honest notes
- DDG HTML scraping is inherently brittle: a markup change yields zero usable results → Tavily fallback covers keyed users; unkeyed users hit the fetch-failed path. Stated plainly in code comments.
- On-device visual + live DDG confirmation needs a screenshot (no adb in this env) — verify via build + unit tests.

---

## Workstream 1 — DDG client + executor + VM/tool-loop reroute (backend)

### Task 1.1: DuckDuckGoSearchRepository (client + parser + DDG-primary/Tavily-fallback executor)
- **Files:**
  - `app/src/main/java/com/warped/data/grounding/DuckDuckGoSearchRepository.kt` (new)
  - `app/src/test/java/com/warped/data/grounding/DuckDuckGoSearchRepositoryTest.kt` (new)
  - `app/src/main/java/com/warped/di/*.kt` (Hilt binding, whichever module owns `TavilySearchRepository`/`WebPageFetcher` singletons — add DDG repo alongside)
- **Action:**
  - New `@Singleton DuckDuckGoSearchRepository @Inject constructor(baseClient: OkHttpClient, apiKeyStore: ApiKeyStore, tavily: TavilySearchRepository)` (field-inject or constructor — follow the module's existing pattern; do NOT change `TavilySearchRepository` signature).
  - Derive an OkHttp client from `baseClient` exactly like `WebPageFetcher`: 10s connect / 15s read / 30s call, `followRedirects(false)`, strip `AuthInterceptor`, desktop Chrome UA header. GET `https://html.duckduckgo.com/html/?q=<urlencoded query>` with `Accept: text/html`. Reuse `WebPageFetcher.hasValidatedInternet`-equivalent offline gate: offline → `ModelOnly(OFFLINE)` with no socket (call `WebPageFetcher.hasValidatedInternet()` via injected instance if cheap, else duplicate the `ConnectivityManager` check — prefer injection, no new permissions).
  - Parser (pure function, unit-testable without network): extract result anchors from DDG HTML — result links are `//duckduckgo.com/l/?uddg=<urlencoded real>` wrappers. For each: read `uddg` query param → URL-decode → accept only `http`/`https` schemes (drop anything else + drop the wrapper itself); title = anchor text trimmed; snippet = nearest sibling snippet node text trimmed (parser must tolerate missing snippet → blank snippet = OMITIDA row, never crash on malformed HTML; use regex/Jsoup-free lightweight parsing — check what HTML parsing exists: `HtmlToMarkdown`/`HtmlToTextExtractor` use; if Jsoup is on the classpath reuse it, else regex on `<a ... class="result__a" ...>` shape with a comment noting brittleness + fallback covers it).
  - `fuse()` mirrors `TavilySearchRepository.fuse()` exactly: top-N (default 5, cap 10, same constants), text = `sanitize("$title\n$snippet")`, per-page budget via `GroundingBudget.perPageBudget`, blank url/text → OMITIDA `GroundedSource`, all-blank → `ModelOnly(FETCH_FAILED)`, else `Grounded(MultiUrlResult.Fused(...))` via `GroundingPrompt.buildFusedBlock`.
  - `search(query, maxResults, contextSize)` policy: (1) blank query → `ModelOnly(FETCH_FAILED)`; (2) DDG attempt (offline short-circuit first); (3) DDG yields ≥1 OK pair → return `Grounded`; (4) DDG empty/fail → check Tavily key via `apiKeyStore.getTavilyKey()`: key present → delegate to `tavily.search(...)` and return its outcome verbatim (including `InvalidKey`/`UsageLimit`/`MissingKey`-race); key absent → `ModelOnly(FETCH_FAILED)`. Never log query/key contents. `CancellationException` rethrows. Expose the SAME outcome type: reuse `TavilySearchOutcome` sealed interface (no new outcome type — keeps all callers compiling; `MissingKey` becomes nearly unreachable but stays for the key-race edge).
  - Query length cap: reuse `TavilySearchRepository.MAX_QUERY_CHARS` (take(500), verbatim, no rewriting).
  - Unit tests: parser fixtures (minimal DDG HTML with 2 wrapped results incl. `uddg` wrapper + 1 bare link + 1 non-http scheme dropped + 1 missing snippet); `uddg` unwrap test asserting real URL returned and wrapper host absent; fallback matrix with faked Tavily repo + faked HTTP (MockWebServer if on classpath, else fake the fetch layer via injectable fetcher lambda): DDG-ok+no-key→Grounded, DDG-ok+key→Grounded without Tavily call (verify Tavily mock never invoked), DDG-empty+key→delegates (Tavily Grounded passes through), DDG-empty+no-key→ModelOnly(FETCH_FAILED), DDG-throw+bad-key→InvalidKey passthrough, DDG-throw+no-key→ModelOnly(FETCH_FAILED).
- **Verify:** `./gradlew :app:testDebugUnitTest --tests "com.warped.data.grounding.DuckDuckGoSearchRepositoryTest"` green.
- **Done:** DDG repo returns `TavilySearchOutcome` with DDG-primary/Tavily-fallback policy; parser handles `uddg` unwrap + scheme validation; all matrix tests pass.

### Task 1.2: ChatViewModel branch + LocalToolLoop web_search reroute + copy updates
- **Files:**
  - `app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` (search branch ~lines 488–647)
  - `app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt` (`executeRemoteTool` web_search leg ~lines 334–355)
  - Local `LiteRT` web_search executor if one exists (grep `TavilySearchRepository` under `data/` — same reroute; if only OpenAIProvider + ChatViewModel call `tavily.search`, touch just those two)
  - `app/src/test/java/...` gate-matrix tests (existing Tavily/ChatViewModel search tests — update + extend)
- **Action:**
  - ChatViewModel: replace the `tavilySearchRepository.search(...)` call with the DDG repository's `search(...)` (same args, same `when (outcome)` arms). Keep `Grounded`/`ModelOnly`/`InvalidKey`/`UsageLimit` arms byte-identical. `MissingKey` arm: keep the arm (type still exists) but it is now unreachable in practice — leave rendering as-is for the key-race edge; update the branch comment (Phase 55 comment block) to state DDG-primary policy: no-key + DDG-OK = silent success (no notice), no-key + DDG-fail = FETCH_FAILED notice (no key nag). Do NOT change loop-armed skip logic, progress snapshots, persist path, or `GroundingPrompt.augment` usage. Constructor injection: add DDG repo alongside (do not remove Tavily — it stays injected as the fallback delegate inside the DDG repo).
  - `executeRemoteTool` (+ LiteRT equivalent if found): replace `tavily.search(...)` with DDG repo `search(...)` same args; `LocalToolLoop.mapSearchOutcome` call unchanged (same outcome type). Schema files (`WebSearchToolSet.kt`, `WebFetchToolSet.kt`) UNTOUCHED per decision D.
  - Copy: `TAVILY_MISSING_KEY` notice string stays (SettingsViewModel test-path + race edge) — no copy change needed unless a test asserts no-key→missing-key on the search path, in which case update that test to no-key+DDG-fail→FETCH_FAILED. Keep invalid-key message when key present-but-bad (planner-locked).
  - Tests: update gate-matrix tests — no-key no longer short-circuits pre-socket (DDG attempted); add DDG-ok-no-key→grounded case and DDG-fail-no-key→FETCH_FAILED case; keep InvalidKey/UsageLimit cases (now reachable only via fallback leg).
- **Verify:** `./gradlew :app:testDebugUnitTest --tests "com.warped.ui.chat.*" --tests "com.warped.data.grounding.*" --tests "com.warped.data.remote.provider.*"` green (adjust filters to actual test packages; full suite runs in Task 2.2).
- **Done:** Same signatures in/out everywhere; no-key users get DDG results silently; fallback invisible to the model; invalid-key copy preserved.

---

## Workstream 2 — Sources carousel (UI)

### Task 2.1: Horizontal sources carousel reusing OgSourceCard pieces
- **Files:**
  - `app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt` (Fuentes block ~lines 221–276)
  - `app/src/main/java/com/warped/ui/chat/components/OgSourceCard.kt` (extract reusable pieces or add compact-carousel variant)
  - `app/src/main/java/com/warped/ui/chat/components/SourcePreviewSheet.kt` (wiring only if needed — contents unchanged)
- **Action:**
  - Replace the vertical `fuenteList.forEachIndexed` column of full-width `OgSourceCard`s with: "Sources" header (unchanged) + `LazyRow` (`horizontalArrangement = spacedBy(8.dp)`, `contentPadding` 0) of compact cards, fixed width **272dp** (inside the locked 260–280dp band).
  - Compact card reuses `OgSourceCard` pieces — thumbnail (`AsyncImage` 64dp path), title `maxLines = 2` with `TextOverflow.Ellipsis`, `[N]` badge, open-in-browser `IconButton`; tap thumb → `openUrlInBrowser(context, url)` (existing rule), tap card body → `onPreview` (sets `previewSource`/`previewNumber`, sheet host below unchanged). Extract a shared internal composable if duplication exceeds ~30 lines; otherwise add `CompactSourceCard` in `OgSourceCard.kt` reusing its image/badge helpers.
  - Text-only sources (legacy `GroundedSource(url)` fallback + empty-extract rows): compact text card — same 272dp width, title falls back to host, same tap→sheet behavior (sheet shows the empty-extract copy + browser button, as today).
  - Omitida rows: stay as-is (struck `[N] url — skipped` text rows, no card) rendered BELOW the carousel (or interleaved positionally — simplest: carousel of clickables first, struck rows after; keep `fuenteItems` numbering stable so `[N]` matches fetch-block order).
  - Zero-ok → no block (unchanged Phase 50 behavior: the `fuenteList.any { it.clickable }` gate stays).
  - A11y: English content-descriptions only (`"Open source N: <title>"`, `"Preview source N"`), `Role.Button` where the full card already uses roles — mirror existing semantics, no new strings language. Colors: zero purple — reuse `OgCardDark`/M3 surfaceVariant neutrals already in `OgSourceCard`; no new color constants.
  - Do NOT touch sheet contents, OG scrape (`OG-01`), persist, or citation logic.
- **Verify:** `./gradlew :app:assembleDebug` green + visual check on-device (screenshot; no adb in this env — note honestly).
- **Done:** Grounded answers show a 272dp-card horizontal carousel; thumb→browser, card→sheet; omitida/zero-ok behavior unchanged; English a11y; no purple.

### Task 2.2: Full verification + brittle-surface notes
- **Files:** none (verification only; fix fallout in place if red)
- **Action:** Run `./gradlew :app:assembleDebug` then full `./gradlew :app:testDebugUnitTest`. Fix any red fallout in the touched files only (no drive-by refactors). Confirm no `TavilySearchRepository` signature changes, no ToolSet schema changes (`git diff --stat` shows only: new DDG repo + test, ChatViewModel branch, provider executor(s), MessageBubble/OgSourceCard).
- **Verify:** Both commands green.
- **Done:** Build + full unit suite green; PLAN honesty notes (DDG brittleness, no-adb visual) carried into the SUMMARY.

---

## Execution order
- Wave 1: Task 1.1 + Task 2.1 (independent files — parallel).
- Wave 2: Task 1.2 (needs 1.1's repo) + Task 2.2 (final gate; needs 1.2 + 2.1).
