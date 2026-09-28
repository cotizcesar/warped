# Roadmap: Warped

## Overview

v2.2 Simplificación + Web Grounding shipped 2026-09-28 (Phases 49–51): dead surface removed (Skills tool-calling, HF token plumbing, HF model search → static catalog), one heuristic zero-dependency web-grounding hook, and the syntax-theme selector fix. 10/14 requirements MET, 4 PARTIAL (deferred device smokes, accepted). v2.3 Web Grounding v2 extends the single-URL pipeline to multi-page fused context (Phases 52–54): foundation fetch + extraction swap first, preview + toggle second (one Room migration v15), offline retry last.

## Phases

**Phase Numbering:**

- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

- [x] **Phase 52: Multi-URL Fetch Foundation** - Parallel fan-out, fused context, global budget, Jsoup extraction swap (completed 2026-09-28)
- [x] **Phase 53: Sources Preview + Per-Chat Toggle** - Bottom-sheet preview, Fuentes list, tri-state toggle, one Room migration v15 (completed 2026-09-28)
- [x] **Phase 54: Offline Retry** - Message-scoped queued retry on reconnect (completed 2026-09-28)

## Phase Details

### Shipped: v2.1 Finish v2.0 Leftovers (2026-09-28)

4 phases (45–48), 18/18 requirements MET. Full archive: [`.planning/milestones/v2.1-ROADMAP.md`](milestones/v2.1-ROADMAP.md) · Audit: [`v2.1-MILESTONE-AUDIT.md`](v2.1-MILESTONE-AUDIT.md) (passed)

### Shipped: v2.2 Simplificación + Web Grounding (2026-09-28)

3 phases (49–51), 5 plans, 14 requirements — 10 MET, 4 PARTIAL (DEL-06, WEB-05, WEB-06, THEME-01: automated gates pass, device smoke deferred, user-accepted). Full archive: [`.planning/milestones/v2.2-ROADMAP.md`](milestones/v2.2-ROADMAP.md) · Audit: [`milestones/v2.2-MILESTONE-AUDIT.md`](milestones/v2.2-MILESTONE-AUDIT.md) (gaps_found, accepted)

### Shipped: v2.3 Web Grounding v2 (2026-09-28)

3 phases (52–54), 8 plans, 12 requirements — 12/12 verified (automatable evidence 100%; 3 device-smoke follow-ups accepted, release-UAT standing). Full archive: [`.planning/milestones/v2.3-ROADMAP.md`](milestones/v2.3-ROADMAP.md) · Audit: [`milestones/v2.3-MILESTONE-AUDIT.md`](milestones/v2.3-MILESTONE-AUDIT.md) (gaps_found, accepted)


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
