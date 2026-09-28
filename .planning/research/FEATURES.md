# Feature Research

**Domain:** Web grounding v2 for mobile LLM chat (Warped / Android)
**Researched:** 2026-09-28
**Confidence:** HIGH (existing codebase verified) / MEDIUM (competitor behavior via web sources)

## Feature Landscape

### Table Stakes (Users Expect These)

Features users assume exist once any web grounding exists. Missing these = v2 feels incomplete.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Multi-URL fetch (2–5 URLs/message, fused context) | Users paste/compare multiple links; Perplexity cites 6–12 sources per answer, ChatGPT 1–3 — a single-URL cap feels broken once grounding exists | MEDIUM | Parallel fetch with `async`/`awaitAll` on Dispatchers.IO, per-URL timeout (reuse existing 8/10/20s budgets), per-URL 64KB cap; fuse as numbered `[WEB CONTEXT 1..N]` blocks. Total context budget must be enforced (e.g. truncate tail sources) so small-context local models don't overflow. Depends on existing `WebPageFetcher`, `UrlDetector` (must return list, not first), `GroundingPrompt.buildBlock`, `WebContextSanitizer` (per-block sanitization to preserve hijack protection). |
| Numbered Fuentes list matching context blocks | Already shipped in v2.2 for 1 URL; with N URLs the [1..N] numbering must stay stable between injected context, model-visible numbering, and rendered list | LOW | Extend existing `GroundingResult` (currently `Grounded(block, url)` single) to `Grounded(blocks: List<SourcedBlock>)`; UI already renders numbered list — extend to N items. |
| Per-source fetch status in "Leyendo página…" chip | Existing transient chip covers 1 fetch; with N fetches users expect progress ("Leyendo 2 de 4…") and per-source success/skip indication | LOW | Chip state becomes `Loading(done, total)` → `Done(okCount, skippedCount)`. Skipped sources must still appear or be explicitly marked skipped — silent drops destroy trust. |
| Partial grounding (some URLs fail → ground with the rest) | Network reality: one dead link must not poison the whole turn. Android offline-first guidance: retry connectivity errors, don't retry 4xx/auth errors | LOW | Per-URL outcome is independent: success → block; failure → `ModelOnly`-equivalent skip for that URL only. Turn-level banner only when ALL fail (reuse existing offline-vs-failure banner copy). |
| Global default-ON toggle keeps working | Already shipped (`AdvancedPreferences.webGroundingEnabled`, DataStore, Settings switch). v2 must not regress it | LOW | No change; per-chat override resolves against this default. |

### Differentiators (Competitive Advantage)

Features that set Warped apart. Perplexity/ChatGPT web patterns inform, but on-device + offline-first is Warped's edge.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Sources preview UI (tap source → preview extracted text in-chat) | Trust pattern from Perplexity (citations as "receipts"); in-chat preview avoids leaving the app via Custom Tab/browser. No mobile competitor does *extracted-text* preview — they link out | MEDIUM | Bottom sheet (`ModalBottomSheet`) showing stored extracted text per source (title/domain, char count, truncated flag), with "Abrir en navegador" action. Requires persisting extracted text per message (Room: new `grounded_sources` table or JSON column on MessageEntity: url, title, snippet/extract, status). Cache hit = instant preview, no re-fetch. Design decision: full extracted text (up to 64KB cap each) vs snippet — recommend full-with-scroll, it's already sanitized and local. |
| Per-chat web toggle (per-conversation override of global default) | Power-user control: research chats grounded, creative/coding chats not. ChatGPT/LM Studio have no per-chat web toggle — genuine differentiator | LOW–MEDIUM | Tri-state per conversation: `null` (follow global) / `true` / `false`. Needs Room migration (new nullable column on `ConversationEntity` + migration v15), chat header/menu UI (overflow menu or header icon), resolution `effectiveEnabled = override ?: globalDefault`. Keep tri-state, NOT boolean — boolean forces backfill and breaks "follow global" semantics. |
| Per-message override (skip grounding for one message) | Lightweight escape hatch: "answer from memory" without toggling the chat. Cheap to add once per-chat toggle exists | LOW | Long-press send / chip toggle on composer ("Sin web" chip when grounding would trigger). Pass-through flag on the send path only; no persistence needed. |
| Offline retry queue (retry fetch when back online) | Offline-first is Warped's core value; local chat works offline but grounding silently degrades. Queue turns failed-only-offline fetches into retry | MEDIUM | Two scopes possible (see dependencies). Recommended: **message-scoped retry** first (see MVP). Full WorkManager persistent queue (NetworkType.CONNECTED + exponential backoff) is the Android-canonical pattern (developer.android.com offline-first guide) but is heavier; message-scoped `ConnectivityManager.registerDefaultNetworkCallback` + one-shot re-fetch is enough for chat UX. Must distinguish OFFLINE (retryable) from FETCH_FAILED (not retryable) — existing `GroundingResult.Reason` already does this. |
| Fused multi-source answer hints (numbered context blocks the model can cite) | Extending `[WEB CONTEXT]` to numbered `[WEB CONTEXT 1..N]` with per-block source URL lets even small local models attribute ("según [2]…") without any citation-parsing machinery | LOW | Prompt-engineering only: `GroundingPrompt.buildBlock` gains index + total. No model output parsing (unlike Perplexity's bracket-pill system — explicitly out of scope, see anti-features). |

### Anti-Features (Commonly Requested, Often Problematic)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Model-emitted inline citation pills (`[1]` tappable in response text) | Looks like Perplexity; "real" grounded UX | Requires parsing/validating model output citations — small local models hallucinate markers, break Markdown rendering (brackets collide with link syntax), need renderer + copy-behavior surgery. Flaig (2026) teardown: inline citations are a 4-constraint compound problem (rendering, copy, portability, grammar). | Numbered Fuentes list + preview sheet (already planned). Model *may* reference [N] via prompt hints, but UI never parses response text for markers. |
| Unlimited URL count per message | "More context = better answers" | Context-window overflow on small local models, battery/radio drain from N parallel fetches, prompt-injection surface scales with N. Perplexity retrieves many but *cites few* for a reason. | Hard cap 5 (milestone target), per-source char budget, total fused budget with tail truncation. |
| Full WorkManager persistent grounding queue with history rewrite | "Never lose a grounding" | Grounding is turn-scoped and ephemeral — re-injecting fetched context into an old turn after reconnect rewrites history semantics (message already answered model-only). Persistent queue + re-answer flow is a second feature disguised as retry. | Message-scoped retry: banner/queued-state on the failed turn with "Reintentar" action when online; re-fetch re-runs the same turn (regenerate with context), doesn't patch history. Graduate to WorkManager only if message-scoped retry proves insufficient. |
| JavaScript-rendered page support (WebView extraction) | Many modern pages are JS SPAs; heuristic extractor gets shells | WebView per-URL fetch is slow (seconds), memory-heavy, breaks `Dispatchers.IO` threading (WebView is main-thread), and executes arbitrary JS = XSS/prompt-injection escalation. | Keep heuristic HTML→text; mark JS-shell pages as skipped (empty-extract → skip path already exists). Revisit only via research spike on extraction quality. |
| Auto-grounding linkified URLs inside *model responses* | "Everything clickable should ground" | Recursive fetch loop risk (response links trigger fetches trigger responses), unbounded network use, unpredictable latency mid-stream. | Ground only user-pasted URLs in user messages (current v2.2 semantic). Response URLs render as plain links. |

## Feature Dependencies

```
[Multi-URL fetch]
    └──requires──> [UrlDetector returns list]
    └──requires──> [GroundingResult.Grounded becomes list]
    └──requires──> [GroundingPrompt numbered blocks]
    └──requires──> [WebContextSanitizer per-block]
    └──requires──> [Chip progress state (done/total)]

[Sources preview UI]
    └──requires──> [Multi-URL fetch] (N sources to preview)
    └──requires──> [Persist extracted text per message (Room)]
                        └──requires──> [Room migration v15]

[Per-chat web toggle]
    └──requires──> [Room migration v15 (nullable override column)]
    └──requires──> [Effective-flag resolution (override ?: global)]
    ├──enhances──> [Per-message override] (same send-path flag)

[Offline retry queue (message-scoped)]
    └──requires──> [OFFLINE vs FETCH_FAILED distinction] (already exists)
    └──requires──> [Partial grounding] (know which URLs to retry)
    ├──enhances──> [Sources preview UI] (retry fills preview cache)
```

### Dependency Notes

- **Multi-URL fetch requires UrlDetector list:** current detector returns first URL only (v2.2 semantic). Must return ordered distinct list, capped at 5, with same-scheme validation.
- **Multi-URL fetch requires GroundingResult reshape:** `Grounded(block, url)` → `Grounded(sources: List<GroundedSource>)` where `GroundedSource(url, block, extract, title?)`. Everything downstream (ChatViewModel injection, Fuentes UI, banner logic) keys off this type — reshape first, it's the keystone change.
- **Sources preview requires persistence:** extracted text lives only in the prompt today. Persist per assistant message (FK to MessageEntity) so preview works after restart/scroll without re-fetch.
- **Per-chat toggle and preview both need Room migration:** combine into a single migration v15 (one `ConversationEntity` column + one new `grounded_sources` table) to avoid two migrations in one milestone.
- **Retry enhances preview:** a successful retry populates the same persisted source rows the preview sheet reads — no separate cache path.
- **Banner logic conflict:** current banner shows on any model-only turn. With partial grounding, banner must fire only on all-fail; per-source skip is chip-level, not banner-level. Update banner condition when partial grounding lands — same phase, not separate.

## MVP Definition

### Launch With (v2.3)

- [ ] Multi-URL fetch (2–5, parallel `async`/`awaitAll`, per-URL caps, numbered fused blocks) — core milestone promise
- [ ] Numbered Fuentes list for N sources + progress chip (`done/total`, skip counts) — table stakes once N>1
- [ ] Partial grounding (fail-one-keep-rest, all-fail banner only) — correctness requirement of multi-fetch
- [ ] `GroundingResult` list reshape + `UrlDetector` list + numbered `GroundingPrompt` — keystone refactor enabling everything
- [ ] Per-chat web toggle (tri-state, migration v15, header/menu UI) — headline differentiator, cheap once migration exists
- [ ] Sources preview bottom sheet reading persisted extracts — headline differentiator; persist in same v15 migration
- [ ] Message-scoped offline retry (queued state + "Reintentar" on reconnect, OFFLINE-only) — offline-resilience promise without WorkManager weight

### Add After Validation (v2.3.x)

- [ ] Per-message "Sin web" composer override — trigger: users ask for one-off model-only answers
- [ ] Preview "Abrir en navegador" (Custom Tab) — trigger: users want full page after reading extract
- [ ] Total fused-context budget tuning per model context size — trigger: overflow reports on small local models

### Future Consideration (v2.4+)

- [ ] WorkManager persistent grounding queue — why defer: message-scoped retry covers chat UX; persistent queue only pays off with background/drain semantics Warped doesn't need yet
- [ ] Extraction quality upgrade (heuristic → robust HTML→text) — why defer: explicitly a research decision for this milestone; don't bundle with multi-URL
- [ ] Model-output citation parsing — why defer: anti-feature (see above); revisit only with larger/more reliable local models

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Multi-URL fetch + fused numbered context | HIGH | MEDIUM | P1 |
| GroundingResult list reshape (keystone) | HIGH | LOW | P1 |
| Partial grounding + all-fail banner fix | HIGH | LOW | P1 |
| Per-chat web toggle (tri-state) | HIGH | LOW–MEDIUM | P1 |
| Sources preview bottom sheet + persistence | HIGH | MEDIUM | P1 |
| Message-scoped offline retry | MEDIUM | MEDIUM | P1 |
| Progress chip (done/total) | MEDIUM | LOW | P1 |
| Per-message override | MEDIUM | LOW | P2 |
| Custom Tab "open in browser" | LOW | LOW | P2 |
| WorkManager persistent queue | LOW | HIGH | P3 |
| Model-output citation pills | LOW | HIGH | P3 |
| JS-rendered extraction | LOW | HIGH | P3 |

**Priority key:**
- P1: Must have for launch
- P2: Should have, add when possible
- P3: Nice to have, future consideration

## Competitor Feature Analysis

| Feature | Perplexity (answer engine) | ChatGPT + browsing | LM Studio (desktop) | Our Approach |
|---------|---------------------------|--------------------|---------------------|--------------|
| Multi-source grounding | 6–12 sources retrieved, inline `[N]` pills + Sources card above response | 1–3 sources, footnote markers, paraphrase-heavy | No web grounding at all | 2–5 pasted URLs, numbered `[WEB CONTEXT N]` blocks + Fuentes list (no output parsing) |
| Source preview | Source cards/chips open full pages | Footnote links open full pages (external) | N/A | In-chat bottom sheet over *extracted text* (offline-readable, no re-fetch) |
| Per-chat/per-message web control | Always-on (retrieval is the product) | Per-message browsing toggle in some modes | N/A | Tri-state per-chat override + global default-ON + per-message escape hatch |
| Offline behavior | Online-only product | Online-only product | Fully offline (no web) | Offline-first: local chat continues model-only; OFFLINE fetches queued for retry |
| Citation trust model | Citations as receipts, persistent numbered | Inconsistent unless Deep Research | None | Fuentes list + preview = receipts without output parsing |

## Sources

- Existing codebase (HIGH): `data/grounding/` (WebPageFetcher, GroundingResult, HtmlToTextExtractor, UrlDetector, GroundingPrompt, WebContextSanitizer), `AdvancedPreferences.webGroundingEnabled` (DataStore), `ConversationEntity`/`MessageEntity` Room schema, `ChatViewModel` grounding call site
- Setproduct "Designing AI chat interfaces" (2026) — citation-as-receipts pattern, Perplexity trust model, message-state checklist (MEDIUM)
- Flaig "How Leading AI Apps Implement Inline Citations" (2026-04) — 4-constraint compound problem, Perplexity `[web:1]` uniform syntax, Markdown-collision warning (MEDIUM)
- LibreChat PR #7032 (Perplexity sources menu + tooltips) — two-tab Search-vs-Sources menu precedent for preview UI (MEDIUM)
- Android Developers: offline-first guide (read queue + WorkManager drain + exponential backoff), WorkManager BackoffPolicy docs (EXPONENTIAL default, LINEAR option, 10s min) (HIGH for platform pattern)
- AnythingLLM #2827 (Perplexity citations populated from response object, not parsed) — precedent for persisting source metadata alongside message (LOW, single issue)

---
*Feature research for: Warped v2.3 Web Grounding v2 (multi-URL, preview, per-chat toggle, retry)*
*Researched: 2026-09-28*
