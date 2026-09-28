---
gsd_state_version: "1.0"
milestone: v2.2
milestone_name: Simplificación + Web Grounding
status: planning
last_updated: "2026-09-28T12:00:00.000Z"
last_activity: 2026-09-28
progress:
  total_phases: 3
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
  percent: 0
---

# Project State: Warped

**Last updated:** 2026-09-28
**Last activity:** 2026-09-28 — v2.2 roadmap created (Phases 49–51)

## Project Reference

See: .planning/PROJECT.md (updated 2026-09-28 after v2.2 milestone start)

**Core value:** Run and chat with any LLM — local or remote — from a single Android app, with a simple LM Studio-grade experience that works offline.
**Current focus:** **v2.2 Simplificación + Web Grounding** — remove skills/HF-token/search surface, heuristic web grounding with offline fallback, syntax-theme fix. 3 phases (49–51), 14 requirements.

## Current Position

Phase: 49 of 51 (Surface Removal — ready to plan)
Plan: —
Status: Ready to plan
Last activity: 2026-09-28 — Roadmap created

Progress: [░░░░░░░░░░] 0%

## Phase Structure (v2.2)

| Phase | Name | Requirements | Status | Depends On |
|-------|------|--------------|--------|------------|
| 49 | Surface Removal | DEL-01..06 (6) | Not started | Phase 48 |
| 50 | Web Grounding | WEB-01..06 (6) | Not started | Phase 49 |
| 51 | Syntax-Theme Fix | THEME-01..02 (2) | Not started | Phase 50 |

**Total v2.2:** 3 phases, 14 requirements, 14/14 mapped ✓

## Performance Metrics

**Velocity:**

- Total plans completed: 46 (across 6 milestones)
- v1.6 plans: 8 plans across 3 phases
- Average duration: ~18 min

## Accumulated Context

### Decisions

- [v2.2]: 3 phases, not 4 — trust-boundary hardening folded into Phase 50 as exit criteria (single-req WEB-05 phase would be a thin anti-pattern). Coarse granularity.
- [v2.2]: Removals-first ordering (DEL before WEB) — grounding hooks into post-removal transcript shape. Theme fix last, parallelizable with Phase 49.
- [v2.2]: Zero new dependencies — grounding over existing OkHttp + ConnectivityManager; hand-rolled HTML→text (STACK over FEATURES Jsoup).
- [v2.1]: LiteRT-LM target 0.17.1; Summarize stays PromptTemplate (persona, not function).

### Pending Todos

None yet.

### Blockers/Concerns

- Pixel 7 reference-device numbers (PERF-16 + PERF-12/13) stay CI-gated since v2.1 — unchanged, out of v2.2 scope
- Phase 50 research flags: HTML→text quality bar + Jsoup-escalation trigger; fetch-budget numbers vs smallest allowlisted model context window; delimiter robustness on small local models
- Phase 49 planning must check Room `Role` TypeConverter (name vs ordinal) and decide `Summarize` persona fate up front

## Deferred Items

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| Benchmarks | Pixel 7 reference numbers (PERF-16 + PERF-12/13) | CI-gated | v2.1 close |

## Session Continuity

Last session: 2026-09-28
Stopped at: v2.2 roadmap created (Phases 49–51)
Resume: `/gsd-plan-phase 49`
