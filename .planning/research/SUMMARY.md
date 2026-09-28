# Project Research Summary

**Project:** Warped — v2.3 milestone (Web Grounding v2)
**Domain:** On-device Android LLM chat with multi-URL web grounding
**Researched:** 2026-09-28
**Confidence:** HIGH

## Executive Summary

Warped v2.2 already ships single-URL web grounding: a bounded OkHttp fetch, heuristic HTML→text extraction, `[WEB CONTEXT]` prompt-prefix augmentation, hijack sanitization, offline fallback, and a global default-ON toggle. v2.3 extends that proven pipeline to multi-URL fetch (2–5 URLs per message, fused numbered context), in-chat source previews, a per-chat toggle override, and offline retry — without touching the inference providers, which stay blind to grounding (prompt string gets longer, interfaces unchanged).

The recommended approach is to build the new `GroundingOrchestrator` (bounded parallel fan-out + context fusion + global token budget) as the foundation first, with the Jsoup extraction swap folded into that same phase — because budget numbers and the adversarial baseline both depend on extraction density. Source previews and the per-chat toggle come second (they share one Room migration v15 and consume the orchestrator's `List<GroundedSource>` output shape), and offline retry comes last (it orchestrates all three prior pieces and must respect fetch deadlines, cache identity, and toggle resolution). The only new dependency v2.3 needs is Jsoup 1.23.2 (plus desugar NIO build config); everything else reuses the existing OkHttp / Room / DataStore / WorkManager / Compose stack.

The key risks are context-budget blowout on small local-model windows (fix: global grounding budget divided across pages, model-window-aware, with a worst-case assertion test), injection surface scaling ×N (fix: single `sanitizePage` choke point + multi-page adversarial suite incl. collusion cases as a merge gate), and retry-mechanism mismatch (fix: message-scoped foreground retry first; WorkManager only as explicit opt-in with strict dedup — never periodic, never re-running inference).

## Key Findings

### Recommended Stack

Only one new dependency is needed. Everything else is already in the project and sufficient (full detail in `STACK.md`).

**Core technologies:**
- Jsoup `org.jsoup:jsoup` 1.23.2: HTML→text extraction (parse-only via `Jsoup.parse(htmlString)`) — replaces the hand-rolled regex core of `HtmlToTextExtractor`; WHATWG-spec parser, ~430 KB pre-R8, zero runtime deps — this is the single new dependency
- Desugar JDK libs NIO `com.android.tools:desugar_jdk_libs_nio` 2.1.5: mandatory build config for Jsoup on Android (`isCoreLibraryDesugaringEnabled = true`; project is on AGP 9.3.0 so compatible)
- Kotlinx Coroutines (existing 1.11.0): multi-URL parallel fan-out (`supervisorScope` + `async`/`awaitAll` + `Semaphore(3)`)
- OkHttp (existing 4.12.0): per-URL bounded fetch transport — existing `WebPageFetcher` policy reused unchanged per URL; Jsoup is parse-only, never `Jsoup.connect()`
- Room (existing 2.8.5) + DataStore Preferences (existing 1.2.1): per-chat tri-state override column + unchanged global default-ON
- WorkManager (existing 2.12.0) + Compose Material3 `ModalBottomSheet`: retry transport (conditional, see reconciliation below) + sources preview UI

**Stack rule that must survive:** fetching stays a security boundary — Jsoup never fetches, the stripped-OkHttp-client policy (no AuthInterceptor, 64 KB cap, manual redirects, timeouts) applies to every page including retries and cache-miss refetches.

### Expected Features

(Full landscape, dependency graph, and competitor analysis in `FEATURES.md`.)

**Must have (table stakes):**
- Multi-URL fetch (2–5 URLs/message, parallel, fused numbered `[WEB CONTEXT 1..N]` blocks) — the core milestone promise; users paste/compare multiple links
- Numbered Fuentes list for N sources + progress chip (`Leyendo 2 de 4…`, per-source ok/skipped) — silent drops destroy trust
- Partial grounding (one dead link never poisons the turn; banner only when ALL fail) — correctness requirement of multi-fetch
- `GroundingResult` list reshape + `UrlDetector` list + numbered `GroundingPrompt` — the keystone refactor enabling everything
- Per-chat web toggle (tri-state `null` = inherit global) — headline differentiator, cheap once migration v15 exists
- Sources preview bottom sheet over persisted extracts — headline differentiator; trust receipts without leaving chat
- Message-scoped offline retry (queued state + "Reintentar" on reconnect, OFFLINE-only) — offline-resilience promise

**Should have (competitive / v2.3.x on trigger):**
- Per-message "Sin web" composer override — one-off model-only escape hatch, cheap once per-chat toggle exists
- Preview "Abrir en navegador" (Custom Tab) — trigger: users want the full page after reading the extract
- Fused-context budget tuning per model context size — trigger: overflow reports on small local models

**Defer (v2.4+ / explicit anti-features):**
- WorkManager persistent grounding queue — message-scoped retry covers chat UX; persistent queue is a second feature disguised as retry
- Model-output citation pills (`[1]` tappable in response text) — 4-constraint compound problem (rendering, copy, portability, grammar); small local models hallucinate markers
- JavaScript-rendered (WebView) extraction — main-thread, memory-heavy, JS-execution escalation; mark JS-shell pages skipped
- Unlimited URL count — hard cap 5 (context window, radio/battery, injection surface all demand it)
- Auto-grounding URLs inside model responses — recursive-fetch risk; ground user-pasted URLs only

### Architecture Approach

(Full system diagram, build order, and boundary contracts in `ARCHITECTURE.md`. All structural claims verified against the live codebase — HIGH confidence.)

v1's invariants survive: providers unchanged (prompt-prefix augmentation only), history keeps originals (fused blocks never persist into `MessageEntity.content`), `activeCall` cancel discipline extends to N calls, AuthInterceptor stripping applies to every fetch path. The new logic lives in `data/grounding/` (same package, two new files — no new module), with fan-out encapsulated in a JVM-testable `GroundingOrchestrator`, never inline in the ViewModel.

**Major components:**
1. `GroundingOrchestrator` (NEW, `data/grounding/`) — parallel fan-out + fusion + budget; owns a `CallRegistry` with `cancelAll()`; exposes `ground()` + `cancel()`; the ViewModel hook becomes ~10 lines
2. `GroundedSource` value type + `GroundingResult.Grounded(sources: List<…>)` reshape (NEW/MODIFIED) — the keystone change; everything downstream (ViewModel injection, Fuentes UI, banner logic, preview, retry) keys off this type
3. `UrlDetector.extractUrls(limit=5)` + `GroundingPrompt.buildFusedBlock()` (MODIFIED, additive) — ordered distinct list; uniform fused prompt shape for 1..N URLs so formats never drift
4. Preview + toggle persistence (MODIFIED schema, single `MIGRATION_14_15`) — nullable `web_grounding_mode` column (NULL = inherit, zero backfill) + `grounded_sources` metadata/excerpt store; preview sheet reads excerpts, full text behind a bounded in-memory LRU
5. Retry path (NEW, last) — immediate "Reintentar" button (orchestrator direct call) + deferred connectivity-gated refetch converging on the same `ground()` entry point

**Schema fork decided here:** combine the toggle column + source store into ONE migration v15 (avoid two migrations in one milestone); preview sheet state lives in Compose `remember{}`, not the ViewModel (48-01 single-owner discipline).

### Critical Pitfalls

(Full 7-pitfall catalog with warning signs, debt table, and "looks done but isn't" checklist in `PITFALLS.md`. Do not regress the v2.2 injection/SSRF/fetch-cap defenses.)

1. **Parallel fetch storm (tail latency)** — 5 naive `async` fetches make every turn as slow as the slowest page. Avoid: `supervisorScope` (never bare `coroutineScope` + `awaitAll()`), per-fetch timeout + overall deadline with drop policy, one shared OkHttp client, per-source progress UI.
2. **Context-budget blowout** — 5 pages × v2.2 per-page caps overflow 4–8K local-model windows. Avoid: replace per-page constants with a global grounding budget divided across fetched pages (`perPage = budget / pagesFetched`), model-window-aware, truncation markers, worst-case assertion test. Never ship `MAX_URLS` raised with caps untouched.
3. **Stop doesn't stop N fetches** — fan-out outside the cancellable `generationJob` leaks fetches past Stop into the next turn's context. Avoid: fan-out as a child of the generation job, shared `Call` handles, generation-epoch check before prompt build, mid-fetch Stop regression test as exit criterion.
4. **Preview storage bloat** — full extracted text per source in Room (~20 KB/grounded turn) bloats the DB and janks recomposition. Avoid: Room holds metadata + ~500-char excerpt only; full text in a bounded in-memory LRU (keyed by URL+hash) with re-fetch-on-miss; stable IDs (not strings) through Compose state.
5. **Toggle precedence ambiguity** — three layers (per-message > per-chat > global) with no single resolver produces wrong-state bugs and NULL-legacy crashes. Avoid: ONE pure unit-tested `effectiveGrounding()` function resolved at exactly one call site; nullable per-chat column (NULL = inherit, never backfill); 12-case matrix + legacy-upgrade tests as entry criteria for toggle UI.
6. **Retry-mechanism mismatch** — WorkManager periodic (15-min minimum) is wrong for chat-timescale retry; risks worker spam, stale-turn injection, retrying inference instead of fetch. Avoid: message-scoped foreground retry on connectivity-gain events first; any WorkManager use gets `REPLACE` + unique name per (conversation, message) + attempt cap + URL-only input data; retry fetch only, user re-sends.
7. **Injection surface × N** — the new merge path can bypass per-page sanitization; colluding pages can run quorum attacks single-page tests never exercise. Avoid: choke-point `sanitizePage` (merge concatenates sanitized spans only), multi-page adversarial suite (1-of-5 malicious, 3-of-5 colluding, delimiter-mimic, malicious-in-truncated-tail) as merge gate, per-source provenance delimiters, extractor change = security-gated change, plain-text-only preview rendering.

## Implications for Roadmap

Based on research, suggested phase structure:

### Phase 1: Multi-URL fetch + fusion + budget (+ Jsoup swap)
**Rationale:** The fetch policy is the foundation everything else consumes — preview content, retry payloads, and toggle-gated fetching all key off the `List<GroundedSource>` shape. Budget numbers and the adversarial baseline both depend on extraction density, so the extraction-quality decision must land before or inside this phase, not after.
**Delivers:** `UrlDetector.extractUrls` → `WebPageFetcher.fetchOne` + call registry → `GroundingOrchestrator.ground` → `GroundingPrompt.buildFusedBlock` → rewired `ChatViewModel` hook; Jsoup replacing the regex extractor core (keeping the 4000-char truncation contract + `WebContextSanitizer` untouched); 2–5 URL turns grounding with fused context; counted chip copy; Stop cancels all.
**Addresses:** Multi-URL fetch, keystone `GroundingResult` reshape, partial grounding + all-fail banner fix, progress chip, fused citation hints.
**Avoids:** Pitfalls 1 (fetch storm), 2 (budget blowout), 3 (Stop leak), 7 (injection × N — choke-point + adversarial gate are exit criteria).
**Uses:** Jsoup 1.23.2 + desugar NIO (only new deps); coroutines `supervisorScope` fan-out; existing fetch policy per URL.

### Phase 2: Sources preview + per-chat toggle
**Rationale:** Both features consume Phase 1's output shape and both need schema work — combine into a single `MIGRATION_14_15` (one nullable toggle column + one source metadata/excerpt store) to avoid two migrations in one milestone. Storage shape and the precedence resolver must exist before any UI is built, or the UI bakes in the wrong data source.
**Delivers:** `GroundedSource` domain type → `ChatMessage.groundedSources` list migration → tappable Fuentes rows → `SourcePreviewSheet` (excerpt-first, LRU full text, plain-text-only rendering) → tri-state toggle column + `effectiveGrounding()` resolver + chat-surface toggle affordance showing effective state + source layer.
**Addresses:** Numbered Fuentes list for N, sources preview bottom sheet + persistence, per-chat toggle, per-message override groundwork (same send-path flag).
**Avoids:** Pitfalls 4 (preview storage — schema review: no unbounded text column; LRU bounds test) and 5 (precedence — 12-case matrix + legacy-NULL upgrade tests are entry criteria for toggle UI).
**Implements:** `SourcePreviewSheet`, `GroundedSourceDao` (if persisting), `Conversation.webGroundingMode`, repository get/set, `MIGRATION_14_15`.

### Phase 3: Offline retry (message-scoped first)
**Rationale:** Retry orchestrates all three prior pieces and must respect fetch deadlines, cache identity, and toggle resolution — so it builds last. The FEATURES + PITFALLS consensus (over the STACK default) is message-scoped foreground retry first, WorkManager only as explicit opt-in.
**Delivers:** OFFLINE-vs-FETCH_FAILED-gated pending-retry record per (message, URL) → connectivity-gain re-fetch through the same `ground()` entry point → banner "Reintentar" immediate path + deferred path with dedup (superseded turns cancel) → retry populates the same persisted source rows the preview sheet reads.
**Addresses:** Message-scoped offline retry, retry-fills-preview enhancement, closed-conversation drop semantics.
**Avoids:** Pitfall 6 (dedup test, no-inference test, closed-conversation drop test, SSRF re-check on every refetch).

### Phase Ordering Rationale

- **Dependencies force the order:** Phase 1's `Grounded(sources: List)` reshape is the keystone — Phases 2 and 3 both key off that type. Phase 3 needs Phase 2's store (where retry results land) and Phase 2's resolver (retry must respect the effective toggle).
- **Schema economy:** Phase 2 bundles both Room changes into one migration v15 (toggle column + source store) — the FEATURES dependency graph and ARCHITECTURE migration precedent agree.
- **Security gating:** the adversarial suite + budget assertion are Phase 1 exit gates, because Phase 2's preview persistence and Phase 3's refetch paths would otherwise inherit an untested baseline; any later extractor change must re-pass the same gate.
- **Contested decision resolved:** STACK.md defaults to a WorkManager retry queue; FEATURES.md and PITFALLS.md both argue message-scoped foreground retry first (WorkManager's 15-min granularity and worker-spam risk are wrong for chat). Recommendation: follow FEATURES+PITFALLS (message-scoped first, WorkManager only as explicit user-scheduled retry with `REPLACE` + unique names + attempt cap). The Phase 3 planner should treat this as settled unless new evidence appears.

### Research Flags

Phases likely needing deeper research during planning (`--research-phase` recommended):
- **Phase 1:** YES — extraction-quality tuning (Jsoup main-content selectors `article`/`main`/`[role=main]` vs full-body, title+description fallback for JS-shell pages, fused ~8K cap vs per-model-window sizing) needs device validation; exact global token-budget numbers per allowlisted model window are LOW-confidence estimates. Also verify Compose BOM / desugar versions against Google Maven at plan time (STACK flags MEDIUM on BOM pinning).
- **Phase 3:** LIGHT — verify current WorkManager constraint/backoff/unique-work APIs against developer.android.com at plan time (PITFALLS cites training knowledge); connectivity-Flow extension of `ConnectivityGate` needs a plan-time API check.

Phases with standard patterns (skip research-phase):
- **Phase 2:** Room manual migration (v14 precedent in `Migrations.kt`), tri-state override resolution, `ModalBottomSheet` preview — all established codebase/platform patterns with HIGH-confidence guidance. Proceed directly to planning.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Jsoup 1.23.2 + desugar 2.1.5 verified via jsoup.org, mvnrepository, Context7; project gap (no desugaring enabled) verified in `app/build.gradle.kts`. Only BOM pinning is MEDIUM (verify against Google Maven). |
| Features | HIGH / MEDIUM | HIGH on codebase-verified items (existing `data/grounding/`, DataStore toggle, Room schema, ViewModel hook); MEDIUM on competitor behavior (Perplexity/ChatGPT patterns via 2026 teardowns). |
| Architecture | HIGH | Every structural claim verified against live code (`data/grounding/`, `ChatViewModel` hook/cancel lines, Room entities, workers). Sizing recommendations (fused cap, parallelism 3, TTL) are MEDIUM — need device validation. |
| Pitfalls | HIGH / MEDIUM | HIGH on repo-verified pipeline shape and inherited v2.2 defenses; MEDIUM on OkHttp/coroutine/WorkManager standard practices; LOW on exact budget numbers and LRU tuning (flagged for phase validation). |

**Overall confidence:** HIGH

### Gaps to Address

- **Global token-budget numbers per model window (LOW):** exact fused-cap values are estimates. Handle in Phase 1 planning: read allowlisted model metadata, clamp fused output to a fraction of `contextSize`, log worst-case prefix length (Timber, debug), add the 5×max-size budget assertion test.
- **Preview LRU size/TTL tuning (LOW):** 10 entries / 200 KB / 5 MB / 7 days are starting points. Handle in Phase 2 planning with an eviction test and low-RAM device check.
- **Extraction-density effect on budget/adversarial baseline (open research question):** if Jsoup output density differs materially from the heuristic, re-tune the Phase 1 budget and re-run the full adversarial suite before merging — treat extractor change as security-gated.
- **Version pins to verify at plan time:** Compose BOM 2026.06.01, desugar 2.1.5, WorkManager/hilt-work pair — re-check against Google Maven / AGP compatibility table before freezing.

## Sources

### Primary (HIGH confidence)
- Live Warped codebase: `data/grounding/{UrlDetector,WebPageFetcher,GroundingPrompt,GroundingResult,HtmlToTextExtractor,WebContextSanitizer}.kt`; `ui/chat/ChatViewModel.kt` (turn hook, cancel discipline); `ui/chat/{ChatUiState,ChatScreen}` + `components/MessageBubble.kt`; `domain/model/{ChatMessage,Conversation}`; `data/local/db/{AppDatabase,Migrations}`; `AdvancedPreferences.webGroundingEnabled`; `ModelDownloadWorker`/`ModelBenchmarkWorker` (WorkManager precedent); `app/build.gradle.kts` (desugaring gap)
- https://jsoup.org/download + https://jsoup.org/news/release-1.23.1 + https://jsoup.org/news/release-1.22.2 — current release 1.23.2, zero runtime deps, Android desugaring requirement, R8/re2j rule
- Context7 `/jhy/jsoup` (342 snippets) + mvnrepository `org.jsoup:jsoup` versions + google/desugar_jdk_libs CHANGELOG (2.1.5)

### Secondary (MEDIUM confidence)
- Android Developers offline-first guide + WorkManager BackoffPolicy docs (`NetworkType.CONNECTED`, `EXPONENTIAL` default, 15-min periodic minimum)
- Setproduct "Designing AI chat interfaces" (2026) — citations-as-receipts, message-state checklist
- Flaig "How Leading AI Apps Implement Inline Citations" (2026-04) — 4-constraint compound problem, Markdown-collision warning
- LibreChat PR #7032 (Perplexity sources menu precedent); AnythingLLM #2827 (source-metadata-alongside-message precedent)
- OkHttp Dispatcher/pool/timeout behavior (square.github.io/okhttp); Kotlin `supervisorScope` semantics (kotlinlang.org); OWASP LLM prompt-injection cheat sheet
- `.planning/research/PITFALLS.md` (v2.2) Pitfalls 5–6 — inherited injection/SSRF baseline still in force

### Tertiary (LOW confidence, needs phase validation)
- Exact global token budgets per allowlisted model window; preview LRU size/TTL tuning; whether the extraction upgrade shifts the adversarial baseline (gated, not assumed)

---
*Research completed: 2026-09-28*
*Ready for roadmap: yes*
