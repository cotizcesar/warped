# Pitfalls Research: v2.3 Web Grounding v2

**Domain:** Android LLM chat app (Warped) — extending a shipped single-fetch grounding pipeline to multi-URL fetch, source previews, per-chat toggle, offline retry
**Researched:** 2026-09-28
**Confidence:** HIGH (repo-verified: v2.2 grounding shape, ChatViewModel hook, Room transcript, DataStore prefs); MEDIUM (Android/OkHttp/coroutine standard practices cited below); LOW where flagged (extraction-library decision still open per milestone scope)

> Scope note: v2.2 shipped heuristic single-fetch grounding (`data/grounding/`, `[WEB CONTEXT]` block, hijack sanitization, offline fallback, default-ON toggle). Its pitfalls (indirect injection, SSRF/private-IP, unbounded fetch) are recorded in the v2.2 PITFALLS research and remain in force. This file covers only what is **new or amplified** when extending that pipeline. Do not regress the v2.2 defenses.

## Critical Pitfalls

### Pitfall 1: Parallel fetch storm — slowest page sets latency, bursty connections waste radio/battery

**What goes wrong:**
2–5 URLs per message fetched naively (`async` × N with no limits) means every grounded turn opens up to 5 TLS connections at once, and total latency equals the *slowest* page. On mobile networks one stalled host turns a 1s grounding step into a 10–15s hang. Bursty parallel connections also keep the radio in high-power state longer than sequential or bounded fetch, draining battery per turn.

**Why it happens:**
The v2.2 fetcher was built for one URL: one call, one timeout, done. Scaling that loop to N without a concurrency policy feels like no change ("just `awaitAll()`"), but the failure mode changes from single-timeout to tail-latency-dominated. OkHttp's default `Dispatcher` (64 max requests, 5 per host) won't save you — it caps abuse, it doesn't bound *your* turn latency.

**How to avoid:**
1. Bound concurrency explicitly: `async` fan-out inside a `supervisorScope` with a per-fetch timeout (e.g. 8s each) AND an overall deadline (e.g. 12s total). First-N-wins or deadline cutoff: pages that miss the deadline are dropped with a "source unavailable" marker, never awaited indefinitely.
2. Fail-partial, never fail-all: one host down must not poison the other four. `supervisorScope` (child failure doesn't cancel siblings) is mandatory — bare `coroutineScope` + `awaitAll()` cancels everything on the first exception.
3. Reuse a single OkHttpClient (connection pooling, one Dispatcher) rather than a client per fetch. Consider `dispatcher.maxRequestsPerHost` tuning if grounding hammers one domain.
4. Show per-source progress ("Fetching 2/5…") via the existing `isFetchingWeb` transient state extended to a count, so a stalled page is visible, not a mystery hang.

**Warning signs:**
- `awaitAll()` inside `coroutineScope` (not `supervisorScope`) in the multi-fetch design.
- No overall deadline — only per-request timeouts.
- A new OkHttpClient instantiated per URL.
- Plan mentions "parallel fetch" with no dropped-source policy.

**Phase to address:**
Multi-fetch + context-budget phase (first v2.3 phase — the fetch policy is the foundation everything else builds on).

---

### Pitfall 2: Context-budget blowout — 5 pages × current caps overflow small on-device windows

**What goes wrong:**
v2.2 caps (~4k chars/page, ≤3 URLs ≈ 3k tokens) were sized for *one* fetch. Naively keeping the per-page cap and raising the URL count to 5 injects ~5–7k tokens of web context into models whose total windows can be 4–8k. Result: the grounding context evicts conversation history, or LiteRT-LM inference OOMs / slows to a crawl on-device — the exact memory pressure v2.1–v2.2 fought (smart presets, `largeHeap` awareness).

**Why it happens:**
Caps were designed as per-page constants, not as a *global budget* to be divided. Developers raise `MAX_URLS` without touching the char cap, and no test asserts total grounded tokens against the active model's window.

**How to avoid:**
1. Replace per-page constants with a **global grounding budget** (e.g. 3–4k tokens total) divided across successfully fetched pages: `perPage = budget / pagesFetched`. Truncation gets a `…[truncated, N chars omitted]` marker per page so the model knows content is partial.
2. Budget against the *active model's* context window (the allowlist already carries model metadata — reuse that read path). Small-window local models get fewer/shorter pages than large-window remote ones.
3. Fuse-then-rank, don't just concatenate: if extraction quality work lands (milestone open question), prefer first-paragraph/lead extraction per page over tail truncation — lead paragraphs carry more signal per token for grounding.
4. Add a budget assertion test: 5 × max-size pages → total `[WEB CONTEXT]` block ≤ budget, every page marked truncated.

**Warning signs:**
- `MAX_URLS` raised with the per-page char cap untouched.
- No test asserting total context size for the 5-URL worst case.
- Grounding built before the extraction-quality decision (budget numbers depend on extraction density).

**Phase to address:**
Multi-fetch + context-budget phase (same phase as Pitfall 1 — fetch policy and token budget are one review, not two).

---

### Pitfall 3: Stop doesn't stop N fetches — cancellation doesn't propagate to the fan-out

**What goes wrong:**
v2.1 built cancellable single-flight inference (`Call.cancel()`, Stop means stop). Multi-fetch adds N concurrent network calls inside the pre-inference hook. If the fan-out scope isn't a child of the cancellable `generationJob`, pressing Stop cancels inference but leaves up to 5 fetches running — wasted data/battery, and late-arriving pages can race into the *next* turn's context.

**Why it happens:**
The grounding hook runs *before* `runInference`, so it's tempting to launch it in the ViewModel scope rather than inside the cancellable generation job. Single-fetch was fast enough that the leak window was invisible; 5 parallel fetches with a 12s deadline make it a real race.

**How to avoid:**
1. Launch the entire fetch fan-out as a child of the existing cancellable `generationJob` (same discipline as inference). Structured concurrency then cancels all N calls on Stop for free.
2. Use a shared OkHttp `Call` handle per fetch and cancel via coroutine cancellation (OkHttp's `suspend` extension / `Call.cancel()` on `ensureActive()` paths) — don't rely on timeout alone.
3. Guard against late arrival: generation counter / single-flight check before appending fetched pages to context, so a cancelled turn's pages can never leak into the next turn.
4. Regression test: start grounded turn → Stop mid-fetch → assert zero network callbacks fire afterward and next turn contains no stale pages.

**Warning signs:**
- Fetch launched in `viewModelScope` instead of the generation job scope.
- Stop tested only for inference, not for a mid-fetch Stop.
- No generation-epoch check between fetch completion and prompt build.

**Phase to address:**
Multi-fetch + context-budget phase. Cancellation wiring is an exit criterion, not a follow-up.

---

### Pitfall 4: Source preview storage — persisting full extracted text per source bloats Room and janks the list

**What goes wrong:**
Source preview ("tap a source to preview extracted text") needs the extracted text available after the turn. Storing full text for up to 5 pages per grounded turn as Room columns means the transcript table grows by ~20KB per grounded turn — chat history DB bloat, slower queries, slower backup, and `LazyColumn` recomposition passing multi-KB strings through every recompose. Storing nothing means previews break after process death or on old conversations.

**Why it happens:**
The transcript is the convenient place (it's already persisted, already observed), so full text lands in a `sourcesJson`/`extractedText` column "temporarily" and never moves. The v2.2 transcript holds small rows; nobody re-evaluates the size assumption.

**How to avoid:**
1. Two-tier storage: Room persists **metadata only** (URL, title, fetch timestamp, char count, truncation flag, content hash) + a short excerpt (~500 chars) for instant preview paint. Full text lives in an in-memory LRU cache (keyed by URL+hash, size-bounded, e.g. 10 entries / 200KB) with re-fetch-on-miss as the fallback.
2. Cap the excerpt, index nothing full-text (no Room FTS on extracted content — query cost for zero user value).
3. Pass stable IDs (not full strings) through Compose state; load preview text lazily on tap (`LaunchedEffect(sourceId)`), so list recomposition never touches page bodies.
4. Decide the offline-preview policy explicitly: cached full text previewable offline; evicted/missing full text shows excerpt + "reconnect to reload" — consistent with the offline-first posture.

**Warning signs:**
- Room entity gains an `extractedText: String` (unbounded) column.
- Preview composable receives the full text as a parameter from list state.
- No cache-size bound or eviction policy in the preview design.

**Phase to address:**
Sources-preview + per-chat-toggle phase (storage shape must be decided before the preview UI is built, or the UI bakes in the wrong data source).

---

### Pitfall 5: Per-chat toggle precedence ambiguity — three layers, no defined override order

**What goes wrong:**
v2.3 adds per-conversation/per-message override on top of the v2.2 global default-ON toggle. Without an explicit precedence chain, edge cases produce wrong behavior: user disables globally but an old conversation re-enables; per-message toggle contradicts per-chat setting mid-thread; existing conversations (created before the column exists) read NULL and crash or silently default the wrong way.

**Why it happens:**
Each toggle is added where convenient (global in DataStore, per-chat as a Room column, per-message as transient UI state) and the resolution logic becomes an ad-hoc `if` chain scattered across ViewModel call sites. NULL-legacy handling is forgotten because all test conversations are created fresh.

**How to avoid:**
1. Define and document ONE precedence function, pure and unit-tested: `effectiveGrounding(perMessageOverride?, perChatSetting?, globalDefault) -> Boolean`. Recommended: per-message (single-turn override) > per-chat (conversation setting, NULL = inherit) > global default-ON.
2. Per-chat column nullable with NULL meaning "inherit global" — never backfill existing rows with a hardcoded value (that would silently override users' global choice on upgrade).
3. Resolve the effective value at exactly one call site (the grounding hook), not in UI collectors. UI shows the *effective* state with its source ("On — from global default" vs "Off — for this chat") so users can predict behavior.
4. Tests: matrix of (global × perChat NULL/true/false × perMessage null/true/false) = 12 cases; plus upgrade test opening a pre-v2.3 conversation (NULL column) asserting global applies.

**Warning signs:**
- Toggle resolution logic duplicated in more than one place.
- Non-nullable per-chat column with a default that isn't the global value.
- Settings UI with no indication of which layer is currently deciding.

**Phase to address:**
Sources-preview + per-chat-toggle phase. The precedence function and its 12-case test are entry criteria for any toggle UI work.

---

### Pitfall 6: Offline retry queue — WorkManager overkill, duplicate retries, retrying the wrong thing

**What goes wrong:**
"Retry fetch when back online" sounds like a WorkManager job, but WorkManager's minimum periodic interval (15 min) and its persistent-job machinery are wrong for a chat-timescale retry (user expects seconds, not minutes). Misuse produces: duplicate enqueued workers per failed URL (5 URLs × retries = worker spam), retry firing long after the conversation moved on (stale context injected into a dead turn), battery drain from unconstrained retry loops, and retrying *inference* instead of just the *fetch*.

**Why it happens:**
WorkManager is the project's standard background tool (model downloads), so it becomes the default answer. But downloads are deferrable-by-nature; grounding retry is interactive-by-nature — different problem, different mechanism.

**How to avoid:**
1. Prefer a lightweight foreground mechanism: `ConnectivityManager.NetworkCallback` (or existing `ConnectivityGate` extended to a Flow) → on reconnect, retry only the pending *fetch*, only if its conversation is still open and its turn still current (generation epoch check). No persistent workers for the common case.
2. If WorkManager is used at all, reserve it for explicit user-requested "retry when online" with: `NetworkType.CONNECTED` constraint, `ExistingWorkPolicy.REPLACE` + unique work name per (conversationId, messageId) for dedup, `setBackoffCriteria(EXPONENTIAL)` with a max-attempt cap (e.g. 3), and input data carrying only URL + turn identity (never full context).
3. Deduplicate by identity: one pending-retry record per (message, URL). New turn on the same conversation supersedes — cancel superseded retries, never pile them.
4. Never auto-retry inference on reconnect — only the fetch. The user re-sends; the app doesn't hallucinate intent.
5. Battery guard: retries only on actual connectivity *gain* events, never polling; cap attempts; drop retries for conversations closed >N minutes.

**Warning signs:**
- `PeriodicWorkRequest` with 15-min interval proposed for chat retry.
- Worker input data containing prompt text or full context.
- No unique-work-name / dedup story in the retry design.
- Retry path re-triggers `runInference` instead of just re-fetch.

**Phase to address:**
Offline-retry phase (last functional phase — it depends on the fetch fan-out, preview cache, and toggle resolution all being final, since retry must respect all three).

---

### Pitfall 7: Injection surface × N — per-page sanitization drift and cross-page collusion

**What goes wrong:**
v2.2's hijack sanitization was built and adversarial-tested for ONE page. With 5 pages: (a) a new code path (fan-out merge, fused-context builder) can bypass or reorder sanitization for some pages — one unsanitized page poisons the whole fused block; (b) coordinated pages can run quorum attacks ("three independent sources agree: …ignore previous instructions…") which single-page adversarial tests never exercise; (c) the extraction-quality upgrade (if it changes HTML→text handling) can re-admit scripts/comments/metadata vectors the heuristic stripper removed.

**Why it happens:**
Sanitization lives at the single-fetch layer; the multi-page merge is written as *new* code that calls the fetcher but builds the context block itself. Security review covers "the fetcher" (unchanged, ✓) and misses "the new merge path" (untainted, ✗). Multi-page adversarial testing feels redundant ("we already test injection") so it's skipped.

**How to avoid:**
1. Sanitize at the narrowest choke point: ONE function `sanitizePage(raw) -> trusted-span` that every page passes through regardless of path (single, multi, retry-refetch, cache-hit). The merge step only concatenates already-sanitized spans — it must be *incapable* of inserting raw text (type-level if cheap: a `SanitizedText` inline class the builder accepts).
2. Extend the v2.2 adversarial suite: multi-page cases — 1-of-5 malicious, 3-of-5 colluding (same instruction repeated), delimiter-mimic inside page 4, malicious content only in the truncated-away tail. Minimum bar before merge.
3. Per-page provenance in the fused block (`--- source N: url, fetchedAt ---`), carried into the preview UI (Pitfall 4 metadata) so users can attribute influence per source.
4. If extraction is upgraded (robust HTML→text), re-run the FULL adversarial suite against the new extractor before it touches the merge path — extractor change = security-relevant change, gated like one.
5. Keep the v2.2 SSRF/private-IP/fetch-cap policy enforced per page AND on the fan-out (per-hop re-check already exists — verify the parallel path doesn't skip it).

**Warning signs:**
- Merge/fuse builder accepts raw `String` page bodies.
- Adversarial tests only cover single-page cases.
- Extraction upgrade PR with no adversarial re-run.
- Fused block without per-source delimiters/provenance.

**Phase to address:**
Multi-fetch phase for the choke-point + adversarial tests (exit gate); extraction-upgrade work (whenever scheduled) must re-pass the same gate before merging.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Reuse v2.2 per-page caps × 5 URLs, no global budget | Zero new logic | Context overflow on small-window local models; OOM-class bug shipped | Never — budget is day-one (Pitfall 2) |
| `coroutineScope` + `awaitAll()` for fan-out ("simpler") | Less code | One bad host kills all pages; no partial results | Never — `supervisorScope` is the same line count |
| Full extracted text in Room "for now" | Preview works immediately | DB bloat, slow queries, recomposition jank; migration needed to undo | Never — metadata + excerpt + LRU from the start (Pitfall 4) |
| WorkManager periodic retry ("standard tool") | Familiar API | 15-min granularity, worker spam, stale-turn injection | Never for auto-retry; only for explicit user-scheduled retry with dedup (Pitfall 6) |
| Per-chat toggle as non-null default-false column | No NULL handling | Silently overrides global-ON users' choice on upgrade | Never — nullable inherit (Pitfall 5) |
| Merge path concatenates raw strings, "sanitizer runs earlier" | Faster merge code | One bypass poisons fused block; invisible until exploit | Never — choke-point sanitize (Pitfall 7) |
| Retry re-runs inference on reconnect | "Feels seamless" | App acts on stale intent; unexpected data/battery use | Never — retry fetch only, user re-sends |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| OkHttp fan-out | New client per URL; default timeouts inherited blindly | One shared client; per-fetch timeout + overall deadline; `maxRequestsPerHost` reviewed |
| `ConnectivityGate` (v2.2) | Boolean check reused for retry ("poll until online") | Extend to a connectivity *Flow*; retry on gain-events only, never poll |
| Room transcript | New columns for full page text + non-null toggle with wrong default | Metadata + excerpt columns only; nullable toggle (NULL = inherit global) |
| ChatViewModel grounding hook | Second hook/branch for multi-URL alongside the v2.2 single path | One hook, N=1 is just the degenerate case — single code path for 1..5 URLs |
| LiteRT-LM / LM Studio helpers | Per-backend budget tweaks scattered in helpers | Budget computed once in the hook from allowlist model metadata; helpers unchanged (v2.2 keystone discipline preserved) |
| Preview UI ↔ cache | Preview reads Room full-text column directly | Preview reads LRU by content-hash; Room holds excerpt fallback; re-fetch on miss |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Tail-latency fan-out | Grounded turns take 10s+ on flaky networks | Per-fetch timeout + overall deadline + drop policy (Pitfall 1) | First stalled host on mobile data |
| Token-budget overflow | Slow inference, evicted history, RAM spike on-device | Global budget ÷ pages; model-window-aware (Pitfall 2) | First 5-URL turn on a small-window local model |
| Cache without bounds | Preview cache grows to MBs over a long session | LRU with entry + byte caps; eviction test | Long grounding-heavy session on low-RAM device |
| Re-fetch every follow-up | Same 5 URLs re-downloaded per turn in a thread | URL+hash cache with short TTL (e.g. 5–10 min) scoped per conversation | Second follow-up question on same sources |
| Retry storms on flapping network | Connect/disconnect oscillation triggers fetch per flap | Debounce gain-events; dedup by (message, URL); attempt cap (Pitfall 6) | Elevator/tunnel commute usage |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Merge path bypasses per-page sanitization | Single malicious page hijacks fused answer | Choke-point `sanitizePage`; merge accepts sanitized spans only (Pitfall 7) |
| Colluding pages untested | Quorum-style instruction override succeeds | Multi-page adversarial suite incl. 3-of-5 collusion (Pitfall 7) |
| Extractor upgrade without adversarial re-run | Re-admitted script/comment/metadata vectors | Extractor change gated on full adversarial suite |
| Retry refetch skipping SSRF re-check | Redirect target changed since first fetch; LAN probe via retry | Same URL policy + per-hop checks on every refetch, including retries |
| Preview rendering extracted HTML raw | Stored-XSS-adjacent: `WebView`/HTML render executes page content | Preview renders plain text only (extracted text, never raw HTML, never `WebView` with JS) |
| Stale-turn retry injecting context | Retry lands in a conversation the user already left | Epoch check: retry applies only if conversation + turn still current |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| "Fetching…" with no per-source detail | Mystery hang on 5-URL turns | Progressive state: "Fetching 2/5…", per-source success/failed markers, dropped-source note |
| Failed source silently dropped | Answer cites sources that weren't actually read | Visible per-source status in Fuentes UI (✓/✗/truncated); answer never claims dropped sources |
| Toggle layers disagree silently | User disables globally, old chat still grounds (or vice versa) | Show effective state + its source layer at the toggle site (Pitfall 5) |
| Preview empty after process death | Tap source → blank, looks broken | Excerpt always available (Room); full text reload affordance when evicted |
| Retry fires into a dead conversation | Surprise data use + confusing late banner | Retry only for the open conversation/current turn; silent-drop otherwise with no banner |
| 5 sources, no attribution | Can't tell which source influenced which claim | Per-source numbered markers preserved from v2.2, extended per page; tap → preview (ties Pitfalls 4 + 7 together) |

## "Looks Done But Isn't" Checklist

- [ ] **Multi-fetch:** Often missing partial-failure handling — verify 1-of-5 hostile/slow/dead still yields a 4-page grounded answer within the deadline
- [ ] **Multi-fetch:** Often missing cancellation — verify Stop mid-fetch cancels all N calls with zero late-arriving pages in the next turn
- [ ] **Budget:** Often missing worst-case assertion — verify 5 × max-size pages produce a `[WEB CONTEXT]` block within the global budget, all pages truncation-marked
- [ ] **Budget:** Often missing model-awareness — verify a small-window local model gets a smaller grounding block than a large-window remote one
- [ ] **Preview:** Often missing storage bounds — verify Room holds metadata + excerpt only, full text behind a bounded LRU, preview works from excerpt alone
- [ ] **Preview:** Often missing plain-text rendering — verify preview never renders raw HTML / never uses `WebView` with JS
- [ ] **Toggle:** Often missing precedence matrix — verify all 12 (global × perChat × perMessage) combinations resolve correctly
- [ ] **Toggle:** Often missing legacy upgrade — verify a pre-v2.3 conversation (NULL column) follows the global default
- [ ] **Retry:** Often missing dedup — verify flapping connectivity produces exactly one retry per (message, URL), superseded turns cancel
- [ ] **Retry:** Often missing scope guard — verify retry re-fetches only, never re-runs inference, and never touches a closed conversation
- [ ] **Security:** Often missing multi-page adversarial cases — verify 1-of-5 malicious, 3-of-5 colluding, and delimiter-mimic-in-page-N all fail to hijack
- [ ] **Security:** Often missing refetch policy — verify retry and cache-miss refetch re-apply SSRF/private-IP checks and fetch caps

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Fetch storm / tail latency shipped | MEDIUM | Hotfix timeouts + deadline + drop policy; no schema change needed — reine in the fan-out, ship |
| Budget blowout on small models | MEDIUM | Hotfix global cap + per-page division; consider grounding kill-switch pref while fixing; no migration |
| Stop-leak / stale pages in next turn | MEDIUM | Move fan-out under generation job; add epoch check; regression test mid-fetch Stop |
| Full text already in Room | HIGH (migration territory) | Ship migration moving bodies to cache/file store, leaving excerpt+metadata; or versioned table with cleanup worker — expensive, hence "never" in debt table |
| Toggle precedence wrong post-ship | LOW–MEDIUM | Centralize resolver + backfill policy doc; if non-null default shipped, migrate values to nullable-inherit carefully |
| Worker-spam retry shipped | LOW | Cancel all by tag, replace with connectivity-Flow retry; drain duplicate workers on upgrade |
| Multi-page injection in the wild | HIGH (trust + safety) | Tighten choke-point, emergency adversarial patch, disclose; grounding kill-switch pref buys time |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| 1 Fetch storm | Multi-fetch + budget phase | Kill-1-of-5 test; deadline test; single shared client |
| 2 Budget blowout | Multi-fetch + budget phase | 5×max-size budget assertion; model-window-aware test |
| 3 Stop doesn't stop N | Multi-fetch + budget phase | Mid-fetch Stop regression test; epoch check |
| 7 Injection × N | Multi-fetch + budget phase (choke-point + adversarial gate) | Multi-page adversarial suite incl. collusion; extractor re-gate rule |
| 4 Preview storage | Preview + toggle phase | Schema review (no unbounded text col); LRU bounds test; excerpt-only preview test |
| 5 Toggle precedence | Preview + toggle phase | 12-case matrix test; legacy-NULL upgrade test |
| 6 Retry queue | Offline-retry phase (last) | Dedup test; no-inference test; closed-conversation drop test |

Suggested ordering rationale: fetch policy + budget + security choke-point first (everything else — preview content, retry payloads, toggle-gated fetching — consumes the fan-out's output shape); preview + toggle second (storage schema and precedence resolver must exist before retry can reference them); offline retry last (it orchestrates all three prior pieces and must respect fetch deadlines, cache identity, and toggle resolution). Extraction-quality decision (open research question) should land before or inside the first phase — budget numbers and adversarial baselines both depend on it.

## Sources

- Repo-verified, HIGH: v2.2 grounding pipeline shape (`data/grounding/`, pre-inference hook in `ChatViewModel.sendMessage`, `[WEB CONTEXT]` block, `ConnectivityGate`, `isFetchingWeb` transient, default-ON global toggle); v2.1 single-flight cancellation (`Call.cancel()`, Stop discipline); Room transcript + DataStore prefs conventions; `.planning/research/PITFALLS.md` (v2.2) Pitfalls 5–6 for the inherited injection/SSRF baseline
- Android WorkManager constraints/backoff/unique-work guidance, MEDIUM (training knowledge, verify against current docs at plan time): `developer.android.com` background-work guides — `NetworkType.CONNECTED` constraints, `ExistingWorkPolicy`, 15-min periodic minimum
- OkHttp Dispatcher/connection-pool/timeout behavior, MEDIUM (training knowledge, stable API surface): `square.github.io/okhttp` — shared client, per-call timeouts, `Dispatcher.maxRequestsPerHost`
- Kotlin `supervisorScope` vs `coroutineScope` failure semantics, MEDIUM (stable language contract): `kotlinlang.org/api/kotlinx.coroutines`
- OWASP LLM prompt-injection prevention (cheat sheet series) + v2.2 research citations, MEDIUM — policy for choke-point sanitization and adversarial testing carries over unchanged, amplified to N pages
- LOW confidence, needs phase-level validation: exact global token budget numbers per allowlisted model window; LRU size/TTL tuning for the preview cache; whether extraction upgrade changes the adversarial baseline (flagged as a gate, not assumed)

---
*Pitfalls research for: Warped v2.3 Web Grounding v2*
*Researched: 2026-09-28*
