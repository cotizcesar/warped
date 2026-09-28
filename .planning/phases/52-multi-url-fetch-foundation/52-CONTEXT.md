# Phase 52: Multi-URL Fetch Foundation - Context

**Gathered:** 2026-09-28
**Status:** Ready for planning

<domain>
## Phase Boundary

Pasted URLs (2–5 per message) ground answers with fused multi-page context that fits small local-model windows. Parallel fan-out over the v2.2 single-URL pipeline (stripped OkHttp client, 64KB cap, timeouts unchanged), per-source progress with no silent drops, partial grounding when some pages die, Jsoup parse-only extraction replacing the regex core, and a global model-window-aware grounding budget. Prompt-prefix augmentation only — `LlmProvider` / `LlmModelHelper` untouched, no Room migration in this phase.

</domain>

<decisions>
## Implementation Decisions

### Multi-URL Fetch Orchestration
- Max 5 URLs per message — matches FETCH-01 2–5 range, bounds memory/time; 6th+ URLs ignored deterministically
- Parallel fan-out with async + awaitAll on Dispatchers.IO, single offline check upfront — reuses `WebPageFetcher.fetch()` per URL unchanged
- Dedupe preserving first-seen order, sources numbered in paste order — deterministic `[WEB CONTEXT 1..N]` / Fuentes ordering
- Single cancel path cancels all in-flight calls (Stop button / new-turn pre-cancel) — matches v2.2 UX, cooperative CancellationException rethrow

### Partial Failure + Progress UX
- Partial grounding: any ok page grounds the turn; dead links skipped in prompt but surfaced as omitidas in progress state — matches FETCH-02
- Model-only banner ONLY when ALL pages fail/offline; per-source reasons collapsed to worst-case (OFFLINE wins over FETCH_FAILED)
- Progress copy "Leyendo N de M…" with per-source ok/omitida states, no silent drops — extends `isFetchingWeb` chip (FETCH-03)
- `groundedSources: List<String>` extended to N URLs; Fuentes list renders all N in block order — no Room change in Phase 52 (persistence comes in Phase 53)

### Jsoup Extraction Swap
- Add Jsoup 1.23.2 + desugar NIO build config; parse-only (`Jsoup.parse`, never `Jsoup.connect()`) — per v2.3 STATE decision
- Fetch policy frozen: stripped client (no AuthInterceptor leak), 64KB cap streamed in 8KB chunks, 8/10/20s timeouts, 3 manual redirects, text/html + text/plain only — EXTRACT-01
- Extraction semantics parity + cleaner density: title prepend, script/style/noscript + nav/footer/aside strip, block-tag newlines, entity decode, line-boundary truncation with "… [truncado]" marker
- Blank Jsoup output falls back to the hand-rolled regex extractor; never model-only on parse miss alone

### Global Budget + Exit Gates
- Single global grounding budget (e.g. ~6000 chars) divided evenly across N pages, model-window-aware shrink for small local models — replaces fixed 4000/page constant (EXTRACT-02, LOW-confidence numbers validated on device)
- Numbered `[WEB CONTEXT 1..N]` fused prefix blocks; SYSTEM_PROMPT cites [1]/[2] markers; Fuentes order == block order
- Phase exit gate: multi-page adversarial suite (1-dead-of-3, all-dead, 5-URL cap, hijack-injection across pages with sanitizer per page) must pass
- Phase exit gate: 5× max-size budget assertion (5 pages × 64KB) proves fused block fits window; later extractor changes must re-pass

### the agent's Discretion
- Exact global budget constant and per-model window table values — pick from codebase model capability signals, keep LOW-confidence flag until device validation
- Coroutine fan-out structure (supervisorScope vs awaitAll failure semantics) — partial-failure behavior above is the contract
- Jsoup selector/strip list details beyond the agreed baseline — density wins as long as adversarial suite passes

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `data/grounding/WebPageFetcher.kt` — bounded single-page fetcher (stripped client, 64KB cap, 8/10/20s timeouts, 3 redirects); reuse per-URL inside fan-out
- `data/grounding/UrlDetector.kt` — `firstUrl()` regex heuristic; extend to `allUrls()` (dedupe, cap 5) in same style
- `data/grounding/HtmlToTextExtractor.kt` — hand-rolled regex extractor (MAX_CHARS 4000, title prepend, truncado marker); keep as fallback after Jsoup swap
- `data/grounding/GroundingPrompt.kt` — `buildBlock()` / `augment()` prefix augmentation; extend to numbered fused blocks
- `data/grounding/GroundingResult.kt` — `Grounded(block, url)` / `ModelOnly(reason)`; compose N results into partial/all-fail routing
- `data/grounding/WebContextSanitizer.kt` — hijack sanitization, must run per page before fusion
- `ui/chat/ChatViewModel.kt:269-294` — grounding fetch hook (detect → fetch → augment) after save, before helper resolution; fan-out plugs in here
- `ui/chat/ChatUiState.kt:49-51` + `ChatScreen.kt:234-256` — `isFetchingWeb` transient chip ("Leyendo página…"); extend to N-de-M + per-source states
- `ui/chat/components/MessageBubble.kt:202-214` — numbered Fuentes list below grounded assistant messages; extend to N sources

### Established Patterns
- Grounding files are pure Kotlin, no Android imports (except fetcher Context/ConnectivityManager) — JVM unit-testable; keep new fan-out/budget/fusion logic pure where possible
- Clean architecture: `domain/` never imports Android; `data/grounding/` implements policy; `ui/chat/` observes via Hilt-injected ViewModel StateFlow
- Hilt `@Singleton` fetcher with `Dispatchers.IO`; cooperative cancel via `Call.cancel()` + CancellationException rethrow
- Zero-dependency heuristic ethos (v2.2) — v2.3 adds exactly one dep: Jsoup parse-only

### Integration Points
- `ChatViewModel.sendMessage()` grounding hook — multi-URL fan-out entry point, same position (after save, before helper resolution)
- `ChatScreen` fetch chip + `MessageBubble` Fuentes list — progress/surfaces for N sources (no navigation changes)
- `gradle/libs.versions.toml` + `app/build.gradle.kts` — Jsoup 1.23.2 + desugar NIO config
- `LlmProvider` / `LlmModelHelper` — untouched (prompt-prefix augmentation only, per out-of-scope guardrail)

</code>

<specifics>
## Specific Ideas

- User pasting 2–5 URLs in one message gets one fused grounded answer with numbered sources
- Progress copy in Spanish: "Leyendo 2 de 4…" with per-source ok/omitida states
- v2.2 fetch policy numbers are frozen references: 64KB cap, 8KB chunks, 8s connect / 10s read / 20s call, 3 redirects, browser User-Agent, text/html + text/plain only
- Budget numbers per model window are LOW-confidence estimates — validate on device in this phase

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope. Global out-of-scope guardrails reaffirmed (no citation pills in generated text, no WorkManager periodic retry, no `Jsoup.connect()`, no provider interface changes).

</deferred>
