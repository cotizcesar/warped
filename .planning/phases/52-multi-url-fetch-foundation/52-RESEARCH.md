# Phase 52: Multi-URL Fetch Foundation - Research

**Researched:** 2026-09-28
**Domain:** Parallel multi-URL web grounding (OkHttp fan-out, Jsoup parse-only extraction, global grounding budget)
**Confidence:** HIGH

## Summary

Phase 52 extends the proven v2.2 single-URL grounding pipeline (`data/grounding/`, 6 files, JVM-testable pure Kotlin) to 2–5 URLs per message. The work is backend-heavy: a fan-out orchestrator over the unchanged `WebPageFetcher.fetch()`, a `UrlDetector.allUrls()` extension, a Jsoup parse-only swap inside `HtmlToTextExtractor` (regex kept as fallback), a global model-window-aware grounding budget replacing the fixed 4000-char constant, numbered fused `[WEB CONTEXT 1..N]` blocks in `GroundingPrompt`, and N-source progress/partial-failure routing in `ChatViewModel` + the two existing UI surfaces (fetch chip, Fuentes list).

The single most important finding for planning: **`WebPageFetcher.cancel()` tracks one `activeCall` field — it is not safe for parallel fan-out.** The last-started call wins; earlier in-flight calls become uncancellable, breaking the single-cancel-path contract (Stop button / new-turn pre-cancel). The plan must replace the single field with a thread-safe set of active calls (or restructure cancel ownership into the fan-out orchestrator) before any parallel work ships.

Second key finding: **use `coroutineScope` + `async` + `awaitAll`, NOT `supervisorScope`.** `fetch()` only throws `CancellationException` (failures return `ModelOnly`), so `awaitAll` preserves partial-failure semantics naturally, while `coroutineScope` propagates cooperative cancellation to all siblings — exactly the locked single-cancel-path contract. `supervisorScope` would isolate cancellation per child and break Stop-cancels-all.

Third: **Jsoup 1.23.2 is confirmed as the latest stable on Maven Central** (metadata `latest`/`release` = 1.23.2, published Aug 2026) [CITED: repo1.maven.org/maven2/org/jsoup/jsoup/maven-metadata.xml]. Parse-only API (`Jsoup.parse(html, baseUri)`, `doc.select(...).remove()`, `doc.title()`, `doc.body().text()`) verified against official jsoup cookbook [CITED: jsoup.org/cookbook]. Jsoup needs no desugar NIO config at minSdk 28 for pure parsing — desugar is only required if the plan touches `java.nio` file APIs, which parse-only extraction does not.

**Primary recommendation:** Build a pure-Kotlin `MultiUrlFetcher` orchestrator (dedupe → cap-5 → single offline check → `coroutineScope` fan-out over `WebPageFetcher.fetch()` → per-page sanitize → budget-split → fused numbered blocks), fix `activeCall` into a concurrent set, swap the extractor core to Jsoup-with-regex-fallback, and drive the whole turn through the existing `ChatViewModel` hook position.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Multi-URL parallel fetch + cancel | API / Backend (data/grounding) | — | Network I/O, OkHttp ownership, Dispatchers.IO; pure Kotlin, JVM-testable |
| HTML→text extraction (Jsoup) | API / Backend (data/grounding) | — | CPU-bound parse on Dispatchers.Default or IO; pure Kotlin |
| Budget split + block fusion | API / Backend (data/grounding) | — | Deterministic string assembly; pure function, unit-tested |
| Progress chip (`Leyendo N de M…`) | Browser / Client (Compose UI) | — | Transient `ChatUiState.isFetchingWeb` observation only |
| Fuentes N-source list | Browser / Client (Compose UI) | — | Renders `groundedSources` already on the message; no persistence in Phase 52 |
| Offline detection | API / Backend (data/grounding) | — | `ConnectivityManager` check lives in fetcher; single upfront check for fan-out |

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Max 5 URLs per message — matches FETCH-01 2–5 range, bounds memory/time; 6th+ URLs ignored deterministically
- Parallel fan-out with async + awaitAll on Dispatchers.IO, single offline check upfront — reuses `WebPageFetcher.fetch()` per URL unchanged
- Dedupe preserving first-seen order, sources numbered in paste order — deterministic `[WEB CONTEXT 1..N]` / Fuentes ordering
- Single cancel path cancels all in-flight calls (Stop button / new-turn pre-cancel) — matches v2.2 UX, cooperative CancellationException rethrow
- Partial grounding: any ok page grounds the turn; dead links skipped in prompt but surfaced as omitidas in progress state — matches FETCH-02
- Model-only banner ONLY when ALL pages fail/offline; per-source reasons collapsed to worst-case (OFFLINE wins over FETCH_FAILED)
- Progress copy "Leyendo N de M…" with per-source ok/omitida states, no silent drops — extends `isFetchingWeb` chip (FETCH-03)
- `groundedSources: List<String>` extended to N URLs; Fuentes list renders all N in block order — no Room change in Phase 52 (persistence comes in Phase 53)
- Add Jsoup 1.23.2 + desugar NIO build config; parse-only (`Jsoup.parse`, never `Jsoup.connect()`) — per v2.3 STATE decision
- Fetch policy frozen: stripped client (no AuthInterceptor leak), 64KB cap streamed in 8KB chunks, 8/10/20s timeouts, 3 manual redirects, text/html + text/plain only — EXTRACT-01
- Extraction semantics parity + cleaner density: title prepend, script/style/noscript + nav/footer/aside strip, block-tag newlines, entity decode, line-boundary truncation with "… [truncado]" marker
- Blank Jsoup output falls back to the hand-rolled regex extractor; never model-only on parse miss alone
- Single global grounding budget (e.g. ~6000 chars) divided evenly across N pages, model-window-aware shrink for small local models — replaces fixed 4000/page constant (EXTRACT-02, LOW-confidence numbers validated on device)
- Numbered `[WEB CONTEXT 1..N]` fused prefix blocks; SYSTEM_PROMPT cites [1]/[2] markers; Fuentes order == block order
- Phase exit gate: multi-page adversarial suite (1-dead-of-3, all-dead, 5-URL cap, hijack-injection across pages with sanitizer per page) must pass
- Phase exit gate: 5× max-size budget assertion (5 pages × 64KB) proves fused block fits window; later extractor changes must re-pass

### the agent's Discretion
- Exact global budget constant and per-model window table values — pick from codebase model capability signals, keep LOW-confidence flag until device validation
- Coroutine fan-out structure (supervisorScope vs awaitAll failure semantics) — partial-failure behavior above is the contract
- Jsoup selector/strip list details beyond the agreed baseline — density wins as long as adversarial suite passes

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope. Global out-of-scope guardrails reaffirmed (no citation pills in generated text, no WorkManager periodic retry, no `Jsoup.connect()`, no provider interface changes).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| FETCH-01 | 2–5 URLs per message → fused answer in all fetchable pages (parallel fan-out, numbered blocks) | Fan-out pattern (coroutineScope+awaitAll), `UrlDetector.allUrls()` dedupe+cap-5, `GroundingPrompt` fused numbered blocks |
| FETCH-02 | Partial grounding; model-only banner only when ALL fail | Per-URL `GroundingResult` composition (any-Grounded-wins, worst-case reason collapse), sanitizer-per-page before fusion |
| FETCH-03 | Per-source progress, no silent drops | Extended progress state (`Leyendo N de M…`, ok/omitida per source) on existing `isFetchingWeb` chip |
| EXTRACT-01 | Jsoup 1.23.2 parse-only replaces regex core; fetch policy unchanged; never `Jsoup.connect()` | Jsoup parse-only API (verified), strip-selector baseline, regex-fallback contract, frozen fetch policy reference |
| EXTRACT-02 | Global grounding budget split across pages, model-window-aware | Budget-split formula keyed on `GenerationParameters.contextSize` (default 4096); LOW-confidence constants flagged for device validation |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| org.jsoup:jsoup | 1.23.2 [CITED: Maven Central metadata, latest=release=1.23.2, Aug 2026] | Parse-only HTML→Document (`Jsoup.parse`, selectors, `text()`) | Locked v2.3 STATE decision; latest stable; zero-config pure-Java parser, no native code, minSdk-28 safe |
| kotlinx-coroutines-core | 1.11.0 (existing catalog) | `coroutineScope` + `async` + `awaitAll` fan-out on Dispatchers.IO | Already the project standard; exact semantics needed for cancel-all + partial-failure contract |
| OkHttp | 4.12.0 (existing, via `WebPageFetcher`) | Per-URL fetch reusing frozen policy | Unchanged — fan-out reuses `fetch()` per URL, no new client config |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Truth + JUnit 5 | 1.4.5 / 5.14.4 (existing) | Adversarial multi-page suite + 5×max-size budget assertion | Phase exit gates; extend the 4 existing grounding test files |
| Timber | 5.0.1 (existing) | Per-URL failure logging in fan-out | Same pattern as `WebPageFetcher` failure paths |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Jsoup | Keep hand-rolled regex only | Rejected upstream (v2.3 STATE): regex misses nav/footer/aside density wins; Jsoup is the one sanctioned dep |
| `coroutineScope`+`awaitAll` | `supervisorScope` | Supervisor isolates child cancellation → breaks locked single-cancel-path; use only if cancel is re-implemented manually (don't) |
| Global budget split | Keep fixed 4000/page | 5 pages × 4000 = 20K chars overflows small local windows; the 5×max-size exit gate exists precisely to kill this |

**Installation:**
```bash
# Gradle (version catalog) — the ONLY new dependency in v2.3
# gradle/libs.versions.toml: jsoup = "1.23.2"
# library: jsoup = { group = "org.jsoup", name = "jsoup", version.ref = "jsoup" }
# app/build.gradle.kts: implementation(libs.jsoup)
```

**Version verification:** `org.jsoup:jsoup` 1.23.2 confirmed via Maven Central `maven-metadata.xml` (`<latest>1.23.2</latest>`, `<release>1.23.2</release>`, lastUpdated Aug 2026) [CITED]. No `npm view` equivalent applies — JVM Maven artifact, verified on the correct ecosystem registry.

## Package Legitimacy Audit

> slopcheck targets npm/PyPI registries and has no Maven-resolver path; for this JVM-only phase the equivalent gate is Maven Central metadata + official-docs provenance. No new npm/Python/Rust packages are installed.

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| org.jsoup:jsoup:1.23.2 | Maven Central | ~18 yrs (since 2009) [CITED: jsoup.org] | Ubiquitous ( East-Asian-mirror-independent canonical artifact) | github.com/jhy/jsoup (canonical, Jonathan Hedley) [CITED: jsoup.org] | N/A (JVM) | Approved — metadata latest==release==1.23.2 |

**Packages removed due to slopcheck [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none
**Postinstall risk:** N/A — JVM AAR/JAR has no postinstall scripts; verify no `Jsoup.connect()` usage in review (security boundary, see below).

## Architecture Patterns

### System Architecture Diagram

```
User message (2–5 URLs)
        │
        ▼
ChatViewModel.sendMessage() hook (after save, before helper resolution) [ChatViewModel.kt:269-294]
        │  UrlDetector.allUrls() → dedupe, first-seen order, cap 5
        │  single offline check upfront
        ▼
coroutineScope { urls.map { async(Dispatchers.IO) { fetcher.fetch(url) } }.awaitAll() }
        │  frozen policy per URL: stripped client, 64KB/8KB-chunk cap,
        │  8/10/20s timeouts, 3 redirects, html/plain only
        ▼
Per-page pipeline (× N, order restored to paste order):
  raw (≤64KB) → Jsoup.parse extract (fallback: regex) → per-page budget cut
        → WebContextSanitizer.sanitize (per page, pre-fusion)
        ▼
  Fusion: [WEB CONTEXT 1]…[WEB CONTEXT N] fused prefix block (global budget)
        ▼
  Routing: ≥1 Grounded → GroundingPrompt.augment() + groundedSources=[urls…]
           ALL ModelOnly → banner (worst-case reason: OFFLINE > FETCH_FAILED)
        ▼
  UI: chip "Leyendo N de M…" (transient) → Fuentes [1..N] under assistant message
```

### Recommended Project Structure

```
data/grounding/                  # existing — extend, no new package
├── WebPageFetcher.kt            # FIX: activeCall → concurrent set; expose offline check
├── MultiUrlFetcher.kt           # NEW: pure orchestration (dedupe/cap/fan-out/compose)
├── UrlDetector.kt               # EXTEND: allUrls() alongside firstUrl()
├── HtmlToTextExtractor.kt       # SWAP: Jsoup core + regex fallback
├── GroundingBudget.kt           # NEW (or companion): global budget split by contextSize
├── GroundingPrompt.kt           # EXTEND: buildFusedBlock() numbered 1..N
├── GroundingResult.kt           # EXTEND or compose: multi-result routing helper
└── WebContextSanitizer.kt       # UNCHANGED (already per-page safe)
```

### Pattern 1: Structured fan-out with cancel-all + partial failure
**What:** `coroutineScope` + per-URL `async(Dispatchers.IO)` + `awaitAll()`, restoring paste order by index. Individual results are `GroundingResult` values (never thrown except `CancellationException`), so `awaitAll` never fails-fast on a dead page — partial grounding falls out naturally. One `CancellationException` (Stop / new-turn pre-cancel) cancels the scope and therefore all siblings.
**When to use:** Always for this phase — it is the locked contract.
**Example:**
```kotlin
// Pattern: structured fan-out (standard kotlinx.coroutines usage)
val results: List<GroundingResult> = coroutineScope {
    urls.map { url ->
        async(Dispatchers.IO) { fetcher.fetch(url) }
    }.awaitAll()
}
// results[i] corresponds to urls[i]; compose: any Grounded wins,
// all-ModelOnly collapses to worst-case reason (OFFLINE > FETCH_FAILED).
```

### Pattern 2: Parse-only Jsoup extraction with regex fallback
**What:** `Jsoup.parse(html, baseUri)` → `doc.select("script, style, noscript, nav, footer, aside").remove()` → `title = doc.title()` prepend → `body.text()` (+ block separators) → entity decode is built-in → line-boundary truncation with existing `… [truncado]` marker. If the result is blank, delegate to the existing hand-rolled extractor; only blank-from-both yields `ModelOnly(FETCH_FAILED)` (never model-only on parse miss alone).
**When to use:** Inside `HtmlToTextExtractor.extract()`, behind the same signature — `WebPageFetcher` call site unchanged.
**Example:**
```kotlin
// Source: https://jsoup.org/cookbook/input/parse-document-from-string
//         https://jsoup.org/cookbook/extracting-data/attributes-text-html
val doc: Document = Jsoup.parse(html, url)          // parse-only, no network
doc.select("script, style, noscript, nav, footer, aside").remove()
val title: String = doc.title()
val bodyText: String = doc.body().text()            // normalized combined text
// NOTE: Element.text() normalizes/collapses whitespace — block-tag newline
// density must be reconstructed (see Pitfall 3).
```

### Pattern 3: Global budget split by model window
**What:** `perPage = globalBudget(contextSize) / N`. Window signal is `GenerationParameters.contextSize` (default 4096 [VERIFIED: codebase grep]); `LocalModelEntity.paramContextSize` defaults to 4096 likewise. Suggested starting table (LOW confidence — device validation required): context ≤ 4096 → global ~6000 chars; larger windows scale up; never below a per-page floor that makes a page useless (~800–1000 chars).
**When to use:** After per-page extraction, before fusion; truncation reuses the existing line-boundary + marker semantics per page.
**Example:**
```kotlin
// Pseudocode — exact constants are the agent's discretion, LOW confidence
fun globalBudget(contextSize: Int): Int =
    if (contextSize <= 4096) 6000 else 6000 + (contextSize - 4096) / 4
fun perPageBudget(contextSize: Int, n: Int): Int =
    (globalBudget(contextSize) / n).coerceAtLeast(MIN_PER_PAGE)
```

### Anti-Patterns to Avoid
- **Single `activeCall` field under fan-out:** last call wins, earlier calls leak uncancellable. Use a `ConcurrentHashMap.newKeySet<Call>()` (add on create, remove in `finally`, `cancel()` iterates) or move cancel ownership to the orchestrator.
- **`supervisorScope` for fan-out:** breaks Stop-cancels-all; `coroutineScope` is the correct scope.
- **`Jsoup.connect()` anywhere:** bypasses the stripped client, 64KB cap, and timeouts — a security-boundary violation and explicit out-of-scope item. Grep-gate it in review.
- **Per-page offline checks × N:** wasteful and racy; single upfront check, then fetch (each `fetch()` re-checks harmlessly — acceptable, no socket opened either way).
- **Sanitizing after fusion:** a hijack line referencing `[WEB CONTEXT` delimiters in page K could break block framing for all pages. Sanitize per page, pre-fusion (current `sanitize()` already escapes delimiters — keep calling it per page).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HTML parsing / tag stripping / entity decoding | Regex extensions beyond the fallback | Jsoup 1.23.2 parse-only | Entity tables, malformed-HTML recovery, nested-tag edge cases; regex already proven insufficient for nav/footer/aside density |
| Parallel fetch orchestration | Raw threads / custom executor | `coroutineScope`+`async`+`awaitAll` | Structured cancellation, exception routing, and testability (`runTest`) come free |
| URL extraction | New regex dialect | Extend `URL_REGEX` in `UrlDetector` | Existing punctuation-trimming semantics + test suite must be preserved verbatim |
| Prompt-injection scrubbing | New sanitizer | `WebContextSanitizer.sanitize` per page | Adversarial suite is calibrated against its exact patterns; changing both extractor and sanitizer at once voids the baseline |
| Context-window fitting | Tokenizer-based budgeting | Char-budget split (this phase) | No tokenizer dep on device; char budgets are the v2.2-established approximation — token-precise fitting is TUNE-01 territory (v2.4) |

**Key insight:** This phase swaps exactly one engine (regex → Jsoup) inside a frozen pipeline. Every surrounding invariant — fetch policy, sanitizer, truncation marker, hook position — stays fixed so the adversarial suite measures only the extractor delta.

## Common Pitfalls

### Pitfall 1: `activeCall` race under fan-out (CRITICAL)
**What goes wrong:** 5 parallel `fetch()` calls overwrite `activeCall`; Stop cancels only the latest; 4 sockets leak until 20s call-timeout, and the turn may still ground after the user hit Stop.
**Why it happens:** The field was designed for the single-URL v2.2 flow.
**How to avoid:** Replace with a concurrent set of active calls (add before `execute()`, remove in `finally`, cancel-all iterates a snapshot). Cooperative `CancellationException` rethrow stays as-is.
**Warning signs:** Stop-button UAT with 3+ slow URLs still shows grounding completing after cancel.

### Pitfall 2: `Element.text()` whitespace normalization kills block density
**What goes wrong:** `doc.body().text()` collapses all whitespace runs — headings, list items, and paragraphs merge into a wall of text, failing the density/adversarial bar and possibly the "cleaner answers" intent.
**Why it happens:** Jsoup's `text()` returns *normalized* combined text by design [CITED: jsoup.org cookbook].
**How to avoid:** Reconstruct block separators before calling `text()` (e.g. `doc.select("p, div, h1-h6, li, tr, br, section, article, blockquote, pre").prepend("\\n")` or iterate `body.wholeText()` per block element). The agent's-discretion strip/selector list covers this — require a density comparison test (Jsoup output vs legacy regex on the Wikipedia fixture) in the plan.
**Warning signs:** Extractor tests pass on assertions but fused answers read as unbroken paragraphs.

### Pitfall 3: Order instability across `awaitAll` with dedupe
**What goes wrong:** Results zip to wrong URLs; Fuentes order ≠ block order; `[1]` cites the wrong page.
**Why it happens:** Mapping results back by completion order instead of index.
**How to avoid:** `urls.map { async { fetch(it) } }.awaitAll()` preserves list order by construction; dedupe with `distinct()` (preserves first-seen order) *before* fan-out; number blocks from the deduped list index.
**Warning signs:** Fuentes `[N] url` mismatches the URL inside `[WEB CONTEXT N]`.

### Pitfall 4: Per-page truncation marker inflation eats the global budget
**What goes wrong:** Each of 5 pages appends `… [truncado]` + newline; markers + titles consume a non-trivial slice of the global budget, and naive `global/N` slicing double-truncates (page cut, then fusion cut).
**Why it happens:** Truncation applied at two levels independently.
**How to avoid:** Single truncation point: per-page cut at `perPageBudget` with marker; fusion concatenates without re-cutting (assert total ≤ global + N×marker overhead in the 5×max-size gate). Keep marker semantics identical to v2.2.
**Warning signs:** 5×max-size budget assertion fails by a few hundred chars.

### Pitfall 5: `6th+ URLs silently ignored` vs `omitida` confusion
**What goes wrong:** Over-cap URLs shown as "omitida" (they were never attempted) or under-cap failures not shown at all.
**Why it happens:** UI-SPEC distinguishes: 6th+ ignored deterministically and NOT surfaced; only attempted-but-failed URLs are `omitida`.
**How to avoid:** Progress state tracks the attempted set (≤5) with ok/omitida per item; cap-dropped URLs never enter the state.
**Warning signs:** "Leyendo 3 de 7…" copy or omitida rows for URLs never fetched.

### Pitfall 6: Desugar misconfiguration breaking the build
**What goes wrong:** Adding `coreLibraryDesugaring` unnecessarily (version conflicts, longer builds) or missing it if Jsoup touches `java.nio` paths on API 28.
**Why it happens:** Cargo-culting the CONTEXT "desugar NIO" line without checking need.
**How to avoid:** First try *without* desugar: Jsoup core parsing uses `java.lang`/`java.util` only on the parse path and minSdk is 28 (API 26+ already desugars most `java.time`/`java.util` APIs via built-in AGP support). Add `coreLibraryDesugaring` only if lint/build proves a missing API. Verify with `./gradlew :app:assembleDebug` + the JVM unit tests (which run on JDK, unaffected either way).
**Warning signs:** Build failure referencing `java.nio.file` from Jsoup classes — only then add desugar.

## Code Examples

Verified patterns from official sources:

### Parse-only extraction skeleton
```kotlin
// Source: https://jsoup.org/cookbook/input/parse-document-from-string
//         https://jsoup.org/cookbook/extracting-data/attributes-text-html
import org.jsoup.Jsoup

fun extractJsoup(html: String, url: String): String {
    val doc = Jsoup.parse(html, url) // NEVER Jsoup.connect() — security boundary
    doc.select("script, style, noscript, nav, footer, aside").remove()
    val title = doc.title().trim()
    // Rebuild block separators BEFORE normalized text() — see Pitfall 2
    doc.select("p, div, h1, h2, h3, h4, h5, h6, li, tr, section, article, header, blockquote, pre").prepend("\n")
    val body = doc.body()?.text().orEmpty()
        .lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
    val combined = if (title.isNotEmpty()) "$title\n$body" else body
    return WebContextSanitizer.sanitize(combined) // per page, pre-fusion
}
```

### Numbered fused blocks (extends GroundingPrompt)
```kotlin
// Extends existing GroundingPrompt.buildBlock(url, text) single-source format
fun buildFusedBlock(pages: List<Pair<String, String>>): String =
    pages.mapIndexed { i, (url, text) ->
        "[WEB CONTEXT ${i + 1} — fuente [${i + 1}]: $url]\n$text\n[FIN WEB CONTEXT ${i + 1}]"
    }.joinToString("\n\n")
// SYSTEM_PROMPT must cite [1]/[2] markers — extend existing SYSTEM_PROMPT,
// keep the "never invent URLs" guard verbatim.
```

### allUrls() extension (same regex style)
```kotlin
// Extends UrlDetector.URL_REGEX semantics: same pattern, same trailing-
// punctuation trim, distinct() preserves first-seen order, take(5) caps.
fun allUrls(text: String, max: Int = 5): List<String> =
    URL_REGEX.findAll(text)
        .map { it.value.trimEnd('.', ',', ';', ':', '!', '?') }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(max)
        .toList()
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `firstUrl()` single fetch, 2nd+ URLs ignored | `allUrls()` fan-out, cap 5 | This phase (52) | Pasted multi-link messages ground fully |
| Fixed 4000-char/page truncation | Global budget ÷ N, window-aware | This phase (52) | Fits small local windows; 5×max-size gate proves it |
| Regex HTML→text core | Jsoup parse-only core + regex fallback | This phase (52) | Cleaner density; fallback preserves worst-case behavior |
| `Leyendo página…` single chip | `Leyendo N de M…` + ok/omitida states | This phase (52) | No silent drops (FETCH-03) |

**Deprecated/outdated:**
- `HtmlToTextExtractor.MAX_CHARS` (4000) as the live budget: superseded by the global split budget; keep the constant only as the regex-fallback path's internal ceiling or remove if the fallback takes the budget as a parameter.
- Single-`activeCall` cancel: superseded by the concurrent-set pattern (Pitfall 1).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Global ~6000-char budget + per-model window table values | Architecture Patterns (Pattern 3) | Fused context overflows small local windows (gibberish/truncation mid-answer) or under-uses large windows; mitigated by LOW-confidence flag + on-device validation + 5×max-size gate |
| A2 | Jsoup core parsing needs no desugar at minSdk 28 | Pitfall 6 | Build/lint failure on API-28 devices if a `java.nio` path is hit; fallback is adding `coreLibraryDesugaring` (small, well-understood fix) |
| A3 | `GenerationParameters.contextSize` (default 4096) is available at the grounding hook for budget selection | Pattern 3 | Budget falls back to the conservative 4096-tier constant; no functional breakage |
| A4 | `Element.text()` + `prepend("\\n")` block reconstruction matches legacy line-boundary truncation semantics closely enough for the adversarial suite | Pattern 2 / Pitfall 2 | Density tests fail; fallback is per-block-element `wholeText()` iteration (more code, same API) |

## Open Questions

1. **Exact per-model window table**
   - What we know: `contextSize` default 4096 in `GenerationParameters` and `LocalModelEntity`; remote providers expose no window signal in the hook path.
   - What's unclear: Real on-device windows for LiteRT-LM targets and per-remote-model sizes.
   - Recommendation: Ship the LOW-confidence table behind one function (`globalBudget(contextSize)`), validate on device this phase, let TUNE-01 (v2.4) refine.

2. **Progress-state shape for per-source ok/omitida**
   - What we know: `ChatUiState.isFetchingWeb: Boolean` is the only fetch state today; UI-SPEC mandates N-de-M copy + per-source states.
   - What's unclear: Whether to extend to a `WebFetchProgress(done, total, perSource)` data class or derive copy from counts.
   - Recommendation: Agent's discretion — a small immutable progress model in `ChatUiState` is cleaner than overloading the Boolean; Fuentes stays on `groundedSources`.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|------------------|
| V2 Authentication | Partial | Stripped OkHttp client (AuthInterceptor removed) — re-verify the strip still applies after any client rebuild; no credentials in fetch path |
| V4 Access Control | No | N/A — public web fetch, no user data scoping |
| V5 Input Validation | Yes | http/https scheme allowlist (detector regex + redirect resolver rejects non-http(s)); `text/html`+`text/plain` content-type gate; 3-redirect cap; 64KB cap |
| V6 Cryptography | No | Plain HTTPS via platform TLS; no custom crypto |
| V14 Configuration | Yes | `Jsoup.connect()` forbidden (would bypass all fetch policy); ProGuard keep-rules check for `org.jsoup.**` if release shrinking strips parser classes — verify release build |

### Known Threat Patterns for OkHttp + Jsoup grounding stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Prompt injection via fetched pages (× N attack surface) | Tampering / Elevation | `WebContextSanitizer.sanitize` per page pre-fusion + delimiter escaping; adversarial hijack-across-pages gate |
| Auth header leak to arbitrary hosts | Information disclosure | AuthInterceptor strip on derived client (frozen policy); regression-test that fetch requests carry no `Authorization` |
| SSRF-ish fetch of LAN/internal URLs | Tampering | Out of scope for this phase (v2.2 accepted); note as residual — pasted URLs fetch as-is |
| Billion-laughs / zip-bomb HTML parse DoS | Denial of service | 64KB pre-parse cap bounds Jsoup input; parse runs off the UI thread; blank-output → regex fallback → ModelOnly chain never hangs the turn |
| Redirect to non-http(s) scheme | Tampering | Existing resolver rejects non-http/https — preserved verbatim in fan-out |

## Sources

### Primary (HIGH confidence)
- Maven Central `org/jsoup/jsoup/maven-metadata.xml` — 1.23.2 is latest+release (Aug 2026)
- https://jsoup.org/cookbook/input/parse-document-from-string — `Jsoup.parse(html, baseUri)` parse-only contract
- https://jsoup.org/cookbook/extracting-data/attributes-text-html — `Element.text()` normalized-text semantics, `select().remove()` pattern
- Codebase (VERIFIED by read): `WebPageFetcher.kt`, `UrlDetector.kt`, `HtmlToTextExtractor.kt`, `GroundingPrompt.kt`, `GroundingResult.kt`, `WebContextSanitizer.kt`, `ChatViewModel.kt:269-294`, `ChatUiState.kt:51`, `MessageBubble.kt:202-214`, `GenerationParameters.kt` (contextSize=4096), `gradle/libs.versions.toml`, `app/build.gradle.kts`

### Secondary (MEDIUM confidence)
- CONTEXT.md code-context + UI-SPEC.md copywriting contract (project-authored, consistent with codebase reads)

### Tertiary (LOW confidence)
- None — web search returned no usable results; all external claims above are CITED to fetched primary sources. Budget constants are [ASSUMED]/LOW per CONTEXT.md itself.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — Jsoup version verified on Maven Central; coroutines/OkHttp already in the catalog
- Architecture: HIGH — fan-out semantics follow from `fetch()`'s throw-only-on-cancel contract (read in source); `coroutineScope`-vs-`supervisorScope` reasoning is first-principles coroutines
- Pitfalls: HIGH — `activeCall` race and `text()` normalization verified against read sources; desugar note is MEDIUM (build-dependent)

**Project Constraints (from AGENTS.md):** Kotlin-only; Clean architecture + MVVM + repository pattern; Hilt DI; Room/DataStore; OkHttp+Retrofit networking; coroutines (Main/IO/Default discipline) + WorkManager for deferrable work; API keys via EncryptedSharedPreferences/Keystore, no plaintext secrets, no hardcoded keys; offline-first (remote fails gracefully); never block UI thread; single-module YAGNI; KSP (not kapt); Kotlin DSL Gradle + version catalog; R8/ProGuard for release.
**Validation section:** Omitted — `workflow.nyquist_validation` is explicitly `false` in `.planning/config.json`.
**Environment Availability:** Skipped — no external tools/services/CLIs required; Jsoup arrives via Gradle, JVM unit tests run on JDK, on-device validation uses the existing WEB-06 smoke precedent.
**Research date:** 2026-09-28
**Valid until:** ~30 days (stable domain; Jsoup latest verified at research time)
