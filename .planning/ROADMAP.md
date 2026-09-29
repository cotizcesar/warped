# Roadmap: Warped

## Overview

v2.4 Agentic Web shipped 2026-09-29 (Phases 55–58): Tavily search backend with Keystore key, local LiteRT-LM function-calling loop (web_search/web_fetch, device-confirmed), remote OpenAI-compatible tools[] loop across 5 providers, and OpenGraph thumbnail cards via Coil (Room v16). 10/10 requirements verified, 3 device follow-ups accepted. Previous: v2.3 Web Grounding v2 (Phases 52–54, multi-page fused grounding, preview + toggle, offline retry).

## Phases

**Phase Numbering:**

- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

- [ ] **Phase 55: Tavily Search Foundation** - API key settings + Keystore, Tavily client, search→ground fusion
- [ ] **Phase 56: Local Agentic Loop** - LiteRT-LM function calling (web_search/web_fetch), channel hygiene, trust boundary
- [ ] **Phase 57: Remote Agentic Loop** - OpenAI-compatible tools[] loop with capability gating
- [x] **Phase 58: OpenGraph Thumbnails** - OG scrape + Room v16, Coil cards, sheet header (completed 2026-09-29)

## Phase Details

### Shipped: v2.1 Finish v2.0 Leftovers (2026-09-28)

4 phases (45–48), 18/18 requirements MET. Full archive: [`.planning/milestones/v2.1-ROADMAP.md`](milestones/v2.1-ROADMAP.md) · Audit: [`v2.1-MILESTONE-AUDIT.md`](v2.1-MILESTONE-AUDIT.md) (passed)

### Shipped: v2.2 Simplificación + Web Grounding (2026-09-28)

3 phases (49–51), 5 plans, 14 requirements — 10 MET, 4 PARTIAL (DEL-06, WEB-05, WEB-06, THEME-01: automated gates pass, device smoke deferred, user-accepted). Full archive: [`.planning/milestones/v2.2-ROADMAP.md`](milestones/v2.2-ROADMAP.md) · Audit: [`milestones/v2.2-MILESTONE-AUDIT.md`](milestones/v2.2-MILESTONE-AUDIT.md) (gaps_found, accepted)

### Shipped: v2.3 Web Grounding v2 (2026-09-28)

3 phases (52–54), 8 plans, 12 requirements — 12/12 verified (automatable evidence 100%; 3 device-smoke follow-ups accepted, release-UAT standing). Full archive: [`.planning/milestones/v2.3-ROADMAP.md`](milestones/v2.3-ROADMAP.md) · Audit: [`milestones/v2.3-MILESTONE-AUDIT.md`](milestones/v2.3-MILESTONE-AUDIT.md) (gaps_found, accepted)

### Shipped: v2.4 Agentic Web (2026-09-29)

4 phases (55–58), 9 plans, 10 requirements — 10/10 verified (automatable evidence 100%; 3 device follow-ups accepted, release-UAT standing). Full archive: [`.planning/milestones/v2.4-ROADMAP.md`](milestones/v2.4-ROADMAP.md) · Audit: [`milestones/v2.4-MILESTONE-AUDIT.md`](milestones/v2.4-MILESTONE-AUDIT.md) (gaps_found, accepted)

## Progress

**Execution Order:**
Next milestone continues from Phase 58.

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 49. Surface Removal | 2/2 | Complete (smoke deferred) | 2026-09-28 |
| 50. Web Grounding | 2/2 | Complete (smoke deferred) | 2026-09-28 |
| 51. Syntax-Theme Fix | 1/1 | Complete (smoke deferred) | 2026-09-28 |
| 52. Multi-URL Fetch Foundation | 2/2 | Complete    | 2026-09-28 |
| 53. Sources Preview + Per-Chat Toggle | 4/4 | Complete    | 2026-09-28 |
| 54. Offline Retry | 2/2 | Complete    | 2026-09-28 |
| 55. Tavily Search Foundation | 2/2 | Complete    | 2026-09-29 |
| 56. Local Agentic Loop | 2/2 | Complete    | 2026-09-29 |
| 57. Remote Agentic Loop | 2/2 | Complete    | 2026-09-29 |
| 58. OpenGraph Thumbnails | 2/2 | Complete    | 2026-09-29 |

---

**Cumulative state after v2.4:** 58 phases shipped, 337 requirements delivered across v1.0–v2.4 (327 entering v2.4 + 10 verified in v2.4; 3 device follow-ups accepted as release-UAT).
