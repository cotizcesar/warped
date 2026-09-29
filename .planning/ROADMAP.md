# Roadmap: Warped

## Overview

v2.2 Simplificación + Web Grounding shipped 2026-09-28 (Phases 49–51): dead surface removed (Skills tool-calling, HF token plumbing, HF model search → static catalog), one heuristic zero-dependency web-grounding hook, and the syntax-theme selector fix. 10/14 requirements MET, 4 PARTIAL (deferred device smokes, accepted). v2.3 Web Grounding v2 extends the single-URL pipeline to multi-page fused context (Phases 52–54): foundation fetch + extraction swap first, preview + toggle second (one Room migration v15), offline retry last.

## Phases

**Phase Numbering:**

- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

- [ ] **Phase 55: Tavily Search Foundation** - API key settings + Keystore, Tavily client, search→ground fusion
- [ ] **Phase 56: Local Agentic Loop** - LiteRT-LM function calling (web_search/web_fetch), channel hygiene, trust boundary
- [ ] **Phase 57: Remote Agentic Loop** - OpenAI-compatible tools[] loop with capability gating
- [ ] **Phase 58: OpenGraph Thumbnails** - OG scrape + Room v16, Coil cards, sheet header

## Phase Details

### Shipped: v2.1 Finish v2.0 Leftovers (2026-09-28)

4 phases (45–48), 18/18 requirements MET. Full archive: [`.planning/milestones/v2.1-ROADMAP.md`](milestones/v2.1-ROADMAP.md) · Audit: [`v2.1-MILESTONE-AUDIT.md`](v2.1-MILESTONE-AUDIT.md) (passed)

### Shipped: v2.2 Simplificación + Web Grounding (2026-09-28)

3 phases (49–51), 5 plans, 14 requirements — 10 MET, 4 PARTIAL (DEL-06, WEB-05, WEB-06, THEME-01: automated gates pass, device smoke deferred, user-accepted). Full archive: [`.planning/milestones/v2.2-ROADMAP.md`](milestones/v2.2-ROADMAP.md) · Audit: [`milestones/v2.2-MILESTONE-AUDIT.md`](milestones/v2.2-MILESTONE-AUDIT.md) (gaps_found, accepted)

### Shipped: v2.3 Web Grounding v2 (2026-09-28)

3 phases (52–54), 8 plans, 12 requirements — 12/12 verified (automatable evidence 100%; 3 device-smoke follow-ups accepted, release-UAT standing). Full archive: [`.planning/milestones/v2.3-ROADMAP.md`](milestones/v2.3-ROADMAP.md) · Audit: [`milestones/v2.3-MILESTONE-AUDIT.md`](milestones/v2.3-MILESTONE-AUDIT.md) (gaps_found, accepted)

### Active: v2.4 Agentic Web

**Milestone Goal:** The model itself searches (Tavily) and fetches the web via tool calling — local and remote — with OpenGraph thumbnail cards per source.

### Phase 55: Tavily Search Foundation

**Goal**: Users ground answers in Tavily search results with a stored API key
**Depends on**: Phase 54
**Requirements**: TAV-01, TAV-02, TAV-03
**Success Criteria** (what must be TRUE):

  1. User stores a Tavily key in Settings (Keystore-encrypted) with a working test-connection
  2. User gets answers grounded in top-N Tavily results with numbered citations through the same Fuentes/preview pipeline
  3. Search never runs when grounding is off or offline, with a clear missing/invalid-key message

**Plans**: 2 plans

- [x] 55-01-PLAN.md — Tavily key alias + search-to-fused producer (tracer backbone)
- [x] 55-02-PLAN.md — Settings key UI + ChatViewModel search branch with gates

**UI hint**: yes

### Phase 56: Local Agentic Loop

**Goal**: Local models invoke web_search/web_fetch autonomously via LiteRT-LM function calling
**Depends on**: Phase 55
**Requirements**: AGENT-01, AGENT-02, AGENT-04
**Success Criteria** (what must be TRUE):

  1. User sees the model search and fetch on its own (multi-turn loop, step cap, Stop cancels) when the question needs fresh info
  2. Tool outputs pass the same trust boundary as fetched pages (sanitized, no hijack, no breakout)
  3. Only web_search/web_fetch are ever exposed — no file/system tools, no local-context leaks

**Plans**: 2 plans

- [x] 56-01-PLAN.md — ToolSets + loop policy, allowlist flag flips, KV-cache hygiene (tracer backbone)
- [x] 56-02-PLAN.md — Provider manual loop + status rows + on-device smoke checkpoint

### Phase 57: Remote Agentic Loop

**Goal**: Remote OpenAI-compatible models use the same tools via native tools[] loop
**Depends on**: Phase 56
**Requirements**: AGENT-03
**Success Criteria** (what must be TRUE):

  1. User on a remote endpoint gets agentic search/fetch with per-provider capability gating (graceful fallback where tools[] unsupported)
  2. Tool-call streaming renders progress honestly (no silent loops); Stop cancels mid-loop

**Plans**: 2 plans

- [ ] 57-01-PLAN.md — Shared pure core (accumulator + matrix + classifier + tools DTOs) + OpenAI tools[] loop tracer
- [ ] 57-02-PLAN.md — Anthropic native dialect + Ollama/LMStudio/Custom fallback + VM skip + secret proof

### Phase 58: OpenGraph Thumbnails

**Goal**: Every grounded source renders a rich thumbnail card with its OpenGraph data
**Depends on**: Phase 57
**Requirements**: OG-01, OG-02, OG-03
**Success Criteria** (what must be TRUE):

  1. Fetch captures og:title/description/image persisted with source rows (single Room migration v16)
  2. User sees a Coil-loaded thumbnail card per source (tap → preview); text-only fallback without image
  3. Preview sheet shows the OG header above extracted text

**Plans**: TBD
**UI hint**: yes

## Progress

**Execution Order:**
Phases execute in numeric order: 52 → 53 → 54. Next milestone continues from Phase 54.

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 49. Surface Removal | 2/2 | Complete (smoke deferred) | 2026-09-28 |
| 50. Web Grounding | 2/2 | Complete (smoke deferred) | 2026-09-28 |
| 51. Syntax-Theme Fix | 1/1 | Complete (smoke deferred) | 2026-09-28 |
| 52. Multi-URL Fetch Foundation | 2/2 | Complete    | 2026-09-28 |
| 53. Sources Preview + Per-Chat Toggle | 4/4 | Complete    | 2026-09-28 |
| 54. Offline Retry | 2/2 | Complete    | 2026-09-28 |

---

**Cumulative state after v2.3:** 54 phases shipped, 327 requirements delivered across v1.0–v2.3 (315 entering v2.3 + 12 verified in v2.3; 3 device-smoke follow-ups accepted as release-UAT).
