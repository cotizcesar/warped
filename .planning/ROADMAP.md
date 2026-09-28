# Roadmap: Warped

## Overview

v2.2 Simplificación + Web Grounding shipped 2026-09-28 (Phases 49–51): dead surface removed (Skills tool-calling, HF token plumbing, HF model search → static catalog), one heuristic zero-dependency web-grounding hook, and the syntax-theme selector fix. 10/14 requirements MET, 4 PARTIAL (deferred device smokes, accepted). v2.3 Web Grounding v2 extends the single-URL pipeline to multi-page fused context (Phases 52–54): foundation fetch + extraction swap first, preview + toggle second (one Room migration v15), offline retry last.

## Phases

**Phase Numbering:**

- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

- [x] **Phase 52: Multi-URL Fetch Foundation** - Parallel fan-out, fused context, global budget, Jsoup extraction swap (completed 2026-09-28)
- [ ] **Phase 53: Sources Preview + Per-Chat Toggle** - Bottom-sheet preview, Fuentes list, tri-state toggle, one Room migration v15
- [ ] **Phase 54: Offline Retry** - Message-scoped queued retry on reconnect

## Phase Details

### Shipped: v2.1 Finish v2.0 Leftovers (2026-09-28)

4 phases (45–48), 18/18 requirements MET. Full archive: [`.planning/milestones/v2.1-ROADMAP.md`](milestones/v2.1-ROADMAP.md) · Audit: [`v2.1-MILESTONE-AUDIT.md`](v2.1-MILESTONE-AUDIT.md) (passed)

### Shipped: v2.2 Simplificación + Web Grounding (2026-09-28)

3 phases (49–51), 5 plans, 14 requirements — 10 MET, 4 PARTIAL (DEL-06, WEB-05, WEB-06, THEME-01: automated gates pass, device smoke deferred, user-accepted). Full archive: [`.planning/milestones/v2.2-ROADMAP.md`](milestones/v2.2-ROADMAP.md) · Audit: [`milestones/v2.2-MILESTONE-AUDIT.md`](milestones/v2.2-MILESTONE-AUDIT.md) (gaps_found, accepted)

### Active: v2.3 Web Grounding v2

**Milestone Goal:** Pasted URLs ground answers with multi-page context, previewable sources, and offline resilience.

### Phase 52: Multi-URL Fetch Foundation

**Goal**: Pasted URLs ground answers with fused multi-page context that fits small local-model windows
**Depends on**: Phase 51
**Requirements**: FETCH-01, FETCH-02, FETCH-03, EXTRACT-01, EXTRACT-02
**Success Criteria** (what must be TRUE):

  1. User pasting 2–5 URLs in one message gets one answer grounded in all fetchable pages with numbered sources
  2. User gets a grounded answer from working pages when one link is dead, and the model-only banner only when ALL pages fail
  3. User sees fetch progress per source ("Leyendo 2 de 4…") with per-source ok/skipped states — no silent drops
  4. User on a small local model gets answers that fit context (global grounding budget divided across pages, model-window-aware)
  5. User gets cleaner grounded answers via Jsoup parse-only extraction with the v2.2 fetch policy unchanged (stripped client, 64KB cap, timeouts)

**Plans**: 2 plans

- [x] 52-01-PLAN.md — Jsoup swap, allUrls, budget, fused blocks + exit-gate tests
- [x] 52-02-PLAN.md — cancel fix, fan-out orchestrator, hook + N-source UI

**UI hint**: yes

### Phase 53: Sources Preview + Per-Chat Toggle

**Goal**: Users can preview what each source says and control web grounding per conversation
**Depends on**: Phase 52
**Requirements**: SRC-01, SRC-02, SRC-03, TOGGLE-01, TOGGLE-02, TOGGLE-03
**Success Criteria** (what must be TRUE):

  1. User can tap a source to preview its extracted text in a bottom sheet without leaving chat
  2. User sees a numbered Fuentes list covering all N fetched sources for the turn
  3. User can open the full page in the browser from the preview ("Abrir en navegador")
  4. User can override web grounding per conversation (on/off/inherit-global) and send a one-off model-only message ("Sin web") without changing any toggle
  5. User's per-chat web preference and persisted sources survive app restarts (single Room migration v15)

**Plans**: TBD
**UI hint**: yes

### Phase 54: Offline Retry

**Goal**: Users offline at send time can retry grounding when back online without resending
**Depends on**: Phase 53
**Requirements**: RETRY-01
**Success Criteria** (what must be TRUE):

  1. User offline at send time sees a queued state with a "Reintentar" affordance on reconnect
  2. Retry fetches the URLs again through the same grounding entry point — history is never rewritten and inference never re-runs silently
  3. Retry results land in the same persisted source rows the preview sheet reads

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
| 53. Sources Preview + Per-Chat Toggle | 0/TBD | Not started | - |
| 54. Offline Retry | 0/TBD | Not started | - |

---

**Cumulative state after v2.2:** 51 phases shipped, 315 requirements delivered across v1.0–v2.2 (305 entering v2.2 + 10 MET in v2.2; 4 partials carried as release-UAT smokes).
